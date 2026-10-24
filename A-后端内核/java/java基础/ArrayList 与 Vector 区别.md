ArrayList 与 Vector 区别呢?为什么要用Arraylist取代Vector呢？
====

一句话答案
====

```Vector``` 类的所有方法都是同步的。可以由两个线程安全地访问一个Vector对象。但是一个线程访问 Vector 时，代码要在同步操作上耗费时间。

```Arraylist```不是同步的，所以在不需要保证线程安全时建议使用Arraylist。

一、总览对比表
====

| 维度 | ArrayList | Vector |
| ---- | ---- | ---- |
| 线程安全 | 非线程安全 | 公开方法几乎全部 synchronized 修饰（锁 this） |
| 同步开销 | 无锁，单线程零额外开销 | 即使单线程访问，每次读写也要走监视器锁 |
| 扩容策略 | 每次 1.5 倍：oldCapacity + (oldCapacity >> 1) | 默认每次 2 倍（可通过 capacityIncrement 自定义增量） |
| 默认容量 | JDK8 起懒分配：首次 add 时才扩到 10 | 构造时即分配 10 |
| 版本地位 | JDK1.2 引入，List 的标准实现 | JDK1.0 遗留类，Javadoc 标注 legacy，不推荐使用 |
| fail-fast | 迭代中检测 modCount，抛 ConcurrentModificationException | 同样基于 modCount 的 fail-fast，行为一致 |
| null 支持 | 允许任意个 null 元素 | 同样允许（注意这点和 Hashtable 禁 null 不同） |

二、同步实现差异
====

1、Vector 的同步是"方法级 synchronized"

```java
// Vector#add：锁加在方法声明上，锁对象就是 Vector 实例本身（this）
public synchronized boolean add(E e) {
    modCount++;
    ensureCapacityHelper(elementCount + 1);
    elementData[elementCount++] = e;
    return true;
}
```

要点：

* **锁粒度粗**：```get```/```size``` 这类读方法也同步，读多场景下所有读操作串行化，吞吐急剧下降。
* **单线程也要付费**：无竞争的 synchronized 经过锁升级优化后开销不大。但终究多了一条获取监视器锁的路径，JIT 优化也更受限。
* **复合操作照样不安全**：```if (list.size() > 0) list.remove(list.size() - 1)``` 这种"先检查再操作"在两个线程交替执行时依然会越界。方法级同步只保证**单个方法**原子，不保证**调用方逻辑**原子。这个经典反例在 [CopyOnWriteArrayList源码](../Java集合/CopyOnWriteArrayList源码.md) 一篇里有完整展开。

2、ArrayList 干脆不同步

JDK 的取舍是：容器默认不为多线程付费。需要线程安全时，由调用方显式选择并发容器或同步包装（见第五节）。

三、扩容策略：1.5 倍 vs 2 倍
====

```java
// ArrayList#grow（JDK8）：新容量 = 旧容量 + 旧容量右移一位，即 1.5 倍
int newCapacity = oldCapacity + (oldCapacity >> 1);

// Vector#grow：未指定 capacityIncrement 时直接翻倍，即 2 倍
int newCapacity = oldCapacity + ((capacityIncrement > 0) ?
                                 capacityIncrement : oldCapacity);
```

从 10 起步的增长轨迹对比：

```
ArrayList : 10 -> 15 -> 22 -> 33 -> 49  -> 73  -> 109 ...（×1.5，空间浪费小）
Vector    : 10 -> 20 -> 40 -> 80 -> 160 -> 320 -> 640 ...（×2，扩容次数少）
```

* 增长越保守，**内存浪费越小**：多扩出来的空间是白白占用的堆内存，大列表场景下差距明显。
* 增长越激进，**拷贝次数越少**：每次扩容都是 Arrays.copyOf 全量搬迁，O(n) 动作。
* 两者都可在构造时预估容量或调用 ensureCapacity 预分配，彻底规避扩容，详见 [ArrayList扩容](../Java集合/ArrayList扩容.md)。

四、失败机制：fail-fast
====

两者的迭代器都是 **fail-fast（快速失败）** 的：

* 结构性修改（列表自身的 add、remove 这类方法）会使 ```modCount++```；
* 迭代器每次 ```next()``` 前校验 ```modCount != expectedModCount```，一旦不等立即抛 ```ConcurrentModificationException```；
* **注意 Vector 虽"线程安全"，迭代它同样需要外部同步**——同步的是单个方法，而迭代是跨多次调用的过程。正确姿势是 ```synchronized(list){ for(...){...} }``` 或统一用 ```iterator.remove()```。

原理细节（包括为什么"不能依赖这个异常来编写并发程序"）见 [快速失败（fail—fast）和安全失败（fail—safe）](快速失败（fail—fast）和安全失败（fail—safe）.md)。

五、替代方案：为什么不该再写 Vector
====

| 方案 | 适用场景 | 特点 |
| ---- | ---- | ---- |
| ArrayList | 单线程（绝大多数场景） | 无锁，最常用的 List 实现 |
| Collections.synchronizedList(new ArrayList<>()) | 低并发、写多读少 | 包装类在方法体内 synchronized，本质仍是粗粒度锁 |
| CopyOnWriteArrayList | 读多写少（监听器列表、黑白名单） | 写时复制底层数组，读完全无锁，迭代不抛 CME |

所以"要线程安全"**不再是保留 Vector 的理由**：它对比的每个场景都有更好的替代。翻倍扩容、方法级粗锁这两个历史包袱是永久的。面试话术总结"为什么用 ArrayList 取代 Vector"：单线程下省掉同步开销。真需要线程安全时也轮不到 Vector，而是 CopyOnWriteArrayList 这类并发容器。

延伸阅读：[ArrayList扩容](../Java集合/ArrayList扩容.md)、[ArrayList源码注释](../Java集合/ArrayList源码注释.md)、[CopyOnWriteArrayList源码](../Java集合/CopyOnWriteArrayList源码.md)、[快速失败（fail—fast）和安全失败（fail—safe）](快速失败（fail—fast）和安全失败（fail—safe）.md)、[Arraylist 与 LinkedList 区别](Arraylist%20与%20LinkedList%20区别.md)。

> 2026-10 扩充（原内容过薄）。
