框架与中间件系列导读
====

> 实况 **91 篇**。后端工程师每天都在用的那一层：框架、存储、缓存、消息、Web 容器。
> 按组件分目录；体量大的（Spring、数据库、Redis、消息队列）各自再分章，各有系列导读。

## 目录

| 模块 | 篇数 | 内容 | 先看 |
|---|---|---|---|
| [Spring/](Spring/) | 27 | IoC 与 Bean → AOP → Web 机制 → 事务与升级（Boot3 / Boot4） | [Spring 系列导读](Spring/) |
| [数据库/](数据库/) | 30 | MySQL 核心 → 分库分表与分布式 ID → 存储引擎与列存 | [数据库系列导读](数据库/) |
| [redis/](redis/) | 9 | 基础内功 → 缓存实战 → 集群与高可用 → 版本与生态 | [Redis 系列导读](redis/) |
| [消息队列/](消息队列/) | 6 | 可靠性三件套（不丢不重、顺序、事务与延迟）→ 选型与演进 | [消息队列系列导读](消息队列/) |
| [web server/](web%20server/) | 9 | Netty 源码五篇、Tomcat 三篇，外加 JBoss 与 Tomcat 的对比 | [Web Server 系列导读](web%20server/)、[JBoss Web与Tomcat区别](web%20server/jboss/JBoss%20Web与Tomcat区别.md) |
| [安全/](安全/) | 4 | Web 攻防、加密算法、TLS 实践、支付回调验签与幂等 | [Web安全三攻三防](安全/Web安全三攻三防.md) |
| [zookeeper/](zookeeper/) | 3 | ZAB 与 Watcher、分布式锁实战、协调中间件选型 | [ZooKeeper 系列导读](zookeeper/) |
| [MyBatis/](MyBatis/) | 2 | 执行流程与缓存体系、常见面试题 | [MyBatis 导读](MyBatis/) |
| [config/](config/) | 1 | 配置中心选型：Apollo 对比 Nacos 与 Spring Cloud Config | [配置中心选型-Apollo对比Nacos与SpringCloudConfig](config/配置中心选型-Apollo对比Nacos与SpringCloudConfig.md) |

## 推荐阅读路径

* **面试冲刺**：[MySQL索引原理与面试要点](数据库/01-MySQL核心/MySQL索引原理与面试要点.md) → [缓存穿透-击穿-雪崩](redis/02-缓存实战/缓存穿透-击穿-雪崩.md) → [Spring IOC容器源码分析](Spring/01-IoC与Bean/Spring%20IOC容器源码分析.md)；
* **生产救火**：[线上慢SQL复盘-范围条件断前缀与回表风暴](数据库/01-MySQL核心/线上慢SQL复盘-范围条件断前缀与回表风暴.md)、[Insert into select语句事故分析](数据库/01-MySQL核心/Insert%20into%20select语句事故分析.md)、[K8s生产排障手册](../../D-云原生/容器技术/Kubernetes/K8s生产排障手册.md)；
* **选型立项**：[协调中间件选型-ZooKeeper对比etcd与Nacos](zookeeper/协调中间件选型-ZooKeeper对比etcd与Nacos.md) → [MQ选型对比-Kafka-RocketMQ-RabbitMQ](消息队列/02-选型与演进/MQ选型对比-Kafka-RocketMQ-RabbitMQ.md) → [缓存架构选型-本地Caffeine对比分布式Redis与多级缓存](redis/02-缓存实战/缓存架构选型-本地Caffeine对比分布式Redis与多级缓存.md)。

原理篇在 [C-架构](../../C-架构/)（分布式事务、一致性、协调），落地篇在这里，两边互相引用。
