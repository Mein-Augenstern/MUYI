怎样选择HeapByteBuffer与DirectByteBuffer
====

> 语境：JDK NIO 的 `ByteBuffer.allocate()` 分配在** JVM 堆内**，返回 HeapByteBuffer；`ByteBuffer.allocateDirect()` 分配在**堆外本地内存**，返回 DirectByteBuffer。这道题是 NIO 面试的进阶题，考的是"你知道 IO 的数据到底在内存里怎么流动的吗"。

一句话结论先给出：

* **IO 密集、缓冲区生命周期长、追求吞吐** → DirectByteBuffer（配合池化更佳）；
* **短生命周期、数据要留在 JVM 里频繁解析/修改、按字节 get/put 频繁** → HeapByteBuffer；
* **用 Netty 等框架** → 默认池化的 Direct，交给框架即可，重点是记得 release。

1 两者到底是什么：先看内存布局
------

```
                     JVM 进程地址空间
+------------------------------------------------------+
|  Java 堆 (-Xmx 控制, GC 管辖)                          |
|   +----------------+   +-------------------------+  |
|   | HeapByteBuffer |   |  普通业务对象 ...         |  |
|   | 持有 byte[] 元素 |   |  Minor/Full GC 会移动它们 |  |
|   +----------------+   +-------------------------+  |
+------------------------------------------------------+
|  本地内存 / Off-heap (受 -XX:MaxDirectMemorySize 约束)  |
|   +----------------------+  +---------------------+  |
|   | DirectByteBuffer 指向  |  | 线程栈/CodeCache/    |  |
|   | 的连续内存块(malloc)    |  | JVM 自身结构 ...     |  |
|   | GC 只管壳对象, 不搬数据 |  +---------------------+  |
|   +----------------------+                           |
+------------------------------------------------------+
        |                    |
        | read()/write() 系统调用必须使用堆外这块稳定地址 |
        v                    v
   内核缓冲区(Page Cache / Socket 收发缓冲)
```

* `HeapByteBuffer`：壳对象在堆里，真正的数据在堆内的 `byte[]`，受 GC 管理，**GC 搬家时数据地址会变**；
* `DirectByteBuffer`：壳对象也在堆里（很小，只有地址引用和 Cleaner）。数据在堆外一块**地址固定**的本地内存，GC 不搬运数据本身。

2 原理：为什么内核 IO 不能直接用堆内数组
------

2.1 核心矛盾：GC 搬家 vs 系统调用要稳定地址
------

`channel.read(buf)` 最终落到 `read()/write()` 系统调用上，内核要拿着用户态缓冲区的**起始地址**去（DMA）搬运数据。假设直接把堆内 `byte[]` 的地址交给内核：

* 系统调用执行期间线程阻塞在内核态，但 **GC 可以在另外的线程运行**；
* 一旦发生复制/压缩，`byte[]` 被搬走，内核还在往旧地址写数据 → 内存损坏；
* HotSpot 不支持把对象"钉住（pinning）"不动，所以 JDK 只能换方案。

2.2 JDK 的解法：临时 DirectBuffer 中转（隐藏拷贝）
------

在 JDK 的 IOUtil 实现里，如果传进来的是 HeapByteBuffer，会**先拷贝到一个线程本地的临时 DirectByteBuffer 池**（`Util.getTemporaryDirectBuffer`）。再用它去做系统调用：

```
HeapByteBuffer 参与 IO 的真实路径：

  内核  ==拷贝==>  临时 DirectByteBuffer  ==拷贝==>  堆内 byte[]
                   (每线程缓存复用)              (业务最终要读的数据)

DirectByteBuffer 参与 IO 的路径：

  内核  ==拷贝==>  堆外内存 (业务直接在这里读)
```

结论：**用堆内 buffer 做 Channel IO，每次都多一次 CPU 拷贝**；用堆外 buffer 则省掉这一次。这就是"IO 密集选 Direct"的根源。

3 性能对比：三个维度
------

| 维度 | HeapByteBuffer | DirectByteBuffer |
|------|----------------|------------------|
| 分配成本 | 便宜（TLAB 内一次指针碰撞，几乎免费） | 贵（JNI malloc + 全局预留计数 Bits.reserveMemory，空间不足还可能触发 System.gc() 等待回收） |
| 释放成本 | GC 自动 | 依赖 Cleaner（PhantomReference）在对象被回收时异步 free；Netty 则是引用计数手动 release |
| 单元素 get/put | 快（数组访问，JIT 可深度优化） | 慢（每次都要边界检查 + Unsafe 按地址访问堆外内存） |
| 批量读写（Channel IO） | 多一次中转拷贝 | 直接与内核交互，吞吐更高 |
| GC 影响 | 占堆、加剧 GC；大缓冲区进出老年代代价高 | 不占堆，降低 GC 压力与停顿；但泄漏难发现 |
| 拿数组 | `hasArray()`=true，可直接 `array()` | 没有 backing array，`array()` 抛异常 |
| 内存上限 | -Xmx | -XX:MaxDirectMemorySize（默认约等于 -Xmx） |

3.1 什么时候 Heap 反而更快
------

* 数据读进来**立即要逐字节解析**（协议解码、checksum）：堆外每次 get 都走 Unsafe，比数组访问慢一截；
* 缓冲区**生命周期极短**、分配频繁但小：堆内分配近乎免费，堆外 malloc 的成本摊不平；
* 程序本身不做多少 Channel IO：没有"省一次拷贝"的收益，纯付堆外分配的成本。

所以最优策略常见组合是：**Direct 做 IO 缓冲 + 拷贝到 Heap 做解析**。或者反过来用 Netty 的 CompositeByteBuf 零拷贝组合视图避免这次拷贝。

4 代码示例
------

4.1 基本分配与 IO
------

```java
// 堆内：分配便宜，适合短生命周期、马上要处理的数据
ByteBuffer heapBuf = ByteBuffer.allocate(64 * 1024);

// 堆外：分配贵，适合长期复用、IO 密集
ByteBuffer directBuf = ByteBuffer.allocateDirect(64 * 1024);

SocketChannel ch = ...;
ch.read(directBuf);            // 内核 -> 堆外，一步到位
directBuf.flip();
while (directBuf.hasRemaining()) {
    ch.write(directBuf);       // 堆外 -> 内核，一步到位
}

// 对比：如果用 heapBuf 参与 read/write，JDK 内部会先拷进临时 direct buffer
ch.read(heapBuf);              // 内核 -> 临时堆外 -> 堆内，多一次拷贝

// 逐字节解析场景（堆外偏慢的例子）
byte magic = directBuf.get();  // Unsafe 访问堆外 + 边界检查
byte[] arr = heapBuf.array();  // 堆内可直接拿数组，for 循环最快
```

4.2 复用堆外缓冲（把分配成本摊薄）
------

```java
// 大块堆外内存做成应用级单例复用，只分配一次
private static final ByteBuffer IO_BUF =
        ByteBuffer.allocateDirect(4 * 1024 * 1024);

synchronized void pump(InputStream in, OutputStream out) throws IOException {
    IO_BUF.clear();
    // ... 用 IO_BUF 做搬运 ...
}
```

4.3 Netty 的答案：池化 + 默认堆外
------

Netty 默认使用 `PooledByteBufAllocator` 且偏好 direct（非 Android 平台默认 preferDirect=true）：用 jemalloc 思想的 arena 池化把 malloc/free 成本摊薄，同时天然减少 GC。代价是**必须手动释放**：

```java
ByteBufAllocator alloc = PooledByteBufAllocator.DEFAULT;
ByteBuf buf = alloc.directBuffer(8 * 1024);   // 池化堆外
try {
    // 编解码、读写
} finally {
    buf.release();   // 忘记 release => 堆外泄漏, 池不会被归还
}
```

排查泄漏：`-Dio.netty.leakDetection.level=paranoid`（默认 simple，采样报告 `LEAK: ByteBuf.release() was not called`）。

5 堆外内存的坑与运维参数
------

* **OOM 形态不同**：`java.lang.OutOfMemoryError: Direct buffer memory`。堆内监控一切正常、进程 RSS 却持续上涨，是典型堆外泄漏特征；
* `-XX:MaxDirectMemorySize`：限制总量的软上限。JDK 在分配失败时会主动调 `System.gc()` 等 Cleaner 释放（前提是没有禁用显式 GC）。用了 `-XX:+DisableExplicitGC` 反而可能让堆外直接 OOM——经典线上坑，可改用 `-XX:+ExplicitGCInvokesConcurrent`。
* 泄漏来源：DirectByteBuffer 壳对象被长期强引用（如全局缓存）、Netty 忘 release、引用计数跨线程传递未 retain；
* 排查工具：Netty 泄漏检测、`jcmd <pid> VM.native_memory`（配合 `-XX:NativeMemoryTracking=summary`）、pmap 看进程 RSS；
* MappedByteBuffer（FileChannel.map，mmap）属于另一类堆外内存，不走 DirectMemory 计数，页面由操作系统管理，适合大文件只读/随机读。

6 决策表（背这张表去面试）
------

| 场景 | 选择 | 理由 |
|------|------|------|
| 网络服务/文件搬运，缓冲区跨多次 IO 复用 | Direct | 省掉每次 IO 的一次中转拷贝 |
| 高吞吐 + 低 GC 停顿要求（网关、存储） | Direct（池化） | 不占堆，减轻 GC |
| 一次性小数据、读进来立即用完丢弃 | Heap | 分配回收近乎免费 |
| 读到内存后高频 get/put 解析协议 | Heap（或 copy 到 heap 再解析） | 数组访问比 Unsafe 快 |
| 需要通过 array() 直接操作字节数组 | Heap | Direct 没有 backing array |
| 生命周期不确定/无法保证释放纪律 | Heap | 堆外泄漏比堆内泄漏更隐蔽 |
| 用 Netty | 默认 PooledDirect + release | 框架已经做了最优解 |

7 追问链（面试官会怎么往下问）
------

* **Q：为什么 read()/write() 不能直接用堆内数组？**
  A：系统调用需要稳定地址，而 GC 会移动堆对象，HotSpot 又不支持 pin 对象。直接传地址会在内核搬运期间被 GC 改坏。
* **Q：那我传 HeapByteBuffer 给 channel.read 时发生了什么？**
  A：JDK 内部拷贝到每线程缓存的临时 DirectByteBuffer 再做系统调用，多一次 CPU 拷贝。
* **Q：DirectByteBuffer 什么时候被释放？**
  A：壳对象被 GC 回收时，关联的 Cleaner（幻象引用机制）调用 Deallocator 去 free 堆外内存。所以它还"占着"直到壳对象被回收。Netty 池化场景是引用计数到 0 归还池。
* **Q：堆外内存会 OOM 吗？怎么发现？**
  A：会，`OutOfMemoryError: Direct buffer memory`；特征是堆很健康但进程 RSS 涨。用 Netty leak detection、NMT、pmap 排查。
* **Q：DirectBuffer 和零拷贝是一回事吗？**
  A：不是。DirectBuffer 省的是"JDK 内部那次中转拷贝"。零拷贝通常指 sendfile/transferTo 和 mmap（MappedByteBuffer），省的是内核态与用户态之间的拷贝，两者常配合使用但概念独立。
* **Q：既然堆外这么好，为什么不全部用堆外？**
  A：分配/释放贵、单字节访问慢、泄漏难查、没有 GC 兜底；只有 IO 密集且能池化复用时收益才为正。
* **Q：-XX:MaxDirectMemorySize 不设会怎样？**
  A：默认约等于最大堆大小；配合 `-XX:+DisableExplicitGC` 时分配失败前连 System.gc() 的自救机会都没有，容易莫名堆外 OOM。

8 小结
------

* 选择的标准只有一条：**看数据的一生在哪边**。只在 IO 通道里流，放堆外。要进 JVM 被反复解析，放堆内（或 IO 用堆外、解析前拷进堆）。
* 原理三句话：内核要稳定地址 → 堆内 buffer 参与 IO 会多一次隐藏拷贝。堆外换来吞吐与低 GC，代价是分配贵、访问慢、泄漏隐蔽。
* 生产实践：大块堆外缓冲做单例复用，或直接用 Netty 池化（记住 release）；
* 必会的配套参数与工具：MaxDirectMemorySize、DisableExplicitGC 的坑、Netty leakDetection、NMT。

> 2026-10 依据主题重写（原文件为空壳）。
