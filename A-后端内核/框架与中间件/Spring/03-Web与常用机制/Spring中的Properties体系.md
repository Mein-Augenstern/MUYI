Spring 中的 Properties 体系
====

一句话定位：Spring 里 Properties 的"用法三种 + 配置四种 + Boot 一套"全景图，外加一条最容易被问住的暗线——为什么 Environment 不是总能取到配置值；面试考的不是背诵，而是对新旧 API（PropertyPlaceholderConfigurer → PropertySourcesPlaceholderConfigurer → Boot 体系）演进关系的判断。

1 使用 Properties 的三种方式
------

**方式一：XML 里 `${}` 占位符自动替换**：

```xml
<bean id="xxx" class="com.javadoop.Xxx">
    <property name="url" value="${javadoop.jdbc.url}" />
</bean>
```

**方式二：@Value 注入**：

```java
@Value("${javadoop.jdbc.url}")
private String url;
```

**方式三：注入 Environment 取值**：

```java
@Autowired
private Environment env;

public void foo() {
    env.getProperty("javadoop.jdbc.url");
}
```

关键 caveat（原文作者亲测）：Environment 方式**只有配置来自 @PropertySource 时可用**（Spring Boot 的 application.properties 注册的也可以），用 `<context:property-placeholder/>` 配的属性这样取会得到 null——原因见第 4 节，这是本篇最有面试价值的细节。

2 配置 Properties 的四种方式
------

**方式一：XML 一行流（最常用）**：

```xml
<context:property-placeholder location="classpath:sys.properties" />
```

**方式二：@PropertySource（必须搭配 @Configuration 使用）**：

```java
@PropertySource("classpath:sys.properties")
@Configuration
public class JavaDoopConfig {
}
```

**方式三：PropertyPlaceholderConfigurer**（Spring 3.1 之前的主流，位于 org.springframework.beans.factory.config 包）：

```java
@Bean
public PropertyPlaceholderConfigurer propertyPlaceholderConfigurer() {
    PropertyPlaceholderConfigurer configurer = new PropertyPlaceholderConfigurer();
    configurer.setLocations(new ClassPathResource[]{new ClassPathResource("sys.properties")});
    configurer.setIgnoreUnresolvablePlaceholders(true);
    return configurer;
}
```

**方式四：PropertySourcesPlaceholderConfigurer**（Spring 3.1 引入，位于 spring-context 包，名字多一个 "Sources"），配置方式与方式三完全相同，只换类名。

分水岭判断：**3.1 之前用 PropertyPlaceholderConfigurer，之后一律 PropertySourcesPlaceholderConfigurer**——后者对接 Environment 的 PropertySources 体系（解析占位符时会连容器环境属性一起查），前者已被归入不推荐。实际上 3.1+ 的 `<context:property-placeholder/>` 底层注册的就是 PropertySourcesPlaceholderConfigurer。

3 Spring Boot 的 application.properties 体系
------

* **application.properties 自动注册**，无需任何配置；改配置文件位置/名字：`java -Dspring.config.location=classpath:sys.properties -jar app.jar`；
* **多环境**：application-{env}.properties（如 application-dev.properties / application-prd.properties），启动时 `java -Dspring.profiles.active=prd -jar app.jar` 激活。两份文件里的配置**都会注册**，重复 key 时环境文件优先级更高；
* **覆盖顺序**：启动参数（-D 或命令行）> application-{env}.properties > application.properties；此外还支持系统环境变量、随机数注入等来源，整体构成一条优先级链；
* **@ConfigurationProperties：类型安全绑定**（Boot 特有，零散 @Value 的升级版）：

```java
@Configuration
@ConfigurationProperties(prefix = "javadoop.database")
public class DataBase {
    private String url;
    private String username;
    private String password;
    // getter/setter 省略
}
```

配合 `javadoop.database.url/username/password` 配置项，Boot 自动注册一个绑好属性的 bean——结构化配置批量绑定、类型校验、松散规则匹配（驼峰/中划线/下划线互通）都比 @Value 强。

版本注脚：Boot 2.4 起配置文件加载机制重构，新增 `spring.config.import` 统一导入，`spring.config.location` 语义收紧为整组替换；Boot 3 沿用这套规则，升级视角全貌见 [SpringBoot3升级要点-Jakarta-AOT-Native](./SpringBoot3升级要点-Jakarta-AOT-Native.md)。

4 暗线：为什么 Environment 不是总能取到值
------

把第 1 节的 caveat 讲透，答案是两类配置器的职责不同：

* `<context:property-placeholder/>` / PropertySourcesPlaceholderConfigurer 的定位是**占位符解析器**（BeanFactoryPostProcessor 阶段工作）：把本地 properties 拿来解析 `${}`，但**不会把本地属性注册进 Environment**；
* @PropertySource 的语义则是**直接往 Environment 添加一个 PropertySource**（prepareRefresh 之后、Bean 实例化之前就位），所以 env.getProperty() 能取到；Boot 的 application.properties 同理走 Environment 的 PropertySources 体系。

于是 Environment 的正确心智模型：**一组有序的 PropertySource（SystemProperties、SystemEnvironment、各 @PropertySource、Boot 的 application.properties……）+ Profile 决策 + 类型转换**。原文把这定位为"理解 Properties 体系的深水区入口"——想再往下走，就是去读 Environment/ConfigurableEnvironment 与 AbstractEnvironment 的源码（StandardEnvironment 里 systemProperties/systemEnvironment 两个 PropertySource 的注册逻辑）。

面试追问链
------

* @Value("${x}") 的解析靠什么？→ 占位符配置器注册的 embeddedValueResolver，@Value 注入时请求它完成解析；
* PropertyPlaceholderConfigurer 和 PropertySourcesPlaceholderConfigurer 差在哪？→ 3.1 分界：后者对接 Environment 的 PropertySources 体系，支持环境属性联动与 @Value，前者不推荐；
* 为什么 XML 里 property-placeholder 配的属性 env.getProperty() 取不到？→ 它只做占位符解析，不往 Environment 写 PropertySource；@PropertySource 才写；
* application-{env}.properties 和 application.properties 谁赢？→ 环境文件优先；命令行/启动参数更高；
* @ConfigurationProperties 和 @Value 怎么选？→ 结构化、批量、要类型安全和松散绑定用前者；零散一两个值用后者；
* Boot 里换配置文件名怎么做？→ spring.config.location（注意 2.4+ 是整组替换语义，追加场景用 spring.config.import）；
* 多个 PropertySource 里有同名 key 听谁的？→ 按注册顺序遍历，先命中先返回——顺序就是优先级；
* 环境变量/系统属性没配过也能被 Environment 读到吗？→ 能，StandardEnvironment 默认就注册了 systemProperties 与 systemEnvironment 两个 PropertySource；
* 外部化配置除了文件还有哪些来源？→ 命令行参数、系统环境变量、JNDI 等，Boot 侧还有专门的 Externalized Configuration 优先级文档可查。

面试官视角小结
------

Properties 题看似背诵题，实际分三层筛人：第一层背得出三种用法、四种配置；第二层讲得清 3.1 前后的类名演进与 Boot 优先级链（命令行 > 环境文件 > 默认文件），说明真用过；第三层知道"哪些配置进 Environment、哪些只是占位符解析"这条暗线——这一层能主动讲出来的人极少，一句话就是区分度。再往下就是 Environment 源码的深水区，属于愿意主动跳进去的人的加分项，但别在面试里恋战，点到 PropertySource 有序集合即收。

> 来源：javadoop《Spring 中无处不在的 Properties》（https://javadoop.com/post/spring-properties）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
