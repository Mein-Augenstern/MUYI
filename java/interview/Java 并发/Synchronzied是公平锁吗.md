看在前面
====

> * <a href="https://blog.csdn.net/wab719591157/article/details/86221109">synchronized 是公平锁吗？可以重入吗？</a>

一句话答案
====

**```synchronized```是非公平锁，可以重入**。

**公平锁**

获取不到锁的时候，会自动加入队列，等待线程释放后，队列的第一个线程获取锁

**非公平锁**

获取不到锁的时候，会自动加入队列，等待线程释放锁后所有等待的线程同时去竞争

**什么是可重入**？

同一个线程可以反复获取锁多次，然后需要释放多次

在来看几个问题
====

1. synchronized 加在 static 修饰的 方法上锁的是哪个对象？

锁的是 Class 对象

2. synchronized(this)  锁的是哪个对象？

锁的是当前对象的实例也就是 new 出来的对象

3. synchronized() 锁的是哪个对象？

同  synchronized(this) 锁的也是当前对象的实例

4. synchronized(lock) 锁的是哪个对象？

锁的是 lock 对象

5. 同一个对象里面 有 static 方法加了 synchronized 还有一个普通方法也加了  synchronized 如果这个时候有2个线程，一个先获取 了 static 方法上的锁，另外一个线程可以在 第一个线程释放锁之前获取 普通方法上的锁吗？反过来呢？

可以的，因为这是 2 把锁，2 个线程分别获取2把锁，没有任何问题。
