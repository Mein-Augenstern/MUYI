一句话描述
====

在 JDK1.2 之前，Java的内存模型实现总是从主存（即共享内存）读取变量，不需要特别的注意。而在当前的 Java 内存模型下，线程可以把变量保存在本地内存（比如机器的寄存器）中。而不是直接在主存中读写。这就可能造成一个线程在主存中修改了一个变量的值。而另外一个线程还继续使用它在寄存器中的变量值的拷贝，造成数据的不一致。

要解决这个问题，就需要把变量声明为volatile，这就指示JVM，这个变量是不稳定的。每次使用它都到主存中读取。说白了，volatile 关键字的主要作用就是**保证变量的可见性然后还有一个作用是防止指令重排序**。

volatile 关键字
====

大部分开发者应该都知道怎么使用这个关键字，只是可能不太了解个中缘由。**如果你下次面试的时候有人问你 volatile 的作用，记住两点：内存可见性和禁止指令重排序。**

**<h3>要点速览</h3>**

* **内存可见性**：读一个 volatile 变量之前需先使相应的本地缓存失效，必须到主内存读取最新值；写一个 volatile 属性会立即刷入到主内存。所以，**volatile 读和 monitorenter 有相同的语义，volatile 写和 monitorexit 有相同的语义**。
* **禁止重排序**：**volatile 属性操作和它周围的普通属性的操作也不能重排序**。推论：线程 a 写入一个 volatile 变量，线程 b 再读取该变量，则对于线程 a 可见的所有属性，对于线程 b 都是可见的。
* **无原子性**：volatile 属性的读写操作都是无锁的，**它不能替代 synchronized**（没有提供原子性和互斥性），适用于一写多读、状态标志等场景。

以上三点的完整论述见 [Java内存模型](../JVM/JVM内存管理/Java内存模型.md)。

一、JMM 两层语义：为什么需要 volatile
------

volatile 的两条语义（可见性、有序性）都源自 Java 内存模型（JMM）的抽象，系统讲解见 [Java内存模型](../JVM/JVM内存管理/Java内存模型.md)。JMM 可以从两层理解：

* **规范层**：JMM 定义了线程与主内存的抽象关系——每个线程有本地内存（工作内存）。线程对变量的操作都发生在本地内存，何时同步回主内存由 JMM 的 happens-before 规则约束。编译器、处理器只要遵守这套规则，就可以自由地做优化（缓存、重排序）；

* **硬件层**：对应到真实机器，本地内存 ≈ CPU 缓存/寄存器，主内存 ≈ 物理内存。没有同步语义时，一个线程的写可能停留在自己的缓存里，别的核心看不到。这就是上文"另一个线程还在用寄存器里的旧拷贝"的物理原因。volatile 就是让编译器在生成指令时插入内存屏障，强制走最新的值。

两层合起来一句话：**JMM 是规范，volatile 是规范提供给程序员的"开关"，底层由内存屏障落地**。三者的边界辨析见 [JVM内存结构 VS Java内存模型 VS Java对象模型](../JVM/JVM内存管理/JVM内存结构%20VS%20Java内存模型%20VS%20Java对象模型.md)。

二、内存屏障：volatile 的落地手段
------

处理器层面的内存屏障（memory barrier）是 volatile 语义的实际执行者。JMM 定义了四类屏障，volatile 的插入策略如下：

| 屏障 | 作用 | volatile 写 | volatile 读 |
| ---- | ---- | ---- | ---- |
| StoreStore | 前面的普通写先完成，再执行后面的写 | 写**前**插入 | —— |
| StoreLoad | 前面的写对后面的读可见（四类中最重） | 写**后**插入 | —— |
| LoadLoad | 前面的读先完成，再执行后面的读 | —— | 读**后**插入 |
| LoadStore | 前面的读先完成，再执行后面的写 | —— | 读**后**插入 |

* 效果对照着读：**volatile 写** = 把之前的普通写全部刷出去再写，写完立刻全局可见（StoreStore + StoreLoad）。**volatile 读** = 必须读到最新值，读完后后面的读写不能被提前到它前面（LoadLoad + LoadStore）；

* 这正是"禁止重排序"的机制化表述：屏障就是指令重排不可跨越的墙。所以 volatile 操作"上下的普通操作"都不会乱序——呼应上文要点速览；

* x86 天生是强内存模型（TSO），只有 StoreLoad 一种屏障需要真实指令（lock 前缀）。所以 volatile 在 x86 上读几乎零开销；但 JMM 面向所有架构，不能依赖硬件巧合。

三、happens-before：可见性的判定规则
------

"写 volatile 先于读"不是孤立规则，而是 JMM happens-before 体系的一环。写 v happens-before 后续对 v 的读，再结合**传递性**，就得到上文那条重要推论。推论内容：线程 a 写 v 前的普通写，对之后读 v 的线程 b 全部可见。

| 规则 | 一句话 |
| ---- | ---- |
| 程序顺序规则 | 同线程内，前面的操作 hb 后面的操作 |
| 锁规则 | 解锁 hb 后续加锁 |
| **volatile 规则** | **写 v hb 后续读 v** |
| 线程启动规则 | Thread.start() hb 新线程的任何操作 |
| 线程终止规则 | 线程的所有操作 hb join() 返回 |
| 中断规则 | interrupt() hb 被中断线程检测到中断 |
| 对象终结规则 | 构造完成 hb finalize() |
| 传递性 | a hb b，b hb c ⇒ a hb c |

* 反过来说：两个操作之间没有 happens-before 路径，JMM 就允许重排/不可见。分析并发问题时先画 hb 链，而不是凭直觉。volatile 读写与 monitor 的语义对应关系见上文。synchronized 的完整语义见 [synchronized](synchronized.md)。

四、经典应用：双重检查锁（DCL）单例
------

" volatile + DCL"是这两条语义的综合应用，也是必背代码：

```java
public class Singleton {
    private static volatile Singleton instance;   // 没有 volatile 就是半个正确的单例

    private Singleton() { }

    public static Singleton getInstance() {
        if (instance == null) {                    // 第一次检查：无锁快路径
            synchronized (Singleton.class) {
                if (instance == null) {            // 第二次检查：拿锁后再确认
                    instance = new Singleton();    // 问题就在这一行
                }
            }
        }
        return instance;
    }
}
```

* `new Singleton()` 不是原子操作，分三步：①分配内存 → ②执行构造初始化 → ③把引用赋给 instance。CPU/编译器可能把 ③ 排到 ② 前面；

* 没有 volatile 时：线程 A 执行到"③已赋值、②未完成"的中间态。线程 B 第一次检查看到 instance != null 直接返回，拿到的却是**半初始化对象**。字段是默认值，业务随即出错；

* volatile 的禁止重排序保证 ② 一定先于 ③ 对其他线程可见。两道 if 分别省掉"已初始化后的加锁"和"等锁期间的重复创建"；

* 更省心的替代：静态内部类（类加载机制保证线程安全 + 懒加载）或枚举（《Effective Java》推荐）。

五、volatile vs synchronized vs 原子类：怎么选
------

| | volatile | synchronized | Atomic*(CAS) |
| ---- | ---- | ---- | ---- |
| 可见性 | 保证 | 保证 | 保证 |
| 原子性 | 不保证（i++ 照样丢） | 保证 | 保证（单变量） |
| 阻塞 | 不阻塞 | 可能阻塞 | 不阻塞（自旋） |
| 适用 | 一写多读、状态标志、DCL | 复合操作、临界区 | 单变量计数/累加 |

* 典型合格场景：`volatile boolean running` 作为停止标志（一写多读）、配置引用的整体替换（写少读多，换整个引用）；

* 典型不合格场景：多线程 `count++`（读-改-写三步，volatile 管不了中间态），改用 AtomicInteger 或加锁，CAS 原理见 [CAS](CAS.md)；

* 与单例相关的完整集合篇目：[Java并发基础常见面试题总结](Java%20并发基础常见面试题总结.md)、[ThreadLocal](ThreadLocal.md)。

一句话总结：**volatile = 可见性 + 有序性，唯独没有原子性**。判断要不要它，先看操作是不是"单次写、整体读"，再看要不要复合操作原子性——后者交给锁或 CAS。

> 2026-10 扩充（Round1 查漏）。
