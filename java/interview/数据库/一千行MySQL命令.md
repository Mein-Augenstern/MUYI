看在前面
====

> 原文地址：https://shockerli.net/post/1000-line-mysql-note/ ，JavaGuide 对本文进行了简答排版，新增了目录。 作者：格物

非常不错的总结，强烈建议保存下来，需要的时候看一看。

基本操作
====

```mysql
/* Windows服务 */

-- 启动MySQL
    net start mysql
    
-- 创建Windows服务
    sc create mysql binPath= mysqld_bin_path(注意：等号与值之间有空格)
    
/* 连接与断开服务器 */

mysql -h 地址 -P 端口 -u 用户名 -p 密码

SHOW PROCESSLIST -- 显示哪些线程正在运行

SHOW VARIABLES -- 显示系统变量信息
```

数据库操作
====

```mysql
/* 数据库操作 */ ------------------

-- 查看当前数据库
    SELECT DATABASE();
    
-- 显示当前时间、用户名、数据库版本
    SELECT now(), user(), version();
    
-- 创建库
    CREATE DATABASE[ IF NOT EXISTS] 数据库名 数据库选项
    数据库选项：
        CHARACTER SET charset_name
        COLLATE collation_name
    eg: CREATE DATABASE IF NOT EXISTS yourdbname DEFAULT CHARSET utf8 COLLATE utf8_general_ci;
    
-- 查看已有库
    SHOW DATABASES[ LIKE 'PATTERN']
    eg: show databases like 'demotransfer';
    
-- 查看当前库信息
    SHOW CREATE DATABASE 数据库名
    eg：show create database demotransfer;
    +--------------+-----------------------------------------------------------------------+
    | Database     | Create Database                                                       |
    +--------------+-----------------------------------------------------------------------+
    | demotransfer | CREATE DATABASE `demotransfer` /*!40100 DEFAULT CHARACTER SET utf8 */ |
    +--------------+-----------------------------------------------------------------------+
    1 row in set (0.00 sec)

    
-- 修改库的选项信息
    ALTER DATABASE 库名 选项信息
    eg：mysql> alter database demotransfer character set gbk;
    Query OK, 1 row affected (0.00 sec)
    
-- 删除库
    DROP DATABASE[ IF EXISTS] 数据库名
        同时删除该数据库相关的目录及其目录内容
```

参考资料：

> * <a href="https://www.cnblogs.com/qcloud1001/p/10033364.html">MYSQL中的COLLATE是什么？</a>

表的操作
====

