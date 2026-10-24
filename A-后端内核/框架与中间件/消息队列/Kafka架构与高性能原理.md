看在前面
====

Kafka 是消息队列面试的绝对主角。原因：它同时踩中三个高频考点：

* **高吞吐架构设计**（分区、顺序写、零拷贝）
* **分布式一致性权衡**（ISR、HW/LEO）
* **大规模运维演进**（ZooKeeper 到 KRaft）

面试官问「你了解 Kafka 吗」，真正想听的是你能不能把这三层串成一条线。

本篇先搭整体架构，再逐层拆高性能四件套，最后讲 Consumer Group 与 rebalance、KRaft 演进。可靠性与顺序问题见后续：[消息不丢失与不重复](消息不丢失与不重复.md)、[顺序消息](顺序消息.md)。

整体架构：一张图记住 Kafka
====

```text
                          ┌────────────────────────────────────────────┐
                          │              Broker 集群（3 台）             │
   Producer ──push───────►│  Broker0        Broker1        Broker2     │
  （批量+压缩）            │ ┌────────────┐ ┌────────────┐ ┌──────────┐ │
                          │ │ P0-L(Leader)│ │ P0-F(Follower)│ P0-F    │ │
                          │ │ P1-F        │ │ P1-L        │ │ P1-F    │ │
                          │ │ P2-F        │ │ P2-F        │ │ P2-L    │ │
                          │ └────────────┘ └────────────┘ └──────────┘ │
                          │      ▲ pull         ▲ pull        ▲ pull   │
                          └──────┼───────────────┼─────────────┼───────┘
                                 │               │             │
                          ┌──────┴───────┐ ┌─────┴─────┐ ┌─────┴─────┐
                          │ Consumer A1  │ │ Consumer  │ │ Consumer  │
                          │              │ │   B1      │ │   C1      │
                          │ Partition 0  │ │ Partition │ │ Partition │
                          │ Partition 1  │ │   2       │ │ （独立组） │
                          └──────────────┘ └───────────┘ └───────────┘
                                Group A        Group B      Group C
```

几个关键角色一句话说清：

* **Producer**：消息生产者，往某个 topic 的某个分区写消息，负责路由（按 key 哈希或轮询）。
* **Broker**：Kafka 服务节点，本身无状态化的设计趋势（元数据外置/内置），消息以日志文件形式落盘。
* **Topic / Partition**：topic 是逻辑分类，partition 是物理分片，也是**并行度的最小单位**。
* **Consumer Group**：组内分区独占消费、组间互不干扰，是 Kafka 实现「发布订阅」和「队列」两种模型的统一抽象。
* **ZooKeeper / KRaft**：早期存元数据、选 controller；2.8 之后 KRaft 逐步取代。

Topic、Partition 与副本机制
====

为什么要有分区
------

单机吞吐有上限，Kafka 的答案是**水平拆分**。一个 topic 拆成 N 个 partition，分布在不同 broker 上，读写并行度立刻翻 N 倍。同时分区也是**保证顺序的最小单位**：只保证分区内 FIFO，不保证全局有序。这是刻意设计——全局有序意味着退化为单队列，吞吐崩塌。详见 [顺序消息](顺序消息.md)。

每个分区物理上是一个**提交日志（append-only log）**：

```text
topic-order, partition-0 （磁盘上的 segment 文件序列）
┌─────────────────────────────────────────────────────────┐
│ 00000000000000000000.log   offset 0 ~ 9999              │
│ 00000000000000100000.log   offset 10000 ~ 19999 (活跃)  │
│ 00000000000000200000.index / .timeindex  (稀疏索引)      │
└─────────────────────────────────────────────────────────┘
消息不可变（immutable），删除按 segment 为单位整体过期
```

副本（Replica）与 Leader/Follower
------

每个 partition 有 1 个 leader 副本 + N-1 个 follower 副本。**读写都只走 leader**。follower 唯一的工作是从 leader 拉取数据并同步。这是「pull 同步 + 读写分离但只分离写冗余」的独特设计，对比 RabbitMQ 的镜像队列是推送复制。

* 所有副本统称 AR（Assigned Replicas）。
* Follower 落后太多会被踢出 ISR，临时下线又追回来的放进 OSR。
* AR = ISR + OSR。

ISR：Kafka 一致性与可用性的核心权衡
====

**ISR（In-Sync Replicas，同步副本集合）**是理解 Kafka 的钥匙。它不是「和 leader 时刻一致」的副本集合，而是「落后程度在阈值内」的副本集合：

* `replica.lag.time.max.ms`（默认 30s）：follower 在该时间内没有追上 leader 的 log-end-offset，就被踢出 ISR。
* 注意新版 Kafka 已**移除按消息条数（replica.lag.max.messages）判断**——因为流量突增时正常 follower 会被误杀，引发抖动。

两个重要水位：

```text
LEO (Log End Offset)      ：每个副本日志的下一条待写位置
HW  (High Watermark)      ：ISR 中最小 LEO，消费者只能读到 HW 之前
                            → 保证消费者永远读不到「可能未同步完成」的消息

Leader:   [0 ... 10]  LEO=11,  HW=9
FollowerA:[0 ... 10]  LEO=11  ┐ 都在 ISR，HW 取 min(LEO)
FollowerB:[0 ... 8 ]  LEO=9   ┘
```

配合两个参数就得到了 Kafka 的 durability 承诺（详见 [消息不丢失与不重复](消息不丢失与不重复.md)）：

```properties
# topic 级别：至少 2 个副本在 ISR 时才允许写入 acks=all 的消息
min.insync.replicas=2
replication.factor=3
# 不允许 OSR 中的落后副本当选 leader（可能丢数据，换可用性）
unclean.leader.election.enable=false
```

高性能四件套
====

Kafka 单机百万级吞吐不是魔法，是四个工程决策的叠加。

1. 顺序写（Sequential I/O）
------

磁盘顺序写速度接近内存随机写。普通机械盘顺序写 ~100 MB/s+，随机写可能不到 1 MB/s；SSD 上差距缩小但依然显著。Kafka 的分区是 append-only log，**只追加不修改**，天然把随机写变成了顺序写。消费者靠 offset 直接定位，不需要索引重建。

2. 页缓存（Page Cache）
------

Kafka **不自己管缓存，全靠操作系统页缓存**。写入先进 page cache，由 OS 异步刷盘。热数据留在 page cache 里，消费者读最新消息几乎不触盘。

* 好处一：进程重启缓存不丢（还在 OS 里）。32 G 内存的机器可以放心给 JVM 只分配 6 G 左右堆，剩下全留给 page cache。
* 好处二：避免 JVM GC 管理大对象的开销与停顿。
* 代价：机器断电丢页缓存。所以 Kafka 的持久性依赖**多副本**而不是单机刷盘（`log.flush.interval.messages` 一般不去手动调，交给副本机制兜底）。

3. 零拷贝（sendfile）
------

传统读取一条消息发给消费者要 4 次拷贝、4 次上下文切换：

```text
传统路径：磁盘 → 内核缓冲 → 用户空间(producer/consumer API 处理) → socket 缓冲 → 网卡
                    ①             ②                        ③            ④

零拷贝路径（sendfile 系统调用）：
磁盘 → 内核页缓存 ════════════════════════════════► 网卡
          （数据全程不进入用户空间，仅拷贝描述符/偏移）
```

Java 层面就是 `FileChannel.transferTo()`。消费者消费的历史消息是**不可变文件**，不需要应用层加工，完美契合 sendfile 的使用条件。这也是为什么建议**不在 consumer 端做消息体加工再转发**的架构——一旦进用户空间，零拷贝就没了。

4. 批量 + 压缩
------

* Producer 攒批：`batch.size`（字节数）+ `linger.ms`（最多等多久）二选一触发发送，把 N 次网络 RPC 合并成 1 次。
* 整批压缩（lz4/zstd/snappy/gzip），broker **原样存储压缩批次**，不解压，消费者拉走再解压，CPU 换带宽。

```java
props.put(ProducerConfig.BATCH_SIZE_CONFIG, 64 * 1024);   // 64KB
props.put(ProducerConfig.LINGER_MS_CONFIG, 5);            // 等 5ms 攒批
props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
```

一句话总结四件套：**顺序写解决写得快，页缓存解决读得快。零拷贝解决传输快，批量压缩解决网络省**。

Consumer Group 与 Rebalance
====

消费模型
------

* 组内：一个 partition 同一时刻只被组内一个 consumer 消费（独占），组内消费者数超过分区数时多余的消费者闲置。
* 组间：每个 group 独立消费全量消息，互不影响。同一个 topic，A 组当队列用（组内分摊），B 组当广播用（多组各拿一份）。

Rebalance 的触发与代价
------

三种触发条件：组成员变化（上下线、crash）、订阅 topic 数变化、topic 分区数扩容。

代价非常重：

1. **Stop The World**：rebalance 期间整组**所有**消费者停止消费。对比 RocketMQ 只迁移涉及队列，粒度更细。
2. 频繁误伤：早期靠 consumer 心跳超时（`session.timeout.ms`）判死，GC 停顿就可能引发连锁 rebalance。

```properties
session.timeout.ms=10000     # 心跳会话超时
heartbeat.interval.ms=3000   # 心跳间隔（一般设为超时的 1/3）
max.poll.interval.ms=300000  # 两次 poll 最大间隔，处理太慢会被踢出组
```

演进版本要能说出来：

* **0.9 之前**：基于 ZooKeeper watch。
* **0.9~2.4**：由 coordinator 主导（GroupCoordinator 在 broker 端，两阶段 JoinGroup/SyncGroup）。
* **2.4+**：引入增量协作式 rebalance（CooperativeStickyAssignor），只迁移需要变动的分区，不再整组暂停。

这是能体现知识新的加分点。

从 ZooKeeper 到 KRaft
====

ZooKeeper 模式的问题：元数据（broker 注册、controller 选举、ISR 变更）存 ZK，controller 要把变更**再同步回各 broker**。两层存储扩容/故障恢复都慢。分区数上十万级时 controller 故障切换要分钟级。

KRaft（Kafka Raft）模式：

* broker 自己组成 Raft 组，元数据作为一条**元数据日志**（`__cluster_metadata`）复制，quorum 选举 controller。
* 元数据变更走日志，broker 从日志尾部增量拉取，controller failover 从分钟级降到秒级。
* 少维护一个 ZK 组件，部署面减一。Kafka 3.3 生产可用，**4.0 起移除 ZK 模式**。

```text
旧：ZK ◄──► Controller ──推送元数据──► Brokers     （两套存储，切换慢）
新：Brokers(含 Controller 候选) ──Raft 复制元数据日志──► 仲裁选主，秒级切换
```

追问链
====

**Q1：为什么 Kafka 读写都走 leader，不让 follower 分担读？**

 follower 的数据可能落后，读 follower 会破坏单调一致性（同一条消息可能被消费两次或漏读）。Kafka 选择吞吐靠分区横向扩展而不是读写分离。新版有 ` RackAwareView`/follower fetching 的实验能力，但默认仍是 leader 读。

**Q2：HW 和 LEO 在消费者眼里有什么区别？**

消费者只能读到 HW 以下的消息。leader 收到数据但 ISR 未同步完时 LEO 前进、HW 不动，这条消息对消费者「不存在」，这是不丢语义的一环。

**Q3：分区数是不是越多越好？**

不是。分区越多单机吞吐越好，但：

* leader 副本的选举/rebalance 时间变长。
* broker 端打开的文件句柄和内存占用（每个分区一套索引）上升。
* 端到端延迟反而可能因小批次恶化。

经验值：单机 2000~4000 分区。集群分区数不超过 4 万时，先做容量评估。

**Q4：`linger.ms=0` 还值得开批量吗？**

值得，`batch.size` 仍然会把同一时刻发出的多条消息合并。`linger` 是主动等，`batch` 是被动合。

**Q5：Kafka 为什么不用 mmap 而用 sendfile？**

mmap 适合小文件小块写（索引用 mmap）。日志段读取用 sendfile，免去了用户态映射管理的复杂度与缺页开销。两者 Kafka 都用了：**索引 mmap、日志传输 sendfile**。

面试官视角小结
====

* 答架构先给「topic-partition-副本」三层，再给 ISR，最后给四件套。这个顺序体现的是**存储模型 → 一致性 → 性能**的完整认知，比上来就背「Kafka 快是因为顺序写」高一档。
* ISR 必须能推导：为什么 HW 取 ISR 最小 LEO、`min.insync.replicas` 和 `acks=all` 为什么必须成对出现（见 [消息不丢失与不重复](消息不丢失与不重复.md)）。
* rebalance 要能说出代价（STW）和演进（cooperative incremental），只说「重新分配分区」等于没读过源码级资料。
* KRaft 是 2024 后的新趋势，答出来直接和只会 ZK 时代的候选人拉开代差。
* 高性能四件套每个都要能落到「它解决了哪个硬件瓶颈」。磁盘随机慢→顺序写、内存贵→页缓存。内核态切换贵→零拷贝、网络贵→批量压缩。

> 2026-10 新增（P0 补全）。
