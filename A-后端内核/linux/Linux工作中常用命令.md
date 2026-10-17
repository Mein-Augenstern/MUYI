将目录授权给其他用户步骤
------

> 1.更改目录所有者命令:
chown -R 用户名称 目录名称

> 2.更改目录权限命令:
chmod -R 755 目录名称

linux_port_check
------

lsof -i:端口号 用于查看某一端口的占用情况，比如查看8000端口使用情况，lsof -i:8000

```java
# lsof -i:8000
COMMAND   PID USER   FD   TYPE  DEVICE SIZE/OFF NODE NAME
lwfs    22065 root    6u  IPv4 4395053      0t0  TCP *:irdmi (LISTEN)
```

可以看到8000端口已经被轻量级文件系统转发服务lwfs占用

netstat -tunlp |grep 端口号，用于查看指定的端口号的进程情况，如查看8000端口的情况，netstat -tunlp |grep 8000

```java
# netstat -tunlp 
Active Internet connections (only servers)
Proto Recv-Q Send-Q Local Address               Foreign Address             State       PID/Program name   
tcp        0      0 0.0.0.0:111                 0.0.0.0:*                   LISTEN      4814/rpcbind        
tcp        0      0 0.0.0.0:5908                0.0.0.0:*                   LISTEN      25492/qemu-kvm      
tcp        0      0 0.0.0.0:6996                0.0.0.0:*                   LISTEN      22065/lwfs          
tcp        0      0 192.168.122.1:53            0.0.0.0:*                   LISTEN      38296/dnsmasq       
tcp        0      0 0.0.0.0:22                  0.0.0.0:*                   LISTEN      5278/sshd           
tcp        0      0 127.0.0.1:631               0.0.0.0:*                   LISTEN      5013/cupsd          
tcp        0      0 127.0.0.1:25                0.0.0.0:*                   LISTEN      5962/master         
tcp        0      0 0.0.0.0:8666                0.0.0.0:*                   LISTEN      44868/lwfs          
tcp        0      0 0.0.0.0:8000                0.0.0.0:*                   LISTEN      22065/lwfs

# netstat -tunlp | grep 8000
tcp        0      0 0.0.0.0:8000                0.0.0.0:*                   LISTEN      22065/lwfs   
```

说明一下几个参数的含义：

-t (tcp) 仅显示tcp相关选项
-u (udp)仅显示udp相关选项
-n 拒绝显示别名，能显示数字的全部转化为数字
-l 仅列出在Listen(监听)的服务状态
-p 显示建立相关链接的程序名

附加一个python端口占用监测的程序，该程序可以监测指定IP的端口是否被占用。

```java
#!/usr/bin/env python
# -*- coding:utf-8 -*-

import socket, time, thread
socket.setdefaulttimeout(3) #设置默认超时时间

def socket_port(ip, port):
    """
    输入IP和端口号，扫描判断端口是否占用
    """
    try:
        if port >=65535:
            print u'端口扫描结束'
        s=socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        result=s.connect_ex((ip, port))
        if result==0:
            lock.acquire()
            print ip,u':',port,u'端口已占用'
            lock.release()
    except:
        print u'端口扫描异常'

def ip_scan(ip):
    """
    输入IP，扫描IP的0-65534端口情况
    """
    try:
        print u'开始扫描 %s' % ip
        start_time=time.time()
        for i in range(0,65534):
            thread.start_new_thread(socket_port,(ip, int(i)))
        print u'扫描端口完成，总共用时：%.2f' %(time.time()-start_time)
#       raw_input("Press Enter to Exit")
    except:
        print u'扫描ip出错'

if __name__=='__main__':
    url=raw_input('Input the ip you want to scan: ')
    lock=thread.allocate_lock()
    ip_scan(url)
```

该程序执行结果如下：

```java
# python scan_port.py
Input the ip you want to scan: 20.0.208.112
开始扫描 20.0.208.112
20.0.208.112 : 111 端口已占用
20.0.208.112 : 22 端口已占用
20.0.208.112 : 8000 端口已占用
20.0.208.112 : 15996 端口已占用
20.0.208.112 : 41734 端口已占用
扫描端口完成，总共用时：9.38
```

Linux排查日志文件常用命令
------

查询日志中含有某个关键字的信息

```java
cat app.log |grep 'error'
```
  
查询日志尾部最后10行的日志

```java
tail  -n  10  app.log 
```

查询10行之后的所有日志

```java
tail -n +10 app.log
```
  
查询日志文件中的头10行日志

```java
head -n 10  app.log 
```
 
查询日志文件除了最后10行的其他所有日志

```java
head -n -10  app.log 
```

查询日志中含有某个关键字的信息,显示出行号(在1的基础上修改)

```java
cat -n  app.log |grep 'error'
```
  
显示102行,前10行和后10行的日志

```java
cat -n app.log |tail -n +92|head -n 20
```	
 
根据日期时间段查询(前提日志总必须打印日期,先通过grep确定是否有该时间点)

```java
sed -n '/2014-12-17 16:17:20/,/2014-12-17 16:17:36/p'  app.log
```
  
使用more和less命令(分页查看,使用空格翻页)

```java
cat -n app.log |grep "error" |more
```

日志保存到文件

```java
cat -n app.log |grep "error"  > temp.txt
```
  
查找日志文件中fail所在行以及上下各一行并输出

```java
cat /home/zjm/python_test/re_fold/test.log | grep -C 1 'fail'
```

打印出fail所在行以及前1行（Before）

```java
cat /home/zjm/python_test/re_fold/test.log | grep -B 1 'fail'
```

打印出fail所在行以及后一行（After）

```java
cat /home/zjm/python_test/re_fold/test.log | grep -A 1 'fail'
```

打印出在test.log文件中fail有几行

```java
grep 'fail' /home/zjm/python_test/re_fold/test.log | wc -l
```

查看某段时间内的关键字日志

```java
sed -n ‘/2018-06-21 14:30:20/,/2018-06-21 16:12:00/p’ catalina.out |grep ‘keyword’
```

杀死包含关键字的进程
------

将含有”redis”关键词的进程杀死:

```java
ps -ef | grep redis | awk ‘{print $2}’ | xargs kill -9
```

安装zip、unzip应用
------

```java
yum install zip unzip
```

压缩和解压文件
------

以下命令均在/home目录下操作

```
cd /home #进入/home目录
```
　　
a、把/home目录下面的mydata目录压缩为mydata.zip

```
zip -r mydata.zip mydata #压缩mydata目录
```
　　
b、把/home目录下面的mydata.zip解压到mydatabak目录里面

```
unzip mydata.zip -d mydatabak
```
　　
c、把/home目录下面的abc文件夹和123.txt压缩成为abc123.zip

```
zip -r abc123.zip abc 123.txt
```
　　
d、把/home目录下面的wwwroot.zip直接解压到/home目录里面

```
unzip wwwroot.zip
```
　　
e、把/home目录下面的abc12.zip、abc23.zip、abc34.zip同时解压到/home目录里面

```
unzip abc\*.zip
```

f、查看把/home目录下面的wwwroot.zip里面的内容

```
unzip -v wwwroot.zip
```
　　
g、验证/home目录下面的wwwroot.zip是否完整

```
unzip -t wwwroot.zip
```

h、把/home目录下面wwwroot.zip里面的所有文件解压到第一级目录

```
unzip -j wwwroot.zip
```

查看tomcat占用端口号
------

1、先查看tomcat的进程号

```
ps -ef | grep tomcat*
```

后面带*号，是为了查看多个tomcat，例如tomcat6，tomcat7。

2、根据进程号查看端口号

```
netstat -anop | grep pid值
```

3、可以通过端口号，查看其所属的进程号相关信息

```
lsof -i: port号
```

磁盘命令
------

当磁盘大小超过标准时会有报警提示，这时如果掌握df和du命令是非常明智的选择。

df可以查看一级文件夹大小、使用比例、档案系统及其挂入点，但对文件却无能为力。
du可以查看文件及文件夹的大小。

两者配合使用，非常有效。比如用df查看哪个一级目录过大，然后用df查看文件夹或文件的大小，如此便可迅速确定症结。

下面分别简要介绍

df命令可以显示目前所有文件系统的可用空间及使用情形，请看下列这个例子：

```java
[demo ~]$ df -h
Filesystem            Size  Used Avail Use% Mounted on
/dev/sda1             3.9G  300M  3.4G   8% /
/dev/sda7             100G  188M   95G   1% /data0
/dev/sdb1             133G   80G   47G  64% /data1
/dev/sda6             7.8G  218M  7.2G   3% /var
/dev/sda5             7.8G  166M  7.2G   3% /tmp
/dev/sda3             9.7G  2.5G  6.8G  27% /usr
tmpfs                 2.0G     0  2.0G   0% /dev/shm
```

参数 -h 表示使用「Human-readable」的输出，也就是在档案系统大小使用 GB、MB 等易读的格式。

上面的命令输出的第一个字段（Filesystem）及最后一个字段（Mounted on）分别是档案系统及其挂入点。我们可以看到 /dev/sda1 这个分割区被挂在根目录下。

接下来的四个字段 Size、Used、Avail、及 Use% 分别是该分割区的容量、已使用的大小、剩下的大小、及使用的百分比。 FreeBSD下，当硬盘容量已满时，您可能会看到已使用的百分比超过 100%，因为 FreeBSD 会留一些空间给 root，让 root 在档案系统满时，还是可以写东西到该档案系统中，以进行管理。

du：查询文件或文件夹的磁盘使用空间

如果当前目录下文件和文件夹很多，使用不带参数du的命令，可以循环列出所有文件和文件夹所使用的空间。这对查看究竟是那个地方过大是不利的，所以得指定深入目录的层数，参数：--max-depth=，这是个极为有用的参数！如下，注意使用“*”，可以得到文件的使用空间大小.

提醒：一向命令比linux复杂的FreeBSD，它的du命令指定深入目录的层数却是比linux简化，为 -d。

```
[root@bsso yayu]# du -h --max-depth=1 work/testing
27M     work/testing/logs
35M     work/testing

[root@bsso yayu]# du -h --max-depth=1 work/testing/*
8.0K    work/testing/func.php
27M     work/testing/logs
8.1M    work/testing/nohup.out
8.0K    work/testing/testing_c.php
12K     work/testing/testing_func_reg.php
8.0K    work/testing/testing_get.php
8.0K    work/testing/testing_g.php
8.0K    work/testing/var.php

[root@bsso yayu]# du -h --max-depth=1 work/testing/logs/
27M     work/testing/logs/

[root@bsso yayu]# du -h --max-depth=1 work/testing/logs/*
24K     work/testing/logs/errdate.log_show.log
8.0K    work/testing/logs/pertime_show.log
27M     work/testing/logs/show.log
```

值得注意的是，看见一个针对du和df命令异同的文章：《du df 差异导致文件系统误报解决》(http://www.diybl.com/course/6_system/linux/Linuxjs/2008716/133217.html)。

du 统计文件大小相加 
df  统计数据块使用情况

如果有一个进程在打开一个大文件的时候,这个大文件直接被rm 或者mv掉，则du会更新统计数值，df不会更新统计数值,还是认为空间没有释放。直到这个打开大文件的进程被Kill掉。

如此一来在定期删除 /var/spool/clientmqueue下面的文件时，如果没有杀掉其进程，那么空间一直没有释放。

使用下面的命令杀掉进程之后，系统恢复。

```
fuser -u /var/spool/clientmqueue

```

http://www.yayu.org/look.php?id=162

---------------------------------------------------------------------------------------------------------------------
---------------------------------------------------------------------------------------------------------------------
查看linux文件目录的大小和文件夹包含的文件数

统计总数大小

```
du -sh xmldb/

du -sm * | sort -n //统计当前目录大小 并安大小 排序

du -sk * | sort -n

du -sk * | grep guojf //看一个人的大小

du -m | cut -d "/" -f 2 //看第二个/ 字符前的文字
```

查看此文件夹有多少文件 /*/*/* 有多少文件

```
du xmldb/

du xmldb/*/*/* |wc -l

40752
```

解释：

wc [-lmw]

参数说明：

-l :多少行

-m:多少字符

-w:多少字

---------------------------------------------------------------------------------------------------------------------

---------------------------------------------------------------------------------------------------------------------
Linux:ls以K、M、G为单位查看文件大小

```
#man ls

……

-h, --human-readable

                print sizes in human readable format (e.g., 1K 234M 2G)

……

# ls

cuss.war    nohup.out

# ls -l

total 30372

-rw-r--r--    1 root root 31051909 May 24 10:07 cuss.war

-rw-------    1 root root          0 Mar 20 13:52 nohup.out

# ls -lh

total 30M

-rw-r--r--    1 root root 30M May 24 10:07 cuss.war

-rw-------    1 root root     0 Mar 20 13:52 nohup.out

# ll -h

total 30M

-rw-r--r--    1 root root 30M May 24 10:07 cuss.war

-rw-------    1 root root     0 Mar 20 13:52 nohup.out
```

grep是每个Linux发行版都预装的一个强有力的文件模式搜索工具。无论何种原因，如果你的系统没有预装它的话，
你可以很容易的通过系统的包管理器来安装它（Debian/Ubuntu系中的apt-get和RHEl/CentOS/Fedora系中的yum）。

```
$ sudo apt-get install grep #Debian/Ubuntu $ sudo yum install grep #RHEL/CentOS/Fedora
```

只是看可能还是不是很了解，我们还是直接用现实生活中的真实例子直截了当的让你接触grep命令吧！

1.找出所有的mp3文件

grep命令对于过滤来自于标准输出的结果非常有用。例如，假设你的一个文件夹里面全是各种格式的音乐文件。
你要找出艺术家jayZ的所有mp3格式的音乐文件，里面也不要有任何混合音轨。使用find命令再结合管道使用grep就可以完成这个魔法：

```
$ sudo find . -name ".mp3" | grep -i JayZ | grep -vi "remix""
```

在这个例子中，我们使用find命令打印出所有以.mp3为后缀名的文件，接着将其使用管道传递给grep -i过滤和打印出名字为“JayZ”的文件,
再使用管道传送给grep -vi以便过滤掉含有“remix”的项。

2.计算匹配项的数目

这个功能类似于将grep输出的结果用管道传送给计数器（wc程序），grep内建的选项可以达到同样的目的：

```
$ sudo ifconfig | grep -c inet6
```

3.搜索和寻找文件

假设你已经在你的电脑上安装了一个全新的Ubuntu，然后你打算卸载Python。你浏览网页寻找教程，但是你发现存在两个不同版本的Python在使用，
而你不知道你的Ubuntu安装器到底在你的系统中安装了哪个版本的Python，也不知道它安装了哪些模块。解决这个烦恼只需简单的运行以下命令：

```
$ sudo dpkg -l | grep -i python
```

输出例子

```
ii python2.7 2.7.3-0ubuntu3.4 Interactive high-level object-oriented language(version 2.7)

ii python2.7-minimal 2.7.3-0ubuntu3.4 Minimal subset of the Python language (version2.7)

ii python-openssl 0.12-1ubuntu2.1 Python wrapper around the OpenSSL library

ii python-pam 0.4.2-12.2ubuntu4 A Python interface to the PAM library
```

首先，我们运行dpkg -l列出你系统上安装的.deb包。接着，我们使用管道将输出结果传输给命令grep -i python，
这一步可以简单解释为把结果传输给grep然后过滤出所有含有python的项，并返回结果。–i选项用于忽略大小写,因为 grep 是大小写敏感的。
使用选项-i是个好习惯，除非你打算进行更细节的搜索。

4.搜索和过滤文件

grep还可以在一个或多个文件里用于搜索和过滤。让我们来看一个这样的情景：

你的Apache网页服务器出现了问题，你不得不从许多专业网站里找一个发帖询问。
好心回复你的人让你粘贴上来你的/etc/apache2/sites-available/default-ssl文件内容。
假如你能移除掉所有的注释行，那么对你，对帮你的人，以及所有阅读该文件的人，不是更容易发现问题吗？你当然可以很容易的做到！只需这样做就可以了：

```
$ sudo grep -v "#" /etc/apache2/sites-available/default-ssl
```

选项-v是告诉grep命令反转它的输出结果，意思就是不输出匹配的项，做相反的事，打印出所有不匹配的项。
这个例子中，有#的是注释行（译注：其实这个命令并不准确，包含“#”的行不全是注释行。关于如何精确匹配注释行，可以了解更多的关于正则表达式的内容。）。

5.在搜索字符串前面或者后面显示行号

另外两个选项是-A和-B之间的切换，是用以显示匹配的行以及行号，分别控制在字符串前或字符串后显示的行数。
Man页给出了更加详细的解释，我发现一个记忆的小窍门：-A=after、-B=before。

```
$ sudo ifconfig | grep -A 4 etho $ sudo ifconfig | grep -B 2 UP
```

6.在匹配字符串周围打印出行号

grep命令的-C选项和例4中的很相似，不过打印的并不是在匹配字符串的前面或后面的行，而是打印出两个方向都匹配的行
（译注：同上面的记忆窍门一样：-C=center，以此为中心）： $ sudo ifconfig | grep -C 2 lo

7.按给定字符串搜索文件中匹配的行号

当你在编译出错时需要调试时，grep命令的-n选项是个非常有用的功能。它能告诉你所搜索的内容在文件的哪一行：

```
$ sudo grep -n "main" setup.py
```

8.进行精确匹配搜索

传递-w选项给grep命令可以在字符串中进行精确匹配搜索（译注：包含要搜索的单词，而不是通配）。例如，像下面这样输入：

```
$ sudo ifconfig | grep -w “RUNNING”
```

将打印出含有引号内匹配项的行。另外，你还可以试一下这个：

```
$ sudo ifconfig | grep -w “RUN”
```

搜索这个匹配项时，若搜索的东西里面没有这样的一个单独的单词，将什么也不会返回。

9.在所有目录里递归的搜索

假若你要在当前文件夹里搜索一个字符串，而当前文件夹里又有很多子目录，你可以指定一个-r选项以便于递归的搜索：

```
$ sudo grep -r “function” *
```

10.在Gzip压缩文件中搜索

我们还要关注一下grep的衍生应用。第一个是zgrep，这个与zcat很相似，可以用于gzip压缩过的文件。它有与grep相似的命令选项，使用方式也一样：

```
$ sudo zgrep -i error /var/log/syslog.2.gz
```

11.在文件中匹配正则表达式

egrep是另一个衍生应用，代表着“扩展全局正则表达式”。它可以识别更多的正则表达式元字符，例如at + ? | 和。
在搜索源代码文件时，egrep是一个非常有用的工具，还有其他的一些零碎代码文件的搜索需要，使得这样的搜索能力成为必需。
可以在grep命令中使用选项-E来启用它。

```
$ sudo grep -E
```

12.搜索一个固定匹配字符串

fgrep用于在一个文件或文件列表中搜索固定样式的字符串。功能与grep -F同。fgrep的一个通常用法为传递一个含有样式的文件给它：

```
$ sudo fgrep -f file_full_of_patterns.txt file_to_search.txt
```

这些仅仅是grep命令的开始，但是它对于实现各种各样的需求简直是太有用了。除了这种运行的这种只有一行的命令，
grep还可以写成cron任务或者自动的shell脚本去执行，更多的可以相互一起学习！


----------------------------------工作中常用的命令，来判断服务器状态是否正常-------------------------------------

top命令作用是实时现实服务器当前CPU、内存、负载、进程等信息

```
top - 11:10:14 up 7 min,  3 users,  load average: 3.99, 6.85, 3.94
Tasks: 439 total,   2 running, 437 sleeping,   0 stopped,   0 zombie
%Cpu(s): 11.0 us,  5.0 sy,  0.0 ni, 79.1 id,  4.2 wa,  0.0 hi,  0.6 si,  0.0 st
KiB Mem : 32782308 total, 17674748 free, 10686796 used,  4420764 buff/cache
KiB Swap:  8257532 total,  8257532 free,        0 used. 21395408 avail Mem 

  PID USER      PR  NI    VIRT    RES    SHR S  %CPU %MEM     TIME+ COMMAND                                                                                                                                                                                                                                                                                             
 7522 postgres  20   0  385656 162232 155072 S 100.0  0.5   0:36.57 postgres                                                                                                                                                                                                                                                                                            
  872 root      20   0 11.192g 581184  16760 S  54.6  1.8   3:21.29 java  
```
  
第一行：
00:09:14 — 当前系统时间
1days, 13:14 — 系统已经运行了1天13小时14分钟（在这期间没有重启过）
1 users — 当前有1个用户登录系统
load average: 0.75, 0.91, 0.95 — load average后面的三个数分别是1分钟、5分钟、15分钟的负载情况。

 

第二行：
Tasks — 任务（进程），系统现在共有276个进程，其中处于运行中的有2个，274个在休眠（sleep），stoped状态的有0个，zombie状态（僵尸）的有0个

 

第三行：cpu状态
6.7% us — 用户空间占用CPU的百分比。
0.9% sy — 内核空间占用CPU的百分比。
0.0% ni — 改变过优先级的进程占用CPU的百分比
91.6% id — 空闲CPU百分比
0.5% wa — IO等待占用CPU的百分比
0.0% hi — 硬中断（Hardware IRQ）占用CPU的百分比
0.3% si — 软中断（Software Interrupts）占用CPU的百分比

0.0% st — 虚拟内存占用CPU的百分比

 

第四行：内存状态
7138276k total — 物理内存总量（7GB）
3573996k used — 使用中的内存总量（3.5GB）
3564280k free — 空闲内存总量（3.5G）
177540k buffers — 缓存的内存量 （177M）

 

第五行：swap交换分区
0k total — 交换区总量（0GB）
0k used — 使用的交换区总量（0M）
0k free — 空闲交换区总量（0GB）
770076k cached — 缓冲的交换区总量（770M）

 

第六行是空行

 

第七行以下：各进程（任务）的状态监控
PID — 进程id
USER — 进程所有者
PR — 进程优先级
NI — nice值。负值表示高优先级，正值表示低优先级
VIRT — 进程占用的虚拟内存值，单位kb。VIRT=SWAP+RES
RES — 进程占用的物理内存值，单位kb。RES=CODE+DATA
SHR — 进程使用的共享内存值，单位kb
S — 进程状态。D=不可中断的睡眠状态 R=运行 S=睡眠 T=跟踪/停止 Z=僵尸进程
%CPU — 上次更新到现在的CPU时间占用百分比
%MEM — 进程使用的物理内存百分比
TIME+ — 进程使用的CPU时间总计
COMMAND — 进程名称（命令名/命令行）

 

top命令使用过程中，还可以使用一些交互的命令来完成其它参数的功能。

1：显示CPU内核数占用资源情况。
<空格>：立刻刷新。
P：根据CPU使用大小进行排序。
T：根据时间、累计时间排序。
q：退出top命令。
m：切换显示内存信息。
t：切换显示进程和CPU状态信息。
c：切换显示命令名称和完整命令行。
M：根据使用内存大小进行排序。
W：将当前设置写入~/.toprc文件中。这是写top配置文件的推荐方法。
b：打开/关闭允许状态进程的加亮效果
x：打开/关闭当前排序CPU或内存或运行时间的高亮效果
f：查看当前可以自己定义显示内容的格式
z：top界面颜色切换
Z：top界面颜色选择（有0-7种颜色可以选择）


df命令是linux系统以磁盘分区为单位查看文件系统，可以加上参数查看磁盘剩余空间信息，命令格式：
df -hl
显示格式为：　

```
文件系统              容量 已用 可用 已用% 挂载点　
Filesystem            Size Used Avail Use% Mounted on
/dev/hda2              45G   19G   24G 44% /
/dev/hda1             494M   19M 450M   4% /boot
/dev/hda6             4.9G 2.2G 2.5G 47% /home
/dev/hda5             9.7G 2.9G 6.4G 31% /opt
none                 1009M     0 1009M   0% /dev/shm
/dev/hda3             9.7G 7.2G 2.1G 78% /usr/local
/dev/hdb2              75G   75G     0 100% /
/dev/hdb2              75G   75G     0 100% /
```

以上面的输出为例，表示的意思为：
HD硬盘接口的第二个硬盘（b），第二个分区（2），容量是75G，用了75G，可用是0，因此利用率是100%， 被挂载到根分区目录上（/）。

下面是相关命令的解释：

df -hl 查看磁盘剩余空间

df -h 查看每个根路径的分区大小

du -sh [目录名] 返回该目录的大小

du -sm [文件夹] 返回该文件夹总M数

更多功能可以输入一下命令查看：

df --help

du --help

查看linux文件目录的大小和文件夹包含的文件数

统计总数大小

du -sh xmldb/

du -sm * | sort -n //统计当前目录大小 并安大小 排序

du -sk * | sort -n

du -sk * | grep guojf //看一个人的大小

du -m | cut -d "/" -f 2 //看第二个/ 字符前的文字

查看此文件夹有多少文件 /*/*/* 有多少文件

du xmldb/

du xmldb/*/*/* |wc -l

40752

解释：

wc [-lmw]

参数说明：

-l :多少行

-m:多少字符

-w:多少字
