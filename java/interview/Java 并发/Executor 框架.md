看在前面
====

* <a href="https://github.com/Snailclimb/JavaGuide/blob/master/docs/java/Multithread/java%E7%BA%BF%E7%A8%8B%E6%B1%A0%E5%AD%A6%E4%B9%A0%E6%80%BB%E7%BB%93.md#%E4%BA%8C-executor-%E6%A1%86%E6%9E%B6">java线程池学习总结</a>

简介
====

Executor 框架是 Java5 之后引进的，在 Java 5 之后，通过 Executor 来启动线程比使用 Thread 的 start 方法更好，除了更易管理，效率更好（用线程池实现，节约开销）外，还有关键的一点：有助于避免 this 逃逸问题。

> 补充：this 逃逸是指在构造函数返回之前其他线程就持有该对象的引用。调用尚未构造完全的对象的方法可能引发令人疑惑的错误。

Executor 框架不仅包括了线程池的管理，还提供了线程工厂、队列以及拒绝策略等，Executor框架让并发编程变得更加简单。

Executor 框架结构(主要由三大部分组成)
====

**1. 任务(```Runnable``` /```Callable```)**

执行任务需要实现的 ```Runnable 接口``` 或 ```Callable接口```。```Runnable 接口```或 ```Callable 接口``` 实现类都可以被 ```ThreadPoolExecutor``` 或 ```ScheduledThreadPoolExecutor``` 执行。

**2. 任务的执行(```Executor```)**

如下图所示，包括任务执行机制的核心接口 ```Executor``` ，以及继承自 ```Executor``` 接口的 ```ExecutorService``` 接口。```ThreadPoolExecutor``` 和 ```ScheduledThreadPoolExecutor``` 这两个关键类实现了 ```ExecutorService``` 接口。

**这里提了很多底层的类关系，但是，实际上我们需要更多关注的是 ```ThreadPoolExecutor``` 这个类，这个类在我们实际使用线程池的过程中，使用频率还是非常高的。**

> 注意： 通过查看 ```ScheduledThreadPoolExecutor``` 源代码我们发现 ```ScheduledThreadPoolExecutor``` 实际上是继承了 ```ThreadPoolExecutor``` 并实现了 ```ScheduledExecutorService``` ，而 ```ScheduledExecutorService``` 又实现了 ```ExecutorService```，正如我们下面给出的类关系图显示的一样。

```ThreadPoolExecutor``` 类描述:

```java
//AbstractExecutorService实现了ExecutorService接口
public class ThreadPoolExecutor extends AbstractExecutorService
```

```ScheduledThreadPoolExecutor``` 类描述:

```java
//ScheduledExecutorService实现了ExecutorService接口
public class ScheduledThreadPoolExecutor
        extends ThreadPoolExecutor
        implements ScheduledExecutorService
```

**3. 异步计算的结果(```Future```)**

```Future 接口```以及 ```Future 接口```的实现类 ```FutureTask``` 类都可以代表异步计算的结果。

当我们把 ```Runnable接口``` 或 ```Callable 接口``` 的实现类提交给 ```ThreadPoolExecutor``` 或 ```ScheduledThreadPoolExecutor``` 执行。（调用 ```submit()``` 方法时会返回一个 ```FutureTask``` 对象）

Executor 框架的使用示意图
====

![Executor框架使用示意图](https://github.com/Mein-Augenstern/MUYI/blob/master/java/interview/picture/Executor%E6%A1%86%E6%9E%B6%E4%BD%BF%E7%94%A8%E7%A4%BA%E6%84%8F%E5%9B%BE.png)

1. **主线程首先要创建实现 ```Runnable``` 或者 ```Callable``` 接口的任务对象。**
2. **把创建完成的实现 ```Runnable/Callable```接口的 对象直接交给 ```ExecutorService``` 执行:**```ExecutorService.execute（Runnable command））```或者也可以把 ```Runnable ```对象或```Callable```对象提交给 ```ExecutorService```执行（```ExecutorService.submit（Runnable task）```或```ExecutorService.submit（Callable <T> task）```）
3. **如果执行```ExecutorService.submit(...)```，```ExecutorService```将返回一个实现```Future```接口的对象**。由于```FutureTask```实现了```Runnable```，我们也可以创建```FutureTask```，然后直接交给```ExecutorService```执行。
4. **最后，主线程可以执行```FutureTask.get()```方法来等待任务执行完成。主线程也可以执行FutureTask.cancel(boolean mayInterruptIfRunning)来取消此任务的执行。**
