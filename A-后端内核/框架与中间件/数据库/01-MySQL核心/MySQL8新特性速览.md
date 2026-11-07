MySQL8 新特性速览（对照 5.7）
====

> 声明：默认指 MySQL 的 InnoDB 存储引擎，以 MySQL 8.0 为主视角、5.7 为对照，涉及小版本差异处单独标注。索引失效、慢查询优化的专题展开见文末互链。

一句话定位，8.0 的变化分四组：

* **SQL 能力补课（CTE/窗口函数）**
* **DDL 体验重做（原子/Instant/隐藏索引）**
* **优化器更聪明（直方图/降序索引）**
* **一批必须知道的默认行为变化**

前三组是"新武器"，最后一组是"升级的坑"。面试两边都要会。

1 总览
------

| 分组 | 特性 | 一句话价值 | 版本 |
|------|------|-----------|------|
| SQL 能力 | CTE（含递归） | 层级/树形查询不再靠自连接和应用层循环 | 8.0 |
| SQL 能力 | 窗口函数 | 排名、分组 TopN、移动聚合下沉到数据库 | 8.0 |
| DDL | 原子 DDL | DDL 失败不再留半成品 | 8.0 |
| DDL | Instant DDL | 秒级加/删列，与表大小无关 | 8.0.12 / 8.0.29 |
| 索引 | 隐藏索引 | 删索引可灰度、可秒级回滚 | 8.0 |
| 索引 | 降序索引（真正生效） | 混合序排序免 filesort | 8.0 |
| 优化器 | 直方图统计 | 无索引列的分布信息参与成本估算 | 8.0 |
| 默认行为 | utf8mb4 / 0900_ai_ci / caching_sha2_password / GROUP BY 不隐式排序 | 升级兼容性 checklist | 8.0 |

2 CTE：WITH 与递归查询
------

非递归用法即"给子查询起名字"：`WITH x AS (子查询) SELECT ... FROM x`。一次定义多处引用、执行器只算一次，替代层层嵌套子查询与临时表。真正的考点在递归。

2.1 递归 CTE：树形结构实践（必背例）
------

需求：org 表自关联（id, name, parent_id），查"华东大区"下所有层级子部门并标出层级：

```sql
WITH RECURSIVE sub_org(id, name, lvl) AS (
    -- 锚点部分：起点，只执行一次
    SELECT id, name, 1 FROM org WHERE name = '华东大区'
    UNION ALL
    -- 递归部分：引用 CTE 自身，直到不再产生新行为止
    SELECT o.id, o.name, s.lvl + 1
    FROM org o JOIN sub_org s ON o.parent_id = s.id
    WHERE s.lvl < 10          -- 层级上限兜底，防数据成环
)
SELECT * FROM sub_org ORDER BY lvl;
```

要点（都是追问点）：

* 结构固定：**锚点 UNION ALL 递归项**，递归项必须引用 CTE 自身；
* 递归部分**不允许** GROUP BY / ORDER BY / LIMIT / DISTINCT / 聚合函数（会破坏"逐层产出新行"的迭代语义）。要排序分页包在外层查询做。
* 防失控：`cte_max_recursion_depth` 默认 1000，超出会显示错误。数据成环（A 的 parent 是 B，B 的 parent 是 A）会递归到上限。治理靠外层 lvl 上限 + 数据侧禁止成环。
* 对比 5.7 老写法：应用层循环多次查库、自连接 N 层（层级未知就写不了）、或中间表维护路径字段（`path LIKE 'a/b/%'`）。递归 CTE 是数据库内一次往返。

3 窗口函数：排名与移动聚合
------

一句话区分：**GROUP BY 是"折叠"（一组压成一行），窗口函数是"保留"（每行都在，按分区/帧计算后贴回每行）**。所以排名、组内 TopN、累计/移动指标只能窗口做。

3.1 排名三兄弟与分组 TopN
------

```sql
SELECT name, dept, score,
       ROW_NUMBER() OVER (PARTITION BY dept ORDER BY score DESC) AS rn,     -- 1,2,3,4 连续序号
       RANK()       OVER (PARTITION BY dept ORDER BY score DESC) AS rk,     -- 1,2,2,4 并列同号、跳号
       DENSE_RANK() OVER (PARTITION BY dept ORDER BY score DESC) AS dense_rk -- 1,2,2,3 并列同号、不跳号
FROM exam;
```

经典派生用法——**每组 TopN**（5.7 时代的高频难题）：

```sql
SELECT * FROM (
    SELECT t.*, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY create_time DESC) AS rn
    FROM orders t
) x WHERE rn <= 3;        -- 每个用户最近 3 单
```

3.2 移动/累计聚合与前后行
------

```sql
SELECT trade_date, amount,
       SUM(amount) OVER (ORDER BY trade_date
           ROWS BETWEEN 6 PRECEDING AND CURRENT ROW) AS sum_7d,  -- 7 日滚动求和
       AVG(amount) OVER (ORDER BY trade_date
           ROWS BETWEEN 6 PRECEDING AND CURRENT ROW) AS avg_7d,  -- 7 日移动平均
       amount - LAG(amount, 1) OVER (ORDER BY trade_date) AS diff -- 环比增量
FROM daily_stats;
```

* 帧语法：`ROWS BETWEEN n PRECEDING AND CURRENT ROW`（物理行窗口）；省略帧时默认 `RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`（累计到当前，按值范围）；
* `LAG/LEAD` 取前/后 n 行，替代老式自连接 `JOIN t prev ON prev.date = t.date - INTERVAL 1 DAY`。

4 索引与优化器增强
------

4.1 隐藏索引（INVISIBLE）：灰度删索引的标准流程
------

索引对优化器"隐身"，但**仍实时维护写入**（不是停写，写放大还在）：

```sql
ALTER TABLE orders ALTER INDEX idx_redundant INVISIBLE;  -- 隐藏：优化器不再把它当候选
-- ……观察一个完整业务周期：慢日志、EXPLAIN、错误索引依赖告警……
ALTER TABLE orders DROP INDEX idx_redundant;             -- 确认无影响后真删
ALTER TABLE orders ALTER INDEX idx_redundant VISIBLE;    -- 反悔路径：秒级恢复
```

注意点：主键不能 INVISIBLE；排查期可用 `SET optimizer_use_invisible_indexes = ON` 让指定会话重新可见，做 A/B 对比。5.7 没有该能力，删索引只能赌，或用 gh-ost/pt-osc 模拟。

4.2 降序索引：8.0 才"真生效"
------

```sql
-- 5.7：DESC 被解析但忽略，实际仍按 ASC 存储 → 下面的查询 filesort
-- 8.0 ：真正按 DESC 组织 B+ 树 → 反向定位，免 filesort
CREATE INDEX idx_a_b ON t (a ASC, b DESC);

SELECT * FROM t ORDER BY a ASC, b DESC LIMIT 100;
```

适用：混合序排序（时间倒序 + 其他列正序的目录页）。排序失效的系统梳理见 [数据库索引失效总结](数据库索引失效总结.md)。

4.3 直方图：给没有索引的列"画像"
------

```sql
ANALYZE TABLE orders UPDATE HISTOGRAM ON channel WITH 100 BUCKETS;  -- 默认 100 桶，上限 102
-- 查看：information_schema.column_statistics；删除：ANALYZE TABLE orders DROP HISTOGRAM ON channel;
SELECT * FROM orders WHERE channel = 'ios';
-- 优化器从直方图知道 ios 只占 3%，估算行数贴近现实，不再乱选计划
```

* 定位：**索引的轻量替代**——只进统计信息、不加速查询本身。数据倾斜场景（某状态占 90%）纠正成本估算。影响走不走索引、JOIN 顺序这类决策。
* 适合低频过滤列、不便建索引的长字符串列；数据大幅变化后需重新 ANALYZE，不会自动跟随。

5 DDL：原子性与 Instant 演进
------

5.1 原子 DDL
------

5.7 的 DDL 中途失败（连接断、实例 crash）可能留下孤儿 .ibd 文件/半套表结构，主从还会因此表结构不一致。8.0 依赖**事务性数据字典**（表结构存进 InnoDB 表，.frm 文件取消）。DDL 成为"要么全部提交、要么全部回滚"的原子操作。binlog 中也是单个事务，从库不会再重放出半成品。

注意：原子 ≠ DDL 可与 DML 同事务——DDL 依旧隐式提交，"原子"只承诺**自身不残留中间态**。

5.2 Instant DDL 演进（8.0.12 → 8.0.29）
------

| 算法 | 原理 | 耗时 | 典型操作 |
|------|------|------|----------|
| COPY | 建影子表重写全量数据后改名 | 与表大小成正比，最久 | 改列类型 |
| INPLACE | InnoDB 内部重建（online DDL） | 与表大小相关，多数允许并发 DML | 加二级索引、8.0.12 前的加列 |
| INSTANT | **只改数据字典元数据，行不动** | 秒级，与表大小无关 | 8.0.12：尾部加列；8.0.29：任意位置加列/删列 |

```sql
-- 显式指定算法：不支持时直接报错，而不是默默退化成重建（生产 DDL 必备写法）
ALTER TABLE orders ADD COLUMN remark VARCHAR(200), ALGORITHM=INSTANT;
```

* 8.0.12 限制：INSTANT 加列只能加在**最后一列**；
* 8.0.29 起：任意位置 ADD COLUMN、DROP COLUMN 均可 INSTANT（内部用"行版本"记录每行缺哪些列，读取旧行时按元数据补默认值）。一条语句同时 ADD + DROP 或修改列类型仍退回 INPLACE/COPY。
* 判断依据：EXPLAIN 不了 DDL，就用 `ALGORITHM=INSTANT` 显式试探 + `SHOW CREATE TABLE` 核对结果。

6 默认行为变化清单（升级必查）
------

| 变化点 | 5.7 | 8.0 | 踩坑表现 / 对策 |
|--------|-----|-----|-----------------|
| 默认字符集 | latin1 | **utf8mb4**（utf8 只是 utf8mb3 的别名） | 显式声明；新表老表 JOIN 见下一行 |
| 默认排序规则 | utf8mb4_general_ci 等 | **utf8mb4_0900_ai_ci** | 跨 collation JOIN 索引失效；库内统一 collation |
| 认证插件 | mysql_native_password | **caching_sha2_password** | 老驱动连不上；升 Connector/J 8.x，非 SSL 加 allowPublicKeyRetrieval=true |
| GROUP BY 隐式排序 | 默认按分组列 ASC 排序 | **不再隐式排序** | 依赖排序的老 SQL 必须补 ORDER BY |
| 查询缓存 | 有 Query Cache | **整体移除** | query_cache_* 参数全部失效（本来收益就差） |
| 数据字典 | .frm 文件 | InnoDB 事务性字典（mysql.ibd） | 跨大版本只能逐级 in-place 升级（5.6→5.7→8.0） |
| 新增保留字 | - | RANK/GROUPS/WINDOW/OVER 等 | 叫这些名字的表/列会触发语法错误，需重命名 |

0900_ai_ci 拆开读：UCA **9.0** 权重算法、**a**ccent **i**nsensitive（重音不敏感）、**c**ase **i**nsensitive（大小写不敏感）。与 general_ci 的差异举例：ß 的排序位置、部分中日韩字符权重不同。**两列 collation 不同时 JOIN 会导致索引失效**（原理展开见互链）。

7 升级动作建议（简短 checklist）
------

* 预检：MySQL Shell `util.checkForServerUpgrade()` 扫保留字冲突、过时特性、引擎问题；
* 驱动先行：应用侧先把 Connector/J 升到 8.x（5.7/8.0 双兼容），再动服务端；
* collation 巡检：`SHOW CREATE TABLE` 逐表核对，统一 utf8mb4 + 单一 collation；
* SQL 兼容：依赖 GROUP BY 排序的语句全部补 ORDER BY；rank/groups/window 等新保留字重命名；
* 回归：慢日志 diff + EXPLAIN 对比，重点关注计划变化（8.0.18+ hash join 接管无索引 JOIN，详见互链）。

8 追问链（面试官会怎么往下问）
------

* **Q：递归 CTE 怎么防死循环？**
  A：三层防线——外层加 lvl 上限、`cte_max_recursion_depth`（默认 1000）超限显示错误、数据侧约束禁止成环。递归部分本身不允许 ORDER BY/LIMIT/GROUP BY/聚合。
* **Q：窗口函数和 GROUP BY 的本质区别？**
  A：GROUP BY 折叠（一行代表一组），窗口函数保留每行、按 PARTITION 和帧计算。所以排名/组内 TopN/累计指标只能用窗口。
* **Q：隐藏索引能保证删索引安全吗？有什么局限？**
  A：能兜住"优化器还用它"的风险（隐藏→观察→删除，回退秒级）。局限：写放大和空间照旧占用、主键不可隐藏、只在优化器选择层面生效。
* **Q：Instant DDL 为什么能做到与表大小无关？**
  A：只改数据字典：新列默认值记在元数据，存量行不动，读取时按行版本补默认值，所以是 O(1)。改列类型必须重写每一行，永远做不到 instant。
* **Q：原子 DDL 和事务是一回事吗？**
  A：不是。DDL 依旧隐式提交、不能参与业务事务；"原子"指单条 DDL 全有或全无，失败不留中间态、binlog 单事务下发。
* **Q：GROUP BY 隐式排序为什么被移除？**
  A：隐式排序是没有任何语义保证的实现副作用，还附赠 filesort 开销。SQL 标准从不要求，要序就显式 ORDER BY，语义和性能都更明确。
* **Q：直方图和索引什么关系？**
  A：互补不替代：索引服务定位（B+ 树加速存取），直方图只服务成本估算（不加速查询）。倾斜列上直方图纠正"估行数"偏差，避免选错计划。
* **Q：8.0 升级最常炸的三个点？**
  A：认证插件（老驱动连不上）、collation 不一致（JOIN 索引失效/排序变化）、GROUP BY 排序依赖 + 新保留字冲突。

9 小结
------

* SQL 层两件套：**CTE 管层级与复用，窗口函数管排名/组内 TopN/移动聚合**。都是 5.7 没有的表达力，也是"少写应用层代码"的考点。
* DDL 层两件事：**原子 DDL 不留半成品；Instant 秒级加列（8.0.12 尾部 / 8.0.29 任意位置与删列）**。删索引走 INVISIBLE 灰度，写 DDL 习惯带上 ALGORITHM=INSTANT 试探。
* 优化器层：降序索引真生效、直方图纠偏成本估算。配合函数索引/Skip Scan/hash join（见互链）构成完整的"8.0 让优化器更聪明"答题面。
* 升级四坑：**utf8mb4 与 0900_ai_ci、caching_sha2_password、GROUP BY 不再隐式排序、新保留字**——预检工具 + 驱动先行 + collation 巡检三步走；
* 展开阅读：[数据库索引失效总结](数据库索引失效总结.md)（函数索引/降序索引/不可见索引的失效视角）。[数据库语句优化手段](数据库语句优化手段.md)（hash join、EXPLAIN ANALYZE 等 8.0 优化工具）。[数据库设计时主要注意哪些事情？](数据库表设计要点.md)（建表字符集与 collation 规范）。

> 2026-10 新增（P1 时效升级）。
