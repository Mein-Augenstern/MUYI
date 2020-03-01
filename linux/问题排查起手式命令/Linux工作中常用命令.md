1、将目录授权给其他用户步骤
------

> 1.更改目录所有者命令:
chown -R 用户名称 目录名称

> 2.更改目录权限命令:
chmod -R 755 目录名称

2、linux_port_check
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

3、Linux排查日志文件常用命令
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
