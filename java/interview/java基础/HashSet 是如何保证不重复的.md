初识印象
====

hashSet底层是基于hashMap实现的，hashSet存储的元素对应hashMap的key，因为hashMap不能存储重复的Key，所以hashSet不能存放重复元素；由于hashMap的key是基于hashCode存储对象的，所以hashSet中存放的对象也是无序的；hashSet也没有提供get方法，可以通过Iterator迭代器获取数据。

核心成员变量
====

```Java
// HashSet底层是基于HashMap存储数据，该map的key就是HashSet要存放的数据
private transient HashMap<E,Object> map;

// 该变量用来填充上一个map的value字段，因为HashSet关注的是map的Key
private static final Object PRESENT = new Object();
```
