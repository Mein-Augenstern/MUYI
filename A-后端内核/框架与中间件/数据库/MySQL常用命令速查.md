MySQL常用命令速查
====

> 原文参考：<a href="https://shockerli.net/post/1000-line-mysql-note/">一千行 MySQL 命令</a>（格物 著，JavaGuide 整理过目录）——完整千行版见原文；本篇是按后端日常使用频率重组织的**自写速查**，只保留真正高频的命令与排障入口。配套索引原理见 [MySQL索引详解](./MySQL索引详解.md)、线上事故视角见 [线上慢SQL复盘-范围条件断前缀与回表风暴](./线上慢SQL复盘-范围条件断前缀与回表风暴.md)。

1 连接与库表
------

```mysql
mysql -h 地址 -P 端口 -u 用户名 -p          -- 连接（-p 后留空回车输密码）
CREATE DATABASE IF NOT EXISTS 库名 DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SHOW DATABASES;  USE 库名;  SELECT DATABASE();
SHOW TABLES;  SHOW CREATE TABLE 表名\G      -- 建表语句原样（\G 竖排）
DESC 表名;                                  -- 列结构
ALTER TABLE 表名 ADD COLUMN 列 类型 COMMENT '注释' AFTER 已有列;
ALTER TABLE 表名 DROP COLUMN 列, MODIFY COLUMN 列 新类型;
```

2 DML 与查询要点
------

```mysql
INSERT INTO t(列…) VALUES (…), (…) ON DUPLICATE KEY UPDATE 列=VALUES(列);  -- 批量+upsert
UPDATE t SET 列=值 WHERE 主键=?;                 -- 写操作永远带索引条件
SELECT ... FORCE INDEX(索引名) WHERE ...;        -- 强制走索引（验证优化器误判时用）
EXPLAIN SELECT ...;  EXPLAIN ANALYZE SELECT ...; -- 执行计划；8.0+ 后者带真实耗时
SELECT ... FROM a JOIN b ON a.id=b.aid WHERE ... -- 连接条件与过滤条件都影响索引选择
```

* 分页深翻页用"延迟关联/游标"：`WHERE id > 上次最大id LIMIT n` 优于 `LIMIT 1000000, 20`（原理见 [SQL limit 与 limit offset 用法区别](./SQL%20limit%20与%20limit%20offset%20用法区别.md)）。

3 索引与执行计划
------

```mysql
SHOW INDEX FROM 表名;
CREATE INDEX idx_x ON t(列1, 列2);              -- 联合索引等值列前置、范围列殿后
ALTER TABLE t DROP INDEX idx_x;
EXPLAIN SELECT ...;   -- 看 type/key/rows/Extra 四列：range 之后 key_len 即断言处
```

* `Using index` = 覆盖索引免回表；`Using filesort/Using temporary` = 排序/临时表告警。
* 慢查询定位：`SET GLOBAL slow_query_log=ON; SET GLOBAL long_query_time=0.5;` 后看 slow log。

4 用户与权限
------

```mysql
CREATE USER 'u'@'%' IDENTIFIED BY '密码';
GRANT SELECT, INSERT ON 库.表 TO 'u'@'%';
SHOW GRANTS FOR 'u'@'%';
REVOKE INSERT ON 库.表 FROM 'u'@'%';
```

* 应用账号最小权限（业务库不给 DDL/DROP）；排障用只读账号，写操作走变更流程。

5 备份与恢复
------

```bash
mysqldump -h... -u... -p --single-transaction 库 表 > dump.sql   # InnoDB 一致性快照
mysql -u... -p 库 < dump.sql                                      # 恢复
```

* 大表迁移用 `--where` 分片或物理备份（XtraBackup/clone plugin）；`--single-transaction` 不锁 InnoDB 读。

6 运维排障入口（事故先看这三条）
------

```mysql
SHOW PROCESSLIST;                       -- 谁在跑什么、堆积多久（KILL <id> 止血）
SHOW ENGINE INNODB STATUS\G            -- 死锁与等待链
SELECT * FROM information_schema.INNODB_TRX;  -- 活跃事务与锁等待
```

* 连接池满的完整排障链路（processlist → 同形态 SQL 定位 → 索引治理）见 [线上慢SQL复盘-范围条件断前缀与回表风暴](./线上慢SQL复盘-范围条件断前缀与回表风暴.md)。

7 常用信息函数
------

```mysql
SELECT VERSION(), now(), user(), database();
SELECT DISTINCT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='库名';
```

参考资料：

> * <a href="https://shockerli.net/post/1000-line-mysql-note/">一千行 MySQL 命令（格物）</a>——完整千行版
> * <a href="https://www.cnblogs.com/qcloud1001/p/10033364.html">MYSQL 中的 COLLATE 是什么？</a>
