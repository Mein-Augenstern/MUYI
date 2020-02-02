看在前面
====

> * <a href="https://www.cnblogs.com/digdeep/p/4518571.html">深入剖析 Spring 框架的 BeanFactory</a>

简介
====

说到Spring框架，人们往往大谈特谈一些似乎高逼格的东西，比如依赖注入，控制反转，面向切面等等。但是却忘记了最基本的一点，Spring的本质是一个bean工厂(beanFactory)或者说bean容器，它按照我们的要求，生产我们需要的各种各样的bean，提供给我们使用。只是在生产bean的过程中，需要解决bean之间的依赖问题，才引入了依赖注入(DI)这种技术。也就是说依赖注入是beanFactory生产bean时为了解决bean之间的依赖的一种技术而已。

那么我们为什么需要Spring框架来给我们提供这个beanFactory的功能呢？原因是一般我们认为是，可以将原来硬编码的依赖，通过Spring这个beanFactory这个工厂来注入依赖，也就是说原来只有依赖方和被依赖方，现在我们引入了第三方——spring这个beanFactory，由它来解决bean之间的依赖问题，达到了松耦合的效果；这个只是原因之一，还有一个更加重要的原因：在没有spring这个beanFactory之前，我们都是直接通过new来实例化各种对象，现在各种对象bean的生产都是通过beanFactory来实例化的，这样的话，spring这个beanFactory就可以在实例化bean的过程中，做一些小动作——在实例化bean的各个阶段进行一些额外的处理，也就是说beanFactory会在bean的生命周期的各个阶段中对bean进行各种管理，并且spring将这些阶段通过各种接口暴露给我们，**让我们可以对bean进行各种处理，我们只要让bean实现对应的接口，那么spring就会在bean的生命周期调用我们实现的接口来处理该bean**。下面我们看是如何实现这一点的。

一、bean容器的启动
====

bean在实例化之前，必须是在bean容器启动之后。所以就有了两个阶段：

> * bean容器的启动阶段；
> * 容器中bean的实例化阶段；
 
首先我们来说说bean容器的启动阶段做了什么事情：

1. 首先是读取```bean```的```xml```配置文件，然后解析```xml```文件中的各种```bean```的定义，将```xml```文件中的每一个```<bean />```元素分别转换成一个```BeanDefinition```对象，其中保存了从配置文件中读取到的该```bean```的各种信息：

```java
public abstract class AbstractBeanDefinition extends BeanMetadataAttributeAccessor
        implements BeanDefinition, Cloneable {
    private volatile Object beanClass;
    private String scope = SCOPE_DEFAULT;
    private boolean abstractFlag = false;
    private boolean lazyInit = false;
    private int autowireMode = AUTOWIRE_NO;
    private int dependencyCheck = DEPENDENCY_CHECK_NONE;
    private String[] dependsOn;private ConstructorArgumentValues constructorArgumentValues;
    private MutablePropertyValues propertyValues;private String factoryBeanName;
    private String factoryMethodName;
    private String initMethodName;
    private String destroyMethodName;
```

```beanClass```保存```bean```的```class```属性，```scope```保存```bean```是否单例，```abstractFlag```保存该```bean```是否抽象，```lazyInit```保存是否延迟初始化，```autowireMode```保存是否自动装配，```dependencyCheck```保存是否坚持依赖，```dependsOn```保存该```bean```依赖于哪些```bean```(这些```bean```必须提取初始化)，**```constructorArgumentValues```保存通过构造函数注入的依赖，```propertyValues```保存通过```setter```方法注入的依赖**，```factoryBeanName```和```factoryMethodName```用于```factorybean```，也就是工厂类型的```bean```，```initMethodName```和```destroyMethodName```分别对应```bean```的```init-method```和```destory-method```属性，比如：

```java
<bean name="dataSource" class="com.alibaba.druid.pool.DruidDataSource" init-method="init" destroy-method="close">
```

读完配置文件之后，得到了很多的BeanDefinition对象。

2. 然后通过BeanDefinitionRegistry将这些bean注册到beanFactory中

```java
public interface BeanDefinitionRegistry extends AliasRegistry {
    void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)throws BeanDefinitionStoreException;
    void removeBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;
    BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;
    boolean containsBeanDefinition(String beanName);
    String[] getBeanDefinitionNames();
    int getBeanDefinitionCount();
    boolean isBeanNameInUse(String beanName);
}
```

BeanFactory的实现类，需要实现BeanDefinitionRegistry 接口：

```java
@SuppressWarnings("serial")
public class DefaultListableBeanFactory extends AbstractAutowireCapableBeanFactory
        implements ConfigurableListableBeanFactory, BeanDefinitionRegistry, Serializable {
    /** Map of bean definition objects, keyed by bean name */
    private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<String, BeanDefinition>(64);
    
    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
            throws BeanDefinitionStoreException {
        // ... ...
       this.beanDefinitionMap.put(beanName, beanDefinition);
       // ... ...
    }
```

我们看到BeanDefinition被注册到了 DefaultListableBeanFactory， 保存在它的一个ConcurrentHashMap中。

将BeanDefinition注册到了beanFactory之后，在这里Spring为我们提供了一个扩展的切口，允许我们通过实现接口BeanFactoryPostProcessor 在此处来插入我们定义的代码：

```java
public interface BeanFactoryPostProcessor {
    /**
     * Modify the application context's internal bean factory after its standard
     * initialization. All bean definitions will have been loaded, but no beans
     * will have been instantiated yet. This allows for overriding or adding
     * properties even to eager-initializing beans.
     * @param beanFactory the bean factory used by the application context
     * @throws org.springframework.beans.BeansException in case of errors
     */
    void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException;
}
```

典型的例子就是：```PropertyPlaceholderConfigurer```，我们一般在配置数据库的dataSource时使用到的占位符的值，就是它注入进去的：

```java
public abstract class PropertyResourceConfigurer extends PropertiesLoaderSupport
        implements BeanFactoryPostProcessor, PriorityOrdered {
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        try {
            Properties mergedProps = mergeProperties();
            // Convert the merged properties, if necessary.
            convertProperties(mergedProps);
            // Let the subclass process the properties.
            processProperties(beanFactory, mergedProps);
        }
        catch (IOException ex) {
            throw new BeanInitializationException("Could not load properties", ex);
        }
    }
```
```processProperties(beanFactory, mergedProps);```在子类中实现的，功能就是将

```java
<bean name="dataSource" class="com.alibaba.druid.pool.DruidDataSource" init-method="init" destroy-method="close">
        <property name="url" value="${jdbc_url}" />
        <property name="username" value="${jdbc_username}" />
        <property name="password" value="${jdbc_password}" />
```
```${jdbc_username}```等等这些替换成实际值。

在上面我们了解了bean容器的启动阶段做了什么事情，接下来看看bean的实例化阶段做了什么事情。

实例化阶段主要是**通过反射或者CGLIB对bean进行实例化**，在这个阶段Spring又给我们暴露了很多的扩展点：

1. 各种的Aware接口，比如 BeanFactoryAware，MessageSourceAware，ApplicationContextAware

对于实现了这些Aware接口的bean，在实例化bean时Spring会帮我们注入对应的：BeanFactory， MessageSource，ApplicationContext的实例：

```java
public interface BeanFactoryAware extends Aware {
    /**
     * Callback that supplies the owning factory to a bean instance.
     * <p>Invoked after the population of normal bean properties
     * but before an initialization callback such as
     * {@link InitializingBean#afterPropertiesSet()} or a custom init-method.
     * @param beanFactory owning BeanFactory (never {@code null}).
     * The bean can immediately call methods on the factory.
     * @throws BeansException in case of initialization errors
     * @see BeanInitializationException
     */
    void setBeanFactory(BeanFactory beanFactory) throws BeansException;
}
```
```java
public interface ApplicationContextAware extends Aware {
    /**
     * Set the ApplicationContext that this object runs in.
     * Normally this call will be used to initialize the object.
     * <p>Invoked after population of normal bean properties but before an init callback such
     * as {@link org.springframework.beans.factory.InitializingBean#afterPropertiesSet()}
     * or a custom init-method. Invoked after {@link ResourceLoaderAware#setResourceLoader},
     * {@link ApplicationEventPublisherAware#setApplicationEventPublisher} and
     * {@link MessageSourceAware}, if applicable.
     * @param applicationContext the ApplicationContext object to be used by this object
     * @throws ApplicationContextException in case of context initialization errors
     * @throws BeansException if thrown by application context methods
     * @see org.springframework.beans.factory.BeanInitializationException
     */
    void setApplicationContext(ApplicationContext applicationContext) throws BeansException;
}
```

```java
public interface MessageSourceAware extends Aware {
    /**
     * Set the MessageSource that this object runs in.
     * <p>Invoked after population of normal bean properties but before an init
     * callback like InitializingBean's afterPropertiesSet or a custom init-method.
     * Invoked before ApplicationContextAware's setApplicationContext.
     * @param messageSource message sourceto be used by this object
     */
    void setMessageSource(MessageSource messageSource);
}
```

2. BeanPostProcessor接口

实现了BeanPostProcessor接口的bean，在实例化bean时Spring会帮我们调用接口中的方法：

```java
public interface BeanPostProcessor {
    /**
     * Apply this BeanPostProcessor to the given new bean instance <i>before</i> any bean
     * initialization callbacks (like InitializingBean's {@code afterPropertiesSet}
     * or a custom init-method). The bean will already be populated with property values.
     * The returned bean instance may be a wrapper around the original.*/
    Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException;
    /**
     * Apply this BeanPostProcessor to the given new bean instance <i>after</i> any bean
     * initialization callbacks (like InitializingBean's {@code afterPropertiesSet}
     * or a custom init-method). The bean will already be populated with property values.
     * The returned bean instance may be a wrapper around the original.*/
    Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException;
}
```

从注释中可以知道 postProcessBeforeInitialization方法在 InitializingBean接口的 afterPropertiesSet方法之前执行，而postProcessAfterInitialization方法在 InitializingBean接口的afterPropertiesSet方法之后执行。

3. InitializingBean接口

实现了InitializingBean接口的bean，在实例化bean时Spring会帮我们调用接口中的方法：

```java
public interface InitializingBean {
    /**
     * Invoked by a BeanFactory after it has set all bean properties supplied
     * (and satisfied BeanFactoryAware and ApplicationContextAware).
     * <p>This method allows the bean instance to perform initialization only
     * possible when all bean properties have been set and to throw an
     * exception in the event of misconfiguration.
     * @throws Exception in the event of misconfiguration (such
     * as failure to set an essential property) or if initialization fails.
     */
    void afterPropertiesSet() throws Exception;
}
```
4. DisposableBean接口

实现了BeanPostProcessor接口的bean，在该bean死亡时Spring会帮我们调用接口中的方法：
复制代码

```java
public interface DisposableBean {
    /**
     * Invoked by a BeanFactory on destruction of a singleton.
     * @throws Exception in case of shutdown errors.
     * Exceptions will get logged but not rethrown to allow
     * other beans to release their resources too.
     */
    void destroy() throws Exception;
}
```

InitializingBean接口 和 DisposableBean接口对应于 <bean /> 的 init-method 和 destory-method 属性，其经典的例子就是dataSource:

```java
<bean name="dataSource" class="com.alibaba.druid.pool.DruidDataSource" init-method="init" destroy-method="close">
```

所以在Spring初始化 dataSource 这个bean之后会调用 DruidDataSource.init 方法:

```java
public void init() throws SQLException {
    // ... ...try {
        lock.lockInterruptibly();
    } catch (InterruptedException e) {
        throw new SQLException("interrupt", e);
    }
    boolean init = false;
    try {  
        connections = new DruidConnectionHolder[maxActive];
        SQLException connectError = null;
        try {                
            for (int i = 0, size = getInitialSize(); i < size; ++i) {
                Connection conn = createPhysicalConnection();
                DruidConnectionHolder holder = new DruidConnectionHolder(this, conn);
                connections[poolingCount++] = holder;
            }
            if (poolingCount > 0) {
                poolingPeak = poolingCount;
                poolingPeakTime = System.currentTimeMillis();
            }
        } catch (SQLException ex) {
            LOG.error("init datasource error", ex);
            connectError = ex;
        }          
    } catch (SQLException e) {
        LOG.error("dataSource init error", e);
        throw e;
    } catch (InterruptedException e) {
        throw new SQLException(e.getMessage(), e);
    } finally {
        inited = true;
        lock.unlock();
    }
}
```

基本就是初始化数据库连接池。

在dataSource 这个bean死亡时会调用 DruidDataSource.close()方法：

```java
 public void close() {
        lock.lock();
        try {
          for (int i = 0; i < poolingCount; ++i) {
                try {
                    DruidConnectionHolder connHolder = connections[i];
                    for (PreparedStatementHolder stmtHolder : connHolder.getStatementPool().getMap().values()) {
                        connHolder.getStatementPool().closeRemovedStatement(stmtHolder);
                    }
                    connHolder.getStatementPool().getMap().clear();
                    Connection physicalConnection = connHolder.getConnection();
                    physicalConnection.close();
                    connections[i] = null;
                    destroyCount.incrementAndGet();
                } catch (Exception ex) {
                    LOG.warn("close connection error", ex);
                }
            }          
        } finally {
            lock.unlock();
        }
    }
```

基本就是关闭连接池中的连接。

另外注解 @PostConstruct 和 @PreDestroy 也能达到 InitializingBean接口 和 DisposableBean接口的效果。

二、总结

spring容器接管了bean的实例化，不仅仅是通过依赖注入达到了松耦合的效果，同时给我们提供了各种的扩展接口，来在bean的生命周期的各个时期插入我们自己的代码：

 1. BeanFactoryPostProcessor接口(在容器启动阶段)
 2. 各种的Aware接口
 3. BeanPostProcessor接口
 4. InitializingBean接口(@PostConstruct， init-method)
 5. DisposableBean接口(@PreDestroy, destory-method)
 
三、FactoryBean接口

实现了FactoryBean接口的bean是一类叫做factory的bean。其特点是，spring会在使用getBean()调用获得该bean时，会自动调用该bean的getObject()方法，所以返回的不是factory这个bean，而是这个bean.getOjbect()方法的返回值：

```java
public interface FactoryBean<T> {
    T getObject() throws Exception;
    Class<?> getObjectType();
    boolean isSingleton();
}
```

典型的例子有spring与mybatis的结合：

```java
<bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
  <property name="dataSource" ref="dataSource" />
  <property name="configLocation" value="classpath:config/mybatis-config-master.xml" />
  <property name="mapperLocations" value="classpath*:config/mappers/master/**/*.xml" />
</bean>
```

我们看上面该bean，**因为实现了FactoryBean接口，所以返回的不是 SqlSessionFactoryBean 的实例，而是他的 SqlSessionFactoryBean.getObject() 的返回值**：

```java
public class SqlSessionFactoryBean implements FactoryBean<SqlSessionFactory>, InitializingBean, ApplicationListener<ApplicationEvent> {

  private static final Log logger = LogFactory.getLog(SqlSessionFactoryBean.class);

  private Resource configLocation;

  private Resource[] mapperLocations;

  private DataSource dataSource;
  
  public SqlSessionFactory getObject() throws Exception {
    if (this.sqlSessionFactory == null) {
      afterPropertiesSet();
    }

    return this.sqlSessionFactory;
  }
```

其实他是一个专门生产 sqlSessionFactory 的工厂，所以才叫 SqlSessionFactoryBean。 而SqlSessionFactory又是生产SqlSession的工厂。

还有spring与ibatis的结合：

```java
<!-- Spring提供的iBatis的SqlMap配置 -->
<bean id="sqlMapClient" class="org.springframework.orm.ibatis.SqlMapClientFactoryBean">
    <property name="configLocation" value="classpath:sqlmap/sqlmap-config.xml" />
    <property name="dataSource" ref="dataSource" />
</bean>
```

```java
public class SqlMapClientFactoryBean implements FactoryBean<SqlMapClient>, InitializingBean {
    private Resource[] configLocations;
    private Resource[] mappingLocations;
    private Properties sqlMapClientProperties;
    private DataSource dataSource;
    private boolean useTransactionAwareDataSource = true;
    private Class transactionConfigClass = ExternalTransactionConfig.class;
    private Properties transactionConfigProperties;
    private LobHandler lobHandler;
    private SqlMapClient sqlMapClient;
    public SqlMapClient getObject() {
        return this.sqlMapClient;
    }
```

SqlMapClientFactoryBean 返回的是 getObject() 中返回的 sqlMapClient, 而不是 SqlMapClientFactoryBean 自己的实例。


