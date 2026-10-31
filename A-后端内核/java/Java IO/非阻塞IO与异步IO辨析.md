非阻塞 IO 与异步 IO 辨析
====

"异步非阻塞"这个词被说得太滥，但能把**非阻塞（NIO）和异步（AIO）**真正讲分开的候选人很少。面试里这是一道分水岭题：**非阻塞解决的是"等待就绪不占线程"，异步解决的是"数据搬运也不占线程"**。本文从阻塞 BIO 的代码出发，推演出非阻塞 NIO，再到 JDK7 的 NIO.2 异步 IO，顺带把 select/poll/epoll 的演进说清。API 基础（Buffer/Channel/Selector 的接口细节）见姊妹篇 [Java NIO三组件](Java%20NIO三组件-Buffer-Channel-Selector.md)。

1 阻塞 BIO：每连接每线程的两宗罪
------

BIO 服务端的标准写法：主线程 accept，来一个连接开一个线程：

```java
public class Server {
    public static void main(String[] args) throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open();
        server.socket().bind(new InetSocketAddress(8080));
        while (true) {
            SocketChannel socketChannel = server.accept(); // 阻塞到有连接
            new Thread(new SocketHandler(socketChannel)).start();
        }
    }
}
```

SocketHandler 在自己的线程里 read→flip→get→write→clear 循环。这个模式的瓶颈有两层：

* **线程爆炸**：活跃连接几十几百还行，几万几十万时，每线程一份栈内存（默认 512K~1M）迅速吃光内存，线程切换的 CPU 开销也急剧上升；
* **阻塞浪费**：accept 返回只代表连接建立，**不代表对端已经把数据发过来**。handler 线程随即阻塞在 read() 上空等数据，write() 同样要等通道可写。线程大部分时间在睡觉，却一个都省不掉。

2 非阻塞 NIO：Selector 一个线程管所有通道
------

NIO 的核心思路：用一个 Selector 管理多个 Channel，单线程轮询"哪些通道就绪了"，就绪才去做真正的读写，从根上取消"一连接一线程"：

```java
public class SelectorServer {
    public static void main(String[] args) throws IOException {
        Selector selector = Selector.open();
        ServerSocketChannel server = ServerSocketChannel.open();
        server.socket().bind(new InetSocketAddress(8080));
        server.configureBlocking(false);                  // 非阻塞
        server.register(selector, SelectionKey.OP_ACCEPT);

        while (true) {
            selector.select();                            // 阻塞到有事件就绪
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();
                if (key.isAcceptable()) {
                    SocketChannel sc = server.accept();   // 此刻必有连接，不阻塞
                    sc.configureBlocking(false);
                    // 关键：新连接 != 有数据，注册 OP_READ 等数据到达
                    sc.register(selector, SelectionKey.OP_READ);
                } else if (key.isReadable()) {
                    SocketChannel sc = (SocketChannel) key.channel();
                    ByteBuffer buf = ByteBuffer.allocate(1024);
                    int num = sc.read(buf);
                    if (num > 0) { /* 处理并回写 */ }
                    else if (num == -1) { sc.close(); }   // -1 表示对端关闭
                }
            }
        }
    }
}
```

两个面试细节：accept 触发后**注册的是 OP_READ 而不是直接读**，因为连接建立与数据到达是两件事；read 返回 -1 不是 0，-1 才代表连接关闭。

3 Selector 底下：select/poll/epoll 的演进
------

Selector 是 JVM 对操作系统多路复用实现的抽象，值得知道它下面是什么：

* **select**（1980 年代）：支持 FD_SETSIZE=1024 个 socket，上限硬编码；
* **poll**（1997）：去掉了数量限制，但和 select 一样**只告诉你有几个就绪，不说是谁**，应用还得自己 O(n) 全量扫描，几十万连接时扫描本身就成为瓶颈；
* **epoll**（2002，Linux 2.5.44）：直接返回就绪的通道列表，O(1)，是 Linux 高并发网络的基石；
* 其他平台：FreeBSD 有 kqueue（2000），Solaris 有 /dev/poll；Windows 的非阻塞 IO 仍用 select，但它的异步方案 IOCP 很强。

JVM 屏蔽了这些差异，**面向 Selector 编程即可**——这也是 NIO 跨平台的边界。

4 JDK7 异步 IO（NIO.2）：Future 与 CompletionHandler
------

NIO.2 随 JDK 1.7 发布，类名就是在原有通道前加 Asynchronous：AsynchronousSocketChannel、AsynchronousServerSocketChannel、AsynchronousFileChannel。语义区别在于：**非阻塞 IO 只是"就绪不用等"，读写仍由用户线程发起并完成；异步 IO 连数据搬运都交给系统/线程池，完成后回调你**。两种编程模型：

* **Future**：`channel.read(buffer, 0)` 立即返回 Future<Integer>，isDone/cancel/get/get(timeout) 与线程池语义一致；
* **CompletionHandler 回调**：

```java
public interface CompletionHandler<V, A> {
    void completed(V result, A attachment);
    void failed(Throwable exc, A attachment);
}
```

AIO 服务端的 accept 回调里有一个必须记住的动作——**立即再次 accept** 等下一个连接，并在 read/write 之间用自定义 Attachment 携带上下文（server、client、buffer、readMode），形成"读完成→回写→写完成→继续读或关连接"的回调链。文件侧的 AsynchronousFileChannel 额外提供 force(boolean) 刷盘（写操作走系统缓存，怕断电就手动刷）、lock/tryLock 文件区域锁；注意**异步文件的读写必须传 position**。

5 AsynchronousChannelGroup：AIO 的线程池从哪来
------

异步 IO 一定存在一个线程池负责接任务、处理 IO 事件、执行回调。Unix/Linux 上 JDK 用并发包线程池模拟（见 AsynchronousChannelGroup 源码），**Windows 上则直接用操作系统的 IOCP**，把更多调度交给系统优化——这是 Linux 原生 AIO 限制多、性能一般背景下的务实选择。

通道属于 group：open() 不传参就用 JVM 管理的默认 group，可用两个系统属性定制——`java.nio.channels.DefaultThreadPool.threadFactory`（线程工厂全限定类名）和 `java.nio.channels.DefaultThreadPool.initialSize`（初始大小）。自建 group 用静态方法：

```java
AsynchronousChannelGroup group = AsynchronousChannelGroup
        .withFixedThreadPool(10, Executors.defaultThreadFactory());
AsynchronousServerSocketChannel server = AsynchronousServerSocketChannel.open(group);
```

另有 withCachedThreadPool/withThreadPool。**AsynchronousFileChannel 不属于任何 group**，但同样关联线程池，可在 open(...) 时传入 ExecutorService。group 关闭，内部线程池随之关闭。

6 工程视角：为什么大家最后还是选了 Netty
------

看懂 API 不等于能写工程代码：半包粘包、超时、慢连接、线程隔离、内存池……细节非常多，这正是 Netty/MINA 盛行的原因。Linux 上 AIO 的实际收益有限，主流高组件（Netty、Tomcat NIO、Dubbo）**都以 NIO + Reactor 为基础自建异步语义**，Windows IOCP 的优势在服务端场景基本没机会兑现。延伸阅读见 [实际工作中NIO使用场景](实际工作中NIO使用场景.md) 与库内 Netty 源码分析系列。

面试追问链
------

* 非阻塞和异步到底差在哪？→ 非阻塞：等待就绪不占线程，读写仍自己做；异步：连读写都交出去，完成回调；
* select 和 epoll 区别？→ 1024 上限 + O(n) 全量扫描 vs 无上限 + O(1) 直接返回就绪列表；
* OP_ACCEPT 之后为什么注册 OP_READ？→ 连接建立≠数据到达，两件事分开等；
* read 返回 0 和 -1 分别是什么？→ 0 是没有数据可读（非阻塞），-1 是对端关闭；
* AIO 在 Linux 上是真异步吗？→ 不是，JDK 用线程池模拟；Windows 才走 IOCP；
* 为什么 Netty 不用 AIO？→ Linux AIO 限制多收益小，NIO + epoll 足够，还统一了跨平台行为。

面试官视角小结
------

这道题筛的是概念精度。及格线：说出 BIO 的线程瓶颈和"一个 Selector 管多通道"。良好：能画出 accept→OP_READ→read 的状态推进，讲清 select/poll/epoll 演进的动因（连接数 × 扫描成本）。资深信号：能区分"等待就绪"与"数据搬运"两个阶段在四种 IO 模型里的归属，指出 Linux AIO 是线程池模拟而 Windows 是 IOCP，并落地到"Tomcat 的 Poller、Netty 的 NioEventLoop 都是 select 循环 + 任务队列"这一层。

> 来源：javadoop《Java 非阻塞 IO 和异步 IO》（https://javadoop.com/post/nio-and-aio）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
