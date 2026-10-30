看在前面
====

> * <a href="https://blog.csdn.net/qq_42405666/article/details/91550277">Spring Bean的生命周期</a>
> * <a href="https://www.cnblogs.com/javazhiyin/p/10905294.html">深究Spring中Bean的生命周期</a>

Bean的生命周期主线
====

先看 Servlet 的生命周期作对照：实例化，初始 init，接收请求 service，销毁 destroy。Spring 上下文中的 Bean 生命周期与之类似。

在传统的 Java 应用中，Bean 的生命周期很简单。使用 Java 关键字 new 实例化之后就能够使用了。一旦不再被使用，则由 Java 自动执行垃圾回收。相比之下，Spring 管理的 Bean 生命周期复杂得多。正确理解 Bean 的生命周期至关重要，因为 Spring 对 Bean 的管理可扩展性强。完整的构造过程如下图：

![Spring中Bean的生命周期](/images/legacy/Spring中Bean的生命周期.jpg)

下面对图中每一个步骤做文字描述：

1. **实例化 Bean**：对于 BeanFactory 容器，客户请求未初始化的 bean 时，就调用 createBean 实例化。初始化 bean 时需要注入另一个尚未初始化的依赖，也会触发 createBean。对于 ApplicationContext 容器，启动结束后会根据 BeanDefinition 中的信息，实例化所有的 bean。

2. **设置对象属性（依赖注入）**：实例化后的对象被封装在 BeanWrapper 对象中。Spring 根据 BeanDefinition 中的信息，通过 BeanWrapper 提供的设置属性的接口完成依赖注入。

3. **Aware 回调**：Spring 会检测该对象是否实现了 xxxAware 接口，并将相关的 xxxAware 实例注入给 Bean：

    * 如果这个 Bean 实现了 BeanNameAware 接口，会调用它实现的 setBeanName(String beanId) 方法，此处传递的就是 Spring 配置文件中 Bean 的 id 值；

    * 如果这个 Bean 实现了 BeanFactoryAware 接口，会调用它实现的 setBeanFactory() 方法，传递的是 Spring 工厂自身；

    * 如果这个 Bean 实现了 ApplicationContextAware 接口，会调用 setApplicationContext(ApplicationContext) 方法，传入 Spring 上下文。

4. **BeanPostProcessor 前置处理**：如果 Bean 实现了 BeanPostProcessor 接口，将会调用 postProcessBeforeInitialization(Object obj, String s) 方法。

5. **InitializingBean**：如果 Bean 实现了 InitializingBean 接口，Spring 将调用其 afterPropertiesSet() 方法。

6. **init-method**：如果 Bean 在 Spring 配置文件中配置了 init-method 属性，则自动调用该初始化方法。

7. **BeanPostProcessor 后置处理**：如果这个 Bean 实现了 BeanPostProcessor 接口，将会调用 postProcessAfterInitialization(Object obj, String s) 方法。由于这个方法是在 Bean 初始化结束时调用的，所以可以被应用于内存或缓存技术。

   以上几个步骤完成后，Bean 就已经被正确创建，可以使用了。它们将一直驻留在应用上下文中，直到应用上下文被销毁。

8. **销毁链**：当 Bean 不再需要时，会经过清理阶段。如果 Bean 实现了 DisposableBean 接口，会调用其实现的 destroy() 方法。最后，如果配置了 destroy-method 属性，会自动调用其配置的销毁方法。

> 面试口径提示：不同资料对这个流程的描述粒度略有差异。有的版本把 afterPropertiesSet 与 init-method 归为「初始化」一步，有的版本合并描述 BeanPostProcessor 的前后置处理。但主干顺序统一：**实例化 → 依赖注入 → Aware 回调 → BeanPostProcessor#postProcessBeforeInitialization → InitializingBean#afterPropertiesSet → init-method → BeanPostProcessor#postProcessAfterInitialization → 使用 → DisposableBean#destroy → destroy-method**。
