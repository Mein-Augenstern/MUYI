看在前面
------

* <a href="https://www.cnblogs.com/54chensongxia/p/11703822.html">你听说过JMX么</a>

* <a href="https://zhidao.baidu.com/question/1881747821256634788.html">什么是JMX</a>

* <a href="https://baike.baidu.com/item/JMX/2829357?fr=aladdin">百度百科介绍</a>


<a href="https://baike.baidu.com/item/JMX/2829357?fr=aladdin">百度百科介绍</a>
------

> JMX（Java Management Extensions，即Java管理扩展）是一个为应用程序、设备、系统等植入管理功能的框架。JMX可以跨越一系列异构操作系统平台、系统体系结构和网络传输协议，灵活的开发无缝集成的系统、网络和服务管理应用。

JMX最常见的场景是监控Java程序的基本信息和运行情况，任何Java程序都可以开启JMX，然后使用JConsole或Visual VM进行预览。下图是使用Jconsle通过JMX查看Java程序的运行信息

![jmx_1](https://github.com/DemoTransfer/Java-Guide/blob/master/java/JMX/picture/jmx_1.png)

<a href="https://baike.baidu.com/item/JMX/2829357?fr=aladdin">百度百科简介</a>
------

> JMX在Java编程语言中定义了应用程序以及网络管理和监控的体系结构、设计模式、应用程序接口以及服务。通常使用JMX来监控系统的运行状态或管理系统的某些方面，比如清空缓存、重新加载配置文件等
优点是可以非常容易的使应用程序被管理
伸缩性的架构使每个JMX Agent服务可以很容易的放入到Agent中，每个JMX的实现都提供几个核心的Agent服务，你也可以自己编写服务，服务可以很容易的部署，取消部署。
主要作用是提供接口，允许有不同的实现

<a href="https://www.cnblogs.com/54chensongxia/p/11703822.html">什么是JMX</a>

> JMX（Java管理扩展），是一套给应用程序引入监控管理功能的接口。比如我们可以通过JMX来监控Tomcat的运行状态。JMX最主要的应用场景就是中间件的监控，配置文件的在线修改配置。

相关概念
------

一个典型的JMX架构图：

![what_jmx_one]()

**MBean**: 是Managed Bean的简称。在JMX中MBean代表一个被管理的资源实例，通过MBean中暴露的方法和属性，外界可以获取被管理的资源的状态和操纵MBean的行为。事实上，MBean就是一个Java Object，同JavaBean模型一样，外界使用自醒和反射来获取Object的值和调用Object的方法，只是MBean更为复杂和高级一些。

**MBeanServer**: MBean生存在一个MBeanServer中。MBeanServer管理这些MBean，并且代理外界对它们的访问。并且MBeanServer提供了一种注册机制，是的外界可以通过名字来得到相应的MBean实例。

**JMX Agent**: Agent只是一个Java进程，它包括这个MBeanServer和一系列附加的MbeanService。当然这些Service也是通过MBean的形式来发布。

**ObjectName**: MBean在MBeanServer中的唯一标识。


