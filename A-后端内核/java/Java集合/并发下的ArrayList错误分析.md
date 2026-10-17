看在前面
=====

* 并发下的ArrayList错误分析：https://blog.csdn.net/lan861698789/article/details/81697409
* 为什么说ArrayList是线程不安全的：https://zhuanlan.zhihu.com/p/140374850

现象举例以及原因分析
====

* 并发导致数据丢失样例
* 并发导致数据新增为null
* 并发导致数据越界

三种情况分析
------

首先看看这个类所拥有的部分属性字段：

```java
public class ArrayList<E> extends AbstractList<E>
        implements List<E>, RandomAccess, Cloneable, java.io.Serializable
{
    /**
     * 列表元素集合数组
     * 如果新建ArrayList对象时没有指定大小，那么会将EMPTY_ELEMENTDATA赋值给elementData，
     * 并在第一次添加元素时，将列表容量设置为DEFAULT_CAPACITY 
     */
    transient Object[] elementData; 

    /**
     * 列表大小，elementData中存储的元素个数
     */
    private int size;
}
```

所以通过这两个字段我们可以看出，ArrayList的实现主要就是用了一个Object的数组，用来保存所有的元素，以及一个size变量用来保存当前数组中已经添加了多少元素。接着我们看下最重要的add操作时的源代码：

```java
public boolean add(E e) {
    /**
     * 添加一个元素时，做了如下两步操作
     * 1.判断列表的capacity容量是否足够，是否需要扩容
     * 2.真正将元素放在列表的元素数组里面
     */
    ensureCapacityInternal(size + 1);  // Increments modCount!!
    elementData[size++] = e;
    return true;
}
```

ensureCapacityInternal()这个方法的详细代码我们可以暂时不看，它的作用就是判断如果将当前的新元素加到列表后面，列表的elementData数组的大小是否满足，如果size + 1的这个需求长度大于了elementData这个数组的长度，那么就要对这个数组进行扩容。

由此看到add元素时，实际做了两个大的步骤：

* 判断elementData数组容量是否满足需求
* 在elementData对应位置上设置值

这样也就出现了第一个导致线程不安全的隐患，在多个线程进行add操作时可能会导致elementData数组越界。具体逻辑如下：

* 列表大小为9，即size=9

* 线程A开始进入add方法，这时它获取到size的值为9，调用ensureCapacityInternal方法进行容量判断。

* 线程B此时也进入add方法，它获取到size的值也为9，也开始调用ensureCapacityInternal方法。

* 线程A发现需求大小为10，而elementData的大小就为10，可以容纳。于是它不再扩容，返回。

* 线程B也发现需求大小为10，也可以容纳，返回。

* 线程A开始进行设置值操作， elementData[size++] = e 操作。此时size变为10。

* 线程B也开始进行设置值操作，它尝试设置elementData[10] = e，而elementData没有进行过扩容，它的下标最大为9。于是此时会报出一个数组越界的异常ArrayIndexOutOfBoundsException.

![arraylist-并发数组越界问题出现](https://github.com/Mein-Augenstern/MUYI/blob/master/java/interview/Java%E9%9B%86%E5%90%88%E5%B9%B6%E5%8F%91%E9%97%AE%E9%A2%98/picture/%E6%95%B0%E7%BB%84%E8%B6%8A%E7%95%8C-arraylist.png)

另外第二步 elementData[size++] = e 设置值的操作同样会导致线程不安全。从这儿可以看出，这步操作也不是一个原子操作，它由如下两步操作构成：

* elementData[size] = e;

* size = size + 1;

在单线程执行这两条代码时没有任何问题，但是当多线程环境下执行时，可能就会发生一个线程的值覆盖另一个线程添加的值，具体逻辑如下：

* 列表大小为0，即size=0

* 线程A开始添加一个元素，值为A。此时它执行第一条操作，将A放在了elementData下标为0的位置上。

* 接着线程B刚好也要开始添加一个值为B的元素，且走到了第一步操作。此时线程B获取到size的值依然为0，于是它将B也放在了elementData下标为0的位置上。

* 线程A开始将size的值增加为1

* 线程B开始将size的值增加为2

* 这样线程AB执行完毕后，理想中情况为size为2，elementData下标0的位置为A，下标1的位置为B。而实际情况变成了size为2，elementData下标为0的位置变成了B，下标1的位置上什么都没有。并且后续除非使用set方法修改此位置的值，否则将一直为null，因为size为2，添加元素时会从下标为2的位置上开始。

![arraylist-null和覆盖的情况出现](https://github.com/Mein-Augenstern/MUYI/blob/master/java/interview/Java%E9%9B%86%E5%90%88%E5%B9%B6%E5%8F%91%E9%97%AE%E9%A2%98/picture/null%E5%92%8C%E8%A6%86%E7%9B%96%E6%83%85%E5%86%B5-arraylist.png)

并发导致数据丢失样例
------

* 示例代码

```java
/**
 * ArrayList并发错误演示
 */
public class ArrayListCocurrentTest {

    private static List<String> threadList = new ArrayList<String>();

    public static void main(String[] args) {
        try {
            testWriteArrayListError();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 测试方法
     *
     * @throws Exception
     */
    private static void testWriteArrayListError() throws Exception {
        /** 创建线程 */
        Runnable writeR = new Runnable() {
            public void run() {
                threadList.add(Thread.currentThread().getName());
            }
        };

        /** 循环启动线程启动 */
        for (int i = 0; i < 1000000; i++) {
            Thread t = new Thread(writeR, i + "");
            t.start();
        }

        /** 等待子线程插入完成 */
        Thread.sleep(2000);

        /** 输出集合内容 */
        for (int i = 0; i < threadList.size(); i++) {
            System.out.println("index:" + i + "->" + threadList.get(i));
        }

        /** 输出集合长度大小 */
        System.out.println("集合长度大小: " + threadList.size());
    }

}
```
![ArrayList并发丢失数据结果示例](https://github.com/Mein-Augenstern/MUYI/blob/master/java/interview/Java%E9%9B%86%E5%90%88%E5%B9%B6%E5%8F%91%E9%97%AE%E9%A2%98/picture/ArrayList-%E5%B9%B6%E5%8F%91%E4%B8%A2%E5%A4%B1%E6%95%B0%E6%8D%AE.png)

并发导致数据新增为null情况
------

* 示例代码

```java
/**
 * ArrayList并发错误演示
 */
public class ArrayListCocurrentTest {

    private static List<String> threadList = new ArrayList<String>(200);

    public static void main(String[] args) {
        try {
            testWriteArrayListError();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 测试方法
     *
     * @throws Exception
     */
    private static void testWriteArrayListError() throws Exception {
        /** 创建线程 */
        Runnable writeR = new Runnable() {
            public void run() {
                threadList.add(Thread.currentThread().getName());
            }
        };

        /** 循环启动线程启动 */
        for (int i = 0; i < 10000; i++) {
            Thread t = new Thread(writeR, i + "");
            t.start();
        }

        /** 等待子线程插入完成 */
        Thread.sleep(2000);

        /** 输出集合内容 */
        for (int i = 0; i < threadList.size(); i++) {
            System.out.println("index:" + i + "->" + threadList.get(i));
        }

        /** 输出集合长度大小 */
        System.out.println("集合长度大小: " + threadList.size());
    }

}
```
![ArrayList-新增元素null情况](https://github.com/Mein-Augenstern/MUYI/blob/master/java/interview/Java%E9%9B%86%E5%90%88%E5%B9%B6%E5%8F%91%E9%97%AE%E9%A2%98/picture/ArrayList-%E6%96%B0%E5%A2%9E%E5%85%83%E7%B4%A0null%E6%83%85%E5%86%B5.png)

并发导致数据越界
------

* 样例待补充


