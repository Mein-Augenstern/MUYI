看在前面
====

> * <a href="https://github.com/DemoTransfer/Java-Guide/blob/master/java/interview/JVM/JVM%E5%86%85%E5%AD%98%E7%AE%A1%E7%90%86/%E5%85%B3%E4%BA%8Eclass%E5%8D%B8%E8%BD%BD%E3%80%81%E7%83%AD%E6%9B%BF%E6%8D%A2%E6%98%AF%E4%BB%80%E4%B9%88.md">关于class卸载、热替换是什么</a>

Tomcat中与其说有热加载，还不如说是热部署来的准确些。因为对于一个应用，其中class文件被修改过，那么Tomcat会先卸载这个应用(Context)，然后重新加载这个应用，其中关键就在于自定义ClassLoader的应用。

Tomcat启动的时候，ClassLoader加载的流程
------

1. Tomcat启动的时候，用```system classloader```即```AppClassLoader```加载```{catalina.home}/bin```里面的jar包，也就是tomcat启动相关的jar包

2. Tomcat启动类Bootstrap中有3个classloader属性，```catalinaLoader、commonLoader、sharedLoader```在Tomcat7中默认他们初始化都为同一个```StandardClassLoader```实例。具体的也可以在```{catalina.home}/bin/bootstrap.jar```包中的```catalina.properites```中进行配置。

3. ```StandardClassLoader```加载```{catalina.home}/lib```下面的所有Tomcat用到的jar包。

4. 一个Context容器，代表了一个app应用。```Context-->WebappLoader-->WebClassLoader```。并且```Thread.contextClassLoader=WebClassLoader```。应用程序中的jsp文件、class类、```lib/*.jar```包，都是```WebClassLoader```加载的。

Tomcat加载资源的概况图
------

![Tomcat加载资源的概况图](https://github.com/DemoTransfer/Java-Guide/blob/master/java/web%20server/tomcat/picture/loadprocess.jpg)

当Jsp文件修改的时候，Tomcat更新步骤
------

1. 但访问1.jsp的时候，1.jsp的包装类JspServletWrapper会去比较1.jsp文件最新修改时间和上次的修改时间，以此判断1.jsp是否修改过。

2. 1.jsp修改过的话，那么jspservletWrapper会清除相关引用，包括1.jsp编译后的servlet实例和加载这个servlet的JasperLoader实例。

3. 重新创建一个JasperLoader实例，重新加载修改过后的1.jsp，重新生成一个Servlet实例。

4. 返回修改后的1.jsp内容给用户。

![Jsp清除引用和资源](https://github.com/DemoTransfer/Java-Guide/blob/master/java/web%20server/tomcat/picture/jsp_unload.jpg)

当app下面的class文件修改的时候，Tomcat更新步骤
------

1. Context容器会有专门线程监控app下面的类的修改情况。

2. 如果发现有类被修改了。那么调用Context.reload()。清除一系列相关的引用和资源。

3. 然后创新创建一个WebClassLoader实例，重新加载app下面需要的class。

![Context清除引用和资源 ](https://github.com/DemoTransfer/Java-Guide/blob/master/java/web%20server/tomcat/picture/servlet_unload.jpg)

在一个有一定规模的应用中，如果文件修改多次，重启多次的话，java.lang.OutOfMemoryErrorPermGen space这个错误的的出现非常频繁。主要就是因为每次重启重新加载大量的class，超过了PermGen space设置的大小。

两种情况可能导致PermGen space溢出：

1. GC(Garbage Collection)在主程序运行期对PermGen space没有进行清理(GC的不可控行)

2. 重启之前WebClassLoader加载的class在别的地方还存在着引用。










