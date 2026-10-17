看在前面
====

> * <a href="https://review-notes.top/language/java-jvm/%E6%95%85%E9%9A%9C%E8%AF%8A%E6%96%AD-%E9%AB%98CPU%E5%8D%A0%E7%94%A8.html">故障诊断--高CPU占用</a>

排查过程
====

* 执行 top 命令，定位高 CPU 占用的 PID

```java
  PID USER  PR  NI    VIRT    RES    SHR S  %CPU  %MEM     TIME+     COMMAND
10834 hdfs  20   0 6125996   3.4g   6800 S  107.0  5.4    343:35.01  /usr/java/jdk1.8...

```

* 执行 ps -mp PID -o THREAD,tid,time 命令查看线程耗时情况

```java
ps -mp 10834 -o THREAD,tid,time
USER     %CPU PRI SCNT WCHAN  USER SYSTEM   TID     TIME
hdfs      0.2   -    - -         -      -     - 05:43:35
hdfs      0.0  19    - futex_    -      - 11013 00:47:47 --以该线程长耗时为例分析
hdfs      0.0  19    - futex_    -      - 11014 00:01:21
hdfs      0.0  19    - futex_    -      - 11035 00:00:07
hdfs      0.0  19    - futex_    -      - 11037 00:20:04
hdfs      0.0  19    - ep_pol    -      - 11401 00:04:52
hdfs      0.0  19    - ep_pol    -      - 11402 00:04:31
hdfs      0.0  19    - futex_    -      - 11403 00:21:01

```

* 执行 printf "%x\n" TID 将 tid 转换为十六进制

```java
printf "%x\n" 11013
2b05

```

* 执行 jstack PID |grep TID -A 30 定位具体线程

```java
jstack 10834 |grep 2b05 -A 30
"C1 CompilerThread0" #7 daemon prio=9 os_prio=31 cpu=398.45ms elapsed=1607.13s tid=0x00007fd03c809800 nid=0x3d03 waiting on condition  [0x0000000000000000]
   java.lang.Thread.State: RUNNABLE
   No compile task
--
"生产者-3" #14 prio=5 os_prio=31 cpu=375.00ms elapsed=1606.74s tid=0x00007fd03b8bd800 nid=0x6403 waiting on condition  [0x0000700009200000]
   java.lang.Thread.State: TIMED_WAITING (sleeping)
	at java.lang.Thread.sleep(java.base@11.0.2/Native Method)
	at io.gourd.java.concurrency.app.pc.CarFactory$Producer.run(CarFactory.java:45)
	at java.lang.Thread.run(java.base@11.0.2/Thread.java:834)

"生产者-4" #15 prio=5 os_prio=31 cpu=375.46ms elapsed=1606.74s tid=0x00007fd03b931000 nid=0xa203 waiting on condition  [0x0000700009303000]
   java.lang.Thread.State: TIMED_WAITING (sleeping)
	at java.lang.Thread.sleep(java.base@11.0.2/Native Method)
	at io.gourd.java.concurrency.app.pc.CarFactory$Producer.run(CarFactory.java:45)
	at java.lang.Thread.run(java.base@11.0.2/Thread.java:834)

```

扩展
====

根据实际线程情况定位相关代码，如果定位到 GC 相关线程引起高 CPU 问题，可使用 <a href="https://blog.csdn.net/xiaohulunb/article/details/103887785">jstat</a> 相关命令观察 GC 情况
例如： jstat -gcutil -t -h 5 PID 500 10

说明
====

相关测试代码参考-<a href="https://github.com/GourdErwa/java-advanced/tree/master/java-jvm/src/main/java/io/gourd/java/jvm/oom">源码</a>

实际生产过程中我们可以选择更多的工具进行运行监控、分析 dump 文件：

* <a href="https://www.eclipse.org/mat/">推荐 Memory Analyzer (MAT)</a>

* <a href="https://docs.oracle.com/javase/9/tools/jhsdb.htm#JSWOR-GUID-0345CAEB-71CE-4D71-97FE-AA53A4AB028E">jhsdb</a>

* <a href="http://openjdk.java.net/tools/svc/jconsole/">jconsole</a>

* <a href="https://docs.oracle.com/javacomponents/jmc-5-4/jfr-runtime-guide/run.htm#JFRUH176">推荐 Flight Recorder-飞行记录仪</a>

* <a href="https://www.oracle.com/technetwork/java/javaseproducts/mission-control/java-mission-control-1998576.html">Java Mission Control</a>

* <a href="https://www.ej-technologies.com/products/jprofiler/overview.html">推荐 jprofiler-付费 </a>

