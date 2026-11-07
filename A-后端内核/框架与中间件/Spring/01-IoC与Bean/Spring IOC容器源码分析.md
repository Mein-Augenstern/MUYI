Spring IOC 容器源码分析
====

一句话定位：从 `new ClassPathXmlApplicationContext(...)` 到 `getBean()` 拿到可用对象，中间的容器创建、BeanDefinition 加载注册、单例预初始化三段主链路——Spring 源码面试的"主干道"，`refresh()` 的模板方法顺序是必考题。概念层入门见 [Spring中IoC和AOP概念介绍](./Spring中IoC和AOP概念介绍.md)，三级缓存与循环依赖专项见 [Spring循环依赖](./Spring循环依赖.md)。

1 ApplicationContext 与 BeanFactory 的真实关系
------

启动入口（以 Spring 4.3.x 源码 + XML 配置为例；只依赖 spring-context 即可，会自动带入 spring-core/beans/aop/expression）：

```java
ApplicationContext context = new ClassPathXmlApplicationContext("classpath:applicationfile.xml");
MessageService messageService = context.getBean(MessageService.class);
```

* 常用三种实现：ClassPathXmlApplicationContext（classpath 路径）、FileSystemXmlApplicationContext（文件系统路径）、AnnotationConfigApplicationContext（注解驱动，后来的大势所趋）；
* **ApplicationContext 本身就是一个 BeanFactory**，但更准确的理解是：它内部持有一个实例化好的 BeanFactory——DefaultListableBeanFactory，容器操作全部委托给它，是"组合优于继承"的典型落地；
* BeanFactory 接口体系的三个分支：
  * ListableBeanFactory：可按类型批量获取多个 Bean（顶层 BeanFactory 只能按名取单个）；
  * HierarchicalBeanFactory：支持父子容器层级；
  * AutowireCapableBeanFactory：支持自动装配——ApplicationContext 并不继承它，而是通过 getAutowireCapableBeanFactory() 组合获得；
* ConfigurableListableBeanFactory 融合上述三支，唯一实现类就是 DefaultListableBeanFactory，IOC 容器的"真身"。

2 refresh()：容器启动的模板方法（必背）
------

构造器先 setConfigLocations() 处理配置路径（按分号、逗号、空格、tab、换行切分），随后调用核心方法 refresh()——refresh 的语义是"容器可销毁重建"。AbstractApplicationContext#refresh() 是标准模板方法，全程 synchronized(startupShutdownMonitor)，子步骤顺序：

1. prepareRefresh()：记录启动时间、置 active/closed 标志、校验必填属性占位符；
2. **obtainFreshBeanFactory()：创建 BeanFactory，加载并注册全部 BeanDefinition**；
3. prepareBeanFactory(beanFactory)：设置类加载器、注册 ApplicationContextAwareProcessor 等；
4. postProcessBeanFactory(beanFactory)：留给子类的扩展点；
5. invokeBeanFactoryPostProcessors(beanFactory)：回调各 BFPP 的 postProcessBeanFactory()；
6. registerBeanPostProcessors(beanFactory)：注册 BeanPostProcessor（与 BFPP 是两类角色，见追问链）；
7. initMessageSource()：国际化；
8. initApplicationEventMulticaster()：初始化事件广播器；
9. onRefresh()：子类钩子（如 Web 容器在此启动内嵌 Server）；
10. registerListeners()：注册事件监听器；
11. **finishBeanFactoryInitialization(beanFactory)：初始化所有非懒加载单例 Bean**；
12. finishRefresh()：发布容器就绪事件。

异常兜底走 destroyBeans() + cancelRefresh(ex)。面试时能一口气报出顺序，并点破"第 2 步定容器里有什么、第 11 步才真正造 Bean"的两阶段结构，第一层筛选就过了。

3 obtainFreshBeanFactory：加载与注册 BeanDefinition
------

**创建容器。** refreshBeanFactory()（AbstractRefreshableApplicationContext）：已有 BeanFactory 先销毁；createBeanFactory() new 出 DefaultListableBeanFactory；customizeBeanFactory() 设置两个著名开关——allowBeanDefinitionOverriding（同文件重复 id 报错、跨文件默认允许覆盖）、allowCircularReferences（字段循环依赖默认允许，构造器循环依赖永远不行）；最后调 loadBeanDefinitions(beanFactory)。

**加载链路。** AbstractXmlApplicationContext 创建 XmlBeanDefinitionReader 并 setEnvironment/setResourceLoader/setEntityResolver，然后：loadBeanDefinitions(Resource...) → loadBeanDefinitions(EncodedResource)（ThreadLocal 检测循环 import）→ doLoadBeanDefinitions()（XML 解析为 Document）→ registerBeanDefinitions() → DefaultBeanDefinitionDocumentReader#doRegisterBeanDefinitions(root)。

**解析分派。** 具体解析交给 BeanDefinitionParserDelegate；解析前先做 profile 检查，不匹配则整段跳过。默认命名空间（http://www.springframework.org/schema/beans）只有四个标签：

* `<import/>`：引入其他配置文件；
* `<alias/>`：定义别名；
* `<bean/>`：核心，走 processBeanDefinition；
* `<beans/>`：嵌套时用 parent delegate 递归处理。

其余 `<context/>`、`<aop/>`、`<mvc/>` 等自定义标签走 parseCustomElement()，路由到 ContextNamespaceHandler、AopNamespaceHandler 等——Spring 命名空间扩展机制的入口就在这里。

**解析 `<bean/>`。** parseBeanDefinitionElement 提取 id 与 name（name 按逗号/分号/空格切分为 aliases）；无 id 则用第一个别名当 beanName；都没有就生成"全限定类名#0"，类名作别名。随后创建 AbstractBeanDefinition，依次解析 meta、lookup-method、replaced-method、constructor-arg、property、qualifier 子元素，封装为 BeanDefinitionHolder，经 decorateBeanDefinitionIfRequired 后由 BeanDefinitionReaderUtils#registerBeanDefinition 完成注册。

**注册落点。** DefaultListableBeanFactory#registerBeanDefinition 维护三处：beanDefinitionMap（beanName 到 BeanDefinition 的 ConcurrentHashMap）、beanDefinitionNames（ArrayList，保注册序）、manualSingletonNames。同名冲突：不允许覆盖直接抛异常；允许覆盖且 hasBeanCreationStarted() 时还要同步搬迁集合。

至此"容器里有什么"定案——**Bean 在代码层面就是一份 BeanDefinition**（beanClassName、scope、lazyInit、dependsOn、primary、factoryBeanName、constructorArguments、propertyValues 等），实例化尚未发生。

4 容器就绪前的基础设施：prepareBeanFactory
------

prepareBeanFactory(beanFactory) 为容器装好公共设施，面试常考的几个动作：

* setBeanClassLoader / setBeanExpressionResolver / 注册 ResourceEditorRegistrar；
* 添加 ApplicationContextAwareProcessor：负责回调各 Aware 接口；
* ignoreDependencyInterface(EnvironmentAware.class 等)：这类接口的注入不走常规装配；
* registerResolvableDependency：BeanFactory 映射到 beanFactory，ResourceLoader/ApplicationEventPublisher/ApplicationContext 映射到 this；
* 添加 ApplicationListenerDetector；
* 手动注册三个特殊 bean：environment、systemProperties、systemEnvironment。

finishBeanFactoryInitialization 则先初始化 conversionService 与 LoadTimeWeaverAware 类型的 bean，freezeConfiguration() 冻结 BeanDefinition，最后进入 preInstantiateSingletons()。

5 单例 Bean 的诞生：preInstantiateSingletons → doGetBean → doCreateBean
------

**preInstantiateSingletons()**（DefaultListableBeanFactory）遍历 beanDefinitionNames：先 getMergedLocalBeanDefinition() 合并 parent 属性；非抽象、singleton、非懒加载才初始化；FactoryBean 特殊处理（beanName 加 `&` 前缀取其本身，SmartFactoryBean#isEagerInit 决定是否预初始化产品）；普通 bean 直接 getBean(beanName)。全部完成后回调 SmartInitializingSingleton#afterSingletonsInstantiated。

**doGetBean()** 主干六步：

1. transformedBeanName：剥 `&` 前缀、解别名；
2. getSingleton 查缓存，命中且是 FactoryBean 则经 getObjectForBeanInstance 取产品；
3. 未命中：prototype 正在创建则抛循环异常，否则查父容器，再 markBeanAsCreated；
4. 处理 depends-on：isDependent() 检测环，registerDependentBean 登记，先 getBean(dep)；
5. 按 scope 分派：singleton 走 getSingleton(beanName, ObjectFactory) 包住 createBean；prototype 每次新建；自定义 scope 委托 Scope 实现；
6. 类型检查/转换后返回。

**doCreateBean()**（AbstractAutowireCapableBeanFactory）是重头戏：

1. createBeanInstance：按工厂方法 → autowireConstructor（有参构造）→ instantiateBean（无参构造）实例化；无方法覆写用反射 BeanUtils.instantiateClass，有 lookup-method/replaced-method 覆写则用 CGLIB 子类；
2. applyMergedBeanDefinitionPostProcessors；
3. earlySingletonExposure：addSingletonFactory + getEarlyBeanReference 提前曝光——三级缓存入口，链路详见 [Spring循环依赖](./Spring循环依赖.md)；
4. populateBean：属性装配，autowireByName/autowireByType；@Autowired/@Value 由 AutowiredAnnotationBeanPostProcessor 在此处理；最后 applyPropertyValues 设值；
5. initializeBean：invokeAwareMethods（BeanNameAware/BeanClassLoaderAware/BeanFactoryAware）→ BeanPostProcessor#postProcessBeforeInitialization → invokeInitMethods（InitializingBean#afterPropertiesSet 或 init-method）→ postProcessAfterInitialization。**两个 BPP 回调夹着 init-method，是执行顺序题的题眼**——AOP 代理正是从 postProcessAfterInitialization 挂上来的（见 [Spring AOP源码解析](./Spring%20AOP源码解析.md)）；
6. earlySingletonReference 校验与 registerDisposableBeanIfNecessary。

6 面试高频附录：源码主线外的考点
------

* **id 与 name**：id 即 beanName，name 切分出别名；都没有则生成"全限定类名#0"，类名作别名；
* **关闭 Bean 覆盖/循环依赖**：自定义 ContextLoader 覆写 customizeContext，对内部 beanFactory setAllowBeanDefinitionOverriding(false)；
* **工厂方法与 FactoryBean 是两回事**：factory-method（静态/实例工厂）写在 BeanDefinition 里；FactoryBean 是接口（getObject/getObjectType/isSingleton），`getBean("&name")` 取工厂本身，适合封装复杂创建逻辑；
* **初始化回调四法**：@PostConstruct（postProcessBeforeInitialization 阶段）→ InitializingBean#afterPropertiesSet → init-method / @Bean(initMethod)；销毁对称：@PreDestroy → DisposableBean#destroy → destroy-method；
* **lookup-method 方法注入**：CGLIB 子类覆写取值方法，解 singleton 依赖 prototype 的失效问题（注解等价物是 @Lookup）。

面试追问链
------

* refresh() 哪一步创建 BeanFactory、哪一步实例化单例？→ obtainFreshBeanFactory() 与 finishBeanFactoryInitialization()；
* ApplicationContext 是 BeanFactory 的实现类吗？→ 是其子接口，但内部组合 DefaultListableBeanFactory 委托执行，"真身"实现类是后者；
* BeanFactoryPostProcessor 与 BeanPostProcessor 的区别？→ 前者改容器元数据（refresh 第 5 步，操作 BeanDefinition），后者改 Bean 实例（initializeBean 前后回调）；
* @PostConstruct、afterPropertiesSet、init-method 谁先执行？→ 依次就是这个顺序，分别落在 BPP 前置回调内与其后的 invokeInitMethods；
* 为什么构造器循环依赖无解？→ addSingletonFactory 发生在 createBeanInstance 之后，构造期没有任何对象可提前曝光；
* @Autowired 在哪个环节生效？→ populateBean 阶段，由 AutowiredAnnotationBeanPostProcessor 解析注入元数据并完成装配；
* BeanDefinition 什么时候被"用掉"？→ preInstantiateSingletons 按 beanDefinitionNames 顺序逐个触发 getBean 实例化。

面试官视角小结
------

背出 refresh() 十二步只是及格线。良好的标志是能讲"两阶段"：先把所有 BeanDefinition 注册成"图纸"，再统一实例化非懒加载单例——并说清 BeanPostProcessor 两个回调与 init-method 的相对位置。加分层是把三条大题串成一条线：AOP 代理挂在 postProcessAfterInitialization（衔接 AOP 源码篇）、循环依赖靠 createBeanInstance 之后的 earlySingletonExposure（衔接循环依赖篇）、设计层面还能点评模板方法/委托/钩子扩展点。基于 4.3.x 读的源码也不怕，主线在 5.x/6.x 依然成立，只是个别步骤内部更丰富。

> 来源：javadoop《Spring IOC 容器源码分析》（https://javadoop.com/post/spring-ioc）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
