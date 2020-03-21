看在前面
====

> * <a href="https://blog.csdn.net/hackersuye/article/details/83545871">Java源码分析——java.lang.reflect反射包解析(三)　动态代理、Proxy类、WeakCache类</a>

> * <a href="https://blog.csdn.net/sinat_36945592/article/details/88071228#_1">Java源码阅读------WeakCache</a>

在讨论Proxy类之前，不得不先了解这个类，这个类是用来干嘛的呢？从其英文意义上来看，它是弱缓存类，也就是说它是一个起着缓存作用的类，它用来存贮软引用类型的实例，从该类在Proxy类中的实例化可以看出：

```java
private static final WeakCache<ClassLoader, Class<?>[], Class<?>>
        proxyClassCache = new WeakCache<>(new KeyFactory(), new ProxyClassFactory());
```

WeakCache是缓存类加载器以及该加载器加载的委托类的。其中KeyFactory类用来生产Key的，生成因不同数量的类加载器采取不同的生成策略来对应生成的Key对象。

<a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/JDK%E5%8A%A8%E6%80%81%E4%BB%A3%E7%90%86KeyFactory.md">KeyFactory</a>和<a href="https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/JDK%E5%8A%A8%E6%80%81%E4%BB%A3%E7%90%86ProxyClassFactory.md">ProxyClassFactory</a>了解一下。

WeakCache类作用
------

WeakCache类的实质作用是缓存实现了代理目标接口的类的信息以及对这些类的一些操作，以及这些类的类加载器的。其中的重要的方法是get方法，该方法是来获取WeakCache缓存中与委托类的Class类对象的。WeakCache重要的方法是，get方法，该方法实现了缓存的机制，其源码以及注释如下：

```java
public V get(K key, P parameter) {
	Objects.requireNonNull(parameter);
	//删除过期的缓存
	expungeStaleEntries();
	//创建CatchKey对象，Key不为空
	Object cacheKey = CacheKey.valueOf(key, refQueue);
	// 懒惰地为特定的CaseKeKE安装第二级值映射
	//根据CatchMap获取ConcurrentMap对象
	ConcurrentMap<Object, Supplier<V>> valuesMap = map.get(cacheKey);
	//如果没有则创建一个ConcurrentMap对象
	if (valuesMap == null) {
		ConcurrentMap<Object, Supplier<V>> oldValuesMap
			= map.putIfAbsent(cacheKey,
							  valuesMap = new ConcurrentHashMap<>());
		if (oldValuesMap != null) {
			valuesMap = oldValuesMap;
		}
	}
   //利用KeyFactory获取Key对象
	Object subKey = Objects.requireNonNull(subKeyFactory.apply(key, parameter));
	//利用Key对象取出Supplier，Supplier是一个接口
	Supplier<V> supplier = valuesMap.get(subKey);
	Factory factory = null;
	while (true) {
	//不为空，则直接取出
		if (supplier != null) {
			// 从supplier接口的实现类获取Class类对象
			V value = supplier.get();
			if (value != null) {
				return value;
			}
		}
	  //如果supplier为空，进行缓存，Factory是supplier接口的实现类
		if (factory == null) {
			factory = new Factory(key, parameter, subKey, valuesMap);
		}
		if (supplier == null) {
			supplier = valuesMap.putIfAbsent(subKey, factory);
			if (supplier == null) {
				//赋值factory
				supplier = factory;
			}
		} else {
		//假如不为空，且factory中value为空，则替换factory
			if (valuesMap.replace(subKey, supplier, factory)) {
				// successfully replaced
				// cleared CacheEntry / unsuccessful Factory
				// with our Factory
				supplier = factory;
			} else {
				// retry with current supplier
				//继续尝试获取接口实现类的supplier类对象
				supplier = valuesMap.get(subKey);
			}
		}
	}
}
```
get方法利用ConcurrentMap类来进行缓存，里面存贮Key对象以及Factory对象，Factory类用来存贮缓存的信息的，其实现了Supplier接口，其get方法里面调用了ProxyClassFactory类的apply方法来返回Class类对象，而且get方法是一个同步的方法，也表明WeakCache缓存是线程安全的。


