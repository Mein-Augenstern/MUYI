RocksDB 架构设计一：LSM 与存储引擎
====

嵌入式 KV 持久化库，没有独立进程和端口，像 SQLite 一样被"装进"应用里。TiKV、Flink 状态后端、Kafka Streams、MyRocks 都拿它做底座，后端工程师即使不直接写它，也大概率在依赖它的系统里排查过问题。它的核心命题只有一句：**把随机写转化为顺序写**——一切设计由此展开，一切代价（读放大、空间放大、Compaction）也由此而来。本篇讲骨架（LSM、MemTable、WAL、SST、读取、Compaction），版本与一致性深水区见 [RocksDB架构设计二-版本快照与一致性读取](./RocksDB架构设计二-版本快照与一致性读取.md)；B+ 树的对照见 [MySQL索引详解](./MySQL索引详解.md)。

1 它是什么：五个 API 的存储引擎
------

* Facebook 从 LevelDB fork，针对 SSD/NVMe 优化：多线程 Compaction、Column Family、多种压缩算法。
* 数据模型：key/value 都是字节数组，key **按字节序有序存储**，因此同时支持点查与范围扫描。
* 核心 API 只有五个：`Put / Get / Delete / Iterator / WriteBatch`。没有表结构、没有 SQL、没有二级索引——要什么自己建。

* 在 JVM 里跑的三个工程要点（RocksJava 是 JNI 封装）：
  - **MemTable 和 Block Cache 是 C++ 堆外内存，不受 -Xmx 管控**，容量规划要按进程算；
  - open 时走"读 MANIFEST 恢复元信息 → 回放 WAL → 起后台线程池"，实例本身线程安全；
  - put 在调用线程同步执行 JNI，不切换线程；后台 Flush/Compaction 持续吃 CPU 和 IO，用 `setMaxBackgroundJobs` 控制。

2 LSM-Tree：一切为了顺序写
------

* B+ 树为读优化，写入要原地更新（随机 IO）；LSM 反过来：**先写内存，攒一批顺序刷盘，后台再整理**。
* 主线数据流：

```
写: WAL → MemTable ─(写满)→ Immutable MemTable ─(后台Flush)→ SST(L0) ─(Compaction)→ L1..Ln
读: MemTable → Immutable → L0(文件间重叠,逐个查) → L1+(层内不重叠,二分) → 直到最底层
```

* 代价：读要查多层（读放大）、旧版本占空间（空间放大）、Compaction 反复重写（写放大）。后面 Bloom Filter、Block Cache、Compaction 策略全是为这三笔账打的补丁。

3 写入路径：WriteBatch 与组提交
------

* `Put → 封装 WriteBatch → Write()`：**单条与批量走同一条路径**，WriteBatch 是最小写入单元，天然保证原子性。
* 顺序：先 `WriteToWAL`，再 `InsertInto(mem_table)`；MemTable 超过 `write_buffer_size`（默认 64MB）触发 SwitchMemtable。
* 关键设计：MemTable 写满**不是立刻 Flush，而是转成 Immutable（只读）+ 新开一个活跃 MemTable**，写入永不被 Flush 阻塞。
* **组提交（Group Commit）**：并发写线程排成 Writer Group，首个线程当 Leader，把等待中多个线程的 batch 合并成一次 WAL 写 + 一次 MemTable 写，再通知大家——小 IO 合并成大 IO。

4 WAL：写入流水账
------

* 文件格式：32KB 的 Block 序列；Block 内是多条 Record：`CRC 4B + Len 2B + Type 1B + Data(=WriteBatch)`；跨 Block 拆分为 kFull / kFirst / kMiddle / kLast 四种类型。
* 澄清一个误区：**WAL 与 MemTable 不是一一对应**。WAL 是全实例的写入流水账，多 Column Family 共享 WAL、各有 MemTable。
* WAL 的回收条件：对应数据已安全落成 SST 之后。持久性分级：**默认不 fsync**——进程崩溃可恢复，掉电可能丢最后一段写入；`WriteOptions::sync=true` 强制 fsync，用性能换持久性，按业务容忍度选。

5 SST 文件：BlockBasedTable 布局
------

从文件尾往头看（读文件也是这个顺序）：

```
┌────────────┐ ┌──────────────────────────────┐
│ Data Block │ │ Footer(固定长度) → 指向两级索引 │
│   × N      │ │ Meta Index Block              │
│            │ │ Index Block: 每块最后key+offset│
│            │ │ Stats Block                   │
│            │ │ Filter Block (Bloom)          │
└────────────┘ └──────────────────────────────┘
  文件头方向 ←                    → 文件尾
```

* Data Block 默认 **4KB**：内部 key 有序 + **前缀压缩**（存 shared/unshared 字节数与 key 增量）；**Restart Point 默认每 16 条一个**，该位置存完整 key，支撑块内二分查找。
* Index Block：每个 Data Block 的最后一个 key + 偏移。读取永远先读 Footer，再逐级定位。

6 读取：Bloom Filter 与 Block Cache
------

* Get 的查找顺序：活跃 MemTable → 各 Immutable → **L0（文件间 key 重叠，只能按新旧顺序逐个查）** → L1+（层内不重叠，二分定位）→ 最底层。L0 文件堆积是读慢的第一嫌疑人。
* **Bloom Filter**（Filter Block）：回答"一定不在/可能在"，`bits per key=10` 时假阳性约 1%——在"要不要打开这个 SST"这道题上先挡掉绝大多数无效 IO。
* **Block Cache**（LRU，也支持 Clock）：缓存单位是 Data Block；读一个 key 会加载整个 block，读多场景值得调大（如 512MB~1GB）。进阶：`cacheIndexAndFilterBlocks(true)` 把索引和过滤器也缓存；`pinL0FilterAndIndexBlocksInCache(true)` 钉住 L0/L1 的索引防淘汰。

7 Compaction：三种策略与背压
------

* 本质：多路归并 SST，顺手清掉被覆盖的旧版本和可回收的墓碑。
* **Leveled（默认）**：触发条件 L0 文件数 > `level0_file_num_compaction_trigger`（默认 4）或 L1+ 层总大小超阈值；过程是"选 L0 文件 → 找 L1 中 key 重叠的文件 → 归并出新文件"。层容量 `max_bytes_for_level_base=256MB`、倍数 10：L1 256MB / L2 2.56GB / L3 25.6GB……
* **Universal**：写放大更低、空间放大更大，适合写多读少；**FIFO**：只留最近数据、过期文件直接删，几乎零写放大，适合 TTL/缓存/时序。
* **写放大**要有体感：写 1 字节实际可能落盘几十上百字节——数据在层级间被反复重写。
* 背压两级：L0 文件到 `level0_slowdown_writes_trigger`（默认 20）**减速写**，到 `level0_stop_writes_trigger`（默认 36）**停写**。Compaction 跟不上时先抖写入，保护磁盘。

8 Column Family 与压缩配置
------

* CF 是多 key 命名空间：**共享 WAL，但各有独立 MemTable 与 SST**。收益：逻辑隔离、独立调参（compaction/压缩）、跨 CF 原子写（WriteBatch）、整 CF Drop（比逐 key 删快得多）。
* 压缩按层差异化是常见打法：L0/L1 不压（量小省 CPU）→ 中间层 LZ4（快）→ 最底层 Zstd（量最大、压缩比优先）。

9 调优速查：三个放大的平衡术
------

| 参数 | 默认值 |
|---|---|
| write_buffer_size | 64MB |
| MemTable 结构 | SkipList（O(log n)，支持有序遍历与并发） |
| WAL fsync | 关闭 |
| Data Block / Restart 间隔 | 4KB / 16 条 |
| Compaction | Leveled |
| L0 触发 / 减速 / 停写 | 4 / 20 / 36 个文件 |
| 层容量基线 / 倍数 | 256MB / 10 |

* 一句话哲学：**为写入而生，为读取而优化**。调优就是在写放大、读放大、空间放大之间按场景（写多/读多/点查/扫描/空间紧张）找平衡点。

面试追问链
------

* LSM 为什么写快？→ 随机写转顺序写：WAL 追加 + 内存 MemTable，攒批 Flush；代价是什么？→ 读放大（多层查找）、空间放大（旧版本）、写放大（Compaction 重写）；
* MemTable 写满会怎样？→ 转 Immutable 只读、新开活跃表，写不阻塞，Flush 交给后台；为什么选跳表？→ O(log n) 有序插入，天然支持 Flush 需要的有序输出且并发友好；
* Get 的查找顺序？→ MemTable → Immutable → L0 逐个 → L1+ 二分；L0 为什么特殊？→ Flush 直接产物，文件间 key 重叠，没法二分；
* 读慢怎么救？→ Bloom Filter 挡无效 SST + Block Cache + 控制 L0 文件数；Bloom 的假阳性呢？→ bits/key=10 约 1%，只会多一次 IO 不会漏；
* Leveled Compaction 怎么触发？→ L0 超 4 个文件或层大小超阈值；写打满了怎么办？→ 20 个文件减速、36 个停写的背压设计；
* JVM 里用 RocksDB 最大的坑？→ MemTable/Block Cache 是堆外内存，-Xmx 管不住，OOM 要按进程排查。

面试官视角小结
------

RocksDB 题的分层信号：能画出 WAL → MemTable → Immutable → SST 的主线并解释"顺序写"就算入门；能讲透 L0 与 L1+ 的读取差异、Bloom/Block Cache 的分工、三种 compaction 策略的选型，才算理解存储引擎；能主动提"三个放大的三角权衡"和 JVM 堆外内存的坑，说明有真实运维体感。这题本质考的是：候选人是把存储引擎当黑盒，还是理解每个机制背后那笔 IO 账。

> 来源：javadoop《RocksDB 架构设计与核心机制(1/2)》（https://javadoop.com/post/rocksdb-1）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
