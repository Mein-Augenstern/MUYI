看在前面
====

> * <a href="https://blog.csdn.net/u012394095/article/details/79470904">分布式定时任务对比</a>

什么是分布式定时任务
------

把分散的，可靠性差的计划任务纳入统一的平台，并实现集群管理调度和分布式部署的一种定时任务的管理方式。叫做分布式定时任务。

常见开源方案
------

* elastic-job 

* xxl-job 

* quartz 

* saturn

* opencron

* antares

elastic-job
------

elastic-job 是由当当网基于quartz 二次开发之后的分布式调度解决方案，由两个相对独立的子项目Elastic-Job-Lite和Elastic-Job-Cloud组成 。

Elastic-Job-Lite定位为轻量级无中心化解决方案，使用jar包的形式提供分布式任务的协调服务。

Elastic-Job-Cloud使用Mesos + Docker(TBD)的解决方案，额外提供资源治理、应用分发以及进程隔离等服务

**亮点**

1. 基于quartz 定时任务框架为基础的，因此具备quartz的大部分功能

2. 使用zookeeper做协调，调度中心，更加轻量级

3. 支持任务的分片

4. 支持弹性扩容 ， 可以水平扩展 ， 当任务再次运行时，会检查当前的服务器数量，重新分片，分片结束之后才会继续执行任务

5. 失效转移，容错处理，当一台调度服务器宕机或者跟zookeeper断开连接之后，会立即停止作业，然后再去寻找其他空闲的调度服务器，来运行剩余的任务

6. 提供运维界面，可以管理作业和注册中心

elastic-job结合了quartz非常优秀的时间调度功能，并且利用ZooKeeper实现了灵活的分片策略。除此之外，还加入了大量实用的监控和管理功能，以及其开源社区活跃、文档齐全、代码优雅等优点，是分布式任务调度框架的推荐选择。由于elastic-job-lite  不支持动态添加作业，此处仅贴上elastic-job-Cloud架构图

![elastic-job-Cloud架构图](https://github.com/DemoTransfer/Java-Guide/blob/master/docs/%E5%88%86%E5%B8%83%E5%BC%8F%E4%BB%BB%E5%8A%A1%E8%B0%83%E5%BA%A6/picture/elastic-job-Cloud%E6%9E%B6%E6%9E%84%E5%9B%BE.png)

xxl-job
------

由个人开源的一个轻量级分布式任务调度框架 ，主要分为 调度中心和执行器两部分 ， 调度中心在启动初始化的时候，会默认生成执行器的RPC代理

对象（http协议调用）， 执行器项目启动之后， 调度中心在触发定时器之后通过jobHandle 来调用执行器项目里面的代码，核心功能和elastic-job差不多，同时技术文档比较完善

![xxl-job系统架构图](https://github.com/DemoTransfer/Java-Guide/blob/master/docs/%E5%88%86%E5%B8%83%E5%BC%8F%E4%BB%BB%E5%8A%A1%E8%B0%83%E5%BA%A6/picture/xxl-job%E7%B3%BB%E7%BB%9F%E6%9E%B6%E6%9E%84%E5%9B%BE.jpg)

quartz
------

quartz 的常见集群方案如下，通过在数据库中配置定时器信息， 以数据库悲观锁的方式达到同一个任务始终只有一个节点在运行

**优点**

1. 保证节点高可用 （HA）， 如果某一个几点挂了， 其他节点可以顶上

**缺点**

1. 同一个任务只能有一个节点运行，其他节点将不执行任务，性能低，资源浪费

2. 当碰到大量短任务时，各个节点频繁的竞争数据库锁，节点越多这种情况越严重。性能会很低下

3. quartz 的分布式仅解决了集群高可用的问题，并没有解决任务分片的问题，不能实现水平扩展

![quartz结构图](https://github.com/DemoTransfer/Java-Guide/blob/master/docs/%E5%88%86%E5%B8%83%E5%BC%8F%E4%BB%BB%E5%8A%A1%E8%B0%83%E5%BA%A6/picture/quartz%E6%9E%B6%E6%9E%84%E5%9B%BE.png)

Saturn
------

Saturn是唯品会在github开源的一款分布式任务调度产品。它是基于当当elastic-job 1.0版本来开发的，其上完善了一些功能和添加了一些新的feature。

**亮点**

1. 支持多语言开发 python、Go、Shell、Java、Php

2. 管理控制台和数据统计分析更加完善

**缺点**

1. 技术文档较少，该框架是2016年由唯品会的研发团队基于elastic-job开发而来的

opencron 
------

一个功能完善真正通用的linux定时任务调度定系统,满足多种场景下各种复杂的定时任务调度,同时集成了linux实时监控,webssh,提供一个方便管理定时任务的平台

**缺点**

1. 仅支持kill任务，现场执行，查询任务运行状态等，主要功能是着重于任务的修改和查询上。不能动态的添加任务以及任务分片。

antares
------

**优点**

1. 一个任务仅会被服务器集群中的某个节点调度，调度机制基于成熟的 quartz

2. 并行执行 ， 用户可通过对任务预分片，有效提升任务执行效率

3. 失效转移

4. 弹性扩容，在任务运行时，可以动态的加机器

5. 友好的管理控制台

**缺点**

1. 不能动态的添加任务，仅能在控制台对任务进行触发，暂停，删除等操作

2. 文档不多，开源社区不够活跃

![系统架构图](https://github.com/DemoTransfer/Java-Guide/blob/master/docs/%E5%88%86%E5%B8%83%E5%BC%8F%E4%BB%BB%E5%8A%A1%E8%B0%83%E5%BA%A6/picture/antares%E7%B3%BB%E7%BB%9F%E6%9E%B6%E6%9E%84%E5%9B%BE.png)

比较
------

此处列出了几个代表性的开源产品

![比较](https://github.com/DemoTransfer/Java-Guide/blob/master/docs/%E5%88%86%E5%B8%83%E5%BC%8F%E4%BB%BB%E5%8A%A1%E8%B0%83%E5%BA%A6/picture/%E6%AF%94%E8%BE%83%E7%B3%BB%E7%BB%9F%E6%9E%B6%E6%9E%84%E5%9B%BE.png)

