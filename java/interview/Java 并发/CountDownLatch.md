一句话描述
====

CountDownLatch 是一个同步工具类，它允许一个或多个线程一直等待，直到其他线程的操作执行完后再执行。在 Java 并发中，countdownlatch 的概念是一个常见的面试题，所以一定要确保你很好的理解了它。

Question
====

* 解释一下 CountDownLatch 概念？

* CountDownLatch 和 CyclicBarrier 的不同之处？

* 给出一些 CountDownLatch 使用的例子？

* CountDownLatch 类中主要的方法？

CountDownLatch 的三种典型用法
====

① 某一线程在开始运行前等待 n 个线程执行完毕。将 ```CountDownLatch``` 的计数器初始化为``` n ：new CountDownLatch(n)```，每当一个任务线程执行完毕，就将计数器减 1 ```countdownlatch.countDown()```，当计数器的值变为 0 时，在```CountDownLatch```上 ```await()``` 的线程就会被唤醒。一个典型应用场景就是启动一个服务时，主线程需要等待多个组件加载完毕，之后再继续执行。

② 实现多个线程开始执行任务的**最大并行性。注意是并行性，不是并发，强调的是多个线程在某一时刻同时开始执行**。类似于赛跑，将多个线程放到起点，等待发令枪响，然后同时开跑。做法是初始化一个共享的 ```CountDownLatch``` 对象，将其计数器初始化为 ```1 ：new CountDownLatch(1)```，多个线程在开始执行任务前首先 ```coundownlatch.await()```，当主线程调用 ```countDown()``` 时，计数器变为 0，多个线程同时被唤醒。

③ 死锁检测：一个非常方便的使用场景是，你可以使用 n 个线程访问共享资源，在每次测试阶段的线程数目是不同的，并尝试产生死锁。

CountDownLatch 代码示例
====

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CountDownLatchExample1 {

	// 请求的数量
	private static final int threadCount = 550;

	public static void main(String[] args) throws InterruptedException {
		// 创建一个具有固定线程数量的线程池对象（如果这里线程池的线程数量给太少的话你会发现执行的很慢）
		ExecutorService threadPool = Executors.newFixedThreadPool(300);
		final CountDownLatch countDownLatch = new CountDownLatch(threadCount);
		for (int i = 0; i < threadCount; i++) {
			final int threadNum = i;
			threadPool.execute(() -> {
				try {
					test(threadNum);
				} catch (InterruptedException e) {
					e.printStackTrace();
				} finally {
					countDownLatch.countDown();
				}
			});
		}

		countDownLatch.await();
		threadPool.shutdown();
		System.out.println("finish");
	}

	public static void test(int threadnum) throws InterruptedException {
		// 模拟请求的耗时操作
		Thread.sleep(1000);
		System.out.println("threadnum:" + threadnum);
		// 模拟请求的耗时操作
		Thread.sleep(1000);
	}

}

```

上面的代码中，我们定义了请求的数量为 550，当这 550 个请求被处理完成之后，才会执行```System.out.println("finish");```。

与 ```CountDownLatch``` 的第一次交互是主线程等待其他线程。主线程必须在启动其他线程后立即调用 ```CountDownLatch.await()```方法。这样主线程的操作就会在这个方法上阻塞，直到其他线程完成各自的任务。

其他 N 个线程必须引用闭锁对象，因为他们需要通知 ```CountDownLatch``` 对象，他们已经完成了各自的任务。这种通知机制是通过 ```CountDownLatch.countDown()```方法来完成的；每调用一次这个方法，在构造函数中初始化的 count 值就减 1。所以当 N 个线程都调 用了这个方法，count 的值等于 0，然后主线程就能通过 ```await()```方法，恢复执行自己的任务。

CountDownLatch 的不足
====

```CountDownLatch``` 是一次性的，计数器的值只能在构造方法中初始化一次，之后没有任何机制再次对其设置值，当 ```CountDownLatch``` 使用完毕后，它不能再次被使用。
