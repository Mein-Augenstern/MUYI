Netty 源码分析一：启动与 EventLoop
====

Netty 面试的第一层是背概念，第二层是讲得出"new NioEventLoopGroup() 时发生了什么、Channel 什么时候才真正创建"。本文基于 Netty 4.1.25.Final，把启动路径上的四个部件拆开：**Channel 的出生方式、Future/Promise 异步模型、Pipeline 的双向链表骨架、EventLoopGroup 这个特殊线程池的构造**。这是系列第一篇；注册与 NioEventLoop.run() 的完整执行见 [Netty源码分析二-Pipeline与Handler](Netty源码分析二-Pipeline与Handler.md)，内存与 ByteBuf 见 [Netty源码分析三-内存分配与ByteBuf](Netty源码分析三-内存分配与ByteBuf.md)，面试速答见 [Netty常见面试题](Netty常见面试题.md)。

1 从 Echo 例子看启动骨架
------

服务端模板代码浓缩了 Netty 的全部角色分工：

```java
EventLoopGroup bossGroup = new NioEventLoopGroup(1);   // 接入
EventLoopGroup workerGroup = new NioEventLoopGroup();  // 读写
ServerBootstrap b = new ServerBootstrap();
b.group(bossGroup, workerGroup)
 .channel(NioServerSocketChannel.class)   // 用反射工厂决定 Channel 类型
 .handler(...)                            // 针对 ServerSocketChannel 的 handler
 .childHandler(...)                       // 针对 accept 出的连接的 handler
 .bind(8080).sync();
```

要点：ServerBootstrap 造服务端、Bootstrap 造客户端；**服务端两个 group（boss 接入、worker 读写），客户端一个**；NioServerSocketChannel/NioSocketChannel 是对 JDK ServerSocketChannel/SocketChannel 的包装；handler 与 childHandler 的作用对象不同；所有 IO 操作返回 ChannelFuture（异步）。每个 Channel 内部是一条由 handler 组成的 pipeline。

2 Channel 是怎么 new 出来的
------

`channel(NioServerSocketChannel.class)` 只是存了一个 **ReflectiveChannelFactory**，真正的实例化发生在 bind()/connect() 内部的 initAndRegister() 第一步：`channel = channelFactory.newChannel()`（反射调用无参构造）。

以客户端 NioSocketChannel 为例，构造链做三件事：

```java
// NioSocketChannel
super(null, channel, SelectionKey.OP_READ);  // 传 interestOps
// AbstractNioChannel
ch.configureBlocking(false);                 // ※ 一出生就是非阻塞 ※
```

JDK 的 SocketChannel 由 provider.openSocketChannel() 打开；AbstractNioChannel 构造器里**设置非阻塞模式并记下 interestOps**——客户端是 OP_READ，服务端是 OP_ACCEPT。所以"Netty 的 Channel 什么时候创建"的答案：**在 bind/connect 发起时，而不是 new Bootstrap() 时**。

3 Future 与 Promise：异步操作的统一载体
------

Netty 的 io.netty.util.concurrent.Future<V> 扩展了 JDK Future，补齐了"主动通知"能力：

* `isSuccess()` / `cause()`：成功与否、失败原因（JDK Future 只有 done/cancelled）；
* `addListener(...)`：完成时回调，不必阻塞等待；
* `sync()` vs `await()`：**sync() 内部先 await()，等完之后如果操作失败会把异常直接抛出**；await() 只等不抛——面试高频区分点；
* `getNow()`：立刻拿结果（可能为 null）。

再往上一层：ChannelFuture = Future + 绑定到具体 Channel；**Promise 是可写的 Future**（setSuccess/trySuccess/setFailure/tryFailure），ChannelPromise = Promise + Channel。DefaultPromise 内部用一个 volatile Object result 存结果、一个 listeners 列表、等待线程计数，setSuccess/setFailure 时唤醒等待线程并逐个通知监听器。

用一个脱离网络的最小实验体会"完成时通知"（DefaultEventExecutor 演示）：

```java
EventExecutor executor = new DefaultEventExecutor();
Promise<String> promise = executor.newPromise();
promise.addListener(f -> System.out.println("listener-1: " + f.getNow()))
       .addListener(f -> System.out.println("listener-2: " + f.getNow()));
executor.execute(() -> {
    try { Thread.sleep(5000); } catch (InterruptedException ignore) {}
    promise.setSuccess("done");       // 5 秒后两个 listener 依次被回调
});
```

分工记忆法：**Future 是消费者视角（等结果），Promise 是生产者视角（给结果）**，ChannelPromise 把两者和 Channel 绑在一起，贯穿 register/bind/connect/write 全链路。

4 Pipeline 与 Inbound/Outbound
------

AbstractChannel 构造器里除了 id、unsafe，还有 `pipeline = newChannelPipeline()`。**DefaultChannelPipeline 是一条双向链表**，固定头尾节点 head 和 tail，中间是 AbstractChannelHandlerContext（包装 handler + 前后指针）。事件流向是 Netty 的灵魂规则：

* **Inbound 事件**（read、channelRegistered、channelActive 等）：从 **head 往 tail** 传播，只路过 Inbound 类型的 handler；
* **Outbound 操作**（connect、bind、write、flush 等）：从 **tail 往 head** 传播，只路过 Outbound 类型的 handler。

经典验证题：pipeline 依次 addLast 编码器(Outbound)、解码器(Inbound)、业务 handler(Inbound)，入站数据实际经过顺序是 **1→3→2**（解码→业务→编码），不是 add 的顺序。

两个基础设施顺便记下：Netty 的 **Unsafe** 接口包装 JDK NIO 底层操作（register/bind/connect），语义类似 sun.misc.Unsafe，不提供给用户代码；**ChannelInitializer** 是一个特殊的 Inbound handler，它唯一的使命是在 channel 注册完成时把 initChannel() 里 add 的真正 handler 装进 pipeline，然后把自己删掉。ServerBootstrap.init() 装的就是一个 ChannelInitializer + 内置的 ServerBootstrapAcceptor（负责把 accept 到的子 channel 注册到 worker group）。

5 EventLoopGroup：一个特殊的线程池
------

NioEventLoopGroup 本质是线程池，但与普通线程池有两个本质区别：**每个 NioEventLoop 是单线程执行器，且内部绑定一个 Selector**；任务类型分 IO 事件与普通任务。默认 nThreads = CPU 核数 × 2，boss 常写成 new NioEventLoopGroup(1)。构造参数依次：nThreads、executor、chooserFactory、selectorProvider、selectStrategyFactory、rejectedExecutionHandler。

MultiThreadEventExecutorGroup 的核心构造器：

```java
children = new EventExecutor[nThreads];
for (int i = 0; i < nThreads; i++) {
    children[i] = newChild(executor, args);   // new 出每个 NioEventLoop
}
chooser = chooserFactory.newChooser(children); // 分配策略
```

* **executor 是 ThreadPerTaskExecutor**：每提交一个任务就新开一个线程。注意它属于**每个 NioEventLoop**，不是 group——group 层面不建线程，线程随 EventLoop 逐个诞生；
* **Chooser**：池大小为 2 的幂时用 `idx.getAndIncrement() & executors.length - 1`（位运算取模），否则用普通取模——一个细节但常被追问；
* 构造失败时会把已创建的 children 优雅关闭，并用 terminationListener 等全部子 executor 结束后才算 group 终止；
* **NioEventLoop 构造器里 openSelector()** 创建自己专属的 Selector，关键字段 selector、wakenUp、ioRatio=50；它自身还带一个 taskQueue（默认容量 16，满了抛 RejectedExecutionException）。

学源码建议直接依赖 netty-example（含各官方示例）而不是 netty-all，跟着 example 里的 EchoServer/EchoClient 走读最省力。客户端与服务端启动的异同一句话对比：客户端 Bootstrap 只有一个 group，channel 用 NioSocketChannel，入口是 connect()；服务端 ServerBootstrap 有 boss/worker 两个 group，channel 用 NioServerSocketChannel，入口是 bind()——但两者最终都汇入同一个 initAndRegister()，这是下一篇的主角。

6 小结：线程还没出生
------

到本文为止的状态：**group 里每个 NioEventLoop 都已存在，但 Thread 实例一个都还没创建**——ThreadPerTaskExecutor 只有在第一个任务提交时才会开线程。第一个任务是谁？就是 register。这条从 initAndRegister 到 NioEventLoop.run() select 循环的完整链路，展开在系列第二篇 [Netty源码分析二-Pipeline与Handler](Netty源码分析二-Pipeline与Handler.md)。

面试追问链
------

* boss 和 worker 各干什么？→ boss 负责 accept 新连接，worker 负责连接读写；boss 默认几个线程合适？→ 1 个（绑定端口数决定）；
* NioEventLoopGroup 默认多少线程？→ 2×CPU 核数；EventLoop 和 group 什么关系？→ group 是池，EventLoop 是单线程执行器 + 独立 Selector；
* sync() 和 await() 区别？→ sync 等待失败后抛出异常，await 只等待不抛；
* Inbound 和 Outbound 的传播方向？→ 入站 head→tail，出站 tail→head；编码器是哪种？→ Outbound；
* Channel 什么时候创建？→ bind/connect 内的 initAndRegister() 里 channelFactory.newChannel()，反射无参构造；
* new 完 group 就有线程了吗？→ 没有，首个任务提交时 ThreadPerTaskExecutor 才建线程；
* chooser 为什么能用位运算？→ 池大小是 2 的幂时 `idx & (len-1)` 等价取模，Netty 专门做了 PowerOfTwo 分支。

面试官视角小结
------

这篇对应"你真的读过 Netty 源码吗"。及格线：说清 boss/worker 分工与默认线程数。良好：讲得出 ReflectiveChannelFactory 的延迟实例化时机、sync/await 差异、Inbound/Outbound 的 1→3→2 题。资深信号：能描述 MultiThreadEventExecutorGroup 构造器里 newChild/chooser/terminationListener 的细节，点破"Thread 延迟到首个任务才创建"，并把 EventLoop 与 Tomcat Poller（见 [Tomcat中的NIO源码分析](../tomcat/Tomcat中的NIO源码分析.md)）归到同一个 select 循环模型。更深的注册与 run 循环继续看系列第二篇。

> 来源：javadoop《认真的 Netty 源码解析（一）》（https://javadoop.com/post/netty-1）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
