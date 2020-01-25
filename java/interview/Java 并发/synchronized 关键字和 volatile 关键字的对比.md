一句话答案
====

* **volatile关键字**是线程同步的轻量级实现，所以**volatile性能肯定比synchronized关键字要好。**但是**volatile关键字只能用于变量而synchronized关键字可以修饰方法以及代码块。**synchronized关键字在JavaSE1.6（包括JDK1.6版本）之后进行了主要包括为了减少获取锁和释放锁带来的性能消耗而引入的偏向锁和轻量级锁以及其他各种优化之后执行效率有了显著提升，**实际开发中使用synchronized关键字的场景还是更多一些。**

* **多线程访问volatile关键字不会发生阻塞，而synchronized关键字可能会发生阻塞。**

* **volatile关键字能保证数据的可见性，但不能保证数据的原子性。synchronized关键字两者都能保证。**

* **volatile关键字主要用于解决变量在多个线程之间的可见性，而synchronized关键字解决的是多个线程之间的访问资源的同步性。**
