看在前面
===

> * <a href="http://www.mamicode.com/info-detail-2919836.html">多线程面试题</a>

一句话答案
====

CountDownLatch 是计数器，只能使用一次，而 CyclicBarrier 的计数器提供 reset 功能，可以多次使用。但是我不那么认为它们之间的区别仅仅就是这么简单的一点。我们来从 jdk 作者设计的目的来看，javadoc 是这么描述它们的：

> CountDownLatch: A synchronization aid that allows one or more threads to wait until a set of operations being performed in other threads completes.(CountDownLatch: 一个或者多个线程，等待其他多个线程完成某件事情之后才能执行；) CyclicBarrier : A synchronization aid that allows a set of threads to all wait for each other to reach a common barrier point.(CyclicBarrier : 多个线程互相等待，直到到达同一个同步点，再继续一起执行。)

对于 CountDownLatch 来说，重点是“一个线程（多个线程）等待”，而其他的 N 个线程在完成“某件事情”之后，可以终止，也可以等待。而对于 CyclicBarrier，重点是多个线程，在任意一个线程没有完成，所有的线程都必须等待。

CountDownLatch 是计数器，线程完成一个记录一个，只不过计数不是递增而是递减，而 CyclicBarrier 更像是一个阀门，需要所有线程都到达，阀门才能打开，然后继续执行。


CyclicBarrier和CountDownLatch的区别
====

两个看上去有点像的类，都在java.util.concurrent下，都可以用来表示代码运行到某个点上，二者的区别在于：

1. CyclicBarrier的某个线程运行到某个点上之后，该线程即停止运行，直到所有的线程都到达了这个点，所有线程才重新运行；CountDownLatch则不是，某线程运行到某个点上之后，只是给某个数值-1而已，该线程继续运行

2. CyclicBarrier只能唤起一个任务，CountDownLatch可以唤起多个任务

3. CyclicBarrier可重用，CountDownLatch不可重用，计数值为0该CountDownLatch就不可再用了

等待多线程完成的CountDownLatch
------

CountDownLatch允许一个或多个线程等待其他线程完成操作。例如要解析一个Excel中的多个sheet表，等到所有sheet表都解析完之后，程序提示解析完成。

```java
public class Jyy {
    /**
     * parse1先执行，parse2后执行
     * @param args
     */
    public static void main(String[] args) {
        Thread parse1 = new Thread(new Runnable() {
            public void run() {
                System.out.println("parse1");
            }
        });
        
        Thread parse2 = new Thread(new Runnable() {
            public void run() {
                try {
                    parse1.join();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                System.out.println("parse2");
            }
        });
        
        parse1.start();
        parse2.start();
        
        try {
            parse2.join();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        System.out.println("finished");
    }
}
```

程序会按上述逻辑进行，输出结果为：

```java
parse1
parse2
finished
```

```join()```用于让当前线程等待调用```join()```的线程执行结束。其原理是不停的检查调用```join()```的线程t是否存活，如果线程t一直存活则当前线程会永远等待。直到```join()```线程终止后，线程的```notifyAll()```会被调用。```join()```内部实现如下：

```java
if (millis == 0) {
    while (isAlive()) {
        wait(0);
    }
}
```

CountDownLatch也可以实现```join()```功能。

```java
public class Jyy {
    public static void main(String[] args) {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        
        Thread parse1 = new Thread(new Runnable() {
            public void run() {
                System.out.println("parse1");
                countDownLatch.countDown();
            }
        });
        
        Thread parse2 = new Thread(new Runnable() {
            public void run() {
                System.out.println("parse2");
                countDownLatch.countDown();
            }
        });
        
        parse1.start();
        parse2.start();
        
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        System.out.println("finished");
    }
}
```

CountDownLatch的构造函数接收一个int类型的参数作为计数器，如果你想等待N个点完成，这里就传入N。

当我们调用一次CountDownLatch的countDown方法时，N就会减1，CountDownLatch的await会阻塞当前线程，直到N变成零。由于countDown方法可以用在任何地方，所以这里说的N个点，可以是N个线程，也可以是1个线程里的N个执行步骤。用在多个线程时，你只需要把这个CountDownLatch的引用传递到线程里。

注意：计数器必须大于等于0，只是等于0时候，计数器就是零，调用await方法时不会阻塞当前线程。**CountDownLatch不可能重新初始化或者修改CountDownLatch对象的内部计数器的值**。一个线程调用countDown方法 happen-before 另外一个线程调用await方法。

同步屏障CyclicBarrier
------

CyclicBarrier 的字面意思是可循环使用（Cyclic）的屏障（Barrier）。它要做的事情是，让一组线程到达一个屏障（也可以叫同步点）时被阻塞，直到最后一个线程到达屏障时，屏障才会开门，所有被屏障拦截的线程才会继续干活。CyclicBarrier默认的构造方法是CyclicBarrier(int parties)，其参数表示屏障拦截的线程数量，每个线程调用await方法告诉CyclicBarrier我已经到达了屏障，然后当前线程被阻塞。

```java
public class CyclicBarrierTest {
    static CyclicBarrier c = new CyclicBarrier(2);
    public static void main(String[] args) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    c.await();
                } catch (Exception e) {

                }
                System.out.println(1);
            }
        }).start();

        try {
            c.await();
        } catch (Exception e) {

        }
        System.out.println(2);
    }
}
```

如果把new CyclicBarrier(2)修改成new CyclicBarrier(3)则主线程和子线程会永远等待，因为没有第三个线程执行await方法，即没有第三个线程到达屏障，所以之前到达屏障的两个线程都不会继续执行。

CyclicBarrier还提供一个更高级的构造函数CyclicBarrier(int parties, Runnable barrierAction)，用于在线程到达屏障时，优先执行barrierAction，方便处理更复杂的业务场景。代码如下：

```java
public class Jyy {
    static CyclicBarrier c = new CyclicBarrier(2, new A());

    public static void main(String[] args) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    c.await();
                } catch (Exception e) {

                }
                System.out.println(1);
            }
        }).start();

        try {
            c.await();
        } catch (Exception e) {

        }
        System.out.println(2);
    }

    static class A implements Runnable {

        @Override
        public void run() {
            System.out.println(3);
        }

    }
}
```

输出结果：

```java
3 2 1
或 3 1 2
```

CyclicBarrier的应用场景
------

CyclicBarrier可以用于多线程计算数据，最后合并计算结果的应用场景。比如我们用一个Excel保存了用户所有银行流水，每个Sheet保存一个帐户近一年的每笔银行流水，现在需要统计用户的日均银行流水，先用多线程处理每个sheet里的银行流水，都执行完之后，得到每个sheet的日均银行流水，最后，再用barrierAction用这些线程的计算结果，计算出整个Excel的日均银行流水。

CyclicBarrier和CountDownLatch的区别
------

* CountDownLatch的计数器只能使用一次。而CyclicBarrier的计数器可以使用reset() 方法重置。所以CyclicBarrier能处理更为复杂的业务场景，比如如果计算发生错误，可以重置计数器，并让线程们重新执行一次。

* CountDownLatch强调的是一个线程（或多个）需要等待另外的n个线程干完某件事情之后才能继续执行。 CyclicBarrier强调的是n个线程，大家相互等待，只要有一个没完成，所有人都得等着。

一个更加形象的例子参见文章<a href="https://www.jianshu.com/go-wild?ac=2&url=http%3A%2F%2Faaron-han.iteye.com%2Fblog%2F1591755">尽量把CyclicBarrier和CountDownLatch的区别说通俗点</a>

参考资料：

> 作者：南南啦啦啦
链接：https://www.jianshu.com/p/78905e0e8190
来源：简书
著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。
