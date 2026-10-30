Question
====

> Main方法启动子线程执行任务，Main执行结束，那么程序执行结束了么？

一句话答案
====

没有。main 只是 JVM 启动时创建的**入口线程**，不是进程的"主人"。JVM 退出的条件是：**所有非 daemon 线程都运行结束**，与 main 结不结束无关。只要子线程不是 daemon 线程，main 先走一步，JVM 也会继续活着，等它跑完才退出。

一、JVM 的退出条件
====

Java 语言规范对初始退出条件的描述可以概括为：当 JVM 中**只剩 daemon 线程**在运行时，JVM 正常终止。即：

* main 线程没有任何特殊地位，它结束只是"少了一个非 daemon 线程"；
* 判断进程活不活，数的是**非 daemon 线程的个数**：还有 1 个，JVM 就不退；
* daemon 线程不阻止退出——JVM 走的时候直接把它们抛弃（见第四节陷阱）；
* 另外两条提前退出路径与此无关：代码里调 `System.exit(status)`，或进程被 kill/发生 OOM 等致命错误。

二、main 与子线程的生命周期对照
====

```
main ──┬── new Thread(task) ── start() ── 打印"main 结束" ──╳ main 死亡
       │
child  └────────── 仍在运行 tick...tick... ──────────────╳ 子线程结束
                                                          ▲
                                          JVM 等到它才退出进程
```

| 维度 | main 线程 | 子线程 |
| ---- | ---- | ---- |
| 创建者 | JVM（依据 main 入口） | 应用代码 |
| 角色 | 入口/启动线程，跑完就退 | 与 main 平级的普通线程 |
| 栈 | 独立线程栈 | 独立线程栈 |
| 结束的影响 | 不影响子线程继续运行 | 非 daemon 未结束则 JVM 不退出 |

线程之间是**平级**关系，"父子"只体现在创建瞬间传承的少量属性：daemon 标志、优先级、contextClassLoader、InheritableThreadLocal。运行期互相不依赖，谁先死都不影响对方。

三、代码验证
====

```java
public class MainAndChild {
    public static void main(String[] args) {
        Thread child = new Thread(() -> {
            for (int i = 1; i <= 5; i++) {
                System.out.println("child tick " + i);
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
            }
            System.out.println("child done");
        }, "child-1");
        child.start();
        System.out.println("main 线程执行完毕：" + Thread.currentThread().getName());
    }
}
```

输出顺序：

```
main 线程执行完毕：main
child tick 1
child tick 2
child tick 3
child tick 4
child tick 5
child done          ← 进程直到这里才退出
```

main 打印完就结束了，但进程多活了 5 秒。期间用 `jps` 能看到进程还在，`jstack <pid>` 里 main 线程已消失、child-1 还在 TIMED_WAITING (sleeping)。想让 main **等**子线程再退，调 `child.join()` 即可改变时序。

四、daemon 线程与它的陷阱
====

把子线程设为 daemon，JVM 就不必等它：

```java
Thread t = new Thread(runnable);
t.setDaemon(true);   // 必须在 start() 之前调用，否则抛 IllegalThreadStateException
t.start();
```

陷阱一：**finally 不保证执行**。JVM 退出时直接终止 daemon 线程，不走完 catch/finally：

```java
Thread t = new Thread(() -> {
    try {
        Thread.sleep(60_000);
    } finally {
        flushToFile();   // main 先结束触发 JVM 退出时，这里不会被调用，数据丢失
    }
});
t.setDaemon(true);
t.start();
```

陷阱二：**别让守护线程干落盘的活**。日志异步刷盘、缓冲落库这类任务放 daemon 里，进程退出就可能丢最后一批数据。Logback 异步 Appender 专门用 shutdownHook 处理这件事。

陷阱三：**线程池默认非 daemon**。`Executors.newFixedThreadPool()` 的工作线程都是普通线程，单测里建了池忘 shutdown，测试方法结束后 JVM 迟迟不退，就是这个原因。

适用判断：心跳、监控上报这类"丢了无所谓"的后台任务用 daemon。凡是结果要落地的任务，不用。

五、实战清单
====

* main 结束 ≠ 程序结束：想等子线程做完再退，用 `join()` 或 `CountDownLatch`；
* 想让后台任务不阻止退出：`setDaemon(true)` 且必须在 start 前调用；
* 线程池用完要 `shutdown()`，测试代码可用自定义 daemon ThreadFactory 方便快速退出；
* 停线程的正确姿势是 interrupt 协作式停止，见同目录 [如何正确地停止一个线程？](线程的正确停止方式.md)。

延伸阅读：[如何正确地停止一个线程？](线程的正确停止方式.md)、[关于并发工具包——线程安全的 List、Map、Set](%E5%B9%B6%E5%8F%91%E5%B7%A5%E5%85%B7%E5%8C%85-%E7%BA%BF%E7%A8%8B%E5%AE%89%E5%85%A8%E7%9A%84List%E3%80%81Map%E3%80%81Set.md)。

> 2026-10 扩充（原内容过薄）。
