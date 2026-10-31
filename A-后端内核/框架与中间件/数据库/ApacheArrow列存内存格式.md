Apache Arrow：列存内存格式
====

如果说 Parquet 是"磁盘上的列存标准"，Arrow 就是"**内存里的列存标准**"——规定数据在 RAM 中如何排列的一套规范加各语言库。为什么后端要懂：分析型负载里大量时间烧在系统间数据搬运的序列化上，Arrow 的零拷贝思路（Polars、DuckDB、DataFusion 都在用）是当代数据基础设施的通用底座，也是理解"为什么新分析工具突然都变快了"的钥匙。与磁盘侧的 [Parquet深入理解-总起](./Parquet深入理解-总起.md) 配套阅读。

1 痛点：序列化税
------

* 一段典型链路：Spark → Parquet 文件 → pandas → DuckDB → Polars。每个系统**内存里的数据表示都不同**，每次交接都要"序列化 → 字节流 → 反序列化"。
* Wes McKinney（pandas 作者）的观察：分析负载里 **80~90% 的时间花在序列化/反序列化上，而不是计算**。
* 具体感受一下这笔税：Java 服务查出 100 万行 → 拼成 JSON（CPU 序列化）→ Python 收到 → json 反序列化成 dict（又是 CPU + 几倍内存膨胀）→ 再转 pandas DataFrame（第三次拷贝）。三次转换里没有任何一步在做"业务要的计算"。
* Arrow 的解法：大家共用同一种内存布局，数据在系统间**免转换直接使用**——零拷贝。部分系统（Polars、DataFusion）干脆直接拿 Arrow 当内部格式。

2 内存布局：为 CPU 计算而设计
------

* **定长类型**：Data Buffer 紧密排列。INT32 `[1,2,3,4,5]` = 20 字节，取第 i 个元素就是偏移 `i×4`，O(1)。**不编码、不压缩**——内存格式要的是随机访问和 SIMD，不是省空间（省空间是 Parquet 的活）。
* **NULL 处理：Validity Bitmap**。1=有值、0=NULL：`[1,NULL,3,NULL,5]` → 位图 `0b00010101`。100 万值位图约 125KB；全无 NULL 时整个位图可省略；NULL 槽位的数据不读、也不破坏对齐。
* **变长字符串：Offsets + Data 双 buffer**。`["hello","arrow","世界"]` → Offsets `[0,5,10,16]` + Data。第 i 个串 = `Data[offsets[i] .. offsets[i+1]]`，依旧 O(1)。
* **64 字节对齐**：对齐 AVX-512 SIMD 寄存器宽度，一条指令处理 16 个 INT32 / 8 个 INT64——DuckDB、Polars 向量化执行快的物理基础（与 [Parquet三-编码技术](./Parquet三-编码技术.md) 里讲的编码数据直算同一思想）。

```
Array<INT32>: [1,2,3,4,5]
┌ Validity Bitmap (可选) ┬ Data Buffer (20B, 64B 对齐) ┐
```

3 类型系统：嵌套也不破坏列式
------

* 原始类型：Int/UInt 8~64、Float16/32/64、Boolean（1 bit/值）、Decimal128/256、Date/Time/Timestamp、Utf8/Binary；逻辑类型映射到物理（DATE→INT32、STRING→UTF-8 Binary），思路与 Parquet 一致。
* **List**：与字符串同款 Offsets + Child Array。`[[1,2], null, [3,4,5]]` → Bitmap `0b00000101` + Offsets `[0,2,2,5]` + Child `[1,2,3,4,5]`。
* **Struct**：各字段独立子数组（纯列式）。只查 name 时根本不碰 age 数组——**嵌套下列裁剪依然有效**，r/d level 那套"拍平"思想（[Parquet四](./Parquet四-Schema与嵌套结构.md)）在内存侧的延续。
* **Map**：物理上就是 `List<Struct<key,value>>`。
* **Dictionary**：注意它是**数据类型**而非压缩手段。7 个城市名 → Indices `[0,1,0,2,0,1,0]`（Int8）+ 3 项字典。收益：省内存、整数比较更快、小字典整个塞进 CPU 缓存。

4 RecordBatch 与 Table
------

* **RecordBatch = 一组等长的 Array + Schema**，可类比 Parquet Row Group 的内存版（3 行 3 列就是 3 个 Array 并排）。
* **Table = 多个 RecordBatch 的逻辑拼接**。Array 不可变，新数据就追加新 Batch，**不需要复制旧数据**——与 LSM"追加不原地改"的哲学遥相呼应。
* Schema 是显式契约：列名、类型、可空性都写在里面，两个系统交接时先对 Schema 再谈数据——这也是 IPC/Flight 能做到"免协商直传"的前提。
* "等长"是硬约束：一个 Batch 内所有 Array 行数一致，按位置对齐（同 Parquet Column Chunk 的位置对齐思路），取第 i 行的第 j 列就是 `arrays[j][i]`。

5 数据交换：IPC 与 Flight
------

* **IPC Stream**：`Schema → Batch1 → Batch2 → ... → EOS`，顺序流式消费。
* **IPC File**：首尾魔数 "ARROW1"，Footer 记各 Batch 偏移，支持随机访问。关键是 **buffer 布局与内存一致，可以 mmap**——10GB 文件 mmap 后常驻内存接近 0，按需加载。
* **Arrow Flight**：基于 gRPC 网络直传 RecordBatch。流程 `GetFlightInfo → FlightInfo(schema, 节点) → DoGet(ticket) → RecordBatch 流`，支持多节点分布式并行拉取，跳过传统 JDBC/ODBC 的"列→行→序列化→反序列化→列"整条冤枉路。
* **Flight SQL**：Flight 之上的 SQL 协议，Arrow 原生的 ODBC/JDBC 替代，分析场景快 10~100 倍；ClickHouse、Doris、Dremio、InfluxDB 已支持。

6 双打组合：Parquet + Arrow
------

```
磁盘:  Parquet（编码压缩，省空间、少 IO）
         │  读入 = 解码解压
内存:  Arrow（裸列 + 位图 + 对齐，零拷贝、SIMD）
         │  Flight / IPC
网络:  RecordBatch 直传（免序列化）
```

* "Parquet 管存储、Arrow 管内存计算"已成现代数据栈默认搭配：Polars、DuckDB、DataFusion、pandas 生态全部围着 Arrow 转。
* 后端选型启示：跨服务/跨引擎搬分析数据时，先问"能不能走 Arrow/Flight"，再考虑 JSON/CSV 这些到处都在序列化的格式。
* 两者逐项对照（把分工钉死）：

| 维度 | Parquet | Arrow |
|---|---|---|
| 位置 | 磁盘文件格式 | 内存布局规范 |
| 目标 | 省空间、省 IO | 随机访问、SIMD、零拷贝 |
| 手段 | 字典/RLE/Delta 编码 + 压缩 | 裸数据 + 位图 + 64B 对齐 |
| 可变性 | 不可变，追加新文件 | Array 不可变，追加新 Batch |
| 嵌套 | r/d level 拍平 | List/Struct 子数组 |
| 传输 | 文件/对象存储 | IPC/Flight 直传 |

7 生态速览与落地判断
------

* **引擎侧**：DuckDB、Polars、DataFusion 以 Arrow 为内部格式（省一次转换）；Spark、Flink 支持 Arrow 作为加速通道（如 Spark 的 Arrow 版 pandas UDF，省掉 JVM/Python 间的行式转换）。
* **IO 侧**：各语言的 parquet 读写库普遍走"Parquet ↔ Arrow"直转，文件里的 Page 解出来就是 Arrow Array。
* **传输侧**：Flight SQL 正在替代"JDBC 拉行式结果再转列"的老路，ClickHouse、Doris、Dremio、InfluxDB 已实现。
* 什么时候用不上它：纯 OLTP 行式业务数据（一条一条来）、小到无所谓序列化开销的接口、强依赖行式语义的老系统——Arrow 的收益与"批量 + 列式 + 多系统接力"三要素成正比。
* 一个常见误区：Arrow 不是数据库、不是文件格式竞品、也不是压缩方案。它只是**把内存布局标准化**，让计算与搬运变便宜——定位想清楚了，才知道自家系统该在哪一环接它。

面试追问链
------

* Arrow 和 Parquet 什么关系？→ 磁盘列存格式 vs 内存列存格式，一对搭档：Parquet 省空间，Arrow 服务计算；
* Arrow 解决什么问题？→ 各系统内存表示不同导致的序列化税（80~90% 时间），统一布局换零拷贝；
* 内存里 NULL 怎么存？→ Validity Bitmap 位图，1/0 标记，无 NULL 可整体省略；为什么不用 Parquet 那套编码？→ 内存格式要随机访问与 SIMD，编码/压缩反而碍事；
* 变长字符串怎么 O(1) 取值？→ Offsets 数组 + Data buffer，`Data[off[i]..off[i+1]]`；64 字节对齐图什么？→ 对齐 AVX-512，一条指令吃 16 个 INT32；
* List/Struct 怎么存？→ List 用 Offsets+Child；Struct 各字段独立子数组，嵌套仍可列裁剪；Dictionary 是压缩吗？→ 是数据类型，顺带省内存且整数比较快；
* 两个系统间怎么传 Arrow？→ 进程内/文件用 IPC（可 mmap），跨网络用 Flight/Flight SQL 免序列化。

面试官视角小结
------

Arrow 题考察的是候选人对"现代数据栈为什么快"的完整拼图：知道 Parquet 是磁盘格式只算一半，能讲清内存侧"不编码不压缩、位图标 NULL、64 字节对齐喂 SIMD"的设计动机，以及零拷贝/Flight 砍掉序列化税的价值，才算把"存储-内存-网络"三层串起来了。最强的信号是能对比出两条原则：**磁盘格式优化空间，内存格式优化速度**——一句话见功夫。

> 来源：javadoop《Apache Arrow 的简单介绍》（https://javadoop.com/post/arrow）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
