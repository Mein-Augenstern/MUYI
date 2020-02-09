看在前面
====

> 作者：_小咖喱黄不辣
链接：https://www.jianshu.com/p/afb440a48aba
来源：简书
著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。

缓存，不是存储，无法保证以前设置的缓存绝对存在。因为缓存容量是有上限的，即使set值的时候不设置过期时间，在内存不够的时候，会根据内存淘汰策略删除一些缓存。设置过期时间的key是如何删除的？过期后会立即释放内存吗？

一、定期删除
====

redis会把设置了过期时间的key放在单独的字典中，定时遍历来删除到期的key。

 1. 每100ms从过期字典中 随机挑选20个，把其中过期的key删除；
 2. 如果过期的key占比超过1/4，重复步骤1
 
为了保证不会循环过度，导致卡顿，扫描时间上限默认不超过25ms。根据以上原理，系统中应避免大量的key同时过期，给要过期的key设置一个随机范围。

二、惰性删除
====

过期的key并不一定会马上删除，还会占用着内存。当你真正查询这个key时，redis会检查一下，这个设置了过期时间的key是否过期了? 如果过期了就会删除，返回空。这就是惰性删除。

但是实际上这还是有问题的，如果定期删除漏掉了很多过期key，然后你也没及时去查，也就没走惰性删除，此时会怎么样？如果大量过期 key 堆积在内存里，导致 redis 内存块耗尽了，咋整？

答案是：走内存淘汰机制。


三、内存淘汰机制
====

当redis内存超出物理内存限制时，会和磁盘产生swap，这种情况性能极差，一般是不允许的。通过设置 maxmemory 限制最大使用内存。超出限制时，根据redis提供的几种内存淘汰机制让用户自己决定如何腾出新空间以提供正常的读写服务。

 - noeviction： 拒绝写操作， 读、删除可以正常使用。默认策略，不建议使用；
 
 - allkeys-lru： 移除最近最少使用的key，最常用的策略；
 
 - allkeys-random：随机删除某个key，不建议使用；
 
 - volatile-lru：在设置了过期时间的key中，移除最近最少使用的key，不建议使用；
 
 - volatile-random：在设置了过期时间的key中，随机删除某个key，不建议使用；
 
 - volatile-ttl： 在设置了过期时间的key中，把最早要过期的key优先删除。

or

 - noeviction: 当内存不足以容纳新写入数据时，新写入操作会报错，这个一般没人用吧，实在是太恶心了
 
 - allkeys-lru：当内存不足以容纳新写入数据时，在键空间中，移除最近最少使用的key（这个是最常用的）
 
 - allkeys-random：当内存不足以容纳新写入数据时，在键空间中，随机移除某个key，这个一般没人用吧，为啥要随机，肯定是把最近最少使用的 key 给干掉啊。
 
 - volatile-lru：当内存不足以容纳新写入数据时，在设置了过期时间的键空间中，移除最近最少使用的 key（这个一般不太合适）
 
 - volatile-random：当内存不足以容纳新写入数据时，在设置了过期时间的键空间中，随机移除某个 key
 
 - volatile-ttl：当内存不足以容纳新写入数据时，在设置了过期时间的键空间中，有更早过期时间的 key 优先移除

四、用java手写一个LRU算法实现
====

```java
public class LRUCache<K,V> extends LinkedHashMap<K,V> {

    private int cacheSize;

    public LRUCache(int cacheSize){
        super(10,0.75f,true);
        // 设置hashmap大小，true是让linkedhashmap按照访问顺序排序
        this.cacheSize = cacheSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        // 当map中数量大于指定缓存个数的时候，自动删除最老的数据
        return size()>cacheSize;
    }
    
}
```
