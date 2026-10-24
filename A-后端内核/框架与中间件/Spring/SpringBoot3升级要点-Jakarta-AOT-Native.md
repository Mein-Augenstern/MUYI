SpringBoot3 升级要点：Jakarta、Observation、AOT 与 Native
====

> 声明：以 Spring Boot 3.0（Spring Framework 6）为主线、Boot 2.7 为对照，涉及小版本差异处单独标注。面试视角：三条主线 + 一个注册机制演进 + 一份迁移清单。

一句话定位：Spring Boot 3 不是"多几个注解"的常规升级，而是把整条技术栈搬到了新地基。**Java 17 是门槛，Jakarta 命名空间是契约，AOT/Native 是方向**。能把这三条主线讲成体系，比背零散新特性值钱得多。

1 三条主线总览
------

| 主线 | 关键词 | 解决什么问题 | 面试考点 |
|------|--------|--------------|----------|
| 基线升级 | Java 17 / Jakarta EE 9+ / Servlet 6 | 甩掉历史包袱，语言与 API 基线统一 | javax→jakarta 影响面 |
| 可观测性 | Micrometer Observation / Micrometer Tracing / OTel | 指标与链路追踪统一编程模型 | Observation 与 Meter/Span 的关系 |
| 原生化 | AOT / GraalVM Native Image | 启动毫秒级、内存降档（云原生按需扩缩） | 为什么难在反射/动态代理 |

辅助记忆：**地基（Jakarta）→ 眼睛（Observation）→ 身材（AOT/Native）**，前两条是必答题，第三条是区分度题。

2 基线升级：Java 17 + Jakarta EE 9
------

2.1 发生了什么：为什么包名必须换
------

Oracle 把 Java EE 移交给 Eclipse 基金会（更名 Jakarta EE）后，`javax` 命名空间无法继续用于新版本 API。**Jakarta EE 9 起全部从 `javax.*` 迁到 `jakarta.*`**。Spring Framework 6 / Boot 3 把基线直接定在这里，并实际对齐到 EE 10 级别：

| 组件 | Boot 2.7 | Boot 3.0 |
|------|----------|----------|
| Java | 8+ | 17+（硬性门槛） |
| Spring Framework | 5.3.x | 6.0.x |
| Servlet API | 4.0（javax） | 6.0（jakarta） |
| 内嵌 Tomcat | 9.x | 10.1.x |
| JPA / Hibernate | 2.2 / 5.6 | 3.1 / 6.1+（校验同步换 jakarta.validation 3.0） |

2.2 javax→jakarta 迁移影响面
------

自己代码里的替换是机械的（IDE 一键迁移、编译期就能暴露），真正的坑在依赖层：

```java
// 影响 1：所有容器/持久化/校验/公共注解相关 import
- import javax.servlet.http.HttpServletRequest;
+ import jakarta.servlet.http.HttpServletRequest;
- import javax.persistence.*;
+ import jakarta.persistence.*;
- import javax.annotation.PostConstruct;    // JSR-250 也换了
+ import jakarta.annotation.PostConstruct;
```

* **三方库/自研 starter**：传递了 `javax.servlet`/`javax.persistence` 的依赖与 Boot 3 冲突（运行期 NoSuchMethodError/ClassNotFound），必须换 jakarta 版本。例如 springdoc 1.x→2.x、mybatis-spring-boot-starter 2.x→3.x。springfox 停更不兼容，只能迁 springdoc；
* **按类名字符串找类的代码**：反射、代理、SPI 配置文件里的全限定名，编译期查不出来，属于"隐性断裂面"。

标准答题表述：**命名空间迁移是契约层面的断裂**。编译能过不代表没事，凡是运行期按字符串加载类的路径都可能埋雷。

3 可观测性：Micrometer Observation
------

Boot 2 时代指标走 Micrometer、链路走 Spring Cloud Sleuth，两套 API 两套埋点。Boot 3 把两者收进一个编程模型：

```
业务代码（@Observed / Observation API）——一次埋点，两个出口
      ▼
  Observation（"观察事件"抽象）
      ├── Metrics：Meter 体系（Timer/Counter…）→ /actuator/prometheus
      └── Tracing：Micrometer Tracing（Sleuth 的接棒者）
              ├── bridge-brave → Zipkin
              └── bridge-otel  → OpenTelemetry → Jaeger/SkyWalking 等
```

* **Observation 是事件源抽象**：一次 HTTP 请求/定时任务/消息消费，既产出指标（耗时、计数），也产出 span（trace id 透传）。业务只写一次；
* **Sleuth 生命周期终结**：Spring Cloud Sleuth 不再支持 Boot 3。官方接棒方案是 Micrometer Tracing（选 bridge-otel 即融入 OTel 生态，trace 语义与导出协议对齐）；
* **上下文透传**：trace 上下文跨线程池/WebFlux 依赖 context-propagation 库。@Async、并行流丢 trace id 的老问题在这里统一解决。

```java
@Service
public class OrderService {
    @Observed(name = "order.create", contextualName = "创建订单")
    public Order create(CreateCmd cmd) { /* ... */ }
    // 指标见 /actuator/metrics/order.create；
    // span 经 OTLP/Zipkin 端点上报，由 tracing 后端消费
}
```

Actuator 侧变化：`httptrace` 端点更名为 `httpexchanges`；Prometheus 抓取端点仍是 `/actuator/prometheus`，但导出配置键整体换了位置（见第 6 节）。

4 原生化：AOT 与 GraalVM Native Image
------

4.1 为什么 Native 的难点在反射和动态代理
------

JVM 上的 Spring 重度依赖"运行时动态性"，而 Native Image 是**构建期静态编译 + 可达性分析**（封闭世界假设）：

```
JVM 模式：启动后还能"现做"类，越跑越快
  反射 Class.forName(变量) / JDK 代理 / cglib 运行期生成子类字节码
    └→ 运行期生成并加载类 → JIT 边跑边优化

Native 模式：构建期可达性分析 + 静态编译（封闭世界假设）
  └→ 只保留从入口可达的代码；运行期不能生成字节码、不能按名字随意
     加载类、没有 JIT 再优化
```

* **反射**：`Class.forName(字符串变量)` 构建期分析不出来 → 二进制里没有该类的元数据 → 运行期直接失败。解法是构建期显式登记（RuntimeHints，三方库可复用 GraalVM reachability metadata 仓库）；
* **动态代理**：JDK 代理/cglib 都靠运行期生成新类，封闭世界里"生成"不成立。解法是把"代理谁、代理成什么"提前到构建期确定；
* 面试金句：**Spring AOT 的本质是把运行期的决策提前到构建期做完并固化为代码**。运行期的决策包括扫描、条件评估、代理生成。用"构建期处理"换"运行期零反射猜测"。

4.2 AOT 处理流程与代价
------

```
mvn spring-boot:process-aot（native 构建：native:compile 或 buildpacks build-image -Pnative）
  → 预演一次 ApplicationContext（不真正跑业务逻辑）
  → 解析 @Conditional/@Profile、完成扫描 → 结果固化
  → 生成 Java 代码（target/spring-aot/main/sources）：BeanDefinition 用代码
     直接声明，Bean 实例化从"反射调构造器"变成"直接调构造器"
```

代价必须心里有数（高频追问）：

* **条件注解构建期一锤定音**：AOT 后运行期改 classpath/条件不再重评。**@Profile 也是构建期确定**：构建时激活哪些 profile，产物就只含哪些。运行期动态注册 Bean（registerBean、SPI 动态发现）同样不生效；
* Spring 自身的自动配置/事务代理/@Configuration 官方已做好 hints，**用户的自定义反射、资源、序列化要自己登记**：

```java
@ImportRuntimeHints(OrderService.Hints.class)
public class OrderService {
    static class Hints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader cl) {
            // 反射调用的类要登记 MemberCategory
            hints.reflection().registerType(PayConfig.class, MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.resources().registerPattern("config/*.yml");   // 资源文件同样要登记
        }
    }
}
```

4.3 Native 限制清单与取舍
------

| 能力 | JVM 模式 | Native 模式 |
|------|----------|-------------|
| 反射 / Class.forName(动态串) | 随意用 | 必须注册 hints，未注册时运行期出现错误 |
| 动态代理（JDK/cglib/ByteBuddy） | 运行期生成 | 需 hints 或构建期生成；Spring AOT 自动处理自家代理 |
| 资源文件（yml/xml/模板） | classpath 直读 | 必须 registerPattern 登记 |
| 序列化/JNI/Unsafe | 正常 | 需额外配置，部分行为受限 |
| 启动速度/内存 | 秒级 / 数百 MB | **毫秒级 / 数十 MB** |
| 峰值吞吐 | JIT 预热后最高 | 有折损（PGO 可缓解） |
| 构建耗时 | 秒~分钟 | 分钟~十分钟级 |
| 排查手段 | 全套 JVM 工具 | 工具链不同，堆栈/内存分析受限 |

选型口径：**Serverless、CLI 工具、扩缩到 0 的场景收益最大**。**长期高吞吐服务用 JVM + 虚拟线程（Boot 3.2+、Java 21，`spring.threads.virtual.enabled=true`）往往更划算**。

5 自动配置注册演进：spring.factories → AutoConfiguration.imports
------

`spring.factories` 原本是"一个文件装所有 SPI"：ApplicationListener、EnvironmentPostProcessor、EnableAutoConfiguration 全堆在一个 properties 里。自动配置类名占了大头，职责不清、也难做按需优化。演进分两步：

```properties
# Boot 2.6 及以前：META-INF/spring.factories（所有 SPI 混一个文件，还要写 key）
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  com.x.FooAutoConfiguration,\
  com.x.BarAutoConfiguration
```

```properties
# Boot 2.7+/3.x：META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
# 一行一个类名，不再需要 key
com.x.FooAutoConfiguration
com.x.BarAutoConfiguration
```

* **2.7→3.0 两步走**：2.7 新文件上线、旧写法兼容（双注册过渡）；3.0 起 spring.factories 里的 EnableAutoConfiguration 条目**失效**，自动配置只认新文件。注意 **spring.factories 本身没有废除**，其他 SPI（ApplicationListener、EnvironmentPostProcessor 等）仍然用它；
* 配套注解 `@AutoConfiguration`（表明自动配置类身份），排序用其 `before/after` 属性（替代 @AutoConfigureBefore/After 在自动配置类上的用法）；
* 自研 starter 不迁移的表象：**升级 3.0 后自动配置"静默失效"**——没有错误提示、Bean 悄悄没了。这是排查自研 starter 的第一怀疑点。

6 迁移评估清单（2.7 → 3.x）
------

1. **前置条件**：Java 17 编译与运行时就位，构建工具同步升级（Gradle 7.5+ / Maven 3.6.3+）。建议先停在 2.7 把废弃告警清零（官方指定的过渡台阶）；
2. **依赖矩阵核对**（缺一不可）：

| 依赖 | Boot 2.7 对应 | Boot 3.x 对应 | 备注 |
|------|---------------|---------------|------|
| springdoc-openapi | 1.x | 2.x（starter-webmvc-ui） | springfox 停更，只能迁 |
| mybatis-spring-boot-starter | 2.x | 3.x | 否则 jakarta 冲突 |
| Spring Cloud | 2021.0 | 2022.0+ | 须与 Boot 大版本对齐，同批升 |
| mysql-connector-java | 5.1.x 可用 | 8.x | 顺带解决 MySQL8 caching_sha2_password 认证 |
| lombok / mapstruct | 旧版 | 支持 JDK17 的版本 | 注解处理器兼容 |

3. **配置属性更名**：`spring.redis.*`→`spring.data.redis.*`、`management.metrics.export.prometheus.*`→`management.prometheus.metrics.export.*`、`server.max-http-header-size`→`server.max-http-request-header-size` 这类更名。过渡期引入 `spring-boot-properties-migrator`，启动时自动迁移并打印告警，稳定后移除；
4. **三方/自研 starter 改造 + 批量重构**：spring.factories→AutoConfiguration.imports（第 5 节）+ javax→jakarta 全量替换。机械部分交给 OpenRewrite / Spring 官方迁移配方 / IntelliJ 迁移助手。人工聚焦条件注解、反射点、Hibernate 6 方言与命名策略差异。同时关注 Actuator 端点与权限、连接池与时区参数这些回归项；

7 追问链（面试官会怎么往下问）
------

* **Q：为什么 Native 难点在反射和动态代理？**
  A：Native 构建期做可达性分析并静态编译，运行期是封闭世界。反射的类名字符串和运行期生成的代理类在构建期不可知 → 产物里没有 → 运行期失败。解法是 RuntimeHints 显式登记 + 构建期生成（AOT）。
* **Q：Spring AOT 到底替代了什么？**
  A：把运行期的注解扫描、@Conditional 评估、BeanDefinition 反射构建，提前到构建期预演一遍并生成代码。运行期按生成结果直接实例化，不再反射猜测。
* **Q：AOT 模式下 @Profile 还能用吗？**
  A：构建期确定：构建时激活的 profile 被固化进产物，运行期切换需要为不同 profile 组合分别构建。
* **Q：cglib 在 Native 里为什么不行？**
  A：cglib 靠运行期生成子类字节码并加载，封闭世界不允许运行期造新类。Spring 自身代理改由构建期处理 + JDK 代理满足，业务里直接用 cglib 的代码要重写。
* **Q：Observation 和 Micrometer、OTel 什么关系？**
  A：Observation 是统一埋点 API；向下分两路——Metrics（Micrometer MeterRegistry）与 Tracing（Micrometer Tracing）；Tracing 经 bridge-brave/bridge-otel 接 Zipkin 或 OTel 生态。
* **Q：Native 化能提升吞吐吗？**
  A：通常不能，它优化的是启动时间/内存/镜像体积。峰值吞吐因无 JIT 通常有折损（PGO 可缓解），长期服务更倾向 JVM + 虚拟线程（Boot 3.2+、`spring.threads.virtual.enabled=true`）。

8 小结
------

* 三条主线：**Jakarta 地基（Java 17 + jakarta 命名空间）→ Observation 眼睛（指标+链路一套 API）→ AOT/Native 身材（构建期固化换运行期轻快）**；
* 升级最高频翻车点：三方库 jakarta 冲突（编译过、运行炸）、自研 starter 的 spring.factories 静默失效。配置键更名后不生效、老版本 Spring Cloud 混搭；
* spring.factories 的自动配置条目已迁至 AutoConfiguration.imports（2.7 过渡、3.0 断旧），但 spring.factories 承载的其他 SPI 仍在用；
* Native 的核心矛盾是**封闭世界 vs 运行时动态性**，Spring 用 AOT + RuntimeHints 化解。用户侧要为自定义反射/资源/序列化补登记；
* 迁移按"2.7 清告警 → 依赖矩阵 → 属性更名 → starter 改造 → 回归"推进，properties-migrator 与 OpenRewrite 是两件趁手工具；
* 自动配置原理的完整答题线见 [SpringBoot 速查卡](../../cheatsheet/SpringBoot.md)；框架定位差异见 [Springboot和Spring区别](Springboot和Spring区别.md)。

> 2026-10 新增（P1 时效升级）。
