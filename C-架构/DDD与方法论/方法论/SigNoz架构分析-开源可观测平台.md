SigNoz 架构分析：开源可观测平台
====

开源 APM，一个产品同时装下 Logs、Traces、Metrics，对标 Datadog 的开源答案。它值得后端研读的理由很特别：**SigNoz 几乎没有自研客户端和数据模型，全盘站在 OpenTelemetry 肩上，自研部分只有 collector 的三个 exporter 加查询层**；存储则押注 ClickHouse。这是一堂"如何用现成标准件组装一个生产级系统"的架构课，也是 OTel 落地形态的最佳标本。三大信号的分工先看 [观测性三件套](./观测性三件套.md)，存储底座见 [ClickHouse核心架构与设计原理](../../../A-后端内核/框架与中间件/数据库/ClickHouse核心架构与设计原理.md)。

1 总体架构：三个角色
------

```
应用(SDK/agent, OTLP: gRPC 4317 / HTTP 4318)
        │
        ▼
signoz-otel-collector（OTel Collector 发行版：接收 + 批处理
        │                + 三个 exporter 分别写 CH）
        ▼
ClickHouse（traces / logs / metrics 各自的库表）
        ▲
query-service（把页面查询翻译成 ClickHouse SQL）←→ 前端
```

* 服务端哲学是"**薄**"：原始数据尽量原样进 ClickHouse，聚合在**查询时**做，不在内存里维护实时统计——与 Cat 那种内存聚合型 APM 正好相反，工程复杂度大幅下降。
* 适合初创与中等流量团队：部署成本低（一套 docker-compose），无供应商锁定。

2 Traces：span 模型与落库
------

* Span 关键字段：trace_id（16B/32 hex）、span_id（8B）、parent_span_id、name、kind（SERVER/CLIENT/INTERNAL/PRODUCER/CONSUMER）、start/end（无 duration 字段，写入时算好）、status（UNSET/OK/ERROR）、attributes、events、links；外层 ResourceSpans/ScopeSpans，service.name 来自 resource。
* 与 Cat 的结构对照：span ≈ Transaction，span event ≈ Event。本质区别：**span 没有对象引用，每个 span 独立经 BatchSpanProcessor 发送**——服务端收到的是零散 span，查询时靠 trace_id + parent_span_id 在内存里拼树。
* 跨进程传播：W3C Trace Context 的 traceparent 头；消息队列同理走 header。
* 异常表示：名为 `exception` 的 span event（type/message/stacktrace 三属性）。**recordException 与 status=ERROR 是两个独立动作**——只记录不设 status，界面上不会标红，这是最常见的接入 bug。服务端把异常抽到 signoz_error_index_v2，group id = md5(serviceName + exception.type)。
* 主表 signoz_index_v3：`ORDER BY (ts_bucket_start, resource_fingerprint, has_error, name, timestamp)`，ts_bucket_start 是 **30 分钟桶**，TTL 15 天。设计要点：
  - attributes 按类型拆 string/number/bool 三个 **Map 列** + 少量高频属性物化列（http_method、resource_string_service$$name 等）；
  - events/links 直接序列化成 JSON 字符串，不做结构化——够用就好；
  - 辅助表 traces_v3_resource（fingerprint → 属性映射，**先查小表再查大表**）、tag_attributes_v2（属性 key 自动补全，高基数 key 只记 key 不记值）；
  - 物化视图 trace_summary（按 trace_id 聚合起止时间与 span 数）、dependency_graph_minutes（分钟级服务依赖图）；
  - trace_id 上只有 bloom filter 跳数索引，**查单个 trace 要先从 trace_summary 拿时间范围再进主表**——为点查补了一条旁路。

3 三层采样
------

* **SDK head sampling**：`parentbased_traceidratio`，比例 0.1；决策随 traceparent flags 传播，最便宜、开头就丢。
* **collector probabilistic sampler**：对 trace_id 哈希采样，跨实例结果一致。
* **tail sampling**：等整个 trace 到齐再决策——decision_wait 10s、缓存 num_traces 50000；策略示例：ERROR 全留 + latency>1000ms 全留 + 其余 5%，策略间 OR。**多实例部署时要两层 collector + loadbalancing exporter 按 trace_id 一致性哈希**（同一个 trace 必须落到同一实例才能等到齐）。副作用：spanmetrics 派生指标会失真。

4 Logs：两条采集路
------

* LogRecord 字段：timestamp、observed_timestamp、severity_text/number、body、attributes，以及 trace_id/span_id/trace_flags——**只是共享 id，与 span 存储互不相干**。
* 采集路径一：SDK 接管日志框架（Java agent 给 Logback/Log4j 装 appender 直发 OTLP）。
* 采集路径二：写文件 + filelog receiver 采集。日志 pattern 里打印 trace_id（Java 用 MDC 的 `%X{trace_id}`），collector 用 regex/JSON parser 加 trace_parser 回填——**日志与链路在此会师**。K8s 环境多为 DaemonSet 采集 stdout。
* 主表 logs_v2：`ORDER BY (ts_bucket_start, resource_fingerprint, severity_text, timestamp, id)`，id 用 ksuid 支持翻页，TTL 15 天；body 上是 ngram bloom filter 跳数索引——全文搜索本质仍是扫描，**先缩小时间/severity/service 范围再搜**。

5 Metrics：序列与样本分离
------

* 类型：gauge、sum（cumulative/delta 两种 temporality）、histogram、exponential histogram、summary（仅为兼容 Prometheus）。
* 采集是**定时导出聚合值**（默认 60 秒），不是原始事件；Java agent 自带 JVM/HTTP/DB 指标；也支持 prometheus receiver 直接抓 /metrics。
* 派生指标是亮点：**signozspanmetrics 处理器从 span 直接累加出 signoz_calls_total 与 signoz_latency**（按 service_name/operation/span_kind/status_code 维度），Services 页的 RPS/错误率/P99 全来自这里——链路数据顺手变指标，不多埋一个点。
* 存储：samples_v4 **每点一行**，`ORDER BY (env, temporality, metric_name, fingerprint, unix_milli)`，TTL 30 天；time_series_v4 用 ReplacingMergeTree 每序列每小时一行存 labels JSON（exporter 45 分钟缓存去重）。fingerprint = metric 名 + 全部 labels 的 64 位哈希——**Prometheus 的 series/sample 分离思路原样搬进 ClickHouse**。
* histogram 一个数据点拆成 .count/.sum/.min/.max/.bucket（每桶一条序列，le 标签）——20 个桶就是 24 行，序列乘法的开端。rate/分位数查询时现算（histogramQuantile）；5 分钟与 30 分钟粒度的预聚合物化视图供长时间范围查询。

6 trace_id 的六个作用
------

身份（128 位随机，root span 生成）、跨服务传播、服务端拼树、日志/异常关联、采样哈希一致性、tail sampling 的一致性哈希路由。span_id 只干两件事：拼树、把日志关联到具体 span。**一个 id 串起三大信号**，这就是 OTel 体系的龙骨。

7 五个压力点（作者的判断）
------

1. **高基数（最严重）**：tag_attributes_v2 膨胀、Map 列压缩变差、metrics 序列乘法增长。span name 没 normalize（把订单号写进 name）是最常见事故源；
2. **写入**：batch 要够大防 too many parts；collector 有状态，宕机丢数据；**默认无消息队列兜底，大流量要前置 Kafka**；
3. **查询**：ClickHouse 不擅点查——trace_id 查询、非物化列过滤、body 关键字搜索全是扫描；几万 span 的大 trace 拼树渲染吃力；
4. **tail sampling 拓扑**：最耗内存，很多团队最终退回 head sampling + 短 TTL；
5. **存储量**：全量存 span，中等系统一天几亿 span，15 天 TTL 即由此而来；好在 ClickHouse 压缩率高，相对好扛。

8 什么时候选它：对着压力点做决策
------

* 适合：中小规模（日千万到低亿级 span）、想要**三信号一个产品一套部署**、团队已认 OTel 标准的团队——这正是"初创/中等流量"定位的含义。
* 谨慎：超大流量（先想好 Kafka 兜底与采样拓扑）、重度全文日志检索需求（ClickHouse 打 ES 的特长要掂量：赢在压缩率与写入吞吐，输在全文检索）、强依赖点查式取单条数据的场景。
* 它对标的是"Datadog 的开源替代"而不是"ELK + Jaeger + Prometheus 三件拼盘的替代者"——拼盘各自成熟但胶水成本在三处（数据模型、查询、告警），SigNoz 用 OTel + ClickHouse 把三处胶水合成一处。这个"少造轮子、押注标准"的思路，比工具本身更值得抄走。
* 引入前先立规矩：span name 规范化、属性 key 白名单、TTL 与容量预估——五个压力点里四个能靠"写入侧纪律"提前拆掉。

面试追问链
------

* SigNoz 自研了什么？→ 三个 exporter + 查询层，客户端与数据模型全是 OTel 的；为什么能这么薄？→ 聚合放查询时、数据原样进 ClickHouse，不做内存实时统计；
* span 怎么变成树？→ 每个 span 独立上报，查询时按 trace_id + parent_span_id 内存拼装；与 Cat 区别？→ Cat 在 agent 内存里就有完整调用树；
* 异常没标红为什么？→ recordException 与 setStatus(ERROR) 是两个动作；错误索引怎么建？→ 抽到 signoz_error_index_v2，md5(service+type) 分组；
* 采样三层怎么选？→ head 便宜先丢、collector 哈希保一致、tail 等全 trace 再按 ERROR/延迟/比例决策；tail 的部署前提？→ trace_id 一致性哈希保证同 trace 同实例；
* 日志怎么关联链路？→ MDC 打 trace_id，collector parser 回填；全文搜索为什么慢？→ 本质扫描，靠 ngram bloom 先缩小范围；
* metrics 为什么序列/样本分表？→ Prometheus 思路，labels 存 time_series、样本每点一行进 samples；RPS/P99 哪来的？→ spanmetrics 从 span 派生指标；
* 最大的坑？→ 高基数：name 未 normalize、Map 列膨胀、histogram 序列爆炸。

面试官视角小结
---

SigNoz 是少数能把"可观测性 + 存储选型"串起来考的素材。只答"用了 ClickHouse"不够；能讲清 trace 表的排序键为什么这么设计（30 分钟桶 + fingerprint 前置）、spanmetrics 如何让 span 顺手产出指标、三层采样的成本梯度与 tail sampling 的一致性哈希前提，才算读懂了这个系统。最高层的答案会落到架构方法论上：SigNoz 证明了"押注标准化生态（OTel）+ 押对存储引擎（ClickHouse）+ 自研只做胶水"这条组装路线的可行性——以及它的代价（高基数与无队列兜底），这正是绝大多数团队自建可观测平台前该想清楚的问题。

> 来源：javadoop《SigNoz 架构分析》（https://javadoop.com/post/signoz）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
