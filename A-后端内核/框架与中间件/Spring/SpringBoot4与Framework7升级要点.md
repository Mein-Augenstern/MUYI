SpringBoot 4 与 Spring Framework 7 升级要点
====

一句话定位：如果说 Boot 3 那次是「搬地基」——Java 17、Jakarta、Observation（见 [SpringBoot3升级要点-Jakarta-AOT-Native](./SpringBoot3升级要点-Jakarta-AOT-Native.md)），那么 Boot 4 / Framework 7 就是「在新地基上重盖楼层」：**基线只小步走（JDK 17+、Jakarta EE 11），真正的跳变在定位——Java 25 一等支持、模块化重组、AI 生态卡位（Spring AI 2.0 只认 Boot 4.x）**。面试里的对应考题是「你们升 4 了吗、为什么升或为什么不升、和升 3 那次痛感有何不同」——考的是升级决策的判断力，不是背新特性清单。

1 升级值不值：从 3.x 到 4 的收益清单
------

先立时间线（日期记个大概就行，关键是记住「三代并存」的格局）：

| 版本 | GA 时间 | 基线 | 2026-10 状态 |
| ---- | ---- | ---- | ---- |
| Spring Framework 7.0 | 2025-11-13 | JDK 17+（推荐 25）/ Jakarta EE 11 | 当前大版本 |
| Spring Boot 4.0 | 2025-11-20 | Framework 7 | 2026 年中已迭代到 4.1.x |
| Spring Boot 3.5.x | 2025-05 | Framework 6.2 / JDK 17 | 仍在维护线，3.x 的收尾线 |

升级收益，按说服力排序：

* **AI 生态卡位（最硬的一条）**：Spring AI 2.0（2026-06 GA）**要求 Boot 4.x**——MCP、ChatClient 这条新生态只铺在新地基上。做 AI 网关、Agent 服务、模型中间层的团队，Boot 4 不是选择题；
* **Java 25 一等支持**：Boot 4 是第一个把 JDK 25（LTS）当一等公民的版本线。虚拟线程自 JDK 24（JEP 491）修复 synchronized pinning 后可用性大增，「每任务一线程」从试验选项变成可放心默认选用的线程模型，并发侧全景见 [JDK25并发终局-ScopedValues与结构化并发](../../java/Java%20并发/JDK25并发终局-ScopedValues与结构化并发.md)；
* **启动与资源基线**：GraalVM Native Image 对多数 Spring 应用已是生产就绪；JDK 26 起的 Leyden AOT 缓存给出「不放弃 JIT 换启动速度」的中间路线（第 3.4 节）；
* **框架侧新能力**：API versioning 内置、HTTP Interface 客户端成熟、JSpecify null 安全——都只在 Framework 7 上有。

反过来，什么团队可以不急：纯内部 CRUD 服务、深度绑定 Spring Cloud 旧版本线、存量自研 starter 一大把且无人验证——停在 3.5 维护线并不吃亏，等 4.1 站稳再上是完全合理的决策。**这题的第一分：结论必须带条件，答成无条件的「升」或「不升」都丢分**。

2 Framework 7 核心变化
------

2.1 基线与移除项
------

| 维度 | Framework 6.x / Boot 3.x | Framework 7 / Boot 4 |
| ---- | ---- | ---- |
| Java 基线 | 17 | 17+（推荐 25） |
| Jakarta EE | 10（Servlet 6.0） | 11（Servlet 6.1） |
| 内嵌 Tomcat | 10.1.x | 11.x |
| JPA / Hibernate | Persistence 3.1 / 6.x | Persistence 3.2 / 7.x |

移除项的主旋律是「清账」：6.x 标记 @Deprecated 的 API 成批移除；自家 org.springframework.lang 的 null 注解体系让位 JSpecify（见 2.4）。这次没有 javax→jakarta 那种全库断裂面——EE 10→11 对用户代码的影响小一个量级（Servlet 6.0→6.1 属小版本演进）。实操建议：升级前先在 3.5 上把 deprecation 告警清零，是最便宜的前置体检；批量替换交给 OpenRewrite / IntelliJ 迁移工具。

2.2 API versioning：从手写 hack 到一等公民
------

以前做版本路由只能自己扩展 RequestMappingHandlerMapping、用 header 条件硬凑，每个团队一套轮子。Framework 7 把「版本」做成 request mapping 的正式维度：

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping(version = "1.0")
    List<OrderV1> listV1() { ... }   // 请求 v1.0 落这里

    @GetMapping(version = "1.2")
    List<OrderV2> listV2() { ... }   // 请求 v1.2+、无更高版本可匹配时落这里
}
```

* 版本来源（请求头、路径段、query 参数、media-type 参数）由版本策略配置决定；匹配走语义化版本比较：请求 1.3 而只有 1.0/1.2 两个 handler 时，路由到「不大于请求版本的最新一个」——向后兼容的 fallback 规则内建了；
* 对比记忆：URL 里 /v1//v2/ 的土办法、按 header 自行分发的地方轮子，现在统一由框架层收编，多团队共享 API 时尤其值钱。

2.3 HTTP Interface 客户端成熟
------

@HttpExchange 声明式 HTTP 客户端在 Framework 6.0 引入、6.1 增强，到 7.0 已是生产推荐的客户端写法，与 RestClient（6.1+，RestTemplate 的接棒者）组成一套班子：

```java
public interface OrderClient {
    @GetExchange("/api/orders/{id}")
    Order get(@PathVariable long id);
}

HttpServiceProxyFactory factory = HttpServiceProxyFactory
        .builderFor(RestClient.builder().baseUrl("http://order-svc"))
        .build();
OrderClient client = factory.createClient(OrderClient.class);
```

为什么在升级题里提它：声明式接口在 AOT/Native 下走构建期处理，与 Boot 4 的原生化路线天然对齐；升级盘点时，手写 RestTemplate/HTTPUtil 的存量调用是顺手收编的对象。

2.4 null 安全与 Kotlin 改进
------

* Framework 7 把整个代码库的 null 安全注解从自家 org.springframework.lang 迁到标准化的 **JSpecify**（@NullMarked / @NonNull / @Nullable），旧注解废弃；
* 用户侧收益：NullAway / IDE 能沿 Spring API 签名做编译期空安全检查，第三方框架的 null 语义第一次有了统一语言；
* Kotlin 侧：JSpecify 注解自动映射为 Kotlin 的可空类型，Java/Kotlin 混编工程里 null 语义不再靠猜；Kotlin 基线同步上调，协程支持继续打磨。

3 Boot 4 实操要点
------

3.1 starters 重组与模块化
------

这是 Boot 4 表面上最显眼、实际上最伤筋动骨的变化：

* 单体 spring-boot 拆成按职能的细粒度模块（HTTP、web server、Tomcat、Jackson 等各自成模块），自动配置跟着模块走，依赖树按需组合；
* 最直观的一刀：**spring-boot-starter-web 更名为 spring-boot-starter-webmvc**（与 webflux 终于对仗），旧名保留兼容别名过渡；
* 自研 starter 是重灾区：过去 import 的类可能被搬进新模块，「升级后自动配置静默失效（Bean 悄悄没了）」依旧是第一怀疑点——与 3.0 那次 spring.factories 迁移的排查思路相同；
* 模块化的回报：依赖更小、Native/AOT 的可达性分析面更小、与按需组装的构建形态对齐。

3.2 配置与测试迁移
------

* 配置键随模块化继续有一批更名/搬家，过渡工具仍是 spring-boot-properties-migrator（启动时自动迁移并打印告警，稳定后移除）；
* 测试侧硬断裂：@MockBean 在 3.4 废弃、4.0 移除，统一换 @MockitoBean；
* 三方依赖矩阵：MyBatis、springdoc 等须换 Boot 4 适配版本；Spring Cloud 与 Boot 大版本绑定，必须同批对齐，不可长期混搭——这两条纪律与升 3 那次一致。

3.3 观测默认：Micrometer + OTel 定型
------

Boot 3 引入的 Observation 统一埋点模型（一次埋点、指标 + 链路双出口，见前作第 3 节）在 Boot 4 成为唯一主线：Micrometer Metrics/Tracing 全家桶随 4.x 整体对齐新版本，bridge-otel 成为事实默认推荐，OTLP 导出是常规形态。应用侧大多无感，升级时重点核对两处：自定义 ObservationHandler 的 API 兼容；tracing 上下文在虚拟线程 fan-out 场景的透传（context-propagation 库）。

3.4 启动优化两条路线：GraalVM Native vs Leyden AOT 缓存
------

| 维度 | JVM + JIT（默认） | JVM + Leyden AOT 缓存 | GraalVM Native Image |
| ---- | ---- | ---- | ---- |
| 思路 | 无预热优化 | 训练运行固化类加载/链接数据，启动直读缓存 | 构建期静态编译，封闭世界 |
| 启动速度 | 秒级 | 数百毫秒级 | 毫秒级 |
| 峰值吞吐 | 最高（JIT 预热后） | 最高（JIT 还在） | 有折损，PGO 可缓解 |
| 内存 | 数百 MB | 与 JVM 模式相当 | 数十 MB |
| 迁移成本 | 无 | 加训练运行与缓存生成流程 | RuntimeHints 登记反射/资源，构建链路大改 |
| GC | 任意 | JDK 26 起任意 GC（此前限 G1） | Native 自带 GC |
| 适用 | 常规长期服务 | 要快启动但不想放弃 JIT 与全套工具链 | Serverless、扩缩到 0、CLI |

选型口径一句话：**Native 是「换运行时」，Leyden 是「给 JVM 加启动缓存」**。Boot 4 时代多数服务的折中答案是 Leyden（自 JDK 24 起分阶段落地，JDK 26 起解除 GC 限制）；只有彻底 Serverless、按请求计费的形态，才值得付 Native 封闭世界的成本（反射/资源登记、无 JIT 折损）。

4 与 Spring AI 2.0 的联动：为什么 AI 应用该上 Boot 4
------

* **版本硬约束**：Spring AI 2.0（2026-06 GA）要求 Boot 4.x；1.x 线停在 Boot 3.x 的维护期，新特性（MCP 工具链、模型生态、观测埋点）都发在 2.0 上；
* **负载画像契合**：AI 应用的典型形态是「一次请求 fan-out 多路 IO」——模型 API、向量检索、RAG、工具调用，正是虚拟线程 + Java 25 的主场；
* **观测契合**：ChatClient 调用链天然落在 Observation 模型上，token 用量、耗时、工具调用可与普通业务指标同屏观测；
* 决策句：**新起盘的 AI 服务直接 Boot 4.x + JDK 25，没有历史包袱就不该选旧基线；存量服务接 AI 能力，走完第 5 节的台阶再接**。

5 升级路线图：3.2 → 3.5 → 4.0 → 4.1 的台阶式走法
------

台阶式原则：一步只动一个变量，每一台阶都是可独立回滚的发布。

1. **3.2/3.3/3.4 → 3.5**：先停在 3.5 清账——deprecation 告警清零（含 @MockBean）、properties-migrator 清配置旧账、Spring Cloud 对齐到 3.5 配套线。3.5 与 4.x 的行为最接近，是事实上的跳板；
2. **3.5 → 4.0**：JDK 17 即可升，不必先动 JDK；主战场是模块化重组——starter 更名、自研 starter 适配、三方矩阵、配置迁移。这一步动的是构建与依赖，业务逻辑几乎不动；
3. **4.0 → 4.1**：生产落点建议直接选 4.1.x（2026 年中已就位）：x.0 的定位是让生态先适配，等 4.1 站稳再上是低风险节奏；
4. **JDK 17 → 25 单独一步**：Boot 4 与 JDK 升级解耦，先 Boot 后 JDK，出问题时好二分定位。

回滚与灰度纪律：

* 每台阶独立成镜像/版本，回滚粒度 = 一个台阶，绝不 Boot + JDK + 依赖矩阵三变量同时动；
* 灰度先非核心服务，重点盯三类信号：自动配置静默失效（Bean 缺失）、配置键搬家后不生效、Native/AOT 构建产物行为差异；
* 有 AI 需求的服务优先走完全程；纯 CRUD 服务可在 3.5 长期停留——但要有书面的再评估时间点，而不是「永远不升」。

面试追问链
------

* Boot 4 和 Boot 3 那次升级的本质区别？→ 3 是搬地基（Jakarta/Java 17/契约断裂），4 是地基上的重组与卡位（模块化/Java 25/AI 生态）；痛感从「包名全换」变成「依赖重组」；
* 最硬的升级理由？→ Spring AI 2.0（2026-06 GA）只支持 Boot 4.x，AI 生态绑定新基线；
* spring-boot-starter-web 没了？→ 更名 spring-boot-starter-webmvc，旧名别名过渡；背后是单体模块化拆分；
* 基线还是 JDK 17，那升 Boot 4 要不要先升 25？→ 不用，两者解耦、分两步升；但虚拟线程的完整收益要 JDK 24+（JEP 491 修 pinning）；
* API versioning 解决什么？→ 版本路由从自研 HandlerMapping hack 变成 mapping 的 version 属性 + 语义化匹配，fallback 规则内建；
* Native 和 Leyden 怎么选？→ Serverless/CLI 选 Native（毫秒级启动、数十 MB 内存）；长期服务要启动速度选 Leyden AOT 缓存（JDK 26 起任意 GC），保留 JIT 与全套工具链；
* @MockBean 编译不过了？→ 3.4 废弃、4.0 移除，换 @MockitoBean。

面试官视角小结
------

这道题的分层很明显：能报出 GA 时间线与基线变化只是及格；能讲出「升级决策的条件化」（AI 卡位 vs 维护线观望）、模块化对自研 starter 的冲击、Native 与 Leyden 的取舍逻辑，是有架构视角的信号；能给出台阶式路线（3.5 清账 → 4.0 重组 → 4.1 落点 → JDK 单独走）并设计回滚粒度，才是真操盘过升级的人。与 [SpringBoot3升级要点-Jakarta-AOT-Native](./SpringBoot3升级要点-Jakarta-AOT-Native.md) 连读，构成 2.7 → 3.x → 4.x 的完整升级答题线；并发基线（虚拟线程/ScopedValue/结构化并发）见 [JDK25并发终局-ScopedValues与结构化并发](../../java/Java%20并发/JDK25并发终局-ScopedValues与结构化并发.md)。

> 2026-10 新增（2026 技术栈补全）。
