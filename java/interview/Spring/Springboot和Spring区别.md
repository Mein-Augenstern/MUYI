看在前面
====

> * <a href="https://blog.csdn.net/u012556994/article/details/81353002">SpringBoot与Spring的区别</a>

一句话答案
====

SpringBoot不是Spring官方的框架模式，而是一个团队在Spring4.0版本上二次开发并开源公布出来的。简而言之，SpringBoot就是一个轻量级，简化配置和开发流程的web整合框架。SpringBoot是最近这几年才火起来的，那么它到底与Spring有啥区别呢？想了解区别，其实就是SpringBoot提供了哪些特性：

1. Spring Boot可以建立独立的Spring应用程序；

2. 内嵌了如Tomcat，Jetty和Undertow这样的容器，也就是说可以直接跑起来，用不着再做部署工作了；

3. 无需再像Spring那样搞一堆繁琐的xml文件的配置；

4. 可以自动配置Spring。SpringBoot将原有的XML配置改为Java配置，将bean注入改为使用注解注入的方式(@Autowire)，并将多个xml、properties配置浓缩在一个appliaction.yml配置文件中。

5. 提供了一些现有的功能，如量度工具，表单数据验证以及一些外部配置这样的一些第三方功能；

6. 整合常用依赖（开发库，例如spring-webmvc、jackson-json、validation-api和tomcat等），提供的POM可以简化Maven的配置。当我们引入核心依赖时，SpringBoot会自引入其他依赖。
