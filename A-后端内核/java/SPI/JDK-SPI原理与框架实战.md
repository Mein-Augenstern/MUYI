JDK SPI 原理与框架实战
====

看在前面
------

SPI（Service Provider Interface）：**接口归调用方，实现归提供方，装配靠约定文件**。把"用哪个实现"的决定权从编译期挪到运行期。本目录已有一篇入门（[了解简单的SPI实现原理和运用.md](了解简单的SPI实现原理和运用.md)，讲清了 META-INF/services 约定与优缺点）。这篇按面试深度补齐四块：

- **ServiceLoader 源码机制与失败语义**
- **JDK SPI 的缺陷到底坑在哪**
- **Dubbo SPI 怎么增强**
- **Spring Boot 的 spring.factories 与 AutoConfiguration.imports 两代机制**

最后用 JDBC 驱动加载把三代串起来。

一、ServiceLoader：懒加载是真的，但懒得很粗糙
------

### 1.1 机制全景

```
调用方 ServiceLoader.load(S.class)
        │  记录接口 + 线程上下文类加载器（TCCL），此时什么也不扫
        ▼
for (S s : loader)  ── hasNext()/next() 惰性推进
        │  LazyIterator.nextName = pending 迭代器的下一个全限定名
        ▼
Class.forName(name, false, loader)   ← 只加载、不初始化（initialize=false）
        ▼
clazz.getConstructor().newInstance() ← 无参构造反射实例化
        ▼
构造器执行失败 → ServiceConfigurationError（Error 不是 Exception！）
```

三个源码级细节，面试区分度全在这：

* **懒加载只到"类"这一级**。`Class.forName(name, false, loader)` 第二个参数是 false——类加载了但静态块不执行，直到 `newInstance()` 才触发初始化与实例化。所以"不 next 就不加载"是真的；但**一旦开始迭代，每一个实现都会被实例化**，哪怕你只要第一个。
* **失败会炸整个迭代**。任何一个实现的构造器抛异常，`ServiceConfigurationError` 直接冒出来，**后面本来能用的实现也拿不到了**。类路径上放一个坏 jar（比如缺依赖的旧版本驱动），整个 `ServiceLoader` 循环全军覆没。这是"实例化失败"的经典事故形态。
* **每次 load 都是新实例**。`ServiceLoader.load()` 返回的实例本身不缓存单例，迭代两次 = 实例化两遍。实现类的实例化成本（连接池、注册中心客户端）会被放大。

### 1.2 META-INF/services 约定

```text
provider.jar
└── META-INF/services
    └── com.example.SpiDemo          ← 文件名 = 接口全限定名（UTF-8）
        └── com.example.impl.SpiDemoImpl1   ← 每行一个实现类全限定名
            （# 注释、空行合法；同一文件可多行）
```

类加载器扫描的是 **classpath 下所有** 该路径文件（`ClassLoader.getResources`），多 jar 同名文件会**合并**而非覆盖。这正是"插拔式"的物理基础：加一个 jar 就多一组实现，去掉就少一组，主程序零改动。

二、JDK SPI 的三大缺陷：为什么框架都要自己造轮子
------

| 缺陷 | 表现 | 后果 |
| --- | --- | --- |
| 不按需加载 | 只能 Iterator 全量遍历，无法按名字/条件取一个 | 十个实现全实例化，浪费且可能触发上面说的连锁失败 |
| 非线程安全 | `ServiceLoader` 实例共享迭代会抛 `ConcurrentModificationException` | 每线程各自 load，实例不共享，缓存失效 |
| 无依赖注入 | 实现类里需要别的组件时只能自己 new | 装配能力为零，与 IoC 容器脱节 |

一句话总结缺陷根源：**JDK SPI 是"发现协议"，不是"装配框架"**。它只回答"有哪些实现"，不回答"怎么选、怎么注入、怎么缓存"。

三、Dubbo SPI：按名获取 + 自适应扩展
------

Dubbo 自己实现了一套 `ExtensionLoader`（配置目录 `META-INF/dubbo/`、`META-INF/dubbo/internal/`，key=value 格式而非 JDK 的纯类名），补齐上面所有缺陷：

```java
// 1) @SPI 声明扩展点，value 是默认实现名
@SPI("dubbo")
public interface Protocol {
    int getDefaultPort();
}

// 2) 按名获取——JDK SPI 做不到的第一件事
Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class)
                                    .getExtension("dubbo");

// 3) @Adaptive 自适应：运行时按 URL 参数决定用哪个实现
@Adaptive
Protocol adaptiveProtocol = ExtensionLoader.getExtensionLoader(Protocol.class)
                                    .getAdaptiveExtension();
// 调用时读 URL 里的 ?protocol=grpc 动态路由到 GrpcProtocol
```

增强点逐条对上 JDK 缺陷：

* **按名获取 + 懒实例化 + 实例缓存**（`getExtension` 有 `ConcurrentMap` 缓存，`Holder` + double-check）——按需与线程安全一起解决；
* **@Adaptive 动态代理**：编译期生成（或 javassist 字节码生成）代理类，运行时按参数选实现。这是 Dubbo 整个"面向接口 + 全链路可替换协议"架构的地基；
* **@Activate 条件激活**：按条件（如 `filter` 链上的自动激活）批量装扩展，实现"默认装一组，配置加减项"；
* **扩展点依赖注入（set 方法注入 + IoC）**：扩展实例里需要别的扩展点时，`ExtensionLoader` 自动装配。

四、Spring Boot 两代加载机制
------

Spring Boot 的自动装配本质也是 SPI，只是换了个目录，且换了两次：

```text
第一代（Boot 1.x ~ 2.6）：META-INF/spring.factories
    org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
    com.example.XxxAutoConfiguration,\
    com.example.YyyAutoConfiguration
    ← 全文件一个 properties，key 是框架内部接口，多个 key 共存

第二代（Boot 2.7+ / 3.x）：META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    每行一个自动配置类全限定名
    ← spring.factories 中 AutoConfiguration 相关 key 已删除，其余 key（如 EnableLoadTimeWeaver）保留
```

```java
// SpringFactoriesLoader（第一代）与 ImportCandidates.load（第二代）的姿势
List<String> names = SpringFactoriesLoader.loadFactoryNames(
        EnableAutoConfiguration.class, getClass().getClassLoader());
// Boot 3 里自动配置改走 AutoConfiguration.imports，
// 并配合 @AutoConfiguration + @ConditionalOnClass 等条件注解做装配裁剪
```

为什么换代：spring.factories 是"一个文件塞所有框架扩展点"的大杂烩，自动配置项动辄上百行、启动期解析慢。拆出专用文件后**自动配置的加载与去重（`@AutoConfigureOrder`/`@AutoConfigureBefore`）有了独立通道**，语义和性能都更干净。面试答出"2.7 引入、3.0 删旧 key"这条时间线即可加分。

五、JDBC 驱动加载：SPI 最老的实战案例
------

```java
// JDBC 4.0 之前：显式加载，驱动类静态块里 DriverManager.registerDriver(this)
Class.forName("com.mysql.cj.jdbc.Driver");

// JDBC 4.0+（JDK6）：DriverManager 静态块里跑 ServiceLoader
ServiceLoader<Driver> loadedDrivers = ServiceLoader.load(Driver.class);
// mysql-connector jar 里：
// META-INF/services/java.sql.Driver → com.mysql.cj.jdbc.Driver
// 于是 Class.forName 这行代码在纯 JDBC 4.0 驱动下可以删掉
```

这个案例有两个必考追问点。**其一，为什么需要 TCCL（线程上下文类加载器）**：`DriverManager` 在 java.sql（Bootstrap 加载器）里，看不见应用 classpath 上的驱动 jar。JDK 核心库反向加载应用实现，违反双亲委派。所以 `ServiceLoader.load(Driver.class)` 默认取 TCCL，把"用哪个类加载器"的决定权交回应用层。**这是 SPI 存在的深层原因：框架类加载器需要加载"不属于它"的实现**，Tomcat、JNDI 同理。

**其二，驱动为什么敢注册进 DriverManager 这种全局单例**：静态块自注册 + SPI 触发实例化，两条路最终都汇到 `registerDriver`。理解了这一点就理解了 SPI 与静态初始化的配合关系。

追问链
------

> **面试官：ServiceLoader 是懒加载的，为什么还说它"不按需加载"？**

懒的是类初始化（`forName` 的 initialize=false）与实例化时机（next 时才 new），但**选择权没有**。迭代器只能从头走到尾，取第 N 个实现必须把前 N 个全部实例化。JDK9+ 补了个 `stream()` 可以 `filter` 后再 `findFirst`，但过滤前实现类还是都加载了——只省实例化不省加载。

> **面试官：一个实现类构造器抛异常会发生什么？怎么防？**

抛 `ServiceConfigurationError`（继承 Error，catch Exception 接不住），当前迭代直接中断，后面的实现全部不可用。防御姿势：每个实现单独 try-catch Throwable 并记日志降级。或者迭代前先 `loader.stream()` 拿 `ServiceProvider`（只含类名不实例化），按名 `Class.forName` 后自己可控地实例化。把"发现"与"实例化"拆开，是生产代码对该语义的标准处理。

> **面试官：Dubbo 为什么要自己写 ExtensionLoader，不用 JDK 的？**

三个词：按名获取（IoC 风格 `getExtension("dubbo")`）、自适应（@Adaptive 运行时按 URL 参数路由）、依赖注入（扩展点套扩展点）。JDK SPI 只做发现不做装配。Dubbo 的微内核 + 插件架构（协议/序列化/负载均衡全部可换）要求装配能力，只能自建。附送细节：Dubbo 同时兼容读 META-INF/services，平滑过渡存量实现。

> **面试官：Spring Boot 3 的 starter 为什么突然不生效了？**

大概率是自动配置类还写在 spring.factories 里。Boot 3 已删除该 key，必须迁到 `META-INF/spring/...AutoConfiguration.imports`，并把 `@Configuration` 换成 `@AutoConfiguration`（其元数据支持条件排序）。这是 2.7→3.0 迁移的高频踩坑点。

> **面试官：为什么不直接 new，非要绕 SPI？**

解耦的方向问题：接口在调用方 jar，实现在提供方 jar，**调用方编译期根本引用不到实现类**。引用了就硬编码了。SPI 用"约定文件 + 反射"跨过编译期依赖，让实现在运行期才浮现——换实现换 jar 即可。代价是失去编译期类型检查与 IDE 导航。所以 SPI 只该用在"真有多实现插拔需求"的边界上（驱动、协议、自动配置），业务代码滥用 SPI 是反模式。

面试官视角小结
------

1. **机制三细节**：`forName(name, false, cl)` 只加载不初始化、next 时才实例化、失败抛 Error 且中断后续。能讲出这三点的候选人读过源码；
2. **约定**：META-INF/services/接口全限定名，多 jar 同名文件合并，这是"插拔"的物理基础；
3. **缺陷与根源**：不按需、非线程安全、无装配。根源是它是发现协议不是装配框架；
4. **Dubbo SPI**：@SPI 默认实现 + getExtension 按名 + @Adaptive 运行时路由 + @Activate 条件激活 + set 注入。逐条对上 JDK 缺陷；
5. **Spring Boot 两代**：spring.factories（1.x~2.6，properties 多 key）→ AutoConfiguration.imports（2.7+，每行一类），3.0 删旧 key 是迁移坑；
6. **JDBC 案例**：静态块自注册 → SPI 自动发现 → Class.forName 可删。TCCL 是"核心库反向加载应用实现"的钥匙，双亲委派的例外必须有它。

> 2026-10 新增（Round1 补缺）。
