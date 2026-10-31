Phaser 使用指南
====

Phaser 是 JDK7 引入、却常常被忽略的并发工具：**把 CountDownLatch 的"第三方放行"和 CyclicBarrier 的"人到齐开门、可循环"合成一个类，并且 parties 可以动态增减、可以构树分层**。一句话定位：更灵活的多阶段栅栏。面试里它是"CyclicBarrier 和 CountDownLatch 区别"之后的天然追问——答出 Phaser 并说得出 register/arrive/phase/分层这些词，就是并发工具箱完备的信号。本文只讲使用不抠源码；前两者的源码见 [AQS 源码精读（三）：共享模式与工具类基座](./AQS源码精读三-共享模式与工具类基座.md)。

1 同一个需求，三种工具
------

需求：启动 10 个线程，等所有线程都就位后再同时开始执行业务。

**CountDownLatch 版**——最直白：

```java
CountDownLatch latch = new CountDownLatch(1);
for (int i = 0; i < 10; i++) {
    new Thread(() -> {
        try {
            latch.await();      // 每个线程等在栅栏上
            // doWork();
        } catch (InterruptedException ignore) {
        }
    }).start();
}
doSomethingELse();              // 确保每个线程都到了 await() 上
latch.countDown();              // 放开栅栏
```

原理是 AQS 共享模式：count 设给 state，countDown 递减，减到 0 唤醒所有 await 线程。

**CyclicBarrier 版**——这种场景用它更省事：

```java
CyclicBarrier barrier = new CyclicBarrier(10);
for (int i = 0; i < 10; i++) {
    executorService.submit(() -> {
        try {
            barrier.await();
            // doWork()
        } catch (InterruptedException | BrokenBarrierException ex) {
            ex.printStackTrace();
        }
    });
}
```

ReentrantLock + Condition 实现，参与者即等待者，满了自动开门。

**Phaser 版**——本文主角：

```java
Phaser phaser = new Phaser();
phaser.register();              // 1. 注册主线程这一个 party

for (int i = 0; i < 10; i++) {
    phaser.register();          // 2. 每个线程一个 party
    executorService.submit(() -> {
        phaser.arriveAndAwaitAdvance();   // 3. 到栅栏等待所有人
        // doWork()
    });
}
phaser.arriveAndAwaitAdvance(); // 4. 主线程也到栅栏，11 个 party 齐了，同放
```

注意 Phaser 不需要在构造时固定 parties：这里一共 11 次 register()，可类比 CountDownLatch 的 count / CyclicBarrier 的 parties；11 次 `arriveAndAwaitAdvance()` 到齐后所有线程同时通过，进入下一个 phase。

2 重要接口：arrive 家族与动态 parties
------

parties 的三种增加方式：

```java
public Phaser(int parties) {
    this(null, parties);        // 构造时指定
}
public int register() {
    return doRegister(1);       // 加 1
}
public int bulkRegister(int parties) {   // 一次加 N
    if (parties < 0)
        throw new IllegalArgumentException();
    if (parties == 0)
        return getPhase();
    return doRegister(parties);
}
```

减少只有一条路：线程中途退出时 `arriveAndDeregister()`。到达相关的四个方法要分清：

* `arrive()`：声明"我到了"，**不阻塞**。party 与线程没有必然关系——一个线程内可以重复调用 arrive()（比如一个线程代表多个任务方）；
* `arriveAndAwaitAdvance()`：到达并等待其他 party，全到齐后一起进入下一 phase；
* `arriveAndDeregister()`：到达且永久退出，总 parties 减 1；
* `awaitAdvance(int phase)`：阻塞到指定 phase 号的门打开（先 `int phase = phaser.getPhase()` 再在别的线程等这代结束）。

还有个关键钩子 `onAdvance(int phase, int registeredParties)`：protected 方法，每个 phase 结束时被调用，返回 true 表示终结整个 phaser。控制"跑 N 轮就停"就靠覆写：

```java
protected boolean onAdvance(int phase, int registeredParties) {
    return phase >= N || registeredParties == 0;
}
```

终结后 `isTerminated()` 为 true，register()/arrive() 等失去意义，原本返回 phase 的方法一律返回负数。

3 监控方法
------

四个只读监控，写监控大盘或测试断言常用：

| 方法 | 含义 |
| ---- | ---- |
| `getPhase()` | 当前 phase，从 0 起，到 Integer.MAX_VALUE 后溢出归 0 |
| `getRegisteredParties()` | 当前注册的 parties 总数（随 register/deregister 变动） |
| `getArrivedParties()` | 本 phase 已到达的 party 数 |
| `getUnarrivedParties()` | 本 phase 还没到的 party 数 |

4 分层结构：一棵 Phaser 树
------

Phaser 用**单个 volatile long state** 管理全部状态，位布局：

```java
/**
 * 0-15   : unarrived
 * 16-31  : parties，所以一个 phaser 实例最大支持 2^16-1 = 65535 个 parties
 * 32-62  : phase，31 位，最大 Integer.MAX_VALUE，达到后从 0 重新开始
 * 63     : terminated
 */
private volatile long state;
```

单实例 65535 上限的另一面是：parties 越多，对这个 state 的 CAS 竞争越激烈。**解法就是分层（tiering）——构树，让竞争摊到多个 state 上**：

```java
Phaser root = new Phaser(5);
Phaser n1 = new Phaser(root, 5);
Phaser n2 = new Phaser(root, 5);
Phaser m1 = new Phaser(n1, 5);
Phaser m2 = new Phaser(n1, 5);
Phaser m3 = new Phaser(n1, 5);
Phaser m4 = new Phaser(n2, 5);
```

```
            root(5+2)          ← 每个子节点整体算作父节点的 1 个 party
           /        \
       n1(5+3)     n2(5+1)
      /  |  \         \
    m1  m2  m3        m4     ← 叶子节点各 5 个 parties，业务线程注册在这里
```

树的关键规则：

* **注册时机**：父节点上的 party 注册发生在**子节点注册第一个 party 时**，而不是子节点构造时。验证：`Phaser m5 = new Phaser(n2)` 后 n2 的 parties 不变（m5 此刻 parties=0），`m5.register()` 之后 n2 才 +1（输出依次为 6 和 7）；
* **剥离**：子节点 parties 降为 0 时自动从父节点注销。`m5.arriveAndDeregister()` 后 m5 归零，n2 的 parties 从 7 回到 6；
* **phase 协调**：整棵树的 phase 由 root 统一协调，root 的 phase 就是各节点的 phase；任何节点想进入下一 phase 必须等所有节点都结束当前 phase。理解时把整棵树当作一个 phaser 实例，节点只是为降低并发竞争而存在；
* **终止**：terminated 由 root 控制，m5 即使 parties 为 0 也仍可 `m5.register()` 重新加入。

单层够用就单层；两层通常足以应付数千级并发（如 java doc 所述的四层树可支持百万级 parties）。每个节点 parties 取多大、分几层，取决于竞争强度，是留给使用者的调优题。

5 选型：Phaser vs CountDownLatch vs CyclicBarrier
------

| 维度 | CountDownLatch | CyclicBarrier | Phaser |
| ---- | ---- | ---- | ---- |
| 计数/party | 构造时固定 | 构造时固定 | 动态 register/bulkRegister/deregister |
| 角色 | 第三方 countDown 放行 | 参与者即等待者 | 两者皆可（party 与线程无绑定） |
| 可重用 | 一次性，归零即报废 | 循环（generation 代） | 循环（phase 阶段） |
| 回调 | 无 | barrierCommand | onAdvance（可决定终止） |
| 超大规模 | - | - | 树形分层摊竞争 |

经验法则：一锤子买卖用 latch；多轮"全员到齐再走"用 barrier；需要动态增减参与者、按阶段推进或海量 parties 时上 Phaser。原理对比见 [CountDownLatch](./CountDownLatch.md)、[CyclicBarrier(循环栅栏)](./CyclicBarrier%28循环栅栏%29.md)、[CyclicBarrier 和 CountDownLatch 的区别](./CyclicBarrier%20和%20CountDownLatch%20的区别.md)。

面试追问链
------

* Phaser 解决什么问题？→ 多阶段栅栏 + 动态 parties，补齐 latch 不可重用、barrier 计数固定的短板；
* parties 怎么增减？→ register()/bulkRegister() 增，arriveAndDeregister() 减，构造可指定初值；
* arrive() 和 arriveAndAwaitAdvance() 区别？→ 前者声明到岗不阻塞，后者到岗并等全队；party 与线程无必然关系；
* phase 是什么？→ 从 0 开始的周期号，到 Integer.MAX_VALUE 后归 0；awaitAdvance(phase) 可等指定周期；
* onAdvance 干嘛的？→ 每 phase 结束的回调，返回 true 终结 phaser，之后注册/到达失效、phase 方法返回负数；
* 单个 Phaser 最多多少 parties？→ 65535，因为 state 的 parties 占 16 位；
* parties 很多怎么办？→ 构树分层，每个子节点是父节点一个 party，竞争摊到各节点的 state 上；
* 子节点什么时候向父注册？→ 注册第一个 party 时，构造时不注册；parties 归零自动剥离；
* 树的 phase 谁说了算？→ root 统一协调，全部节点结束当前 phase 才能进入下一 phase。

面试官视角小结
------

Phaser 是并发工具链的加分题。候选人若只报菜名，追问"parties 能不能中途加"就能试出深浅；能讲出 arrive 不阻塞、party 与线程解绑、onAdvance 控制终止，说明真用过。分层是源码级亮点——说出"一个 volatile long 装全部状态、16 位 parties 限 65535、构树摊 CAS 竞争"，并能把树当作单一 phaser 理解，基本可判定为并发工具有体系认知。工程判断题则看选型：动态成员、多阶段流水、海量 parties 这三个信号出现时想到 Phaser。

> 来源：javadoop《Phaser 使用介绍》（https://javadoop.com/post/phaser-tutorial）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
