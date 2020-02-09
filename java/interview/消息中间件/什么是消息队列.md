看在前面
====

> * <a href="https://juejin.im/post/5cb025fb5188251b0351ef48">什么是消息队列？</a>

> * <a href="https://zhuanlan.zhihu.com/p/37405836">Kafka简明教程</a>

> * <a href="https://zhuanlan.zhihu.com/p/55712984">消息队列使用的四种场景介绍，有图有解析，一看就懂</a>

> * <a href="https://zhuanlan.zhihu.com/p/21479556">消息队列设计精要</a>

> * <a href="https://www.zhihu.com/question/34243607">消息队列的使用场景是怎样的</a>

* **掘金特邀作者 | 技术公众号：Java3 y**

* **文章导航：https://github.com/ZhongFuCheng3y/3y**

一、什么是消息队列？
====

消息队列不知道大家看到这个词的时候，会不会觉得它是一个比较高端的技术，反正我是觉得它好像是挺牛逼的。

> 消息队列，一般我们会简称它为MQ(Message Queue)，嗯，就是很直白的简写。

我们先不管消息(Message)这个词，来看看队列(Queue)。这一看，队列大家应该都熟悉吧。

> 队列是一种先进先出的数据结构。

![消息队列数据结构](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E6%B6%88%E6%81%AF%E9%98%9F%E5%88%97%E6%95%B0%E6%8D%AE%E7%BB%93%E6%9E%84.png)

在Java里边，已经实现了不少的队列了，比如：```AbstractQueue```、```BlockingQueue```、```Deque```、```ConcurrentLinkedQueue```等等。

那为什么还需要消息队列(MQ)这种**中间件**呢？其实这个问题，跟之前我学Redis的时候很像。Redis是一个以```key-value```形式存储的内存数据库，明明我们可以使用类似HashMap这种实现类就可以达到类似的效果了，<a href="https://mp.weixin.qq.com/s?__biz=MzI4Njg5MDA5NA==&mid=2247484609&idx=1&sn=4c053236699fde3c2db1241ab497487b&chksm=ebd745c0dca0ccd682e91938fc30fa947df1385b06d6ae9bb52514967b0736c66684db2f1ac9&token=177635168&lang=zh_CN&scene=21#wechat_redirect">那还为什么要Redis</a>？

到这里，大家可以先猜猜为什么要用消息队列(MQ)这种中间件，下面会继续补充。消息队列可以简单理解为：**把要传输的数据放在队列中**。

![消息数据结构模型](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E6%B6%88%E6%81%AF%E6%95%B0%E6%8D%AE%E7%BB%93%E6%9E%84%E6%A8%A1%E5%9E%8B.png)

科普：

* 把数据放到消息队列叫做**生产者**

* 从消息队列里边取数据叫做**消费者**

二、为什么要用消息队列？
====

为什么要用消息队列，也就是在问：用了消息队列有什么好处。我们看看以下的场景

2.1 解耦
------

现在我有一个系统A，系统A可以产生一个```userId```

![消息队列解耦一](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E6%B6%88%E6%81%AF%E9%98%9F%E5%88%97%E8%A7%A3%E8%80%A6%E4%B8%80.png)

然后，现在有系统B和系统C都需要这个```userId```去做相关的操作

![消息队列解耦二](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E6%B6%88%E6%81%AF%E9%98%9F%E5%88%97%E8%A7%A3%E8%80%A6%E4%BA%8C.png)

写成伪代码可能是这样的：

```java
public class SystemA {

    // 系统B和系统C的依赖
    SystemB systemB = new SystemB();
    SystemC systemC = new SystemC();

    // 系统A独有的数据userId
    private String userId = "Java3y";

    public void doSomething() {

        // 系统B和系统C都需要拿着系统A的userId去操作其他的事
        systemB.SystemBNeed2do(userId);
        systemC.SystemCNeed2do(userId);

    }
}
```

结构图如下：

![消息队列解耦三](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E6%B6%88%E6%81%AF%E9%98%9F%E5%88%97%E8%A7%A3%E8%80%A6%E4%B8%89.png)

ok，一切平安无事度过了几个天。

某一天，系统B的负责人告诉系统A的负责人，现在系统B的```SystemBNeed2do(String userId)```这个接口不再使用了，让系统A别去调它了。

于是，系统A的负责人说"好的，那我就不调用你了。"，于是就把调用系统B接口的代码给删掉了：

```java
public void doSomething() {

  // 系统A不再调用系统B的接口了
  //systemB.SystemBNeed2do(userId);
  systemC.SystemCNeed2do(userId);

}
```

又过了几天，系统D的负责人接了个需求，也需要用到系统A的userId，于是就跑去跟系统A的负责人说："老哥，我要用到你的userId，你调一下我的接口吧"

于是系统A说："没问题的，这就搞"

![消息队列解耦四](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E6%B6%88%E6%81%AF%E9%98%9F%E5%88%97%E8%A7%A3%E8%80%A6%E5%9B%9B.png)

然后，系统A的代码如下：

```java
public class SystemA {

    // 已经不再需要系统B的依赖了
    // SystemB systemB = new SystemB();

    // 系统C和系统D的依赖
    SystemC systemC = new SystemC();
    SystemD systemD = new SystemD();

    // 系统A独有的数据
    private String userId = "Java3y";

    public void doSomething() {


        // 已经不再需要系统B的依赖了
        //systemB.SystemBNeed2do(userId);

        // 系统C和系统D都需要拿着系统A的userId去操作其他的事
        systemC.SystemCNeed2do(userId);
        systemD.SystemDNeed2do(userId);

    }
}
```

时间飞逝：

* 又过了几天，系统E的负责人过来了，告诉系统A，需要userId。

* 又过了几天，系统B的负责人过来了，告诉系统A，还是重新掉那个接口吧。

* 又过了几天，系统F的负责人过来了，告诉系统A，需要userId。

* ……

于是系统A的负责人，每天都被这给骚扰着，改来改去，改来改去…….

还有另外一个问题，调用系统C的时候，如果系统C挂了，系统A还得想办法处理。如果调用系统D时，由于网络延迟，请求超时了，那系统A是反馈fail还是重试？？

最后，系统A的负责人，觉得隔一段时间就改来改去，没意思，于是就跑路了。

然后，公司招来一个大佬，大佬经过几天熟悉，上来就说：将系统A的userId写到消息队列中，这样系统A就不用经常改动了。为什么呢？下面我们来一起看看：

![消息队列解耦五](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E6%B6%88%E6%81%AF%E9%98%9F%E5%88%97%E4%BA%94.png)

系统A将userId写到消息队列中，系统C和系统D从消息队列中拿数据。这样有什么好处？

* 系统A只负责把数据写到队列中，谁想要或不想要这个数据(消息)，系统A一点都不关心

* 即便现在系统D不想要userId这个数据了，系统B又突然想要userId这个数据了，都跟系统A无关，系统A一点代码都不用改。

* 系统D拿userId不再经过系统A，而是从消息队列里边拿。系统D即便挂了或者请求超时，都跟系统A无关，只跟消息队列有关。

这样一来，系统A与系统B、C、D都解耦了。

2.2 异步
------

我们再来看看下面这种情况：系统A还是直接调用系统B、C、D

![消息队列异步一](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E6%B6%88%E6%81%AF%E9%98%9F%E5%88%97%E5%BC%82%E6%AD%A5%E4%B8%80.png)

代码如下：

```java
public class SystemA {

    SystemB systemB = new SystemB();
    SystemC systemC = new SystemC();
    SystemD systemD = new SystemD();

    // 系统A独有的数据
    private String userId ;

    public void doOrder() {

        // 下订单
          userId = this.order();
        // 如果下单成功，则安排其他系统做一些事  
        systemB.SystemBNeed2do(userId);
        systemC.SystemCNeed2do(userId);
        systemD.SystemDNeed2do(userId);

    }
}
```

假设系统A运算出userId具体的值需要50ms，调用系统B的接口需要300ms，调用系统C的接口需要300ms，调用系统D的接口需要300ms。那么这次请求就需要50+300+300+300=950ms

并且我们得知，系统A做的是主要的业务，而系统B、C、D是非主要的业务。比如系统A处理的是订单下单，而系统B是订单下单成功了，那发送一条短信告诉具体的用户此订单已成功，而系统C和系统D也是处理一些小事而已。

那么此时，为了提高用户体验和吞吐量，其实可以异步地调用系统B、C、D的接口。所以，我们可以弄成是这样的：

![消息队列异步二](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E6%B6%88%E6%81%AF%E9%98%9F%E5%88%97%E5%BC%82%E6%AD%A5%E4%BA%8C.png)

系统A执行完了以后，将userId写到消息队列中，然后就直接返回了(至于其他的操作，则异步处理)。

* 本来整个请求需要用950ms(同步)

* 现在将调用其他系统接口异步化，从请求到返回只需要100ms(异步)

（例子可能举得不太好，但我觉得说明到点子上就行了，见谅。)

2.3 削峰/限流
------

我们再来一个场景，现在我们每个月要搞一次大促，大促期间的并发可能会很高的，比如每秒3000个请求。假设我们现在有两台机器处理请求，并且每台机器只能每次处理1000个请求。

![消息队列削峰限流一](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E6%B6%88%E6%81%AF%E9%98%9F%E5%88%97%E5%89%8A%E5%B3%B0%E9%99%90%E6%B5%81%E4%B8%80.png)

那多出来的1000个请求，可能就把我们整个系统给搞崩了…所以，有一种办法，我们可以写到消息队列中：

![消息队列削峰限流二](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E6%B6%88%E6%81%AF%E9%98%9F%E5%88%97%E5%89%8A%E5%B3%B0%E9%99%90%E6%B5%81%E4%BA%8C.png)

系统B和系统C根据自己的能够处理的请求数去消息队列中拿数据，这样即便有每秒有8000个请求，那只是把请求放在消息队列中，去拿消息队列的消息由系统自己去控制，这样就不会把整个系统给搞崩。

三、使用消息队列有什么问题？
====

经过我们上面的场景，我们已经可以发现，消息队列能做的事其实还是蛮多的。

说到这里，我们先回到文章的开头，"明明JDK已经有不少的队列实现了，我们还需要消息队列中间件呢？"其实很简单，JDK实现的队列种类虽然有很多种，但是都是简单的内存队列。为什么我说JDK是简单的内存队列呢？下面我们来看看要实现消息队列(中间件)可能要考虑什么问题。

3.1 高可用
------

无论是我们使用消息队列来做解耦、异步还是削峰，消息队列肯定不能是单机的。试着想一下，如果是单机的消息队列，万一这台机器挂了，那我们整个系统几乎就是不可用了。

![消息队列高可用一](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E6%B6%88%E6%81%AF%E9%98%9F%E5%88%97%E9%AB%98%E5%8F%AF%E7%94%A8%E4%B8%80.png)

所以，当我们项目中使用消息队列，都是得集群/分布式的。要做集群/分布式就必然希望该消息队列能够提供现成的支持，而不是自己写代码手动去实现。

3.2 数据丢失问题
------

我们将数据写到消息队列上，系统B和C还没来得及取消息队列的数据，就挂掉了。如果没有做任何的措施，我们的数据就丢了。

学过Redis的都知道，Redis可以将数据持久化磁盘上，万一Redis挂了，还能从磁盘从将数据恢复过来。同样地，消息队列中的数据也需要存在别的地方，这样才尽可能减少数据的丢失。

那存在哪呢？

* 磁盘？

* 数据库？

* Redis？

* 分布式文件系统？

同步存储还是异步存储？

3.3消费者怎么得到消息队列的数据？
------

消费者怎么从消息队列里边得到数据？有两种办法：

* 生产者将数据放到消息队列中，消息队列有数据了，主动叫消费者去拿(俗称push)

* 消费者不断去轮训消息队列，看看有没有新的数据，如果有就消费(俗称pull)

3.4 其他
------

除了这些，我们在使用的时候还得考虑各种的问题：

* 消息重复消费了怎么办啊？

* 我想保证消息是绝对有顺序的怎么做？

* ......

虽然消息队列给我们带来了那么多的好处，但同时我们发现引入消息队列也会提高系统的复杂性。市面上现在已经有不少消息队列轮子了，每种消息队列都有自己的特点，选取哪种MQ还得好好斟酌。

最后
====

本文主要讲解了什么是消息队列，消息队列可以为我们带来什么好处，以及一个消息队列可能会涉及到哪些问题。希望给大家带来一定的帮助。
