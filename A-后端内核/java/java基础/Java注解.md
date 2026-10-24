What（注解）
====

我们都知道在Java代码中使用注释是为了提升代码的可读性。即注释是给人看的（对于编译器来说没有意义）。注解可以看做是注释的“强力升级版&#34;。它可以向编译器、虚拟机等解释说明一些事情（即它对编译器等工具也是“可读”的）。比如我们熟悉的@Override注解，它的作用是告诉编译器它所注解的方法是重写的父类中的方法。这样编译器就会去检查父类是否存在这个方法，以及这个方法的签名与父类是否相同。

即注解是描述Java代码的代码，它能够被编译器解析，注解处理工具在运行时也能够解析注解。除了向编译器等传递一些信息，我们也可以使用注解生成代码。比如我们可以使用注解来描述我们的意图。然后让注解解析工具来解析注解，以此来生成一些”模板化“的代码。比如Hibernate、Spring等框架大量使用了注解，来避免一些重复的工作。**注解是一种”被动“的信息，必须由编译器或虚拟机来“主动”解析它，它才能发挥自己的作用**。

注解（Annotation）提供了一种安全的类似注释的机制，为我们在代码中添加信息提供了一种形式化得方法。使我们可以在稍后某个时刻方便的使用这些数据（通过解析注解来使用这些数据）。用来将任何的信息或者元数据与程序元素（类、方法、成员变量等）关联。就是更加直观更加明了的说明。这些说明信息与程序业务逻辑没有关系，并且是供指定的工具或框架使用的。Annotation像一种修饰符一样，应用于包、类型、构造方法、方法、成员变量、参数及本地变量的申明语句中。

Annotation是一种接口。通过Java的反射机制相关的API来访问Annotation信息。相关类（框架或工具中的类）根据这些信息来决定如何使用该程序元素或改变它们的行为。Java语言解释器在工作时会忽略这些Annotation。因此在JVM中这些Annotation是“不起作用”的，只能通过配套的工具才能访问和处理这些Annotation类型的信息。

Annotation和interface的异同
------

* annotition的类型使用关键字@interface而不是interface。它继承了java.lang.annotition.Annotition接口，并非申明了一个interface。

* Annotation类型、方法定义是独特的、受限制的。Annotation类型的方法必须申明为无参数、无异常抛出的。这些方法定义了Annotation的成员：方法名称为了成员名，而方法返回值称为了成员的类型。而方法返回值必须为primitive类型、Class类型、枚举类型、Annotation类型或者由前面类型之一作为元素的一位数组。方法的后面可以使用default和一个默认数值来申明成员的默认值，null不能作为成员的默认值。这与我们在非Annotation类型中定义方法有很大不同。Annotation类型和他的方法不能使用Annotation类型的参数，成员不能是generic。只有返回值类型是Class的方法可以在Annotation类型中使用generic。因为此方法能够用类转换将各种类型转换为Class。

* Annotation类型又与接口有着近似之处。它们可以定义常量、静态成员类型（比如枚举类型定义）。Annotation类型也可以如接口一般被实现或者继承。

一、注解的本质：继承 Annotation 的接口
------

`@interface` 只是语法糖。javac 编译后，注解就是一个**继承了 java.lang.annotation.Annotation 的接口**，反编译可见：

```java
// 源码                          // 编译产物（反编译）
@Override                 ==>    public @interface Override { ... }
                                 // 实际上是：
public @interface Override {}    public interface Override extends Annotation {}
```

* 所以注解"没有实现体"——它只是**贴在程序元素上的元数据**，本身不含逻辑。这也解释了上文"注解是被动信息"的说法；

* 注解的成员 = 接口的"无参方法"：`String value();` 声明成员 value，`default` 给默认值，类型限制即上文列出的那几类；

* 使用注解等价于让编译器生成一个实现了该接口的代理实例（运行期由 JDK 动态代理生成）。反射读注解拿到的就是这个代理对象。所以注解与 [JDK动态代理源码分析](JDK动态代理源码分析.md) 在底层是同一套机制。

二、元注解：给注解定规矩的注解
------

自定义注解前先想清楚四个元注解（详见 [Java元注解](Java元注解.md)）：

| 元注解 | 管什么 | 高频取值 |
| ---- | ---- | ---- |
| @Target | 能贴在哪些位置 | TYPE、METHOD、FIELD、PARAMETER |
| @Retention | 保留到什么阶段 | SOURCE、CLASS、RUNTIME |
| @Documented | 是否进 javadoc | 无参数，标了就进 |
| @Inherited | 子类能否继承类上的注解 | 只对 class 的 extends 生效 |

* @Retention 三档对应的经典注解：@Override 是 SOURCE（编译器检查完就丢）、@FunctionalInterface 是 SOURCE。Spring 的 @Component 是 RUNTIME（容器启动反射扫描要用）。框架做代码生成（如 Lombok）常用 CLASS/注解处理器在编译期处理。

三、运行时怎么读取注解
------

只有 `@Retention(RetentionPolicy.RUNTIME)` 的注解才能在运行期反射读到——这是 Spring "扫包注入" 的地基：

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MyComponent { String value() default ""; }

@MyComponent("orderService")
public class OrderService { }

// 运行期读取
MyComponent anno = OrderService.class.getAnnotation(MyComponent.class);
boolean has = OrderService.class.isAnnotationPresent(MyComponent.class);
String name = anno.value();               // "orderService"
```

* 读方法/字段上的注解用 Method/Field 的同名 API；参数注解用 `method.getParameterAnnotations()`；

* 只查"自己声明的"用 getDeclaredAnnotation（不含继承来的），getAnnotation 会沿 @Inherited 向父类找——两者差异见 [Java注解的继承](Java注解的继承.md)；

* 注意：注解继承有严格规则：@Inherited 只对**类**生效，方法被重写、接口上的注解都会“断链”。Spring 里 @Transactional “看起来能继承”多数是框架自己向上扫描的结果。

四、注解能干什么：三种消费方式
------

* **编译期检查**：@Override、@FunctionalInterface，编译器直接消费，零运行时成本；

* **编译期生成代码**：注解处理器（APT）扫描注解生成新源码，Lombok、MapStruct、Dagger 的原理；

* **运行期反射驱动框架**：Spring 的 @Component/@Autowired、MyBatis 的 @Select、JUnit 的 @Test——"注解 + 反射 + 动态代理"三件套就是主流框架的骨架。

一句话总结：**注解 = 贴在代码上的、可被工具读取的类型化元数据，本质是继承 Annotation 的接口。Retention 决定它能活到哪个阶段，RUNTIME 注解 + 反射构成了几乎所有框架的入口。**

> 2026-10 扩充（Round1 查漏）。
