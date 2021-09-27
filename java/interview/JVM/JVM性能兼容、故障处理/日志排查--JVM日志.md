看在前面
====

* GC(Allocation Failure)引发的一些JVM知识点梳理：https://blog.csdn.net/zc19921215/article/details/83029952
* 快速解读GC日志：https://blog.csdn.net/renfufei/article/details/49230943

线上GC日志样例
------
```java
OpenJDK 64-Bit Server VM (25.192-b9) for linux-amd64 JRE (1.8.0_192-b9), built on Jul  8 2019 09:37:10 by "admin" with gcc 4.4.7
Memory: 4k page, physical 8388608k(7775048k free), swap 0k(0k free)
CommandLine flags: -XX:+CMSClassUnloadingEnabled -XX:CMSFullGCsBeforeCompaction=5 -XX:CMSInitiatingOccupancyFraction=75 -XX:CompressedClassSpaceSize=348127232 -XX:+DisableExplicitGC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/home/admin/logs -XX:InitialHeapSize=6442450944 -XX:+ManagementServer -XX:MaxHeapSize=6442450944 -XX:MaxMetaspaceSize=356515840 -XX:MaxNewSize=3221225472 -XX:MaxTenuringThreshold=6 -XX:MetaspaceSize=356515840 -XX:NewSize=3221225472 -XX:OldPLABSize=16 -XX:+PrintGC -XX:+PrintGCDateStamps -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -XX:ThreadStackSize=256 -XX:+UseCMSCompactAtFullCollection -XX:+UseCMSInitiatingOccupancyOnly -XX:+UseCompressedClassPointers -XX:+UseCompressedOops -XX:+UseConcMarkSweepGC -XX:+UseParNewGC 
2021-09-23T19:42:35.659+0800: 3.740: [GC (Allocation Failure) 2021-09-23T19:42:35.659+0800: 3.740: [ParNew: 2516608K->17451K(2831168K), 0.0275186 secs] 2516608K->17451K(5976896K), 0.0276535 secs] [Times: user=0.08 sys=0.02, real=0.03 secs] 
2021-09-23T19:42:39.816+0800: 7.897: [GC (Allocation Failure) 2021-09-23T19:42:39.816+0800: 7.897: [ParNew: 2534059K->84152K(2831168K), 0.0711330 secs] 2534059K->84152K(5976896K), 0.0713023 secs] [Times: user=0.22 sys=0.05, real=0.07 secs] 
```

以其中一行GC日志为例解释GC日志代表的含义

```
2021-09-23T19:42:35.659+0800: 3.740: [GC (Allocation Failure) 2021-09-23T19:42:35.659+0800: 3.740: [ParNew: 2516608K->17451K(2831168K), 0.0275186 secs] 2516608K->17451K(5976896K), 0.0276535 secs] [Times: user=0.08 sys=0.02, real=0.03 secs] 
```
 
> GC：

表明进行了一次垃圾回收，前面没有Full修饰，表明这是一次Minor GC ,注意它不表示只GC新生代，并且现有的不管是新生代还是老年代都会STW。

> Allocation Failure：

表明本次引起GC的原因是因为在年轻代中没有足够的空间能够存储新的数据了。

>ParNew：

表明本次GC发生在年轻代并且使用的是ParNew垃圾收集器。ParNew是一个Serial收集器的多线程版本，会使用多个CPU和线程完成垃圾收集工作（默认使用的线程数和CPU数相同，可以使用-XX：ParallelGCThreads参数限制）。该收集器采用复制算法回收内存，期间会停止其他工作线程，即Stop The World。

> 367523K->1293K(410432K)：单位是KB

三个参数分别为：GC前该内存区域(这里是年轻代)使用容量，GC后该内存区域使用容量，该内存区域总容量。

> 0.0023988 secs：

该内存区域GC耗时，单位是秒

> 522739K->156516K(1322496K)：

三个参数分别为：堆区垃圾回收前的大小，堆区垃圾回收后的大小，堆区总大小。

> 0.0025301 secs：

该内存区域GC耗时，单位是秒

> [Times: user=0.04 sys=0.00, real=0.01 secs]：

分别表示用户态耗时，内核态耗时和总耗时



