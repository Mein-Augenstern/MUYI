看在前面
=====

* 并发下的ArrayList错误分析：https://blog.csdn.net/lan861698789/article/details/81697409

现象举例
====

* 并发导致数据丢失样例
* 并发导致数据新增为null情况

并发导致数据丢失样例
------

* 示例代码

```java
/**
 * ArrayList并发错误演示
 */
public class ArrayListCocurrentTest {

    private static List<String> threadList = new ArrayList<String>();

    public static void main(String[] args) {
        try {
            testWriteArrayListError();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 测试方法
     *
     * @throws Exception
     */
    private static void testWriteArrayListError() throws Exception {
        /** 创建线程 */
        Runnable writeR = new Runnable() {
            public void run() {
                threadList.add(Thread.currentThread().getName());
            }
        };

        /** 循环启动线程启动 */
        for (int i = 0; i < 1000000; i++) {
            Thread t = new Thread(writeR, i + "");
            t.start();
        }

        /** 等待子线程插入完成 */
        Thread.sleep(2000);

        /** 输出集合内容 */
        for (int i = 0; i < threadList.size(); i++) {
            System.out.println("index:" + i + "->" + threadList.get(i));
        }

        /** 输出集合长度大小 */
        System.out.println("集合长度大小: " + threadList.size());
    }

}
```
![ArrayList并发丢失数据结果示例](https://github.com/Mein-Augenstern/MUYI/blob/master/java/interview/Java%E9%9B%86%E5%90%88%E5%B9%B6%E5%8F%91%E9%97%AE%E9%A2%98/picture/ArrayList-%E5%B9%B6%E5%8F%91%E4%B8%A2%E5%A4%B1%E6%95%B0%E6%8D%AE.png)

并发导致数据新增为null情况
------

* 示例代码

```java
/**
 * ArrayList并发错误演示
 */
public class ArrayListCocurrentTest {

    private static List<String> threadList = new ArrayList<String>(200);

    public static void main(String[] args) {
        try {
            testWriteArrayListError();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 测试方法
     *
     * @throws Exception
     */
    private static void testWriteArrayListError() throws Exception {
        /** 创建线程 */
        Runnable writeR = new Runnable() {
            public void run() {
                threadList.add(Thread.currentThread().getName());
            }
        };

        /** 循环启动线程启动 */
        for (int i = 0; i < 10000; i++) {
            Thread t = new Thread(writeR, i + "");
            t.start();
        }

        /** 等待子线程插入完成 */
        Thread.sleep(2000);

        /** 输出集合内容 */
        for (int i = 0; i < threadList.size(); i++) {
            System.out.println("index:" + i + "->" + threadList.get(i));
        }

        /** 输出集合长度大小 */
        System.out.println("集合长度大小: " + threadList.size());
    }

}
```
![ArrayList-新增元素null情况](https://github.com/Mein-Augenstern/MUYI/blob/master/java/interview/Java%E9%9B%86%E5%90%88%E5%B9%B6%E5%8F%91%E9%97%AE%E9%A2%98/picture/ArrayList-%E6%96%B0%E5%A2%9E%E5%85%83%E7%B4%A0null%E6%83%85%E5%86%B5.png)


