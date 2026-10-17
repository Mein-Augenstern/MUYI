看在前面
====

> * <a href="https://baijiahao.baidu.com/s?id=1668247436055457456&wfr=spider&for=pc">高级Java程序员都知道的JMX扩展，SpringBoot中也有使用的知识点？</a>

JMX 快速回顾
====

JMX（Java Management Extensions，Java 管理扩展）是一个为应用程序植入管理功能的框架，最常见的作用是监控 Java 程序的基本信息和运行情况（内存、线程、类加载等），任何 Java 程序都可以开启 JMX，然后使用 JConsole / VisualVM 查看。

JMX 体系结构中的三个核心角色
----

* **MBean（Managed Bean）**：存放被管理资源的 Java 对象，分为 Standard MBean（接口 + 实现类，接口名必须以 MBean 结尾）、Dynamic MBean、Model MBean、Open MBean 等类型。
* **MBeanServer**：MBean 的注册中心，提供注册、注销、按 ObjectName 查找和调用 MBean 的能力，是 JMX 的核心组件。
* **Connector / Adaptor**：对外提供访问入口。Connector（如 RMIConnector）基于远程协议让客户端像调用本地方法一样操作 MBean；Adaptor（如 HtmlAdaptorServer）把 MBean 映射成 HTTP 页面。

普通 Java 程序开启 JMX 只需追加 JVM 参数
----

```java
-Djava.rmi.server.hostname=127.0.0.1
-Dcom.sun.management.jmxremote.port=1000
-Dcom.sun.management.jmxremote.ssl=false
-Dcom.sun.management.jmxremote.authenticate=false
```

Spring Boot 与 JMX 的整合
====

默认开关：spring.jmx.enabled
----

* Spring Boot 2.2 之前：JMX 默认开启，所有 Actuator 端点默认会注册为 MBean。
* Spring Boot 2.2 及之后：JMX **默认关闭**，需要手动打开：

```properties
spring.jmx.enabled=true
```

* 开启后 Spring Boot 会自动定位/创建最合适的 `MBeanServer`，并以 `mbeanServer` Bean 的形式暴露给应用使用，`@ManagedResource` 标注的 Bean 和 Endpoint MBeans 都会注册到该 Server 上。

自定义 MBean：注解方式
----

用 Spring 的注解把任意 Bean 发布为 MBean，无需再手写 MBean 接口和 `MBeanServer.registerBean`：

```java
@Component
@ManagedResource(objectName = "com.muyi:type=Name=AppStats",
        description = "应用运行时统计")
public class AppStatsMBean {

    private AtomicInteger dbQueryCount = new AtomicInteger(0);

    public void incrDbQuery() {
        dbQueryCount.incrementAndGet();
    }

    @ManagedAttribute(description = "累计数据库操作次数")
    public int getDbQueryCount() {
        return dbQueryCount.get();
    }

    @ManagedOperation(description = "清空统计计数")
    public void reset() {
        dbQueryCount.set(0);
    }
}
```

* `@ManagedResource`：类级注解，声明该 Bean 要导出为 JMX MBean，`objectName` 必须符合 `domain:key=value` 格式。
* `@ManagedAttribute`：只暴露 getter/setter 属性。
* `@ManagedOperation`：暴露任意方法，可在 JConsole 中直接点击执行（如上例的 reset，可用来做清缓存、刷新配置等运维动作）。

Actuator 端点通过 JMX 暴露
----

引入 `spring-boot-starter-actuator` 后，端点可同时通过 HTTP 和 JMX 两种方式暴露：

```properties
# 暴露所有端点到 JMX（JMX 暴露默认比 HTTP 宽松）
management.endpoints.jmx.exposure.include=*
management.endpoints.jmx.exposure.exclude=env,beans
```

* 用 JConsole / VisualVM 连接进程后，在 `org.springframework.boot` 域下可以看到 `EndpointMBean`，逐个查看 health、metrics、threaddump 等信息。
* 典型排查场景：生产只开 JMX 端点不开 HTTP 端口，通过跳板机上的 JConsole 拉取 thread dump / heap 指标，避免暴露 HTTP 攻击面。

远程与容器环境
----

* 远程连接需要开启 JVM 的 `com.sun.management.jmxremote.port` 参数（见上文）。
* Docker/K8s 环境注意：RMI 端口与 `java.rmi.server.hostname` 要指向宿主机可达地址，否则 JConsole 连不上：

```java
-Dcom.sun.management.jmxremote.port=9010
-Dcom.sun.management.jmxremote.rmi.port=9010
-Djava.rmi.server.hostname=<宿主机IP>
```

* 更通用的做法是引入 **Jolokia**，把 JMX 的 MBean 操作翻译成 HTTP/JSON，便于 Prometheus 抓取和网页端访问：

```xml
<dependency>
    <groupId>org.jolokia</groupId>
    <artifactId>jolokia-core</artifactId>
</dependency>
```

小结
====

* JMX 的价值：让运行中的应用"可观测、可操作"，通用信息（内存/线程/类加载）自动具备，专有信息（执行了多少次数据库操作、队列积压多少任务）通过 MBean 定制暴露。
* Spring Boot 整合要点：`spring.jmx.enabled=true`（2.2+）→ `@ManagedResource/@ManagedAttribute/@ManagedOperation` 暴露自定义指标 → Actuator JMX 端点 → JConsole/VisualVM/Jolokia 查看。
* 与自建 HTTP 接口相比，JMX 能完成的事情 HTTP 也能完成，但监控类场景用 JMX 更标准、更省事，且天然集成在 JDK 工具链中。

> 原链接已失效，2026-10 依据主题重写。
