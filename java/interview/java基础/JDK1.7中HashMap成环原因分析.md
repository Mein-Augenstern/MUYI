看在前面
====

* <a href="https://coolshell.cn/articles/9606.html">感谢High一下! 酷 壳 – CoolShell-->疫苗：JAVA HASHMAP的死循环分享</a>

* <a href="https://juejin.im/post/5dee6f54f265da33ba5a79c8">《吊打面试官》系列-HashMap</a>

一句话描述
====

主要原因在于 并发下的```Rehash``` 会造成元素之间会形成一个循环链表。不过，jdk 1.8 后解决了这个问题，但是还是不建议在多线程下使用 ```HashMap```,因为多线程下使用 ```HashMap``` 还是会存在其他问题比如数据丢失。并发环境下推荐使用 ```ConcurrentHashMap``` 。

关于HashMap需要知道的事情
====

**java8之前是头插法**，就是说新来的值会取代原有的值，原有的值就顺推到链表中去，就像上面的例子一样，因为写这个代码的作者认为后来的值被查找的可能性更大一点，提升查找的效率。

但是，**在java8之后，都是所用尾部插入了**。

使用头插会改变链表的上的顺序，但是如果使用尾插，在扩容时会保持链表元素原本的顺序，就不会出现链表成环的问题了。

Java7在多线程操作HashMap时可能引起死循环，原因是扩容转移后前后链表顺序倒置，在转移过程中修改了原来链表中节点的引用关系。

Java8在同样的前提下并不会引起死循环，原因是扩容转移后前后链表顺序不变，保持之前节点的引用关系。

为什么要重新Hash呢，直接复制过去不香么？
====

**是因为长度扩大以后，Hash的规则也随之改变**。

Hash的公式---> ```index = HashCode（Key） & （Length - 1）```

原来长度（Length）是8你位运算出来的值是2 ，新的长度是16你位运算出来的值明显不一样了。

写在前面
====

在淘宝内网里看到同事发了贴说了一个CPU被100%的线上故障，并且这个事发生了很多次，原因是在Java语言在并发情况下使用```HashMap```造成```Race Condition```，从而导致死循环。这个事情我4、5年前也经历过，本来觉得没什么好写的，因为Java的```HashMap```是非线程安全的，所以在并发下必然出现问题。但是，我发现近几年，很多人都经历过这个事（在网上查“```HashMap Infinite Loop```”可以看到很多人都在说这个事）所以，觉得这个是个普遍问题，需要写篇疫苗文章说一下这个事，并且给大家看看一个完美的“```Race Condition```”是怎么形成的。

问题的症状
====

从前我们的Java代码因为一些原因使用了```HashMap```这个东西，但是当时的程序是单线程的，一切都没有问题。后来，我们的程序性能有问题，所以需要变成多线程的，于是，变成多线程后到了线上，发现程序经常占了100%的CPU，查看堆栈，你会发现程序都Hang在了```HashMap.get()```这个方法上了，重启程序后问题消失。但是过段时间又会来。而且，这个问题在测试环境里可能很难重现。

我们简单的看一下我们自己的代码，我们就知道```HashMap```被多个线程操作。而Java的文档说```HashMap```是非线程安全的，应该用```ConcurrentHashMap```。

但是在这里我们可以来研究一下原因。

Hash表数据结构
====

我需要简单地说一下```HashMap```这个经典的数据结构。

HashMap通常会用一个指针数组（假设为```table[]```）来做分散所有的```key```，当一个```key```被加入时，会通过```Hash```算法通过```key```算出这个数组的下标i，然后就把这个```<key, value>```插到```table[i]```中，如果有两个不同的```key```被算在了同一个i，那么就叫冲突，又叫碰撞，这样会在```table[i]```上形成一个链表。

我们知道，如果```table[]```的尺寸很小，比如只有2个，如果要放进10个keys的话，那么碰撞非常频繁，于是一个O(1)的查找算法，就变成了链表遍历，性能变成了O(n)，这是Hash表的缺陷（可参看<a href="https://coolshell.cn/articles/6424.html">《Hash Collision DoS 问题》</a>）。

所以，```Hash```表的尺寸和容量非常的重要。一般来说，```Hash```表这个容器当有数据要插入时，都会检查容量有没有超过设定的```thredhold```，如果超过，需要增大```Hash```表的尺寸，但是这样一来，整个```Hash```表里的无素都需要被重算一遍。这叫```rehash```，这个成本相当的大。

相信大家对这个基础知识已经很熟悉了。

<a href="https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/HashMap%E6%BA%90%E7%A0%81%E6%B3%A8%E9%87%8A.md">如果不熟悉，请点击</a>

HashMap的rehash源代码
====

下面，我们来看一下Java的```HashMap```的源代码。

Put一个Key,Value对到Hash表中：

```java
public V put(K key, V value)
{
    ......
    //算Hash值
    int hash = hash(key.hashCode());
    int i = indexFor(hash, table.length);
    //如果该key已被插入，则替换掉旧的value （链接操作）
    for (Entry<K,V> e = table[i]; e != null; e = e.next) {
        Object k;
        if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
            V oldValue = e.value;
            e.value = value;
            e.recordAccess(this);
            return oldValue;
        }
    }
    modCount++;
    //该key不存在，需要增加一个结点
    addEntry(hash, key, value, i);
    return null;
}
```

检查容量是否超标

```java
void addEntry(int hash, K key, V value, int bucketIndex)
{
    Entry<K,V> e = table[bucketIndex];
    table[bucketIndex] = new Entry<K,V>(hash, key, value, e);
    //查看当前的size是否超过了我们设定的阈值threshold，如果超过，需要resize
    if (size++ >= threshold)
        resize(2 * table.length);
}
```

新建一个更大尺寸的```Hash```表，然后把数据从老的```Hash```表中迁移到新的```Hash```表中。

```java
void resize(int newCapacity)
{
    Entry[] oldTable = table;
    int oldCapacity = oldTable.length;
    ......
    //创建一个新的Hash Table
    Entry[] newTable = new Entry[newCapacity];
    //将Old Hash Table上的数据迁移到New Hash Table上
    transfer(newTable);
    table = newTable;
    threshold = (int)(newCapacity * loadFactor);
}
```

迁移的源代码，注意高亮处：

```java
void transfer(Entry[] newTable)
{
    Entry[] src = table;
    int newCapacity = newTable.length;
    //下面这段代码的意思是：
    //  从OldTable里摘一个元素出来，然后放到NewTable中
    for (int j = 0; j < src.length; j++) {
        Entry<K,V> e = src[j];
        if (e != null) {
            src[j] = null;
            do {
                Entry<K,V> next = e.next;
                int i = indexFor(e.hash, newCapacity);
                e.next = newTable[i];
                newTable[i] = e;
                e = next;
            } while (e != null);
        }
    }
}
```
好了，这个代码算是比较正常的。而且没有什么问题。

正常的ReHash的过程
====

画了个图做了个演示。

* 我假设了我们的hash算法就是简单的用key mod 一下表的大小（也就是数组的长度）。

* 最上面的是old hash 表，其中的Hash表的size=2, 所以key = 3, 7, 5，在mod 2以后都冲突在table[1]这里了。

* 接下来的三个步骤是Hash表 resize成4，然后所有的<key,value> 重新rehash的过程

![正常的ReHash过程](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/HashMap%E7%9A%84ReHash%E8%BF%87%E7%A8%8B.jpg)

并发下的Rehash
====

1）**假设我们有两个线程**。我用红色和浅蓝色标注了一下。

我们再回头看一下我们的 transfer代码中的这个细节：

```java
do {
    Entry<K,V> next = e.next; // <--假设线程一执行到这里就被调度挂起了
    int i = indexFor(e.hash, newCapacity);
    e.next = newTable[i];
    newTable[i] = e;
    e = next;
} while (e != null);
```
而我们的线程二执行完成了。于是我们有下面的这个样子。

![HashMap并发下ReHash过程一](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/HashMap%E5%B9%B6%E5%8F%91%E4%B8%8B%E7%9A%84ReHash%E4%B8%80.jpg)

注意，**因为Thread1的 e 指向了key(3)，而next指向了key(7)，其在线程二rehash后，指向了线程二重组后的链表**。我们可以看到链表的顺序被反转后。

**2）线程一被调度回来执行。**

* **先是执行 newTalbe[i] = e;**

* **然后是e = next，导致了e指向了key(7)，**

* **而下一次循环的next = e.next导致了next指向了key(3)**

![HashMap并发下ReHash过程二](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/HashMap%E5%B9%B6%E5%8F%91%E4%B8%8B%E7%9A%84ReHash%E4%BA%8C.jpg)

**3）一切安好。**

线程一接着工作。**把key(7)摘下来，放到newTable[i]的第一个，然后把e和next往下移。**

![HashMap并发下ReHash过程三](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/HashMap%E5%B9%B6%E5%8F%91%E4%B8%8B%E7%9A%84ReHash%E4%B8%89.jpg)

**4）环形链接出现。**

**e.next = newTable[i] 导致  key(3).next 指向了 key(7)**

**注意：此时的key(7).next 已经指向了key(3)， 环形链表就这样出现了。**

![HashMap并发下ReHash过程四](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/HashMap%E5%B9%B6%E5%8F%91%E4%B8%8B%E7%9A%84ReHash%E5%9B%9B.jpg)

**于是，当我们的线程一调用到，HashTable.get(11)时，悲剧就出现了——Infinite Loop。**

其它
====

有人把这个问题报给了Sun，不过Sun不认为这个是一个问题。因为HashMap本来就不支持并发。要并发就用ConcurrentHashmap

http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6423457

我在这里把这个事情记录下来，只是为了让大家了解并体会一下并发环境下的危险。

参考：http://mailinator.blogspot.com/2009/06/beautiful-race-condition.html

