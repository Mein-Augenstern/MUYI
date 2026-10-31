AQS 源码精读（一）：加锁与队列
====

AbstractQueuedSynchronizer 是整个 java.util.concurrent 的地基：ReentrantLock、CountDownLatch、Semaphore、ReentrantReadWriteLock、线程池里的 Worker 全都架在它上面。一句话定位：**用一个 volatile int state 加一条 FIFO 双向队列，把"抢资源失败的线程如何挂起、唤醒后如何接着抢"这一套脏活全部收口**。面试里"讲讲 AQS 原理"是并发问题的必考开场，能精确讲到 `acquireQueued(addWaiter(Node.EXCLUSIVE), arg)` 这种调用链和 waitStatus 的真实语义，才算读过源码。本篇以 ReentrantLock 公平锁为主线读加锁路径，解锁与条件队列见 [AQS 源码精读（二）：独占锁释放与条件队列](./AQS源码精读二-独占锁释放与条件队列.md)，共享模式与 CountDownLatch/Semaphore 见 [AQS 源码精读（三）：共享模式与工具类基座](./AQS源码精读三-共享模式与工具类基座.md)。并发全景可回看 [AQS&ReentrantLock](./AQS%26ReentrantLock.md) 与 [深度解读 java 线程池设计思想及源码实现](./深度解读%20java%20线程池设计思想及源码实现.md)。

1 AQS 的骨架：一个 state，一条队列
------

AQS 本身不实现任何一种具体的锁，它只提供机制。类的核心属性：`head`（队列头节点，可理解为"当前持锁线程"的位置）、`tail`（尾节点）、`state`（0 表示无人占用，大于 0 表示被持有，可重入时累加）、`exclusiveOwnerThread`（继承自 AbstractOwnableSynchronizer，重入判断靠它）。

**阻塞队列不包含 head 节点**——head 是占位空节点，真正等待的是它后面的节点。每个等待线程被包装成内部类 Node：

```java
static final class Node {
    // 标识节点当前在共享模式下
    static final Node SHARED = new Node();
    // 标识节点当前在独占模式下
    static final Node EXCLUSIVE = null;

    static final int CANCELLED =  1;
    static final int SIGNAL    = -1;
    static final int CONDITION = -2;
    static final int PROPAGATE = -3;

    volatile int waitStatus;
    // 前驱节点的引用
    volatile Node prev;
    // 后继节点的引用
    volatile Node next;
    // 这个就是线程本尊
    volatile Thread thread;
}
```

waitStatus 常态只有两种：大于 0（即 CANCELLED=1）表示该线程已取消等待；SIGNAL(-1) 表示**当前节点的后继节点**需要被唤醒——注意方向，waitStatus 描述的是后继节点的状态，不是自己的。CONDITION 标记节点在条件队列（第二篇），PROPAGATE 用于共享模式唤醒传播（第三篇）。"waitStatus 说的是后继"这条线索能把取消排队、唤醒操作全部串起来。使用侧就是标准的 `lock(); try { ... } finally { unlock(); }` 套路；ReentrantLock 内部是 `abstract static class Sync extends AbstractQueuedSynchronizer` 派生 FairSync / NonfairSync 的三层结构，`new ReentrantLock(true)` 选公平锁，lock/unlock 全部委托给 sync 实例——ReentrantLock 自己只是壳。

2 从 lock() 到 acquire(1)：模板方法的开场
------

公平锁的入口只有一行：`final void lock() { acquire(1); }`。acquire 是 AQS 的模板方法，四行代码定下整个加锁流程：

```java
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}
```

读法：先 `tryAcquire` 试一把——锁本就空闲则直接成功，队列都不用建；失败则 `addWaiter(Node.EXCLUSIVE)` 把当前线程包装成独占节点挂到队尾，`acquireQueued(...)` 在队列里自旋/挂起直到拿到锁；返回值是"期间是否被中断过"，是则 `selfInterrupt()` 补设中断标志（lock() 不响应中断的真相，第二篇展开）。

3 tryAcquire：公平锁的先来后到
------

```java
protected final boolean tryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    if (c == 0) {
        if (!hasQueuedPredecessors() &&
            compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    }
    else if (current == getExclusiveOwnerThread()) {
        int nextc = c + acquires;
        if (nextc < 0)
            throw new Error("Maximum lock count exceeded");
        setState(nextc);
        return true;
    }
    return false;
}
```

三个分支：**state==0（空闲）**时公平锁必须先过 `hasQueuedPredecessors()`——队列里已有人排队就不许插队，这是公平语义的全部落点，CAS 改 state 成功后设置 exclusiveOwnerThread；**重入**时（`current == getExclusiveOwnerThread()`）state 直接加、不走 CAS——锁已是自己的，不存在竞争；其余返回 false 交给外层入队。非公平锁的差异就藏在"state==0 时直接 CAS、不问队列"，第二篇对比。

4 addWaiter 与 enq：入队与 head 的延迟初始化
------

入队先走快速路径：

```java
private Node addWaiter(Node mode) {
    Node node = new Node(Thread.currentThread(), mode);
    Node pred = tail;
    if (pred != null) {
        node.prev = pred;
        if (compareAndSetTail(pred, node)) {
            pred.next = node;
            return node;
        }
    }
    enq(node);
    return node;
}
```

tail 已存在就 CAS 把自己接到队尾：先设 node.prev，CAS tail 成功后回填 pred.next（回填失败不影响正确性，唤醒时从 tail 反向遍历能兜底）。CAS 失败或队列为空则进 enq 自旋：

```java
private Node enq(final Node node) {
    for (;;) {
        Node t = tail;
        if (t == null) { // Must initialize
            if (compareAndSetHead(new Node()))
                tail = head;
        } else {
            node.prev = t;
            if (compareAndSetTail(t, node)) {
                t.next = node;
                return t;
            }
        }
    }
}
```

两个常被问的细节：**head 是延迟初始化的**，第一个竞争者出现才 `new Node()` 造出 head，且这个节点不绑定任何线程；第一轮 if 分支设置 `tail = head` 之后**故意不 return**，继续下一轮循环走 else 分支把真正带线程的节点挂上去。

5 acquireQueued：自旋、挂起与被唤醒后再抢
------

```java
final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return interrupted;
            }
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                interrupted = true;
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}
```

队列里的线程被唤醒后从 park 处继续，跑的就是这个循环。关键判断 `p == head && tryAcquire(arg)`：只有**前驱是 head**（队首）才有资格抢锁——既保住 FIFO，也解释了 head 为什么必须是空占位节点（它可能刚被初始化、不属于任何线程）。抢成功则 `setHead(node)` 把自己升为新 head，老 head 出队 `p.next = null // help GC`。failed 只在 tryAcquire 抛异常时保持 true，走 `cancelAcquire(node)` 清理（第二篇）。抢不到就问一句"能睡了吗"：`shouldParkAfterFailedAcquire(p, node)` 返回 true 才真正挂起。

6 shouldParkAfterFailedAcquire：waitStatus 说的从来不是自己
------

```java
private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
    int ws = pred.waitStatus;
    if (ws == Node.SIGNAL)
        return true;
    if (ws > 0) {
        do {
            node.prev = pred = pred.prev;
        } while (pred.waitStatus > 0);
        pred.next = node;
    } else {
        compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
    }
    return false;
}
```

按前驱状态分三种处理：**SIGNAL(-1)**——前驱释放时会负责唤醒我，放心睡，返回 true；**已取消（>0）**——唤醒依赖前驱，前驱弃权了就不行，向前遍历"找个好爹"，把中间的取消节点全部跨过去；**0（新节点默认值）**——CAS 把前驱改成 SIGNAL，但本次返回 false，外层再循环一轮，所以第一次调用此方法几乎不会直接挂起。为什么不改完 SIGNAL 直接睡？因为从"上轮抢锁失败"到"现在"之间，前驱可能已释放锁、自己可能已是 head 的直接后继——多跑一轮重试 tryAcquire，避免错过唤醒信号造成永久挂起。真正挂起只有两行：

```java
private final boolean parkAndCheckInterrupt() {
    LockSupport.park(this);
    return Thread.interrupted();
}
```

被唤醒（释放锁/中断都可能）后回到 acquireQueued 的 for 循环重新抢锁。

7 一次完整抢锁的时间线
------

三个线程竞争公平锁，把前几节按时间排开：

```
t1: lock() → tryAcquire 成功，state=1
    队列根本不存在，head 未初始化，谈不上阻塞队列

t2: lock() → tryAcquire 失败 → enq：
    第一轮 CAS(new Node()) 初始化 head（thread==null, waitStatus==0）
    第二轮 t2 入队（此时 head==tail==t2节点）
    shouldParkAfterFailedAcquire 把 head.waitStatus 改为 -1，t2 park

t3: lock() → addWaiter 快速路径 CAS 接到队尾
    t2节点.waitStatus 被改为 -1，t3 park

     head(ws=-1)      t2(ws=-1)      t3(ws=0)
   [空,无线程]  ←prev/next→ [t2]  ←prev/next→ [t3] ← tail
```

要点复盘：**每个 node 入队时都会把前驱改成 SIGNAL 然后阻塞，等前驱唤醒自己**；SIGNAL(-1) 代表的不是自己的状态而是后继节点的状态；第一个拿到锁的线程根本不碰队列——队列是竞争失败者的俱乐部。Doug Lea 用三样东西协调并发加锁：**state 原子计数**（CAS 抢锁、重入累加）、**LockSupport.park/unpark**（阻塞与唤醒，许可制、先 unpark 后 park 不丢信号）、**CLH 变体的 FIFO 双向队列**（排队与唤醒传播）。加锁路径到此闭环；解锁如何交棒队首、条件队列怎么联动，见第二篇。

面试追问链
------

* AQS 的核心数据结构？→ 一个 volatile int state + head/tail 两个引用的 FIFO 双向队列；state 语义由子类定义；
* acquire(1) 的流程？→ `!tryAcquire(arg) && acquireQueued(addWaiter(Node.EXCLUSIVE), arg)` 为 true 则 `selfInterrupt()`；
* 为什么 p == head 才 tryAcquire？head 在队列里吗？→ 保证 FIFO；head 不在队列里，是延迟初始化的空占位节点、不属任何线程；
* waitStatus 的 SIGNAL 是什么意思？→ 描述**后继节点**需要被唤醒，不是自己的状态；
* 第一次 shouldParkAfterFailedAcquire 会挂起吗？→ 一般不会，前驱默认 0，先 CAS 成 SIGNAL 返回 false 再循环一轮；
* 入队 CAS 的是哪个指针？→ tail（compareAndSetTail），prev 先行设置，next 回填失败可由反向遍历兜底。

面试官视角小结
------

多数候选人背得出"state + CLH 队列"，往下三问就分层了：head 是不是队列成员、waitStatus 到底描述谁的状态、为什么第一次不会直接 park。能主动讲出"waitStatus 说的是后继节点"和 head 延迟初始化这两个细节，说明真翻过 AbstractQueuedSynchronizer.java；能顺着 `acquireQueued(addWaiter(Node.EXCLUSIVE), arg)` 把自旋两阶段（试抢 → 改前驱 SIGNAL → park）画成时间线的，是读活的标志。追问方向自然滑向公平/非公平差异与 Condition，衔接 [AQS 源码精读（二）](./AQS源码精读二-独占锁释放与条件队列.md)。

> 来源：javadoop《一行一行源码分析清楚AbstractQueuedSynchronizer》（https://javadoop.com/post/AbstractQueuedSynchronizer）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
