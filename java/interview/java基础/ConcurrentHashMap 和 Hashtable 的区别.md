看在前面
====

* <a href="https://www.cnblogs.com/chengxiao/p/6842045.html">ConcurrentHashMap实现原理及源码分析</a>

ConcurrentHashMap 和 Hashtable 的区别主要体现在实现线程安全的方式上不同。
====

* **底层数据结构：** JDK1.7的ConcurrentHashMap底层采用 **分段的数组+链表** 实现，JDK1.8采用的数据结构跟HashMap1.8的结构一样，数组+链表/红黑二叉树。Hashtable和JDK1.8之前的HashMap的底层数据结构类似都是采用 **数组+链表** 的形式，数组是HashMap的主体，链表则是主要为了解决哈希冲突而存在的；

* **实现线程安全的方式（重要）：**

    1）**在JDK1.7的时候，ConcurrentHashMap（分段锁）**对整个桶数组进行了分割分段（Segment），每一把锁只锁容器其中一部分数据，多线程访问容器里不同数据段的数据，就不会存在锁竞争，提高并发访问率。**到了JDK1.8的时候已经摒弃了Segment的概念，而是直接用Node数组+链表+红黑树的数据结构来实现，并发控制使用synchronized和CAS来操作。（JDK1.6以后对synchronized锁做了很多优化）**整个看起来就像是优化过且线程安全的HashMap，虽然在JDK1.8中还可以看到Segment的数据结构，但是已经简化了属性，只是为了兼容旧版本；

    2） **Hashtable（同一把锁）：**使用 synchronized 来保证线程安全，效率非常低下。当一个线程访问同步方法时，其他线程也访问同步方法，可能会进入阻塞或轮询状态，如使用 put 添加元素，另一个线程不能使用 put 添加元素，也不能使用 get，竞争会越来越激烈效率越低。
    
    
