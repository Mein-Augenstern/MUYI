# JMX 速查卡

JMX 是什么
====

1. JMX（Java Management Extensions，Java 管理扩展）。它为应用程序/设备/系统植入管理功能的框架，跨平台、跨体系结构、跨传输协议。
2. 典型场景：监控 Java 程序运行情况（JConsole / Visual VM）。中间件监控、在线修改配置（如清空缓存、重载配置文件）。
3. 优点：应用易被管理。架构可伸缩，Agent 服务可插拔（部署/取消部署）。只定义接口、允许多种实现。
4. 任何 Java 程序都可启用 JMX。JConsole 展示的内存/线程/类加载等通用信息都基于 JMX。

架构三层与核心概念
----

| 概念 | 一句话 |
|------|--------|
| MBean | Managed Bean，被管理的资源实例，暴露属性和方法供外界读取/操纵 |
| MBeanServer | MBean 容器，管理 MBean 并代理外界访问，提供按名字注册机制 |
| JMX Agent | 一个 Java 进程 = MBeanServer + 多个 MBeanService（服务也以 MBean 发布） |
| ObjectName | MBean 在 MBeanServer 中的唯一标识 |
| Adapter | 用 Internet 协议联系 Agent（SNMP/HTTP Adapter），Agent 端处理协议细节 |
| Connector | 类 RPC 方式访问，两端都有对象处理请求应答（如 RMI Connector） |

1. 三层结构：Instrumentation（MBean 规范/资源层）→ Agent（MBeanServer+服务+Adapter/Connector）→ Distributed（远端访问接口）。
2. 一个 JVM 可有多个 MBeanServer。每个可注册多个 rmi/http 端口。platformMBeanServer 由 JVM 创建并内置系统 MBean（CPU/内存/线程这类）。
3. Adapter 与 Connector 区别：Adapter 走协议（SNMP/HTTP），Connector 走 RPC 式请求应答（RMI）；Agent 可带任意多个 Adapter。

MBean 编程四步
----

1. 定义接口，**必须以 MBean 结尾**（如 `ServerInfoMBean`，规范强制）。
2. 实现类实现该接口（Standard MBean）。
3. 注册：`ManagementFactory.getPlatformMBeanServer()` → `new ObjectName("xx:type=Echo")` → `mbs.registerMBean(mbean, name)`；可用 `mbs.invoke(name, "print", ...)` 调方法。
4. JConsole 本地自动发现进程，双击进入。MBean 页签可读属性、点击按钮调方法（不止展示，还能执行方法）。

本地 / 远程连接
----

1. 本地：进程启动后在用户目录写 localConnectorAddress，jps/jinfo/jmap/jstat 按此地址连接。
2. 远程两种方式：
   - 代码方式：`LocateRegistry.createRegistry(8081)` + `JMXConnectorServerFactory.newJMXConnectorServer(new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:8081/jmxrmi"), null, server)` 后 start()。
   - 启动参数（JConsole/VisualVM 远程连接，Tomcat 监控同理）：

```
-Dcom.sun.management.jmxremote=true                # JMX 代理开关
-Djava.rmi.server.hostname=<服务器IP>
-Dcom.sun.management.jmxremote.port=10086          # 监听端口
-Dcom.sun.management.jmxremote.ssl=false           # 是否 SSL
-Dcom.sun.management.jmxremote.authenticate=false  # 是否密码验证
```

与 HTTP 方案对比
----

1. HTTP 能做的 JMX 都能做，但监控类需求 JMX 更省事：不用起 http 服务、写接口、转 JSON。
2. JMX 是更具体、应用面窄、轻量的 C/S 工具；不需要大张旗鼓建 server/client 时就用它。

Spring Boot 中使用
====

1. Spring Boot 2.2 之前 JMX 默认启用；2.2 及之后默认关闭，需显式配置 `spring.jmx.enabled=true`。启用后 Actuator 端点以 MBean 形式暴露到 `org.springframework.boot` 域，JConsole/VisualVM 连上即可查看。
2. 自定义 MBean 常用注解（AnnotationMBeanExporter 自动注册）：
   - `@ManagedResource(objectName = "com.xx:type=MyApp")` 标在类上（注册为 MBean）；
   - `@ManagedAttribute` 标在 getter/setter 上暴露属性；
   - `@ManagedOperation` 标在方法上暴露操作。
3. 配合 `@Component` 交给 Spring 管理；`@ManagedMetric` 可暴露指标类属性。
4. 自定义端点：`@Endpoint(id = "xxx")` + `@ReadOperation/@WriteOperation/@DeleteOperation`，可通过 JMX 与 Web 双通道暴露。
5. 远程访问同样走 `com.sun.management.jmxremote.*` 启动参数；生产建议启用 authenticate + SSL。

> 内容来源：[What JMX](../java/JMX/JMX%E6%A6%82%E5%BF%B5%E4%B8%8E%E4%BD%93%E7%B3%BB%E7%BB%93%E6%9E%84.md) · [JMX的用处及用法](../java/JMX/JMX的用处及用法.md) · Springboot 篇原链接已失效，2026-10 依据主题重写。
