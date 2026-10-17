看在前面
------

> * <a href="https://mp.weixin.qq.com/s/TC7gQnxBoxvFh-iaQg28YQ">面试官问：服务的心跳机制与断线重连，Netty底层是怎么实现的？懵了</a>

为什么需要心跳机制
====

* 在 TCP 长连接中，客户端和服务器之间定期互相发送一种特殊的数据包（心跳包），通知对方自己还在线，以保证连接的有效性。
* 心跳还有一个常被忽略的作用：连接长时间不用时，**防火墙或路由器会将其断开**，心跳可以保活中间链路。

核心组件：IdleStateHandler
====

* Netty 为心跳机制提供了开箱即用的 `IdleStateHandler`，构造器参数（默认单位秒）：`readerIdleTimeSeconds`（读超时触发 `READER_IDLE`）、`writerIdleTimeSeconds`（写超时触发 `WRITER_IDLE`）、`allIdleTimeSeconds`（读写超时触发 `ALL_IDLE`）。
* 其他时间单位用重载：`IdleStateHandler(long reader, long writer, long all, TimeUnit unit)`；空闲事件以 `IdleStateEvent` 在 pipeline 中传播，业务 Handler 重写 `userEventTriggered` 捕获。

客户端：发送心跳
====

方式一：ClientIdleStateTrigger 捕获 WRITER_IDLE
----

```java
public class ClientIdleStateTrigger extends ChannelInboundHandlerAdapter {
    public static final String HEART_BEAT = "heart beat!";

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            if (((IdleStateEvent) evt).state() == IdleState.WRITER_IDLE) {
                ctx.writeAndFlush(HEART_BEAT); // 向服务端发送心跳
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
```

方式二（Pinger 随机延时循环 ping）
----

* `channelActive` 时启动，`channel.eventLoop().schedule()` 随机等待几秒（`Math.max(1, random.nextInt(8))`）后发送心跳；发送前判断 `channel.isActive()`，连接已断则 `closeFuture()` 并取消任务；任务完成后的 listener 里若成功则**递归调用** `ping(channel)` 调度下一次。

客户端骨架：TcpClient + pipeline 组装
----

* `TcpClient` 持有 host/port，`Bootstrap` **可复用，只在实例化时初始化一次**；`connect()` 中 `bootstrap.connect(host, port)` 并 `future.addListener(getConnectionListener())` 监听连接失败。
* pipeline 组装（`ClientHandlersInitializer`）：`ReconnectHandler`（放最前）→ `LengthFieldBasedFrameDecoder`/`LengthFieldPrepender` → `StringDecoder`/`StringEncoder` → `Pinger`。

服务端：检测心跳
====

```java
public class ServerIdleStateTrigger extends ChannelInboundHandlerAdapter {
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            if (((IdleStateEvent) evt).state() == IdleState.READER_IDLE) {
                ctx.disconnect(); // 规定时间内没收到客户端上行数据，主动断开
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}
```

* 服务端 pipeline：`new IdleStateHandler(5, 0, 0)` 表示 5 秒内未收到任何数据包就触发 READER_IDLE 断开，后接帧解码、字符串编解码与业务 Handler。
* 实测现象：客户端发送 4 个心跳后，第 5 个因随机等待过久，发送时连接已被服务端断开，抛 `Connection reset by peer`——`channel.isActive()` 判断与真正发送之间连接恰好断开，这是心跳方案实战中要注意的竞态。

断线重连的实现
====

* 思路：客户端在**连接断开**（重写 `channelInactive` 触发）或**初次连接失败**时，按重连策略反复尝试，直到成功或重试次数耗尽。

ConnectionListener：连接失败时触发 channelInactive
----

```java
private ChannelFutureListener getConnectionListener() {
    return future -> {
        if (!future.isSuccess()) {
            future.channel().pipeline().fireChannelInactive(); // 手动触发统一重连逻辑
        }
    };
}
```

ReconnectHandler：channelInactive 中延时重连
----

```java
@ChannelHandler.Sharable
public class ReconnectHandler extends ChannelInboundHandlerAdapter {
    private int retries = 0;
    private RetryPolicy retryPolicy;
    private TcpClient tcpClient;
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        retries = 0; // 连接成功后重置重试计数
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (retries == 0) { ctx.close(); }
        if (getRetryPolicy().allowRetry(retries)) {
            long sleepTimeMs = getRetryPolicy().getSleepTimeMs(retries);
            ctx.channel().eventLoop().schedule(() -> tcpClient.connect(), // 复用 Bootstrap 重连
                    sleepTimeMs, TimeUnit.MILLISECONDS);
            retries++;
        }
        ctx.fireChannelInactive();
    }
}
```

RetryPolicy：指数退避重试策略
----

```java
public class ExponentialBackOffRetry implements RetryPolicy {
    // 字段：baseSleepTimeMs / maxRetries / maxSleepMs；MAX_RETRIES_LIMIT = 29 防位移溢出

    @Override
    public boolean allowRetry(int retryCount) { return retryCount < maxRetries; }

    @Override
    public long getSleepTimeMs(int retryCount) {
        // 随机取 1~2^retryCount 之间系数，乘以 baseSleepTimeMs，封顶 maxSleepMs
        long sleepMs = baseSleepTimeMs * Math.max(1, random.nextInt(1 << min(retryCount, 29)));
        return Math.min(sleepMs, maxSleepMs);
    }
}
```

* 默认策略：`new ExponentialBackOffRetry(1000, Integer.MAX_VALUE, 60 * 1000)`——基准 1 秒、无限重试、单次等待封顶 60 秒。
* 实测：先启动客户端会不断重连，间隔指数增长但被 60s 封顶；之后启动服务器，下一次重试即成功连上。
* 扩展点：不同环境可实现 `RetryPolicy` 接口，创建 `TcpClient` 时覆盖默认策略。

面试要点小结
====

* 心跳 = 定时互发探测包保证连接有效性 + 防中间设备掐断空闲链路；Netty 落地靠 `IdleStateHandler` + `userEventTriggered`。
* 分工：客户端关注写空闲（WRITER_IDLE）发心跳，服务端关注读空闲（READER_IDLE）踢连接。
* 断线重连 = `channelInactive` + `eventLoop().schedule` 延时任务 + 可插拔指数退避策略；Bootstrap 只初始化一次可复用。

> 内容来源：[原文](https://mp.weixin.qq.com/s/TC7gQnxBoxvFh-iaQg28YQ) · 2026-10 整理回填。
