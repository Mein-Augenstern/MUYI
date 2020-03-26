看在前面
====

> * <a href="https://blog.csdn.net/qq_35190492/article/details/104691668">字节跳动面试，问了我乐观锁和悲观锁的AQS、sync和Lock，这个回答让我拿了offer</a>

前言
====

关于线程安全一提到可能就是加锁，在面试中也是面试官百问不厌的考察点，往往能看出面试者的基本功和是否对线程安全有自己的思考。

那锁本身是怎么去实现的呢？又有哪些加锁的方式呢？

我今天就简单聊一下乐观锁和悲观锁，他们对应的实现 CAS ，Synchronized，ReentrantLock

> 你能跟我聊一下CAS么？

CAS（Compare And Swap 比较并且替换）是乐观锁的一种实现方式，是一种轻量级锁，JUC 中很多工具类的实现就是基于 CAS 的。

> CAS 是怎么实现线程安全的？

线程在读取数据时不进行加锁，在准备写回数据时，先去查询原值，操作的时候比较原值是否修改，若未被其他线程修改则写回，若已被修改，则重新执行读取流程。

举个栗子：现在一个线程要修改数据库的name，修改前我会先去数据库查name的值，发现name=“帅丙”，拿到值了，我们准备修改成name=“三歪”，在修改之前我们判断一下，原来的name是不是等于“帅丙”，如果被其他线程修改就会发现name不等于“帅丙”，我们就不进行操作，如果原来的值还是帅丙，我们就把name修改为“三歪”，至此，一个流程就结束了。

有点懵？理一下停下来理一下思路。

Tip：比较+更新 整体是一个原子操作，当然这个流程还是有问题的，我下面会提到。

他是乐观锁的一种实现，就是说认为数据总是不会被更改，我是乐观的仔，每次我都觉得你不会渣我，差不多是这个意思。

![cas+syn_1](https://github.com/DemoTransfer/JavaGuide/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/picture/cas%2Bsyn_1.jpg)

> 你这个栗子不错，他存在什么问题呢？

有，当然是有问题的，我也刚好想提到。

你们看图发现没，要是结果一直就一直循环了，CUP开销是个问题，还有ABA问题和只能保证一个共享变量原子操作的问题。

> 你能分别介绍一下么？

好的，我先介绍一下ABA这个问题，直接口述可能有点抽象，我画图解释一下：

![cas+syn_2](https://github.com/DemoTransfer/JavaGuide/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/picture/cas_syn_2.jpg)

1. 线程1读取了数据A

2. 线程2读取了数据A

3. 线程2通过CAS比较，发现值是A没错，可以把数据A改成数据B

4. 线程3读取了数据B

5. 线程3通过CAS比较，发现数据是B没错，可以把数据B改成了数据A

6. 线程1通过CAS比较，发现数据还是A没变，就写成了自己要改的值

懂了么，我尽可能的幼儿园化了，在这个过程中任何线程都没做错什么，但是值被改变了，线程1却没有办法发现，其实这样的情况出现对结果本身是没有什么影响的，但是我们还是要防范，怎么防范我下面会提到。

**循环时间长开销大的问题**

是因为CAS操作长时间不成功的话，会导致一直自旋，相当于死循环了，CPU的压力会很大。

**注意：会一直自旋，但是也是有个时间上限的**。

**只能保证一个共享变量的原子操作**

CAS操作单个共享变量的时候可以保证原子的操作，多个变量就不行了，JDK 5之后 AtomicReference可以用来保证对象之间的原子性，就可以把多个对象放入CAS中操作。

> 我还记得你之前说在JUC包下的原子类也是通过这个实现的，能举个栗子么？

那我就拿AtomicInteger举例，他的自增函数incrementAndGet（）就是这样实现的，其中就有大量循环判断的过程，直到符合条件才成功。

```java
public final int getAndAddInt(Object var1, long var2, int var4) {
	int var5;
	do {
		var5 = this.getIntVolatile(var1, var2);
	} while(!this.compareAndSwapInt(var1, var2, var5, var5 + var4));

	return var5;
}
```
大概意思就是循环判断给定偏移量是否等于内存中的偏移量，直到成功才退出，看到do while的循环没。

> 乐观锁在项目开发中的实践，有么？

有的就比如我们在很多订单表，流水表，为了防止并发问题，就会加入CAS的校验过程，保证了线程的安全，但是看场景使用，并不是适用所有场景，他的优点缺点都很明显。

> 那开发过程中ABA你们是怎么保证的？

**加标志位**，例如搞个自增的字段，操作一次就自增加一，或者搞个时间戳，比较时间戳的值。

举个栗子：现在我们去要求操作数据库，根据CAS的原则我们本来只需要查询原本的值就好了，现在我们一同查出他的标志位版本字段vision。

之前不能防止ABA的正常修改：

```java
update table set value = newValue where value = #{oldValue}
//oldValue就是我们执行前查询出来的值 
```

带版本号能防止ABA的修改：

```java
update table set value = newValue ，vision = vision + 1 where value = #{oldValue} and vision = #{vision} 
// 判断原来的值和版本号是否匹配，中间有别的线程修改，值可能相等，但是版本号100%不一样
```

除了版本号，像什么时间戳，还有JUC工具包里面也提供了这样的类，想要扩展的小伙伴可以去了解一下。

> 聊一下悲观锁？

悲观锁从宏观的角度讲就是，他是个渣男，你认为他每次都会渣你，所以你每次都提防着他。

我们先聊下JVM层面的synchronized：

synchronized加锁，synchronized 是最常用的线程同步手段之一，上面提到的CAS是乐观锁的实现，synchronized就是悲观锁了。

> 它是如何保证同一时刻只有一个线程可以进入临界区呢？

synchronized，代表这个方法加锁，相当于不管哪一个线程（例如线程A），运行到这个方法时,都要检查有没有其它线程B（或者C、 D等）正在用这个方法(或者该类的其他同步方法)，有的话要等正在使用synchronized方法的线程B（或者C 、D）运行完这个方法后再运行此线程A，没有的话，锁定调用者，然后直接运行。

我分别从他对对象、方法和代码块三方面加锁，去介绍他怎么保证线程安全的：

* synchronized 对对象进行加锁，在 JVM 中，对象在内存中分为三块区域：对象头（Header）、实例数据（Instance
Data）和对齐填充（Padding）。

* 对象头：我们以Hotspot虚拟机为例，Hotspot的对象头主要包括两部分数据：Mark Word（标记字段）、Klass Pointer（类型指针）。

    * Mark Word：默认存储对象的HashCode，分代年龄和锁标志位信息。它会根据对象的状态复用自己的存储空间，也就是说在运行期间Mark Word里存储的数据会随着锁标志位的变化而变化。
    
    * Klass Point：对象指向它的类元数据的指针，虚拟机通过这个指针来确定这个对象是哪个类的实例。
    
你可以看到在对象头中保存了锁标志位和指向 monitor 对象的起始地址，如下图所示，右侧就是对象对应的 Monitor 对象。

![cas_syn_3](https://github.com/DemoTransfer/JavaGuide/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/picture/cas_syn_3.jpg)


当 Monitor 被某个线程持有后，就会处于锁定状态，如图中的 Owner 部分，会指向持有 Monitor 对象的线程。

另外 Monitor 中还有两个队列分别是EntryList和WaitList，主要是用来存放进入及等待获取锁的线程。

如果线程进入，则得到当前对象锁，那么别的线程在该类所有对象上的任何操作都不能进行。

> 在对象级使用锁通常是一种比较粗糙的方法，为什么要将整个对象都上锁，而不允许其他线程短暂地使用对象中其他同步方法来访问共享资源？

如果一个对象拥有多个资源，就不需要只为了让一个线程使用其中一部分资源，就将所有线程都锁在外面。

由于每个对象都有锁，可以如下所示使用虚拟对象来上锁：

```java
class FineGrainLock{

  　MyMemberClassx,y;

  　Object xlock = new Object(), ylock = newObject();

  　public void foo(){
  　    synchronized(xlock){
  　    	//accessxhere
  　    }
  　    //dosomethinghere-butdon'tusesharedresources
  　    synchronized(ylock){
  　    	//accessyhere
  　    }
  　}

    public void bar(){
  　    synchronized(this){
  　       //accessbothxandyhere
      　}

     　 //dosomethinghere-butdon'tusesharedresources
    }

}
```

* synchronized 应用在方法上时，在字节码中是通过方法的 ACC_SYNCHRONIZED 标志来实现的。

我反编译了一小段代码，我们可以看一下我加锁了一个方法，在字节码长啥样，flags字段瞩目：

```java
synchronized void test();
  descriptor: ()V
  flags: ACC_SYNCHRONIZED
  Code:
    stack=0, locals=1, args_size=1
       0: return
    LineNumberTable:
      line 7: 0
    LocalVariableTable:
      Start  Length  Slot  Name   Signature
          0       1     0  this   Ljvm/ClassCompile;
```

反正其他线程进这个方法就看看是否有这个标志位，有就代表有别的仔拥有了他，你就别碰了。

* synchronized 应用在同步块上时，在字节码中是通过 monitorenter 和 monitorexit 实现的。

**每个对象都会与一个monitor相关联，当某个monitor被拥有之后就会被锁住，当线程执行到monitorenter指令时，就会去尝试获得对应的monitor**。

步骤如下：

1. 每个monitor维护着一个记录着拥有次数的计数器。未被拥有的monitor的该计数器为0，当一个线程获得monitor（执行monitorenter）后，该计数器自增变为 1 。

    1. 当同一个线程再次获得该monitor的时候，计数器再次自增；
    
    2. 当不同线程想要获得该monitor的时候，就会被阻塞。
    
2. 当同一个线程释放 monitor（执行monitorexit指令）的时候，计数器再自减。

当计数器为0的时候，monitor将被释放，其他线程便可以获得monitor。
    
同样看一下反编译后的一段锁定代码块的结果：
    
```java
public void syncTask();
  descriptor: ()V
  flags: ACC_PUBLIC
  Code:
    stack=3, locals=3, args_size=1
       0: aload_0
       1: dup
       2: astore_1
       3: monitorenter  //注意此处，进入同步方法
       4: aload_0
       5: dup
       6: getfield      #2             // Field i:I
       9: iconst_1
      10: iadd
      11: putfield      #2            // Field i:I
      14: aload_1
      15: monitorexit   //注意此处，退出同步方法
      16: goto          24
      19: astore_2
      20: aload_1
      21: monitorexit //注意此处，退出同步方法
      22: aload_2
      23: athrow
      24: return
    Exception table:
    //省略其他字节码.......
```

**小结**

**同步方法和同步代码块底层**都是通过monitor来实现同步的。

两者的区别：同步方法是通过方法中的access_flags中设置ACC_SYNCHRONIZED标志来实现，同步代码块是通过monitorenter和monitorexit来实现。

我们知道了每个对象都与一个monitor相关联，而monitor可以被线程拥有或释放。

> 小伙子我只能说，你确实有点东西，以前我们一直锁synchronized是重量级的锁，为啥现在都不提了？

在多线程并发编程中 synchronized 一直是元老级角色，很多人都会称呼它为重量级锁。

但是，随着 Java SE 1.6 对 synchronized 进行了各种优化之后，有些情况下它就并不那么重，Java SE 1.6 中为了减少获得锁和释放锁带来的性能消耗而引入的偏向锁和轻量级锁。

针对 synchronized 获取锁的方式，JVM 使用了锁升级的优化方式，就是先使用偏向锁优先同一线程然后再次获取锁，如果失败，就升级为 CAS 轻量级锁，如果失败就会短暂自旋，防止线程被系统挂起。最后如果以上都失败就升级为重量级锁。

Tip：本来锁升级的过程我是搞了个贼详细贼复杂的图，但是我发现不便于理解，我就幼儿园化了，所以就有了个简单版本的，先看下复杂版本的：

![cas_syn_4](https://github.com/DemoTransfer/JavaGuide/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/picture/cas_syn_4.jpg)

幼儿园版本：

![cas_syn_5](https://github.com/DemoTransfer/JavaGuide/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/picture/cas_syn_5.jpg)

看到这你如果还想白嫖，我劝你善良，万水千山总是情，不要白嫖行不行？点个赞再走哈哈。

![cas_syn_6](https://github.com/DemoTransfer/JavaGuide/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/picture/cas_syn_6.jpg)

对了锁只能升级，不能降级。

> 还有其他的同步手段么？

ReentrantLock但是在介绍这玩意之前，我觉得我有必要先介绍AQS（AbstractQueuedSynchronizer）。

AQS：也就是队列同步器，这是实现 ReentrantLock 的基础。

AQS 有一个 state 标记位，值为1 时表示有线程占用，其他线程需要进入到同步队列等待，同步队列是一个双向链表。

![cas_syn_7](https://github.com/DemoTransfer/JavaGuide/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/picture/cas_syn_7.jpg)

当获得锁的线程需要等待某个条件时，会进入 condition 的等待队列，等待队列可以有多个。

当 condition 条件满足时，线程会从等待队列重新进入同步队列进行获取锁的竞争。

ReentrantLock 就是基于 AQS 实现的，如下图所示，ReentrantLock 内部有公平锁和非公平锁两种实现，差别就在于新来的线程是否比已经在同步队列中的等待线程更早获得锁。

和 ReentrantLock 实现方式类似，Semaphore 也是基于 AQS 的，差别在于 ReentrantLock 是独占锁，Semaphore 是共享锁。

![cas_syn_8](https://github.com/DemoTransfer/JavaGuide/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/picture/cas_syn_8.jpg)

从图中可以看到，ReentrantLock里面有一个内部类Sync，Sync继承AQS（AbstractQueuedSynchronizer），添加锁和释放锁的大部分操作实际上都是在Sync中实现的。

它有公平锁FairSync和非公平锁NonfairSync两个子类。

ReentrantLock默认使用非公平锁，也可以通过构造器来显示的指定使用公平锁。
