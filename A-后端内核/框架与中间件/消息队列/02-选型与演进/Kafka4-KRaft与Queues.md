Kafka 4：KRaft-only 与 Queues for Kafka
====

Kafka 4.x 是消息队列面试的时代分水岭：4.0 起 KRaft-only，ZooKeeper 被彻底移除；4.2 起 Queues for Kafka（Share Groups，KIP-932）转 GA production-ready。一句话定位：**元数据自主管理 + 队列语义补全，Kafka 从「流日志」扩张为「流 + 队列」双模引擎**。面试里它考的是知识保鲜度——还在答「Kafka 元数据存 ZooKeeper」的候选人，与能讲 KRaft quorum、share group acknowledge 的人，差了一个代际。基础架构先看 [Kafka架构与高性能原理](./Kafka架构与高性能原理.md)，选型底盘看 [MQ选型对比-Kafka-RocketMQ-RabbitMQ](./MQ选型对比-Kafka-RocketMQ-RabbitMQ.md)。

1 Kafka 4 这两年改变了什么：KRaft-only 的运维账
------

先摆版本时间线，这是知识保鲜度的坐标轴：

| 版本 | 关键事件 |
| ---- | ---- |
| 2.8 | KRaft 模式首次引入（预览） |
| 3.3 | KRaft 生产可用 |
| 3.4 ~ 3.9 | 提供 ZK → KRaft 迁移路径（迁移收尾前可中止回退） |
| 4.0 | KRaft-only：ZooKeeper 模式代码彻底移除 |
| 4.2.0（2026-02-17 发布） | Queues for Kafka（Share Groups，KIP-932）转 GA，production-ready |

运维账的三个变化：

* **少一个分布式组件**：不再维护 ZooKeeper ensemble，部署面、监控面、升级面各减一块——分布式系统里「删组件」几乎等于「删故障面」；
* **元数据自主管理**：元数据从「外置 ZK + controller 二次推送」变为 Kafka 自己的 Raft 日志。controller failover 从分钟级降到秒级，分区规模上限同步抬升。
* **迁移不可逆**：4.0 起没有降级回 ZK 模式的路——要么留在 3.x，要么彻底进 KRaft。这是一扇单向门，升级决策必须带着「回滚预案不可用」的自觉去做。

还有一条容易被忽略：**新建集群只剩一种形态**。过去面试爱考的「ZK 模式与 KRaft 模式对比」，2026 年的正确姿势是讲「Kafka 凭什么敢把 ZooKeeper 删掉」——答案在下一节的 quorum 与 metadata log 里。

2 KRaft 架构要点：quorum、metadata log 与 listener 分离
------

KRaft（Kafka Raft）= 用内嵌的 Raft 变体管理元数据，替代 ZooKeeper。三个要点：

* **quorum 协议**：controller 节点（奇数个，通常 3 或 5）组成 Raft 组，多数派选举出 active controller。它是 Raft 的工程变体——**元数据日志本身就是 Raft 日志**，不需要在 Kafka 里再叠一层独立共识日志。非 controller 角色的 broker 以 observer 身份拉取元数据，不参与投票。
* **metadata log**：一条内部日志（`__cluster_metadata`）承载全部元数据（broker 注册、topic/分区/ISR、配置、ACL）。只由 active controller **单写**，controller 组多数派确认后提交。broker 像消费者一样从日志尾部增量 fetch。日志定期生成 **snapshot**，新加入或落后太多的节点先加载快照再追日志，日志因此可截断、不无限增长。
* **controller/listener 分离**：`process.roles` 声明进程角色（`broker,controller` 合并部署适合中小集群，controller 独立进程适合大集群）。controller 间流量走独立端口（`controller.listener.names`），与客户端流量物理隔离，网络策略可分开治理。

```text
            Controller 组（Raft quorum，3 个 voter，多数派提交）
   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
   │ Controller-1  │    │ Controller-2  │    │ Controller-3  │
   │ active：单写  │◄───┼──元数据日志复制┼───►│ follower      │
   └──────┬───────┘    └──────────────┘    └──────────────┘
          │  __cluster_metadata（元数据日志 + 定期 snapshot）
          ▼  broker 从尾部增量 fetch
   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
   │   Broker A    │    │   Broker B    │    │   Broker C    │
   │ (可兼任 ctrl) │    │               │    │               │
   └──────────────┘    └──────────────┘    └──────────────┘
```

与 ZK 时代的关键差异：

| 维度 | ZK 模式（3.x 及以前） | KRaft 模式（4.x 唯一形态） |
| ---- | ---- | ---- |
| 组件 | Kafka + ZooKeeper 两套集群 | Kafka 单套自包含 |
| 元数据真相 | ZK znode + controller 内存再推送 | metadata log 单一事实源 |
| controller failover | 分钟级（新 controller 重建全量状态） | 秒级（follower 已有日志，追上即切换） |
| 分区规模 | 十万级开始吃力 | 面向百万级分区设计 |
| 部署 | 先搭 ZK 再搭 Kafka | 单二进制，`process.roles` 定角色 |

两个高频陷阱。其一，KRaft 移除的是「元数据外置」，**不是没有 controller**——controller 还在，只是从「ZK 的客户端」变成了「Raft 组的参与者」。其二，KRaft 不改变数据面的 ISR/副本机制（见 [Kafka架构与高性能原理](./Kafka架构与高性能原理.md)）。元数据共识与数据副本共识是两回事，别混为一谈。

3 Queues for Kafka：Share Groups 补上队列语义
------

**为什么 Kafka 也要「队列语义」**——consumer group 有三个天生限制：

* **消费者数 ≤ 分区数**：分区独占消费，消费者多于分区就有人闲置。任务队列想临时加机器扛积压，只能先扩分区，而扩分区破坏 key 顺序、放大运维成本。
* **提交粒度是位移不是消息**：ack 的单位是 offset 批次。单条消息处理失败要么整批重拉、要么业务自建重试/死信（可靠性姿势见 [消息不丢失与不重复](./消息不丢失与不重复.md)）。
* **rebalance 代价重**：消费者上下线、长任务超过 `max.poll.interval.ms` 都可能触发整组 STW，worker 池频繁伸缩的任务队列场景尤其痛。

**KIP-932 核心（4.2 GA）**：

* **share group**：消费者不再独占分区，broker 端新增的 share coordinator 把消息按批分发给组内任意空闲消费者。消费者数与分区数彻底解耦，扩 worker 不动分区。
* **acknowledge 三态**：`ACCEPT`（处理完成）、`RELEASE`（放回队列稍后重投）、`REJECT`（送入该组的死信 topic）。同一消息投递次数超过上限（`share.delivery.count.limit`，默认 5）自动进死信——重试与死信第一次成为 Kafka 原生语义。
* **没有 rebalance**：消费者以 share session 与 coordinator 保持心跳。单个消费者掉线只影响它持有的未确认消息（获取锁超时后自动放回重投），其余消费者无感知。对比 consumer group 掉线触发整组再平衡，这是任务队列最想要的性质。
* **交付语义 at-least-once**：重投机制决定了同一条消息可能被处理多次，**消费端必须幂等**。这是队列语义的标配代价，不是缺陷。
* **与 purge/位移的关系**：消费确认仍然不删日志，retention/segment 过期模型不变。ack 推进 share group 的 checkpoint 位移（分区内前序消息全部确认才推进），推进后服务端 share 状态可清理。「读不删」的日志模型与「处理完即完」的队列语义在这里缝合。

| 维度 | Consumer Group | Share Group（KIP-932） |
| ---- | ---- | ---- |
| 消费模型 | 分区独占，客户端 pull | 服务端分发，任意消费者处理任意消息 |
| 消费者数上限 | 分区数 | 无此耦合 |
| 确认单位 | offset 提交（批次粒度） | 单条消息 acknowledge |
| 失败处理 | 业务自建重试/死信 | RELEASE 重投 / REJECT 死信，超限自动死信 |
| rebalance | 有（STW 风险） | 无（share session 逐消费者管理） |
| 顺序性 | 分区内严格 FIFO（[顺序消息](./顺序消息.md)） | 投递保持分区序，处理完成顺序不保证 |
| 适合 | 流处理、分区内状态聚合 | 任务队列、WorkQueue、事件驱动 |

客户端是新 API（KafkaShareConsumer），语义示意：

```java
// 4.2 新客户端 API，语义示意（方法形态以官方客户端为准）
try (var consumer = new KafkaShareConsumer<>(props)) {
    consumer.subscribe(List.of("orders-to-ship"));
    while (running) {
        for (var record : consumer.poll(Duration.ofSeconds(1))) {
            try {
                handle(record);                    // 必须幂等：at-least-once
                ack(consumer, record, ACCEPT);     // 处理完成
            } catch (RetryableException e) {
                ack(consumer, record, RELEASE);    // 可重试失败，稍后重投
            } catch (NonRetryableException e) {
                ack(consumer, record, REJECT);     // 不可恢复，进死信
            }
        }
    }
}
```

**适用场景**：任务队列 / WorkQueue（履约、图片处理、通知发送——要 per-message ack + 重试 + 死信的负载）。命令侧的事件驱动微服务（处理完确认，而非流式聚合）。积压驱动的弹性 worker 池（worker 数随积压伸缩，不受分区数限制、不触发 rebalance）。

**不适用场景**：流处理与窗口聚合（分区内状态，用 consumer group + Kafka Streams）。强顺序业务（完成顺序不保证，顺序姿势见 [顺序消息](./顺序消息.md)）。超大规模镜像/管道类负载（share coordinator 分发路径比分区独占直读重）。官方定位是补队列语义，不是替代 consumer group。

云托管跟进是 GA 成色的佐证：Confluent Cloud 与 AWS MSK（含 Express brokers）均已支持 Queues for Kafka。先在云上小规模验证再自建，是低风险路径。

4 2026 格局重估：Kafka、RabbitMQ 与 RocketMQ 的地盘再划分
------

Kafka 侵蚀了谁：

* RabbitMQ 的「任务队列 + ack + 死信 + 弹性 worker」场景，被 share group 一比一对上。**已有 Kafka 的团队不再需要为队列语义多养一套 RabbitMQ**。「少一个中间件」在运维账上极其值钱；
* 轻量自建队列（Redis Streams 一类）在「已有 Kafka」的团队里进一步边缘化。

RocketMQ 的反击：

* RocketMQ 5.5.0（2026-04）推出 **Lite 订阅模式**，配合 5.x 存算分离与低时延改造，主打低时延点对点与轻量订阅。美团这类团队已有 5.x 低时延生产实践；
* 传统强项未失守：事务消息、定时/延迟消息、内建重试队列，Kafka 至今没有原生对等物。对比见 [MQ选型对比-Kafka-RocketMQ-RabbitMQ](./MQ选型对比-Kafka-RocketMQ-RabbitMQ.md)；
* 国内微服务存量盘还有栈惯性：Nacos 3.2.x 稳定线 + RocketMQ 5.x + Sentinel 的组合依然是默认解。

2026 版决策表：

| 场景 | 2024 常见答案 | 2026 更新 |
| ---- | ---- | ---- |
| 高吞吐事件流 / 日志 / 埋点 | Kafka | 不变 |
| 任务队列 / WorkQueue（ack+重试+死信） | RabbitMQ / RocketMQ | 已有 Kafka → share group；绿地轻量 → RabbitMQ 仍最省 |
| 低时延交易链路点对点 | RocketMQ | RocketMQ 5.x（Lite 订阅主打低时延） |
| 事务消息 / 延迟消息 | RocketMQ | 不变（Kafka 无原生对等） |
| 多语言 / 复杂路由 / 低运维小团队 | RabbitMQ | 不变 |
| 流处理 / 事件溯源 | Kafka（+Flink/Streams） | 不变，且别用 share group |

一句话：**Kafka 的地盘从「流」扩到「流 + 队列」，RabbitMQ 退守「轻量、多语言、复杂路由」，RocketMQ 靠 5.x 低时延与事务/延迟消息守盘**。

5 真实参照：16 容器里 RocketMQ 5.3.1 在跑，业务却走 NoopEventPublisher
------

一个真实的反例，来自作者自己的家庭项目（16 容器）：

* **容器层**：RocketMQ 5.3.1 的 NameServer / Broker / Dashboard 一直在跑——保留部署熟练度、验证 5.x 行为，这部分成本认了；
* **业务层**：事件发布是 **NoopEventPublisher——事件先写数据库表，一条都不进 MQ**。

为什么停用业务侧 MQ 语义，权衡是真实的：

* **MQ 是运维成本**：容器、磁盘水位、堆积告警、版本升级、故障演练，每一项都是持续开销。家庭项目的人力是 1，中间件数量必须省着用；
* **事务事件没有消费方**：领域事件落库后没有任何下游订阅，此时接 MQ 等于为「架构完整性」付费。一条消息都不会被消费，队列语义（ack / 重试 / 死信）全部空转；
* **事件先落库 = Outbox 的前半段**：事件表天然是 transactional outbox 的存储层。未来出现真实消费方时，把「扫表投递」接上、Publisher 从 Noop 换成真实现即可，演进路径平滑，不需要返工。

什么时候该真上 MQ（引入时机判据）：

* 出现两个以上需要解耦 / fan-out 的真实消费方；
* 有明确削峰填谷诉求（上游洪峰、下游处理慢，且积压有业务代价）；
* 需要跨服务异步通知，而不是同库内联动。

这段反面教材的面试价值：**中间件不是架构完整性的装饰品，而是「有消费方、有解耦收益」之后才落的地板**。能讲清「我们为什么暂时不上 MQ」的候选人，比「全家桶都接上」的候选人可信得多——后者往往说不清每条链路的消费方是谁。

面试追问链
------

* KRaft 是什么？为什么 Kafka 要去掉 ZooKeeper？→ 元数据从外置组件回归内嵌 Raft 日志：少一个组件、failover 秒级、分区规模上限抬升；
* KRaft 的 quorum 怎么工作？→ 奇数个 controller 组成 Raft 组，多数派选主，active controller 单写 metadata log，其余 broker 增量 fetch；
* metadata log 无限增长怎么办？→ 定期 snapshot，新节点/落后节点先加载快照再追日志；
* 4.0 之后还能退回 ZK 模式吗？→ 不能，ZK 代码已移除，升级是单向门；
* share group 与 consumer group 的区别？→ 分区独占 vs 服务端分发；offset 批次提交 vs 单条 acknowledge；掉线触发 rebalance vs share session 逐个管理；
* acknowledge 有哪几态？→ ACCEPT / RELEASE / REJECT，投递超限（默认 5 次）自动进死信；
* share group 的交付语义？→ at-least-once，消费端必须幂等，姿势同 [消息不丢失与不重复](./消息不丢失与不重复.md)；
* ack 之后消息会删除吗？→ 不会，retention 模型不变；ack 只推进 checkpoint 位移、让 share 状态可清理；
* 哪些场景不该用 share group？→ 流处理聚合、强顺序业务——分区独占 + offset 才是对的抽象；
* 2026 年任务队列选型怎么答？→ 已有 Kafka 用 share group；绿地轻量用 RabbitMQ；低时延/事务/延迟消息看 RocketMQ 5.x。

面试官视角小结
------

这道题分层清晰：知道「Kafka 去掉了 ZooKeeper」只算及格。能讲清 quorum 单写、metadata log + snapshot、controller/listener 分离，说明读过架构演进。能主动对比 share group 的 acknowledge 三态、幂等代价、与 retention/purge 的关系。并给出 2026 版选型格局（Kafka 蚕食任务队列、RocketMQ 5.x 低时延守盘），是知识新鲜的信号。

最高档是能结合自己项目讲「MQ 引入时机」的真实权衡——比如事件先落库 + NoopEventPublisher，等有消费方再接真 MQ。这说明中间件是拿来解决问题的，不是拿来填简历的。深挖依次见 [Kafka架构与高性能原理](./Kafka架构与高性能原理.md) 与 [MQ选型对比-Kafka-RocketMQ-RabbitMQ](./MQ选型对比-Kafka-RocketMQ-RabbitMQ.md)。

> 2026-10 新增（2026 技术栈补全）。
