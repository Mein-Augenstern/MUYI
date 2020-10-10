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
