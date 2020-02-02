Question
====

* 依赖注入的概念？
* 依赖注入的作用？

示例：
====

1. 依赖注入的方式分为构造函数注入和setter方法注入：

```java
<bean id="exampleBean" class="examples.ExampleBean">
    <constructor-arg index="0" value="7500000"/>
    <constructor-arg index="1" ref="bar"/>
</bean>
<bean id="bar" class="x.y.Bar"/>
```

构造函数注入使用：<constructor-arg index="0" value="7500000"/>， <constructor-arg type="int" value="7500000"/>，对于非简单参数，需要使用ref <constructor-arg index="1" ref="bar"/>

```java
<bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
  <property name="dataSource" ref="dataSource" />
  <property name="configLocation" value="classpath:config/mybatis-config.xml" />
  <property name="mapperLocations" value="classpath*:config/mappers/**/*.xml" />
</bean>
```

setter方法注入使用 <property name="username" value="xxx"/>, 非简单类型属性使用ref <property name="xxbean" ref="xxx"/>

2. 集合等复杂类型的注入

```java
<bean id="moreComplexObject" class="example.ComplexObject">
    <!-- results in a setAdminEmails(java.util.Properties) call -->
    <property name="adminEmails">
        <props>
            <prop key="administrator">administrator@example.org</prop>
            <prop key="support">support@example.org</prop>
            <prop key="development">development@example.org</prop>
        </props>
    </property>
    <!-- results in a setSomeList(java.util.List) call -->
    <property name="someList">
        <list>
            <value>a list element followed by a reference</value>
            <ref bean="myDataSource" />
        </list>
    </property>
    <!-- results in a setSomeMap(java.util.Map) call -->
    <property name="someMap">
        <map>
            <entry key="an entry" value="just some string"/>
            <entry key ="a ref" value-ref="myDataSource"/>
        </map>
    </property>
    <!-- results in a setSomeSet(java.util.Set) call -->
    <property name="someSet">
        <set>
            <value>just some string</value>
            <ref bean="myDataSource" />
        </set>
    </property>
</bean>
```

也很简单，list属性就是 <list>里面包含<value>或者<ref>或者<bean>, set也类似。map是<map>里面包含<entry>这个也好理解，因为map的实现就是使用内部类Entry来存储key和value. Properties是 <props>里面包含<prop>.
