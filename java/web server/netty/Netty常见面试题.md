看在前面
------

> * <a href="https://www.cnblogs.com/hulianwangjiagoushi/p/13143938.html">你要的Netty常见面试题总结，我面试回来整理好了！</a>

概览
------

* Netty 是什么？

* 为什么要用 Netty？

* Netty 应用场景了解么？

* Netty 核心组件有哪些？分别有什么作用？

* EventloopGroup 了解么?和 EventLoop 啥关系?

* Bootstrap 和 ServerBootstrap 了解么？

* NioEventLoopGroup 默认的构造函数会起多少线程？

* Netty 线程模型了解么？

* Netty 服务端和客户端的启动过程了解么？

* Netty 长连接、心跳机制了解么？

* Netty 的零拷贝了解么？

Netty 是什么？
------

面试官 ：介绍一下自己对 Netty 的认识吧！小伙子。

我 ：好的！那我就简单用 3 点来概括一下 Netty 吧！

1、Netty 是一个 基于 NIO 的 client-server(客户端服务器)框架，使用它可以快速简单地开发网络应用程序。

2、它极大地简化并优化了 TCP 和 UDP 套接字服务器等网络编程,并且性能以及安全性等很多方面甚至都要更好。

3、支持多种协议 如 FTP，SMTP，HTTP 以及各种二进制和基于文本的传统协议。

用官方的总结就是：Netty 成功地找到了一种在不妥协可维护性和性能的情况下实现易于开发，性能，稳定性和灵活性的方法。

除了上面介绍的之外，很多开源项目比如我们常用的 Dubbo、RocketMQ、Elasticsearch、gRPC 等等都用到了 Netty。

网络编程我愿意称 Netty 为王 。

为什么要用 Netty？
------

面试官 ：为什么要用 Netty 呢？能不能说一下自己的看法。

我 ：因为 Netty 具有下面这些优点，并且相比于直接使用 JDK 自带的 NIO 相关的 API 来说更加易用。

* 统一的 API，支持多种传输类型，阻塞和非阻塞的。

* 简单而强大的线程模型。

* 自带编解码器解决 TCP 粘包/拆包问题。

* 自带各种协议栈。

* 真正的无连接数据包套接字支持。

* 比直接使用 Java 核心 API 有更高的吞吐量、更低的延迟、更低的资源消耗和更少的内存复制。

* 安全性不错，有完整的 SSL/TLS 以及 StartTLS 支持。

* 社区活跃

* 成熟稳定，经历了大型项目的使用和考验，而且很多开源项目都使用到了 Netty， 比如我们经常接触的 Dubbo、RocketMQ 等等。

Netty 应用场景了解么？
------

面试官 ：能不能通俗地说一下使用 Netty 可以做什么事情？

我 ：凭借自己的了解，简单说一下吧！理论上来说，NIO 可以做的事情 ，使用 Netty 都可以做并且更好。Netty 主要用来做网络通信 :

1、**作为 RPC 框架的网络通信工具** ：我们在分布式系统中，不同服务节点之间经常需要相互调用，这个时候就需要 RPC 框架了。不同服务节点之间的通信是如何做的呢？可以使用 Netty 来做。比如我调用另外一个节点的方法的话，至少是要让对方知道我调用的是哪个类中的哪个方法以及相关参数吧！

2、**实现一个自己的 HTTP 服务器** ：通过 Netty 我们可以自己实现一个简单的 HTTP 服务器，这个大家应该不陌生。说到 HTTP 服务器的话，作为 Java 后端开发，我们一般使用 Tomcat 比较多。一个最基本的 HTTP 服务器可要以处理常见的 HTTP Method 的请求，比如 POST 请求、GET 请求等等。

3、**实现一个即时通讯系统** ：使用 Netty 我们可以实现一个可以聊天类似微信的即时通讯系统，这方面的开源项目还蛮多的，可以自行去 Github 找一找。

4、**实现消息推送系统** ：市面上有很多消息推送系统都是基于 Netty 来做的。

Netty 核心组件有哪些？分别有什么作用？
------

面试官 ：Netty 核心组件有哪些？分别有什么作用？

我 ：表面上，嘴上开始说起 Netty 的核心组件有哪些，实则，内心已经开始 mmp 了，深度怀疑这面试官是存心搞我啊！

1.Channel
------

Channel 接口是 Netty 对网络操作抽象类，它除了包括基本的 I/O 操作，如 bind()、connect()、read()、write() 等。

比较常用的Channel接口实现类是NioServerSocketChannel（服务端）和NioSocketChannel（客户端），这两个 Channel 可以和 BIO 编程模型中的ServerSocket以及Socket两个概念对应上。Netty 的 Channel 接口所提供的 API，大大地降低了直接使用 Socket 类的复杂性。

2.EventLoop
------

这么说吧！EventLoop（事件循环）接口可以说是 Netty 中最核心的概念了！

《Netty 实战》这本书是这样介绍它的：

> EventLoop 定义了 Netty 的核心抽象，用于处理连接的生命周期中所发生的事件。

是不是很难理解？说实话，我学习 Netty 的时候看到这句话是没太能理解的。

说白了，EventLoop 的主要作用实际就是负责监听网络事件并调用事件处理器进行相关 I/O 操作的处理。

那 Channel 和 EventLoop 直接有啥联系呢？

Channel 为 Netty 网络操作(读写等操作)抽象类，EventLoop 负责处理注册到其上的Channel 处理 I/O 操作，两者配合参与 I/O 操作。

3.ChannelFuture
------

Netty 是异步非阻塞的，所有的 I/O 操作都为异步的。

因此，我们不能立刻得到操作是否执行成功，但是，你可以通过 ChannelFuture 接口的 addListener() 方法注册一个 ChannelFutureListener，当操作执行成功或者失败时，监听就会自动触发返回结果。

并且，你还可以通过ChannelFuture 的 channel() 方法获取关联的Channel

```java
public interface ChannelFuture extends Future<Void> {
    Channel channel();

    ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> var1);
     ......

    ChannelFuture sync() throws InterruptedException;
}
```
另外，我们还可以通过 ChannelFuture 接口的 sync()方法让异步的操作变成同步的。

4.ChannelHandler 和 ChannelPipeline
------

下面这段代码使用过 Netty 的小伙伴应该不会陌生，我们指定了序列化编解码器以及自定义的 ChannelHandler 处理消息。

```java
b.group(eventLoopGroup)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new NettyKryoDecoder(kryoSerializer, RpcResponse.class));
                        ch.pipeline().addLast(new NettyKryoEncoder(kryoSerializer, RpcRequest.class));
                        ch.pipeline().addLast(new KryoClientHandler());
                    }
                });
```
ChannelHandler 是消息的具体处理器。他负责处理读写操作、客户端连接等事情。

ChannelPipeline 为 ChannelHandler 的链，提供了一个容器并定义了用于沿着链传播入站和出站事件流的 API 。当 Channel 被创建时，它会被自动地分配到它专属的 ChannelPipeline。

我们可以在 ChannelPipeline 上通过 addLast() 方法添加一个或者多个ChannelHandler ，因为一个数据或者事件可能会被多个 Handler 处理。当一个 ChannelHandler 处理完之后就将数据交给下一个 ChannelHandler 。

EventloopGroup 了解么?和 EventLoop 啥关系?
------

面试官 ：刚刚你也介绍了 EventLoop。那你再说说 EventloopGroup 吧！和 EventLoop 啥关系?

![Netty_Q_1]()

EventLoopGroup 包含多个 EventLoop（每一个 EventLoop 通常内部包含一个线程），上面我们已经说了 EventLoop 的主要作用实际就是负责监听网络事件并调用事件处理器进行相关 I/O 操作的处理。

并且 EventLoop 处理的 I/O 事件都将在它专有的 Thread 上被处理，即 Thread 和 EventLoop 属于 1 : 1 的关系，从而保证线程安全。

上图是一个服务端对 EventLoopGroup 使用的大致模块图，其中 Boss EventloopGroup 用于接收连接，Worker EventloopGroup 用于具体的处理（消息的读写以及其他逻辑处理）。

从上图可以看出：当客户端通过 connect 方法连接服务端时，bossGroup 处理客户端连接请求。当客户端处理完成后，会将这个连接提交给 workerGroup 来处理，然后 workerGroup 负责处理其 IO 相关操作。

Bootstrap 和 ServerBootstrap 了解么？
------

面试官 ：你再说说自己对 Bootstrap 和 ServerBootstrap 的了解吧！

我 ：

Bootstrap 是客户端的启动引导类/辅助类，具体使用方法如下：

```JAVA
EventLoopGroup group = new NioEventLoopGroup();
try {
    //创建客户端启动引导/辅助类：Bootstrap
    Bootstrap b = new Bootstrap();
    //指定线程模型
    b.group(group).
            ......
    // 尝试建立连接
    ChannelFuture f = b.connect(host, port).sync();
    f.channel().closeFuture().sync();
} finally {
    // 优雅关闭相关线程组资源
    group.shutdownGracefully();
}
```
ServerBootstrap 客户端的启动引导类/辅助类，具体使用方法如下：
```JAVA
// 1.bossGroup 用于接收连接，workerGroup 用于具体的处理
EventLoopGroup bossGroup = new NioEventLoopGroup(1);
EventLoopGroup workerGroup = new NioEventLoopGroup();
try {
    //2.创建服务端启动引导/辅助类：ServerBootstrap
    ServerBootstrap b = new ServerBootstrap();
    //3.给引导类配置两大线程组,确定了线程模型
    b.group(bossGroup, workerGroup).
           ......
    // 6.绑定端口
    ChannelFuture f = b.bind(port).sync();
    // 等待连接关闭
    f.channel().closeFuture().sync();
} finally {
    //7.优雅关闭相关线程组资源
    bossGroup.shutdownGracefully();
    workerGroup.shutdownGracefully();
}
```
从上面的示例中，我们可以看出：

1、Bootstrap 通常使用 connet() 方法连接到远程的主机和端口，作为一个 Netty TCP 协议通信中的客户端。另外，Bootstrap 也可以通过 bind() 方法绑定本地的一个端口，作为 UDP 协议通信中的一端。

2、ServerBootstrap通常使用 bind() 方法绑定本地的端口上，然后等待客户端的连接。

3、Bootstrap 只需要配置一个线程组— EventLoopGroup ,而 ServerBootstrap需要配置两个线程组— EventLoopGroup ，一个用于接收连接，一个用于具体的处理。

NioEventLoopGroup 默认的构造函数会起多少线程？
------

面试官 ：看过 Netty 的源码了么？NioEventLoopGroup 默认的构造函数会起多少线程呢？

我 ：嗯嗯！看过部分。

回顾我们在上面写的服务器端的代码：

```java
// 1.bossGroup 用于接收连接，workerGroup 用于具体的处理
EventLoopGroup bossGroup = new NioEventLoopGroup(1);
EventLoopGroup workerGroup = new NioEventLoopGroup();
```

为了搞清楚NioEventLoopGroup 默认的构造函数 到底创建了多少个线程，我们来看一下它的源码。

```java
/**
 * 无参构造函数。
 * nThreads:0
 */
public NioEventLoopGroup() {
    //调用下一个构造方法
    this(0);
}

/**
 * Executor：null
 */
public NioEventLoopGroup(int nThreads) {
    //继续调用下一个构造方法
    this(nThreads, (Executor) null);
}

//中间省略部分构造函数

/**
 * RejectedExecutionHandler（）：RejectedExecutionHandlers.reject()
 */
public NioEventLoopGroup(int nThreads, Executor executor, final SelectorProvider selectorProvider,final SelectStrategyFactory selectStrategyFactory) {
   //开始调用父类的构造函数
    super(nThreads, executor, selectorProvider, selectStrategyFactory, RejectedExecutionHandlers.reject());
}
```
一直向下走下去的话，你会发现在 MultithreadEventLoopGroup 类中有相关的指定线程数的代码，如下：
```java
// 从1，系统属性，CPU核心数*2 这三个值中取出一个最大的
//可以得出 DEFAULT_EVENT_LOOP_THREADS 的值为CPU核心数*2
private static final int DEFAULT_EVENT_LOOP_THREADS = Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));

// 被调用的父类构造函数，NioEventLoopGroup 默认的构造函数会起多少线程的秘密所在
// 当指定的线程数nThreads为0时，使用默认的线程数DEFAULT_EVENT_LOOP_THREADS
protected MultithreadEventLoopGroup(int nThreads, ThreadFactory threadFactory, Object... args) {
    super(nThreads == 0 ? DEFAULT_EVENT_LOOP_THREADS : nThreads, threadFactory, args);
}
```

综上，我们发现 NioEventLoopGroup 默认的构造函数实际会起的线程数为 CPU核心数*2。

另外，如果你继续深入下去看构造函数的话，你会发现每个NioEventLoopGroup对象内部都会分配一组NioEventLoop，其大小是 nThreads, 这样就构成了一个线程池， 一个NIOEventLoop 和一个线程相对应，这和我们上面说的 EventloopGroup 和 EventLoop关系这部分内容相对应。


