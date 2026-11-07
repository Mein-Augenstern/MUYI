# 第三章 · 存储引擎与列存

> 行存（MySQL）为事务而生，列存为分析而生。Parquet 系列五篇从文件结构讲到编码技术，再看 Arrow（内存列存标准）、ClickHouse 与 Cassandra 两个引擎。

## 本篇目录

1. [RocksDB架构设计一-LSM与存储引擎](RocksDB架构设计一-LSM与存储引擎.md) / [二-版本快照与一致性读取](RocksDB架构设计二-版本快照与一致性读取.md)——LSM-KV 世界（与 MySQL B+ 树对照；ShardingSphere 底座实证）
2. [Parquet 深入理解-总起](Parquet深入理解-总起.md) → [一-理解列式存储](Parquet一-理解列式存储.md) → [二-文件结构剖析](Parquet二-文件结构剖析.md) → [三-编码技术](Parquet三-编码技术.md) → [四-Schema与嵌套结构](Parquet四-Schema与嵌套结构.md)
3. [ApacheArrow列存内存格式](ApacheArrow列存内存格式.md)
4. [ClickHouse核心架构与设计原理](ClickHouse核心架构与设计原理.md) / [Cassandra核心架构与设计原理](Cassandra核心架构与设计原理.md)

对照面：行存引擎的地基见[第一章](../01-MySQL核心/)。
