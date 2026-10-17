LVS/Nginx 与负载均衡实践
====

看在前面
------

同目录[《常见负载均衡算法》](常见负载均衡算法.md)讲的是**算法**（轮询/随机/哈希/加权/最小连接，怎么选一个后端），这篇讲**工程落地**：四层与七层怎么分工、LVS 三种转发模式、Nginx upstream 实践、健康检查与摘除——即"流量从入口到后端这一公里"的完整答案。网关层视角的职责切分见[BFF模式与网关层设计](../BFF/BFF模式与网关层设计.md)。

一、四层 vs 七层
------

| 维度 | 四层（LVS/DPVS） | 七层（Nginx/Envoy） |
| --- | --- | --- |
| 工作层 | TCP/UDP 报文，只看 IP+端口 | 解析 HTTP，看得见 Host/Path/Cookie/Header |
| 转发 | 改写地址转发或直接路由，不解应用数据 | 反向代理：终结连接、重建到后端的连接 |
| 性能 | 内核态转发，百万级 QPS/十万级 CPS | 用户态解析，低一个量级但绝对值仍高 |
| 能力 | 只能按 IP:port 分流 | 按路径路由、改写头、重试、缓存、灰度、限流 |

实践结论是**不是二选一，是叠着用**：LVS 顶在最前面扛连接（四层快），Nginx 在其后做业务分流（七层活），后端服务前面还可以再有一层注册中心客户端负载均衡（Ribbon/负载策略，互链算法篇）：

```
 Client ──> LVS（VIP，四层，转发/直接路由）
               │
               v
           Nginx 集群（七层：按 path 分流、限流、重试、压缩）
               │
               ├──> /api/**     ──> 网关 ──> 微服务集群
               └──> /static/**  ──> 本地缓存 / CDN 回源
```

二、LVS 三种转发模式
------

LVS（Linux Virtual Server）工作在内核 netfilter 钩子上，三种模式决定"包怎么到 Real Server、响应怎么回来"：

```
 NAT：进出都过 LVS（改写 目标IP/端口）
   Client ──> VIP(LVS) ──改 DIP:port──> RS（内网）
   Client <── VIP(LVS) <─────响应也回来改写──── RS
   瓶颈：双倍流量过 LVS；RS 可藏内网（最省 IP）

 DR（直接路由）：只改 MAC 帧
   Client ──> VIP ──LVS 只换目的 MAC──> RS（同物理网络，lo 上也绑 VIP + 抑制 ARP）
   Client <─────────────响应由 RS 直接发回，不过 LVS──────────
   去程过 LVS、回程直达：吞吐最高；要求 LVS 与 RS 同二层

 TUN（IP 隧道）：
   Client ──> VIP ──原包外再封一层 IP 隧道头──> RS（解隧道后处理，直接回包）
   RS 可跨机房/跨网段；要求 RS 支持隧道协议
```

| 对比项 | NAT | DR | TUN |
| --- | --- | --- | --- |
| 吞吐 | 低（进出双倍） | 最高 | 高 |
| RS 网络位置 | 任意（可内网） | 必须同二层 | 可跨网段（隧道） |
| RS 改造 | 无 | 绑 VIP 到 lo+ARP 抑制 | 支持隧道封装 |
| RS 数量上限 | 小（LVS 端口/性能瓶颈） | 大 | 大 |
| 典型场景 | 小规模/隐藏内网 | 同机房主流选择 | 跨机房容灾 |

生产标配是 **LVS + Keepalived**：Keepalived 用 VRRP 维持主备两台 LVS，VIP 漂在主上；同时周期性健康检查 RS，死了自动从转发表摘除、恢复再加回——转发（LVS）与控制面（Keepalived）分离：

```
        Keepalived（VRRP 主备，VIP 漂移）
          |  控制：写 LVS 转发表 + 探活 RS
          v
   LVS 主（持有 VIP）<──心跳──> LVS 备（standby）
          |  主挂了：备秒级接管 VIP（ARP 免费通告）
          v
   RS1 / RS2 / RS3（真实服务）
```

```conf
# /etc/keepalived/keepalived.conf（DR 模式骨架）
vrrp_instance VI_1 {
    state MASTER            # 备机 BACKUP
    interface eth0
    virtual_router_id 51
    priority 100            # 备机 <100，谁大谁持 VIP
    virtual_ipaddress { 192.168.1.100/24 }   # VIP
}
virtual_server 192.168.1.100 80 {
    delay_loop 3            # 每 3s 探活一次
    lb_algo wlc             # 加权最小连接（四层算法，呼应算法篇）
    lb_kind DR
    real_server 192.168.1.11 8080 { weight 1
        TCP_CHECK { connect_timeout 3 } }    # 挂了自动摘，恢复自动加
    real_server 192.168.1.12 8080 { weight 1
        TCP_CHECK { connect_timeout 3 } }
}
```

三、Nginx 负载与一致性哈希
------

```nginx
upstream app_server {
    hash $request_uri consistent;          # 一致性哈希：按 URI 落点（缓存命中场景）
    server 10.0.0.11:8080 weight=2 max_fails=2 fail_timeout=10s;
    server 10.0.0.12:8080 weight=1 max_fails=2 fail_timeout=10s;
    server 10.0.0.13:8080 backup;          # 备机：全挂才上
    keepalive 64;                          # 到后端的长连接池，省握手
}

server {
    listen 80;
    location / {
        proxy_pass http://app_server;
        proxy_next_upstream error timeout http_502 http_503;  # 失败换下一台（幂等前提）
        proxy_connect_timeout 2s;
        proxy_read_timeout    5s;
    }
}
```

调度指令速查：默认轮询（round-robin）；`least_conn` 最小连接（请求耗时差异大时比轮询均匀）；`ip_hash` 源 IP 哈希（会话保持的老办法）；`hash KEY consistent` 一致性哈希。

**为什么哈希要加 `consistent`**：算法篇里源地址哈希是 `hash % N`——N 变（摘一台/加一台）几乎所有 key 重新落点，缓存集体失效瞬间穿透到后端。一致性哈希把节点和 key 都映射到 2^32 的环上、key 顺时针找节点，节点增减只影响环上相邻的那段 key（约 1/N），其余缓存原地命中。适用前提：**落点键必须对应"可缓存"的维度**（URI、商品 ID），按客户端 IP 哈希没有缓存收益只有倾斜风险。

四、健康检查与摘除
------

Nginx 开源版只有**被动检查**：`max_fails=2 fail_timeout=10s` = 10 秒窗口内失败 2 次就摘 10 秒（窗口后再给一次机会）。特点是"用户请求当探针"——摘除发生在线上真实失败之后，总有几个用户当了测试员。**主动检查**要靠商业版 `health_check` 指令或开源模块（nginx_upstream_check_module），后台定时探活、异常提前摘、恢复灰度加回。

完整摘除流程比"从 LB 列表删除"多三步，这是生产与玩具的分界：

```
 ① 主动机房：摘"服务发现注册"（Nacos 注销）——挡住新增流量
 ② 摘 LB/Nginx 列表（等一个 fail_timeout/探活周期）——存量列表失效
 ③ 等在途请求耗尽（QPS×P99，或连接数归零）——不掐断进行中的请求
 ④ 再停进程/发布
 顺序错了：注册摘了但 LB 还转发 → 摘 LB 太急掐了在途请求 → 都会出现规律性 5xx 毛刺
```

一句话记忆：**摘流量分三层（注册中心、LB、连接），恢复顺序正好反过来，每层之间等一个探活周期**。K8s 把这套手序内置成了 preStop + terminationGracePeriodSeconds，见[K8s生产排障手册](../../../D-云原生/容器技术/Kubernetes/K8s生产排障手册.md)的滚动更新一节。

追问链
------

> **面试官：LVS 和 Nginx 都能做负载均衡，为什么大厂还要 LVS + Nginx 两层？**

分工不同：LVS 内核态四层转发，单位 CPU 能扛的连接/CPS 是 Nginx 的数倍到数十倍，适合顶住海量并发连接与 DDoS 第一波；Nginx 用户态七层，负责按路径/域名分流、限流、重试、缓存。两层叠加各取所长——LVS 保吞吐上限，Nginx 保灵活性，且任一层可独立扩容。

> **面试官：DR 模式下 RS 怎么"抢"了 VIP 还不乱？**

关键在 ARP 抑制：VIP 配在 LVS 的对外网卡与每个 RS 的 lo 回环上，但 RS 必须 `arp_ignore=1`、`arp_announce=2`，让"谁应答 VIP 的 ARP"只有 LVS——这样外部流量只会被引进 LVS，LVS 换掉目的 MAC 后帧在同二层直达 RS；RS 处理完用 lo 上的 VIP 作为源地址直接回包。漏配 ARP 抑制的经典症状：交换机 ARP 表学到了 RS 的 VIP，流量绕过 LVS 直奔某台 RS，负载瞬间失衡。

> **面试官：会话保持（session 亲和）有哪些做法？各有什么坑？**

按层次：四层 ip_hash/source hash（简单，但 NAT 出口聚在一台、后端挂了会话全丢）；七层 Cookie 插入（Nginx sticky，精确，但要全链路支持）；应用层无状态化（token/Redis 存会话，最正确，改造量最大）。现代答案默认第三种——LB 层的亲和都是"兼容旧架构的过渡手段"。

> **面试官：某台后端一直 502，Nginx 会自动踢掉它吗？**

会，但按被动规则的节奏：`max_fails` 次失败后摘 `fail_timeout` 时长，然后放回来再试——如果它持续坏，就呈现"周期性地把少数请求打向坏节点"的毛刺。所以被动摘除之外要有主动探活与告警；upstream 摘除指标接入监控，能第一时间知道"3 台里只剩 2 台"（容量掉了 1/3，可能需要扩容或降级）。

面试官视角小结
------

1. **四层七层分工**：四层（LVS）内核转发保吞吐、七层（Nginx）反代保灵活，生产叠着用，各层独立扩容；
2. **LVS 三模式**：NAT 进出双倍流量最省 IP、DR 去程过 LVS 回程直达吞吐最高（同二层 + lo 绑 VIP + ARP 抑制）、TUN 隧道跨机房；Keepalived 管主备 VIP 漂移与 RS 探活；
3. **Nginx 调度**：轮询/least_conn/ip_hash/hash consistent；一致性哈希解决"节点增减全量重落点"的缓存失效问题，键要选可缓存维度（URI/ID 而非 IP）；
4. **健康检查**：被动（max_fails/fail_timeout，用户当探针）与主动（探活提前摘）互补；**摘流量三层顺序**：注册中心 → LB → 等在途耗尽，恢复反向；
5. **会话保持三档**：IP 哈希（粗）、Cookie 亲和（准）、无状态化（正解）。

> 2026-10 新增（留白补齐）。算法基础见同目录[《常见负载均衡算法》](常见负载均衡算法.md)。
