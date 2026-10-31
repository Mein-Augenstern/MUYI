Tomcat 中的 NIO 源码分析
====

"Tomcat 是怎么用 NIO 的"是区分背题者与读源码者的经典题。**答案的关键反直觉点有两个：监听用的 ServerSocketChannel 是阻塞模式；真正干活的线程池在 Selector 之外**。本文基于 Tomcat 9.0.6（tomcat-embed-core）走一遍 Acceptor、Poller、worker 三级线程模型的源码链路。NIO 基础 API 先看库内 [Java NIO三组件](../../../java/Java%20IO/Java%20NIO三组件-Buffer-Channel-Selector.md)；同目录 [Tomcat类加载器与双亲委派](Tomcat类加载器与双亲委派.md) 讲的是另一条主线。

1 源码环境与 Endpoint 结构
------

调试环境很简单：Maven 引入 tomcat-embed-core，手动 new Tomcat()、addConnector(new Connector("HTTP/1.1"))、setPort(8080)、start() 后 getServer().await()。Connector 的 protocolHandler 有三种选择：Http11NioProtocol（NIO，内部 NioEndpoint）、Http11Nio2Protocol（异步 IO，Nio2Endpoint）、Http2Protocol。本文主线是 NioEndpoint，继承链为 **NioEndpoint → AbstractJsseEndpoint → AbstractEndpoint**（JSSE 层负责 HTTPS，不在主线内）。一句话：**协议处理器只负责协议解析，IO 模型的差异全部封装在 Endpoint 里**。

2 init/bind：ServerSocketChannel 是阻塞的
------

AbstractProtocol#init 设置 endpoint 的名称后调 endpoint.init()，最终到 NioEndpoint#bind()，做四件事：

```java
serverSock = ServerSocketChannel.open();
serverSock.socket().bind(addr, getAcceptCount());   // backlog 默认 100
serverSock.configureBlocking(true);                 // ※ 阻塞模式 ※
acceptorThreadCount = 1;
pollerThreadCount = Math.min(2, Runtime.getRuntime().availableProcessors());
```

**重点就是 configureBlocking(true)**：监听通道故意保持阻塞。因为 accept 只交给一个专职线程（Acceptor）做，阻塞式 accept 反而最简单——select 循环里被"顺手"accept 的写法（把 OP_ACCEPT 也交给 Selector）是另一种方案，Tomcat 没有采用。pollerThreadCount 在多核机器上是 2。

3 startInternal：缓存、线程池与连接闸门
------

NioEndpoint#startInternal 依次创建：

* 三个 SynchronizedStack 缓存：**processorCache**（Http11Processor 复用）、**eventCache**（PollerEvent 复用）、**nioChannels**（NioChannel 复用）——对象池化是 Tomcat 对抗 GC 的老手艺；
* 工作线程池 **executor**：内部 ThreadPoolExecutor，默认 core 10、max 200，真正执行请求处理；
* **initializeConnectionLatch()**：LimitLatch 限流，默认 maxConnections=10000；
* 先 startPollerThreads（默认 2 个 Poller），再 startAcceptorThreads（默认 1 个 Acceptor）。

用 jconsole 看线程画像：1 个 acceptor、2 个 poller、若干 worker，外加一个 AsyncTimeout 守护线程——三级分工一目了然。

4 Acceptor：专职阻塞 accept 的线程
------

Acceptor 是 AbstractEndpoint 的内部类，核心字段就一个 volatile 的 AcceptorState（NEW/RUNNING/PAUSED/ENDED）。run() 主循环：

```java
while (running) {
    // paused 时睡 50ms；超过 maxConnections 时阻塞在连接闩上
    endpoint.countUpOrAwaitConnection();
    socket = endpoint.serverSocketAccept();     // 阻塞式 accept
    if (!endpoint.setSocketOptions(socket)) {
        socket.close();
    }
}
```

它的职责被刻意压到最薄：拿到 SocketChannel 就丢给 setSocketOptions，自己立刻回去等下一个连接。**MaxConnections 打满时，Acceptor 阻塞在 LimitLatch 上，accept 不再消费，backlog 里排队——这就是 Tomcat 层面的连接数闸门**。

5 setSocketOptions：包装与移交 Poller
------

NioEndpoint#setSocketOptions 是"从阻塞世界进入 NIO 世界"的转换器：

```java
socket.configureBlocking(false);              // ※ 连接通道切非阻塞 ※
SocketBufferHandler bufHandler = new SocketBufferHandler(
        socketProperties.getAppReadBufSize(),   // 8KB 读缓冲
        socketProperties.getAppWriteBufSize(),  // 8KB 写缓冲
        ...);
NioChannel channel = nioChannels.pop();       // 对象池复用，没有则 new
... 
getPoller0().register(channel);                // 轮询选一个 Poller 注册
```

注意对比：**监听通道阻塞、连接通道非阻塞**。包装出 NioChannel（SSL 时是 SecureNioChannel）后按 round-robin 挑一个 Poller 移交，Acceptor 的工作到此结束。

6 Poller：事件队列 + select 循环
------

每个 Poller 持有自己的 **Selector**、一个 SynchronizedQueue<PollerEvent> events 队列和 wakeupCounter。register() 把 socket 包装成 NioSocketWrapper，设置读写超时与 keep-alive，interestOps 置为 OP_READ，构造 PollerEvent（OP_REGISTER）放入 events 队列。PollerEvent#run() 才真正执行 `socket.getIOChannel().register(selector, OP_READ, socketWrapper)`——**注册动作被推迟到 Poller 线程执行，避免多线程并发操作同一个 Selector**。

Poller#run() 的主循环：

```java
while (running) {
    hasEvents = events();                     // 消费事件队列（含注册事件）
    if (wakeupCounter.get() > 0)
        keyCount = selector.selectNow();      // 有待处理事件时非阻塞轮询
    else
        keyCount = selector.select(selectorTimeout); // 默认 1s 超时
    // 遍历 selectedKeys，对每个就绪 key：
    //   processKey(sk, attachment)  ※※※ 处理 ready key ※※※
    timeout(keyCount, hasEvents);             // 超时检测、长连接清理
}
```

7 processKey → processSocket：移交工作线程池
------

processKey 先 unreg()（把当前就绪的 interestOps 摘掉，避免同一事件重复触发），然后：

```java
if (sk.isReadable()) {
    if (!processSocket(attachment, SocketEvent.OPEN_READ, true))
        closeSocket = true;                  // 失败则取消 key 关连接
}
```

AbstractEndpoint#processSocket 从 processorCache 弹出一个 SocketProcessorBase（NioEndpoint 的实现是内部类 SocketProcessor），**dispatch=true 时 submit 给 executor**，由工作线程执行协议解析与业务调用；线程池拒绝时返回 false 触发关连接。源码分析到此为止，SocketProcessor 内部的 Http11Processor 协议解析属于另一条故事线。

8 全链路总结
------

```text
Connector(Http11NioProtocol)
   └─ NioEndpoint
       ├─ bind:  ServerSocketChannel(阻塞, backlog=100)
       ├─ start: executor(10/200) + LimitLatch(10000) + Poller×2 + Acceptor×1
       │
Acceptor(阻塞 accept) → setSocketOptions(切非阻塞, NioChannel 包装)
   → Poller0.register(事件队列) → PollerEvent.run(注册 OP_READ)
   → Poller.run: events() + selector.select(1s)
   → processKey(unreg + OPEN_READ) → processSocket(SocketProcessor → executor)
```

一句话：**Acceptor 管接、Poller 管等、worker 管干**，三组线程通过事件队列和对象池解耦，这与 Netty 的 boss/worker 在思想上同源（对比见库内 [Netty源码分析一-启动与EventLoop](../netty/Netty源码分析一-启动与EventLoop.md)）。

面试追问链
------

* Tomcat NIO 里几类线程？→ Acceptor 1 个、Poller 默认 2 个（min(2, cores)）、worker 池 10/200，另有 AsyncTimeout；
* 为什么 ServerSocketChannel 是阻塞模式？→ accept 交给专职 Acceptor 阻塞等待最简单，非阻塞注册给 Selector 只是把 accept 也事件化，没有必要；
* maxConnections 和 maxThreads 什么关系？→ maxConnections（LimitLatch，默认 10000）卡 Acceptor，maxThreads（200）卡 worker；前者打满时连接堆在 backlog；
* Poller 为什么要 events 队列，不直接 register？→ Selector 非线程安全，注册统一搬到 Poller 线程串行执行；
* processKey 为什么要先 unreg？→ 摘掉就绪的 interestOps，防止工作线程处理期间同一事件被重复触发；
* Poller 和 Netty 的 NioEventLoop 什么关系？→ 同为"select 循环 + 任务队列"，差别是 Netty 把 IO 处理也留在 EventLoop 线程，Tomcat 全部外派 worker。

面试官视角小结
------

Tomcat NIO 是把 [Java NIO三组件](../../../java/Java%20IO/Java%20NIO三组件-Buffer-Channel-Selector.md) 工程化的最佳样本。及格线：说出 Acceptor/Poller/executor 三级分工。良好：能解释"监听通道阻塞、连接通道非阻塞"的取舍，maxConnections/backlog/maxThreads 三道闸门的层次。资深信号：能指出注册被推迟到 PollerEvent、processKey 先 unreg 的并发动机，SynchronizedStack 对象池与 Netty Recycler 的呼应，并横向对比 NioEndpoint 与 NioEventLoop 的线程归属差异（IO 在谁手里做）。

> 来源：javadoop《Tomcat 中的 NIO 源码分析》（https://javadoop.com/post/tomcat-nio）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
