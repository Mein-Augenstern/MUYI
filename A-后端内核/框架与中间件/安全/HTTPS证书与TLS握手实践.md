HTTPS 证书与 TLS 握手实践
====

看在前面
------

同目录[《浅谈常见的七种加密算法及实现》](常见七种加密算法及实现.md)结尾留了一句"HTTPS/TLS 正是混合加密的落地"。这篇就是那个落地的展开：TLS 1.2 完整握手时序、TLS 1.3 的 1-RTT/0-RTT、证书链怎么验。还有 SNI/双证书/ALPN 这些运维面关键词。面试官问 HTTPS，想听的从来不是"加密了更安全"，而是**非对称换钥匙、对称传数据的每一步发生在哪**。

一、TLS 1.2 握手时序：混合加密的现场
------

前提共识：对称快但钥匙怎么安全给对方是难题，非对称能安全换钥匙但慢。TLS 的答案 = **握手用非对称协商出对称会话密钥，之后全程对称加密**。ECDHE 套件的完整时序：

```
 Client                                                      Server
   | ---- ClientHello --------------------------------------> |  支持的 TLS 版本、随机数 RC、
   |                                                          |  密码套件列表、SNI、ALPN、
   |                                                          |  (1.3: key_share 直接带上)
   | <--- ServerHello --------------------------------------- |  选定套件、随机数 RS
   | <--- Certificate --------------------------------------- |  证书链（叶子 + 中间 CA）
   | <--- ServerKeyExchange --------------------------------- |  ECDHE：服务端椭圆曲线公钥
   | <--- ServerHelloDone ----------------------------------- |
   | ---- ClientKeyExchange --------------------------------> |  客户端椭圆曲线公钥
   |                                                          |
   |      双方各自用对方公钥+自己私钥算出相同的 pre-master     |
   |      pre-master + RC + RS ──KDF──> 会话密钥（六把，      |
   |      收发各持加密钥与 MAC 钥）                            |
   |                                                          |
   | ---- ChangeCipherSpec / Finished --------------------->  |  Finished 用会话密钥加密
   | <--- ChangeCipherSpec / Finished ---------------------- |  双方互证"钥匙真的对上了"
   | ================== 应用数据（AES-GCM 对称加密）========= |
```

四个常被追问的点：

* **为什么 Finished 必须有**：前面所有消息都是明文，若被中间人篡改（降级/换套件）。双方算出的 Finished 校验和不一致立刻暴露——握手本身也被握出来的钥匙保护；
* **ECDHE 的 E（临时）**：每次握手生成一次性密钥对，**前向保密**。服务端私钥将来泄露，历史流量也解不开。固定密钥的 RSA 密钥交换没有这性质，已淘汰；
* **私钥永远不出服务器**：客户端只拿公钥做密钥协商，加密的是"密钥材料"不是数据本身；
* **完整握手 2-RTT**：慢在往返。session id/ticket 复用可省到 1-RTT（双方还记得协商结果），1.3 更进一步。

二、TLS 1.3：1-RTT 与 0-RTT
------

1.3 把 ClientHello 直接带上 `key_share`（客户端猜测的曲线公钥），服务端一个回包完成协商。**握手 2-RTT → 1-RTT**。同时砍掉 RSA 密钥交换与一堆弱套件。套件列表从 30+ 缩到 5 个，全 ECDHE，前向保密成为默认而非选项。

再往下是 **0-RTT（Early Data）**：客户端用上次会话的 PSK 直接加密应用数据跟 ClientHello 一起发。服务端首包就收到请求——代价是**这些数据可被重放**（攻击者原样重发 0-RTT 包，服务端无法区分）。工程约束：0-RTT 只能承载幂等请求（GET），非幂等（下单/支付）必须等握手完成。Nginx 里 `ssl_early_data on` 打开的同时要自己承担重放面。

三、证书链校验：信任是怎么传递的
------

证书不能自证（叶子证书说"我是 example.com"凭什么信？），信任靠**逐级签名链**回溯到内置信任库：

```
 根 CA（自签，内置在操作系统/浏览器信任库里，离线保管）
   └── 用根私钥签名 ──> 中间 CA 证书（Issuer = 根 CA）
                         └── 用中间 CA 私钥签名 ──> 叶子证书
                                                    （Issuer = 中间 CA；
                                                     SAN = 域名清单；有效期；用途）
 校验方向（客户端逐级向上走）：
 叶子证书 ──中间 CA 公钥验签──> 中间 CA ──根公钥验签──> 根（只查是否在信任库）
 任何一环失败即断链
```

客户端逐项检查清单：**签名逐级可验**。**当前时间在有效期窗口内**。**访问的域名匹配 SAN 条目**（现代浏览器只看 SAN，CN 只是旧习惯）。**用途字段**（该证书可用于服务器认证）。**未被吊销**（CRL 名单/OCSP 在线查询，OCSP Stapling 由服务端代查并随握手下发，省一轮客户端往返）。

运维高频坑：**Nginx 上 `ssl_certificate` 必须给 fullchain（叶子+中间）**——服务器只发自己配的文件。中间证书缺失时部分客户端（没缓存中间 CA 的老安卓/某些 SDK）显示"证书链不完整"。而桌面浏览器恰好缓存过中间证书于是"我这里好的"，成为经典"部分用户证书错误"工单。

四、SNI、双证书、协议降级与 ALPN
------

* **SNI（Server Name Indication）**：一个 IP 上多个域名各自有证书时，客户端在 ClientHello **明文**带上要访问的域名。服务器据此选对应证书。没有 SNI（或老客户端）就只能返回默认证书。SNI 明文意味着中间人能看到你访问的域名（ECH 加密 ClientHello 是 1.3 的后续演进，落地中）；
* **双证书**：国密合规场景一个域名挂两张证书——签名证书（SM2，验身份）+ 加密证书（SM2，换密钥）。网关按客户端密码套件（TLS-GM vs TLS 国际）分流返回对应链。国际场景也有 RSA + ECC 双链同配（老客户端拿到 RSA 链、新客户端拿到 ECC 链，均由 SNI+套件协商决定）；
* **协议降级防护**：中间人伪造"只支持 TLS1.0"迫使双方降级。防护是**握手里的版本信息随 Finished 一并校验**：TLS 1.2 用 downgrade protection（ServerHello 末尾的填充字节标记）。1.3 用 `supported_versions` 扩展替代明文版本字段。被篡改的版本协商会让 Finished 对不上，握手失败而不是降级；
* **ALPN**：ClientHello 里带上支持的应用协议清单（h2、http/1.1），服务端选定。**HTTP/2 在 TLS 上的启用全靠它**，没有协商到 h2 就自动回退 1.1。这也是为什么"开了 443 不等于支持 h2"。

五、Nginx 生产配置基线
------

```nginx
server {
    listen 443 ssl;
    http2 on;                                        # 经 ALPN 协商生效
    ssl_certificate     /etc/nginx/certs/fullchain.pem;   # 叶子+中间链，别只给叶子
    ssl_certificate_key /etc/nginx/certs/site.key;
    ssl_protocols       TLSv1.2 TLSv1.3;             # 关 SSLv3/TLS1.0/1.1（POODLE/BEAST 一代漏洞）
    ssl_ciphers         ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256;
    ssl_prefer_server_ciphers on;                    # 服务端定套件，缩小降级面
    ssl_session_cache   shared:SSL:10m;              # 会话复用：回访省 1-RTT
    ssl_session_timeout 1h;
    ssl_stapling        on;                          # OCSP Stapling：服务端代查吊销状态
    ssl_stapling_verify on;
    add_header Strict-Transport-Security "max-age=31536000" always;  # HSTS：浏览器只走 https
}
```

六、动手验证：openssl 三连
------

面试讲得再顺，不如亲手跑过一次验证。排查证书问题的三条命令：

```bash
# 1. 看服务端发了什么：证书链（是否缺中间证书）、协商出的协议与套件、SNI 是否生效
openssl s_client -connect example.com:443 -servername example.com -brief </dev/null
#    CONNEST ... Protocol version: TLSv1.3 / Ciphersuite: TLS_AES_256_GCM_SHA384
#    不带 -servername 时返回的是默认证书 → 验证 SNI 配置

# 2. 证书本体：域名(SAN)、签发者链、有效期——过期前监控就盯这两行
openssl x509 -in cert.pem -noout -subject -issuer -dates -ext subjectAltName
#    subject=C = CN, CN = example.com
#    issuer=C = US, O = Let's Encrypt, CN = R3        ← 中间 CA
#    notBefore=...  notAfter=...

# 3. 本地校验链是否闭合（叶子+中间 拼好后能否验到信任库里的根）
openssl verify -untrusted intermediate.pem leaf.pem
#    leaf.pem: OK    ← 配 Nginx 前先本地验，别等部分用户报错
```

三条命令对应三张工单。**"部分用户证书错误"**（查 1，看链）、**"突然全站证书错误"**（查 2，看过期）、**"上线前自检"**（查 3）。curl 的 `-v` 是轻量替代（`SSL connection using TLSv1.3 / TLS_AES_...` 一行同样能看到协商结果）。

顺手记第四条：`openssl s_client -tls1_2 -connect ...` 强制指定版本，专门验证"降级是否真的被关闭"。配置宣称关 TLS1.0 与线上真的拒答 TLS1.0，要用这条命令完成验证。

追问链
------

> **面试官：抓包工具（Charles/Fiddler）能解 HTTPS，是不是说明 HTTPS 能被中间人？**

前提是你**手动把它的根证书装进了信任库**——工具成了你设备上的"根 CA"。于是它能现场签一张假叶子证书完成完整握手（这就是中间人攻击的原理演示）。信任链的根在"你信谁的信任库"，装根证书等于亲手换根。反过来也说明：不装证书的抓包只能看到 ClientHello 里的 SNI 与 IP，正文拿不到。

> **面试官：TLS 1.2 和 1.3 握手最本质的区别是什么？**

不是少一个 RTT 这个结果，而是结构变化：1.3 把密钥交换材料（key_share）提前进 ClientHello。ClientHello 之后的所有握手消息全部加密（1.2 里证书是明文飞的）。既省往返又少泄露（证书内容、套件协商对抓包者不可见）。

> **面试官：证书过期了会发生什么？客户端怎么表现？**

逐级校验里"有效期"这一环断掉，链不完整。浏览器全屏警告、SDK 直接抛 SSLHandshakeException。且多数客户端**不缓存旧证书**，过期即全量故障。所以证书续期是运维纪律：ACME 自动续期（Let's Encrypt 90 天一换）+ 到期前告警。杜绝"人肉记得换证书"这种单点。

> **面试官：为什么前面强调 ECDHE 有前向保密，RSA 密钥交换就被淘汰了？**

RSA 密钥交换是"客户端用服务端公钥加密 pre-master"。服务端私钥一旦泄露，攻击者存档的历史密文全部可解。ECDHE 每次握手生成一次性密钥对、用完即弃。私钥泄露只影响"之后的身份冒充"，救不回"之前录下的流量"。GCM 套件里的 DHE/ECDHE 都是这个目的。

面试官视角小结
------

1. **一张时序图讲完混合加密**：ClientHello→证书→双方 ECDHE 公钥→派生会话密钥。Finished 互证→对称传数据。非对称只换钥匙不传数据；
2. **1.3 三变化**：key_share 前置（1-RTT）、握手消息全程加密、只留 ECDHE 套件（前向保密默认）。0-RTT 快但有重放面，只放幂等请求；
3. **证书链**：根 CA 在信任库、逐级验签向上走。校验五项（签名/有效期/SAN/用途/吊销）。fullchain 必须带中间证书——"部分用户证书错误"的经典来源；
4. **四个关键词**：SNI（一 IP 多证书，明文）、双证书（国密签名+加密/老新兼容）。降级防护（Finished 覆盖版本协商）、ALPN（h2 的启用依据）；
5. **配置基线**：关旧协议、服务端选套件、session cache 复用、stapling、HSTS——五件套张口能报。

> 2026-10 新增（留白补齐）。算法前置：[浅谈常见的七种加密算法及实现](常见七种加密算法及实现.md)。
