一、一句话描述
------

```java.lang.reflect.Proxy类中缓存变量```

```java
/**
 * a cache of proxy classes
 */
private static final WeakCache<ClassLoader, Class<?>[], Class<?>>
	proxyClassCache = new WeakCache<>(new KeyFactory(), new ProxyClassFactory());
```

```java.lang.reflect.WeakCache类中缓存数据结构```

```java
// the key type is Object for supporting null key
private final ConcurrentMap<Object, ConcurrentMap<Object, Supplier<V>>> map
	= new ConcurrentHashMap<>();
```

二级缓存key，由classLoader和interfaces[]标识代理类。

```java
// lazily install the 2nd level valuesMap for the particular cacheKey
ConcurrentMap<Object, Supplier<V>> valuesMap = map.get(cacheKey);
if (valuesMap == null) {
	ConcurrentMap<Object, Supplier<V>> oldValuesMap
		= map.putIfAbsent(cacheKey,
						  valuesMap = new ConcurrentHashMap<>());
	if (oldValuesMap != null) {
		valuesMap = oldValuesMap;
	}
}

// create subKey and retrieve the possible Supplier<V> stored by that
// subKey from valuesMap
Object subKey = Objects.requireNonNull(subKeyFactory.apply(key, parameter));
Supplier<V> supplier = valuesMap.get(subKey);
```

二、Proxy内部类KeyFactory源码

```java
/**
 * A function that maps an array of interfaces to an optimal key where
 * Class objects representing interfaces are weakly referenced.
 */
private static final class KeyFactory
	implements BiFunction<ClassLoader, Class<?>[], Object>
{
	@Override
	public Object apply(ClassLoader classLoader, Class<?>[] interfaces) {
		switch (interfaces.length) {
			case 1: return new Key1(interfaces[0]); // the most frequent
			case 2: return new Key2(interfaces[0], interfaces[1]);
			case 0: return key0;
			default: return new KeyX(interfaces);
		}
	}
}
```

```java
/*
 * a key used for proxy class with 1 implemented interface
 */
private static final class Key1 extends WeakReference<Class<?>> {
	private final int hash;

	Key1(Class<?> intf) {
		super(intf);
		this.hash = intf.hashCode();
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		Class<?> intf;
		return this == obj ||
			   obj != null &&
			   obj.getClass() == Key1.class &&
			   (intf = get()) != null &&
			   intf == ((Key1) obj).get();
	}
}
```

```java
/*
 * a key used for proxy class with 2 implemented interfaces
 */
private static final class Key2 extends WeakReference<Class<?>> {
	private final int hash;
	private final WeakReference<Class<?>> ref2;

	Key2(Class<?> intf1, Class<?> intf2) {
		super(intf1);
		hash = 31 * intf1.hashCode() + intf2.hashCode();
		ref2 = new WeakReference<Class<?>>(intf2);
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		Class<?> intf1, intf2;
		return this == obj ||
			   obj != null &&
			   obj.getClass() == Key2.class &&
			   (intf1 = get()) != null &&
			   intf1 == ((Key2) obj).get() &&
			   (intf2 = ref2.get()) != null &&
			   intf2 == ((Key2) obj).ref2.get();
	}
}
```

```java
/*
 * a key used for proxy class with any number of implemented interfaces
 * (used here for 3 or more only)
 */
private static final class KeyX {
	private final int hash;
	private final WeakReference<Class<?>>[] refs;

	@SuppressWarnings("unchecked")
	KeyX(Class<?>[] interfaces) {
		hash = Arrays.hashCode(interfaces);
		refs = (WeakReference<Class<?>>[])new WeakReference<?>[interfaces.length];
		for (int i = 0; i < interfaces.length; i++) {
			refs[i] = new WeakReference<>(interfaces[i]);
		}
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(Object obj) {
		return this == obj ||
			   obj != null &&
			   obj.getClass() == KeyX.class &&
			   equals(refs, ((KeyX) obj).refs);
	}

	private static boolean equals(WeakReference<Class<?>>[] refs1,
								  WeakReference<Class<?>>[] refs2) {
		if (refs1.length != refs2.length) {
			return false;
		}
		for (int i = 0; i < refs1.length; i++) {
			Class<?> intf = refs1[i].get();
			if (intf == null || intf != refs2[i].get()) {
				return false;
			}
		}
		return true;
	}
}
```

```java
/*
 * a key used for proxy class with 0 implemented interfaces
 */
private static final Object key0 = new Object();
```
