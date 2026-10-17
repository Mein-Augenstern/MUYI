SpringBoot 速查卡
====

一句话定位
----

* SpringBoot ≠ 新框架，是 Spring 的**快速开发脚手架**：约定优于配置 + 自动配置 + starter 依赖管理 + 内嵌容器，达到"开箱即用、java -jar 直跑"。

@SpringBootApplication 三件套（必背）
----

| 组合注解 | 本质 | 作用 |
|---|---|---|
| @SpringBootConfiguration | 就是 @Configuration | 声明配置类，注册 Bean |
| @ComponentScan | 包扫描 | 默认扫**主类所在包及子包**（所以主类要放根包） |
| @EnableAutoConfiguration | @Import(AutoConfigurationImportSelector) | 开启自动配置，入口中的入口 |

自动配置原理（标准答题线）
----

1. `@EnableAutoConfiguration` 通过 `@Import(AutoConfigurationImportSelector)` 导入候选配置类。
2. `AutoConfigurationImportSelector` 读取候选清单：
    * Boot 2.7 前：`META-INF/spring.factories` 中 `EnableAutoConfiguration` 键下全量类名。
    * Boot 2.7+/3.x：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（一行一个类，避免 fatories 臃肿）。
3. 每个自动配置类（如 `DataSourceAutoConfiguration`）用**条件注解**过滤，全部满足才生效：

| 条件注解 | 生效条件 |
|---|---|
| @ConditionalOnClass | classpath 有指定类（依赖进了才配） |
| @ConditionalOnMissingBean | 容器没有用户自定义 Bean（用户配置优先） |
| @ConditionalOnProperty | 配置项匹配（prefix + havingValue） |
| @ConditionalOnWebApplication | 是 Servlet/WebFlux 环境 |
| @ConditionalOnBean | 容器已有某 Bean |

4. 生效的配置类通过 `@EnableConfigurationProperties(XxxProperties.class)` 绑定 `application.yml` 前缀 → 属性类 → 注入组件。
5. 一句话总结：**导入 selector → 读清单 → 条件装配 → 属性绑定**；排错用 `--debug` 打印 Conditions Evaluation Report。

starter 机制
----

* starter = **依赖聚合 + 自动配置的"功能套餐"**：

```
spring-boot-starter-web
 ├─ spring-web / spring-webmvc      （真依赖）
 ├─ spring-boot-starter-tomcat      （内嵌容器）
 └─ spring-boot-starter-json        （Jackson）
        ↓ 触发
 spring-boot-autoconfigure 里的相关 @Configuration（条件装配）
```

* 命名约定：官方 `spring-boot-starter-*`；第三方 `xxx-spring-boot-starter`。
* 版本管理：父 POM `spring-boot-starter-parent` → `spring-boot-dependencies` BOM 统一控版本，业务方不写版本号。
* 自定义 starter 三步：
    1. 写 `XxxAutoConfiguration` + 条件注解 + `XxxProperties`（@ConfigurationProperties）。
    2. 在 `AutoConfiguration.imports`（或旧版 spring.factories）登记。
    3. 打包发布，引入即生效；加 `spring-boot-configuration-processor` 生成 yml 提示。

启动流程（SpringApplication.run 简版）
----

* 推断应用类型（SERVLET/REACTIVE/NONE）→ 加载 ApplicationContextInitializer/Listener（也是从 spring.factories）→ 准备 Environment（配 application.yml）→ 创建并 refresh ApplicationContext（含自动配置）→ 回调 `ApplicationRunner/CommandLineRunner` → 发布 ready 事件。

SpringBoot vs Spring 差异表
----

| 维度 | Spring | SpringBoot |
|---|---|---|
| 定位 | IoC/AOP 基础框架 | 快速构建脚手架（仍是 Spring 生态） |
| 配置 | XML/JavaConfig 手动配 MVC、数据源等 | 自动配置 + 约定优于配置，yml 只改差异项 |
| 依赖管理 | 手动挑版本，易冲突 | parent BOM + starter 一键引入、版本托管 |
| 部署 | 通常 war + 外部 Tomcat | 内嵌 Tomcat/Jetty/Undertow，jar 直接 `java -jar` |
| 运维 | 无内置 | Actuator：健康检查/指标/环境信息 |
| 起一个 Web 服务 | 依赖+web.xml/初始化器+一堆配置 | 引一个 starter-web 即可跑 |

高频追问速答
----

* **自动配置如何被用户覆盖**：@ConditionalOnMissingBean 让用户 Bean 优先；也可 `@SpringBootApplication(exclude = XxxAutoConfiguration.class)` 或配置 `spring.autoconfigure.exclude`。
* **换容器**：排除 `spring-boot-starter-tomcat`，引入 `starter-jetty/undertow`。
* **run 方法做了什么 vs @SpringBootApplication**：注解只是配置元数据，真正驱动在 `SpringApplication.run()`（环境准备、context 创建刷新、runner 回调）。
* **配置优先级**：命令行 > application-{profile}.yml > application.yml（高覆盖低）。

> 2026-10 依据主题重写（原文件为空壳）。
