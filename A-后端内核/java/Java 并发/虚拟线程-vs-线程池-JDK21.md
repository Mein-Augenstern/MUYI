虚拟线程 vs 线程池（JDK21）
====

JDK21 正式转正虚拟线程（JEP 444），是 Java 十几年来最大的一次并发模型升级。一句话定位：**用「一任务一线程」的阻塞式写法，拿到接近异步框架的吞吐**。面试里它几乎必然和线程池绑定出现：虚拟线程会取代线程池吗？什么时候迁？坑在哪？本篇按「为什么 → 原理 → 对比 → 坑 → 迁移」展开，线程池的基础与源码先看 [深度解读 java 线程池设计思想及源码实现](./深度解读%20java%20线程池设计思想及源码实现.md)。

1 为什么需要虚拟线程：阻塞式写法与高并发的和解
------

传统 Web 服务是 thread-per-request 模型：一个请求占一个线程，代码里同步调 JDBC、HTTP、Redis，写法直观、栈trace清晰。矛盾出在平台线程的成本上：

* **1:1 映射内核线程**：每个约 1MB 栈预留 + 内核调度实体，创建/销毁走系统调用，切换要陷入内核；
* **数量上限低**：几千个就是常见极限，1 万个线程光栈预留就要 ~10GB，调度器先被拖垮；
* 于是只能把并发度压到池大小（比如 200）。**系统吞吐被线程数而非后端容量限制**——数据库明明能扛更多连接，线程先耗尽了。

另一条路是异步/响应式（Netty、WebFlux、CompletableFuture 组合）：吞吐确实高。但代价是回调地狱、栈断裂、调试困难、"彩色函数"传染（同步异步两套 API 无法混用）。

虚拟线程给出第三条路。**线程对象搬到堆里、成本降到微不足道，阻塞时把载体线程让出去——写法回到同步，吞吐逼近异步**。阻塞式开发体验 + 高并发吞吐，这对矛盾终于和解。

2 原理：载体线程、挂载点与 ForkJoinPool 调度器
------

虚拟线程是 **M:N 调度**：M 个虚拟线程跑在 N 个平台线程上。关键概念：

* **载体线程（carrier thread）**：真正执行虚拟线程代码的平台线程，来自一个专用的 **ForkJoinPool**（FIFO 模式，不是 commonPool）；
* **挂载（mount）**：虚拟线程的栈以 stack chunk 形式存在堆里，被调度执行时，栈帧被放到载体线程的栈上跑；
* **卸载（unmount）**：JDK 类库里的阻塞点（例如 socket 读写、sleep、`LockSupport.park`、`ReentrantLock`）已被改造。阻塞发生时，把栈帧整体拷回堆、虚拟线程挂起，**载体线程立即去跑别的虚拟线程**。事件就绪后虚拟线程重新入队，可能挂到任意载体上。

```
   平台线程(载体)                        虚拟线程（住在堆里，极其廉价）
┌──────────────┐    mount      ┌────────────────┐
│ carrier #1   │◄──────────────│  VT-7 栈chunk   │
│  正在执行 VT-7│               └────────────────┘
│              │   阻塞于 socket.read()
│              │─────────────►  栈帧拷回堆（unmount），VT-7 等待 IO 事件
│  立刻 mount   │    mount      ┌────────────────┐
│  VT-9        │◄──────────────│  VT-9 栈chunk   │
└──────────────┘               └────────────────┘
   IO 完成 → VT-7 重新入队 → 可能挂到 carrier #3 继续跑
```

调度器参数（默认值即可用）：并行度 = CPU 核数（`-Djdk.virtualThreadScheduler.parallelism`），阻塞补偿下最多临时扩到 256 个载体。**注意：同时真正运行的虚拟线程数仍受核数限制**——这也是 CPU 密集任务用虚拟线程无增益的根源。

顺带回答一个常见疑问：百万个虚拟线程不会拖垮调度器。原因：**阻塞中的虚拟线程只是堆上的普通对象**（栈 chunk + 线程对象），根本不在调度器视野里。调度器任一时刻只需要管理"可运行"的那一小撮。API 一览：

```java
// 创建方式一：直接构建（虚拟线程默认是 daemon，优先级固定不可改）
Thread vt = Thread.ofVirtual().name("worker-", 0).start(() -> handle(req));
// 创建方式二：每任务一个虚拟线程的 Executor（迁移期的主角）
try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
    exec.submit(() -> callRemoteApi());
    exec.submit(() -> callDb());
}   // close() 隐式等待所有任务结束，天然配合 try-with-resources
```

3 与线程池的本质区别：占用 vs 复用的成本模型
------

线程池的设计前提是「线程昂贵，必须复用摊销成本」（见 [深度解读 java 线程池设计思想及源码实现](./深度解读%20java%20线程池设计思想及源码实现.md)）；虚拟线程把线程做便宜到「按需创建、用完即弃」。成本模型对比：

| 维度 | 平台线程 + 线程池 | 虚拟线程 |
| ---- | ---- | ---- |
| 线程与内核映射 | 1:1，内核调度 | M:N，JVM 用户态调度 |
| 栈 | ~1MB 预留（内核栈 + 线程栈） | 堆上的 stack chunk，按需伸缩，初始几百字节~KB |
| 创建成本 | 系统调用，毫秒级 | 堆对象分配，微秒级 |
| 可行数量级 | 数千 | 数百万 |
| 并发度来源 | 池大小（复用摊销） | 每任务一个线程（占用足够便宜） |
| 排队模型 | 任务在池的 BlockingQueue 排队 | 线程即任务，没有池队列 |
| 阻塞的代价 | 占住一个池线程 → 池耗尽 → 雪崩 | 卸载载体线程，近乎免费 |
| 限流手段 | 池大小 + 队列容量 | Semaphore、下游连接池本身 |

一句话：**线程池是「复用昂贵资源」，虚拟线程是「占用廉价资源」**。所以官方明确说不要池化虚拟线程。池化恰恰把虚拟线程最重要的「每任务一线程、随用随弃」语义丢掉了，还把 ThreadLocal 脏数据问题请了回来。

4 两大坑：pinning 与 ThreadLocal 内存放大
------

**pinning（钉住）**：某些情况下虚拟线程阻塞时无法卸载，载体线程被一起拖着阻塞：

* JDK21 现状：**持有 monitor（synchronized 块/方法内，包括 Object.wait()）期间发生阻塞**，或执行 **native/JNI 帧**时，虚拟线程钉住载体线程。注意 synchronized 本身不 pin，钉住发生在"持锁期间又阻塞"；
* 危害：少量 pin 无碍；热点路径 pin 会让并发度塌回载体线程数，等于白升 JDK21；
* 排查：`-Djdk.tracePinnedThreads=full`（打印钉住的栈），或 `jcmd <pid> Thread.dump_to_file -format=json`（JDK21 的 JSON 线程 dump 会标出 pinned）；JFR 有 `jdk.VirtualThreadPinned` 事件；
* 缓解（JDK21 上）：热点路径把 synchronized 换成 **ReentrantLock**（见 [AQS&ReentrantLock](./AQS%26ReentrantLock.md)），其 park 已支持卸载；
* **JDK24（JEP 491）重构了 monitor 实现，synchronized 不再引起 pinning**，只剩 native 帧一种钉住场景——升级 24 后此坑基本消除。

```java
// JDK21：持锁期间发生阻塞 → 载体线程被钉住，一起干等
synchronized (lock) {
    blockingIo();            // 无法 unmount，占用一个载体
}
// 换 ReentrantLock：park 走 LockSupport，可正常卸载载体
rl.lock();
try { blockingIo(); } finally { rl.unlock(); }
```

**ThreadLocal 内存放大**：ThreadLocal 本身与虚拟线程兼容（原理见 [ThreadLocal](./ThreadLocal.md)），问题在量变引起质变：

* 百万级虚拟线程 × 每线程一份 ThreadLocalMap（value 强引用），内存被显著放大。线程不再复用，"下一个任务覆盖旧值"的隐式清理假设失效，泄漏更隐蔽；
* 池化时代"每线程缓存一个重对象（buffer、SimpleDateFormat）"的模式在虚拟线程下变成"每任务缓存一份"，纯属浪费；
* 解法：大 value 改显式传参。或用 **ScopedValue**（JDK21 预览 JEP 446、25 转正）——不可变、作用域有界、方法返回即失效，从设计上避免放大。

5 适用场景与迁移策略
------

适用与不适用：

* **IO 密集 ✓**：Web/BFF、网关、爬虫、RPC fan-out——绝大多数服务端负载；
* **高并发短任务 ✓**：百万连接、消息风暴，线程数不再是瓶颈；
* **CPU 密集 ✗**：并行度仍 = 核数，虚拟线程无增益还多一层调度开销。老实用平台线程池（见 [几种常见的线程池](./几种常见的线程池.md)）；
* **依赖重度 synchronized 包 IO 的老库 ✗（暂缓）**：先修 pinning 再迁；
* **需要隔离/配额的后台批任务 ✗**：平台线程池 + 有界队列天然提供隔离与背压，虚拟线程没有池边界。

从现有线程池迁移的推荐路径：

1. 升 JDK21，先在预发灰度，用 JSON 线程 dump/JFR 摸清 pinning 与 ThreadLocal 现状；
2. 入口层换 `Executors.newVirtualThreadPerTaskExecutor()`（Spring Boot 3.2 一键 `spring.threads.virtual.enabled=true`，Tomcat NIO 早已就绪）；
3. 并发度控制从"池大小"改为 **Semaphore**。**物理资源池保留**——数据库连接池、HTTP 连接池限的是连接不是线程。必须继续池化；
4. 热点 synchronized → ReentrantLock；ThreadLocal 重对象 → 显式传参/ScopedValue；
5. 压测对比吞吐与 P99，观察载体线程 CPU 与 GC（百万线程对象也是堆压力）。

```java
// 迁移前
ExecutorService pool = Executors.newFixedThreadPool(200);
// 迁移后：每任务一个虚拟线程，用 Semaphore 限并发（保护下游）
Semaphore permits = new Semaphore(200);
ExecutorService vpool = Executors.newVirtualThreadPerTaskExecutor();
vpool.submit(() -> {
    permits.acquire();
    try { callRemoteApi(); } finally { permits.release(); }
});
```

迁移点速查表：

| 迁移点 | 线程池时代 | 虚拟线程时代 |
| ---- | ---- | ---- |
| 并发执行器 | `newFixedThreadPool(200)` | `newVirtualThreadPerTaskExecutor()` |
| 并发上限 | 池大小 | Semaphore / 下游连接池 maxPoolSize |
| IO 互斥 | synchronized | ReentrantLock（JDK21；24 后无差） |
| 上下文透传 | ThreadLocal | 显式传参 / ScopedValue |
| 排队与背压 | 有界队列 + 拒绝策略 | 没有池队列，背压上移到入口（网关/MQ） |
| CPU 密集批任务 | 平台线程池 | 维持平台线程池不动 |

面试追问链
------

* 虚拟线程和平台线程区别？→ M:N 调度、栈在堆按需伸缩、JVM 用户态调度；
* 调度器是什么？并行度多少？→ 专用 ForkJoinPool（FIFO），默认核数，阻塞补偿最多 256；那 CPU 密集任务用虚拟线程有意义吗？→ 没有，同时运行的仍只有核数个；
* 阻塞时发生了什么？→ unmount 栈帧拷回堆；什么时候卸载不了？→ pinning；
* synchronized 为什么会 pin？JDK21 怎么办？→ monitor 实现未改造，热点换 ReentrantLock；JDK24 JEP 491 已修复；
* 为什么不池化虚拟线程？→ 池化的前提是资源昂贵；池化丢掉每任务一线程序语义，还召回 ThreadLocal 脏数据；
* 虚拟线程会取代线程池吗？→ 取代"IO 并发限流"这一用途；CPU 密集、隔离批任务、连接池这类物理资源池不会；
* ThreadLocal 在虚拟线程下的风险？→ 内存放大 + 复用假设失效，换 ScopedValue 或显式传参。

面试官视角小结
------

这道题分层很明显：知道 API 和"更轻量"的说法只是及格。能讲清 mount/unmount、ForkJoinPool 调度和"并行度仍受核数限制"，说明读过原理。能主动说出 **pinning 的成因与 JDK21→24 的演进**、**ThreadLocal 内存放大**。以及"连接池是物理资源池不能跟着去掉"的迁移边界。这才是有生产经验的信号。反过来，能论证"我们为什么暂时没迁"（JDK 基线、老库 synchronized、观测改造成本）同样加分。更多线程池侧考点见 [线程池面试问题总结](./线程池面试问题总结.md)。

> 2026-10 新增（P1 时效升级）。
