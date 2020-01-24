一句话描述
====

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

CyclicBarrier 的应用场景
====

CyclicBarrier 可以用于多线程计算数据，最后合并计算结果的应用场景。比如我们用一个 Excel 保存了用户所有银行流水，每个 Sheet 保存一个帐户近一年的每笔银行流水，现在需要统计用户的日均银行流水，先用多线程处理每个 sheet 里的银行流水，都执行完之后，得到每个 sheet 的日均银行流水，最后，再用 barrierAction 用这些线程的计算结果，计算出整个 Excel 的日均银行流水。

CyclicBarrier 的使用示例
====

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

CyclicBarrier源码分析
====

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
