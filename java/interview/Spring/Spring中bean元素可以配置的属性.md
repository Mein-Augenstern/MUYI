<bean> 除了 id 和 class 属性之外，还有一些可选的属性:

1. scope属性，默认<bean> 的 scope就是 ```singleton="true"```, springmvc和struts2的重要区别之一就是spring的controll是单例的，而struts2的action是：scope="prototype" ，还有 scope="request" ， scope="session"，scope="globalSession"(仅用于portlet)

2. abstract属性，是否是抽象的bean：

```java
<bean id="baseDAO" abstract="true">
    <property name="dataSource" ref="dataSource" />
    <property name="sqlMapClient" ref="sqlMapClient" />
</bean>    
<bean id="collectionDAO" class="net.minisns.dal.dao.CollectionDAOImpl" parent="baseDAO" />
<bean id="commentDAO" class="net.minisns.dal.dao.CommentDAOImpl" parent="baseDAO" />
```
3. depends-on 依赖于某个bean，其必须先初始化：<bean id="xxx" class="xxx" depends-on="refbean" />

4. lazy-init="true" 是否延迟初始化，默认为 false

5. dependency-check 是否对bean依赖的其它bean进行检查，默认值为 none，可取值有：none, simple, object, all等

6. factory-method 和 factory-bean用于静态工厂和非静态工厂：

```java
<bean id="bar" class="...StaticBarInterfaceFactory" factory-method="getInstance"/>
<bean id="barFactory" class="...NonStaticBarInterfaceFactory"/> 
<bean id="bar" factory-bean="barFactory" factory-method="getInstance"/>
```

7. init-method, destory-method 指定bean初始化和死亡时调用的方法，常用于 dataSource的连接池的配置

8. lookup-method 方法注入

```java
<bean id="newsBean" class="..xxx" singleton="false"> 
<bean id="mockPersister" class="..impl.MockNewsPersister">
  <lookup-method name="getNewsBean" bean="newsBean"/> 
</bean>  
```

表示 mockPersister 有一个依赖属性 newsBean，该属性的每次注入都是通过调用newsBean.getNewsBean() 方法获得的。

9. autowire 是否启用自动装配依赖，默认为 no, 其它取值还有：byName, byType, constructor
