写给Java程序员的Rust入门
====

对写了多年 Java 的人，Rust 的价值不是"再学一门语法"，而是**换一个问题解决的时机**：Java 靠 JVM+GC 在运行时兜底（NPE 运行时才炸、内存靠 GC 回收、数据竞争靠 code review 自觉），Rust 把这些问题全部搬进编译期——null 变成 `Option<T>`、异常变成 `Result<T, E>`、数据竞争变成所有权与 Send/Sync 的编译错误。本篇是 Rust 轨首篇，按"心智转换 → 工具链 → 所有权 → 类型系统 → 泛型与 trait → 生命周期 → 智能指针与并发 → 异步 → 错误处理与生态"展开，对照学习法与 [从Java视角看Go-开篇](../go/从Java视角看Go-开篇.md) 一脉相承（Rust→Go→Java 构成同层语言对照系，Python 侧见 [从Java视角看Python工程化-开篇](../python/从Java视角看Python工程化-开篇.md)）。

1 心智转换：四类运行时问题的编译期化
------

| Java 的运行时问题 | Rust 的编译期方案 | 付出的代价 |
|---|---|---|
| NullPointerException | `Option<T>`：不处理 None 拿不到 T | 到处 match，没有便捷兜底 |
| 异常体系（受检之争） | `Result<T, E>` + `?` 显式传播 | 没有 try/catch 一把梭 |
| GC 停顿与内存泄漏 | 所有权：离开作用域编译期确定 drop | 学习曲线最陡的一段 |
| 数据竞争 | 借用规则 + Send/Sync 编译期拒绝 | 共享可变状态要绕道设计 |

一句话：**Java 把安全交给运行时换开发效率，Rust 把安全交给编译器换运行时零开销**。产物是原生二进制，无 JVM、无 GC——这是它拿下系统层（数据库内核、网络中间件、WASM）的底气。

2 工具链与 Cargo：SDKMAN + Maven 的合体
------

对应关系：rustup↔SDKMAN、rustc↔javac、cargo↔Maven/Gradle、clippy↔SonarLint、rustfmt↔google-java-format。版本分 stable/beta/nightly，项目用 `rust-toolchain.toml` 锁版本（类比 `.sdkmanrc`）。

```toml
[package]
name = "hello_cargo"
version = "0.1.0"
edition = "2024"

[dependencies]
rand = "0.8.5"        # 语义是 >=0.8.5, <0.9.0；精确锁定写 "=0.8.5"
```

与 Maven 的三个关键差异：

* **禁止白嫖传递依赖**：用到哪个 crate 必须自己声明，靠别人的依赖"顺带"可用是不存在的；
* **版本冲突不做 mediation**：Maven 最近优先可能埋 NoSuchMethodError，Cargo 直接多版本共存各自链接，不会运行期缺方法；
* **大包 + features 是常态**：tokio 这类按需开特性、靠 dead code elimination 裁剪；`Cargo.lock` 类比 npm lockfile；日常用 `cargo check` 只查编译不产出，最快。

3 所有权与借用：最劝退也最核心
------

规则一句话：每个值有唯一 owner，owner 离开作用域值被 drop，赋值默认是 move 不是拷贝（基础类型实现 `Copy` trait，按位复制不 move，所以 `let x = 5` 不用纠结）。

```rust
let s1 = String::from("hello");
let s2 = s1;              // 所有权移走，s1 从此失效，再用直接编译错误
let s3 = s2.clone();      // 显式深拷贝
```

借用规则一句话：**同一时间，要么任意多个不可变引用 `&T`，要么仅一个可变引用 `&mut T`，二者不能共存**。配 NLL（非词法生命周期）：引用的存活到"最后一次使用"为止，不是到作用域结尾。全部检查在编译期完成，运行时零开销。Javaer 的对应直觉：编译器强制你回答"这里谁在读、谁在写"，读写在编译期就不许打架。

4 类型系统：struct 只有字段，enum 远强于 Java
------

```rust
#[derive(Debug)]
struct Rectangle { width: u32, height: u32 }

impl Rectangle {
    fn area(&self) -> u32 { self.width * self.height }       // &self：只读借用，最常用
    fn square(size: u32) -> Self { Self { width: size, height: size } } // 关联函数 ≈ static 方法
}
```

self 三形态对照标准库：`len(&self)` 只读、`push_str(&mut self)` 可变、`into_bytes(self)` 拿走所有权——签名即文档。

enum 是带数据的代数类型，Java 要用 sealed interface + record 才能模拟：

```rust
enum Message {
    Quit,
    Move { x: i32, y: i32 },
    Write(String),
    ChangeColor(i32, i32, i32),
}
```

两个最重要的内置枚举：

* `Option<T>`：`enum Option<T> { None, Some(T) }`。与 `Optional` 的本质区别是**类型系统强制**——不处理 None 编译不过，Optional 只是提醒；
* `Result<T, E>`：`enum Result<T, E> { Ok(T), Err(E) }`，配 `?` 操作符——解包 Ok、遇 Err 提前 return 并自动转换错误类型，相当于每处都显式标注的 throws；match 必须穷尽所有变体、可解构、是表达式（if let 是语法糖），穷尽性检查是重构安全网：新增变体后所有 match 处编译报错，不会漏改。

5 泛型与 trait：单态化 vs 类型擦除
------

泛型语法与 Java 几乎一致，但底层是**单态化（monomorphization）**：每个具体类型生成一份特化代码，零运行时开销；Java 是类型擦除 + 一份代码 + 运行时装箱。

trait ≈ interface，但两点不同：

* `impl Trait for Type` 与类型定义分离，可给已有类型实现新 trait（Kotlin 扩展函数的来源）；受**孤儿规则**约束——trait 或类型至少一个属于你的 crate，绕行用 newtype 模式；
* trait bound `<T: PartialOrd>` ≈ `<T extends Comparable<T>>`，多个用 `+`，复杂的用 `where`；`#[derive(Debug, Clone, PartialEq, Eq, Hash)]` 一行省样板，类比 Lombok。

分发方式二选一：`impl Trait`/泛型是编译期单态化（静态分发），默认选它；`&dyn Trait` 走虚表（动态分发），异质集合 `Vec<Box<dyn Greet>>` 这类场景必须用。关联类型的取舍同理：同一类型对 trait 只该有一种实现 → 关联类型（Iterator 的 `type Item`）；可多次实现 → 泛型参数（From、Add）。

6 生命周期：描述关系，不是改变存活
------

核心原则只有一句：**引用的存活不能超过它指向的值**，编译期由借用检查器验证：

```rust
fn longest<'a>(x: &'a str, y: &'a str) -> &'a str {
    if x.len() > y.len() { x } else { y }    // 'a 取二者存活期的交集
}
```

标注不改变存活时间，只描述关系。三条省略规则（每个引用参数各有生命周期；单一输入赋给全部输出；方法里 `&self` 的生命周期赋给输出）覆盖业务代码绝大多数场景。实用建议：被生命周期卡住时，最简单解法是换 owned 类型（`&str`→`String`、`&[T]`→`Vec<T>`），业务代码几乎不用手写标注；`'static` 别无脑加。

7 智能指针与并发：把纪律做进类型系统
------

* `Box<T>`：堆分配。递归类型（`Cons(i32, Box<List>)`）、大对象、`Box<dyn Trait>`；
* `Rc<T>`：单线程引用计数多所有权，`Rc::clone` 只 +1 计数；只给 `&T` 不给 `&mut T`；
* `RefCell<T>`：内部可变性——借用检查推到运行时，违规 panic。适合"对外 &self、对内改 cache"；
* 经典组合 `Rc<RefCell<T>>` ≈ Java 普通对象引用的"多处持有 + 随时可变"；多线程版即 `Arc<Mutex<T>>`。

Mutex 的设计哲学是全篇点题的一例：**把"必须先加锁"做进类型系统**——数据装在锁内，拿不到锁就访问不到数据（Java synchronized 靠自觉，忘锁是运行时事故）。Drop 时机编译期确定（对比 finalize 靠 GC 心情），这就是 RAII。

Send（所有权可跨线程转移）与 Sync（`&T` 可多线程共享）由编译器自动派生：`Rc`/`RefCell`/`MutexGuard` 不满足，跨线程只能换 Arc——错误在编译期就出现，而不是线上 race 之后。

8 异步：tokio 与 Future 的惰性
------

标准库不带 runtime，事实标准是 tokio（≈Netty + ForkJoinPool：N 个 worker + 本地队列 + work-stealing，但调度的是可让出的 Future 而非从头跑到尾的 Runnable）。

与 Java 最大的心智差：**Rust Future 是惰性的**——调用 async fn 不执行，`.await` 才驱动；`CompletableFuture` 是 eager 的，创建了就跑。

```rust
#[tokio::main]                            // 宏展开为 Runtime::new().block_on(async { ... })
async fn main() {
    let h = tokio::spawn(fetch(url));     // ≈ executor.submit，要求 Send
    let (a, b) = tokio::join!(fa, fb);    // ≈ allOf：单任务内并发，不要求 Send
}
```

经典坑：std Mutex 的 guard 跨 `.await` 持有 → future is not Send。解法一：用 `{}` 缩短 guard 作用域；解法二：`tokio::sync::Mutex`（更贵，能不用就不用）。同 Netty EventLoop 原则：async 里不要阻塞，同步重活交 `spawn_blocking`（独立线程池）。`select!` ≈ anyOf，未选中的 Future 被丢弃，注意 cancel-safe。

9 错误处理与生态：thiserror/anyhow 分工
------

panic（不可恢复）留给"程序不变量被打破的 bug"：数组越界、`unwrap()`/`expect()`、显式 `panic!`。调用方可处理的错误一律 `Result`；`catch_unwind` 仅供 FFI 边界，不要当 try/catch 用。

库与应用分工：

* **thiserror（库代码）**：derive 定义结构化错误枚举，`#[from]` 生成 From 供 `?` 自动转换，`#[error("...")]` 定义 Display，调用方可按变体 match 分支处理；
* **anyhow（应用代码）**：`anyhow::Result<T>` 聚合一切错误，`.context()` 加错误链（类比 getCause 链），`fn main() -> anyhow::Result<()>` 直接打印整链。

常用 crate 按 Java 对应物索引：serde+serde_json↔Jackson（derive 编译期生成、非反射）、reqwest↔OkHttp、tracing↔SLF4J+Logback（`#[instrument]` 自动 span ≈ AOP 日志）、sqlx↔JdbcTemplate（编译期连库校验 SQL）。

面试追问链
------

* Rust 和 Java 最根本的差异？→ 不是语法，是把 NPE/异常/内存/数据竞争四类运行时问题编译期化，运行时零开销；
* Option 和 Optional 的区别？→ 类型系统强制处理 None，还是库层面的提醒；
* 借用规则一句话？→ 任意多个 `&T` 或一个 `&mut T`，不能共存；NLL 让存活期到最后一次使用为止；
* move 什么时候发生？→ 非 Copy 类型的赋值与传参；想保留原值用 clone 或借用；
* Rust 泛型与 Java 泛型的底层差异？→ 单态化 vs 类型擦除；零开销的代价是编译慢、二进制大；
* `impl Trait` 和 `&dyn Trait` 怎么选？→ 默认静态分发，异质集合才用虚表；
* Rc 和 Arc 差在哪？→ 非原子/原子引用计数；RefCell 为什么不能跨线程？→ 借用检查推迟到运行时且非 Sync；
* Mutex 的哲学？→ 数据装在锁内，不拿锁访问不到；对比 Java synchronized 靠自觉；
* Rust Future 与 CompletableFuture 的差异？→ 惰性 vs eager；guard 跨 await 怎么办？→ 缩作用域或 tokio::sync::Mutex；
* 库和应用的错误类型怎么选？→ thiserror 暴露规整枚举，anyhow 聚合。

面试官视角小结
------

Java 背景转 Rust 的候选人，区分度不在语法而在**是否接受了"编译器说了算"的协作方式**：能把 Option/Result 讲成类型系统保证而非"更好的 Optional"，能说出借用规则与 NLL，能解释单态化与类型擦除的取舍，说明过了第一道门。再往上是工程判断：`Rc<RefCell<T>>` 与 `Arc<Mutex<T>>` 的对应关系、guard 跨 await 的 Send 问题、thiserror/anyhow 的库应用分工——讲得清这些的，是真写过能跑的 Rust。只会"Rust 安全、性能好"这类形容词的，还停留在公众号阶段。

> 2026-10 新增（Rust 轨首篇）。

> 来源：javadoop《写给 Java 程序员的 Rust 入门》（https://javadoop.com/post/rust-for-java-developers）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
