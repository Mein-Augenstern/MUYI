看在前面
====

> * <a href="https://blog.csdn.net/AD_plus/article/details/97936209">面试题解答系列：什么是Spring beans?解释Spring支持的几种bean的作用域</a>

------

什么是Spring beans?
====

Spring bean 表示受到Spring管理的对象。具体说来，它是被Spring框架容器初始化、配置和管理的对象。Spring bean是在Spring的配置文件中定义（现在也可以通过annotation注解来定义），在Spring容器中初始化，然后注入到应用程序中的。

因为在最早的版本中，Spring是被设计用来管理JavaBean的，所以Spring管理的对象会被称为“bean”。当然，现在Spring已经可以管理任何对象，即使它不具备默认构造器和设置方法（getter和setter）这些JavaBean的特性。然而，”Spring bean“这个术语仍然被保存了下来。

Spring bean可以是POJO吗？当然可以，并且它通常就是。（即使它并不一定得是POJO，例如Spring可以用来处理重量级Java对象，比如EJB对象）。

当通过Spring容器创建一个Bean实例时，不仅可以完成Bean实例的实例化，还可以为Bean指定特定的作用域。

Spring支持如下5种作用域
====

* singleton：单例模式，在整个Spring IoC容器中，使用singleton定义的Bean将只有一个实例

* prototype：原型模式，每次通过容器的getBean方法获取prototype定义的Bean时，都将产生一个新的Bean实例

* request：对于每次HTTP请求，使用request定义的Bean都将产生一个新实例，即每次HTTP请求将会产生不同的Bean实例。只有在Web应用中使用Spring时，该作用域才有效

* session：session作用域，该属性仅用于HTTP Session，同一个Session共享一个Bean实例。不同Session使用不同的实例。同样只有在Web应用中使用Spring时，该作用域才有效

* globalsession：全局作用域，该属性仅用于HTTP Session，同session作用域不同的是，所有的Session共享一个Bean实例。典型情况下，仅在使用portlet context的时候有效。同样只有在Web应用中使用Spring时，该作用域才有效。global-session和Portlet应用相关。当你的应用部署在Portlet容器中工作时，它包含很多portlet。如果你想要声明让所有的portlet共用全局的存储变量的话，那么这全局变量需要存储在global-session中。全局作用域与Servlet中的session作用域效果相同。


其中比较常用的是singleton和prototype两种作用域。对于singleton作用域的Bean，每次请求该Bean都将获得相同的实例。容器负责跟踪Bean实例的状态，负责维护Bean实例的生命周期行为；如果一个Bean被设置成prototype作用域，程序每次请求该id的Bean，Spring都会新建一个Bean实例，然后返回给程序。在这种情况下，Spring容器仅仅使用new 关键字创建Bean实例，一旦创建成功，容器不在跟踪实例，也不会维护Bean实例的状态。

如果不指定Bean的作用域，Spring默认使用singleton作用域。Java在创建Java实例时，需要进行内存申请；销毁实例时，需要完成垃圾回收，这些工作都会导致系统开销的增加。因此，prototype作用域Bean的创建、销毁代价比较大。而singleton作用域的Bean实例一旦创建成功，可以重复使用。因此，除非必要，否则尽量避免将Bean被设置成prototype作用域。

