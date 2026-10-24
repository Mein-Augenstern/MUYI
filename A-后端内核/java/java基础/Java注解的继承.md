看在前面
====

> * <a href="https://www.iteye.com/blog/zxmsdyz-1974249">Java Annotation注解继承方式说明</a>

> * <a href="https://www.cnblogs.com/xhy-shine/p/11138183.html">Java注解的继承</a>

注解继承的基本规则
====

JDK 官方文档对注解继承的说明是：只有类上的注解能被继承。但实测发现比官方说法多一条：**没有被重写的方法上的注解也能被继承**。整理为四条规则：

1. 注解要能被继承，定义时必须加 `@Inherited` 元注解。若还要在运行期通过反射读取，必须同时声明 `@Retention(RetentionPolicy.RUNTIME)`。
2. **类上的注解**：加了 `@Inherited` 的注解标注在父类上，子类自动"继承"该注解；
3. **方法上的注解**：会被子类中**没有被重写的方法**继承。一旦子类重写该方法，注解即失效。
4. **接口（含接口方法）上的注解不能被继承**：无论实现类还是实现方法都拿不到。注解继承机制不适用于接口（接口可多实现，"继承"会产生歧义）。

1 @Inherited 元注解
------

`@Inherited` 本身只能作用于注解类型，定义如下：

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Inherited {
}
```

用最小例子验证上述规则：

```java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@interface Mark { }

@Mark
class Parent {
    @Mark
    public void hello() { }
}

class Child extends Parent { }      // Child 类上"有" @Mark

class SubChild extends Parent {
    @Override
    public void hello() { }         // 重写后方法上的 @Mark 失效
}

// Parent.class.isAnnotationPresent(Mark.class)      -> true
// Child.class.isAnnotationPresent(Mark.class)       -> true（继承而来）
// Child.class.getDeclaredAnnotation(Mark.class)     -> null（不是直接声明）
```

注意 `getAnnotation`/`isAnnotationPresent` 走的是包含继承注解的查找路径。`getDeclaredAnnotation` 只查当前类直接声明的注解。所谓"被继承"只对前者可见。

框架中的注解"继承"
====

Spring/SpringMVC 中很多注解看起来"能继承"，实际是**框架在启动阶段主动向上扫描了父类/接口**。这并不是原生注解继承机制在起作用。

1 @Transactional
------

* 可以标注在接口、接口方法、类、类方法上；
* Spring 官方**不建议**标注在接口或接口方法上：只有基于接口的代理（JDK 动态代理）才会生效。使用 CGLIB 类代理时会被忽略。
* 只应对 public 方法生效：标注在 protected、private 或默认可见性的方法上会被忽略，且**不会抛异常**。这是由 Spring AOP 基于方法代理增强的本质决定的。

2 @RequestMapping
------

* 反射查看时子类并没有真正"继承"到父类/父方法上的 `@RequestMapping`。但子类能享有其映射效果——这是 SpringMVC 初始化 HandlerMapping 时递归向上扫描的结果。
* 父类型是接口时该机制无效：接口可以多实现，而类是单继承。

小结
====

| 位置 | 能否被子类继承 | 条件 |
| ---- | ---- | ---- |
| 父类上 | 能 | 注解加了 @Inherited |
| 父类方法上（未重写） | 能 | 注解加了 @Inherited |
| 父类方法上（已重写） | 不能 | —— |
| 接口 / 接口方法上 | 不能 | —— |

一句话：`@Inherited` 只对类的 extends 生效，方法重写和接口都会让注解"断链"。框架中的"可继承"多为框架自身扫描实现。

> 内容来源：[原文](https://www.cnblogs.com/xhy-shine/p/11138183.html) · 2026-10 整理回填。
