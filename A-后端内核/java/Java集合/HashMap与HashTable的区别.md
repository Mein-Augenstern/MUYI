看在前面
====

* <a href="https://www.jianshu.com/p/c955491a3398">HashMap 进阶篇那些唬人的名词: Resize, Capacity, bucket,Load factor</a>

先搞懂 HashMap 的几个名词
====

HashMap 是"数组的链表"：底层数组，每一项是一个链表，链表存 Entry（含 key、value、next）。围绕它有几个高频名词：

* **Capacity（容量）**：bucket 的总数量，即 Entry 数组的长度；
* **bucket（桶）**：数组中可存储元素的位置，有索引，可以快速访问；
* **Load factor（负载因子）**：bucket 填满程度的最大比例，默认 0.75；
* **threshold（阈值）**：capacity × loadFactor，超过即触发扩容。例如 16 × 0.75 = 12，元素超过 12 就扩容到 32；
* **resize（扩容）**：entries 数量超过 capacity × load factor 时，容量扩为 2 倍并重新散列。

初始化时构造函数会算出"大于 initialCapacity 的最小的 2 的 n 次方"作为容量（最大为 2^30），threshold = capacity × loadFactor。

put 与 resize 流程（JDK1.7）
====

1 put
------

key 为 null 放到数组第一个链表；否则 hash 后用 `indexFor(h, length)` 即 `h & (length-1)` 定位下标，遍历链表，key 已存在则替换 value，否则 addEntry。

2 addEntry
------

当 `size >= threshold` 且对应 bucket 非空时，触发 `resize(2 * table.length)`，再重算 hash 和下标插入新节点。

3 resize 与 transfer
------

新建 2 倍长度的新数组，transfer 迁移数据，更新 threshold；若已达最大容量则把阈值设为 Integer.MAX_VALUE 不再扩容。transfer 遍历旧数组，采用**头插法**把节点插入新表（JDK1.7 中同索引位置的链表会倒置，1.8 不会——这也是 1.7 并发扩容成环的原因）。

为什么扩容是 2 倍
====

由于按 2 的幂次扩展，元素在新表中的位置**要么不变，要么移动 oldCap（即原索引 + 旧容量）**：只需看原 hash 新增的那一位 bit 是 0 还是 1，无需重新计算 hash。新 bit 是 0/1 可视为随机，扩容时还能把原本冲突的节点均匀分散到两个桶。

```java
// JDK1.8 的 hash：高 16 位异或低 16 位，让高低 bit 都参与定位
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

HashMap vs HashTable
====

1 线程安全
------

HashTable 用 synchronized 保证线程安全但慢；HashMap 非同步、单线程下更快；并发场景推荐 ConcurrentHashMap（锁分段/CAS，见库内对应篇章）。

2 对 null 的支持
------

HashTable 不允许 null 键/值（只要有一个 null 直接抛 NullPointerException）；HashMap 均允许（null 键只有一个，存放在数组首位）。因此判断键是否存在应使用 `containsKey` 而非 `get() == null`。

3 初始容量与扩容
------

* 不指定初始容量：HashTable 默认 11，之后每次扩充容量变为原来的 2n+1；HashMap 默认 16，之后每次扩充变为原来的 2 倍；
* 指定初始容量：HashTable 直接使用给定大小；HashMap 会通过 `tableSizeFor()` 扩充为 2 的幂次方（保证哈希表大小总是 2 的幂）。

4 底层数据结构
------

JDK1.8 的 HashMap 在链表长度大于阈值（默认 8）时将链表转化为红黑树以减少搜索时间；HashTable 没有树化机制，且基本已被淘汰，不要在代码中使用。

5 顺序
------

HashMap 不保证顺序，也不保证顺序随时间不变。

更多对比细节见库内 [HashMap 和 Hashtable 的区别](HashMap、Hashtable、LinkedHashMap和TreeMap特点说明.md)。

> 内容来源：[原文](https://www.jianshu.com/p/c955491a3398) · 2026-10 整理回填。
