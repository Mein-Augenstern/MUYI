看在前面
====

> 原文地址：<a href="https://shockerli.net/post/1000-line-mysql-note/">一千行 MySQL 命令</a>，作者：格物。JavaGuide 对本文做过简单排版并新增了目录。

本文是这篇千行速查的**链接卡片**。原文按章节组织了上千行常用命令，覆盖：

* 连接与服务器、数据库操作、表的操作
* 数据增删改查（SELECT/JOIN/UNION/子查询）
* 数据类型与列约束、视图、事务与锁
* 触发器、存储过程、用户与权限管理
* 备份还原、表维护

非常不错的总结，建议保存原文、需要的时候看一看。

本地仅保留最常用的十条左右命令，**完整千行速查见[原文](https://shockerli.net/post/1000-line-mysql-note/)**。

最常用命令速览
====

```mysql
/* 连接与断开服务器 */
mysql -h 地址 -P 端口 -u 用户名 -p 密码

/* 数据库操作 */
CREATE DATABASE IF NOT EXISTS 库名 DEFAULT CHARSET utf8 COLLATE utf8_general_ci; -- 创建库
SHOW DATABASES;                    -- 查看已有库
SELECT DATABASE();                 -- 查看当前数据库
SHOW CREATE DATABASE 库名;         -- 查看当前库信息
ALTER DATABASE 库名 CHARACTER SET gbk; -- 修改库的选项信息
DROP DATABASE IF EXISTS 库名;      -- 删除库（同时删除该数据库相关的目录及其目录内容）

/* 服务器状态 */
SHOW PROCESSLIST;                  -- 显示哪些线程正在运行
SHOW VARIABLES;                    -- 显示系统变量信息
SELECT now(), user(), version();   -- 显示当前时间、用户名、数据库版本
```

参考资料：

> * <a href="https://www.cnblogs.com/qcloud1001/p/10033364.html">MYSQL中的COLLATE是什么？</a>
