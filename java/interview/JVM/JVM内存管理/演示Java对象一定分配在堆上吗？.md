看在前面
====

> * <a href="https://my.oschina.net/u/3015633/blog/3133017">原创|面试官：Java对象一定分配在堆上吗？</a>

最近在看 Java 虚拟机方面的资料，以备工作中的不时之需。首先我先抛出一个我自己想的面试题，然后再引出后面要介绍的知识点如逃逸分析、标量替换、栈上分配等知识点

面试题
====

> Java 对象一定分配在堆上吗？

自己先思考下，再往下阅读效果更佳哦！

分析
====

我们都知道 Java 对象一般分配在堆上，而堆空间又是所有线程共享的。了解 NIO 库的朋友应该知道还有一种是堆外内存也叫直接内存。直接内存是直接向操作系统申请的内存区域，访问直接内存的速度一般会优于堆内存。直接内存的大小不直接受 Xmx 设定的值限制，但是在使用的时候也要注意，毕竟系统内存有限，堆内存和直接内存的总和依然还是会受操作系统的内存限制的。

通过上面的分析，大家也知道了，Java 对象除了可以分配在堆上，还可以直接分配在堆外内存中。但这点不是我今天想讨论的，我想和大家聊聊栈上分配，说到栈上分配就不得不先说下**逃逸分析**

逃逸分析
====

逃逸分析是是一种动态确定指针动态范围的静态分析，它可以分析在程序的哪些地方可以访问到指针。

换句话说，**逃逸分析的目的是判断对象的作用域是否有可能逃出方法体**

判断依据有两个

1. 对象是否被存入堆中（静态字段或堆中对象的实例字段）

2. 对象是否被传入未知代码中（方法的调用者和参数）

我们来分析下这两个依据

对于第一点对象是否被存入堆中，我们知道堆内存是线程共享的，一旦对象被分配在堆中，那所有线程都可以访问到该对象，这样即时编译器就追踪不到所有使用到该对象的地方了，这样的对象就属于逃逸对象，如下所示

```java
public class Escape {
    private static User u;
    public static void alloc() {
        u = new User(1, "baiya");
    }
}
```

User 对象属于类 Escape 的成员变量，该对象是可能被所有线程访问的，所以会发生逃逸

第二点是对象是否被传入未知代码中，Java 的即时编译器是以方法为单位进行编译，即时编译器会把方法中未被内联的方法当成未知代码，所以无法判断这个未知方法的方法调用会不会将调用者或参数放到堆中，所以认为方法的调用者和参数是逃逸的，如下所示

```java
public class Escape {
    private static User u; 
    public static void alloc(User user) {
        u = user;
    }
}
```

方法 alloc 的参数 user 被赋值给类 Escape 的成员变量 u，所以也会被所有线程访问，也是会发生逃逸的。

栈上分配
====

栈上分配是 Java 虚拟机提供的一种优化技术，该技术的基本思想是可以将线程私有的对象打散，分配到栈上，而非堆上。那分配到栈上有什么好处呢？ 我们知道栈中的变量会在方法调用结束后自动销毁，所以省掉了 jvm 进行垃圾回收，进而可以提高系统的性能

栈上分配是要基于**逃逸分析**和**标量替换**实现的

我们通过一个具体的例子来验证下非逃逸分析的对象确实是分配到了栈上

```java
public class OnStack {
    public static void alloc() {
        User user = new User(1, "baiya");
    }
    public static void main(String[] args) {
        long start = Instant.now().toEpochMilli();
        for (int i = 0; i < 100_000_000; i++) {
            alloc();
        }
        long end = Instant.now().toEpochMilli();
        System.out.println("耗时：" + (end - start));
    }
}
```

上面的代码是循环 1 亿次执行 alloc 方法创建 User 对象，每个 User 对象占用约 16 bytes（怎么计算的下面会说） 空间，创建 1 亿次，所以如果 User 都是在堆上分配的话则需要 1.5G 的内存空间。如果我们设置堆空间小于这个数，应该会发生 gc，如果设置的特别小，应该会发生大量的 gc。

我们用下面的参数执行上述代码

> ```-server -Xmx10m -Xms10m -XX:+DoEscapeAnalysis -XX:+PrintGCDetails -XX:+EliminateAllocations```
其中 -server 是开启 server 模式，逃逸分析需要 server 模式的支持
-Xmx10 -Xms10m，设置堆内存是 10m，远小于 1.5G
-XX:+DoEscapeAnalysis 开启逃逸分析
-XX:+PrintGCDetails 如果发生 gc，打印 gc 日志
-XX:+EliminateAllocations 开启标量替换，允许把对象打散分配在栈上，比如 User 对象，它有两个属性 id 和 name，可以把他们看成独立的局部变量分别进行分配

配置好 jvm 参数后，执行代码，查看结果可知执行了 3 次 gc，耗时 10 毫秒，可以推断出 User 对象并未全部分配到堆上，而是把绝大多数分配到了栈上，**分配在栈上的好处是方法结束后自动释放对应的内存，是一种优化手段**。

```java
[GC (Allocation Failure) [PSYoungGen: 2048K->496K(2560K)] 2048K->664K(9728K), 0.0120617 secs] [Times: user=0.01 sys=0.00, real=0.01 secs] 
[GC (Allocation Failure) [PSYoungGen: 2544K->512K(2560K)] 2712K->696K(9728K), 0.0092794 secs] [Times: user=0.00 sys=0.00, real=0.01 secs] 
耗时:27
Heap
 PSYoungGen      total 2560K, used 689K [0x00000000ffd00000, 0x0000000100000000, 0x0000000100000000)
  eden space 2048K, 8% used [0x00000000ffd00000,0x00000000ffd2c458,0x00000000fff00000)
  from space 512K, 100% used [0x00000000fff80000,0x0000000100000000,0x0000000100000000)
  to   space 512K, 0% used [0x00000000fff00000,0x00000000fff00000,0x00000000fff80000)
 ParOldGen       total 7168K, used 184K [0x00000000ff600000, 0x00000000ffd00000, 0x00000000ffd00000)
  object space 7168K, 2% used [0x00000000ff600000,0x00000000ff62e010,0x00000000ffd00000)
 Metaspace       used 3351K, capacity 4500K, committed 4864K, reserved 1056768K
  class space    used 364K, capacity 388K, committed 512K, reserved 1048576K

Process finished with exit code 0
```

我们上面说了栈上分配依赖逃逸分析和标量替换，那么我们可以破坏其中任意一个条件，去掉逃逸分析就可以通过 -XX:-DoEscapteAnalysis 或者关闭标量替换 -XX:-EliminateAllocations 再去执行上述代码，观察执行情况，发现发生了大量的 gc，并且耗时 3182 毫秒，执行时间远远高于上面的 10 毫秒，所以可以推测出并未执行栈上分配的优化手段。JVM参数：```-server -Xmx10m -Xms10m -XX:-DoEscapeAnalysis -XX:+PrintGCDetails -XX:-EliminateAllocations```

```java
[GC (Allocation Failure) [PSYoungGen: 2048K->0K(2560K)] 3913K->1865K(9728K), 0.0002287 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[GC (Allocation Failure) [PSYoungGen: 2048K->0K(2560K)] 3913K->1865K(9728K), 0.0002393 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[GC (Allocation Failure) [PSYoungGen: 2048K->0K(2560K)] 3913K->1865K(9728K), 0.0002220 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[GC (Allocation Failure) [PSYoungGen: 2048K->0K(2560K)] 3913K->1865K(9728K), 0.0002201 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
...
...
...
[GC (Allocation Failure) [PSYoungGen: 2048K->0K(2560K)] 3913K->1865K(9728K), 0.0002303 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[GC (Allocation Failure) [PSYoungGen: 2048K->0K(2560K)] 3913K->1865K(9728K), 0.0002351 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[GC (Allocation Failure) [PSYoungGen: 2048K->0K(2560K)] 3913K->1865K(9728K), 0.0002088 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[GC (Allocation Failure) [PSYoungGen: 2048K->0K(2560K)] 3913K->1865K(9728K), 0.0002095 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[GC (Allocation Failure) [PSYoungGen: 2048K->0K(2560K)] 3913K->1865K(9728K), 0.0002156 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[GC (Allocation Failure) [PSYoungGen: 2048K->0K(2560K)] 3913K->1865K(9728K), 0.0002034 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[GC (Allocation Failure) [PSYoungGen: 2048K->0K(2560K)] 3913K->1865K(9728K), 0.0001591 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[GC (Allocation Failure) [PSYoungGen: 2048K->0K(2560K)] 3913K->1865K(9728K), 0.0002095 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
[GC (Allocation Failure) [PSYoungGen: 2048K->0K(2560K)] 3913K->1865K(9728K), 0.0002111 secs] [Times: user=0.00 sys=0.00, real=0.00 secs] 
耗时:17461
Heap
 PSYoungGen      total 2560K, used 1638K [0x00000000ffd00000, 0x0000000100000000, 0x0000000100000000)
  eden space 2048K, 79% used [0x00000000ffd00000,0x00000000ffe998d8,0x00000000fff00000)
  from space 512K, 0% used [0x00000000fff00000,0x00000000fff00000,0x00000000fff80000)
  to   space 512K, 0% used [0x00000000fff80000,0x00000000fff80000,0x0000000100000000)
 ParOldGen       total 7168K, used 1865K [0x00000000ff600000, 0x00000000ffd00000, 0x00000000ffd00000)
  object space 7168K, 26% used [0x00000000ff600000,0x00000000ff7d2468,0x00000000ffd00000)
 Metaspace       used 3852K, capacity 4540K, committed 4864K, reserved 1056768K
  class space    used 424K, capacity 428K, committed 512K, reserved 1048576K
```

计算 User 对象占用空间大小
====

对象由四部分构成

1. 对象头：记录一个对象的实例名字、ID和实例状态。

  普通对象占用 8 bytes，数组占用 12 bytes （8 bytes 的普通对象头 + 4 bytes 的数组长度）

2. 基本类型

  boolean,byte 占用 1 byte

  char,short 占用 2 bytes

  int,float 占用 4 bytes

  long,double 占用 8 bytes

3. 引用类型：每个引用类型占用 4 bytes

4. 填充物：以 8 的倍数计算，不足 8 的倍数会自动补齐

我们上面的 User 对象有两个属性，一个 int 类型的 id 占用 4 bytes，一个引用类型的 name 占用 4bytes，在加上 8 bytes 的对象头，正好是 16 bytes

总结
====

关于虚拟机的知识点还有很多而且也比较重要，如果懂对写优质代码、优化性能、排查问题等都是锦上添花，比如逃逸分析，即时编译器会根据逃逸分析的结果进行优化，如所消除以及标量替换。感兴趣的朋友可以自己查查资料学习下。通过这个栈上分配的例子，以后我们写代码时，把可以不逃逸的对象写进方法体中，这样就会被编译器优化，提升性能。而且也知道了上面面试题的答案，就是 Java 中的对象并一定分配在堆上，也可能分配在栈上

参考资料
====

1. 《实战Java虚拟机》

2. 《深入理解Java虚拟机》

3. https://zh.wikipedia.org/wiki/%E9%80%83%E9%80%B8%E5%88%86%E6%9E%90

