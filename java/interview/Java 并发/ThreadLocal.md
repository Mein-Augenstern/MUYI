ThreadLocal简介
====

通常情况下，我们创建的变量是可以被任何一个线程访问并修改的。**如果想实现每一个线程都有自己的专属本地变量该如何解决呢？JDK中提供的```ThreadLocal```类正是为了解决这样的问题。```Threadlocal``` 类主要解决的就是让每个线程绑定自己的值，可以将ThreadLocal类形象的比喻成存放数据的盒子，盒子中可以存储每个线程的私有数据。**

**如果你创建了一个ThreadLocal变量，那么访问这个变量的每个线程都会有这个变量的本地副本，这也是ThreadLocal变量名的由来。他们可以使用get()和set()方法来获取默认值或将其值更改为当前线程所存的副本的值，从而避免了线程安全问题。**

再举个简单的例子：

比如有两个人去宝屋收集宝物，这两个共用一个袋子的话肯定会产生争执，但是给他们两个人每个人分配一个袋子的话就不会出现这样的问题。如果把这两个人比作线程的话，那么ThreadLocal就是用来避免这两个线程竞争的。

> **ThreadLocal的创建并不是为了解决资源共享和并发问题哦！**

ThreadLocal代码示例
====

相信看了上面的解释，大家已经搞懂 ThreadLocal 类是个什么东西了。

```java
import java.text.SimpleDateFormat;
import java.util.Random;

public class ThreadLocalExample implements Runnable {

	private static final ThreadLocal<SimpleDateFormat> formatter = new ThreadLocal<SimpleDateFormat>() {
		@Override
		protected SimpleDateFormat initialValue() {
			return new SimpleDateFormat("yyyyMMdd HHmm");
		}
	};

	public static void main(String[] args) throws InterruptedException {
		ThreadLocalExample threadLocalExample = new ThreadLocalExample();
		for (int i = 0; i < 10; i++) {
			Thread thread = new Thread(threadLocalExample, "" + i);
			Thread.sleep(new Random().nextInt(1000));
			thread.start();
		}
	}

	@Override
	public void run() {
		System.out.println("Thread Name= " + Thread.currentThread().getName() + " default Formatter = " + formatter.get().toPattern());

		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// formatter pattern is changed here by thread, but it won't reflect to other
		// threads
		formatter.set(new SimpleDateFormat());

		System.out.println("Thread Name = " + Thread.currentThread().getName() + " formatter = " + formatter.get().toPattern());
	}

}
```

Output:

```java
Thread Name= 0 default Formatter = yyyyMMdd HHmm
Thread Name= 1 default Formatter = yyyyMMdd HHmm
Thread Name= 2 default Formatter = yyyyMMdd HHmm
Thread Name= 3 default Formatter = yyyyMMdd HHmm
Thread Name = 0 formatter = yy-M-d ah:mm
Thread Name= 4 default Formatter = yyyyMMdd HHmm
Thread Name = 1 formatter = yy-M-d ah:mm
Thread Name= 5 default Formatter = yyyyMMdd HHmm
Thread Name = 2 formatter = yy-M-d ah:mm
Thread Name = 3 formatter = yy-M-d ah:mm
Thread Name = 4 formatter = yy-M-d ah:mm
Thread Name= 6 default Formatter = yyyyMMdd HHmm
Thread Name = 5 formatter = yy-M-d ah:mm
Thread Name= 7 default Formatter = yyyyMMdd HHmm
Thread Name= 8 default Formatter = yyyyMMdd HHmm
Thread Name= 9 default Formatter = yyyyMMdd HHmm
Thread Name = 6 formatter = yy-M-d ah:mm
Thread Name = 7 formatter = yy-M-d ah:mm
Thread Name = 8 formatter = yy-M-d ah:mm
Thread Name = 9 formatter = yy-M-d ah:mm
```

从输出中可以看出，Thread-0已经改变了formatter的值，但仍然是thread-2默认格式化程序与初始化值相同，其他线程也一样。

上面有一段代码用到了创建 ```ThreadLocal``` 变量的那段代码用到了 Java8 的知识，它等于下面这段代码，如果你写了下面这段代码的话，IDEA会提示你转换为Java8的格式(IDEA真的不错！)。因为```ThreadLocal```类在Java 8中扩展，使用一个新的方法```withInitial()```，将Supplier功能接口作为参数。

```java
private static final ThreadLocal<SimpleDateFormat> formatter = new ThreadLocal<SimpleDateFormat>(){
    @Override
    protected SimpleDateFormat initialValue()
    {
        return new SimpleDateFormat("yyyyMMdd HHmm");
    }
};
```

ThreadLocal原理
====

从 ```Thread```类源代码入手。

```java
public class Thread implements Runnable {
     ......
    //与此线程有关的ThreadLocal值。由ThreadLocal类维护
    ThreadLocal.ThreadLocalMap threadLocals = null;
    
    //与此线程有关的InheritableThreadLocal值。由InheritableThreadLocal类维护
    ThreadLocal.ThreadLocalMap inheritableThreadLocals = null;
     ......
}
```

从上面```Thread```类 源代码可以看出```Thread``` 类中有一个 ```threadLocals``` 和 一个 ```inheritableThreadLocals``` 变量，它们都是 ```ThreadLocalMap``` 类型的变量,我们可以把 ```ThreadLocalMap``` 理解为```ThreadLocal``` 类实现的定制化的 ```HashMap```。默认情况下这两个变量都是```null```，只有当前线程调用 ```ThreadLocal``` 类的 ```set```或```get```方法时才创建它们，实际上调用这两个方法的时候，我们调用的是```ThreadLocalMap```类对应的 ```get()、set()``` 方法。

```ThreadLocal```类的```set()```方法

```java
/**
 * Sets the current thread's copy of this thread-local variable
 * to the specified value.  Most subclasses will have no need to
 * override this method, relying solely on the {@link #initialValue}
 * method to set the values of thread-locals.
 *
 * @param value the value to be stored in the current thread's copy of
 *        this thread-local.
 */
public void set(T value) {
    Thread t = Thread.currentThread();
    ThreadLocalMap map = getMap(t);
    if (map != null)
        map.set(this, value);
    else
        createMap(t, value);
}

/**
 * Get the map associated with a ThreadLocal. Overridden in
 * InheritableThreadLocal.
 *
 * @param  t the current thread
 * @return the map
 */
ThreadLocalMap getMap(Thread t) {
    return t.threadLocals;
}
```

通过上面这些内容，我们足以通过猜测得出结论：**最终的变量是放在了当前线程的 ```ThreadLocalMap``` 中，并不是存在 ```ThreadLocal``` 上，```ThreadLocal``` 可以理解为只是```ThreadLocalMap```的封装，传递了变量值。**```ThrealLocal``` 类中可以通过```Thread.currentThread()```获取到当前线程对象后，直接通过```getMap(Thread t)```可以访问到该线程的```ThreadLocalMap```对象。

**每个```Thread```中都具备一个```ThreadLocalMap```，而```ThreadLocalMap```可以存储以```ThreadLocal```为key的键值对。** 比如我们在同一个线程中声明了两个 ```ThreadLocal``` 对象的话，会使用 ```Thread```内部的```ThreadLocalMap```变量存放数据的，```ThreadLocalMap```的 ```key``` 就是 ```ThreadLocal```对象，```value``` 就是 ```ThreadLocal``` 对象调用```set```方法设置的值。```ThreadLocal```内部维护的是一个类似```Map```的```ThreadLocalMap```数据结构，```key```为当前对象的```ThreadLocal```对象，值为泛型的```Object```。这也就解释了```ThreadLocal```声明的变量为什么在每一个线程都有自己的专属本地变量。

```ThreadLocalMap```是```ThreadLocal```的静态内部类。

![ThreadLocalMap和ThreadLocal的UML图](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/ThreadLocalMap%E5%92%8CThreadLocal%E7%9A%84UML%E5%9B%BE.png)

首先来看看为什么ThreadLocal会产生内存泄漏
====

![ThreadLocal](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/ThreadLocal.png)

```ThreadLocal```的实现是这样的：每个```Thread``` 维护一个 ```ThreadLocalMap``` 映射表，这个映射表的 ```key``` 是 ```ThreadLocal```实例本身，```value``` 是真正需要存储的 ```Object```。 

也就是说 ```ThreadLocal``` 本身并不存储值，它只是作为一个 ```key``` 来让线程从 ```ThreadLocalMap``` 获取 ```value```。
值得注意的是图中的虚线，表示 ```ThreadLocalMap``` 是使用 ```ThreadLocal``` 的弱引用作为 ```Key``` 的，弱引用的对象在 ```GC``` 时会被回收。

```ThreadLocalMap```使用```ThreadLocal```的弱引用作为```key```，如果一个```ThreadLocal```没有外部强引用来引用它，那么系统 ```GC``` 的时候，这个```ThreadLocal```势必会被回收，这样一来，```ThreadLocalMap```中就会出现```key```为```null```的```Entry```，就没有办法访问这些```key```为```null```的```Entry```的```value```，如果当前线程再迟迟不结束的话，这些```key```为```null```的```Entry```的```value```就会一直存在一条强引用链：```Thread Ref -> Thread -> ThreaLocalMap -> Entry -> value```永远无法回收，造成内存泄漏。

ThreadLocal内存泄漏的问题
====
ThreadLocalMap中使用key为```ThreadLocal```的弱引用，而value是强引用。所以，如果ThreadLocal没有被外部强引用的情况下，在垃圾回收的时候，key会被清理掉，而value不会被清理掉。这样一来，```ThreadLocalMap```中就会出现key为null的```Entry```。如果我们不做任何措施的话，value永远无法被GC回收，这个时候就可能会产生内存泄漏。ThreadLocalMap实现中已经考虑了这种情况，在调用```set()、get()、remove()```方法的时候，会清理掉key为null的纪录。使用完```ThreadLocal```方法后，最后手动调用```remove()```方法

```java
static class Entry extends WeakReference<ThreadLocal<?>> {
    /** The value associated with this ThreadLocal. */
    Object value;

    Entry(ThreadLocal<?> k, Object v) {
        super(k);
        value = v;
    }
}
```

**弱引用介绍**

> 如果一个对象只具有弱引用，那就类似于可有可无的生活用品。弱引用与软引用的区别在于：只具有弱引用的对象拥有更短暂的生命周期。在垃圾回收器线程扫描它 所管辖的内存区域的过程中，一旦发现了只具有弱引用的对象，不管当前内存空间足够与否，都会回收它的内存。不过，由于垃圾回收器是一个优先级很低的线程， 因此不一定会很快发现那些只具有弱引用的对象。

> 弱引用可以和一个引用队列（ReferenceQueue）联合使用，如果弱引用所引用的对象被垃圾回收，Java虚拟机就会把这个弱引用加入到与之关联的引用队列中。


**关于弱引用可以参照下面链接了解**

<a href="https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/%E5%BC%BA%E5%BC%95%E7%94%A8%20%E3%80%81%E8%BD%AF%E5%BC%95%E7%94%A8%E3%80%81%20%E5%BC%B1%E5%BC%95%E7%94%A8%E3%80%81%E8%99%9A%E5%BC%95%E7%94%A8.md">强引用 、软引用、 弱引用、虚引用</a>

如何避免ThreadLocal内存泄漏问题
====

每次使用完ThreadLocal，都调用它的remove()方法，清除数据。在使用线程池的情况下，及时清理ThreadLocal，规避内存泄漏问题的发生。
