看在前面
====

> * <a href="https://blog.csdn.net/qq_34651407/article/details/85015947">简单介绍下servlet的生命周期，servlet是否会多次初始化？</a>

一句话答案
====

Servlet的生命周期由Web容器来进行管理：

1. 实例化，一个Servlet类只会创建一个实例，当第一个用户第一次访问Servlet时，创建对象保存在web容器中；

2. 初始化，调用init方法，对Servlet进行初始化，只执行一次

3. 服务，调用service方法，用户每访问一次Servlet就调用一次

4. 销毁，调用destroy方法，web容器关闭

5. 最后，Servlet 是由 JVM 的垃圾回收器进行垃圾回收的。

init 方法被设计成只调用一次。它在第一次创建 Servlet 时被调用，在后续每次用户请求时不再调用。因此servlet不会多次初始化
