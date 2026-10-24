看在前面
------

* <a href="https://tech.meituan.com/2016/11/04/nio.html">Java NIO浅析</a>

BIO 模型的问题
====

经典 BIO 是"每连接每线程"：主线程 accept() 后把连接交给线程池处理。缺点是严重依赖线程，而线程是"贵"的资源：

* 线程的创建/销毁是重量级系统调用；
* 线程栈默认占用 512K~1M 内存；
* 线程切换成本高（CPU sy 超过 20% 时系统接近不可用）；
* 容易造成锯齿状的系统负载。

结论：连接数小于单机 1000 时 BIO 可用，面对十万/百万级长连接无能为力。

NIO 是怎么工作的
====

1 I/O 的两个阶段
------

所有 I/O 分为两步：**等待就绪**（如等待网卡数据到达 RecvBuffer）和**数据操作**（真正的读写，本质是 memory copy，很快）。等待就绪的阻塞并不占 CPU（空等）。

2 三种 I/O 模型对比
------

以 socket.read() 为例：

* **BIO**：RecvBuffer 没有数据就一直阻塞；
* **NIO**：有数据就读取返回，没有数据直接返回 0，**永不阻塞**。等待就绪阶段非阻塞，但真正的 I/O 操作仍是同步的；
* **AIO**：连数据从网卡到内核缓冲区的拷贝过程也是异步的，完成后回调通知。

一句话概括：BIO 关心"我要读"，NIO 关心"我可以读了"，AIO 关注"读完了"。

3 三大组件
------

* **Channel**：对应连接及事件（读、写或连接）；
* **Selector**：事件选择器。select() 本身是阻塞的（Linux 2.6 前基于 select/poll，之后用 epoll，Windows 是 IOCP），可以放心放在 while(true) 中调用，不会 CPU 空转；
* **Buffer**：DirectByteBuffer 可以减少一次内核空间到用户空间的拷贝。但创建/销毁成本高，需要内存池配合。小数据量用 HeapByteBuffer，大数据量用 DirectByteBuffer（选型细节见库内 [怎样选择HeapByteBuffer与DirectByteBuffer](./怎样选择HeapByteBuffer与DirectByteBuffer.md)）。

Reactor 模式
====

核心思想：注册"读就绪、写就绪、新连接"三类事件的处理器。单线程轮询选择就绪事件并执行对应处理器：

```java
interface ChannelHandler {
    void channelReadable(Channel channel);
    void channelWritable(Channel channel);
}
class Channel {
    Socket socket;
    Event event; // 读，写或者连接
}
class IoThread extends Thread {
    public void run() {
        Channel channel;
        while (channel = Selector.select()) { // 选择就绪的事件和对应的连接
            if (channel.event == accept) registerNewChannelHandler(channel);
            if (channel.event == write)  getChannelHandler(channel).channelWritable(channel);
            if (channel.event == read)   getChannelHandler(channel).channelReadable(channel);
        }
    }
}
Map<Channel, ChannelHandler> handlerMap;
```

（示意伪代码，不可用于生产环境。）

1 Reactor vs Proactor
------

* Reactor 基于同步 I/O：分发器只通知"可读写了"，实际读写由用户处理器完成；
* Proactor 基于异步 I/O（如 Windows overlapped I/O）：处理器发起异步操作，操作系统完成读写后再通知"已完成"；
* 可以把 Reactor 改造成"模拟 Proactor"：把读写从事件处理器移到分发器，分发器读到数据后再回调 channelReadComplete。工作量不变、性能不削弱。还能对不支持异步 API 的系统暴露统一的异步接口。

2 线程模型优化
------

多核机器上把线程职责拆开：

* 事件分发器：单线程选择就绪事件；
* I/O 处理器：纯 CPU 操作，开 CPU 核心数个线程；
* 业务线程：含 DB、RPC 这类阻塞操作时必须独立线程池；
* 限制：一个 socket 只能属于一个 IoThread，一个 IoThread 可以管理多个 socket。

NIO 在客户端的魔力
====

* **Redis 客户端**：单线程 + 请求队列，pipeline 批量发送请求并返回 future，channel 可读时从队列取出 future 并 done()；
* **HttpClient 短连接抓取**：单线程做连接/读/写，以 socket 引用作为 map 的 key 保存请求/响应上下文；
* **Thrift/Dubbo 等 RPC**：以请求号为 key、result 为 future 的 map，配合 NIO + 长连接实现同步语义。

Selector.wakeup()
====

作用：解除阻塞在 select()/select(long) 上的线程。两次 select 之间的多次调用等价于一次。没有线程阻塞时作用于下一次 select（"记忆"作用）。触发场景：注册新 channel/事件、channel 关闭取消注册、更高优先级事件（如定时器任务）到期。

原理：Linux 用 pipe，Windows 用 loopback TCP 连接（win32 管道无法加入 fd set）。往管道写入一个字节使 select 立即返回，开销不可忽视。

存在的问题
====

* 使用 NIO 不等于高性能：连接数 <1000、并发不高或局域网环境下没有明显优势；
* 没有完全屏蔽平台差异，仍基于各操作系统的 I/O 实现；
* 事件驱动模型编程不易、陷阱重重；
* 优化方向：**使用成熟框架如 Netty、MINA**。

总结
====

NIO 的价值在于：事件驱动模型、避免多线程、单线程处理多任务、非阻塞 I/O（读写直接返回 0）。以及基于 block 的传输更高效、zero-copy、IO 多路复用带来的可伸缩性。

> 内容来源：[原文](https://tech.meituan.com/2016/11/04/nio.html) · 2026-10 整理回填。
