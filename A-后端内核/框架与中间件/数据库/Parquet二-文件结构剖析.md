Parquet 深入理解（二）：文件结构剖析
====

系列第二篇。上一篇（[Parquet一-理解列式存储](./Parquet一-理解列式存储.md)）讲了列存的为什么，这一篇拆开一个真实的 .parquet 文件看**它长什么样**：三层切分（Row Group / Column Chunk / Page）加一套放在文件末尾的元数据。理解这层结构，"列裁剪、谓词下推、为什么 Parquet 不适合改"就全部有了物理落点。首尾相接的还有 [总起](./Parquet深入理解-总起.md)、[编码技术](./Parquet三-编码技术.md)。

1 文件整体布局
------

```
+------------------+
| "PAR1" (4B 魔数) |
+------------------+
| Row Group 1      |   数据主体
| Row Group 2      |   （每组内是各列的 Column Chunk，
| ...              |    每个 Chunk 内是一个个 Page）
+------------------+
| File Metadata    |   文件"目录"：schema + 统计信息
+------------------+
| Footer Length 4B |   Metadata 的大小，用于定位
+------------------+
| "PAR1" (4B 魔数) |
+------------------+
```

* 开头结尾各 4 字节 "PAR1"：校验这是完整的 Parquet 文件（尾部被截断立刻暴露）。

2 为什么 Metadata 放在文件末尾
------

* 写入顺序决定的：PAR1 → 依次写各 Row Group → 全部写完才能**生成** Metadata（此时才知道每个块的确切位置与统计）→ Footer Length → PAR1。
* 若放开头，需要预先知道所有 Row Group 的大小与偏移——**流式写入根本做不到**。
* 读取只需两次 seek：
  1. 读最后 8 字节：验 PAR1 + 拿 Footer Length；
  2. seek 到 `文件尾 - 8 - Footer Length` 读完整 Metadata；
  3. 按 Metadata 决定读哪些 Row Group、哪些列——不读数据先看目录。

3 Row Group：水平切分的艺术
------

* 例：100 万行、6 列 → 切 4 组、每组 25 万行。**先横切再竖切**，是列存与数据局部性之间的折中。
* 三个作用：**谓词下推**（按组内统计跳过整组）、**IO 并行**（一组一个扫描任务）、**内存控制**（按组解压解码，不会一次把整个文件摊开）。
* 大小权衡（Spark 默认 128MB）：太小 → 元数据开销大、压缩效果差、IO 效率低；太大 → 写内存压力大、读取粒度粗、并行度差。经验值：常规 HDFS 128~256MB（对齐 HDFS Block）、大内存集群 512MB~1GB、流式场景 32~64MB。

4 Column Chunk：列裁剪的物理基础
------

* 一个 Row Group 内，**列数 = Chunk 数**；每个 Chunk 独立编码、独立压缩、文件内连续存放。
* `SELECT city, AVG(salary) ... GROUP BY city` 只需读 2 个 Chunk，其余 4 个完全不碰——列裁剪到这一层才真正兑现。
* **位置对齐**：同组内各列的第 N 个值属于同一行。`WHERE id=10`：扫 id 列找到第 9 个位置，再取 age 列第 9 个值。这也从结构上解释了**列存点查为什么慢**——取整行要在 N 个 Chunk 间来回。

5 Page：最小存储单元
------

* 三种类型：**Data Page**（实际数据）、**Dictionary Page**（字典编码的字典，见 [编码篇](./Parquet三-编码技术.md)）、Index Page（索引信息）。
* Data Page 结构：`Page Header（压缩前后大小、值数量、编码方式）+ Repetition Levels（嵌套结构才有）+ Definition Levels（NULL/嵌套才有）+ Encoded Values`。
* 默认约 1MB：太小 header 开销大、不利顺序 IO；太大单值读取的解压代价高、内存占用大。一般不需要调。

6 File Metadata：文件的"目录"
------

* Schema：列名、类型、嵌套结构。
* 每个 Row Group 的元数据：file_offset、total_byte_size、num_rows；其下每个 Column Chunk 的 offset、类型、encodings、compression、num_values、压缩/未压缩大小、**statistics（min / max / null_count）**。
* 另有 Key-Value Metadata（如 Spark 写入的 schema）、Created By。
* **统计信息 + 谓词下推**实战：100 万行按 id 排序分 4 组，查 `id > 500000`——RG1(max=250000)、RG2(max=500000) 整组跳过，只读 RG3/RG4，**IO 直接减半**。
* 关键推论：**数据排序决定谓词下推效果**。乱序数据每组的 min/max 都接近全域，下推形同虚设——这就是"写 Parquet 前先按查询列排序"的由来。

7 用工具验证
------

`parquet-tools meta example.parquet` 一眼可见：creator、schema（OPTIONAL INT64/BINARY...）、每个 Row Group 的行数与 offset、每列的压缩比、编码（DELTA、DICT,RLE）、min/max/null_count。`schema` 看结构、`head -n 5` 看数据。纸上结构对不上时，动手 dump 一次最有效。

| 层级 | 说明 | 关键点 |
|---|---|---|
| File | 整个文件 | 首尾 PAR1，Metadata 在末尾 |
| Row Group | 水平切分 | 平衡列存与局部性，默认 128MB |
| Column Chunk | 组内的列 | 独立压缩编码，列裁剪的落点 |
| Page | 最小单元 | Data/Dictionary/Index Page |
| Metadata | 元数据 | schema + 统计，谓词下推的依据 |

面试追问链
------

* Parquet 文件怎么组织？→ PAR1 + Row Group×N + File Metadata + Footer Length + PAR1；Metadata 为什么在尾部？→ 流式写完才有完整统计，放开头需预知未来；
* 读一个 Parquet 文件的第一步？→ 读末 8 字节定位并解析 Metadata，先看目录再读数据；Row Group 为什么存在？→ 纯列存缺局部性，横切兼顾谓词下推/并行/内存三件事；
* 列裁剪发生在哪层？→ Column Chunk 独立存储，只读所需列的 Chunk；为什么列存点查慢？→ 取整行要跨 N 个 Chunk 按位置拼装；
* 谓词下推怎么生效？→ 每组每列的 min/max 统计，整组跳过；什么时候失效？→ 数据乱序导致各组 min/max 都接近全域；
* Page 里除了数据还有什么？→ r/d level（嵌套与 NULL）与 header，编码细节见下篇。

面试官视角小结
------

文件结构题考的是"有没有把列存落到物理层"。能画出三层结构并说出 Metadata 在尾部的原因（流式写入），算入门；能讲清 Column Chunk 位置对齐既支撑过滤又解释点查慢、能点破"排序决定谓词下推效果"这层因果，说明真用 parquet-tools 看过文件。这题的隐藏考点是工程直觉：为什么这么设计——每个"为什么"都指向一个被解决的具体矛盾。

> 来源：javadoop《深入理解 Parquet（二）：文件结构剖析》（https://javadoop.com/post/parquet-02-parquet-file-structure）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
