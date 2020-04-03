看在前面
====

> * <a href="https://www.iteye.com/blog/pre-2440310">【Java核心-进阶】并发工具包——线程安全的 List、Map、Set</a>

三大并发类：Concurrent、CopyOnWrite、Blocking
------

Java并发包中的集合从线程安全实现方式而言可分为三类：Concurrent、CopyOnWrite、Blocking。

* Concurrent 类的集合基于 lock-free 的方式实现。严格来说，它们是真正的并发。适合实现较高的吞吐量。

* CopyOnWrite 类的集合顾名思义，会在该变集合的操作中拷贝原数据，并用新的内部集合对象替换原内部对象。

* Blocking 类的集合则通过锁（ReentrantLock）实现。它们会提供 “等待性” 的方法。

<h4>Concurrent 的代价</h4>

虽然 Concurrent 类的集合没有 CopyOnWrite 那么中的修改开销，但这是有代价的：

* Concurrent 集合的遍历一致性较弱。

在利用迭代器遍历时，如果容器发生修改，迭代器可以继续进行遍历，不会抛出 ConcurrentModificationException。在 HashMap 中则会抛出此异常（也就是 fail-fast 机制）

* 因为是弱一致性，所以 size 等操作未必准确。

* 读取的性能也具有不确定性。

List
------

* CopyOnWriteArrayList

CopyOnWrite 是指对该集合的任何修改操作都会：拷贝原数组，修改后替换原数组；以此达到线程安全的目的。
这种数据结构适合 读多写少 的场景。因为修改开销比较大。可直接查看其 add() 方法了解实现原理：

```java
/**
 * Appends the specified element to the end of this list.
 *
 * @param e element to be appended to this list
 * @return {@code true} (as specified by {@link Collection#add})
 */
public boolean add(E e) {
	final ReentrantLock lock = this.lock;
	lock.lock();
	try {
		Object[] elements = getArray();
		int len = elements.length;
		Object[] newElements = Arrays.copyOf(elements, len + 1);
		newElements[len] = e;
		setArray(newElements);
		return true;
	} finally {
		lock.unlock();
	}
}
```

Map
------

* ConcurrentHashMap vs ConcurrentSkipListMap

* ConcurrentHashMap 的存取速度更快

* ConcurrentSkipListMap 的元素是经过排序的（它实现了接口 SortedMap）

为什么 HashMap 有对应的 ConcurrentHashMap，而 TreeMap 没有对应的 ConcurrentTreeMap ？

TreeMap 也实现了接口 SortedMap，它的元素也是经过排序的，为什么又造出一个 ConcurrentSkipListMap？

因为 TreeMap 内部基于复杂的红黑树，很难在并发场景中实现进行合理粒度的同步。SkipList 的内部结构更简单，实现增删元素时的线程安全开销更小；但是会占用更多空间。

Set
------

* 不存在的 ConcurrentHashSet

不知道为啥没有一个对应的 ConcurrentHashSet。虽然可以通过JDK自带的 Collections.newSetFromMap() 方法创建一个基于 ConcurrentHashMap 的线程安全 Set。但是一般来说有一个对称的语义是比较好的设计。HashMap - HashSet, ConcurrentHashMap - ConcurrentHashSet。Guava 中的 Sets.newConcurrentHashSet() 内部就是通过上述方式实现的：
```java
public static  Set newConcurrentHashSet() {  
    return Collections.newSetFromMap(new ConcurrentHashMap<E, Boolean>());  
}  
```

* ConcurrentSkipListSet

就像 HashSet 和 HashMap 的关系，ConcurrentSkipListSet 内部用 ConcurrentSkipListMap 实现（value 为 Boolean.TRUE）

* CopyOnWriteArraySet

CopyOnWriteArraySet 内部用 CopyOnWriteArrayList 实现。适用于数据量很少，且读操作远远多于写操作的情况。在遍历的时候应避免多个线程操作其引用。

线程安全的 LinkedHashMap
------

LinkedHashMap 经常被用于实现 LRU缓存（Least Recently Used）。很遗憾的是JDK中没有对应的线程安全实现。对性能要求高的场景中，Collections.synchronizedMap() 之类的外层一刀切式同步方案又不合适。这时可以考虑一个第三方的实现方案：guava 的 CacheBuilder。例：

```java
Map<String, String> cache = CacheBuilder.newBuilder()  
    .maximumSize(100)  
    .<String, String>build()  
    .asMap();  
```

pom.xml如下：

```java
<dependency>  
    <groupId>com.google.guava</groupId>  
    <artifactId>guava</artifactId>  
    <version>28.0-jre</version>  
</dependency>  
```
