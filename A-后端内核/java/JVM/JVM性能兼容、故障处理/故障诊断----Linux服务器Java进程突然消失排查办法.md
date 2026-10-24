看在前面
====

* <a href="https://my.oschina.net/u/4313749/blog/3358259">Linux服务器Java进程突然消失排查办法</a>

* <a href="https://developer.aliyun.com/article/657791">记一次Java进程突然消失问题</a>

现象特征
====

* 多台服务器部署同一应用，部分机器上的服务进程**无故消失**。程序自身日志中没有任何异常输出（没有 OOM 栈、没有 error）。
* 程序日志干净恰恰是关键线索：**堆内 OOM 会抛出 OutOfMemoryError 并留下日志。进程"无声无息消失"通常是系统级动作（最常见是 OOM Killer）**。

排查过程（真实案例回放）
====

第一步：查程序日志
----

* 日志无异常输出，排除应用层错误抛出、System.exit 这类代码主动退出的可能。

第二步：查磁盘空间
----

* `df -h` 确认磁盘未满，排除日志写满导致进程被守护脚本拉起失败/杀死等运维层原因。

第三步：加 JVM 参数观察 GC 与堆
----

```java
-Xloggc:/home/admin/logs/gc.log
-XX:+PrintGCDetails
-XX:+PrintGCDateStamps
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/home/admin/logs/java.hprof
```

* 结果显示无任何 GC 异常、没有生成 hprof，说明堆内存本身没有问题，问题不在 JVM 堆内。

第四步：查系统日志，发现 OOM Killer
----

```java
dmesg | grep -i "oom"
dmesg | grep -i "killed process"
```

* dmesg 输出中发现 `oom kill` 字样，说明进程是被 Linux 内核的 OOM Killer 杀掉的。
* 注意：dmesg 只保留最近的内存缓存日志，**应在现场第一时间查看**；更完整的历史可查 `/var/log/messages` 或 `journalctl -k | grep -i "out of memory"`。

第五步：free 复现确认
----

```java
free -m
```

* 观察到 total 和 used 相差无几，机器物理内存被占满。系统触发 OOM Killer，选择了占内存最大的进程（正是 Java 进程）将其 kill。

根因与解决
====

根因
----

* 检查 JVM 启动参数发现 `-Xmx` 配置与机器物理内存几乎相等。堆可以无限膨胀到吃满整机内存，内核 OOM Killer 直接杀进程。

解决方案
----

* 将 `-Xmx` 调整为系统内存的 **1/2 ~ 2/3** 左右，给系统本身、堆外内存、页缓存留出余量。

方法论总结
====

进程消失的可能原因清单
----

* 代码里调用了 `System.exit()`。被 shell 以 `kill -9` 杀掉（`kill -9` 不给 JVM 机会执行 ShutdownHook，日志无痕）。运维脚本/守护进程误杀。**系统 OOM Killer**（最常见且最隐蔽）。

快速定位三板斧
----

```java
# 1. 看退出码：OOM Killer 杀掉的进程退出码为 137（128+9）
echo $?

# 2. 看内核日志：Out of memory: Kill process <pid> (java)
dmesg -T | grep -i -E "oom|killed process"
tail -n 200 /var/log/messages | grep -i "out of memory"

# 3. 看现场内存
free -m
```

预防措施
----

* 合理设置 `-Xmx`/`-Xms` 与堆外内存上限（DirectMemory、线程栈、Metaspace 都在堆外），总量必须小于物理内存。
* 保持 `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=...` 常开，配合 GC 日志留档。
* 云主机/容器环境注意 cgroup 内存 limit。容器内 JVM 即使 -Xmx 合理，cgroup 超限同样会触发 OOM Kill（Java 10+ 可用 `-XX:+UseContainerSupport` 自动感知）。
* 区分 heap 与 non-heap：堆内存不足会抛 OOM 异常有迹可循。进程"无缘无故消失"要优先怀疑堆外/系统级内存问题。其他会触发 OOM Killer 的场景还有如用 VIM 编辑超大文件把机器内存耗尽等。

> 内容来源：[记一次Java进程突然消失问题](https://developer.aliyun.com/article/657791) · 2026-10 整理回填。
> 延伸：更多按症状分类的 JVM 故障排查入口见 [JVM故障排查FAQ](JVM故障排查FAQ.md)。
