从Java视角看Go-错误处理与panic工程实践
====

开篇第④点说过"error 是值，没有 try/catch"（见 [从Java视角看Go-开篇](./从Java视角看Go-开篇.md)），本篇解决实战问题：**天天 `if err != nil` 之后，错误链怎么组织、panic 什么时候能用、服务边界怎么翻译错误**。十年 Java 攒下的异常体系直觉（受检/非受检/异常层次/全局 handler）在 Go 里要整体换血——不是换语法，是换**错误的归属权模型**。

1 心智模型：错误是返回值，不是"事件"
------

Java 的异常是**控制流跳转**：throw 点不关心谁处理，catch 点跨越 N 层栈帧捕获。Go 的 error 是**普通返回值**：每一层显式接、显式传，控制流永远可见。

```
Java:  异常是向上冒泡的"事件"，中间层可以完全无感（声明 throws 即可）
Go:    错误是向下传递的"货物"，每一层都要亲手接、亲手转交
```

代价是代码噪音（真实 Go 项目 30% 行数是 err 处理），收益是 **review 时错误路径零隐藏**——没有哪个函数会"偷偷"失败。这笔账 Go 社区算过来回：啰嗦但直白 > 简洁但隐式，与受检异常之争的 Java 阵营表态恰好相反。

2 错误链三件套：包装、判等、断言
------

### 2.1 %w 包装 = 异常 cause 链

```go
if err := loadConfig(); err != nil {
    return fmt.Errorf("加载网关配置失败: %w", err)   // %w 保留错误链（cause）
    //  %v 只是拼字符串，链断了——只加日志不加判断的场景才用 %v
}
```

`%w` 对应 Java 的 `new XxxException("msg", cause)`——带 cause 的包装。链断了（%v）就相当于 `throw new XxxException(e.toString())`， instanceof 断言全废，Javaer 都懂这有多伤。

### 2.2 errors.Is = catch 具体异常 / ==

```go
var ErrNotFound = errors.New("not found")          // sentinel：全局错误值

if errors.Is(err, ErrNotFound) { return 404 }      // 沿链判等（穿透所有 %w 层）
if errors.Is(err, context.Canceled) { return 499 } // 取消≠错误，这是必写边界
if errors.Is(err, sql.ErrNoRows) { ... }           // 标准库 sentinel 同款用法
```

对应 Java 的 `catch (SQLException e)` 里查 `e.getErrorCode()`，或 `instanceof`——区别是 Go 按值判等（哨兵必须是同一实例），所以**哨兵错误用 `var Err... =` 全局单例**，绝不 `errors.New` 两份。

### 2.3 errors.As = catch 类型 + 拿字段

```go
type BizError struct { Code int; Msg string }
func (e *BizError) Error() string { return fmt.Sprintf("[%d] %s", e.Code, e.Msg) }

var be *BizError
if errors.As(err, &be) {                            // 沿链找第一个可赋值类型（注意取址）
    respond(be.Code, be.Msg)
}
```

对应 Java 的 `catch (BizException e) { respond(e.getCode()) }`——自定义错误类型带业务字段，边界处 `As` 出来翻译成 HTTP/gRPC 码。这是**错误体系设计的正确姿势**：领域包定义 1-2 个携带结构化信息的错误类型，比撒一堆哨兵更可维护（1.20 后还有 `errors.Join` 聚合多错，对应 `addSuppressed` 的场景）。

3 panic：语言给了，工程上要用对位置
------

**panic 的正当场景只有两类**：①真·程序错误（空指针解引用、数组越界、断言失败——这些运行时本来就会替你 panic）；②初始化期的 fail-fast（配置缺失直接死，`MustCompile` 就是这个哲学：正则字面量都错了还活着干嘛）。

**panic 不是异常通道**：跨层用 panic 传业务错误，等于在 Go 里伪造 Java 异常——丢掉了"控制流显式"这条 Go 的立身之本，还要在每个边界 `recover` 兜底。**框架边界除外**：gin/net/http 的中间件链条里 `recover()` 把 panic 转 500 是标准姿势（`net/http` 每个 connection 自带 recover，你的 handler panic 不会杀死进程——这点和 Tomcat 一致）：

```go
func Recover() gin.HandlerFunc {
    return func(c *gin.Context) {
        defer func() {
            if r := recover(); r != nil {
                log.Printf("panic: %v\n%s", r, debug.Stack())
                c.AbortWithStatus(500)
            }
        }()
        c.Next()
    }
}
```

注意 `recover()` **只在直接 defer 的函数里生效**——`defer log(recover())` 这种嵌套拿到的是 nil，经典翻车点。

4 服务边界翻译：错误的"出口海关"
------

单体内错误链一路 `%w`，到了出口（HTTP handler / gRPC server）统一翻译——这是 Go 版"全局异常处理器 @ControllerAdvice"：

```go
func handle(c *gin.Context, err error) {
    var be *BizError
    switch {
    case errors.As(err, &be):
        c.JSON(be.Code, gin.H{"error": be.Msg})
    case errors.Is(err, ErrNotFound):
        c.JSON(404, gin.H{"error": "资源不存在"})
    case errors.Is(err, context.Canceled):
        c.JSON(499, gin.H{"error": "客户端已断开"})   // 取消降级为 warn 日志，不打 error
    default:
        log.Printf("[ERR] %+v", err)                  // 未知错误：全文与栈是排查生命线
        c.JSON(500, gin.H{"error": "内部错误"})        // 对外不漏内部细节
    }
}
```

gRPC 侧同构：`status.Error(codes.NotFound, "...")` 出、`status.FromError(err)` 入。要点两条：**错误分级的依据是 `Is/As` 而不是字符串匹配**（match err.Error() 内容是 Javaer 最容易带过来的坏习惯，链一包装就失效）；**取消要单独降级**——客户端断连是常态不是故障，全按 500 记告警会淹死值班的人。

5 六个实战反模式（都是 Java 直觉的锅）
------

* **吞错误继续跑**：`if err != nil { log.Printf(...) }` 之后当没事发生——Go 没有"异常没接住会炸"的安全网，吞了就是静默错误数据。要么处理（换默认值要有注释说明凭什么安全），要么向上传；
* **panic 当异常用**：见 §3，业务错误 panic 是行为艺术；
* **哨兵错误各 New 各的**：两个包各自 `errors.New("not found")`，`Is` 永远不相等——哨兵必须共享同一实例；
* **%v 一路包装**：链断了，边界处 Is/As 全部失灵——包装一律 `%w`，只在"仅记日志不再判断"的最外层用 `%+v`（带栈的详细格式，`github.com/pkg/errors` 风格；标准库栈要看 `debug.Stack()`）;
* **err 变量遮蔽**：`if err := f(); err != nil` 的作用域写法没问题，但循环里 `result, err := f()` 混用 `=` 与 `:=` 会让外层 err 拿不到内层错误——循环体内显式 `var err error` 或统一 `:=`；
* **把 ctx 取消当 5xx**：见 §4，`context.Canceled`/`DeadlineExceeded` 要在边界显式分流。

6 与 Java 异常体系总对照
------

| 维度 | Java | Go |
|---|---|---|
| 传播机制 | 栈展开（throw/catch 跨帧） | 返回值逐层显式传递 |
| 类型层次 | Throwable 继承树 | 接口 + 哨兵值 + 自定义 struct（无层次） |
| cause 链 | `new Ex(msg, cause)` | `fmt.Errorf("%w", err)` |
| 类型判断 | `catch (XxxException e)` / instanceof | `errors.As(err, &target)` |
| 值判断 | `e.getCode()==...` | `errors.Is(err, sentinel)` |
| 多错聚合 | `addSuppressed` | `errors.Join`（1.20+） |
| 不可恢复 | `Error` / RuntimeException | panic（recover 只在框架边界） |
| 全局兜底 | `@ControllerAdvice` | handler 出口 switch 翻译（§4） |
| 栈信息 | 异常自带栈（免费） | error 无栈需 `%+v`/panic 栈（要主动带） |

最后一行的差异常被忽略：**Java 异常天生带栈，Go 的 error 是个值没有栈**——深链路排查时要么用 `github.com/pkg/errors`（`%+v` 打印包装点栈），要么接受"错误消息自己会说话"的纪律：包装时每层带上足够定位的上下文（哪个配置、哪个 URL、哪个 id）。

面试追问链
------

* %w 和 %v 包装的区别？→ %w 保留 Unwrap 链，Is/As 可穿透；什么时候用 %v？→ 包装后不再需要判断、只给人看的最外层；
* errors.Is 和 errors.As 分别对应 Java 什么？→ 哨兵判等≈catch 后比错误码；As≈catch 具体异常类型拿字段；
* panic/recover 能不能当 try/catch 用？→ 语法上能，工程上只留框架边界兜底与初始化 fail-fast；recover 什么情况拿到 nil？→ 非 defer 直接调用、或 defer 里隔了一层函数；
* 客户端断连怎么处理？→ ctx.Canceled 在边界降级 warn + 499，不进 5xx 告警；
* Go 错误没有栈怎么排查线上问题？→ 包装纪律（每层带上下文）+ pkg/errors %+v + panic 路径的 debug.Stack；
* 自定义错误类型设计原则？→ 领域包少量 struct 带结构化字段（Code/Msg），哨兵只给真正全局单点语义（io.EOF 级别）。

面试官视角小结
------

错误处理是 Go 代码Review 的重灾区，也是区分"写过"与"写对"的分水岭：能说出 **%w 链完整、Is/As 在边界分流、Canceled 单独降级**这三件的，生产代码大概率拿得出手；还在字符串匹配错误、panic 满天飞、吞错误打日志继续跑的，是 Java 直觉没换血。加分项是主动对比两套模型的归属权：Java 异常是"事件冒泡找主人"，Go 错误是"货物传递经我手"——能讲清这个视角转换的，说明已经建立了 Go 的错误观而不是语法映射。

> 2026-11 新增（Go 轨第 5 篇）。姊妹篇：[并发三件套](./从Java视角看Go-并发三件套-goroutine-channel-context.md)、[slice与map的值语义陷阱](./从Java视角看Go-slice与map的值语义陷阱.md)。
