看在前面
------

* 锁定数据行 for update和for update nowait：https://blog.51cto.com/13140426/1948205

* FOR UPDATE、FOR UPDATE NOWAIT、WAIT详解：FOR UPDATE、FOR UPDATE NOWAIT、WAIT详解

锁定数据行 for update和for update nowait
------

锁定数据行

```
select * from emp t where t.deptno='20' for update nowait;
```

这样就锁定了emp表中deptno = 20的那行数据。注意：通过for update锁定后，这些行不能修改了，但是还可以查询。

> for update和for update nowait

使用for update锁定行，对这行执行update,delete,select .. for update语句都会阻塞，即等待锁的释放后继续执行

使用for update nowait锁定行，对这行执行update,delete,select .. for udapte语句，会马上返回一个“ORA-00054:resource busy”错误，不用一直等待锁的释放后继续执

 FOR UPDATE和FOR UPDATE NOWAIT的区别
 ------
 
 首先一点, 如果只是SELECT的话, ORACLE是不会加任何锁的, 也就是ORACLE对SELECT读到的数据不会有任何限制, 虽然这时候有可能另外一个进程正在修改表中的数据, 并且修改的结果可能影响到你目前SELECT语句的结果, 但是因为没有锁, 所以SELECT结果为当前时刻表中记录的状态。

如果加入了FOR UPDATE, 则ORACLE一旦发现(符合查询条件的)这批数据正在被修改, 则不会发出该SELECT语句查询, 直到数据被修改结束(被COMMIT), 马上自动执行这个SELECT语句。 补充: 也就是说这个FOR UPDATE语句进入了阻塞状态, 直到锁被释放, 才会马上执行这个SELECT语句。

同样, 如果该查询语句发出后, 有人需要修改这批数据中的一条或几条, 它必须等到查询结束后(COMMIT或者ROLLBACK)后, 才能修改。

FOR UPDATE NOWAIT和FOR UPDATE都会对所查询到的结果集进行加锁, 所不同的是, 如果另外一个线程正在修改结果集中的数据, FOR UPDATE NOWAIT不会进行资源等待, 只要发现结果集中有些数据被加锁, 立刻返回"ORA-00054错误, 内容是资源正忙, 但指定以NOWAIT方式获取资源"。而FOR UPDATE会进入等待状态。

FOR UPDATE 和FOR UPDATE NOWAIT加上的是一个行级锁, 也就是只有符合WHERE条件的数据被加锁。如果仅仅用UPDATE语句来更改数据时, 可能会因为加不上锁而没有响应的、莫名其妙的等待, 但如果在此之前, FOR UPDATE NOWAIT语句将要更改的数据试探性的加锁, 就可以通过立即返回的错误提示而明白其中的道理, 或许这FOR UPDATE和NOWAIT的意义之所在。

经过测试, 以FOR UPDATE 或 FOR UPDATE NOWAIT方式进行查询加锁, 在SELECT的结果集中, 只要有任何一个记录在加琐, 则整个结果集都在等待系统资源(如果是NOWAIT, 则抛出相应的异常)。

例子:

> **FOR UPDATE NOWAIT**

开启一个会话

SELECT EMPNO, ENAME FROM EMP FOR UPDATE NOWAIT WHERE EMPNO='7369' ;

得到下面结果集:

empno ename

7369    smith

开启另一个会话

SELECT EMPNO, ENAME FROM EMP FOR UPDATE NOWAIT WHRE EMPNO='7369' ;

返回ORA-00054错误, 内容是资源正忙。

> **FOR UPDATE**

开启一个会话

SELECT EMPNO, ENAME FROM EMP FOR UPDATE WHRE EMPNO='7369' ;

得到下面结果集

EMPNO ENAME

7369       SMITH

开启另一个会话

SELECT EMPNO, ENAME FROM EMP FOR UPDATE WHRE EMPNO='7369' ;

阻塞, 不返回错误, COMMIT或者ROLLBACK第一个会话, 第二个会话自动执行并锁定数据。

> NOWAIT:   立即执行, 如果另有会话正在修改该记录会立即报告错误：ORA-00054: 资源正忙
> WAIT (N):  等待N秒, 如果另有会话正在修改该记录会报告错误：ORA-30006: 资源已被占用; 执行操作时出现 WAIT 超时 
