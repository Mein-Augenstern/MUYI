看在前面
====

> * <a href="https://blog.csdn.net/u012556994/article/details/81353002">SpringBoot与Spring的区别</a>

一句话答案
====

SpringBoot不是Spring官方的框架模式，而是一个团队在Spring4.0版本上二次开发并开源公布出来的。简而言之，SpringBoot就是一个轻量级，简化配置和开发流程的web整合框架。SpringBoot是最近这几年才火起来的。它与Spring的区别，就是SpringBoot提供的特性：

1. Spring Boot可以建立独立的Spring应用程序；

2. 内嵌了如Tomcat，Jetty和Undertow这样的容器。即可以直接跑起来，用不着再做部署工作了；

3. 无需再像Spring那样搞一堆繁琐的xml文件的配置；

4. 可以自动配置Spring。SpringBoot将原有的XML配置改为Java配置。将bean注入改为使用注解注入的方式(@Autowire)。并将多个xml、properties配置浓缩在一个appliaction.yml配置文件中。

5. 提供了现有的功能。如量度工具、表单数据验证以及外部配置这样的第三方功能；

6. 整合常用依赖（开发库，例如spring-webmvc、jackson-json、validation-api和tomcat等），提供的POM可以简化Maven的配置。当我们引入核心依赖时，SpringBoot会自引入其他依赖。

一、先摆正关系：演进链条上的三层
------

SpringBoot 不是"取代" Spring，而是站在 Spring Framework 肩膀上的**脚手架**，三者是演进与被演进的关系：

```
Spring Framework（地基：IoC 容器 + AOP + 事务 + MVC）
        │  解决"对象怎么创建、依赖怎么组装"
        ▼
Spring Boot（脚手架：自动配置 + starter + 内嵌容器）
        │  解决"配置太多太繁琐、部署太重"
        ▼
Spring Cloud（方案集：注册发现、配置中心、熔断限流……）
           解决"服务拆了之后怎么协作"（大部分组件基于 Boot 构建）
```

* Spring Framework 提供的是**能力**（IoC/AOP 这类编程模型），Spring Boot 提供的是**开箱即用的工程体验**。没有 Spring 的容器，Boot 什么也不是。Boot 里写的 @Service/@Autowired/@Transactional 全部是 Spring 的注解。

二、自动配置到底"自动"了什么
------

传统 Spring 时代（SSM），接入一个功能要自己写一串 XML/Java 配置；Boot 的思路是"**约定优于配置 + 条件化装配**"：

| 差异点 | 传统 Spring | Spring Boot |
| ---- | ---- | ---- |
| 依赖管理 | 自己挑版本，容易冲突 | starter 按功能聚合，版本由 parent BOM 统一管 |
| DataSource/事务 | 手写数据源、事务管理器 Bean | classpath 有 DataSource 相关类就自动装配 |
| MVC | 配 DispatcherServlet、视图解析器 | @EnableAutoConfiguration 一并搞定 |
| Web 容器 | 打 war 部署到外部 Tomcat | 内嵌 Tomcat，jar 包直接 java -jar |
| 配置文件 | 多个 xml/properties | 一个 application.yml 汇总 + profile 分环境 |

* 核心入口是 @SpringBootApplication = @SpringBootConfiguration + @EnableAutoConfiguration + @ComponentScan；

* @EnableAutoConfiguration 通过加载 `META-INF/spring.factories`（Boot 2.7 起迁移为 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）里的自动配置类清单。再由 @ConditionalOnClass、@ConditionalOnMissingBean、@ConditionalOnProperty 这类**条件注解**逐个裁决：类路径里有啥、用户配了啥，就装配啥；

* 用户自己声明的 Bean 优先——@ConditionalOnMissingBean 保证"你配了就用你的，没配才给默认的"，这就是覆盖自动配置的原理。

三、内嵌容器与 starter：start 与容器两码事
------

* **starter 是依赖打包艺术**：spring-boot-starter-web 一个坐标带出 webmvc + 内嵌 tomcat + jackson + validation，解决的是"Maven 依赖怎么凑齐"；

* **内嵌容器是运行方式变化**：Boot 应用是 main 方法启动、内嵌 Tomcat/Jetty/Undertow 随进程启动。应用从"war 部署进容器"变成"自带容器独立跑"。启动更快、更容器化友好（一个 jar 就是一个进程，天然契合 Docker）；

* 注意区分：`@SpringBootApplication`（应用启动注解）与"内嵌容器"没有绑定关系——换 Undertow 只需要排除 tomcat starter 换一个坐标；

* Servlet 容器模型本身没变，变的只是"谁来启动容器"。参见 [简单介绍下servlet的生命周期，servlet是否会多次初始化？](Servlet 生命周期与初始化.md)。

四、互补而非替代（面试标准收尾）
------

* Boot 依赖 Spring：Boot 的自动配置类最终也是在**拼装 Spring 容器里的 Bean**，容器、注解、生命周期管理全部沿用 Spring Framework；

* Spring 也吸收 Boot：官方后来把 Boot 收编为主推方式，Boot 3.x 已基于 Spring Framework 6，升级要点见 [SpringBoot3升级要点-Jakarta-AOT-Native](SpringBoot3升级要点-Jakarta-AOT-Native.md)；

* 类比记忆：Spring 是发动机，Spring Boot 是整装好、一键点火的整车。Spring Cloud 是车队调度系统——你不会说"整车取代了发动机"。

一句话总结：**Spring Boot = Spring Framework + 自动配置 + starter 依赖管理 + 内嵌容器**。它解决的是配置与部署的效率问题，内核仍是 Spring。

延伸阅读：[Spring中IoC和AOP概念介绍](Spring中IoC和AOP概念介绍.md)、[Spring中BeanFactory和ApplicationContext有什么区别](Spring中BeanFactory与ApplicationContext区别.md)、[谈谈Spring框架的依赖注入（DI）](Spring依赖注入DI.md)。

> 2026-10 扩充（Round1 查漏）。
