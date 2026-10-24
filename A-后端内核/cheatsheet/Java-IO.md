# Java IO / NIO 速查卡

字节流 vs 字符流
====

| 维度 | 字节流 | 字符流 |
|------|--------|--------|
| 命名 | 以 Stream 结尾 | 以 Reader/Writer 结尾 |
| 祖先 | InputStream / OutputStream | Reader / Writer |
| 单位 | 按字节（8bit） | 按字符（按 VM 编码转换） |
| 缓冲区 | 不用缓冲区，直接操作文件 | 用缓冲区，关闭/flush 才输出 |
| 场景 | 二进制（图片/音视频），通用 | 文本，需按行/按字符处理时 |

1. 常见字节流：FileInputStream/OutputStream、BufferedInputStream/OutputStream、ObjectInputStream/OutputStream。
2. 常见字符流：转换流 InputStreamReader/OutputStreamWriter、BufferedReader、PrintWriter。
3. 缓冲区 = 一段内存，减少频繁操作资源；字符流不关闭时可用 `flush()` 强制输出。
4. 开发建议优先字节流：文件在硬盘/传输中都是字节，字符只在内存中形成。
5. 中文乱码本质 = 字节流与字符流互转（byte[] ↔ String）时字符集不统一：`new String(bytes, charsetName)` / `getBytes(charsetName)`。

四种 IO 模型
====

| 模型 | 描述 | Java 对应 |
|------|------|----------|
| 同步阻塞 | 发起 IO 后必须等完成 | 传统 IO（BIO） |
| 同步非阻塞 | 发起后可返回，需轮询就绪 | NIO |
| 异步阻塞 | select 监听多句柄，select 本身阻塞 | — |
| 异步非阻塞 | 内核完成 IO 后通知，进程只处理数据 | JDK 尚未支持（AIO 方向） |

1. IO 分两阶段：等待就绪（BIO 里"空等"不用 CPU）+ 真正读写（memory copy，快）。
2. `socket.read()`：BIO 无数据一直阻塞；NIO 无数据直接返回 0。
3. 一句话：BIO 关心"我要读"，NIO 关心"我可以读了"。

NIO 三大核心
====

Buffer（缓冲区）
----

1. NIO 面向缓冲区，IO 面向流；Channel 读写必须经过 Buffer。
2. Buffer = 数组 + 结构化访问 + 读写进度跟踪；类型：ByteBuffer/CharBuffer/IntBuffer/LongBuffer/DoubleBuffer/ShortBuffer/FloatBuffer。
3. 读写四步：写入 Buffer → `flip()` 切读模式 → 读取 → `clear()`（全清）/ `compact()`（只清已读）。
4. 四属性：`mark <= position <= limit <= capacity`；flip 即 `limit=position; position=0`。
5. HeapByteBuffer vs DirectByteBuffer：后者（堆外）可省一次内核态↔用户态拷贝，但创建销毁贵（用内存池）。数据量小用 heap，大/IO 密集用 direct。

Channel（通道）
----

1. 与流的区别：双向（读+写）、可异步读写、必须经 Buffer。
2. 主要类型：FileChannel（文件）、DatagramChannel（UDP）、SocketChannel（TCP）、ServerSocketChannel（监听 TCP 连接）。

Selector（选择器）
----

1. 一个 Selector 可注册多个 Channel，单线程管理多连接，避免每连接一线程。适合连接多、流量低的场景。
2. 使用步骤：`Selector.open()` → Channel `configureBlocking(false)` → `register(selector, 事件)` → 循环 `select()` → 遍历 `selectedKeys()` 处理 → `keyIterator.remove()`。
3. 四种事件：OP_ACCEPT / OP_CONNECT / OP_READ / OP_WRITE；可用 `|` 组合。
4. SelectionKey 含：interest set、ready set、channel、selector、attachment（可附 Buffer）。
5. select 三兄弟：`select()` 阻塞至就绪、`select(timeout)` 超时返回 0、`selectNow()` 立即返回。
6. 注意：FileChannel 不能注册 Selector（无异步模式）；ACCEPT 事件里 accept 出的 SocketChannel 常再注册 OP_READ。
7. 缺陷与优化：单线程处理全部事件无法利用多 CPU → 按事件类型拆分多个 Reactor。

堆外内存（直接内存）
====

1. 是什么：JVM Heap 之外的内存块，非 JVM 规范定义区域；`ByteBuffer.allocateDirect()` → 底层 DirectByteBuffer → `unsafe.allocateMemory()`（native，C malloc）。
2. 为什么：堆内数据写 Socket/文件前要先拷到堆外。直接放堆外省一次拷贝（Netty/RPC 常用）。
3. 分配流程（Bits.reserveMemory）：额度够 → 直接分配。不够 → 触发 `Reference#tryHandlePending(false)` 释放已 GC 的 DirectBuffer。仍不够 → `System.gc()`（Full GC）后最多 9 次循环重试。仍失败 → `OutOfMemoryError: Direct buffer memory`。
4. 额度：`-XX:MaxDirectMemorySize` 显式指定；否则默认 `Runtime.maxMemory()`（≈ -Xmx 减一个 survivor 预留）。
5. 回收：DirectByteBuffer 是"冰山对象"（堆内小、堆外大），被 GC 时通过 Cleaner（PhantomReference 子类）→ ReferenceHandler 线程调 `clean()` → Deallocator.run() → `unsafe.freeMemory()`。
6. 坑：小对象易进老年代不触发 Full GC → 堆外先 OOM。`-XX:+DisableExplicitGC` 会掐断 System.gc() 保险（Netty 因此自己做内存池主动释放）。
7. 人工释放：反射调用 DirectByteBuffer 私有 cleaner 的 clean()。

> 内容来源：[字符流和字节流区别](../java/Java%20IO/字节流和字符流区别.md) · [谈谈NIO](../java/Java%20IO/谈谈NIO.md) · [深入解读 Java 堆外内存](../java/JVM/JVM内存管理/补充-深入解读%20Java%20堆外内存（直接内存）.md) · 2026-10 整理回填。
