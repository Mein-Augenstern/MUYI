## 看在前面

* JVM内存结构 VS Java内存模型 VS Java对象模型：https://www.hollischuang.com/archives/2509

* Java并发基础之内存模型：https://javadoop.com/post/java-memory-model

## Java并发基础之内存模型

关于 Java 并发也算是写了好几篇文章了，本文将介绍一些比较基础的内容，注意，阅读本文需要一定的并发基础。

本文的主要目的是让大家对于并发程序中的重排序、内存可见性以及原子性有一定的了解，同时要能准确理解 synchronized、volatile、final 几个关键字的作用。

另外，本文还对**双重检查形式的单例模式为什么需要使用 volatile** 做了深入的解释。

<!-- toc -->

## 并发三问题

这节将介绍重排序、内存可见性以及原子性相关的知识，这些也是并发程序为什么难写的原因。

### 1. 重排序

请读者先在自己的电脑上运行一下以下程序：

```java
public class Test {
    
    private static int x = 0, y = 0;
    private static int a = 0, b =0;

    public static void main(String[] args) throws InterruptedException {
        int i = 0;
        for(;;) {
            i++;
            x = 0; y = 0;
            a = 0; b = 0;
            CountDownLatch latch = new CountDownLatch(1);

            Thread one = new Thread(() -> {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                }
                a = 1;
                x = b;
            });

            Thread other = new Thread(() -> {
                try {
                    latch.await();
                } catch (InterruptedException e) {
                }
                b = 1;
                y = a;
            });
            one.start();other.start();
            latch.countDown();
            one.join();other.join();

            String result = "第" + i + "次 (" + x + "," + y + "）";
            if(x == 0 && y == 0) {
                System.err.println(result);
                break;
            } else {
                System.out.println(result);
            }
        }
    }
}
```

几秒后，我们就可以得到 x == 0 && y == 0 这个结果，仔细看看代码就会知道，如果不发生重排序的话，这个结果是不可能出现的。

**重排序由以下几种机制引起：**

1. 编译器优化：对于没有数据依赖关系的操作，编译器在编译的过程中会进行一定程度的重排。

   > 大家仔细看看线程 1 中的代码，编译器是可以将 a = 1 和 x = b 换一下顺序的，因为它们之间没有数据依赖关系，同理，线程 2 也一样，那就不难得到 x == y == 0 这种结果了。

2. 指令重排序：CPU 优化行为，也是会对不存在数据依赖关系的指令进行一定程度的重排。

   > 这个和编译器优化差不多，就算编译器不发生重排，CPU 也可以对指令进行重排，这个就不用多说了。

3. 内存系统重排序：内存系统没有重排序，但是由于有缓存的存在，使得程序整体上会表现出乱序的行为。

   > 假设不发生编译器重排和指令重排，线程 1 修改了 a 的值，但是修改以后，a 的值可能还没有写回到主存中，那么线程 2 得到 a == 0 就是很自然的事了。同理，线程 2 对于 b 的赋值操作也可能没有及时刷新到主存中。

### 2. 内存可见性

前面在说重排序的时候，也说到了内存可见性的问题，这里再啰嗦一下。

线程间的对于共享变量的可见性问题不是直接由多核引起的，而是由多缓存引起的。如果每个核心共享同一个缓存，那么也就不存在内存可见性问题了。

现代多核 CPU 中每个核心拥有自己的一级缓存或一级缓存加上二级缓存等，问题就发生在每个核心的独占缓存上。每个核心都会将自己需要的数据读到独占缓存中，数据修改后也是写入到缓存中，然后等待刷入到主存中。所以会导致有些核心读取的值是一个**过期**的值。

Java 作为高级语言，屏蔽了这些底层细节，用 JMM 定义了一套读写内存数据的规范，虽然我们不再需要关心一级缓存和二级缓存的问题，但是，JMM 抽象了主内存和本地内存的概念。

所有的共享变量存在于主内存中，**每个线程有自己的本地内存**，线程读写共享数据也是通过本地内存交换的，所以可见性问题依然是存在的。这里说的本地内存并不是真的是一块给每个线程分配的内存，而是 JMM 的一个抽象，是对于寄存器、一级缓存、二级缓存等的抽象。

### 3. 原子性

在本文中，原子性不是重点，它将作为并发编程中需要考虑的一部分进行介绍。

说到原子性的时候，大家应该都能想到 long 和 double，它们的值需要占用 64 位的内存空间，Java 编程语言规范中提到，对于 64 位的值的写入，可以分为两个 32 位的操作进行写入。本来一个整体的赋值操作，被拆分为低 32 位赋值和高 32 位赋值两个操作，中间如果发生了其他线程对于这个值的读操作，必然就会读到一个奇怪的值。

这个时候我们要使用 volatile 关键字进行控制了，JMM 规定了对于 volatile long 和 volatile double，JVM 需要保证写入操作的原子性。

另外，对于引用的读写操作始终是原子的，不管是 32 位的机器还是 64 位的机器。

Java 编程语言规范同样提到，鼓励 JVM 的开发者能保证 64 位值操作的原子性，也鼓励使用者尽量使用 volatile 或使用正确的同步方式。关键词是”鼓励“。

> 在 64 位的 JVM 中，不加 volatile 也是可以的，同样能保证对于 long 和 double 写操作的原子性。关于这一点，我没有找到官方的材料描述它，如果读者有相关的信息，希望可以给我反馈一下。

## Java 对于并发的规范约束

并发问题使得我们的代码有可能会产生各种各样的执行结果，显然这是我们不能接受的，所以 Java 编程语言规范需要规定一些基本规则，JVM 实现者会在这些规则的约束下来实现 JVM，然后开发者也要按照规则来写代码，这样写出来的并发代码我们才能准确预测执行结果。下面进行一些简单的介绍。

### Synchronization Order

Java 语言规范对于同步定义了一系列的规则：[17.4.4. Synchronization Order](https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html#jls-17.4.4)，包括了如下同步关系：

- 对于监视器 m 的解锁与所有后续操作对于 m 的加锁同步

- 对 volatile 变量 v 的写入，与所有其他线程后续对 v 的读同步

- 启动线程的操作与线程中的第一个操作同步。

- 对于每个属性写入默认值（0， false，null）与每个线程对其进行的操作同步。

  尽管在创建对象完成之前对对象属性写入默认值有点奇怪，但从概念上来说，每个对象都是在程序启动时用默认值初始化来创建的。

- 线程 T1 的最后操作与线程 T2 发现线程 T1 已经结束同步。

  线程 T2 可以通过 T1.isAlive() 或 T1.join() 方法来判断 T1 是否已经终结。

- 如果线程 T1 中断了 T2，那么线程 T1 的中断操作与其他所有线程发现 T2 被中断了同步（通过抛出 InterruptedException 异常，或者调用 Thread.interrupted 或 Thread.isInterrupted ）

### Happens-before Order

两个操作可以用 happens-before 来确定它们的执行顺序，如果一个操作 happens-before 于另一个操作，那么我们说第一个操作对于第二个操作是可见的。

如果我们分别有操作 x 和操作 y，我们写成 **hb(x, y)** 来表示 **x happens-before y**。以下几个规则也是来自于 Java 8 语言规范 [Happens-before Order](https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html#jls-17.4.5)：

- 如果操作 x 和操作 y 是同一个线程的两个操作，并且在代码上操作 x 先于操作 y 出现，那么有 hb(x, y)

- 对象构造方法的最后一行指令 happens-before 于 finalize() 方法的第一行指令。
- 如果操作 x 与随后的操作 y 构成同步，那么 hb(x, y)。这条说的是前面一小节的内容。
- hb(x, y) 和 hb(y, z)，那么可以推断出 hb(x, z)

这里再提一点，x happens-before y，并不是说 x 操作一定要在 y 操作之前被执行，而是说 x 的执行结果对于 y 是可见的，只要满足可见性，发生了重排序也是可以的。

## synchronized 关键字

> monitor，这里翻译成监视器锁，为了大家理解方便。

synchronized 这个关键字大家都用得很多了，这里不会教你怎么使用它，我们来看看它对于内存可见性的影响。

一个线程在获取到监视器锁以后才能进入 synchronized 控制的代码块，一旦进入代码块，首先，该线程对于共享变量的缓存就会失效，因此 synchronized 代码块中对于共享变量的读取需要从主内存中重新获取，也就能获取到最新的值。

退出代码块的时候的，会将该线程写缓冲区中的数据刷到主内存中，所以在 synchronized 代码块之前或 synchronized 代码块中对于共享变量的操作随着该线程退出 synchronized 块，会立即对其他线程可见（这句话的前提是其他读取共享变量的线程会从主内存读取最新值）。

因此，我们可以总结一下：线程 a 对于进入 synchronized 块之前或在 synchronized 中对于共享变量的操作，对于后续的持有同一个监视器锁的线程 b 可见。虽然是挺简单的一句话，请读者好好体会。

注意一点，在进入 synchronized 的时候，并不会保证之前的写操作刷入到主内存中，synchronized 主要是保证退出的时候能将本地内存的数据刷入到主内存。

## 单例模式中的双重检查

我们趁热打铁，为大家解决下单例模式中的双重检查问题。关于这个问题，大神们发过[文章](http://www.cs.umd.edu/~pugh/java/memoryModel/DoubleCheckedLocking.html)对此进行阐述了，这里搬运一下。

> 来膜拜下文章署名中的大神们：[David Bacon](http://www.research.ibm.com/people/d/dfb) (IBM Research) Joshua Bloch (Javasoft), [Jeff Bogda](http://www.cs.ucsb.edu/~bogda/), Cliff Click (Hotspot JVM project), [Paul Haahr](http://www.webcom.com/~haahr/), [Doug Lea](http://www.cs.oswego.edu/~dl), [Tom May](mailto:tom@go2net.com), [Jan-Willem Maessen](http://www.csg.lcs.mit.edu/~earwig/), [Jeremy Manson](http://www.cs.umd.edu/~jmanson), [John D. Mitchell (jGuru)](http://www.jguru.com/johnm) Kelvin Nilsen, [Bill Pugh](http://www.cs.umd.edu/~pugh), [Emin Gun Sirer](http://www.cs.washington.edu/homes/egs/)，至少 Joshua Bloch 和 Doug Lea 大家都不陌生吧。

废话少说，看以下单例模式的写法：

```java
public class Singleton {

    private static Singleton instance = null;
    
    private int v;
    private Singleton() {
        this.v = 3;
    }

    public static Singleton getInstance() {
        if (instance == null) { // 1. 第一次检查
            synchronized (Singleton.class) { // 2
                if (instance == null) { // 3. 第二次检查
                    instance = new Singleton(); // 4
                }
            }
        }
        return instance;
    }
}
```

很多人都知道上述的写法是不对的，但是可能会说不清楚到底为什么不对。

我们假设有两个线程 a 和 b 调用 getInstance() 方法，假设 a 先走，一路走到 4 这一步，执行  `instance = new Singleton()` 这句代码。

instance = new Singleton() 这句代码首先会申请一段空间，然后将各个属性初始化为零值(0/null)，执行构造方法中的属性赋值[1]，将这个对象的引用赋值给 instance[2]。在这个过程中，[1] 和 [2] 可能会发生重排序。

此时，线程 b 刚刚进来执行到 1（看上面的代码块），就有可能会看到 instance 不为 null，然后线程 b 也就不会等待监视器锁，而是直接返回 instance。问题是这个 instance 可能还没执行完构造方法（线程 a 此时还在 4 这一步），所以线程 b 拿到的 instance 是**不完整的**，它里面的属性值可能是初始化的零值(0/false/null)，而不是线程 a 在构造方法中指定的值。

> 回顾下前面的知识，分析下这里为什么会有这个问题。
>
> 1、编译器可以将构造方法内联过来，之后再发生重排序就很容易理解了。
>
> 2、即使不发生代码重排序，线程 a 对于属性的赋值写入到了线程 a 的本地内存中，此时对于线程 b 不可见。

最后提一点，如果线程 a 从 synchronized 块出来了，那么 instance 一定是正确构造的**完整**实例，这是我们前面说过的 synchronized 的内存可见性保证。

—————分割线————— 

对于大部分读者来说，这一小节其实可以结束了，很多读者都知道，解决方案是使用 volatile 关键字，这个我们在介绍 volatile 的时候再说。当然，如果你还有耐心，也可以继续看看本小节。

我们看下下面这段代码，看看它能不能解决我们之前碰到的问题。

```java
public static Singleton getInstance() {
    if (instance == null) { //
        Singleton temp;
        synchronized (Singleton.class) { //
            temp = instance;
            if (temp == null) { //
                synchronized (Singleton.class) { // 内嵌一个 synchronized 块
                    temp = new Singleton();
                }
                instance = temp; //
            }
        }
    }
    return instance;
}
```

上面这个代码很有趣，想利用 synchronized 的内存可见性语义，不过这个解决方案还是失败了，我们分析下。

前面我们也说了，**synchronized 在退出的时候，能保证 synchronized 块中对于共享变量的写入一定会刷入到主内存中**。也就是说，上述代码中，内嵌的 synchronized 结束的时候，temp 一定是完整构造出来的，然后再赋给 instance 的值一定是好的。

可是，synchronized 保证了释放监视器锁之前的代码一定会在释放锁之前被执行（如 temp 的初始化一定会在释放锁之前执行完 ），但是没有任何规则规定了，释放锁之后的代码不可以在释放锁之前先执行。

也就是说，代码中释放锁之后的行为 `instance = temp` 完全可以被提前到前面的 synchronized 代码块中执行，那么前面说的重排序问题就又出现了。

最后扯一点，如果所有的属性都是使用 final 修饰的，其实之前介绍的双重检查是可行的，不需要加 volatile，这个等到 final 那节再介绍。

## volatile 关键字

大部分开发者应该都知道怎么使用这个关键字，只是可能不太了解个中缘由。

**如果你下次面试的时候有人问你 volatile 的作用，记住两点：内存可见性和禁止指令重排序。**

### volatile 的内存可见性

我们还是用 JMM 的**主内存**和**本地内存**抽象来描述，这样比较准确。还有，并不是只有 Java 语言才有 volatile 关键字，所以后面的描述一定要建立在 Java 跨平台以后抽象出了内存模型的这个大环境下。

还记得 synchronized 的语义吗？进入 synchronized 时，使得本地缓存失效，synchronized 块中对共享变量的读取必须从主内存读取；退出 synchronized 时，会将进入 synchronized 块之前和 synchronized 块中的写操作刷入到主存中。

volatile 有类似的语义，读一个 volatile 变量之前，需要先使相应的本地缓存失效，这样就必须到主内存读取最新值，写一个 volatile 属性会立即刷入到主内存。所以，volatile 读和 monitorenter 有相同的语义，volatile 写和 monitorexit 有相同的语义。

> 内容来自GPT4.0
> 
> 问题：其他线程是如何检测到volatile变量的内容发生变化的呢
> 
> 在Java内存模型中，每个线程都有自己的工作内存（也叫线程栈），用于存储正在使用的变量的副本。同时，所有线程共享主内存，其中存储了所有变量的原始值。当变量被声明为volatile之后，它会有两个主要影响：
>
> 当线程对一个volatile变量进行写操作时，它不仅会改变自己工作内存中的副本，还会立即将这个新值更新到主内存中去。
> 
> 当其他线程要读取这个volatile变量时，它会从主内存中直接读取这个变量的最新值，而不是从自己工作内存中的副本读取。
> 
> 这样一来，当线程a更新了volatile变量后，它的值会被刷新到主内存中。这个新值对于线程b来说是可见的，因为线程b在读取volatile变量时会直接从主内存中读取。这个机制确保了volatile变量的可见性。
> 
> 这个过程是由JMM中的“内存屏障”机制保证的。内存屏障是一种CPU指令，它提供了一种限制不同类型（读或写）操作的执行顺序，保证了特定条件下的可见性和顺序性。对于volatile变量，JMM插入了特定的内存屏障来保证volatile的写-读操作的可见性。这意味着：
> 
> 在每次volatile写操作后，都会插入一个写屏障（Store Barrier），强制将此变量的新值立即写入主内存。
> 
> 在每次volatile读操作前，都会插入一个读屏障（Load Barrier），强制从主内存读取变量的最新值而不是缓存。
> 
> 通过这样的机制，其他线程就可以检测到volatile变量内容的变化。
>
> 其中内存屏障就像是这样的一个约定机制，它确保了在进行一系列重要活动（这里指的是存取数据）前后，都有一个清晰的、准确的交接点。通过这样的机制，在多线程环境中工作的各个线程就能确保它们之间传递的信息是一致且最新的。

### volatile 的禁止重排序

大家还记得之前的双重检查的单例模式吧，前面提到，加个 volatile 能解决问题。其实就是利用了 volatile 的禁止重排序功能。

volatile 的禁止重排序并不局限于两个 volatile 的属性操作不能重排序，而且是 volatile 属性操作和它周围的普通属性的操作也不能重排序。

之前 instance = new Singleton() 中，如果 instance 是 volatile 的，那么对于 instance 的赋值操作（赋一个引用给 instance 变量）就不会和构造函数中的属性赋值发生重排序，能保证构造方法结束后，才将此对象引用赋值给 instance。

根据 volatile 的内存可见性和禁止重排序，那么我们不难得出一个推论：线程 a 如果写入一个 volatile 变量，此时线程 b 再读取这个变量，那么此时对于线程 a 可见的所有属性对于线程 b 都是可见的。

### volatile 小结

1. volatile 修饰符适用于以下场景：某个属性被多个线程共享，其中有一个线程修改了此属性，其他线程可以立即得到修改后的值。在并发包的源码中，它使用得非常多。
2. volatile 属性的读写操作都是无锁的，它不能替代 synchronized，因为**它没有提供原子性和互斥性**。因为无锁，不需要花费时间在获取锁和释放锁上，所以说它是低成本的。
3. volatile 只能作用于属性，我们用 volatile 修饰属性，这样 compilers 就不会对这个属性做指令重排序。
4. volatile 提供了可见性，任何一个线程对其的修改将立马对其他线程可见。volatile 属性不会被线程缓存，始终从主存中读取。
5. volatile 提供了 happens-before 保证，对 volatile 变量 v 的写入 happens-before 所有其他线程后续对 v 的读操作。
6. volatile 可以使得 long 和 double 的赋值是原子的，前面在说原子性的时候提到过。

## final 关键字

用 final 修饰的类不可以被继承，用 final 修饰的方法不可以被覆写，用 final 修饰的属性一旦初始化以后不可以被修改。当然，我们不关心这些段子，这节，我们来看看 final 带来的内存可见性影响。

之前在说双重检查的单例模式的时候，提过了一句，如果所有的属性都使用了 final 修饰，那么 volatile 也是可以不要的，这就是 final 带来的可见性影响。

在对象的构造方法中设置 final 属性，**同时在对象初始化完成前，不要将此对象的引用写入到其他线程可以访问到的地方**（不要让引用在构造函数中逸出）。如果这个条件满足，当其他线程看到这个对象的时候，那个线程始终可以看到正确初始化后的对象的 final 属性。

上面说得很明白了，final 属性的写操作不会和此引用的赋值操作发生重排序，如：

```java
x.finalField = v; ...; sharedRef = x;
```

如果你还想查看更多的关于 final 的介绍，可以移步到我之前翻译的 Java 语言规范的 [final属性的语义](/post/Threads-And-Locks-md#17.5.%20final%20%E5%B1%9E%E6%80%A7%E7%9A%84%E8%AF%AD%E4%B9%89%EF%BC%88final%20Field%20Semantics%EF%BC%89) 部分。


## 小结

之前翻译过 Java8 语言规范《[深入分析 java 8 编程语言规范：Threads and Locks](https://javadoop.com/post/Threads-And-Locks-md)》，本文中的很多知识是和它相关的，不过那篇直译的文章的可读性差了些，希望本文能给读者带来更多的收获。

描述该类知识需要非常严谨的语言描述，虽然我仔细检查了好几篇，但还是担心有些地方会说错，一来这些内容的正误非常受我自身的知识积累影响，二来也和我在行文中使用的话语有很大的关系。希望读者能帮助指正我表述错误的地方。

**参考资料：**

JSR 133：https://jcp.org/en/jsr/detail?id=133

The "Double-Checked Locking is Broken" Declaration：http://www.cs.umd.edu/~pugh/java/memoryModel/DoubleCheckedLocking.html

美团点评技术团队：https://tech.meituan.com/java-memory-reordering.html

（全文完）

> update：2018-03-22 留个小问题给读者
>
> **我们不难得出一个推论：线程 a 如果写入一个 volatile 变量，此时线程 b 再读取这个变量，那么此时对于线程 a 可见的所有属性对于线程 b 都是可见的。**
>
> 文中我写了上面这么一句，读者可以考虑下这个结论是怎么推出来的。
