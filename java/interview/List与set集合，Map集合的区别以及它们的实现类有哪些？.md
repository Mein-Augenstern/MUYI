List和set集合，Map集合的区别以及它们的实现类有哪些？有什么区别？
====

List 是可重复集合，Set 是不可重复集合，这两个接口都实现了 Collection 父接口。

Map 未继承 Collection，而是独立的接口，Map 是一种把键对象和值对象进行映射的集合，它的每一个元素都包含了一对键对象和值对象，Map 中存储的数据是没有顺序的， 其 key 是不能重复的，它的值是可以有重复的。

List 的实现类有 ArrayList，Vector 和 LinkedList：

ArrayList 和 Vector 内部是线性动态数组结构，在查询效率上会高很多，Vector 是线程安全的，相比 ArrayList 线程不安全的，性能会稍慢一些。

LinkedList：是双向链表的数据结构存储数据，在做查询时会按照序号索引数据进行前向或后向遍历，查询效率偏低，但插入数据时只需要记录本项的前后项即可，所以插入速度较快。

Set 的实现类有 HashSet 和 TreeSet；

HashSet：内部是由哈希表（实际上是一个 HashMap 实例）支持的。它不保证 set 元素的迭代顺序。

TreeSet：TreeSet 使用元素的自然顺序对元素进行排序，或者根据创建 set 时提供的 Comparator 进行排序。

Map 接口有三个实现类：Hashtable，HashMap，TreeMap，LinkedHashMap；

Hashtable：内部存储的键值对是无序的是按照哈希算法进行排序，与 HashMap 最大的区别就是线程安全。键或者值不能为 null，为 null 就会抛出空指针异常。

TreeMap：基于红黑树 (red-black tree) 数据结构实现，按 key 排序，默认的排序方式是升序。

LinkedHashMap：有序的 Map 集合实现类，相当于一个栈，先 put 进去的最后出来，先进后出。

List 和 Map 区别？
====

一个是存储单列数据的集合，另一个是存储键和值这样的双列数据的集合，List 中存储的数据是有顺序，并且允许重复；Map 中存储的数据是没有顺序的，其 key 是不能重复的，它的值是可以有重复的。

![集合UML类图](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E9%9B%86%E5%90%88%E7%B1%BBUML%E5%9B%BE.jpg)
