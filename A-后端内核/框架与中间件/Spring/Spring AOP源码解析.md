Spring AOP 源码解析
====

一句话定位：回答"AOP 在 Spring 里到底怎么落地"——代理挂在 BeanPostProcessor 的 postProcessAfterInitialization 上，由 AbstractAutoProxyCreator#wrapIfNecessary 创建，DefaultAopProxyFactory 决定 JDK 还是 CGLIB，运行期靠拦截器链执行增强；使用层的三种配置形态见 [Spring AOP使用指南-前世与今生](./Spring%20AOP使用指南-前世与今生.md)，IOC 主线见 [Spring IOC容器源码分析](./Spring%20IOC容器源码分析.md)。

1 出发点：AOP 原理就是动态代理
------

生成代理类，用代理实例替换真实实现对外提供服务。放到 IOC 容器的语境里：**getBean(...) 返回的是 JDK Proxy 或 CGLIB 生成的代理对象**。这也直接解释了为什么 Spring AOP 只能作用于容器中的 Bean——不在容器里，就没有"被替换的获取入口"。

调试样板（Spring 4.x/5.x 通用）：UserService/OrderService 两个接口与实现，两个 Advice——LogArgsAdvice（MethodBeforeAdvice，打印方法与参数）、LogResultAdvice（AfterReturningAdvice，打印返回值）：

```java
public class LogArgsAdvice implements MethodBeforeAdvice {
    public void before(Method method, Object[] args, Object target) {
        System.out.println("准备执行方法: " + method.getName() + ", 参数列表: " + Arrays.toString(args));
    }
}
```

再配两个 Advisor 声明各自匹配 createXxx/queryXxx 方法，注册 DefaultAdvisorAutoProxyCreator 让所有 Advisor 自动生效。跑 main 即可验证：同一个 bean 的不同方法被不同 advice 分别拦截——切入点匹配与代理创建都发生在容器启动阶段。

2 代理挂在哪里：BeanPostProcessor 的 postProcessAfterInitialization
------

顺着 IOC 主线（refresh → finishBeanFactoryInitialization → getBean → doCreateBean）走：DefaultAdvisorAutoProxyCreator 本质是一个 **BeanPostProcessor**，在 doCreateBean → initializeBean 阶段执行，核心入口是 applyBeanPostProcessorsAfterInitialization。

AbstractAutoProxyCreator 覆写了 postProcessAfterInitialization，调 wrapIfNecessary(bean, beanName, cacheKey)，骨架：

```java
protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
    // 略过提前暴露检查与已处理缓存
    Object[] specificInterceptors =
            getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, this);
    if (specificInterceptors == DO_NOT_PROXY) {
        return bean;
    }
    Object proxy = createProxy(bean.getClass(), beanName,
            specificInterceptors, new SingletonTargetSource(bean));
    return proxy;   // 返回代理替换原 bean
}
```

getAdvicesAndAdvisorsForBean 的两步（AbstractAdvisorAutoProxyCreator#findEligibleAdvisors）：

1. findCandidateAdvisors：找出容器中所有候选 Advisor（@AspectJ 模式下由 AnnotationAwareAspectJAutoProxyCreator 增补解析 @Aspect bean）；
2. findAdvisorsThatCanApply：AopUtils#canApply 用各 Advisor 的 Pointcut（ClassFilter + MethodMatcher）逐类逐方法匹配，留下命中的；
3. 最后按 Ordered/@Order 排序——多切面的执行顺序在这里定，不是调用时才排。

有匹配 → createProxy 返回代理；无匹配 → 原样返回。一句话结论：**AOP 不是独立机制，而是 IOC 主线上 initializeBean 的一站**——这也是使用篇里"三种配置殊途同归"的原因。

3 createProxy 与 ProxyFactory：代理怎么造出来
------

createProxy 的骨架：

* new 一个 ProxyFactory，设置要代理的接口与 TargetSource（目标 bean 包在 SingletonTargetSource 里）；
* 处理 proxy-target-class 配置（决定强制 CGLIB 与否）；
* buildAdvisors：把传入的 advice/interceptor 统一包装成 Advisor 形态挂到 ProxyFactory；
* 最后 proxyFactory.getProxy(classLoader) 产出代理。

getProxy 内部走 createAopProxy()，落到 DefaultAopProxyFactory#createAopProxy 的分支：

* 配了 optimize 或 proxyTargetClass，或目标类没有可用接口 → 走 CGLIB 系：目标类本身是接口或已经是 Proxy 类则退回 JdkDynamicAopProxy，否则用 ObjenesisCglibAopProxy；
* 有接口 → JdkDynamicAopProxy。

记结论：**默认有接口用 JDK 动态代理；无接口、或 `<aop:aspectj-autoproxy proxy-target-class="true"/>` 强制时用 CGLIB**。CGLIB 的边界也要记：final 类无法被继承、final/private/static 方法无法被增强。

4 运行期：invoke 与拦截器链
------

**JdkDynamicAopProxy#getProxy** 调 Proxy.newProxyInstance，它自己实现 InvocationHandler，增强全部发生在 invoke 里：

1. 先处理 Object 的 equals/hashCode 与 Advised 接口方法（不进拦截器链）；
2. exposeProxy 开启时把当前代理放进 ThreadLocal（供 AopContext.currentProxy() 解决自调用）；
3. advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass) 获取该方法对应的**拦截器链**——按 method+targetClass 缓存，首次构建（AdvisorChainFactory 负责把每个 Advisor 的 advice 适配成 MethodInterceptor，命中动态匹配的保留为 InterceptorAndDynamicMethodMatcher）；
4. 链为空 → 直接反射调用目标方法；链非空 → new ReflectiveMethodInvocation(...).proceed()；
5. 返回值处理：目标方法返回 this 时替换为代理对象，保证链式调用仍在代理上。

核心骨架（简化后依旧能对上源码结构）：

```java
// JdkDynamicAopProxy#invoke 的主干
List<Object> chain = advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
Object target = this.targetSource.getTarget();
if (chain.isEmpty()) {
    // 链空：没有任何拦截命中该方法，直接反射调用目标
    return AopUtils.invokeJoinpointUsingReflection(target, method, args);
}
// 链非空：包一个 MethodInvocation，由 proceed() 逐层推进
MethodInvocation invocation = new ReflectiveMethodInvocation(
        proxy, target, method, args, targetClass, chain);
return invocation.proceed();
```

**ReflectiveMethodInvocation#proceed()** 是链式执行的心脏：维护当前下标，每调用一次推进一个拦截器；每个 MethodInterceptor 在自己的 invoke 里先做前置逻辑、再调 invocation.proceed() 触发下一个——递归到链尾执行真正的方法，返回时层层回穿，形成洋葱圈。@Around 对应的正是"包住 proceed"的位置。

**CglibAopProxy#getProxy** 换一套底座：基于 Enhancer 设置 superclass、interfaces、Callback 数组与 ProxyCallbackFilter，最后 createProxyClassAndInstance；核心回调是 DynamicAdvisedInterceptor，逻辑与 JDK 版 invoke 同构（同样取拦截器链，链空直调、链非空走 CglibMethodInvocation.proceed()）。ObjenesisCglibAopProxy 用内嵌的 Objenesis 绕过构造器实例化代理对象——所以 CGLIB 代理不要求目标类有无参构造器。

5 @AspectJ 的注册入口与两个 Instantiation 的辨析
------

`<aop:aspectj-autoproxy/>`（或 @EnableAspectJAutoProxy）由 AopNamespaceHandler 交给 AspectJAutoProxyBeanDefinitionParser 解析，最终注册的是 **AnnotationAwareAspectJAutoProxyCreator**（beanName 为 internalAutoProxyCreator）。它继承 AbstractAutoProxyCreator，同样是 BeanPostProcessor——增补的能力是解析容器里的 @Aspect bean、把 @Before/@Around 等织入点适配成 Advisor，之后的主线与第 2-4 节完全一致。

高频误区辨析：很多人以为 AOP 是 InstantiationAwareBeanPostProcessor 干的。两个词只差一点，时机完全不同：

* Instantiation：实例化（构造器执行）之前——postProcessBeforeInstantiation；
* Initialization：初始化（init-method/Aware 回调）前后——postProcessBeforeInitialization / postProcessAfterInitialization。

AbstractAutoProxyCreator 确实也实现了 postProcessBeforeInstantiation，里面也有 createProxy 逻辑，但**只有配置了 customTargetSourceCreators（自定义 TargetSource 创建器）才会走到**，通常直接返回 null。AOP 主链路不在这里。

面试追问链
------

* Spring 在哪个环节把 Bean 换成代理？→ initializeBean 的 postProcessAfterInitialization，AbstractAutoProxyCreator#wrapIfNecessary；
* @EnableAspectJAutoProxy 做了什么？→ 注册 AnnotationAwareAspectJAutoProxyCreator（internalAutoProxyCreator），一个会解析 @Aspect 的 BeanPostProcessor；
* JDK/CGLIB 怎么选？→ DefaultAopProxyFactory#createAopProxy：有接口默认 JDK；proxyTargetClass/optimize/无接口走 CGLIB，目标类本身是接口时退回 JDK；
* 拦截器链什么时候构建？→ 首次调用该方法时 getInterceptorsAndDynamicInterceptionAdvice 构建，按 method+targetClass 缓存；
* 多个切面的顺序谁定？→ findEligibleAdvisors 阶段按 Ordered/@Order 排好，不是调用时临时排；
* 洋葱圈顺序谁控制？→ ReflectiveMethodInvocation#proceed() 的下标推进，每个 Interceptor 包住下一次 proceed；
* CGLIB 代理的限制？→ final 类不可继承，final/private/static 方法增强不了；目标类无无参构造器倒不怕，有 Objenesis；
* equals/hashCode 为什么不进链？→ 语义应基于代理对象自身比较，进链会造成代理与目标比较不一致；
* InstantiationAwareBeanPostProcessor 负责 AOP 吗？→ 不是，它是实例化前扩展点；AOP 挂在初始化后的 BPP 上（自定义 TargetSource 除外）；
* 自调用问题在源码层怎么解？→ exposeProxy 把代理放 ThreadLocal，目标类里用 AopContext.currentProxy() 调用。

面试官视角小结
------

AOP 源码题的分水岭一句话：**AOP 是 IOC 主线上的一站**。答得出"代理创建挂在 initializeBean 的 postProcessAfterInitialization"，说明看过主干；能追到 DefaultAopProxyFactory 的分支条件与 ReflectiveMethodInvocation#proceed() 的链式推进，是熟练层；多数人卡在把 InstantiationAwareBeanPostProcessor 当 AOP 核心——能主动辨析两个 Instantiation 的，才算真读过源码。往上游讲得清 getBean/doCreateBean 的位置（衔接 IOC 篇），往使用层讲得清三种配置为何殊途同归（衔接使用篇），这道题就从考点变成了你的主场。

> 来源：javadoop《Spring AOP 源码解析》（https://javadoop.com/post/spring-aop-source）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
