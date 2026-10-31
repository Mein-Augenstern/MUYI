Parquet 深入理解：总起
====

Apache 顶级项目的**列式存储文件格式**——和 CSV、JSON 同属"文件格式"，区别是数据按列而非按行组织。为什么后端要懂它：Spark 默认存储格式、数据湖事实标准，ClickHouse、DuckDB、Databend、Polars 全都把它当交换格式（见 [ClickHouse核心架构与设计原理](./ClickHouse核心架构与设计原理.md)）；更要紧的是，**列式存储是现代数据分析的地基**，而 Parquet 是学列存最好的具体载体——概念抽象地学记不住，跟着一个真实文件格式走一遍就全通了。学完之后再 ORC、[ApacheArrow列存内存格式](./ApacheArrow列存内存格式.md)、ClickHouse 的存储设计，都会有似曾相识之感。

1 它凭什么：两个数量级
------

* 分析查询场景下，同样一份数据，Parquet 比 CSV **查询快 10~100 倍**；
* 文件体积**小 5~10 倍**。
* 这两个数字不是靠某个单点优化，而是列存 + 编码 + 元数据三件事叠加的结果，也是本系列要拆开讲的东西。

2 三个设计看点（全系列地图）
------

* **文件结构**：`Row Group → Column Chunk → Page` 的三层设计——纯列存和实用文件格式之间隔着"数据局部性"这个矛盾，这一层切分就是答案；
* **编码技术**：字典编码、RLE、Delta、Bit-Packing——列存压缩率的真正来源，不同数据特征配不同编码；
* **嵌套结构**：Repetition Level 与 Definition Level，源自 Google Dremel 论文，把嵌套数据"拍平"成列的神来之笔。

3 系列导航
------

* [Parquet一-理解列式存储](./Parquet一-理解列式存储.md)：行存 vs 列存的根本分歧，为什么列存适合分析。一切的起点。
* [Parquet二-文件结构剖析](./Parquet二-文件结构剖析.md)：PAR1 魔数、三层切分、Footer 元数据，一个 .parquet 文件内部长什么样。
* [Parquet三-编码技术](./Parquet三-编码技术.md)：Page 里的数据怎么编码，字典/RLE/Delta/Bit-Packing 各管什么数据。
* [Parquet四-Schema与嵌套结构](./Parquet四-Schema与嵌套结构.md)：r/d level 如何把嵌套 JSON 状的数据拍平成列再无损还原。
* 延伸：[ApacheArrow列存内存格式](./ApacheArrow列存内存格式.md)——Parquet 管磁盘，Arrow 管内存，一对搭档。

4 范围与读法
------

* 定位入门：只讲核心原理，不讲生态工具、各语言读写 API 与调参——这些查文档即可，原理才是长保质期的知识。
* 适合任何想加深数据存储理解的工程师，不限大数据方向。
* 读法建议：按序读，每篇都从"它解决什么矛盾"入手；Parquet 的每个设计都是具体 trade-off 的产物，带着"为什么"读比背结论有效。
* 一条自检线：读完四篇应能不查资料回答四个问题——为什么列存省 IO（篇一）、Row Group/Chunk/Page 各管什么（篇二）、字典与 RLE 何时失效（篇三）、嵌套数据的 r/d level 各记录什么（篇四，四问中最见功力）。
* 库内关联：分析型数据库的落地样本见 [ClickHouse核心架构与设计原理](./ClickHouse核心架构与设计原理.md)——MergeTree 的列存、压缩、跳数索引与本系列逐条对应，可对照食用。

面试追问链
------

* Parquet 是什么？→ 列式存储文件格式，Spark 默认、数据湖标准；为什么快？→ 列裁剪少读 IO + 编码压缩小 + 元数据统计跳数据，三层叠加；
* 学它有什么用？→ ClickHouse/ORC/Arrow/DuckDB 的存储设计同源，学一个通一类。

面试官视角小结
---

问 Parquet 不是考大数据八股，而是探候选人有没有"存储格式决定查询性能"的意识。能说出"快 10 倍不是魔法，是列裁剪 + 压缩 + 谓词下推的叠加"，并知道 Row Group / 编码 / r-d level 分别解决什么矛盾，就已经超过大多数只会用 Spark 读写 parquet 的人。

> 来源：javadoop《深入理解 Parquet：开篇》（https://javadoop.com/post/parquet-00-introduction）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
