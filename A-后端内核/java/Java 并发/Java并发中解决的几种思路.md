Java并发中解决的几种思路
====

并发问题的根源是：**多个线程同时读写共享的可变状态**。落到 Java 内存模型上就是三性问题：

* **可见性**：一个线程的修改，另一个线程看不到（CPU 缓存/寄存器暂存）；
* **原子性**：read-modify-write 不是一步，中途会被打断；
* **有序性**：编译器和处理器会指令重排，单线程语义不变，多线程会互相破坏假设。

三性与手段的对应关系：

| 三性 | 主要手段 |
| ---- | ---- |
| 可见性 | volatile、final（安全发布）、synchronized（解锁前刷回主存） |
| 原子性 | CAS、synchronized/Lock 互斥 |
| 有序性 | volatile（内存屏障）、happens-before 规则 |

所有解决方案本质上都在围绕"不共享、不可变、少竞争、减小冲突面"做文章。面试中把思路串成主线再落到具体组件，比零散背组件加分得多。

1 空间换时间：不共享就没有竞争
------

核心是让每个线程操作自己的副本，天然线程安全：

* **ThreadLocal**：为每个线程维护一份变量副本，线程内读写互不干扰。典型场景：SimpleDateFormat 复用、用户上下文透传、数据库连接绑定（细节见 [ThreadLocal](./ThreadLocal.md)）；
* 代价是占用更多内存，且用完必须 `remove()`，否则线程池场景下线程复用会出现脏数据，并造成 Entry 泄漏（见 ThreadLocal 源码篇）。

2 加锁：串行化临界区
------

**乐观锁**：假设冲突很少发生，不加锁直接操作，冲突了再重试：

* 借助类似 **CAS**（Compare And Swap）的操作实现（细节见 [CAS](./CAS.md)）；
* 典型应用：Atomic 原子类、并发容器的无锁读、AQS 的状态获取；
* 注意 ABA 问题（AtomicStampedReference 解决）与高冲突下的自旋开销。

**悲观锁**：假设冲突经常发生，先加锁再操作：

* **synchronized**：借助 Monitor（锁监视器）实现，本质依赖操作系统的锁机制；JDK1.6 后有偏向锁/轻量级锁等优化（细节见 [synchronized](./synchronized.md)）；
* **Lock**：基于 AQS 实现，支持可中断、超时获取、公平锁、多条件变量（细节见 [AQS&ReentrantLock](./AQS%26ReentrantLock.md)）；
* 两者对比与选型见 [乐观锁与悲观锁](./乐观锁与悲观锁.md)。

3 不可变：状态改不了就绝对安全
------

* final 修饰的字段/引用、String、`List.of` 等不可变集合；
* **CopyOnWriteArrayList/Set**：写时复制一份新数组，读完全无锁；适合读多写极少的小集合（见库内 CopyOnWriteArrayList 源码篇）；
* volatile 解决可见性与有序性（happens-before），但不解决原子性，常与 CAS 配合（见 [volatile](./volatile.md)）。

4 减小锁粒度：把一把锁拆成多把
------

竞争激烈时不是加更粗的锁，反而是把锁拆细：

* **ConcurrentHashMap（JDK1.7 锁分段）**：Segment 数组，每段一把 ReentrantLock，不同段的读写互不阻塞；JDK1.8 进一步细化到桶头节点 CAS + synchronized；
* **LongAdder**：把一个计数热点拆成 base + Cell 数组，更新时只命中其中一个 cell，sum 时再汇总，用空间换吞吐；
* synchronized 的锁升级（偏向→轻量级→重量级）也是同一思想：按竞争激烈程度选择更便宜的手段。

5 思路对比与选型
------

| 思路 | 代表 | 适用场景 | 代价 |
| ---- | ---- | ---- | ---- |
| 空间换时间 | ThreadLocal | 线程内上下文、状态隔离 | 内存、泄漏风险 |
| 乐观锁 | CAS、Atomic | 读多写少、冲突低 | 自旋、ABA |
| 悲观锁 | synchronized、Lock | 写多、临界区较重 | 阻塞、上下文切换 |
| 不可变 | final、COW 容器 | 配置类数据、读多写极少 | 写放大/复制成本 |
| 减小粒度 | 分段锁、LongAdder | 热点计数、大 Map | 结构更复杂 |

一句话：**能不共享就不共享（ThreadLocal/不可变），必须共享先试乐观（CAS），冲突严重再上悲观锁（synchronized/Lock），锁太热就拆细（分段/分散热点）**。各并发容器的落地对比见 [并发容器总结](./并发容器总结.md)。

面试常见追问
------

* ThreadLocal 为什么会内存泄漏？——Entry 的 key 是弱引用会被 GC，value 强引用链仍在；线程池里线程长生命周期，用完必须 remove()；
* CAS 的 ABA 怎么解决？——加版本号，用 AtomicStampedReference / AtomicMarkableReference；
* synchronized 和 Lock 怎么选？——竞争不激烈、无需高级特性时用 synchronized（1.6 后有锁升级优化）；需要可中断、超时获取、公平锁、多条件变量时用 Lock；
* ConcurrentHashMap 锁的演化？——JDK1.7 分段锁（Segment 继承 ReentrantLock）→ JDK1.8 桶级 CAS + synchronized；
* 为什么 LongAdder 比 AtomicLong 快？——把一个热点变量拆成 base + Cell[]，分散竞争，sum 时汇总。

> 原链接已失效，2026-10 依据主题重写。
