看在前面
------

* <a href="https://www.jianshu.com/p/fa4e88f95631">JMX的用处及用法</a>

JMX最常见的场景是监控Java程序的基本信息和运行情况，任何Java程序都可以开启JMX，然后使用JConsole或Visual VM进行预览。下图是使用Jconsle通过JMX查看Java程序的运行信息

![jmx_1](https://github.com/DemoTransfer/Java-Guide/blob/master/java/JMX/picture/jmx_1.png)

为Java程序开启JMX很简单，只要在运行Java程序的命令后面指定如下命令即可

```java
-Djava.rmi.server.hostname=127.0.0.1
-Dcom.sun.management.jmxremote.port=1000
-Dcom.sun.management.jmxremote.ssl=false
-Dcom.sun.management.jmxremote.authenticate=false
```
我们从Jconsole的视图标签中见到，JConsole通过JMX展示的信息都是Java程序的通用信息，如内存情况、线程情况、类加载情况等，换言之，只要是Java程序就都具备这些信息。这些信息为我们优化程序性能、排查BUG非常有用，而JMX就是获取这些信息的基础，因此它是一种非常有用的技术。

然而JMX的强大远不止此，它出了能提供一些通用的信息以外，还能通过特定的编程接口提供一些针对具体程序的专有信息并在JConsole等JMX客户端工具中展示，具体点说就是程序员可以把需要展示的信息放在一种叫做MBean的Java对象内，然后JConsole之类的客户端工具可以连接到JMX服务，识别MBean并在图形界面中显示。从纯抽象的角度触发，这其实有点像浏览器发送一个请求给http服务器，然后http服务器执行浏览器的请求并返回相应的数据，从某种角度来说JConsole和JMX也是以这种方式工作的，只是它们使用的协议不是http，交换数据协议格式不是http数据包，但是他们的确是以客户端/服务器这种模式工作的，而且完成的事情也差不多。

那么既然有了http，JMX又有何存在意义呢。 事实上，JMX能完成的任务通过http的确都能完成，只不过某些情况下用JMX来做会更加方便。

比如说你需要知道服务器上个运行中程序的相关信息， 如执行了多少次数据库操作、任务队列中有多少个任务在等待处理

最常用的解决方案，我们会在程序中启动一个http服务，当接收到来自客户端的请求这些信息的请求时，我们的http处理程序会获得这些信息，并转换成特定格式的数据如JSON返回给客户端，客户端会以某种方式展现这些信息。

如以JMX作为解决方案，核心流程也是如此，但在数据的交换方式上会略有不同。

下面我们展示JMX是如何完成此任务的。

一、定义一个展示所需信息的MBean接口
------

```java
public interface ServerInfoMBean {
    int getExecutedSqlCmdCount();
}
```

在使用 Standard Mbean 作为数据传输对象的情况下这个接口的定义是必须的， 并且接口名称必须以“MBean”这个单词结尾。

二、实现具体的MBean
------

```java
public class ServerInfo implements ServerInfoMBean {
    public int getExecutedSqlCmdCount() {
        return Dbutil.getExecutedSqlCmdCount();
    }
}
```

三、在程序的某个地方启动JMX服务并注册ServerInfoMBean
------

```java
public static void main(String[] args)  throws JMException, Exception{
    MBeanServer server = ManagementFactory.getPlatformMBeanServer();
    ObjectName name = new ObjectName("serverInfoMBean:name=serverInfo");
    server.registerMBean(new ServerInfo(), name);
}
```

四、运行程序，并通过JConsole查看
------

如果程序运行在本地，Jconsole会自动检测到程序的进程，鼠标双击进入即可

![jmx_2](https://github.com/DemoTransfer/Java-Guide/blob/master/java/JMX/picture/jmx_2.png)

在JConsole下面即会展示我们定义的MBean中的内容

![jmx_3](https://github.com/DemoTransfer/Java-Guide/blob/master/java/JMX/picture/jmx_3.png)

那么假如Java程序并非运行在本地而是运行在远端服务器上我们应该如何通过客户端去连接呢， 很简单，只要使用JDK提供的JMX类库监听端口提供服务即可

```java                
public class Main {
    public static void main(String[] args)  throws JMException, Exception{
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName("serverInfoMBean:name=serverInfo");
        server.registerMBean(new ServerInfo(), name);


        LocateRegistry.createRegistry(8081);
        JMXServiceURL url = new JMXServiceURL
                ("service:jmx:rmi:///jndi/rmi://localhost:8081/jmxrmi");
        JMXConnectorServer jcs = JMXConnectorServerFactory.newJMXConnectorServer(url, null, server);
        jcs.start();
    }
}
```

或者在启动Java程序指定命令行参数也

```java
-Djava.rmi.server.hostname=127.0.0.1
-Dcom.sun.management.jmxremote.port=10086
-Dcom.sun.management.jmxremote.ssl=false
-Dcom.sun.management.jmxremote.authenticate=false
```

然后使用JConsole的连接远端进程功能即可

![jmx_4](https://github.com/DemoTransfer/Java-Guide/blob/master/java/JMX/picture/jmx_4.png)

其余的操作和本地无差。

这相对于提供一个http服务来完成任务是不是要简单了不少，http是一个更加抽象、应用面更广泛、功能更强大的服务，因此所作的工作也要更多一些。JMX则是一个更加具体、应用面不那么广、功能也没有http强大的服务，不过呢它胜在解决特定问题更加轻松方便，上面的示例已经很好的说明了。

此外，JMX和Jconsole并不仅仅只能展示数据，它还能执行Java方法。以上面的示例为基础我们再进行一系列改进。

一、扩展ServerInfoMBean接口和实现的类
------

```java
public interface ServerInfoMBean {
    int getExecutedSqlCmdCount();
    void printString(String fromJConsole);
}

public class ServerInfo implements ServerInfoMBean {
    public int getExecutedSqlCmdCount() {
        return 100;
    }

    public void printString(String fromJConsole) {
        System.out.println(fromJConsole);
    }
}
```

二、运行程序并使用JConsole连接
------

![jmx_5](https://github.com/DemoTransfer/Java-Guide/blob/master/java/JMX/picture/jmx_5.png)

mbean页签中出现了我们新添加的方法

三、点击printString按钮调用方法
------

![jmx_6](https://github.com/DemoTransfer/Java-Guide/blob/master/java/JMX/picture/jmx_6.png)

方法被调用，同时控制台也打印了通过Jconsole传递的参数

![jmx_7](https://github.com/DemoTransfer/Java-Guide/blob/master/java/JMX/picture/jmx_7.png)

这里只是讲解了JMX的用处和最基础的使用方法，显然JMX真正提供的功能远不及此，比如它可以不用JConsole而是客户端编程的方式访问等等， 有兴趣的同学可以深入研究。

总而言之， 我觉得JMX是一种小巧精悍的工具，在不需要大张旗鼓的通过http或者其他server\client方式提供服务时，就是他发挥用处的时机了。



