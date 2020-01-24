看在前面
====

<a href="https://blog.csdn.net/qq_19431333/article/details/70212663">深入理解Semaphore</a>

代码举栗说明
====

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
