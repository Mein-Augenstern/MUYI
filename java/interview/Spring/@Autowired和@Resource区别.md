看在前面
====

> * <a href="https://blog.csdn.net/qq_35180973/article/details/82319564">java面试专题之@Autowired和@Resource的区别</a>

1、@Autowired与@Resource都可以用来装配bean. 都可以写在字段上,或写在setter方法上。

2、@Autowired默认按类型装配（这个注解是属于spring的），默认情况下必须要求依赖对象必须存在，如果要允许null值，可以设置它的required属性为false，如：```@Autowired(required=false)``` ，如果我们想使用名称装配可以结合@Qualifier注解进行使用，如下：

```java
@Autowired()
@Qualifier(“baseDao”) 
private BaseDao baseDao;
```

3、@Resource 是JDK1.6支持的注解，默认按照名称进行装配，名称可以通过name属性进行指定，如果没有指定name属性，当注解写在字段上时，默认取字段名，按照名称查找，如果注解写在setter方法上默认取属性名进行装配。当找不到与名称匹配的bean时才按照类型进行装配。但是需要注意的是，如果name属性一旦指定，就只会按照名称进行装配。

只不过注解处理器我们使用的是Spring提供的，是一样的，无所谓解耦不解耦的说法，两个在便利程度上是等同的。

区别
====

1.@Autowired是默认按照类型装配的，@Resource默认是按照名称装配的byName，通过参数名 自动装配，如果一个bean的name 和另外一个bean的 property 相同，就自动装配。byType 通过参数的数据类型自动装配，如果一个bean的数据类型和另外一个bean的property属性的数据类型兼容，就自动装配

即@Autowired 默认按类型装配 。依赖对象必须存在，如果要允许null值，可以设置它的required属性为false @Autowired(required=false)。也可以使用名称装配，配合@Qualifier注解。

```java
public class TestServiceImpl {
    @Autowired
    @Qualifier("userDao")
    private UserDao userDao; 
}
```

@Resource 默认按名称进行装配，通过name属性进行指定

```java
public class TestServiceImpl {
    // 下面两种@Resource只要使用一种即可
    @Resource(name="userDao")
    private UserDao userDao; // 用于字段上

    @Resource(name="userDao")
    public void setUserDao(UserDao userDao) { // 用于属性的setter方法上
        this.userDao = userDao;
    }
}
```

共同点
====

装配bean. 写在字段上,或写在setter方法

总结
====

@Autowired自动注解，举个例子吧，一个类，俩个实现类，Autowired就不知道注入哪一个实现类，而Resource有name属性，可以区分。
