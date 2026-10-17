看在前面
====

* <a href="https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/java%E5%9F%BA%E7%A1%80/HashSet%20%E6%98%AF%E5%A6%82%E4%BD%95%E4%BF%9D%E8%AF%81%E4%B8%8D%E9%87%8D%E5%A4%8D%E7%9A%84.md">HashSet 是如何保证不重复的</a>

HashMap 和 HashSet区别
====

如果你看过 HashSet 源码的话就应该知道：HashSet 底层就是基于 HashMap 实现的。（HashSet 的源码非常非常少，因为除了 clone() 、writeObject()、readObject()是 HashSet 自己不得不实现之外，其他方法都是直接调用 HashMap 中的方法。

| HashMap        | HashSet    |  
| :------:   | :-------:   | 
| 实现了Map接口        | 实现Set接口      |   
| 存储键值对        | 仅存储对象      |   
| 调用 put（）向map中添加元素        | 调用 add（）方法向Set中添加元素      |   
| HashMap使用键（Key）计算Hashcode       | HashSet使用成员对象来计算hashcode值，对于两个对象来说hashcode可能相同，所以equals()方法用来判断对象的相等性      |   
