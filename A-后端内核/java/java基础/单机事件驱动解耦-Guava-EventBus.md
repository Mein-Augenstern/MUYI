单机事件驱动解耦：Guava EventBus
====

「订单创建后要发短信、发邮件、更新库存，这些代码堆在 createOrder 里越滚越长怎么办」——面试里的标准答案之一就是事件驱动解耦。不上 MQ 的单机场景，Guava 的 EventBus 是最轻的落地件：观察者模式的官方工具化实现（模式背景见 [Java设计模式全览](Java设计模式全览.md) 的观察者模式一节）。本篇搭一套 Spring 环境下的完整骨架，并补两个实战要点：注册自动化的原理、同步事件异常如何抛回调用方。

1 定位：单机版观察者
------

EventBus 处理的事情类似观察者模式：观察者订阅自己感兴趣的事件类型，发布方 post 一个事件，总线分发给所有订阅了该类型的方法。价值在**解耦**：系统里很多地方都会触发订单修改——用户改、客服改、拼团失败系统自动改，每个触发点都要发短信、发邮件，每加一个动作就要改 N 处。改成事件驱动后，触发点只管 post 事件，后续动作各自挂监听，加操作只加 listener。

Guava 提供两种总线：`EventBus` 同步模式，post 的线程自己依次执行所有 subscriber；`AsyncEventBus` 异步模式，subscriber 丢进线程池执行，post 立即返回。发短信这类不必等待的事走异步、需要事务内联动的走同步，一个注册中心可同时持有两个。

2 标记注解与依赖
------

先定义注解，只用于标记"这是个 EventBus 监听器"，有了它注册就能自动化，业务方不需要知道任何注册细节：

```java
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EventBusListener {
}
```

依赖即 guava 单包引入（com.google.guava:guava，22.0 即可用）。

3 注册中心：EventBusCenter
------

核心组件，双总线 + 启动时自动注册：

```java
@Component
public class EventBusCenter {

    private EventBus syncEventBus = new EventBus();                       // 管理同步事件

    private AsyncEventBus asyncEventBus =                                 // 管理异步事件
            new AsyncEventBus(Executors.newCachedThreadPool());

    public void postSync(Object event) { syncEventBus.post(event); }
    public void postAsync(Object event) { asyncEventBus.post(event); }

    @PostConstruct
    public void init() {
        // 获取所有带 @EventBusListener 的 bean，注册为监听者
        List<Object> listeners = SpringContextUtils.getBeansWithAnnotation(EventBusListener.class);
        for (Object listener : listeners) {
            asyncEventBus.register(listener);
            syncEventBus.register(listener);
        }
    }
}
```

设计要点：注册中心是 Spring 组件，`@PostConstruct` 保证所有 bean 就绪后完成注册；同一个 listener 同时挂两条总线，方法订阅哪个事件由 `@Subscribe` 的参数类型决定，互不干扰。

4 事件、监听器与发布
------

事件是普通 POJO，按业务动作命名：

```java
public class OrderCreatedEvent {
    private long orderId;
    private long userId;
    public OrderCreatedEvent(long orderId, long userId) { /* 赋值 */ }
    // getter 省略
}
```

监听器：类上打 `@EventBusListener`，方法上打 Guava 的 `@Subscribe`，**方法参数类型即订阅的事件类型**，这是 EventBus 的分发依据：

```java
@Component
@EventBusListener
public class OrderChangeListener {

    @Subscribe
    public void created(OrderCreatedEvent event) {
        // 发短信、发邮件等。事件可被多个方法订阅，
        // 没必要在一个方法里塞进所有动作；订阅别的事件再写一个 @Subscribe 方法即可
    }
}
```

业务代码只依赖注册中心，完全不知道后面有谁在听：

```java
@Service
public class OrderService {

    @Autowired
    private EventBusCenter eventBusCenter;

    public void createOrder() {
        // 处理创建订单本身 ...
        eventBusCenter.postAsync(new OrderCreatedEvent(1L, 1L));  // 异步通知所有订阅方
    }
}
```

5 注册自动化的原理：SpringContextUtils
------

第 3 节用到的 `SpringContextUtils` 是大部分 Spring 应用的标配工具——通过 BeanFactoryPostProcessor 提前抓下 BeanFactory：

```java
@Component
public class SpringContextUtils implements BeanFactoryPostProcessor {

    private static ConfigurableListableBeanFactory beanFactory;

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) {
        SpringContextUtils.beanFactory = factory;
    }

    // 注册中心用到的就是这个：按注解取所有 bean（@Component 派生注解也会被匹配）
    public static List<Object> getBeansWithAnnotation(Class<? extends Annotation> type) {
        return beanFactory.getBeansWithAnnotation(type).entrySet()
                .stream().map(Map.Entry::getValue).collect(Collectors.toList());
    }
}
```

要点：用 BeanFactoryPostProcessor 而非 ApplicationContextAware 拿容器，可以在更早阶段、以纯静态方式取 bean，适合基础设施工具类。

6 同步事件的异常如何抛回调用方
------

同步模式下，post 线程亲自执行完所有 subscriber 才返回；但 EventBus 默认的异常处理只是打日志，**subscriber 抛出的异常不会传给 post 的调用方**——对"用同步事件做联动校验"的场景是致命的。解法是自定义 SubscriberExceptionHandler + ThreadLocal 传递：

```java
ThreadLocal<ServiceException> threadLocal = new ThreadLocal<>();

private EventBus syncEventBus = new EventBus(new SubscriberExceptionHandler() {
    @Override
    public void handleException(Throwable exception, SubscriberExceptionContext context) {
        if (exception instanceof ServiceException) {
            threadLocal.set((ServiceException) exception);
        }
    }
});

public void postSync(Object event) {
    syncEventBus.post(event);
    ServiceException ex = threadLocal.get();
    if (ex != null) {
        threadLocal.remove();   // 记得清理，防止污染线程池中的下个请求
        throw ex;
    }
}
```

两个必须知道的细节：同步总线上**一个 subscriber 抛异常不会阻止后续 subscriber 执行**，异常统一进 handler，多个 subscriber 都抛时 ThreadLocal 被覆盖、最终拿到最后一个；postSync 里务必 remove，否则异常泄漏给复用该线程的下一个请求。

7 边界与注意事项
------

* **没有订阅者的事件**不报错，会被包装成 `DeadEvent` 再发一次——可订阅 DeadEvent 排障：post 了没人听，多半是忘加 @EventBusListener 或事件类型对不上；
* @Subscribe 方法**只能有一个参数**，分发按参数类型匹配，注意父类型会被同时命中；
* AsyncEventBus 的线程池要按业务量有界化，CachedThreadPool 在事件风暴下可能拖垮机器；
* EventBus 是纯单机进程内总线：无持久化、无重试、宕机即丢；集群与可靠性场景直接上 MQ，别拿 EventBus 硬撑。

面试追问链
------

* EventBus 和 MQ 的区别？→ 进程内分发 vs 跨进程可靠传输；EventBus 无持久化无重试；
* 同步和异步怎么选？→ 需要事务内联动、异常要反馈给调用方用同步；通知类动作用异步（AsyncEventBus + 线程池）；
* subscriber 抛异常会怎样？→ 不阻断同事件的其他 subscriber，进 SubscriberExceptionHandler，默认只打日志；要抛回调用方需自定义 handler（ThreadLocal 方案）；
* 监听器怎么自动注册？→ 自定义注解标记 + getBeansWithAnnotation 扫描 + @PostConstruct 统一 register；
* post 一个没人订阅的事件？→ 包装成 DeadEvent 再发，可订阅 DeadEvent 发现配置遗漏。

面试官视角小结
------

这道题考的不是"会不会用 Guava"，而是**解耦的分寸感**。及格线是说出观察者模式与"触发点只管发事件"；答出同步/异步两条总线的选型、异常回传的 ThreadLocal 方案，说明真在生产里用过；能主动划清边界——进程内解耦用 EventBus、跨服务可靠性用 MQ——才是面试官想听的工程判断。加分点还有 DeadEvent 排障和 AsyncEventBus 线程池有界化。与 [Java设计模式全览](Java设计模式全览.md) 的观察者模式对照着复习，一题双吃。

> 来源：javadoop《单机环境下优雅地使用事件驱动进行代码解耦》（https://javadoop.com/post/guava-eventbus）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
