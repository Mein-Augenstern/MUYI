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
