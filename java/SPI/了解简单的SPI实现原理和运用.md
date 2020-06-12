看在前面
------

* <a href= "">SPI实现原理及应用</a>

SPI原理
------

![SPI-1](https://github.com/DemoTransfer/Java-Guide/blob/master/java/SPI/picture/spi_1.png)

SPI的全名为Service Provider Interface.大多数开发人员可能不熟悉，因为这个是针对厂商或者插件的。在java.util.ServiceLoader的文档里有比较详细的介绍。简单的总结下java spi机制的思想。我们系统里抽象的各个模块，往往有很多不同的实现方案，比如日志模块的方案，xml解析模块、jdbc模块的方案等。面向对象的设计里，我们一般推荐模块之间基于接口编程，模块之间不使用实现类进行硬编码。一旦代码里涉及具体的实现类，就违反了可拔插的原则，如果需要替换一种实现，就需要修改代码。为了实现在模块装配的时候动态指定具体实现类，这就需要一种服务发现机制。 java spi就是提供这种功能的机制：为某个接口寻找服务实现的机制。有点类似IOC的思想，将装配的控制权移到程序之外，在模块化设计中这个机制尤其重要。 

java SPI应用场景很广泛，在Java底层和一些框架中都很常用，比如java数据驱动加载和Dubbo。Java底层定义加载接口后，由不同的厂商提供驱动加载的实现方式，当我们需要加载不同的数据库的时候，只需要替换数据库对应的驱动加载jar包，就可以进行使用。

要使用Java SPI，需要遵循如下约定：

1、当服务提供者提供了接口的一种具体实现后，在jar包的META-INF/services目录下创建一个以“接口全限定名”为命名的文件，内容为实现类的全限定名；

2、接口实现类所在的jar包放在主程序的classpath中；

3、主程序通过java.util.ServiceLoder动态装载实现模块，它通过扫描META-INF/services目录下的配置文件找到实现类的全限定名，把类加载到JVM；

4、SPI的实现类必须携带一个不带参数的构造方法；

SPI实现
------

![spi_2](https://github.com/DemoTransfer/Java-Guide/blob/master/java/SPI/picture/spi_2.png)

(1) spi-demo-api 提供需要实现的接口

```java
public interface SpiDemo {
    void say();
}
```

（2）spi-demo-impl1 为第一种实现

```java
public class SpiDemoImpl1 implements SpiDemo {
    public void say() {
        System.out.println("SpiDemoImpl1");
    }
}
```
每一个SPI接口都需要在自己项目的静态资源目录中声明一个services文件，文件名为实现规范接口的类名全路径。在resources目录中创建\META-INF\services目录，创建以com.hanggle.spi.api.SpiDemo为名的文件。（文件名即是要实现的接口类的全路径如下图）

![spi_3](https://github.com/DemoTransfer/Java-Guide/blob/master/java/SPI/picture/spi_3.png)

![spi_4](https://github.com/DemoTransfer/Java-Guide/blob/master/java/SPI/picture/spi_4.png)

![spi_5](https://github.com/DemoTransfer/Java-Guide/blob/master/java/SPI/picture/spi_5.png)

文件内容：

```java
com.hanggle.spi.api.impl1.SpiDemoImpl1
```

（3）spi-demo-impl2 为第二种实现 

```java
public class SpiDemoImpl2 implements SpiDemo {
    public void say() {
        System.out.println("SpiDemoImpl2");
    }
}
```
同spi-demo-impl1一样

在resources目录中创建\META-INF\services目录，创建以com.hanggle.spi.api.SpiDemo为名的文件。

文件内容：

```java
com.hanggle.spi.api.impl1.SpiDemoImpl2
```

（4）spi-demo-main 程序中的使用

```java
public static void main(String[] args) {
    ServiceLoader<SpiDemo> serviceLoader = ServiceLoader.load(SpiDemo.class);
    for (SpiDemo o : serviceLoader) {
        o.say();
    }
}
```

运行结果：

![spi_6](https://github.com/DemoTransfer/Java-Guide/blob/master/java/SPI/picture/spi_6.png)

SPI 源码
------

![spi_7](https://github.com/DemoTransfer/Java-Guide/blob/master/java/SPI/picture/spi_7.png)

![spi_8](https://github.com/DemoTransfer/Java-Guide/blob/master/java/SPI/picture/spi_8.png)

有源代码可以，java会根据定义的路径去扫描可能存在的接口的实现。放在config中，然后使用parse方法将配置文件中的接口实现全路径放在pending中，并取得第一个实现类（变量nextName），

然后使用类加载器加载，加载需要调用的类，然后调用实现的方法

优缺点
------

**优点**

使用Java SPI机制的优势是实现解耦，使得第三方服务模块的装配控制的逻辑与调用者的业务代码分离，而不是耦合在一起。应用程序可以根据实际业务情况启用框架扩展或替换框架组件。

**缺点**

* 虽然ServiceLoader也算是使用的延迟加载，但是基本只能通过遍历全部获取，也就是接口的实现类全部加载并实例化一遍。如果你并不想用某些实现类，它也被加载并实例化了，这就造成了浪费。获取某个实现类的方式不够灵活，只能通过Iterator形式获取，不能根据某个参数来获取对应的实现类。

* 多个并发多线程使用ServiceLoader类的实例是不安全的。
