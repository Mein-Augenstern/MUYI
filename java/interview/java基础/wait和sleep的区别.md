看在前面
====
* <a href="https://java2blog.com/difference-between-sleep-and-wait-in/">Difference between sleep and wait in java</a>

wait方法简介
====

*

sleep方法简介
====

*

wait和sleep的区别
====

sleep
-----

* 让当前线程休眠指定时间。

* 休眠时间的准确性依赖于系统时钟和CPU调度机制。

* 不释放已获取的锁资源，如果sleep方法在同步上下文中调用，那么其他线程是无法进入到当前同步块或者同步方法中的。

* 可通过调用interrupt()方法来唤醒休眠线程。

wait
-----

* 让当前线程进入等待状态，当别的其他线程调用notify()或者notifyAll()方法时，当前线程进入就绪状态

* wait方法必须在同步上下文中调用，例如：同步方法块或者同步方法中，这也就意味着如果你想要调用wait方法，前提是必须获取对象上的锁资源

* 当wait方法调用时，当前线程将会释放已获取的对象锁资源，并进入等待队列，其他线程就可以尝试获取对象上的锁资源。

sleep vs wait
-----

