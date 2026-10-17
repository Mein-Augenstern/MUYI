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
