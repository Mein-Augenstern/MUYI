Netty 源码分析二：Pipeline 与 Handler
====

上一篇（[Netty源码分析一-启动与EventLoop](Netty源码分析一-启动与EventLoop.md)）结束时留了一个悬念：EventLoopGroup 建好了，但一个线程都还没出生。本文（基于 Netty 4.1.25.Final）顺着 register 操作往下走，**补上三个高频考点的源码证据：Channel 与 EventLoop 怎么绑定、NioEventLoop.run() 的 select 循环长什么样、事件在 Pipeline 里怎么从 handler 流到 handler**，最后看 connect/bind 如何从 tail 出发。内存与 ByteBuf 见 [Netty源码分析三-内存分配与ByteBuf](Netty源码分析三-内存分配与ByteBuf.md)，速答版见 [Netty常见面试题](Netty常见面试题.md)。

1 initAndRegister：一切从注册开始
------

无论客户端 connect 还是服务端 bind，入口都是 AbstractBootstrap.initAndRegister()：

```java
final ChannelFuture initAndRegister() {
    Channel channel = channelFactory.newChannel();  // 1. 反射造 Channel
    init(channel);                                  // 2. Bootstrap/ServerBootstrap 各自初始化
    ChannelFuture regFuture = config().group().register(channel); // 3. 注册进 group
    ...
    return regFuture;
}
```

第三步的调用链：`group().register(channel)`（MultithreadEventLoopGroup）先用 next() 由 chooser 挑一个 NioEventLoop，再进入 SingleThreadEventLoop.register()，new 一个 DefaultChannelPromise(channel, eventLoop)，最后委托给 channel 内部的 unsafe：`promise.channel().unsafe().register(this, promise)`。

2 AbstractUnsafe.register：绑定与线程出生
------

AbstractChannel#AbstractUnsafe.register 的两步：

```java
AbstractChannel.this.eventLoop = eventLoop;   // ※ Channel 从此绑定这个 EventLoop ※
if (eventLoop.inEventLoop()) {
    register0(promise);                       // 已在 EventLoop 线程：直接做
} else {
    eventLoop.execute(() -> register0(promise)); // 否则：提交任务，立即返回
}
```

**这行赋值是 Netty 线程模型的基石**：一个 Channel 终身属于一个 EventLoop，后续该 channel 的所有异步操作都提交给它，因此同一连接的 IO 天然串行、无锁。register 任务经 execute 进入 taskQueue，正是它触发了线程创建：

```java
// SingleThreadEventExecutor#doStartThread（CAS 保证只启动一次）
executor.execute(() -> {
    thread = Thread.currentThread();          // 绑定线程
    SingleThreadEventExecutor.this.run();     // 进入 NioEventLoop.run()
});
```

executor 就是上一篇说的 ThreadPerTaskExecutor——第一个任务到来，EventLoop 的线程才真正出生。

3 NioEventLoop.run()：select 循环与 ioRatio
------

run() 是一个无限循环，每轮四段：

* **选择策略**：`selectStrategy.calculateStrategy(selectNowSupplier, hasTasks())`——taskQueue 非空就 selectNow()（非阻塞，赶紧回来跑任务）；为空才走 SELECT 分支做 `select(wakenUp.getAndSet(false))` 阻塞等待，醒来后 `if (wakenUp.get()) selector.wakeup()`；
* **处理 IO**：processSelectedKeys()，分发就绪事件；
* **跑任务**：ioRatio 默认 50。等于 100 时先 IO 后 `runAllTasks()`；否则记录 ioStartTime，IO 完成后按 `runAllTasks(ioTime * (100 - ioRatio) / ioRatio)` **限时**执行任务——IO 时间与任务时间对半分，防止业务任务饿死 IO；
* **收尾**：检测 isShuttingDown()，需要关闭时 closeAll() 并 confirmShutdown() 决定退出。

追问"register 属于 IO 还是任务"：它是 register0 提交的普通任务，走 taskQueue 与 runAllTasks 路径。

4 register0：interestOps=0 与 ChannelInitializer
------

register0(promise) 在 EventLoop 线程执行，五步：

```java
doRegister();   // 1. JDK 层注册
pipeline.invokeHandlerAddedIfNeeded(); // 2. 触发 handlerAdded
safeSetSuccess(promise);               // 3. 通知注册方
pipeline.fireChannelRegistered();      // 4. 入站事件传播
// 5. isActive() && firstRegistration → fireChannelActive()；已注册且 autoRead → beginRead()
```

第 1 步的真实代码值得背下来：`selectionKey = javaChannel().register(eventLoop().unwrappedSelector(), 0, this)`——**interestOps 传 0，什么都不监听**，后续必有 beginRead() 把 OP_READ 补上（第 5 步就是补的地方）。

第 2 步是 ChannelInitializer 的主场：

```java
public void handlerAdded(ChannelHandlerContext ctx) {
    if (ctx.channel().isRegistered()) {
        initChannel(ctx);   // add 用户 handler
    }
}
// initChannel 的 finally 中：ctx.pipeline().remove(this)  // 用完即删
```

至此 pipeline 从"head + ChannelInitializer + tail"变成"head + 用户 handler + tail"。

5 事件的传播：head 进、tail 出、findContext 找下家
------

同一名字的两个方法来自不同的类，务必分清：

```java
// DefaultChannelPipeline：往整条 pipeline 抛事件（从 head 开始）
public final ChannelPipeline fireChannelRegistered() {
    AbstractChannelHandlerContext.invokeChannelRegistered(head);
    return this;
}
// HeadContext：处理完自己，往后传
public void channelRegistered(ChannelHandlerContext ctx) {
    invokeHandlerAddedIfNeeded();
    ctx.fireChannelRegistered();
}
// AbstractChannelHandlerContext：找下一个 Inbound handler 再 invoke
invokeChannelRegistered(findContextInbound());
```

**pipeline.fireXxx() 是入口，ctx.fireXxx() 是传播**；findContextInbound() 沿链表跳过非 Inbound 节点找下一个。invoke 之前还有一步 executor.inEventLoop() 判断，不在 EventLoop 线程就先把任务入队——**handler 切换线程就发生在这里**。同理可推出 Outbound 的 tail 出发、findContextOutbound 逆行，终点都是 HeadContext。

6 connect 与 bind：Outbound 从 tail 出发
------

注册完成后，客户端走 doResolveAndConnect0 → AbstractChannel.connect()：

```java
public ChannelFuture connect(SocketAddress remote, ChannelPromise promise) {
    return pipeline.connect(remote, promise);   // Outbound：从 tail 开始
}
// 链路：tail.connect → ... → HeadContext.connect
public void connect(ChannelHandlerContext ctx, ..., ChannelPromise promise) {
    unsafe.connect(remoteAddress, localAddress, promise);   // 兜底到 unsafe
}
```

AbstractNioChannel#AbstractNioUnsafe.connect 里两个细节：doConnect() 完成后把 interestOps 设为 SelectionKey.OP_CONNECT；**连接未立即成功时用 eventLoop().schedule() 挂一个定时任务**，超过 connectTimeoutMillis 就 `connectPromise.tryFailure(new ConnectTimeoutException(...))` 并 close——超时控制就是这么来的。bind 同构：doBind0 → pipeline.bind → tail.bind → HeadContext.bind → unsafe.bind（同步、无超时逻辑）。固定套路总结：**Inbound 事件 head 进，Outbound 操作 tail 出，真正的底层动作都落在 HeadContext 调 unsafe**。

面试追问链
------

* Channel 和 EventLoop 的关系？→ 终身绑定（register 时赋值 eventLoop 字段），后续操作全部回到这个线程，免锁串行；
* register 时监听什么事件？→ interestOps=0，什么都不监听；OP_READ 何时加上？→ register0 末尾 beginRead()（fireChannelActive 路径）；
* NioEventLoop 里 IO 和任务怎么平衡？→ ioRatio=50，任务按 IO 耗时等比限时执行；
* taskQueue 有任务时还 select 阻塞吗？→ 不，selectNow() 非阻塞，优先处理任务；
* pipeline.fireChannelRead 和 ctx.fireChannelRead 区别？→ 前者从 head 进入整条链，后者是当前 handler 处理完向后传播；
* handler 里做耗时操作会怎样？→ 阻塞整个 EventLoop，该线程上所有 Channel 的 IO 全部卡住；怎么办？→ 另开业务线程池或 addLast 时指定 EventExecutorGroup；
* connect 超时怎么实现？→ EventLoop 的 schedule 定时任务 tryFailure(ConnectTimeoutException)。

面试官视角小结
------

这一篇是"Netty 为什么快且不用锁"的证据链。及格线：说出 Channel 绑定 EventLoop、事件传播方向。良好：能默写 register0 的五步顺序，解释 interestOps=0 到 beginRead 的补位过程，ioRatio 的限时任务算法。资深信号：能指出 inEventLoop() 判断 + 任务入队正是 handler 换线程的切换点，connect 超时利用 EventLoop 定时任务实现，并把 NioEventLoop.run() 与 Tomcat Poller.run()（[Tomcat中的NIO源码分析](../tomcat/Tomcat中的NIO源码分析.md)）对照成同一个模式。数据在管道里怎么承载、怎么分配内存，见系列第三篇 [Netty源码分析三-内存分配与ByteBuf](Netty源码分析三-内存分配与ByteBuf.md)。

> 来源：javadoop《认真的 Netty 源码解析（二）》（https://javadoop.com/post/netty-2）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
