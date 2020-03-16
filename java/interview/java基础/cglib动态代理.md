看在前面
====

> * <a href="https://www.cnblogs.com/hetutu-5238/p/11996529.html">jdk动态代理和cglib动态代理底层实现原理详细解析(cglib动态代理篇)</a>

代理模式是一种很常见的模式，本文主要分析cglib动态代理的过程

一、举例
====

使用cglib代理需要引入两个包，maven的话包引入如下

```java
		<!-- https://mvnrepository.com/artifact/cglib/cglib -->
        <dependency>
            <groupId>cglib</groupId>
            <artifactId>cglib</artifactId>
            <version>3.3.0</version>
        </dependency>
        <!-- https://mvnrepository.com/artifact/org.ow2.asm/asm -->
        <dependency>
            <groupId>org.ow2.asm</groupId>
            <artifactId>asm</artifactId>
            <version>7.1</version>
        </dependency>
```

示例代码

```java
import net.sf.cglib.core.DebuggingClassWriter;
import net.sf.cglib.core.KeyFactory;
import net.sf.cglib.proxy.Callback;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

/**
 * @Description:
 * @author: zhoum
 * @Date: 2019-12-05
 * @Time: 9:36
 */
public class CgProxyFactory implements MethodInterceptor {



    public <T>T getProxy(Class<T> c){
        Enhancer enhancer = new Enhancer();
        enhancer.setCallbacks(new Callback[]{this});
        enhancer.setSuperclass(c);
        return (T)enhancer.create();
    }

    @Override
    public Object intercept(Object obj , Method method , Object[] args , MethodProxy proxy) throws Throwable {
        System.out.println("执行前-------->");
        Object o = proxy.invokeSuper(obj , args);
        System.out.println("执行后-------->");
        return o;
    }

    public static void main(String[] args) {
        System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "F:/DEBUG");
        CgProxyFactory factory = new CgProxyFactory();
        CgProxy proxy = factory.getProxy(CgProxy.class);
        proxy.say();
    }
}

class CgProxy{

    public String say(){
        System.out.println("普通方法执行");
        return "123";
    }
}
```
