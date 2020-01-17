ArrayList简介
====

ArrayList 的底层是数组队列，相当于动态数组。与 Java 中的数组相比，它的容量能动态增长。在添加大量元素前，应用程序可以使用```ensureCapacity```操作来增加 ArrayList 实例的容量。这可以减少递增式再分配的数量。

它继承于 **AbstractList**，实现了 **List, RandomAccess, Cloneable, java.io.Serializable** 这些接口。

在我们学数据结构的时候就知道了线性表的顺序存储，插入删除元素的时间复杂度为O（n）,求表长以及增加元素，取第 i 元素的时间复杂度为O（1）

ArrayList 继承了AbstractList，实现了List。它是一个数组队列，提供了相关的添加、删除、修改、遍历等功能。

ArrayList 实现了**RandomAccess 接口**， RandomAccess 是一个标志接口，表明实现这个这个接口的 List 集合是支持**快速随机访问**的。在 ArrayList 中，我们即可以通过元素的序号快速获取元素对象，这就是快速随机访问。

ArrayList 实现了**Cloneable 接口**，即覆盖了函数 clone()，**能被克隆**。

ArrayList 实现**java.io.Serializable 接口**，这意味着ArrayList**支持序列化，能通过序列化去传输。**

和 Vector 不同，**ArrayList 中的操作不是线程安全的！** 所以，建议在单线程中才使用 ArrayList，而在多线程中可以选择 Vector 或者 CopyOnWriteArrayList。
