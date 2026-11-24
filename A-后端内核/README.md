# A 轨 · 后端内核

> 实况 **264 篇**。全书主轨道：Java 后端的深度沉淀，加上以 Java 为锚点看 Go / Python / Rust。
> 深度标准：原理 + 真实代码 + 追问链 + 面试官视角小结；实战反哺篇可验证到真实类名/文件路径。

## 从哪进

| 你想做什么 | 从这里进 |
|---|---|
| 系统读 Java | [Java 系列导读](java/)：语言内核主线，分章递进 |
| 读框架与中间件 | [框架与中间件系列导读](框架与中间件/)：Spring / 数据库 / Redis / MQ / Web Server |
| 补网络与 Linux | [计算机基础](计算机基础/)：TCP / HTTP / TLS 与排查命令 |
| 面试前快速过一遍 | [cheatsheet](cheatsheet/)：速查卡片，深度见对应篇目 |
| 看真实面试复盘 | [面经](面经/) |
| 按题找答案 | [自测清单](../自测清单.md)：每题挂答案定位 |

## 目录

| 目录 | 篇数 | 范围 | 代表作 |
|---|---|---|---|
| [java/](java/) | 123 | 语言内核主线：基础 / 并发 / JVM / 集合 / IO / 规范 | [深度解读 java 线程池设计思想及源码实现](java/Java%20并发/深度解读%20java%20线程池设计思想及源码实现.md)、[虚拟线程-vs-线程池-JDK21](java/Java%20并发/虚拟线程-vs-线程池-JDK21.md) |
| [框架与中间件/](框架与中间件/) | 91 | Spring、数据库、Redis、消息队列、Web Server、安全、zookeeper、MyBatis、配置中心 | [Spring IOC容器源码分析](框架与中间件/Spring/01-IoC与Bean/Spring%20IOC容器源码分析.md)、[支付回调的HMAC验签与幂等设计](框架与中间件/安全/支付回调的HMAC验签与幂等设计.md) |
| [计算机基础/](计算机基础/) | 17 | 通信网络、Linux、专业术语 | [TCP 三次握手和四次挥手](计算机基础/通信网络/TCP%20三次握手和四次挥手.md) |
| [cheatsheet/](cheatsheet/) | 17 | 速查手册（JVM / 并发 / 数据库 / Spring…） | [JVM速查](cheatsheet/JVM速查.md) |
| [面经/](面经/) | 8 | 真实面试复盘 | [滴滴-后端面经](面经/滴滴-后端面经.md) |
| [go/](go/) | 6 | 从 Java 视角看 Go：开篇、上手实战、场景全景，加并发三件套、错误处理、slice 与 map 的值语义 | [从Java视角看Go-并发三件套-goroutine-channel-context](go/从Java视角看Go-并发三件套-goroutine-channel-context.md) |
| [python/](python/) | 1 | 从 Java 视角看 Python 工程化 | [开篇](python/从Java视角看Python工程化-开篇.md) |
| [rust/](rust/) | 1 | 写给 Java 程序员的 Rust 入门 | [写给Java程序员的Rust入门](rust/写给Java程序员的Rust入门.md) |

## 写作规范

1. 每篇遵循深度四件套：原理 + 真实代码 + 追问链 + 面试官视角小结。
2. 实战反哺的文章必须引用真实类名/文件路径（如 star-site 系列仓库），可追溯。
3. 新语言轨（go/python/rust）一律走"从 Java 视角"对照体例——受众是十年 Java 老兵，锚点先行。
