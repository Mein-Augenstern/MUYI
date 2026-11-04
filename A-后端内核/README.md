# A 轨 · 后端内核

> 全书主轨道（256 篇）：Java 后端十年沉淀 + 新语言实战对照（Go/Python/Rust）。
> 深度标准：原理 + 真实代码 + 追问链 + 面试官视角小结；实战反哺篇可验证到真实类名/文件路径。

## 目录规划（2026-11-04 实况 256 篇）

| 目录 | 篇数 | 范围 | 代表作 |
|---|---|---|---|
| java/ | 123 | 语言内核主线：并发/集合/JVM/IO/基础/规范 | [深度解读线程池设计思想及源码实现](./java/Java%20并发/深度解读%20java%20线程池设计思想及源码实现.md)、[虚拟线程 vs 线程池 JDK21](./java/Java%20并发/虚拟线程-vs-线程池-JDK21.md)、[Spring IOC 容器源码分析](./框架与中间件/Spring/Spring%20IOC容器源码分析.md) |
| 框架与中间件/ | 83 | Spring 家族、数据库（MySQL/RocksDB）、Redis、MQ、Web Server、安全 | [RocksDB 架构设计系列](./框架与中间件/数据库/)、Spring 源码系列 27 篇 |
| 计算机基础/ | 17 | 网络、操作系统、组成原理 | TCP/IO 多路复用专题 |
| cheatsheet/ | 17 | 速查手册（git/vim/shell/docker 等） | 工具命令速查 |
| 面经/ | 8 | 真实面试复盘 | 追问链实录 |
| go/ | 6 | **从 Java 视角看 Go 系列**：开篇/上手实战/场景全景 + 并发三件套/错误处理/slice与map 值语义 | [并发三件套 goroutine-channel-context](./go/从Java视角看Go-并发三件套-goroutine-channel-context.md) |
| python/ | 1 | 从 Java 视角看 Python 工程化 | [开篇](./python/从Java视角看Python工程化-开篇.md) |
| rust/ | 1 | Rust 起步（对照 Java 所有权语义） | — |

## java/ 二级结构（123 篇主线）

| 子目录 | 篇数 | 子目录 | 篇数 |
|---|---|---|---|
| java基础 | 39 | JVM | 25 |
| Java 并发 | 32 | Java集合 | 13 |
| Java IO | 7 | 开发规范 / JMX / SPI | 7 |

## 框架与中间件/ 二级结构（83 篇）

| 子目录 | 篇数 | 子目录 | 篇数 |
|---|---|---|---|
| 数据库 | 29 | 消息队列 / redis | 12 |
| Spring | 27 | 安全 / MyBatis / web server | 15 |

## 写作规范

1. 每篇遵循深度四件套：原理 + 真实代码 + 追问链 + 面试官视角小结。
2. 实战反哺的文章必须引用真实类名/文件路径（如 star-site 系列仓库），可追溯。
3. 新语言轨（go/python/rust）一律走"从 Java 视角"对照体例——受众是十年 Java 老兵，锚点先行。
