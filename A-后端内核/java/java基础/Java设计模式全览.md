Java设计模式全览
====

设计模式题的面试形态早就不是「背 23 种定义」，而是「你说用过策略模式，现场画一下」「代理和装饰、适配器到底差在哪」「Spring 里哪些地方是工厂」。本篇按 GoF 三大类逐个过一遍——**每个模式一小节精要：一句定位、核心结构、一个能落地的例子**，并在易混模式间直接给出对比。速查版见 [设计模式速查](../../cheatsheet/设计模式速查.md)；单例的攻防细节另见 [单例模式的安全性](单例模式的安全性.md)，代理的字节码实现见 [JDK动态代理源码分析](JDK动态代理源码分析.md) 与 [cglib动态代理](cglib动态代理.md)，观察者的工具化落地见 [单机事件驱动解耦-Guava-EventBus](单机事件驱动解耦-Guava-EventBus.md)。

1 总纲：三条贯穿全文的设计原则
------

* **面向接口编程，而不是面向实现**——优雅、可扩展代码的第一步，客户端依赖的永远是抽象；
* **职责单一**——每个类只做一件事，并把这件事完整封装起来；
* **开闭原则**——对修改关闭（写好、测好的代码不许随便动），对扩展开放（加功能不动老代码）。

以下按创建型 6 式、结构型 7 式、行为型 5 式展开（简单工厂严格说不属于 GoF 23 式，但太常用，一并收入）。

2 简单工厂
------

一个工厂类 + 一个静态方法，按参数返回同一父类的不同实例：

```java
public class FoodFactory {
    public static Food makeFood(String name) {
        if ("noodle".equals(name)) return new LanZhouNoodle();
        if ("chicken".equals(name)) return new HuangMenChicken();
        return null;
    }
}
```

要点：客户端只认 `Food`；工厂职责单一——只管生产。缺点是加新品要改工厂的 if-else，违背开闭原则，但多数场景够用，别过度设计。

3 工厂方法（Factory Method）
------

当需要**两个以上并列的工厂**时，把工厂本身抽象成接口，先选工厂、再造产品：

```java
public interface FoodFactory { Food makeFood(String name); }
public class ChineseFoodFactory implements FoodFactory { /* 造中餐 */ }
public class AmericanFoodFactory implements FoodFactory { /* 造美餐 */ }

FoodFactory factory = new ChineseFoodFactory();   // 第一步选工厂
Food food = factory.makeFood("A");                // 第二步造产品
```

与简单工厂的本质区别：多了一层「选工厂」的维度。典型如日志体系——FileLogFactory 写文件、KafkaLogFactory 写 Kafka，选定了工厂，后续行为整套确定。加产品族成员不用改老工厂，符合开闭原则。

4 抽象工厂（Abstract Factory）
------

解决**产品族**问题：产品由多个配件构成且配件之间有兼容性约束。CPU 工厂、主板工厂各自独立（工厂方法），客户端就可能拼出不兼容的 Intel CPU + AMD 主板。抽象工厂把整族产品的生产收归一个品牌工厂：

```java
ComputerFactory cf = new AmdFactory();      // 选定一个"大厂"
CPU cpu = cf.makeCPU();                     // 全部配件出自同一家
MainBoard board = cf.makeMainBoard();
HardDisk disk = cf.makeHardDisk();
Computer c = new Computer(cpu, board, disk); // 必然兼容
```

代价：加一种配件（如显示器）要给所有工厂加方法，违反开闭原则。判断口诀——**产品间有兼容性约束用抽象工厂，只是并列选择用工厂方法**。

5 单例（Singleton）
------

用得最多、错得最多。四种主流写法的精要：

```java
// 1) 饿汉：类加载即创建，最简单
private static Singleton instance = new Singleton();

// 2) 双重检查锁（DCL）：注意 volatile 不可省，防"分配-初始化-赋引用"重排序
private static volatile Singleton instance;
public static Singleton getInstance() {
    if (instance == null) {
        synchronized (Singleton.class) {
            if (instance == null) instance = new Singleton();
        }
    }
    return instance;
}

// 3) 静态嵌套类 Holder：延迟加载 + 无锁，首选
private static class Holder { private static Singleton3 instance = new Singleton3(); }
public static Singleton3 getInstance() { return Holder.instance; }
```

第 4 种是**枚举**：JVM 在类初始化时创建全部实例并保证不再实例化，天生单例，且免疫反射与序列化破坏（攻防全解见 [单例模式的安全性](单例模式的安全性.md)）。别把 Holder 写法说成"静态内部类"——嵌套类（nested class）与内部类（inner class）的访问权限并不相同。

6 建造者（Builder）
------

对付**属性多、必填与选填混杂**的类。套路：Builder 与目标类字段一一镜像，链式设值，build() 时统一校验并构造：

```java
User user = User.builder()
        .name("foo")
        .password("pAss12345")
        .age(25)
        .build();          // build() 内校验必填、补默认值，再调私有构造器
```

要点：构造器私有化，强制走 Builder；校验放在 build() 比挤在构造器里优雅。Lombok 的 @Builder 一个注解搞定样板代码。只要链式不要建造者的话，setter 返回 this 也行，但那不是建造者模式，可读性也差。

7 原型（Prototype）
------

基于原型实例克隆新实例。核心只关联一个 API 知识点：`Object#clone()` 是 native 方法，类必须实现标志接口 `Cloneable`，否则抛 `CloneNotSupportedException`。Java 克隆是**浅克隆**——引用字段共享；深克隆的常规做法是序列化再反序列化。实际使用频率最低，知道边界即可。

8 代理（Proxy）
------

用一个代理隐藏真实实现，并在核心逻辑前后加料。代理类与实现类实现同一接口，内部持有真实实例：

```java
public class FoodServiceProxy implements FoodService {
    private FoodService foodService = new FoodServiceImpl();  // 真实实现

    public Food makeChicken() {
        System.out.println("即将开始制作");        // 前置增强
        Food food = foodService.makeChicken();    // 核心逻辑始终是真实类做的
        food.addCondiment("pepper");              // 后置增强
        return food;
    }
}
```

本质是**方法增强**。静态代理手写麻烦，生产里主力是动态代理：有接口走 JDK 动态代理（java.lang.reflect.Proxy），无接口走 CGLIB——这正是 Spring AOP 的两条路，详见 [JDK动态代理源码分析](JDK动态代理源码分析.md)、[cglib动态代理](cglib动态代理.md)。

9 适配器（Adapter）
------

接口有了，现成实现不匹配，中间加一层转换。三种形态：

* **默认适配器**：目标接口方法太多（如 commons-io 的 FileAlterationListener 有 8 个方法），先给一个全空实现的 Adaptor 基类，业务类继承它只覆写关心的方法——JDK 监听器、AWT Adapter 都是这个套路；
* **对象适配器（组合，主流）**：鸡冒充鸭——`CockAdapter implements Duck` 内部持有一只 Cock，quack() 委托 cock.gobble()，fly() 直接复用；
* **类适配器（继承）**：Adapter 同时继承被适配类、实现目标接口，少一个对象但被单继承锁死，用得少。

**与代理的区别一句话**：代理是「同一接口的增强」，适配是「不同接口的转换」——鸡和鸭之间本来没有继承关系。

10 桥接（Bridge）
------

把「形状」与「颜色」两个独立变化的维度拆开：抽象类 Shape 持有 DrawAPI 接口，Circle/Rectangle 是一边，RedPen/GreenPen 是另一边，任意组合。

```java
public abstract class Shape {
    protected DrawAPI drawAPI;                    // 桥在这：抽象持有实现接口
    protected Shape(DrawAPI drawAPI) { this.drawAPI = drawAPI; }
    public abstract void draw();
}
Shape greenCircle = new Circle(10, new GreenPen());
```

精髓是**优先组合 over 继承**：不用桥接就得为每种组合定义一个子类（RedCircle、GreenCircle……），m×n 爆炸；桥接后两边各自扩展，加一种笔不动形状。JDBC 的 Driver / Connection 体系就是桥接的现实原型。

11 装饰（Decorator）
------

不改原类、动态叠加小功能，且**可叠加多次**——这是它相对代理的核心优势（代理包装多层就复杂了）。饮料例子：

```java
public abstract class Beverage {
    public abstract String getDescription();
    public abstract double cost();
}
public abstract class Condiment extends Beverage {}       // 装饰者基类，必须是 Beverage

public class Lemon extends Condiment {
    private Beverage beverage;                             // 包裹任意一杯饮料
    public Lemon(Beverage beverage) { this.beverage = beverage; }
    public String getDescription() { return beverage.getDescription() + ", 加柠檬"; }
    public double cost() { return beverage.cost() + 2; }
}

Beverage drink = new Lemon(new Lemon(new BlackTea()));     // 双份柠檬想叠几层叠几层
```

Java IO 是教科书案例：FileInputStream 是基础流（红茶），BufferedInputStream/DataInputStream 等是挂在 FilterInputStream 下的装饰器（柠檬、芒果）。使用时注意要按具体类型声明变量，`InputStream is = new DataInputStream(...)` 会丢掉 DataInputStream 的独有方法——装饰模式里"面向接口编程"要适当让位。

12 门面（Facade）
------

给复杂子系统一个统一入口，客户端不再关心该实例化哪个实现类：

```java
ShapeMaker maker = new ShapeMaker();   // 门面内部持有 circle、rectangle、square
maker.drawCircle();                    // 客户端一行一个动作
maker.drawRectangle();
```

价值是把"调用哪些类、什么顺序"收敛到门面内。slf4j 就是门面思想的代表：API 是门面，logback/log4j 是背后的实现。

13 组合（Composite）
------

表示**树形层次结构**，使单个对象与组合对象的访问一致。典型如员工树：Employee 持有 `List<Employee> subordinates`，同时提供 add/remove/getSubordinates——节点和整树用同一个类型操作。文件系统（文件/目录）、菜单/子菜单同理，知道这个形状就够。

14 享元（Flyweight）
------

**复用已创建的对象以省内存**：用一个 Map（如 HashMap）缓存对象，要对象先查缓存，未命中再创建并放入。包装类缓存池、字符串常量池、数据库连接池的核心思想都是享元。注意与单例的区别——享元池里是"每类状态一个实例"，不是全局唯一。

15 策略（Strategy）
------

把可变的算法封装成一族可互换的实现，Context 持有策略接口：

```java
public interface Strategy { void draw(int radius, int x, int y); }
public class RedPen implements Strategy { /* ... */ }
public class BluePen implements Strategy { /* ... */ }

new Context(new BluePen()).executeDraw(10, 0, 0);   // 换笔只需换策略对象
```

消灭 if-else 分支爆炸的主力模式。Spring 里各种 XxxStrategy 按 key 注入 Map<String, Strategy> 再 route，是生产最高频用法。**与桥接的区别**：结构几乎同构，但桥接在抽象侧多压了一层（Shape 持 Strategy），是"两个维度都要独立扩展"；策略只有单维度替换，Context 不构成维度。

16 观察者（Observer）
------

主题持有观察者列表，状态变化时遍历回调：

```java
public class Subject {
    private List<Observer> observers = new ArrayList<>();
    public void attach(Observer o) { observers.add(o); }
    public void setState(int state) { /* 存值后 */ notifyAllObservers(); }
    private void notifyAllObservers() { observers.forEach(Observer::update); }
}
public abstract class Observer {
    protected Subject subject;
    public abstract void update();
}
```

要义三句：一定有个地方存着所有观察者；事件发生时遍历调用回调；一个事件允许多个不同类各自处理（订单变更 → 短信、邮件、物流各自监听）。JDK 内置过 java.util.Observable/Observer（已废弃）；分布式场景直接用 MQ，**单机推荐 Guava EventBus**，见 [单机事件驱动解耦-Guava-EventBus](单机事件驱动解耦-Guava-EventBus.md)。

17 责任链（Chain of Responsibility）
------

节点持有后继节点引用，处理不了或通过后向后传递。活动领奖校验为例：

```java
public abstract class RuleHandler {
    protected RuleHandler successor;                        // 后继节点
    public abstract void apply(Context context);
    public void setSuccessor(RuleHandler s) { this.successor = s; }
}

public class NewUserRuleHandler extends RuleHandler {
    public void apply(Context context) {
        if (context.isNewUser() && successor != null) successor.apply(context);  // 放行，传下去
        else if (!context.isNewUser()) throw new RuntimeException("仅限新用户");
    }
}

locationHandler.setSuccessor(limitHandler);   // 按需组链：本次只校验地区+库存
locationHandler.apply(context);
```

精髓：**调用方只碰链头，链的组装可以按场景动态拼接**（上例临时去掉了新用户校验）。流程审批、Servlet Filter、Netty Pipeline、Spring Interceptor 全是责任链。与「List 装规则 foreach」的区别：责任链节点可以决定是否继续传递、可以中途短路，且无需把规则集合暴露给调用方。

18 模板方法（Template Method）
------

父类定骨架（模板方法），子类填血肉：

```java
public abstract class AbstractTemplate {
    public void templateMethod() {        // 模板方法：固定流程
        init();                           // 可选覆写
        apply();                          // 抽象方法，子类必填
        end();                            // 钩子方法，需要才覆写
    }
    protected void init() { /* 默认实现 */ }
    protected abstract void apply();
    protected void end() { }
}
```

继承结构里的最常用模式，JDK 的 AbstractList、Spring 的 JdbcTemplate、Servlet 的 service→doGet/doDoPost 全是它。记忆点：**流程不可变，步骤可重写；钩子方法是留给子类的"要不要参与"开关**。

19 状态（State）
------

把每种状态下的行为封装成状态对象，Context 持有当前状态并委托执行：

```java
public interface State { void doAction(Context context); }
public class DeductState implements State {
    public void doAction(Context context) {
        context.setState(this);           // 状态对象自己完成迁移
        // 执行减库存
    }
}
```

与策略的区分：结构同构，**意图不同**——策略是客户端从外部挑选算法，状态是对象在运行中随行为自我迁移（减库存后 Context 处于 DeductState）。订单状态机（待支付/已支付/已发货）是面试标准落地场景。

20 分类速记与未尽模式
------

| 类别 | 模式 | 一句话钩子 |
| ---- | ---- | ---- |
| 创建型 | 简单工厂 / 工厂方法 / 抽象工厂 / 单例 / 建造者 / 原型 | 静态方法造产品 / 先选工厂 / 整族生产 / 全局一份 / 链式拼装 / clone 克隆 |
| 结构型 | 代理 / 适配器 / 桥接 / 装饰 / 门面 / 组合 / 享元 | 增强 / 转换 / 拆维度 / 叠功能 / 收入口 / 树结构 / 池复用 |
| 行为型 | 策略 / 观察者 / 责任链 / 模板方法 / 状态 | 换算法 / 订阅通知 / 逐级传递 / 定骨架 / 自迁移 |

GoF 行为型还有命令、迭代器、中介者、备忘录、访问者、解释器六种，使用面较窄：迭代器已被语言内置（for-each），命令模式倒值得一看（Runnable、线程池任务、撤销栈的本质），面试提一句即可。速查场景直接翻 [设计模式速查](../../cheatsheet/设计模式速查.md)。

面试追问链
------

* 工厂方法与抽象工厂怎么选？→ 并列选工厂用工厂方法；产品间有兼容约束、要整族生产用抽象工厂；
* DCL 为什么要 volatile？→ 禁止 new 的指令重排，防止另一线程拿到半初始化对象；
* 代理和装饰的区别？→ 结构同构，意图不同：代理是管控/增强同一接口，装饰是透明叠加多层小功能；
* 代理和适配器的区别？→ 代理同接口增强，适配器跨接口转换（鸡变鸭，二者原本无继承关系）；
* 策略和桥接的区别？→ 同构，桥接是两个维度独立扩展（多压一层抽象），策略是单维度替换；
* 策略和状态的区别？→ 策略由外部挑选算法，状态由对象随行为自我迁移；
* Spring 里见过哪些设计模式？→ 工厂（BeanFactory）、单例（默认 scope）、代理（AOP）、模板方法（JdbcTemplate）、观察者（事件机制）、策略（Resource 加载）——报得出具体组件是加分项；
* 责任链和直接 List 遍历的区别？→ 节点可决定是否传递/短路，链可按场景动态组装，规则不暴露给调用方。

面试官视角小结
------

设计模式面试的淘汰线是「背定义」，及格线是「每个模式给得出一个真实组件名」（IO=装饰、slf4j=门面、AOP=代理、JdbcTemplate=模板方法、EventBus/MQ=观察者），高分线是**讲清易混对的意图差**——代理 vs 适配器 vs 装饰、策略 vs 桥接 vs 状态——因为这些区分背后是"结构可以相同、意图才是模式"这一GoF 的核心论断。最强信号是反向能力：拿到一段需求能立刻说出"这里用工厂方法是因为要并列两个实现"。全库速记配合 [设计模式速查](../../cheatsheet/设计模式速查.md) 食用，单例攻防深挖进 [单例模式的安全性](单例模式的安全性.md)。

> 来源：javadoop《设计模式也可以这么简单》（https://javadoop.com/post/design-pattern）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
