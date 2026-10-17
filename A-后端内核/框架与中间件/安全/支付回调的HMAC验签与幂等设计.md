支付回调的 HMAC 验签与幂等设计（star-site 生产实践反哺）
====

> 源码出处：`star-site-backend/star-site-platform-payment-service`。
> 关键文件：`PaymentNotifyController`（回调入口）、`MockPaymentChannel`（HMAC 验签）、`PaymentOrderServiceImpl#handleNotify`（幂等与状态机）、`PaymentOrderMapper.xml#casStatus`（CAS SQL）、`docker/mysql-init/21-payment.sql`（幂等三键）。
> 这套代码是真实跑通的：mock 沙箱渠道用 HMAC-SHA256 完整走「预下单凭证 → 签名回调 → 验签入账」闭环，alipay/wechat 适配器共享同一套回调处理骨架。

支付回调为什么是安全重灾区
------

支付回调（notify）和普通接口最大的区别：**调用方不是你的用户，是渠道服务器，而你的鉴权体系（JWT/session）对它完全无效**。

star-site 里的处理很直白——网关和服务两层都把回调路径放行：

```java
// star-site-platform-gateway/.../JwtGlobalFilter.java
/** 免 token 白名单（/api/payment/notify/** 为渠道服务器异步回调，防线是渠道验签） */
private static final String[] WHITELIST = {
        "/api/auth/**",
        "/api/payment/notify/**",
        "/actuator/**",
};

// payment-start/.../WebConfig.java
registry.addInterceptor(new JwtAuthInterceptor(jwtUtil))
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/payment/notify/**");
```

注释里那句话就是全局的定性：**唯一防线是渠道验签**。这个入口一旦处理不好，三类事故排队上门：

- **伪造回调 = 白嫖**。回调 URL 是公开的（写在预下单请求里），攻击者拿到一个 orderNo 就能 POST 一个"支付成功"。如果没有验签（或者验签形同虚设），一单 9.9 元的"已支付"就凭空产生了，下游业务照常发货/开通会员。这不是理论风险——支付回调是黑产扫描器的标配目标。
- **重放 = 重复入账**。渠道的重试机制是设计出来的（你没应答它就重发），网络重传、渠道侧超时重发也都是常态。同一笔"支付成功"回调可能到达 2 次、3 次。如果每次都执行一次入账逻辑，用户充 100 送 100 的活动能被刷成充 100 送 300。
- **乱序 = 状态错乱**。回调不是状态机教程里的理想消息流：用户点了支付又在别处发起退款、关单请求和支付回调并发到达、退款成功回调比支付成功回调晚到但业务已经把钱退了……如果处理逻辑是"收到成功回调就把订单置为已支付"，就会把一个已退款的订单又掰回已支付。

所以回调处理的安全模型是三件套：**验签防伪造、幂等防重放、状态机防乱序**。下面按这个顺序讲。

HMAC-SHA256 验签原理
------

### 为什么不是 MD5 / 裸 SHA

先看 star-site mock 渠道的签名实现（`MockPaymentChannel`，沙箱里模拟"渠道侧签名 → 商户侧验签"的完整流程）：

```java
// payment-integration/.../MockPaymentChannel.java
/** 沙箱签名：HMAC-SHA256(secret, orderNo)，模拟支付端与回调共用 */
public String sign(String orderNo) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(orderNo.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
        throw new IllegalStateException("mock 渠道签名失败", e);
    }
}
```

三个候选方案逐一排除：

- **MD5**：抗碰撞性早已被击穿（可构造任意前缀碰撞），支付宝早年 `sign_type=MD5` 的接口就是前车之鉴，现在全线 RSA2/证书模式。摘要算法本身不带密钥，还要求双方约定"字段排序 + 拼接 + 加盐"的私有协议，盐一旦泄露全军覆没。**淘汰。**
- **裸 SHA-256**（`sha256(data)`）：解决了抗碰撞，但**没有密钥参与**。任何人都能对同一个 orderNo 算出一模一样的摘要——攻击者给你 `sign=sha256("PP123")`，你验它当然通过。摘要 ≠ 签名，差的那个"只有双方知道的秘密"才是防伪造的核心。
- **HMAC-SHA256**（`hmac(secret, data)`）：密钥参与进每一轮压缩函数（内层/外层两次哈希嵌套，`H((K' ⊕ opad) || H((K' ⊕ ipad) || m))`），改一个比特的密钥摘要全变；构造性伪造需要拿到 secret 本身。密钥参与 + SHA-256 抗碰撞，两个性质都要有。

一句话：**MD5 死于碰撞，裸 SHA 死于无密钥，HMAC 两头都占。**

### 时序安全比较：为什么不能用 ==

验签的最后一公里是比较签名。`MockPaymentChannel#verifyAndParse` 里的写法：

```java
if (!sign(orderNo).equals(sign)) {
    log.warn("mock 回调验签失败: orderNo={}", orderNo);
    throw new BizException(401, "mock 回调验签失败");
}
```

这是沙箱里的简化写法，**生产上要换成 `MessageDigest.isEqual`**——这是本文反哺的第一个改进点：

```java
// 时序安全比较：无论第几个字节不同，耗时都一致
byte[] expected = HexFormat.of().parseHex(sign(orderNo));
byte[] actual   = HexFormat.of().parseHex(sign);
if (!MessageDigest.isEqual(expected, actual)) {
    throw new BizException(401, "回调验签失败");
}
```

原因：`String.equals`（以及 `Arrays.equals`）在**第一个不匹配的字节处立即返回 false**。攻击者逐字节爆破签名时：猜对第 1 个字节，响应平均多耗几十纳秒；猜错，立刻返回。测量足够多次求均值，就能把 256 个可能的首字节筛到 1 个，再依次爆破后续字节——把 2^256 的穷举降成 2×256 次"字节 oracle"查询。这就是 timing attack（时序攻击），和 Spring Security 里密码比较必须用常量时间函数是同一个道理。

`MessageDigest.isEqual` 的实现是先把两个数组**都完整异或/累加到底**再返回结果，比较耗时与"在第几个字节分岔"无关，时序侧信道被焊死。

> 经验值：HTTP 网络抖动会让单次测量噪声很大，远程 timing attack 难度不低——但验签代码是写在知识库里要被复制一百遍的，**一律写常量时间比较**，不给自己留"这次应该没人测"的侥幸。

### 密钥管理：环境变量进、不落库、不进日志

看 star-site 的配置约定（`payment-start/src/main/resources/application.yml`）：

```yaml
# —— 支付渠道（军规：渠道密钥一律环境变量/配置中心注入，禁入库禁入仓）——
star:
  payment:
    notify-url: ${PAYMENT_NOTIFY_URL:https://47.92.235.34:9090/api/payment/notify}
    mock:
      enabled: ${PAYMENT_MOCK_ENABLED:true}
      secret: ${PAYMENT_MOCK_SECRET:star-site-mock-payment-secret-dev-only}
    # 支付宝（当面付扫码）：@ConditionalOnProperty 按 app-id 是否存在装配——
    # 密钥不经 yml 默认值（空串会被判定为已配置），接入时在 nacos yaml/compose env 补齐以下键：
    #   star.payment.alipay.app-id / private-key / alipay-public-key
```

几条硬规则，每条都有对应的坑：

- **注入链路**：K8s Secret / 配置中心 → 容器环境变量 → `${ENV_VAR}` 占位符。密钥永远不出现在代码仓库（yml 里的默认值只允许是 dev-only 沙箱值，且命名自带 `dev-only` 字样自证身份）。
- **不落库**：密钥存进配置表 = 拿到数据库只读权限就拿到资金操作权。数据库的访问面（DBA、备份、只读实例、SQL 审计日志）远大于环境变量的访问面。
- **不进日志**：`log.warn("mock 回调验签失败: orderNo={}")` 打的是单号，**永远不打 secret、不打算出的签名串全文**。把密钥打进日志一次，日志采集链路（ELK、对象存储归档）上的每个节点都成了泄漏点。
- **可轮换**：HMAC 是对称密钥，双方要同步换。设计上留 `PAYMENT_MOCK_SECRET` 这种环境变量位，就是为了换钥不发版。

验签实现走读：原始字节流的坑
------

### 入口：原始报文必须原样进验签器

回调入口 `PaymentNotifyController` 是整个链路的钥匙：

```java
// payment-adapter/.../PaymentNotifyController.java
@PostMapping("/{channel}")
public ResponseEntity<String> onNotify(@PathVariable("channel") String channel,
                                       @RequestHeader Map<String, String> headers,
                                       @RequestBody String rawBody) {
    try {
        String ack = orderService.handleNotify(channel, lowercaseKeys(headers), rawBody);
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(ack);
    } catch (BizException e) {
        // 验签失败/金额不符：4xx 原文应答，渠道按策略重试；本地状态机未动
        log.warn("渠道回调拒绝: channel={}, code={}, msg={}", channel, e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getCode() == 401 ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST)
                .contentType(MediaType.TEXT_PLAIN)
                .body(e.getMessage());
    }
}

/** 渠道签名头大小写不敏感（网关/HTTP 实现可能改写），统一转小写键 */
private Map<String, String> lowercaseKeys(Map<String, String> headers) {
    Map<String, String> lowered = new HashMap<>();
    headers.forEach((k, v) -> lowered.put(k == null ? "" : k.toLowerCase(), v));
    return lowered;
}
```

注意三个细节，都是踩过才知道的：

1. **`@RequestBody String rawBody`，不是 DTO**。回调体必须以原始形态进验签器。微信 v3 的签名覆盖的是 HTTP body 的**原始字节**；如果绑定成 JSON 对象再序列化回去，字段顺序、空格、转义方式任何一点变化，字节就变了，签名必挂。更隐蔽的是字符编码转手：Spring 按 `Content-Type` 里的 charset 把字节解码成 String，如果容器/网关在中间按错误 charset 解过一次（比如把 UTF-8 当 ISO-8859-1），非法字节序列会被替换字符吞掉，**String 再编码回字节就和签名时的字节对不上了**。最稳的写法是 `@RequestBody byte[] rawBody`，从头到尾不碰字符语义。star-site 用 String 是因为微信 SDK 的 `RequestParam.body` 接收 String 且链路上没有二次转码；但凡多一层过滤器改写 body，这里就是事故点。
2. **headers 键转小写**。HTTP 头名大小写不敏感，但 Java Map 查找敏感：`Wechatpay-Serial` 和 `wechatpay-serial` 是两个 key。网关转发、HTTP 客户端实现都可能改写大小写，微信验签四件套（`wechatpay-serial/nonce/signature/timestamp`）按裸键 `headers.get("wechatpay-serial")` 取，进来先统一小写，别在验签器里猜 casing。
3. **应答体不套统一 R 模板**。渠道要的是它自己认识的应答（alipay 要纯文本 `success`），包一层 `{"code":0,"data":...}` 渠道不认，会判定应答失败然后重试到天荒地老。

### 微信 v3：四件套 + 官方 SDK 验签解密一体

```java
// payment-integration/.../WechatPayChannel.java
@Override
public CallbackResult verifyAndParse(Map<String, String> headers, String rawBody) {
    RequestParam param = new RequestParam.Builder()
            .serialNumber(headers.get(HDR_SERIAL))      // wechatpay-serial：平台证书序列号
            .nonce(headers.get(HDR_NONCE))              // wechatpay-nonce
            .signature(headers.get(HDR_SIGNATURE))      // wechatpay-signature
            .timestamp(headers.get(HDR_TIMESTAMP))      // wechatpay-timestamp
            .body(rawBody)                              // 原始报文——验签就是验它
            .build();
    Transaction transaction;
    try {
        // 验签 + AES-GCM 解密一体完成；失败抛 WechatPayException
        transaction = notificationParser.parse(param, Transaction.class);
    } catch (Exception e) {
        log.warn("微信回调验签失败: err={}", e.getMessage());
        throw new BizException(401, "微信回调验签失败");
    }
    boolean success = transaction.getTradeState() == Transaction.TradeStateEnum.SUCCESS;
    BigDecimal paid = transaction.getAmount() == null ? BigDecimal.ZERO
            : BigDecimal.valueOf(transaction.getAmount().getTotal(), 2);
    return new CallbackResult(transaction.getOutTradeNo(), transaction.getTransactionId(), paid, success);
}
```

微信 v3 的签名串是 `timestamp\nnonce\nbody\n` 四段拼出来的，其中 body 就是上面强调的原始字节——这是"回调体不能经字符编码转手"的最直接出处。`notificationParser.parse` 内部做两件事：用平台证书（`RSAAutoCertificateConfig` 自动下载轮换）验 RSA-SHA256 签名，再用 APIv3 密钥做 AES-GCM 解密拿到明文报文。**验签在前、解密在后、解析 JSON 在最后**——顺序反了就是拿未验签数据喂业务逻辑。

### 支付宝：form 报文 + rsaCheckV1

```java
// payment-integration/.../AlipayPaymentChannel.java
@Override
public CallbackResult verifyAndParse(Map<String, String> headers, String rawBody) {
    Map<String, String> params = parseForm(rawBody);
    try {
        // rsaCheckV1 内部剔除 sign/sign_type 后验签；验签失败直接拒绝响应
        if (!AlipaySignature.rsaCheckV1(params, alipayPublicKey, CHARSET, SIGN_TYPE)) {
            log.warn("支付宝回调验签失败: trade_no={}", params.get("trade_no"));
            throw new BizException(401, "支付宝回调验签失败");
        }
    } catch (AlipayApiException e) {
        throw new BizException(401, "支付宝回调验签异常");
    }
    String tradeStatus = params.get("trade_status");
    boolean success = TRADE_SUCCESS.equals(tradeStatus) || TRADE_FINISHED.equals(tradeStatus);
    return new CallbackResult(params.get("out_trade_no"), params.get("trade_no"),
            new BigDecimal(params.getOrDefault("total_amount", "0")), success);
}

/** form-urlencoded → 参数表（alipay 回调标准形态） */
private Map<String, String> parseForm(String rawBody) {
    Map<String, String> params = new HashMap<>();
    for (String pair : rawBody.split("&")) {
        int eq = pair.indexOf('=');
        if (eq <= 0) {
            continue;
        }
        String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
        String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
        params.put(key, value);
    }
    return params;
}
```

支付宝的坑在 form 解码：签名是对**URL 解码后的参数值**按 key 的 ASCII 序排列拼 `k1=v1&k2=v2` 再算的，`rsaCheckV1` 内部会剔除 `sign`/`sign_type` 再排序。所以解码必须用和支付宝约定一致的 UTF-8，解错 charset 签名必挂；同时别用任何"智能"参数绑定器先拆了表——原始字符串进、自己拆、自己送验签，链路最短。

### 验签之后的金额核对

```java
// PaymentOrderServiceImpl#handleNotify
// 金额核对：渠道入账金额必须与应付一致，不符拒绝应答（渠道重试，人工介入排查）
if (order.getAmount().compareTo(callback.getPaidAmount()) != 0) {
    log.error("回调金额不符，拒绝入账: orderNo={}, expect={}, paid={}",
            order.getOrderNo(), order.getAmount(), callback.getPaidAmount());
    throw new BizException(ResultCode.BAD_REQUEST.getCode(), "回调金额与应付不符");
}
```

注意用 `BigDecimal.compareTo` 而不是 `equals`（`equals` 连 scale 一起比，`0.01` 和 `0.010` 不等）。这一层防的不是篡改（改金额会破坏签名），防的是**"签名正确但单号对错"**：渠道侧改价重发、并发单串号、乃至自家下单逻辑的 bug。验签证明"报文是真的"，金额核对证明"这单真的是这笔钱"。

幂等设计：回调重试是常态
------

### 先接受设定：同一笔回调会来不止一次

渠道的应答超时窗口很短（秒级），你的服务一次 GC 停顿、一次数据库主从切换，渠道就判定失败开始重发。**重试不是异常，是渠道协议的一部分**。所以幂等不是"加个 if 判断"，而是要让"处理 N 次"和"处理 1 次"在效果上不可区分。

### 第一层：数据库唯一约束兜底

```sql
-- docker/mysql-init/21-payment.sql
-- 幂等三键：uk_order_no（应用侧单号）、uk_biz（source+biz_type+biz_id 业务在途唯一）、
-- uk_channel_trade（channel+channel_trade_no 渠道回调防重放）
UNIQUE KEY `uk_order_no` (`order_no`),
UNIQUE KEY `uk_biz` (`source`, `biz_type`, `biz_id`),
UNIQUE KEY `uk_channel_trade` (`channel`, `channel_trade_no`),
```

三把唯一键各管一件事：`uk_order_no` 管单号全局唯一；`uk_biz` 管同一笔业务（来源服务+业务类型+业务单号）在途支付单只有一张，重复下单在 insert 时就被数据库顶回去；`uk_channel_trade` 管同一个渠道交易号只能被绑到一张支付单上——即使某条代码路径想拿同一笔渠道交易给两张本地单入账，数据库也不同意。**唯一约束是幂等的最后防线：应用层可以有 bug，数据库不会。**

### 第二层（核心）：状态机 CAS

回调入账的代码只有一段：

```java
// PaymentOrderServiceImpl#handleNotify
// CAS 状态机：CREATED → SUCCESS 仅一次；重放（已成功）幂等应答
boolean moved = orderRepository.casStatus(order.getId(),
        PaymentOrder.STATUS_CREATED, PaymentOrder.STATUS_SUCCESS,
        callback.getChannelTradeNo(), LocalDateTime.now());
if (!moved) {
    log.info("回调重放或状态已迁移，幂等应答: orderNo={}, status={}", order.getOrderNo(),
            orderRepository.findById(order.getId()).map(PaymentOrder::getStatus).orElse(-1));
    return ackOf(channelType);
}
```

背后的 SQL（`payment-dal/src/main/resources/mapper/PaymentOrderMapper.xml`）：

```xml
<update id="casStatus">
    UPDATE payment_order
    SET status = #{newStatus},
        channel_trade_no = COALESCE(#{channelTradeNo}, channel_trade_no),
        paid_at = COALESCE(#{paidAt}, paid_at)
    WHERE id = #{id} AND deleted = 0 AND status = #{expectStatus}
</update>
```

这就是数据库层的 compare-and-set。**为什么这一条 SQL 天然幂等且并发安全，值得掰开讲透：**

- **单语句原子性**：`UPDATE ... WHERE status = 期望值` 是一条语句，判断和写入之间没有缝隙。对比经典的 check-then-act 写法——先 `SELECT status` 判断是 CREATED，再 `UPDATE status = SUCCESS`——两步之间任何并发回调都能插进来，两个事务都读到 CREATED、都更新、都发一次"支付成功"事件，重复入账。CAS 把判断压进 WHERE 子句，缝隙消失。
- **InnoDB 行锁保证串行**：两个并发回调 UPDATE 同一行，第一个事务拿到行排他锁完成更新（status: 1→2）提交；第二个事务在锁上等待，拿到锁后重新评估 WHERE——此刻 status 已经是 2，`status = 1` 不再成立，**影响行数 = 0**。`casStatus` 的返回值 `> 0` 就是"这次迁移归我"的凭证。不需要 Redis 分布式锁、不需要 `synchronized`——单行状态的并发控制，数据库行锁就是最便宜最正确的工具。
- **幂等性从谓词来**：重放的回调带着同样的内容再来一次，WHERE 条件 `status = 1` 已经不满足（是 2），0 行受影响，调用方走 `!moved` 分支幂等应答。**"执行 N 次等于执行 1 次"不是靠记住"处理过的请求列表"，而是靠状态本身只允许单向迁移**——第一次执行改变了世界（状态变了），后续执行的条件不再成立。这比请求号去重表更优雅：去重表要考虑清理、考虑和业务写入的事务一致性，而状态谓词天然和业务数据同生共死。
- **`COALESCE` 的细节**：`channel_trade_no = COALESCE(#{channelTradeNo}, channel_trade_no)` 表示"传了新值就写，传 null 就保留旧值"。这样关单路径（`casStatus(id, CREATED, CLOSED, null, null)`）复用同一条 SQL，不会把已有值抹成 null。一个 CAS 方法服务整张状态机。
- **状态值本身是防 ABA 的**：支付状态是单向流转的（CREATED→SUCCESS→REFUNDED，CREATED→CLOSED），不存在"改回原值"的合法路径，所以不需要版本号就能防止 ABA。如果建模允许回退（比如支付单从失败回到待支付），就得再加 version 列做 ABA 防护。

### 状态字段设计

```java
// payment-core-model/.../PaymentOrder.java
/** 已创建（凭证已生成，等待用户支付） */
public static final int STATUS_CREATED = 1;
/** 支付成功（渠道回调确认，终态前状态：可退款） */
public static final int STATUS_SUCCESS = 2;
/** 已关闭（超时未付/主动关闭，终态） */
public static final int STATUS_CLOSED = 3;
/** 已全额退款（终态） */
public static final int STATUS_REFUNDED = 4;
```

```sql
`status` TINYINT NOT NULL DEFAULT 1 COMMENT '1已创建 2成功 3已关闭 4已退款',
```

TINYINT + int 常量，不用 ENUM（改枚举值要 DDL，且跨语言联调时 int 更直白）；状态语义直接写在注释里，`channel_trade_no`（渠道交易号）和 `paid_at`（支付时间）作为**成功回调的伴生字段**，由 CAS 语句在迁移时一并回填——"状态"和"状态的证据"原子地落在一起。

乱序处理：状态机只允许合法迁移
------

验签解决"报文是真的"，不解决"报文该被现在处理"。乱序场景里最有代表性的是**迟到回调**：订单已经退款成功（status=REFUNDED=4），又收到一笔支付成功回调。看代码怎么自愈：

```java
boolean moved = orderRepository.casStatus(order.getId(),
        PaymentOrder.STATUS_CREATED, PaymentOrder.STATUS_SUCCESS, ...);
if (!moved) {
    log.info("回调重放或状态已迁移，幂等应答: ...");
    return ackOf(channelType);
}
```

成功回调的 CAS 期望值写死为 `STATUS_CREATED`。REFUNDED 状态下这条 UPDATE 的 WHERE 不成立，`moved = false`——**不会入账、不会重发支付成功事件、不会把已退款订单掰回已支付**，只是记一笔日志、给渠道回 ACK。乱序防御不是"识别出这是迟到回调然后特殊处理"，而是**状态机天然拒绝非法迁移**：任何"当前状态不是 CREATED 却声称支付成功"的报文，无论迟到、重放还是伪造（假设验签被绕过），都撞在同一堵墙上。

合法迁移全景（全部经 casStatus，全部单向）：

```
CREATED(1) ──支付成功回调──▶ SUCCESS(2) ──全额退款──▶ REFUNDED(4)
     │
     └──关单/超时──▶ CLOSED(3)
```

每条边都有明确的触发者和明确的胜者规则：

- **关单 vs 支付回调并发**（用户正在扫码付款，定时任务/用户同时关单）：两边都是 CAS——关单是 `CREATED→CLOSED`，回调是 `CREATED→SUCCESS`，同一行上行锁串行，**恰好一个赢**。关单代码对输掉的处理：

```java
// PaymentOrderServiceImpl#close
if (!orderRepository.casStatus(id, PaymentOrder.STATUS_CREATED, PaymentOrder.STATUS_CLOSED, null, null)) {
    throw new BizException(ResultCode.BAD_REQUEST.getCode(), "订单状态已变化（可能已支付），请刷新查看");
}
```

  如果回调先到，关单失败并提示用户刷新——钱已收，单不能关。反过来关单先到、回调后到，回调的 CAS 失败幂等应答。这里有个诚实的边角：极小概率下用户付了钱但关单 CAS 先赢，本地单是 CLOSED 而渠道钱已收——渠道侧关单接口对已支付订单会报错（所以代码里渠道关单在前、本地 CAS 在后），真出现就只能靠**对账/退款兜底**。CAS 保证的是"本地状态不自相矛盾"，跨系统的最终一致要靠对账任务收口，这是另一篇的故事。

- **退款路径复用同一套**（`PaymentRefundServiceImpl#apply`）：全额退款成功时 `casStatus(orderId, STATUS_SUCCESS, STATUS_REFUNDED, null, null)`——SUCCESS→REFUNDED 也只许走一次，部分退款不动支付单状态。

- **非成功类通知**（用户关闭了二维码、交易超时关闭）：`callback.isSuccess() == false` 时直接 ACK 忽略，不碰状态机：

```java
if (!callback.isSuccess()) {
    // 非支付成功类通知（如关闭通知）：应答成功让渠道停止重发
    log.info("渠道非成功通知，忽略: channel={}, orderNo={}", channelType, callback.getOrderNo());
    return ackOf(channelType);
}
```

应答协议：返回什么才算 ACK
------

### 各渠道的"签收暗号"不一样

```java
// PaymentOrderServiceImpl
/** 渠道应答体：alipay/mock 收到 "success" 停止重试；微信端见 WechatAck（JSON） */
private static final String CHANNEL_ACK_SUCCESS = "success";
private static final String WECHAT_ACK_SUCCESS = "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";

private String ackOf(String channelType) {
    return PaymentChannelType.WECHAT.equals(channelType) ? WECHAT_ACK_SUCCESS : CHANNEL_ACK_SUCCESS;
}
```

- **支付宝**：HTTP 200 + body 为小写 `success` 七个字符。差一个字（`Success`、`success\n`、包了 JSON）都算失败，按 4m/10m/10m/1h/2h/6h…的衰减节奏重试一天以上。
- **微信 v3**：HTTP 200 + `{"code":"SUCCESS"}`；处理失败回 4xx/5xx，微信按 15s/15s/30s/3m/10m/20m/30m…重试。
- **失败应答**：star-site 里验签失败/金额不符回 401/400 原文。这类是**永久性失败**，渠道重试也不会成功——选择回 4xx 让异常在渠道侧重试队列和自家日志里"有声"，而不是 200 静默吞掉（静默会掩盖攻击尝试）；反过来**临时性失败**（数据库闪断、下游超时）必须回 5xx 让渠道重试，把重试当免费的投递保障。区分"重试有用"和"重试无用"，是应答策略的核心判断。

### 同步快速应答 + 事务后置的取舍

回调线程的使命清单按耗时排序：验签（微秒级）→ 单行 CAS（毫秒级）→ 发 MQ 事件（不应答的依赖）→ 回 ACK。star-site 的取舍：

```java
// 事件增强链路：MQ 停用期发布失败仅告警，入账不受影响（业务方以内部查单兜底）
try {
    com.xp.starsite.api.event.payment.PaymentSucceededEvent event = ...;
    eventPublisher.publish(Topics.TOPIC_PAYMENT, EventTypes.PAYMENT_SUCCEEDED,
            order.getBizType(), order.getBizId(), event);
} catch (Exception e) {
    log.warn("支付成功事件发布失败（不阻断入账）: orderNo={}, err={}", order.getOrderNo(), e.getMessage());
}
```

- **入账（状态迁移）同步做**：单行 UPDATE 足够快，放在 ACK 之前。绝不"先 ACK 后落库"——ACK 之后进程崩溃，这笔支付就丢了，只能等对账捞；而先落库后 ACK，崩溃了渠道会重发，CAS 幂等接住，**不丢不重**。宁可让渠道多重试一轮，不能丢钱。
- **下游联动异步做**：发货、开会员、发通知全部走 MQ 事件，不占回调线程。且发布失败只告警不回滚——**资金入账（对）和业务联动（下游）解耦**，下游通过内部查单接口兜底（`getByBizInternal`）。
- **`handleNotify` 没加 `@Transactional`**：整个方法里唯一的写就是一条自提交的 CAS 语句，不存在需要跨语句一致的东西；包进大事务反而拉长行锁持有时间、拖慢应答。事务边界最小化也是应答速度的一部分。

追问链：换成 RSA2 验签（支付宝公钥模式）差异在哪
------

面试官问完 HMAC，十有八九追问："支付宝用的是 RSA2，和你这套 HMAC 差在哪？" star-site 恰好两个都有（mock 用 HMAC、alipay 用 RSA2），对比是现成的。

- **对称 vs 非对称**。HMAC 是对称的：签名方和验签方持有**同一把** secret——mock 沙箱里"模拟支付端与回调共用"就是这个模型。RSA2 是非对称的：商户用自己的私钥签请求，支付宝用商户公钥验；支付宝用自己的私钥签回调，商户用**支付宝公钥**验（`AlipaySignature.rsaCheckV1(params, alipayPublicKey, CHARSET, "RSA2")` 里的 `alipayPublicKey` 就是支付宝公钥）。两侧密钥不同，才能在互不信任的前提下建立认证。
- **为什么真实渠道不用 HMAC**。对称密钥要求你和渠道共享同一个秘密：秘密在你这边泄漏，攻击者能冒充渠道给你发回调；在渠道侧泄漏，能冒充你发起请求。非对称把"能验"和"能签"拆开——拿到公钥也造不出合法签名。另外 RSA 提供不可否认性（non-repudiation）：签名只能出自私钥持有者，HMAC 双方都能算，出了纠纷无法归责。
- **验签内容不同**。HMAC 是对约定的消息串（这里是 orderNo）做 MAC；RSA2 是对**参数表**做签名：剔除 `sign`/`sign_type`，按 key 的 ASCII 序排列，拼成 `k1=v1&k2=v2...`，`SHA256withRSA` 签名。所以 RSA2 验签前必须先把 form 参数完整解出来（`parseForm`），任何一个参数漏解/多解/解码不一致，待验串就变了。
- **密钥管理复杂度**。HMAC 一把密钥、双方同步轮换；RSA2 是**两对密钥**（商户密钥对 + 支付宝密钥对），你只保管自己的私钥（`star.payment.alipay.private-key`，环境变量注入，不落库不进仓），支付宝公钥是公开资产直接配置。升级版是**公钥证书模式**：支付宝公钥换成证书（含序列号和有效期），配合 `alipayCertSN_RSA2` 与证书自动更新——密钥轮换不再靠人工换配置。微信 v3 走得更远：平台证书由 `RSAAutoCertificateConfig` 自动下载轮换，回调报文体再用 APIv3 密钥做 AES-GCM 加密（对称密钥用在了"保密"上，非对称用在了"认证"上，各司其职）。
- **安全强度与比较方式**。RSA-SHA256 的安全性基于大整数分解难题，抗碰撞由 SHA-256 提供；timing attack 层面，验签仍是"比较两个签名串"，常量时间比较（`MessageDigest.isEqual`）照旧是最佳实践，只是伪造难度本身已经高到不需要靠时序侧信道。
- **共同点**：不管 HMAC 还是 RSA2，**验签都只是认证层**——通过之后该做的金额核对、状态机 CAS、幂等应答，一行都不能少。换算法换不掉幂等设计。

面试官视角小结
------

- **为什么回调是安全重灾区**：入口无 JWT（网关白名单放行），验签是唯一防线；三类攻击——伪造（白嫖）、重放（重复入账）、乱序（状态错乱），分别由验签、幂等、状态机对付。
- **HMAC vs MD5 vs 裸 SHA**：MD5 死于碰撞攻击，裸 SHA 无密钥参与人人可算，HMAC = 密钥混入每一轮压缩 + SHA-256 抗碰撞，防伪造的本质是"没有 secret 造不出合法摘要"。
- **时序安全比较**：`String.equals`/`Arrays.equals` 首个不匹配字节提前返回，耗时侧信道可逐字节爆破签名；用 `MessageDigest.isEqual` 常量时间比较，比较耗时与分歧位置无关。
- **密钥管理**：环境变量/配置中心注入，不落库、不进仓、不进日志；沙箱默认值必须自证 dev-only；HMAC 对称钥要预留轮换通道。
- **原始报文的坑**：签名覆盖的是 HTTP body 的**原始字节**——绑定 DTO 再序列化、字符集转码、网关改写 body、日志过滤器消费流，任何一环都会让字节变化导致验签失败；`@RequestBody byte[]` 或保证 String 链路无二次转码；HTTP 头名统一小写再取。
- **验签顺序**：验签（含解密）→ 金额核对（`BigDecimal.compareTo`）→ 状态迁移 → ACK，未验签的数据不碰业务逻辑。
- **CAS 为什么天然幂等且并发安全**：`UPDATE ... SET status=new WHERE id=? AND status=expected` 把判断压进 WHERE，单语句原子无 check-then-act 缝隙；InnoDB 行锁让并发回调串行，后来者 WHERE 不成立影响行数为 0；单向状态流防 ABA，无需版本号；`COALESCE` 让一条 SQL 服务全部迁移边。幂等的本质不是"记住处理过哪些请求"，而是"第一次处理改变了状态，后续处理的条件不再成立"。
- **数据库唯一约束是最后防线**：`uk_order_no`/`uk_biz`/`uk_channel_trade` 三键，应用层有 bug 时数据库说不。
- **乱序处理**：状态机只允许合法迁移，迟到回调撞在 CAS 的 WHERE 上自动幂等应答；关单与支付回调并发时恰好一个赢；跨系统极端场景（已付款但关单先赢）靠对账兜底。
- **应答协议**：支付宝 200+`success`、微信 200+`{"code":"SUCCESS"}`，不套统一响应模板；永久性失败回 4xx（让异常有声），临时性失败回 5xx（让渠道重试当投递保障）。
- **同步快速应答 + 异步落库的取舍**：入账 = 单行 CAS 同步做完再 ACK（宁可重试不可丢钱）；下游联动走 MQ 事件，发布失败不阻断入账，业务方查单兜底；事务边界最小化，不为回调包大事务。
- **追问 RSA2**：对称（共享秘密、无不可否认性）vs 非对称（商户私钥签、支付宝公钥验，两对密钥各管方向）；RSA2 对排序后的参数表做 SHA256withRSA，验签前须完整解码 form 参数；公钥模式 vs 公钥证书模式（自动轮换）；微信 v3 是"非对称认证 + 对称加密（AES-GCM）"混合。换算法换不掉幂等设计。
