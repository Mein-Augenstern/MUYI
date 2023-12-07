## 看在前面

* <a href="http://www.cnblogs.com/waterystone/p/4920797.html">Java并发之AQS详解</a>
* <a href="https://www.cnblogs.com/chengxiao/archive/2017/07/24/7141160.html">Java并发包基石-AQS详解</a>
* 一行一行源码分析清楚AbstractQueuedSynchronizer：https://javadoop.com/post/AbstractQueuedSynchronizer

## 一、AQS初步介绍

### 1 AQS第一印象

AQS 的全称为（```AbstractQueuedSynchronizer```），这个类在 ```java.util.concurrent.locks``` 包下面。

AQS 是一个用来**构建锁和同步器的框架**，使用 AQS 能简单且高效地构造出应用广泛的大量的同步器，比如我们提到的 ```ReentrantLock```，```Semaphore```，其他的诸如 ```ReentrantReadWriteLock```，```SynchronousQueue```，```FutureTask```(jdk1.7) 等等皆是基于 AQS 的。当然，我们自己也能利用 AQS 非常轻松容易地构造出符合我们自己需求的同步器。

### 2 AQS原理了解

> 在面试中被问到并发知识的时候，大多都会被问到“请你说一下自己对于 AQS 原理的理解”。下面给大家一个示例供大家参考，面试不是背题，大家一定要加入自己的思想，即使加入不了自己的思想也要保证自己能够通俗的讲出来而不是背出来。

下面大部分内容其实在 AQS 类注释上已经给出了，不过是英语看着比较吃力一点，感兴趣的话可以看看源码。

**2.1 AQS 原理概览**

**AQS 核心思想是，如果被请求的共享资源空闲，则将当前请求资源的线程设置为有效的工作线程，并且将共享资源设置为锁定状态。如果被请求的共享资源被占用，那么就需要一套线程阻塞等待以及被唤醒时锁分配的机制，这个机制 AQS 是用 CLH 队列锁实现的，即将暂时获取不到锁的线程加入到队列中。**

> CLH(Craig,Landin,and Hagersten)队列是一个虚拟的双向队列（虚拟的双向队列即不存在队列实例，仅存在结点之间的关联关系）。AQS 是将每条请求共享资源的线程封装成一个 CLH 锁队列的一个结点（Node）来实现锁的分配。

看个 AQS(AbstractQueuedSynchronizer)原理图：

![AQS原理](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/AQS%E5%8E%9F%E7%90%86%E4%B8%80.png)

AQS 使用一个 int 成员变量来表示同步状态，通过内置的 FIFO 队列来完成获取资源线程的排队工作。AQS 使用 CAS 对该同步状态进行原子操作实现对其值的修改。

```java
private volatile int state;//共享变量，使用volatile修饰保证线程可见性
```

状态信息通过 ```protected``` 类型的```getState```，```setState```，```compareAndSetState```进行操作

```java
//返回同步状态的当前值
protected final int getState() {
        return state;
}
 // 设置同步状态的值
protected final void setState(int newState) {
        state = newState;
}
//原子地（CAS操作）将同步状态值设置为给定值update如果当前同步状态的值等于expect（期望值）
protected final boolean compareAndSetState(int expect, int update) {
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
}
```

**2.2 AQS 对资源的共享方式**

**AQS 定义两种资源共享方式**

**1)Exclusive（独占）**

只有一个线程能执行，如 ReentrantLock。又可分为公平锁和非公平锁,ReentrantLock 同时支持两种锁,下面以 ReentrantLock 对这两种锁的定义做介绍：

* 公平锁：按照线程在队列中的排队顺序，先到者先拿到锁
* 非公平锁：当线程要获取锁时，先通过两次 CAS 操作去抢锁，如果没抢到，当前线程再加入到队列中等待唤醒。

> 说明：下面这部分关于 ```ReentrantLock``` 源代码内容节选自：<a href="https://www.javadoop.com/post/AbstractQueuedSynchronizer-2">一行一行源码分析清楚 AbstractQueuedSynchronizer (二)</a>

**下面来看 ReentrantLock 中相关的源代码：**

ReentrantLock 默认采用非公平锁，因为考虑获得更好的性能，通过 boolean 来决定是否用公平锁（传入 true 用公平锁）。

```java
/** Synchronizer providing all implementation mechanics */
private final Sync sync;
public ReentrantLock() {
    // 默认非公平锁
    sync = new NonfairSync();
}
public ReentrantLock(boolean fair) {
    sync = fair ? new FairSync() : new NonfairSync();
}
```

ReentrantLock 中公平锁的 ```lock``` 方法

```java
static final class FairSync extends Sync {
    final void lock() {
        acquire(1);
    }
    // AbstractQueuedSynchronizer.acquire(int arg)
    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }
    protected final boolean tryAcquire(int acquires) {
        final Thread current = Thread.currentThread();
        int c = getState();
        if (c == 0) {
            // 1. 和非公平锁相比，这里多了一个判断：是否有线程在等待
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
}
```

非公平锁的 lock 方法：

```java
static final class NonfairSync extends Sync {
    final void lock() {
        // 2. 和公平锁相比，这里会直接先进行一次CAS，成功就返回了
        if (compareAndSetState(0, 1))
            setExclusiveOwnerThread(Thread.currentThread());
        else
            acquire(1);
    }
    // AbstractQueuedSynchronizer.acquire(int arg)
    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }
    protected final boolean tryAcquire(int acquires) {
        return nonfairTryAcquire(acquires);
    }
}
/**
 * Performs non-fair tryLock.  tryAcquire is implemented in
 * subclasses, but both need nonfair try for trylock method.
 */
final boolean nonfairTryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    if (c == 0) {
        // 这里没有对阻塞队列进行判断
        if (compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    }
    else if (current == getExclusiveOwnerThread()) {
        int nextc = c + acquires;
        if (nextc < 0) // overflow
            throw new Error("Maximum lock count exceeded");
        setState(nextc);
        return true;
    }
    return false;
}
```

总结：公平锁和非公平锁只有两处不同：

1. 非公平锁在调用 lock 后，首先就会调用 CAS 进行一次抢锁，如果这个时候恰巧锁没有被占用，那么直接就获取到锁返回了。
2. 非公平锁在 CAS 失败后，和公平锁一样都会进入到 tryAcquire 方法，在 tryAcquire 方法中，如果发现锁这个时候被释放了（state == 0），非公平锁会直接 CAS 抢锁，但是公平锁会判断等待队列是否有线程处于等待状态，如果有则不去抢锁，乖乖排到后面。

公平锁和非公平锁就这两点区别，如果这两次 CAS 都不成功，那么后面非公平锁和公平锁是一样的，都要进入到阻塞队列等待唤醒。

相对来说，非公平锁会有更好的性能，因为它的吞吐量比较大。当然，非公平锁让获取锁的时间变得更加不确定，可能会导致在阻塞队列中的线程长期处于饥饿状态。

**2)Share（共享）**

多个线程可同时执行，如 ```Semaphore/CountDownLatch```。```Semaphore```、```CountDownLatch```、 ```CyclicBarrier```、```ReadWriteLock``` 我们都会在后面讲到。

```ReentrantReadWriteLock``` 可以看成是组合式，因为 ```ReentrantReadWriteLock``` 也就是读写锁允许多个线程同时对某一资源进行读。

不同的自定义同步器争用共享资源的方式也不同。自定义同步器在实现时只需要实现共享资源 state 的获取与释放方式即可，至于具体线程等待队列的维护（如获取资源失败入队/唤醒出队等），AQS 已经在上层已经帮我们实现好了。

**2.3 AQS 底层使用了模板方法模式**

同步器的设计是基于模板方法模式的，如果需要自定义同步器一般的方式是这样（模板方法模式很经典的一个应用）：

1. 使用者继承 AbstractQueuedSynchronizer 并重写指定的方法。（这些重写方法很简单，无非是对于共享资源 state 的获取和释放）
2. 将 AQS 组合在自定义同步组件的实现中，并调用其模板方法，而这些模板方法会调用使用者重写的方法。

这和我们以往通过实现接口的方式有很大区别，这是模板方法模式很经典的一个运用，下面简单的给大家介绍一下模板方法模式，模板方法模式是一个很容易理解的设计模式之一。

> 模板方法模式是基于”继承“的，主要是为了在不改变模板结构的前提下在子类中重新定义模板中的内容以实现复用代码。举个很简单的例子假如我们要去一个地方的步骤是：购票buyTicket()->安检securityCheck()->乘坐某某工具回家ride()->到达目的地arrive()。我们可能乘坐不同的交通工具回家比如飞机或者火车，所以除了ride()方法，其他方法的实现几乎相同。我们可以定义一个包含了这些方法的抽象类，然后用户根据自己的需要继承该抽象类然后修改 ride()方法。

**AQS 使用了模板方法模式，自定义同步器时需要重写下面几个 AQS 提供的模板方法：**

```java
isHeldExclusively()//该线程是否正在独占资源。只有用到condition才需要去实现它。
tryAcquire(int)//独占方式。尝试获取资源，成功则返回true，失败则返回false。
tryRelease(int)//独占方式。尝试释放资源，成功则返回true，失败则返回false。
tryAcquireShared(int)//共享方式。尝试获取资源。负数表示失败；0表示成功，但没有剩余可用资源；正数表示成功，且有剩余资源。
tryReleaseShared(int)//共享方式。尝试释放资源，成功则返回true，失败则返回false。
```

默认情况下，每个方法都抛出 ```UnsupportedOperationException```。 这些方法的实现必须是内部线程安全的，并且通常应该简短而不是阻塞。AQS 类中的其他方法都是 final ，所以无法被其他类使用，只有这几个方法可以被其他类使用。

以 ReentrantLock 为例，state 初始化为 0，表示未锁定状态。A 线程 lock()时，会调用 tryAcquire()独占该锁并将 state+1。此后，其他线程再 tryAcquire()时就会失败，直到 A 线程 unlock()到 state=0（即释放锁）为止，其它线程才有机会获取该锁。当然，释放锁之前，A 线程自己是可以重复获取此锁的（state 会累加），这就是可重入的概念。但要注意，获取多少次就要释放多么次，这样才能保证 state 是能回到零态的。

再以 CountDownLatch 以例，任务分为 N 个子线程去执行，state 也初始化为 N（注意 N 要与线程个数一致）。这 N 个子线程是并行执行的，每个子线程执行完后 countDown()一次，state 会 CAS(Compare and Swap)减 1。等到所有子线程都执行完后(即 state=0)，会 unpark()主调用线程，然后主调用线程就会从 await()函数返回，继续后余动作。

一般来说，自定义同步器要么是独占方法，要么是共享方式，他们也只需实现```tryAcquire-tryRelease```、```tryAcquireShared-tryReleaseShared```中的一种即可。但 AQS 也支持自定义同步器同时实现独占和共享两种方式，如```ReentrantReadWriteLock```。

## 二、AQS源码阅读-前提在ReentrantLock的公平锁和非公平锁分析

### AQS属性介绍

```java
/**
 * AQS队列的头节点，持有锁的节点
 *
 * GPT4.0的翻译：等待队列的头部，延迟初始化。除了初始化之外，只能通过方法setHead来修改。注意：如果头部存在，它的等待状态保证不会是CANCELLED（已取消）。 
 * Head of the wait queue, lazily initialized.  Except for
 * initialization, it is modified only via method setHead.  Note:
 * If head exists, its waitStatus is guaranteed not to be
 * CANCELLED.
 */
private transient volatile Node head;

/**
 * AQS队列的尾节点，每个新进入的线程会首先被初始化成node节点，设置到已有尾节点的后面，从而形成了链表结构
 * Tail of the wait queue, lazily initialized.  Modified only via
 * method enq to add new wait node.
 */
private transient volatile Node tail;

/**
 * 这个是最重要的，代表当前锁的状态，0代表没有被占用，大于 0 代表有线程持有当前锁
 * 这个值可以大于 1，是因为锁可以重入，每次重入都加上 1
 * 同步状态
 * The synchronization state.
 */
private volatile int state;
```
> 父类AbstractOwnableSynchronizer中的属性介绍

```java
/**
 * 代表当前持有独占锁的线程
 * The current owner of exclusive mode synchronization.
 */
private transient Thread exclusiveOwnerThread;
```

AbstractQueuedSynchronizer 的等待队列示意如下所示，注意了，**之后分析过程中所说的 queue，也就是阻塞队列不包含 head，不包含 head，不包含 head**。

<img width="985" alt="截屏2023-12-01 10 05 06" src="https://github.com/Mein-Augenstern/MUYI/assets/34135120/c6231fa1-7352-49c0-8493-54b7eba8acd8">

### Node

```java
static final class Node {

    /** 表示一个节点正在共享模式下等待的标记。 */
    /** Marker to indicate a node is waiting in shared mode */
    static final Node SHARED = new Node();

    /** 表示一个节点正在独占模式下等待的标记。 */
    /** Marker to indicate a node is waiting in exclusive mode */
    static final Node EXCLUSIVE = null;

    /** waitStatus 的值表示线程已取消。 */
    // 在AQS（AbstractQueuedSynchronizer）中，static final int CANCELLED = 1; 表示一个常量值，用于指示一个节点的等待状态（waitStatus）已经被取消。
        //在AQS的上下文中，节点通常代表一个正在等待获取锁或者条件的线程。
    // 当一个线程在同步队列中等待时，由于某些原因（比如超时或者中断），它可能决定不再等待资源的分配，此时它会被赋予一个“已取消”（CANCELLED）的状态。用于标示这个状态的整数值就是1。
    // 这个值用来告诉AQS和与之相关的同步组件，这个特定的节点不应该再被考虑为正常等待资源的线程，应该从同步队列中移除。
    // 这对于资源的管理和线程的调度很重要，确保系统能够正确响应线程的取消操作，并保持同步队列的秩序和性能。
    /** waitStatus value to indicate thread has cancelled */
    static final int CANCELLED =  1;

    // 代表着通知状态，这个状态下的节点如果被唤醒，就有义务去唤醒它的后继节点。这也就是为什么一个节点的线程阻塞之前必须保证前一个节点是 SIGNAL 状态。
    /** waitStatus value to indicate successor's thread needs unparking */
    static final int SIGNAL    = -1;

    // 代表条件等待状态，条件等待队列里每一个节点都是这个状态，它的节点被移到同步队列之后状态会修改为 0。
    /** waitStatus value to indicate thread is waiting on condition */
    static final int CONDITION = -2;

    // 传播状态，在一些地方用于修复 bug 和提高性能，减少不必要的循环。
    /**
     * waitStatus value to indicate the next acquireShared should
     * unconditionally propagate
     */
    static final int PROPAGATE = -3;

    /**
     * Status field, taking on only the values:
     *   SIGNAL:     The successor of this node is (or will soon be)
     *               blocked (via park), so the current node must
     *               unpark its successor when it releases or
     *               cancels. To avoid races, acquire methods must
     *               first indicate they need a signal,
     *               then retry the atomic acquire, and then,
     *               on failure, block.
     *   CANCELLED:  This node is cancelled due to timeout or interrupt.
     *               Nodes never leave this state. In particular,
     *               a thread with cancelled node never again blocks.
     *   CONDITION:  This node is currently on a condition queue.
     *               It will not be used as a sync queue node
     *               until transferred, at which time the status
     *               will be set to 0. (Use of this value here has
     *               nothing to do with the other uses of the
     *               field, but simplifies mechanics.)
     *   PROPAGATE:  A releaseShared should be propagated to other
     *               nodes. This is set (for head node only) in
     *               doReleaseShared to ensure propagation
     *               continues, even if other operations have
     *               since intervened.
     *   0:          None of the above
     *
     * The values are arranged numerically to simplify use.
     * Non-negative values mean that a node doesn't need to
     * signal. So, most code doesn't need to check for particular
     * values, just for sign.
     *
     * The field is initialized to 0 for normal sync nodes, and
     * CONDITION for condition nodes.  It is modified using CAS
     * (or when possible, unconditional volatile writes).
     */
    // 状态字段，只取以下值：
    // SIGNAL: 表示此节点的后继节点正在被阻塞（通过park），或将很快被阻塞，因此当前节点在释放或取消时必须唤醒其后继节点。
         // 为了避免竞争，获取方法必须首先表明它们需要一个信号，然后重试原子性获取，如果失败，再进行阻塞。
    // CANCELLED: 由于超时或中断，此节点已被取消。节点一旦处于此状态便永远不会离开。特别地，拥有已取消节点的线程将不再被阻塞。
    // CONDITION: 此节点当前位于条件队列中。在被转移之前，它不会作为同步队列节点使用，转移时状态将被设置为0。（此处使用此值与字段的其他用途无关，但简化了操作机制。）
    // PROPAGATE: 释放共享锁的操作应当被传播到其他节点。这一设置（仅针对头节点）是在doReleaseShared中进行的，以确保即使后面有其他操作介入，传播依然可以继续。
    // 0: 以上情况均不符合。
    // 这些值按数值顺序排列，以简化使用。非负值意味着节点不需要发出信号。因此，大多数代码不需要检查特定值，只需检查符号即可。
    // 该字段对于正常同步节点初始化为0，对于条件节点则为CONDITION。它使用CAS（或在可能的情况下，使用无条件的易失性写操作）进行修改。
    volatile int waitStatus;

    /**
     * Link to predecessor node that current node/thread relies on
     * for checking waitStatus. Assigned during enqueuing, and nulled
     * out (for sake of GC) only upon dequeuing.  Also, upon
     * cancellation of a predecessor, we short-circuit while
     * finding a non-cancelled one, which will always exist
     * because the head node is never cancelled: A node becomes
     * head only as a result of successful acquire. A
     * cancelled thread never succeeds in acquiring, and a thread only
     * cancels itself, not any other node.
     */
    // 指向前驱节点的链接，当前节点/线程依赖它来检查 waitStatus。在入队时分配，并且只有在出队时才置为 null（为了垃圾回收）。
    // 同时，当一个前驱节点被取消时，我们会快速寻找一个未被取消的节点，这样的节点总是存在的，
    // 因为头节点永远不会被取消：一个节点只有在成功获取资源后才成为头节点。被取消的线程永远不会成功获取资源，而且一个线程只会取消自己，不会取消任何其他节点。 
    volatile Node prev;

    /**
     * Link to the successor node that the current node/thread
     * unparks upon release. Assigned during enqueuing, adjusted
     * when bypassing cancelled predecessors, and nulled out (for
     * sake of GC) when dequeued.  The enq operation does not
     * assign next field of a predecessor until after attachment,
     * so seeing a null next field does not necessarily mean that
     * node is at end of queue. However, if a next field appears
     * to be null, we can scan prev's from the tail to
     * double-check.  The next field of cancelled nodes is set to
     * point to the node itself instead of null, to make life
     * easier for isOnSyncQueue.
     */
     // 指向后继节点的链接，当前节点/线程在释放时会通过此链接来唤醒后继节点。该链接在入队时分配，在绕过被取消的前驱节点时调整，并且在出队时置为 null（为了垃圾回收）。
     // 入队操作在节点附加之后才会为前驱节点的 next 字段赋值，因此看到一个 null 的 next 字段并不一定意味着该节点就在队列的末端。
     // 然而，如果 next 字段似乎是 null，我们可以从尾部开始扫描 prev 字段来双重检查。被取消节点的 next 字段被设置为指向节点本身而不是 null，以简化 isOnSyncQueue 方法的逻辑。
    volatile Node next;

    /**
     * The thread that enqueued this node.  Initialized on
     * construction and nulled out after use.
     */
    volatile Thread thread;

    /**
     * Link to next node waiting on condition, or the special
     * value SHARED.  Because condition queues are accessed only
     * when holding in exclusive mode, we just need a simple
     * linked queue to hold nodes while they are waiting on
     * conditions. They are then transferred to the queue to
     * re-acquire. And because conditions can only be exclusive,
     * we save a field by using special value to indicate shared
     * mode.
     */
     // 指向等待条件的下一个节点的链接，或者是特殊值 SHARED。
     // 因为条件队列只在持有独占模式时才被访问，我们只需要一个简单的链表队列来保存在等待条件时的节点。
     // 之后，这些节点会被转移到队列中以重新获取资源。并且由于条件只能是独占的，我们通过使用特殊值来指示共享模式来节省一个字段。
    Node nextWaiter;

    /**
     * Returns true if node is waiting in shared mode.
     */
    final boolean isShared() {
        return nextWaiter == SHARED;
    }

    /**
     * Returns previous node, or throws NullPointerException if null.
     * Use when predecessor cannot be null.  The null check could
     * be elided, but is present to help the VM.
     *
     * 返回前驱节点，如果为 null 则抛出 NullPointerException 异常。在前驱节点不可能为 null 的情况下使用。虽然可以省略 null 检查，但它的存在是为了帮助虚拟机。
     * 
     * 之所以会返回需要主动抛异常，可以结合AQS中调用当前方法的处理逻辑理解，基本都被try{}finally{}代码块包裹。 
     *
     * @return the predecessor of this node
     */
    final Node predecessor() throws NullPointerException {
        Node p = prev;
        if (p == null)
            throw new NullPointerException();
        else
            return p;
    }

    Node() {    // Used to establish initial head or SHARED marker
    }

    Node(Thread thread, Node mode) {     // Used by addWaiter
        this.nextWaiter = mode;
        this.thread = thread;
    }

    Node(Thread thread, int waitStatus) { // Used by Condition
        this.waitStatus = waitStatus;
        this.thread = thread;
    }
}
```

## 强锁过程分析

### AQS-acquire

```java
/**
* 以独占模式获取，忽略中断。通过至少调用一次 {@link #tryAcquire} 来实现，并在成功时返回。否则，线程将被加入队列，
* 可能反复阻塞和解除阻塞，并调用 {@link #tryAcquire} 直到成功。此方法可用于实现方法 {@link Lock#lock}。
* Acquires in exclusive mode, ignoring interrupts.  Implemented
* by invoking at least once {@link #tryAcquire},
* returning on success.  Otherwise the thread is queued, possibly
* repeatedly blocking and unblocking, invoking {@link
* #tryAcquire} until success.  This method can be used
* to implement method {@link Lock#lock}.
*
* @param arg the acquire argument.  This value is conveyed to
*        {@link #tryAcquire} but is otherwise uninterpreted and
*        can represent anything you like.
*/
public final void acquire(int arg) {
        if (
            // 尝试获取资源
            !tryAcquire(arg) &&
            // 将当前线程放入队列中
            acquireQueued(
                // 将当前线程包装成独占模式的node，同时进入到队列中
                addWaiter(Node.EXCLUSIVE),
                arg
              )
        )
        selfInterrupt();
}
```

### ReentrantLock-FairSync-tryAcquire

```java
static final class FairSync extends Sync {
    private static final long serialVersionUID = -3000897897090466540L;

    // 尝试强锁
    final void lock() {
        acquire(1);
    }

    // 公平版本的 tryAcquire。
    // 在AQS类中的 方法：public final void acquire(int arg) 执行时，若下面的方法返回true时，则AQS中acquire方法就直接结束了
    // 若下面的方法返回false时，则AQS中的acquire方法会继续执行acquireQueued方法，将当前线程压入到队列中。
    /**
     * 公平版本的尝试获取资源。除非是递归调用、没有等待者或者是第一个请求者，否则不授予访问权限。
     * Fair version of tryAcquire.  Don't grant access unless
     * recursive call or no waiters or is first.
     */
    protected final boolean tryAcquire(int acquires) {
        final Thread current = Thread.currentThread();
        int c = getState();

        // state==0意味着资源还没有被其他线程抢占
        if (c == 0) {
            if (
                // 确认是否有其他线程在队列中等待，在并发的场景下会发生。可参照：AQS-hasQueuedPredecessors 方法注释翻译。
                // 如果下面的方法return true了，则说明虽然state==0了，但是依旧有其他线程抢先在队列中了。
                !hasQueuedPredecessors()
                &&
                // CAS抢占更新state资源标记位
                // 如果下面的CAS失败了，则说明并发强锁失败了
                compareAndSetState(0, acquires)
                ) {
                // 强锁成功，将当前线程设置到资源持有的标记位上
                setExclusiveOwnerThread(current);
                return true;
            }
        }

        // 此分支意味着当前线程锁重入了
        else if (current == getExclusiveOwnerThread()) {
            int nextc = c + acquires;

            // 重入锁次数的校验
            if (nextc < 0)
                throw new Error("Maximum lock count exceeded");
                
            setState(nextc);
            return true;
        }

        // 说明当前线程强锁失败
        return false;
    }
}
```

### AQS-hasQueuedPredecessors

简单点说，下面这个方法在ReentrantLock-FairSync-tryAcquire中调用时，起到的作用时尽可能保证公平，虽然在ReentrantLock-FairSync-tryAcquire中调用时代表锁是可用的，但考虑到是公平锁的前提，就得是FIFO，所以再二次判断下是否有其他线程在队列中等待了。

```java
// 查询是否有任何线程等待获取资源的时间比当前线程更长。
// 调用此方法等同于（但可能比）以下操作更高效：
// getFirstQueuedThread() != Thread.currentThread() && hasQueuedThreads()
// 请注意，因为中断和超时导致的取消可以随时发生，返回true并不保证其他线程会在当前线程之前获取资源。
// 同样，由于队列为空，此方法返回false后，其他线程可能会赢得入队竞争。
// 此方法旨在被公平的同步器使用，以避免闯入。
// 这样的同步器的tryAcquire方法应当在此方法返回true时返回false（除非这是一个可重入的获取），它的tryAcquireShared方法应当返回一个负值。
public final boolean hasQueuedPredecessors() {
    // The correctness of this depends on head being initialized
    // before tail and on head.next being accurate if the current
    // thread is first in queue.
    Node t = tail; // Read fields in reverse initialization order
    Node h = head;
    Node s;
    return h != t &&
        ((s = h.next) == null || s.thread != Thread.currentThread());
}
```

### AQS-acquire-addWaiter

```java
// 为当前线程及给定模式创建并入队节点。
private Node addWaiter(Node mode) {
    // 将当前线程包装成node节点对象
    Node node = new Node(Thread.currentThread(), mode);

    // 尝试快速路径入队；如果失败则回退到完整的入队操作。
    // Try the fast path of enq; backup to full enq on failure

    Node pred = tail;

    // 如果下面的条件不成立，则说明队列为空，即tail==head的时候，其实队列是空的。
    if (pred != null) {
        node.prev = pred;
        if (
                // CAS设置队列尾节点失败时返回false时，则说明有并发
                compareAndSetTail(pred, node)
                ) {
            pred.next = node;
            return node;
        }
    }

    // 走到下面这一步说明满足下面其中的一个场景
    // 1、队列是空的
    // 2、CAS失败
    enq(node);
    return node;
}
```

### AQS-acquire-addWaiter-enq

```java
// 如果需要，将节点插入队列并进行初始化。
// 采用自旋方式入队列
private Node enq(final Node node) {
    for (;;) {
        Node t = tail;
        // 检测队列尾节点是否为空，若为空，则必须先初始化后，再重新将当前线程节点压入队列中。
        if (t == null) { // Must initialize
            if (compareAndSetHead(new Node()))
                tail = head;
        }
        // 尝试将当前线程节点压入队列中
        else {
            node.prev = t;
            if (compareAndSetTail(t, node)) {
                t.next = node;
                return t;
            }
        }
    }
}
```

### AQS-acquireQueued

```java
// 为队列中已存在的线程以排他的不可中断模式获取资源。这种方式被条件等待方法以及获取操作所使用。
final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();

            // 强锁成功，则直接返回
            if (
                // 若当前线程节点的前置节点是头节点
                p == head
                        &&
                        // 尝试获取获取资源
                        tryAcquire(arg)) {
                // 将
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return interrupted;
            }

            // 强锁失败，则将线程挂起
            if (
                // 检查并更新一个未能获取资源的节点的状态
                // 若返回false，继续for循环，留个点：若返回false时，为什么不挂起线程
                                           // 是为了应对在经过这个方法后，node已经是head的直接后继节点了
                shouldParkAfterFailedAcquire(p, node)
                &&
                // 挂起并检查当前线程
                parkAndCheckInterrupt()
               )
                // 返回当前线程需要被挂起的标识
                interrupted = true;
        }
    } finally {
        if (failed)
            // 抛异常时会执行此代码，比如predecessor()方法主动抛出NPE、tryAcquire(arg)方法抛异常
            cancelAcquire(node);
    }
}
```

### AQS-shouldParkAfterFailedAcquire

```java

// 仔细看shouldParkAfterFailedAcquire(p, node)，我们可以发现，其实第一次进来的时候，一般都不会返回true的，
// 原因很简单，前驱节点的waitStatus=-1是依赖于后置节点设置的。也就是说，我都还没给前驱设置-1呢，怎么可能是true呢，
// 但是要看到，这个方法是套在循环里的，所以第二次进来的时候状态就是-1了。


// 检查并更新一个未能获取资源的节点的状态。如果线程应该阻塞，则返回 true。
// 这是所有获取循环中的主要信号控制。要求 pred == node.prev。
private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
    // 获取要入队列节点的前置节点状态
    int ws = pred.waitStatus;

    // 前置节点状态为 SIGNAL，说明前置节点状态正常，当前线程需要挂起，即返回挂起标志true
    // 结合Node中SIGNAL状态的说明：
    // 代表着通知状态，这个状态下的节点如果被唤醒，就有义务去唤醒它的后继节点。这也就是为什么一个节点的线程阻塞之前必须保证前一个节点是 SIGNAL 状态。
    // 返回true，则意味着当前线程节点应该被阻塞
    if (ws == Node.SIGNAL)
        /*
         * 这个节点已经设置了状态，请求一个释放信号来唤醒它，因此它可以安全地进行挂起。
         * This node has already set status asking a release
         * to signal it, so it can safely park.
         */
        return true;

    // 前置节点状态大于0，意味着前置节点已经从队列中移除了
    if (ws > 0) {
        /*
         * 前驱节点被取消了。跳过前驱节点，并表示需要重试。
         * Predecessor was cancelled. Skip over predecessors and
         * indicate retry.
         */
        do {
            // 将当前节点的 prev 指针指向它前驱节点的前一个节点（即 pred 的前驱节点），同时也将 pred 变量更新为它之前的节点，
                // 这样做的目的通常是为了移除当前节点 pred，并将其前一个节点连接到当前节点 node，或者是在节点插入队列时调整节点的位置。
                // 在AQS的上下文中，该操作可能是在进行节点的取消排队（例如，当一个线程在同步队列中等待获取锁时决定不再等待，并需要被移除）或者在进行某些优化的重新排列时使用。
            node.prev = pred = pred.prev;
        } while (pred.waitStatus > 0);

        pred.next = node;
    }

    // 前驱节点的waitStatus不等于-1和1，那也就是只可能是0，-2，-3
    // 在我们前面的源码中，都没有看到有设置waitStatus的，所以每个新的node入队时，waitStatu都是0
    // 正常情况下，前驱节点是之前的 tail，那么它的 waitStatus 应该是 0
    // 用CAS将前驱节点的waitStatus设置为Node.SIGNAL(也就是-1)
    else {
        /*
         * 等待状态必须是 0 或 PROPAGATE。
         * 表示我们需要一个信号，但不要立即挂起。调用者需要重试以确保在挂起之前无法获取资源。
         * waitStatus must be 0 or PROPAGATE.  Indicate that we
         * need a signal, but don't park yet.  Caller will need to
         * retry to make sure it cannot acquire before parking.
         */
        compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
    }

    // 返回false，则说明外部调用此方法的地方，还得重新执行for循环，即自旋的过程
    return false;
}
```

## 解锁过程分析

### ReentrantLock-unlock

```java

// ReentrantLock的解锁入口

/**
 * Attempts to release this lock.
 *
 * 如果当前线程是这个锁的持有者，那么持有计数将被减少。
 * 如果持有计数现在为零，那么锁将被释放。如果当前线程不是这个锁的持有者，那么将抛出 {@link IllegalMonitorStateException} 异常。
 * 
 * <p>If the current thread is the holder of this lock then the hold
 * count is decremented.  If the hold count is now zero then the lock
 * is released.  If the current thread is not the holder of this
 * lock then {@link IllegalMonitorStateException} is thrown.
 *
 * @throws IllegalMonitorStateException if the current thread does not
 *         hold this lock
 */
public void unlock() {
    sync.release(1);
}
```

### AQS-release

```java
/**
 * 
 * 释放独占模式。如果{@link #tryRelease}返回true，则通过解除一个或多个线程的阻塞来实现。此方法可用于实现{@link Lock#unlock}方法。
 * 
 * Releases in exclusive mode.  Implemented by unblocking one or
 * more threads if {@link #tryRelease} returns true.
 * This method can be used to implement method {@link Lock#unlock}.
 *
 * @param arg the release argument.  This value is conveyed to
 *        {@link #tryRelease} but is otherwise uninterpreted and
 *        can represent anything you like.
 * @return the value returned from {@link #tryRelease}
 */
public final boolean release(int arg) {
    if (
        tryRelease(arg)
        ) {
        Node h = head;
        if (h != null && h.waitStatus != 0)
            // 
            unparkSuccessor(h);
        return true;
    }
    return false;
}
```

### ReentrantLock-Sync-tryRelease

```java
protected final boolean tryRelease(int releases) {
    // 计算锁重入的次数减去要释放的次数
    int c = getState() - releases;

    // 判断持有锁的线程是否等于当前要释放锁的线程
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();

    // 判断是否当前是否需要释放锁
    boolean free = false;
    if (c == 0) {
        free = true;
        // help gc
        setExclusiveOwnerThread(null);
    }

    // 更新锁被冲入的次数
    setState(c);
    return free;
}
```

### AQS-unparkSuccessor

```java
/**
 *
 *如果节点的后继存在，则唤醒该后继节点。
 * 
 * Wakes up node's successor, if one exists.
 *
 * @param node the node
 */
private void unparkSuccessor(Node node) {
    /*
     *
     * 如果状态是负的（即，可能需要信号），尝试在发出信号前清除它。如果这操作失败了，或者等待线程改变了状态，也是可以接受的。
     * 
     * If status is negative (i.e., possibly needing signal) try
     * to clear in anticipation of signalling.  It is OK if this
     * fails or if status is changed by waiting thread.
     */
    int ws = node.waitStatus;
    if (ws < 0)
        compareAndSetWaitStatus(node, ws, 0);


        // 下面的代码就是唤醒后继节点，但是有可能后继节点取消了等待（waitStatus==1）
        // 从队尾往前找，找到 waitStatus <= 0 的所有节点中排在最前面的
    /*
     * Thread to unpark is held in successor, which is normally
     * just the next node.  But if cancelled or apparently null,
     * traverse backwards from tail to find the actual
     * non-cancelled successor.
     */
    Node s = node.next;
    if (s == null || s.waitStatus > 0) {
        s = null;
        // 从后往前找，仔细看代码，不必担心中间有节点取消(waitStatus==1)的情况
        for (Node t = tail; t != null && t != node; t = t.prev)
            if (t.waitStatus <= 0)
                s = t;
    }

    // 唤醒线程
    if (s != null)
        LockSupport.unpark(s.thread);
}
```

## ReentrantLock的加锁和解锁示例图解析

内容来自于：https://javadoop.com/post/AbstractQueuedSynchronizer

下面属于回顾环节，用简单的示例来说一遍，如果上面的有些东西没看懂，这里还有一次帮助你理解的机会。

首先，第一个线程调用 reentrantLock.lock()，翻到最前面可以发现，tryAcquire(1) 直接就返回 true 了，结束。只是设置了 state=1，连 head 都没有初始化，更谈不上什么阻塞队列了。要是线程 1 调用 unlock() 了，才有线程 2 来，那世界就太太太平了，完全没有交集嘛，那我还要 AQS 干嘛。

如果线程 1 没有调用 unlock() 之前，线程 2 调用了 lock(), 想想会发生什么？

线程 2 会初始化 head【new Node()】，同时线程 2 也会插入到阻塞队列并挂起 (注意看这里是一个 for 循环，而且设置 head 和 tail 的部分是不 return 的，只有入队成功才会跳出循环)

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

首先，是线程 2 初始化 head 节点，此时 head==tail, waitStatus==0

<img width="992" alt="截屏2023-12-05 10 44 33" src="https://github.com/Mein-Augenstern/MUYI/assets/34135120/38bbc275-2711-45c7-9146-e7c40fae1d9e">

然后线程 2 入队：

<img width="986" alt="截屏2023-12-05 10 45 04" src="https://github.com/Mein-Augenstern/MUYI/assets/34135120/6ddcb715-fa59-46d1-9990-8f4d8418552b">

同时我们也要看此时节点的 waitStatus，我们知道 head 节点是线程 2 初始化的，此时的 waitStatus 没有设置， java 默认会设置为 0，但是到 shouldParkAfterFailedAcquire 这个方法的时候，线程 2 会把前驱节点，也就是 head 的waitStatus设置为 -1。

那线程 2 节点此时的 waitStatus 是多少呢，由于没有设置，所以是 0；

如果线程 3 此时再进来，直接插到线程 2 的后面就可以了，此时线程 3 的 waitStatus 是 0，到 shouldParkAfterFailedAcquire 方法的时候把前驱节点线程 2 的 waitStatus 设置为 -1。

<img width="975" alt="截屏2023-12-05 10 45 32" src="https://github.com/Mein-Augenstern/MUYI/assets/34135120/4b25b91b-e9cd-4dad-8664-1135809b6bfc">

这里可以简单说下 waitStatus 中 SIGNAL(-1) 状态的意思，Doug Lea 注释的是：代表后继节点需要被唤醒。也就是说这个 waitStatus 其实代表的不是自己的状态，而是后继节点的状态，我们知道，每个 node 在入队的时候，都会把前驱节点的状态改为 SIGNAL，然后阻塞，等待被前驱唤醒。这里涉及的是两个问题：有线程取消了排队、唤醒操作。其实本质是一样的，读者也可以顺着 “waitStatus代表后继节点的状态” 这种思路去看一遍源码。


## ReentrantLock的公平锁和非公平锁区别

ReentrantLock 默认采用非公平锁，除非你在构造方法中传入参数 true 。

```java
public ReentrantLock() {
    // 默认非公平锁
    sync = new NonfairSync();
}
public ReentrantLock(boolean fair) {
    sync = fair ? new FairSync() : new NonfairSync();
}
```

公平锁的 lock 方法：

```java
static final class FairSync extends Sync {
    final void lock() {
        acquire(1);
    }
    // AbstractQueuedSynchronizer.acquire(int arg)
    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }
    protected final boolean tryAcquire(int acquires) {
        final Thread current = Thread.currentThread();
        int c = getState();
        if (c == 0) {
            // 1. 和非公平锁相比，这里多了一个判断：是否有线程在等待
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
}
```

非公平锁的 lock 方法：

```java
static final class NonfairSync extends Sync {
    final void lock() {
        // 2. 和公平锁相比，这里会直接先进行一次CAS，成功就返回了
        if (compareAndSetState(0, 1))
            setExclusiveOwnerThread(Thread.currentThread());
        else
            acquire(1);
    }
    // AbstractQueuedSynchronizer.acquire(int arg)
    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }
    protected final boolean tryAcquire(int acquires) {
        return nonfairTryAcquire(acquires);
    }
}
/**
 * Performs non-fair tryLock.  tryAcquire is implemented in
 * subclasses, but both need nonfair try for trylock method.
 */
final boolean nonfairTryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    if (c == 0) {
        // 这里没有对阻塞队列进行判断
        if (compareAndSetState(0, acquires)) {
            setExclusiveOwnerThread(current);
            return true;
        }
    }
    else if (current == getExclusiveOwnerThread()) {
        int nextc = c + acquires;
        if (nextc < 0) // overflow
            throw new Error("Maximum lock count exceeded");
        setState(nextc);
        return true;
    }
    return false;
}
```

总结：公平锁和非公平锁只有两处不同：

- 非公平锁在调用 lock 后，首先就会调用 CAS 进行一次抢锁，如果这个时候恰巧锁没有被占用，那么直接就获取到锁返回了。

- 非公平锁在 CAS 失败后，和公平锁一样都会进入到 tryAcquire 方法，在 tryAcquire 方法中，如果发现锁这个时候被释放了（state == 0），非公平锁会直接 CAS 抢锁，但是公平锁会判断等待队列是否有线程处于等待状态，如果有则不去抢锁，乖乖排到后面。

公平锁和非公平锁就这两点区别，如果这两次 CAS 都不成功，那么后面非公平锁和公平锁是一样的，都要进入到阻塞队列等待唤醒。

相对来说，非公平锁会有更好的性能，因为它的吞吐量比较大。当然，非公平锁让获取锁的时间变得更加不确定，可能会导致在阻塞队列中的线程长期处于饥饿状态。

## Condition

Tips: 这里重申一下，要看懂这个，必须要先看懂上一篇关于 AbstractQueuedSynchronizer(https://javadoop.com/2017/06/16/AbstractQueuedSynchronizer/) 的介绍，或者你已经有相关的知识了，否则这节肯定是看不懂的。

我们先来看看 Condition 的使用场景，Condition 经常可以用在生产者-消费者的场景中，请看 Doug Lea 给出的这个例子：

```java
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class BoundedBuffer {
    final Lock lock = new ReentrantLock();
    // condition 依赖于 lock 来产生
    final Condition notFull = lock.newCondition();
    final Condition notEmpty = lock.newCondition();

    final Object[] items = new Object[100];
    int putptr, takeptr, count;

    // 生产
    public void put(Object x) throws InterruptedException {
        lock.lock();
        try {
            while (count == items.length)
                notFull.await();  // 队列已满，等待，直到 not full 才能继续生产
            items[putptr] = x;
            if (++putptr == items.length) putptr = 0;
            ++count;
            notEmpty.signal(); // 生产成功，队列已经 not empty 了，发个通知出去
        } finally {
            lock.unlock();
        }
    }

    // 消费
    public Object take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0)
                notEmpty.await(); // 队列为空，等待，直到队列 not empty，才能继续消费
            Object x = items[takeptr];
            if (++takeptr == items.length) takeptr = 0;
            --count;
            notFull.signal(); // 被我消费掉一个，队列 not full 了，发个通知出去
            return x;
        } finally {
            lock.unlock();
        }
    }
}
```

**1、我们可以看到，在使用 condition 时，必须先持有相应的锁。这个和 Object 类中的方法有相似的语义，需要先持有某个对象的监视器锁才可以执行 wait(), notify() 或 notifyAll() 方法。**

**2、ArrayBlockingQueue 采用这种方式实现了生产者-消费者，所以请只把这个例子当做学习例子，实际生产中可以直接使用 ArrayBlockingQueue**

我们常用 obj.wait()，obj.notify() 或 obj.notifyAll() 来实现相似的功能，但是，它们是基于对象的监视器锁的。需要深入了解这几个方法的读者，可以参考我的另一篇文章《深入分析 java 8 编程语言规范：Threads and Locks》(https://javadoop.com/2017/07/05/Threads-And-Locks-md/)。而这里说的 Condition 是基于 ReentrantLock 实现的，而 ReentrantLock 是依赖于 AbstractQueuedSynchronizer 实现的。

在往下看之前，读者心里要有一个整体的概念。condition 是依赖于 ReentrantLock  的，不管是调用 await 进入等待还是 signal 唤醒，都必须获取到锁才能进行操作。

每个 ReentrantLock  实例可以通过调用多次 newCondition 产生多个 ConditionObject 的实例：

```java
final ConditionObject newCondition() {
    // 实例化一个 ConditionObject
    return new ConditionObject();
}
```

### ConditionObject

我们首先来看下我们关注的 Condition 的实现类 AbstractQueuedSynchronizer 类中的 ConditionObject。

**ConditionObject中属性介绍**

```java
public class ConditionObject implements Condition, java.io.Serializable {

    // 条件队列中的第一个节点
    /** First node of condition queue. */
    private transient Node firstWaiter;

    // 条件队列中的最后一个节点
    /** Last node of condition queue. */
    private transient Node lastWaiter;
```

在上面介绍 AQS 的时候，我们有一个**阻塞队列**，用于保存等待获取锁的线程的队列。这里我们引入另一个概念，叫**条件队列（condition queue）**，我画了一张简单的图用来说明这个。

> 这里的阻塞队列如果叫做同步队列（sync queue）其实比较贴切，不过为了和前篇呼应，我就继续使用阻塞队列了。记住这里的两个概念，阻塞队列和条件队列。

<img width="880" alt="截屏2023-12-06 11 07 27" src="https://github.com/Mein-Augenstern/MUYI/assets/34135120/56cabf2e-a2c9-4942-b0d3-fbcb53a477cc">

这里，我们简单回顾下 Node 的属性：

```java
volatile int waitStatus; // 可取值 0、CANCELLED(1)、SIGNAL(-1)、CONDITION(-2)、PROPAGATE(-3)
volatile Node prev;
volatile Node next;
volatile Thread thread;
Node nextWaiter;

prev 和 next 用于实现阻塞队列的双向链表，这里的 nextWaiter 用于实现条件队列的单向链表
```

基本上，把这张图看懂，你也就知道 condition 的处理流程了。所以，我先简单解释下这图，然后再具体地解释代码实现。

1、条件队列和阻塞队列的节点，都是 Node 的实例，因为条件队列的节点是需要转移到阻塞队列中去的；

2、我们知道一个 ReentrantLock 实例可以通过多次调用 newCondition() 来产生多个 Condition 实例，这里对应 condition1 和 condition2。注意，ConditionObject 只有两个属性 firstWaiter 和 lastWaiter；

3、每个 condition 有一个关联的条件队列，如线程 1 调用 condition1.await() 方法即可将当前线程 1 包装成 Node 后加入到条件队列中，然后阻塞在这里，不继续往下执行，条件队列是一个单向链表；

4、调用condition1.signal() 触发一次唤醒，此时唤醒的是队头，会将condition1 对应的条件队列的 firstWaiter（队头） 移到阻塞队列的队尾，等待获取锁，获取锁后 await 方法才能返回，继续往下执行。

上面的 2->3->4 描述了一个最简单的流程，没有考虑中断、signalAll、还有带有超时参数的 await 方法等，不过把这里弄懂是这节的主要目的。

同时，从图中也可以很直观地看出，哪些操作是线程安全的，哪些操作是线程不安全的。 这个图看懂后，下面的代码分析就简单了。接下来，我们一步步按照流程来走代码分析，我们先来看看 wait 方法：

```java
/**
 * 首先，这个方法是可被中断的，不可被中断的是另一个方法 awaitUninterruptibly()
 * 这个方法会阻塞，直到调用 signal 方法（指 signal() 和 signalAll()，下同），或被中断
 * 
 * Implements interruptible condition wait.
 * <ol>
 * <li> If current thread is interrupted, throw InterruptedException.
 * <li> Save lock state returned by {@link #getState}.
 * <li> Invoke {@link #release} with saved state as argument,
 *      throwing IllegalMonitorStateException if it fails.
 * <li> Block until signalled or interrupted.
 * <li> Reacquire by invoking specialized version of
 *      {@link #acquire} with saved state as argument.
 * <li> If interrupted while blocked in step 4, throw InterruptedException.
 * </ol>
 */
public final void await() throws InterruptedException {
    // 判断当前线程是否已中断
    if (Thread.interrupted())
        throw new InterruptedException();

    // 将当前线程添加到condition队列中
    Node node = addConditionWaiter();

    // 释放锁，返回值是释放锁之前的 state 值
    // await() 之前，当前线程是必须持有锁的，这里肯定要释放掉
    int savedState = fullyRelease(node);

    // 这里退出循环有两种情况，之后再仔细分析
    // 1. isOnSyncQueue(node) 返回 true，即当前 node 已经转移到阻塞队列了
    // 2. checkInterruptWhileWaiting(node) != 0 会到 break，然后退出循环，代表的是线程中断
    int interruptMode = 0;
    while (!isOnSyncQueue(node)) {
        LockSupport.park(this);
        if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
            break;
    }

    // 被唤醒后，将进入阻塞队列，等待获取锁
    if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
        interruptMode = REINTERRUPT;
    if (node.nextWaiter != null) // clean up if cancelled
        unlinkCancelledWaiters();
    if (interruptMode != 0)
        reportInterruptAfterWait(interruptMode);
}
```

### 将节点加入到条件队列

addConditionWaiter() 是将当前节点加入到条件队列，看图我们知道，这种条件队列内的操作是线程安全的。

```java
/**
 *
 * // 将当前线程对应的节点入队，插入队尾
 * 
 * Adds a new waiter to wait queue.
 * @return its new wait node
 */
private Node addConditionWaiter() {
    Node t = lastWaiter;

    // 如果条件队列的最后一个节点取消了，将其清除出去
    // 为什么这里把 waitStatus 不等于 Node.CONDITION，就判定为该节点发生了取消排队？
    // If lastWaiter is cancelled, clean out.
    if (t != null && t.waitStatus != Node.CONDITION) {
        // 清除队列中已经取消等待的节点
        unlinkCancelledWaiters();

        // 更新最后一个等待者的引用，因为unlinkCancelledWaiters可能会改变它
        t = lastWaiter;
    }

    // node 在初始化的时候，指定 waitStatus 为 Node.CONDITION
    Node node = new Node(Thread.currentThread(), Node.CONDITION);

    // t 此时是 lastWaiter，即t 代表着的是等待着，如果t==null，即意味着队列中没有等待着，也就是说队列为空。
    if (t == null)
        firstWaiter = node;
    // 更新当前节点的下一集节点指针
    else
        t.nextWaiter = node;

    // 更新尾节点
    lastWaiter = node;
    return node;
}
```

上面的这块代码很简单，就是将当前线程进入到条件队列的队尾。在addWaiter 方法中，有一个 unlinkCancelledWaiters() 方法，该方法用于清除队列中已经取消等待的节点。当 await 的时候如果发生了取消操作（这点之后会说），或者是在节点入队的时候，发现最后一个节点是被取消的，会调用一次这个方法。

**AQS-ConditionObject-unlinkCancelledWaiters**

```java
// 等待队列是一个单向链表，遍历链表将已经取消等待的节点清除出去
// 纯属链表操作，很好理解，看不懂多看几遍就可以了

// 注释翻译：该方法用于在条件队列中移除那些已经取消的等待节点。这个操作只会在获取到锁的情况下进行。
// 当条件等待过程中出现了取消行为，或者在向队列添加新的等待节点时发现最后一个等待节点已被取消，就会调用这个方法。
// 我们需要这个方法来防止在没有信号传递的情况下出现内存垃圾累积。
// 因而，尽管这个方法可能会导致对整个队列的遍历，但它只在特定情况下才会被使用，即在没有信号传递时发生了超时或取消操作。
// 此方法会遍历所有节点，而不是只停在某个特定节点上，这样做是为了一次性断开所有指向无用节点的连接，避免在发生大量取消操作时需要进行多次重复遍历。
private void unlinkCancelledWaiters() {

    Node t = firstWaiter;

    // 追踪变量，用于标记队列在每次循环后的最新有效节点
    Node trail = null;

    // 循环队列节点，当循环到队列尾部时，即节点=null 时，跳出循环
    while (t != null) {

        Node next = t.nextWaiter;

        // 如果节点的状态不是 Node.CONDITION 的话，这个节点就是被取消的
        if (t.waitStatus != Node.CONDITION) {
            t.nextWaiter = null;

            // 如果trail为null，意味着第一次循环的时候才会发生，意味着当前节点是队列的第一个节点
            if (trail == null)
                // 将队列的第一个节点设置为当前节点的下一个节点
                // or
                // 更新队列的第一个节点内容，为当前节点的下一个节点。
                firstWaiter = next;

            // 将trail节点指向当前节点的下一个节点，从而移除当前节点
            // 即将最新有效节点的下一个节点指针更新为当前节点的下一个节点，从而达到删除当前节点的目的
            else
                trail.nextWaiter = next;

            // 如果没有更多节点，确保更新lastWaiter指向正确的最后一个节点
            if (next == null)
                lastWaiter = trail;
        }

        // 当前节点未被取消，trail指向当前节点，作为下一个节点的前置节点
        else
            // 将有效的节点赋值给追踪变量
            trail = t;

        // 继续检查下一个节点
        t = next;
    }
}
```

## 完全释放独占锁

**AQS-ConditionObject-fullyRelease**

回到 wait 方法，节点入队了以后，会调用 int savedState = fullyRelease(node); 方法释放锁，注意，这里是完全释放独占锁（fully release），因为 ReentrantLock 是可以重入的。

> 考虑一下这里的 savedState。如果在 condition1.await() 之前，假设线程先执行了 2 次 lock() 操作，那么 state 为 2，我们理解为该线程持有 2 把锁，这里 await() 方法必须将 state 设置为 0，然后再进入挂起状态，这样其他线程才能持有锁。当它被唤醒的时候，它需要重新持有 2 把锁，才能继续下去。

```java

// 首先，我们要先观察到返回值 savedState 代表 release 之前的 state 值
// 对于最简单的操作：先 lock.lock()，然后 condition1.await()。
//         那么 state 经过这个方法由 1 变为 0，锁释放，此方法返回 1
//         相应的，如果 lock 重入了 n 次，savedState == n
// 如果这个方法失败，会将节点设置为"取消"状态，并抛出异常 IllegalMonitorStateException

/**
 * Invokes release with current state value; returns saved state.
 * Cancels node and throws exception on failure.
 * @param node the condition node for this wait
 * @return previous sync state
 */
final int fullyRelease(Node node) {
    boolean failed = true;
    try {
        // 获取当前线程重入锁的次数
        int savedState = getState();

        // 这里使用了当前的 state 作为 release 的参数，也就是完全释放掉锁，将 state 置为 0
        if (release(savedState)) {
            failed = false;
            return savedState;
        } else {
            throw new IllegalMonitorStateException();
        }
    } finally {
        if (failed)
            node.waitStatus = Node.CANCELLED;
    }
}
```

考虑一下，如果一个线程在不持有 lock 的基础上，就去调用 condition1.await() 方法，它能进入条件队列，但是在上面的这个方法中，由于它不持有锁，release(savedState) 这个方法肯定要返回 false，进入到异常分支，然后进入 finally 块设置 node.waitStatus = Node.CANCELLED，这个已经入队的节点之后会被后继的节点”请出去“。

## 等待进入阻塞队列

释放掉锁以后，接下来是这段，这边会自旋，如果发现自己还没到阻塞队列，那么挂起，等待被转移到阻塞队列。参照await方法

```java
int interruptMode = 0;
// 如果不在阻塞队列中，注意了，是阻塞队列
while (!isOnSyncQueue(node)) {
    // 线程挂起
    LockSupport.park(this);

    // 这里可以先不用看了，等看到它什么时候被 unpark 再说
    if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
        break;
}
```

### AQS-isOnSyncQueue

isOnSyncQueue(Node node) 用于判断节点是否已经转移到阻塞队列了：

```java
/**
 * Returns true if a node, always one that was initially placed on
 * a condition queue, is now waiting to reacquire on sync queue.
 * @param node the node
 * @return true if is reacquiring
 */
final boolean isOnSyncQueue(Node node) {

    // 移动过去的时候，node 的 waitStatus 会置为 0，这个之后在说 signal 方法的时候会说到
    // 如果 waitStatus 还是 Node.CONDITION，也就是 -2，那肯定就是还在条件队列中
    // 如果 node 的前驱 prev 指向还是 null，说明肯定没有在 阻塞队列(prev是阻塞队列链表中使用的)
    if (node.waitStatus == Node.CONDITION || node.prev == null)
        return false;

    // 如果 node 已经有后继节点 next 的时候，那肯定是在阻塞队列了
    if (node.next != null) // If has successor, it must be on queue
        return true;

    // 下面这个方法从阻塞队列的队尾开始从后往前遍历找，如果找到相等的，说明在阻塞队列，否则就是不在阻塞队列

    // 可以通过判断 node.prev() != null 来推断出 node 在阻塞队列吗？答案是：不能。
    // 这个可以看上面 AQS 中的入队方法，首先设置的是 node.prev 指向 tail，
    // 然后是 CAS 操作将自己设置为新的 tail，可是这次的 CAS 是可能失败的。

    /*
     * node.prev can be non-null, but not yet on queue because
     * the CAS to place it on queue can fail. So we have to
     * traverse from tail to make sure it actually made it.  It
     * will always be near the tail in calls to this method, and
     * unless the CAS failed (which is unlikely), it will be
     * there, so we hardly ever traverse much.
     */
    return findNodeFromTail(node);
}
```

### AQS-findNodeFromTail

```java
/**
 *
 * 从阻塞队列的队尾往前遍历，如果找到，返回 true
 *
 * Returns true if node is on sync queue by searching backwards from tail.
 * Called only when needed by isOnSyncQueue.
 * @return true if present
 */
private boolean findNodeFromTail(Node node) {
    Node t = tail;
    for (;;) {
        if (t == node)
            return true;
        if (t == null)
            return false;
        t = t.prev;
    }
}
```

回到小节：等待进入阻塞队列中的while循环，isOnSyncQueue(node) 返回 false 的话，那么进到 LockSupport.park(this); 这里线程挂起。

## signal 唤醒线程，转移到阻塞队列

为了大家理解，这里我们先看唤醒操作，因为刚刚到 LockSupport.park(this); 把线程挂起了，等待唤醒。

唤醒操作通常由另一个线程来操作，就像生产者-消费者模式中，如果线程因为等待消费而挂起，那么当生产者生产了一个东西后，会调用 signal 唤醒正在等待的线程来消费。

### AQS-ConditionObject-signal

```java
// 唤醒等待了最久的线程
// 其实就是，将这个线程对应的 node 从条件队列转移到阻塞队列
public final void signal() {
    // 调用 signal 方法的线程必须持有当前的独占锁
    if (!isHeldExclusively())
        throw new IllegalMonitorStateException();
    Node first = firstWaiter;
    if (first != null)
        doSignal(first);
}
```

### AQS-ConditionObject-doSignal

```java
// 从条件队列队头往后遍历，找出第一个需要转移的 node
// 因为前面我们说过，有些线程会取消排队，但是可能还在队列中
private void doSignal(Node first) {
    do {
      	// 将 firstWaiter 指向 first 节点后面的第一个，因为 first 节点马上要离开了
        // 如果将 first 移除后，后面没有节点在等待了，那么需要将 lastWaiter 置为 null
        if ( (firstWaiter = first.nextWaiter) == null)
            lastWaiter = null;
        // 因为 first 马上要被移到阻塞队列了，和条件队列的链接关系在这里断掉
        first.nextWaiter = null;
    } while (!transferForSignal(first) &&
             (first = firstWaiter) != null);
      // 这里 while 循环，如果 first 转移不成功，那么选择 first 后面的第一个节点进行转移，依此类推
}
```

### AQS-transferForsignal

```java
// 将节点从条件队列转移到阻塞队列
// true 代表成功转移
// false 代表在 signal 之前，节点已经取消了
final boolean transferForSignal(Node node) {
    
    // CAS 如果失败，说明此 node 的 waitStatus 已不是 Node.CONDITION，说明节点已经取消，
    // 既然已经取消，也就不需要转移了，方法返回，转移后面一个节点
    // 否则，将 waitStatus 置为 0
    if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
        return false;
  
    // enq(node): 自旋进入阻塞队列的队尾
    // 注意，这里的返回值 p 是 node 在阻塞队列的前驱节点
    Node p = enq(node);
    int ws = p.waitStatus;
    // ws > 0 说明 node 在阻塞队列中的前驱节点取消了等待锁，直接唤醒 node 对应的线程。唤醒之后会怎么样，后面再解释
    // 如果 ws <= 0, 那么 compareAndSetWaitStatus 将会被调用，上篇介绍的时候说过，节点入队后，需要把前驱节点的状态设为 Node.SIGNAL(-1)
    if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
        // 如果前驱节点取消或者 CAS 失败，会进到这里唤醒线程，之后的操作看下一节
        LockSupport.unpark(node.thread);
    return true;
}
```

正常情况下，ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL) 这句中，ws <= 0，而且 compareAndSetWaitStatus(p, ws, Node.SIGNAL) 会返回 true，所以一般也不会进去 if 语句块中唤醒 node 对应的线程。然后这个方法返回 true，也就意味着 signal 方法结束了，节点进入了阻塞队列。

假设发生了阻塞队列中的前驱节点取消等待，或者 CAS 失败，只要唤醒线程，让其进到下一步即可。



