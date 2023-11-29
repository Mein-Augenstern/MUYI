## 看在前面

> * <a href="https://github.com/Snailclimb/JavaGuide/blob/master/docs/java/jvm/Java%E5%86%85%E5%AD%98%E5%8C%BA%E5%9F%9F.md">Java内存区域</a>
> * 《深入理解 Java 虚拟机：JVM 高级特性与最佳实践（第二版》
> * 《实战 java 虚拟机》
> * https://docs.oracle.com/javase/specs/index.html
> * http://www.pointsoftware.ch/en/under-the-hood-runtime-data-areas-javas-memory-model/
> * https://dzone.com/articles/jvm-permgen-%E2%80%93-where-art-thou
> * https://stackoverflow.com/questions/9095748/method-area-and-permgen
> * 深入解析String#internhttps://tech.meituan.com/2014/03/06/in-depth-understanding-string-intern.html

## 三 HotSpot 虚拟机对象探秘

通过上面的介绍我们大概知道了虚拟机的内存情况，下面我们来详细的了解一下 HotSpot 虚拟机在 Java 堆中对象分配、布局和访问的全过程。

### 3.1 对象的创建

下图便是 Java 对象的创建过程，我建议最好是能默写出来，并且要掌握每一步在做什么。

![Java对象创建过程](https://github.com/Mein-Augenstern/MUYI/blob/master/java/interview/picture/java%E5%AF%B9%E8%B1%A1%E5%88%9B%E5%BB%BA%E8%BF%87%E7%A8%8B.png)

**Step1:类加载检查**

虚拟机遇到一条 new 指令时，首先将去检查这个指令的参数是否能在常量池中定位到这个类的符号引用，并且检查这个符号引用代表的类是否已被加载过、解析和初始化过。如果没有，那必须先执行相应的类加载过程。

**Step2:分配内存**

在**类加载检查**通过后，接下来虚拟机将为新生对象**分配内存**。对象所需的内存大小在类加载完成后便可确定，为对象分配空间的任务等同于把一块确定大小的内存从 Java 堆中划分出来。**分配方式**有 “**指针碰撞**” 和 “**空闲列表**” 两种，**选择那种分配方式由 Java 堆是否规整决定，而 Java 堆是否规整又由所采用的垃圾收集器是否带有压缩整理功能决定**。

内存分配的两种方式：

* **指针碰撞 (Bump the Pointer)**：这种方式适用于带有压缩功能的垃圾收集器，如Minor GC中的新生代（Young Generation）。在这种分配策略中，所有用过的内存放在一边，空闲的内存放在另一边，中间有一个指针分界。当分配一个新对象时，只需要移动这个指针即可，从而快速完成分配。但是这种方式要求内存区域必须是连续的，所以需要定期的内存压缩来整理内存碎片。
    
    * **指针碰撞内存分配策略不适合在没有压缩功能的垃圾收集器么**？：指针碰撞（Bump the Pointer）内存分配策略确实更适合在具有压缩功能的垃圾收集器中使用，因为这种策略依赖于内存区域的连续性。在进行对象分配时，只需将指针从已分配内存的末端向空闲内存的方向移动所需的大小即可。这种方式简单快速，但随着对象的分配和回收，会出现内存碎片。如果垃圾收集器没有压缩（Compaction）功能，那么随着时间的推移，内存碎片会越来越多，这会导致即使堆中还有足够的总空间，也可能找不到连续的空间来分配较大的对象。这种情况被称为“内存碎片化”。在没有压缩功能的垃圾收集器中，通常使用空闲列表（Free List）策略来管理内存。通过维护一个所有空闲内存块的列表，垃圾收集器可以在需要分配新对象时，查找并重用适合大小的空闲块。这种方式虽然处理内存分配请求的速度相对较慢，并且在维护空闲列表时需要更多的计算，但它可以更有效地应对内存碎片化问题，使得内存利用率更高。
    * ![image](https://github.com/Mein-Augenstern/MUYI/assets/34135120/b592ffc9-d879-42fd-bd09-7d03967dd402)

* **空闲列表 (Free List)**：这种方式适用于老年代（Old Generation）或堆内存中没有进行压缩的区域。在这种策略中，堆内存中所有的空闲内存块都会被维护在一个列表中。当需要分配内存时，垃圾收集器会遍历这个列表，寻找一个足够大的空闲内存块来存放新对象。这种方式的优点是可以更灵活地利用内存空间，但是分配速度相对较慢，并且可能会有更多的内存碎片。
    * ![image](https://github.com/Mein-Augenstern/MUYI/assets/34135120/0b3b3402-59e8-4085-8cb2-bcbfa69d929d)

选择以上两种方式中的哪一种，取决于 Java 堆内存是否规整。而 Java 堆内存是否规整，取决于 GC 收集器的算法是"标记-清除"，还是"标记-整理"（也称作"标记-压缩"），值得注意的是，复制算法内存也是规整的

![内存分配的两种方式](https://github.com/Mein-Augenstern/MUYI/blob/master/java/interview/picture/%E5%86%85%E5%AD%98%E5%88%86%E9%85%8D%E7%9A%84%E4%B8%A4%E7%A7%8D%E6%96%B9%E5%BC%8F.png)

**内存分配并发问题**

在创建对象的时候有一个很重要的问题，就是线程安全，因为在实际开发过程中，创建对象是很频繁的事情，作为虚拟机来说，必须要保证线程是安全的，通常来讲，虚拟机采用两种方式来保证线程安全：

* **CAS+失败重试**：CAS 是乐观锁的一种实现方式。所谓乐观锁就是，每次不加锁而是假设没有冲突而去完成某项操作，如果因为冲突失败就重试，直到成功为止。**虚拟机采用 CAS 配上失败重试的方式保证更新操作的原子性**。

* **TLAB**：为每一个线程预先在 Eden 区分配一块儿内存，JVM 在给线程中的对象分配内存时，首先在 TLAB 分配，当对象大于 TLAB 中的剩余内存或 TLAB 的内存已用尽时，再采用上述的 CAS 进行内存分配

**Step3:初始化零值**

内存分配完成后，虚拟机需要将分配到的内存空间都初始化为零值（不包括对象头），这一步操作保证了对象的实例字段在 Java 代码中可以不赋初始值就直接使用，程序能访问到这些字段的数据类型所对应的零值。

**Step4:设置对象头**

初始化零值完成之后，**虚拟机要对对象进行必要的设置**，例如这个对象是那个类的实例、如何才能找到类的元数据信息、对象的哈希码、对象的 GC 分代年龄等信息。 **这些信息存放在对象头中**。 另外，根据虚拟机当前运行状态的不同，如是否启用偏向锁等，对象头会有不同的设置方式。

**Step5:执行 init 方法**

在上面工作都完成之后，从虚拟机的视角来看，一个新的对象已经产生了，但从 Java 程序的视角来看，对象创建才刚开始，```<init>``` 方法还没有执行，所有的字段都还为零。所以一般来说，执行 new 指令之后会接着执行 ```<init>``` 方法，把对象按照程序员的意愿进行初始化，这样一个真正可用的对象才算完全产生出来。

**补充阅读**

* 求你了，别再说Java对象都是在堆内存上分配空间的了！：https://www.hollischuang.com/archives/4521

### 3.2 对象的内存布局

在 Hotspot 虚拟机中，对象在内存中的布局可以分为 3 块区域：**对象头**、**实例数据**和**对齐填充**。

**Hotspot 虚拟机的对象头包括两部分信息，第一部分用于存储对象自身的运行时数据**（哈希码、GC 分代年龄、锁状态标志等等），**另一部分是类型指针**，即对象指向它的类元数据的指针，虚拟机通过这个指针来确定这个对象是那个类的实例。

**实例数据部分是对象真正存储的有效信息**，也是在程序中所定义的各种类型的字段内容。

**对齐填充部分不是必然存在的，也没有什么特别的含义，仅仅起占位作用**。 因为 Hotspot 虚拟机的自动内存管理系统要求对象起始地址必须是 8 字节的整数倍，换句话说就是对象的大小必须是 8 字节的整数倍。而对象头部分正好是 8 字节的倍数（1 倍或 2 倍），因此，当对象实例数据部分没有对齐时，就需要通过对齐填充来补全。

### 3.3 对象的访问定位

建立对象就是为了使用对象，我们的 Java 程序通过栈上的 reference 数据来操作堆上的具体对象。对象的访问方式由虚拟机实现而定，目前主流的访问方式有**①使用句柄**和**②直接指针**两种：

1. **句柄**： 如果使用句柄的话，那么 Java 堆中将会划分出一块内存来作为句柄池，reference 中存储的就是对象的句柄地址，而句柄中包含了对象实例数据与类型数据各自的具体地址信息；

![对象的访问定位-使用句柄](https://github.com/Mein-Augenstern/MUYI/blob/master/java/interview/picture/%E5%AF%B9%E8%B1%A1%E7%9A%84%E8%AE%BF%E9%97%AE%E5%AE%9A%E4%BD%8D-%E5%8F%A5%E6%9F%84.png)

2. **直接指针**： 如果使用直接指针访问，那么 Java 堆对象的布局中就必须考虑如何放置访问类型数据的相关信息，而 reference 中存储的直接就是对象的地址。

![对象的访问定位-直接指针](https://github.com/Mein-Augenstern/MUYI/blob/master/java/interview/picture/%E5%AF%B9%E8%B1%A1%E7%9A%84%E8%AE%BF%E9%97%AE%E5%AE%9A%E4%BD%8D-%E7%9B%B4%E6%8E%A5%E6%8C%87%E9%92%88.png)

**这两种对象访问方式各有优势。使用句柄来访问的最大好处是 reference 中存储的是稳定的句柄地址，在对象被移动时只会改变句柄中的实例数据指针，而 reference 本身不需要修改。使用直接指针访问方式最大的好处就是速度快，它节省了一次指针定位的时间开销**。


## 四 重点补充内容

### 4.1 String 类和常量池

**String 对象的两种创建方式**：

```java
String str1 = "abcd";//先检查字符串常量池中有没有"abcd"，如果字符串常量池中没有，则创建一个，然后 str1 指向字符串常量池中的对象，如果有，则直接将 str1 指向"abcd""；
String str2 = new String("abcd");//堆中创建一个新的对象
String str3 = new String("abcd");//堆中创建一个新的对象
System.out.println(str1==str2);//false
System.out.println(str2==str3);//false
```

这两种不同的创建方法是有差别的。

* 第一种方式是在常量池中拿对象；
* 第二种方式是直接在堆内存空间创建一个新的对象。

记住一点：**只要使用 new 方法，便需要创建新的对象**。

再给大家一个图应该更容易理解，图片来源：https://www.journaldev.com/797/what-is-java-string-pool

![String-Pool-Java](https://github.com/Mein-Augenstern/MUYI/blob/master/java/interview/picture/String%E7%B1%BB%E5%92%8C%E5%B8%B8%E9%87%8F%E6%B1%A0.png)

**String 类型的常量池比较特殊。它的主要使用方法有两种**：

* 直接使用双引号声明出来的 String 对象会直接存储在常量池中。
* 如果不是用双引号声明的 String 对象，可以使用 String 提供的 intern 方法。String.intern() 是一个 Native 方法，它的作用是：如果运行时常量池中已经包含一个等于此 String 对象内容的字符串，则返回常量池中该字符串的引用；如果没有，JDK1.7之前（不包含1.7）的处理方式是在常量池中创建与此 String 内容相同的字符串，并返回常量池中创建的字符串的引用，JDK1.7以及之后的处理方式是在常量池中记录此字符串的引用，并返回该引用。

```java
String s1 = new String("计算机");
String s2 = s1.intern();
String s3 = "计算机";
System.out.println(s2);//计算机
System.out.println(s1 == s2);//false，因为一个是堆内存中的 String 对象一个是常量池中的 String 对象，
System.out.println(s3 == s2);//true，因为两个都是常量池中的 String 对象
```

**字符串拼接**:

```java
String str1 = "str";
String str2 = "ing";

String str3 = "str" + "ing";//常量池中的对象
String str4 = str1 + str2; //在堆上创建的新的对象	  
String str5 = "string";//常量池中的对象
System.out.println(str3 == str4);//false
System.out.println(str3 == str5);//true
System.out.println(str4 == str5);//false
```

![字符串拼接](https://github.com/Mein-Augenstern/MUYI/blob/master/java/interview/picture/%E5%AD%97%E7%AC%A6%E4%B8%B2%E6%8B%BC%E6%8E%A5.png)

尽量避免多个字符串拼接，因为这样会重新创建对象。如果需要改变字符串的话，可以使用 StringBuilder 或者 StringBuffer。

## 4.2 String s1 = new String("abc");这句话创建了几个字符串对象？

**将创建 1 或 2 个字符串。如果池中已存在字符串常量“abc”，则只会在堆空间创建一个字符串常量“abc”。如果池中没有字符串常量“abc”，那么它将首先在池中创建，然后在堆空间中创建，因此将创建总共 2 个字符串对象**。

验证：

```java
String s1 = new String("abc");// 堆内存的地址值
String s2 = "abc";
System.out.println(s1 == s2);// 输出 false,因为一个是堆内存，一个是常量池的内存，故两者是不同的。
System.out.println(s1.equals(s2));// 输出 true
```
结果：

```java
false
true
```

## 4.3 8 种基本类型的包装类和常量池

**Java 基本类型的包装类的大部分都实现了常量池技术，即 Byte,Short,Integer,Long,Character,Boolean；前面 4 种包装类默认创建了数值[-128，127] 的相应类型的缓存数据，Character创建了数值在[0,127]范围的缓存数据，Boolean 直接返回True Or False。如果超出对应范围仍然会去创建新的对象**。为啥把缓存设置为[-128，127]区间？（<a href="https://github.com/Snailclimb/JavaGuide/issues/461">参见issue/461</a>）性能和资源之间的权衡。

```java
public static Boolean valueOf(boolean b) {
    return (b ? TRUE : FALSE);
}
```

```java
private static class CharacterCache {         
    private CharacterCache(){}
          
    static final Character cache[] = new Character[127 + 1];          
    static {             
        for (int i = 0; i < cache.length; i++)                 
            cache[i] = new Character((char)i);         
    }   
}
```

**两种浮点数类型的包装类 Float,Double 并没有实现常量池技术**。

```java
Integer i1 = 33;
Integer i2 = 33;
System.out.println(i1 == i2);// 输出 true
Integer i11 = 333;
Integer i22 = 333;
System.out.println(i11 == i22);// 输出 false
Double i3 = 1.2;
Double i4 = 1.2;
System.out.println(i3 == i4);// 输出 false
```

**Integer 缓存源代码**：

```java
/**
* 此方法将始终缓存-128 到 127（包括端点）范围内的值，并可以缓存此范围之外的其他值。
*/
public static Integer valueOf(int i) {
if (i >= IntegerCache.low && i <= IntegerCache.high)
return IntegerCache.cache[i + (-IntegerCache.low)];
return new Integer(i);
}
```

**应用场景**：

1. Integer i1=40；Java 在编译的时候会直接将代码封装成 Integer i1=Integer.valueOf(40);，从而使用常量池中的对象。
2. Integer i1 = new Integer(40);这种情况下会创建新的对象。

```java
Integer i1 = 40;
Integer i2 = new Integer(40);
System.out.println(i1==i2);//输出 false
```

**Integer 比较更丰富的一个例子**:

```java
Integer i1 = 40;
Integer i2 = 40;
Integer i3 = 0;
Integer i4 = new Integer(40);
Integer i5 = new Integer(40);
Integer i6 = new Integer(0);

System.out.println("i1=i2   " + (i1 == i2));
System.out.println("i1=i2+i3   " + (i1 == i2 + i3));
System.out.println("i1=i4   " + (i1 == i4));
System.out.println("i4=i5   " + (i4 == i5));
System.out.println("i4=i5+i6   " + (i4 == i5 + i6));   
System.out.println("40=i5+i6   " + (40 == i5 + i6));     
```

结果：

```java
i1=i2   true
i1=i2+i3   true
i1=i4   false
i4=i5   false
i4=i5+i6   true
40=i5+i6   true
```

解释：

语句 i4 == i5 + i6，因为+这个操作符不适用于 Integer 对象，首先 i5 和 i6 进行自动拆箱操作，进行数值相加，即 i4 == 40。然后 Integer 对象无法与数值进行直接比较，所以 i4 自动拆箱转为 int 值 40，最终这条语句转为 40 == 40 进行数值比较。

----------------
----------------

## 看在前面

> * <a href="https://review-notes.top/language/java-jvm/%E4%BB%8E%E8%99%9A%E6%8B%9F%E6%9C%BA%E7%9A%84%E8%A7%92%E5%BA%A6%E7%9C%8B%E5%AF%B9%E8%B1%A1%E7%9A%84%E5%88%9B%E5%BB%BA%E4%B8%8E%E8%AE%BF%E9%97%AE.html#%E4%B8%80%E3%80%81-%E5%89%8D%E8%A8%80">从虚拟机的角度看对象的创建与访问</a>

## 一、 前言

以最常用的虚拟机 HotSpot 和最常用的内存区域 Java 堆为例，深入探讨一下 HotSpot 虚拟机在 Java 堆中对象分配、布局和访问的全过程。

## 二、 对象在虚拟机的创建过程


当 Java 虚拟机遇到一条字节码 new 指令时。在虚拟机的创建过程主要步骤：

```
1. 类加载

2. 内存分配

3. 内存空间初始化

4. 对象头设置

5. 构造方法
```

下面小节逐一介绍各步骤完成的工作。

### 1. 创建过程-类加载

* 首先去检查这个指令的参数是否能在常量池中定位到一个类的符号引用。
* 并且检查这个符号引用代表的类是否已被加载、解析和初始化过。
* 如果没有，那必须先执行相应的类加载过程，参考<a href="https://gourderwa.blog.csdn.net/article/details/103914303">Java JVM JDK9-类加载机制</a>

### 2. 创建过程-内存分配

在类加载检查通过后，虚拟机将为新生对象分配内存，对象所需的内存大小在类加载完成后可完全确定。

内存分配方式「指针碰撞」与「空闲列表」介绍：

* 指针碰撞：如果 Java 堆中内存是绝对规整的，所有被使用过的内存和空闲的内存中间用一个指针作为分界点分离，那分配内存就是把那个指针向空闲空间方向挪动一段与对象大小相等的距离。（理解为一个拖地的效果）

* 空闲列表：如果 Java 堆中的内存并不是规整的，虚拟机就必须维护一个列表，记录上哪些内存块是可用的，在分配的时候从列表中找到一块足够大的空间划分给对象实例，并更新列表上的记录。

![指针碰撞和空闲列表](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/%E6%8C%87%E9%92%88%E7%A2%B0%E6%92%9E%E5%92%8C%E7%A9%BA%E9%97%B2%E5%88%97%E8%A1%A8.png)

选择哪种分配方式由 Java 堆是否规整决定。因此：

* 当使用 Serial、ParNew 等带压缩整理过程的收集器时，系统采用的分配算法是指针碰撞，既简单又高效；

* 当使用 CMS 这种基于清除（Sweep）算法的收集器时，理论上就只能采用较为复杂的空闲列表来分配内存。

并发分配的问题及解决方案：

对象创建在虚拟机中是非常频繁的行为，即使仅仅修改一个指针所指向的位置，在并发情况下也并不是线程安全的，可能出现正在给对象 A 分配内存，指针还没来得及修改，对象 B 又同时使用了原来的指针来分配内存的情况。解决这个问题有两种可选方案：

* 对分配内存空间的动作进行同步处理，实际上虚拟机是采用 CAS 配上失败重试的方式保证更新操作的原子性；

* 把内存分配的动作按照线程划分在不同的空间之中进行，即每个线程在 Java 堆中预先分配一小块内存，称为本地线程分配缓冲（TLAB），哪个线程要分配内存，就在哪个线程的本地缓冲区中分配，只有本地缓冲区用完了，分配新的缓存区时才需要同步锁定。

> 虚拟机是否使用 TLAB，可以通过-XX：+/-UseTLAB 参数来设定。

### 3. 创建过程-内存空间初始化

内存分配完成之后，虚拟机必须将分配到的内存空间（但不包括对象头）都初始化为零值。

这步操作保证了对象的实例字段在 Java 代码中可以不赋初始值就直接使用，使程序能访问到这些字段的数据类型所对应的零值。

> 如果使用了 TLAB 的话，这一项工作也可以提前至 TLAB 分配时进行。

### 4. 创建过程-对象头设置

内存空间初始化后，Java 虚拟机还要对对象进行必要的设置，例如这个对象是哪个类的实例、如何才能找到类的元数据信息、对象的哈希码（实际上对象的哈希码会延后到真正调用 Object.hashCode() 方法时才计算）、对象的 GC 分代年龄等信息。 这些信息存放在对象的对象头之中。

根据虚拟机当前运行状态的不同，如是否启用偏向锁等，对象头会有不同的设置方式。

### 5. 创建过程-虚拟机创建对象完成

在上面工作都完成之后，从虚拟机的视角来看，一个新的对象已经产生了。

但是从 Java 程序的视角看来，对象创建才刚刚开始，构造函数还没有执行，所有的字段都为默认的零值，对象需要的其他资源和状态信息也没有构造好。通过 new 指令创建的对象此时会执行构造方法。

> 通过其他方式创建的对象此时不一定执行构造方法，比如克隆对象。

![虚拟机创建对象-流程图](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/%E8%99%9A%E6%8B%9F%E6%9C%BA%E5%88%9B%E5%BB%BA%E5%AF%B9%E8%B1%A1-%E6%B5%81%E7%A8%8B%E5%9B%BE.png)

## 三、 对象的内存布局

在 HotSpot 虚拟机里，对象在堆内存中的存储布局可以划分为三个部分：

* 对象头：用于存储对象自身的运行时数据和类型指针

* 实例数据：对象真正存储的有效信息

* 对齐填充：补齐二进制的位数

### 1. 对象头

HotSpot 虚拟机对象的对象头部分包括两类信息

* 对象头信息

* 类型指针

#### 1.1 对象头信息（Mark Word）

对象头信息用于存储对象自身的运行时数据，如哈希码、GC 分代年龄、锁状态标志、线程持有的锁、偏向线程 ID、偏向时间戳等。

* 这部分数据的长度在 32 位和 64 位的虚拟机（未开启压缩指针）中分别为 32 bit和 64 bit。

* 对象头信设计成一个有着动态定义的数据结构，以便在极小的空间内存储尽量多的数据，根据对象的状态复用自己的存储空间。

> 比如锁的升级过程，头信息会一直变换，不同的标识位代表不同的锁类型

下面为 64 位虚拟机对象头标志位：

```java
|------------------------------------------------------------------------|
|                            Mark Word (64 bits)                         |
|------------------------------------------------------------------------|
| unused:25   | hashcode:31 | unused:1 | GC年龄:4 | 是否是偏向锁:1 | lock:2    无锁
|------------------------------------------------------------------------|
| threadId:54 | 偏向时间戳:2  | unused:1 | GC年龄:4 | 是否是偏向锁:1 | lock:2    偏向锁
|------------------------------------------------------------------------|
|              指向栈中锁记录的指针:62                              | lock:2    轻量级锁
|------------------------------------------------------------------------|
|              指向管程 Monitor 的指针:62                          | lock:2    重量级锁 
|------------------------------------------------------------------------|
|                                                               | lock:2     GC 标识
|------------------------------------------------------------------------|
```
#### 1.2 对象头信息-类型指针

* 对象头类型指针对象是指向它的类型元数据的指针

* Java 虚拟机通过这个指针来确定该对象是哪个类的实例

此外，如果对象是一个 Java 数组，那在对象头中还必须有一块用于记录数组长度的数据，因为虚拟机可以通过普通 Java 对象的元数据信息确定 Java 对象的大小，但是如果数组的长度是不确定的，将无法通过元数据中的信息推断出数组的大小。

#### 1.3 对象头信息实战

我们使用 openjdk 的一个工具包 <a href="http://openjdk.java.net/projects/code-tools/jol/">Code Tools-jol</a> 打印对象信息。<a href="https://github.com/GourdErwa/java-advanced/blob/master/java-jvm/src/main/java/io/gourd/java/jvm/jol/JolObjectHeard.java"> 源代码</a>，部分伪代码如下：

```java
class Obj {
    long longVal0 = 10;
    int intVal0;
    long longVal1;
    byte byteVal0;
    short shortVal0;
    String strVal0 = "hello world";
    String[] arrayVal0 = new String[]{strVal0, strVal0};
}

final Obj obj = new Obj();
final ClassLayout classLayout = ClassLayout.parseInstance(obj);

// 初始化后打印
out.println(classLayout.toPrintable());

synchronized (obj) {
    // 加锁后后打印
    out.println(classLayout.toPrintable());
}
```

开启指针压缩，最终执行效果如下：

![对象头信息打印](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/%E5%AF%B9%E8%B1%A1%E5%A4%B4%E4%BF%A1%E6%81%AF%E6%89%93%E5%8D%B0.jpeg)

### 2. 实例数据

实例数据部分是对象真正存储的有效信息，即我们在程序代码里面所定义的各种类型的字段内容，无论是从父类继承下来的，还是在子类中定义的字段都必须记录起来。

HotSpot 虚拟机默认的分配顺序为 longs/doubles、ints、shorts/chars、bytes/booleans、oops（普通对象指针）， 从默认的分配策略中可以看到，相同宽度的字段总是被分配到一起存放，在满足这个前提条件的情况下，在父类中定义的变量会出现在子类之前。

> 这部分的存储顺序会受到虚拟机分配策略参数（```-XX：FieldsAllocationStyle``` 参数）和字段在 Java 源码中定义顺序的影响。
如果 HotSpot 虚拟机的 ```+XX：CompactFields``` 参数值为 true（默认为 true），那子类之中较窄的变量也允许插入父类变量的空隙之中，以节省出一点点空间。

### 3. 对齐填充

对齐填充没有特别的含义，它仅仅起着占位符的作用。可以简单的理解为二进制数据位补齐的操作。

由于 HotSpot 虚拟机的自动内存管理系统要求对象起始地址必须是 8 字节的整数倍。对象头部分已经被精心设计成正好是 8 字节的倍数（1 倍或者 2 倍），因此，如果对象实例数据部分没有对齐的话，就需要通过对齐填充来补全。

## 四、 对象的访问方式

reference 类型在《Java 虚拟机规范》里面只规定了它是一个指向对象的引用，并没有定义这个引用应该通过什么方式去定位、访问到堆中对象的具体位置， 所以对象访问方式也是由虚拟机实现而定的，主流的访问方式主要有「使用句柄」和「直接指针」两种

* 使用句柄访问的话，Java 堆中将可能会划分出一块内存来作为句柄池，reference 中存储的就是对象的句柄地址，而句柄中包含了对象实例数据与类型数据各自具体的地址信息，其结构如下图所示。

* 使用直接指针访问的话，Java 堆中对象的内存布局就必须考虑如何放置访问类型数据的相关信息，reference 中存储的直接就是对象地址，如果只是访问对象本身的话，就不需要多一次间接访问的开销，如下图所示。

![对象访问方式](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/JVM/picture/%E5%AF%B9%E8%B1%A1%E8%AE%BF%E9%97%AE%E6%96%B9%E5%BC%8F.jpeg)

访问方式比较：

* 句柄访问的最大好处就是 reference 中存储的是稳定句柄地址，在对象被移动（垃圾收集时移动对象是非常普遍的行为）时只会改变句柄中的实例数据指针，而 reference 本身不需要被修改。

* 直接指针来访问最大的好处就是速度更快，它节省了一次指针定位的时间开销，由于对象访问在 Java 中非常频繁，因此这类开销积少成多也是一项极为可观的执行成本。

> HotSpot 虚拟机主要使用第二种方式，有例外情况，如果使用 Shenandoah 收集器的话也会有一次额外的转发，具体可参见收集器章节

## 总结

* 对象在虚拟机的创建过程为：类加载、内存分配、内存空间初始化、对象头设置、构造方法

* 对象在堆内存的存储布局为：对象头、实例数据、对齐填充

* 对象的访问方式为：使用句柄、直接指针（HotSpot 虚拟机使用直接指针）

* 内存分配时，根据内存的规整性，分配方式分为「指针碰撞」与「空闲列表」，具体用哪种需要参考使用的垃圾回收器支持压缩整理过程。

----------------
----------------

## 看在前面

- 对象的内存布局：https://www.cnblogs.com/duanxz/p/4967042.html

深入理解Java虚拟机,在HotSpot虚拟机中,对象在内存中存储的布局可以分为3块区域：

- 对象头(Header)
- 实例数据(Instance Data)
- 对齐填充(Padding)

## 32位

![image](https://github.com/Mein-Augenstern/MUYI/assets/34135120/e12f8c8c-c0cd-4a4f-ad61-c29f61eb5d27)

## 64位

![image](https://github.com/Mein-Augenstern/MUYI/assets/34135120/1b345654-47d3-467a-8199-a856f212bd35)

从上面的这张图里面可以看出，对象在内存中的结构主要包含以下几个部分：

- 对象头：

  - Mark Word(标记字段)：关于锁的信息。对象的Mark Word部分占4个字节/8个字节，表示对象的锁状态（比如轻量级锁的标记位，偏向锁标记位），另外还可以用来配合GC分代年龄、存放该对象的hashCode等。
  
  - Klass Pointer（Class对象指针）：Class对象指针的大小也是4个字节/8个字节，其指向的位置是对象对应的Class对象（其对应的元数据对象）的内存地址。
  
  - 数组长度：如果对象是数组类型，占用4个字节/8个字节，因为JVM虚拟机可以通过Java对象的元数据信息确定Java对象的大小，但是无法从数组的元数据来确认数组的大小，所以用一块来记录数组长度。
  
- Instance Data（对象实际数据）：这里面包括了对象的所有成员变量，其大小由各个成员变量的大小决定，比如：byte和boolean是1个字节，short和char是2个字节，int和float是4个字节，long和double是8个字节，reference是4个字节。1字节 = 8bit

- padding data（对齐）：如果上面的数据所占用的空间不能被8整除，padding则占用空间凑齐使之能被8整除。被8整除在读取数据的时候会比较快

## 对象头

HotSpot虚拟机的对象头包括两部分信息,第一部分用于存储对象自身的运行时数据,如哈希码(HashCode)、GC分代年龄、锁状态标志、线程持有的锁、偏向线程ID、偏向时间戳等,这部分数据的长度在32位和64位的虚拟机(末开启压缩指针)中分别为32bit和64bit,官方称它为"Mark Word"。对象需要存储的运行时数据很多,其实已经超出了32位、64位Bitmap结构所能记录的限度,但是对象头信息是与对象自身定义的数据无关的额外存储成本,考虑到虚拟机的空间效率, Mark Word被设计成一个非固定的数据结构以便在极小的空间内存储尽量多的信息,它会根据对象的状态复用自己的存储空间,例如,在32位的HotSpot虚拟机中,如果对象处于未被锁定的状态下,那么Mark Word的32bit空间中的25bit用于存储对象哈希码, 4bit用于存储对象分代年龄, 2bit用于存储锁标志位, 1bit固定为0,而在其他状态(轻量级锁定、重量级锁定、GC标记、可偏向)下对象的存储内容见下表。

![image](https://github.com/Mein-Augenstern/MUYI/assets/34135120/ac1e5bd9-6797-4cd0-8c30-0cf9f209afdf)

对象头的另外一部分是类型指针,即对象指向它的类元数据的指针,虚拟机通过这个指针来确定这个对象是哪个类的实例。并不是所有的虚拟机实现都必须在对象数据上保留类型指针,换句话说,查找对象的元数据信息并不一定要经过对象本身,这点将在2.3.3节讨论。另外,如果对象是一个Java数组,那在对象头中还必须有一块用于记录数组长度的数据,因为虚拟机可以通过普通Java对象的元数据信息确定Java对象的大小,但是从数组的元数据中却无法确定数组的大小。

## 实例数据
接下来的实例数据部分是对象真正存储的有效信息,也是在程序代码中所定义的各种类型的字段内容。无论是从父类继承下来的,还是在子类中定义的,都需要记录起来。这部分的存储顺序会受到虚拟机分配策略参数(FieldsAllocationStyle)和字段在Java源码中定义顺序的影响。HotSpot虚拟机默认的分配策略为longs/doubles, ints, shorts/chars, bytes/booleans. oops (Ordinary Object Pointers),从分配策略中可以看出,相同宽度的字段总是被分配到起。在满足这个前提条件的情况下,在父类中定义的变量会出现在子类之前。如果CompactFields参数值为true (默认为true),那么子类之中较窄的变量也可能会插入到父类变量的空隙之中。

## 对齐填充
第三部分对齐填充并不是必然存在的,也没有特别的含义,它仅仅起着占位符的作用。由于HotSpot VM的自动内存管理系统要求对象起始地址必须是8字节的整数倍,换句话说,就是对象的大小必须是8字节的整数倍。而对象头部分正好是8字节的倍数(1倍或者2倍),因此,当对象实例数据部分没有对齐时,就需要通过对齐填充来补全。


