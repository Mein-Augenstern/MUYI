看在前面
====

> * <a href="https://blog.csdn.net/u012240455/article/details/79014329">Spring开启@Async异步方法（javaconfig配置）</a>
> * <a href="https://www.cnblogs.com/jpfss/p/10273129.html">Spring中@Async用法总结</a>
> * <a href="https://blog.csdn.net/YoungLee16/article/details/88398045">Spring boot 注解@Async无效,不起作用</a>
> * <a href="https://blog.csdn.net/fenglllle/article/details/91398384">Spring boot异步任务原理分析</a>

我们经常在需要提升性能或者项目架构解耦的过程中，使用线程池异步执行任务，经常使用ThreadPoolExecutor创建线程池。那么Spring对异步任务是如何处理的呢？

一、spring 异步任务
====

估计或多或少了解过一些，比如@EnableAsync可以开启异步任务，@Async用于注解说明当前方法是异步执行，下面使用demo看看Spring的异步任务如何执行。pom依赖，其实仅依赖Spring core context 就可以了，这里演示，另外spring boot还要许多好玩的特性。

```java
<dependencies>
	<dependency>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-web</artifactId>
		<version>2.1.3.RELEASE</version>
	</dependency>
	<dependency>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-test</artifactId>
		<version>2.1.3.RELEASE</version>
		<scope>test</scope>
	</dependency>
</dependencies>
```

```java
@RestController
@SpringBootApplication
public class AsyncMain {
    public static void main(String[] args) {
        SpringApplication.run(AsyncMain.class, args);
    }
 
    @Autowired
    private TaskService taskService;
 
    @RequestMapping(value = "/async-task", method = RequestMethod.GET)
    public String asyncMapping(){
        System.out.println(Thread.currentThread().getThreadGroup() + "http-------" + Thread.currentThread().getName());
        taskService.doTask();
        return "exec http ok--------------";
    }
}
```

```java
@EnableAsync
@Service
public class TaskService {
 
    @Async
    public String doTask(){
        System.out.println(Thread.currentThread().getThreadGroup() + "-------" + Thread.currentThread().getName());
        return "do task done";
    }
}
```

运行main方法，访问localhost:8080/async-task，控制台可以看到：

```java
2020-03-18 15:51:56.378  INFO 18036 --- [nio-8080-exec-3]
java.lang.ThreadGroup[name=main,maxpri=10]-------task-1
```

可以看到线程的name是task-1，而http访问的线程是http-nio-xxx。说明任务异步执行了。然而Spring的异步任务是如何执行的呢，我们也并未创建线程池，难道Spring替我们创建了？

二、Spring boot异步任务执行过程分析
====

首先，需要执行异步任务，必须创建线程池，那我们来揪出Spring创建的线程池，从启动日志可以看出

```java
2020-03-18 15:51:15.861  INFO 18036 --- [           main] o.s.s.concurrent.ThreadPoolTaskExecutor  : Initializing ExecutorService 'applicationTaskExecutor'
```

Spring默认给我们创建了applicationTaskExecutor的ExecutorService的线程池。通过源码分析，Spring boot的starter已经给我们设置了默认的执行器

```java
/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@link TaskExecutor}.
 *
 * @author Stephane Nicoll
 * @author Camille Vienot
 * @since 2.1.0
 */
@ConditionalOnClass(ThreadPoolTaskExecutor.class)
@Configuration
@EnableConfigurationProperties(TaskExecutionProperties.class)
public class TaskExecutionAutoConfiguration {

	/**
	 * Bean name of the application {@link TaskExecutor}.
	 */
	public static final String APPLICATION_TASK_EXECUTOR_BEAN_NAME = "applicationTaskExecutor";

	private final TaskExecutionProperties properties;

	private final ObjectProvider<TaskExecutorCustomizer> taskExecutorCustomizers;

	private final ObjectProvider<TaskDecorator> taskDecorator;

	public TaskExecutionAutoConfiguration(TaskExecutionProperties properties,
			ObjectProvider<TaskExecutorCustomizer> taskExecutorCustomizers,
			ObjectProvider<TaskDecorator> taskDecorator) {
		this.properties = properties;
		this.taskExecutorCustomizers = taskExecutorCustomizers;
		this.taskDecorator = taskDecorator;
	}

	@Bean
	@ConditionalOnMissingBean
	public TaskExecutorBuilder taskExecutorBuilder() {
		TaskExecutionProperties.Pool pool = this.properties.getPool();
		TaskExecutorBuilder builder = new TaskExecutorBuilder();
		builder = builder.queueCapacity(pool.getQueueCapacity());
		builder = builder.corePoolSize(pool.getCoreSize());
		builder = builder.maxPoolSize(pool.getMaxSize());
		builder = builder.allowCoreThreadTimeOut(pool.isAllowCoreThreadTimeout());
		builder = builder.keepAlive(pool.getKeepAlive());
		builder = builder.threadNamePrefix(this.properties.getThreadNamePrefix());
		builder = builder.customizers(this.taskExecutorCustomizers);
		builder = builder.taskDecorator(this.taskDecorator.getIfUnique());
		return builder;
	}

	@Lazy
	@Bean(name = { APPLICATION_TASK_EXECUTOR_BEAN_NAME,
			AsyncAnnotationBeanPostProcessor.DEFAULT_TASK_EXECUTOR_BEAN_NAME })
	@ConditionalOnMissingBean(Executor.class)
	public ThreadPoolTaskExecutor applicationTaskExecutor(TaskExecutorBuilder builder) {
		return builder.build();
	}

}
```

追根溯源：在Spring boot的autoconfigure中已经定义了默认实现，找到spring-boot-autoconfigure-2.1.3.RELEASE.jar打开META-INF/spring.factories文件

```java
org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration,\
org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration,\
```

Spring为我们定义了两种实现，如上所示，根据Spring boot的配置定律，我们可以通过配置来定义异步任务的参数

```java
/**
 * Configuration properties for task execution.
 *
 * @author Stephane Nicoll
 * @since 2.1.0
 */
@ConfigurationProperties("spring.task.execution")
public class TaskExecutionProperties {

	private final Pool pool = new Pool();

	/**
	 * Prefix to use for the names of newly created threads.
	 */
	private String threadNamePrefix = "task-";

	public Pool getPool() {
		return this.pool;
	}

	public String getThreadNamePrefix() {
		return this.threadNamePrefix;
	}

	public void setThreadNamePrefix(String threadNamePrefix) {
		this.threadNamePrefix = threadNamePrefix;
	}

	public static class Pool {

		/**
		 * Queue capacity. An unbounded capacity does not increase the pool and therefore
		 * ignores the "max-size" property.
		 */
		private int queueCapacity = Integer.MAX_VALUE;

		/**
		 * Core number of threads.
		 */
		private int coreSize = 8;

		/**
		 * Maximum allowed number of threads. If tasks are filling up the queue, the pool
		 * can expand up to that size to accommodate the load. Ignored if the queue is
		 * unbounded.
		 */
		private int maxSize = Integer.MAX_VALUE;

		/**
		 * Whether core threads are allowed to time out. This enables dynamic growing and
		 * shrinking of the pool.
		 */
		private boolean allowCoreThreadTimeout = true;

		/**
		 * Time limit for which threads may remain idle before being terminated.
		 */
		private Duration keepAlive = Duration.ofSeconds(60);

		public int getQueueCapacity() {
			return this.queueCapacity;
		}

		public void setQueueCapacity(int queueCapacity) {
			this.queueCapacity = queueCapacity;
		}

		public int getCoreSize() {
			return this.coreSize;
		}

		public void setCoreSize(int coreSize) {
			this.coreSize = coreSize;
		}

		public int getMaxSize() {
			return this.maxSize;
		}

		public void setMaxSize(int maxSize) {
			this.maxSize = maxSize;
		}

		public boolean isAllowCoreThreadTimeout() {
			return this.allowCoreThreadTimeout;
		}

		public void setAllowCoreThreadTimeout(boolean allowCoreThreadTimeout) {
			this.allowCoreThreadTimeout = allowCoreThreadTimeout;
		}

		public Duration getKeepAlive() {
			return this.keepAlive;
		}

		public void setKeepAlive(Duration keepAlive) {
			this.keepAlive = keepAlive;
		}

	}

}
```

spring boot的配置以spring.task.execution开头，参数的设置参考如上源码的属性设置。各位可以自行尝试，当然因为Spring bean的定义方式，我们可以复写bean来达到自定义的目的

```java
@Lazy
@Bean(name = { APPLICATION_TASK_EXECUTOR_BEAN_NAME,
		AsyncAnnotationBeanPostProcessor.DEFAULT_TASK_EXECUTOR_BEAN_NAME })
@ConditionalOnMissingBean(Executor.class)
public ThreadPoolTaskExecutor applicationTaskExecutor(TaskExecutorBuilder builder) {
	return builder.build();
}
```

比如：

```java

@Configuration
@EnableAsync
public class TaskAsyncConfig {
 
    @Bean
    public Executor initExecutor(){
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //定制线程名称，还可以定制线程group
        executor.setThreadFactory(new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
 
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(Thread.currentThread().getThreadGroup(), r,
                        "async-task-" + threadNumber.getAndIncrement(),
                        0);
                return t;
            }
        });
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setKeepAliveSeconds(5);
        executor.setQueueCapacity(100);
        executor.setRejectedExecutionHandler(null);
        return executor;
    }
}
```

重启，访问localhost:8080/async-task，证明我们写的Executor已经覆盖系统默认了。

```java
Thread t = new Thread(Thread.currentThread().getThreadGroup(), r,
                        "async-task-" + threadNumber.getAndIncrement(),
                        0);
```

```java
java.lang.ThreadGroup[name=main,maxpri=10]-------async-task-1
```

三、Spring 异步任务执行过程分析
====

方法断点跟踪

```java
@EnableAsync
@Service
public class TaskService {
 
    @Async
    public String doTask(){
        System.out.println(Thread.currentThread().getThreadGroup() + "-------" + Thread.currentThread().getName());
        return "do task done";
    }
}
```

**执行异步任务使用Spring CGLib动态代理AOP实现**

!
