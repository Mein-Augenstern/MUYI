从Java视角看Go-slice与map的值语义陷阱
====

开篇语法速览里 slice 和 map 各占了一条坑位（见 [从Java视角看Go-开篇](./从Java视角看Go-开篇.md)），本篇展开成完整弹药库：**从内存布局理解值语义**，这是 Javaer 在 Go 数据结构上出血的头号来源。Java 的数组/对象变量全是指针，Go 的赋值和传参**默认拷贝值**——这条底层规则的每一个投影都值得单独钉一次。

1 先立地基：Go 赋值即拷贝，Java 赋值拷引用
------

```go
a := [3]int{1, 2, 3}
b := a          // 拷贝整个数组！b 改不动 a —— Java 的 int[] b = a 是引用别名
b[0] = 99       // a[0] 仍是 1
```

Java 里"变量=对象的引用"是全天候成立的；Go 里**数组是纯值类型**（赋值/传参全量拷贝），而日常用的 slice/map/channel/func 是**引用语义的补偿**——它们的"值"内部含有指针。所以真实规则是：**拷贝的永远是字面值，只是有的字面值里带指针**。带着这条读下面两节，所有"陷阱"都是推论而非需要背的特例。

2 slice：ptr+len+cap 的三字头视图
------

### 2.1 header 模型

slice 不是数组，是指向数组的**视图描述符**：

```
slice header（24 字节，传参拷贝的就是它）
┌─────────┬─────┬─────┐
│ ptr     │ len │ cap │    ptr → 底层数组
└─────────┴─────┴─────┘
```

传参拷贝 header（三个字段），**底层数组仍然共享**——所以"函数内改 slice 元素，外面看得见"（共享数组）与"函数内 append，外面看不见"（header 是拷贝）同时成立，Javaer 对此的直觉恰好各错一半（对应 Java：`Arrays.asList` 视图 vs `ArrayList` 拷贝，但 Go 里两者揉在同一个类型上）。

### 2.2 append 三态：串扰与分家全在这

```go
s := make([]int, 3, 5)      // len=3 cap=5，底层数组 5 格
a := append(s, 1)           // ①cap 内：写进共享数组第 4 格——s 的 len 还是 3 看不见，别的视图看得见
t := append(s, 1, 2, 3)     // ②超 cap：换新数组——t 与 s 从此分家，互不可见
u := s[:3:3]                // ③三索引限制 cap=3：给 s 的视图上锁，append 必然分家
```

**串扰现场**：`a := s[1:3]; a[0] = 99` 会改掉 `s[1]`——两个视图同一数组。**防御惯用法**：要独立就显式拷贝 `copy(dst, src)`（内建函数）或 1.21 标准库 `slices.Clone(s)`；要把"只读视图"交给外部又怕对方 append 污染，用三索引切片 `s[:len(s):len(s)]` 封 cap。

### 2.3 扩容与预分配

cap 不足时 append 按策略扩新数组（1.18 起：256 以内翻倍，之上约 1.25 倍渐进）——**旧 slice 不动，新 slice 指向新家**，这就是"append 返回值必须接住"的硬原因（`append(s, x)` 不赋值回 s = 扔掉新 header，扩容场景直接丢数据）。知道长度的循环填充一律 `make([]T, 0, n)` 预分配——等价 Java 的 `new ArrayList<>(n)`，省的是反复扩容拷贝。

### 2.4 nil slice 与空 slice

`var s []int`（nil，header 零值）与 `s := []int{}`（空，指向零长数组）在 `len/append/range` 上行为一致——**API 返回"无结果"用 nil**，JSON 序列化时 `nil` → `null`、空 slice → `[]`，对外契约要挑一个（Java 的 null vs 空 List 同款纠结，但 Go 惯例明确偏 nil）。

3 map：无序、非并发安全、不可寻址的哈希表
------

### 3.1 基本差异对照 Java HashMap

| 维度 | Java HashMap | Go map |
|---|---|---|
| 迭代顺序 | 桶数组天然"看似有序"（面试名题） | **故意随机化**（runtime 每次随机起始桶）——依赖顺序是 bug |
| null 键值 | 允许 1 个 null 键 | nil map **读零值不炸、写直接 panic** |
| key 约束 | hashCode/equals 实现 | **编译期**要求可比较类型（slice/map/func 作 key 编译不过） |
| 缺省查询 | `getOrDefault`/`containsKey` | `v, ok := m[k]` comma-ok 一招全覆盖 |
| 并发写 | 加锁或 ConcurrentHashMap | **直接 fatal 不可 recover**（见 §3.3） |

两个高频点：**comma-ok 是 map 的灵魂语法**（`v, ok := m[k]` 把 get+contains 合成一次查询）；**nil map 只读**——`var m map[string]int` 后 `m["a"]=1` 是 panic（要先 `make`），从函数返回"空 map"时返回 nil 读安全写崩，是最常见的初学者崩溃栈。

### 3.2 底层一瞥：为什么无序、为什么不可寻址

桶数组 + 每桶 8 对 KV + **扩容时渐进式搬迁**（每次写操作搬一点，避免一次性全量 rehash 卡顿——对照 Java 一次 resize 的卡顿尖刺，Go 选了摊销）。渐进搬迁导致元素地址不稳定，所以 **`&m[k]` 取址编译不过**（struct 值 map 想改字段：取出来改完放回去 `v := m[k]; v.f = x; m[k] = v`；高频更新的字段别放 map 值里，放 `map[K]*V` 指针）。迭代随机化是 runtime 刻意的（防依赖实现细节），Javaer 转Go 后第一次 range map 输出顺序乱跳不是 bug 是特性。

### 3.3 并发不安全：fatal 而非 panic

```go
// fatal error: concurrent map writes —— 进程级死刑，recover() 接不住
go func() { for { m["a"] = 1 } }()
go func() { for { _ = m["a"] } }()
```

三选一：**`sync.RWMutex`**（通用，读写分锁）；**`sync.Map`**（读多写少且 key 集合稳定——内部读写分离，写多场景反而更慢，对照 ConcurrentHashMap 的定位但适用面更窄）；**分片 map**（`[N]map` + 按 key hash 选片，超高并发热点锁）。判据一句话：**有并发写就上锁，`go test -race` 在 CI 里必须开**——race 检测器能把还没炸的串扰当场抓获，这是 Java 生态要花大价钱买工具才有的待遇，Go 官方内置白送。

4 字符串与 []byte：一对必须懂的值语义邻居
------

`string` 是**不可变的字节序列**（不是 runes！），`[]byte` 是可变版。互转是**全量拷贝**（不可变性要求的防御）——循环里反复 `string(b)`/`[]byte(s)` 是隐形性能税。拼接用 `strings.Builder`（对照 `StringBuilder`，同款理由：不可变拼接的中间垃圾）。`range` 字符串按 UTF-8 解码遍历 rune，`s[i]` 是字节——中文乱码问题全是这两个视图混用造成的。

5 陷阱速查卡（本篇全部结论压缩版）
------

| 场景 | 现象 | 一句话解法 |
|---|---|---|
| 函数内改 slice 元素 | 外面看得见 | 共享底层数组——要隔离先 Clone |
| 函数内 append | 外面看不见 | header 拷贝；返回新 slice 或传 `*[]T` |
| `append(s, x)` 不接返回值 | 扩容时丢数据 | 永远 `s = append(s, x)` |
| 子切片改元素 | 原切片被改 | 视图共享；三索引 `s[:n:n]` 封 cap |
| nil map 写入 | panic | 先 make；只读场景返回 nil 安全 |
| map 迭代顺序 | 每次不同 | 特性不是 bug；要顺序先 sort keys |
| `&m[k]` | 编译不过 | 值拷贝改完放回，或 `map[K]*V` |
| map 并发读写 | fatal 进程死 | RWMutex/sync.Map/分片 + CI 开 -race |
| 数组赋值 | 全量拷贝 | 要引用用 slice/指针（Java 无此语义） |
| string(b) 循环转 | 性能税 | 一次转换存变量；Builder 拼接 |

面试追问链
------

* slice 的底层结构？→ ptr/len/cap 三字 header；传参拷贝什么？→ 只拷 header，数组共享；
* append 什么时候改原数组什么时候不改？→ cap 内原地写、超 cap 换新数组；所以为什么要接返回值？→ 扩容后新 header 在返回值里；
* 两个 slice 什么情况下互相串扰？→ 共享底层数组的写；怎么彻底隔离？→ copy/slices.Clone，或三索引封 cap；
* map 并发读写什么表现？为什么不可 recover？→ fatal 是 runtime 主动杀（保护内部结构），不是可恢复的 panic；三种替代怎么选？→ RWMutex 通用、sync.Map 读多写少、分片热点；
* nil slice 和空 slice 区别？JSON 里各是什么？→ 行为等价；null vs []，API 契约选 nil 惯例；
* 为什么 `&m[k]` 不行而 `&s[i]` 可以？→ map 渐进搬迁地址不稳（不可寻址），slice 数组地址稳定；
* 字符串 range 和下标访问的区别？→ range 解码 rune、下标取字节；中文按字节截断会怎样？→ 乱码/换行符碎片。

面试官视角小结
------

数据结构是"写了多久 Go"的试纸：**内存布局视角（header 共享、赋值拷贝、渐进搬迁）讲得清的，是把 Go 当第二母语在学**；只会背"slice 是引用类型"（不准确——是含指针的值类型）或拿 ArrayList/HashMap 直觉硬套的，第一周代码必出血。最高频的判别题就一句："`s = append(s, x)` 的等号能不能省？"——答"能"的当场出局，答"扩容场景丢数据"再追问"那串扰呢"，三层问完功力尽现。

> 2026-11 新增（Go 轨第 6 篇）。姊妹篇：[并发三件套](./从Java视角看Go-并发三件套-goroutine-channel-context.md)、[错误处理与panic工程实践](./从Java视角看Go-错误处理与panic工程实践.md)。
