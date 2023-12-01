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

## 二、AQS源码阅读

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
    // 在AQS（AbstractQueuedSynchronizer）中，static final int CANCELLED = 1; 表示一个常量值，用于指示一个节点的等待状态（waitStatus）已经被取消。在AQS的上下文中，节点通常代表一个正在等待获取锁或者条件的线程。
    // 当一个线程在同步队列中等待时，由于某些原因（比如超时或者中断），它可能决定不再等待资源的分配，此时它会被赋予一个“已取消”（CANCELLED）的状态。用于标示这个状态的整数值就是1。
    // 这个值用来告诉AQS和与之相关的同步组件，这个特定的节点不应该再被考虑为正常等待资源的线程，应该从同步队列中移除。这对于资源的管理和线程的调度很重要，确保系统能够正确响应线程的取消操作，并保持同步队列的秩序和性能。
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
    // SIGNAL: 表示此节点的后继节点正在被阻塞（通过park），或将很快被阻塞，因此当前节点在释放或取消时必须唤醒其后继节点。为了避免竞争，获取方法必须首先表明它们需要一个信号，然后重试原子性获取，如果失败，再进行阻塞。
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
    // 指向前驱节点的链接，当前节点/线程依赖它来检查 waitStatus。在入队时分配，并且只有在出队时才置为 null（为了垃圾回收）。同时，当一个前驱节点被取消时，我们会快速寻找一个未被取消的节点，这样的节点总是存在的，
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
     // 因为条件队列只在持有独占模式时才被访问，我们只需要一个简单的链表队列来保存在等待条件时的节点。之后，这些节点会被转移到队列中以重新获取资源。并且由于条件只能是独占的，我们通过使用特殊值来指示共享模式来节省一个字段。
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
