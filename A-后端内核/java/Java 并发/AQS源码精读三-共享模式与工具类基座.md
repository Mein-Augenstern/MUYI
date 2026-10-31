AQS 源码精读（三）：共享模式与工具类基座
====

AQS 系列收尾篇。前两篇啃完独占模式（[一：加锁与队列](./AQS源码精读一-加锁与队列.md)、[二：独占锁释放与条件队列](./AQS源码精读二-独占锁释放与条件队列.md)），本篇讲**共享模式**——一句话定位：**state 的语义完全由子类定义，tryAcquireShared 返回剩余量而不是 boolean，唤醒会沿队列链式传播**。CountDownLatch、Semaphore、CyclicBarrier 这三个面试常客全部在这一篇落地：前两个是 AQS 共享模式的直接应用，CyclicBarrier 反而另起炉灶用 ReentrantLock + Condition。基础概念可先看 [AQS&ReentrantLock](./AQS%26ReentrantLock.md)、[CountDownLatch](./CountDownLatch.md)、[Semaphore(信号量)](./Semaphore%28信号量%29.md)。

1 共享模式与独占模式的差别
------

AQS 的设计是模板方法：子类只需实现 `tryAcquire/tryRelease`（独占）或 `tryAcquireShared/tryReleaseShared`（共享），排队、挂起、唤醒全部复用。共享模式的契约不同：

* **tryAcquireShared 返回 int**：负数失败入队；0 成功但无剩余；正数成功且有剩余，剩余量会驱动唤醒传播；
* 多个线程可以同时持锁，head 的后继被唤醒后如果还有剩余，会继续唤醒自己的后继——这就是"传播"。

2 CountDownLatch：state 是一个倒数计数器
------

用法心智模型：m 个线程做任务、n 个线程在栅栏上等，m 个任务全部完成（每个 countDown 一次）后 n 个等待线程同时通过。经典的双 latch 写法：`startSignal = new CountDownLatch(1)` 让所有线程"站上起跑线"，`doneSignal = new CountDownLatch(N)` 等全部完工。

内部实现只有一层皮：

```java
private static final class Sync extends AbstractQueuedSynchronizer {
    CountDownLatch(int count) {
        setState(count);     // state == count
    }
    protected int tryAcquireShared(int acquires) {
        return (getState() == 0) ? 1 : -1;   // 减到 0 才放行
    }
    protected boolean tryReleaseShared(int releases) {
        for (;;) {
            int c = getState();
            if (c == 0)
                return false;
            int nextc = c - 1;
            if (compareAndSetState(c, nextc))
                return nextc == 0;            // 只有减到 0 的那次返回 true
        }
    }
}
```

await 的路径：`await() → sync.acquireSharedInterruptibly(1)`——先查中断，再 tryAcquireShared，state 非 0 返回 -1，进 `doAcquireSharedInterruptibly`：

```java
private void doAcquireSharedInterruptibly(int arg) throws InterruptedException {
    final Node node = addWaiter(Node.SHARED);
    boolean failed = true;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                int r = tryAcquireShared(arg);
                if (r >= 0) {
                    setHeadAndPropagate(node, r);
                    p.next = null; // help GC
                    return;
                }
            }
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                throw new InterruptedException();   // 共享版：可中断
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}
```

结构与独占的 acquireQueued 同构：addWaiter 换成 `Node.SHARED`，成功后多一步 setHeadAndPropagate，中断直接抛异常。countDown 的路径：`countDown() → sync.releaseShared(1)`，tryReleaseShared 自旋 CAS 减 1，**只有减到 0 的那个线程拿到 true**，由它调 doReleaseShared 负责唤醒。套路一句话：所有 await 线程挂起，别人递减 state，减到 0 的线程负责唤醒全部。

3 doReleaseShared 与 setHeadAndPropagate：唤醒的接力
------

共享模式最精妙的一段：

```java
private void doReleaseShared() {
    for (;;) {
        Node h = head;
        if (h != null && h != tail) {
            int ws = h.waitStatus;
            if (ws == Node.SIGNAL) {
                if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                    continue;            // CAS 失败：有新节点入队改了状态，重来
                unparkSuccessor(h);      // 唤醒 head 的后继
            }
            else if (ws == 0 &&
                     !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                continue;                // 失败说明恰好有节点入队，重来
        }
        if (h == head)                   // head 没变，收工；变了，继续下一轮
            break;
    }
}
```

三层意思：

* `h == null || h == tail` 直接跳过：空队列或没有后继可唤醒；
* head 状态是 SIGNAL 就 CAS 清 0 并 unparkSuccessor。这个 CAS 可能失败——本线程下一轮循环会和"刚被唤醒、即将成为新 head"的线程在同一个 head 上竞争，只会有一个成功，失败方 continue 重试；
* 结尾 `if (h == head) break;`：循环期间 head 被换掉了（被唤醒的线程 setHead 占了位），就再循环一轮唤醒下一个。

被唤醒的 t3 回到自旋：p == head 且 tryAcquireShared 返回 1，进入 `setHeadAndPropagate(node, r)`：先 setHead 占位，再检查（propagate > 0，或老/新 head 的 waitStatus < 0）后，若后继是 shared 节点就**再调 doReleaseShared** 唤醒 t4——t4 醒来又如法炮制唤醒 t5。唤醒像接力棒沿队列传播，每条线程自己负责叫醒下家；doReleaseShared 里的循环是"出于吞吐量的考虑"再加一棒。PROPAGATE(-3) 这个状态就是为传播语义兜底而设。

4 CyclicBarrier：不走共享模式的那一个
------

CyclicBarrier 字面是"可循环使用的栅栏"，实现却与 CountDownLatch 大相径庭：**ReentrantLock + Condition（trip）**，一点都不碰共享模式。核心字段：`parties`（参与数）、`count`（未到栅栏者，递减）、`barrierCommand`（过栅栏前执行的动作）、`generation`（当前代）。

内部类 Generation 只有一个 `boolean broken`，"代"就是一次栅栏周期。两个关键方法：

* `nextGeneration()`：最后一个线程到达时调用——`trip.signalAll()` 唤醒所有等待者、`count = parties`、换新 Generation，类似重新实例化一个 CyclicBarrier；
* `breakBarrier()`：`generation.broken = true`、重置 count、`trip.signalAll()`。

核心都在 `dowait(timed, nanos)`：

```java
// 加 lock；broken 则抛 BrokenBarrierException；进方法时已中断则先 breakBarrier 再抛
int index = --count;
if (index == 0) {               // 最后一个到的
    // 先跑 barrierCommand，成功则 nextGeneration() 返回 0；
    // command 抛异常则 finally 中 breakBarrier()
} else {
    for (;;) {
        trip.await();           // 或带超时的 awaitNanos(nanos)
        // 醒来后：g.broken → 抛 BrokenBarrierException
        //         g != generation → return index（正常开启新代）
        //         超时 → breakBarrier + 抛 TimeoutException
        //         等待中被中断且 g == generation && !g.broken → breakBarrier + 抛 InterruptedException
    }
}
// finally 释放锁
```

**栅栏被打破只有三种情况**：等待线程中断、等待超时、barrierCommand 抛异常——任何一种都会叫醒整代线程并抛 BrokenBarrierException。`reset()` = breakBarrier + nextGeneration：parties=4 已到 3 个时 reset，这 3 个线程全部以 BrokenBarrierException 醒来。与 CountDownLatch 对比：latch 的计数归零即报废、countDown 不阻塞；barrier 人到齐才开门、await 的都是参与者、可循环。细节另见 [CyclicBarrier(循环栅栏)](./CyclicBarrier%28循环栅栏%29.md) 与 [CyclicBarrier 和 CountDownLatch 的区别](./CyclicBarrier%20和%20CountDownLatch%20的区别.md)。

5 Semaphore：state 是资源池余量
------

Semaphore 类比资源池：acquire 取资源（state 递减），release 归还（state 递增），state==0 时 acquire 需等待。构造可选公平/非公平，tryAcquireShared 的区别"往往就那么一两行"：

```java
// NonfairSync.tryAcquireShared：直接自旋 CAS 减
for (;;) {
    int available = getState();
    int remaining = available - acquires;
    if (remaining < 0 ||
        compareAndSetState(available, remaining))
        return remaining;
}
// FairSync.tryAcquireShared：仅多一个排队检查
if (hasQueuedPredecessors())
    return -1;
```

acquire 走 `doAcquireShared`（无 Interruptibly 后缀的版本）：结构与 doAcquireSharedInterruptibly 相同，但被中断不抛异常，记标志、成功后 `selfInterrupt()` 补回——对应 `acquire()` / `acquireUninterruptibly()` 两族 API，还有一次取多个 permits 的 `acquire(int permits)` 重载。release 的 tryReleaseShared 自旋 CAS 加 1（带溢出检查），恒返回 true，随后 doReleaseShared 唤醒等待者。

6 用 AQS 造自己的同步器
------

三个工具读完，模板已经浮现。自定义同步器只需继承 AQS 并实现四选二/四的钩子：独占用 `tryAcquire` / `tryRelease`，共享用 `tryAcquireShared` / `tryReleaseShared`，条件队列再加 `isHeldExclusively()`。state 的语义自己定：ReentrantLock 是重入次数、CountDownLatch 是倒计数、Semaphore 是许可数——**AQS 从不解释 state，它只保证 state 的 CAS 原子性和队列调度**。Doug Lea 用这一套骨架撑起了半个 JUC，对 Java 并发而言确实是神一样的存在。想看共享模式更近的应用，读写锁的读锁就是 acquireShared 的完整实战，见 [ReentrantReadWriteLock源码分析](./ReentrantReadWriteLock源码分析.md)；线程池里 Worker 对独占模式的借用见 [深度解读 java 线程池设计思想及源码实现](./深度解读%20java%20线程池设计思想及源码实现.md)。进阶替代品见 [Phaser使用指南](./Phaser使用指南.md)。

面试追问链
------

* 共享模式和独占模式源码上的区别？→ tryAcquireShared 返回 int 剩余量、成功后 setHeadAndPropagate 链式传播唤醒、Node.SHARED 标记；
* CountDownLatch 的 state 语义？→ 构造时 setState(count)，tryAcquireShared 只认 state==0，countDown 自旋 CAS 减 1；
* 谁负责唤醒等待线程？→ 把 state 减到 0 的那个线程，调 doReleaseShared；
* doReleaseShared 为什么要 for 循环、`h == head` 才 break？→ 唤醒期间 head 可能被新线程顶掉，变了就再唤醒下一个，吞吐考虑；
* PROPAGATE 状态干嘛的？→ 共享唤醒传播的兜底标记，防止唤醒信号丢失；
* CyclicBarrier 基于 AQS 吗？→ 不直接基于，是 ReentrantLock + Condition(trip) + Generation；
* 栅栏什么时候 broken？→ 等待中断 / 超时 / barrierCommand 抛异常，三种；
* CyclicBarrier 和 CountDownLatch 区别？→ 参与者即等待者 vs 第三方放行；可重用 vs 一次性；人到齐开门 vs 计数归零；
* Semaphore 公平/非公平差在哪？→ 公平版 tryAcquireShared 先 hasQueuedPredecessors；
* 怎么基于 AQS 写同步器？→ 覆写 tryAcquire/tryRelease（或共享版）定义 state 语义，排队挂起唤醒全复用。

面试官视角小结
------

CountDownLatch/Semaphore 的实现是"小而准"的考题：state 语义 + 一两个方法的覆写，十分钟就能讲透，最适合验证候选人是背名词还是懂机制。追问 setHeadAndPropagate 的传播、doReleaseShared 的循环条件，能把真读过源码的人筛出来。CyclicBarrier 是很好的反向题——"它为什么不用共享模式"能引出 ReentrantLock + Condition 的组合思路。最后落到"如何自己造一个同步器"，就是 AQS 三篇的终点：state 定义权交给子类，调度机制收归框架。

> 来源：javadoop《一行一行源码分析清楚 AbstractQueuedSynchronizer (三)》（https://javadoop.com/post/AbstractQueuedSynchronizer-3）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
