Cassandra 核心架构与设计原理
====

一句话定位：**Dynamo 的分布式架构 + Bigtable 的存储模型**——去中心化的分布式 KV 系统，写吞吐线性扩展、跨地域多活、无单点。Apple 用它撑过 15 万节点数百 PB，Discord 每天经它写入数十亿条消息。后端要懂它的理由：它是"AP 系统做出什么样工程取舍"的最完整样本，一致性哈希、可调一致性、LSM 存储三大件在面试里都是高频考点。存储引擎细节与 [RocksDB架构设计一-LSM与存储引擎](./RocksDB架构设计一-LSM与存储引擎.md) 同源，单机 MySQL 的对照见 [MySQL索引详解](./MySQL索引详解.md)。

1 数据模型：Partition Key 决定一切
------

* 层次：Keyspace → Table → Row → Column，看起来像关系库，本质是分布式有序 KV。
* **Primary Key = Partition Key + Clustering Key**。前者决定数据落在哪个节点，后者决定**分区内**的排序。
* 复合 Partition Key：`PRIMARY KEY ((sensor_id, date), time)`——把 date 拼进分区键，用时间把大分区拆散，是治"单分区过大"的常规手段。
* 查询必须带 Partition Key，否则报错（或显式 `ALLOW FILTERING` 全节点扫）。因此建模思路与 MySQL 相反：**Query-Driven Modeling，先定查询再建表**，宁可按查询模式冗余多张表。
* 非主键查询的三条出路：二级索引（要广播全节点，性能差）、物化视图（换主键冗余一份）、**反范式化多表（社区最推荐）**。

2 去中心化架构：没有 Master 这回事
------

* 对等 P2P：所有节点平等，客户端连任意节点，该节点即充当本次请求的 **Coordinator** 负责路由。没有 ZooKeeper 类中心服务，也就没有中心故障面。
* **Gossip 协议**：每节点每秒随机挑几个节点交换集群状态（谁活着、负载如何、有没有新节点），几秒内传遍全集群。成员发现与故障检测全靠它。

```
client ──→ 任一节点(Coordinator) ──┬──→ 副本1
                                   ├──→ 副本2   (token 环上顺时针 N 个)
                                   └──→ 副本3
```

3 一致性哈希与虚拟节点
------

* Token 环范围 0 ~ 2^63-1。对 Partition Key 哈希得到 Token，落在哪个区间就归哪个节点；副本放在环上**顺时针连续的 N 个节点**。
* 相比普通取模，一致性哈希扩缩容只影响相邻节点，数据迁移量最小。
* **Vnode（虚拟节点）**：每个物理节点默认占环上 256 个小段。数据分布更均匀（异构机器也能按比例分摊），新节点加入时从**所有**老节点各迁一点，而不是压垮一个邻居。`nodetool status` 里看到的 Tokens: 256 就是它。

4 复制策略与多数据中心
------

* SimpleStrategy：`replication_factor = 3`，不管机架拓扑，测试用。
* **NetworkTopologyStrategy**：按数据中心分别指定副本数，如 `'dc-east': 3, 'dc-west': 3`——每个 DC 内独立走 token 环放副本，还要避开同机架。跨地域多活、就近读写就是靠它。

5 可调一致性：W + R > N
------

写级别与读级别各自可选，N 为副本数（以 3 为例）：

| 级别 | 写语义 | 读语义 |
|---|---|---|
| ONE | 1 副本落盘即返回 | 读 1 个副本 |
| QUORUM | 多数派（2/3）成功 | 读多数派取最新 |
| ALL | 全部副本 | 全部副本 |

* 强一致判据：**W + R > N**，读写集合必有交集，不会漏掉最新写入。N=3 时 QUORUM+QUORUM、ONE+ALL、ALL+ONE 都满足；ONE+ONE（2 < 3）就可能读到旧值。
* 生产常用 QUORUM/QUORUM；日志、计数类吞吐优先的场景可以放宽到 ONE，用最终一致换性能。

6 存储引擎：LSM 一套件
------

写路径与读路径（LSM 完整原理见 [RocksDB架构设计一](./RocksDB架构设计一-LSM与存储引擎.md)）：

```
写: CommitLog(WAL) → MemTable(跳表) ──flush──→ SSTable ──后台──→ Compaction
读: MemTable ∪ SSTable×N  多路归并取最新版本
```

* 写只有顺序日志追加 + 内存写，**没有随机 IO**，这就是"写吞吐线性扩展"的物理来源；读要查多层文件，读放大是 LSM 的固有代价。
* SSTable 一组文件：Data.db（数据）、Index.db（分区索引）、Summary.db（索引的采样索引）、Filter.db（Bloom Filter）、Statistics.db、TOC.txt（清单）。
* Compaction 三策略：
  - **STCS**（默认）：大小相近的 SSTable 互并，写多读少合适，但空间放大可能翻倍；
  - **LCS**：分层归并，层内 key 不重叠，读好、空间省、写放大高；
  - **TWCS**：按时间窗口（如 DAYS/1）分组，**TTL 到期整组直接删文件**，时序数据绝配。

7 删除为什么难：墓碑机制
------

* DELETE 不是删数据，而是写入一条**墓碑（tombstone）**标记，物理清理推迟到 Compaction。原因：分布式下直接物理删除，落后副本会通过 Read Repair / Repair 把旧数据"同步复活"回来——墓碑就是挡复活的证据。
* `gc_grace_seconds` 默认 **10 天**：墓碑至少保留这么久，保证所有修复机制都有机会见到它。
* 副作用：大量墓碑会拖慢读取，严重时抛 `TombstoneOverwhelmingException`。实践建议：**能用 TTL 就别用 DELETE**，TTL 数据配 TWCS 整文件回收，几乎不产生墓碑堆积。

8 三层一致性修复机制
------

* **Hinted Handoff**：副本临时不可用时，Coordinator 把写请求本地暂存（hint），副本恢复后补发；hint 默认只存 3 小时。
* **Read Repair**：QUORUM 读发现副本版本不一致，顺手后台把最新值推给落后副本。**只在被读的数据上生效**，冷数据没人读就永远修不上。
* **Anti-Entropy Repair**：`nodetool repair`，用 **Merkle Tree** 对账——叶子是各数据范围的哈希，逐层向上组合；根哈希相同即一致，不同就递归下钻，最终只传差异。重操作，要求**每个节点在 gc_grace_seconds（10 天）内至少跑一次**，生产用 Reaper 工具调度。

9 运维要点与适用边界
------

* 扩容：新节点启动自动 Bootstrap（Joining → Normal），`nodetool netstats` 看迁移进度；老节点随后要跑 `nodetool cleanup` 清掉已不属于自己（且不再会被修复到）的数据。全程不停机。
* 缩容：正常节点 `nodetool decommission`；宕机节点在其他节点 `nodetool removenode <host-id>`。
* 磁盘水位不超过 **50%**——Compaction 需要新旧两份空间；备份用 `nodetool snapshot`（硬链接实现，各节点都要执行）。
* 不擅长：JOIN、子查询、聚合（4.0 后有限支持）、ACID 事务（LWT 基于 Paxos，开销大不建议高频用）。
* 适合的判断式：**TB 级数据 + 高写吞吐 + 查询模式固定 + 要求极高可用 + 可接受最终一致**（时序/日志/消息收件箱/画像/跨地域 7×24）。GB 级数据上它是杀鸡用牛刀。

面试追问链
------

* Cassandra 怎么定位？→ Dynamo 架构 + Bigtable 存储，去中心化 AP 系统；为什么没有 Master？→ P2P 对等，任意节点当 Coordinator，Gossip 每秒交换状态做成员发现；
* 数据怎么分布？→ 对 Partition Key 一致性哈希到 token 环，副本取顺时针 N 个；vnode 解决什么？→ 数据倾斜与扩容压垮单邻居，默认 256 段；
* 怎么保证读到新值？→ 可调一致性，W+R>N 则读写集合有交集；QUORUM+QUORUM 为什么强？→ 2+2>3，多数派必交叠；
* 删除为什么要墓碑？→ 防修复机制把已删数据从落后副本同步"复活"；gc_grace_seconds 是什么？→ 墓碑最短保留 10 天，保证修复见得到它；
* 读放大怎么缓解？→ Bloom Filter 挡掉无效 SSTable + 合适的 compaction 策略；时序数据选什么策略？→ TWCS，TTL 到期整文件删；
* 三种修复机制各管什么？→ Hinted Handoff 管短暂宕机补写、Read Repair 管热数据顺手修、Anti-Entropy Repair 管 Merkle Tree 全量对账。

面试官视角小结
------

Cassandra 题的分层信号：能说出"去中心化 + 一致性哈希 + LSM"三件套算入门；能推导 W+R>N、讲清墓碑与 gc_grace_seconds 防数据复活的设计动机，才摸到分布式存储的门；能对答 vnode、NetworkTopologyStrategy 跨 DC 副本、三种 repair 的分工与 TWCS 选型，说明真碰过生产集群。最好的答案会主动给出不适用场景（小数据、复杂查询、强事务），而不是把它吹成万能库——知道边界比知道功能更显功力。

> 来源：javadoop《Cassandra 核心架构与设计原理》（https://javadoop.com/post/cassandra）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
