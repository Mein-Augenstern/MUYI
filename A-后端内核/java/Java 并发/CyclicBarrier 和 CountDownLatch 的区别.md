看在前面
===

> * <a href="http://www.mamicode.com/info-detail-2919836.html">多线程面试题</a>

一句话答案
====

CountDownLatch 是计数器，只能使用一次，而 CyclicBarrier 的计数器提供 reset 功能，可以多次使用。但是我不那么认为它们之间的区别仅仅就是这么简单的一点。我们来从 jdk 作者设计的目的来看，javadoc 是这么描述它们的：

> CountDownLatch: A synchronization aid that allows one or more threads to wait until a set of operations being performed in other threads completes.(CountDownLatch: 一个或者多个线程，等待其他多个线程完成某件事情之后才能执行；) CyclicBarrier : A synchronization aid that allows a set of threads to all wait for each other to reach a common barrier point.(CyclicBarrier : 多个线程互相等待，直到到达同一个同步点，再继续一起执行。)

对于 CountDownLatch 来说，重点是“一个线程（多个线程）等待”。其他的 N 个线程在完成“某件事情”之后，可以终止，也可以等待。而对于 CyclicBarrier，重点是多个线程，在任意一个线程没有完成，所有的线程都必须等待。

CountDownLatch 是计数器，线程完成一个记录一个，只不过计数不是递增而是递减。而 CyclicBarrier 更像是一个阀门，需要所有线程都到达，阀门才能打开，然后继续执行。


CyclicBarrier和CountDownLatch的区别
====

两个看上去有点像的类，都在java.util.concurrent下，都可以用来表示代码运行到某个点上，二者的区别在于：

1. CyclicBarrier的某个线程运行到某个点上之后，该线程即停止运行。直到所有的线程都到达了这个点，所有线程才重新运行。CountDownLatch则不是：某线程运行到某个点上之后，只是给某个数值-1而已。该线程继续运行。

2. CyclicBarrier只能唤起一个任务，CountDownLatch可以唤起多个任务

3. CyclicBarrier可重用，CountDownLatch不可重用，计数值为0该CountDownLatch就不可再用了

两者的用法摘要
------

**等待多线程完成的 CountDownLatch**：允许一个或多个线程等待其他线程完成操作（典型场景：解析一个 Excel 中的多个 sheet，全部解析完再提示完成）。构造函数接收一个 int 参数 N 作为计数器，每调用一次 ```countDown()``` 方法 N 就减 1，```await()``` 会阻塞当前线程直到 N 变成零；countDown 可以用在任何地方，N 个点可以是 N 个线程，也可以是 1 个线程里的 N 个步骤。注意**计数器必须大于等于 0，且 CountDownLatch 不可能重新初始化或修改内部计数器的值**（计数值为 0 后不可再用），它也可以实现 ```Thread.join()``` 的等待功能。完整用法、Doug Lea 的两个经典示例与源码分析见 [CountDownLatch](./CountDownLatch.md)。

**同步屏障 CyclicBarrier**：字面意思是可循环使用（Cyclic）的屏障（Barrier）。让一组线程到达一个屏障（同步点）时被阻塞，直到最后一个线程到达屏障时，屏障才会开门，所有被拦截的线程才继续干活。默认构造方法为 ```CyclicBarrier(int parties)```（parties 为拦截的线程数量），每个线程调用 ```await()``` 表示自己到达屏障；还提供更高级的构造函数 ```CyclicBarrier(int parties, Runnable barrierAction)```，在线程到达屏障时优先执行 barrierAction，适合多线程计算数据、最后合并计算结果的应用场景（如多线程统计各 sheet 流水后汇总日均流水）。源码实现（基于 ReentrantLock + Condition）与使用示例见 [CyclicBarrier(循环栅栏)](./CyclicBarrier%28循环栅栏%29.md)。

一个更加形象的例子参见文章<a href="https://www.jianshu.com/go-wild?ac=2&url=http%3A%2F%2Faaron-han.iteye.com%2Fblog%2F1591755">尽量把CyclicBarrier和CountDownLatch的区别说通俗点</a>

参考资料：

> 作者：南南啦啦啦
链接：https://www.jianshu.com/p/78905e0e8190
来源：简书
著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。
