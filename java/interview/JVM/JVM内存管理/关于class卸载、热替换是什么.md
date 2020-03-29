看在前面
====

>  * <a href="http://www.blogjava.net/heavensay/archive/2012/11/07/389685.html">class卸载、热替换和Tomcat的热部署的分析</a>

一、概述
====

名词解释：所谓热部署，就是在应用正在运行的时候升级软件，却不需要重新启动应用。本文主要是分析Tomcat中关于热部署和JSP更新替换的原理，在此之前先介绍class的热替换和class的卸载的原理。

二、Class热替换
====

ClassLoader中重要方法： loadClass：```ClassLoader.loadClass(...)``` 是ClassLoader的入口点。当一个类没有指明用什么加载器加载的时候，JVM默认采用```AppClassLoader```加载器加载没有加载过的class，调用的方法的入口就是```loadClass(...)```。如果一个class被自定义的ClassLoader加载，那么JVM也会调用这个自定义的```ClassLoader.loadClass(...)```方法来加载class内部引用的一些别的class文件。重载这个方法，能实现自定义加载class的方式，会抛弃双亲委托机制，但是即使不采用双亲委托机制，比如java.lang包中的相关类还是不能自定义一个同名的类来代替，主要因为JVM解析、验证class的时候，会进行相关判断。

defineClass：系统自带的ClassLoader，默认加载程序的是AppClassLoader，ClassLoader加载一个class，最终调用的是```defineClass(...)```方法，这时候就在想是否可以重复调用```defineClass(...)```方法加载同一个类(或者修改过)，最后发现调用多次的话会有相关错误:

```java
java.lang.LinkageError 
attempted duplicate class definition
```

所以一个class被一个ClassLoader实例加载过的话，就不能再被这个ClassLoader实例再次加载(这里的加载指的是，调用了```defineClass(...)```方法，重新加载字节码、解析、验证)。而系统默认的AppClassLoader加载器，他们内部会缓存加载过的class，重新加载的话，就直接取缓存。所与对于热加载的话，只能重新创建一个ClassLoader，然后再去加载已经被加载过的class文件。

下面看一个class热加载的例子： 代码：HotSwapURLClassLoader自定义classloader，实现热替换的关键

```java
package testjvm.testclassloader;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

/**
  * 只要功能是重新加载更改过的.class文件，达到热替换的作用
  * @author banana
  */
public class HotSwapURLClassLoader extends URLClassLoader {
     
    //缓存加载class文件的最后最新修改时间
    public static Map<String,Long> cacheLastModifyTimeMap = new HashMap<String,Long>();
    
    //工程class类所在的路径
    public static String projectClassPath = "D:/Ecpworkspace/ZJob-Note/bin/";
    
    //所有的测试的类都在同一个包下
    public static String packagePath = "testjvm/testclassloader/";
   
    private static HotSwapURLClassLoader hcl = new HotSwapURLClassLoader();
 
    public HotSwapURLClassLoader() {
        //设置ClassLoader加载的路径
        super(getMyURLs());
    }
      
    public static HotSwapURLClassLoader  getClassLoader(){
        return hcl;
    } 

    private static  URL[] getMyURLs(){
        URL url = null;
        try {
            url = new File(projectClassPath).toURI().toURL();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
            return new URL[] { url };
    }
     
    /**
     * 重写loadClass，不采用双亲委托机制("java."开头的类还是会由系统默认ClassLoader加载)
     */
    @Override
    public Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
        Class clazz = null;
        //查看HotSwapURLClassLoader实例缓存下，是否已经加载过class
        //不同的HotSwapURLClassLoader实例是不共享缓存的
        clazz = findLoadedClass(name);
        if (clazz != null ) {
            if (resolve){
                resolveClass(clazz);
            }
            //如果class类被修改过，则重新加载
            if (isModify(name)) {
                hcl = new HotSwapURLClassLoader();
                clazz = customLoad(name, hcl);
            }
            return (clazz);
        }
 
        //如果类的包名为"java."开始，则有系统默认加载器AppClassLoader加载
        if(name.startsWith("java.")){
            try {
                //得到系统默认的加载cl，即AppClassLoader
                ClassLoader system = ClassLoader.getSystemClassLoader();
                clazz = system.loadClass(name);
                if (clazz != null) {
                    if (resolve)
                        resolveClass(clazz);
                   return (clazz);
                }
            } catch (ClassNotFoundException e) {
                // Ignore
            }
        }
         
        return customLoad(name,this);
     }
 
     public Class load(String name) throws Exception{
         return loadClass(name);
     }
 
     /**
      * 自定义加载
      * @param name
      * @param cl 
      * @return
      * @throws ClassNotFoundException
      */
     public Class customLoad(String name,ClassLoader cl) throws ClassNotFoundException {
         return customLoad(name, false,cl);
     }
 
     /**
      * 自定义加载
      * @param name
      * @param resolve
     * @return
      * @throws ClassNotFoundException
      */
     public Class customLoad(String name, boolean resolve,ClassLoader cl) throws ClassNotFoundException {
         //findClass()调用的是URLClassLoader里面重载了ClassLoader的findClass()方法
         Class clazz = ((HotSwapURLClassLoader)cl).findClass(name);
         if (resolve)
             ((HotSwapURLClassLoader)cl).resolveClass(clazz);
         //缓存加载class文件的最后修改时间
         long lastModifyTime = getClassLastModifyTime(name);
         cacheLastModifyTimeMap.put(name,lastModifyTime);
         return clazz;
     }
     
     public Class<?> loadClass(String name) throws ClassNotFoundException {
         return loadClass(name,false);
     }

     @Override
     protected Class<?> findClass(String name) throws ClassNotFoundException {
         // TODO Auto-generated method stub
        return super.findClass(name);
     }
    
     /**
      * @param name
      * @return .class文件最新的修改时间
      */
     private long getClassLastModifyTime(String name){
         String path = getClassCompletePath(name);
         File file = new File(path);
         if(!file.exists()){
            throw new RuntimeException(new FileNotFoundException(name));
         }
         return file.lastModified();
     }
     
     /**
      * 判断这个文件跟上次比是否修改过
      * @param name
      * @return
      */
     private boolean isModify(String name){
        long lastmodify = getClassLastModifyTime(name);
        long previousModifyTime = cacheLastModifyTimeMap.get(name);
        if(lastmodify>previousModifyTime){
            return true;
       }
       return false;
     }
   
    /**
     * @param name
     * @return .class文件的完整路径 (e.g. E:/A.class)
     */
     private String getClassCompletePath(String name){
         String simpleName = name.substring(name.lastIndexOf(".")+1);
         return projectClassPath+packagePath+simpleName+".class";
     }    
 }
```

代码：Hot被用来修改的类

```java
package testjvm.testclassloader;
public class Hot {
    public void hot(){
        System.out.println(" version 1 : "+this.getClass().getClassLoader());
    }
}
```

代码：TestHotSwap测试类

```java
package testjvm.testclassloader;
import java.lang.reflect.Method;
public class TestHotSwap {
    public static void main(String[] args) throws Exception {
        //开启线程，如果class文件有修改，就热替换
        Thread t = new Thread(new MonitorHotSwap());
        t.start();
    }
}

public class MonitorHotSwap implements Runnable {
    // Hot就是用于修改，用来测试热加载
    private String className = "testjvm.testclassloader.Hot";
    private Class hotClazz = null;
    private HotSwapURLClassLoader hotSwapCL = null;
 
    @Override
    public void run() {
        try {
            while (true) {
                initLoad();
                Object hot = hotClazz.newInstance();
                Method m = hotClazz.getMethod("hot");
                m.invoke(hot, null); //打印出相关信息
                // 每隔10秒重新加载一次
                Thread.sleep(10000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 加载class
     */
    public void initLoad() throws Exception {
        hotSwapCL = HotSwapURLClassLoader.getClassLoader();
        // 如果Hot类被修改了，那么会重新加载，hotClass也会返回新的
        hotClazz = hotSwapCL.loadClass(className);
    }
}
```

在测试类运行的时候，修改Hot.class文件 

```java
Hot.class
	原来第五行:System.out.println(" version 1 : "+this.getClass().getClassLoader());
	改后第五行:System.out.println(" version 2 : "+this.getClass().getClassLoader());
```

输出：

```java
输出
	version 1 : testjvm.testclassloader.HotSwapURLClassLoader@610f7612
	version 1 : testjvm.testclassloader.HotSwapURLClassLoader@610f7612
	version 2 : testjvm.testclassloader.HotSwapURLClassLoader@45e4d960
	version 2 : testjvm.testclassloader.HotSwapURLClassLoader@45e4d960
```

所以HotSwapURLClassLoader是重加载了Hot类 。注意上面，其实当加载修改后的Hot时，HotSwapURLClassLoader实例跟加载没修改Hot的HotSwapURLClassLoader不是同一个。

![HotSwapURLClassLoader加载一](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/HotSwapURLClassLoader%E5%8A%A0%E8%BD%BD%E4%B8%80.jpg)

总结：上述类热加载，需要自定义ClassLoader，并且只能重新实例化ClassLoader实例，利用新的ClassLoader实例才能重新加载之前被加载过的class。并且程序需要模块化，才能利用这种热加载方式。

二、Class卸载

在Java中class也是可以unload。JVM中class和Meta信息存放在```PermGen space```区域。如果加载的class文件很多，那么可能导致PermGen space区域空间溢出。引起：```java.lang.OutOfMemoryErrorPermGen space```.  对于有些Class我们可能只需要使用一次，就不再需要了，也可能我们修改了class文件，我们需要重新加载 newclass，那么oldclass就不再需要了。那么JVM怎么样才能卸载Class呢。

JVM中的Class只有满足以下三个条件，才能被GC回收，也就是该Class被卸载（unload）：

* 该类所有的实例都已经被GC。

* 加载该类的ClassLoader实例已经被GC。

* 该类的java.lang.Class对象没有在任何地方被引用。

GC的时机我们是不可控的，那么同样的我们对于Class的卸载也是不可控的。 

代码：SimpleURLClassLoader，一个简单的自定义classloader

```java
package testjvm.testclassloader;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
 
public class SimpleURLClassLoader extends URLClassLoader {
    //工程class类所在的路径
    public static String projectClassPath = "E:/IDE/work_place/ZJob-Note/bin/";
    //所有的测试的类都在同一个包下
    public static String packagePath = "testjvm/testclassloader/";
     
    public SimpleURLClassLoader() {
        //设置ClassLoader加载的路径
        super(getMyURLs());
    }
     
    private static  URL[] getMyURLs(){
        URL url = null;
        try {
            url = new File(projectClassPath).toURI().toURL();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return new URL[] { url };
    }
 
    public Class load(String name) throws Exception{
        return loadClass(name);
    }
 
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name,false);
    }
     
    /**
     * 重写loadClass，不采用双亲委托机制("java."开头的类还是会由系统默认ClassLoader加载)
     */
    @Override
    public Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
        Class clazz = null;
        //查看HotSwapURLClassLoader实例缓存下，是否已经加载过class
        clazz = findLoadedClass(name);
        if (clazz != null ) {
            if (resolve) {
                resolveClass(clazz);
            }
            return (clazz);
        }
 
        //如果类的包名为"java."开始，则有系统默认加载器AppClassLoader加载
        if(name.startsWith("java.")) {
            try {
                //得到系统默认的加载cl，即AppClassLoader
                ClassLoader system = ClassLoader.getSystemClassLoader();
                clazz = system.loadClass(name);
                if (clazz != null) {
                    if (resolve)
                        resolveClass(clazz);
                    return (clazz);
                }
            } catch (ClassNotFoundException e) {
                // Ignore
            }
        }      
        return customLoad(name,this);
    } 
    /**
     * 自定义加载
     * @param name
     * @param cl 
     * @return
     * @throws ClassNotFoundException
     */
    public Class customLoad(String name,ClassLoader cl) throws ClassNotFoundException {
        return customLoad(name, false,cl);
    }
    
    /**
     * 自定义加载
     * @param name
     * @param resolve
     * @return
     * @throws ClassNotFoundException
     */
    public Class customLoad(String name, boolean resolve,ClassLoader cl) throws ClassNotFoundException {
        //findClass()调用的是URLClassLoader里面重载了ClassLoader的findClass()方法
        Class clazz = ((SimpleURLClassLoader)cl).findClass(name);
        if (resolve)
            ((SimpleURLClassLoader)cl).resolveClass(clazz);
        return clazz;
    }
 
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return super.findClass(name);
    }
}
```

代码：A

```java
public class A {
    //  public static final Level CUSTOMLEVEL = new Level("test", 550) {}; // 内部类
}
```

代码：TestClassUnload，测试类

```java
package testjvm.testclassloader;
public class TestClassUnLoad {
    public static void main(String[] args) throws Exception {
        SimpleURLClassLoader loader = new SimpleURLClassLoader();
        // 用自定义的加载器加载A
        Class clazzA = loader.load("testjvm.testclassloader.A");
        Object a = clazzA.newInstance();
        // 清除相关引用
        a = null;
        clazzA = null;
        loader = null;
        // 执行一次gc垃圾回收
        System.gc();
        System.out.println("GC over");
    }
}
```

运行的时候配置VM参数: ```-verbose:class```；用于查看class的加载与卸载情况。如果用的是Eclipse，在Run Configurations中配置此参数即可。

![Run Configuration配置](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/RunConfigureation%E9%85%8D%E7%BD%AE.jpg)

输出结果：

```java
[Loaded java.net.URI$Parser from E:\java\jdk1.7.0_03\jre\lib\rt.jar]
[Loaded testjvm.testclassloader.A from file:/E:/IDE/work_place/ZJob-Note/bin/]
[Unloading class testjvm.testclassloader.A]
GC over
[Loaded sun.misc.Cleaner from E:\java\jdk1.7.0_03\jre\lib\rt.jar]
[Loaded java.lang.Shutdown from E:\java\jdk1.7.0_03\jre\lib\rt.jar]
```

上面输出结果中的确A.class被加载了，然后A.class又被卸载了。这个例子中说明了，即便是class加载进了内存，也是可以被释放的。

程序运行中，引用没清楚前，内存中情况：

![class_unload_1](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/class_unload_1.png)

垃圾回收后，程序没结束前，内存中情况：

![class_unload_2](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/class_unload_2.png)

1. 由启动类加载器加载的类型在整个运行期间是不可能被卸载的(jvm和jls规范).

2. 被系统类加载器和标准扩展类加载器加载的类型在运行期间不太可能被卸载，因为系统类加载器实例或者标准扩展类的实例基本上在整个运行期间总能直接或者间接的访问的到，其达到unreachable的可能性极小.(当然，在虚拟机快退出的时候可以，因为不管ClassLoader实例或者Class(java.lang.Class)实例也都是在堆中存在，同样遵循垃圾收集的规则).

3. 被开发者自定义的类加载器实例加载的类型只有在很简单的上下文环境中才能被卸载，而且一般还要借助于强制调用虚拟机的垃圾收集功能才可以做到.可以预想，稍微复杂点的应用场景中(尤其很多时候，用户在开发自定义类加载器实例的时候采用缓存的策略以提高系统性能)，被加载的类型在运行期间也是几乎不太可能被卸载的(至少卸载的时间是不确定的).

综合以上三点， 一个已经加载的类型被卸载的几率很小至少被卸载的时间是不确定的.同时，我们可以看的出来，开发者在开发代码时候，不应该对虚拟机的类型卸载做任何假设的前提下来实现系统中的特定功能.
