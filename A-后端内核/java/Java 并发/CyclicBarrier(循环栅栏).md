## 一句话描述

下面内容转载自：https://javadoop.com/post/AbstractQueuedSynchronizer-3 。还有一部分是从其他地方整理过来的

## CyclicBarrier

字面意思是“可重复使用的栅栏”或“周期性的栅栏”，总之不是用了一次就没用了的，CyclicBarrier 相比 CountDownLatch 来说，要简单很多，其源码没有什么高深的地方，它是 ReentrantLock 和 Condition 的组合使用。看如下示意图，CyclicBarrier 和 CountDownLatch 是不是很像，只是 CyclicBarrier 可以有不止一个栅栏，因为它的栅栏（Barrier）可以重复使用（Cyclic）。

![image](https://github.com/Mein-Augenstern/MUYI/assets/34135120/a4409e64-7755-4b41-bcfc-507aec8b52f6)

首先，CyclicBarrier 的源码实现和 CountDownLatch 大相径庭，CountDownLatch 基于 AQS 的共享模式的使用，而 CyclicBarrier 基于 Condition 来实现。

因为 CyclicBarrier 的源码相对来说简单许多，读者只要熟悉了前面关于 Condition 的分析，那么这里的源码是毫无压力的，就是几个特殊概念罢了。

先用一张图来描绘下 CyclicBarrier 里面的一些概念，和它的基本使用流程：

![image](https://github.com/Mein-Augenstern/MUYI/assets/34135120/3ff3d9b7-b7ce-44d3-8def-0d5813883f37)

**看图我们也知道了，CyclicBarrier 的源码最重要的就是 await() 方法了。**

大家先把图看完，然后我们开始源码分析：

```java
public class CyclicBarrier {
    // 我们说了，CyclicBarrier 是可以重复使用的，我们把每次从开始使用到穿过栅栏当做"一代"，或者"一个周期"
    private static class Generation {
        boolean broken = false;
    }

    /** The lock for guarding barrier entry */
    private final ReentrantLock lock = new ReentrantLock();

    // CyclicBarrier 是基于 Condition 的
    // Condition 是“条件”的意思，CyclicBarrier 的等待线程通过 barrier 的“条件”是大家都到了栅栏上
    private final Condition trip = lock.newCondition();

    // 参与的线程数
    private final int parties;

    // 如果设置了这个，代表越过栅栏之前，要执行相应的操作
    private final Runnable barrierCommand;

    // 当前所处的“代”
    private Generation generation = new Generation();

    // 还没有到栅栏的线程数，这个值初始为 parties，然后递减
    // 还没有到栅栏的线程数 = parties - 已经到栅栏的数量
    private int count;

    public CyclicBarrier(int parties, Runnable barrierAction) {
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties;
        this.count = parties;
        this.barrierCommand = barrierAction;
    }

    public CyclicBarrier(int parties) {
        this(parties, null);
    }
```
首先，先看怎么开启新的一代：

```java
// 开启新的一代，当最后一个线程到达栅栏上的时候，调用这个方法来唤醒其他线程，同时初始化“下一代”
private void nextGeneration() {
    // 首先，需要唤醒所有的在栅栏上等待的线程
    trip.signalAll();
    // 更新 count 的值
    count = parties;
    // 重新生成“新一代”
    generation = new Generation();
}
```
**开启新的一代，类似于重新实例化一个 CyclicBarrier 实例**

看看怎么打破一个栅栏：

```java
private void breakBarrier() {
    // 设置状态 broken 为 true
    generation.broken = true;
    // 重置 count 为初始值 parties
    count = parties;
    // 唤醒所有已经在等待的线程
    trip.signalAll();
}
```
这两个方法之后用得到，现在开始分析最重要的等待通过栅栏方法 await 方法：

```java
// 不带超时机制
public int await() throws InterruptedException, BrokenBarrierException {
    try {
        return dowait(false, 0L);
    } catch (TimeoutException toe) {
        throw new Error(toe); // cannot happen
    }
}
// 带超时机制，如果超时抛出 TimeoutException 异常
public int await(long timeout, TimeUnit unit)
    throws InterruptedException,
           BrokenBarrierException,
           TimeoutException {
    return dowait(true, unit.toNanos(timeout));
}
```
继续往里看：
```java
private int dowait(boolean timed, long nanos)
        throws InterruptedException, BrokenBarrierException,
               TimeoutException {
    final ReentrantLock lock = this.lock;
    // 先要获取到锁，然后在 finally 中要记得释放锁
    // 如果记得 Condition 部分的话，我们知道 condition 的 await() 会释放锁，被 signal() 唤醒的时候需要重新获取锁
    lock.lock();
    try {
        final Generation g = generation;
        // 检查栅栏是否被打破，如果被打破，抛出 BrokenBarrierException 异常
        if (g.broken)
            throw new BrokenBarrierException();
        // 检查中断状态，如果中断了，抛出 InterruptedException 异常
        if (Thread.interrupted()) {
            breakBarrier();
            throw new InterruptedException();
        }
        // index 是这个 await 方法的返回值
        // 注意到这里，这个是从 count 递减后得到的值
        int index = --count;

        // 如果等于 0，说明所有的线程都到栅栏上了，准备通过
        if (index == 0) {  // tripped
            boolean ranAction = false;
            try {
                // 如果在初始化的时候，指定了通过栅栏前需要执行的操作，在这里会得到执行
                final Runnable command = barrierCommand;
                if (command != null)
                    command.run();
                // 如果 ranAction 为 true，说明执行 command.run() 的时候，没有发生异常退出的情况
                ranAction = true;
                // 唤醒等待的线程，然后开启新的一代
                nextGeneration();
                return 0;
            } finally {
                if (!ranAction)
                    // 进到这里，说明执行指定操作的时候，发生了异常，那么需要打破栅栏
                    // 之前我们说了，打破栅栏意味着唤醒所有等待的线程，设置 broken 为 true，重置 count 为 parties
                    breakBarrier();
            }
        }

        // loop until tripped, broken, interrupted, or timed out
        // 如果是最后一个线程调用 await，那么上面就返回了
        // 下面的操作是给那些不是最后一个到达栅栏的线程执行的
        for (;;) {
            try {
                // 如果带有超时机制，调用带超时的 Condition 的 await 方法等待，直到最后一个线程调用 await
                if (!timed)
                    trip.await();
                else if (nanos > 0L)
                    nanos = trip.awaitNanos(nanos);
            } catch (InterruptedException ie) {
                // 如果到这里，说明等待的线程在 await（是 Condition 的 await）的时候被中断
                if (g == generation && ! g.broken) {
                    // 打破栅栏
                    breakBarrier();
                    // 打破栅栏后，重新抛出这个 InterruptedException 异常给外层调用的方法
                    throw ie;
                } else {
                    // 到这里，说明 g != generation, 说明新的一代已经产生，即最后一个线程 await 执行完成，
                    // 那么此时没有必要再抛出 InterruptedException 异常，记录下来这个中断信息即可
                    // 或者是栅栏已经被打破了，那么也不应该抛出 InterruptedException 异常，
                    // 而是之后抛出 BrokenBarrierException 异常
                    Thread.currentThread().interrupt();
                }
            }

              // 唤醒后，检查栅栏是否是“破的”
            if (g.broken)
                throw new BrokenBarrierException();

            // 这个 for 循环除了异常，就是要从这里退出了
            // 我们要清楚，最后一个线程在执行完指定任务(如果有的话)，会调用 nextGeneration 来开启一个新的代
            // 然后释放掉锁，其他线程从 Condition 的 await 方法中得到锁并返回，然后到这里的时候，其实就会满足 g != generation 的
            // 那什么时候不满足呢？barrierCommand 执行过程中抛出了异常，那么会执行打破栅栏操作，
            // 设置 broken 为true，然后唤醒这些线程。这些线程会从上面的 if (g.broken) 这个分支抛 BrokenBarrierException 异常返回
            // 当然，还有最后一种可能，那就是 await 超时，此种情况不会从上面的 if 分支异常返回，也不会从这里返回，会执行后面的代码
            if (g != generation)
                return index;

            // 如果醒来发现超时了，打破栅栏，抛出异常
            if (timed && nanos <= 0L) {
                breakBarrier();
                throw new TimeoutException();
            }
        }
    } finally {
        lock.unlock();
    }
}
```
好了，我想我应该讲清楚了吧，我好像几乎没有漏掉任何一行代码吧？

下面开始收尾工作。

首先，我们看看怎么得到有多少个线程到了栅栏上，处于等待状态：

```java
public int getNumberWaiting() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        return parties - count;
    } finally {
        lock.unlock();
    }
}
```
判断一个栅栏是否被打破了，这个很简单，直接看 broken 的值即可：
```java
public boolean isBroken() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        return generation.broken;
    } finally {
        lock.unlock();
    }
}
```
前面我们在说 await 的时候也几乎说清楚了，什么时候栅栏会被打破，总结如下：

- 中断，我们说了，如果某个等待的线程发生了中断，那么会打破栅栏，同时抛出 InterruptedException 异常；
- 超时，打破栅栏，同时抛出 TimeoutException 异常；
- 指定执行的操作抛出了异常，这个我们前面也说过。

最后，我们来看看怎么重置一个栅栏：

```java
public void reset() {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        breakBarrier();   // break the current generation
        nextGeneration(); // start a new generation
    } finally {
        lock.unlock();
    }
}
```
我们设想一下，如果初始化时，指定了线程 parties = 4，前面有 3 个线程调用了 await 等待，在第 4 个线程调用 await 之前，我们调用 reset 方法，那么会发生什么？

首先，打破栅栏，那意味着所有等待的线程（3个等待的线程）会唤醒，await 方法会通过抛出 BrokenBarrierException 异常返回。然后开启新的一代，重置了 count 和 generation，相当于一切归零了。

怎么样，CyclicBarrier 源码很简单吧。

-------

CyclicBarrier 和 CountDownLatch 非常类似，它也可以实现线程间的技术等待，但是它的功能比 CountDownLatch 更加复杂和强大。主要应用场景和 CountDownLatch 类似。

CyclicBarrier 的字面意思是可循环使用（Cyclic）的屏障（Barrier）。它要做的事情是，让一组线程到达一个屏障（也可以叫同步点）时被阻塞，直到最后一个线程到达屏障时，屏障才会开门，所有被屏障拦截的线程才会继续干活。CyclicBarrier 默认的构造方法是```CyclicBarrier(int parties)```，其参数表示屏障拦截的线程数量，每个线程调用```await```方法告诉 CyclicBarrier 我已经到达了屏障，然后当前线程被阻塞。

再来看一下它的构造函数：

```java
public CyclicBarrier(int parties) {
    this(parties, null);
}

public CyclicBarrier(int parties, Runnable barrierAction) {
    if (parties <= 0) throw new IllegalArgumentException();
    this.parties = parties;
    this.count = parties;
    this.barrierCommand = barrierAction;
}
```

其中，parties 就代表了有拦截的线程的数量，当拦截的线程数量达到这个值的时候就打开栅栏，让所有线程通过。

### CyclicBarrier 的应用场景

CyclicBarrier 可以用于多线程计算数据，最后合并计算结果的应用场景。比如我们用一个 Excel 保存了用户所有银行流水，每个 Sheet 保存一个帐户近一年的每笔银行流水，现在需要统计用户的日均银行流水，先用多线程处理每个 sheet 里的银行流水，都执行完之后，得到每个 sheet 的日均银行流水，最后，再用 barrierAction 用这些线程的计算结果，计算出整个 Excel 的日均银行流水。

### CyclicBarrier 的使用示例

```java
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CyclicBarrierExample2 {

	// 请求的数量
	private static final int threadCount = 550;
	// 需要同步的线程数量
	private static final CyclicBarrier cyclicBarrier = new CyclicBarrier(5);

	public static void main(String[] args) throws InterruptedException {
		ExecutorService threadPool = Executors.newFixedThreadPool(10);
		for (int i = 0; i < threadCount; i++) {
			final int threadNum = i;
			Thread.sleep(1000);
			threadPool.execute(() -> {
				try {
					test(threadNum);
				} catch (InterruptedException | BrokenBarrierException e) {
					e.printStackTrace();
				}
			});
		}

		threadPool.shutdown();
	}

	public static void test(int threadNum) throws InterruptedException, BrokenBarrierException {
		System.out.println("threadNum: " + threadNum + " is ready");

		try {
			/** 等待60秒，保证子线程完全执行结束 */
			cyclicBarrier.await(60, TimeUnit.SECONDS);
		} catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
			System.out.println("-----CyclicBarrierException------");
			e.printStackTrace();
		}
		System.out.println("threadNum: " + threadNum + " is finish");
	}

}
```

运行结果，如下：

```java
threadnum:0is ready
threadnum:1is ready
threadnum:2is ready
threadnum:3is ready
threadnum:4is ready
threadnum:4is finish
threadnum:0is finish
threadnum:1is finish
threadnum:2is finish
threadnum:3is finish
threadnum:5is ready
threadnum:6is ready
threadnum:7is ready
threadnum:8is ready
threadnum:9is ready
threadnum:9is finish
threadnum:5is finish
threadnum:8is finish
threadnum:7is finish
threadnum:6is finish
......
```

可以看到当线程数量也就是请求数量达到我们定义的 5 个的时候， await方法之后的方法才被执行。

另外，```CyclicBarrier``` 还提供一个更高级的构造函数```CyclicBarrier(int parties, Runnable barrierAction)```，用于在线程到达屏障时，优先执行```barrierAction```，方便处理更复杂的业务场景。示例代码如下：

```java
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CyclicBarrierExample3 {

	// 请求的数量
	private static final int threadCount = 550;
	// 需要同步的线程数量
	private static final CyclicBarrier cyclicBarrier = new CyclicBarrier(5, () -> {
		System.out.println("----当线程数到达之后，优先执行----");
	});

	public static void main(String[] args) throws InterruptedException {
		ExecutorService threadPool = Executors.newFixedThreadPool(10);
		for (int i = 0; i < threadCount; i++) {
			final int threadNum = i;
			Thread.sleep(1000);
			threadPool.execute(() ->{
				try {
					test(threadNum);
				} catch (InterruptedException | BrokenBarrierException e) {
					e.printStackTrace();
				}
			});
		}
		
		threadPool.shutdown();
	}

	public static void test(int threadNum) throws InterruptedException, BrokenBarrierException {
		System.out.println("threadNum: " + threadNum + " is ready");
		cyclicBarrier.await();
		System.out.println("threadNum: " + threadNum + " is finish");
	}

}
```

运行结果如下：

```java
threadnum:0is ready
threadnum:1is ready
threadnum:2is ready
threadnum:3is ready
threadnum:4is ready
------当线程数达到之后，优先执行------
threadnum:4is finish
threadnum:0is finish
threadnum:2is finish
threadnum:1is finish
threadnum:3is finish
threadnum:5is ready
threadnum:6is ready
threadnum:7is ready
threadnum:8is ready
threadnum:9is ready
------当线程数达到之后，优先执行------
threadnum:9is finish
threadnum:5is finish
threadnum:6is finish
threadnum:8is finish
threadnum:7is finish
......
```

### CyclicBarrier记录

当调用 ```CyclicBarrier``` 对象调用 ```await()``` 方法时，实际上调用的是```dowait(false, 0L)```方法。 ```await()``` 方法就像树立起一个栅栏的行为一样，将线程挡住了，当拦住的线程数量达到 ```parties``` 的值时，栅栏才会打开，线程才得以通过执行。

```java
public int await() throws InterruptedException, BrokenBarrierException {
    try {
        return dowait(false, 0L);
    } catch (TimeoutException toe) {
        throw new Error(toe); // cannot happen
    }
}
```

```dowait(false, 0L)：```

```java
/**
 * Main barrier code, covering the various policies.
 */
private int dowait(boolean timed, long nanos)
    throws InterruptedException, BrokenBarrierException,
           TimeoutException {
    final ReentrantLock lock = this.lock;
    lock.lock();
    try {
        final Generation g = generation;

        if (g.broken)
            throw new BrokenBarrierException();

        if (Thread.interrupted()) {
            breakBarrier();
            throw new InterruptedException();
        }

        int index = --count;
        if (index == 0) {  // tripped
            boolean ranAction = false;
            try {
                final Runnable command = barrierCommand;
                if (command != null)
                    command.run();
                ranAction = true;
                nextGeneration();
                return 0;
            } finally {
                if (!ranAction)
                    breakBarrier();
            }
        }

        // loop until tripped, broken, interrupted, or timed out
        for (;;) {
            try {
                if (!timed)
                    trip.await();
                else if (nanos > 0L)
                    nanos = trip.awaitNanos(nanos);
            } catch (InterruptedException ie) {
                if (g == generation && ! g.broken) {
                    breakBarrier();
                    throw ie;
                } else {
                    // We're about to finish waiting even if we had not
                    // been interrupted, so this interrupt is deemed to
                    // "belong" to subsequent execution.
                    Thread.currentThread().interrupt();
                }
            }

            if (g.broken)
                throw new BrokenBarrierException();

            if (g != generation)
                return index;

            if (timed && nanos <= 0L) {
                breakBarrier();
                throw new TimeoutException();
            }
        }
    } finally {
        lock.unlock();
    }
}
```

总结：```CyclicBarrier``` 内部通过一个 ```count``` 变量作为计数器，```count``` 的初始值为 ```parties``` 属性的初始化值，每当一个线程到了栅栏这里了，那么就将计数器减一。如果 ```count``` 值为 0 了，表示这是这一代最后一个线程到达栅栏，就尝试执行我们构造方法中输入的任务。
