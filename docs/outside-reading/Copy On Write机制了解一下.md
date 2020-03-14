看在前面
====

> * <a href="https://mp.weixin.qq.com/s?__biz=MzI4Njg5MDA5NA==&mid=2247484364&idx=1&sn=60b00b2188047267e5c46c09ae248ca8&chksm=ebd742cddca0cbdbcf40710dec04757208ee8e64473966b28a01cc87352515e45f9ec237c0a3&token=1534404710&lang=zh_CN#rd">COW奶牛！Copy On Write机制了解一下</a>

文章来源
====

在读《Redis设计与实现》关于哈希表扩容的时候，发现这么一段话：

> 执行BGSAVE命令或者BGREWRITEAOF命令的过程中，Redis需要创建当前服务器进程的子进程，而大多数操作系统都采用**写时复制（copy-on-write）来优化子进程的使用效率**，所以在子进程存在期间，服务器会提高负载因子的阈值，从而避免在子进程存在期间进行哈希表扩展操作，避免不必要的内存写入操作，最大限度地节约内存。

触及到知识的盲区了，于是就去搜了一下copy-on-write写时复制这个技术究竟是怎么样的。发现涉及的东西蛮多的，也挺难读懂的。于是就写下这篇笔记来记录一下我学习copy-on-write的过程。

本文力求简单讲清copy-on-write这个知识点，希望大家看完能有所收获。

一、Linux下的copy-on-write
====

在说明Linux下的copy-on-write机制前，我们首先要知道两个函数：```fork()和exec()```。需要注意的是```exec()```并不是一个特定的函数, 它是一组函数的统称, 它包括了```execl()、execlp()、execv()、execle()、execve()、execvp()```。

1.1 简单来用用fork
------

首先我们来看一下fork()函数是什么鬼：

> fork is an operation whereby a process creates a copy of itself.

fork是类Unix操作系统上创建进程的主要方法。fork用于创建子进程(等同于当前进程的副本)。

* 新的进程要通过老的进程复制自身得到，这就是fork！

如果接触过Linux，我们会知道Linux下init进程是所有进程的爹(相当于Java中的Object对象)

* Linux的进程都通过init进程或init的子进程fork(vfork)出来的。

下面以例子说明一下fork吧：

```java
#include <unistd.h>  
#include <stdio.h>  

int main ()   
{   
    pid_t fpid; //fpid表示fork函数返回的值  
    int count=0;

    // 调用fork，创建出子进程  
    fpid=fork();

    // 所以下面的代码有两个进程执行！
    if (fpid < 0)   
        printf("创建进程失败!/n");   
    else if (fpid == 0) {  
        printf("我是子进程，由父进程fork出来/n");   
        count++;  
    }  
    else {  
        printf("我是父进程/n");   
        count++;  
    }  
    printf("统计结果是: %d/n",count);  
    return 0;  
}  
```

得到的结果输出为：

```java
我是子进程，由父进程fork出来

统计结果是: 1

我是父进程

统计结果是: 1
```

解释一下：

* fork作为一个函数被调用。这个函数会有两次返回，将子进程的PID返回给父进程，0返回给子进程。(如果小于0，则说明创建子进程失败)。

* 再次说明：当前进程调用fork()，会创建一个跟当前进程完全相同的子进程(除了pid)，所以子进程同样是会执行fork()之后的代码。

所以说：

* 父进程在执行if代码块的时候，fpid变量的值是子进程的pid

* 子进程在执行if代码块的时候，fpid变量的值是0

1.2 再来看看exec()函数
------

从上面我们已经知道了fork会创建一个子进程。**子进程的是父进程的副本**。

exec函数的作用就是：**装载一个新的程序**（可执行映像）覆盖**当前进程**内存空间中的映像，**从而执行不同的任务**。

* exec系列函数在执行时会直接替换掉当前进程的地址空间。

我去画张图来理解一下：

![cow一](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/picture/cow%E4%B8%80.png)

<h3>参考资料</h3>

* 程序员必备知识——fork和exec函数详解https://blog.csdn.net/bad_good_man/article/details/49364947

* linux中fork（）函数详解（原创！！实例讲解）：https://blog.csdn.net/jason314/article/details/5640969

* linux c语言 fork() 和 exec 函数的简介和用法：https://blog.csdn.net/nvd11/article/details/8856278

* Linux下Fork与Exec使用：https://www.cnblogs.com/hicjiajia/archive/2011/01/20/1940154.html

* Linux 系统调用 —— fork()内核源码剖析：https://blog.csdn.net/chen892704067/article/details/76596225

1.3 回头来看Linux下的COW是怎么一回事
------

> fork()会产生一个和父进程完全相同的子进程(除了pid)

如果按**传统**的做法，会**直接**将父进程的数据拷贝到子进程中，拷贝完之后，父进程和子进程之间的数据段和堆栈是**相互独立的**。

![cow二](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/picture/cow%E4%BA%8C.png)

但是，以我们的使用经验来说：往往子进程都会执行exec()来做自己想要实现的功能。

* 所以，如果按照上面的做法的话，创建子进程时复制过去的数据是没用的(因为子进程执行exec()，原有的数据会被清空)

既然很多时候复制给子进程的数据是无效的，于是就有了Copy On Write这项技术了，原理也很简单：

* fork创建出的子进程，与父进程共享内存空间。也就是说，如果子进程不对内存空间进行写入操作的话，内存空间中的数据并不会复制给子进程，这样创建子进程的速度就很快了！(不用复制，直接引用父进程的物理空间)。

* 并且如果在fork函数返回之后，子进程第一时间exec一个新的可执行映像，那么也不会浪费时间和内存空间了。

另外的表达方式：

> 在fork之后exec之前两个进程用的是相同的物理空间（内存区），子进程的代码段、数据段、堆栈都是指向父进程的物理空间，也就是说，两者的虚拟空间不同，但其对应的物理空间是同一个。
当父子进程中有更改相应段的行为发生时，再为子进程相应的段分配物理空间。
如果不是因为exec，内核会给子进程的数据段、堆栈段分配相应的物理空间（至此两者有各自的进程空间，互不影响），而代码段继续共享父进程的物理空间（两者的代码完全相同）。
而如果是因为exec，由于两者执行的代码不同，子进程的代码段也会分配单独的物理空间。

Copy On Write技术实现原理：

> fork()之后，kernel把父进程中所有的内存页的权限都设为read-only，然后子进程的地址空间指向父进程。当父子进程都只读内存时，相安无事。当其中某个进程写内存时，CPU硬件检测到内存页是read-only的，于是触发页异常中断（page-fault），陷入kernel的一个中断例程。中断例程中，kernel就会把触发的异常的页复制一份，于是父子进程各自持有独立的一份。

Copy On Write技术好处是什么？

* COW技术可减少分配和复制大量资源时带来的瞬间延时。

* COW技术可减少不必要的资源分配。比如fork进程时，并不是所有的页面都需要复制，父进程的代码段和只读数据段都不被允许修改，所以无需复制。

Copy On Write技术缺点是什么？

* 如果在fork()之后，父子进程都还需要继续进行写操作，那么会产生大量的分页错误(页异常中断page-fault)，这样就得不偿失。

几句话总结Linux的Copy On Write技术：

* fork出的子进程共享父进程的物理空间，当父子进程有内存写入操作时，read-only内存页发生中断，将触发的异常的内存页复制一份(其余的页还是共享父进程的)。

* fork出的子进程功能实现和父进程是一样的。如果有需要，我们会用exec()把当前进程映像替换成新的进程文件，完成自己想要实现的功能。

<h3>参考资料</h3>

* Linux进程基础：http://www.cnblogs.com/vamei/archive/2012/09/20/2694466.html

* Linux写时拷贝技术(copy-on-write)http://www.cnblogs.com/biyeymyhjob/archive/2012/07/20/2601655.html

* 当你在 Linux 上启动一个进程时会发生什么？https://zhuanlan.zhihu.com/p/33159508

* Linux fork()所谓的写时复制(COW)到最后还是要先复制再写吗？https://www.zhihu.com/question/265400460

* 写时拷贝（copy－on－write） COW技术https://blog.csdn.net/u012333003/article/details/25117457

* Copy-On-Write 写时复制原理https://blog.csdn.net/ppppppppp2009/article/details/22750939

二、解释一下Redis的COW
====

基于上面的基础，我们应该已经了解COW这么一项技术了。

下面我来说一下我对《Redis设计与实现》那段话的理解：

* Redis在持久化时，如果是采用BGSAVE命令或者BGREWRITEAOF的方式，那Redis会fork出一个子进程来读取数据，从而写到磁盘中。

* 总体来看，Redis还是读操作比较多。如果子进程存在期间，发生了大量的写操作，那可能就会出现很多的分页错误(页异常中断page-fault)，这样就得耗费不少性能在复制上。

* 而在rehash阶段上，写操作是无法避免的。所以Redis在fork出子进程之后，将负载因子阈值提高，尽量减少写操作，避免不必要的内存写入操作，最大限度地节约内存。

<h3>参考资料</h3>

* fork()后copy on write的一些特性：https://zhoujianshi.github.io/articles/2017/fork()%E5%90%8Ecopy%20on%20write%E7%9A%84%E4%B8%80%E4%BA%9B%E7%89%B9%E6%80%A7/index.html

* 写时复制：https://miao1007.github.io/gitbook/java/juc/cow/

三、文件系统的COW
====

下面来看看文件系统中的COW是啥意思：

Copy-on-write在对数据进行修改的时候，不会直接在原来的数据位置上进行操作，而是重新找个位置修改，这样的好处是一旦系统突然断电，重启之后不需要做Fsck。好处就是能保证数据的完整性，掉电的话容易恢复。

* 比如说：要修改数据块A的内容，先把A读出来，写到B块里面去。如果这时候断电了，原来A的内容还在！

<h3>参考资料</h3>

* 文件系统中的 copy-on-write 模式有什么具体的好处？https://www.zhihu.com/question/19782224/answers/created

* 新一代 Linux 文件系统 btrfs 简介:https://www.ibm.com/developerworks/cn/linux/l-cn-btrfs/

最后
====

最后我们再来看一下写时复制的思想(摘录自维基百科)：

> 写入时复制（英语：Copy-on-write，简称COW）是一种计算机程序设计领域的优化策略。其核心思想是，如果有多个调用者（callers）同时请求相同资源（如内存或磁盘上的数据存储），他们会共同获取相同的指针指向相同的资源，直到某个调用者试图修改资源的内容时，系统才会真正复制一份专用副本（private copy）给该调用者，而其他调用者所见到的最初的资源仍然保持不变。这过程对其他的调用者都是透明的（transparently）。此作法主要的优点是如果调用者没有修改该资源，就不会有副本（private copy）被建立，因此多个调用者只是读取操作时可以共享同一份资源。

至少从本文我们可以总结出：

* Linux通过Copy On Write技术极大地减少了Fork的开销。

* 文件系统通过Copy On Write技术一定程度上保证数据的完整性。

其实在Java里边，也有Copy On Write技术。

![cow三](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/picture/cow%E4%B8%89.png)

<h3>参考资料</h3>

* 写时复制，写时拷贝，写时分裂，Copy on write：https://my.oschina.net/dubenju/blog/815836

* 不会产奶的COW(Copy-On-Write)https://www.jianshu.com/p/b2fb2ee5e3a0
