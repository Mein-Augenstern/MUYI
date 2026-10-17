一句话答案
====

```HashMap```的```key```是不能重复的，而这里```HashSet```的元素又是作为了```map```的```key```，当然也不能重复。且需要注意：若要将对象存放到```HashSet```中并保证对象不重复，应根据实际情况将对象的```hashCode```方法和```equals```方法进行重写。

Question
====

* **HashMap是怎么做到key不能重复的**？

Answer
====

当你把对象加入```HashSet```时，```HashSet```会先计算对象的```hashcode```值来判断对象加入的位置，同时也会与其他加入的对象的```hashcode```值作比较，如果没有相符的```hashcode```，```HashSet```会假设对象没有重复出现。但是如果发现有相同```hashcode```值的对象，这时会调用```equals()```方法来检查```hashcode```相等的对象是否真的相同。如果两者相同，```HashSet```就不会让加入操作成功。

```hashCode()```与```equals()```的相关规定
====

 1. 如果两个对象相等，则```hashcode```一定也是相同的
 2. 两个对象相等,对两个```equals```方法返回```true```
 3. 两个对象有相同的```hashcode```值，它们也不一定是相等的
 4. 综上，```equals```方法被覆盖过，则```hashCode```方法也必须被覆盖
 5. ```hashCode()```的默认行为是对堆上的对象产生独特值。如果没有重写```hashCode()```，则该```class```的两个对象无论如何都不会相等（即使这两个对象指向相同的数据）。

==与equals的区别
====

 1. ==是判断两个变量或实例是不是指向同一个内存空间 ```equals```是判断两个变量或实例所指向的内存空间的值是不是相同
 2. ==是指对内存地址进行比较 ```equals()```是对字符串的内容进行比较
 3. ==指引用是否相同 ```equals()```指的是值是否相同

初识印象
====

```hashSet```底层是基于```hashMap```实现的，```hashSet```存储的元素对应```hashMap```的```key```，因为```hashMap```不能存储重复的```Key```，所以```hashSet```不能存放重复元素；由于```hashMap```的```key```是基于```hashCode```存储对象的，所以```hashSet```中存放的对象也是无序的；```hashSet```也没有提供```get```方法，可以通过```Iterator```迭代器获取数据。

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
* ```hashSet```底层是基于```hashMap```实现的，```hashSet```存放的数据实际就是```hashMap```的key，而hashMap的value存放的是一个静态的final对象PERSENT;

* 当调用```hashSet```无参构造函数的时候，实际只是实例化了```hashMap```对象。


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
* 往```hashSet```中添加元素，实际是往```map```成员变量里面添加对应的```key```和```value```；

* ```map```中的```key```实际就是要添加的元素，```value```是一个固定的对象；

* 当第一次往```map```中添加```key```时，添加成功返回```null```，所以当第一次往```hashSet```中添加元素时，会返回true；

* 由于```hashMap```中的```key```不能重复，所以```hashSet```不能存储重复元素；


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
* 当```hashSet```删除一个元素时，实际是操作```map```删除对应的元素；

* 当删除```map```中一个不存在的对象是，会返回```null```，所以这里当返回```PERSENT```时，说明之前```hashSet```往```map```中添加过对应的元素，因此，当```remove(o)```返回```true```时，说明之前已经存在该元素，并且成功删除；当返回```false```时，说明之前并没有添加过该对象；


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

* ```hashset```获取迭代器实际是获取```map```的```keySet```的```iterator```；


size()方法实现
====

```Java
public int size() {
    return map.size();
}
```
说明

* ```size```方法实际是调用```map.size```方法；
