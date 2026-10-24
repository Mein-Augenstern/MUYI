# 设计模式速查卡

单例模式（Singleton）
====

1. 意图：保证一个类仅一个实例，提供全局访问点。实现要点：私有构造器 + 静态实例 + 静态获取方法。

| 写法 | 线程安全 | 懒加载 | 备注 |
|------|---------|--------|------|
| 饿汉式 | 是 | 否 | 类加载即创建，简单 |
| 懒汉式+synchronized 方法 | 是 | 是 | 每次获取都加锁，性能差 |
| Double-Check 锁（DCL） | 是 | 是 | 需 volatile，主流写法 |
| 静态内部类 Holder | 是 | 是 | JVM 类初始化锁保证安全，推荐 |
| 枚举 | 是 | 否 | 《Effective Java》推荐，天然防反射/反序列化破坏 |

Double-Check 详解（面试高频）
----

```java
public class Singleton {
    private static volatile Singleton instance;   // 必须 volatile
    private Singleton() {}
    public static Singleton getInstance() {
        if (instance == null) {                    // 第一次检查：避免每次都加锁
            synchronized (Singleton.class) {
                if (instance == null) {            // 第二次检查：防止重复创建
                    instance = new Singleton();
                }
            }
        }
        return instance;
    }
}
```

1. `new Singleton()` 非原子，分三步：分配内存 → 初始化对象 → 引用赋值。
2. 若发生指令重排（先赋值后初始化），另一线程在第一次检查处读到非 null。此时直接使用**未初始化完成**的对象。
3. `volatile` 禁止重排 + 保证可见性，这是 DCL 成立的关键。
4. 追问"为什么两次 if"：外层避免不必要加锁（有实例后不再竞争锁）。内层保证同批等待线程只创建一次。

代理模式（Proxy）
----

1. 意图：为对象提供代理以控制访问（增强而不改原类）；Spring AOP 的基础。

| 方式 | 原理 | 要求 | 特点 |
|------|------|------|------|
| 静态代理 | 手写代理类实现同接口 | 接口 | 类爆炸，每个方法手动转发 |
| JDK 动态代理 | `Proxy.newProxyInstance` + `InvocationHandler`，运行时生成接口实现类 | 目标类必须实现接口 | 基于反射调用 |
| CGLIB | 运行时生成目标类**子类**，重写非 final 方法 | 类/方法不能是 final | FastClass 索引直接调用，避免反射，性能更好 |

2. Spring AOP 选择逻辑：有接口默认 JDK 代理（Boot 2.x 默认 CGLIB），无接口用 CGLIB。

工厂模式（Factory）
----

1. **简单工厂**：一个工厂类 + switch/if 创建产品；加产品要改工厂代码，违反开闭原则。
2. **工厂方法**：每个产品对应一个工厂（抽象工厂接口 + 具体工厂）。加产品只需新增类，符合开闭原则。类数量膨胀。
3. **抽象工厂**：工厂创建**产品族**（一组相关产品）；加新产品族成员要改抽象接口。
4. 记忆：简单工厂（一个厂造所有）、工厂方法（一厂一品）、抽象工厂（一厂一族）。

观察者模式（Observer）
----

1. 意图：对象间一对多依赖，Subject 状态变化自动通知所有 Observer，解耦发布与订阅。
2. 角色：Subject（维护观察者列表、notify）+ Observer（update 回调）。
3. Java 内置 `Observable/Observer`（Java 9 已废弃）；Gui 事件、MQ 发布订阅同思想。
4. Spring 实现：`ApplicationEventPublisher.publishEvent(event)` + `@EventListener` 监听，同步默认（可 `@Async` 异步）。

Reactor 反应堆模式
----

1. 意图：基于 IO 多路复用（Selector/epoll）的事件驱动模型。Reactor 监听事件 → 分发到 Handler 处理，适合海量连接。

| 变体 | 结构 | 代表 |
|------|------|------|
| 单线程 Reactor | 1 线程完成 accept + 读写 + 业务 | Redis 6 前 |
| 多线程 Reactor | Reactor 分发，Worker 线程池处理业务 | — |
| 主从 Reactor | mainReactor 专收连接，subReactor 处理 IO | Netty（boss/worker 线程组） |

2. 与观察者区别：观察者是对象级事件回调。Reactor 是网络 IO 事件的多路复用分发。

Spring 中常用模式速查
====

| 模式 | Spring 中的应用 |
|------|----------------|
| 工厂 | BeanFactory / ApplicationContext 创建管理 Bean |
| 单例 | Bean 默认 scope=singleton（容器级，非 JVM 级） |
| 代理 | AOP（JDK/CGLIB 动态代理） |
| 模板方法 | JdbcTemplate / RedisTemplate 等固定流程+可变部分 |
| 观察者 | ApplicationEvent 事件机制 |
| 适配器 | Spring MVC 的 HandlerAdapter |
| 策略 | Resource 资源加载、实例化策略 |
| 装饰器 | Java IO 流包装（BufferedReader 包装 Reader 同思想） |
| 委派 | DispatcherServlet 分发请求 |

> 原链接已失效，2026-10 依据主题重写。
