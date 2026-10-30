看在前面
------

> * <a href="https://mp.weixin.qq.com/s/rOJOVAmvcMrUrcIN0PE16A">必备快速定位排查问题命令</a>

> * <a href="https://blog.csdn.net/qq_27384769/article/details/79284259">如何查看linux服务器负载</a>（原同名文章独有内容已并入本文）

> * <a href="https://blog.csdn.net/shixiansen6535/article/details/89539842">查看Linux服务器的负载情况</a>（同上并入）

排查总思路
====

服务器出问题按"资源 → 进程 → 日志 → 业务"四步收敛。先看整机资源（CPU/内存/磁盘/IO/网络）。再定位到具体进程和线程。然后翻应用与系统日志。最后回到业务代码。以下命令均可在 1 分钟内给出线索。

整机负载：top / uptime / vmstat
====

* 第一条命令永远是看整体负载：

```java
top                       # 综合：负载、CPU、内存、各进程占用
uptime                    # 快速看 load average：1/5/15 分钟负载
  10:01:23 up 45 days,  2 users,  load average: 3.20, 2.10, 1.50
```

* load average 大于 CPU 核数说明任务排队；三个值递增说明压力正在上升，递减说明正在恢复。排查负载过高时建议以 15 分钟平均值为参考。负载高可能由 CPU 资源不足、I/O 读写瓶颈、内存资源不足造成，也可能是 CPU 正在执行密集型计算。
* 除 top/uptime/vmstat 外，还可以用 `w`、`procinfo` 命令查看负载，或直接读文件 `cat /proc/loadavg`。

```java
vmstat 1 5                # 每秒采样 5 次
procs -----------memory---------- ---swap-- -----io---- -system-- -----cpu-----
 r  b   swpd   free   buff  cache   si   so    bi    bo   in   cs us sy id wa st
 4  0      0 120000 210000 8900000    0    0    10    35 1200 2000 85 10  5  0  0
```

* 重点列：`r`（等待运行的进程数）、`si/so`（swap 换入换出，非 0 说明内存吃紧）。`bi/bo`（块设备 IO）、`us/sy/wa`（用户/内核/等待 IO 的 CPU 占比，`wa` 高 = 磁盘瓶颈）。

内存：free
====

```java
free -m
              total        used        free      shared  buff/cache   available
Mem:          7822        4210         120         200        3492        3180
Swap:         2047           0        2047
```

* 看可用内存看 `available` 而不是 `free`（buff/cache 可回收）。
* `Swap used` 持续增长 = 物理内存不足；若同时进程消失，警惕系统 OOM Killer（`dmesg | grep -i "killed process"`）。
* 其他入口：`top`（执行后按 `shift+m` 对内存排序）、`vmstat`、`procinfo`，或直接读文件 `/proc/meminfo`。

磁盘空间与 IO：df / du / iostat
====

```java
df -h                     # 各挂载点空间使用率，排除磁盘写满
df -i                     # inode 使用率（小文件过多会 inode 耗尽，同样报 No space left）
du -sh /var/log/* | sort -rh | head    # 找出最大的目录/文件
iostat -x 1 3             # 磁盘 IO：关注 %util（接近 100% 磁盘打满）和 await（IO 等待 ms）
```

进程定位：ps / pidstat
====

```java
ps -ef | grep java                       # 找目标进程 PID
ps aux --sort=-%cpu | head -6            # 按 CPU 排序前 5
ps aux --sort=-%mem | head -6            # 按内存排序前 5
pidstat -u -p <pid> 1                    # 按秒看进程 CPU
pidstat -t -p <pid> 1                    # 细化到线程级 CPU（定位热点线程）
```

* 查单个进程占用的内存大小：`top -p <pid>`、`pmap -x <pid>`、`ps aux | grep <pid>`，或读 `/proc/<pid>/status` 文件（如 `/proc/7159/status`）。
* **僵尸进程**：`top` 可看 zombie 总数；用 `ps -ef | grep defunct | grep -v grep` 找到具体僵尸进程。杀死僵尸进程要杀它的父进程才有效：先 `ps -o ppid <pid>` 查到父进程 ID，再 `kill -9 <ppid>`。

网络：ss / netstat / ping / telnet
====

```java
ss -s                                     # 连接数总览
ss -tanp | grep 8080                      # 8080 端口监听与连接、占用进程
netstat -anp | grep ESTABLISHED | wc -l   # 当前 established 连接数
ping target.com                           # 连通性与时延
telnet 10.0.0.1 3306                      # 端口是否可达（服务是否起来/防火墙是否放行）
```

* 定位"端口被谁占了"：`ss -tanp | grep <port>` 或 `lsof -i:<port>`（`netstat -tunlp` 也可列出监听端口与对应服务）。大量 `CLOSE_WAIT` = 应用没关连接（代码泄漏）。大量 `TIME_WAIT` 属正常回收。
* 端口起不来：linux 中小于 1024 的端口只能由 root 用户启动监听。先 `sudo su -` 获取 root 权限再启用服务端口。应用侧通过启动日志排查端口冲突、配置问题。

日志检索：tail / grep
====

```java
tail -f app.log                           # 实时滚动跟踪
tail -n 500 error.log                     # 看最后 500 行
grep -C 5 "NullPointerException" app.log  # 匹配行上下各 5 行上下文
grep -c "ERROR" app.log                   # 统计错误行数
grep "2026-10-17 10:0" app.log            # 按时间窗切日志
less app.log   ->  /关键字  n下一个 N上一个 # 大文件交互式检索
```

Java 应用专属：jps / jstat / jstack / jmap
====

```java
jps -l                                    # 找 Java 进程 PID 与主类
jstat -gcutil <pid> 1000                  # 每秒看 GC 情况：O 区占比、YGC/FGC 次数与耗时
jstack <pid> > stack.txt                  # 导出线程栈（死锁、线程卡死分析）
jmap -histo <pid> | head -20              # 对象统计直方图（哪个类对象最多）
jmap -dump:format=b,file=heap.hprof <pid> # 导出堆 dump，用 MAT 分析内存泄漏
```

* 高 CPU 一分钟定位法（与上面命令串起来）：

```java
top                          # 1. 找到高 CPU 进程 PID
ps -mp <pid> -o THREAD,tid,time   # 2. 找到高 CPU 线程 TID
printf "%x\n" <tid>          # 3. TID 转十六进制（jstack 里 nid 是 16 进制）
jstack <pid> | grep <hex-tid> -A 30   # 4. 定位到具体线程栈，看到底在执行什么
```

命令组合小结
====

| 症状 | 先敲什么 |
| --- | --- |
| 机器慢/卡 | top → vmstat 1 → 看 r、wa、si/so |
| 内存不足 | free -m → dmesg 查 OOM Killer |
| 磁盘告警 | df -h → du -sh 逐层找大文件 → iostat -x 看 IO |
| 端口不通 | ping → telnet 目标端口 → ss -tanp 看监听 |
| 连接异常堆积 | ss -tanp 统计状态分布（CLOSE_WAIT/TIME_WAIT） |
| 端口起不来 | netstat -tunlp 看监听 → 小于 1024 需 root → 翻应用启动日志 |
| 僵尸进程堆积 | top 看 zombie 总数 → ps -ef \| grep defunct → kill -9 父进程 |
| Java 高 CPU/内存 | top → ps -mp → printf %x → jstack / jmap |
| 找错误证据 | tail -f + grep -C 关键字，按时间窗切分 |

> 原链接已失效，2026-10 依据主题重写。
