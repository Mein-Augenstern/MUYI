看在前面
====

> * <a href="https://github.com/Snailclimb/JavaGuide/blob/master/docs/java/jvm/GC%E8%B0%83%E4%BC%98%E5%8F%82%E6%95%B0.md">GC调优参数</a>

JVM 配置常用参数
====

1. 堆参数

2. 回收器参数

3. 项目中常用配置

4. 常用组合

<h3>堆参数</h3>

![堆参数](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/%E5%A0%86%E5%8F%82%E6%95%B0.jpg)

<h3>回收器参数</h3>

![回收器参数](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/%E5%9B%9E%E6%94%B6%E5%99%A8%E5%8F%82%E6%95%B0.jpg)

如上表所示，目前 **主要有串行、并行和并发三种**，对于大内存的应用而言，串行的性能太低，因此使用到的主要是并行和并发两种。并行和并发GC的策略通过```UseParallelGC```和```UseConcMarkSweepGC```来指定，还有一些细节的配置参数用来配置策略的执行方式。例如：```XX:ParallelGCThreads```，```XX:CMSInitiatingOccupancyFraction```等。通常：```Young```区对象回收只可选择并行（耗时间），Old区选择并发（耗CPU）。

<h3>项目中常用配置</h3>

> 备注：在Java8中永久代的参数```-XX:PermSize```和```-XX:MaxPermSize```已经失效。

![项目中常用组合](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/%E9%A1%B9%E7%9B%AE%E4%B8%AD%E5%B8%B8%E7%94%A8%E9%85%8D%E7%BD%AE.jpg)

<h3>常用组合</h3>

![常用组合](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/%E5%B8%B8%E7%94%A8%E7%BB%84%E5%90%88.jpg)

常用GC调优策略
------

1. GC 调优原则；
2. GC 调优目的；
3. GC 调优策略；

<h3>GC 调优原则</h3>

在调优之前，我们需要记住下面的原则：

> 多数的 Java 应用不需要在服务器上进行 GC 优化； 多数导致 GC 问题的 Java 应用，都不是因为我们参数设置错误，而是代码问题； 在应用上线之前，先考虑将机器的 JVM 参数设置到最优（最适合）； 减少创建对象的数量； 减少使用全局变量和大对象； GC 优化是到最后不得已才采用的手段； 在实际使用中，分析 GC 情况优化代码比优化 GC 参数要多得多。

<h3>GC 调优目的</h3>

将转移到老年代的对象数量降低到最小； 减少 GC 的执行时间。

<h3>GC 调优策略</h3>

**策略 1**：将新对象预留在新生代，由于 Full GC 的成本远高于 Minor GC，因此尽可能将对象分配在新生代是明智的做法，实际项目中根据 GC 日志分析新生代空间大小分配是否合理，适当通过“-Xmn”命令调节新生代大小，最大限度降低新对象直接进入老年代的情况。

**策略 2**：大对象进入老年代，虽然大部分情况下，将对象分配在新生代是合理的。但是对于大对象这种做法却值得商榷，大对象如果首次在新生代分配可能会出现空间不足导致很多年龄不够的小对象被分配的老年代，破坏新生代的对象结构，可能会出现频繁的 full gc。因此，对于大对象，可以设置直接进入老年代（当然短命的大对象对于垃圾回收来说简直就是噩梦）。```-XX:PretenureSizeThreshold``` 可以设置直接进入老年代的对象大小。

**策略 3**：合理设置进入老年代对象的年龄，```-XX:MaxTenuringThreshold``` 设置对象进入老年代的年龄大小，减少老年代的内存占用，降低 full gc 发生的频率。

**策略 4**：设置稳定的堆大小，堆大小设置有两个参数：```-Xms``` 初始化堆大小，```-Xmx``` 最大堆大小。

**策略 5**：注意： 如果满足下面的指标，**则一般不需要进行 GC 优化**：

> MinorGC 执行时间不到50ms； Minor GC 执行不频繁，约10秒一次； Full GC 执行时间不到1s； Full GC 执行频率不算频繁，不低于10分钟1次。
