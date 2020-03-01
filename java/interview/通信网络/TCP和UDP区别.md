看在前面
====

> * <a href="https://github.com/Snailclimb/JavaGuide/blob/master/docs/network/%E8%AE%A1%E7%AE%97%E6%9C%BA%E7%BD%91%E7%BB%9C.md#%E4%B8%80-osi%E4%B8%8Etcpip%E5%90%84%E5%B1%82%E7%9A%84%E7%BB%93%E6%9E%84%E4%B8%8E%E5%8A%9F%E8%83%BD%E9%83%BD%E6%9C%89%E5%93%AA%E4%BA%9B%E5%8D%8F%E8%AE%AE">计算机网络常见面试题</a>

一句话答案
====

![计算机网络六](https://github.com/DemoTransfer/LearningRecord/blob/master/java/interview/%E9%80%9A%E4%BF%A1%E7%BD%91%E7%BB%9C/picture/%E8%AE%A1%E7%AE%97%E6%9C%BA%E7%BD%91%E7%BB%9C%E5%85%AD.jpg)

UDP 在传送数据之前不需要先建立连接，远地主机在收到 UDP 报文后，不需要给出任何确认。虽然 UDP 不提供可靠交付，但在某些情况下 UDP 确是一种最有效的工作方式（一般用于即时通信），比如： QQ 语音、 QQ 视频 、直播等等

TCP 提供面向连接的服务。在传送数据之前必须先建立连接，数据传送结束后要释放连接。 TCP 不提供广播或多播服务。由于 TCP 要提供可靠的，面向连接的传输服务（TCP的可靠体现在TCP在传递数据之前，会有三次握手来建立连接，而且在数据传递时，有确认、窗口、重传、拥塞控制机制，在数据传完后，还会断开连接用来节约系统资源），这一难以避免增加了许多开销，如确认，流量控制，计时器以及连接管理等。这不仅使协议数据单元的首部增大很多，还要占用许多处理机资源。TCP 一般用于文件传输、发送和接收邮件、远程登录等场景。
