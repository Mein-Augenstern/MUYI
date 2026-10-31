看在前面
====

> * <a href="（原链已失效 2026-10，内容已在本篇/本库）%B0%E6%8C%87%E5%8D%97.md">最重要的JVM参数指南</a>
> * <a href="（原链已失效 2026-10，内容已在本篇/本库）">GC调优参数</a>（本文第 4、5 章整理自此篇，2026-10 合并入本文）

1、概述
====

在本篇文章中，你将掌握最常用的 JVM 参数配置。如果对于下面提到了一些概念比如堆。

2、堆内存相关
====

> Java 虚拟机所管理的内存中最大的一块。Java 堆是所有线程共享的一块内存区域，在虚拟机启动时创建。**此内存区域的唯一目的就是存放对象实例，几乎所有的对象实例以及数组都在这里分配内存。**

2.1、显式指定堆内存```–Xms```和```-Xmx```

与性能有关的最常见实践之一是根据应用程序要求初始化堆内存。如果我们需要指定最小和最大堆大小（推荐显示指定大小），以下参数可以帮助你实现：

```java
-Xms<heap size>[unit] 
-Xmx<heap size>[unit]
```

* heap size 表示要初始化内存的具体大小。

* unit 表示要初始化内存的单位。单位为“**g**” (GB) 、“**m**”（MB）、“**k**”（KB）。

举个栗子🌰，如果我们要为JVM分配最小2 GB和最大5 GB的堆内存大小，我们的参数应该这样来写：

```java
-Xms2G -Xmx5G
```

2.2、显式新生代内存（Young Generation）
------

根据<a href="https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/sizing.html">Oracle官方文档</a>，在堆总可用内存配置完成之后，第二大影响因素是为 Young Generation 在堆内存所占的比例。默认情况下，YG 的最小大小为 1310 MB，最大大小为无限制。

一共有两种指定 新生代内存(Young Ceneration)大小的方法：

1. **通过```-XX:NewSize```和```-XX:MaxNewSize```指定**

```java
-XX:NewSize=<young size>[unit] 
-XX:MaxNewSize=<young size>[unit]
```

举个栗子chestnut，如果我们要为 新生代分配 最小256m 的内存，最大 1024m的内存我们的参数应该这样来写：

```java
-XX:NewSize=256m
-XX:MaxNewSize=1024m
```

2. 通过```-Xmn<young size>[unit]``` 指定

举个栗子chestnut，如果我们要为 新生代分配256m的内存（NewSize与MaxNewSize设为一致），我们的参数应该这样来写：

```java
-Xmn256m
```

GC调优策略中很重要的一条经验总结是这样说的：

> 将新对象预留在新生代。由于 Full GC 的成本远高于 Minor GC，因此尽可能将对象分配在新生代是明智的做法。实际项目中根据 GC 日志分析新生代空间大小分配是否合理，适当通过“-Xmn”命令调节新生代大小。以此最大限度减少新对象直接进入老年代。

另外，你还可以通过```-XX:NewRatio=<int>```来设置新生代和老年代内存的比值。

比如下面的参数就是设置新生代（包括Eden和两个Survivor区）与老年代的比值为1。即：新生代与老年代所占比值为1：1，新生代占整个堆栈的 1/2。

```java
-XX:NewRatio=1
```

2.3、显示指定永久代/元空间的大小
------

**从Java 8开始，如果我们没有指定 Metaspace 的大小，随着更多类的创建，虚拟机会耗尽所有可用的系统内存。永久代并不会出现这种情况。**

JDK 1.8 之前永久代还没被彻底移除的时候通常通过下面这些参数来调节方法区大小

```java
-XX:PermSize=N //方法区 (永久代) 初始大小
-XX:MaxPermSize=N //方法区 (永久代) 最大大小,超过这个值将会抛出 OutOfMemoryError 异常:java.lang.OutOfMemoryError: PermGen
```

相对而言，垃圾收集行为在这个区域出现次数少，但并非数据进入方法区后就“永久存在”了。

**JDK 1.8 的时候，方法区（HotSpot 的永久代）被彻底移除了（JDK1.7 就已经开始了）。取而代之是元空间，元空间使用的是直接内存**。

下面是一些常用参数：

```java
-XX:MetaspaceSize=N //设置 Metaspace 的初始（和最小大小）
-XX:MaxMetaspaceSize=N //设置 Metaspace 的最大大小，如果不指定大小的话，随着更多类的创建，虚拟机会耗尽所有可用的系统内存。
```

3、垃圾收集相关
====

3.1、垃圾回收器
------

为了提高应用程序的稳定性，选择正确的<a href= "https://www.oracle.com/webfolder/technetwork/tutorials/obe/java/gc01/index.html">垃圾收集</a>算法至关重要。

JVM具有四种类型的GC实现：

* 串行垃圾收集器

* 并行垃圾收集器

* CMS垃圾收集器

* G1垃圾收集器

可以使用以下参数声明这些实现：

```java
-XX:+UseSerialGC
-XX:+UseParallelGC
-XX:+USeParNewGC
-XX:+UseG1GC
```

有关垃圾回收实施的更多详细信息，请参见<a href="（原链已失效 2026-10，内容已在本篇/本库）">此处</a>。


3.2、GC记录
------

为了严格监控应用程序的运行状况，我们应该始终检查JVM的垃圾回收性能。最简单的方法是以人类可读的格式记录GC活动。

使用以下参数，我们可以记录GC活动：

```java
-XX:+UseGCLogFileRotation 
-XX:NumberOfGCLogFiles=< number of log files > 
-XX:GCLogFileSize=< file size >[ unit ]
-Xloggc:/path/to/gc.log
```

GC 日志各字段（如 GC (Allocation Failure)、ParNew、[Times: user/sys/real]）的含义解读，参见 [日志排查--JVM日志](日志排查-JVM日志.md)。

4、JVM 配置常用参数速查
====

> 本章与下一章「GC 调优策略」整理自 JavaGuide《GC调优参数》（见文首来源标注）。

堆参数
------

![堆参数](/images/legacy/堆参数.jpg)

堆内存相关常用参数一览，与第 2 章介绍的```-Xms```/```-Xmx```、```-Xmn```、```-XX:NewRatio``` 等参数对应。

回收器参数
------

![回收器参数](/images/legacy/回收器参数.jpg)

如上表所示，目前 **主要有串行、并行和并发三种**。对于大内存的应用而言，串行的性能太低，因此使用到的主要是并行和并发两种。并行和并发GC的策略通过```UseParallelGC```和```UseConcMarkSweepGC```来指定。还有一些细节的配置参数用来配置策略的执行方式。例如：```XX:ParallelGCThreads```，```XX:CMSInitiatingOccupancyFraction```。通常：```Young```区对象回收只可选择并行（耗时间），Old区选择并发（耗CPU）。

项目中常用配置
------

> 备注：在Java8中永久代的参数```-XX:PermSize```和```-XX:MaxPermSize```已经失效。

![项目中常用配置](/images/legacy/项目中常用配置.jpg)

实际项目中一套典型的 JVM 参数配置模板，覆盖堆大小、新生代、GC 日志与 OOM 时自动 dump 等常用开关。

常用组合
------

![常用组合](/images/legacy/常用组合.jpg)

不同类型应用（偏吞吐/偏低延迟等）下常用 GC 参数组合速查表。

5、GC 调优策略
====

在调优之前，我们需要记住下面的原则：

> 多数的 Java 应用不需要在服务器上进行 GC 优化。多数导致 GC 问题的 Java 应用，都不是因为我们参数设置错误，而是代码问题。在应用上线之前，先考虑将机器的 JVM 参数设置到最优（最适合）。
>
> 减少创建对象的数量。减少使用全局变量和大对象。GC 优化是到最后不得已才采用的手段。在实际使用中，靠分析 GC 来优化代码，比优化 GC 参数要多得多。

GC 调优目的：将转移到老年代的对象数量降低到最小； 减少 GC 的执行时间。

常用调优策略如下（策略 1「将新对象预留在新生代」已作为经验总结引用于第 2.2 节，此处不再重复）：

**策略 2**：大对象进入老年代，虽然大部分情况下，将对象分配在新生代是合理的。但是对于大对象这种做法却值得商榷。大对象如果首次在新生代分配，可能会出现空间不足。这导致很多年龄不够的小对象被分配的老年代，破坏新生代的对象结构。这时可能会出现频繁的 full gc。

因此，对于大对象，可以设置直接进入老年代（当然短命的大对象对于垃圾回收来说简直就是噩梦）。```-XX:PretenureSizeThreshold``` 可以设置直接进入老年代的对象大小。

**策略 3**：合理设置进入老年代对象的年龄。```-XX:MaxTenuringThreshold``` 设置对象进入老年代的年龄大小。减少老年代的内存占用，降低 full gc 发生的频率。

**策略 4**：设置稳定的堆大小，堆大小设置有两个参数：```-Xms``` 初始化堆大小，```-Xmx``` 最大堆大小。

**策略 5**：注意： 如果满足下面的指标，**则一般不需要进行 GC 优化**：

> MinorGC 执行时间不到 50 ms。Minor GC 执行不频繁，约 10 秒一次。Full GC 执行时间不到 1 s。Full GC 执行频率不算频繁，不低于 10 分钟 1 次。

推荐阅读
====

* <a href="https://www.jianshu.com/p/832fc4d4cb53">CMS GC 默认新生代是多大？</a>

* <a href="https://www.cnblogs.com/hongdada/p/10277782.html">CMS GC启动参数优化配置</a>

* <a href="https://tech.meituan.com/2017/12/29/jvm-optimize.html">从实际案例聊聊Java应用的GC优化-美团技术团队</a>

* <a href="https://www.choupangxia.com/2019/11/11/interview-jvm-gc-08/">JVM性能调优详解 （2019-11-11）</a>

* <a href="https://segmentfault.com/a/1190000010603813">JVM参数使用手册</a>
