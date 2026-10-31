# 台账 B8 · javadoop 经典系列迁移（37 篇）

> 2026-10-31 执行。来源：javadoop.com（Java 源码深读经典站，55 篇全量研读）。
> 原则：**研读消化后按库内体例重写，不逐字搬运**；每篇文末规范标注
> `> 来源：javadoop《原文标题》（URL）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。`

## 迁移清单（37 篇 / 5242 行）

| 组 | 篇数 | 去向 | 内容 |
|---|---|---|---|
| JUC 深读 | 6 | java/Java 并发、Java集合 | AQS 源码精读一二三（220/205/183 行）、ReentrantReadWriteLock、Phaser、HashMap与ConcurrentHashMap源码全解析 |
| Spring 源码 | 5 | 框架与中间件/Spring | IOC 容器源码（refresh() 十二步）、AOP 使用指南与源码解析、AspectJ、Properties 体系 |
| NIO/Netty | 6 | java/Java IO、web server/tomcat、netty | NIO 三组件、非阻塞与异步 IO 辨析、Tomcat NIO 源码、Netty 源码一二三 |
| JVM 与杂项 | 6 | JVM/JVM内存管理、java基础、数据库 | Metaspace 深入、字符串常量池（按原文勘误后的正确口径撰写）、Java与MySQL日期时间、设计模式全览（329 行 18 模式）、单例安全性、Guava EventBus |
| 数据基建 | 11 | 数据库、C-架构/方法论 | ClickHouse、Cassandra、RocksDB×2、Parquet×5（总起+四篇成串）、Apache Arrow、SigNoz |
| Rust 与 AI 工具 | 3 | rust/（新轨）、B-AI工程/工程工具/（新目录）、F-前端 | 写给Java程序员的Rust入门（与 Go/Python 轨构成语言对照系）、ClaudeCode使用实践、跨域请求全解（补 Spring 5.3+ allowedOriginPatterns 等现代注脚） |

## 跳过清单（9 篇，库内已有覆盖）

解读并发队列 BlockingQueue、深度解读线程池源码、Threads and Locks、Java 并发基础内存模型（库内 JVM 内存模型篇）、ZGC（库内 GC现代化篇）、HotSpot JVM 内存管理（库内 JVM内存管理 系列）、短链服务（库内 短链系统设计）、线程池使用姿势（与线程池面试问题总结重叠）、swagger 二级目录小技巧（时效性低）。

## 特殊处理记录

* **Netty（三）原文为空占位页**（"还在组织内容……"从未成文）：按系列脉络基于 Netty 4.1 源码续写（PoolArena/PoolChunk/PoolSubpage/引用计数/零拷贝），来源标注如实注明"原文为占位、系续写"，不做虚假归属
* 互链建设：AQS 三部曲互链并挂接既有 AQS&ReentrantLock；Parquet 五篇成串；Rust↔Go↔Python 语言对照系互链；SigNoz 挂观测性三件套
* 重写时顺手修正的事实性内容均标注于正文（如字符串常量池 JDK8 仍在堆）

## 验收（kb-upkeep 全量）

* 来源标注合规：37/37（含 URL 可达——代理逐篇抓取成功即证活）
* 体例结构（追问链+面试官小结）：37/37
* 体检：空壳 0 / 占位 0 / 相对死链 0 / 外链死图 0 / 文字外链死链 0
* 未改动任何既有文件（git status 仅新增）
