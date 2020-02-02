看在前面
====

> * <a href="https://www.cnblogs.com/XiOrang/p/9337994.html">Spring(二、bean生命周期、用到的设计模式、常用注解)</a>

------

一、简单工厂
====

简单工厂叫做静态工厂方法（StaticFactory Method）模式，但不属于23种GOF设计模式之一。 
简单工厂模式的实质是由一个工厂类根据传入的参数，动态决定应该创建哪一个产品类。 
spring中的BeanFactory就是简单工厂模式的体现，根据传入一个唯一的标识来获得bean对象，但是否是在传入参数后创建还是传入参数前创建这个要根据具体情况来定。如下配置，就是在 HelloItxxz 类中创建一个 itxxzBean。
