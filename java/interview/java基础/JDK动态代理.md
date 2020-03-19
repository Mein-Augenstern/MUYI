看在前面
====

> * <a href="https://www.cnblogs.com/hetutu-5238/p/11988946.html">jdk动态代理和cglib动态代理底层实现原理超详细解析(jdk动态代理篇)</a>

代理模式是一种很常见的模式，本文主要分析jdk动态代理的过程

一、举例
====

```java
public interface  IProxy {

    String hello(String id);

}
```

```java
public class ProxyImpl implements IProxy {

    @Override
    public String hello(String id) {
        System.out.println(id);
        return null;
    }

}

```

```java
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ProxyFactory implements InvocationHandler {

    private Class target;

    public <T> T getProxy(Class<T> c) {
        this.target = c;
        return (T) Proxy.newProxyInstance(c.getClassLoader(), c.isInterface() ? new Class[]{c} : c.getInterfaces(), this);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        System.out.println("代理执行执行");
        if (!target.isInterface()) {
            method.invoke(target.newInstance(), args);
        }
        return "代理返回值";
    }

    public static void main(String[] args) {
        // 保存生成的代理类的字节码文件
        ProxyFactory proxyFactory = new ProxyFactory();
        IProxy proxyImpl = proxyFactory.getProxy(ProxyImpl.class);
        String result = proxyImpl.hello("hello word");
        System.out.println(result);
        
        System.out.println("---------");

        IProxy proxy = proxyFactory.getProxy(IProxy.class);
        result = proxy.hello("hello word");
        System.out.println(result);
    }

}
```

执行main方法后结果如下

```java
代理执行执行
hello word
代理返回值
---------
代理执行执行
代理返回值
```

可以看到定义的hello方法已经被执行，并且可以在不定义接口的实现类的时候仍然可以执行方法获取结果，这其实就很容易想到mybatis中直接调用mapper接口获取查询结果其实也是调用的mapper的动态代理类，说明动态代理对于构造框架有很重要的作用

二、原理解析
====

1 Proxy.newProxyInstance方法
------

我们可以看到构造代理类的核心方法为这句三个参数分别为

* 代理类的类加载器

* 代理类的所有接口，如果本身就是接口则直接传入本身

* 传入InvocationHandler接口的实现类

```java
Proxy.newProxyInstance(c.getClassLoader(),c.isInterface()?new Class[]{c}:c.getInterfaces(),this);
```

直接进入方法中查看

```java
public static Object newProxyInstance(ClassLoader loader,
                                          Class<?>[] interfaces,
                                          InvocationHandler h)throws IllegalArgumentException{
        //InvocationHandler不能为null
        Objects.requireNonNull(h);
    　　 //克隆出所有传入的接口数组
        final Class<?>[] intfs = interfaces.clone();
        
        //权限校验
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            checkProxyAccess(Reflection.getCallerClass(), loader, intfs);
        }

        /*
         * 查找或者生成代理类  核心逻辑  参数为类加载器和上面的接口数组
         */
        Class<?> cl = getProxyClass0(loader, intfs);

        /************************下面的代码逻辑先不用管 到后面会专门分析***************************/
        try {
            if (sm != null) {
                checkNewProxyPermission(Reflection.getCallerClass(), cl);
            }
            //拿到其构造方法
            final Constructor<?> cons = cl.getConstructor(constructorParams);
            final InvocationHandler ih = h;
            if (!Modifier.isPublic(cl.getModifiers())) {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    public Void run() {
                        cons.setAccessible(true);
                        return null;
                    }
                });
            }
            //通过构造方法新建类的实例并返回
            return cons.newInstance(new Object[]{h});
        } catch (IllegalAccessException|InstantiationException e) {
            throw new InternalError(e.toString(), e);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new InternalError(t.toString(), t);
            }
        } catch (NoSuchMethodException e) {
            throw new InternalError(e.toString(), e);
        }
    }
```

可以看到生成代理类的逻辑主要为 下面

```java
//查找或者生成代理类 核心逻辑 参数为类加载器和上面的接口数组
Class<?> cl = getProxyClass0(loader, intfs);
```

2 getProxyClass0方法
------

继续进入方法getProxyClass0(loader,intfs)

```java
private static Class<?> getProxyClass0(ClassLoader loader,
                                           Class<?>... interfaces) {
        if (interfaces.length > 65535) {
            throw new IllegalArgumentException("interface limit exceeded");
        }
        return proxyClassCache.get(loader, interfaces);
    }
```

可以看到方法比较简单，主要对接口数量做了个判断，然后通过缓存proxyClassCache获取代理类，这儿注意proxyClassCache在初始化时已经初始化了一个KeyFactory和ProxyClassFactory

这个后面创建代理类时将会用到

```java
private static final WeakCache<ClassLoader, Class<?>[], Class<?>>proxyClassCache = new WeakCache<>(new KeyFactory(), new ProxyClassFactory());
```

我们继续查看proxyClassCache的get方法

3 WeakCache的get方法
------

```java
    // key为加载器  P为接口数组
    public V get(K key, P parameter) {
        //接口不能为空
        Objects.requireNonNull(parameter);
        //清理旧的缓存
        expungeStaleEntries();
        //构造缓存的ClassLoader key
        Object cacheKey = CacheKey.valueOf(key, refQueue);

        //通过缓存的map查找
        ConcurrentMap<Object, Supplier<V>> valuesMap = map.get(cacheKey);
        if (valuesMap == null) {
            //如果值为空  则put一个新的空值进去 
            ConcurrentMap<Object, Supplier<V>> oldValuesMap
                = map.putIfAbsent(cacheKey,
                                  valuesMap = new ConcurrentHashMap<>());
            //再次确认 应该是防止并发状况如果已经有值则将valuesMap赋值为已有的值
            if (oldValuesMap != null) {
                valuesMap = oldValuesMap;
            }
        }

        //拿到该代理类的classKey
        Object subKey = Objects.requireNonNull(subKeyFactory.apply(key, parameter));
        //通过代理类的key查找对应的缓存Supplier
        Supplier<V> supplier = valuesMap.get(subKey);
        //factory为supplier的实现类
        Factory factory = null;

        while (true) {
            //如果Supplier不为空
            if (supplier != null) {
                //直接返回值
                V value = supplier.get();
                if (value != null) {
                    return value;
                }
            }
            //如果代理类创建工厂为空
            if (factory == null) {
                //则新建一个创建工厂
                factory = new Factory(key, parameter, subKey, valuesMap);
            }
            //如果supplier为空
            if (supplier == null) {
                //将当前的代理类key   以及新建的Factory存到缓存里面去
                supplier = valuesMap.putIfAbsent(subKey, factory);
                if (supplier == null) {
                    // 这个时候将supplier设置为factory
                    supplier = factory;
                }
                //如果supplier不为空  则用这个supplier替代之前的代理类key的值
            } else {
                if (valuesMap.replace(subKey, supplier, factory)) {
                    //将supplier设置为factory实现类
                    supplier = factory;
                } else {
                    //没有替换成功则直接返回缓存里面有的
                    supplier = valuesMap.get(subKey);
                }
            }
        }
    }
```

4 Factory的get方法
------

通过上面的逻辑可以得知  最后是新建了Factory factory = new Factory(key, parameter, subKey, valuesMap); 并将这个factory赋值给Suplier调用get方法返回构造的代理类。所以我们直接看Factory的get方法即可

```java
@Override
public synchronized V get() { // serialize access
    // //即再次确认 对应的类代理key的supplier有没有被更改
    Supplier<V> supplier = valuesMap.get(subKey);
    //如果被更改了则返回null
    if (supplier != this) {
	return null;
    }

    V value = null;
    try {
	//创建value
	value = Objects.requireNonNull(valueFactory.apply(key, parameter));
    } finally {
	if (value == null) { 
	    //如果value为null  则代表当前的supplier有问题 所以直接移除
	    valuesMap.remove(subKey, this);
	}
    }
    //再次确认value即返回的代理类不能为null
    assert value != null;

    // 将返回的代理类包装为一个缓存
    CacheValue<V> cacheValue = new CacheValue<>(value);

    // 将这个包装缓存存入缓存map
    reverseMap.put(cacheValue, Boolean.TRUE);

    // 替换当前代理类key的supplier为刚包装的缓存 后面则可以直接调用缓存  必须成功
    if (!valuesMap.replace(subKey, this, cacheValue)) {
	throw new AssertionError("Should not reach here");
    }
    return value;
}
```

该方法的主要作用则是通过valueFactory创建代理类后  将代理类包装为CacheValue(注意该类实现了Supplier接口)并将valuesMap缓存中对应代理类的Supplier替换为包装后的CacheValue,这样后面就可以直接调用CacheValue的get方法来获取代理类

接下来我们开始分析valueFactory.apply(key,parameters),这儿注意上面在初始化weakCache时已经讲到，在构造函数中传入了两个参数，new KeyFactory(), new ProxyClassFactory(),分别对应subKeyFactory和valueFactory，所以这里的valueFactory则代表ProxyClassFactory，所以我们直接看ProxyClassFactory的apply方法逻辑

5 ProxyClassFactory的apply方法
------

```java
private static final class ProxyClassFactory implements BiFunction<ClassLoader, Class<?>[], Class<?>>{
        // 代理类名前缀
        private static final String proxyClassNamePrefix = "$Proxy";

        // 原子long  用来做代理类编号
        private static final AtomicLong nextUniqueNumber = new AtomicLong();

        @Override
        public Class<?> apply(ClassLoader loader, Class<?>[] interfaces) {
            //构建一个允许相同值的key的map
            Map<Class<?>, Boolean> interfaceSet = new IdentityHashMap<>(interfaces.length);
            //遍历代理类的接口
            for (Class<?> intf : interfaces) {
                
                Class<?> interfaceClass = null;
                try {
                    //根据接口的名字以及传入的类加载器来构建一个class
                    interfaceClass = Class.forName(intf.getName(), false, loader);
                } catch (ClassNotFoundException e) {
                }
                if (interfaceClass != intf) {
                    //如果不同 则说明传入的类加载器和指定代理方法时的类加载器不是同一个加载器
                    //，根据双亲委派机制和jvm规定，只有同样的类加载器加载出来的一个类  才确保
                    //为同一个类型   即instance of 为true
                    throw new IllegalArgumentException(
                        intf + " is not visible from class loader");
                }
                //确认传入的接口class数组没有混入奇怪的东西
                if (!interfaceClass.isInterface()) {
                    throw new IllegalArgumentException(
                        interfaceClass.getName() + " is not an interface");
                }
                //将接口存储上面的interfaceSet中
                if (interfaceSet.put(interfaceClass, Boolean.TRUE) != null) {
                    throw new IllegalArgumentException(
                        "repeated interface: " + interfaceClass.getName());
                }
            }
            //声明代理类的包
            String proxyPkg = null;
            //设置生成代理类的修饰级别  目前是public final
            int accessFlags = Modifier.PUBLIC | Modifier.FINAL;
            
            for (Class<?> intf : interfaces) {
                //获取class修饰符魔数 
                //这儿接口正常获取到的值为1536 (INTERFACE 512  +  ABSTRACT1024)
                int flags = intf.getModifiers();
                //如果为接口则为true
                if (!Modifier.isPublic(flags)) {
                    //将accessFlags设置为final
                    accessFlags = Modifier.FINAL;
                    //获取接口名
                    String name = intf.getName();
                    
                    int n = name.lastIndexOf('.');
                    //再获取到包名
                    String pkg = ((n == -1) ? "" : name.substring(0, n + 1));
                    if (proxyPkg == null) {
                        //如果代理包名为空 则直接为这个接口的包  这儿是循环获取的  所以最后得到的包名是最后一个接口的包
                        proxyPkg = pkg;
                    } else if (!pkg.equals(proxyPkg)) {
                        throw new IllegalArgumentException(
                            "non-public interfaces from different packages");
                    }
                }
            }
            //如果包获取到的为空  则用系统提供的
            if (proxyPkg == null) {
                // com.sun.proxy.
                proxyPkg = ReflectUtil.PROXY_PACKAGE + ".";
            }

            //获得一个编号( 线程安全)
            long num = nextUniqueNumber.getAndIncrement();
            //构建代理类的名字  例如  com.sun.proxy.$Proxy0
            String proxyName = proxyPkg + proxyClassNamePrefix + num;

            //生成代理类的字节码
            byte[] proxyClassFile = ProxyGenerator.generateProxyClass(
                proxyName, interfaces, accessFlags);
            try {
                //根据字节码生成代理类
                return defineClass0(loader, proxyName,
                                    proxyClassFile, 0, proxyClassFile.length);
            } catch (ClassFormatError e) {
               
                throw new IllegalArgumentException(e.toString());
            }
        }
    }
```

该方法的逻辑则主要得到代理类的包名（一般来说为最后一个接口的包名），以及产生字节码文件 ，根据字节码文件生成代理类class并返回，核心的两个方法为ProxyGenerator.generateProxyClass(proxyName, interfaces, accessFlags);  以及defineClass0(loader, proxyName,proxyClassFile, 0, proxyClassFile.length);

6 generateProxyClass方法
------

我们先看字节码生成的方法，ProxyGenerator.generateProxyClass(proxyName, interfaces, accessFlags);  后面的类不开放源代码，可以使用idea自带的反编译工具查看，我将

参数名字做了些修改  （汗   。。不然var1  var2看的属实难受 ）

```java
public static byte[] generateProxyClass(final String proxyName, Class<?>[] interfaces, int accessFlags) {
        //生成一个生成器实例   并赋予对应的属性(包名，接口数组，修饰符)
        ProxyGenerator generator = new ProxyGenerator(proxyName, interfaces, accessFlags);
        //生成数组
        final byte[] resultByte = generator.generateClassFile();
        
        //如果saveGeneratedFiles为true  则生成本地文件  这儿不讲解 有兴趣的可以研究
        if (saveGeneratedFiles) {
            AccessController.doPrivileged(new PrivilegedAction<Void>() {
                public Void run() {
                    try {
                        int interfaces = proxyName.lastIndexOf(46);
                        Path accessFlags;
                        if (interfaces > 0) {
                            Path generator = Paths.get(proxyName.substring(0, interfaces).replace('.', File.separatorChar));
                            Files.createDirectories(generator);
                            accessFlags = generator.resolve(proxyName.substring(interfaces + 1, proxyName.length()) + ".class");
                        } else {
                            accessFlags = Paths.get(proxyName + ".class");
                        }

                        Files.write(accessFlags, resultByte, new OpenOption[0]);
                        return null;
                    } catch (IOException resultBytex) {
                        throw new InternalError("I/O exception saving generated file: " + resultBytex);
                    }
                }
            });
        }

        return resultByte;
    }
```

该方法主要声明一个生成器，然后调用generateClassFile方法生成byte[]数组，后面的可选则生成本地文件，这个就是一开始main方法中的System.getProperties().put("sun.misc.ProxyGenerator.saveGeneratedFiles", "true"); 当然也可以在java启动参数加上-D传入参数，我们主要看generateClassFile()方法

7 generateClassFile方法
------

该方法为核心生成方法  每步均有说明，代码后会附带一张java字节码文件构造，可以参照

```java
private byte[] generateClassFile() {
        //Map<String, List<ProxyGenerator.ProxyMethod>> proxyMethods = new HashMap(); 用来存储方法签名  及其对应的方法描述
        //addProxyMethod方法下面有详解
        
        
        //添加Object的hashcode方法
        this.addProxyMethod(hashCodeMethod, Object.class);
        //添加Object的equals方法
        this.addProxyMethod(equalsMethod, Object.class);
        //添加Object的toString方法
        this.addProxyMethod(toStringMethod, Object.class);
        Class[] interfaces = this.interfaces;
        //获取到接口数组的长度
        int interLength = interfaces.length;

        int index;
        Class in;
        for(index = 0; index < interLength; ++index) {
            in = interfaces[index];
            //迭代获取到当前接口的所有方法
            Method[] methods = in.getMethods();
            int methodLength = methods.length;
            //迭代所有方法
            for(int methodIndex = 0; methodIndex < methodLength; ++methodIndex) {
                Method m = methods[methodIndex];
                //添加方法签名与方法描述
                this.addProxyMethod(m, in);
            }
        }
        /************上面则就将所有的方法签名与方法描述都存入到了proxyMethods中********************/
        Iterator methodsIterator = this.proxyMethods.values().iterator();
        
        List meth;
        while(methodsIterator.hasNext()) {
            //迭代每个方法签名的值 （List<ProxyGenerator.ProxyMethod>）
            meth = (List)methodsIterator.next();
            //检查方法签名值一样的方法返回值是否相同
            checkReturnTypes(meth);
        }
        
        Iterator me;
        try {
            //添加构造方法  并将构造方法的二进制代码写入methodInfo中的ByteArrayOutputStream --》对应字节码文件中的有参构造
            this.methods.add(this.generateConstructor());
            //获取方法签名对应迭代器
            methodsIterator = this.proxyMethods.values().iterator();
            
            while(methodsIterator.hasNext()) {
                meth = (List)methodsIterator.next();
                me = meth.iterator();
                //获取到每个方法签名中对应描述列表的迭代器
                while(me.hasNext()) {
                    //获取到方法信息
                    ProxyGenerator.ProxyMethod proMe = (ProxyGenerator.ProxyMethod)me.next();
                    //在feilds添加方法对应参数名字 签名，以及访问修饰符的FiledInfo
                    this.fields.add(new ProxyGenerator.FieldInfo(proMe.methodFieldName, "Ljava/lang/reflect/Method;", 10));
                    //添加对应的方法  --》对应字节码文件中的代理类接口实现方法
                    this.methods.add(proMe.generateMethod());
                }
            }  
            //添加对应的静态方法  --》对应字节码文件中的static方法
            this.methods.add(this.generateStaticInitializer());
        } catch (IOException var10) {
            throw new InternalError("unexpected I/O Exception", var10);
        }
        //做一些方法和参数数量校验
        if (this.methods.size() > 65535) {
            throw new IllegalArgumentException("method limit exceeded");
        } else if (this.fields.size() > 65535) {
            throw new IllegalArgumentException("field limit exceeded");
        } else {
            //字节码常量池中的符号引用  保存类全限定名
            this.cp.getClass(dotToSlash(this.className));
            //常量池中符号引用保存继承的Proxy类的权限定名
            this.cp.getClass("java/lang/reflect/Proxy");
            //获取到所有的接口
            interfaces = this.interfaces;
            //获取到接口的长度
            interLength = interfaces.length;
            //遍历接口
            for(index = 0; index < interLength; ++index) {
                in = interfaces[index];
                //常量池中符号引用保存接口的全限定定名
                this.cp.getClass(dotToSlash(in.getName()));
            }
            //常量池符号引用中保存访问标志
            this.cp.setReadOnly();
            //创建输出流
            ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
            DataOutputStream outputStream = new DataOutputStream(byteOutputStream);

            try {
                //1.写入java魔数4个字节 对应16进制数据为JAVA魔数CA FE BA BE
                outputStream.writeInt(-889275714);
                //2..写入小版本号2个字节
                outputStream.writeShort(0);
                //3.写入大版本号2个字节
                outputStream.writeShort(49);
                //4.写入常量池计数2个字节 5.以及上面添加的常量池
                this.cp.write(outputStream);
                //6.设置访问标志 2个字节
                outputStream.writeShort(this.accessFlags);
                //7.设置类索引2个字节
                outputStream.writeShort(this.cp.getClass(dotToSlash(this.className)));
                //8.设置父类索引 2个字节   这儿继承Proxy类  所以只能代理接口 因为java单继承
                outputStream.writeShort(this.cp.getClass("java/lang/reflect/Proxy"));
                //9.设置接口长度 2个字节  所以前面要判断接口数量不能大于65535
                outputStream.writeShort(this.interfaces.length);
                Class[] interfacess = this.interfaces;
                int interfacelength = interfacess.length;

                for(int index = 0; index < interfacelength; ++index) {
                    Class c = interfacess[index];
                    //10.写入接口索引 2个字节
                    outputStream.writeShort(this.cp.getClass(dotToSlash(c.getName())));
                }
                //11.写入变量数量 2个字节
                outputStream.writeShort(this.fields.size());
                me = this.fields.iterator();
                
                while(me.hasNext()) {
                    ProxyGenerator.FieldInfo p = (ProxyGenerator.FieldInfo)me.next();
                    //12.写入变量
                    p.write(outputStream);
                }
                //13.写入方法数量 2个字节
                outputStream.writeShort(this.methods.size());
                me = this.methods.iterator();

                while(me.hasNext()) {
                    ProxyGenerator.MethodInfo proMe = (ProxyGenerator.MethodInfo)me.next();
                    //14.写入方法
                    proMe.write(outputStream);
                }
                //15.没有属性表  直接写0
                outputStream.writeShort(0);
                //返回一个完整class的字节码
                return byteOutputStream.toByteArray();
            } catch (IOException var9) {
                throw new InternalError("unexpected I/O Exception", var9);
            }
        }
    }
    
    
     private void addProxyMethod(Method method, Class<?> class) {
        //获取到方法名
        String methodName = method.getName();
        //获取所有方法参数类型
        Class[] methodParamTypes = method.getParameterTypes();
        //获取返回类型
        Class returnTypes = method.getReturnType();
        //获取所有的异常
        Class[] exceptions = method.getExceptionTypes();
        //方法名+方法参数类形的方法签名  用来标识唯一的方法
        String methodSign = methodName + getParameterDescriptors(methodParamTypes);
        //获取该方法的List<ProxyGenerator.ProxyMethod>
        Object me = (List)this.proxyMethods.get(methodSign);
        if (me != null) {
            //如果不为空 即出现了相同方法签名的方法
            Iterator iter = ((List)me).iterator();
            //则获得这个list的迭代器
            while(iter.hasNext()) {
                //开始迭代
                ProxyGenerator.ProxyMethod proxyMe = (ProxyGenerator.ProxyMethod)iter.next();
                //如果这个方法签名对应的方法描述中返回值也与传入的一致
                if (returnTypes == proxyMe.returnType) {
                    //则直接合并两个方法中抛出的所有异常然后直接返回
                    ArrayList methodList = new ArrayList();
                    collectCompatibleTypes(exceptions, proxyMe.exceptionTypes, methodList);
                    collectCompatibleTypes(proxyMe.exceptionTypes, exceptions, methodList);
                    proxyMe.exceptionTypes = new Class[methodList.size()];
                    proxyMe.exceptionTypes = (Class[])methodList.toArray(proxyMe.exceptionTypes);
                    return;
                }
            }
        } 
        //如果为空
        else {
            //则新建一个List
            me = new ArrayList(3);
            //将方法签名 与这个新建的List<ProxyGenerator.ProxyMethod> 方法描述 put进去
            this.proxyMethods.put(methodSign, me);
        }
        //然后再这个list中新添加一个ProxyMethod 方法描述  这个Proxymethod方法包含了传入的方法的所有特征
        ((List)me).add(new ProxyGenerator.ProxyMethod(methodName, methodParamTypes, returnTypes, exceptions, class));
    }
```

这个方法中涉及到两个重要概念，一个是class字节码文件构造  参考如下  类型中u后面的数字则代表字节长度   。而上述方法中构造自字节码文件的15个步骤也是按照如下表一步一步构造的，最后没有属性表的值

![JDK动态代理一](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/picture/JDK%E5%8A%A8%E6%80%81%E4%BB%A3%E7%90%86%E4%B8%80.png)

另一个是常量池 ，主要放置两大类常量，一个是字面量  即常见的字符串，以及声明为final的变量，另一个则是语言层面的符号引用，包含三类常量：类和接口的全限定名，字段的名称和描述符，方法的名称和描述符

生成的字节码文件如下

```java
package cn.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.UndeclaredThrowableException;

final class $Proxy0 extends Proxy implements IProx {
    
    //方法变量
    private static Method m1;
    private static Method m2;
    private static Method m3;
    private static Method m0;

    //构造方法
    public $Proxy0(InvocationHandler var1) throws  {
        super(var1);
    }
    //重写方法
    public final boolean equals(Object var1) throws  {
        try {
            return (Boolean)super.h.invoke(this, m1, new Object[]{var1});
        } catch (RuntimeException | Error var3) {
            throw var3;
        } catch (Throwable var4) {
            throw new UndeclaredThrowableException(var4);
        }
    }
    //重写方法
    public final String toString() throws  {
        try {
            return (String)super.h.invoke(this, m2, (Object[])null);
        } catch (RuntimeException | Error var2) {
            throw var2;
        } catch (Throwable var3) {
            throw new UndeclaredThrowableException(var3);
        }
    }
    //重写方法
    public final String hello(String var1) throws  {
        try {
            return (String)super.h.invoke(this, m3, new Object[]{var1});
        } catch (RuntimeException | Error var3) {
            throw var3;
        } catch (Throwable var4) {
            throw new UndeclaredThrowableException(var4);
        }
    }
    //重写方法
    public final int hashCode() throws  {
        try {
            return (Integer)super.h.invoke(this, m0, (Object[])null);
        } catch (RuntimeException | Error var2) {
            throw var2;
        } catch (Throwable var3) {
            throw new UndeclaredThrowableException(var3);
        }
    }
    //静态方法
    static {
        try {
            m1 = Class.forName("java.lang.Object").getMethod("equals", Class.forName("java.lang.Object"));
            m2 = Class.forName("java.lang.Object").getMethod("toString");
            m3 = Class.forName("cn.proxy.IProx").getMethod("hello", Class.forName("java.lang.String"));
            m0 = Class.forName("java.lang.Object").getMethod("hashCode");
        } catch (NoSuchMethodException var2) {
            throw new NoSuchMethodError(var2.getMessage());
        } catch (ClassNotFoundException var3) {
            throw new NoClassDefFoundError(var3.getMessage());
        }
    }
}
```

上述方法生成代理类字节码返回后则回到了 第5步中的ProxyClassFactory的apply方法 ，获取到字节码文件后 ，生成代理类class并返回，方法为

defineClass0(loader, proxyName,proxyClassFile, 0, proxyClassFile.length)   这个方法为本地方法 ，不能获取到源码，但也容易理解，就是根据类名，类加载器，字节码文件创建一个class类型

```java
private static native Class<?> defineClass0(ClassLoader loader, String name,byte[] b, int off, int len);
```

defineClass0返回了代理类信息后则回到了第4步中的Factory的get()方法中，并将生成的类信息继续返回到第3步，第3步则继续返回  一直会返回到第1步

8 所以，回到第1步的代码中继续分析
------

```java
public static Object newProxyInstance(ClassLoader loader,
                                          Class<?>[] interfaces,
                                          InvocationHandler h)throws IllegalArgumentException{
        //InvocationHandler不能为null
        Objects.requireNonNull(h);
    
        final Class<?>[] intfs = interfaces.clone();
        
        //权限校验
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            checkProxyAccess(Reflection.getCallerClass(), loader, intfs);
        }

        /*
         * 查找或者生成代理类  核心逻辑  参数为类加载器和上面的接口数组
         */
        Class<?> cl = getProxyClass0(loader, intfs);

        /*
         * 获取到代理类的class类  即cl
         */
        try {
            //校验权限
            if (sm != null) {
                checkNewProxyPermission(Reflection.getCallerClass(), cl);
            }
            //拿到其有参构造方法   即字节码文件中的 public $Proxy0(InvocationHandler var1)方法
            final Constructor<?> cons = cl.getConstructor(constructorParams);
            
            //拿到我们传入的InvocationHandler
            final InvocationHandler ih = h;
            //如果类访问修饰符不是public  那么则将其设置可访问
            if (!Modifier.isPublic(cl.getModifiers())) {
                AccessController.doPrivileged(new PrivilegedAction<Void>() {
                    public Void run() {
                        cons.setAccessible(true);
                        return null;
                    }
                });
            }
            //通过构造方法传入我们自己定义的InvocationHandler 新建类的实例并返回
            return cons.newInstance(new Object[]{h});
        } catch (IllegalAccessException|InstantiationException e) {
            throw new InternalError(e.toString(), e);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new InternalError(t.toString(), t);
            }
        } catch (NoSuchMethodException e) {
            throw new InternalError(e.toString(), e);
        }
    }
```

获取到字节码生成的类信息后，则获取代理类的有参构造并传入我们自己一开始定义的InvocationHandler。通过构造函数创建代理类的实例并返回。所以字节码的父类文件中InvocationHandler则是我们创建的并传入的

```java
final class $Proxy0 extends Proxy implements IProx {

    ....
    public $Proxy0(InvocationHandler var1) throws  {
        super(var1);
    }
    ....
    public final String hello(String var1) throws  {
        try {
            return (String)super.h.invoke(this, m3, new Object[]{var1});
        } catch (RuntimeException | Error var3) {
            throw var3;
        } catch (Throwable var4) {
            throw new UndeclaredThrowableException(var4);
    }
}
}
```

```java
public class Proxy implements java.io.Serializable {
    ....
    /**
     * the invocation handler for this proxy instance.
     * @serial
     */
    protected InvocationHandler h;
    ....
    protected Proxy(InvocationHandler h) {
        Objects.requireNonNull(h);
        this.h = h;
    }
}
```

由上述代码可以得知 代理类在执行hello方法时  实际上是通过我们传入的InvocationHandler的invoke方法进行调用。到此  jdk动态代理的分析以及全部完成

三、结论
====

至此可以得知  jdk动态代理的大致逻辑即是: 传入代理类 类加载器，与接口数组和自定义的InvocationHandler，然后通过分析接口信息生成java文件的字节码数据，然后调用本地方法将类加载到内存中，最后返回构造参数为InvocationHandler的代理类，该类实现代理接口，并继承Proxy类（所以jdk动态代理只能代理接口，java单继承），我们调用方法实际上是调用代理类的方法，代理类则可以通过我们传入的InvocationHandler反射调用原本的方法来实现无侵入的修改原有方法逻辑
