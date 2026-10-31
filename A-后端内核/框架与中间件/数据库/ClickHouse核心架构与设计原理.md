ClickHouse 核心架构与设计原理
====

列式 OLAP 数据库的代表，"单机 SSD、十亿行、简单聚合几十到几百毫秒"是它给自己划的性能圈。后端工程师撞见它的场景越来越多：报表、埋点、日志与 metrics 存储都在向它聚集（库内 [SigNoz架构分析](../../../C-架构/DDD与方法论/方法论/SigNoz架构分析-开源可观测平台.md) 就是拿它当存储底座）。它的所有设计围绕一个取舍：**把分析查询做到极致，主动放弃 OLTP 能力**。理解这套取舍比记参数更重要；列式存储的共同背景见 [Parquet深入理解-总起](./Parquet深入理解-总起.md)，B+ 树的对照见 [MySQL索引详解](./MySQL索引详解.md)。

1 它是什么：为聚合分析而生的列式数据库
------

* Yandex 出品，2016 年开源，最初支撑 Yandex.Metrica 的海量点击流分析。定位是**开源列式 OLAP 数据库**。
* 性能量级：单机 SSD、十亿行数据，简单聚合几十到几百毫秒，GROUP BY 几百毫秒到几秒——**不搭分布式集群也能先跑起来**。
* 反面清单同样要背：不擅长单行增删改查、无事务、点查弱、JOIN 能力有限（推荐大宽表）。UPDATE/DELETE 是重量级 Mutation（异步重写整个受影响数据块），不是常规操作手段。

2 MergeTree：存储引擎的主干
------

名字来自 LSM-Tree 的思想（LSM 背景见 [RocksDB架构设计一-LSM与存储引擎](./RocksDB架构设计一-LSM与存储引擎.md)）：**每次写入生成新 Part，后台不断把小 Part 归并成大 Part**。绝大多数实用表引擎都是 MergeTree 家族。

存储层级自上而下：

```
Table
 └─ Partition   分区（PARTITION BY 定义）
     └─ Part    一次写入落盘的物理单元，后台持续归并
         └─ Granule   默认 8192 行，读取的最小单位
```

* 每个 Part 目录内**每列一组文件**：`.bin`（压缩后的列数据）、`.mrk2`（Granule 偏移量索引）、`primary.idx`（稀疏主键索引），外加 count/columns/checksums 等元信息文件。
* 读取按列走文件：稀疏索引定位 Granule → 查 `.mrk2` 拿偏移 → seek 读出并解压。查询没涉及的列一个字节都不碰——这是列存省 IO 的物理来源。

3 稀疏索引：不是 B+ 树，是"每 8192 行记一个目录项"
------

* MySQL 的 B+ 树索引进于点查（见 [MySQL索引原理与面试要点](./MySQL索引原理与面试要点.md)）；ClickHouse 反过来：**每个 Granule 的边界记一次主键值**，一亿行只有约 1.2 万个索引项，整棵索引约 **95KB，常驻内存毫无压力**。
* 查询时在内存索引上二分定位起始 Granule，然后顺序扫描。定位粒度粗（8192 行），但分析查询本来就要扫大量行，粗粒度换来的是索引极小、写入极快。
* ORDER BY 即主键，**组合主键遵循最左前缀原则**：`(a, b)` 的索引对只按 b 过滤的查询基本无效。建表时排序键的选择直接决定查询性能。

4 分区、TTL 与跳数索引
------

* PARTITION BY 通常按月/日切。查询条件命中分区时**整个分区直接跳过**，连索引都不用查。分区数建议控制在几十到几百：太细会导致 Part 爆炸、归并压力大。
* TTL：如 metrics 表 `TTL create_time + INTERVAL 14 DAY DELETE`。**TTL 周期与分区粒度对齐效果最好**——整分区过期时可以直接 drop 掉整个 Part，代价极低。还支持分层存储：热数据 SSD、冷数据机械盘、超期删除。
* 跳数索引（Data Skipping Index）服务**非排序键列**的过滤：按 GRANULARITY 把若干 Granule 组成一个索引块，块内记摘要信息，查询时先看摘要决定跳过哪些块。常用类型：
  - `minmax`：记录块内最小/最大值，适合数值范围过滤；
  - `set(N)`：记录块内出现过的值，适合低基数等值；
  - `bloom_filter`：高基数等值列；`tokenbf_v1`/`ngrambf_v1` 面向文本匹配。
* 跳数索引的效果依赖数据的物理分布（数据越按该列有序越有效），GRANULARITY 建议 2~8。

5 写入与读取的全链路
------

* INSERT 流程：按分区分组 → 组内按排序键排序 → 按列压缩 → 生成稀疏索引与 mark → 落盘成 Part。**原子写入，没有 WAL**——因为 Part 本身就是一次完整的批量提交，要么可见要么不可见。
* 后台线程持续归并排序小 Part。写入侧的最佳实践：**每次批量几千到几万行，频率别超过每秒一次**——小批量高频写入会产生海量小 Part，把归并打爆。

* 一次查询的八步流水线：

```
分区裁剪 → 主键稀疏索引过滤 → 跳数索引过滤 → 列裁剪
   → 读取并解压 Granule → 精确过滤(WHERE) → 聚合 → 返回
```

* 前四步都在做同一件事：**让第五步尽量少读**。一句话总结 ClickHouse 快的原因："它读得特别少"。

6 压缩与向量化执行
------

* `.bin` 内部按压缩块组织：`方法 1B + 压缩后大小 4B + 原始大小 4B + 数据`。默认 LZ4，可换 ZSTD（压缩比更高、速度略慢），还能按列指定 `CODEC(Delta, LZ4)` 这类组合编码。日志场景压缩比常见 **10:1 到 20:1**。
* 向量化执行：不逐行解释执行，而是**一批 8192 个值一起算**，配合 CPU 缓存友好 + SIMD 指令（AVX2/AVX-512），常见 2~4 倍加速。列存数据连续排列，天然适合 SIMD——存储格式和执行引擎是配套设计。

7 MergeTree 家族变体：按场景选引擎
------

* **ReplacingMergeTree**：按版本去重，查询要 `FINAL` 才能拿到去重结果，而 FINAL 代价高——只适合"最终一致去重"，不适合在线去重查询。
* **AggregatingMergeTree**：预聚合中间状态。用 `AggregateFunction` 类型列，写入用 `-State` 后缀函数、查询用 `-Merge` 后缀函数，配合**物化视图自动聚合**，是"明细表 + 物化视图预聚合表"的经典架构。
* **SummingMergeTree**：数值列自动 SUM，适合计数器场景。
* **CollapsingMergeTree**：用 sign=1/-1 的正负行相互抵消来"实现"更新删除；VersionedCollapsingMergeTree 加 version 列解决乱序写入导致抵消失败的问题，**生产上推荐带版本的形式**。

8 副本与分片
------

* 协调服务用 **ClickHouse Keeper**：C++ 实现的 ZooKeeper 兼容替代品，更省资源。
* 副本靠 ReplicatedMergeTree：**拉取式同步**，副本间通过 Keeper 协调 Part 的元信息，谁缺 Part 谁去拉。
* 分片靠 **Distributed 表**：它本身不存数据，只做"按分片键路由查询"的逻辑层；典型拓扑如 2 分片 × 2 副本。
* 容量感：**日增数据 100GB 以内单机基本够用**，先单机后分片。

9 Parquet 互通与选型口径
------

* 与 Parquet 的关系：`file()` 函数可直接查询 Parquet 文件，`INTO OUTFILE` 可导出为 Parquet——列存数据库与列存文件格式天然互通（Parquet 全系列见 [Parquet深入理解-总起](./Parquet深入理解-总起.md)），内存侧的对应物则是 [ApacheArrow列存内存格式](./ApacheArrow列存内存格式.md)。
* 选型口径：需求是"少量行全列 + 事务"去 MySQL（[MySQL基础-架构与SQL执行流程](./MySQL基础-架构与SQL执行流程.md)）；"大量行少数列 + 聚合"来 ClickHouse。两者常并存于同一系统（交易库 + 分析库），经 CDC 或批量同步衔接，规模再大才谈 [数据库分库分表](./数据库分库分表.md)。

面试追问链
------

* ClickHouse 为什么快？→ 列存列裁剪 + 稀疏索引读得少 + 压缩 + 向量化执行，四件套缺一不可；为什么不用 B+ 树？→ 面向范围扫描而非点查，稀疏索引 95KB 常驻内存，粒度粗但写入便宜；
* Granule 是什么？→ 默认 8192 行的读取最小单位，稀疏索引和 .mrk2 都以它为刻度；主键怎么建？→ ORDER BY 即主键，遵循最左前缀，等值/范围高频列放前面；
* 为什么不建议高频小批量 INSERT？→ 每次写入产生一个 Part，小 Part 爆炸会拖垮归并；UPDATE/DELETE 为什么慢？→ Mutation 异步重写整个数据块，重量级操作；
* 去重场景怎么选引擎？→ ReplacingMergeTree + FINAL 有代价，一般容忍最终一致或用 Collapsing 系列；预聚合怎么做？→ AggregatingMergeTree + 物化视图，-State 写 -Merge 查；
* 副本和分片分别怎么实现？→ ReplicatedMergeTree 拉取式同步 + Distributed 表路由，Keeper 做协调。

面试官视角小结
------

考察 ClickHouse 的分层信号：只会说"列式存储快"是不及格的；能讲清"稀疏索引 + 列裁剪 + 向量化"三件套各自解决什么问题算入门；能解释 Part/Granule 的物理模型、Mutation 的重量级本质、MergeTree 家族各变体的适用边界与坑（FINAL 代价、Collapsing 乱序、小 Part 爆炸），才是真在生产里用过。最高档的答案会主动说清它放弃了什么——不支持事务、点查弱、JOIN 有限——并能据此判断业务适不适合上 ClickHouse。

> 来源：javadoop《ClickHouse 核心架构与设计原理》（https://javadoop.com/post/clickhouse）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
