Spring中常用注解
====

看在前面
------

> * 原文：<a href="https://www.cnblogs.com/XiOrang/p/9337994.html">spring(二、bean生命周期、用到的设计模式、常用注解)</a>（博客园 XiOrang）

* 原文还覆盖 bean 生命周期与九种设计模式。库内已有专文《谈谈Spring中Bean的生命周期》《谈谈Spring中常用设计模式》。本篇聚焦**常用注解**部分。

一、组件类注解
------

* `@Component`：标注一个普通的 Spring Bean 类。

* `@Repository`：标注 DAO 组件类。

* `@Service`：标注业务逻辑组件类。

* `@Controller`：标注控制器组件类。

* 四者实质同类、用法相同，区别只是标识的组件类型。`@Component` 可替代其他三个（后三者本身被 `@Component` 标注）。Bean 实例名默认为类名首字母小写。

* 使用时需配置扫描：`<context:component-scan base-package="..."/>`。详见库内《@Repository和@Componet和@Service和@Controller之间区别和联系》。

二、装配 Bean 注解
------

* `@Autowired`：Spring 的注解，可用于属性、构造器、方法注值。**默认按类型（byType）注入**，依赖对象必须存在，可用 `required = false` 改变默认行为。同一类型有多个 Bean 时需结合 `@Qualifier` 限定 bean 名称。

* `@Resource`：来自 JSR-250（`java.annotation` 包），**默认按名称（byName）注入**，也可指定按类型。有 name 和 type 两个重要属性。未指定 name 且按默认名称找不到依赖时，会回退到按类型装配。一旦指定 name 就只能按名称装配。使用上比 `@Autowired` 灵活、抛异常更少。更细对比见库内《@Autowired和@Resource区别》。

* `@PostConstruct` / `@PreDestroy`：实现 Bean 初始化前和销毁前的操作，对应 init-method / destroy-method 的注解化写法。

三、@Configuration 与 @Bean
------

* `@Configuration`：标注类可作为 bean 定义资源供 IoC 容器使用。

* `@Bean`：标注在方法上，类似工厂方法；可配合 `@Qualifier` 定义工厂方法名、`@Scope` 定义作用域（singleton / prototype 等）。

* **`@Component` 与 `@Configuration` 的区别（高频追问）**：`@Configuration` 下的 `@Bean` 方法会被 CGLIB 包装。首次调用执行方法体并在容器中注册，后续调用返回容器中的同一个 bean。而 `@Component` 下的 `@Bean` 方法之间调用是纯 Java 方法调用，会创建容器外的新实例（不归 Spring 管理）。需通过 `@Autowired` 注入来修正。

四、Spring MVC 注解
------

* `@Controller`：标明类为与前端交互的控制层组件，支持自动检测和注册。

* `@RequestMapping`：将 URL 映射到处理类或方法。可作用于类级别和方法级别，支持通配符。可用 `method` 属性限定请求类型（如 `RequestMethod.GET`）。

* `@RequestParam`：将请求参数绑定到方法参数；有 required 属性，默认为 true（必须传），可设为 false。

* `@PathVariable`：修饰方法参数，将其绑定为 URI 模板变量（RESTful 动态绑定）。支持任意简单类型（如 int、long、Date）自动转换，支持正则表达式。

* `@RequestBody`：将方法参数绑定到 HTTP 请求 Body，可绑定自定义对象类型（反序列化）。

* `@ResponseBody`：将返回类型直接写入 HTTP response body，常用于输出 JSON。

* `@RestController`：`@Controller` + `@ResponseBody` 的组合注解，创建 REST 型控制器，只服务于 JSON、XML 这类内容，避免逐个方法写 `@ResponseBody`。

* `@ModelAttribute`：作用于方法或方法参数；作用于方法时表示目的是添加模型属性，**会在 @RequestMapping 方法之前调用**。

五、事务注解 @Transactional
------

* 声明式事务，需在配置中激活：`<tx:annotation-driven transaction-manager="transactionManager"/>`（SpringBoot 自动装配）。

* 常用属性：

    * `propagation`：传播行为；

    * `readOnly`：只读属性，默认 false；

    * `rollbackFor`：指定哪些异常触发回滚；

    * `timeout`：超时秒数；

    * `isolation`：隔离级别。

* **七种传播行为**：REQUIRED（有则加入、无则新建，最常见）、SUPPORTS、MANDATORY、REQUIRES_NEW（新建事务并挂起当前事务）、NOT_SUPPORTED、NEVER、NESTED（嵌套事务）。

* **五种隔离级别**：DEFAULT、READ_UNCOMMITTED（会脏读）、READ_COMMITTED（防脏读）、REPEATABLE_READ、SERIALIZABLE（资源消耗大一般不用）。更多事务考点见库内《Spring 事务面试考点》。

六、一图速记
------

| 分层 | 注解 |
| --- | --- |
| 声明组件 | @Component / @Repository / @Service / @Controller / @RestController |
| 装配注入 | @Autowired / @Qualifier / @Resource / @Value |
| 配置注册 | @Configuration / @Bean / @Scope / @PostConstruct / @PreDestroy |
| Web 交互 | @RequestMapping / @RequestParam / @PathVariable / @RequestBody / @ResponseBody / @ModelAttribute |
| 事务 | @Transactional / @EnableTransactionManagement |
| 异步启用 | @EnableAsync / @Async |

> 内容来源：[原文](https://www.cnblogs.com/XiOrang/p/9337994.html) · 2026-10 整理回填。
