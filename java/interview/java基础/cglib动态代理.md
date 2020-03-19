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

MethodInterceptor接口源码如下

```java
/**
 * General-purpose {@link Enhancer} callback which provides for "around advice".
 * @author Juozas Baliuka <a href="mailto:baliuka@mwm.lt">baliuka@mwm.lt</a>
 * @version $Id: MethodInterceptor.java,v 1.8 2004/06/24 21:15:20 herbyderby Exp $
 */
public interface MethodInterceptor
extends Callback
{
    /**
     * All generated proxied methods call this method instead of the original method.
     * The original method may either be invoked by normal reflection using the Method object,
     * or by using the MethodProxy (faster).
     * @param obj "this", the enhanced object
     * @param method intercepted Method
     * @param args argument array; primitive types are wrapped
     * @param proxy used to invoke super (non-intercepted method); may be called
     * as many times as needed
     * @throws Throwable any exception may be thrown; if so, super method will not be invoked
     * @return any value compatible with the signature of the proxied method. Method returning void will ignore this value.
     * @see MethodProxy
     */    
    public Object intercept(Object obj, java.lang.reflect.Method method, Object[] args,
                               MethodProxy proxy) throws Throwable;

}
```

intercept方法四个参数含义如下

* obj表示增强的对象，即实现这个接口类的一个对象

* method表示要被拦截的方法

* args表示要被拦截方法的参数

* proxy表示要触发父类的方法对象

二、原理解析
====

1 Enhancer.create()方法
------

通过上面代码，相信大家都能知道主要创建代理类的方法为Enhancer.create()方法，但是我们在执行这个方法之前设置了两个值，可以分别看下方法体

**```setCallbacks()```，即设置回调，我们创建出代理类后调用方法则是使用的这个回调接口，类似于jdk动态代理中的```InvocationHandler```**

```java
public void setCallbacks(Callback[] callbacks) {
	if (callbacks != null && callbacks.length == 0) {
		throw new IllegalArgumentException("Array cannot be empty");
	}
	this.callbacks = callbacks;
}
```

**setSuperClass 即设置代理类，这儿可以看到做了个判断，如果为interface则设置interfaces,如果是Object则设置为null(因为所有类都自动继承Object),如果为普通class则设置class，可以看到cglib代理不光可以代理接口，也可以代理普通类**

```java
/**
 * Set the class which the generated class will extend. As a convenience,
 * if the supplied superclass is actually an interface, <code>setInterfaces</code>
 * will be called with the appropriate argument instead.
 * A non-interface argument must not be declared as final, and must have an
 * accessible constructor.
 * @param superclass class to extend or interface to implement
 * @see #setInterfaces(Class[])
 */
public void setSuperclass(Class superclass) {
	if (superclass != null && superclass.isInterface()) {
		setInterfaces(new Class[]{ superclass });
	} else if (superclass != null && superclass.equals(Object.class)) {
		// affects choice of ClassLoader
		this.superclass = null;
	} else {
		this.superclass = superclass;
	}
}

/**
 * Set the interfaces to implement. The <code>Factory</code> interface will
 * always be implemented regardless of what is specified here.
 * @param interfaces array of interfaces to implement, or null
 * @see Factory
 */
public void setInterfaces(Class[] interfaces) {
	this.interfaces = interfaces;
}
```

上面总结看来主要设置两个我们需要用到的信息

```java
/**
 * Generate a new class if necessary and uses the specified
 * callbacks (if any) to create a new object instance.
 * Uses the no-arg constructor of the superclass.
 * @return a new instance
 */
public Object create() {
	classOnly = false;
	argumentTypes = null;
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

3.1 KEY_FACTORY
------

追踪源码可以看到，KEY_FACTORY在Enhancer的初始化即会创建一个final的静态变量

```java
private static final EnhancerKey KEY_FACTORY =
      (EnhancerKey)KeyFactory.create(EnhancerKey.class, KeyFactory.HASH_ASM_TYPE, null);
```

3.2 Keyfactory_create方法
------

这儿可以看到使用key工厂创建出对应class的代理类，后面的KeyFactory_HASH_ASM_TYPE即代理类中创建HashCode方法的策略。我们接着点击源码查看

```java
public static KeyFactory create(ClassLoader loader, Class keyInterface, KeyFactoryCustomizer customizer,
                                    List<KeyFactoryCustomizer> next) {
	//创建一个最简易的代理类生成器 即只会生成HashCode equals toString newInstance方法
	Generator gen = new Generator();
	//设置接口为enhancerKey类型
	gen.setInterface(keyInterface);

	if (customizer != null) {
		//添加定制器
		gen.addCustomizer(customizer);
	}
	if (next != null && !next.isEmpty()) {
		for (KeyFactoryCustomizer keyFactoryCustomizer : next) {
			//添加定制器
			gen.addCustomizer(keyFactoryCustomizer);
		}
	}
	//设置生成器的类加载器
	gen.setClassLoader(loader);
	//生成enhancerKey的代理类
	return gen.create();
}
```

3.3 Generator的create方法
------

这儿创建了一个简易的代理类生成器(KeyFactory的内部类Generator ，与Enhancer一样继承自抽象类AbstractClassGenerator)来生成我们需要的标识代理类，我们接着看gen.create()方法

```java
public KeyFactory create() {
　　//设置了该生成器生成代理类的名字前缀，即我们的接口名Enhancer.enhancerKey
	setNamePrefix(keyInterface.getName());
	return (KeyFactory)super.create(keyInterface.getName());
}
```

这儿可以看到调用的是父类AbstractClassGenerator的create方法，参数名为接口名

3.4 AbstractClassGenerator的create(Key)方法
------

```java
protected Object create(Object key) {
	try {
		//获取到当前生成器的类加载器
		ClassLoader loader = getClassLoader();
		//当前类加载器对应的缓存  缓存key为类加载器，缓存的value为ClassLoaderData  这个类后面会再讲
		Map<ClassLoader, ClassLoaderData> cache = CACHE;
		//先从缓存中获取下当前类加载器所有加载过的类
		ClassLoaderData data = cache.get(loader);
		//如果为空
		if (data == null) {
			synchronized (AbstractClassGenerator.class) {
				cache = CACHE;
				data = cache.get(loader);
				//经典的防止并发修改 二次判断
				if (data == null) {
					//新建一个缓存Cache  并将之前的缓存Cache的数据添加进来 并将已经被gc回收的数据给清除掉
					Map<ClassLoader, ClassLoaderData> newCache = new WeakHashMap<ClassLoader, ClassLoaderData>(cache);
					//新建一个当前加载器对应的ClassLoaderData 并加到缓存中  但ClassLoaderData中此时还没有数据
					data = new ClassLoaderData(loader);
					newCache.put(loader, data);
					//刷新全局缓存
					CACHE = newCache;
				}
			}
		}
		//设置一个全局key 
		this.key = key;
		
		//在刚创建的data(ClassLoaderData)中调用get方法 并将当前生成器，
		//以及是否使用缓存的标识穿进去 系统参数 System.getProperty("cglib.useCache", "true")  
		//返回的是生成好的代理类的class信息
		Object obj = data.get(this, getUseCache());
		//如果为class则实例化class并返回  就是我们需要的代理类
		if (obj instanceof Class) {
			return firstInstance((Class) obj);
		}
		//如果不是则说明是实体  则直接执行另一个方法返回实体
		return nextInstance(obj);
	} catch (RuntimeException e) {
		throw e;
	} catch (Error e) {
		throw e;
	} catch (Exception e) {
		throw new CodeGenerationException(e);
	}
}
```

这个方法可以看到主要为根据类加载器定义一个缓存，里面装载了缓存的类信息，然后调用这个ClassLoaderData的get方法获取到数据，如果为class信息 那么直接使用反射实例化，如果返回的是实体类，则解析实体类的信息，调用其newInstance方法重新生成一个实例(**cglib的代理类都会生成newInstance方法**)

具体根据class信息或者实体信息实例化数据都比较简单，相信代码大家都能看懂，这儿就不讲解了。核心点还是在于如何根据生成器来返回代理类或者代理类信息，我们继续查看data.get(this,getUseCache)

3.5 data.get(this,getUseCache)
------

```java
public Object get(AbstractClassGenerator gen, boolean useCache) {
	//如果不用缓存  (默认使用)
	if (!useCache) {
		//则直接调用生成器的命令
	  return gen.generate(ClassLoaderData.this);
	} else {
	  //从缓存中获取值
	  Object cachedValue = generatedClasses.get(gen);
	  //解包装并返回
	  return gen.unwrapCachedValue(cachedValue);
	}
}
```

这儿可以看到  如果可以用缓存 则调用缓存，不能调用缓存则直接生成， 这儿我们先看调用缓存的，在看之前需要再看一个东西，就是3.4之中，我们设置了一个key为ClassLoader，值为ClassLoaderData的缓存，这儿我们new了一个ClassLoaderData 并将类加载器传了进去 ，并且设置了这个Generator的key，我们看下new的逻辑

```java
public ClassLoaderData(ClassLoader classLoader) {
            //判断类加载器不能为空 
            if (classLoader == null) {
                throw new IllegalArgumentException("classLoader == null is not yet supported");
            }
            //设置类加载器   弱引用 即在下次垃圾回收时就会进行回收
            this.classLoader = new WeakReference<ClassLoader>(classLoader);
            //新建一个回调函数  这个回调函数的作用在于缓存中没获取到值时  调用传入的生成的生成代理类并返回
            Function<AbstractClassGenerator, Object> load =
                    new Function<AbstractClassGenerator, Object>() {
                        public Object apply(AbstractClassGenerator gen) {
                            Class klass = gen.generate(ClassLoaderData.this);
                            return gen.wrapCachedClass(klass);
                        }
                    };
            //为这个ClassLoadData新建一个缓存类   这个loadingcache稍后会讲        
            generatedClasses = new LoadingCache<AbstractClassGenerator, Object, Object>(GET_KEY, load);
        }
        
        
 private static final Function<AbstractClassGenerator, Object> GET_KEY = new Function<AbstractClassGenerator, Object>() {
     public Object apply(AbstractClassGenerator gen) {
         return gen.key;
     }
};
```

可以看到每个类加载器都对应着一个代理类缓存对象 ，这里面定义了类加载器，缓存调用没查询到的调用函数，以及新建了一个LoadingCache来缓存这个类加载器对应的缓存，这儿传入的两个参数，load代表缓存查询失败时的回调函数，而GET_KEY则是回调时获取调用生成器的key  即3.4中传入的key  也即是我们的代理类标识符。 然后我们接着看generatedClasses.get(gen);的方法


3.6 generatedClasses.get(gen)
------

这个方法主要传入代理类生成器  并根据代理类生成器获取值返回。这儿主要涉及到的类就是LoadingCache，这个类可以看做是某个CLassLoader对应的所有代理类缓存库，是真正缓存东西的地方。我们分析下这个类

```java
package net.sf.cglib.core.internal;

import java.util.concurrent.*;

public class LoadingCache<K, KK, V> {
    protected final ConcurrentMap<KK, Object> map;
    protected final Function<K, V> loader;
    protected final Function<K, KK> keyMapper;

    public static final Function IDENTITY = new Function() {
        public Object apply(Object key) {
            return key;
        }
    };
    //初始化类  kemapper代表获取某个代理类生成器的标识，loader即缓存查找失败后的回调函数
    public LoadingCache(Function<K, KK> keyMapper, Function<K, V> loader) {
        this.keyMapper = keyMapper;
        this.loader = loader;
        //这个map是缓存代理类的地方
        this.map = new ConcurrentHashMap<KK, Object>();
    }

    @SuppressWarnings("unchecked")
    public static <K> Function<K, K> identity() {
        return IDENTITY;
    }
    //这儿key是代理类生成器
    public V get(K key) {
        //获取到代理类生成器的标识
        final KK cacheKey = keyMapper.apply(key);
        //根据缓代理类生成器的标识获取代理类
        Object v = map.get(cacheKey);
        //如果结果不为空且不是FutureTask 即线程池中用于获取返回结果的接口
        if (v != null && !(v instanceof FutureTask)) {
            //直接返回
            return (V) v;
        }
        //否则就是没查询到  或者还未处理完
        return createEntry(key, cacheKey, v);
    }

    protected V createEntry(final K key, KK cacheKey, Object v) {
        //初始化任务task
        FutureTask<V> task;
        //初始化创建标识
        boolean creator = false;
        if (v != null) {
            // 则说明这是一个FutureTask 
            task = (FutureTask<V>) v;
        } else {
            //否则还没开始创建这个代理类  直接创建任务  
            task = new FutureTask<V>(new Callable<V>() {
                public V call() throws Exception {
                    //这儿会直接调用生成器的generate方法
                    return loader.apply(key);
                }
            });
            //将这个任务推入缓存Map  如果对应key已经有则返回已经有的task，
            Object prevTask = map.putIfAbsent(cacheKey, task);
            //如果为null则代表还没有创建  标识更新为true 且运行这个任务
            if (prevTask == null) {
                // creator does the load
                creator = true;
                task.run();
            } 
            //如果是task  说明另一个线程已经创建了task
            else if (prevTask instanceof FutureTask) {
                task = (FutureTask<V>) prevTask;
            } 
            //到这儿说明另一个线程已经执行完了  直接返回
            else {
                return (V) prevTask;
            }
            
            //上面的一堆判断主要是为了防止并发出现的问题
        }
        
        V result;
        try {
            //到这儿说明任务执行完并拿到对应的代理类了
            result = task.get();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Interrupted while loading cache item", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw ((RuntimeException) cause);
            }
            throw new IllegalStateException("Unable to load cache item", cause);
        }
        //如果这次执行是新建的
        if (creator) {
            //将之前的FutureTask缓存直接覆盖为实际的代理类信息
            map.put(cacheKey, result);
        }
        //返回结果
        return result;
    }
}
```

通过上面的分析可以得知，这个类主要作用是传入代理类生成器，根据这个代理类生成器以及代理类生成器的key来获取缓存，如果没有获取到则构建一个FutureTask来回调我们之前初始化时传入的 回调函数，并调用其中的apply方法，而具体调用的则是我们传入的代理类生成器的generate(LoadClassData)方法，将返回值覆盖之前的FutureTask成为真正的缓存。所以这个类的主要作用还是缓存。  这样则和3.5中不使用缓存时调用了一样的方法。所以我们接着来分析生成方法 generate(ClassLoadData),这儿因为我们使用的代理类生成器是Genrator，该类没有重写generate方法，所以回到了父类AbstractClassGenerator的generate方法。

3.7 AbstractClassGenerator.generate 方法
------

```java
protected Class generate(ClassLoaderData data) {
	Class gen;
	Object save = CURRENT.get();
	//当前的代理类生成器存入ThreadLocal中
	CURRENT.set(this);
	try {
		//获取到ClassLoader
		ClassLoader classLoader = data.getClassLoader();
		//判断不能为空
		if (classLoader == null) {
			throw new IllegalStateException("ClassLoader is null while trying to define class " +
					getClassName() + ". It seems that the loader has been expired from a weak reference somehow. " +
					"Please file an issue at cglib's issue tracker.");
		}
		synchronized (classLoader) {
		 //生成代理类名字
		  String name = generateClassName(data.getUniqueNamePredicate()); 
		 //缓存中存入这个名字
		  data.reserveName(name);
		  //当前代理类生成器设置类名
		  this.setClassName(name);
		}
		//尝试从缓存中获取类
		if (attemptLoad) {
			try {
				//要是能获取到就直接返回了  即可能出现并发 其他线程已经加载
				gen = classLoader.loadClass(getClassName());
				return gen;
			} catch (ClassNotFoundException e) {
				// 发现异常说明没加载到 不管了
			}
		}
		//生成字节码
		byte[] b = strategy.generate(this);
		//获取到字节码代表的class的名字
		String className = ClassNameReader.getClassName(new ClassReader(b));
		//核实是否为protect
		ProtectionDomain protectionDomain = getProtectionDomain();
		synchronized (classLoader) { // just in case
			//如果不是protect
			if (protectionDomain == null) {
				//根据字节码 类加载器 以及类名字  将class加载到内存中
				gen = ReflectUtils.defineClass(className, b, classLoader);
			} else {
				//根据字节码 类加载器 以及类名字 以及找到的Protect级别的实体 将class加载到内存中
				gen = ReflectUtils.defineClass(className, b, classLoader, protectionDomain);
			}
		}
　　　　　　　 //返回生成的class信息
		return gen;
	} catch (RuntimeException e) {
		throw e;
	} catch (Error e) {
		throw e;
	} catch (Exception e) {
		throw new CodeGenerationException(e);
	} finally {
		CURRENT.set(save);
	}
}
```

这个方法主要设置了下当前类生成器的类名，然后调用stratege的generate方法返回字节码，根据字节码  类名 类加载器将字节码所代表的类加载到内存中，这个功能看一下大概就懂，我们接下来主要分析字节码生成方法

3.8 DefaultGeneratorStrategy.generate(ClassGenerator cg)
------

```java
public byte[] generate(ClassGenerator cg) throws Exception {
	//创建一个写入器
	DebuggingClassWriter cw = getClassVisitor();
	//加入自己的转换逻辑后执行代理类生成器的generateClass方法
	transform(cg).generateClass(cw);
	//将cw写入的东西转换为byte数组返回
	return transform(cw.toByteArray());
}
```

这里面主要是新建一个写入器，然后执行我们代理类生成器的generateClass方法将class信息写入这个ClassWriter  最后将里面的东西转换为byte数组返回，所以又回到了我们的代理类生成器的generateClass方法，这儿进入的是Generator的generateClass方法

3.9 Generator.generateClass（ClassVisitor v）
------

这个类是核心的class信息写入类

```java
//该方法为字节码写入方法 为最后一步
 public void generateClass(ClassVisitor v) {
	//创建类写入聚合对象
	ClassEmitter ce = new ClassEmitter(v);
	//找到被代理类的newInstance方法 如果没有会报异常  由此可知 如果想用Generator代理类生成器  必须要有newInstance方法
	Method newInstance = ReflectUtils.findNewInstance(keyInterface);
	//如果被代理类的newInstance不为Object则报异常  此处我们代理的Enchaer.EnhancerKey newInstance方法返回值为Object
	if (!newInstance.getReturnType().equals(Object.class)) {
		throw new IllegalArgumentException("newInstance method must return Object");
	}
	//找到newInstance方法的所有参数类型 并当做成员变量 
	Type[] parameterTypes = TypeUtils.getTypes(newInstance.getParameterTypes());
	
	//1.创建类开始写入类头   版本号  访问权限  类名等通用信息
	ce.begin_class(Constants.V1_8,
				   Constants.ACC_PUBLIC,
				   getClassName(), 
				   KEY_FACTORY,
				   new Type[]{ Type.getType(keyInterface) },
				   Constants.SOURCE_FILE);
	//2.写入无参构造方法               
	EmitUtils.null_constructor(ce);
	//3.写入newInstance方法
	EmitUtils.factory_method(ce, ReflectUtils.getSignature(newInstance));
	
	int seed = 0;
	//4.开始构造 有参构造方法
	CodeEmitter e = ce.begin_method(Constants.ACC_PUBLIC,
									TypeUtils.parseConstructor(parameterTypes),
									null);
	e.load_this();
	//4.1有参构造中调用父类构造方法  即super.构造方法() 
	e.super_invoke_constructor();
	e.load_this();
	//4.2找到传入的定制器 例如一开始传入的hashCode方法定制器
	List<FieldTypeCustomizer> fieldTypeCustomizers = getCustomizers(FieldTypeCustomizer.class);
	//4.3遍历成员变量即newInstance方法的所有参数
	for (int i = 0; i < parameterTypes.length; i++) {
		Type parameterType = parameterTypes[i];
		Type fieldType = parameterType;
		for (FieldTypeCustomizer customizer : fieldTypeCustomizers) {
			fieldType = customizer.getOutType(i, fieldType);
		}
		seed += fieldType.hashCode();
		//4.3将这些参数全部声明到写入类中
		ce.declare_field(Constants.ACC_PRIVATE | Constants.ACC_FINAL,
						 getFieldName(i),
						 fieldType,
						 null);
		e.dup();
		e.load_arg(i);
		for (FieldTypeCustomizer customizer : fieldTypeCustomizers) {
			customizer.customize(e, i, parameterType);
		}
		//4.4设置每个成员变量的值  即我们常见的有参构造中的this.xx = xx
		e.putfield(getFieldName(i));
	}
	//设置返回值
	e.return_value();
	//有参构造及成员变量写入完成
	e.end_method();
	
	/*************************到此已经在class中写入了成员变量  写入实现了newInstance方法  写入无参构造  写入了有参构造 *************************/
	
	// 5.写入hashcode方法
	e = ce.begin_method(Constants.ACC_PUBLIC, HASH_CODE, null);
	int hc = (constant != 0) ? constant : PRIMES[(int)(Math.abs(seed) % PRIMES.length)];
	int hm = (multiplier != 0) ? multiplier : PRIMES[(int)(Math.abs(seed * 13) % PRIMES.length)];
	e.push(hc);
	for (int i = 0; i < parameterTypes.length; i++) {
		e.load_this();
		e.getfield(getFieldName(i));
		EmitUtils.hash_code(e, parameterTypes[i], hm, customizers);
	}
	e.return_value();
	//hashcode方法结束
	e.end_method();

	// 6.写入equals方法
	e = ce.begin_method(Constants.ACC_PUBLIC, EQUALS, null);
	Label fail = e.make_label();
	e.load_arg(0);
	e.instance_of_this();
	e.if_jump(e.EQ, fail);
	for (int i = 0; i < parameterTypes.length; i++) {
		e.load_this();
		e.getfield(getFieldName(i));
		e.load_arg(0);
		e.checkcast_this();
		e.getfield(getFieldName(i));
		EmitUtils.not_equals(e, parameterTypes[i], fail, customizers);
	}
	e.push(1);
	e.return_value();
	e.mark(fail);
	e.push(0);
	e.return_value();
	//equals方法结束
	e.end_method();

	// 7.写入toString方法
	e = ce.begin_method(Constants.ACC_PUBLIC, TO_STRING, null);
	e.new_instance(Constants.TYPE_STRING_BUFFER);
	e.dup();
	e.invoke_constructor(Constants.TYPE_STRING_BUFFER);
	for (int i = 0; i < parameterTypes.length; i++) {
		if (i > 0) {
			e.push(", ");
			e.invoke_virtual(Constants.TYPE_STRING_BUFFER, APPEND_STRING);
		}
		e.load_this();
		e.getfield(getFieldName(i));
		EmitUtils.append_string(e, parameterTypes[i], EmitUtils.DEFAULT_DELIMITERS, customizers);
	}
	e.invoke_virtual(Constants.TYPE_STRING_BUFFER, TO_STRING);
	e.return_value();
	//toString方法结束
	e.end_method();
	//类写入结束  至此类信息收集完成 并全部写入ClassVisitor
	ce.end_class();
}
```

这个方法主要将一个完整的类信息写入ClassVisitor中，例如目前实现的Enhancer.EnhancerKey代理，即实现了newInstance方法，  重写了HashCode,toSting,equals方法，并将newInstance的所有参数作为了成员变量，这儿我们也可以看下具体实现newInstance方法的逻辑  即这个代码  EmitUtils.factory_method(ce, ReflectUtils.getSignature(newInstance)); 具体内容如下   其他的写入内容大致命令也是这样，如果有兴趣可以去研究asm字节码写入的操作

```java
public static void factory_method(ClassEmitter ce, Signature sig) {
	//开始写入方法
	CodeEmitter e = ce.begin_method(Constants.ACC_PUBLIC, sig, null);
	//写入 一个创建对象命令  即new命令
	e.new_instance_this();
	e.dup();
	//加载参数命令
	e.load_args();
	//执行该类的有参构造命令
	e.invoke_constructor_this(TypeUtils.parseConstructor(sig.getArgumentTypes()));
	//将上面指令执行的值返回
	e.return_value();
	//结束写入方法
	e.end_method();
}
```

所以通过方法构造指令可以得知 我们通过Generator创建的代理类大致内容应该如下，Enhancer.EhancerKey代理类字节码的class内容应该是把参数换为newInstance中的参数

```java
public class EnhancerKeyProxy extends xxx implements xxx{

 private paramA;
 private paramB;
 private paramC;
 
 public EnhancerKeyProxy() {
         super.xxx();
 }
 public EnhancerKeyProxy(paramA, paramB,paramC) {
       super.xxx();
       this.paramA = paramA
       this.paramB = paramB
       this.paramC = paramC
    }

 public Object newInstance(paramA,paramB,paramC){
        EnhancerKeyProxy param = new EnhancerKeyProxy(o);
        return param;
 }
 
  public int hashCode(){
      ...
  }
   public String toString(){
      ...
  }
  
  public boolean equals(Object o){
      ...
  }

}
```

最后执行传入的ClassVisitor  即我们传入的实例DebuggingClassWriter的toByteArray即可以将写入的内容转换为byte[]返回

至此 我们的成功的生成了Enhancer.EnhancerKey的代理类，也就是我们需要的代理类标识类  用来标识被代理的类，这个代理类主要用来作为被代理类的标识，在进行缓存时作为判断相等的依据。可以看到 cglib代理主要也是利用我们传入的被代理类信息来生成对应的代理类字节码，然后用类加载器加载到内存中。虽然我们的实际的代理任务才刚刚开始，但是要了解的东西已经基本上差不多了，对具体的我们案例中的ProxyFactory代理时，只是生成器Enhancer对比生成器Generator在生成过程中重写了一些操作而已

现在我们重新回看步骤2中的代码

```java
private Object createHelper() {
	preValidate();
	//获取到了代理类标识类
	Object key = KEY_FACTORY.newInstance((superclass != null) ? superclass.getName() : null,
			ReflectUtils.getNames(interfaces),
			filter == ALL_ZERO ? null : new WeakCacheKey<CallbackFilter>(filter),
			callbackTypes,
			useFactory,
			interceptDuringConstruction,
			serialVersionUID);
	//设置当前enhancer正在代理生成的类信息
	this.currentKey = key;
	//调用父类的create(key方法)
	Object result = super.create(key);
	return result;
}
```

可以看到获取到代理类标志类后 将其设置为当前代理类生成器的正在代理的类 并同样调用父类AbstractClassGenerator中create(key)的方法

下面开始分析Ehancer生成器的逻辑，由于部分逻辑和Generator生成器一致  所以一样的我会略过

4 AbstractClassGenerator.create方法
------

这个逻辑和步骤3.4一致，查询当前key即代理类标志类对应的ClassLoadData缓存，如果没有则建一个空的缓存并初始化一个对应的ClassLoadData,传入相应的生成器，生成失败回调函数等

按照同样的逻辑一直走到3.5中的generate(ClassLoadData)方法时，由于Enhancer生成器重写了这个方法 所以我们分析Enahncer的生成逻辑

5 Enhancer.generate(ClassLoadData data)
------

```java
@Override
protected Class generate(ClassLoaderData data) {
	validate();
	if (superclass != null) {
		setNamePrefix(superclass.getName());
	} else if (interfaces != null) {
		setNamePrefix(interfaces[ReflectUtils.findPackageProtected(interfaces)].getName());
	}
	return super.generate(data);
}
```

可以发现ehancer生成器只是做了个检查命名操作  在上面的Generator中也是做了个命名操作，然后继续执行父类的generate(data)方法，这个和步骤3.7一致，我们主要看其中生成字节码的方法，即最后调用的Enhancer.generatorClass(ClassVisitor c)方法，

方法代码如下

6 Enhancer.generatorClass(ClassVisitor c)
------

```java
public void generateClass(ClassVisitor v) throws Exception {
        //声明需代理的类 或者接口
        Class sc = (superclass == null) ? Object.class : superclass;
        //检查 final类无法被继承
        if (TypeUtils.isFinal(sc.getModifiers()))
            throw new IllegalArgumentException("Cannot subclass final class " + sc.getName());
        //找到该类所有声明了的构造函数
        List constructors = new ArrayList(Arrays.asList(sc.getDeclaredConstructors()));
        //去掉private之类的不能被继承的构造函数
        filterConstructors(sc, constructors);

        // Order is very important: must add superclass, then
        // its superclass chain, then each interface and
        // its superinterfaces.
        //这儿顺序非常重要  上面是源码的注释  直接留着  相信大家都能看懂 
        
        //声明代理类方法集合
        List actualMethods = new ArrayList();
        //声明代理接口接口方法集合
        List interfaceMethods = new ArrayList();
        //声明所有必须为public的方法集合  这儿主要是代理接口接口的方法
        final Set forcePublic = new HashSet();
        //即通过传入的代理类 代理接口，遍历所有的方法并放入对应的集合
        getMethods(sc, interfaces, actualMethods, interfaceMethods, forcePublic);
        
        //对所有代理类方法修饰符做处理 
        List methods = CollectionUtils.transform(actualMethods, new Transformer() {
            public Object transform(Object value) {
                Method method = (Method)value;
                int modifiers = Constants.ACC_FINAL
                    | (method.getModifiers()
                       & ~Constants.ACC_ABSTRACT
                       & ~Constants.ACC_NATIVE
                       & ~Constants.ACC_SYNCHRONIZED);
                if (forcePublic.contains(MethodWrapper.create(method))) {
                    modifiers = (modifiers & ~Constants.ACC_PROTECTED) | Constants.ACC_PUBLIC;
                }
                return ReflectUtils.getMethodInfo(method, modifiers);
            }
        });
        //创建类写入器
        ClassEmitter e = new ClassEmitter(v);
        
        //1.开始创建类  并写入基本信息  如java版本，类修饰符 类名等
        if (currentData == null) {
        e.begin_class(Constants.V1_8,
                      Constants.ACC_PUBLIC,
                      getClassName(),
                      Type.getType(sc),
                      (useFactory ?
                       TypeUtils.add(TypeUtils.getTypes(interfaces), FACTORY) :
                       TypeUtils.getTypes(interfaces)),
                      Constants.SOURCE_FILE);
        } else {
            e.begin_class(Constants.V1_8,
                    Constants.ACC_PUBLIC,
                    getClassName(),
                    null,
                    new Type[]{FACTORY},
                    Constants.SOURCE_FILE);
        }
        List constructorInfo = CollectionUtils.transform(constructors, MethodInfoTransformer.getInstance());
        //2. 声明一个private boolean 类型的属性：CGLIB$BOUND
        e.declare_field(Constants.ACC_PRIVATE, BOUND_FIELD, Type.BOOLEAN_TYPE, null);
        //3. 声明一个public static Object 类型的属性：CGLIB$FACTORY_DATA
        e.declare_field(Constants.ACC_PUBLIC | Constants.ACC_STATIC, FACTORY_DATA_FIELD, OBJECT_TYPE, null);
        // 这个默认为true  如果为false则会声明一个private boolean 类型的属性：CGLIB$CONSTRUCTED
        if (!interceptDuringConstruction) {
            e.declare_field(Constants.ACC_PRIVATE, CONSTRUCTED_FIELD, Type.BOOLEAN_TYPE, null);
        }
        //4. 声明一个public static final 的ThreadLocal：ThreadLocal
        e.declare_field(Constants.PRIVATE_FINAL_STATIC, THREAD_CALLBACKS_FIELD, THREAD_LOCAL, null);
        //5. 声明一个public static final 的CallBack类型的数组：CGLIB$STATIC_CALLBACKS
        e.declare_field(Constants.PRIVATE_FINAL_STATIC, STATIC_CALLBACKS_FIELD, CALLBACK_ARRAY, null);
        //如果serialVersionUID不为null  则设置一个public static final 的Long类型 serialVersionUID
        if (serialVersionUID != null) {
            e.declare_field(Constants.PRIVATE_FINAL_STATIC, Constants.SUID_FIELD_NAME, Type.LONG_TYPE, serialVersionUID);
        }
        
        //遍历CallBackTypes 即我们构建Enhancer是setCallBack的所有类的类型  本案例中是methodInterceptor 并且只传入了一个
        for (int i = 0; i < callbackTypes.length; i++) {
            //6.声明一个private 的传入的CallBack类型的属性：CGLIB$CALLBACK_0 (从0开始编号，)
            e.declare_field(Constants.ACC_PRIVATE, getCallbackField(i), callbackTypes[i], null);
        }
        //7声明一个private static 的传入的Object类型的属性：CGLIB$CALLBACK_FILTER
        e.declare_field(Constants.ACC_PRIVATE | Constants.ACC_STATIC, CALLBACK_FILTER_FIELD, OBJECT_TYPE, null);
        
        //判断currentData
        if (currentData == null) {
            //8.为null则开始声明所有的代理类方法的变量 以及其具体的重写实现方法，还有static初始化执行代码块
            emitMethods(e, methods, actualMethods);
            //9.声明构造函数
            emitConstructors(e, constructorInfo);
        } else {
            //声明默认构造函数
            emitDefaultConstructor(e);
        }
        //
        emitSetThreadCallbacks(e);
        emitSetStaticCallbacks(e);
        emitBindCallbacks(e);
        //如果currentData不为null
        if (useFactory || currentData != null) {
            //获取到所有CallBack索引数组
            int[] keys = getCallbackKeys();
            //10.声明三个newInstance方法
            //只有一个callback参数
            emitNewInstanceCallbacks(e);
            //参数为callback数组
            emitNewInstanceCallback(e);
            //参数为callback数组 以及附带的一些参数
            emitNewInstanceMultiarg(e, constructorInfo);
            //11.声明getCallBack方法
            emitGetCallback(e, keys);
            //12.声明setCallBack方法
            emitSetCallback(e, keys);
            //12.声明setCallBacks方法
            emitGetCallbacks(e);
            //12.声明setCallBacks方法
            emitSetCallbacks(e);
        }
        //类声明结束
        e.end_class();
```

可以看到这儿也是声明一个写入类 然后按照Ehancer的代理生成策略写入符合的class信息然后返回，最红依旧会执行toByteArray方法返回byte[]数组，这样则又回到了步骤3.5中 根据类加载器 字节码数组来动态将代理类加载进内存中的方法了。最后我们回到3.4中根据class获取实例的代码即可返回被代理实例。 而我们执行方法时执行的是代理类中对应的方法，然后调用我们传入的callback执行 原理和jdk动态代理类似，至此  cglib动态代理源码分析到此结束

总结
====

对比jdk动态代理和cglib动态代理我们可以得知

> jdk动态代理只能代理接口，而cglib动态代理可以代理类或者接口，说明cglib的优势更加明显。但是jdk动态代理是java原生支持，所以不需要引入额外的包，cglib需要引入额外的包。二者的原理类似，都是在内存中根据jvm的字节码文件规范动态创建class并使用传入的类加载器加载到内存中，再反射调用生成代理实例，并且都根据类加载器做了相应的缓存。实际使用中应根据利弊按需使用


