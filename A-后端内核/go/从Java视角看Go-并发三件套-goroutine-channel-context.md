从Java视角看Go-并发三件套-goroutine-channel-context
====

开篇把 goroutine 和虚拟线程的异同点到为止（见 [从Java视角看Go-开篇](./从Java视角看Go-开篇.md)），本篇是真正写生产 Go 代码前的硬通货：**goroutine/channel/context 三件套的工程语义**。写惯 JUC 的人到 Go 最大的危险不是不会用，而是**用 Java 直觉套 Go 语义**——本篇按"心智模型对照 → channel 语义矩阵 → 并发编排三模式 → 泄漏排查"展开，每节都先给 Java 对应物。

1 goroutine：没有线程池的世界
------

**Java 直觉**：并发 = 线程池（`ExecutorService`），池大小是核心调优项，任务提交靠 `submit` 拿 Future。

**Go 现实**：`go f()` 直接起，没有池。GMP 调度（G=goroutine/M=内核线程/P=逻辑处理器）把"多少线程跑多少任务"的全局问题拆掉了——goroutine 初始栈 2KB 按需增长，十万并发是常态，**"并发度"不再是资源约束，"共享数据的安全"才是**。

```
Java:  任务 → 池（大小调优）→ 线程（1MB 栈）        池是稀缺资源的管理者
Go:    任务 → goroutine（2KB 起）← M:N 调度垫底      并发本身不稀缺，直接表达
```

Javaer 最常问"那限流怎么办"——答案是**并发不受限、资源受限**：用带缓冲 channel 当信号量（见 §3.2），而不是池。1.14 起 goroutine 支持异步抢占（函数调用点 + 10ms 时间片），死循环不再饿死调度器，与 JVM 的安全点思路殊途同归。

2 channel：语义矩阵比 API 更重要
------

channel 不是 Java 任何类的对应物——最接近的比喻是 **`SynchronousQueue`（无缓冲）和 `ArrayBlockingQueue`（有缓冲）的合体，且多了"关闭"这个一等语义**。真正要背下来的是这张矩阵：

| 操作 | nil channel | 已关闭 channel | 正常 channel |
|---|---|---|---|
| 发送 `ch <-` | **永久阻塞** | **panic** | 阻塞或入缓冲 |
| 接收 `<-ch` | **永久阻塞** | 立即返回零值 + `ok=false` | 阻塞或取缓冲 |
| `close(ch)` | panic | panic | 关闭 |

三条工程推论：

* **关闭语义是广播**：关闭后所有接收者立刻醒（`ok=false`），这是 Java 里要 `CountDownLatch` 模拟的多播唤醒——"生产者退出，全体消费者收工"一行 `close` 搞定；
* **谁发送谁关闭**：接收方关闭 = 未来某个发送者 panic 的定时炸弹；多发送者场景关闭权上收到协调者（用额外的 done channel 或 WaitGroup 汇合后再 close）；
* **nil channel 在 select 里有妙用**：把不想参与的分支置 nil 即"禁用该分支"（矩阵第一列的永久阻塞恰好让 select 跳过它）——轮询多路复用时的惯用开关。

**有缓冲 vs 无缓冲的选择标准**：无缓冲 = 同步握手（发送等到接收才返回，语义是"交接"）；有缓冲 = 解耦（语义是"队列"）。需要背压语义时用无缓冲，需要削峰时才有缓冲——Javaer 拿 `ArrayBlockingQueue` 直觉默认加缓冲，Go 惯用法默认**不加**。

3 并发编排三模式（对照 JUC 器件逐个翻译）
------

### 3.1 WaitGroup = CountDownLatch（可复用版）

```go
var wg sync.WaitGroup
for _, t := range tasks {
    wg.Add(1)
    go func() {
        defer wg.Done()   // 必须 defer：panic 也要 Done，否则主协程永久等
        process(t)
    }()
}
wg.Wait()
```

对照 `CountDownLatch`：Add/Done/Wait 与 countDown/await 一一对应；差异是**Go 没有 Future**——要结果就往 channel 收集，要错误就用 errgroup。

### 3.2 信号量 = 带缓冲 channel（Go 版池限流）

```go
sem := make(chan struct{}, 8)        // 最多 8 并发
for _, t := range tasks {
    sem <- struct{}{}                // 占坑（满了就阻塞——背压）
    go func() {
        defer func() { <-sem }()     // 释放坑
        process(t)
    }()
}
```

这是"无限 goroutine + 有界资源"的标准解，1.21 后也可以用 `golang.org/x/sync/errgroup` 的 `SetLimit(8)` 一步到位。

### 3.3 errgroup = CompletableFuture.allOf（带错误传播）

```go
g, ctx := errgroup.WithContext(ctx)   // 任一任务出错 → ctx 取消 → 其余任务感知退出
for _, t := range tasks {
    g.Go(func() error { return fetch(ctx, t) })
}
if err := g.Wait(); err != nil { ... }
```

对照表：`errgroup.Go`≈`supplyAsync`、`g.Wait()`≈`allOf().join()`、`WithContext` 的取消联动≈`CompletableFuture` 的 `cancel` 传染但 Go 版**取消是真语义**（ctx 会传进 fetch 内部），Java 的 Future cancel 只是标记。**扇出聚合请求的标准姿势就是它**——这是开篇学习路径里"入门考题"的答案。

4 context：Java 里没有的"取消树"
------

Java 的取消是 `Thread.interrupt()`——设标志位，阻塞操作抛 `InterruptedException`，靠人肉传播。Go 把取消做成**显式的值，随调用链传递**：

```go
ctx, cancel := context.WithTimeout(ctx, 3*time.Second)
defer cancel()                        // 释放计时器资源——不 defer 就是泄漏（Go 最常见的 ctx 反模式）
res, err := fetch(ctx, url)           // ctx 是函数第一个参数：ctx context.Context——全社区铁律
```

四个要点：

* **Done()/Err() 二件套**：`<-ctx.Done()` 阻塞等待取消；`ctx.Err()` 区分 `context.Canceled`（上游主动）与 `context.DeadlineExceeded`（超时）。服务边界判 `errors.Is(err, context.Canceled)` 决定记 warn 还是 error——**客户端断连导致的取消不是错误**；
* **树形传播**：子 ctx 随父取消（WithTimeout/WithCancel/WithDeadline 都从父派生）——一个请求的取消自动吹熄它扇出的所有子任务，这正是 errgroup 模式的地基；
* **协作式，不抢占**：ctx 取消不会杀死 goroutine，只是发信号——长循环要自觉检查 `ctx.Done()`，和 Java interrupt 需要 `Thread.currentThread().isInterrupted()` 自查同理，但 Go 有编译器与社区规范双重强制（第一参数）；
* **只传值不用它传业务参数**：`ctx.Value` 只放请求级元数据（traceID），当 ThreadLocal 用就是反模式（Javaer 的本能，要压住）。

5 goroutine 泄漏：Go 版线程泄漏，排查三板斧
------

**泄漏形态**：goroutine 卡在"发送无人收"或"接收永不至"——channel 矩阵第一列的永久阻塞就是泄漏现场。典型代码：带超时的 select 里起了 helper goroutine 往 channel 送结果，超时方先走，helper 永久阻塞。

**排查**：
1. `runtime.NumGoroutine()` 打点看趋势（只升不降=泄漏）；
2. `import _ "net/http/pprof"` 后 `go tool pprof http://localhost:6060/debug/pprof/goroutine`——看泄漏 goroutine 卡在哪一行（ `-debug=2` 可看创建栈）；
3. 测试期用 `uber-go/goleak`（`TestMain` 里 `goleak.VerifyTestMain`）——单测就抓泄漏，等价于 Java 侧对线程池 shutdown 的断言。

**预防心法**：每个起 goroutine 的地方问一句"谁在什么条件下能让它返回"——答案依赖"对面有人收/有人关"的，就得有超时或 ctx 兜底；**channel 的每个阻塞点都该出现在某个 select 里且该 select 有退出分支**。

6 与 Java 并发器件总对照
------

| Go | Java 对应 | 关键差异 |
|---|---|---|
| `go f()` | `executor.submit` / 虚拟线程 | 无池；并发不受限、资源受限 |
| `sync.WaitGroup` | `CountDownLatch` | 可复用；必须 defer Done |
| 无缓冲 channel | `SynchronousQueue` + `take/put` | 关闭语义=多播唤醒 |
| 带缓冲 channel | `ArrayBlockingQueue` | close 后读零值不阻塞 |
| `select` | 无（NIO/Netty 精神对应） | 多路复用 + default 非阻塞 + nil 禁用 |
| `errgroup` | `CompletableFuture.allOf` | 取消是真传播（ctx 传染） |
| `context` | `Thread.interrupt` + ThreadLocal | 显式树形值传递；协作式 |
| `sync.Once` | double-checked singleton | 语言级，无锁心智负担 |
| `go test -race` | JCStress / 商用工具 | 官方内置，CI 必开 |

面试追问链
------

* 无缓冲和有缓冲 channel 的本质区别？→ 交接（同步）vs 队列（异步）；什么时候必须无缓冲？→ 需要背压/握手语义时；
* 向已关闭 channel 发送会怎样？接收呢？→ 发送 panic、接收立刻零值+ok=false；多发送者谁负责 close？→ 协调者在所有发送者退场后 close，或干脆不 close（靠 ctx 退出）；
* goroutine 和虚拟线程调度差异？→ 都 M:N；Go GMP+2KB 可增长连续栈+1.14 异步抢占 vs JVM 栈 chunk 拷贝+安全点；生态差异 Go 以 channel 为中心；
* ctx 取消了 goroutine 会死吗？→ 不会，协作式；那怎么写不泄漏？→ 每个阻塞点进 select 且带 Done 分支，长循环周期性查 Err；
* 怎么定位 goroutine 泄漏？→ NumGoroutine 趋势 + pprof goroutine profile 卡点行 + goleak 单测拦截；
* errgroup 比 WaitGroup 好在哪？→ 错误传播 + 首错即取消（WithContext）+ SetLimit 限流三合一；
* map 并发读写会怎样？（延伸见下篇）→ fatal 不可 recover，`-race` 或 sync.RWMutex/sync.Map 兜底。

面试官视角小结
------

并发是 Java 老兵转 Go 的主战场，判据非常清晰：**能把"池化思维"切换成"信号量限资源"的、能说出 channel 关闭语义矩阵的、知道 defer cancel 和 goleak 的，是真正写过生产 Go**；还在问"Go 线程池怎么配"或拿 channel 当队列到处加缓冲的，是把 Java 直觉硬套了新语法。最高分的表现是主动对比：同一个扇出聚合需求， CompletableFuture 的 cancel 标记 vs errgroup 的 ctx 真传播——一句话就能看出十年并发功底有没有迁移过来。

> 2026-11 新增（Go 轨第 4 篇）。姊妹篇：[错误处理与panic工程实践](./从Java视角看Go-错误处理与panic工程实践.md)、[slice与map的值语义陷阱](./从Java视角看Go-slice与map的值语义陷阱.md)。
