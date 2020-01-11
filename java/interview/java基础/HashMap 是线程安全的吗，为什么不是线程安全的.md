* <a href="https://zhuanlan.zhihu.com/p/21673805">感谢美团技术团队分享-->Java 8系列之重新认识HashMap</a>
* <a href="https://github.com/Snailclimb/JavaGuide/blob/master/docs/java/collection/HashMap.md">感谢Snailclimb-->JavaGuide-->HashMap</a>

一句话答案
====

HashMap不是线程安全的。

Question
====
* 线程不安全的原因是什么？
* 什么情况下会导致HashMap线程不安全？

Map接口UML图
====

![Map接口UML图](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/Map%E6%8E%A5%E5%8F%A3UML%E5%9B%BE.png)


HashMap 简介
====

HashMap 主要用来存放键值对，它基于哈希表的Map接口实现，是常用的Java集合之一。

JDK1.8之前HashMap由 **数组+链表** 组成，数组是HashMap的主体，链表则是主要为了解决哈希冲突而存在的（“拉链法”解决冲突）。JDK1.8以后在解决哈希冲突时有了较大的变化，当链表长度大于阈值（默认为8）时，将链表转化为红黑树（将链表转换成红黑树前会判断，如果当前数组的长度小于64，那么会先进行数组扩容，而不是转换为红黑树），以减少搜索时间，具体实现可以参照 treeifyBin 方法。


HashMap 底层数据结构
====


**JDK1.8之前**

JDK1.8 之前 HashMap 底层是 **数组和链表** 结合在一起使用也就是 **链表散列**。HashMap **通过key的hashCode经过 扰动函数 处理过后得到hash值，然后通过 (n - 1) & hash 判断当前元素存放的位置（这里的n指的是数组的长度），如果当前位置存在元素的话，就判断该元素与要存入的元素的hash值以及key是否相同，如果相同的话，直接覆盖，不相同就通过拉链法解决冲突。**

$\color{DarkOrchid4}{所谓扰动函数指的就是 HashMap 的 hash 方法。使用 hash 方法也就是扰动函数是为了防止一些实现比较差的 hashCode() 方法 换句话说使用扰动函数之后可以减少碰撞。}$

**JDK 1.8 HashMap 的 hash 方法源码:**

JDK 1.8 的 hash方法 相比于 JDK 1.7 hash 方法更加简化，但是原理不变。

```java
static final int hash(Object key) {
    int h;
    // key.hashCode()：返回散列值也就是hashcode
    // ^ ：按位异或
    // >>>:无符号右移，忽略符号位，空位都以0补齐
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

对比一下 JDK1.7的 HashMap 的 hash 方法源码.

```java
static int hash(int h) {
    // This function ensures that hashCodes that differ only by
    // constant multiples at each bit position have a bounded
    // number of collisions (approximately 8 at default load factor).

    h ^= (h >>> 20) ^ (h >>> 12);
    return h ^ (h >>> 7) ^ (h >>> 4);
}
```

相比于 JDK1.8 的 hash 方法 ，JDK 1.7 的 hash 方法的性能会稍差一点点，因为毕竟扰动了 4 次。

所谓 “拉链法” 就是：将链表和数组相结合。也就是说创建一个链表数组，数组中每一格就是一个链表。若遇到哈希冲突，则将冲突的值加到链表中即可。

![](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/HashMap_1.png)

**JDK1.8之后**

相比于之前的版本，jdk1.8在解决哈希冲突时有了较大的变化，当链表长度大于阈值（默认为8）时，将链表转化为红黑树，以减少搜索时间。

![](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/HashMap_2.jpg)


