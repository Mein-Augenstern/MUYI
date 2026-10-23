JDK25 并发终局：ScopedValue 与结构化并发
====

JDK21 用虚拟线程解决了「线程贵」（见 [虚拟线程-vs-线程池-JDK21](./虚拟线程-vs-线程池-JDK21.md)），JDK25（2025-09 GA，LTS）补上配套的两块拼图。一句话定位：**ScopedValue（JEP 506）转正，接替 ThreadLocal 做线程上下文载体；结构化并发（JEP 505）进入第 5 轮预览，JEP 543 已 targeted 到 JDK 28 转正**。面试里这是同一条链：虚拟线程便宜了 → 每任务一线程 → ThreadLocal 模式崩 → ScopedValue + 结构化并发接管。能把链条讲通，比单背 API 值钱。

1 从 ThreadLocal 到 ScopedValue
------

1.1 语义速览
------

```java
private static final ScopedValue<User> USER = ScopedValue.newInstance();
private static final ScopedValue<String> TRACE_ID = ScopedValue.newInstance();

// 入口处绑定，作用域 = call() 的整个动态范围
ScopedValue.where(USER, currentUser)
           .where(TRACE_ID, tid)               // 多个绑定链式声明
           .call(() -> handle(request));        // 返回即解绑，抛异常也解绑

// 任意深层调用里取值
User u = USER.get();                  // 作用域内必有绑定
String tid = TRACE_ID.orElse("none"); // 可能未绑定时的兜底
```

四条核心语义，每条都对着 ThreadLocal 的软肋：

* **不可变绑定**：只有 where 绑定，没有 set 修改；一个作用域内值恒定，被多个线程读同一份不可变数据，天然线程安全；
* **有界生命周期**：绑定存活于 call()/run() 的动态范围内，方法返回（无论正常还是异常）自动失效——没有 remove()，也就没有忘调 remove() 的事故；
* **rebind 而非 set**：内层确实要「改」怎么办？再开一个作用域遮蔽（shadow）外层值，外层完全不受影响：

```java
ScopedValue.where(USER, systemUser).call(() -> {
    audit();        // 本作用域内 USER.get() == systemUser
});
// 外层作用域的 USER 仍是原值——写不坏别人
```

* **继承**：作用域内创建的子线程（尤其是结构化并发 fork 出的子任务）自动继承当前绑定——这是与虚拟线程时代配套的设计。

1.2 与虚拟线程的配合
------

虚拟线程把线程数放大到百万级，也让「每线程一份可变副本」的 ThreadLocal 成本模型崩塌（前作第 4 节）。ScopedValue 的实现思路正相反：**每线程只持有一个指向不可变绑定链的引用**，进入作用域压栈、退出弹栈，写一次、读多次；百万虚拟线程共享同一份绑定对象，内存不随线程数放大。它就是为虚拟线程时代设计的上下文载体。

2 ThreadLocal 三宗罪与 ScopedValue 的解法
------

三宗罪的机理详见 [ThreadLocal](./ThreadLocal.md) 与 [ThreadLocal源码阅读记录](./ThreadLocal源码阅读记录.md)，这里直接上对照：

| 罪状 | ThreadLocal 的问题 | ScopedValue 的解法 |
| ---- | ---- | ---- |
| 内存放大 | 每线程一份 ThreadLocalMap，百万虚拟线程 × N 个 entry 被放大 | 绑定一次不可变，线程间共享同一份引用，无每线程副本 |
| 复用污染 | 线程池复用线程，上个任务的值泄漏给下个任务（串号） | 生命周期绑定作用域而非线程，call 返回即失效，任务间不可能串 |
| 清理靠约定 | remove() 忘写 → 泄漏/串值，靠 finally 和 code review 兜底 | 无需清理，语言结构保证失效 |

一句话：**ThreadLocal 的正确性靠程序员自律，ScopedValue 的正确性靠语言结构**。

边界也要说清（加分点）：ScopedValue 不是全能替代——值不可变，确实需要每线程独立可变状态的场景（如缓冲区复用）仍属 ThreadLocal 的地盘，或者干脆改显式传参/局部变量；ThreadLocal 在 JDK 里长期共存，不会被移除。

3 结构化并发：让任务树与调用树同构
------

3.1 非结构化的痛
------

ExecutorService/CompletableFuture 的任务关系是「发射后不管」：主线程提交之后，谁在跑、跑完没有、异常去了哪，全靠手工同步。

```java
// 非结构化：fork 出去的任务脱离了任何语法作用域
Future<A> fa = pool.submit(this::fetchA);
Future<B> fb = pool.submit(this::fetchB);   // fetchA 失败时 fetchB 还在空跑
A a = fa.get();                              // 异常到这行才冒出来，且不知 fb 死活
```

3.2 树形任务模型
------

结构化并发的主张：**并发任务的生命周期必须落在一段语法作用域内（try-with-resources），形成与调用树同构的任务树**。JDK21 预览期的经典写法（多数资料里的模样）：

```java
// JDK21 预览 API（JEP 453 时代，子任务默认跑在虚拟线程上）
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    Subtask<Order>  o = scope.fork(() -> orderClient.get(id));   // fork：挂进任务树
    Subtask<Detail> d = scope.fork(() -> detailClient.get(id));  // 与上者并行
    scope.joinAndThrowFailed();      // join：等全树；任一失败则抛出
    return merge(o.get(), d.get());  // 全部成功才走到这
}   // close：作用域退出，树内任务必已结束——结构上不可能泄漏线程
```

四个关键机制：

* **fork**：在当前作用域内开一个子任务挂进任务树；子任务自动继承当前的 ScopedValue 绑定——两个 API 是一套组合拳；
* **join**：等待本作用域全部子任务结束；**错误传播**：ShutdownOnFailure 策略下，任一子任务失败会让 join 抛出首个异常；
* **取消扩散**：一个子任务失败 → 同级兄弟被 interrupt 取消 → 异常向父作用域冒泡——「一损俱损」的树语义，替代手写的 Future.cancel 编排；
* **生命周期封闭**：close() 保证退出作用域时没有存活的子任务——对比 ExecutorService 忘 shutdown 的经典泄漏事故，这类 bug 在结构上被消灭。

3.3 JDK25 的 API 演进（预览期的代价）
------

迟迟未转正的原因之一就是接口仍在打磨：JDK25（JEP 505）把「构造器 + 内置子类」重构为**静态工厂 open() + 显式 Joiner 策略**：

```java
// JDK25（JEP 505 第 5 轮预览）：零参 open() 默认「全部成功或任一失败取消」
try (var scope = StructuredTaskScope.open()) {
    Subtask<A> a = scope.fork(this::fetchA);
    Subtask<B> b = scope.fork(this::fetchB);
    scope.join();                    // 任一失败：join 抛 FailedException，兄弟被取消
    return merge(a.get(), b.get());
}

// 其他策略显式给 Joiner：awaitAllSuccessfulOrThrow（承接 ShutdownOnFailure 语义）、
// anySuccessfulResultOrThrow（竞速取首个成功）、allUntil(predicate)（自定义取消条件）
try (var scope = StructuredTaskScope.open(
        StructuredTaskScope.Joiner.awaitAllSuccessfulOrThrow())) {
    ...
}
```

老写法的 ShutdownOnFailure 语义没有丢，只是从「内置子类」改为显式 Joiner——面试时两种形态都要认得。**第 5 轮预览意味着 API 仍可能微调**：JEP 543 已于 2026-10-15 targeted 到 JDK 28 转正，终局在望；生产核心链路上用预览特性需要 --enable-preview 的取舍判断。

4 JDK 21 → 25 → 28 演进时间线
------

| 版本（GA） | 虚拟线程 | ScopedValue | 结构化并发 | 备注 |
| ---- | ---- | ---- | ---- | ---- |
| JDK 21（2023-09，LTS） | 转正（JEP 444） | 首轮预览（JEP 446） | 首轮预览（JEP 453） | synchronized 块内阻塞会 pinning |
| JDK 22 / 23 | — | 持续预览 | 持续预览 | 语义打磨 |
| JDK 24（2025-03） | JEP 491：monitor 重构，synchronized 不再 pinning | 持续预览 | 第 4 轮预览（JEP 499） | 虚拟线程对存量代码兼容的分水岭 |
| JDK 25（2025-09，LTS） | 稳定 | **转正（JEP 506）** | 第 5 轮预览（JEP 505），open()+Joiner 重构 | 生产主线基线 |
| JDK 26（2026-03-17） | — | — | 预览持续 | HTTP/3 客户端、任意 GC 的 AOT 缓存 |
| JDK 28（预计 2027-03） | — | — | **JEP 543 拟转正**（2026-10-15 已 targeted） | 并发三件套全部收官 |

单独强调 JDK 24 那一格的意义：JEP 491 重构 monitor 实现，synchronized 块内阻塞不再钉住载体线程（只剩 native 帧场景）——存量代码不改造就能放心上虚拟线程，这是「虚拟线程默认可用」的分水岭，也是 Boot 4 时代把它列为默认可选线程模型的前提（Spring 侧见 [SpringBoot4与Framework7升级要点](../../框架与中间件/Spring/SpringBoot4与Framework7升级要点.md)）。

5 生产迁移路径：登录态与 traceId 透传
------

5.1 入口改造示例
------

```java
// 旧：ThreadLocal 版（set + finally remove，靠纪律）
public class AuthFilter implements Filter {
    public void doFilter(...) {
        UserContext.set(user);                  // ThreadLocal.set
        try { chain.doFilter(req, res); }
        finally { UserContext.remove(); }       // 忘了就串号/泄漏
    }
}

// 新：ScopedValue 版（作用域即生命周期）
public class AuthFilter implements Filter {
    private static final ScopedValue<User> CURRENT = ScopedValue.newInstance();
    public void doFilter(...) {
        ScopedValue.where(CURRENT, user)
                   .run(() -> chain.doFilter(req, res));   // 返回即解绑
    }
}
```

traceId 同理：入口绑定一次，深层日志直接读。注意 MDC 本身是 ThreadLocal 实现的存量设施，过渡期常见做法是「where 绑定时同步写 MDC、作用域结束清理」做桥接；虚拟线程下 MDC 的每线程副本仍有成本，高并发服务可优先把 traceId 本身换成 ScopedValue 传。

5.2 跨线程边界怎么办
------

* **作用域内 fork 的子任务**：自动继承绑定，零改造——这是优先把并行 fan-out 改成结构化并发的直接收益；
* **提交给线程池/异步 SDK 的任务**：ScopedValue 不跟着走——它只继承「作用域内创建的线程」（这是与 InheritableThreadLocal 的关键差异，后者按线程创建关系传递、池化场景照样出事故）。池边界只能显式传值或在新线程的任务内重新 where 绑定；
* 迁移顺序建议：

1. JDK 25（LTS）就位，虚拟线程入口先铺开；
2. 入口层上下文 ThreadLocal → ScopedValue：先迁登录态/租户号这类不可变值，可变状态留下或改显式传参；
3. 并行 fan-out 从 CompletableFuture/池提交改 StructuredTaskScope——预览开关的取舍：非核心链路可先用，核心链路等 JDK 28 转正（JEP 543 已 targeted）；
4. 补验证观测链路：取消扩散后兄弟任务以中断收场，要在指标/日志里可见，别当静默失败。

收尾对照（扩展前作第 5 节的迁移速查表）：

| 迁移点 | 线程池时代 | 虚拟线程时代（JDK21） | JDK25 终局形态 |
| ---- | ---- | ---- | ---- |
| 上下文透传 | ThreadLocal | 显式传参 / ScopedValue（预览） | ScopedValue（final） |
| 并行编排 | CompletableFuture / 池提交 | newVirtualThreadPerTaskExecutor + CF | StructuredTaskScope（JDK 28 转正在望） |

面试追问链
------

* ScopedValue 和 ThreadLocal 的本质区别？→ 不可变绑定 + 有界生命周期 + 作用域继承；正确性从「靠自律」变「靠结构」；
* 那还要不要 remove？→ 没有 remove 这个 API，call/run 返回自动失效；追问：值能改吗？→ 不能，只能嵌套 where rebind 遮蔽，外层不受影响；
* 跨线程池传得过去吗？→ 传不过去，只继承作用域内创建的线程；池边界要显式传值或任务内重绑——与 InheritableThreadLocal 的差异；
* 虚拟线程为什么更需要 ScopedValue？→ 百万线程 × ThreadLocalMap 内存放大 + 线程不复用使「下个任务覆盖旧值」的隐式清理假设失效；
* 结构化并发解决什么？→ 任务树与调用树同构：错误传播、取消扩散、close 保证生命周期封闭（无泄漏）；
* ShutdownOnFailure 怎么没了？→ JDK25 预览重构为 open() + Joiner 显式策略，零参 open() 默认即「全部成功或任一失败取消」，语义保留、API 换壳；
* 为什么还没转正？→ API 打磨到第 5 轮预览，JEP 543 已 targeted 到 JDK 28（2026-10-15）；
* JDK 24 的修复为什么重要？→ JEP 491 让 synchronized 块内阻塞不再 pinning，存量代码零改造上虚拟线程。

面试官视角小结
------

这题的三层：及格层是知道 ScopedValue/StructuredTaskScope 的名字和基本 API；中间层是讲得出「三宗罪 → 逐条解法」和「任务树同构、错误传播、取消扩散」的设计动机；高级层是能主动点出边界——值不可变的适用限制、跨池不传递、预览状态与 JDK 28 时间点、MDC 等存量 ThreadLocal 设施的桥接成本。与 [虚拟线程-vs-线程池-JDK21](./虚拟线程-vs-线程池-JDK21.md) 互为表里：那篇答「为什么用虚拟线程、坑在哪」，本篇答「上下文与编排如何收尾」，两篇合起来就是「JDK21 开局、JDK25 收官」的完整并发答卷。

> 2026-10 新增（2026 技术栈补全）。
