看在前面
====

> * <a href="https://github.com/doocs/advanced-java/blob/master/docs/high-concurrency/how-to-ensure-high-availability-of-message-queues.md">如何保证消息队列的高可用？</a>

Question
====

* 如何保证消息队列的高可用？

面试官心理分析
====

如果有人问到你 MQ 的知识，高可用是必问的。<a href="https://github.com/DemoTransfer/demotransfer/blob/master/java/interview/%E6%B6%88%E6%81%AF%E4%B8%AD%E9%97%B4%E4%BB%B6/why-mq.md">上一讲</a>提到，MQ 会导致系统可用性降低。所以只要你用了 MQ，接下来问的一些要点肯定就是围绕着 MQ 的那些缺点怎么来解决了。

要是你傻乎乎的就干用了一个 MQ，各种问题从来没考虑过，那你就杯具了，面试官对你的感觉就是，只会简单使用一些技术，没任何思考，马上对你的印象就不太好了。这样的同学招进来要是做个 20k 薪资以内的普通小弟还凑合，要是做薪资 20k+ 的高工，那就惨了，让你设计个系统，里面肯定一堆坑，出了事故公司受损失，团队一起背锅。
