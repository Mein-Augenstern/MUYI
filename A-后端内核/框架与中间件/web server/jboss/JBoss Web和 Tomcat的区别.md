JBoss Web 和 Tomcat 的区别 速查卡
====

一句话定位
----

* **Tomcat**：Apache 出品的轻量级 **Servlet/JSP 容器**（Servlet 规范参考实现）。
* **JBoss（现 WildFly）**：Red Hat 的**全栈 Java EE / Jakarta EE 应用服务器**，Web 层只是它的一部分。
* **JBoss Web**：JBoss 里的 Web 容器模块——早期 fork 自 Tomcat（JBossWeb），后期（WildFly 8+）被自研的 **Undertow** 取代。

容器定位对比
----

```
Tomcat（Web 容器）                 JBoss / WildFly（应用服务器）
┌──────────────────┐              ┌────────────────────────────┐
│  Servlet / JSP   │              │  Web 层: JBossWeb/Undertow │ ← 这块≈Tomcat
│  （HTTP 连接器 +  │              │  EJB 容器 / JMS / JTA      │
│   Servlet 引擎）  │              │  JPA / JNDI / CDI / JAAS   │
└──────────────────┘              │  集群 / HA / 部署管理控制台  │
  只管 Web，短小精悍               └────────────────────────────┘
                                    全家桶，重量级
```

* 类比：Tomcat 是"发动机"，JBoss 是"整车 + 4S 店"。

Servlet 规范实现
----

* Tomcat：**Servlet/JSP 规范官方参考实现**，规范新特性通常先在 Tomcat 落地。
* JBoss Web：**基于 Tomcat 源码 fork**（加了对 JBoss 集成、APR/native 连接器的改造），Servlet 规范行为与 Tomcat 高度一致——所以"JBoss Web 和 Tomcat 的区别"本质是**外层容器能力**的区别，而非 Servlet 引擎本身的区别。
* WildFly 8+ 改用 **Undertow**：NIO、按需堆叠（Servlet 过滤器链可裁剪），也是 Spring Boot 可选内嵌容器之一。

性能与资源
----

| 维度 | Tomcat | JBoss/WildFly |
|---|---|---|
| 启动速度 | 快（秒级） | 慢（服务多，需预热） |
| 内存占用 | 小 | 大 |
| 默认 IO | Tomcat 8+ 默认 NIO（旧版 BIO） | Undertow 天生 NIO |
| 并发吞吐 | 纯 Web 场景足够，调优后表现好 | Web 层相当；EJB/集群场景提供池化、分布式事务加成 |
| 单机多实例 | 常见玩法（轻） | 少见，倾向单实例管全部应用 |

生态与治理
----

* Tomcat：社区最大、文档最全、使用率最高；Spring Boot 默认内嵌容器；遇到问题好排查。
* JBoss：Red Hat 商业支持（JBoss EAP 订阅制），带管理控制台/CLI（domain 模式可集中管多机），适合传统企业级 Java EE 与 EJB/集群/分布式事务需求；社区版 2014 年起更名 **WildFly**。
* 演进趋势：两者都被容器化/微服务浪潮压缩——新项目多为 Spring Boot + 内嵌 Tomcat/Undertow，应用服务器退守存量系统。

选型（面试收尾句）
----

* 只跑 Web/REST（含 Spring 全家桶）→ **Tomcat**：轻、快、生态大。
* 需要 EJB、JMS 服务端、JTA 分布式事务、域管理、Java EE 认证栈 → **WildFly/JBoss EAP**。
* Spring Boot 想要更激进的吞吐/内存 → 可把内嵌容器换成 **Undertow**（源自 JBoss 系，佐证两者技术同源）。

> 2026-10 依据主题重写（原文件为空壳）。
