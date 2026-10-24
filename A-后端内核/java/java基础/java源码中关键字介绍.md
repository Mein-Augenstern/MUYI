Java 关键字速查
====

Java 语言规范一共定义了 **50 个关键字**（JDK1.4 加 assert、JDK5 加 enum 后达到 50）。外加 `true/false/null` 三个**字面量**（不算关键字）和 `_`（JDK9 起不能单独作标识符）。按用途分组记忆：

一、50 个关键字分类表
------

| 分类 | 关键字 | 备注 |
| ---- | ---- | ---- |
| 访问控制（3） | private、protected、public | 加上"不写"共 4 级可见性 |
| 类与继承（10） | class、interface、enum、extends、implements、abstract、final、new、this、super | this/super 还可用于构造器互相调用 |
| 包相关（2） | package、import | —— |
| 流程控制（13） | if、else、switch、case、default、for、while、do、break、continue、return、instanceof、assert | assert 是 JDK1.4 加入 |
| 异常处理（6） | try、catch、finally、throw、throws | 共 5 个；加上 finally 的孪生兄弟 finalize（是方法名，不是关键字）常被合并考 |
| 类型与值（8） | boolean、byte、short、int、long、float、double、char、void | 严格数是 9 个（含 void） |
| 变量修饰（3） | static、transient、volatile | 详见下文逐个说 |
| 并发（1） | synchronized | 可修饰方法与代码块 |
| 其他（1） | native | 修饰本地方法，交给 JNI |
| 浮点严格（1） | strictfp | 强制浮点运算遵循 IEEE 754 |
| 保留未用（2） | const、goto | 保留了名字但语言不给用 |

> 分组只为记忆方便，不必纠结每格精确个数。面试常问的是"const/goto 是关键字吗"（是，但不可用）与"true/false/null 是关键字吗"（不是，是字面量）。

原有关键字笔记（transient / volatile / synchronized 对比）
------

## transient

来自于GPT4.0的回答：transient是在Java中用于标记一个实例变量不应该被序列化的关键字。序列化是将一个对象的状态保存为字节流的过程，这样它就可以被持久化或传输。但是，有些变量可能不需要被序列化，例如敏感信息或临时数据。使用transient关键字标记这些变量，可以防止它们被序列化，从而节省空间和提高性能。

## volatile

详见：https://github.com/Mein-Augenstern/MUYI/blob/master/java/interview/Java%20%E5%B9%B6%E5%8F%91/volatile.md

### synchronized与volatile的对比

* **volatile关键字**是线程同步的轻量级实现，所以**volatile性能肯定比synchronized关键字要好。**但是**volatile关键字只能用于变量而synchronized关键字可以修饰方法以及代码块。**synchronized关键字在JavaSE1.6（包括JDK1.6版本）之后引入了偏向锁和轻量级锁以及其他各种优化。这些优化是为了减少获取锁和释放锁带来的性能消耗。优化之后执行效率有了显著提升。**实际开发中使用synchronized关键字的场景还是更多一些。**

* **多线程访问volatile关键字不会发生阻塞，而synchronized关键字可能会发生阻塞。**

* **volatile关键字能保证数据的可见性，但不能保证数据的原子性。synchronized关键字两者都能保证。**

* **volatile关键字主要用于解决变量在多个线程之间的可见性。而synchronized关键字解决的是多个线程之间的访问资源的同步性。**

二、易混淆关键字逐个说
------

1、final —— "不可变"要分三种语境

* 修饰**类**：不能被继承（String、System 就是这么设计的，防继承防重写保安全）；

* 修饰**方法**：不能被子类重写（可被重载）；

* 修饰**变量**：只能赋值一次。基本类型是值不变；引用类型是**引用不变**（指向的对象内容仍可改，final List 依旧能 add）；

* 修饰**参数**：方法内不能重新赋值（匿名内部类捕获局部变量要求 final 或等效 final）。

2、static —— "属于类"的四用法

* 静态变量：类级一份，所有实例共享；

* 静态方法：没有 this，不能直接访问实例成员，不能被重写（只能被"隐藏"）；

* 静态块：类加载时执行一次，只执行一次（对应单例的静态内部类写法）；

* 静态内部类：不持有外部类引用，静态内部类单例就是靠"类加载机制天然线程安全 + 懒加载"实现。

3、default —— JDK8 后一词两义

* switch 里的默认分支（老用法）；

* 接口中的 default 方法：给接口方法带默认实现。解决"接口加方法、全量实现类遭殃"的演进问题（Iterable.forEach 就是 default 方法）；

4、assert —— 断言，默认关闭

* 语法 `assert 条件 : 提示信息;`，条件为假抛 AssertionError；

* JVM 默认不启用，要 `-ea` 打开。所以**不能用它做业务校验**，定位是开发期自检；生产环境校验请用显式抛异常。

5、native —— 委托给本地代码

* `native` 修饰的方法没有方法体，由 C/C++（JNI）或其他本地实现提供，Object.hashCode、Thread.start0 都是 native；

* 与"跨平台"的关系：JVM 把平台相关的脏活交给本地库，Java 层保持一致抽象。

6、strictfp —— 严格浮点

* 不同平台的浮点协处理器可能用扩展精度（80 位）参与中间运算。导致**同一表达式在不同机器结果有细微差别**；

* strictfp 强制按 IEEE 754 双精度截断中间结果，保证可复现。极致性能反而希望放开（JDK17 后平台默认已是严格语义，该关键字逐渐淡出视野）。

7、transient（对上文笔记的补充）

* 反序列化后被 transient 的字段是类型默认值（null/0/false），需要自行恢复时要配 writeObject/readObject 自定义逻辑；

* static 字段本来就不参与序列化（属于类不属于对象），加 transient 没有意义。

8、volatile（对上文笔记的补充）

* 两层语义：可见性 + 禁止指令重排序，但**不保证原子性**（count++ 照样丢更新，要用 AtomicInteger 或锁）；

* 典型应用是双重检查锁单例的 `instance` 字段，详见 [volatile](../Java%20并发/volatile.md)。

9、synchronized（对上文笔记的补充）

* 三种用法：修饰实例方法（锁 this）、静态方法（锁 Class 对象）、代码块（锁指定对象）；

* 锁升级（偏向锁→轻量级→重量级）的来龙去脉见 [JVM 查漏补缺](../JVM/查漏补缺.md) 与 [synchronized](../Java%20并发/synchronized.md)。

三、放在一起考的经典题
------

* "final、finally、finalize 的区别"——关键字 / 异常收尾块 / Object 的废弃方法，详见 [final finally finalize](final%20finally%20finalize.md)；

* "接口方法能用 static/default 吗"——能（JDK8+），抽象类与接口的语义差异正由此展开；

* "native 方法可以被 abstract 修饰吗"——不能，abstract 要子类实现，native 要本地实现，语义冲突。

> 2026-10 扩充（Round1 查漏）。
