看在前面
====

> * <a href="https://blog.csdn.net/qq_34651407/article/details/85015947">简单介绍下servlet的生命周期，servlet是否会多次初始化？</a>

一句话答案
====

Servlet的生命周期由Web容器来管理：

1. 实例化，一个Servlet类只会创建一个实例。当第一个用户第一次访问Servlet时，创建对象保存在web容器中；

2. 初始化，调用init方法初始化Servlet，只执行一次

3. 服务，调用service方法，用户每访问一次Servlet就调用一次

4. 销毁，调用destroy方法，web容器关闭

5. 最后，Servlet 由 JVM 的垃圾回收器回收。

init 方法被设计成只调用一次。它在第一次创建 Servlet 时被调用，在后续每次用户请求时不再调用。因此servlet不会多次初始化

一、生命周期状态机
------

把五个阶段画成状态机，重点是"谁触发、执行几次"：

```
              (容器启动/首个请求到达, 按 load-on-startup 决定)
                          │ 类加载 + 反射调用无参构造
                          ▼
                    ┌────────────┐
                    │  NEW 实例化 │  构造器执行 1 次
                    └─────┬──────┘
                          │ init(ServletConfig)     —— 只执行 1 次
                          ▼
                    ┌────────────┐   每个请求到达
              ┌────►│  SERVICE   │ ───────────────► service()
              │     │ (服务中)    │   service 按请求方法分派给
              │     └─────┬──────┘   doGet()/doPost()/...
     请求继续到达         │
                          │ 容器正常关闭/应用卸载
                          ▼
                    ┌────────────┐
                    │ DESTROYED  │  destroy() 只执行 1 次
                    │  (销毁)     │  之后交给 GC 回收
                    └────────────┘
```

* 实例化的时机可以配置：`<load-on-startup>1</load-on-startup>`（或注解 `@WebServlet(loadOnStartup=1)`）。让容器**启动时**就创建，否则默认**第一个请求到达**时才创建——这就是"第一次访问偏慢"的常见原因；

* init 只在创建后调用一次，用于做一次性初始化（读配置、建连接池）。想重复初始化只能先 destroy 再重新实例化，正常业务里不存在；

* service 抛出的细节：service 会根据 request.getMethod() 把 GET/POST 分派到 doGet/doPost，所以自定义 Servlet 通常只重写 doGet/doPost 而不碰 service。

二、单实例多线程模型
------

"servlet 不会多次初始化"的另一半是：**一个 Servlet 实例要同时服务所有请求**。容器为每个请求分配一个线程，并发调用同一个实例的 service 方法：

```
        请求A ──► 线程A ─┐
        请求B ──► 线程B ─┼──► 同一个 Servlet 实例 .service()
        请求C ──► 线程C ─┘
```

由此推出三个高频结论：

* **不要在 Servlet 里写可变的成员变量**：成员变量被所有请求线程共享，并发写就是数据竞争。经典事故是把用户信息存成成员变量导致串号；

* 局部变量（方法内创建的）线程安全，每次调用都在各自线程栈上；

* init/destroy 由容器保证单线程调用，适合放初始化与清理逻辑。

三、thread-per-request 与线程池
------

* Servlet 采用 **thread-per-request** 模型：一个请求绑定一个线程。线程阻塞在 IO 上时也占着线程——并发上升，线程数暴涨，上下文切换与内存占用成为瓶颈；

* 容器不会无限制建线程，而是用**线程池**复用。如 Tomcat 的 maxThreads（默认 200）、acceptCount（等待队列）、minSpareThreads 这类参数控制。请求超过 maxThreads + acceptCount 才会拒绝连接；

* 线程池只是"复用线程"救不了"线程被阻塞"。慢 SQL、下游慢调用会占满线程池，表现为接口大面积超时——这也是异步化的动机；

* 异步化的两条路。一是 Servlet 3.0 的 AsyncContext（请求处理权交回容器，业务放到自己的线程池再补写响应）。二是直接换 Spring WebFlux/Reactor 的事件循环模型（线程数小于连接数）。参见 [Spring异步调用@Async](Spring异步调用@Async.md)、[异步请求与异步调用的区别](异步请求与异步调用的区别.md)；

* Spring MVC 的 DispatcherServlet 本身就是一个 Servlet。这条生命周期链条同样适用于 Spring 应用，Spring 只是接管了"service 里做什么"（HandlerMapping → HandlerAdapter → ViewResolver）。

四、常见追问
------

* "init 之前构造器执行几次？"——构造器也只执行一次，实例化与初始化是两个动作，构造器先于 init；

* "两个用户同时访问会不会创建两个 Servlet？"——不会，默认单实例；`SingleThreadModel`（已废弃）曾试图解决并发安全，正确做法是无状态设计；

* "destroy 什么时候执行？"——容器**正常关闭**或应用被卸载时；kill -9 不会给 destroy 机会，所以不能把关键的资源释放全押在 destroy 上。

延伸阅读：[Spring异步调用@Async](Spring异步调用@Async.md)、[谈谈Spring中Bean的生命周期](Spring中Bean的生命周期.md)。

> 2026-10 扩充（Round1 查漏）。
