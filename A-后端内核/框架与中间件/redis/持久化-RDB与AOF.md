看在前面
====

Redis 的数据全在内存，进程一退出就归零。持久化决定两件事：**缓存场景下重启后的回温速度**（冷 Redis = 全量流量回源，正是[缓存穿透-击穿-雪崩](缓存穿透-击穿-雪崩.md)里雪崩的成因 B）；**存储场景下的丢失窗口**（把 Redis 当权威存储时这是正确性问题）。姊妹篇：[数据类型与底层编码](数据类型与底层编码.md)、[过期删除与内存淘汰](过期删除与内存淘汰.md)、[高可用-主从哨兵与集群](高可用-主从哨兵与集群.md)。

两条路线：RDB（某时刻的全量二进制快照）与 AOF（写命令的追加日志），4.0 起还能混合。面试主线是三组权衡：**快照的丢数据窗口 vs 日志的性能损耗；fork 的内存代价 vs 主线程的延迟代价；恢复速度 vs 文件大小**。

先给一张总图：一条写命令的两条持久化路径
====

```
客户端 SET k v
   │
   ▼ ① 主线程执行命令、修改内存，立即回 OK（持久化全部异步化，不阻塞这一步）
┌────────────────── Redis 进程 ──────────────────┐
│ 主线程（事件循环）                                │
│   ├─ AOF 路径：命令追加进 aof_buf                │
│   │     每轮循环末 write() 进 AOF 文件           │
│   │     fsync 时机由 appendfsync 决定（三档）     │
│   └─ RDB 路径：达到 save 触发条件                │
│         fork() 出子进程 → 遍历内存生成 RDB 二进制 │
│         写临时文件 → fsync → 原子 rename          │
└────────────────────────────────────────────────┘
```

分工一句话：RDB 管「灾难恢复时能多快起来」，AOF 管「最多丢多少」。逐个拆。

RDB：fork + 写时复制（COW）
====

> **面试官：bgsave 的完整过程？为什么不阻塞主线程？**

```
父进程（主线程）
   │ fork() ── 阻塞点：内核复制「页表」（不是内存本身）
   │           10GB 实例约几十~几百 ms，见 INFO 的 latest_fork_usec
   ├───────────► 子进程：视角冻结在 fork 那一刻的内存
   │               遍历所有 DB → 序列化为紧凑二进制
   │               写 temp-{pid}.rdb → fsync → rename 为 dump.rdb
   └───────────► 父进程：照常处理写命令
                   写到某内存页时触发缺页异常
                   → 内核复制该页（4KB），父子各持一份 —— COW
```

四个必考细节：

1. **fork 复制的是页表不是数据**：父子先共享物理内存页（标记只读），所以 fork 本身只需复制页表，通常几百毫秒内完成；耗时与内存总量成正比——这就是 `latest_fork_usec` 的含义，也是「Redis 单实例内存建议控制在 10~20GB 内」的第一原因；
2. **COW 的内存翻倍风险**：bgsave 期间父进程每个被改写的页都会复制一份，写入密集 + 快照时间长，内存峰值可能逼近翻倍。注意**删除/过期释放也算写页**，配合 `lazyfree-lazy-expire` 异步释放可减少页拷贝（见[过期删除与内存淘汰](过期删除与内存淘汰.md)）；BigKey 会拖慢快照遍历、拉长 COW 窗口（见[数据类型与底层编码](数据类型与底层编码.md)）；
3. **THP 必须关**：透明大页把复制粒度从 4KB 放大到 2MB，COW 期间写放大 512 倍——`echo never > /sys/kernel/mm/transparent_hugepage/enabled`（Redis 启动日志里那行 WARNING 就是它）；同时 `vm.overcommit_memory=1`，否则大实例 fork 可能直接失败；
4. **触发方式**：手动 `SAVE`（主线程做，阻塞，弃用）/ `BGSAVE`；配置自动触发；**主从全量同步时 master 也会 bgsave**（复用这套机制，见[高可用-主从哨兵与集群](高可用-主从哨兵与集群.md)）；正常 SHUTDOWN 默认做一次同步 save。

RDB 的取舍一句话：**文件紧凑（二进制 + LZF 压缩）、恢复极快（直接载入不用重放）、fork 期间主线程近乎无损；代价是两次快照之间的写入全丢（窗口 = 分钟级）+ fork 瞬时阻塞与内存翻倍风险**。

AOF：三种刷盘策略，先分清 write 和 fsync
====

> **面试官：always / everysec / no 到底差在哪？各丢多少？**

先立概念：`write()` 只是把数据交给操作系统的**页缓存**——进程崩了数据还在，机器断电就没了；`fsync()` 才强迫落盘。三种刷盘策略的差异就是「fsync 的时机」：

| appendfsync | fsync 时机 | 最多丢多少 | 吞吐影响 | 适用 |
|---|---|---|---|---|
| always | 每条写命令后，主线程同步 fsync | **1 条命令** | 受磁盘 IOPS 限制（SSD 约几千 TPS） | 当存储用，丢一条都不行 |
| everysec | 主线程 write，**后台线程每秒 fsync** | **1 秒**的写入 | 几乎无感（生产默认） | 99% 的场景 |
| no | 只 write，交给 OS（Linux 默认约 30s 落盘） | 不确定（可达几十秒） | 最快 | 纯缓存，丢了就回源 |

everysec 的隐藏细节：后台线程若发现**上一次 fsync 超过 2 秒还没完成**（磁盘抖动），本轮主线程只 write 不 fsync 以避免阻塞，同时 `INFO` 里 `aof_delayed_fsync` 计数 +1——生产上拿它监控磁盘健康。

AOF 文件是 RESP 协议原文（`*3\r\n$3\r\nSET...`），恢复 = 逐条重放，所以**文件比 RDB 大、恢复比 RDB 慢**；但只要命令进了文件，窗口外丢失为 0。

AOF 重写：把一万条 INCR 压回一条 SET
====

> **面试官：AOF 越写越大怎么办？重写期间的新写入会不会丢？**

AOF 记的是「过程」：`INCR x` 一万次是一万条记录，当前状态却只需 `SET x 10086`。**BGREWRITEAOF** 同样 fork 子进程，按**当前内存状态**生成最小命令集写新文件；期间父进程的新写入走双写：

```
重写期间：
  父进程新命令 ──┬─► 旧 AOF 文件继续追加（重写失败也不丢数据）
                 └─► 追加到新 AOF（7.0 前经 aof_rewrite_buf 重写缓冲区；
                     7.0 Multi-Part AOF：增量直接写新的 incr 文件，重写缓冲被删除）
  子进程写完 → 父进程补最后一批增量 → 原子替换 → 旧文件清理
```

自动触发条件（两者同时满足）：当前 AOF 比上次重写后**膨胀 100%**（`auto-aof-rewrite-percentage`）且**超过 64MB**（`auto-aof-rewrite-min-size`）。7.0 的 Multi-Part AOF 把文件拆成 base + incr 多文件加 manifest 清单，重写只新开一个 base，不再整体拷贝重写缓冲——重写期间的额外内存与 CPU 显著下降。

4.0 混合持久化：RDB 的速度 + AOF 的窗口
====

`aof-use-rdb-preamble yes`（5.0 起默认开）之后，**AOF 重写时写出的「全量部分」不再是命令，而是 RDB 格式的二进制块**：

```
appendonlydir/
  appendonly.aof.1.base.rdb    ← 重写时刻的全量（RDB 二进制，加载快）
  appendonly.aof.1.incr.aof    ← 重写之后的增量命令（丢失窗口 ≤ 1s）
  appendonly.aof.manifest      ← 7.0 清单，指明 base 与当前 incr
```

恢复时先快速载入 RDB 块，再重放少量增量——**恢复速度接近纯 RDB，丢失窗口仍是 everysec 的 1 秒**。4.0 之前「RDB 恢复快但丢得多 / AOF 全但恢复慢」的两难，混合持久化直接终结，这是生产默认答案。前提：版本 ≥ 4.0；且开了 AOF 重启就优先加载 AOF（不再看 RDB）。

性能权衡表与生产配置
====

| 方案 | 恢复速度 | 丢失窗口 | 运行期开销 | 文件大小 | 定位 |
|---|---|---|---|---|---|
| 纯 RDB | 极快 | 分钟级 | fork 瞬时阻塞 + COW 内存 | 小 | 纯缓存勉强可接受 |
| AOF always | 慢 | ≤1 条 | 每写一次 fsync | 大 | 权威存储 |
| AOF everysec | 中 | ≤1 秒 | 几乎无感 | 大 | 默认 |
| RDB+AOF 混合 | 接近 RDB | ≤1 秒 | fork + 每秒 fsync | 中 | **生产标准** |

```conf
# —— RDB：兜底快照（冷启动回温 + 备份文件）——
save 3600 1 300 100 60 10000      # 1h 内 1 次 / 5min 内 100 次 / 60s 内 1 万次修改
stop-writes-on-bgsave-error yes   # bgsave 失败直接拒写（宁可不可用，不带病运行）
rdbcompression yes                # LZF 压缩
dbfilename dump-6379.rdb
dir /data/redis                   # 独立磁盘，别和日志抢 IO

# —— AOF：everysec + 混合格式 ——
appendonly yes
appendfsync everysec
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 4gb     # 大实例调大，避免频繁重写
aof-use-rdb-preamble yes          # 混合持久化
no-appendfsync-on-rewrite no      # 重写期间不放弃 fsync（保窗口）；极致性能可 yes
aof-load-truncated yes            # 启动时容忍并截断损坏的尾部
```

监控四件套：`latest_fork_usec`（fork 耗时）、`rdb_last_bgsave_status`、`aof_delayed_fsync`（磁盘抖动）、`aof_last_bgrewrite_status`。任何一项恶化，先查磁盘 IO 再查内存规模。

追问链
====

> **面试官：RDB 和 AOF 都开了，重启加载哪个？**

AOF 优先（开了 appendonly 就忽略 RDB 文件），因为 AOF 通常更完整；混合格式下 AOF 的 base 块本身就是 RDB，速度也不吃亏。

> **面试官：bgsave 和 bgrewriteaof 撞到一起会怎样？**

两个子进程互斥排队（`INFO` 的 `rdb_bgsave_in_progress` / `aof_rewrite_in_progress` 可查），同一时刻最多一个 fork 在跑；自动触发要避开写入高峰，否则排队可能把重写推迟到高峰内执行。

> **面试官：fork 为什么会卡几百毫秒？怎么优化？**

fork 要复制页表并调整全内存映射，耗时与实例内存成正比。三板斧：控制单实例内存（10~20GB 内，更大去分片，见[高可用-主从哨兵与集群](高可用-主从哨兵与集群.md)的 Cluster）；关 THP；`vm.overcommit_memory=1`。云主机超卖也会放大 fork 延迟——latest_fork_usec 突刺时先看宿主机。

> **面试官：AOF 文件尾部损坏（断电写了一半）怎么办？**

`redis-check-aof --fix appendonly.aof` 截断到最近一条合法命令边界；`aof-load-truncated yes`（默认）在启动时自动做同样的事。

面试官视角小结
====

1. **双路线定位**：RDB 管恢复速度（二进制快照、fork 异步生成），AOF 管丢失窗口（命令追加、三档 fsync）；缓存丢得起可偏 RDB，存储必须 AOF；
2. **fork + COW**：复制的是页表不是内存；主线程只在 fork 瞬间阻塞（latest_fork_usec）；COW 按页复制带来写入密集期的内存翻倍风险；关 THP、overcommit_memory=1 是标配；
3. **三档 fsync**：always 丢 1 条最慢 / everysec 后台线程每秒、丢 1 秒、生产默认 / no 交给 OS 丢几十秒；用 aof_delayed_fsync 监控磁盘；
4. **重写与混合**：BGREWRITEAOF 按当前内存生成最小命令集，旧文件双写兜底；4.0 混合持久化 base(RDB)+incr(AOF)，恢复快窗口小；7.0 Multi-Part AOF 删掉重写缓冲；
5. **配置口径**：everysec + 混合 + stop-writes-on-bgsave-error yes + 重写阈值按实例调大；监控四件套天天看。

> 2026-10 新增（P0 补全）。
