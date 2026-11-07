AspectJ 使用指南
====

一句话定位：Spring 的 @AspectJ 只借注解不借实现，真正的 AspectJ 靠改字节码织入——本文讲清编译期/编译后/加载期三种织入方式的配置路径与适用边界，是"Spring AOP 拦不住的地方怎么办"这道面试题的标准答案；Spring 侧的代理实现见 [Spring AOP使用指南-前世与今生](./Spring%20AOP使用指南-前世与今生.md) 与 [Spring AOP源码解析](./Spring%20AOP源码解析.md)。

1 AspectJ 与 Spring AOP 的本质差别
------

Spring AOP 靠运行期动态代理，AspectJ 靠**静态织入**：直接修改编译产物字节码，织入完成后类就是"长好了"的样子，因此没有运行期开销，也天然覆盖代理做不到的场景（构造器、字段访问、非容器对象）。

三种织入时机：

* **compile-time weaving（CTW）**：编译期直接产出织入后的 .class；
* **post-compile weaving**：编译后对已编译类织入——典型场景是增强依赖 jar 里没有源码的类；
* **load-time weaving（LTW）**：JVM 类加载时织入。

依赖两个包（版本随时代更新，用法不变）：

```xml
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjrt</artifactId>
    <version>1.8.13</version>
</dependency>
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjweaver</artifactId>
    <version>1.8.13</version>
</dependency>
```

2 样板代码：原生 aspect 语法与注解写法
------

目标类 Account：初始余额 20，pay(int amount) 余额不足返回 false，够则扣款返回 true。

**原生 .aj 语法**（aspect 是关键字，javac 编不了，必须用 AspectJ 编译器）：

```java
public aspect AccountAspect {
    pointcut callPay(int amount, Account account):
        call(boolean com.javadoop.aspectjlearning.model.Account.pay(int))
        && args(amount) && target(account);

    before(int amount, Account account): callPay(amount, account) {
        System.out.println("[AccountAspect] pay " + amount + ", balance " + account.balance);
    }

    boolean around(int amount, Account account): callPay(amount, account) {
        if (account.balance < amount) {
            System.out.println("[AccountAspect] 拒绝付款");
            return false;                       // 短路，proceed 都不调
        }
        return proceed(amount, account);        // proceed 执行原方法
    }

    after(int amount, Account account): callPay(amount, account) {
        System.out.println("[AccountAspect] balance " + account.balance);
    }
}
```

**Java + 注解写法**（与 Spring 里长得一样，但这里由 AspectJ 织入器处理）：

```java
@Aspect
public class ProfilingAspect {
    @Pointcut("execution(* com.javadoop.aspectjlearning.model.*.*(..))")
    public void modelLayer() {}

    @Around("modelLayer()")
    public Object logProfile(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        System.out.println(joinPoint.getSignature() + " cost " + (System.currentTimeMillis() - start));
        return result;
    }
}
```

对照记忆：原生语法能拦 call（调用点）级别，能力比注解版强；around 里的 proceed 是 AspectJ 独有语法，Spring AOP 对应 pjp.proceed()。

3 编译期织入（CTW）
------

用 org.codehaus.mojo:aspectj-maven-plugin，关键配置：complianceLevel/source/target 指定 1.8，showWeaveInfo、verbose 打开，Xlint 忽略，编码 UTF-8，绑定 compile 与 test-compile 两个 goal。该插件同时充当 .aj 文件的编译器。

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>aspectj-maven-plugin</artifactId>
    <version>1.7</version>
    <configuration>
        <complianceLevel>1.8</complianceLevel>
        <source>1.8</source>
        <target>1.8</target>
        <showWeaveInfo>true</showWeaveInfo>
        <verbose>true</verbose>
        <Xlint>ignore</Xlint>
        <encoding>UTF-8</encoding>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>compile</goal>
                <goal>test-compile</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

`mvn clean package` 后跑 main：account.pay(10) 成功、account.pay(50) 被 around 拒绝，before/after 全部生效。奥秘不在运行时——**main 函数与 Account 的编译产物本身已被改写**，反编译即可见织入的代码。这也是"无运行时开销"的由来。

4 编译后织入（Post-Compile Weaving）
------

场景变化：要增强的类在第三方包 aspectj-learning-share.jar 里（无源码，改不了别人的代码）。同一个插件换较新版本，关键配置是 `<weaveDependencies>` 指定要织入的依赖：

```xml
<configuration>
    <weaveDependencies>
        <weaveDependency>
            <groupId>com.javadoop</groupId>
            <artifactId>aspectj-learning-share</artifactId>
        </weaveDependency>
    </weaveDependencies>
</configuration>
```

`mvn clean package` 后，jar 里那个类（如 User）的织入版会被并入当前工程的编译产物。实操提醒：务必用 mvn 命令行构建——IntelliJ 的自家构建逻辑不走这个 maven 插件配置，会得到"没织入"的假象。

5 加载期织入（LTW）
------

去掉编译插件（.aj 不会被编译，只留注解版切面），织入挪到类加载时，两步配置：

启动参数挂 agent：

```bash
java -javaagent:/path/to/aspectjweaver-1.8.13.jar -jar app.jar
```

resources/META-INF/aop.xml 声明切面与织入范围：

```xml
<aspectj>
    <aspects>
        <aspect name="com.javadoop.aspectjlearning.aspect.ProfilingAspect"/>
    </aspects>
    <weaver options="-verbose -showWeaveInfo">
        <include within="com.javadoop.aspectjlearning..*"/>
    </weaver>
</aspectj>
```

验证三步法：打包后先反编译确认 class 文件未被改写；不带 agent 运行，无织入输出；带 -javaagent 运行，出现 ProfilingAspect 耗时日志——证明织入发生在类加载时。跑单测则给 maven-surefire-plugin 配 `<argLine>-javaagent:...</argLine>`（配 useSystemClassLoader、forkMode），同样建议用 mvn 而非 IDE。

实用性与边界：三种方式里前两种更实用——LTW 要改启动脚本，大公司得运维配合，落地成本高；实际生产中用得最多的仍是纯 Spring AOP，AspectJ 留给代理模型覆盖不了的需求。另一个现代边界：GraalVM Native Image 下不存在运行期类加载织入，LTW 不可用，静态织入是唯一选择（AOT/Native 视角见 [SpringBoot3升级要点-Jakarta-AOT-Native](./SpringBoot3升级要点-Jakarta-AOT-Native.md)）。

面试追问链
------

* Spring 里的 @Aspect 用了 AspectJ 的织入器吗？→ 没有，只复用注解和切点表达式，增强由动态代理完成；
* AspectJ 三种织入时机分别适合什么场景？→ CTW 自己的代码；post-compile 增强依赖 jar 里的已编译类；LTW 不想改构建、能接受改启动参数；
* LTW 需要哪两样东西？→ -javaagent 挂 aspectjweaver + classpath 的 META-INF/aop.xml（aspects + weaver include 范围）；
* around 里的 proceed 是什么？→ 执行原方法的钩子，可换参、可短路直接返回；Spring AOP 里对应 pjp.proceed()；
* 什么时候必须上 AspectJ？→ 拦构造器/字段访问、增强非容器对象、增强第三方 jar 无源码类、追求零运行时开销；
* AspectJ 和 Spring AOP 性能差异怎么来的？→ AspectJ 织入发生在运行前，调用零额外开销；Spring 每次调用多一层代理与拦截器链；
* 原生 .aj 语法和注解版差别？→ aspect 关键字可写 call 切点等更强语义，但 javac 编不了，需要专用编译器/插件。

面试官视角小结
------

这道题的价值在于划清能力边界：绝大多数业务答 Spring AOP 就够。但面试官追问"Spring AOP 拦不了构造器和字段怎么办"时，能接住"上 AspectJ 静态织入"，并说清 CTW/post-compile/LTW 三种时机、LTW 的 aop.xml 与 -javaagent 配置路径，就是明确的区分度。再补两句工程现实——post-compile 必须走 mvn 命令行构建、LTW 要改启动脚本需运维配合、Native Image 下 LTW 直接不可用——工程味就齐了。Spring 侧怎么用、怎么读源码，回到 [Spring AOP使用指南-前世与今生](./Spring%20AOP使用指南-前世与今生.md) 与 [Spring AOP源码解析](./Spring%20AOP源码解析.md)。

> 来源：javadoop《AspectJ 使用介绍》（https://javadoop.com/post/aspectj）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
