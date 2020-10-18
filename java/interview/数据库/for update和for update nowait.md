看在前面
------

* 锁定数据行 for update和for update nowait：https://blog.51cto.com/13140426/1948205

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
