What（元注解）
====

元注解即用来描述注解的注解，比如以下代码中我们使用“@Target”元注解来说明MethodInfo这个注解只能用于对方法进行注解：

```java
@Target(ElementType.METHOD)
public @interface MethodInfo { 
    ...
}
```

下面我们来具体介绍一下几种元注解。

1 Retention
------

这个元注解表示一个注解会被保留到什么时候，比如以下代码表示Developer注解会被保留到运行时（也就是说在运行时依然能发挥作用）：

```java
@Retention(RetentionPolicy.RUNTIME)
public @interface Developer {
    String value();
}
```

@Retention元注解的定义如下：

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Retention {
    RetentionPolicy value();
}
```

我们从以上代码中可以看到，**定义注解使用@interface关键字**，这就好比我们定义类时使用class关键字，定义接口时使用interface关键字一样，注解也是一种类型。关于@Documented和@Target的含义，下面我们会进行介绍。

我们在使用@Retention时，后面括号里的内容即表示它的取值，从以上定义我们可以看到，取值的类型为RetentionPolicy，这是一个枚举类型，它可以取以下值：

* SOURCE：表示在编译时这个注解会被移除，不会包含在编译后产生的class文件中；

* CLASS：表示这个注解会被包含在class文件中，但在运行时会被移除；

* RUNTIME：表示这个注解会被保留到运行时，在运行时可以JVM访问到，我们可以在运行时通过反射解析这个注解。

2 Documented
------

当一个注解被@Documented元注解所修饰时，那么无论在哪里使用这个注解，都会被Javadoc工具文档化。我们来看一下它的定义：

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Documented {
}
```

这个元注解被@Documented修饰，表示它本身也会被文档化。@Retention元注解的值RetentionPolicy.RUNTIME表示@Documented这个注解能保留到运行时；@Target元注解的值ElementType.ANNOTATION_TYPE表示@Documented这个注解只能够用来修饰注解类型。

3 Inherited
------

表明被修饰的注解类型是自动继承的。如果你想让一个类和它的子类都包含某个注解，就可以使用@Inherited来修饰这个注解。也就是说，假设Parent类是Child类的父类，那么我们若用被@Inherited元注解所修饰的某个注解对Parent类进行了修饰，则相当于Child类也被该注解所修饰了。这个元注解的定义如下：

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Inherited {
}
```

我们可以看到这个元注解类型被@Documented所注解，能够保留到运行时，只能用来修饰注解类型。

4 Target
------

这个元注解说明了被修饰的注解的应用范围，也就是被修饰的注解可以用来注解哪些程序元素，它的定义如下：

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)public @interface Target {
    ElementType[] value();
}
```

从以上定义我们可以看到它也会保留到运行时，而且它的取值是为ElementType[]类型（一个数组，意思是可以指定多个值），ElementType是一个枚举类型，它可以取以下值：

* TYPE：表示可以用来修饰类、接口、注解类型或枚举类型；

* PACKAGE：可以用来修饰包；

* PARAMETER：可以用来修饰参数；

* ANNOTATION_TYPE：可以用来修饰注解类型；

* METHOD：可以用来修饰方法；

* FIELD：可以用来修饰属性（包括枚举常量）；

* CONSTRUCTOR：可以用来修饰构造器；

* LOCAL_VARIABLE：可用来修饰局部变量。
