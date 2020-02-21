看在前面
====

> * <a href= "https://github.com/Snailclimb/JavaGuide/blob/master/docs/java/jvm/%E7%B1%BB%E5%8A%A0%E8%BD%BD%E5%99%A8.md">类加载器</a>
> * https://blog.csdn.net/xyang81/article/details/7292380
> * https://juejin.im/post/5c04892351882516e70dcc9b
> * http://gityuan.com/2016/01/24/java-classloader/

Question
====

* 说说ClassLoader理解？

* 说说ClassLoader原理？

* 说说双亲委派模型理解？

* 为什么要使用双亲委派模型？

* 怎么样打破双亲委派模型？

* 自定义类加载器实现过程？

* JVM在搜索类的时候，又是如何判定两个class是相同的呢？

一、回顾一下类加载过程
====

类加载过程：**加载->连接->初始化**。连接过程又可分为三步:**验证->准备->解析**。

![类加载过程](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E7%B1%BB%E5%8A%A0%E8%BD%BD%E8%BF%87%E7%A8%8B.png)

一个非数组类的加载阶段（加载阶段获取类的二进制字节流的动作）是可控性最强的阶段，这一步我们可以去完成还可以自定义类加载器去控制字节流的获取方式（重写一个类加载器的 loadClass() 方法）。数组类型不通过类加载器创建，它由 Java 虚拟机直接创建。**所有的类都由类加载器加载，加载的作用就是将 .class文件加载到内存**。

二、类加载器总结
====

<h3>2.1 回顾一下什么是ClassLoader</h3>

大家都知道，当我们写好一个Java程序之后，不是管是CS还是BS应用，都是由若干个.class文件组织而成的一个完整的Java应用程序，当程序在运行时，即会调用该程序的一个入口函数来调用系统的相关功能，而这些功能都被封装在不同的class文件当中，所以经常要从这个class文件中要调用另外一个class文件中的方法，如果另外一个文件不存在的，则会引发系统异常。而程序在启动的时候，并不会一次性加载程序所要用的所有class文件，而是根据程序的需要，通过Java的类加载机制（ClassLoader）来动态加载某个class文件到内存当中的，从而只有class文件被载入到了内存之后，才能被其它class所引用。所以ClassLoader就是用来动态加载class文件到内存当中用的。

<h3>2.2 ClassLoader原理介绍</h3>

ClassLoader使用的是双亲委托模型来搜索类的，每个ClassLoader实例都有一个父类加载器的引用（不是继承的关系，是一个包含的关系），虚拟机内置的类加载器（Bootstrap ClassLoader）本身没有父类加载器，但可以用作其它ClassLoader实例的的父类加载器。当一个ClassLoader实例需要加载某个类时，它会试图亲自搜索某个类之前，先把这个任务委托给它的父类加载器，这个过程是由上至下依次检查的，首先由最顶层的类加载器Bootstrap ClassLoader试图加载，如果没加载到，则把任务转交给Extension ClassLoader试图加载，如果也没加载到，则转交给App ClassLoader 进行加载，如果它也没有加载得到的话，则返回给委托的发起者，由它到指定的文件系统或网络等URL中加载该类。如果它们都没有加载到这个类时，则抛出ClassNotFoundException异常。否则将这个找到的类生成一个类的定义，并将它加载到内存当中，最后返回这个类在内存中的Class实例对象。


<h3>2.2 类加载器总结</h3>

JVM 中内置了三个重要的 ```ClassLoader```，除了 ```BootstrapClassLoader``` 其他类加载器均由 Java 实现且全部继承自```java.lang.ClassLoader```：

1. **BootstrapClassLoader(启动类加载器)** ：最顶层的加载类，由C++实现，负责加载 ```%JAVA_HOME%/lib```目录下的jar包和类或者或被 ```-Xbootclasspath```参数指定的路径中的所有类。

2. **ExtensionClassLoader(扩展类加载器)** ：主要负责加载目录 ```%JRE_HOME%/lib/ext``` 目录下的jar包和类，或被 ```java.ext.dirs``` 系统变量所指定的路径下的jar包。

3. **AppClassLoader(应用程序类加载器)** :面向我们用户的加载器，负责加载当前应用 ```classpath``` 下的所有jar包和类。

三、双亲委派模型
====

**<h3>3.1 双亲委派模型介绍</h3>**

每一个类都有一个对应它的类加载器。系统中的 ```ClassLoder``` 在协同工作的时候会默认使用 **双亲委派模型** 。即在类加载的时候，系统会首先判断当前类是否被加载过。已经被加载的类会直接返回，否则才会尝试加载。加载的时候，首先会把该请求委派该父类加载器的 ```loadClass()``` 处理，因此所有的请求最终都应该传送到顶层的启动类加载器 ```BootstrapClassLoader``` 中。当父类加载器无法处理时，才由自己来处理。当父类加载器为null时，会使用启动类加载器 ```BootstrapClassLoader``` 作为父类加载器。

![ClassLoader]()

每个类加载都有一个父类加载器，我们通过下面的程序来验证。

```java
public class ClassLoaderDemo {
    public static void main(String[] args) {
        System.out.println("ClassLodarDemo's ClassLoader is " + ClassLoaderDemo.class.getClassLoader());
        System.out.println("The Parent of ClassLodarDemo's ClassLoader is " + ClassLoaderDemo.class.getClassLoader().getParent());
        System.out.println("The GrandParent of ClassLodarDemo's ClassLoader is " + ClassLoaderDemo.class.getClassLoader().getParent().getParent());
    }
}
```

Output

```java
ClassLodarDemo's ClassLoader is sun.misc.Launcher$AppClassLoader@18b4aac2
The Parent of ClassLodarDemo's ClassLoader is sun.misc.Launcher$ExtClassLoader@1b6d3586
The GrandParent of ClassLodarDemo's ClassLoader is null
```

```AppClassLoader```的父类加载器为```ExtClassLoader```， ```ExtClassLoader```的父类加载器为```null```，```null```并不代表```ExtClassLoader```没有父类加载器，而是 ```BootstrapClassLoader``` 。

其实这个双亲翻译的容易让别人误解，我们一般理解的双亲都是父母，这里的双亲更多地表达的是“父母这一辈”的人而已，并不是说真的有一个 Mother ClassLoader 和一个 Father ClassLoader 。另外，类加载器之间的“父子”关系也不是通过继承来体现的，是由“优先级”来决定。官方API文档对这部分的描述如下:

> The Java platform uses a delegation model for loading classes. The basic idea is that every class loader has a "parent" class loader. When loading a class, a class loader first "delegates" the search for the class to its parent class loader before attempting to find the class itself.

**<h3>3.2 双亲委派模型实现源码分析</h3>**

双亲委派模型的实现代码非常简单，逻辑非常清晰，都集中在 ```java.lang.ClassLoader``` 的 ```loadClass()``` 中，相关代码如下所示。

```java
private final ClassLoader parent; 
protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException
    {
        synchronized (getClassLoadingLock(name)) {
            // 首先，检查请求的类是否已经被加载过
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                long t0 = System.nanoTime();
                try {
                    if (parent != null) {//父加载器不为空，调用父加载器loadClass()方法处理
                        c = parent.loadClass(name, false);
                    } else {//父加载器为空，使用启动类加载器 BootstrapClassLoader 加载
                        c = findBootstrapClassOrNull(name);
                    }
                } catch (ClassNotFoundException e) {
                   //抛出异常说明父类加载器无法完成加载请求
                }
                
                if (c == null) {
                    long t1 = System.nanoTime();
                    //自己尝试加载
                    c = findClass(name);

                    // this is the defining class loader; record the stats
                    sun.misc.PerfCounter.getParentDelegationTime().addTime(t1 - t0);
                    sun.misc.PerfCounter.getFindClassTime().addElapsedTimeFrom(t1);
                    sun.misc.PerfCounter.getFindClasses().increment();
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }
```

**<h3>3.3 双亲委派模型的好处 or 为什么要使用双亲委托这种模型呢？</h3>**

<h4>3.3.1 双亲委派模型的好处</h4>

双亲委派模型保证了Java程序的稳定运行，可以避免类的重复加载（JVM 区分不同类的方式不仅仅根据类名，相同的类文件被不同的类加载器加载产生的是两个不同的类），也保证了 Java 的核心 API 不被篡改。如果没有使用双亲委派模型，而是每个类加载器加载自己的话就会出现一些问题，比如我们编写一个称为 ```java.lang.Object``` 类的话，那么程序运行的时候，系统就会出现多个不同的 ```Object``` 类。

<h4>3.3.2 为什么要使用双亲委托这种模型呢？</h4>

因为这样可以避免重复加载，当父亲已经加载了该类的时候，就没有必要子ClassLoader再加载一次。考虑到安全因素，我们试想一下，如果不使用这种委托模式，那我们就可以随时使用自定义的String来动态替代java核心api中定义的类型，这样会存在非常大的安全隐患，而双亲委托的方式，就可以避免这种情况，因为String已经在启动时就被引导类加载器（Bootstrcp ClassLoader）加载，所以用户自定义的ClassLoader永远也无法加载一个自己写的String，除非你改变JDK中ClassLoader搜索类的默认算法。


**<h3>3.4 如果我们不想用双亲委派模型怎么办？</h3>**

为了避免双亲委托机制，我们可以自己定义一个类加载器，然后重写 ```loadClass()``` 即可。

四、自定义类加载器
====

除了 ```BootstrapClassLoader``` 其他类加载器均由 Java 实现且全部继承自 ```java.lang.ClassLoader```。如果我们要自定义自己的类加载器，很明显需要继承 ```ClassLoader```。

关于自定义类加载器可以参照：<a href="https://blog.csdn.net/u013412772/article/details/80848909">Java类加载器--自定义类加载器(ClassLoader)</a>

五、JVM在搜索类的时候，又是如何判定两个class是相同的呢？
====

JVM在判定两个class是否相同时，不仅要判断两个类名是否相同，而且要判断是否由同一个类加载器实例加载的。只有两者同时满足的情况下，JVM才认为这两个class是相同的。就算两个class是同一份class字节码，如果被两个不同的ClassLoader实例所加载，JVM也会认为它们是两个不同class。比如网络上的一个Java类org.classloader.simple.NetClassLoaderSimple，javac编译之后生成字节码文件NetClassLoaderSimple.class，ClassLoaderA和ClassLoaderB这两个类加载器并读取了NetClassLoaderSimple.class文件，并分别定义出了java.lang.Class实例来表示这个类，对于JVM来说，它们是两个不同的实例对象，但它们确实是同一份字节码文件，如果试图将这个Class实例生成具体的对象进行转换时，就会抛运行时异常java.lang.ClassCaseException，提示这是两个不同的类型。现在通过实例来验证上述所描述的是否正确：

1. 在web服务器上建一个org.classloader.simple.NetClassLoaderSimple.java类

```java
package org.classloader.simple;
 
public class NetClassLoaderSimple {
	
	private NetClassLoaderSimple instance;
 
	public void setNetClassLoaderSimple(Object obj) {
		this.instance = (NetClassLoaderSimple)obj;
	}
}
```

org.classloader.simple.NetClassLoaderSimple类的setNetClassLoaderSimple方法接收一个Object类型参数，并将它强制转换成org.classloader.simple.NetClassLoaderSimple类型。

2. 自定义类加载器：NetWorkClassLoader

```java
package classloader;
 
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
 
/**
 * 加载网络class的ClassLoader
 */
public class NetworkClassLoader extends ClassLoader {
	
	private String rootUrl;
 
	public NetworkClassLoader(String rootUrl) {
		this.rootUrl = rootUrl;
	}
 
	@Override
	protected Class<?> findClass(String name) throws ClassNotFoundException {
		Class clazz = null;//this.findLoadedClass(name); // 父类已加载	
		//if (clazz == null) {	//检查该类是否已被加载过
			byte[] classData = getClassData(name);	//根据类的二进制名称,获得该class文件的字节码数组
			if (classData == null) {
				throw new ClassNotFoundException();
			}
			clazz = defineClass(name, classData, 0, classData.length);	//将class的字节码数组转换成Class类的实例
		//} 
		return clazz;
	}
 
	private byte[] getClassData(String name) {
		InputStream is = null;
		try {
			String path = classNameToPath(name);
			URL url = new URL(path);
			byte[] buff = new byte[1024*4];
			int len = -1;
			is = url.openStream();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			while((len = is.read(buff)) != -1) {
				baos.write(buff,0,len);
			}
			return baos.toByteArray();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
			   try {
			      is.close();
			   } catch(IOException e) {
			      e.printStackTrace();
			   }
			}
		}
		return null;
	}
 
	private String classNameToPath(String name) {
		return rootUrl + "/" + name.replace(".", "/") + ".class";
	}
 
}
```

3. 测试两个class是否相同

```java
package classloader;
 
public class NewworkClassLoaderTest {
 
	public static void main(String[] args) {
		try {
			//测试加载网络中的class文件
			String rootUrl = "http://localhost:8080/httpweb/classes";
			String className = "org.classloader.simple.NetClassLoaderSimple";
			NetworkClassLoader ncl1 = new NetworkClassLoader(rootUrl);
			NetworkClassLoader ncl2 = new NetworkClassLoader(rootUrl);
			Class<?> clazz1 = ncl1.loadClass(className);
			Class<?> clazz2 = ncl2.loadClass(className);
			Object obj1 = clazz1.newInstance();
			Object obj2 = clazz2.newInstance();
			clazz1.getMethod("setNetClassLoaderSimple", Object.class).invoke(obj1, obj2);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}
```

首先获得网络上一个class文件的二进制名称，然后通过自定义的类加载器NetworkClassLoader创建两个实例，并根据网络地址分别加载这份class，并得到这两个ClassLoader实例加载后生成的Class实例clazz1和clazz2，最后将这两个Class实例分别生成具体的实例对象obj1和obj2，再通过反射调用clazz1中的setNetClassLoaderSimple方法。其中两个ClassLoader实例指的是new了两个不同的NetworkClassLoader实例对象。

4. 测试结果

![自定义ClassLoader加载class文件](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/%E4%B8%8D%E5%90%8CClassLoader%E5%8A%A0%E8%BD%BD%E7%9B%B8%E5%90%8Cclass%E5%BC%82%E5%B8%B8%E7%BB%93%E6%9E%9C.png)

结论：从结果中可以看出，虽然是同一份class字节码文件，但是由于被两个不同的ClassLoader实例所加载，所以JVM认为它们就是两个不同的类。
