ReentrantReadWriteLock 源码分析
====

ReentrantReadWriteLock 是 AQS 模板方法的教科书级应用：**读锁用共享模式、写锁用独占模式，两者共享同一个 state**。一句话定位：读多写少的场景下替代 synchronized/ReentrantLock，让读线程之间不再互斥。面试里"一个 int 怎么同时装两把锁""锁降级是什么、锁升级为什么会死锁"是必问三连。前置知识：AQS 独占模式见 [AQS 源码精读（一）：加锁与队列](./AQS源码精读一-加锁与队列.md)、共享模式见 [AQS 源码精读（三）：共享模式与工具类基座](./AQS源码精读三-共享模式与工具类基座.md)。

1 使用示例与锁降级
------

javadoc 的经典缓存写法（CachedData 模式）：

```java
rwl.readLock().lock();
if (!cacheValid) {
    rwl.readLock().unlock();      // 必须先释放读锁！
    rwl.writeLock().lock();       // 再取写锁
    try {
        if (!cacheValid) {        // 双重检查：等写锁期间可能已被别人更新
            data = load();
            cacheValid = true;
        }
        rwl.readLock().lock();    // 持写锁时再取读锁：锁降级
    } finally {
        rwl.writeLock().unlock(); // 释放写锁，此时仍持读锁
    }
}
try { use(data); } finally { rwl.readLock().unlock(); }
```

两条铁律藏在里面：**持写锁的线程可以再取读锁（降级），持读锁的线程不能取写锁（升级）**——升级会让线程在阻塞队列里等一个"必须由自己释放读锁后才能拿到"的写锁，自己休眠自己，而且可能永远没人唤醒，等于死锁。所以换锁的标准动作是先 unlock 读再 lock 写。

2 一个 state 拆两半：高 16 位读、低 16 位写
------

ReadLock / WriteLock 的方法全部委托给内部类 Sync（AQS 子类），再派生 FairSync / NonfairSync。核心设计是把 32 位 state 拆开：

```java
static final int SHARED_SHIFT   = 16;
static final int SHARED_UNIT    = (1 << SHARED_SHIFT);
static final int MAX_COUNT      = (1 << SHARED_SHIFT) - 1;
static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;

static int sharedCount(int c)    { return c >>> SHARED_SHIFT; }  // 高16位：读锁获取次数(含重入)
static int exclusiveCount(int c) { return c & EXCLUSIVE_MASK; }  // 低16位：写锁重入次数
```

读锁是共享锁，获取一次 state 加 SHARED_UNIT（高 16 位 +1）；写锁独占，低 16 位即重入计数。各 65535 上限。问题随之而来：**state 里只有总数，怎么知道每个线程各自重入了几次读锁？** 释放时不知道自己加了几层就没法正确减。答案是一组专为读锁性能服务的字段：

* `HoldCounter`（count + 线程 tid）：记录单线程读锁持有数，由 `readHolds`（ThreadLocalHoldCounter，ThreadLocal 子类）为每个线程存一份；
* `cachedHoldCounter`：缓存**最后一个**成功获取读锁线程的 HoldCounter——依据是"读锁的获取很快就会伴随着释放"，绝大多数释放都能命中缓存，省一次 ThreadLocal map 查询；
* `firstReader` / `firstReaderHoldCount`：第一个获取读锁且未释放的线程单独记录（它不是全局概念，释放后位置会被后来者占据），无竞争场景连 ThreadLocal 都不用碰。

这几个字段是初学者最头疼的地方，本质全部是为了规避 ThreadLocal 检索开销。另外 Sync 构造器里那句 `setState(getState())`，注释是 "ensures visibility of readHolds"——利用 volatile 写保证 readHolds 的内存可见性。

3 读锁获取：tryAcquireShared 与 fullTryAcquireShared
------

`readLock.lock()` → `sync.acquireShared(1)`，快速路径：

```java
protected final int tryAcquireShared(int unused) {
    for (;;) {
        int c = getState();
        if (exclusiveCount(c) != 0 &&
            getExclusiveOwnerThread() != current)
            return -1;                       // 写锁被别人持有，排队
        int r = sharedCount(c);
        if (!readerShouldBlock() &&
            r < MAX_COUNT &&
            compareAndSetState(c, c + SHARED_UNIT)) {
            // CAS 成功：r==0 设 firstReader；是 firstReader 则 count++；
            // 否则维护 cachedHoldCounter / readHolds，rh.count++
            return 1;
        }
    }
}
```

CAS 竞争失败不甘心的、读锁重入的（重入必须放行，否则自己阻塞自己）、被 readerShouldBlock 拦下的，全部进入 `fullTryAcquireShared(current)` 自旋重试，逻辑同上但更有耐心。注意一个细节：fullTryAcquireShared 里若 `readHolds.get()` 发现 count==0 会顺手 `remove()`，防 ThreadLocal 内存泄漏——读这段代码对理解其他分支很有帮助。

`readerShouldBlock()` 的两个实现是公平性的落点：

* **公平 FairSync**：`hasQueuedPredecessors()`——有人在排队，新来的读线程不许插队；
* **非公平 NonfairSync**：`apparentlyFirstQueuedIsExclusive()`——只有当阻塞队列 head 的第一个后继节点是来取**写锁**的，读线程才让行，**避免写锁饥饿**（Doug Lea 实际上给了写锁更高的优先级）；否则照抢。

4 读锁释放：tryReleaseShared
------

```java
protected final boolean tryReleaseShared(int unused) {
    // 当前线程是 firstReader：count==1 则 firstReader=null，否则 count--
    // 否则取 cachedHoldCounter（不匹配再 readHolds.get()）
    //   count<=1 时 readHolds.remove() 防泄漏；count<=0 抛 unmatchedUnlockException()
    for (;;) {
        int c = getState();
        int nextc = c - SHARED_UNIT;
        if (compareAndSetState(c, nextc))
            return nextc == 0;      // 读写锁全部归零才返回 true
    }
}
```

释放也要 CAS——读锁可能多个线程同时释放，state 是共享的。count<=0 还 unlock 的会抛 unmatchedUnlockException()，即"lock() 一次、unlock() 好几次"的场景。`nextc == 0` 返回 true 后，AQS 的 releaseShared 调 doReleaseShared **唤醒队首等写锁的线程**（共享模式传播机制的复用，见第三篇）。

5 写锁获取与释放：比读锁简单得多
------

`writeLock.lock()` → `acquire(1)` → `tryAcquire`：

```java
protected final boolean tryAcquire(int acquires) {
    int c = getState();
    int w = exclusiveCount(c);
    if (c != 0) {
        // w==0（只有读锁，含自己持有的）或持写锁的是别人 → 排队
        // 否则是写锁重入：setState(c + acquires)，无需 CAS
    }
    if (writerShouldBlock() || !compareAndSetState(c, c + acquires))
        return false;
    setExclusiveOwnerThread(current);
    return true;
}
```

`writerShouldBlock()`：NonfairSync 恒 false（注释原话 "writers can always barge"，写锁随时可抢）；FairSync 用 hasQueuedPredecessors()。注意 w==0 直接 false 就是**锁升级被禁止的源码位置**。释放走独占的 tryRelease：`state - releases` 后 `exclusiveCount(nextc)==0` 才算完全释放，清 exclusiveOwnerThread 并 unparkSuccessor 唤醒后继。相比读锁那套 ThreadCounter，写锁就是标准 ReentrantLock 的路数。

6 小结：一张表收拢读写锁
------

| 维度 | 读锁 | 写锁 |
| ---- | ---- | ---- |
| AQS 模式 | 共享 acquireShared | 独占 acquire |
| state 位置 | 高 16 位（+SHARED_UNIT） | 低 16 位（+1） |
| 计数载体 | state 总数 + HoldCounter/readHolds 按线程记 | state 低 16 位即重入数 |
| 非公平让行 | head 后继是写线程则让（防写饥饿） | 恒不让（barge） |
| 互相嵌套 | 可被写锁持有者获取（降级） | 不可被读锁持有者获取（升级死锁） |

面试追问链
------

* 读写锁的 state 怎么设计？→ 高 16 位读、低 16 位写，sharedCount/exclusiveCount 位运算拆分；
* 每线程读锁重入数存哪？→ HoldCounter 经 readHolds(ThreadLocal) 按线程存，cachedHoldCounter 缓存最后获取者，firstRecord 记第一个；
* 读锁获取的完整链路？→ lock → acquireShared(1) → tryAcquireShared 快速路径，失败 fullTryAcquireShared 自旋，再不行 doAcquireShared 排队；
* 非公平模式读线程什么时候让？→ apparentlyFirstQueuedIsExclusive()，队首在等写锁时让行防写饥饿；
* 读锁释放为什么用 CAS 而写锁不用？→ 读锁多线程并发释放改同一个 state；写锁独占无竞争；
* 锁降级怎么做？→ 持写锁时直接 readLock.lock()，再释放写锁；典型场景：写完立即可读且不想被别的写线程插队；
* 锁升级为什么死锁？→ tryAcquire 见 w==0 返回 false，线程进队列等自己释放读锁，无人能唤醒；
* 写锁饥饿怎么防？→ 非公平下读线程见队列首是写请求即让行。

面试官视角小结
------

读写锁是 AQS 的综合卷：一道题同时考共享/独占两种模式、位运算状态设计、ThreadLocal 应用和死锁推演。能画出一个 int 的高低 16 位、说出 cachedHoldCounter 存在的理由（读锁快取快放），基本就到"读过源码"档；能自己推出"读锁持有者去取写锁会永久挂起"这条链，才到"理解设计"档。反向加分项：知道非公平模式其实偏袒写锁、以及为什么（读线程一多写请求容易被无限插队）。综合复习回 [AQS 源码精读（三）：共享模式与工具类基座](./AQS源码精读三-共享模式与工具类基座.md)。

> 来源：javadoop《Java 读写锁 ReentrantReadWriteLock 源码分析》（https://javadoop.com/post/reentrant-read-write-lock）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
