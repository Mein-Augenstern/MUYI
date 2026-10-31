Netty 源码分析三：内存分配与 ByteBuf
====

前两篇（[一：启动与EventLoop](Netty源码分析一-启动与EventLoop.md)、[二：Pipeline与Handler](Netty源码分析二-Pipeline与Handler.md)）解决了"事件谁来跑"，本篇解决"数据放在哪"：**ByteBuf 为什么比 java.nio.Buffer 好用、池化分配器如何用 arena/chunk/subpage 把分配做成 O(1)、零拷贝在 Netty 里到底指哪几件事**。这是 Netty 面试的深水区，也是"读过源码"和"背过八股"的分界线。速答版考点见 [Netty常见面试题](Netty常见面试题.md)。

1 ByteBuf：双指针干掉 flip
------

java.nio.Buffer 的 position/limit 要靠 flip() 在读写模式间切换，忘了 flip 是 NIO 新手头号 bug。Netty 的 AbstractByteBuf 直接用**两个独立的指针**：

```java
int readerIndex;   // 下一个读位置
int writerIndex;   // 下一个写位置
```

* 读不会动 writerIndex，写不会动 readerIndex，**读写可以交错进行，不需要模式切换**；
* `readableBytes() = writerIndex - readerIndex`；read 族（readByte 等）移动 readerIndex，write 族移动 writerIndex；get/set 族按下标操作、不动指针；
* `discardReadBytes()` 把已读部分丢弃、未读数据前移（有拷贝代价，慎用）；`clear()` 只把两个指针归零、不清数据；
* capacity/maxCapacity 分离：写满可自动扩容到 maxCapacity 上限。

2 ByteBuf 的分类矩阵
------

三个维度组合出常用类型：

| 维度 | 取值 | 说明 |
| ---- | ---- | ---- |
| 内存位置 | heap / direct | heap 有 byte[]，受 GC 管；direct 是堆外，hasArray()==false |
| 是否池化 | pooled / unpooled | 池化走 PooledByteBufAllocator 的 arena 体系 |
| 访问方式 | safe / unsafe | unsafe 走 PlatformDependent 直接按地址读写，更快 |

* 默认分配器是 **PooledByteBufAllocator.DEFAULT**，且倾向 direct（Unsafe 可用且未设 io.netty.noPreferDirect 时 preferDirect=true）；
* 为什么 IO 场景偏爱 direct：JDK 在 socket 写出时，堆内缓冲会先被**内部复制到一个临时直接内存**再进系统调用（JDK 层的隐式拷贝）；direct 免掉这一步，选型讨论见 [HeapByteBuffer与DirectByteBuffer的选择](../../../java/Java%20IO/HeapByteBuffer与DirectByteBuffer的选择.md)；
* Unpooled 工具类：wrappedBuffer 包装已有数组（不拷贝）、copiedBuffer 深拷贝、buffer(...) 直接分配非池化。

3 引用计数与泄漏检测
------

ByteBuf 实现 ReferenceCounted 接口：refCnt 初始 1，`retain()` 加一，`release()` 减一，**减到 0 时内存归还池（或释放堆外内存），之后再访问抛 IllegalReferenceCountException**。规则要记牢：

* 入站消息由**管道末端负责释放**：SimpleChannelInboundHandler 自动 release；裸 ChannelInboundHandlerAdapter 要自己在 channelRead 尾部 `ReferenceCountUtil.release(msg)`，或用 try-finally；
* 出站消息由 Netty 在写出后释放（write 成功后 refCnt 减一）；
* `retainedSlice()/retainedDuplicate()` 会 retain，切片用完同样要释放；
* **泄漏检测** ResourceLeakDetector 默认 SIMPLE（1% 采样），看到 `LEAK: ByteBuf.release() was not called` 就把 -Dio.netty.leakDetection.level 调到 ADVANCED/PARANOID 排查。

4 PooledByteBufAllocator：arena + 线程缓存
------

分配入口 newDirectBuffer → PooledByteBufAllocator：

* arena 数量默认 **2×CPU 核数**（heap/direct 各一组，受可用内存与系统属性 io.netty.allocator.numDirectArenas 等约束）；
* 每线程经 FastThreadLocal 拿到自己的 **PoolThreadCache**，分 small/normal 两组缓存队列：刚释放的缓冲先留在本线程缓存，**同线程再次分配直接命中，完全不加锁**；
* 缓存未命中才进 PoolArena。arena 内按申请尺寸分三档：**tiny**（<512B，16 字节对齐）、**small**（512B~8KB）、**normal**（8KB~16MB），分别对应 tinySubPagePools、smallSubPagePools 与一串按使用率组织的 PoolChunkList（q050/q025/q000/qInit/q075/q100）；**超过 chunkSize 的 huge 分配直接走非池化**；
* release() 时内存回到线程缓存/chunk，ByteBuf 对象本身也进 Recycler 对象池复用。

一把锁都不加的秘密就是"线程 → 缓存 → arena"三级漏斗：绝大多数分配止步于第一级。分配全流程一张图：

```text
ByteBufAllocator.newDirectBuffer(size)
  → PoolThreadCache 命中？（tiny/small/normal 各自的缓存队列）
      是 → 直接返回（无锁、最快）
      否 → PoolArena.allocate
            ├─ size < 512B     → tinySubPagePools 找现成 PoolSubpage
            ├─ size < 8KB      → smallSubPagePools 找现成 PoolSubpage
            ├─ size < 16MB     → qInit→q000→q025→q050→q075→q100 各 PoolChunkList
            │                    找不到空闲页就 new PoolChunk(16MB)
            └─ size > 16MB     → huge，直接 Unpooled 非池化
```

可调参数都在系统属性里：io.netty.allocator.pageSize（8192）、maxOrder（11）、smallCacheSize（256）、normalCacheSize（64）、numDirectArenas 等；泄漏检测是 io.netty.leakDetection.level。

5 PoolChunk：16MB 的伙伴算法
------

一个 PoolChunk 默认 16MB（**pageSize=8192、maxOrder=11，chunkSize = pageSize << maxOrder = 2^24**，可用 io.netty.allocator.pageSize/maxOrder 调整），heap chunk 用 byte[] 承载，direct chunk 用 ByteBuffer 承载。内部分配用**伙伴算法（buddy）**：

* 16MB 按 8KB 一页切成 2048 个 page，用一棵完全二叉树（memoryMap/depthMap 数组）管理：根节点代表整块，叶子代表单页；
* 申请 normal 尺寸时向上取整到页的倍数，在树上找"不小于需求的最小空闲块"，必要时把大块对半拆分，剩下的半个继续待命；
* 释放时递归检查伙伴：**伙伴也空闲就逐层合并回大块**，对抗碎片化。

6 PoolSubpage：小于一页的按槽切分
------

申请小于 pageSize 时，arena 先从对应 subpage 池里找现成的：

* 没有就先按上面伙伴算法要一整页，然后 **PoolSubpage 把这页按 elemSize 切成 N 个槽**（例如 36B 的请求切成 8192/48=170 个 48B 的槽），用 long[] bitmap 记录每个槽的占用；
* 切好的 subpage 挂进 arena 的 tiny/small 链表头，**后续同尺寸请求 O(1) 拿槽**，直到整页空闲才归还给 chunk；
* 这就是规格化的意义：任意怪尺寸都会对齐到固定档位，不同线程的碎请求最终汇聚到少量页上，**避免每小块内存都占一整页**。

7 Netty"零拷贝"到底指什么
------

面试答"零拷贝"不要只说 sendfile，Netty 语境下是五件事：

* **CompositeByteBuf**：把多个 ByteBuf 逻辑拼成一个视图，不做物理合并（典型：组协议头 + 包体）；
* **slice()/duplicate()/retainedSlice()**：与原 buf **共享底层内存**的视图，只复制指针不复制数据（注意：原 buf 释放后视图失效，需要 retained 版本）；
* **Unpooled.wrappedBuffer(byte[])**：包装而不拷贝（对照 copiedBuffer 是深拷贝）；
* **FileRegion/DefaultFileRegion**：文件发送封装 FileChannel.transferTo()，走操作系统 sendfile，数据不进用户态；
* **direct 内存**：省掉 JDK socket 写出时堆内 → 直接内存的那次隐式复制（严格说是"少一次拷贝"）。

池化本身减少的是分配/回收和 GC 成本，常被算作第六件。

顺带两个工程细节：堆外内存的释放有两条路——默认 JDK 的 ByteBuffer.allocateDirect 自带 Cleaner，交给 GC 兜底回收；显式设置 io.netty.maxDirectMemory 后 Netty 切到 noCleaner 分配，由引用计数精确释放（release 才真正归还内存，泄漏纪律因此更严格）；解码器 ByteToMessageDecoder 内部的累积缓冲（cumulation）就是池化的典型受益者——每次 TCP 分片到来都在同一个被复用的缓冲上追加，避免了反复分配。

面试追问链
------

* ByteBuf 和 ByteBuffer 区别？→ 双指针免 flip、capacity 可扩容、引用计数、支持池化；
* readIndex/writerIndex 越界会怎样？→ 读越过 writerIndex 抛 IndexOutOfBoundsException；
* 池化为什么快？→ 线程缓存无锁命中 + arena 伙伴算法 + 对象池复用，绕开 malloc 和 GC；
* 一个 chunk 多大、一页多大？→ 默认 16MB / 8KB，maxOrder=11；tiny 和 small 的分界？→ 512B；
* PoolThreadCache 怎么做到无锁？→ 每线程私有缓存，释放回本线程缓存，分配优先本线程取；
* 零拷贝列举？→ Composite/slice/wrapped/transferTo/direct 五件套；slice 和 copy 的区别？→ 共享内存的视图 vs 深拷贝；
* 什么时候必须 release？→ 入站消息处理完、retain 过的视图用完；谁负责？→ 末端 handler（或 SimpleChannelInboundHandler 自动）；
* 为什么要堆外内存？→ 省 JDK 写 socket 的内部拷贝 + 不给 GC 压力；代价是什么？→ 分配/释放贵（所以要池化）、排查泄漏难。

面试官视角小结
------

内存这层是 Netty 的护城河。及格线：说清 ByteBuf 双指针与引用计数，答出零拷贝至少三件。良好：能讲 PoolThreadCache→arena→chunk 的三级分配漏斗，知道 16MB/8KB/512B 这些默认值的来历（pageSize<<maxOrder）。资深信号：能展开 buddy 树的分裂合并、subpage 的 bitmap 槽位复用、huge 分配绕池的兜底逻辑，并把"池化 direct"的组合解释为"用分配复杂度换 GC 与拷贝成本"，顺带点出 LeakDetector 采样排查思路。结合前两篇的 EventLoop（[一](Netty源码分析一-启动与EventLoop.md)）与 Pipeline（[二](Netty源码分析二-Pipeline与Handler.md)），"线程模型 + 数据模型"合起来才是完整的 Netty。

> 来源：javadoop《认真的 Netty 源码解析（三）》（https://javadoop.com/post/netty-3）——原文页面至今为"还在组织内容......"的占位，系列（一）（二）规划了续篇但未成文；本文按该系列的研读脉络，就其下一篇的自然主题（内存分配与 ByteBuf）消化 Netty 4.1 源码后重写，原文版权归 javadoop，引用请遵原文。
