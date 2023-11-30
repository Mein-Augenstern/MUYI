看在前面
=====

* <a href="https://blog.csdn.net/qq_27384769/article/details/79284259">如何查看linux服务器负载</a>
* <a href="https://blog.csdn.net/shixiansen6535/article/details/89539842">查看Linux服务器的负载情况</a>

什么是linux服务器load average
------

Load是用来度量服务器工作量的大小，即计算机cpu任务执行队列的长度，值越大，表明包括正在运行和待运行的进程数越多。负载(load)是linux机器的一个重要指标，直观了反应了机器当前的状态。如果机器负载过高，那么对机器的操作将难以进行。Linux的负载高，主要是由于CPU使用、内存使用、IO消耗三部分构成。任意一项使用过多，都将导致服务器负载的急剧攀升。

如何查看linux服务器负载
-----

可以通过w，top，uptime，procinfo命令，也可以通过/proc/loadavg文件查看。

服务器负载高怎么办
-----

* 服务器负载（load/load average）是根据进程队列的长度来显示的。
* 当服务器出现负载高的现象时（建议以15分钟平均值为参考），可能是由于CPU资源不足，I/O读写瓶颈，内存资源不足等原因造成，也可能是由于CPU正在进行密集型计算。
* 建议使用vmstat -x，iostat，top命令判断负载过高的原因，然后找到具体占用大量资源的进程进行优化处理。

如何查看服务器内存使用率
------

可以通过free，top（执行后可通过shitf+m对内存排序），vmstat，procinfo命令，也可以通过/proc/meminfo文件查看。

如何查看单个进程占用的内存大小
------

可以使用top -p PID，pmap -x PID，ps aux|grep PID命令，也可以通过/proc/$process_id（进程的PID）/status文件查看，例如/proc/7159/status文件。

如何查看正在使用的服务和端口？
------

可以使用netstat -tunlp，netstat -antup，lsof -i:PORT命令查看。

如何杀死进程
------

* 可以使用kill -9 PID（进程号），killall 程序名（比如killall cron）来杀死进程。
* 如果要杀死的是僵尸进程，则需要杀掉进程的父进程才有效果，命令为： kill -9 ppid（ppid为父进程ID号，可以通过ps -o ppid PID查找，例如ps -o ppid 32535）。

如何查找僵尸进程
------

可以使用top命令查看僵尸进程（zombie）的总数，使用ps -ef | grep defunct | grep -v grep查找具体僵尸进程的信息。

为什么启动不了服务器端口
------

* 服务器端口的启动监听，需要从操作系统本身以及应用程序查看。
* linux操作系统1024以下的端口只能由root用户启动，即需要先运行sudo su –获取root权限后再启用服务端口。
* 应用程序问题，建议通过应用程序启动日志来排查失败原因，例如端口冲突（腾讯服务器系统使用端口不能占用，比如36000），配置问题等


