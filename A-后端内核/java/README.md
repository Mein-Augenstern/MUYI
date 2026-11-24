Java 系列导读
====

> 实况 **123 篇**。Java 语言内核主线，按"打地基 → 并发 → 集合 → IO → JVM"的顺序分章；每章是一个子目录，章内文章可独立检索，连读成体系。读 Spring / MyBatis 源码之前，至少把 java基础 一章过一遍。

## 目录

| 章 | 篇数 | 回答的问题 | 从这篇开始 |
|---|---|---|---|
| [java基础/](java基础/) | 39 | 反射、注解、泛型、动态代理、异常体系——框架源码的建筑材料 | [Java反射](java基础/Java反射.md)、[JDK动态代理源码分析](java基础/JDK动态代理源码分析.md) |
| [Java 并发/](Java%20并发/) | 32 | 线程怎么管、锁怎么加、线程池怎么设计，JDK21 之后并发怎么变 | [深度解读 java 线程池设计思想及源码实现](Java%20并发/深度解读%20java%20线程池设计思想及源码实现.md)、[AQS源码精读一-加锁与队列](Java%20并发/AQS源码精读一-加锁与队列.md) |
| [Java集合/](Java集合/) | 13 | HashMap 深源码（1.7 成环 / 1.8 树化 / 扩容）、ArrayList 扩容、CopyOnWrite | [HashMap底层数据结构](Java集合/HashMap底层数据结构.md)、[HashMap与ConcurrentHashMap源码全解析](Java集合/HashMap与ConcurrentHashMap源码全解析.md) |
| [Java IO/](Java%20IO/) | 7 | BIO 到 NIO 的模型演进、Reactor 模式——Netty 的地基 | [Java NIO原理](Java%20IO/Java%20NIO原理.md)、[非阻塞IO与异步IO辨析](Java%20IO/非阻塞IO与异步IO辨析.md) |
| [JVM/](JVM/) | 25 | 内存管理（模型 / 垃圾收集 / 类加载）与线上性能故障处理 | [Java内存结构](JVM/JVM内存管理/Java内存结构.md)、[故障诊断-高CPU占用](JVM/JVM性能兼容、故障处理/故障诊断-高CPU占用.md) |
| [开发规范/](开发规范/) | 3 | 工程约定与踩坑：构建三统一、调用外部接口的失败处理 | [调用外部接口的失败处理](开发规范/调用外部接口的失败处理.md) |
| [JMX/](JMX/) | 3 | JMX 概念、用法，以及在 Spring Boot 里的使用 | [JMX概念与体系结构](JMX/JMX概念与体系结构.md) |
| SPI | 1 | JDK SPI 原理与框架实战 | [JDK-SPI原理与框架实战](SPI/JDK-SPI原理与框架实战.md) |

## 推荐阅读路径

* **面试主线**：java基础 的反射与动态代理 → Java 并发 全章 → Java集合 的 HashMap 系列 → JVM 内存管理；
* **读源码前**：java基础 全章 + [Java NIO原理](Java%20IO/Java%20NIO原理.md)，再去 [框架与中间件](../框架与中间件/) 读 Spring 与 Netty；
* **线上排障**：[故障诊断-高CPU占用](JVM/JVM性能兼容、故障处理/故障诊断-高CPU占用.md)、[故障诊断-OutOfMemoryError](JVM/JVM性能兼容、故障处理/故障诊断-OutOfMemoryError.md)、[JVM参数指南](JVM/JVM性能兼容、故障处理/JVM参数指南.md)；
* **追新版本**：[JDK17-21语言新特性迁移指南](java基础/JDK17-21语言新特性迁移指南.md) → [虚拟线程-vs-线程池-JDK21](Java%20并发/虚拟线程-vs-线程池-JDK21.md) → [JDK25并发终局-ScopedValues与结构化并发](Java%20并发/JDK25并发终局-ScopedValues与结构化并发.md)。

速查版见 [cheatsheet](../cheatsheet/)；源码分析与生产复盘在各章交错出现。
