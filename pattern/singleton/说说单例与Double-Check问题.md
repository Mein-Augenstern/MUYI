看在前面
====

* <a href="http://benjaminwhx.com/2018/05/15/%E8%AF%B4%E8%AF%B4%E5%8D%95%E4%BE%8B%E4%B8%8Edouble-check%E9%97%AE%E9%A2%98/">说说单例与Double-Check问题 作者: 吴海旭 </a>

Double-check （双重锁检查） 
====

在GoF的23种设计模式中，单例模式是比较简单的一种。然而，有时候越是简单的东西越容易出现问题。下面就单例设计模式详细的探讨一下。

所谓单例模式，简单来说，就是在整个应用中保证只有一个类的实例存在。就像是Java Web中的application，也就是提供了一个全局变量，用处相当广泛，比如保存全局数据，实现全局性的操作等。

我们来用不同的方式来实现单例模式并比较其优劣。

1、饿汉模式
====

```
public class HungrySingleton {

	/**
	 * 饿汉式
	 */

	private static final HungrySingleton instance = new HungrySingleton();

	private HungrySingleton() {
		if (instance == null) {
			throw new IllegalStateException();
		}
	}

	public static HungrySingleton getInstance() {
		return instance;
	}

}
```
or

```java
public class HungrySingletonStaticCodeBlock {

	/**
	 * 饿汉式
	 */
	
	private static final HungrySingletonStaticCodeBlock instance;

	static {
		instance = new HungrySingletonStaticCodeBlock();
	}

	private HungrySingletonStaticCodeBlock() {
		if (instance != null) {
			throw new IllegalStateException();
		}
	}

	public static HungrySingletonStaticCodeBlock getInstance() {
		return instance;
	}

}
```

这个方式是平时大家用的最多的，也是最简单的一种方式。

2、懒汉模式
====

上面的代码虽然简单，但是有一个问题——无论这个类是否被使用，都会创建一个instance对象。如果这个创建过程很耗时，比如需要连接10000次数据库(夸张了…:-))，并且这个类还并不一定会被使用，那么这个创建过程就是无用的。怎么办呢？

为了解决这个问题，我们想到了新的解决方案，其实就是一个Lazy-Loaded：

```java
public class IdleSingleton {

	/**
	 * 懒汉式
	 */

	/**
	 * 懒汉单例且多线程场景下非线程安全
	 */

	private static IdleSingleton instance;

	private IdleSingleton() {

	}

	public static IdleSingleton getInstance() {
		if (null == instance) {
			instance = new IdleSingleton();
		}
		return instance;
	}

}
```
这个方式看起来很完美的解决了我们的问题，但是如果有多个线程同时调用getInstance()的时候，有很大可能都会去把INSTANCE实例化一次，导致会创建了多个对象，导致了一个严重的问题 - **单例失效！**

3、同步方法
====

由于多个线程会有竞争问题，自然而然，我们就想到了一个对策，给getInstance加上synchronized，这样看起来好像很完美的样子。

```java
public class SyncIdlerSingleton {

	/**
	 * 懒汉式
	 */

	/**
	 * 线程安全单例模式且速度比较慢
	 */

	private static SyncIdlerSingleton instance;

	private SyncIdlerSingleton() {

	}

	public static synchronized SyncIdlerSingleton getInstance() {
		if (null == instance) {
			instance = new SyncIdlerSingleton();
		}
		return instance;
	}

}
```

但是使用synchronized修饰的方法在高并发下性能是非常慢的，有没有方式提升它的性能呢？

4、同步代码块
====

大家都知道，同步方法比同步代码块的粒度要大，导致性能会差。我们可以试着把粒度变小，把同步的范围缩小。于是，我们继续修改上面的代码。

```java
public class SingletonClass {
 
    private static SingletonClass INSTANCE = null;

    private SingletonClass() {}

    public static SingletonClass getInstance() {
        synchronized (SingletonClass.class) {
            if (INSTANCE == null) {
                INSTANCE = new SingletonClass();
            }
        }
        return INSTANCE;
    }
}
```
但是其实这样的修改显然并没有提升什么性能，我们试着再改改。

5、double-check
====

我们可以使用double-check锁来解决这个问题。

```java
public class SingletonClass {
    private static SingletonClass INSTANCE = null;

    private SingletonClass() {}

    public static SingletonClass getInstance() {
        if (INSTANCE == null) {                        // 1
            synchronized (SingletonClass.class) {      // 2
                if (INSTANCE == null) {                 // 3
                    INSTANCE = new SingletonClass();  // 4
                }
            }
        }
        return INSTANCE;
    }
}
```

这样一看，既能够兼顾效率，又能够兼顾安全，貌似一切都特别完美，但是真的是这样吗？

6、追溯问题
====

上面代码不仔细看还真的找不出问题，但是我们忽略了第4部的实例化其实是分了3步进行的。

```java
memory = allocate();    // 1:分配对象的内存空间
ctorInstance(memory);    // 2:初始化对象
instance = memory;        // 3:设置instance指向刚分配的内存地址
```

上面3行伪代码中的2和3之间，可能会被重排序（在一些JIT编译器上，这种重排序是真实发生的），2和3之间重排序之后，会导致instance分配了内存地址的时候对象还没有被初始化。

在知晓了问题发生的根源之后，我们可以想出两个办法来实现线程安全的延迟初始化。

* 不允许2和3重排序。
* 允许2和3重排序，但不允许其他线程“看到”这个重排序。

6.1、基于volatile的解决方案
====

为什么用volatile？因为volatile具有禁止重排序的作用，会在2和3之间加上内存屏障，不懂可以参考之前的博文：<a href="http://benjaminwhx.com/2018/05/13/%E3%80%90%E7%BB%86%E8%B0%88Java%E5%B9%B6%E5%8F%91%E3%80%91%E5%86%85%E5%AD%98%E6%A8%A1%E5%9E%8B%E4%B9%8Bvolatile/">【细谈Java并发】内存模型之volatile</a>，解决后代码如下：

```java
public class DCLIdlerSingletonPreventReflection {

	/**
	 * 懒汉式
	 */

	private static volatile DCLIdlerSingletonPreventReflection instance;

	private DCLIdlerSingletonPreventReflection() {
		if (instance != null) {
			throw new IllegalStateException();
		}
	}

	public static DCLIdlerSingletonPreventReflection getInstance() {
		if (null == instance) {
			synchronized (DCLIdlerSingletonPreventReflection.class) {
				if (null == instance) {
					instance = new DCLIdlerSingletonPreventReflection();
				}
			}
		}
		return instance;
	}

}
```

6.2、基于类初始化的解决方案
====

JVM在类的初始化阶段（即在Class被加载后，且被线程使用之前），会执行类的初始化。在执行类的初始化期间，JVM会去获取一个锁。这个锁可以同步多个线程对同一个类的初始化。

基于这个特性，我们可以实现另一种线程安全的延迟初始化方案。

```java
public class StaticCIC {

	/**
	 * 静态内部类单例模式
	 */

	private StaticCIC() {

	}

	public static StaticCIC getInstance() {
		return HelperHodler.INSTANCE;
	}

	private static class HelperHodler {

		private static final StaticCIC INSTANCE = new StaticCIC();

	}

}
```
假设两个线程并发执行getInstance方法，下面是执行的示意图。

![单例模式获取对象](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E5%8D%95%E4%BE%8B%E6%A8%A1%E5%BC%8F%E8%8E%B7%E5%8F%96%E5%AF%B9%E8%B1%A1.png)

初始化一个类，包括**执行这个类的静态初始化**和**初始化在这个类中声明的静态字段**。根据Java语言规范，在首次发生下列任意一种情况时，一个类或接口类型T将被立即初始化。

* T是一个类，而且一个T类型的实例被创建。
* T是一个类，且T中声明的一个静态方法被调用。
* T中声明的一个静态字段被赋值。
* T中声明的一个静态字段被使用，而且这个字段不是一个常量字段。
* T是一个顶级类，而且一个断言语言嵌套在T内部被执行。

Java语言规则规定，对于每一个类或接口C，都有一个唯一的初始化锁LC与之对应。从C到LC的映射，由JVM的具体实现去自由实现。JVM在类初始化期间会获取这个初始化锁，并且每个线程至少获取一次锁来确保这个类已经被初始化过了。

类初始化阶段可以分为5个阶段

**第一阶段：通过在Class对象上同步（即获取Class对象的初始化锁），来控制类或接口的初始化。这个获取锁的线程会一直等待，直到当前线程能够获取到这个初始化锁。**

* 线程A尝试获取Class对象的初始化锁。这里假设线程A获取到了初始化锁。
* 线程B尝试获取Class对象的初始化锁，由于线程A获取了锁，线程B将一直等待获取初始化锁。
* 线程A看到线程还未被初始化（因为读取到state = noInitialization），线程设置state = initializing。
* 线程A释放初始化锁。

**第二阶段：线程A执行类的初始化，同时线程B在初始化锁对应的condition上等待。**

* 线程A执行类的静态初始化和初始化类中声明的静态字段。
* 线程B获取到初始化锁。
* 线程B读取到state = initializing。
* 线程B释放初始化锁。
* 线程B在初始化锁的condition中等待。

**第三阶段：线程A设置state = initialized，然后唤醒condition中等待的所有线程**

* 线程A获取初始化锁。
* 线程A设置state = initialized。
* 线程A唤醒在condition中等待的所有线程。
* 线程A释放初始化锁。
* 线程A的初始化处理过程完成。

**第四阶段：线程B结束类的初始化处理**

* 线程B获取初始化锁。
* 线程B读取到state = initialized。
* 线程B释放初始化锁。
* 线程B的类初始化处理过程完成。

**第五阶段：线程C执行类的初始化的处理**

* 线程C获取初始化锁。
* 线程C读取到state = initialized。
* 线程C释放初始化锁。
* 线程C的类初始化处理过程完成。

6.3、比较
====

通过对比基于volatile的双重检查锁定的方案和基于类初始化的方案，我们会发现基于类初始化的方案的实现代码更简洁。但基于volatile的双重检查锁定的方案有一个额外的优势：除了可以对静态字段实现延迟初始化外，还可以对实例字段实现延迟初始化。

字段延迟初始化降低了初始化类或创建实例的开销，但增加了访问被延迟初始化的字段的开销。在大多数时候，正常的初始化要优于延迟初始化。如果确实需要对实例字段使用线程安全的延迟初始化，请使用上面介绍的基于volatile的延迟初始化的方案；如果确实需要对静态字段使用线程安全的延迟初始化，请使用上面介绍的基于类初始化的方案。
