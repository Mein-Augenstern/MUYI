sql 中 limit 与 limit，offset连用的区别
====

看在前面
------

> * 原文：<a href="https://blog.csdn.net/AinUser/article/details/72803175">sql 中 limit 与 limit，offset连用的区别</a>（CSDN，MySQL 语法，并含深分页优化）

一句话答案
------

* `limit x, y`：逗号前是**偏移量**、逗号后是**条数**；`limit y offset x`：limit 后跟的是**条数**，offset 后是**起始偏移**。两种写法只是参数顺序相反，语义都由位置决定。

一、两种写法逐一拆解
------

### 1.1 `limit x, y`（偏移量, 条数）

* `select * from table limit 2,1;` —— 跳过 2 条、取出 1 条，即读取**第 3 条**记录。

* 第一个参数是偏移量（offset，从 0 计），第二个参数是返回条数（rows）。

### 1.2 `limit y offset x`（条数 offset 偏移量）

* `select * from table limit 2 offset 1;` —— 从第 1 条（不含）开始取 2 条，即读取**第 2、3 条**。

* limit 后面跟条数，offset 后面是起始位置。

### 1.3 更多示例（employee 表）

* `select * from employee limit 3, 7;` —— 返回第 4~11 行（跳过 3 行取 7 行）。

* `select * from employee limit 3,1;` —— 只返回第 4 行。

* `select * from employee limit 3;` —— **单参数**时表示条数，返回前 3 行。

* 通用语法形式：

```sql
SELECT * FROM table LIMIT [offset,] rows | rows OFFSET offset
```

二、记忆与易错点
------

* 逗号写法"**先跳过后取**"：`limit 2,1` 容易误读成"取 2 条再偏移 1"，实际是跳过 2 取 1。

* offset 关键字写法读起来更接近英语语序："取 2 条，从第 1 条之后开始"。

* 两种写法**可互相改写**：`limit 2,1` ≡ `limit 1 offset 2`。

* 注意方言差异：MySQL 用 `limit`；SQL Server 用 `top`/`offset ... fetch`；Oracle 经典分页用 `rownum`。

三、延伸：深分页性能问题
------

* `limit 10000, 20` 这类深分页，需要扫描满足条件的 **10020 行**、扔掉前 10000 行、只返回 20 行——偏移量越大扫描越多，性能越差。

* 优化思路（雅虎工程师 Efficient Pagination 报告）：**用 id 当"线索"**，按 id 递增分页时把偏移量转成条件：

```sql
-- 下一页：记住上一页末尾 id = 1020
SELECT * FROM message WHERE id > 1020 ORDER BY id ASC LIMIT 20;

-- 上一页：记住下一页开头 id = 1000
SELECT * FROM message WHERE id < 1000 ORDER BY id DESC LIMIT 20;
```

* 这样不管翻多少页，每次只扫描 20 行。跳转到第 N 页的场景：记录当前页 id 的最大/最小值，算出目标页与当前页的相对偏移。让 limit 的偏移量尽量小。原作者在 60 万数据量的表上实测，效果明显。

* 局限：要求排序列唯一且连续可比（建议 id 无空洞）。无法直接随机跳页的场景要退化为子查询定位首行 id 再 join。

> 内容来源：[原文](https://blog.csdn.net/AinUser/article/details/72803175) · 2026-10 整理回填。
