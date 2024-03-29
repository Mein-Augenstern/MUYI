## 看在前面

* 一行一行源码分析清楚 AbstractQueuedSynchronizer (二)：https://javadoop.com/post/AbstractQueuedSynchronizer-2

看本文之前先看上面的文章，大佬文章值得花费时间阅读！

在之前的文章中，我们接触了大量的中断，这边算是个总结吧。如果你完全熟悉中断了，没有必要再看这节，本节为新手而写。

## 线程中断

首先，我们要明白，中断不是类似 linux 里面的命令 kill -9 pid，不是说我们中断某个线程，这个线程就停止运行了。中断代表线程状态，每个线程都关联了一个中断状态，是一个 true 或 false 的 boolean 值，初始值为 false。

Java 中的中断和操作系统的中断还不一样，这里就按照状态来理解吧，不要和操作系统的中断联系在一起

关于中断状态，我们需要重点关注 Thread 类中的以下几个方法：

```java
// Thread 类中的实例方法，持有线程实例引用即可检测线程中断状态
public boolean isInterrupted() {}

// Thread 中的静态方法，检测调用这个方法的线程是否已经中断
// 注意：这个方法返回中断状态的同时，会将此线程的中断状态重置为 false
// 所以，如果我们连续调用两次这个方法的话，第二次的返回值肯定就是 false 了
// 当你可能要被大量中断并且你想确保只处理一次中断时，就可以使用这个方法了
public static boolean interrupted() {}

// Thread 类中的实例方法，用于设置一个线程的中断状态为 true
public void interrupt() {}
```

关于上面三个方法，还可以再看下文章：https://segmentfault.com/a/1190000022691446 介绍。

```java
public static void main(String[] args) {
    System.out.println("start");
    Thread.currentThread().interrupt();
    System.out.println(Thread.interrupted());
    System.out.println(Thread.currentThread().isInterrupted());
    System.out.println("end");
}
```
输出结果
```java
start
true
false
end
```


我们说中断一个线程，其实就是设置了线程的 interrupted status 为 true，至于说被中断的线程怎么处理这个状态，那是那个线程自己的事。如以下代码：

```java
while (!Thread.interrupted()) {
   doWork();
   System.out.println("我做完一件事了，准备做下一件，如果没有其他线程中断我的话");
}
```

这种代码就是会响应中断的，它会在干活的时候先判断下中断状态，不过，除了 JDK 源码外，其他用中断的场景还是比较少的，毕竟 JDK 源码非常讲究。

当然，中断除了是线程状态外，还有其他含义，否则也不需要专门搞一个这个概念出来了。

如果线程处于以下三种情况，那么当线程被中断的时候，能自动感知到：

1.  来自 Object 类的 wait()、wait(long)、wait(long, int)，
来自 Thread 类的 join()、join(long)、join(long, int)、sleep(long)、sleep(long, int) 
这几个方法的相同之处是，方法上都有: throws InterruptedException
 
> 如果线程阻塞在这些方法上（我们知道，这些方法会让当前线程阻塞），这个时候如果其他线程对这个线程进行了中断，那么这个线程会从这些方法中立即返回，抛出 InterruptedException 异常，同时重置中断状态为 false。
 
2.  实现了 InterruptibleChannel 接口的类中的一些 I/O 阻塞操作，如 DatagramChannel 中的 connect 方法和 receive 方法等 
如果线程阻塞在这里，中断线程会导致这些方法抛出 ClosedByInterruptException 并重置中断状态。
 
3.  Selector 中的 select 方法，参考下我写的 NIO 的文章 
一旦中断，方法立即返回
 

对于以上 3 种情况是最特殊的，因为他们能自动感知到中断（这里说自动，当然也是基于底层实现），并且在做出相应的操作后都会重置中断状态为 false。

那是不是只有以上 3 种方法能自动感知到中断呢？不是的，如果线程阻塞在 LockSupport.park(Object obj) 方法，也叫挂起，这个时候的中断也会导致线程唤醒，但是唤醒后不会重置中断状态，所以唤醒后去检测中断状态将是 true。

## InterruptedException 概述

它是一个特殊的异常，不是说 JVM 对其有特殊的处理，而是它的使用场景比较特殊。通常，我们可以看到，像 Object 中的 wait() 方法，ReentrantLock 中的 lockInterruptibly() 方法，Thread 中的 sleep() 方法等等，这些方法都带有 throws InterruptedException，我们通常称这些方法为阻塞方法（blocking method）。

阻塞方法一个很明显的特征是，它们需要花费比较长的时间（不是绝对的，只是说明时间不可控），还有它们的方法结束返回往往依赖于外部条件，如 wait 方法依赖于其他线程的 notify，lock 方法依赖于其他线程的 unlock等等。

当我们看到方法上带有 throws InterruptedException 时，我们就要知道，这个方法应该是阻塞方法，我们如果希望它能早点返回的话，我们往往可以通过中断来实现。

除了几个特殊类（如 Object，Thread等）外，感知中断并提前返回是通过轮询中断状态来实现的。我们自己需要写可中断的方法的时候，就是通过在合适的时机（通常在循环的开始处）去判断线程的中断状态，然后做相应的操作（通常是方法直接返回或者抛出异常）。当然，我们也要看到，如果我们一次循环花的时间比较长的话，那么就需要比较长的时间才能感知到线程中断了。

## 处理中断

一旦中断发生，我们接收到了这个信息，然后怎么去处理中断呢？本小节将简单分析这个问题。

我们经常会这么写代码：

```java
try {
    Thread.sleep(10000);
} catch (InterruptedException e) {
    // ignore
}
// go on
```

当 sleep 结束继续往下执行的时候，我们往往都不知道这块代码是真的 sleep 了 10 秒，还是只休眠了 1 秒就被中断了。这个代码的问题在于，我们将这个异常信息吞掉了。（对于 sleep 方法，我相信大部分情况下，我们都不在意是否是中断了，这里是举例）

AQS 的做法很值得我们借鉴，我们知道 ReentrantLock 有两种 lock 方法：

```java
public void lock() {
    sync.lock();
}

public void lockInterruptibly() throws InterruptedException {
    sync.acquireInterruptibly(1);
}
```

前面我们提到过，lock() 方法不响应中断。如果 thread1 调用了 lock() 方法，过了很久还没抢到锁，这个时候 thread2 对其进行了中断，thread1 是不响应这个请求的，它会继续抢锁，当然它不会把“被中断”这个信息扔掉。我们可以看以下代码：

```java
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        // 我们看到，这里也没做任何特殊处理，就是记录下来中断状态。
        // 这样，如果外层方法需要去检测的时候，至少我们没有把这个信息丢了
        selfInterrupt();// Thread.currentThread().interrupt();
}
```

而对于 lockInterruptibly() 方法，因为其方法上面有 throws InterruptedException ，这个信号告诉我们，如果我们要取消线程抢锁，直接中断这个线程即可，它会立即返回，抛出 InterruptedException 异常。

在并发包中，有非常多的这种处理中断的例子，提供两个方法，分别为响应中断和不响应中断，对于不响应中断的方法，记录中断而不是丢失这个信息。如 Condition 中的两个方法就是这样的：

```java
// 响应中断
// await() 方法是响应中断的。当线程在调用 await() 方法时，如果其他线程对其执行了中断操作，await() 会触发 InterruptedException 来响应这个中断。
void await() throws InterruptedException;

// 不响应中断
// awaitUninterruptibly() 方法不会对中断做出响应。即使线程在调用 awaitUninterruptibly() 方法期间被中断，
// 它也会继续等待，直到被信号唤醒或者等待达到某种条件。在这个过程中，它不会抛出 InterruptedException，并且不会清除线程的中断状态。
void awaitUninterruptibly();
```

通常，如果方法会抛出 InterruptedException 异常，往往方法体的第一句就是：

```java
public final void await() throws InterruptedException {
    if (Thread.interrupted())
        throw new InterruptedException();
 	...... 
}
```

熟练使用中断，对于我们写出优雅的代码是有帮助的，也有助于我们分析别人的源码。

## 中断机制的使用场景

通常，中断的使用场景有以下几个

- 点击某个桌面应用中的关闭按钮时（比如你关闭 IDEA，不保存数据直接中断好吗？）；
- 某个操作超过了一定的执行时间限制需要中止时；
- 多个线程做相同的事情，只要一个线程成功其它线程都可以取消时；
- 一组线程中的一个或多个出现错误导致整组都无法继续时；

因为中断是一种协同机制，提供了更优雅中断方式，也提供了更多的灵活性，所以当遇到如上场景等，我们就可以考虑使用中断机制了。

## 使用中断机制有哪些注意事项

其实使用中断机制无非就是注意上面说的两项内容：

原则一：（中断标识）如果遇到的是可中断的阻塞方法, 并抛出 InterruptedException，可以继续向方法调用栈的上层抛出该异常；如果检测到中断，则可清除中断状态并抛出 InterruptedException，使当前方法也成为一个可中断的方法

原则二：（InterruptedException）若有时候不太方便在方法上抛出 InterruptedException，比如要实现的某个接口中的方法签名上没有 throws InterruptedException，这时就可以捕获可中断方法的 InterruptedException 并通过 Thread.currentThread.interrupt() 来重新设置中断状态。

再通过个例子来加深一下理解：本意是当前线程被中断之后，退出while(true), 你觉得代码有问题吗？（先不要向下看）

```java
Thread th = Thread.currentThread();
while(true) {
  if(th.isInterrupted()) {
    break;
  }
  // 省略业务代码
  try {
    Thread.sleep(100);
  }catch (InterruptedException e){
    e.printStackTrace();
  }
}
```

打开 Thread.sleep 方法：

![image](https://github.com/Mein-Augenstern/MUYI/assets/34135120/61734a8f-b938-4875-b2a5-f6ea9b7004f1)

sleep 方法抛出 InterruptedException后，中断标识也被清空置为 false，我们在catch 没有通过调用 th.interrupt() 方法再次将中断标识置为 true，这就导致无限循环了

这两个原则很好理解。总的来说，我们应该留意 InterruptedException，当我们捕获到该异常时，绝不可以默默的吞掉它，什么也不做，因为这会导致上层调用栈什么信息也获取不到。其实在编写程序时，捕获的任何受检异常我们都不应该吞掉。

## JDK 中有哪些使用中断机制的地方呢？

**ThreadPoolExecutor**

ThreadPoolExecutor 中的 shutdownNow 方法会遍历线程池中的工作线程并调用线程的 interrupt 方法来中断线程

![image](https://github.com/Mein-Augenstern/MUYI/assets/34135120/31f719fa-a3d4-436f-ab74-e002dd9468e3)

![image](https://github.com/Mein-Augenstern/MUYI/assets/34135120/e694a1a1-3162-4f59-ad35-2cacae481ddf)

**FutureTask**

FutureTask 中的 cancel 方法，如果传入的参数为 true，它将会在正在运行异步任务的线程上调用 interrupt 方法，如果正在执行的异步任务中的代码没有对中断做出响应，那么 cancel 方法中的参数将不会起到什么效果

![image](https://github.com/Mein-Augenstern/MUYI/assets/34135120/e2b5dd14-a37d-41d3-92c1-cb7059311262)




