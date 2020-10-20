看在前面
------

* X/Open DTP模型与XA协议的学习笔记：https://www.cnblogs.com/gnodev/p/3817323.html

* 初识Open/X XA：https://www.jianshu.com/p/6c1fd2420274

Distributed Transaction Processing（DTP）
------

DTP是一种实现分布式事务处理系统的概念模型，OSI和Open/X都有正式文档来定义它：

* X/Open Guide, Distributed Transaction Processing Reference Model, X/Open Company Ltd., October 1991.
* The ISO/IEC Open Systems Interconnection (OSI) Distributed Transaction Processing (DTP) standard.
* ISO/IEC DIS 10026-1 (1991) (model)
* ISO/IEC DIS 10026-2 (1991) (service)
* ISO/IEC DIS 10026-3 (1991) (protocol)

为了简化理解，我们只考虑它的静态结构。在DTP的经典结构图（下图）中，整套系统由三种角色构成。

!![DTP]()

* 应用程序（Application Program，AP）

这个角色要做两件事情，一方面是定义构成整个事务所需要的所有操作，另一方面是亲自访问资源节点来执行操作。

* 资源管理器（Resource Managers，RM）

这个角色是管理着某些共享资源的自治域，比如说一个MySQL数据库实例。在DTP里面，还有两个要求，一是RM自身必须是支持事务的，二是RM能够根据
将全局（分布式）事务标识定位到自己内部的对应事务。

* 事务管理器（Transaction Manager，TM）

这个角色能与AP和RM直接通信，协调AP和RM来实现分布式事务的完整性。主要的工作是提供AP注册全局事务的接口，颁发全局事务标识（GTID之类 的），存储/管理全局事务的内容和决策并指挥RM做commit/rollback。
