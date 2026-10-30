看在前面
====

* <a href="https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/HashSet%20%E6%98%AF%E5%A6%82%E4%BD%95%E4%BF%9D%E8%AF%81%E4%B8%8D%E9%87%8D%E5%A4%8D%E7%9A%84.md">HashSet 是如何保证不重复的</a>

HashMap 和 HashSet区别
====

如果你看过 HashSet 源码就知道：HashSet 底层就是基于 HashMap 实现的。（HashSet 自己必须实现的方法只有 clone() 、writeObject()、readObject()，其他方法都是直接调用 HashMap 中的方法。

| HashMap        | HashSet    |  
| :------:   | :-------:   | 
| 实现了Map接口        | 实现Set接口      |   
| 存储键值对        | 仅存储对象      |   
| 调用 put（）向map中添加元素        | 调用 add（）方法向Set中添加元素      |   
| HashMap使用键（Key）计算Hashcode       | HashSet使用成员对象来计算hashcode值，对于两个对象来说hashcode可能相同，所以equals()方法用来判断对象的相等性      |   

一、Set=Map 视角：源码证据
------

HashSet 不是"类似 HashMap"，而是**字面意义上包了一个 HashMap**。打开 JDK 源码，4 行代码就能看完它的全部家底：

```java
public class HashSet<E> extends AbstractSet<E>
        implements Set<E>, Cloneable, java.io.Serializable {

    private transient HashMap<E,Object> map;              // 唯一的存储：一个 HashMap

    private static final Object PRESENT = new Object();   // 哨兵：充当所有 value

    public HashSet() { map = new HashMap<>(); }           // 构造器就是 new HashMap

    public boolean add(E e) { return map.put(e, PRESENT) == null; }
    // key 是元素本身，value 永远是 PRESENT；
    // put 返回旧 value，返回 null 说明 key 之前不存在 → 插入成功
    // 返回 PRESENT（非 null）说明 key 已存在 → add 返回 false，"添加失败"
    public boolean contains(Object o) { return map.containsKey(o); }
    public boolean remove(Object o) { return map.remove(o) == PRESENT; }
    public int size() { return map.size(); }
}
```

由此可以直接读出 HashSet 的一切行为：

* **add 的返回值语义**来自 put 的返回值——所以 HashSet 才能告诉你"这个元素是不是新加的"；

* **查询 O(1)** 也是 HashMap 的 containsKey 的能力，Set 没有任何自己的查找逻辑；

* **迭代**就是迭代 map.keySet()，HashSet 的 iterator() 直接转调 keySet().iterator()；

* clone/writeObject/readObject 要自己实现的原因：map 字段是 transient 的，序列化要单独处理（HashMap 有自己的序列化协议）。clone 则要浅拷贝出新的 HashSet，而不是共用同一个 map。

二、PRESENT 哨兵：value 没有意义，但必须有
------

HashMap 的 put 需要一个非语义化的 value 占位，PRESENT 这个全局共享的 `new Object()` 就是干这个的：

* 所有元素共用同一个 PRESENT 实例，**不增加任何额外内存**（value 引用都指向同一对象）；

* 它的唯一使命是"非 null"——`map.put(e, PRESENT) == null` 靠它区分"key 是新的还是旧的"；

* 同样的手法出现在其它"只需存在性"的结构里，比如 ConcurrentSkipListSet、以及各种"用 Map 当 Set 用"的业务代码。**Set 的本质就是"只关心 key 的 Map"**。

三、equals-hashCode 契约：不重复的真正保证
------

HashSet 判重的流程完全继承 HashMap 定位 key 的流程（详见 [HashSet 是如何保证不重复的](../java%E5%9F%BA%E7%A1%80/HashSet%E4%BF%9D%E8%AF%81%E4%B8%8D%E9%87%8D%E5%A4%8D%E7%9A%84%E5%8E%9F%E7%90%86.md)）：

```
add(e)：先 hashCode() 定位桶
   ├── 桶内没有相同 hash     → 不重复，直接插入
   └── 桶内有相同 hash（哈希碰撞）
          ├── equals() == true  → 判定重复，add 返回 false
          └── equals() == false → 哈希碰撞而已，挂到链表/红黑树上
```

* 这就是**契约**的意义：equals 相等必须 hashCode 相等（反之不要求）。只重写 equals 不重写 hashCode，两个"相等"的对象会落到不同的桶，contains 永远查不到——对象"存得进、查不出"；

* 用可变对象当元素同理危险。字段一改 hashCode 变了，元素就"丢"在旧桶里（HashMap/HashSet 共同的坑，详见 [HashSet 是如何保证不重复的](../java%E5%9F%BA%E7%A1%80/HashSet%E4%BF%9D%E8%AF%81%E4%B8%8D%E9%87%8D%E5%A4%8D%E7%9A%84%E5%8E%9F%E7%90%86.md)）；

* 自定义类型放进 HashSet 前三件事：重写 equals、重写 hashCode、保证参与判重的字段不可变。

四、常见追问
------

* "HashSet 允许 null 吗？"——允许一个 null（HashMap 的 key 允许一个 null）；

* "HashSet 有序吗？"——无序（HashMap 无序）。要插入序用 LinkedHashSet，要排序用 TreeSet。和 Map 侧四个实现类一一对应，选型思路见 [HashMap、Hashtable、LinkedHashMap和TreeMap特点说明](HashMap、Hashtable、LinkedHashMap和TreeMap特点说明.md)。

* "HashSet 线程安全吗？"——不安全（HashMap 不安全），并发场景用 Collections.newSetFromMap(new ConcurrentHashMap<>()) 或 CopyOnWriteArraySet；

* "既然 Set 就是 Map，为什么不直接用 Map 而要个 Set？"——语义：add/contains 表达"集合"意图，省掉无意义的 value，API 更准确、可读性更好。

> 2026-10 扩充（Round1 查漏）。
