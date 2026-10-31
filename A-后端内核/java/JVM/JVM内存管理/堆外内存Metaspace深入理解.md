堆外内存 Metaspace 深入理解
====

JDK8 用 Metaspace 彻底取代永久代（PermGen），是 JVM 内存结构面试的高频转折点：「为什么要换」「Metaspace 到底放什么」「为什么不受 -Xmx 管辖」「MaxMetaspaceSize 该怎么设」，一层层问下来基本能探出候选人对 JVM 内部实现的真实理解深度。本篇按「前世今生 → 存放内容 → 分配与回收 → 三层架构 → Compressed Class Space → 容量估算」展开。运行时数据区全景见 [Java内存结构](Java内存结构.md)；注意别和另一种堆外内存混淆——NIO 的 DirectByteBuffer 直接内存，见 [深入解读 Java 堆外内存（直接内存）](深入解读%20Java%20堆外内存（直接内存）.md)；类的加载与卸载机制见 [类加载器](类加载器.md)。

1 从 PermGen 到 Metaspace
------

分代垃圾回收的年代，HotSpot 在堆旁边放了一块永久代 PermGen，为了回收方便直接复用老年代的回收算法。它存放三类数据：

* 类在 JVM 内部的表示及其元数据（class metadata）；
* 类的静态变量（Class statics）；
* 字符串常量（Interned strings）。

这三类东西生命周期各不相同却挤在一个固定大小的区域里，矛盾早就存在。JDK7 起开发者着手拆解：

* Symbols → native memory；
* Interned strings → Java 堆；
* Class statics → Java 堆；

到了 JDK8，PermGen 被彻底废弃，剩余的 class metadata 全部搬进新的堆外区域 Metaspace。一句话概括动机：**类的元数据大小难以预估，放在堆内固定区域要么浪费要么溢出，不如独立到本地内存、按需增长、由类加载器生命周期管理**。

2 Metaspace 放什么、归谁管
------

Metaspace 位于堆外，最大可用内存取决于系统内存而非堆大小。它存放 class metadata，即一个 Java 类在 JVM 内部的全部描述信息：

1. **Klass 结构**——一个 Java 类在虚拟机内部的表示，理解成 C++ 层面的"类对象"；
2. **method metadata**——方法字节码、局部变量表、异常表、参数信息；
3. **常量池**；
4. **注解**；
5. **方法计数器**——记录方法执行次数，辅助 JIT 决策。

一个易混点：每个类关联的 `java.lang.Class` 实例是**堆中的普通 Java 对象**；而类本身的 metadata 不是 Java 对象，在 Metaspace 中。两者一个是镜子，一个是本体。

**分配**：类被加载时，由它的类加载器负责在 Metaspace 中开辟空间。**回收**：空间归属类加载器，只有该类加载器被卸载——即它加载的所有类都没有存活对象、且没有引用到达这些类和加载器——这部分 Metaspace 才会被 GC 释放。所以 Metaspace 泄漏的第一嫌疑人是**无法卸载的类加载器**（动态代理、热部署、脚本引擎滥用）。

还有个反直觉的点：释放 Metaspace 空间通常**不等于把内存还给操作系统**，JVM 往往先保留复用；且 Compressed Class Space 那部分是铁定不还的（见第 5 节）。

3 两个核心参数与 Metaspace 触发 GC
------

| 参数 | 含义 | 默认 |
| ---- | ---- | ---- |
| -XX:MaxMetaspaceSize | Class + Non-Class 两部分总和的上限 | 不限制 |
| -XX:CompressedClassSpaceSize | 其中 Compressed Class Space 的上限 | 1G（reserved 而非 committed） |

Metaspace 在两种情况下会推动 GC：

* **分配时超过内部水位线**：JVM 维护一个与已占用内存联动的阈值，超线后新的分配请求会先尝试通过卸载类加载器复用空间，而不是继续扩张；
* **触碰 OOM 前的最后一搏**：总量达到 MaxMetaspaceSize 或 Class Space 用光，触发 GC 卸载加载器；若卸载不出空间，即使堆很健康也会进入难看的 GC 周期并最终抛 `OutOfMemoryError: Metaspace`。

所以千万别把 MaxMetaspaceSize 设得太小。

4 三层架构：从 mmap 到 Metablock
------

Metaspace 的实现是典型的分层分配器，自底向上三层：

* **底层 VirtualSpaceList**：64 位平台上通过 `mmap` 每次映射 2MB 虚拟内存作为一个 Node 挂进全局链表（只是地址映射，真正用时才消耗物理内存）。Node 内维护水位线，快用完时提前申请内存页，减少系统调用。一个 Node 用尽就再挂新 Node，老 Node "退休"。
* **中层 Metachunk**：从 Node 上切出的 chunk 有 1K / 4K / 64K 三种规格，**一个 chunk 只属于一个类加载器**。一次类加载通常只要几十到几百字节，却先领走一整个 chunk——因为从全局链表拿内存要加锁，是昂贵操作，给大块可以让多个类加载器并发分配，chunk 用完再申请。规格选择靠猜：普通加载器首次 4K，连续申请 4 次后升级 64K；bootstrap 加载器一上来就给 4M（可用 InitialBootClassLoaderMetaspaceSize 调）；反射加载器（jdk.internal.reflect.DelegatingClassLoader）和匿名类加载器只加载一个类，只给 1K。猜错的代价是 chunk 尾部浪费。
* **顶层 Metablock**：类加载器本地的二级分配器把 chunk 切成一个个 Metablock 分给具体调用者，非常原始但极快。chunk 顶部顺序分配，只剩 header 时诞生，unused 部分若加载器不再加载类就是纯浪费。

JVM 内部用 ClassLoaderData 标识一个类加载器，它引用 ClassLoaderMetaspace 结构维护该加载器持有的全部 chunk。加载器卸载时，chunks 进入全局空闲列表 ChunkManager 供其他加载器复用；**只有当某个 2MB Node 上的所有 chunk 都空闲时，整个 Node 才从链表摘除、内存真正归还操作系统**。一个 Node 约 150-200 个 chunk，若全归同一个加载器，卸载它即可整 Node 回收；若分属大量生命周期各异的小加载器（匿名类、反射类最爱这么干），碎片化导致什么也还不了。所以生产上要警惕海量小类加载器。

匿名类有个特殊设计：每个匿名类（大量 Lambda、method handle）拥有独立的二级 ClassLoaderData，生命周期跟随匿名类本身而非宿主加载器，避免拖大宿主加载器的 Metaspace 占用。

5 Compressed Class Space：为什么 Metaspace 要分两半
------

64 位平台上 HotSpot 用了两种指针压缩：CompressedOops（对象引用压缩，不在本篇范围）和 Compressed Class Pointers。每个 Java 对象头部都有一根指针指向 Metaspace 中的 Klass 结构；开启类指针压缩后，这根指针是 32 位偏移量，需加上一个 base 值才是真实 64 位地址。收益是更小的对象头、更好的缓存利用率。

问题随之而来：32 位只能表达 4G 地址范围，而 `malloc`/`mmap` 返回的地址在 64 位系统里无法保证落在任意 4G 窗口内。解法是**单独用一次 mmap 预留一块连续、不超过 4G 的区域专放 Klass**。于是 Metaspace 被切成两半：

* **class part（Compressed Class Space）**：只放 Klass 结构，连续地址空间，默认预留 1G（-XX:CompressedClassSpaceSize，虚拟地址映射，启动后不可改）。名字有点误导——被压缩的不是 Klass 本身，而是指向它的指针；
* **non-class part**：其余所有 metadata，经 64 位地址访问，无连续性要求，可以挂在正常的 VirtualSpaceList 上。

实现上两个空间各有一套 VirtualSpaceList + ChunkManager，Class Space 的链表退化为"只有一个巨型 Node 且不可扩展"；ClassLoaderMetaspace 也就要同时记两份 chunk。开关方面：-XX:+UseCompressedClassPointers 关闭后没有 Class Space，CompressedClassSpaceSize 参数失效；-XX:-UseCompressedClassPointers 依赖 +UseCompressedOops（反之不依赖）。另外 CompressedOops 要求堆小于 32G，堆达到 32G 起两种压缩全部自动关闭。

6 容量估算与调参建议
------

一个类要同时占 Class Space 和 Non-Class Space，比例实测约 1:6。以 WildFly 16 空载（jcmd VM.metaspace 度量）为例：全部 11503 个类，平均每类 non-class 约 5.25K、class 约 0.86K；其中匿名类小得多（1.16K / 0.55K），且 Class Space 占比反而更高——因为 Lambda 类再小，Klass 结构的 sizeof 也省不掉。normal 类可按 **每类 5-7K Non-Class + 600-900B Class** 估算；完全不设限时，默认 1G Class Space 大约能容纳 1-1.5 百万个类才会见顶。

调参思路：MaxMetaspaceSize 默认不限，想监控告警或控制虚拟内存时才设。经验法则：

* 只关心监控 → 只设 MaxMetaspaceSize，别动 CompressedClassSpaceSize；
* 两者都设 → CompressedClassSpaceSize 取 MaxMetaspaceSize 的一半比较安全，设太低会先于总量撞上 Class Space OOM；单独调小它的唯一正当理由是压缩进程虚拟内存；
* 估算举例：预期加载 10000 个类 → 理论 10M class + 80M non-class，乘安全系数 2 → 总 180M，即 -XX:MaxMetaspaceSize=180m。

注意版本差异：JDK8 的 Metaspace 比 JDK11+ 更容易碎片化，同样规模要留更多余量。Metaspace 不同于堆——堆大小可灵活伸缩而不影响功能，Metaspace 需要多少由要加载多少类决定，代码不改，调参救不了。

观察手段按需取用：

```bash
# JDK11+：按类加载器输出 committed/used、chunk 分布与 waste
jcmd <pid> VM.metaspace
# JDK8（需诊断开关）：类维度元数据统计
jcmd <pid> GC.class_stats -all   # 前置 -XX:+UnlockDiagnosticVMOptions
```

线上的 Metaspace 缓慢增长且只涨不跌，先怀疑类加载器泄漏，而不是急着调大上限。

面试追问链
------

* 为什么用 Metaspace 替换 PermGen？→ PermGen 固定大小、与堆绑死，类元数据难预估；Metaspace 在本地内存按需增长、与类加载器生命周期绑定；
* Metaspace 放什么？静态变量和字符串常量池在吗？→ 放 class metadata（Klass、方法元数据、常量池、注解、方法计数器）；静态变量 JDK7 起在堆，字符串常量池也在堆；
* Metaspace 什么时候回收？→ 类加载器卸载时整块释放；空间先进空闲列表复用，整个 2MB Node 清空才还给 OS；
* Metaspace OOM 怎么排查？→ jcmd VM.metaspace / GC.class_stats 看占用分布，重点查动态生成类、热部署、脚本引擎等无法卸载的类加载器；
* 为什么要 Compressed Class Space？→ 类指针压缩成 32 位偏移后只能寻址 4G，必须预留一块连续区域专放 Klass；
* 堆超过 32G 会怎样？→ CompressedOops 失效，类指针压缩一并关闭，对象头变大；
* MaxMetaspaceSize 怎么设？→ 按每类 5-7K non-class + 1K class 估算，乘安全系数 2；JDK8 多留碎片余量。

面试官视角小结
------

这道题的分层非常清晰：知道「PermGen → Metaspace、静态变量和字符串常量池搬到堆」是及格；能讲出「空间归属类加载器、卸载才回收、释放不等于还 OS」是良好；能再往下钻到「2MB Node / Metachunk / Metablock 三层、Compressed Class Space 的 4G 连续地址约束、每类 5-7K 的容量估算」就是稀缺信号了——说明候选人不是背八股，而是真的读过 JVM 内部实现（javadoop 此篇亦是消化 SAP JVM 开发者 Thomas Stüfe 的 Metaspace 系列而来）。反过来，把 Metaspace 说成「存放字符串常量池」或与 DirectByteBuffer 直接内存混为一谈，是一票否决级的硬伤。结合 [类加载器](类加载器.md) 的卸载条件一起答，效果最佳。

> 来源：javadoop《深入理解堆外内存 Metaspace》（https://javadoop.com/post/metaspace）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
