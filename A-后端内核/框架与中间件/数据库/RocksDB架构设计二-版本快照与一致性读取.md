RocksDB 架构设计二：版本、快照与一致性读取
====

系列第二篇。第一篇（[RocksDB架构设计一-LSM与存储引擎](./RocksDB架构设计一-LSM与存储引擎.md)）讲了 LSM 骨架，本篇回答一个更隐蔽的问题：**一次读取到底应该读到什么**——写入与删除并发进行时，迭代器扫出来的是哪个"时刻"的数据。Sequence Number、Internal Key、Snapshot、Compaction 清理时机、MANIFEST 恢复，看似零散的机制其实都在服务这一个问题。这也是 MVCC 思想在 KV 存储里的最小完整实现，与 MySQL 的 [MySQL锁详解](./MySQL锁详解.md) 里的隔离性问题是同一个母题。

1 从一个问题开始：扫描扫出"缝合怪"
------

* 场景：迭代器扫 `user:` 前缀的所有 key，耗时几秒，期间其他线程在写、在删。
* 风险：扫描前半段看到的是旧世界、后半段是新世界——**结果是多个时刻拼出来的缝合数据**。
* 解法：Snapshot——把"此时此刻"固定下来，整次扫描基于一个不变的视图。但前提是得能回答"这条记录属于我该看到的时刻吗"，这就需要版本号。

2 Sequence Number：给每条记录盖章
------

* 单调递增的**逻辑时钟**，不管墙上时间，只管先后。
* 示例：`Put(name,"javadoop")`、`Put(name,"mac")`、`Delete(name)`、`Put(name,"rocksdb")` 依次得到 seq=100/101/102/103。同 key 的四次操作变成四个版本并存。
* **分配时机**：进入写入路径时按 batch 整体分配连续序列。WriteBatch 三条操作拿到 k1→200、k2→201、k3→202，batch 内天然有序且原子。
* 写入主线：取最新 sequence → 给 batch 分配新序列 → 写 WAL → 写 MemTable → 推进全局最新 sequence。

3 Internal Key：真正用来排序的 key
------

* 格式：**Internal Key = user key + sequence number + value type**。type 主要是 kTypeValue（有值）与 kTypeDeletion（删除标记）。
* 为什么把 type 编进 key？因为 Delete 的语义是"**从这个版本起，该 key 视为不存在**"，它不是一次数据操作而是元数据声明——LSM 因此可以用"追加新版本"统一处理写与删，不需要原地修改。
* 排序规则三段式：**先按 user key 升序；同 user key 按 sequence 降序（新的靠前）；再按 type**。
* 好处：读取时**最新版本永远第一个碰到**；带版本上界的读取顺延即可；删除判断有统一规则。MemTable、SST、Compaction 多个模块复用同一套排序逻辑。

4 Snapshot：不复制数据的"时间戳"
------

* 本质：记录一个**可见版本上界** snapshot_seq，不复制任何数据。当前最新 seq=500 时建快照，之后即使发生 seq=501/502/503 的写入（包括 Delete），快照读只看得见 seq<=500 的世界。
* 判定逻辑：沿版本链下找，**第一条 seq <= snapshot_seq 的记录就是答案**。

* 用 name 的四个版本（seq=103 value="rocksdb" / 102 deletion / 101 "mac" / 100 "javadoop"）验证：

| snapshot_seq | 读到 | 原因 |
|---|---|---|
| 103 | rocksdb | 命中第一条 value |
| 102 | NotFound | 第一条即 deletion，直接判不存在 |
| 101 | mac | 跳过 103/102 |
| 100 | javadoop | 跳过 103/102/101 |

* 何时需要：单点 Get 不需要显式快照（RocksDB 自保证一致）；**长时间迭代扫描是典型场景**（`getSnapshot()` + `ReadOptions.setSnapshot()` + `newIterator()`）；多次相关读取要统一视图同理。快照的"读历史版本"只是副产品，固定读取视图才是本体。

5 Delete 与 Compaction：什么时候才敢真删
------

* 不立刻物理删除的原因：老快照可能还要旧值。例：`Put seq=100` → 建快照 A(上界 100) → `Delete seq=101`：最新读取见 name 不存在，但快照 A 必须仍读到 "javadoop"。
* tombstone 逻辑：**先语义正确（打标记），物理清理推迟到 Compaction**。
* Compaction 敢删的依据是三样信息：每条记录的 sequence、type、**当前最老存活快照的边界**。若 oldest_snapshot_seq=102，则 seq=100/101 的旧值与 seq=102 的删除标记统统不能动；最老快照推进到 103 之后才可清理。
* 一句话原则：**Compaction 删的不是"旧"，而是"对所有还活着的读取视图都不可见"的版本**。
* 运维提醒：**长期持有快照会拖住清理**——旧版本堆积、墓碑回收变慢、空间放大（类比数据库长事务拖住 MVCC 清理，见 [脏读、幻读、不可重复读区别](./脏读、幻读、不可重复读区别.md)）。快照用完尽快 release。

6 Get 主流程（把版本逻辑串起来）
------

```
① 确定可见上界 visible_seq（无快照 = 当前最新 sequence）
② 按 internal key 排序定位该 user key 的版本链（seq 大在前）
③ 跳过 sequence > visible_seq 的记录
④ 命中第一条 seq<=visible_seq 的记录，按 type 分流：
     kTypeValue    → 返回 value
     kTypeDeletion → 返回 NotFound（不再往下找）
```

7 Version / VersionSet / MANIFEST：文件视图管理
------

* 目录里有 000101.sst、000120.sst，不代表都在用：有的已被 Compaction 淘汰等待物理删除，有的在 L0、有的在 L2。**数据库的真实状态 = 一个"有效文件视图"**。
* **Version**：某一时刻的文件布局快照（如 L0: 000101/000102，L1: 000090，L2: 000070/000071）。每次 Compaction（删 000101、000090，加 000120）产生一个新 Version。
* **VersionSet**：管理所有 Version，维护三样状态：current Version（读和 Compaction 都看它）、下一个文件编号、最新 sequence。元数据变化统一走 LogAndApply：写 MANIFEST + 原子更新内存 current Version。
* **MANIFEST**：文件视图的变更日志（不是用户数据）。设计成日志而非每次全量重写，因为多数变更很小。类比：**WAL 管数据，MANIFEST 管数据库长什么样**。
* **VersionEdit**：一次变更的描述单元，Compaction 后形如：

```
DeleteFile(level=0, file=000101.sst)
DeleteFile(level=1, file=000090.sst)
AddFile(level=2, file=000120.sst, smallest=..., largest=...)
SetLastSequence(103)  SetLogNumber(56)
```

* **启动恢复链路**：找 CURRENT 文件 → 读当前 MANIFEST 文件名 → 顺序回放全部 VersionEdit → 内存重建 VersionSet 与 current Version → 确定有效 SST 集合 → 再结合 WAL 恢复未 Flush 的数据。要点：**只有 current Version 引用的 SST 才算数据库的一部分**，不是扫目录全收。

8 全篇收拢：一条主线
------

```
写入: batch 分配连续 seq ──→ WAL ──→ MemTable(按 internal key 排序)
读取: visible_seq(最新 seq 或 snapshot 上界)
      → 沿版本链跳过 seq>上界 → 首个命中按 type 分流 value / NotFound
清理: Compaction 按「最老存活 snapshot 边界」删对所有活视图不可见的版本
元数据: Compaction → VersionEdit → MANIFEST(LogAndApply) → 新 Version
```

* 原文点题：RocksDB 里看似互不相关的机制，本质都在解同一个问题——**如何管理版本、判定可见性**。这套模型同样能迁移去理解 MySQL MVCC 的 undo 版本链与 ReadView，属于存储系统的通用心智。

面试追问链
------

* 并发写下迭代器扫描为什么不一致？→ 扫描跨多个时刻，前后数据新旧混杂；怎么解？→ Snapshot 固定可见上界，整次读基于一个视图；
* Sequence Number 是什么？→ 单调递增逻辑时钟，按 batch 分配连续段；为什么按 batch？→ batch 原子性 + 序列连续可判定顺序；
* Internal Key 排序为什么要 seq 降序？→ 最新版本最先命中，读取顺延即得；Delete 为什么不物理删？→ 老快照可能还要旧值；
* Compaction 清理的判据？→ seq + type + 最老存活快照边界，删的是对所有活视图都不可见的版本；长持快照的后果？→ 拖住清理、空间放大，同长事务拖住 MVCC；
* MANIFEST 和 WAL 的分工？→ WAL 管数据未 Flush 部分，MANIFEST 管文件视图变更；启动恢复顺序？→ CURRENT → MANIFEST 回放 VersionEdit 重建 VersionSet → WAL 回放补数据。

面试官视角小结
------

这一篇是最能区分"用过 RocksDB"和"理解 RocksDB"的考题。能说出 Snapshot 存在的人不少，能解释"快照只是上界、不复制数据、靠 internal key 降序 + 上界过滤实现可见性判定"的才是真懂；能进一步把 Compaction 清理条件（最老存活快照）与"长快照拖空间"讲成因果、把 MANIFEST 说成"文件视图的 WAL"，基本可以确认候选人具备多版本存储的通用心智——这套模型在 MVCC、undo log、消息系统里反复出现。

> 来源：javadoop《RocksDB 架构设计与核心机制(2/2)》（https://javadoop.com/post/rocksdb-2）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
