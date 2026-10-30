JDK 26 升级要点：HTTP/3、Leyden 与 final 语义收紧
====

一句话定位：JDK 26（2026-03-17 GA，**非 LTS**）不是又一个特性堆积包，而是三条主线的交汇点——**网络栈进 HTTP/3 时代、Leyden AOT 缓存挣脱 GC 绑定、语言语义开始收账（final means final）**。面试考的不是"背 10 个 JEP"，而是"26 是非 LTS，你们生产为什么在 25 / 为什么试 26"——考版本策略判断力。前置阅读 [JDK17-21语言新特性迁移指南](./JDK17-21语言新特性迁移指南.md)。

1 版本坐标：26 在时间线上的位置
------

| 版本 | GA | 定位 | 2026-10 状态 |
| ---- | ---- | ---- | ---- |
| JDK 25（LTS） | 2025-09 | 当前生产基线 | 25.0.x 维护中 |
| JDK 26 | 2026-03-17 | 特性火车（10 个 JEP，含 4 预览） | 26.0.x 维护中 |
| JDK 27 | 2026-09（六个月节奏） | 下一班特性火车 | 刚 GA 一个多月 |

决策口径：生产 LTS 锚在 25；26/27 是"新特性预览窗口"——尝鲜用，落地等 27（下一个 LTS 是 29，2027-09）。这套节奏感答出来，比背特性清单值钱。

2 三条主线拆解
------

2.1 HTTP/3 客户端（正式特性）
------

- 基于 QUIC/UDP：0-RTT 连接建立、连接迁移（网络切换不断链）——移动端与跨地域 RPC 的收益面；
- API 形态延续 HttpClient 风格（`HttpRequest`/`HttpResponse`），升级成本低；
- 追问链预判："QUIC 和 TCP+TLS1.3 差在哪"→ 拥塞控制在用户态、无队头阻塞（HOL）——HTTP/2 只解决了 HTTP 层 HOL，传输层还在，QUIC 把传输层流也拆了。

2.2 Leyden AOT 缓存：挣脱 GC 绑定
------

- JDK 24 的 AOT 类加载与预热缓存（premain）最初绑特定 GC；26 把**任意 GC 都能用 AOT 缓存**做成正式能力；
- 价值定位：不是替代 GraalVM Native Image（那是"放弃 JIT 换启动"），而是**保住 JIT 峰值性能的同时砍启动时间**——中间路线；
- Spring Boot 4 已按这条线做适配（见 [SpringBoot4与Framework7升级要点](../../框架与中间件/Spring/SpringBoot4与Framework7升级要点.md) 第 3.4 节）。

2.3 final means final：语言语义收账
------

- 语义收紧方向：绕过 final 的既有"口子"（如某些反射改写路径）被逐步堵死；
- 工程含义：依赖反射暴力改 final 字段的库（部分缓存/mock 框架的老版本）在 26 上会踩警告甚至失败——升级前先跑全量测试扫描这类依赖；
- 这条与 records/sealed 的方向一致：Java 在把"不可变性"从约定变成强制。

2.4 其余看点速览
------

- **Lazy Constants**（预览）：instance final 字段延迟初始化的官方形态，替代双检锁手写；
- **G1 更快**：分区扫描与并发 refinement 优化，吞吐普涨；
- **Valhalla 缺席**：Value Objects（JEP 401）只发了 EA 构建供试验，26 正式版没有——连续跳票的第 N 年，面试提它要谨慎，别把预览说成正式。

3 升级决策速答（面试模板）
------

- "你们用哪个 JDK？"→ 生产 25（LTS），新特性在 26 上跑 CI 验证，等 29；
- "26 值得专门升吗？"→ HTTP/3 客户端是唯一"应用层直接受益"的正式特性；AOT 缓存收益集中在 Serverless/短生命周期进程；其余等 LTS；
- 追问"为什么不直接 27？"→ 26→27 差一个版本窗口，新预览在 27 上更全；且非 LTS 停维护快，生产跳 LTS 是主流策略。

---

- 交叉引用：[JDK25并发终局-ScopedValues与结构化并发](../Java%20并发/JDK25并发终局-ScopedValues与结构化并发.md)、[SpringBoot4与Framework7升级要点](../../框架与中间件/Spring/SpringBoot4与Framework7升级要点.md)
- 来源核对面：Oracle 官方博客（The Arrival of Java 26）、InfoQ 2026-02 预览、HappyCoders/BellSoft 特性清单
