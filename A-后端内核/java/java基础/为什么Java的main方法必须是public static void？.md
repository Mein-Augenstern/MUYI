Main 方法是我们学习 Java 编程语言时知道的第一个方法，你是否曾经想过为什么 main 方法是 public、static、void 的。当然，很多人首先学的是 C 和 C++，但是在 Java 中 main 方法与前者有些细微的不同，它不会返回任何值，为什么 main 方式是 public、static、void，这篇文章尝试去找到一些答案。

Main 方法是 Java 程序的入口，记住，我们这里不会讨论 Servlet、MIDlet 和其他任何容器管理的 java 程序，在 java 核心编程中，JVM 会查找类中的 public static void main(String[]args)，如果找不到该方法就抛出错误 NoSuchMethodError:main 程序终止。

Main 方法必须严格遵循它的语法规则，方法签名必须是 public static void，参数是字符串数组类型，如果是 Java1.5 及以后的版本还可以使用可变参数：

```java
public static void main(String... args)
```

为什么 main 方法是静态的（static）？
------

* 正因为 main 方法是静态的，JVM 调用这个方法就不需要创建任何包含这个 main 方法的实例。

* 因为 C 和 C++ 同样有类似的 main 方法作为程序执行的入口。

* 如果 main 方法不声明为静态的，JVM 就必须创建 main 类的实例，因为构造器可以被重载，JVM 就没法确定调用哪个 main 方法。

* 静态方法和静态数据加载到内存就可以直接调用而不需要像实例方法一样创建实例后才能调用，如果 main 方法是静态的，那么它就会被加载到 JVM 上下文中成为可执行的方法。

为什么main方法是公有的（public） ？
------

Java 指定了一些可访问的修饰符如：private、protected、public，任何方法或变量都可以声明为 public，Java 可以从该类之外的地方访问。因为 main 方法是公共的，JVM 就可以轻松的访问执行它。

为什么 main 方法没有返回值（Void）？
------

因为 main 返回任何值对程序都没任何意义，所以设计成 void，意味着 main 不会有任何值返回。

总结
------

* main 方法必须声明为 public、static、void，否则 JVM 没法运行程序 。

* 如果 JVM 找不到 main 方法就抛出 NoSuchMethodError:main 异常，例如：如果你运行命令：java HelloWrold，JVM 就会在 HelloWorld.class 文件中搜索 public static void main (String[] args) 方法。

* main 方式是程序的入口，程序执行的开始处。

* main 方法被一个特定的线程 ”main” 运行，程序会一直运行直到 main 线程结束或者 non-daemon 线程终止。

* 当你看到“Exception in Thread main”如：Excpetion in Thread main:Java.lang.NullPointedException，意味着异常来自于 main 线程。

* 你可以声明 main 方法使用 java1.5 的可变参数的方式如：

```java
public static void main(String... args)
```

* 除了 static、void、和 public，你可以使用 final，synchronized、和 strictfp 修饰符在 main 方法的签名中，如：

```java
public strictfp final synchronized static void main(String[] args)
```

* main 方法在 Java 可以像其他方法一样被重载，但是 JVM 只会调用上面这种签名规范的 main 方法。

* 你可以使用 throws 子句在方法签名中，可以抛出任何 checked 和 unchecked 异常。

* 静态初始化块在 JVM 调用 main 方法前被执行，它们在类被 JVM 加载到内存的时候就被执行了。
