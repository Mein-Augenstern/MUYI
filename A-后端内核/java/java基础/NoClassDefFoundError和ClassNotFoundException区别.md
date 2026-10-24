看在前面
====

> * <a href="https://blog.csdn.net/qq_33543634/article/details/81128096">NoClassDefFoundError</a>

两者是什么
====

`ClassNotFoundException` 和 `NoClassDefFoundError` 都发生在"运行期找不到类"的场景，但它们的类型、抛出时机和排查方向完全不同：

| 对比项 | ClassNotFoundException | NoClassDefFoundError |
| ---- | ---- | ---- |
| 继承体系 | `java.lang.Exception`（受检异常） | `java.lang.Error`（严重错误） |
| 抛出时机 | 显式加载类失败时**同步**抛出 | 隐式加载失败，或首次初始化失败后的**后续**访问 |
| 常见触发 | `Class.forName()`、`ClassLoader.loadClass()`、反射 | 编译期存在、运行期缺失的类被 new/引用；静态初始化失败 |
| 典型原因 | 依赖 jar 未引入、类名写错、ClassLoader 隔离 | jar 冲突/版本不一致、打包缺依赖、静态块初始化失败 |

1 ClassNotFoundException：主动加载找不到
------

`ClassNotFoundException` 是受检异常，必须显式捕获，出现在**程序主动按名字加载类**的路径上：

```java
// 类路径中没有 com.example.Missing 时抛出 ClassNotFoundException
Class<?> clazz = Class.forName("com.example.Missing");
```

常见原因与排查：

1. 依赖 jar 没有进入 classpath（Maven scope 写成 test/provided、打包脚本漏了 jar）；
2. 全限定类名拼错；
3. 多 ClassLoader 环境（Web 容器、OSGi、SPI）中，类明明存在但**当前 ClassLoader 可见的路径里没有**；
4. 排查手段：`mvn dependency:tree` 确认依赖是否进入 classpath。启动参数加 `-verbose:class` 观察类实际从哪个 jar 加载。

2 NoClassDefFoundError：编译期在、运行期没了
------

`NoClassDefFoundError` 是 Error：编译时类是存在的（否则编译都过不了）。运行期 JVM 隐式加载时却找不到，或该类**首次初始化已经失败过**：

```java
// 编译期依赖 Lib 类，运行期 classpath 缺 jar：
// 抛出 java.lang.NoClassDefFoundError: com/example/Lib
Lib lib = new Lib();
```

除 jar 缺失外，还有一个高频且隐蔽的原因——**静态初始化失败**：

```java
public class HasStaticBlock {
    static {
        int i = 1 / 0;   // 首次加载初始化时抛 ExceptionInInitializerError
    }
}

// 第一次 new HasStaticBlock()：ExceptionInInitializerError（Cause: ArithmeticException）
// 之后再 new HasStaticBlock()：NoClassDefFoundError:
//     Could not initialize class HasStaticBlock
```

JVM 对一个类的初始化（`<clinit>`，静态块与静态字段赋值）只执行一次。首次初始化抛出的 `ExceptionInInitializerError` 被吞掉后，后续所有对该类的访问都直接抛 `NoClassDefFoundError`。**遇到 NoClassDefFoundError 必须往前翻日志，找第一次的 ExceptionInInitializerError，那才是真正的病灶。**

`-verbose:class` 是区分两者最直观的手段，能看到每个类实际来自哪个 jar：

```bash
# 启动参数加 -verbose:class 后的输出样例：
# [Loaded com.example.Lib from file:/app/lib/lib-old.jar]
# 若预期应从 lib-new.jar 加载，即为典型的版本冲突
```

排查套路
====

1. 看完整类名：先确认缺的是业务类还是第三方库类；
2. 日志里有前置 `ExceptionInInitializerError` → 去修静态块/静态字段，而不是找 jar；
3. jar 缺失/冲突 → `dependency:tree`、`-verbose:class`、解压发布包逐一确认；
4. 容器/多 ClassLoader 环境 → 打印 `clazz.getClassLoader()` 确认类被哪个 loader 加载。常见原因是重复 jar 分属不同 loader，或 child-first 加载策略导致加载到错误版本；
5. 版本不一致 → 两个 jar 都有同名类，运行时加载到了旧版本（缺新方法/新字段）。常以 NoSuchMethodError 与 NoClassDefFoundError 混合出现。

一句话总结
====

`ClassNotFoundException` 是"我主动去 load，没 load 到"（受检异常，去找 classpath）。`NoClassDefFoundError` 是"编译时明明有，运行时 JVM 自己 load 不出来，或者初始化早就炸过"（Error，先查 ExceptionInInitializerError，再查 jar 缺失/冲突）。

> 原链接已失效，2026-10 依据主题重写。
