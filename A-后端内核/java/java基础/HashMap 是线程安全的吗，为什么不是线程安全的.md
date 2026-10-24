一句话答案
====

HashMap不是线程安全的。

Question
====

* 什么场景下会导致HashMap出现线程不安全的问题？

Answer：多线程下的四类典型事故
====

* **场景一：并发 put 覆盖写（丢数据，最常见）**

两个线程同时 put 发生哈希碰撞、落在同一个桶上，都判断"该位置可以放"。后写入的 Entry 直接覆盖先写入的——前一个 key-value 静默丢失。JDK7 和 JDK8 都存在：

```
桶[3]: null        线程A put("a",1) ──┐   两边都看到桶是空的
                        线程B put("b",2) ──┤   各自执行 "放进去"
桶[3]: b→(a 丢失) ◄──────────────────┘   后写的覆盖先写的
```

* **场景二：并发扩容成环（JDK1.7 经典，CPU 100%）**

JDK1.7 扩容 transfer 用**头插法**倒置链表。两个线程同时扩容，互相 transfer 时指针交错可能把链表改成环形链表。之后 get 落到该桶就死循环：

```
正常:        A → B → null
线程1 transfer 到一半挂起（记录 e=A, next=B）
线程2 完整 transfer（新表里变成 B → A）
线程1 恢复继续头插：A 又指向 B ……  A ⇄ B 成环
后续 get(key) 落入此桶 → while 遍历永不结束 → CPU 100%
```

详细逐步推演见 [JDK1.7中HashMap成环原因分析](JDK1.7中HashMap成环原因分析.md)。

* **场景三：迭代时结构被改，fail-fast 抛 ConcurrentModificationException**

一个线程遍历、另一个线程 put/remove，modCount 与 expectedModCount 对不上，next() 直接抛 CME，详见 [快速失败（fail—fast）和安全失败（fail—safe）](快速失败（fail—fast）和安全失败（fail—safe）.md)。

* **场景四：计数与结构的隐性破坏**

putVal 里 `++size`、树化判断、threshold 判断都是"读-改-写"多步操作。并发下 size 少加、多加都会发生。JDK1.8 改尾插后**成环修复了，但覆盖写、size 错乱、迭代异常一个都没修**。"1.8 线程安全了吗"是高频追问，答案仍然是否定的。

源码层面：操作全无锁
====

翻开 [HashMap源码注释](../Java集合/HashMap源码注释.md) 可以看到，put/get/resize 的主干流程没有任何同步措施：

* put：定位桶 → 遍历/追加链表或红黑树插入 → `++modCount`、`++size`、必要时 `resize()`。每一步都建立在"别的线程不会动这张表"的假设上；

* get：虽然读旧表通常不抛异常，但扩容进行中可能读到"半新半旧"的数据（读到刚迁移一半的桶）。结果不确定；

* 结论：HashMap 的线程不安全不是某个 bug，而是**设计上就没打算并发用**——它是为单线程场景做的性能取舍。

怎么解决
====

* **首选 ConcurrentHashMap**：JDK1.7 分段锁（见 [ConcurrentHashMap在JDK1.7的锁分段技术](ConcurrentHashMap在JDK1.7的锁分段技术.md)），JDK1.8 改为 CAS + 单桶 synchronized，并发度更高（见 [ConcurrentHashMap 和 Hashtable 的区别](ConcurrentHashMap%20和%20Hashtable%20的区别.md)）；

* Collections.synchronizedMap(new HashMap<>())：全表一把锁包装，正确但性能差，基本只出现在老代码；

* 只读共享（初始化后不再写）可以放心用 HashMap，或用不可变 Map（List.of/Map.of 系列）。

追问速答
------

* "get 会有问题吗？"——单次 get 大概率能读到，但扩容进行中可能读到半迁移状态的数据，返回结果不确定。并发写下甚至可能配合重排读到"半初始化"的链表节点；

* "JDK1.8 改成尾插法后还成环吗？"——头插改尾插修掉了 transfer 成环，但覆盖写、size 错乱、fail-fast 依旧存在，所以 1.8 依然不是线程安全的；

* "多线程只读安全吗？"——安全。所有线程都不做结构性修改，读是无锁且幂等的；

* "为什么 JDK 不直接把 HashMap 做成线程安全？"——加锁有开销，单线程是更常见的场景；并发需求交给 ConcurrentHashMap，各取所长。

一句话总结：HashMap 面向单线程。多线程下覆盖写丢数据、JDK1.7 扩容成环死循环、迭代 fail-fast、size 错乱四类问题都可能踩。并发场景直接换 ConcurrentHashMap。

传送门
====

* <a href="https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/HashMap%E6%88%90%E7%8E%AF%E5%8E%9F%E5%9B%A0%E5%88%86%E6%9E%90.md">HashMap成环原因分析</a>

> 2026-10 扩充（Round1 查漏）。
