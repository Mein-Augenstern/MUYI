ArrayList 与 Vector 区别呢?为什么要用Arraylist取代Vector呢？
====

一句话答案
====

```Vector``` 类的所有方法都是同步的。可以由两个线程安全地访问一个Vector对象、但是一个线程访问Vector的话代码要在同步操作上耗费大量的时间。

```Arraylist```不是同步的，所以在不需要保证线程安全时建议使用Arraylist。
