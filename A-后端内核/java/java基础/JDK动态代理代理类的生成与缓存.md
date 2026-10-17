看在前面
====

> * <a href="https://www.cnblogs.com/James-Gong/p/8126129.html">JDK动态代理代理类的生成与缓存</a>

> * <a href="https://www.jianshu.com/p/9f5566b5e7fb">动态代理的缓存机制</a>

入口：Proxy.newProxyInstance
====

调用 `Proxy.newProxyInstance(loader, interfaces, h)` 生成代理实例的流程：

1. 校验 InvocationHandler 非空、克隆接口数组、做安全检查；
2. 关键一步：`Class<?> cl = getProxyClass0(loader, intfs);`——从缓存获取或生成代理类；
3. 通过构造器 `cl.getConstructor(constructorParams)` 创建代理实例（必要时用 doPrivileged 提权）。

getProxyClass0 中接口数量超过 65535 会直接抛异常，随后委托给静态缓存：

```java
private static final WeakCache<ClassLoader, Class<?>[], Class<?>>
        proxyClassCache = new WeakCache<>(new KeyFactory(), new ProxyClassFactory());
```

代理类的生成与缓存全部在 `java.lang.reflect.WeakCache<K, P, V>` 中完成：KeyFactory 生成二级 key，ProxyClassFactory 真正生成 Class（细节见库内 [JDK动态代理KeyFactory](./JDK动态代理KeyFactory.md)、[JDK动态代理ProxyClassFactory](./JDK动态代理ProxyClassFactory.md)、[JDK动态代理WeakCache](./JDK动态代理WeakCache.md)）。

WeakCache 的两级缓存结构
====

```java
private final ReferenceQueue<K> refQueue = new ReferenceQueue<>();
private final ConcurrentMap<Object, ConcurrentMap<Object, Supplier<V>>> map =
        new ConcurrentHashMap<>();
private final ConcurrentMap<Supplier<V>, Boolean> reverseMap = new ConcurrentHashMap<>();
private final BiFunction<K, P, ?> subKeyFactory;
private final BiFunction<K, P, V> valueFactory;
```

* **一级缓存**：key 由类加载器 classLoader 决定（`CacheKey.valueOf`，弱引用），value 是二级缓存 map（valuesMap）——同一个类名被不同 ClassLoader 加载视为不同的类，所以先按 loader 分一层；
* **二级缓存**：key 由 classLoader + interfaces[] 标识（KeyFactory 生成），value 为 `Supplier<V>`：首次存的是 `Factory`，之后取出的都是 `CacheValue`；
* refQueue 配合 GC 清除过期缓存；reverseMap 记录缓存中的 CacheKey，配合过期清理机制。

KeyFactory：二级 key 按接口数量分型
------

```java
switch (interfaces.length) {
    case 1: return new Key1(interfaces[0]); // the most frequent
    case 2: return new Key2(interfaces[0], interfaces[1]);
    case 0: return key0;
    default: return new KeyX(interfaces);
}
```

WeakCache.get 主流程
====

1. `expungeStaleEntries()`：先轮询引用队列，清掉已被 GC 回收的缓存项；
2. 用 `CacheKey.valueOf(key, refQueue)` 得到一级 key，懒加载获取/新建 valuesMap（`putIfAbsent`）；
3. 用 KeyFactory 生成 subKey，`valuesMap.get(subKey)` 取出 supplier；
4. 进入 `while(true)` 循环：supplier 存在且 get() 返回非 null 则直接返回；否则新建 Factory，通过 `putIfAbsent`/`replace` 安装后重试。

Factory.get：真正生成代理类（synchronized）
====

* 复查 `valuesMap.get(subKey)` 是否仍是 this（并发校验），不是则返回 null；
* 调用 `valueFactory.apply(key, parameter)`（即 ProxyClassFactory）生成代理类 Class；失败则从缓存移除自身；
* 包装为 `CacheValue<V>`（弱引用），`valuesMap.replace(subKey, this, cacheValue)` 把 Factory 替换为 CacheValue，并放入 reverseMap，返回 value。

此后再请求同一 (loader, interfaces) 组合时，cacheValue.get() 实际就是父类 `Reference.get()`，直接返回缓存的代理类 Class——**代理类只生成一次，后续全部复用**。

三种场景的执行逻辑
====

* **缓存不存在**：第一轮循环安装 Factory；第二轮调用 factory.get() 完成生成与替换；
* **多线程竞争**：后进入的线程调用 factory.get() 时 supplier 已变为 cacheValue，`supplier != this` 返回 null；随后 replace 失败，重新 get 取到 cacheValue，下一轮循环返回正确值——保证多线程下同一 key 拿到同一引用；
* **缓存已存在**：只执行一次循环，直接命中 CacheValue。

过期机制
====

CacheKey、CacheValue 都是弱引用：GC 满足条件时被 enqueue 到 refQueue，每次 get 前先 `expungeStaleEntries()` 轮询队列并调用 `cacheKey.expungeFrom(map, reverseMap)` 清除对应缓存。这样类加载器或代理类被回收后，缓存条目不会内存泄漏。

涉及的知识点
====

1. ConcurrentMap 的 get、putIfAbsent、replace 的正确用法（putIfAbsent 返回非 null 时必须改用已有值）；
2. 双重检查解决延迟初始化的竞态条件：首次生成走 synchronized 的 Factory，后续读取无需同步，减轻并发压力；
3. Factory → CacheValue 两步替换并不是多余：Factory 借 synchronized 充当"正在生成"的占位，避免多线程重复生成代理类，本质是占位/代理思想的运用。

> 内容来源：[原文](https://www.cnblogs.com/James-Gong/p/8126129.html) · 2026-10 整理回填。
