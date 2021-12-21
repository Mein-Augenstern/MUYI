看在前面
====

* maven依赖jar包时版本冲突解决方案：https://blog.csdn.net/weixin_43238110/article/details/107044835

jar包版本是以groupId+artifactId为坐标的，在groupId+artifactId相同的情况下version不同代码不同的版本，一般都是version的不同导致的jar冲突。同一个产品除非是大版本的升级，否则groupId+artifactId一般不会变。

1、第一声明优先原则：
------

在自己的pom文件中，如果有两个名称相同但版本不同的依赖声明，那么先写的会生效。—开发在自己定义的pom文件中，一般不会发生此种情况。

2、路径优先原则：
------

直接以来(自己在pom中添加的依赖)优于间接依赖;比如自己在pom文件中添加了A 1.0, B 2.0 两个jar包依赖，B又依赖了A2.0，那么最终生效的是A 1.0

3、排除原则：
------

在间接依赖冲突时，可以将不需要的jar的间接依赖排除，从而解决冲突。
间接依赖的生效原则：先看层深，层级浅的生效；层深相同在看前后，谁先声明则生效。

```java
<dependency>
    <groupId>org.apache.struts</groupId>
    <artifactId>struts2-spring-plugin</artifactId>
    <version>2.3.24</version>
    <exclusions>
      <exclusion>
        <groupId>org.springframework</groupId>
        <artifactId>spring-beans</artifactId>
      </exclusion>
    </exclusions>
</dependency>
```
为了方便可以直接在IDEA中的可视化界面排除：点击pom文件，选择下边的Dependency Analyzer，可以搜索框输入jar名称，在需要排除的版本号上右键exclude即可。

4、版本锁定原则(推荐使用)：
------

如果微服务较多，为了三方件版本归一，会在父pom中指定具体的依赖三方件的版本号，在服务中依赖父pom即可。
如果在间接依赖中的某些jar包在父pom中已经指定了对应的版本号，则在依赖树种此依赖的版本都会被刷成父pom中指定的版本；如果pom中没有指定此依赖，则此间接依赖的版本号还是其自身指定的版本号。

```java
	<properties>
        <spring.version>4.2.4.RELEASE</spring.version>
        <hibernate.version>5.0.7.Final</hibernate.version>
        <struts.version>2.3.24</struts.version>
    </properties>
    <!-- 锁定版本，struts2-2.3.24、spring4.2.4、hibernate5.0.7 -->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework</groupId>
                <artifactId>spring-context</artifactId>
                <version>${spring.version}</version>
            </dependency>
	</dependencies>
</dependencyManagement>
```



