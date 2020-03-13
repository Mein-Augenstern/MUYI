看在前面
====

> * <a href="https://gitbook.cn/books/5af96867ae95367fe4a8dd7d/index.html">深入解读 Java 堆外内存（直接内存）</a>

一、引言
====

> 很久没有遇到堆外内存相关的问题了，五一假期刚结束，便不期而遇，以前也处理过几次这类问题，但都没有总结，觉得是时候总结一下了。

先来看一个 Demo：在 Demo 中分配堆外内存用的是 allocateDirect 方法，但其内部调用的是 DirectByteBuffer，换言之，DirectByteBuffer 才是实际操作堆外内存的类，因此，本场 Chat 将围绕 DirectByteBuffer 展开。

```java
import java.nio.ByteBuffer;

public class Demo 
{
    public static void main( String[] args )
    {
        //分配一块1024Bytes的堆外内存(直接内存)
        //allocateDirect方法内部调用的是DirectByteBuffer
        ByteBuffer buffer=ByteBuffer.allocateDirect(1024);
        System.out.println(buffer.capacity());
        //向堆外内存中读写数据
        buffer.putInt(0,2018);
        System.out.println(buffer.getInt(0));       
    }
}
```

二、什么是堆外内存？
====

Java 开发者一般都知道堆内存，但却未必了解堆外内存。事实上，除了堆内存，Java 还可以使用堆外内存，也称直接内存（Direct Memory）。顾名思义，堆外内存是在 JVM Heap 之外分配的内存块，并不是 JVM 规范中定义的内存区域，堆外内存用得并不多，但十分重要。

读者也许会有一个疑问：既然已经有堆内存，为什么还要用堆外内存呢？这主要是因为堆外内存在 IO 操作方面的优势，举一个例子：在通信中，将存在于堆内存中的数据 flush 到远程时，需要首先将堆内存中的数据拷贝到堆外内存中，然后再写入 Socket 中；如果直接将数据存到堆外内存中就可以避免上述拷贝操作，提升性能。类似的例子还有读写文件。

目前，很多 NIO 框架 （如 netty，rpc） 会采用 Java 的 DirectByteBuffer 类来操作堆外内存，DirectByteBuffer 类对象本身位于 Java 内存模型的堆中，由 JVM 直接管控、操纵。但是，DirectByteBuffer 中用于分配堆外内存的方法 unsafe.allocateMemory(size) 是个一个 native 方法，本质上是用 C 的 malloc 来进行分配的。分配的内存是系统本地的内存，并不在 Java 的内存中，也不属于 JVM 管控范围，所以在 DirectByteBuffer 一定会存在某种特别的方式来操纵堆外内存。

三、堆外内存创建过程深度解析
====

首先，我们来看一下 DirectByteBuffer 源代码，从中洞悉分配堆外内存的过程：

![DirectByteBuffer 源代码一](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/DirectByteBuffer%20%E6%BA%90%E4%BB%A3%E7%A0%81%E4%B8%80.png)

<h4>第一个重要方法：Bits.reserveMemory(size, cap)</h4>

![Bits.reserveMemory(size, cap)](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/Bits.reserveMemory(size%2C%20cap).png)

![Bits.reserveMemory(size, cap)二](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/Bits.reserveMemory(size%2C%20cap)%E4%BA%8C.png)

该方法用于在系统中保存总分配内存（按页分配）的大小和实际内存的大小，具体执行中需要首先用 tryReserveMemory 方法来判断系统内存（堆外内存）是否足够，具体代码如下：

![Bits.reserveMemory(size, cap)三](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/Bits.reserveMemory(size%2C%20cap)%E4%B8%89.png)

从 Bits.reserveMemory(size, cap) 源码可以看出，其执行过程中，可能遇到以下三种情况：

**1. 最乐观的情况：可用堆外内存足够，reserveMemory 方法返回 true，该方法结束**。

![Bits.reserveMemory(size, cap)四](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/Bits.reserveMemory(size%2C%20cap)%E5%9B%9B.png)

**2. 如果不幸，堆外内存不足，则须进行第二步**：

![Bits.reserveMemory(size, cap)五](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/Bits.reserveMemory(size%2C%20cap)%E4%BA%94.png)

jlra.tryHandlePendingReference() 会触发一次非堵塞的 Reference#tryHandlePending(false)，该方法会将已经被 JVM 垃圾回收的 DirectBuffer 对象的堆外内存释放。

**3. 如果在进行一次堆外内存资源回收后，还不够进行本次堆外内存分配的话，则进行 GC 操作**：

![Bits.reserveMemory(size, cap)六](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/Bits.reserveMemory(size%2C%20cap)%E5%85%AD.png)

```System.gc()``` 会触发一个 Full GC，当然，前提是你没有显示的设置 ```- XX:+DisableExplicitGC``` 来禁用显式 GC。同时，需要注意的是，调用 ```System.gc()``` 并不能够保证 Full GC 马上就能被执行。

调用 ```System.gc()``` 后，接下来会最多进行 9 次循环尝试，仍然通过 ```tryReserveMemory``` 方法来判断是否有足够的堆外内存可供分配操作。每次尝试都会 sleep，以便 Full GC 能够完成，如下代码所示。

![tryReserveMemory一](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/tryReserveMemory.png)

**4. 最不幸的情况，经过 9 次循环尝试后，如果仍然没有足够的堆外内存，将抛出 OutOfMemoryError 异常**。

综上所述，```Bits.reserveMemory(size, cap)``` 方法将依次执行以下操作：

1. 如果可用堆外内存足以分配给当前要创建的堆外内存大小时，直接返回 True；

2. 如果堆外内存不足，则触发一次非堵塞的 ```Reference#tryHandlePending(false)```。该方法会将已经被 JVM 垃圾回收的 DirectBuffer 对象的堆外内存释放；

3. 如果进行一次堆外内存资源回收后，还不够进行本次堆外内存分配的话，则进行 ```System.gc()```。```System.gc()``` 会触发一个 Full GC，需要注意的是，调用 ```System.gc()``` 并不能够保证 Full GC 马上就能被执行。所以在后面打代码中，会进行最多 9 次尝试，看是否有足够的可用堆外内存来分配堆外内存。并且每次尝试之前，都对延迟等待时间，已给 JVM 足够的时间去完成 Full GC 操作。

4. 如果 9 次尝试后依旧没有足够的可用堆外内存来分配本次堆外内存，则抛出 ```OutOfMemoryError(“Direct buffer memory”)``` 异常。

<h4>第二个重要方法：unsafe.allocateMemory(size)</h4>

它是个一个native方法，真正用于分配堆外内存。

<h4>第三个重要方法：Cleaner.create(this, new Deallocator(base, size, cap))</h4>

创建一个 Cleaner，并把代表清理动作的 Deallocator 类绑定，更新 Bits 里的 totalCapacity，并调用 Unsafe 调 free 去释放分配的堆外内存。Cleaner 的触发机制后文将详述。

<h3>小结一下</h3>

使用 DirectByteBuffer 分配堆外内存的时，首先向 Bits 类申请额度，Bits 类有一个全局的 totalCapacity 变量，用以维护当前已经使用的堆外内存值，每次分配内存前都会检查可用空间是否足够，具体方式为：检查是当前申请的内存值与已经使用的内存值之和是否超过总的堆外内存值。如果超过则首先触发一次非堵塞的 Reference#tryHandlePending(false)，该方法会将已经被 JVM 垃圾回收的 DirectBuffer 对象的堆外内存释放；如果仍然不足，则会主动执行 System.gc()，回收内存，sleep 100ms 后进行最多 9 次循环检查，如果堆外内存仍然不足，则抛出 OOM 异常。

如果检查通过，则接着调用 unsafe.allocateMemory 分配内存，并返回内存基地址，然后再调一次 unsafe.setMemory 将这段内存给清零。特别说明一下，unsafe 并非 “不安全”，而是表明该类为 JDK 内部使用，不推荐开发者直接使用。

最后，创建一个 Cleaner，并把代表清理动作的 Deallocator 类绑定，用于更新 Bits 里的 totalCapacity，并调用 Unsafe 调 free 去释放堆外内存。

四、堆外内存额度控制
====

每当使用 DirectByteBuffer 分配堆外内存的时，首先向 Bits 类申请额度，Bits 类内部维护着当前已经使用的堆外内存值，会检查是当前申请的内存值与已经使用的内存值之和是否超过总的堆外内存值，如果超过则会抛 OOM 异常。

那么，可用的堆外内存额度到底是多少呢？

* 第一种情况：如果显式通过 -XX:MaxDirectMemorySize 来指定最大的堆外内存，则为指定值；否则与 JVM 的有关：

* 第二种情况：在 Sun JDK 6 和 OpenJDK 6 里（在 JDK7 基本一致），有这样一段代码，sun.misc.VM

![堆外内存额度](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/%E5%A0%86%E5%A4%96%E5%86%85%E5%AD%98%E9%A2%9D%E5%BA%A6.png)

如上代码所示，如果通过 -Dsun.nio.MaxDirectMemorySize 指定了这个属性，且它大于 -1，则为属性指定的值；如果指定这个属性等于 -1，那么 directMemory = Runtime.getRuntime().maxMemory()，即等于 JVM 运行时的最大内存，具体值将在下面介绍；如果指定这个属性小于 -1，则默认为 64M。

* 第三种情况：Runtime.getRuntime().maxMemory() 具体数值与 JVM 有关

在 HotSpot VM 里的 C++ 实现代码如下：其中 max_capacity() 实际返回的是 –Xmx 设置值减去一个 survivor space 的预留区大小，与堆内大小存很接近。

```java
JNIEXPORT jlong JNICALL
Java_java_lang_Runtime_maxMemory(JNIEnv *env, jobject this)
{
    return JVM_MaxMemory();
}

JVM_ENTRY_NO_ENV(jlong, JVM_MaxMemory(void))
  JVMWrapper("JVM_MaxMemory");
  size_t n = Universe::heap()->max_capacity();
  return convert_size_t_to_jlong(n);
JVM_END
```

五、堆外内存主动回收原理 1：JVM GC 机制回收内存原理
====

堆内存是 JVM 存放对象和数据的区域，堆是线程共享的，也是 GC 主要的回收区，一个 JVM 实例只存在一个堆内存，其大小可调节，堆内存可分为两部分：新生代+老年代。

> 备注：JDK1.8 及以后已经没有 PermGen（“永久代”），因此，下图堆内存示意图不涉及 PermGen。

![堆内存区域分布](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/%E5%A0%86%E5%86%85%E5%AD%98%E5%8C%BA%E5%9F%9F%E5%88%86%E5%B8%83.png)

先简要介绍一下 JVM GC 机制，JVM GC 可分为两类，如下表所示：

![JVM GC分类](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/JVM%20GC%E5%88%86%E7%B1%BB.png)

存在于堆内的 DirectByteBuffer 对象很小，只有基地址和 Cleaner 等几个空间消耗很小的属性，但它关联着堆外分配的一大段内存，所谓的 “冰山对象” 便是如此。通过前面说的 Cleaner，堆内的 DirectByteBuffer 对象被 GC 时，它背后的堆外内存也会被回收。

上面已经介绍堆内存的 GC 机制，当新生代满了，就会发生 Minor GC；如果此时对象还没失效，就不会被回收；如果经历几次 Minor GC 后仍然存活，对象将被迁移到老生代；当老生代也满了，就会发生 Full GC。

关键点来了：由于 DirectByteBuffer 对象本身的个头很小，只要撑过 Minor GC 进入老年代，即使失效了也能在老生代里继续存活，除非老年代被撑满而触发 Full GC，否则 DirectByteBuffer 对象将不会被回收，就一直在老年代耗着，同时占据着一大片堆外内存不释放。

由于默认情况下，堆内存与堆外内存空间大小一致，存放在堆内老年代里的 DirectByteBuffer 对象虽小，但其关联的堆外内存通常很大，等不到老年代被撑满，堆外内存就直接溢出了，抛出 OOM 异常。所以，如果堆外内存出现泄漏，你查看堆内存通常是看不出什么异常的。

还有一招，当检测到申请额度超限时，显式调用 System.gc() 来触发 Full GC。不过，这道最后的保险其实也不很好，首先它会中断整个进程，然后它让当前线程 sleep 100ms，而且如果 GC 没在 100ms 内完成，它仍然会无情抛出 OOM 异常。

此外，需要特别注意的是：如果进程启动时设置了 - DisableExplicitGC 参数，System.gc() 将会被禁止，程序中显式调用不会生效。设置该参数的本意是防止 System.gc() 被滥用，但同时也掐断了一条保险措施。

鉴于上述分析，堆外内存还是自己主动点回收更好，开源软件 Netty 就是这么做的。

六、堆外内存主动回收原理 2：Cleaner 对象
====

在 DirectByteBuffer(int cap) 方法的最后，有这么一行代码，其中 cleaner 就是用来主动回收堆外内存的：

![cleaner一](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/cleaner%E4%B8%80.png)

Cleaner 类，内部维护了一个 Cleaner 对象的链表，通过 create(Object, Runnable) 方法创建 cleaner 对象，调用自身的 add 方法，将其加入到链表中。同时提供了 clean 方法（如下源码），clean 方法首先将对象自身从链表中删除，以便 Cleaner 对象就可以被 GC 回收掉，然后执行 this.thunk 的 run 方法，thunk 就是由创建时传入的 Runnable 参数，也就是说 clean 只负责触发 Runnable 的 run 方法，至于 Runnable 做什么任务它不关心。

![cleaner二](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/cleaner%E4%BA%8C.png)

接下来，我们来看一下 DirectByteBuffer 传进来的 Runnable 到底是什么？如下代码：

![cleaner三](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/cleaner%E4%B8%89.png)

Deallocator 类的对象就是 DirectByteBuffer 中的 cleaner 传进来的 Runnable 参数类，其中，run 方法中调用 unsafe.freeMemory 释放堆外内存，然后更新 Bits 里已使用的内存数据。

从上述分析可以看出，释放堆外内存过程并不复杂，只要从 DirectByteBuffer 里取出那个 sun.misc.Cleaner，然后调用它的 clean() 就行，而 clean() 执行时实际调用的是被绑定的 Deallocator 类的 run 方法，其中再调用 freeMemory 释放内存。

下一节，我们继续分析 Cleaner 和 GC 是如何有机关联起来的？

七、堆外内存主动回收原理 3：Cleaner 如何与 GC 相关联？
====

虽然 GC 机制无法直接回收 DirectByteBuffer 分配的堆外内存，但 DirectByteBuffer 对象是 Java 对象，存在于 Java 堆中，在 GC 时会扫描 DirectByteBuffer 对象是否有影响 GC 的引用，如没有，在回收 DirectByteBuffer 对象的同时也会回收其占用的堆外内存。通过上一节我们知道，回收堆外内存需要调用 Cleaner 的 clean 方法，那么，JVM 的 GC 机制如何跟 Cleaner 关联起来呢？

首先要介绍一个知识点：那就是 Reference，JDK 除了 StrongReference，SoftReference 和 WeakReference 之外，还有一种 PhantomReference，Cleaner 就是 PhantomReference（虚引用）的子类。虚引用必须和引用队列（ReferenceQueue）一起使用，一般用于实现追踪垃圾收集器的回收动作。虚引用不会影响 JVM 是否要 GC 这个对象的判断，当 GC 某个对象时，如果有此对象上还有虚引用，会将虚引用对象插入 ReferenceQueue 队列。PhantomReference 类继承自 Reference，Reference 对象有个 ReferenceQueue 成员，也就是 PhantomReference 对象插入的 ReferenceQueue 队列。

Reference 类内部 static 静态块会启动 ReferenceHandler 线程，线程优先级很高，这个线程是用来处理 JVM 在 GC 过程中交接过来的 reference。如下代码，run 方法是个死循环，一直在那不停的干活，synchronized 块内的这段主要是交接 JVM 扔过来的 reference（就是 pending）。特别地，对于 Clearner 实例调用了 clean 方法。调完之后直接 continue 结束此次循环，这个 reference 并没有进入 queue，也就是说 Cleaner 虚引用并不放入 ReferenceQueue。

![cleaner四](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/cleaner%E5%9B%9B.png)

<h3>小结一下</h3>

对于 Cleaner 对象，当 GC 时发现它除了虚引用外已不可达（持有它的 DirectByteBuffer 对象在 GC 中被回收了，此时，只有 Cleaner 对象唯一保存了堆外内存的数据），就会把它放进 Reference 类 pending list 静态变量里。与此同时，有一个优先级很高的 ReferenceHandler 线程，关注着这个 pending list，如果看到有对象类型是 Cleaner，就会执行它的 clean()。如此，DirectByteBuffer 分配的堆外内存得以释放。

八、人工释放堆外内存
====

通过前面几节的分析，想必读者也已经意识到一个问题：可以人工释放堆外内存，即通过编码调用 DirectByteBuffer 的 cleaner 的 clean 方法来释放堆外内存。但需要注意：cleaner 是 private 访问权限，所以，需使用反射来实现。

