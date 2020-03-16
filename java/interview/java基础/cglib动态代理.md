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

二、原理解析
====

1 Enhancer.create()方法
------

通过上面代码，相信大家都能知道主要创建代理类的方法为Enhancer.create()方法，但是我们在执行这个方法之前设置了两个值，可以分别看下方法体

setCallbacks()，即设置回调，我们创建出代理类后调用方法则是使用的这个回调接口，类似于jdk动态代理中的InvocationHandler

```java
public void setCallbacks(Callback[] callbacks) {
        if (callbacks != null && callbacks.length == 0) {
            throw new IllegalArgumentException("Array cannot be empty");
        }
        this.callbacks = callbacks;
    }
```

setSuperClass 即设置代理类，这儿可以看到做了个判断，如果为interface则设置interfaces,如果是Object则设置为null(因为所有类都自动继承Object),如果为普通class则设置class，可以看到cglib代理不光可以代理接口，也可以代理普通类

```java
public void setSuperclass(Class superclass) {
        if (superclass != null && superclass.isInterface()) {
　　　　　　　　//设置代理接口
            setInterfaces(new Class[]{ superclass });
        } else if (superclass != null && superclass.equals(Object.class)) {
            // 未Object则用设置
            this.superclass = null;
        } else {
　　　　　　 //设置代理类
            this.superclass = superclass;
        }
    }

public void setInterfaces(Class[] interfaces) {
    this.interfaces = interfaces;
}
```

上面总结看来主要设置两个我们需要用到的信息

```java
    public Object create() {
        //不作代理类限制
        classOnly = false;
        //没有构造参数类型
        argumentTypes = null;
        //执行创建
        return createHelper();
    }
```

2 Enhancer.createHelper()方法
------

```java
private Object createHelper() { 
    //进行验证 并确定CallBack类型 本方法是用的MethodInterceptor
    preValidate();
    
    //获取当前代理类的标识类Enhancer.EnhancerKey的代理
    Object key = KEY_FACTORY.newInstance((superclass != null) ? superclass.getName() : null,
            ReflectUtils.getNames(interfaces),
            filter == ALL_ZERO ? null : new WeakCacheKey<CallbackFilter>(filter),
            callbackTypes,
            useFactory,
            interceptDuringConstruction,
            serialVersionUID);
    //设置当前enhancer的代理类的key标识
    this.currentKey = key;
    //调用父类即 AbstractClassGenerator的创建代理类
    Object result = super.create(key);
    return result;
}

private void preValidate() {
    if (callbackTypes == null) {
        //确定传入的callback类型
        callbackTypes = CallbackInfo.determineTypes(callbacks, false);
        validateCallbackTypes = true;
    }
    if (filter == null) {
        if (callbackTypes.length > 1) {
            throw new IllegalStateException("Multiple callback types possible but no filter specified");
        }
        filter = ALL_ZERO;
    }
}

    //最后是遍历这个数组来确定  本方法是用的MethodInterceptor
    private static final CallbackInfo[] CALLBACKS = {
        new CallbackInfo(NoOp.class, NoOpGenerator.INSTANCE),
        new CallbackInfo(MethodInterceptor.class, MethodInterceptorGenerator.INSTANCE),
        new CallbackInfo(InvocationHandler.class, InvocationHandlerGenerator.INSTANCE),
        new CallbackInfo(LazyLoader.class, LazyLoaderGenerator.INSTANCE),
        new CallbackInfo(Dispatcher.class, DispatcherGenerator.INSTANCE),
        new CallbackInfo(FixedValue.class, FixedValueGenerator.INSTANCE),
        new CallbackInfo(ProxyRefDispatcher.class, DispatcherGenerator.PROXY_REF_INSTANCE),
    };
```
方法主要做了下验证并确定Callback类型，我们使用的是MethodIntercepter。然后创建当前代理类的标识代理类，用这个标识代理类调用父类(AbstractClassGenerator)的create(key方法创建)，我们主要分析下标识代理类创建的逻辑和后面父类创建我们需要的代理类逻辑。

标识代理类的创建类成员变量即KEY_FACTORY是创建代理类的核心，我们先分析下这个





