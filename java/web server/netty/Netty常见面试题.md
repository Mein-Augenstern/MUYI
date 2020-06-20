看在前面
------

> * <a href="https://www.cnblogs.com/hulianwangjiagoushi/p/13143938.html">你要的Netty常见面试题总结，我面试回来整理好了！</a>

概览
------

* Netty 是什么？

* 为什么要用 Netty？

* Netty 应用场景了解么？

* Netty 核心组件有哪些？分别有什么作用？

* EventloopGroup 了解么?和 EventLoop 啥关系?

* Bootstrap 和 ServerBootstrap 了解么？

* NioEventLoopGroup 默认的构造函数会起多少线程？

* Netty 线程模型了解么？

* Netty 服务端和客户端的启动过程了解么？

* Netty 长连接、心跳机制了解么？

* Netty 的零拷贝了解么？

Netty 是什么？
------

面试官 ：介绍一下自己对 Netty 的认识吧！小伙子。

我 ：好的！那我就简单用 3 点来概括一下 Netty 吧！

1、Netty 是一个 基于 NIO 的 client-server(客户端服务器)框架，使用它可以快速简单地开发网络应用程序。

2、它极大地简化并优化了 TCP 和 UDP 套接字服务器等网络编程,并且性能以及安全性等很多方面甚至都要更好。

3、支持多种协议 如 FTP，SMTP，HTTP 以及各种二进制和基于文本的传统协议。

用官方的总结就是：Netty 成功地找到了一种在不妥协可维护性和性能的情况下实现易于开发，性能，稳定性和灵活性的方法。

除了上面介绍的之外，很多开源项目比如我们常用的 Dubbo、RocketMQ、Elasticsearch、gRPC 等等都用到了 Netty。

网络编程我愿意称 Netty 为王 。
