一句话答案
====

HashMap的key是不能重复的，而这里HashSet的元素又是作为了map的key，当然也不能重复。且需要注意：若要将对象存放到HashSet中并保证对象不重复，应根据实际情况将对象的hashCode方法和equals方法进行重写

Question
====

* <label style="color:blue">**HashMap是怎么做到key不能重复的？**</label>

初识印象
====

hashSet底层是基于hashMap实现的，hashSet存储的元素对应hashMap的key，因为hashMap不能存储重复的Key，所以hashSet不能存放重复元素；由于hashMap的key是基于hashCode存储对象的，所以hashSet中存放的对象也是无序的；hashSet也没有提供get方法，可以通过Iterator迭代器获取数据。

![HashSet源码方法](https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/picture/HashSet%E6%BA%90%E7%A0%81%E6%96%B9%E6%B3%95.PNG)


核心成员变量
====

```Java
// HashSet底层是基于HashMap存储数据，该map的key就是HashSet要存放的数据
private transient HashMap<E,Object> map;

// 该变量用来填充上一个map的value字段，因为HashSet关注的是map的Key
private static final Object PRESENT = new Object();
```


无参构造函数
====

```Java
public HashSet() {
    // 实例化map成员变量
    map = new HashMap<>();
}
```
说明：
* hashSet底层是基于hashMap实现的，hashSet存放的数据实际就是hashMap的key，而hashMap的value存放的是一个静态的final对象PERSENT;
* 当调用hashSet无参构造函数的时候，实际只是实例化了hashMap对象。


add(E e)方法实现
====

```Java
// 添加一个元素，如果该元素已经存在，则返回true，如果不存在，则返回false
public boolean add(E e) {
    // 往map中添加元素，返回null，说明是第一个往map中添加该key
    return map.put(e, PRESENT)==null;
}
```
说明：
* 往hashSet中添加元素，实际是往map成员变量里面添加对应的key和value；
* map中的key实际就是要添加的元素，value是一个固定的对象；
* 当第一次往map中添加key时，添加成功返回null，所以当第一次往hashSet中添加元素时，会返回true；
* 由于hashMap中的key不能重复，所以hashSet不能存储重复元素；


remove(Object o)方法实现
====

```Java
// 删除指定的元素，删除成功返回true
public boolean remove(Object o) {
    // 实际是删除map中的一个对象
    return map.remove(o)==PRESENT;
}
```
说明：
* 当hashSet删除一个元素时，实际是操作map删除对应的元素；
* 当删除map中一个不存在的对象是，会返回null，所以这里当返回PERSENT时，说明之前hashSet往map中添加过对应的元素，因此，当remove(o)返回true时，说明之前已经存在该元素，并且成功删除；当返回false时，说明之前并没有添加过该对象；


iterator()方法实现
====

```Java
// 获取hashSet的迭代器
public Iterator<E> iterator() {
    // 调用map获取keySet
    return map.keySet().iterator();
}
```
说明

* hashset获取迭代器实际是获取map的keySet的iterator；


size()方法实现
====

```Java
public int size() {
    return map.size();
}
```
说明

* size方法实际是调用map.size方法；
