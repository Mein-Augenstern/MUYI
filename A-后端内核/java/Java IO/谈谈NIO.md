看在前面
====

* <a href="https://gitbook.cn/books/5b1792ad26a49a55324e782c/index.html">谈谈 Java NIO</a>

> 原创作者信息：应书澜

> 原创作者GitChat主页：<a href="https://gitbook.cn/gitchat/author/5a98122bfdc2050df046d997">应书澜</a>

> 目前正在联系GitChat获取转载权限，若有权限，立马删除^^。

在 JDK1.4 之后，为了提高 Java IO 的效率，Java 提供了一套 New IO (NIO)，之所以称之为 New，原因在于它相对于之前的 IO 类库是新增的。此外，旧的 IO 类库提供的 IO 方法是阻塞的，New IO 类库则让 Java 可支持非阻塞 IO，所以，更多的人喜欢称之为非阻塞 IO（Non-blocking IO）。

NIO 应用非常广泛，是 Java 进阶的必学知识，此外，在 Java 相关岗位的面试中也是“常客”，对于准备深入学习 Java 的读者，了解 NIO 确有必要。

Question
====

* IO 与 NIO 有何不同？
* NIO 核心对象 Buffer 详解；
* NIO 核心对象 Channel 详解；
* NIO 核心对象 Selector 详解；

1 IO 和 NIO 相关的预备知识
====

1.1 IO 的含义
------

讲 NIO 之前，我们先来看一下 IO。

Java IO 即 Java 输入输出。在开发应用软件时，很多时候都需要和各种输入输出相关的媒介打交道。与媒介进行 IO 操作的过程十分复杂，需要考虑众多因素，比如：进行 IO 操作**媒介的类型**（文件、控制台、网络）、**通信方式**（顺序、随机、二进制、按字符、按字、按行等等）。

Java 类库提供了相应的类来解决这些难题，这些类就位于 java.io 包中， 在整个 java.io 包中最重要的就是 5 个类和一个接口。5 个类指的是 ```File```、```OutputStream```、```InputStream```、```Writer```、```Reader```；一个接口指的是 ```Serializable```。

由于老的 Java IO 标准类提供 IO 操作（如 ```read()```，```write()```）都是同步阻塞的，因此，IO 通常也被称为阻塞 IO（即 BIO，```Blocking I/O```）。

1.2 NIO 含义
------

在 JDK1.4 之后，为了提高 Java IO 的效率，Java 又提供了一套 New IO（NIO），原因在于它相对于之前的 IO 类库是新增的。此外，旧的 IO 类库提供的 IO 方法是阻塞的，New IO 类库则让 Java 可支持非阻塞 IO，所以，更多的人喜欢称之为非阻塞 IO（Non-blocking IO）。

1.3 四种 IO 模型
------

**同步阻塞 IO：**

在此种方式下，用户进程在发起一个 IO 操作以后，必须等待 IO 操作的完成，只有当真正完成了 IO 操作以后，用户进程才能运行。 Java 传统的 IO 模型属于此种方式！ 

**同步非阻塞 IO：**

在此种方式下，用户进程发起一个 IO 操作以后 便可返回做其它事情，但是用户进程需要时不时的询问 IO 操作是否就绪，这就要求用户进程不停的去询问，从而引入不必要的 CPU 资源浪费。**其中目前 Java 的 NIO 就属于同步非阻塞 IO** 。 

**异步阻塞 IO：**

此种方式下是指应用发起一个 IO 操作以后，不等待内核 IO 操作的完成，等内核完成 IO 操作以后会通知应用程序，这其实就是同步和异步最关键的区别，同步必须等待或者主动的去询问 IO 是否完成，那么为什么说是阻塞的呢？因为此时是通过 select 系统调用来完成的，而 select 函数本身的实现方式是阻塞的，而采用 select 函数有个好处就是它可以同时监听多个文件句柄，从而提高系统的并发性！ 

**异步非阻塞 IO：**

在此种模式下，用户进程只需要发起一个 IO 操作然后立即返回，等 IO 操作真正的完成以后，应用程序会得到 IO 操作完成的通知，此时用户进程只需要对数据进行处理就好了，不需要进行实际的 IO 读写操作，因为 真正的 IO 读取或者写入操作已经由 内核完成了。目前 Java 中还没有支持此种 IO 模型。

1.4 小结
------

所有的系统 I/O 都分为两个阶段：**等待就绪和操作**。举例来说，读函数，分为等待系统可读和真正的读；同理，写函数分为等待网卡可以写和真正的写。Java IO 的各种流是阻塞的。这意味着当线程调用 write() 或 read() 时，线程会被阻塞，直到有一些数据可用于读取或数据被完全写入。

需要说明的是等待就绪引起的 “阻塞” 是不使用 CPU 的，是在 “空等”；而真正的读写操作引起的“阻塞” 是使用 CPU 的，是真正在”干活”，而且这个过程非常快，属于 memory copy，带宽通常在 1GB/s 级别以上，可以理解为基本不耗时。因此，所谓 “阻塞” 主要是指等待就绪的过程。

**以socket.read()为例子：**

传统的阻塞 IO(BIO) 里面 socket.read()，如果接收缓冲区里没有数据，函数会一直阻塞，直到收到数据，返回读到的数据。

而对于非阻塞 IO(NIO)，如果接收缓冲区没有数据，则直接返回 0，而不会阻塞；如果接收缓冲区有数据，就把数据从网卡读到内存，并且返回给用户。

说得接地气一点，BIO 里用户最关心 “我要读”，NIO 里用户最关心” 我可以读了”。NIO 一个重要的特点是：socket 主要的读、写、注册和接收函数，在等待就绪阶段都是非阻塞的，真正的 I/O 操作是同步阻塞的（消耗 CPU 但性能非常高）。

2 NIO 核心对象 Buffer 详解
====

> 为什么说 NIO 是基于缓冲区的 IO 方式呢？因为，当一个链接建立完成后，IO 的数据未必会马上到达，为了当数据到达时能够正确完成 IO 操作，在 BIO（阻塞 IO）中，等待 IO 的线程必须被阻塞，以全天候地执行 IO 操作。为了解决这种 IO 方式低效的问题，引入了缓冲区的概念，当数据到达时，可以预先被写入缓冲区，再由缓冲区交给线程，因此线程无需阻塞地等待 IO。

在正式介绍 Buffer 之前，我们先来 Stream，以便更深刻的理解 Java IO 与 NIO 的不同。

2.1 Stream
------

Java IO 是面向流的 I/O，这意味着我们需要从流中读取一个或多个字节。它使用流来在数据源/槽和 Java 程序之间传输数据。使用此方法的 I/O 操作较慢。下面来看看在 Java 程序中使用输入/输出流的数据流图 (注意：图中输入/输出均以 Java Program 为参照物)：

![Java-IO-stream示意图](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/Java-IO-stream%E7%A4%BA%E6%84%8F%E5%9B%BE.png)

2.2 Buffer
------

Buffer 是一个对象，它包含一些要写入或读出的数据。在 NIO 中，数据是放入 Buffer 对象的，而在 IO 中，数据是直接写入或者读到 Stream 对象的。应用程序不能直接对 Channel 进行读写操作，而必须通过 Buffer 来进行，即 Channel 是通过 Buffer 来读写数据的，如下示意图。

![Java-NIO-Buffer示意图](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/Java-NIO-Buffer%E7%A4%BA%E6%84%8F%E5%9B%BE.png)

在 NIO 中，所有的数据都是用 Buffer 处理的，它是 NIO 读写数据的中转池。Buffer 实质上是一个数组，通常是一个字节数据，但也可以是其他类型的数组。**但一个缓冲区不仅仅是一个数组，重要的是它提供了对数据的结构化访问，而且还可以跟踪系统的读写进程。**

**Buffer 读写步骤，使用 Buffer 读写数据一般遵循以下四个步骤：**

 1. 写入数据到 Buffer
 2. 调用 flip() 方法
 3. 从 Buffer 中读取数据
 4. 调用 clear() 方法或者 compact() 方法

当向 Buffer 写入数据时，Buffer 会记录下写了多少数据。一旦要读取数据，需要通过 flip() 方法将 Buffer 从写模式切换到读模式。在读模式下，可以读取之前写入到 Buffer 的所有数据。

一旦读完了所有的数据，就需要清空缓冲区，让它可以再次被写入。有两种方式能清空缓冲区：调用 clear() 或 compact() 方法。clear() 方法会清空整个缓冲区。compact() 方法只会清除已经读过的数据。任何未读的数据都被移到缓冲区的起始处，新写入的数据将放到缓冲区未读数据的后面。

**Buffer种类，Buffer主要有如下几种：**

```CharBuffer、DoubleBuffer、IntBuffer、LongBuffer、ByteBuffer、ShortBuffer、FloatBuffer```

上述缓冲区覆盖了我们可以通过 I/O 发送的基本数据类型：

```characters，double，int，long，byte，short和float```

2.3 Buffer 结构
------

Buffer 有几个重要的属性如下：

```java
// Invariants: mark <= position <= limit <= capacity
private int mark = -1;
private int position = 0;
private int limit;
private int capacity;
```

结合如下结构图解释一下：

* position 记录当前读取或者写入的位置，写模式下等于当前写入的单位数据数量，从写模式切换到读模式时，置为 0，在读的过程中等于当前读取单位数据的数量；
* limit 代表最多能写入或者读取多少单位的数据，写模式下等于最大容量 capacity；从写模式切换到读模式时，等于 position，然后再将 position 置为 0，所以，读模式下，limit 表示最大可读取的数据量，这个值与实际写入的数量相等。
* capacity 表示 buffer 容量，创建时分配。

之所以介绍这一节是为了更好的解释为何写/读模式切换时需要调用 flip() 方法，通过上述解释，相信读者已经明白为何写/读模式切换需要调用 flip() 方法了。附上 flip() 方法的解释：

> Flips this buffer. The limit is set to the current position and then the position is set to zero. If the mark is defined then it is discarded.

其中```flip()```方法源码如下：

```java
/**
 * Flips this buffer.  The limit is set to the current position and then
 * the position is set to zero.  If the mark is defined then it is
 * discarded.
 *
 * <p> After a sequence of channel-read or <i>put</i> operations, invoke
 * this method to prepare for a sequence of channel-write or relative
 * <i>get</i> operations.  For example:
 *
 * <blockquote><pre>
 * buf.put(magic);    // Prepend header
 * in.read(buf);      // Read data into rest of buffer
 * buf.flip();        // Flip buffer
 * out.write(buf);    // Write header + data to channel</pre></blockquote>
 *
 * <p> This method is often used in conjunction with the {@link
 * java.nio.ByteBuffer#compact compact} method when transferring data from
 * one place to another.  </p>
 *
 * @return  This buffer
 */
public final Buffer flip() {
    limit = position;
    position = 0;
    mark = -1;
    return this;
}
```

![Java-NIO-Buffer-读写模式](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/Java-NIO-Buffer-%E8%AF%BB%E5%86%99%E6%A8%A1%E5%BC%8F.jpeg)

2.4 Buffer 的选择
------

通常情况下，操作系统的一次写操作分为两步：

 1. 将数据从用户空间拷贝到系统空间（即从 JVM 内存拷贝到系统内存）。
 2. 从系统空间往网卡写。

同理，读操作也分为两步：

 1. 将数据从网卡拷贝到系统空间；
 2. 将数据从系统空间拷贝到用户空间。

对于 NIO 来说，缓存的使用可以使用```DirectByteBuffer```（堆外内存，关于堆外内存，如果存在疑问请阅读<a href="https://gitbook.cn/gitchat/activity/5af07387585c260a21a32b97">作为 Java 开发者，你需要了解的堆外内存知识</a>）和 HeapByteBuffer（堆外内存）。如果使用了 DirectByteBuffer，一般来说可以减少一次系统空间到用户空间的拷贝。但Buffer创建和销毁的成本更高，更不宜维护，通常会用内存池来提高性能。如果数据量比较小的中小应用情况下，可以考虑使用 heapBuffer；反之可以用 directBuffer。

2.5 Buffer 使用实例
------

```java
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class IO_Demo
{
    public static void main(String[] args) throws Exception
    {
        String infile = "D:\\Users\\data.txt";
        String outfile = "D:\\Users\\dataO.txt";
        // 获取源文件和目标文件的输入输出流
        FileInputStream fin = new FileInputStream(infile);
        FileOutputStream fout = new FileOutputStream(outfile);
        // 获取输入输出通道
        FileChannel fileChannelIn = fin.getChannel();
        FileChannel fileChannelOut = fout.getChannel();
        // 创建缓冲区，分配1K堆内存
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        while (true)
        {
            // clear方法重设缓冲区，使它可以接受读入的数据
            buffer.clear();
            // 从输入通道中读取数据数据并写入buffer
            int r = fileChannelIn.read(buffer);
            // read方法返回读取的字节数，可能为零，如果该通道已到达流的末尾，则返回-1
            if (r == -1)
            {
                break;
            }
            // flip方法将 buffer从写模式切换到读模式
            buffer.flip();
            // 从buffer中读取数据然后写入到输出通道中
            fileChannelOut.write(buffer);
        }
        //关闭通道
        fileChannelOut.close();
        fileChannelIn.close();
        fout.close();
        fin.close();
    }
}
```

代码参照:<a href="https://github.com/DemoTransfer/demotransfer/tree/master/java/nio">nio</a>

3 NIO 核心对象 Channel 详解
====

3.1 简要回顾
------

第一节中的例子所示，当执行：```fileChannelOut.write(buffer)```，便将一个 buffer 写到了一个通道中。相较于缓冲区，通道更加抽象，因此，我在第一节详细介绍了缓冲区，并穿插了通道的内容。

引用 Java NIO 中权威的说法：通道是 I/O 传输发生时通过的入口，而缓冲区是这些数据传输的来源或目标。对于离开缓冲区的传输，需要输出的数据被置于一个缓冲区，然后写入通道。对于传回缓冲区的传输，一个通道将数据写入缓冲区中。

例如：

> 有一个服务器通道serverChannel，一个客户端通道 SocketChannel clientChannel；

> 服务器缓冲区：serverBuffer，客户端缓冲区：clientBuffer。

> * 当服务器想向客户端发送数据时，需要调用 clientChannel.write(serverBuffer)。当客户端要读时，调用 clientChannel.read(clientBuffer)

> * 当客户端想向服务器发送数据时，需要调用 serverChannel.write(clientBuffer)。当服务器要读时，调用 serverChannel.read(serverBuffer)

3.2 关于 Channel
------

Channel 是一个对象，可以通过它读取和写入数据。可以把它看做 IO 中的流。但是它和流相比还有一些不同：

* Channel 是双向的，既可以读又可以写，而流是单向的（所谓输入/输出流）；
* Channel 可以进行异步的读写；
* 对 Channel 的读写必须通过 buffer 对象；

正如上面提到的，所有数据都通过 Buffer 对象处理，所以，输出操作时不会将字节直接写入到 Channel 中，而是将数据写入到 Buffer 中；同样，输入操作也不会从 Channel 中读取字节，而是将数据从 Channel 读入 Buffer，再从 Buffer 获取这个字节。

因为 Channel 是双向的，所以 Channel 可以比流更好地反映出底层操作系统的真实情况。特别是在 Unix 模型中，底层操作系统通常都是双向的。

在 Java NIO 中 Channel 主要有如下几种类型：

* FileChannel：从文件读取数据的
* DatagramChannel：读写 UDP 网络协议数据
* SocketChannel：读写 TCP 网络协议数据
* ServerSocketChannel：可以监听 TCP 连接

4 NIO 核心对象 Selector 详解
====

4.1 关于 Selector
------

通道和缓冲区的机制，使得 Java NIO 实现了同步非阻塞 IO 模式，在此种方式下，用户进程发起一个 IO 操作以后便可返回做其它事情，而无需阻塞地等待 IO 事件的就绪，但是用户进程需要时不时的询问 IO 操作是否就绪，这就要求用户进程不停的去询问，从而引入不必要的 CPU 资源浪费。

鉴于此，需要有一个机制来监管这些 IO 事件，如果一个 Channel 不能读写（返回 0），我们可以把这件事记下来，然后切换到其它就绪的连接（channel）继续进行读写。在 Java NIO 中，这个工作由 selector 来完成，这就是所谓的同步。

Selector 是一个对象，它可以接受多个 Channel 注册，监听各个 Channel 上发生的事件，并且能够根据事件情况决定 Channel 读写。这样，通过一个线程可以管理多个 Channel，从而避免为每个 Channel 创建一个线程，节约了系统资源。如果你的应用打开了多个连接（Channel），但每个连接的流量都很低，使用 Selector 就会很方便。

要使用 Selector，就需要向 Selector 注册 Channel，然后调用它的 select() 方法。这个方法会一直阻塞到某个注册的通道有事件就绪，这就是所说的轮询。一旦这个方法返回，线程就可以处理这些事件。

下面这幅图展示了一个线程处理 3 个 Channel 的情况：

![Java-NIO-一个线程处理三个Channel](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/Java-NIO-%E4%B8%80%E4%B8%AA%E7%BA%BF%E7%A8%8B%E5%A4%84%E7%90%86%E4%B8%89%E4%B8%AAChannel.png)

4.2 Selector 使用
------

**1.创建 Selector 对象**

通过 Selector.open() 方法，我们可以创建一个选择器：

```java
Selector selector = Selector.open();
```

 **2. 将 Channel 注册到选择器中**
 
为了使用选择器管理 Channel，我们需要将 Channel 注册到选择器中:
 
```java
channel.configureBlocking(false);
SelectionKey key =channel.register(selector,SelectionKey.OP_READ);
```
 
注意，注册的 Channel 必须设置成异步模式才可以，否则异步 IO 就无法工作，这就意味着我们不能把一个 FileChannel 注册到 Selector，因为 FileChannel 没有异步模式，但是网络编程中的 SocketChannel 是可以的。
 
需要注意 register() 方法的第二个参数，它是一个“interest set”，意思是注册的 Selector 对 Channel 中的哪些事件感兴趣，事件类型有四种（对应 SelectionKey 的四个常量）：

* OP_ACCEPT
* OP_CONNECT
* OP_READ
* OP_WRITE

通道触发了一个事件意思是该事件已经 Ready（就绪）。所以，某个 Channel 成功连接到另一个服务器称为 Connect Ready。一个 ServerSocketChannel 准备好接收新连接称为 Accept Ready，一个有数据可读的通道可以说是 Read Ready，等待写数据的通道可以说是 Write Ready。

如果你对多个事件感兴趣，可以通过 or 操作符来连接这些常量：

```java
int interestSet = SelectionKey.OP_READ | SelectionKey.OP_WRITE; 
```

**3. 关于 SelectionKey**

请注意对 register() 的调用的返回值是一个 SelectionKey。 SelectionKey 代表这个通道在此 Selector 上的这个注册。当某个 Selector 通知您某个传入事件时，它是通过提供对应于该事件的 SelectionKey 来进行的。SelectionKey 还可以用于取消通道的注册。SelectionKey 中包含如下属性：

* The interest set
* The ready set
* The Channel
* The Selector
* An attached object (optional)

这几个属性很好理解，interest set 代表感兴趣事件的集合；ready set 代表通道已经准备就绪的操作的集合；Channel 和 Selector：我们可以通过 SelectionKey 获得 Selector 和注册的 Channel；attached object ：可以将一个对象或者更多信息 attach 到 SelectionKey 上，这样就能方便的识别某个给定的通道。例如，可以附加与通道一起使用的 Buffer。

SelectionKey 还有几个重要的方法，用于检测 Channel 中什么事件或操作已经就绪，它们都会返回一个布尔类型：

```java
selectionKey.isAcceptable();
selectionKey.isConnectable();
selectionKey.isReadable();
selectionKey.isWritable(); 
```

**4. 通过 SelectionKeys() 遍历**

从上文我们知道，对于每一个注册到 Selector 中的 Channel 都有一个对应的 SelectionKey，那么，多个 Channel 注册到 Selector 中，必然形成一个 SelectionKey 集合，通过 SelectionKeys() 方法可以获取这个集合。因此，当 Selector 检测到有通道就绪后，我们可以通过调用 selector.selectedKeys() 方法返回的 SelectionKey 集合来遍历，进而获得就绪的 Channel，再进一步处理。实例代码如下：

```java
// 获取注册到selector中的Channel对应的selectionKey集合
Set<SelectionKey> selectedKeys = selector.selectedKeys();
// 通过迭代器进行遍历，获取已经就绪的Channel，
Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
while (keyIterator.hasNext()) {
	SelectionKey key = keyIterator.next();
	if (key.isAcceptable()) {
		// a connection was accepted by a ServerSocketChannel.
		// 可通过Channel()方法获取就绪的Channel并进一步处理
		SocketChannel channel = (SocketChannel) key.channel();
		// TODO

	} else if (key.isConnectable()) {
		// TODO

	} else if (key.isReadable()) {
		// TODO

	} else if (key.isWritable()) {
		// TODO

	}

	// 删除处理过的事件
	keyIterator.remove();
}
```

**5. select() 方法检测 Selector 中是否有 Channel 就绪**

在进行遍历之前，我们至少应该知道是否已经有 Channel 就绪，否则遍历完全是徒劳。Selector 提供了 select() 方法，它会返回一个数值，代表就绪 Channel 的数量，如果没有 Channel 就绪，将一直阻塞。除了 select()，还有其它几种，如下：

* int select()： 阻塞到至少有一个通道就绪；
* int select(long timeout)：select() 一样，除了最长会阻塞 timeout 毫秒（参数），超时后返回0，表示没有通道就绪；
* int selectNow()：不会阻塞，不管什么通道就绪都立刻返回，此方法执行非阻塞的选择操作。如果自从前一次选择操作后，没有通道变成可选择的，则此方法直接返回零。

加入 select() 方法后的代码：

```java
// 反复循环,等待IO
while (true) {
	// 等待某信道就绪,将一直阻塞，直到有通道就绪
	selector.select();
	// 获取注册到selector中的Channel对应的selectionKey集合
	Set<SelectionKey> selectedKeys = selector.selectedKeys();
	// 通过迭代器进行遍历，获取已经就绪的Channel，
	Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
	while (keyIterator.hasNext()) {
		SelectionKey key = keyIterator.next();
		if (key.isAcceptable()) {
			// a connection was accepted by a ServerSocketChannel.
			// 可通过Channel()方法获取就绪的Channel并进一步处理
			SocketChannel channel = (SocketChannel) key.channel();
			// TODO

		} else if (key.isConnectable()) {
			// TODO

		} else if (key.isReadable()) {
			// TODO

		} else if (key.isWritable()) {
			// TODO

		}

		// 删除处理过的事件
		keyIterator.remove();
	}
}
```

4.3 Selector 使用实例
------

```java
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class NIOElementSelectorTCPServerDemo {

	/** 超时时间，单位毫秒 **/
	private static final int TIMEOUT = 1994;

	/** 本地监听端口 **/
	private static final int LISTENPORT = 1994;

	public static void main(String[] args) throws IOException {
		// 创建选择器
		Selector selector = Selector.open();
		// 打开监听通道
		ServerSocketChannel listenChannel = ServerSocketChannel.open();
		// 与本地端口绑定
		listenChannel.socket().bind(new InetSocketAddress(LISTENPORT));
		// 设置为非阻塞模式
		listenChannel.configureBlocking(false);
		// 将选择器绑定到监听信道，只有非阻塞信道才可以注册选择器，并在注册过程中指出该信道可以进行Accept操作
		// 一个serversocket channel 准备好接收新进入的连接称为“接收就绪”
		listenChannel.register(selector, SelectionKey.OP_ACCEPT);

		// 反复循环，等待IO
		while (true) {
			// 等待某信道就绪（或超时）
			int keys = selector.select(TIMEOUT);
			// 刚启动时连续输出0，client连接后一直输出1
			if (keys == 0) {
				System.out.println("独自等待.");
				continue;
			}

			// 取得迭代器，遍历每一个注册的通道
			Set<SelectionKey> set = selector.selectedKeys();
			Iterator<SelectionKey> keyIterator = set.iterator();
			while (keyIterator.hasNext()) {
				SelectionKey key = keyIterator.next();
				if (key.isAcceptable()) {
					// a connection was accepted by a serverSocketChannel
					// 可通过Channel()方法获取就绪的Channel并进一步处理
					SocketChannel channel = (SocketChannel) key.channel();
					// TODO

				} else if (key.isConnectable()) {
					// TODO

				} else if (key.isReadable()) {
					// TODO

				} else if (key.isWritable()) {
					// TODO

				}

				// 删除处理过的事件
				keyIterator.remove();
			}
		}
	}

}
```

特别说明：例子中 selector 只注册了一个 Channel，注册多个 Channel 操作类似。如下：

```java
for (int i = 0; i < 3; i++) {
	// 打开监听信道
	ServerSocketChannel listenerChannel = ServerSocketChannel.open();
	// 与本地端口绑定
	listenerChannel.socket().bind(new InetSocketAddress(ListenPort + i));
	// 设置为非阻塞模式
	listenerChannel.configureBlocking(false);
	// 注册到selector中
	listenerChannel.register(selector, SelectionKey.OP_ACCEPT);
}
```

在上面的例子中，对于通道 IO 事件的处理并没有给出具体方法，在此，举一个更详细的例子：

```java
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class NIOElementSelectorLearningDemo {

	private static final int BUF_SIZE = 256;

	private static final int TIMEOUT = 3000;

	public static void main(String[] args) throws IOException {
		// 打开服务端Socket
		ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
		// 打开Selector
		Selector selector = Selector.open();
		// 服务端Socket监听8080端口，并配置为非阻塞模式
		serverSocketChannel.socket().bind(new InetSocketAddress(8080));
		serverSocketChannel.configureBlocking(false);
		// 将channel注册到selector中，通常我们都是先注册一个OP_ACCEPT事件，然后在OP_ACCEPT到来时，在将这个channel的OP_READ注册到Selector中
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		while (true) {
			// 通过调用select方法，阻塞的等待channel I/O可操作
			if (selector.select(TIMEOUT) == 0) {
				System.out.println("超时等待......");
				continue;
			}

			// 获取I/O操作就绪的SelectionKey，通过SelectionKey可以知道哪些Channel的哪类I/O操作已经就绪
			Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
			while (keyIterator.hasNext()) {
				SelectionKey key = keyIterator.next();
				// 当获取一个SelectionKey后，就要将它删除，表示我们已经对这个IO事件进行了处理
				keyIterator.remove();

				if (key.isAcceptable()) {
					// 当OP_ACCEPT事件到来时，我们就有从ServerSocketChannel中获取一个SocketChannel，代表客户端的连接
					// 注意，在OP_ACCEPT事件中，从key.channel()返回的是ServerSocketChannel
					// 而在 OP_WRITE 和 OP_READ 中，从key.channel() 返回的是SocketChannel
					SocketChannel clientChannel = ((ServerSocketChannel) key.channel()).accept();
					clientChannel.configureBlocking(false);
					// 在OP_ACCEPT到来时，在将这个Channel的OP_READ注册到Selector中
					// 注意，这里我们如果没有设置 OP_READ 的话，即interest set仍然是 OP_CONNECT的话，那么select方法会一直直接返回
					clientChannel.register(key.selector(), SelectionKey.OP_READ, ByteBuffer.allocate(BUF_SIZE));
				}

				if (key.isReadable()) {
					SocketChannel clientChannel = (SocketChannel) key.channel();
					ByteBuffer buf = (ByteBuffer) key.attachment();
					int bytesRead = clientChannel.read(buf);
					if (bytesRead == -1) {
						clientChannel.close();
					} else if (bytesRead > 0) {
						key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
						System.out.println("Get data length:" + bytesRead);
					}
				}

				if (key.isValid() && key.isWritable()) {
					ByteBuffer buf = (ByteBuffer) key.attachment();
					buf.flip();
					SocketChannel clientChannel = (SocketChannel) key.channel();

					clientChannel.write(buf);

					if (!buf.hasRemaining()) {
						key.interestOps(SelectionKey.OP_READ);
					}
					buf.compact();
				}

			}
		}
	}

}
```

4.4 小结
------

如从上述实例所示，可以将多个 Channel 注册到同一个 Selector 对象上，实现一个线程同时监控多个 Channel 的请求状态，但有一个不容忽视的缺陷：

所有读/写请求以及对新连接请求的处理都在同一个线程中处理，无法充分利用多 CPU 的优势，同时读/写操作也会阻塞对新连接请求的处理。因此，有必要进行优化，可以引入多线程，并行处理多个读/写操作。

一种优化策略是：

将 Selector 进一步分解为 Reactor，从而将不同的感兴趣事件分开，每一个 Reactor 只负责一种感兴趣的事件。这样做的好处是：

* 分离阻塞级别，减少了轮询的时间；
* 线程无需遍历 set 以找到自己感兴趣的事件，因为得到的 set 中仅包含自己感兴趣的事件。下文将要介绍的 Reactor 模式便是这种优化思想的一种实现。
