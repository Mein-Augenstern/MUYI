看在前面
====
* <a href="https://java2blog.com/difference-between-sleep-and-wait-in/">Difference between sleep and wait in java</a>

wait方法简介
====

* 让当前线程进入等待状态，当别的其他线程调用notify()或者notifyAll()方法时，当前线程进入就绪状态

* wait方法必须在同步上下文中调用，例如：同步方法块或者同步方法中，这也就意味着如果你想要调用wait方法，前提是必须获取对象上的锁资源

* 当wait方法调用时，当前线程将会释放已获取的对象锁资源，并进入等待队列，其他线程就可以尝试获取对象上的锁资源。

```java
synchronized(lockedObject) {   
 lockedObject.wait(); // It releases the lock on lockedObject.
 // So until we call notify() or notifyAll() from other thread,It will
 // not wake up

}
```

sleep方法简介
====

* 让当前线程休眠指定时间。

* 休眠时间的准确性依赖于系统时钟和CPU调度机制。

* 不释放已获取的锁资源，如果sleep方法在同步上下文中调用，那么其他线程是无法进入到当前同步块或者同步方法中的。

* 可通过调用interrupt()方法来唤醒休眠线程。

```java
synchronized(lockedObject) {   
    Thread.sleep(1000); // It does not release the lock on lockedObject.
    // So either after 1000 miliseconds, current thread will wake up, or after we call 
    // t. interrupt() method.
}
```

sleep vs wait
-----

|     xx    | wait    |   sleep    |  
| :------:   | :-------:   | :-------:   | 
| 同步        | 只能在同步上下文中调用wait方法，否则或抛出IllegalMonitorStateException异常      |   不需要在同步方法或同步块中调用      |   
| 作用对象        | wait方法定义在Object类中，作用域对象本身      |   sleep方法定义在java.lang.Thread中，作用于当前线程      |   
| 释放锁资源        | 是      |   否      |   
| 唤醒条件       |其他线程调用对象的notify()或者notifyAll()方法      |    超时或者调用interrupt方法体      |
| 方法属性        | wait是实例方法      |   sleep是静态方法      | 

线程的entry sets 和 wait sets
====

![Entry and Wait Sets](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/Entry_and_Wait_Sets.gif)

* 所有期待获得锁的线程，在锁已经被其它线程拥有的时候，这些期待获得锁的线程就进入了Object Lock的entry set区域。

* 所有曾经获得过锁，但是由于其它必要条件不满足而需要wait的时候，线程就进入了Object Lock的wait set区域 。

* 在wait set区域的线程获得Notify/notifyAll通知的时候，随机的一个Thread（Notify）或者是全部的Thread（NotifyALL）从Object Lock的wait set区域进入了entry set中。

* 在当前拥有锁的线程释放掉锁的时候，处于该Object Lock的entryset区域的线程都会抢占该锁，但是只能有任意的一个Thread能取得该锁，而其他线程依然在entry set中等待下次来抢占到锁之后再执行。
