Java NIO 三组件：Buffer、Channel、Selector
====

NIO 面试的第一道门就是三大组件的 API 语义：Buffer 的 position/limit/capacity 怎么变、flip() 到底做了什么、四种 SelectionKey 事件分别是什么、select() 和 wakeup() 的语义。**答不清这几个点，后面再谈 Reactor、Netty 都是空中楼阁**。本文定位是 API 层的逐个击破；模型与选型层面的讨论（BIO 的死穴、Reactor 模式、epoll）见库内 [Java NIO原理](Java%20NIO原理.md)，非阻塞与异步的对比见姊妹篇 [非阻塞IO与异步IO辨析](非阻塞IO与异步IO辨析.md)。

1 Buffer：一块可以反复读写的内存
------

Buffer 本质是内存中的一块，可以往里写数据，也可以从里面取数据。java.nio 下有一族实现（ByteBuffer、IntBuffer、CharBuffer、DoubleBuffer……），**核心只有 ByteBuffer**，其余类型只是把同一块内存按不同基本类型包装；MappedByteBuffer 用于内存映射文件。可以把 Buffer 直接理解为一个数组：IntBuffer 对应 int[]，CharBuffer 对应 char[]。

Buffer 有四个关键属性：

* **capacity**：容量，构造后不可变。capacity=1024 的 IntBuffer 最多放 1024 个 int，写满后必须清空才能重写；
* **position**：下一个读写位置。初始为 0，每写/读一个值自动加 1；
* **limit**：写模式下等于 capacity；flip 切到读模式后等于实际写入的数据量（Buffer 不一定写满）；
* **mark**：暂存 position 的标记位，配合 reset() 回退。

初始化用静态方法 `allocate(int capacity)` 或 `wrap(byte[] array)`：

```java
ByteBuffer byteBuf = ByteBuffer.allocate(1024);
IntBuffer intBuf = IntBuffer.allocate(1024);
ByteBuffer wrapBuf = ByteBuffer.wrap(bytes);
```

填充用 put 族方法，越界抛 java.nio.BufferOverflowException：

```java
public abstract ByteBuffer put(byte b);
public final ByteBuffer put(byte[] src) {...}
public ByteBuffer put(byte[] src, int offset, int length) {...}
```

这里有个初学者最容易绕晕的方向问题：**NIO 语境的"读操作"指 `channel.read(buf)`——数据从外部（文件/网络）进入 Buffer，对 Buffer 而言是写入**；"写操作"指 `channel.write(buf)`——数据从 Buffer 流向 Channel。Channel 只跟 Buffer 打交道，read 返回实际搬运的字节数。

读 Buffer 之前必须先切换模式，flip() 的 JDK 源码就是三个赋值：

```java
public final Buffer flip() {
    limit = position; // limit 置为实际写入量
    position = 0;     // 从头开始读
    mark = -1;
    return this;
}
```

对应地，取数用 get 族方法：`get()` 按 position 顺取、`get(int index)` 取指定位置、`get(byte[] dst)` 批量取出；`new String(buffer.array()).trim()` 是常见收尾姿势。

四个"重置类"方法的区别是高频考点：

* **rewind()**：position=0、mark=-1，不动 limit——重读一遍用；
* **clear()**：position=0、limit=capacity、mark=-1，**并不清空数据**，只是让后续写入可以覆盖旧值，重新写满前常用；
* **compact()**：把 [position, limit) 之间还没读的数据挪到开头，position 指向残留数据之后——**半包 sticky 场景用它**，未读完的字节不会被新数据冲掉；
* **mark()/reset()**：mark() 记下当前 position；reset() 把 position 回退到 mark，mark 未设置时抛 InvalidMarkException。

```java
public final Buffer mark() { mark = position; return this; }
public final Buffer reset() {
    int m = mark;
    if (m < 0) throw new InvalidMarkException();
    position = m;
    return this;
}
```

2 Channel：数据进出的大门
------

所有 NIO 操作始于通道，Channel 是数据来源或写往的目的地，java.nio 中主要四个实现：

* **FileChannel**：文件读写；
* **DatagramChannel**：UDP 收发；
* **SocketChannel**：TCP 连接通道，可理解为 TCP 客户端（也代表服务端 accept 出来的连接）；
* **ServerSocketChannel**：TCP 服务端，监听端口。

**重点在 SocketChannel 和 ServerSocketChannel**，FileChannel 只是顺带熟悉。FileChannel 从流上获取，读写同样围绕 Buffer：

```java
FileChannel fileChannel = new FileInputStream(new File("/data.txt")).getChannel();
int num = fileChannel.read(buffer);            // 文件 -> Buffer
buffer.flip();
while (buffer.hasRemaining()) {
    fileChannel.write(buffer);                 // Buffer -> 文件
}
```

注意：**FileChannel 不支持非阻塞模式**，这是后面 Selector 一节的伏笔。

SocketChannel 两种打开姿势等价，读写与 FileChannel 无异：

```java
SocketChannel sc = SocketChannel.open(new InetSocketAddress(host, 80));
// 等价于：
SocketChannel sc = SocketChannel.open();
sc.connect(new InetSocketAddress(host, 80));
```

ServerSocketChannel 只做一件事：监听端口，accept 到连接后生成 SocketChannel 交给业务，自己回去继续监听——**它不碰 Buffer，因为不实际处理数据**：

```java
ServerSocketChannel server = ServerSocketChannel.open();
server.socket().bind(new InetSocketAddress(8080));
while (true) {
    SocketChannel socketChannel = server.accept(); // 阻塞，直到有连接进来
}
```

到这里可以看到 SocketChannel 的两个身份：客户端主动 connect 出来的通道，和服务端 accept 出来的连接通道，本质都是"可读可写的网络通道"。UDP 则不同，DatagramChannel 一个类同时充当服务端和客户端：bind 之后 `receive(buf)` 收包，`send(buf, new InetSocketAddress(...))` 发包，无连接、不保证送达。

3 Selector：一个线程管多个 Channel
------

Selector 建立在非阻塞之上，也就是常说的**多路复用**。使用三步走：

```java
Selector selector = Selector.open();          // 1. 开选择器
channel.configureBlocking(false);             // 2. 必须先切非阻塞
SelectionKey key = channel.register(selector, SelectionKey.OP_READ); // 3. 注册
```

**FileChannel 不能注册**（不支持非阻塞），能注册的是 SocketChannel/ServerSocketChannel 等。register 的第二个参数是二进制标记位，四种事件：

| 事件 | 值 | 含义 |
| ---- | ---- | ---- |
| OP_READ | 0000 0001 | 通道中有数据可读 |
| OP_WRITE | 0000 0100 | 通道可写 |
| OP_CONNECT | 0000 1000 | TCP 连接建立成功 |
| OP_ACCEPT | 0001 0000 | 收到新的 TCP 连接 |

监听多事件就按位或：OP_ACCEPT | OP_READ = 0001 0001 = 17。register 返回 SelectionKey，携带 Channel、Selector 以及 interest set（感兴趣的事件集合）。

主循环的标准骨架：

```java
while (true) {
    int readyChannels = selector.select();    // 无就绪则阻塞
    if (readyChannels == 0) continue;
    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
    while (it.hasNext()) {
        SelectionKey key = it.next();
        if (key.isAcceptable()) { /* 新连接 */ }
        else if (key.isReadable()) { /* 读数据 */ }
        it.remove();                          // 必须手动移除，否则重复处理
    }
}
```

四个方法必须分清：**select()** 阻塞到至少一个通道就绪，把就绪通道对应的 SelectionKey 放入 selected set；**selectNow()** 立即返回（可能为 0）；**select(long timeout)** 等待一段时间；**wakeup()** 唤醒阻塞在 select 上的线程——若调用时没有线程在阻塞，会让**下一次** select 立即返回，且只作用一次（"记忆"语义，原理与实现见 [Java NIO原理](Java%20NIO原理.md) 的 Selector.wakeup() 一节）。

面试追问链
------

* flip() 做了什么？→ limit=position、position=0、mark=-1；一句话：把"写到哪"翻译成"读到哪"；
* clear() 会清数据吗？→ 不会，只重置三个指针，旧数据等新写入覆盖；读了一半还要继续写怎么办？→ compact()；
* 为什么 FileChannel 不能注册 Selector？→ 文件 IO 在所有操作系统上都没有非阻塞模式；
* OP_READ 和 OP_ACCEPT 的值分别是多少？→ 1<<0 和 1<<4，事件集合用位运算组合；
* selectedKeys 遍历里不调 iterator.remove() 会怎样？→ 就绪集合不会自动清理，下一轮重复消费同一事件；
* select() 是阻塞的，别的线程想注册新 Channel 怎么办？→ wakeup() 唤醒，注册完成后再进入 select。

面试官视角小结
------

三大组件是 NIO 的地基题。及格线：说得出三组件职责，channel.read/write 的方向不搞反。良好：能画出 position/limit/capacity 在写模式、flip、读模式、clear 四个阶段的迁移，并解释 compact 的半包场景。资深信号：能从 OP_* 的位运算讲到 interest set 与 ready set 的区别，指出 selector.select() 在 Linux 上最终落到 epoll_wait（呼应 [Java NIO原理](Java%20NIO原理.md)），并把这套 API 直接迁移到 Tomcat/Netty 的源码语境——Acceptor/Poller 与 EventLoop 的 select 循环正是同一套东西的工程化。

> 来源：javadoop《Java NIO：Buffer、Channel 和 Selector》（https://javadoop.com/post/java-nio）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
