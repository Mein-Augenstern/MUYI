Java中创建一个数组的过程
====

> outside-reading 深度篇。从 `new int[10]` 这一行代码出发，拆解 JVM 层面创建数组的完整过程。完整链条：**类加载检查 → 内存分配 → 默认初始化（零值填充）→（静态初始化时的逐个赋值）**。最后落到一个面试高频的工程优化：零长度数组。

一、从一行代码说起
------

```java
int[] a = new int[10];
```

这行代码在 JVM 层面做的事情，很多人能答"在堆上开了个空间"，但完整的链条是：

```
1. 解析/加载检查：数组类从哪来？元素类型是否已加载？
2. 字节码执行：newarray 指令
3. 内存分配：计算大小 → TLAB 中指针碰撞分配
4. 默认初始化：整块内存零值填充
5. （若为静态初始化 {1,2,3}）逐个元素赋值
6. 引用入栈：栈变量指向堆上数组对象
```

注意一个反直觉的点：**数组也是对象**。它有对象头、有自己的"类"（`[I`、`[Ljava.lang.String;` 这种 JVM 内部类名）。遵循对象分配的一般规律，但又有几处与普通对象不同的特殊待遇。

二、类加载阶段：数组类是个"特殊公民"
------

JVM 规范（JLS §12.2 / JVMS §5.3.3）明确规定：

* **数组类不是由类加载器创建的，而是由 Java 虚拟机直接创建的**。`Class.forName` 加载不到 `[I`，任何一个类加载器都没有 `int[].class` 的字节码——因为根本没有对应的 class 文件。
* 但数组类的**创建过程与类加载器密切相关**。创建引用类型数组（`new String[10]`）时，JVM 会先确保元素类型 `String` 已被对应的类加载器**加载（loading + linking）**。注意触发的是加载，而不是初始化（initialization）——`new Foo[10]` 并不会执行 Foo 的静态初始化块。
* 基本类型数组（`int[]`）连这一步都省了，无任何类加载参与。

验证：

```java
public class Main {
    static class Foo {
        static { System.out.println("Foo clinit 执行"); }
    }
    public static void main(String[] args) throws Exception {
        Foo[] arr = new Foo[10];          // 不打印！只触发 Foo 的加载，不触发初始化
        System.out.println(arr.length);
        Foo f = new Foo();                // 这时才打印
        System.out.println(int[].class.getName());   // 输出 [I
        System.out.println(String[].class.getName()); // 输出 [Ljava.lang.String;
    }
}
```

这也解释了面试题"new 一个数组会触发类加载吗"的准确答案。**数组类本身永远不走类加载器。引用类型数组的创建会触发元素类的加载但不触发其初始化**。

三、字节码层面：三条数组创建指令
------

用 `javap -c` 反编译可以看到数组创建对应**三条专门指令**（普通对象只有 new）：

```java
int[] a = new int[10];              // newarray       (atype=10 即 int)
String[] b = new String[10];        // anewarray      (指向 String 类的常量池索引)
int[][] c = new int[3][4];          // multianewarray (多维，一次分配多维骨架)
```

```
 0: bipush        10
 2: newarray      int          // 单条指令完成"分配+清零"，直接得到引用
 4: astore_1
```

要点：

* `newarray`/`anewarray` 是**原子指令**：分配、零值初始化在这条指令内完成，不会出现"拿到未初始化的内存"。
* 对比普通对象：`new Foo` 只分配+零值，构造逻辑在随后的 `invokespecial <init>` 里。这也是对象半初始化（this 逃逸）风险的来源。而数组没有构造器，不存在这个问题。
* `multianewarray new int[3][4]` 一次分配出 3 个内层数组的完整骨架；而 `new int[3][]` 只分配外层（`anewarray [I`），内层数组需要逐个再创建。

四、内存分配：TLAB 与指针碰撞
------

4.1 数组的对象结构
------

HotSpot 下（启用压缩指针，64 位 JVM 的默认配置）一个数组对象在堆里的布局：

```
+--------------------+ ← 起始地址
| Mark Word     (8B) |   哈希码/GC 分代年龄/锁状态
| Klass 指针    (4B) |   指向数组类 [I 的元数据（压缩后 4 字节）
| length        (4B) |   数组长度——数组对象头独有的字段
+--------------------+
| element[0]         |
| element[1]         |
| ...                |
+--------------------+ ← 按 8 字节对齐（不足补 padding）
```

**长度字段是数组对象区别于普通对象的标志**——它存在对象头里。所以 `.length` 是 JVM 直接读字段（`arraylength` 指令），不走方法调用，也没有 length() 括号。也正因为 length 在对象头，数组一旦创建长度不可变。

4.2 分配过程
------

1）**大小计算**：`对象头(16B) + length × 元素大小`，再对齐到 8 字节边界。所以 `new int[0]` 也要占 16 字节——零长度数组并非"不占内存"，只是大小恒定。

2）**分配方式**（与普通对象相同的快速路径）：

* **TLAB（Thread Local Allocation Buffer）**：每个线程在 Eden 里预划一块私有缓冲区。数组在 TLAB 内通过**指针碰撞（bump-the-pointer）**分配——指针加上对象大小就完成，无需全局同步。绝大多数数组走这条路。
* 数组太大（超过 TLAB 剩余空间较多时）。可能直接在 TLAB 外/Eden 或老年代分配（阈值与 `TLAB Waste` 策略相关）。超大数组（超过虚拟机上限直接抛 `OutOfMemoryError: Requested array size exceeds VM limit`）。
* TLAB 放不下时的慢路径：进入基于 CAS/锁的 Eden 分配 → 仍失败触发 Young GC。

3）**内存清零**：分配成功后，JVM 把这块内存**整体清零**。清零一方面是安全要求，不能读到上一个对象残留的数据。另一方面，清零正是下一节"默认初始化"的实现。

注意：**数组作为整体不会被逃逸分析的标量替换拆散**。不像小对象可以被分解成标量寄存在栈/寄存器上。数组分配绝大多数真实发生在堆上——大数组是 GC 的实打实压力来源。

五、默认初始化：零值从哪来
------

`new int[10]` 之后 a[0]~a[9] 全是 0，这个 0 来自第四步的内存清零，各类型零值：

| 元素类型 | 零值 |
|---|---|
| byte / short / int / long | 0 |
| float / double | 0.0 |
| char | '\u0000' |
| boolean | false |
| 引用类型 | null |

几个由此派生的经典问题：

* `new boolean[10]` 的元素默认 false 而不是"未定义"，原因：清零语义保证了确定性。
* `int[] a;` 之后直接用 `a.length` 会遇到编译不过/编译能过但运行 NPE 两种情况。区别在于：局部变量声明只是栈上引用槽位，**未指向任何数组对象**，没有默认初始化可言。成员变量 `int[] a` 作为引用类型字段，堆上字段区清零后默认是 null。
* 静态初始化和默认初始化的顺序：

```java
int[] a = new int[3];        // 默认初始化：a = [0,0,0]
int[] b = {1, 2, 3};         // 语法糖：编译后仍是 new int[3] + 逐个 iastore 填充
int[] c = new int[]{1, 2, 3}; // 同上
```

`{1,2,3}` 的字节码：

```
 0: iconst_3
 1: newarray int
 3: dup                       // 复制引用备用
 4: iconst_0 / iconst_1 / bipush 2
 ...
 7: iastore                   // 逐个写入
```

即：**先走默认初始化得到 [0,0,0]，再逐个覆盖**。初始值来自常量/计算表达式都行，所以静态初始化本质是"默认初始化 + 显式赋值"两步。

六、零长度数组优化
------

6.1 现象与用法
------

`new int[0]`、`new String[0]` 这类零长度数组在 JDK 与框架源码里被反复用作**共享的不可变空单例**：

```java
// ArrayList 源码（JDK8）：两个共享空数组
private static final Object[] EMPTY_ELEMENTDATA = {};
private static final Object[] DEFAULTCAPACITY_EMPTY_ELEMENTDATA = {};

public ArrayList(int initialCapacity) {
    if (initialCapacity > 0) {
        this.elementData = new Object[initialCapacity];
    } else if (initialCapacity == 0) {
        this.elementData = EMPTY_ELEMENTDATA;   // 不 new，复用常量
    } else {
        throw new IllegalArgumentException(...);
    }
}
// JDK7 的 HashMap 也有 EMPTY_TABLE；commons-lang3 有 ArrayUtils.EMPTY_STRING_ARRAY 等
```

6.2 为什么值得优化
------

* **语义优于 null**：方法返回 `EMPTY_ARRAY` 而不是 null，调用方直接遍历/取 size，不必判空——消灭一整类 NPE。Effective Java 第 54 条：返回零长度数组/集合，不要返回 null。
* **天然线程安全且免分配**：零长度数组**长度为 0，不可能被写入任何元素**，因此逻辑上不可变。全局共享一个实例没有任何并发问题。热点路径上反复 `new int[0]`（各占 16 字节）虽然 TLAB 分配很快，但共享单例连这点分配和 GC 压力都省了。
* **反射/泛型场景刚需**：`toArray` 的坑——`list.toArray(new String[0])` 在 JDK8+ 被官方推荐为最优写法。JDK11 后内部有优化，不必再传精确长度的数组。泛型擦除后 `T[]` 需要零长度数组做类型标记（如 `EnumSet.noneOf` 内部用 `new Enum[0]` 之类手段）。

6.3 复用时的唯一注意点
------

共享的前提是**逻辑不可变**。如果持有方会向数组"原地写入"元素，就不能共享。写一个长度为 0 的数组本来也会越界异常，所以风险实际很小。但若类型是集合视图则要小心。ArrayList 敢共享 `EMPTY_ELEMENTDATA`，是因为 add 时走 `Arrays.copyOf` 扩容到新数组，**绝不原地写共享数组**。设计自己的共享空数组时，确认写入路径是"复制式"而非"原地式"。

6.4 与 Collections.emptyList() 的关系
------

同样的思想在集合侧是 `Collections.emptyList()` / `EMPTY_LIST`：零长度集合也是不可变单例。两者是一件事在不同层面的应用——**用不可变的空单例替代 null 和重复分配**。

七、全流程串联
------

最后把 `new String[3]` 的完整过程串成一条线：

```
编译期   ：生成 anewarray 指令（引用 String 的常量池入口）
运行期 1 ：触发 String 类的加载与链接（不触发 <clinit> 初始化）
        ：数组类 [Ljava/lang/String; 由 JVM 直接创建（无类加载器参与）
运行期 2 ：计算大小 = 16B 头 + 3 × 4B（压缩指针下引用 4B）→ 对齐 8B = 32B
运行期 3 ：TLAB 内指针碰撞分配；空间不足走慢路径或触发 Young GC
运行期 4 ：整块内存清零 → 三个槽位均为 null（默认初始化完成）
运行期 5 ：astore，栈/局部变量表的引用 a 指向堆上数组
之后     ：length 直接读对象头；元素被显式赋值前读到 null
```

面试角度的三个关键结论：

1）**数组类不经类加载器**，由 JVM 直接创建。引用数组创建触发元素类加载，但不触发初始化。
2）**newarray/anewarray 是原子指令**，分配与零值填充一次完成。数组没有构造器，因此不存在半初始化问题。静态初始化 `{...}` 是"先清零、后逐个 iastore"。
3）**零长度数组是不可变空单例**：优于 null 的返回语义 + 免分配。ArrayList 的 `EMPTY_ELEMENTDATA` 是教科书应用。

> 2026-10 依据主题重写（原文件为空壳）。
