Spring AOP 使用指南：前世与今生
====

一句话定位：把 Spring AOP 的三种配置形态（1.2 接口式 → XML schema → @AspectJ 注解）与 Advice/Advisor/Interceptor 等术语一次讲透，AOP 使用层的面试完备地图；代理是怎么造出来的见姊妹篇 [Spring AOP源码解析](./Spring%20AOP源码解析.md)。

1 先分清三个名词：AOP、AspectJ、Spring AOP
------

AOP 的本质：在方法执行前、返回后、抛异常后等位置做拦截增强。Java 语言本身没提供这种钩子，实现手段是**生成代理类**——对外提供服务的实际是代理实例。

* **Spring AOP**：基于动态代理；目标类有接口默认 JDK 动态代理，无接口用 CGLIB（Spring 3.2 起 spring-core 内置 CGLIB 与 ASM，无需额外依赖）；依赖 IOC 容器管理，**只能作用于容器中 Bean 的方法**；Web 场景有时 Filter/Interceptor 比 AOP 更合适；性能弱于 AspectJ——启动时要生成代理，方法调用增加栈深度；
* **AspectJ**：Eclipse 基金会出品的 AOP 完整方案，靠修改字节码（静态织入）实现，织入发生在运行之前，因此**无额外运行时开销**；三种织入时机：compile-time（编译期）、post-compile（编译后，增强已编译类）、load-time（类加载期）；
* Spring AOP 只解决企业开发中最普遍的"方法织入"需求，是 AspectJ 能力的一个子集——完整用法见 [AspectJ使用指南](./AspectJ使用指南.md)。

2 Spring 1.2 接口式配置：Advice、Advisor、Interceptor
------

最古老的用法，也是读 Spring AOP 源码的起点。为每个 bean 配一个 ProxyFactoryBean：

```xml
<bean id="userServiceProxy" class="org.springframework.aop.framework.ProxyFactoryBean">
    <property name="target" ref="userService"/>
    <property name="interceptorNames">
        <list>
            <value>logArgsAdvisor</value>
        </list>
    </property>
</bean>
```

两个致命局限：要拦截 OrderService、PostService 就得再各配一个代理；拦截粒度只到类级别，类中所有方法都被拦。

三个关键概念（碰到一个记一个）：

* **Advice（通知）**：拦截到之后"做什么"，如 MethodBeforeAdvice 打印入参、AfterReturningAdvice 打印返回值；
* **Advisor（切面holder）**：决定"拦哪些方法"，内部持有一个 Advice。NameMatchMethodPointcutAdvisor 按 mappedNames（逗号分隔多个方法名）匹配；RegexpMethodPointcutAdvisor 用正则 pattern，如 `com.javadoop.*.service.*.create.*`，方法级粒度；
* **Interceptor（拦截器）**：实现 MethodInterceptor，在 invoke(MethodInvocation) 里 `invocation.proceed()` 前后包夹逻辑，是"能做最完整事情"的形态（Spring 内部统一把 Advice 适配成 Interceptor 执行）。

```java
public class DebugInterceptor implements MethodInterceptor {
    public Object invoke(MethodInvocation invocation) throws Throwable {
        System.out.println("before: " + invocation.getMethod().getName());
        Object result = invocation.proceed();
        System.out.println("after: " + invocation.getMethod().getName());
        return result;
    }
}
```

3 自动代理：从逐个 ProxyFactoryBean 到 AutoProxyCreator
------

autoproxy 解决"每个 bean 配一个代理"的问题，使用者直接按原类型 getBean，拿到的已是代理：

* **BeanNameAutoProxyCreator**：`beanNames` 支持模式匹配（如 `*ServiceImpl`），`interceptorNames` 可挂 Advice/Interceptor/Advisor——它自己决定拦哪些 bean，方法粒度交给内部 advice；
* **DefaultAdvisorAutoProxyCreator**：配置只要一行，让容器中**所有 Advisor 自动生效**——由各 Advisor 的 Pointcut 自己去匹配 bean 的方法，匹配上才执行其内部 advice。

```xml
<bean class="org.springframework.aop.framework.autoproxy.BeanNameAutoProxyCreator">
    <property name="beanNames" value="*ServiceImpl"/>
    <property name="interceptorNames" value="logArgsAdvice"/>
</bean>

<bean class="org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator"/>
```

两者关系是"粗粒度按名圈 bean"与"细粒度让 Advisor 各显神通"的差别；DefaultAdvisorAutoProxyCreator 本身是个 BeanPostProcessor，这正是源码篇的入口。

4 @AspectJ 注解方式：当下绝对主流
------

名字里带 AspectJ，但和 AspectJ 织入器没有关系——只借用它的注解与切点表达式语法，实现是纯 Spring AOP。依赖 aspectjweaver（Boot 用 spring-boot-starter-aop）；开启方式二选一：XML 的 `<aop:aspectj-autoproxy/>` 或 Java 配置 `@Configuration` + `@EnableAspectJAutoProxy`。@Aspect 必须作用在一个 bean 上（@Component 或 XML 定义均可）。

**切点表达式**，常用匹配原语：

* `execution(* transfer(..))`：方法签名匹配，最常用；`..` 在参数位表示任意参数；
* `within(com.javadoop.springaoplearning.service..*)`：按类/包圈范围（`.` 一个包名，`..` 包及子包）；
* `@annotation(...)`：按方法上的注解匹配，常与 `&&` 组合使用；
* `bean(*Service)`：按 bean 名匹配，Spring 扩展。

官方推荐的 SystemArchitecture 模式：在一个类里集中定义各层切点（web 层、service 层、dao 层）供各切面复用，避免表达式散落。

**五种通知**：

```java
@Aspect
@Component
public class LogAspect {
    @Pointcut("execution(* com.javadoop.springaoplearning.service.*.*(..))")
    public void businessService() {}

    @Before("businessService()")
    public void logArgs(JoinPoint joinPoint) {          // 第一个参数必须是 JoinPoint
        System.out.println(Arrays.toString(joinPoint.getArgs()));
    }

    @AfterReturning(value = "businessService()", returning = "retVal")
    public void logResult(Object retVal) {}             // 可绑定返回值

    @AfterThrowing(value = "businessService()", throwing = "ex")
    public void logException(DataAccessException ex) {} // 可绑定异常类型

    @After("businessService()")
    public void finallyLog() {}                         // finally 语义，正常异常都走

    @Around("businessService()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();                  // 执行原方法，也可 pjp.proceed(args) 换参
        return result;
    }
}
```

* @Around 最全能，"既做得到 @Before 的事，也做得到 @AfterReturning 的事"，还能改参数、改返回值、吞异常；
* 取入参用 JoinPoint#getArgs()；ProceedingJoinPoint 是其子接口，多了 proceed()/proceed(Object[])，只有 @Around 能用；
* 两个不生效场景：切面配置不会作用于 @Aspect bean 自身；目标对象内部 this 自调用不经过代理，同样不触发增强。

5 XML schema 方式：aop:config
------

解析入口是 org.springframework.aop.config.AopNamespaceHandler。结构上所有配置收在 `<aop:config>` 下，`<aop:aspect ref="aBean">` 指定承载处理逻辑的普通 bean：

```xml
<aop:config>
    <aop:pointcut id="businessService"
        expression="execution(* com.javadoop.springaoplearning.service.*.*(..))"/>
    <aop:aspect ref="logArgsAspect">
        <aop:before pointcut-ref="businessService" method="logArgs"/>
    </aop:aspect>
    <aop:aspect ref="logResultAspect">
        <aop:after-returning pointcut-ref="businessService" method="logResult"/>
    </aop:aspect>
</aop:config>
```

* pointcut 作为 `<aop:config>` 直接子元素是全局的；放在 `<aop:aspect>` 内则仅该切面可用；
* expression 也可以引用注解方式定义的切点（如 `com.javadoop.SystemArchitecture.businessService()`），两套体系可混用；
* 各通知元素与第 4 节注解一一对应，适合无源码、需集中管控配置的场景。

实操提示：验证 AOP 配置优先用命令行（mvn 打包后 java -jar）而非 IDE——IDE 的构建与运行代理常把问题"智能"掉。

面试追问链
------

* Spring AOP 和 AspectJ 什么关系？→ 只借注解与切点表达式，实现是纯 Spring 动态代理；AspectJ 才是靠字节码织入的完整方案；
* 有接口和无接口分别用什么代理？→ JDK 动态代理 / CGLIB；
* Advice 和 Advisor 谁决定拦谁？→ Advice 管"做什么"，Advisor = Pointcut + Advice 管"拦哪些"；
* @After 和 @AfterReturning 区别？→ finally 语义（正常异常都执行）vs 仅正常返回后执行；
* 同类自调用为什么切面不生效？→ this 调用不经过代理对象；解法是自注入或 expose-proxy 拿 AopContext.currentProxy()；
* 三种配置方式怎么选？→ 注解为主流；XML schema 用于集中配置/无源码场景；接口式只配读源码和面试；
* DefaultAdvisorAutoProxyCreator 厉害在哪？→ 一行配置让所有 Advisor 自动生效，本质是 BeanPostProcessor，在 Bean 初始化后包代理。

面试官视角小结
------

使用层考 AOP，面试官想确认三件事：知不知道代理才是本质（顺带引出 JDK/CGLIB 的选择规则）；能不能写对 execution 表达式、分清五种通知语义（尤其 @After vs @AfterReturning）；有没有踩过自调用不生效、切面作用范围这类坑。答完主动补一句"Advisor 的匹配与代理创建发生在 BeanPostProcessor 阶段"，就把话题引向自己熟悉的主场——链路详见 [Spring AOP源码解析](./Spring%20AOP源码解析.md)；若被问"Spring AOP 拦不了构造器/字段怎么办"，接 AspectJ 静态织入即可，见 [AspectJ使用指南](./AspectJ使用指南.md)。

> 来源：javadoop《Spring AOP 使用介绍，从前世到今生》（https://javadoop.com/post/spring-aop-intro）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
