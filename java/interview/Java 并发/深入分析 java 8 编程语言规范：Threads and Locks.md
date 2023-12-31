## 文章来源

1、深入分析 java 8 编程语言规范：Threads and Locks：https://javadoop.com/post/Threads-And-Locks-md

## 正文

---
name: Threads-And-Locks-md
title: 深入分析 java 8 编程语言规范：Threads and Locks
date: 2023-12-31 19:34:05
tags: 
categories: concurrency
---
> **2018-02-27**
>
> 评论区的 **xupeng.zhang** 提出了一个我之前没碰到过的情况，推翻了我之前的一个错误理解，修改了相应的内容。
>
> **2017-11-28**
>
> 更新了 17.1、17.2、17.3，更正一些不合理的描述，修改一些话术，使读者理解起来更容易，这遍更新下来，这三节应该说已经很严谨了，读者如果还有不懂，请在评论区留言。
>
> **2017-11-29**
>
> 更新 17.4 内存模型一节，修改了一些容易引起歧义的描述
>
> **2017-12-11**
>
> 更新 17.5 及其后面的内容，对于 final 的语义介绍还是不够精彩，字分裂和 double、long 值的非原子处理也基本上不需要关心，所以整体来说，吃力不讨好。

在 java 并发编程中，线程和锁永远是最重要的概念。语言规范虽然是规范描述，但是其中也有非常多的知识和最佳实践是值得学习的，相信这篇文章还是可以给很多读者提供学习参考的。

本文主要是**翻译 + 解释** Oracle《[The Java Language Specification, Java SE 8 Edition](https://docs.oracle.com/javase/specs/index.html)》的第17章《[Threads and Locks](https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html)》，原文大概30页pdf，我加入了很多自己的理解，希望能帮大家把规范看懂，并且从中得到很多你一直想要知道但是还不知道的知识。

注意，本文在说 Java 语言规范，不是 JVM 规范，JVM 的实现需要满足语言规范中定义的内容，但是具体的实现细节由各 JVM 厂商自己来决定。所以，语言规范要尽可能严谨全面，但是也不能限制过多，不然会限制 JVM 厂商对很多细节进行性能优化。

我能力有限，虽然已经很用心了，但有些地方我真的不懂，我已经在文中标记出来了。

建议分 3 部分阅读。

1. 将 17.1、17.2、17.3 一起阅读，这里关于线程中的 wait、notify、中断有很多的知识；
2. 17.4 的内存模型比较长，重排序和 happens-before 关系是重点；
3. 剩下的 final、字分裂、double和long的非原子问题，这些都是相对独立的 topic。

<!-- more -->

<div style="text-align: center; font-size: 24px; font-weight: bold">Chapter 17. Threads and Locks</div>

<!-- toc -->


### 前言

在 java 中，线程由 Thread 类表示，用户创建线程的**唯一方式**是创建 Thread 类的一个实例，每一个线程都和这样的一个实例关联。在相应的 Thread 实例上调用 start() 方法将启动一个线程。

如果没有正确使用同步，线程表现出来的现象将会是令人疑惑的、违反直觉的。这个章节将描述多线程编程的语义问题，包括一系列的规则，这些规则定义了**在多线程环境中线程对共享内存中值的修改是否对其他线程立即可见**。java编程语言内存模型定义了统一的内存模型用于屏蔽不同的硬件架构，在没有歧义的情况下，下面将用**内存模型**表示这个概念。

这些语义没有规定多线程的程序在 JVM 的实现上应该怎么执行，而是限定了一系列规则，由 JVM 厂商来满足这些规则，即不管 JVM 的执行策略是什么，表现出来的行为必须是可被接受的。

> 操作系统有自己的内存模型，C/C++ 这些语言直接使用的就是操作系统的内存模型，而 Java 为了屏蔽各个系统的差异，定义了自己的统一的内存模型。
>
> 简单说，Java 开发者不再关心每个 CPU 核心有自己的内存，然后共享主内存。而是把关注点转移到：每个线程都有自己的工作内存，所有线程共享主内存。

### 17.1 同步（synchronization）

Java 提供了多种线程之间通信的机制，其中最基本的就是使用同步 (synchronization)，其使用监视器 (monitor) 来实现。java中的**每个对象都关联了一个监视器**，线程可以对其进行加锁和解锁操作。在同一时间，只有一个线程可以拿到对象上的监视器锁。如果其他线程在锁被占用期间试图去获取锁，那么将会被阻塞直到成功获取到锁。同时，监视器锁可以重入，也就是说如果线程 t 拿到了锁，那么线程 t 可以在解锁之前重复获取锁；每次解锁操作会反转一次加锁产生的效果。

**关于对象关联的监视器内容补充如下：答案来自GPT4.0**
> 问题一：监视器在java对象模型中存在哪个部分？
> 在Java对象模型中，监视器并不是作为对象的一个物理部分存在的。监视器是一种逻辑概念，它与每个Java对象相关联，但是它的存在并不直接体现在对象的内存布局或数据结构中。监视器是由Java虚拟机（JVM）在内部实现的，用于确保对于同步代码的互斥访问。
> 当我们在代码中对一个对象使用synchronized关键字时，JVM 负责管理与该对象相关联的监视器。监视器的锁定和释放是由JVM在运行时处理的，你通常不需要在代码中显式地创建或管理监视器。例如，当一个线程进入一个同步方法时，它自动获得与该方法对象关联的监视器的所有权；当它退出该方法时，监视器被自动释放。
> 监视器的锁功能和等待集合的管理都是由JVM内部支持的。例如，JVM内部可能使用锁对象、记录锁的持有者、管理等待线程集合、实现等待/通知机制等来实现监视器的功能，但这些都对Java程序员是透明的。因此，尽管监视器与每个对象逻辑上关联，它们并不存储在对象的内存结构中，而是由JVM的内部机制来实现。
> 总之，监视器是一个由JVM抽象实现的概念，用于控制同步代码的访问，并确保线程安全。这个概念在Java语言规范中有描述，但是怎样具体实现则取决于JVM的具体实现和优化。

> 问题二：监视器在hotspot虚拟机中的对象模型中存在哪个部分的数据结构中？
> 在HotSpot虚拟机中，每个Java对象在内存中的布局可以分为三部分：对象头（Header）、实例数据（Instance Data）和对齐填充（Padding）。监视器（或称为对象锁）的相关信息是存在于对象头部的一部分。对象头包含了几个重要的信息，如哈希码、垃圾回收信息、锁状态标志或指向锁记录（Lock Record）或监视器（Monitor）的指针。
> 锁状态信息是通过对象头中的一部分来表示的，这部分称为Mark Word。在运行时，Mark Word的内容会根据对象当前的状态变化，包括（但不限于）：
> - 未锁定（Biased Locking未激活）
> - 轻量级锁定
> - 重量级锁定
> - 偏向锁定
> - GC标记
> 可能还有其他信息，如年龄、哈希码等
> 对于重量级锁定，对象头的Mark Word会包含一个指向监视器的指针，该监视器是一个在C++层面实现的更复杂的数据结构，其中包括了等待集合的信息。
> 在Java中，监视器或锁的具体实现细节，比如对象头中的Mark Word和锁的状态，是由JVM的底层实现管理的，这些不是能够通过Java代码直接访问或控制的。而是在JVM的C++代码层面实现的，通常是对程序员是透明的。
> 只有当你深入到JVM的源码层面，比如HotSpot VM的C++代码，才能看到监视器（ObjectMonitor）的具体实现。这涉及到HotSpot VM内部的数据结构和内存管理的细节。对于大多数Java开发者来说，通常不需要也不会直接与这些底层结构打交道。
> 然而，我可以大致描述一下监视器在HotSpot VM中的对象模型存在于数据结构的哪个部分：
> 当多个线程尝试访问同一个同步块时，如果出现了竞争，JVM会首先尝试使用轻量级锁定，这是通过在Mark Word中采用CAS（Compare-and-Swap）操作来实现的。如果轻量级锁定失败且线程竞争激烈，JVM会膨胀锁，将其升级为重量级锁定。在这种情况下，锁的信息就会从轻量级锁定在线程栈上的> 锁记录转变为一个在堆中的监视器对象，并且Mark Word会被更新为指向该监视器。
> 请注意，HotSpot虚拟机及其对象布局可能会随着版本更新和不同配置而发生变化。以上描述是基于某个时间点的HotSpot VM实现，未来的实现可能会有所不同。
> 1、对象头（Object Header）:在对象头中的Mark Word包含了锁信息和其他多种状态信息，例如锁的状态、哈希码、分代年龄等。当对象被锁定时，Mark Word会记录锁的信息。根据锁的状态，这可能是指向栈中的锁记录（轻量级锁）或指向堆中的监视器对象（重量级锁）的指针。
> 2、轻量级锁（Lightweight Lock）:如果一个线程已经持有了锁，其他线程尝试获取这个锁时，JVM会尝试使用轻量级锁的优化，即在当前持有锁的线程的栈帧中创建一个锁记录（Lock Record）。
> 3、重量级锁（Heavyweight Lock）:当轻量级锁的尝试失败时（如有多个线程竞争），锁会膨胀为一个重量级锁。此时，Mark Word会指向一个ObjectMonitor对象，该对象存储在JVM堆或本地内存中。ObjectMonitor对象包含了监视器相关的所有信息，如持有该锁的线程、等待获取该锁的线程列表，以及调用wait()方法而处于等待状态的线程列表。
> 这些底层的实现细节是在HotSpot VM的C++代码中实现的，而不是在Java代码层面。如果你有兴趣了解更多关于JVM内部如何处理锁的信息，你将需要查看HotSpot VM的源代码。HotSpot VM是开源的，你可以在OpenJDK的官方网站找到源代码。
> 要注意的是，对于Java程序员而言，在编写同步代码时通常只需要关注如何使用synchronized关键字、wait()、notify()和notifyAll()方法来正确同步线程间的交互，而不必关心锁的内部实现细节。这些细节被JVM抽象化并隐藏起来，以便提供一个干净且一致的编程接口。

> 锁升级的过程: 锁在HotSpot VM中通常有四种状态：无锁状态、偏向锁状态、轻量级锁状态和重量级锁状态。下面是一个锁如何可能从无锁状态升级至重量级锁状态的步骤：
> 无锁状态（Unlocked）:初始时，对象处于无锁状态。对象头的Mark Word字段包含对象的哈希码、GC年龄等信息。
> 偏向锁状态（Biased Locking）:第一次有线程尝试锁定对象时，JVM会设置对象头的Mark Word，将其指向尝试获取锁的线程的线程ID，这称为偏向锁。后续该线程进入同步块时，不需要再次获取锁，因为对象头标记了该线程已经持有锁。
> 轻量级锁状态（Lightweight Locking）:当另一个线程尝试获取同一个已经处于偏向锁状态的对象时，如果当前持有偏向锁的线程正在执行同步块，偏向锁会被撤销。JVM为每个尝试锁定对象的线程在其调用栈的帧上分配一个锁记录（Lock Record），锁记录中存储了对象头的Mark Word原始值。对象头的Mark Word更新为指向该锁记录。
> 重量级锁状态（Heavyweight Locking）:如果线程尝试获取轻量级锁失败（因为有竞争），那么JVM将从轻量级锁升级到重量级锁。此时，JVM为对象分配一个监视器（Monitor），监视器在堆或本地内存中存储所有关于锁的信息，包括持有锁的线程和等待锁的线程队列。对象头的Mark Word更新为指向这个监视器。
> 在不同情况下，锁可以在这些状态之间转换。锁的升级是一个单向过程：它可以从轻量级升级到重量级，但一旦成为重量级锁，不会降级为轻量级锁。需要注意的是，锁的升级过程会增加额外的开销，因为涉及到更复杂的同步机制。
> 这个过程在可视化上可能类似于：
> 无锁状态 --> 偏向锁状态 --> 轻量级锁状态 --> 重量级锁状态
  |               |                  |                  |
  |               |                  |                  |-- 对象头指向监视器（Monitor）
  |               |                  |-- 对象头指向锁记录（Lock Record）
  |               |-- 对象头包含线程ID（偏向该线程）
  |-- 对象头包含哈希码、GC年龄等信息

synchronized 有以下两种使用方式：

1. synchronized 代码块。synchronized(object) 在对某个对象上执行加锁时，会尝试在该对象的监视器上进行加锁操作，只有成功获取锁之后，线程才会继续往下执行。线程获取到了监视器锁后，将继续执行 synchronized 代码块中的代码，如果代码块执行完成，或者抛出了异常，线程将会自动对该对象上的监视器执行解锁操作。

2. synchronized 作用于方法，称为同步方法。同步方法被调用时，会自动执行加锁操作，只有加锁成功，方法体才会得到执行。如果被 synchronized 修饰的方法是实例方法，那么**这个实例的监视器**会被锁定。如果是 static 方法，线程会锁住相应的 **Class 对象的监视器**。方法体执行完成或者异常退出后，会自动执行解锁操作。

Java语言规范既不要求阻止死锁的发生，也不要求检测到死锁的发生。如果线程要在多个对象上执行加锁操作，那么就应该使用传统的方法来避免死锁的发生，如果有必要的话，需要创建更高层次的不会产生死锁的加锁原语。（原文：Programs where threads hold (directly or indirectly) locks on multiple objects should use conventional techniques for deadlock avoidance, creating higher-level locking primitives that do not deadlock, if necessary.）

java 还提供了其他的一些同步机制，比如对 volatile 变量的读写、使用 java.util.concurrent 包中的同步工具类等。

> **同步**这一节说了 Java 并发编程中最基础的 synchronized 这个关键字，大家一定要理解 synchronize 的锁是什么，它的锁是基于 Java 对象的监视器 monitor，所以任何对象都可以用来做锁。有兴趣的读者可以去了解相关知识，包括偏向锁、轻量级锁、重量级锁等。
>
> 小知识点：**对 Class 对象加锁、对对象加锁，它们之间不构成同步**。synchronized 作用于静态方法时是对 **Class 对象**加锁，作用于实例方法时是对实例加锁。
>
> 面试中经常会问到一个类中的两个 synchronized static 方法之间是否构成同步？构成同步。

### 17.2 等待集合 和 唤醒（Wait Sets and Notification）

每个 java 对象，都关联了一个监视器，也关联了一个**等待集合**。等待集合是一个线程集合。

当对象被创建出来时，它的等待集合是空的，对于向等待集合中添加或者移除线程的操作都是原子的，以下几个操作可以操纵这个等待集合：Object.wait, Object.notify, Object.notifyAll。

等待集合也可能受到线程的中断状态的影响，也受到线程中处理中断的方法的影响。另外，sleep 方法和 join 方法可以感知到线程的 wait 和 notify。

> 这里概括得比较简略，没看懂的读者没关系，继续往下看就是了。
>
> 这节要讲Java线程的相关知识，主要包括：
>
> - Thread 中的 sleep、join、interrupt
> - 继承自 Object 的 wait、notify、notifyAll
> - 还有 Java 的中断，这个概念也很重要
>

#### 17.2.1 等待 （Wait）

 等待操作由以下几个方法引发：wait()，wait(long millisecs)，wait(long millisecs, int nanosecs)。在后面两个重载方法中，如果参数为 0，即 wait(0)、wait(0, 0) 和 wait() 是等效的。

如果调用 wait 方法时没有抛出 InterruptedException 异常，则表示正常返回。

> 前方高能，请读者保持高度精神集中。

我们在线程 t 中对对象 m 调用 m.wait() 方法，n 代表加锁编号，同时还没有相匹配的解锁操作，则下面的其中之一会发生：

- 如果 n 等于 0（如线程 t 没有持有对象 m 的锁），那么会抛出 IllegalMonitorStateException 异常。

  > 注意，如果没有获取到监视器锁，wait 方法是会抛异常的，而且注意这个异常是IllegalMonitorStateException 异常。这是重要知识点，要考。

- 如果线程 t 调用的是 m.wait(millisecs) 或m.wait(millisecs, nanosecs)，形参 millisecs 不能为负数，nanosecs 取值应为 [0, 999999]，否则会抛出 IllegalArgumentException 异常。

- 如果线程 t 被中断，此时中断状态为 true，则 wait 方法将抛出 InterruptedException 异常，并将中断状态设置为 false。

  > 中断，如果读者不了解这个概念，可以参考我在 [AQS(二)](/post/AbstractQueuedSynchronizer-2) 中的介绍，这是非常重要的知识。

- 否则，下面的操作会顺序发生：

  > 注意：到这里的时候，wait 参数是正常的，同时 t 没有被中断，并且线程 t 已经拿到了 m 的监视器锁。

  1.线程 t 会加入到对象 m 的**等待集合**中，执行 **加锁编号 n 对应的解锁操作**

  > 这里也非常关键，前面说了，wait 方法的调用必须是线程获取到了对象的监视器锁，而到这里会进行解锁操作。切记切记。。。
   ```java
   public Object object = new Object();
   void thread1() {
       synchronized (object) { // 获取监视器锁
           try {
               object.wait(); // 这里会解锁，这里会解锁，这里会解锁
               // 顺便提一下，只是解了object上的监视器锁，如果这个线程还持有其他对象的监视器锁，这个时候是不会释放的。
           } catch (InterruptedException e) {
               // do somethings
           }
       }
   }
   ```

  2.线程 t 不会执行任何进一步的指令，直到它从 m 的等待集合中移出（也就是等待唤醒）。在发生以下操作的时候，线程 t 会从 m 的等待集合中移出，然后在之后的某个时间点恢复，并继续执行之后的指令。

  >并不是说线程移出等待队列就马上往下执行，这个线程还需要重新获取锁才行，这里也很关键，请往后看17.2.4中我写的两个简单的例子。

  - 在 m上执行了 notify 操作，而且线程 t 被选中从等待集合中移除。

  - 在 m 上执行了 notifyAll 操作，那么线程 t 会从等待集合中移除。

  - 线程 t 发生了 interrupt 操作。

  - 如果线程 t 是调用 wait(millisecs) 或者 wait(millisecs, nanosecs) 方法进入等待集合的，那么过了millisecs 毫秒或者 (millisecs*1000000+nanosecs) 纳秒后，线程 t 也会从等待集合中移出。

  - JVM 的“假唤醒”，虽然这是不鼓励的，但是这种操作是被允许的，这样 JVM 能实现将线程从等待集合中移出，而不必等待具体的移出指令。

    注意，良好的 Java 编码习惯是，只在循环中使用 wait 方法，这个循环等待某些条件来退出循环。

    > 个人理解wait方法是这么用的：
    >
     ```java
     synchronized(m) {
         while(!canExit) {
           m.wait(10); // 等待10ms; 当然中断也是常用的
           canExit = something();  // 判断是否可以退出循环
     	}
     }
     // 2 个知识点：
     // 1. 必须先获取到对象上的监视器锁
     // 2. wait 有可能被假唤醒
     ```

    每个线程在一系列 **可能导致它从等待集合中移出的事件** 中必须决定一个顺序。这个顺序不必要和其他顺序一致，但是线程必须表现为它是按照那个顺序发生的。

    例如，线程 t 现在在 m 的等待集合中，不管是线程 t 中断还是 m 的 notify 方法被调用，这些操作事件肯定存在一个顺序。如果线程 t 的中断先发生，那么 t 会因为 InterruptedException 异常而从 wait 方法中返回，同时 m 的等待集合中的其他线程（如果有的话）会收到这个通知。如果 m 的 notify 先发生，那么 t 会正常从 wait 方法返回，且不会改变中断状态。

    > 我们考虑这个场景：
    >
    > 线程 1 和线程 2 此时都 wait 了，线程 3 调用了 ：
    ```java
    synchronized (object) {
    	thread1.interrupt(); //1
    	object.notify();  //2
    }
    ```
    > 本来我以为上面的情况 线程1 一定是抛出 InterruptedException，线程2 是正常返回的。
    >
    > 感谢评论留言的 **xupeng.zhang**，我的这个想法是错误的，完全有可能线程1正常返回(即使其中断状态是true)，线程2 一直 wait。

3.线程 t 执行编号为 n 的加锁操作

> 回去看 2  说了什么，线程刚刚从等待集合中移出，然后这里需要重新获取监视器锁才能继续往下执行。

4.如果线程 t 在 2 的时候由于中断而从 m 的等待集合中移出，那么它的中断状态会重置为 false，同时 wait 方法会抛出 InterruptedException 异常。

> 这一节主要在讲线程进出等待集合的各种情况，同时，最好要知道中断是怎么用的，中断的状态重置发生于什么时候。
>
> 这里的 1，2，3，4 的发生顺序非常关键，大家可以仔细再看看是不是完全理解了，之后的几个小节还会更具体地阐述这个，参考代码请看 17.2.4 小节我写的简单的例子。

#### 17.2.2 通知（Notification）

通知操作发生于调用 notify 和 notifyAll 方法。

我们在线程 t 中对对象 m 调用 m.notify() 或 m.notifyAll() 方法，n 代表加锁编号，同时对应的解锁操作没有执行，则下面的其中之一会发生：

- 如果 n 等于 0，抛出 IllegalMonitorStateException 异常，因为线程 t 还没有获取到对象 m 上的锁。

  > 这一点很关键，只有获取到了对象上的监视器锁的线程才可以正常调用 notify，前面我们也说过，调用 wait 方法的时候也要先获取锁

- 如果 n 大于 0，而且这是一个 notify 操作，如果 m 的等待集合不为空，那么等待集合中的线程 u 被选中从等待集合中移出。

  对于哪个线程会被选中而被移出，虚拟机没有提供任何保证，从等待集合中将线程 u 移出，可以让线程 u 得以恢复。注意，恢复之后的线程 u 如果对 m 进行加锁操作将不会成功，直到线程 t 完全释放锁之后。

  > 因为线程 t 这个时候还持有 m 的锁。这个知识点在 17.2.4 节我还会重点说。这里记住，被 notify 的线程在唤醒后是需要重新获取监视器锁的。

- 如果 n 大于 0，而且这是一个 notifyAll 操作，那么等待集合中的所有线程都将从等待集合中移出，然后恢复。

  注意，这些线程恢复后，只有一个线程可以锁住监视器。


> 本小节结束，通知操作相对来说还是很简单的吧。

#### 17.2.3 中断（Interruptions）

中断发生于 Thread.interrupt 方法的调用。

令线程 t 调用线程 u 上的方法 u.interrupt()，其中 t 和 u 可以是同一个线程，这个操作会将 u 的中断状态设置为 true。

> 顺便说说中断状态吧，初学者肯定以为 thread.interrupt() 方法是用来暂停线程的，主要是和它对应中文翻译的“中断”有关。中断在并发中是常用的手段，请大家一定好好掌握。可以将中断理解为线程的状态，它的特殊之处在于设置了中断状态为 true 后，这几个方法会感知到：
>
> 1.  wait(), wait(long), wait(long, int), join(), join(long), join(long, int), sleep(long), sleep(long, int)
>
>    这些方法都有一个共同之处，方法签名上都有`throws InterruptedException`，这个就是用来响应中断状态修改的。
>
> 2. 如果线程阻塞在 InterruptibleChannel 类的 IO 操作中，那么这个 channel 会被关闭。
>
> 3. 如果线程阻塞在一个 Selector 中，那么 select 方法会立即返回。
>
> 如果线程阻塞在以上3种情况中，那么当线程感知到中断状态后（此线程的 interrupt() 方法被调用），会将中断状态**重新设置为 false**，然后执行相应的操作（通常就是跳到 catch 异常处）。
>
> 如果不是以上3种情况，那么，线程的 interrupt() 方法被调用，会将线程的中断状态设置为 true。
>
> 当然，除了这几个方法，我知道的是 LockSupport 中的 park 方法也能自动感知到线程被中断，当然，它不会重置中断状态为 false。我们说了，只有上面的几种情况会在感知到中断后先重置中断状态为 false，然后再继续执行。

另外，如果有一个对象 m，而且线程 u 此时在 m 的等待集合中，那么 u 将会从 m 的等待集合中移出。这会让 u 从 wait 操作中恢复过来，u 此时需要获取 m 的监视器锁，获取完锁以后，发现线程 u 处于中断状态，此时会抛出 InterruptedException 异常。

> 这里的流程：t 设置 u 的中断状态 => u 线程恢复 => u 获取 m 的监视器锁 => 获取锁以后，抛出 InterruptedException 异常。
>
> 这个流程在前面 **wait** 的小节已经讲过了，这也是很多人都不了解的知识点。如果还不懂，可以看下一小节的结束，我的两个简单的例子。
>
> 一个小细节：u 被中断，wait 方法返回，并不会立即抛出 InterruptedException 异常，而是在重新获取监视器锁之后才会抛出异常。

实例方法 thread.isInterrupted() 可以知道线程的中断状态。

调用静态方法 Thread.interrupted() 可以返回当前线程的中断状态，同时将中断状态设置为false。

> 所以说，如果是这个方法调用两次，那么第二次一定会返回 false，因为第一次会重置状态。当然了，前提是两次调用的中间没有发生设置线程中断状态的其他语句。

#### 17.2.4 等待、通知和中断 的交互（Interactions of Waits, Notification, and Interruption）

以上的一系列规范能让我们确定 在等待、通知、中断的交互中 有关的几个属性。

如果一个线程在等待期间，**同时发生了通知和中断**，它将发生：

- 从 wait 方法中正常返回，同时不改变中断状态（也就是说，调用 Thread.interrupted 方法将会返回 true）

- 由于抛出了 InterruptedException 异常而从 wait 方法中返回，中断状态设置为 false

线程可能没有重置它的中断状态，同时从 wait 方法中正常返回，即第一种情况。

> 也就是说，线程是从 notify 被唤醒的，由于发生了中断，所以中断状态为 true

同样的，通知也不能由于中断而丢失。

> 这个要说的是，线程其实是从中断唤醒的，那么线程醒过来，同时中断状态会被重置为 false。

假设 m 的等待集合为 线程集合 s，并且在另一个线程中调用了 m.notify(), 那么将发生：

- 至少有集合 s 中的一个线程正常从 wait 方法返回，或者
- 集合 s 中的所有线程由抛出 InterruptedException 异常而返回。

> 考虑是否有这个场景：x 被设置了中断状态，notify 选中了集合中的线程 x，那么这次 notify 将唤醒线程 x，其他线程（我们假设还有其他线程在等待）不会有变化。
>
> 答案：存在这种场景。因为这种场景是满足上述条件的，而且此时 x 的中断状态是 true。

注意，如果一个线程同时被中断和通知唤醒，同时这个线程通过抛出 InterruptedException 异常从 wait 中返回，那么等待集合中的某个其他线程一定会被通知。



> 下面我们通过 3 个例子简单分析下 **wait、notify、中断** 它们的组合使用。
>
> 第一个例子展示了 wait 和 notify 操作过程中的监视器锁的 持有、释放 的问题。考虑以下操作：

```java
public class WaitNotify {

    public static void main(String[] args) {

        Object object = new Object();

        new Thread(new Runnable() {
            @Override
            public void run() {

                synchronized (object) {
                    System.out.println("线程1 获取到监视器锁");
                    try {
                        object.wait();
                        System.out.println("线程1 恢复啦。我为什么这么久才恢复，因为notify方法虽然早就发生了，可是我还要获取锁才能继续执行。");
                    } catch (InterruptedException e) {
                        System.out.println("线程1 wait方法抛出了InterruptedException异常");
                    }
                }
            }
        }, "线程1").start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (object) {
                    System.out.println("线程2 拿到了监视器锁。为什么呢，因为线程1 在 wait 方法的时候会自动释放锁");
                    System.out.println("线程2 执行 notify 操作");
                    object.notify();
                    System.out.println("线程2 执行完了 notify，先休息3秒再说。");
                    try {
                        Thread.sleep(3000);
                        System.out.println("线程2 休息完啦。注意了，调sleep方法和wait方法不一样，不会释放监视器锁");
                    } catch (InterruptedException e) {

                    }
                    System.out.println("线程2 休息够了，结束操作");
                }
            }
        }, "线程2").start();
    }
}

output：
线程1 获取到监视器锁
线程2 拿到了监视器锁。为什么呢，因为线程1 在 wait 方法的时候会自动释放锁
线程2 执行 notify 操作
线程2 执行完了 notify，先休息3秒再说。
线程2 休息完啦。注意了，调sleep方法和wait方法不一样，不会释放监视器锁
线程2 休息够了，结束操作
线程1 恢复啦。我为什么这么久才恢复，因为notify方法虽然早就发生了，可是我还要获取锁才能继续执行。
```

> 上面的例子展示了，wait 方法返回后，需要重新获取监视器锁，才可以继续往下执行。
>
> 同理，我们稍微修改下以上的程序，看下中断和 wait 之间的交互：

```java
public class WaitNotify {

    public static void main(String[] args) {

        Object object = new Object();

        Thread thread1 = new Thread(new Runnable() {
            @Override
            public void run() {

                synchronized (object) {
                    System.out.println("线程1 获取到监视器锁");
                    try {
                        object.wait();
                        System.out.println("线程1 恢复啦。我为什么这么久才恢复，因为notify方法虽然早就发生了，可是我还要获取锁才能继续执行。");
                    } catch (InterruptedException e) {
                        System.out.println("线程1 wait方法抛出了InterruptedException异常，即使是异常，我也是要获取到监视器锁了才会抛出");
                    }
                }
            }
        }, "线程1");
        thread1.start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (object) {
                    System.out.println("线程2 拿到了监视器锁。为什么呢，因为线程1 在 wait 方法的时候会自动释放锁");
                    System.out.println("线程2 设置线程1 中断");
                    thread1.interrupt();
                    System.out.println("线程2 执行完了 中断，先休息3秒再说。");
                    try {
                        Thread.sleep(3000);
                        System.out.println("线程2 休息完啦。注意了，调sleep方法和wait方法不一样，不会释放监视器锁");
                    } catch (InterruptedException e) {

                    }
                    System.out.println("线程2 休息够了，结束操作");
                }
            }
        }, "线程2").start();
    }
}
output:
线程1 获取到监视器锁
线程2 拿到了监视器锁。为什么呢，因为线程1 在 wait 方法的时候会自动释放锁
线程2 设置线程1 中断
线程2 执行完了 中断，先休息3秒再说。
线程2 休息完啦。注意了，调sleep方法和wait方法不一样，不会释放监视器锁
线程2 休息够了，结束操作
线程1 wait方法抛出了InterruptedException异常，即使是异常，我也是要获取到监视器锁了才会抛出
```

> 上面的这个例子也很清楚，如果线程调用 wait 方法，当此线程被中断的时候，wait 方法会返回，然后重新获取监视器锁，然后抛出 InterruptedException 异常。
>
> 我们再来考虑下，之前说的 notify 和中断：


```java
package com.javadoop.learning;

/**
 * Created by hongjie on 2017/7/7.
 */
public class WaitNotify {

    volatile int a = 0;

    public static void main(String[] args) {

        Object object = new Object();

        WaitNotify waitNotify = new WaitNotify();

        Thread thread1 = new Thread(new Runnable() {
            @Override
            public void run() {

                synchronized (object) {
                    System.out.println("线程1 获取到监视器锁");
                    try {
                        object.wait();
                        System.out.println("线程1 正常恢复啦。");
                    } catch (InterruptedException e) {
                        System.out.println("线程1 wait方法抛出了InterruptedException异常");
                    }
                }
            }
        }, "线程1");
        thread1.start();

        Thread thread2 = new Thread(new Runnable() {
            @Override
            public void run() {

                synchronized (object) {
                    System.out.println("线程2 获取到监视器锁");
                    try {
                        object.wait();
                        System.out.println("线程2 正常恢复啦。");
                    } catch (InterruptedException e) {
                        System.out.println("线程2 wait方法抛出了InterruptedException异常");
                    }
                }
            }
        }, "线程2");
        thread2.start();
        
         // 这里让 thread1 和 thread2 先起来，然后再起后面的 thread3
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (object) {
                    System.out.println("线程3 拿到了监视器锁。");
                    System.out.println("线程3 设置线程1中断");
                    thread1.interrupt(); // 1
                    waitNotify.a = 1; // 这行是为了禁止上下的两行中断和notify代码重排序
                    System.out.println("线程3 调用notify");
                    object.notify(); //2
                    System.out.println("线程3 调用完notify后，休息一会");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                    }
                    System.out.println("线程3 休息够了，结束同步代码块");
                }
            }
        }, "线程3").start();
    }
}

// 最常见的output:
线程1 获取到监视器锁
线程2 获取到监视器锁
线程3 拿到了监视器锁。
线程3 设置线程1中断
线程3 调用notify
线程3 调用完notify后，休息一会
线程3 休息够了，结束同步代码块
线程2 正常恢复啦。
线程1 wait方法抛出了InterruptedException异常
```

> 上述输出不是绝对的，再次感谢 **xupeng.zhang**。
>
> 有可能发生 线程1 是正常恢复的，虽然发生了中断，它的中断状态也确实是 true，但是它没有抛出 InterruptedException，而是正常返回。此时，thread2 将得不到唤醒，一直 wait。

### 17.3. 休眠和礼让（Sleep and Yield）

Thread.sleep(millisecs) 使当前正在执行的线程休眠指定的一段时间（暂时停止执行任何指令），时间取决于参数值，精度受制于系统的定时器。**休眠期间，线程不会释放任何的监视器锁**。线程的恢复取决于定时器和处理器的可用性，即有可用的处理器来唤醒线程。

需要注意的是，Thread.sleep 和 Thread.yield 都不具有同步的语义。在 Thread.sleep 和 Thread.yield 方法调用之前，不要求虚拟机将寄存器中的缓存刷出到共享内存中，同时也不要求虚拟机在这两个方法调用之后，重新从共享内存中读取数据到缓存。

*例如，我们有如下代码块，this.done 定义为一个 non-volatile 的属性，初始值为 false。*

```java
while (!this.done)
    Thread.sleep(1000);
```

*编译器可以只读取一次 this.done 到缓存中，然后一直使用缓存中的值，也就是说，这个循环可能永远不会结束，即使是有其他线程将 this.done 的值修改为 true。*

> yield 是告诉操作系统的调度器：我的cpu可以先让给其他线程。注意，调度器可以不理会这个信息。
>
> 这个方法太鸡肋，几乎没用。

### 17.4 内存模型（Memory Model）

> 内存模型这一节比较长，请耐心阅读

内存模型描述的是程序在 JVM 的执行过程中对数据的读写是否是按照程序的规则正确执行的。Java 内存模型定义了一系列规则，这些规则定义了对共享内存的写操作对于读操作的可见性。

> 简单地说，定义内存模型，主要就是为了规范多线程程序中修改或者访问同一个值的时候的行为。对于那些本身就是线程安全的问题，这里不做讨论。

内存模型描述了程序执行时的可能的表现行为。只要执行的结果是满足 java 内存模型的所有规则，那么虚拟机对于具体的实现可以自由发挥。

> 从侧面说，不管虚拟机的实现是怎么样的，多线程程序的执行结果都应该是**可预测的**。

*虚拟机实现者可以自由地执行大量的代码转换，包括重排序操作和删除一些不必要的同步。*

> 这里我画了一条线，从这条线到下一条线之间是两个重排序的例子，如果你没接触过，可以看一下，如果你已经熟悉了或者在其他地方看过了，请直接往下滑。

------

**示例 17.4-1 不正确的同步可能导致奇怪的结果**

java语言允许 compilers 和 CPU 对执行指令进行重排序，导致我们会经常看到似是而非的现象。

> 这里没有翻译 compiler 为编译器，因为它不仅仅代表编译器，后续它会代表所有会导致指令重排序的机制。

如表 17.4-A 中所示，A 和 B 是共享属性，r1 和 r2 是局部变量。初始时，令 A == B == 0。

**表17.4-A. 重排序导致奇怪的结果 - 原始代码**

| Thread 1     | Thread 2     |
| ------------ | ------------ |
| 1: `r2 = A;` | 3: `r1 = B;` |
| 2: `B = 1;`  | 4: `A = 2;`  |

按照我们的直觉来说，r2 == 2 同时 r1 == 1 应该是不可能的。直观地说，指令 1 和 3 应该是最先执行的。如果指令 1 最先执行，那么它应该不会看到指令 4 对 A 的写入操作。如果指令 3 最先执行，那么它应该不会看到执行 2 对 B 的写入操作。

如果真的表现出了 r2==2 和 r1==1，那么我们应该知道，指令 4 先于指令 1 执行了。

如果在执行过程出表现出这种行为（ r2==2 和r1==1），那么我们可以推断出以下指令依次执行：指令 4 => 指令 1=> 指令 2 => 指令 3。看上去，这种顺序是荒谬的。

但是，Java 是允许 compilers 对指令进行重排序的，只要保证在单线程的情况下，能保证程序是按照我们想要的结果进行执行，即 compilers 可以对**单线程内不产生数据依赖的语句之间**进行重排序。如果指令 1 和指令 2 发生了重排序，如按照表17.4-B 所示的顺序进行执行，那么我们就很容易看到，r2==2 和 r1==1 是可能发生的。

**表 17.4-B. 重排序导致奇怪的结果 - 允许的编译器转换**

| Thread 1  | Thread 2  |
| --------- | --------- |
| `B = 1;`  | `r1 = B;` |
| `r2 = A;` | `A = 2;`  |

> B = 1;  **=>**  r1 = B;  **=>**  A = 2;  **=>**  r2 = A;

对于很多程序员来说，这个结果看上去是 broken 的，但是这段代码是没有正确的同步导致的：

- 其中有一个线程执行了写操作
- 另一个线程对同一个属性执行了读操作
- 同时，读操作和写操作没有使用同步来确定它们之间的执行顺序

> 简单地说，之后要讲的一大堆东西主要就是为了确定共享内存读写的执行顺序，不正确或者说非法的代码就是因为读写同一内存地址没有使用同步（这里不仅仅只是说synchronized），从而导致执行的结果具有不确定性。

这个是**数据竞争(data race)**的一个例子。当代码包含数据竞争时，经常会发生违反我们直觉的结果。

有几个机制会导致表 17.4-B 中的指令重排序。java 的 JIT 编译器实现可能会重排序代码，或者处理器也会做重排序操作。此外，java 虚拟机实现中的内存层次结构也会使代码像重排序一样。在本章中，我们将所有这些会导致代码重排序的东西统称为 compiler。

> 所以，后续我们不要再简单地将 compiler 翻译为编译器，不要狭隘地理解为 Java 编译器。而是代表了所有可能会**制造**重排序的机制，包括 JVM 优化、CPU 优化等。

另一个可能产生奇怪的结果的示例如表 17.4-C，初始时 p == q 同时 p.x == 0。这个代码也是没有正确使用同步的；在这些写入共享内存的写操作中，没有进行强制的先后排序。

**Table 17.4-C**

| Thread 1     | Thread 2    |
| ------------ | ----------- |
| `r1 = p;`    | `r6 = p;`   |
| `r2 = r1.x;` | `r6.x = 3;` |
| `r3 = q;`    |             |
| `r4 = r3.x;` |             |
| `r5 = r1.x;` |             |

一个简单的编译器优化操作是会复用 r2 的结果给 r5，因为它们都是读取 r1.x，而且在单线程语义中，r2 到 r5之间没有其他的相关的写入操作，这种情况如表 17.4-D 所示。

**Table 17.4-D**

| Thread 1                                 | Thread 2    |
| ---------------------------------------- | ----------- |
| `r1 = p;`                                | `r6 = p;`   |
| `r2 = r1.x;`                             | `r6.x = 3;` |
| `r3 = q;`                                |             |
| `r4 = r3.x;`                             |             |
| <span style="color: red">r5 = r2;</span> |             |

现在，我们来考虑一种情况，在线程1第一次读取 r1.x 和 r3.x 之间，线程 2 执行 r6=p; r6.x=3; 编译器进行了 r5复用 r2 结果的优化操作，那么 r2==r5==0，r4 == 3，从程序员的角度来看，p.x 的值由 0 变为 3，然后又变为 0。

> 我简单整理了一下：

| Thread 1     | Thread 2    | 结果            |
| ------------ | ----------- | ------------- |
| `r1 = p;`    |             |               |
| `r2 = r1.x;` |             | r2 == 0       |
|              | `r6 = p;`   |               |
|              | `r6.x = 3;` |               |
| `r3 = q;`    |             |               |
| `r4 = r3.x;` |             | r4 == 3       |
| `r5 = r2;`   |             | r5 == r2 == 0 |

------

> 例子结束，回到正题

Java 内存模型定义了在程序的每一步，哪些值是内存可见的。对于隔离的每个线程来说，其操作是由我们线程中的语义来决定的，但是线程中读取到的值是由内存模型来控制的。当我们提到这点时，我们说程序遵守`线程内语义`，线程内语义说的是单线程内的语义，它允许我们基于线程内读操作看到的值完全预测线程的行为。如果我们要确定线程 t 中的操作是否是合法的，我们只要评估当线程 t 在单线程环境中运行时是否是合法的就可以，该规范的其余部分也在定义这个问题。

> 这段话不太好理解，首先记住“线程内语义”这个概念，之后还会用到。我对这段话的理解是，在单线程中，我们是可以通过一行一行看代码来预测执行结果的，只不过，代码中使用到的读取内存的值我们是不能确定的，这取决于在内存模型这个大框架下，我们的程序会读到的值。也许是最新的值，也许是过时的值。

此节描述除了 final 关键字外的`java内存模型`的规范，final将在之后的17.5节介绍。

*这里描述的内存模型并不是基于  Java 编程语言的面向对象。为了简洁起见，我们经常展示没有类或方法定义的代码片段。 大多数示例包含两个或多个线程，其中包含局部变量，共享全局变量或对象的实例字段的语句。 我们通常使用诸如 r1 或 r2 之类的变量名来表示方法或线程本地的变量。 其他线程无法访问此类变量。*

#### 17.4.1. 共享变量（Shared Variables）

所有线程都可以访问到的内存称为`共享内存`或`堆内存`。

所有的实例属性，静态属性，还有数组的元素都存储在堆内存中。在本章中，我们用术语`变量`来表示这些元素。

局部变量、方法参数、异常对象，它们不会在线程间共享，也不会受到内存模型定义的任何影响。

两个线程对同一个变量同时进行`读-写操作`或`写-写操作`，我们称之为“冲突”。

> 好，这一节都是废话，愉快地进入到下一节

#### 17.4.2. 操作（Actions）

> 这一节主要是讲解理论，主要就是严谨地定义**操作**。

`线程间操作`是指由一个线程执行的动作，可以被另一个线程检测到或直接影响到。以下是几种可能发生的`线程间操作`：

- 读 （普通变量，非 volatile）。读一个变量。

- 写 （普通变量，非 volatile）。写一个变量。

- 同步操作，如下：

  - volatile 读。读一个 volatile 变量

  - volatile 写。写入一个 volatile 变量

  - 加锁。对一个对象的监视器加锁。

  - 解锁。解除对某个对象的监视器锁。

  - 线程的第一个和最后一个操作。

  - 开启线程操作，或检测一个线程是否已经结束。

- `外部操作`。一个外部操作指的是可能被观察到的在外部执行的操作，同时它的执行结果受外部环境控制。

  > 简单说，外部操作的外部指的是在 JVM 之外，如 native 操作。

- `线程分歧操作(§17.4.9)`。此操作只由处于无限循环的线程执行，在该循环中不执行任何内存操作、同步操作、或外部操作。如果一个线程执行了分歧操作，那么其后将跟着无数的线程分歧操作。

  *分歧操作的引入是为了用来说明，线程可能会导致其他所有线程停顿而不能继续执行。*

此规范仅关心线程间操作，我们不关心线程内部的操作（比如将两个局部变量的值相加存到第三个局部变量中）。如前文所说，所有的线程都需要遵守线程内语义。对于线程间操作，我们经常会简单地称为**操作**。

我们用元祖< *t*, *k*, *v*, *u* >来描述一个操作：

- **t** - 执行操作的线程

- **k** - 操作的类型。

- **v** - 操作涉及的变量或监视器

  对于加锁操作，v 是被锁住的监视器；对于解锁操作，v 是被解锁的监视器。

  如果是一个读操作（ volatile 读或非 volatile 读），v 是读操作对应的变量

  如果是一个写操作( volatile 写或非 volatile 写)，v 是写操作对应的变量

- **u** - 唯一的标识符标识此操作

外部动作元组还包含一个附加组件，其中包含由执行操作的线程感知的外部操作的结果。 这可能是关于操作的成败的信息，以及操作中所读的任何值。

外部操作的参数（如哪些字节写入哪个 socket）不是外部操作元祖的一部分。这些参数是通过线程中的其他操作进行设置的，并可以通过检查线程内语义进行确定。它们在内存模型中没有被明确讨论。

在非终结执行中，不是所有的外部操作都是可观察的。17.4.9小节讨论非终结执行和可观察操作。

> 大家看完这节最懵逼的应该是`外部操作`和`线程分歧操作`，我简单解释下。
>
> 外部操作大家可以理解为 Java 调用了一个 native 的方法，Java 可以得到这个 native 方法的返回值，但是对于具体的执行其实不感知的，意味着 Java 其实不能对这种语句进行重排序，因为 Java 无法知道方法体会执行哪些指令。
>
> 引用 stackoverflow 中的一个例子：

```java
// method()方法中jni()是外部操作，不会和 "foo = 42;" 这条语句进行重排序。
class Externalization { 
  int foo = 0; 
  void method() { 
    jni(); // 外部操作
    foo = 42; 
  } 
  native void jni(); /* { 
    assert foo == 0; //我们假设外部操作执行的是这个。
  } */ 
}
```

> 在上面这个例子中，显然，`jni()` 与 `foo = 42` 之间不能进行重排序。
>
> 再来个线程分歧操作的例子：

```java
// 线程分歧操作阻止了重排序，所以 "foo = 42;" 这条语句不会先执行
class ThreadDivergence { 
  int foo = 0; 
  void thread1() { 
    while (true){} // 线程分歧操作
    foo = 42; 
  } 

  void thread2() { 
    assert foo == 0; // 这里永远不会失败
  } 
}
```

#### 17.4.3. 程序和程序顺序（Programs and Program Order）

在每个线程 t 执行的所有线程间动作中，t 的程序顺序是反映 **根据 t 的线程内语义执行这些动作的顺序** 的总顺序。

**如果所有操作的执行顺序 和 代码中的顺序一致，那么一组操作就是`连续一致`的**，并且，对变量 v 的每个读操作 r 会看到写操作 w 写入的值，也就是：

- 写操作 w 先于 读操作 r 完成，并且

- 没有其他的写操作 w' 使得 w' 在 w 之后 r 之前发生。

`连续一致性`对于可见性和程序执行顺序是一个非常强的保证。在这种场景下，所有的单个操作（比如读和写）构成一个统一的执行顺序，这个执行顺序和代码出现的顺序是一致的，同时每个单个操作都是原子的，且对所有线程来说立即可见。

如果程序没有任何的数据竞争，那么程序的所有执行操作将表现为连续一致。

Sequential consistency and/or freedom from data races still allows errors arising from groups of operations that need to be perceived atomically and are not.

`连续一致性` 和/或 数据竞争的自由仍然允许错误从一组操作中产生。

> 完全不知道这句话是什么意思

*如果我们用连续一致性作为我们的内存模型，那我们讨论的许多关于编译器优化和处理器优化就是非法的。比如在17.4-C中，一旦执行 p.x=3，那么后续对于该位置的读操作应该是立即可以读到最新值的。*

> **连续一致性**的核心在于每一步的操作都是原子的，同时对于所有线程都是可见的，而且不存在重排序。所以，Java 语言定义的内存模型肯定不会采用这种策略，因为它直接限制了编译器和 JVM 的各种优化措施。
>
> 注意：很多地方所说的**顺序一致性**就是这里的**连续一致性**，英文是 **Sequential consistency**

#### 17.4.4. 同步顺序（Synchronization Order）

每个执行都有一个同步顺序。同步顺序是由执行过程中的每个同步操作组成的顺序。对于每个线程 t，同步操作组成的同步顺序是和线程 t 中的代码顺序一致的。

> 虽然拗口，但毕竟说的是同步，我们都不陌生。

同步操作包括了如下同步关系：

- 对于监视器 m 的解锁与所有后续操作对于 m 的加锁同步

- 对 volatile 变量 v 的写入，与所有其他线程后续对 v 的读同步

- 启动线程的操作与线程中的第一个操作同步。

- 对于每个属性写入默认值（0， false，null）与每个线程对其进行的操作同步。

  尽管在创建对象完成之前对对象属性写入默认值有点奇怪，但从概念上来说，每个对象都是在程序启动时用默认值初始化来创建的。

- 线程 T1 的最后操作与线程 T2 发现线程 T1 已经结束同步。

  线程 T2 可以通过 T1.isAlive() 或 T1.join() 方法来判断 T1 是否已经终结。

- 如果线程 T1 中断了 T2，那么线程 T1 的中断操作与其他所有线程发现 T2 被中断了同步（通过抛出 InterruptedException 异常，或者调用 Thread.interrupted 或 Thread.isInterrupted ）

以上同步顺序可以理解为对于某资源的释放先于其他操作对同一资源的获取。

> 好，这节相对 easy，说的就是关于 **A synchronizes-with B** 的一系列规则。

#### 17.4.5. Happens-before顺序（Happens-before Order）

> Happens-before 是非常重要的知识，有些地方我没有很理解，我尽量将原文直译过来。想要了解更深的东西，你可能还需要查询更多的其他资料。

两个操作可以用 happens-before 来确定它们的执行顺序，如果一个操作 happens-before 于另一个操作，那么我们说第一个操作对于第二个操作是可见的。

> 注意：happens-before 强调的是可见性问题

如果我们分别有操作 x 和操作 y，我们写成 **hb(x, y)** 来表示 **x happens-before y**。

- 如果操作 x 和操作 y 是同一个线程的两个操作，并且在代码上操作 x 先于操作 y 出现，那么有 hb(x, y)

  > 请注意，这里不代表不可以重排序，只要没有数据依赖关系，重排序就是可能的。

- 对象构造方法的最后一行指令 happens-before 于 finalize() 方法的第一行指令。

- 如果操作 x 与随后的操作 y 构成同步，那么 hb(x, y)。

  > 这里说的就是上一小节的同步顺序

- hb(x, y) 和 hb(y, z)，那么可以推断出 hb(x, z)

对象的 wait 方法关联了加锁和解锁的操作，它们的 happens-before 关系即是加锁 happens-before 解锁。

我们应该注意到，两个操作之间的 happens-before 的关系并不一定表示它们在 JVM 的具体实现上必须是这个顺序，如果重排序后的操作结果和合法的执行结果是一致的，那么这种实现就不是非法的。

比如说，在线程中对对象的每个属性写入初始默认值并不需要先于线程的开始，只要这个事实没有被读到就可以了。

> 我们可以发现，happens-before 规则主要还是上一节 **同步顺序** 中的规则，加上额外的几条

更具体地说，如果两个操作是 happens-before 的关系，但是在代码中它们并没有这种顺序，那么就没有必要表现出 happens-before 关系。如线程 1 对变量进行写入，线程 2 随后对变量进行读操作，那么这两个操作是没有 happens-before 关系的。

happens-before 关系用于定义当发生数据竞争的时候。

将上面所有的规则简化成以下列表：

- 对一个监视器的解锁操作 happens-before 于后续的对这个监视器的加锁操作。

- 对 volatile 属性的写操作先于后续对这个属性的读操作。

  > 也就是一旦写操作完成，那么后续的读操作一定能读到最新的值

- 线程的 start() 先于任何在线程中定义的语句。

- 如果 A 线程中调用了 B.join()，那么 B 线程中的操作先于 A 线程 join() 返回之后的任何语句。

  > 因为 join() 本身就是让其他线程先执行完的意思。

- 对象的默认初始值 happens-before 于程序中对它的其他操作。

  > 也就是说不管我们要对这个对象干什么，这个对象即使没有创建完成，它的各个属性也一定有初始零值。

当程序出现两个没有 happens-before 关系的操作对同一数据进行访问时，我们称之为程序中有数据竞争。

除了线程间操作，数据竞争不直接影响其他操作的语义，如读取数组的长度、检查转换的执行、虚拟方法的调用。

*因此，数据竞争不会导致错误的行为，例如为数组返回错误的长度。*

当且仅当所有连续一致的操作都没有数据争用时，程序就是**正确同步**的。

如果一个程序是正确同步的，那么程序中的所有操作就会表现出连续一致性。

*这是一个对于程序员来说强有力的保证，程序员不需要知道重排序的原因，就可以确定他们的代码是否包含数据争用。因此，他们不需要知道重排序的原因，来确定他们的代码是否是正确同步的。一旦确定了代码是正确同步的，程序员也就不需要担心重排序对于代码的影响。*

> 其实就是正确同步的代码不存在数据竞争问题，这个时候程序员不需要关心重排序是否会影响我们的代码，我们的代码执行一定会**表现出**连续一致。

*程序必须正确同步，以避免当出现重排序时，会出现一系列的奇怪的行为。正确同步的使用，不能保证程序的全部行为都是正确的。但是，它的使用可以让程序员以很简单的方式就能知道可能发生的行为。正确同步的程序表现出来的行为更不会依赖于可能的重排序。没有使用正确同步，非常奇怪、令人疑惑、违反直觉的任何行为都是可能的。*

我们说，对变量 v 的读操作 r 能看到对 v 的写操作 w，如果: 

- 读操作 r 不是先于 w 发生（比如不是 hb(r, w) ），同时
- 没有写操作 w' 穿插在 w 和 r 中间（如不存在 hb(w, w') 和 hb(w', r)）。


非正式地，如果没有 happens-before 关系阻止读操作 r，那么读操作 r 就能看到写操作 w 的结果。

> 后面的部分是关于 *happens-before consistency* 的，我也不是很理解，感兴趣的读者请自行参阅其他资料。

A set of actions *A* is *happens-before consistent* if for all reads *r* in *A*, where *W(r)* is the write action seen by *r*, it is not the case that either *hb(r, W(r))* or that there exists a write *w* in *A* such that *w.v* = *r.v* and *hb(W(r), w)* and *hb(w, r)*.

In a *happens-before consistent* set of actions, each read sees a write that it is allowed to see by the *happens-before* ordering.

---

**Example 17.4.5-1. Happens-before Consistency**

For the trace in [Table 17.4.5-A](https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html#jls-17.4.5-A), initially `A == B == 0`. The trace can observe `r2 == 0` and `r1 == 0` and still be *happens-before consistent*, since there are execution orders that allow each read to see the appropriate write.

**Table 17.4.5-A. Behavior allowed by happens-before consistency, but not sequential consistency.**

| Thread 1  | Thread 2  |
| --------- | --------- |
| `B = 1;`  | `A = 2;`  |
| `r2 = A;` | `r1 = B;` |

Since there is no synchronization, each read can see either the write of the initial value or the write by the other thread. An execution order that displays this behavior is:

```
1: B = 1;
3: A = 2;
2: r2 = A;  // sees initial write of 0
4: r1 = B;  // sees initial write of 0
```

Another execution order that is happens-before consistent is:

```
1: r2 = A;  // sees write of A = 2
3: r1 = B;  // sees write of B = 1
2: B = 1;
4: A = 2;
```

In this execution, the reads see writes that occur later in the execution order. This may seem counterintuitive, but is allowed by *happens-before* consistency. Allowing reads to see later writes can sometimes produce unacceptable behaviors.

---

> 关于后面的几个小节，我自己对其理解不够，也不希望误导大家，如果大家感兴趣的话，请参阅其他资料。

#### 17.4.6. Executions

> 未完成

#### 17.4.7. Well-Formed Executions

> 未完成

#### 17.4.8. Executions and Causality Requirements

> 未完成

#### 17.4.9. Observable Behavior and Nonterminating Executions

> 未完成

### 17.5. final 属性的语义（final Field Semantics）

> 我们经常使用 final，关于它最基础的知识是：用 final 修饰的类不可以被继承，用 final 修饰的方法不可以被覆写，用 final 修饰的属性一旦初始化以后不可以被修改。
>
> 当然，这节说的不是这些，这里将阐述 final 关键字的深层次含义。

用 final 声明的属性正常情况下初始化一次后，就不会被改变。final 属性的语义与普通属性的语义有一些不一样。尤其是，对于 final 属性的读操作，compilers 可以自由地去除不必要的同步。相应地，compilers 可以将 final 属性的值缓存在寄存器中，而不用像普通属性一样从内存中重新读取。

final 属性同时也允许程序员不需要使用同步就可以实现**线程安全**的**不可变对象**。一个线程安全的不可变对象对于所有线程来说都是不可变的，即使传递这个对象的引用存在数据竞争。这可以提供安全的保证，即使是错误的或者恶意的对于这个不可变对象的使用。如果需要保证对象不可变，需要正确地使用 final 属性域。

对象只有在构造方法结束了才被认为`完全初始化`了。如果一个对象**完全初始化**以后，一个线程持有该对象的引用，那么这个线程一定可以看到正确初始化的 final 属性的值。

> 这个隐含了，如果属性值不是 final 的，那就不能保证一定可以看到正确初始化的值，可能看到初始零值。

final 属性的使用是非常简单的：在对象的构造方法中设置 final 属性；同时在对象初始化完成前，不要将此对象的引用写入到其他线程可以访问到的地方。如果这个条件满足，当其他线程看到这个对象的时候，那个线程始终可以看到正确初始化后的对象的 final 属性。It will also see versions of any object or array referenced by those `final` fields that are at least as up-to-date as the `final` fields are.

> 这里面说到了一个**正确初始化**的问题，看过《Java并发编程实战》的可能对这个会有印象，不要在构造方法中将 this 发布出去。



---

**Example 17.5-1. final Fields In The Java Memory Model**

这段代码把final属性和普通属性进行对比。

```java
class FinalFieldExample { 
    final int x;
    int y; 
    static FinalFieldExample f;

    public FinalFieldExample() {
        x = 3; 
        y = 4; 
    } 

    static void writer() {
        f = new FinalFieldExample();
    } 

    static void reader() {
        if (f != null) {
            int i = f.x;  // 程序一定能得到 3  
            int j = f.y;  // 也许会看到 0
        } 
    } 
}
```

这个类`FinalFieldExample`有一个 final 属性 x 和一个普通属性 y。我们假定有一个线程执行 writer() 方法，另一个线程再执行 reader() 方法。

因为 writer() 方法在对象完全构造后将引用写入 f，那么 reader() 方法将一定可以看到初始化后的 f.x : 将读到一个 int 值 3。然而， f.y 不是 final 的，所以程序不能保证可以看到 4，可能会得到 0。

---

---

**Example 17.5-2. final Fields For Security**

final 属性被设计成用来保障很多操作的安全性。

考虑以下代码，线程 1 执行：

```java
Global.s = "/tmp/usr".substring(4);
```

同时，线程 2 执行：

```java
String myS = Global.s; 
if (myS.equals("/tmp")) System.out.println(myS);
```

**String** 对象是不可变对象，同时 String 操作不需要使用同步。虽然 String 的实现没有任何的数据竞争，但是其他使用到 String 对象的代码可能是存在数据竞争的，内存模型没有对存在数据竞争的代码提供安全性保证。特别是，如果 String 类中的属性不是 final 的，那么有可能（虽然不太可能）线程 2 会看到这个 string 对象的 offset 为初始值 0，那么就会出现 myS.equals("/tmp")。之后的一个操作可能会看到这个 String 对象的正确的 offset 值 4，那么会得到 “/usr”。Java 中的许多安全特性都依赖于 String 对象的不可变性，即使是恶意代码在数据竞争的环境中在线程之间传递 String 对象的引用。

> 大家看这段的时候，如果要看代码，请注意，这里说的是  JDK6 及以前的 String 类：

```java
public final class String  
    implements java.io.Serializable, Comparable<String>, CharSequence  
{  
    /** The value is used for character storage. */  
    private final char value[];  
  
    /** The offset is the first index of the storage that is used. */  
    private final int offset;  
  
    /** The count is the number of characters in the String. */  
    private final int count;  
  
    /** Cache the hash code for the string */  
    private int hash; // Default to 0  
```

> 因为到 JDK7 和 JDK8 的时候，代码已经变为：

```java
public final class String
    implements java.io.Serializable, Comparable<String>, CharSequence {
    /** The value is used for character storage. */
    private final char value[];

    /** Cache the hash code for the string */
    private int hash; // Default to 0

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    private static final long serialVersionUID = -6849794470754667710L;
```

---

#### 17.5.1. final属性的语义（Semantics of final Fields）

令 o 为一个对象，c 为 o 的构造方法，构造方法中对 final 的属性 f 进行写入值。当构造方法 c 退出的时候，会在final 属性 f 上执行一个 freeze 操作。

注意，如果一个构造方法调用了另一个构造方法，在被调用的构造方法中设置 final 属性，那么对于 final 属性的 freeze 操作发生于被调用的构造方法结束的时候。

> 我没懂这边的 freeze 操作是什么。

对于每一个执行，读操作的行为被其他的两个偏序影响，解引用链 *dereferences()* 和内存链 *mc()*，它们被认为是执行的一部分。这些偏序必须满足下面的约束：

> 我对于解引用链和内存链完全不熟悉，所以下面这段我就不翻译了。

- Dereference Chain: If an action *a* is a read or write of a field or element of an object *o* by a thread *t* that did not initialize *o*, then there must exist some read *r* by thread *t* that sees the address of *o* such that *r* *dereferences(r, a)*.

- Memory Chain: There are several constraints on the memory chain ordering:
  - If *r* is a read that sees a write *w*, then it must be the case that *mc(w, r)*.
  - If *r* and *a* are actions such that *dereferences(r, a)*, then it must be the case that *mc(r, a)*.
  - If *w* is a write of the address of an object *o* by a thread *t* that did not initialize *o*, then there must exist some read *r* by thread *t* that sees the address of *o* such that *mc(r, w)*.

Given a write *w*, a freeze *f*, an action *a* (that is not a read of a `final` field), a read *r1* of the `final` field frozen by *f*, and a read *r2* such that *hb(w, f)*, *hb(f, a)*, *mc(a, r1)*, and *dereferences(r1, r2)*, then when determining which values can be seen by *r2*, we consider *hb(w, r2)*. (This *happens-before* ordering does not transitively close with other *happens-before* orderings.)

Note that the *dereferences* order is reflexive, and *r1* can be the same as *r2*.

For reads of `final` fields, the only writes that are deemed to come before the read of the `final` field are the ones derived through the `final` field semantics.

#### 17.5.2. 在构造期间读 final 属性（Reading final Fields During Construction）

在构造对象的线程中，对该对象的 final 属性的读操作，遵守正常的 happens-before 规则。如果在构造方法内，读某个 final 属性晚于对这个属性的写操作，那么这个读操作可以看到这个 final 属性已经被定义的值，否则就会看到默认值。

#### 17.5.3. final 属性的修改（Subsequent Modification of final Fields）

在许多场景下，如反序列化，系统需要在对象构造之后改变 final 属性的值。final 属性可以通过反射和其他方法来改变。唯一的具有合理语义的模式是：对象被构造出来，然后对象中的 final 属性被更新。在这个对象的所有 final 属性更新操作完成之前，此对象不应该对其他线程可见，也不应该对 final 属性进行读操作。对于 final 属性的 freeze 操作发生于**构造方法的结束，这个时候 final 属性已经被设值**，还有**通过反射或其他方式对于 final 属性的更新之后**。

即使是这样，依然存在几个难点。如果一个 final 属性在属性声明的时候初始化为一个常量表达式，对于这个 final 属性值的变化过程也许是不可见的，因为对于这个 final 属性的使用是在编译时用常量表达式来替换的。

另一个问题是，该规范允许 JVM 实现对 final 属性进行强制优化。在一个线程内，允许**对于 final 属性的读操作**与**构造方法之外的对于这个 final 属性的修改**进行重排序。

---

**Example 17.5.3-1. 对于 final 属性的强制优化（Aggressive Optimization of final Fields**）

```java
class A {
    final int x;
    A() { 
        x = 1; 
    } 

    int f() { 
        return d(this,this); 
    } 

    int d(A a1, A a2) { 
        int i = a1.x; 
        g(a1); 
        int j = a2.x; 
        return j - i; 
    }

    static void g(A a) { 
    	// 利用反射将 a.x 的值修改为 2
        // uses reflection to change a.x to 2 
    } 
}
```

在方法 d 中，编译器允许对 x 的读操作和方法 g 进行重排序，这样的话，`new A().f()`可能会返回 -1, 0, 或 1。

> 我在我的 MBP 上试了好多办法，真的没法重现出来，不过并发问题就是这样，我们不能重现不代表不存在。StackOverflow 上有网友说在 Sparc 上运行，可惜我没有 Sparc 机器。

---



> 下文将说到一个比较少见的 **final-field-safe context**

JVM 实现可以提供一种方式在 **final 属性安全**上下文（final-field-safe context）中执行代码块。如果一个对象是在 *final 属性安全上下文*中构造出来的，那么在这个 *final 属性安全上下文 *中对于 final 属性的读操作不会和相应的对于 final 属性的修改进行重排序。

*final 属性安全上下文*还提供了额外的保障。如果一个线程已经看到一个不正确发布的一个对象的引用，那么此线程可以看到了 final 属性的默认值，然后，在 *final 属性安全上下文*中读取该对象的正确发布的引用，这可以保证看到正确的 final 属性的值。在形式上，在*final 属性安全上下文*中执行的代码被认为是一个独立的线程（仅用于满足 final 属性的语义）。

在实现中，compiler 不应该将对 final 属性的访问移入或移出*final 属性安全上下文*（尽管它可以在这个执行上下文的周边移动，只要这个对象没有在这个上下文中进行构造）。

对于 *final 属性安全上下文*的使用，一个恰当的地方是执行器或者线程池。在每个独立的 *final 属性安全上下文*中执行每一个 `Runnable`，执行器可以保证在一个 `Runnable` 中对对象 o 的不正确的访问不会影响同一执行器内的其他 `Runnable` 中的 final 带来的安全保障。

#### 17.5.4. 写保护属性（Write-Protected Fields）

通常，如果一个属性是 `final` 的和 `static` 的，那么这个属性是不会被改变的。但是， `System.in`, `System.out`, 和 `System.err` 是 `static final` 的，出于遗留的历史原因，它们必须允许被 `System.setIn`, `System.setOut`, 和 `System.setErr` 这几个方法改变。我们称这些属性是**写保护**的，用以区分普通的 final 属性。

```java
    public final static InputStream in = null;
    public final static PrintStream out = null;
    public final static PrintStream err = null;
```

编译器需要将这些属性与 final 属性区别对待。例如，普通 final 属性的读操作对于同步是“免疫的”：锁或 volatile 读操作中的内存屏障并不会影响到对于 final 属性的读操作读到的值。因为写保护属性的值是可以被改变的，所以同步事件应该对它们有影响。因此，语义规定这些属性被当做普通属性，不能被用户的代码改变，除非是 `System`类中的代码。

### 17.6. 字分裂（Word Tearing）

实现 Java 虚拟机需要考虑的一件事情是，每个对象属性以及数组元素之间是独立的，更新一个属性或元素不能影响其他属性或元素的读取与更新。尤其是，两个线程在分别更新 byte 数组相邻的元素时，不能互相影响与干扰，且不需要同步来保证连续一致性。

一些处理器不提供写入单个字节的能力。 通过简单地读取整个字，更新相应的字节，然后将整个字写入内存，用这种方式在这种处理器上实现字节数组更新是非法的。 这个问题有时被称为字分裂（word tearing），在这种不能单独更新单个字节的处理器上，将需要寻求其他的方法。

> 请注意，对于大部分处理器来说，都没有这个问题

---

**Example 17.6-1. Detection of Word Tearing**

以下程序用于测试是否存在字分裂：

```java
public class WordTearing extends Thread {
    static final int LENGTH = 8;
    static final int ITERS = 1000000;
    static byte[] counts = new byte[LENGTH];
    static Thread[] threads = new Thread[LENGTH];

    final int id;

    WordTearing(int i) {
        id = i;
    }

    public void run() {
        byte v = 0;
        for (int i = 0; i < ITERS; i++) {
            byte v2 = counts[id];
            if (v != v2) {
                System.err.println("Word-Tearing found: " +
                        "counts[" + id + "] = " + v2 +
                        ", should be " + v);
                return;
            }
            v++;
            counts[id] = v;
        }
        System.out.println("done");
    }

    public static void main(String[] args) {
        for (int i = 0; i < LENGTH; ++i)
            (threads[i] = new WordTearing(i)).start();
    }
}
```

这表明写入字节时不得覆写相邻的字节。

---

### 17.7. double 和 long 的非原子处理 （Non-Atomic Treatment of double and long）

在Java内存模型中，对于 non-volatile 的 long 或 double 值的写入是通过两个单独的写操作完成的：long 和 double 是 64 位的，被分为两个 32 位来进行写入。那么可能就会导致一个线程看到了某个操作的低 32 位的写入和另一个操作的高 32 位的写入。

写入或者读取 volatile 的 long 和 double 值是原子的。

写入和读取对象引用一定是原子的，不管具体实现是32位还是64位。

将一个 64 位的 long 或 double 值的写入分为相邻的两个 32 位的写入对于 JVM 的实现来说是很方便的。为了性能上的考虑，JVM 的实现是可以决定采用原子写入还是分为两个部分写入的。

如果可能的话，我们鼓励 JVM 的实现避开将 64 位值的写入分拆成两个操作。我们也希望程序员将共享的 64 位值操作设置为 volatile 或者使用正确的同步，这样可以提供更好的兼容性。

> 目前来看，64 位虚拟机对于 long 和 double 的写入都是原子的，没必要加 volatile 来保证原子性。



（全文完）

### References

**官方原文**：https://docs.oracle.com/javase/specs/

**JSR 133 (Java Memory Model) FAQ**: http://www.cs.umd.edu/~pugh/java/memoryModel/jsr-133-faq.html

**The JSR-133 Cookbook for Compiler Writers**： http://gee.cs.oswego.edu/dl/jmm/cookbook.html

> 这是 Doug Lea 大神写的，属于更深层次的实现上的解读了，如果大家有需要的话，我后续也许可以整理整理。



### 结语

路还很长，如果有机会，我会在其中挑出一些 topic 出来和大家分享我自己的理解。
