Question
====

* 依赖注入的概念？
* 依赖注入的作用？
* 有哪些不同类型的IOC（依赖注入）方式？

依赖注入的概念？作用
====

* 通常情况下，调用者会采用"new被调用者"的代码方式来创建对象，但这种方式会导致调用者与被调用者之间的耦合性增加，不利于后期项目的升级和维护。

* 而使用了Spring框架之后，对象的实例不再由调用者来创建，而是**由Spring容器来创建,Spring容器会负责控制程序之间的关系。这样控制权便由应用代码转移到Spring容器，控制权发生了反转，这就是Spring的控制反转**。

* 从Spring容器来看，**Spring容器负责将被依赖对象赋值给调用者的成员变量，这就相当于为调用者注入了它依赖的实例，这就是Spring的依赖注入**。


有哪些不同类型的IOC（依赖注入）方式？
====

* 构造器依赖注入：构造器依赖注入通过容器触发一个类的构造器来实现的，该类有一系列参数，每个参数代表一个对其他类的依赖。

* Setter方法注入：Setter方法注入是容器通过调用无参构造器或无参static工厂 方法实例化bean之后，调用该bean的setter方法，即实现了基于setter的依赖注入。

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

构造函数注入使用：```<constructor-arg index="0" value="7500000"/>， <constructor-arg type="int" value="7500000"/>```，对于非简单参数，需要使用ref ```<constructor-arg index="1" ref="bar"/>```

```java
<bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
  <property name="dataSource" ref="dataSource" />
  <property name="configLocation" value="classpath:config/mybatis-config.xml" />
  <property name="mapperLocations" value="classpath*:config/mappers/**/*.xml" />
</bean>
```

setter方法注入使用 ```<property name="username" value="xxx"/>```, 非简单类型属性使用ref ```<property name="xxbean" ref="xxx"/>```

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

也很简单，list属性就是 ```<list>```里面包含```<value>```或者```<ref>```或者```<bean>```, set也类似。map是```<map>```里面包含```<entry>```这个也好理解，因为map的实现就是使用内部类Entry来存储key和value. Properties是 ```<props>```里面包含```<prop>```.
