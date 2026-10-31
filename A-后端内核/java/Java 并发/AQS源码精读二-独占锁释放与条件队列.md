AQS 源码精读（二）：独占锁释放与条件队列
====

本篇接 [AQS 源码精读（一）：加锁与队列](./AQS源码精读一-加锁与队列.md)，把独占模式剩下的一半讲完：**锁怎么释放、Condition 条件队列怎么和阻塞队列联动、中断在这中间扮演什么角色**。一句话定位：Condition 是 Object.wait/notify 的升级版——await 把节点从阻塞队列搬进条件队列挂起，signal 再把它搬回阻塞队列排队抢锁，全程不丢失锁的重入计数。面试里"Condition 和 wait/notify 的区别""await 期间被中断会怎样"是区分背题者和读过源码者的分水岭。加锁路径见第一篇，共享模式见 [AQS 源码精读（三）：共享模式与工具类基座](./AQS源码精读三-共享模式与工具类基座.md)，基础铺垫见 [AQS&ReentrantLock](./AQS%26ReentrantLock.md)。

1 公平锁与非公平锁：差异只有几行
------

ReentrantLock 默认非公平，`new ReentrantLock(true)` 才是公平。区别两处：

```java
// NonfairSync.lock()：上来先抢一把
final void lock() {
    if (compareAndSetState(0, 1))
        setExclusiveOwnerThread(Thread.currentThread());
    else
        acquire(1);
}
```

非公平锁的 lock() 先直接 CAS 抢锁，成功即返回；失败进 acquire 后，其 tryAcquire（nonfairTryAcquire）在 state==0 时**不检查队列直接 CAS**。公平锁这两处都让路：state==0 时必须先过 `hasQueuedPredecessors()`。 Doug Lea 的取舍是：非公平锁吞吐更好（刚释放的锁被新来的线程顺手拿走，省一次线程挂起/唤醒），代价是队列中的线程可能长期饥饿。两者源码的区别"往往就那么一两行"，但语义完全不同。

2 解锁：tryRelease 减到 0 才算完
------

```java
public void unlock() {
    sync.release(1);
}

public final boolean release(int arg) {
    if (tryRelease(arg)) {
        Node h = head;
        if (h != null && h.waitStatus != 0)
            unparkSuccessor(h);
        return true;
    }
    return false;
}

protected final boolean tryRelease(int releases) {
    int c = getState() - releases;
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();
    boolean free = false;
    if (c == 0) {
        free = true;
        setExclusiveOwnerThread(null);
    }
    setState(c);
    return free;
}
```

要点：解锁不走 CAS——能走到这里的线程必然持锁，无竞争，直接 setState。可重入锁要减到 `c == 0` 才算完全释放（lock 几次就要 unlock 几次），也只有完全释放才返回 true 去唤醒后继。release 里 `h.waitStatus != 0` 说明 head 后面还有等待者（后继入队时会把 head 改成 SIGNAL），调 unparkSuccessor：

```java
private void unparkSuccessor(Node node) {
    int ws = node.waitStatus;
    if (ws < 0)
        compareAndSetWaitStatus(node, ws, 0);
    Node s = node.next;
    if (s == null || s.waitStatus > 0) {
        s = null;
        for (Node t = tail; t != null && t != node; t = t.prev)
            if (t.waitStatus <= 0)
                s = t;
    }
    if (s != null)
        LockSupport.unpark(s.thread);
}
```

后继可能已取消（waitStatus>0）或 next 为 null（入队时 next 回填失败），所以**从 tail 往前遍历**找最靠前的有效节点唤醒——反向遍历不会漏：取消节点的 prev 仍指向有效前驱，而 next 链可能断。这就是"加锁与队列"篇里 prev 先行设置的好处兑现。

3 Condition：两个队列，一个 Node
------

使用 Condition 必须先持有对应的锁（语义同 wait/notify 要持监视器锁），每个 ReentrantLock 可以 `newCondition()` 出多个条件：

```java
final Lock lock = new ReentrantLock();
final Condition notFull  = lock.newCondition();
final Condition notEmpty = lock.newCondition();
// put: while (count == items.length) notFull.await(); ... notEmpty.signal();
// take: while (count == 0) notEmpty.await(); ... notFull.signal();
```

ConditionObject 只有两个属性：`transient Node firstWaiter` 和 `transient Node lastWaiter`。核心是有**两条队列**：

```
阻塞队列（sync queue）：双向，prev/next，抢锁排队
     head ←prev/next→ [node] ←prev/next→ tail

条件队列（condition queue）：单向，nextWaiter，await 等条件
     firstWaiter → [node(CONDITION)] → lastWaiter
```

节点都是同一个 Node，靠 waitStatus==CONDITION 和所处队列区分。await 的完整流程：把当前线程包装成 CONDITION 节点加入条件队列 → **完全释放**锁（含全部重入次数）→ park 直到 signal 把节点**转移**到阻塞队列尾部 → 以原重入次数重新 acquireQueued 抢锁 → 处理中断 → 返回。

4 await 的七步拆解
------

```java
public final void await() throws InterruptedException {
    if (Thread.interrupted())
        throw new InterruptedException();
    Node node = addConditionWaiter();               // 1 入条件队列
    int savedState = fullyRelease(node);            // 2 完全释放锁
    while (!isOnSyncQueue(node)) {                  // 3 挂起等转移
        LockSupport.park(this);
    }
    if (acquireQueued(node, savedState)             // 4 回阻塞队列抢锁
        && interruptMode != THROW_IE)
        interruptMode = REINTERRUPT;
    ...
    if (interruptMode != 0)
        reportInterruptAfterWait(interruptMode);    // 5/6/7 处理中断
}
```

逐步看关键实现：

* **addConditionWaiter**：尾节点已不是 CONDITION 时先 `unlinkCancelledWaiters()` 清理，再 `new Node(Thread.currentThread(), Node.CONDITION)` 挂队尾；
* **fullyRelease**：`int savedState = getState(); if (release(savedState)) return savedState;`——把重入次数整个存下来一次释放。不持锁就调 await 的线程在这里吃到 IllegalMonitorStateException，节点被标 CANCELLED 请出条件队列；
* **isOnSyncQueue**：节点还带 CONDITION 或 prev==null 就不在阻塞队列；next!=null 一定在；否则从 tail 反向找。prev!=null 不代表已入队——CAS 设 tail 可能失败，入队发生在 CAS tail 成功的瞬间。

唤醒的线程回到 while 判断：已在阻塞队列就退出挂起循环，用 savedState 把当年锁了几层一层不少地抢回来。

5 signal：搬运工 transferForSignal
------

```java
public final void signal() {
    if (!isHeldExclusively())
        throw new IllegalMonitorStateException();
    Node first = firstWaiter;
    if (first != null)
        doSignal(first);
}
```

signal 要求当前线程持锁。doSignal 从队头找第一个可转移节点交给 transferForSignal：

```java
final boolean transferForSignal(Node node) {
    if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
        return false;                    // 已取消，换下一个
    Node p = enq(node);                  // 入阻塞队列队尾
    int ws = p.waitStatus;
    if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
        LockSupport.unpark(node.thread); // 前驱取消或设置失败，直接唤醒自保
    return true;
}
```

signal 只是"转移"：把节点从条件队列搬到阻塞队列队尾、让前驱记得唤醒它。被 signal 的线程要重新排队抢锁，不是立刻恢复执行。signalAll 就是 doSignal 循环搬到条件队列清空为止。

6 中断的三种时序：THROW_IE / REINTERRUPT / 0
------

await 挂起期间可能被中断而非 signal 唤醒，处理分三种：

```java
private int checkInterruptWhileWaiting(Node node) {
    return Thread.interrupted() ?
        (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) : 0;
}
```

transferAfterCancelledWait 用 CAS 把 CONDITION 改 0：**改成功说明 signal 还没来**（signal 前被中断），节点由本线程自己 enq 转移，最终抛 InterruptedException（THROW_IE）；改失败说明 signal 已发生（signal 后被中断），自旋 `Thread.yield()` 等 signal 线程完成转移，最终只 `selfInterrupt()` 补回中断状态（REINTERRUPT）。无论哪种，节点都会进阻塞队列——中断不会让节点丢在条件队列里。reportInterruptAfterWait 收尾：

```java
if (interruptMode == THROW_IE) throw new InterruptedException();
else if (interruptMode == REINTERRUPT) selfInterrupt();
```

两个变体：带超时的 await 用 parkNanos，剩余时间小于 `spinForTimeoutThreshold`（1000 纳秒）时自旋比挂起更划算；超时后自己调 transferAfterCancelledWait 走取消转移。awaitUninterruptibly 干脆不抛异常，全程只记标志，最后 `if (acquireQueued(node, savedState) || interrupted) selfInterrupt();` 把中断补回来。

7 取消排队与 lockInterruptibly
------

lock() 不响应中断：park 被中断唤醒后 parkAndCheckInterrupt 只是记下标志，回到 acquireQueued 继续抢锁，抢到后 selfInterrupt() 恢复中断状态，不抛任何异常。lockInterruptibly() 走 doAcquireInterruptibly：检测到中断立刻 `throw new InterruptedException()`，failed=true 触发 cancelAcquire——置 waitStatus=CANCELLED、跳过已取消前驱、队尾则 CAS 摘除自己，否则把前驱与后继拼接或 unparkSuccessor，最后 `node.next = node; // help GC`。

最后把线程中断本身说透（详见 [java 线程中断和 InterruptedException 异常](./java%20线程中断和%20InterruptedException%20异常.md)）：中断只是状态位，不是 kill。`isInterrupted()` 读不清零，静态 `interrupted()` 读**并重置为 false**，`interrupt()` 置位。三类阻塞会自动感知中断：wait/join/sleep（抛 InterruptedException 且重置状态）、InterruptibleChannel 的 IO（抛 ClosedByInterruptException）、Selector.select()（立即返回）；park 会被中断唤醒但不动状态位。实践原则：遇到 InterruptedException 要么继续抛，要么 `Thread.currentThread().interrupt()` 恢复状态，**记录中断而不是丢失这个信息**。

面试追问链
------

* unlock 要走 CAS 吗？→ 不用，持锁线程独占改 state，无竞争；
* 重入锁释放的判定？→ state 减到 0 才算完全释放，才触发唤醒后继；
* unparkSuccessor 为什么从 tail 反向遍历？→ next 链可能因取消/回填失败而断，prev 链始终可靠；
* 公平/非公平差在哪？→ 非公平 lock() 先 CAS 一次、tryAcquire 不查 hasQueuedPredecessors；吞吐高但可能饥饿；
* Condition 和 wait/notify 区别？→ 一把锁多个条件队列、可中断/可超时/可不抛异常的 await、公平锁下按 FIFO 唤醒；
* await 怎么处理重入？→ fullyRelease 保存 savedState 一次性释放，恢复时以 savedState 重新 acquireQueued；
* signal 后线程马上运行吗？→ 不，节点转移到阻塞队列队尾重新排队抢锁；
* signal 前被中断和 signal 后被中断区别？→ THROW_IE 抛异常 vs REINTERRUPT 补中断位，判据是 CAS CONDITION→0 是否成功；
* 不持锁调 await/signal？→ IllegalMonitorStateException。

面试官视角小结
------

Condition 这道题，说得出"两个队列一个 Node、signal 是转移不是执行"已经不错；能讲清 savedState 保重入、transferAfterCancelledWait 用 CAS 区分中断时序，才是源码级掌握。公平/非公平的追问则是送分变送命题——差异行数少，但要说得出"非公平省的是刚唤醒线程的上下文切换"这层性能动机。中断话题最后收束到工程习惯：吞掉 InterruptedException 是常见坏味道。系列收尾见 [AQS 源码精读（三）：共享模式与工具类基座](./AQS源码精读三-共享模式与工具类基座.md)。

> 来源：javadoop《一行一行源码分析清楚 AbstractQueuedSynchronizer (二)》（https://javadoop.com/post/AbstractQueuedSynchronizer-2）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
