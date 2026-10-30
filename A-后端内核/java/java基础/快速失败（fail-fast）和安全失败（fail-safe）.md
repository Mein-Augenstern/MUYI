快速失败（fail—fast）
====

在用迭代器遍历一个集合对象时，如果遍历过程中修改了集合对象的内容（增加、删除、修改）。此时会抛出```Concurrent Modification Exception```。

**原理**：迭代器在遍历时直接访问集合中的内容，并且在遍历过程中使用一个 ```modCount``` 变量。集合在被遍历期间如果内容发生变化，就会改变```modCount```的值。每当迭代器使用```hashNext()/next()```遍历下一个元素之前，都会检测```modCount```变量是否为```expectedmodCount```值。相等就返回遍历；否则抛出异常，终止遍历。

**注意**：这里异常的抛出条件是检测到 ```modCount！=expectedmodCount``` 这个条件。如果集合发生变化时修改```modCount```值刚好又设置为了```expectedmodCount```值，则异常不会抛出。因此，并发操作的编程不能依赖于这个异常是否抛出。这个异常只建议用于检测并发修改的bug。

**上述注意点可以结合CAS的ABA问题结合记忆**。

**场景**：```java.util```包下的集合类都是快速失败的，不能在多线程下发生并发修改（迭代过程中被修改）。

安全失败（fail—safe）
====

采用安全失败机制的集合容器，在遍历时不是直接在集合内容上访问的。而是先复制原有集合内容，在拷贝的集合上遍历。

**原理**：由于迭代时遍历的是原集合的拷贝。遍历过程中对原集合所作的修改并不能被迭代器检测到，所以不会触发```Concurrent Modification Exception```。

**缺点**：基于拷贝内容的优点是避免了```Concurrent Modification Exception```。但同样地，迭代器并不能访问到修改后的内容。即迭代器遍历的是开始遍历那一刻拿到的集合拷贝。在遍历期间原集合发生的修改迭代器是不知道的。

**场景**：```java.util.concurrent```包下的容器都是安全失败，可以在多线程下并发使用，并发修改。

一、modCount 机制看源码
------

fail-fast 的实现只有两个角色。一是集合上的 ```modCount```（结构修改计数）。二是迭代器里的 ```expectedModCount```（创建迭代器时的快照）。以 ArrayList 为例：

```java
// ArrayList 父类 AbstractList
protected transient int modCount = 0;          // add/remove/clear 等结构性修改都会 ++modCount

// 迭代器内部类 Itr
int expectedModCount = modCount;               // 创建迭代器时拍快照

public E next() {
    checkForComodification();                  // 每次取元素前先检查
    ...
}

final void checkForComodification() {
    if (modCount != expectedModCount)
        throw new ConcurrentModificationException();
}
```

* 两个关键推论：

1. **单线程也会触发**——`for (String s : list) list.remove(s)` 同样抛 CME。因为增强 for 就是迭代器语法糖，而 collection.remove 不会同步 expectedModCount。正确姿势是**用 iterator.remove()**，它会做 `expectedModCount = modCount` 回写；

2. **它只是"尽力而为"的错误检测，不是并发保护**。原文说的"modCount 恰好又变回 expectedmodCount 则不抛"正是这个意思。所以 CME 只能当 bug 报警器，不能当同步机制依赖。

二、三类迭代器对比：fail-fast / COW / 弱一致性
------

"fail-safe" 是一大类不抛 CME 的机制，J.U.C 下还分两种截然不同的路子：

| | fail-fast（java.util） | COW 拷贝（CopyOnWriteArrayList） | 弱一致迭代器（ConcurrentHashMap） |
| ---- | ---- | ---- | ---- |
| 迭代对象 | 原集合本体 | 创建迭代器时数组引用的快照 | 不快照整个表，逐桶游走 |
| 遍历中修改 | 抛 CME（尽力检测） | 写时复制新数组，迭代器无感知 | 不抛异常，改动可能看到也可能看不到 |
| 内存/性能代价 | 无额外开销 | 每次**写**全量复制 O(n)，读多写少才划算 | 无复制开销 |
| 读到多新 | —— | 永远是创建迭代器那一刻的旧数据 | 弱一致：尽量新，不保证一致快照 |
| 典型场景 | 单线程集合 | 监听器列表、白名单这类读远多于写的场景 | 高并发读写共享缓存 |

* **CopyOnWriteArrayList**：add 时加 ReentrantLock、复制新数组、改完替换引用（源码细节见 [CopyOnWriteArrayList源码](../Java集合/CopyOnWriteArrayList源码.md)）。迭代器持有旧数组引用，天然不会 CME，但也不支持迭代器自身的 remove——迭代器一改快照就失去意义；

* **ConcurrentHashMap 的"弱一致"**：迭代不锁表、不做全量快照。遍历开始后的修改"碰得上就看到、碰不到就跳过"，也不保证 size() 精确。这是在"一致性"与"并发吞吐"之间取的折中；

* 选择口诀：**单线程用 java.util（出错快速暴露），读多写少用 COW，高并发读写用 concurrent 容器**，总览见 [并发容器总结](../Java%20并发/并发容器总结.md)。

三、常见追问
------

* "怎么在遍历时安全删除元素？"——迭代器场景用 iterator.remove()；集合流式写法用 removeIf(predicate)（内部就是迭代器删除）；过滤生成新列表也是常见做法；

* "COW 集合写入频繁会怎样？"——每次写复制整个数组，内存抖动 + 写放大，GC 压力大，List 大时写 QPS 上不去；

* "fail-fast 是保证吗？"——不是，JDK 文档明确它是 best-effort（尽力而为）行为。"modCount 恰好绕过检测"时不会抛，不能拿它写并发逻辑。呼应原文与 CAS 的 ABA 类比：检测手段与真正安全是两回事，ABA 相关见 [CAS](../Java%20并发/CAS.md)。

* "ArrayList 并发写到底会出什么错？"——并发导致数据丢失、新增为 null、数组越界三类典型错误的具体分析与源码，见 [并发下的ArrayList错误分析](../Java集合/并发下的ArrayList错误分析.md)；

> 2026-10 扩充（Round1 查漏）。
