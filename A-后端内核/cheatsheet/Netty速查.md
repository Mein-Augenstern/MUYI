# Netty 速查卡

定位
====

1. 基于 NIO 的 client-server 框架，快速开发高性能网络程序；简化并优化 TCP/UDP 套接字编程。
2. 支持 FTP/SMTP/HTTP 及各种二进制、文本协议；Dubbo、RocketMQ、Elasticsearch、gRPC 都在用。

为什么用 Netty（对比 JDK NIO）
----

1. 统一 API 支持多种传输（阻塞/非阻塞）；简单强大的线程模型。
2. 自带编解码器解决 TCP 粘包/拆包；自带协议栈；完整 SSL/TLS 支持。
3. 更高吞吐、更低延迟、更少内存复制；成熟稳定、社区活跃。

核心组件
----

| 组件 | 作用 |
|------|------|
| Channel | 网络操作抽象（bind/connect/read/write）；常用 NioServerSocketChannel（服务端）/NioSocketChannel（客户端），对应 BIO 的 ServerSocket/Socket |
| EventLoop | 最核心概念：处理连接生命周期中的事件，监听网络事件并调用处理器；一个 EventLoop : 一个线程，Channel 注册其上保证线程安全 |
| EventLoopGroup | 一组 EventLoop（即线程池）；Boss 组接收连接，Worker 组处理读写 |
| ChannelFuture | IO 全异步；addListener 注册回调，sync() 可变同步等待 |
| ChannelHandler | 消息具体处理器（读写、连接等业务逻辑） |
| ChannelPipeline | Handler 链容器，入站/出站事件沿链传播；Channel 创建时自动分配专属 pipeline，addLast 添加 |

1. NioEventLoopGroup 无参构造默认线程数 = `Math.max(1, io.netty.eventLoopThreads, CPU 核数 × 2)`，即 **CPU 核数 × 2**。
2. Boss 常设 1 个线程，Worker 默认核数 × 2。

Bootstrap / ServerBootstrap
----

1. Bootstrap：客户端启动引导，`connect(host, port)` 建连（UDP 场景用 bind）；只需配一个线程组。
2. ServerBootstrap：服务端启动引导，`bind(port)` 绑端口等待连接；配两个线程组（boss+worker）。
3. 服务端启动步骤：建两组 NioEventLoopGroup → new ServerBootstrap → `.group(boss, worker)` → `.channel(NioServerSocketChannel.class)` → `.childHandler(ChannelInitializer 中 addLast 业务 Handler)` → `bind(port).sync()` → `closeFuture().sync()` → finally `shutdownGracefully()`。
4. 客户端：一组 EventLoopGroup → Bootstrap → `.group()` → `.channel(NioSocketChannel.class)` → `.handler(...)` → `connect().addListener(判断成功/失败).sync()`。

线程模型（基于 Reactor）
----

| 模型 | 结构 | 适用 |
|------|------|------|
| 单线程 | 1 线程干全部 accept/read/decode/process/encode/send | 低负载 |
| 多线程 | 1 个 Acceptor 线程 + NIO 线程池处理 | 绝大部分场景 |
| 主从多线程 | 主线程池选 Acceptor 线程绑端口，Sub 线程池负责 IO 读写 | 高并发 |

- Reactor：事件驱动 + 多路复用（Selector）把事件分发给 Handler，适合海量 IO。

TCP 粘包 / 拆包
----

1. 现象：多次发送的消息在接收端"粘"在一起或被"拆"开（TCP 字节流无消息边界）。
2. Netty 自带解码器：LineBasedFrameDecoder（换行分隔）、DelimiterBasedFrameDecoder（自定义分隔符，前者是其特例）。FixedLengthFrameDecoder（定长）、LengthFieldBasedFrameDecoder（长度域）。
3. 自定义序列化编解码：Java Serializable 性能差不推荐；常用 Kryo/FST（Java 专用）、Protostuff/ProtoBuf/Thrift/Avro/MsgPack（跨语言）、Hessian2/JSON。

长连接与心跳
----

1. 长连接：一次读写后不关闭连接，后续复用。省去反复握手/挥手开销，适合频繁请求。
2. 心跳机制：idle 状态下定期互发特殊包（PING-PONG）确认对方在线。TCP 自带 SO_KEEPALIVE 但不灵活，通常在应用层实现。
3. Netty 核心：`IdleStateHandler`，空闲事件触发后可主动关闭无效连接。

零拷贝
----

1. OS 层零拷贝：避免用户态与内核态之间来回拷贝。
2. Netty 层零拷贝（数据操作优化）：
   - CompositeByteBuf：多个 ByteBuf 合为逻辑整体，避免合并拷贝；
   - ByteBuf.slice()：拆分出共享底层存储的子 Buffer；
   - FileRegion 包装 fileChannel.transferTo()：文件缓冲区直接发送到目标 Channel，避免循环 write 拷贝。

> 内容来源：[原文](../框架与中间件/web%20server/netty/Netty常见面试题.md) · 2026-10 整理回填。
