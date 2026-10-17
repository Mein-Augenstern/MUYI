看在前面
====

> * <a href="https://www.cnblogs.com/java-zzl/p/9767255.html">Java并发集合（二）-ConcurrentSkipListMap分析和使用</a>

概述与选型
====

ConcurrentSkipListMap 是**线程安全的有序 Map**，基于跳表（SkipList）实现：

* 与 TreeMap 对比：TreeMap 非线程安全，基于红黑树。非多线程场景应优先用 TreeMap；并发较低可用 `Collections.synchronizedSortedMap` 包装 TreeMap；**高并发且需要有序 key 时用 ConcurrentSkipListMap**；
* 与 ConcurrentHashMap 对比：4 线程 1.6 万数据下 ConcurrentHashMap 的存取速度约为其 4 倍，但 ConcurrentSkipListMap 有两点优势：
  1. key 有序；
  2. 支持更高并发——存取时间为 O(log N)，几乎与线程数无关，线程越多优势越明显；
* 注意：`size()` 因多线程并发修改，需要遍历链表统计，是 O(n) 量级的开销操作，不应频繁调用。

跳表结构
====

跳表分多层，每层是一条有序索引；上层是下层的子集，第 1 层包含全部数据；**层越高跳跃越大、数据越少**。查找从表头开始，自上而下、从左往右。

以 7,14,21,32,37,71,85 为例：单链表找 32 需要 4 步，跳表只需 2 步（顶层直接跳过 14、21）。

类结构与并发字段
====

* 继承 AbstractMap；
* 内部类 `Index` 对应跳表索引，`HeadIndex` 继承 Index，其实例 head 为表头；
* 通过 volatile/final 字段配合 CAS 实现并发安全：

```java
static final class Node<K,V> {
    final K key;            // final
    volatile V value;       // volatile
    volatile Node<K,V> next;
}
static final class Index<K,V> {
    final Node<K,V> node;   // final
    final Index<K,V> down;  // final
    volatile Index<K,V> right;
}
```

put → doPut（JDK1.7 源码）
====

主干是单线程插入逻辑，其余都是并发冲突处理：

1. `findPredecessor(key)` 找前继节点 b，n = b.next 为待插入位置的后继；
2. 并发防护：若 `n != b.next`、`n.value == null`（节点被删）、`b.value == null`（b 被删），说明读到了不一致状态，break 回外层 for 重试；
3. key 比较结果处理：
   * c > 0：b = n，n = f，继续右移；
   * c == 0：onlyIfAbsent 为真或 `n.casValue(v, value)` 成功则返回旧值；
   * c < 0：新建节点 `z(key, value, n)`，`b.casNext(n, z)` 插入底层链表；CAS 失败则 break 重试；
4. `randomLevel()` 随机选层，level > 0 时调用 `insertIndex(z, level)` 从第 1 层到 level 层逐层插入索引，必要时升高跳表。

remove → doRemove
====

1. 同样先找前继 b、目标节点 n、后继 f，并发检查与 put 一致（读取不一致或节点已被删则重试）；
2. c < 0 说明 key 不存在，返回 null；c > 0 继续右移；c == 0 时：
   * 若指定了期望 value 且不等于当前 v，返回 null（条件删除失败）；
   * `n.casValue(v, null)` 把 value 置空，标记删除；
   * `n.appendMarker(f)` 追加 marker 节点，`b.casNext(n, f)` 把 n 从链表摘除；失败则通过 `findNode(key)` 重试；
3. 成功后 `findPredecessor(key)` 顺带清理各层索引；若 `head.right == null` 则 `tryReduceLevel()` 降低跳表层数。

删除采用 **value 置 null + marker 的惰性删除**方式：其他线程遇到已删节点时会 `helpDelete(b, f)` 协助完成摘除。

get → doGet → findNode
====

findNode 主干：

* findPredecessor 找 b，n = b.next；
* n == null 返回 null（key 不存在）；
* compareTo：c == 0 返回 n；c < 0 返回 null；c > 0 则 b = n、n = f 继续右移。

doGet 循环调用 findNode，取到 `n.value` 非 null 即返回。get 全程无锁，依赖 volatile 读与"删除先置 null"的约定保证一致性。

使用示例
====

```java
Map<String, String> map = new ConcurrentSkipListMap<>();
// 线程 a、b 各循环 6 次：以 "线程名+序号" 为 key、"0" 为 value put，
// 并各自用 Iterator 遍历打印全部元素：
//   ConcurrentSkipListMap：程序正常运行，输出按 key 有序
//   换成 TreeMap：抛出 ConcurrentModificationException
```

一句话总结：需要**并发 + 按 key 有序**（有序遍历、范围查询）时选 ConcurrentSkipListMap；只要并发不需要有序，选 ConcurrentHashMap 更快。

> 内容来源：[原文](https://www.cnblogs.com/java-zzl/p/9767255.html) · 2026-10 整理回填。
