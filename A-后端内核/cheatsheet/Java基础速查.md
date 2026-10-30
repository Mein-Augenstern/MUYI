文章清单
====

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/NoClassDefFoundError%E5%92%8CClassNotFoundException%E5%8C%BA%E5%88%AB.md">NoClassDefFoundError和ClassNotFoundException区别</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/comparable%20%E5%92%8C%20Comparator%E7%9A%84%E5%8C%BA%E5%88%AB.md">Comparable 和 Comparator的区别</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/final%20finally%20finalize.md">final finally finalize</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/transient%E5%85%B3%E9%94%AE%E5%AD%97.md">transient关键字</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/wait%E5%92%8Csleep%E7%9A%84%E5%8C%BA%E5%88%AB.md">wait和sleep区别</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/%E5%BC%BA%E5%BC%95%E7%94%A8%20%E3%80%81%E8%BD%AF%E5%BC%95%E7%94%A8%E3%80%81%20%E5%BC%B1%E5%BC%95%E7%94%A8%E3%80%81%E8%99%9A%E5%BC%95%E7%94%A8.md">强引用、软引用、弱引用、虚引用</a>

* <a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/%E7%A7%BB%E4%BD%8D%E8%BF%90%E7%AE%97%E7%AC%A6.md">移位运算符</a>

一、面向对象四大特性
------

| 特性 | 一句话说明 | 落地手段 |
| ---- | ---- | ---- |
| 封装 | 隐藏内部实现，只暴露必要行为 | private 字段 + public 方法，不变量在类内维护 |
| 继承 | 子类直接复用父类的字段与方法，is-a 关系 | extends（类单继承）、implements（接口多实现） |
| 多态 | 同一个调用，运行期按对象实际类型分派 | 重写 + 父类/接口引用指向子类对象（动态绑定） |
| 抽象 | 约定"做什么"，不关心"怎么做" | abstract 类/方法、interface |

* 多态三前提：有继承或实现、有方法重写、父类引用指向子类对象。

* 重载与重写速记：

| | 重载 Overload | 重写 Override |
| ---- | ---- | ---- |
| 发生位置 | 同一个类内 | 父子类之间 |
| 参数列表 | 必须不同 | 必须相同 |
| 访问权限 | 无限制 | 不能比父类更严格 |
| 绑定时机 | 编译期（静态分派） | 运行期（动态绑定） |

二、基本类型与包装类
------

* 8 种基本类型：byte(1字节)、short(2)、int(4)、long(8)、float(4)、double(8)、char(2)、boolean。

* 数量级速记：int 约 ±21 亿。long 约 ±922 亿亿。金额计算用 long（分为单位）或 BigDecimal，禁用 float/double（二进制无法精确表示十进制小数）。

* 包装类缓存池：Byte/Short/Integer/Long 缓存 -128~127，Character 缓存 0~127，Boolean 只有 true/false；Float/Double 无缓存。

> 经典题：两个 Integer 用 == 比较，127 相等、128 不等——前者命中缓存池返回同一对象。包装类型比较一律用 equals。

* 自动装箱/拆箱是编译期语法糖：Integer i = 1 实际调 Integer.valueOf(1)；i + 1 实际调 i.intValue()。三元运算符里包装类型与基本类型混用会整体拆箱，值为 null 时直接 NPE。

三、== 与 equals 与 hashCode
------

* ==：基本类型比较值；引用类型比较地址（是否同一个对象）。

* equals：Object 里的默认实现就是 ==；例如 String、Integer 重写后比较内容。重写要满足自反、对称、传递、一致，且与 null 比较返回 false。

* 与 hashCode 的契约：equals 相等则 hashCode 必须相等。hashCode 相等 equals 未必相等（哈希冲突）。只重写 equals 不重写 hashCode，对象放进 HashMap/HashSet 后会"存得进、取不出"。

* String 重写 equals 的判断顺序：同一引用 → instanceof → 长度相等 → 逐字符比较，短路上先做便宜的判断。

四、字符串三兄弟
------

| | String | StringBuilder | StringBuffer |
| ---- | ---- | ---- | ---- |
| 可变性 | 不可变 | 可变 | 可变 |
| 线程安全 | 不可变天然安全 | 不安全 | 安全（方法加 synchronized） |
| 典型场景 | 常量、少量操作 | 单线程拼接（默认选它） | 多线程拼接（罕见） |

* String 不可变的三件套：内部数组 private final、类被 final 修饰不可继承、不暴露任何修改入口。换来 hashCode 缓存、常量池复用、安全性（防篡改）。

* 循环里拼接必须用 StringBuilder；"a" + "b" 这类编译期常量会被折叠成一个字面量，无性能问题。

* new String("abc") 创建 1 或 2 个对象：堆中对象必然创建，常量池中没有 "abc" 则再创建一个。

五、散装高频考点
------

* 参数传递：Java 只有值传递。基本类型拷贝值本身，对象拷贝"引用的值"。方法内对参数重新赋值不影响外部，修改对象内容则会影响外部。

* 深拷贝与浅拷贝：浅拷贝只复制引用，深拷贝连引用指向的对象一起复制。

* switch 支持 String（JDK7 起），底层靠 hashCode + equals 定位 case。

* 反射与 SPI 是框架两大地基：反射在运行期操作类（见 [Java反射](../java/java基础/Java反射.md)）。SPI 按 META-INF/services 声明做服务发现（见 [SPI](../java/java基础/SPI.md)）。JDBC 驱动加载就是典型。

延伸阅读
------

* [final finally finalize](../java/java基础/final%20finally%20finalize.md) · [wait和sleep的区别](../java/java基础/wait和sleep的区别.md) · [comparable 和 Comparator的区别](../java/java%E5%9F%BA%E7%A1%80/Comparable%E5%92%8CComparator%E7%9A%84%E5%8C%BA%E5%88%AB.md) · [NoClassDefFoundError和ClassNotFoundException区别](../java/java基础/NoClassDefFoundError和ClassNotFoundException区别.md)

> 2026-10 扩充（Round1 查漏）。
