## 看在前面

详见：https://javadoop.com/post/AbstractQueuedSynchronizer-3

## Semaphore

有了 CountDownLatch 的基础后，分析 Semaphore 会简单很多。Semaphore 是什么呢？它类似一个资源池（读者可以类比线程池），每个线程需要调用 acquire() 方法获取资源，然后才能执行，执行完后，需要 release 资源，让给其他的线程用。

大概大家也可以猜到，Semaphore 其实也是 AQS 中共享锁的使用，因为每个线程共享一个池嘛。

套路解读：创建 Semaphore 实例的时候，需要一个参数 permits，这个基本上可以确定是设置给 AQS 的 state 的，然后每个线程调用 acquire 的时候，执行 state = state - 1，release 的时候执行 state = state + 1，当然，acquire  的时候，如果 state = 0，说明没有资源了，需要等待其他线程 release。

构造方法：

```java
public Semaphore(int permits) {
    sync = new NonfairSync(permits);
}

public Semaphore(int permits, boolean fair) {
    sync = fair ? new FairSync(permits) : new NonfairSync(permits);
}
```
这里和 ReentrantLock 类似，用了公平策略和非公平策略。

看 acquire 方法：

```java
public void acquire() throws InterruptedException {
    sync.acquireSharedInterruptibly(1);
}
public void acquireUninterruptibly() {
    sync.acquireShared(1);
}
public void acquire(int permits) throws InterruptedException {
    if (permits < 0) throw new IllegalArgumentException();
    sync.acquireSharedInterruptibly(permits);
}
public void acquireUninterruptibly(int permits) {
    if (permits < 0) throw new IllegalArgumentException();
    sync.acquireShared(permits);
}
```
这几个方法也是老套路了，大家基本都懂了吧，这边多了两个可以传参的 acquire 方法，不过大家也都懂的吧，如果我们需要一次获取超过一个的资源，会用得着这个的。

我们接下来看不抛出 InterruptedException 异常的 acquireUninterruptibly() 方法吧：

```java
public void acquireUninterruptibly() {
    sync.acquireShared(1);
}
public final void acquireShared(int arg) {
    if (tryAcquireShared(arg) < 0)
        doAcquireShared(arg);
}
```

前面说了，Semaphore 分公平策略和非公平策略，我们对比一下两个 tryAcquireShared 方法：

```java
// 公平策略：
protected int tryAcquireShared(int acquires) {
    for (;;) {
        // 区别就在于是不是会先判断是否有线程在排队，然后才进行 CAS 减操作
        if (hasQueuedPredecessors())
            return -1;
        int available = getState();
        int remaining = available - acquires;
        if (remaining < 0 ||
            compareAndSetState(available, remaining))
            return remaining;
    }
}
// 非公平策略：
protected int tryAcquireShared(int acquires) {
    return nonfairTryAcquireShared(acquires);
}
final int nonfairTryAcquireShared(int acquires) {
    for (;;) {
        int available = getState();
        int remaining = available - acquires;
        if (remaining < 0 ||
            compareAndSetState(available, remaining))
            return remaining;
    }
}
```
也是老套路了，所以从源码分析角度的话，我们其实不太需要关心是不是公平策略还是非公平策略，它们的区别往往就那么一两行。

我们再回到 acquireShared 方法，

```java
public final void acquireShared(int arg) {
    if (tryAcquireShared(arg) < 0)
        doAcquireShared(arg);
}
```
由于 tryAcquireShared(arg) 返回小于 0 的时候，说明 state 已经小于 0 了（没资源了），此时 acquire 不能立马拿到资源，需要进入到阻塞队列等待，虽然贴了很多代码，不在乎多这点了：

```java
private void doAcquireShared(int arg) {
    final Node node = addWaiter(Node.SHARED);
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                int r = tryAcquireShared(arg);
                if (r >= 0) {
                    setHeadAndPropagate(node, r);
                    p.next = null; // help GC
                    if (interrupted)
                        selfInterrupt();
                    failed = false;
                    return;
                }
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

这个方法我就不介绍了，线程挂起后等待有资源被 release 出来。接下来，我们就要看 release 的方法了：

```java
// 任务介绍，释放一个资源
public void release() {
    sync.releaseShared(1);
}
public final boolean releaseShared(int arg) {
    if (tryReleaseShared(arg)) {
        doReleaseShared();
        return true;
    }
    return false;
}

protected final boolean tryReleaseShared(int releases) {
    for (;;) {
        int current = getState();
        int next = current + releases;
        // 溢出，当然，我们一般也不会用这么大的数
        if (next < current) // overflow
            throw new Error("Maximum permit count exceeded");
        if (compareAndSetState(current, next))
            return true;
    }
}
```
tryReleaseShared 方法总是会返回 true，然后是 doReleaseShared，这个也是我们熟悉的方法了，我就贴下代码，不分析了，这个方法用于唤醒所有的等待线程：

```java
private void doReleaseShared() {
    for (;;) {
        Node h = head;
        if (h != null && h != tail) {
            int ws = h.waitStatus;
            if (ws == Node.SIGNAL) {
                if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                    continue;            // loop to recheck cases
                unparkSuccessor(h);
            }
            else if (ws == 0 &&
                     !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                continue;                // loop on failed CAS
        }
        if (h == head)                   // loop if head changed
            break;
    }
}
```

Semphore 的源码确实很简单，基本上都是分析过的老代码的组合使用了。

-------

## 一句话描述

<a href="https://blog.csdn.net/qq_19431333/article/details/70212663">深入理解Semaphore</a>：允许多个线程同时访问

## 代码举栗说明

synchronized 和 ReentrantLock 都是一次只允许一个线程访问某个资源，Semaphore(信号量)可以指定多个线程同时访问某个资源。 示例代码如下：

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class SemaphoreExample1 {

	// 请求的数量
	private static final int threadCount = 550;

	public static void main(String[] args) {
		// 创建一个具有固定线程数量的线程池对象（如果这里线程池的线程数量给太少的话你会发现执行的很慢）
		ExecutorService threadPool = Executors.newFixedThreadPool(300);
		// 一次只能允许执行的线程数量
		final Semaphore semaphore = new Semaphore(20);
		for (int i = 0; i < threadCount; i++) {
			final int threadNum = i;
			threadPool.execute(() -> {
				try {
					semaphore.acquire();
					test(threadNum);
					semaphore.release();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			});
		}

		threadPool.shutdown();
		System.out.println("finish");
	}

	public static void test(int threadNum) throws InterruptedException {
		// 模拟请求的耗时操作
		Thread.sleep(1000);
		System.out.println("threadNum:" + threadNum);
		// 模拟请求的耗时操作
		Thread.sleep(1000);
	}

}
```

执行 ```acquire``` 方法阻塞，直到有一个许可证可以获得然后拿走一个许可证；每个 ```release``` 方法增加一个许可证，这可能会释放一个阻塞的 ```acquire``` 方法。然而，其实并没有实际的许可证这个对象，```Semaphore``` 只是维持了一个可获得许可证的数量。 ```Semaphore``` 经常用于限制获取某种资源的线程数量。

当然一次也可以一次拿取和释放多个许可，不过一般没有必要这样做：

```java
semaphore.acquire(5);// 获取5个许可，所以可运行线程数量为20/5=4
test(threadnum);
semaphore.release(5);// 获取5个许可，所以可运行线程数量为20/5=4
```

除了 ```acquire```方法之外，另一个比较常用的与之对应的方法是```tryAcquire```方法，该方法如果获取不到许可就立即返回 false。

Semaphore 有两种模式，公平模式和非公平模式。

* **公平模式：** 调用 acquire 的顺序就是获取许可证的顺序，遵循 FIFO；
* **非公平模式：** 抢占式的。

**Semaphore 对应的两个构造方法如下：**

```java
public Semaphore(int permits) {
    sync = new NonfairSync(permits);
}

public Semaphore(int permits, boolean fair) {
    sync = fair ? new FairSync(permits) : new NonfairSync(permits);
}
```

**这两个构造方法，都必须提供许可的数量，第二个构造方法可以指定是公平模式还是非公平模式，默认非公平模式。**
