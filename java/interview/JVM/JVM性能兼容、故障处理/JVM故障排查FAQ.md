停机或延迟过长？
------

* 有GC详细日志？先tail GC日志看最近GC的Cause，看不出来再用工具分析GC日志

* 无GC详细日志？```jstat -gc```(具体请查看```man jstat```)

* java内存泄漏？分析heap dump

* native泄漏？先确定是不是Xmx配大了，也许并没有泄漏，然后尝试排查```direct memory```泄漏

load高？性能差？
------

* 重启load高？top -H看热点线程，然后jstack看是否C2编译线程（注意十进制十六进制转化）

* 峰值性能差？

挂了？JVM进程还活着
------

* 线程死锁？分析jstack日志

* 从ps看进程状态有"T"字? 用kill -CONT [pid]恢复

* 怀疑codecache问题? 应用日志codecache满

挂了？JVM进程消失了
------

* 有crash日志？

* 无crash日志,有core文件? $JAVA_HOME/bin/jstack $JAVA_HOME/bin/java core.xxx看jstack，是否有无限递归

* 无crash日志,无core文件? dmesg | grep java 看是不是被oom killer杀死了，可能是内存配置太大。

常见GC问题
------

大部分gc问题都可以通过调整参数来解决

* young gc时间过长，比如十几秒，甚至几十秒，这种都是很不正常，一般是swap打开了。

* young gc过于频繁，一般调大堆或者某个generation能解决。

* CMS/FullGC过于频繁一般是调大堆或者old能解决，这种遇到的比较多的是淘宝的线上的forest每天凌晨数据包更新，forest数据包内存更新会导致凌晨堆内某一段时间有2份数据，而且forest数据包每天也在变大，后来forest更新后，更新后大部分数据放到堆外，这个问题就基本上解决了。

* 线上的好多机器都是虚拟机，8g的物理内存，不少应用cms等gc频率变多后，会自己调大堆，导致堆和堆外用的过多，被kernel kill了。调大堆这个应该是跟业务发展有关系。

* 有不少应用内存碎片触发cms的问题，比如之前的hbase，他们后来pe通过凌晨jmap定时触发Fullgc来解决，而且他们也在java层面实现一些cache来解决这个问题。

* 应用本身代码有问题，导致内存泄露，或者gc严重，甚至oom，这种一般是引用某次升级后出现的，应该是他们的代码有bug，一般我们协助，应用自己解决。
