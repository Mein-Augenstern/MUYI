HashMap 与 ConcurrentHashMap 源码全解析

> 定位区分：本篇走 JDK7/8 源码级（成环/扩容/并发容器演进）；入门概念与高频八股见 [java基础 的 HashMap 系列](../java基础/)（扩容过程/线程不安全/2 的幂次方三篇）。

====

HashMap 是面试出现频率最高的数据结构，ConcurrentHashMap 紧随其后，而且两者都要"Java7 和 Java8 分别说"。一句话定位：**HashMap 从"数组+链表"演进到"数组+链表+红黑树"，ConcurrentHashMap 从"分段锁"演进到"CAS + synchronized 锁单桶"**——两条演进线对应了 JDK 并发与数据结构设计思想的整体迁移。本文按 Java7 HashMap → Java7 CHM → Java8 HashMap → Java8 CHM 的顺序读源码主线。结构铺垫见 [HashMap底层数据结构](./HashMap底层数据结构.md)，更多注解见 [HashMap源码注释](./HashMap源码注释.md)，面试题清单见 [HashMap面试问题罗列](./HashMap面试问题罗列.md)。

1 Java7 HashMap：数组 + 单向链表
------

节点是嵌套类 Entry（key、value、hash、next 四个属性），数组每个槽位挂一条单向链表。三个核心参数：capacity 恒为 2 的幂、loadFactor 默认 0.75、threshold = capacity x loadFactor。

put 的流程：

```java
public V put(K key, V value) {
    if (table == EMPTY_TABLE)
        inflateTable(threshold);          // 第一个元素插入才初始化数组
    ...
    int hash = hash(key);
    int i = indexFor(hash, table.length);
    for (Entry<K,V> e = table[i]; e != null; e = e.next) {
        Object k;
        if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
            V oldValue = e.value;
            e.value = value;              // 判重成功，覆盖返回
            e.recordAccess(this);
            return oldValue;
        }
    }
    modCount++;
    addEntry(hash, key, value, i);        // 否则新增节点
    return null;
}
```

* **inflateTable**：`roundUpToPowerOf2` 把容量补到 2 的幂——`new HashMap(20)` 实际容量是 32；
* **indexFor** 只有一行 `return hash & (length-1);`——length 是 2 的幂时等价于对 length 取模但快得多，这是"容量为什么是 2 的幂"的根源（另见 [HashMap的长度为什么是2的幂次方](../java基础/HashMap的长度为什么是2的幂次方.md)）；
* **插入是表头插法**：`table[bucketIndex] = new Entry<>(hash, key, value, e)`（createEntry 中）。

扩容的触发条件在 addEntry：`size >= threshold && null != table[bucketIndex]`（容量到了且本次插入的位置非空），新容量翻倍。迁移 resize/transfer 把 table[i] 链表上的节点分拆到新数组的 newTable[i] 与 newTable[i + oldLength] 两个桶（hash 对新长度多出的那一位是 0 还是 1）。get 则是 hash 定位 + 沿链表比对，无话可说。这套 transfer 在并发下会成环导致 get 死循环——成因分析专开一篇：[JDK1.7中HashMap成环原因分析](../java基础/JDK1.7中HashMap成环原因分析.md)、[HashMap为什么线程不安全](../java基础/HashMap为什么线程不安全.md)。

2 Java7 ConcurrentHashMap：分段锁 Segment
------

Java7 的答案是 Segment 数组，**Segment 继承 ReentrantLock**，put 时只锁所在的段。默认 concurrencyLevel=16（不可再扩容），理论上 16 个线程并发写。定位两次：先用 hash 的高 4 位选段 `(hash >>> segmentShift) & segmentMask`（segmentShift=28、segmentMask=15），段内再用低位选桶。

段是惰性初始化的：ensureSegment 用 CAS 保证只建一次，且以 segment[0] 的容量/负载因子作模板。段内 put：

```java
// scanAndLockForPut：先 tryLock，失败自旋重试（MAX_SCAN_RETRIES 单核1/多核64），
// 期间还能投机预创建 node；超限才 lock() 阻塞挂起
else if (++retries > MAX_SCAN_RETRIES) {
    lock();
    break;
}
```

段内扩容 rehash 有个值得记住的优化：先找 lastRun 节点——其后所有节点新桶位置相同，整串**直接复用不克隆**，只克隆 lastRun 之前的节点，按默认阈值估算约 1/6 的节点需要克隆。

get 完全无锁：put 的表头插入不影响遍历中的 get；可见性靠 setEntryAt 的 UNSAFE 写与 table 的 volatile；remove 非头节点时靠 next 的 volatile 读兜住。更多分段锁背景见 [ConcurrentHashMap在JDK1.7的锁分段技术](../java基础/ConcurrentHashMap在JDK1.7的锁分段技术.md)。

3 Java8 HashMap：链表 + 红黑树，尾插与先插后扩
------

结构升级为**数组 + 链表 + 红黑树**：Node 用于链表、TreeNode 用于树（看首节点类型即知桶形态），链表长度到 8 转红黑树，桶内查找从 O(n) 降到 O(log n)。putVal 的两处关键反转：

```java
if ((tab = table) == null || (n = tab.length) == 0)
    n = (tab = resize()).length;                 // 懒初始化
...
if (binCount >= TREEIFY_THRESHOLD - 1)           // 链表到 8，树化
    treeifyBin(tab, hash);
...
++modCount;
if (++size > threshold)                          // 插完再判扩容
    resize();
```

* **先插值再扩容**（Java7 是先扩容后插入）；
* **尾插法**（Java7 是表头插）——并发扩容不再可能成环，这是 8 治好 7 的死循环顽疾的另一半原因。

resize 的迁移也不再重算 hash，用 `(e.hash & oldCap) == 0` 判断该节点落在原桶 j 还是新桶 j + oldCap，链表按原顺序拆成 loHead/loTail 与 hiHead/hiTail 两条：

```java
if ((e.hash & oldCap) == 0) {
    if (loTail == null) loHead = e;
    else loTail.next = e;
    loTail = e;
} else { /* hiHead/hiTail 同理 */ }
...
newTab[j] = loHead;
newTab[j + oldCap] = hiHead;
```

容量保持 2 的幂时，扩容只是"hash 多看一位"，节点要么留原位要么平移 oldCap，这是整个 8 版扩容优化的数学基础。

4 Java8 ConcurrentHashMap：CAS + synchronized 单桶锁
------

抛弃 Segment，锁粒度细化到**单个桶头节点**，结构上与 Java8 HashMap 基本一致（Node 的 val/next 均为 volatile）。key 与 value 都不允许 null。putVal 的分支骨架：

```java
if ((tab = table) == null || (n = tab.length) == 0)
    n = (tab = initTable()).length;                      // 1 空表：初始化
if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
    if (casTabAt(tab, i, null,                           // 2 空桶：CAS 无锁插入
                 new Node<K,V>(hash, key, value, null)))
        break;
}
else if ((fh = f.hash) == MOVED)                         // 3 正在扩容：帮忙搬
    tab = helpTransfer(tab, f);
else {
    synchronized (f) {                                   // 4 锁头节点，链表/树插入
        ...
    }
}
addCount(1L, binCount);                                  // 5 计数，必要时触发扩容
```

配套机制：

* **initTable**：`compareAndSwapInt(this, SIZECTL, sc, -1)` 抢到初始化权，抢不到的 `Thread.yield()` 自旋等。sizeCtl 平时存阈值，-1 表示初始化中，负数时其绝对值编码扩容状态；
* **树化的例外**：binCount >= 8 时若 `tab.length < MIN_TREEIFY_CAPACITY`（64），`tryPresize(n << 1)` 优先扩容而不是树化——小表的问题靠扩容就能治；
* **多线程协同扩容 transfer**：stride 步长（多核 `(n >>> 3) / NCPU`，最小 16）从后往前认领桶区间，全局由 transferIndex 调度；搬完的桶放一个 ForwardingNode（hash==MOVED）作标记，**别的线程 put 撞见 MOVED 就去 helpTransfer 帮忙搬**；链表迁移复用 Java7 lastRun 的思路拆 i 与 i+n 两链；红黑树拆完不足 6 个节点退化回链表；
* **get 无锁**：table、Node.val/next 全 volatile；`hash < 0` 说明是 ForwardingNode 或 TreeBin，走它们的 find() 到 nextTable 或树里找，否则沿链表遍历。

5 总结：7 到 8 两条演进主线
------

| 维度 | Java7 | Java8 |
| ---- | ---- | ---- |
| HashMap 结构 | 数组 + 链表（表头插） | 数组 + 链表 + 红黑树（尾插） |
| HashMap 扩容时机 | 先扩容后插入 | 先插入后扩容 |
| HashMap 迁移 | 逐节点重算下标 | hash & oldCap 高低位两条链原序拆分 |
| CHM 锁粒度 | Segment 段锁（ReentrantLock） | 桶头节点（CAS 空桶 + synchronized(f)） |
| CHM 并发度 | 固定 16 段 | 桶数量级，扩容还能帮忙搬 |
| CHM size | 各段计数求和 | addCount 计数 + sizeCtl 状态机 |

记两条主线就够：HashMap 在"治理哈希冲突恶化"（树化 + 小表先扩容）和"迁移成本"（高低位拆链不重 hash）上做文章；ConcurrentHashMap 在"锁粒度"（段 → 桶 → 空桶无锁）和"扩容协作"（帮忙搬运）上做文章。

面试追问链
------

* HashMap 容量为什么是 2 的幂？→ indexFor 用 `hash & (length-1)` 等价取模且更快，扩容时 hash 只需多看一位；
* new HashMap(20) 实际容量？→ 32，inflateTable/resize 都会 roundUpToPowerOf2；
* JDK7 头插 + 并发 transfer 会怎样？→ 链表成环、get 死循环，见专文分析；
* JDK8 怎么治的？→ 改尾插 + 高低位两链原序拆分，结构上不可能成环；
* 链表什么时候树化？→ 桶内 8 个（TREEIFY_THRESHOLD）；但表长 < 64 时优先扩容；
* putVal 的四个分支？→ 空表 initTable / 空桶 CAS / MOVED 帮扩容 / synchronized(f) 插入；
* initTable 怎么防并发重复初始化？→ CAS sizeCtl 为 -1，失败者 yield 自旋；
* ForwardingNode 是什么？→ 已迁移桶的占位节点（hash=MOVED），get 顺它去 nextTable 找，put 撞见它去 helpTransfer；
* JDK8 CHM 的 get 为什么不用加锁？→ val/next volatile 保证可见性，扩容期间有 ForwardingNode.find 兜底；
* JDK7 与 JDK8 CHM 锁粒度差多少？→ 16 个 Segment vs 桶级（空桶连锁都不要）。

面试官视角小结
------

HashMap 这道题的层级感极强：答出"数组+链表、2 的幂、0.75"是入场券；能对比 7/8 的头插尾插、先扩后插与先插后扩、树化 8/64 这些反差点，说明读过 putVal；ConcurrentHashMap 部分能讲清"CAS 空桶 + synchronized 单桶 + ForwardingNode 协同扩容"三板斧，并知道 sizeCtl 的状态机含义，就是源码级的信号。最深的追问是"为什么 JDK8 敢用 synchronized"——锁粒度足够细 + JVM 偏向锁/轻量级锁优化，答到这层才有设计视角。

> 来源：javadoop《Java7/8 中的 HashMap 和 ConcurrentHashMap 全解析》（https://javadoop.com/post/hashmap）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
