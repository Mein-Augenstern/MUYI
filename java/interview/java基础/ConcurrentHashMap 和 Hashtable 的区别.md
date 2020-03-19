看在前面
====

* <a href="https://www.cnblogs.com/chengxiao/p/6842045.html">ConcurrentHashMap实现原理及源码分析</a>

* <a href="https://juejin.im/post/5df8d7346fb9a015ff64eaf9">《我们一起进大厂》系列-ConcurrentHashMap & Hashtable</a>

ConcurrentHashMap 和 Hashtable 的区别主要体现在实现线程安全的方式上不同。
====

* **底层数据结构：** JDK1.7的ConcurrentHashMap底层采用 **分段的数组+链表** 实现，JDK1.8采用的数据结构跟HashMap1.8的结构一样，数组+链表/红黑二叉树。Hashtable和JDK1.8之前的HashMap的底层数据结构类似都是采用 **数组+链表** 的形式，数组是HashMap的主体，链表则是主要为了解决哈希冲突而存在的；

* **实现线程安全的方式（重要）：**

    1）在JDK1.7的时候，ConcurrentHashMap（分段锁）对整个桶数组进行了分割分段（Segment），每一把锁只锁容器其中一部分数据，多线程访问容器里不同数据段的数据，就不会存在锁竞争，提高并发访问率。到了JDK1.8的时候已经摒弃了Segment的概念，而是直接用Node数组+链表+红黑树的数据结构来实现，并发控制使用synchronized和CAS来操作。（JDK1.6以后对synchronized锁做了很多优化）整个看起来就像是优化过且线程安全的HashMap，虽然在JDK1.8中还可以看到Segment的数据结构，但是已经简化了属性，只是为了兼容旧版本；

    2） Hashtable（同一把锁）：使用 synchronized 来保证线程安全，效率非常低下。当一个线程访问同步方法时，其他线程也访问同步方法，可能会进入阻塞或轮询状态，如使用 put 添加元素，另一个线程不能使用 put 添加元素，也不能使用 get，竞争会越来越激烈效率越低。
    
    
**Hashtable**

![Hashtable全表锁](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/Hashtable%E5%85%A8%E8%A1%A8%E9%94%81.png)

**JDK1.7的ConcurrentHashMap**

![ConcurrentHashMap分段锁](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/ConcurrentHashMap%E5%88%86%E6%AE%B5%E9%94%81.jpg)

**JDK1.8的ConcurrentHashMap（TreeBin: 红黑二叉树节点 Node: 链表节点**

![JDK1.8的ConcurrentHashMap](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/JDK1.8%E7%9A%84ConcurrentHashMap.jpg)

HashMap在多线程环境下存在线程安全问题，那你一般都是怎么处理这种情况的？
------

美丽迷人的面试官您好，一般在多线程的场景，我都会使用好几种不同的方式去代替：

* 使用Collections.synchronizedMap(Map)创建线程安全的map集合

* Hashtable

* ConcurrentHashMap

不过出于线程并发度的原因，我都会舍弃前两者使用最后的ConcurrentHashMap，他的性能和效率明显高于前两者。

哦，Collections.synchronizedMap是怎么实现线程安全的你有了解过么？
------

不按照套路出牌呀，正常不都是问HashMap和ConcurrentHashMap么，这次怎么问了这个鬼东西，还好我饱读诗书，经常看敖丙的《吊打面试官》系列，不然真的完了

小姐姐您这个问题真好，别的面试官都没问过，说真的您水平肯定是顶级技术专家吧。在SynchronizedMap内部维护了一个普通对象Map，还有排斥锁mutex

```java
/**
 * @serial include
 */
private static class SynchronizedMap<K,V>
	implements Map<K,V>, Serializable {
	private static final long serialVersionUID = 1978198479659022715L;

	private final Map<K,V> m;     // Backing Map
	final Object      mutex;        // Object on which to synchronize

	SynchronizedMap(Map<K,V> m) {
		this.m = Objects.requireNonNull(m);
		mutex = this;
	}

	SynchronizedMap(Map<K,V> m, Object mutex) {
		this.m = m;
		this.mutex = mutex;
	}

	......
	
```

```java
Collections.synchronizedMap(new HashMap<>(16));
```

我们在调用这个方法的时候就需要传入一个Map，可以看到有两个构造器，如果你传入了mutex参数，则将对象排斥锁赋值为传入的对象。
如果没有，则将对象排斥锁赋值为this，即调用synchronizedMap的对象，就是上面的Map。

创建出synchronizedMap之后，再操作map的时候，就会对方法上锁

```java
public int size() {
	synchronized (mutex) {return m.size();}
}
public boolean isEmpty() {
	synchronized (mutex) {return m.isEmpty();}
}
public boolean containsKey(Object key) {
	synchronized (mutex) {return m.containsKey(key);}
}
public boolean containsValue(Object value) {
	synchronized (mutex) {return m.containsValue(value);}
}
public V get(Object key) {
	synchronized (mutex) {return m.get(key);}
}

public V put(K key, V value) {
	synchronized (mutex) {return m.put(key, value);}
}
public V remove(Object key) {
	synchronized (mutex) {return m.remove(key);}
}
public void putAll(Map<? extends K, ? extends V> map) {
	synchronized (mutex) {m.putAll(map);}
}
public void clear() {
	synchronized (mutex) {m.clear();}
}
```

回答得不错，能跟我聊一下Hashtable么？
------

跟HashMap相比Hashtable是线程安全的，适合在多线程的情况下使用，但是效率可不太乐观。

哦，你能说说他效率低的原因么？
------

嗯嗯面试官，我看过他的源码，他在对数据操作的时候都会上锁，所以效率比较低下。

```java
/**
 * Tests if this hashtable maps no keys to values.
 *
 * @return  <code>true</code> if this hashtable maps no keys to values;
 *          <code>false</code> otherwise.
 */
public synchronized boolean isEmpty() {
	return count == 0;
}

/**
 * Returns an enumeration of the keys in this hashtable.
 *
 * @return  an enumeration of the keys in this hashtable.
 * @see     Enumeration
 * @see     #elements()
 * @see     #keySet()
 * @see     Map
 */
public synchronized Enumeration<K> keys() {
	return this.<K>getEnumeration(KEYS);
}
```

除了这个你还能说出一些Hashtable 跟HashMap不一样点么？
------

呃，面试官我从来没使用过他，你容我想想区别的点，说完便开始抓头发，这次不是装的，是真的！

Hashtable 是不允许键或值为 null 的，HashMap 的键值则都可以为 null。

呃我能打断你一下么？为啥 Hashtable 是不允许 KEY 和 VALUE 为 null, 而 HashMap 则可以呢？
------

因为Hashtable在我们put 空值的时候会直接抛空指针异常，但是HashMap却做了特殊处理。

```java
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

但是你还是没说为啥Hashtable 是不允许键或值为 null 的，HashMap 的键值则都可以为 null？
------

这是因为Hashtable使用的是**安全失败机制（fail-safe）**，这种机制会使你此次读到的数据不一定是最新的数据。

如果你使用null值，就会使得其无法判断对应的key是不存在还是为空，因为你无法再调用一次contain(key）来对key是否存在进行判断，ConcurrentHashMap同理。

好的你继续说不同点吧。
------

* **实现方式不同**：Hashtable 继承了 Dictionary类，而 HashMap 继承的是 AbstractMap 类。Dictionary 是 JDK 1.0 添加的，貌似没人用过这个，我也没用过。

* **初始化容量不同**：HashMap 的初始容量为：16，Hashtable 初始容量为：11，两者的负载因子默认都是：0.75。

* **扩容机制不同**：当现有容量大于总容量 * 负载因子时，HashMap 扩容规则为当前容量翻倍，Hashtable 扩容规则为当前容量翻倍 + 1。

* **迭代器不同**：HashMap 中的 Iterator 迭代器是 fail-fast 的，而 Hashtable 的 Enumerator 不是 fail-fast 的。

所以，当其他线程改变了HashMap 的结构，如：增加、删除元素，将会抛出ConcurrentModificationException 异常，而 Hashtable 则不会。

fail-fast是啥？
------

**快速失败（fail—fast）**是java集合中的一种机制， 在用迭代器遍历一个集合对象时，如果遍历过程中对集合对象的内容进行了修改（增加、删除、修改），则会抛出Concurrent Modification Exception。

他的原理是啥？
------

迭代器在遍历时直接访问集合中的内容，并且在遍历过程中使用一个 modCount 变量。

集合在被遍历期间如果内容发生变化，就会改变modCount的值。

每当迭代器使用hashNext()/next()遍历下一个元素之前，都会检测modCount变量是否为expectedmodCount值，是的话就返回遍历；否则抛出异常，终止遍历。

Tip：这里异常的抛出条件是检测到 modCount！=expectedmodCount 这个条件。如果集合发生变化时修改modCount值刚好又设置为了expectedmodCount值，则异常不会抛出。

因此，不能依赖于这个异常是否抛出而进行并发操作的编程，这个异常只建议用于检测并发修改的bug。

说说他的场景？
------

java.util包下的集合类都是快速失败的，不能在多线程下发生并发修改（迭代过程中被修改）算是一种安全机制吧。

Tip：安全失败（fail—safe）大家也可以了解下，java.util.concurrent包下的容器都是安全失败，可以在多线程下并发使用，并发修改。

哦？那你跟我说说ConcurrentHashMap的数据结构吧，以及为啥他并发度这么高？
------

ConcurrentHashMap 底层是基于 数组 + 链表 组成的，不过在 jdk1.7 和 1.8 中具体实现稍有不同。

我先说一下他在1.7中的数据结构吧：

![ConcurrentHashMap在JDK1.7中数据结构]()

如图所示，是由 Segment 数组、HashEntry 组成，和 HashMap 一样，仍然是数组加链表。

Segment 是 ConcurrentHashMap 的一个内部类，主要的组成如下：

```java
static final class Segment<K,V> extends ReentrantLock implements Serializable {

    private static final long serialVersionUID = 2249069246763182397L;

    // 和 HashMap 中的 HashEntry 作用一样，真正存放数据的桶
    transient volatile HashEntry<K,V>[] table;

    transient int count;

    // 记得快速失败（fail—fast）么？
    transient int modCount;        

	// 大小
	transient int threshold;
	
	// 负载因子    
	final float loadFactor;}
```

HashEntry跟HashMap差不多的，但是不同点是，他使用volatile去修饰了他的数据Value还有下一个节点next。

volatile的特性是啥？
------

* 保证了不同线程对这个变量进行操作时的可见性，即一个线程修改了某个变量的值，这新值对其他线程来说是立即可见的。（实现可见性）

* 禁止进行指令重排序。（实现有序性）

* volatile 只能保证对单次读/写的原子性。i++ 这种操作不能保证原子性。

那你能说说他并发度高的原因么？
------

原理上来说，ConcurrentHashMap 采用了分段锁技术，其中 Segment 继承于 ReentrantLock。

不会像 HashTable 那样不管是 put 还是 get 操作都需要做同步处理，理论上 ConcurrentHashMap 支持 CurrencyLevel (Segment 数组数量)的线程并发。

每当一个线程占用锁访问一个 Segment 时，不会影响到其他的 Segment。

就是说如果容量大小是16他的并发度就是16，可以同时允许16个线程操作16个Segment而且还是线程安全的。
