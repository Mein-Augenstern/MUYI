看在前面
------

* <a href="https://tech.meituan.com/2019/01/03/spring-boot-native-memory-leak.html">Spring Boot引起的“堆外内存泄漏”排查及经验总结</a>

背景
------

为了更好地实现对项目的管理，我们将组内一个项目迁移到MDP框架（基于Spring Boot），随后我们就发现系统会频繁报出Swap区域使用量过高的异常。笔者被叫去帮忙查看原因，发现配置了4G堆内内存，但是实际使用的物理内存竟然高达7G，确实不正常。JVM参数配置是“-XX:MetaspaceSize=256M -XX:MaxMetaspaceSize=256M -XX:+AlwaysPreTouch -XX:ReservedCodeCacheSize=128m -XX:InitialCodeCacheSize=128m, -Xss512k -Xmx4g -Xms4g,-XX:+UseG1GC -XX:G1HeapRegionSize=4M”，实际使用的物理内存如下图所示：

![q-1-springboot]()

排查过程
------

**1. 使用Java层面的工具定位内存区域（堆内内存、Code区域或者使用unsafe.allocateMemory和DirectByteBuffer申请的堆外内存）**

笔者在项目中添加```-XX:NativeMemoryTracking=detailJVM```参数重启项目，使用命令```jcmd pid VM.native_memory detail```查看到的内存分布如下：

![q-2-springboot]()

发现命令显示的committed的内存小于物理内存，因为jcmd命令显示的内存包含堆内内存、Code区域、通过unsafe.allocateMemory和DirectByteBuffer申请的内存，但是不包含其他Native Code（C代码）申请的堆外内存。所以猜测是使用Native Code申请内存所导致的问题。

为了防止误判，笔者使用了pmap查看内存分布，发现大量的64M的地址；而这些地址空间不在jcmd命令所给出的地址空间里面，基本上就断定就是这些64M的内存所导致。

![q-3-springboot]()

**2. 使用系统层面的工具定位堆外内存**

因为笔者已经基本上确定是Native Code所引起，而Java层面的工具不便于排查此类问题，只能使用系统层面的工具去定位问题。

**首先，使用了gperftools去定位问题**

gperftools的使用方法可以参考<a href="https://github.com/gperftools/gperftools">gperftools</a>，gperftools的监控如下：

![q-4-springboot]()

从上图可以看出：使用malloc申请的的内存最高到3G之后就释放了，之后始终维持在700M-800M。笔者第一反应是：难道Native Code中没有使用malloc申请，直接使用mmap/brk申请的？（gperftools原理就使用动态链接的方式替换了操作系统默认的内存分配器（glibc）。）

**然后，使用strace去追踪系统调用**

因为使用gperftools没有追踪到这些内存，于是直接使用命令“strace -f -e”brk,mmap,munmap” -p pid”追踪向OS申请内存请求，但是并没有发现有可疑内存申请。strace监控如下图所示:

![q-5-springboot]()

**接着，使用GDB去dump可疑内存**

因为使用strace没有追踪到可疑内存申请；于是想着看看内存中的情况。就是直接使用命令gdp -pid pid进入GDB之后，然后使用命令dump memory mem.bin startAddress endAddressdump内存，其中startAddress和endAddress可以从/proc/pid/smaps中查找。然后使用strings mem.bin查看dump的内容，如下：

![q-6-springboot]()

从内容上来看，像是解压后的JAR包信息。读取JAR包信息应该是在项目启动的时候，那么在项目启动之后使用strace作用就不是很大了。所以应该在项目启动的时候使用strace，而不是启动完成之后。

**再次，项目启动时使用strace去追踪系统调用**

项目启动使用strace追踪系统调用，发现确实申请了很多64M的内存空间，截图如下：

![q-7-springboot]()
















