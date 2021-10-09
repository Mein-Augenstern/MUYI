看在前面
=====

* 并发下的ArrayList错误分析：https://blog.csdn.net/lan861698789/article/details/81697409


并发导致数据丢失样例
------

* 示例代码

```java
/**
 * ArrayList并发错误演示
 *
 * @author xiaopu.gyy
 * @version $Id: ArrayListCocurrentTest.java, v 0.1 2021年10月09日 21:10 xiaopu.gyy Exp $
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
