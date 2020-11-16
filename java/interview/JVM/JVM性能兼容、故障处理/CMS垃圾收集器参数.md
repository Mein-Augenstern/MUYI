看在前面
====

* <a href="https://it007.blog.csdn.net/article/details/88541589">CMS收集器几个参数详解 -XX:CMSInitiatingOccupancyFraction, CMSFullGCsBeforeCompaction</a>

CMSInitiatingOccupancyFraction
====

```-XX:CMSInitiatingOccupancyFraction```这个参数是指在使用CMS收集器的情况下，老年代使用了指定阈值的内存时，出发FullGC。如:```-XX:CMSInitiatingOccupancyFraction=70```:CMS垃圾收集器，当老年代达到70%时，触发CMS垃圾回收。

> 查看jvm源码:

```c++
product(intx, CMSInitiatingOccupancyFraction, -1,                         \
          "Percentage CMS generation occupancy to start a CMS collection "  \
          "cycle. A negative value means that CMSTriggerRatio is used")   
//省略
_cmsGen ->init_initiating_occupancy(CMSInitiatingOccupancyFraction, CMSTriggerRatio);

void ConcurrentMarkSweepGeneration::init_initiating_occupancy(intx io, uintx tr) {
  assert(io <= 100 && tr <= 100, "Check the arguments");
  if (io >= 0) {
    _initiating_occupancy = (double)io / 100.0;
  } else {
    _initiating_occupancy = ((100 - MinHeapFreeRatio) +
                             (double)(tr * MinHeapFreeRatio) / 100.0)
                            / 100.0;
  }
}
```

如果CMSInitiatingOccupancyFraction在0~100之间，那么由CMSInitiatingOccupancyFraction决定。否则由按 ((100 - MinHeapFreeRatio) + (double)( CMSTriggerRatio * MinHeapFreeRatio) / 100.0) / 100.0 决定。

**即最终当老年代达到 ((100 - 40) + (double) 80 * 40 / 100 ) / 100 = 92 %时，会触发CMS回收**。

UseCMSInitiatingOccupancyOnly
====

-XX:+UseCMSInitiatingOccupancyOnly 只是用设定的回收阈值(上面指定的70%),如果不指定,JVM仅在第一次使用设定值,后续则自动调整。

UseCMSCompactAtFullCollection 与 CMSFullGCsBeforeCompaction
====

有一点需要注意的是：CMS并发GC不是“full GC”。HotSpot VM里对concurrent collection和full collection有明确的区分。所有带有“FullCollection”字样的VM参数都是跟真正的full GC相关，而跟CMS并发GC无关的。
CMSFullGCsBeforeCompaction这个参数在HotSpot VM里是这样声明的：

```C++
product(bool, UseCMSCompactAtFullCollection, true,                     \
        "Use mark sweep compact at full collections")                  \
                                                                       \
product(uintx, CMSFullGCsBeforeCompaction, 0,                          \
        "Number of CMS full collection done before compaction if > 0") \
```

然后这样使用的：

```C++
  *should_compact =
    UseCMSCompactAtFullCollection &&
    ((_full_gcs_since_conc_gc >= CMSFullGCsBeforeCompaction) ||
     GCCause::is_user_requested_gc(gch->gc_cause()) ||
     gch->incremental_collection_will_fail(true /* consult_young */));
```

CMS GC要决定是否在full GC时做压缩，会依赖几个条件。其中

* 第一种条件，UseCMSCompactAtFullCollection 与 CMSFullGCsBeforeCompaction 是搭配使用的；前者目前默认就是true了，也就是关键在后者上。

* 第二种条件是用户调用了System.gc()，而且DisableExplicitGC没有开启。

* 第三种条件是young gen报告接下来如果做增量收集会失败；简单来说也就是young gen预计old gen没有足够空间来容纳下次young GC晋升的对象。

上述三种条件的任意一种成立都会让CMS决定这次做full GC时要做压缩。

CMSFullGCsBeforeCompaction 说的是，在上一次CMS并发GC执行过后，到底还要再执行多少次full GC才会做压缩。默认是0，也就是在默认配置下每次CMS GC顶不住了而要转入full GC的时候都会做压缩。 把CMSFullGCsBeforeCompaction配置为10，就会让上面说的第一个条件变成每隔10次真正的full GC才做一次压缩（而不是每10次CMS并发GC就做一次压缩，目前VM里没有这样的参数）。这会使full GC更少做压缩，也就更容易使CMS的old gen受碎片化问题的困扰。 本来这个参数就是用来配置降低full GC压缩的频率，以期减少某些full GC的暂停时间。CMS回退到full GC时用的算法是mark-sweep-compact，但compaction是可选的，不做的话碎片化会严重些但这次full GC的暂停时间会短些；这是个取舍。

CMSScavengeBeforeRemark
====

在CMS GC前启动一次ygc，目的在于减少old gen对ygc gen的引用，降低remark时的开销。一般CMS的GC耗时 80%都在remark阶段。
