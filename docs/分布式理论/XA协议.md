看在前面
-------

* 初识Open/X XA：https://www.jianshu.com/p/6c1fd2420274

eXtended Architecture（XA）
------

> XA在哪里？XA是RM和TM的交互规范和接口定义

TM和RM们之间使用的是上文提到的《ISO/TEC DIS 10026-1 (1991) (model)》所定义的二阶段提交。在XA规范的描述中，两阶段提交TM协调RM们完成已定义的全局事务的方法，AP找TM申请/注册全局事务的动作并不是二阶段提交的保障内容。

二阶段提交（two-phase commit）
------

对于单个全局（分布式）事务，在DTP环境中，二阶段提交流程大致如下：

> 第一阶段（Phase 1）

TM请求所有RM进行准备（prepare commit, or prepare），并告知它们各自需要做的局部事务（transaction branche）。RM收到请求后，如果判断可以完成自己的局部事务，那就持久化局部事务的工作内容，再给TM肯定答复；要是发生了其他情况，那给TM的都是否定答复。在发送了否定答复并回滚了局部事务之后，RM才能丢弃持久化了的局部事务信息。

> 第二阶段（Phase 2）

TM根据情况（比如说所有RM都prepare成功，或者，AP通知它要rollback等），先持久化它对这个全局事务的处理决定和所涉及的RM清单，然后通知所有涉及的RM去提交（commit）或者回滚（rollback）它们的局部事务。RM们处理完自己的局部事务后，将返回值告诉TM之后，TM才可以清除掉包括刚才持久化的处理决定和RM清单在内的这个全局事务的信息。

两阶段提交的协议层面优化
------

> 只读断言

在Phase 1中，RM可以断言“我这边不涉及数据增删改”来答复TM的prepare请求，从而让这个RM脱离当前的全局事务，从而免去了Phase 2。

这种优化发生在其他RM都完成prepare之前的话，使用了只读断言的RM早于AP其他动作（比如说这个RM返回那些只读数据给AP）前，就释放了相关数据的上下文（比如读锁之类的），这时候其他全局事务或者本地事务就有机会去改变这些数据，结果就是无法保障整个系统的可序列化特性——通俗点说那就会有脏读的风险。

> 一阶段提交（one-phase commit）

如果需要增删改的数据都在同一个RM上，TM可以使用一阶段提交——跳过两阶段提交中的Phase 1，直接执行Phase 2。

但这种优化的本质是跳过Phase 1，这种情况下，RM自行决定了整个局部事务的结果，并且在答复TM前就清除掉局部事务（因为Phase 2中RM应答完请求后，TM就没有必要去联系它了），这样TM就没有必要去持久化使用了这种优化的全局事务，也导致在某些系统故障（比如说由于网络通信抖动，TM没收到RM的回复）时，TM可能会完全不知道这类事务的执行结果。

使用X/Open XA接口描述的二阶段提交
------

X/Open的XA接口分为两类：

* 一类是ax_开头的，只有ax_reg()和ax_unreg()两个，由TM提供给RM调用，从而支撑起RM加入/退出集群时的动态注册机制

* 另一类是xa_开头的，由RM提供给TM调用，用于实现二阶段提交中的各种事务提交、恢复功能

下面是使用这些接口来描述的二阶段提交的一个流程示意图：

![Descript 2PC with XA interface](https://github.com/DemoTransfer/MUYI/blob/master/docs/%E5%88%86%E5%B8%83%E5%BC%8F%E7%90%86%E8%AE%BA/picture/2-PC-XA-Interface.png)

1、在开始一个全局事务之前，涉及的RM必须通过ax_regr()，向TM注册以加入集群；对应的，在没有事务需要处理的时候，RM可以通过ax_unreg()向TM要求注销，离开集群。

2、TM在对一个RM执行xa_开头的具体操作前，必须先通过xa_open()打开这个RM（本质是建立对话）——这其实也是分配XID的一个行为；与之相应的，TM执行xa_close()来关闭RM。

3、TM对RM调用的xa_start()和xa_stop()这对组合，一般用于标记局部事务的开头和结尾。

4、对于同一个RM，根据全局事务的要求，可以前后执行多对组合——俾如说，先标记一个流水账INSERT的局部事务操作，然后再标记账户UPDATE的局部事务操作。

5、TM执行该组合只是起到标记事务的作用，具体的业务命令是由AP交给RM的。

6、该组合除了执行这些标记工作外，其实还能在RM中实现多线程的join/suspend/resume管理。

7、TM调用RM的xa_prepare()来进行第一阶段，调用xa_commit()或xa_rollback()执行第二阶段。

XA接口清单
------

规范中使用ISO C描述了一个xa.h的头文件，给出了XA接口的定义。

> ax_XXX接口

* ax_reg：向一个TM注册一个RM

* ax_unreg：向一个TM注销一个RM

> xa_XXX接口

* xa_close：停止当前AP对某个RM的使用

* xa_commit：通知RM去提交局部事务（第二阶段）

* xa_complete：询问指定的异步xa_操作是否完成

* xa_end：解除线程与局部事务的关联

* xa_forget：RM存在一种优化方式，就是在第一阶段进行先行完成（heuristiccally complete）局部事务，从而尽早释放资源（如释放锁等），但保留局部事务回滚能力与全局事务的对应关系等事务元数据；如果全局事务成功的话，TM通过这个接口许可RM废弃这个事务的事务元数据

* xa_open：初始化某个RM给当前AP使用

* xa_prepare：通知目标RM进行第一阶段工作

* xa_recover：获取指定RM上已完成了第一阶段或者先行完成的XID清单

* xa_rollback：通知指定RM回滚指定的局部事务

* xa_start：启动或恢复RM上的局部事务，换句话说，TM告诉这个RM，它后面的工作都与它现在给的XID相关。
