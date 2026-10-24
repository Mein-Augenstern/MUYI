K8s 网络与存储
====

看在前面
------

接[《K8s核心对象与架构》](K8s核心对象与架构.md)。那篇讲"谁管什么"，这篇讲两条最难绕的路。**流量怎么进来**（CNI → Pod 网络 → Service → Ingress 的四级跳）。**数据怎么留住**（PV/PVC/StorageClass 三级解耦 + StatefulSet 为什么必须存在）。面试里这两块最能区分"用过"和"理解过"。

一、Pod 网络与 CNI
------

K8s 对网络的四个原生要求。Pod 间直接通信不经 NAT。节点上的代理（kube-proxy）能和本节点所有 Pod 通信。每个 Pod 有独立 IP。**Pod 之间不管是否同节点都扁平互通**。实现这四条的不是 K8s 本体，而是 CNI 插件：

```
   Pod 10.244.1.5              Pod 10.244.2.8              Pod 10.244.3.6
   (node-1 网段)               (node-2 网段)               (node-3 网段)
        |                           |                           |
   veth pair +                  veth pair +                 veth pair +
   cni0 网桥                    cni0 网桥                   cni0 网桥
        |                           |                           |
        +------- 节点间打通（二选一）-----------------------------+
              overlay：VXLAN 封 UDP 包（flannel 默认，配置简单）
              路由  ：BGP/路由表直达（calico，性能好、可做网络策略）
```

选型一句话：小集群 flannel 够用。要 NetworkPolicy（命名空间/标签级的网络隔离）或在意吞吐，上 calico。**跨节点 overlay 的性能税在封包**。同节点 Pod 通信走网桥近乎裸速。跨节点走 VXLAN 多一层封解包，时延敏感的服务要把副本打散到各节点去对比。

二、Service 四类型与 kube-proxy
------

Pod 会死会重建、IP 随之变化，Service 提供"虚拟 IP + DNS 名"的稳定锚点：

| 类型 | 暴露方式 | 典型用途 |
| --- | --- | --- |
| ClusterIP（默认） | 集群内虚拟 IP，外部不可达 | 内部互调：`health-service.prod.svc.cluster.local` |
| NodePort | 每个节点开一个 30000-32767 端口 | 临时外露、演示；生产不直接用 |
| LoadBalancer | 云厂商 LB → NodePort → Pod | 云上标准外露方式 |
| ExternalName | DNS CNAME 到外部域名 | 集群内统一访问外部依赖（如托管数据库） |

落地机制在 kube-proxy，两种模式。**iptables**（默认）逐条规则匹配，Service/Pod 规模上千后规则线性增长、更新有秒级抖动。**ipvs** 用内核哈希表，规则多了优势明显——大规模集群的标配切换。

```yaml
apiVersion: v1
kind: Service
metadata:
  name: health-service
spec:
  selector: { app: health }     # 流量入口的"指针"：靠 label 圈定后端 Pod
  ports:
    - port: 80                  # Service 对外端口
      targetPort: 8080          # 容器端口（写容器内 http 端口而非 Service 端口）
```

一条链路记住全图（外部进来的完整路径）：

```
 Client ──> DNS 解析 ──> Ingress Controller（七层：按 Host/Path 分流）
              │                        |
              │                        v
              │              Service（ClusterIP 虚拟 IP）
              │                        |
              │              kube-proxy 规则（iptables/ipvs）
              │                        |
              └──────────────> Endpoints（健康的 Pod IP 列表）
```

三、Ingress：七层路由
------

NodePort/LB 是四层（只转发连接），Ingress 在其上加七层规则：按域名/路径路由、TLS 终止、（配合注解）限流重写。**Ingress 对象只是规则，真正干活的是 Ingress Controller**（nginx-ingress、traefik 等，本身是一个靠 DaemonSet/Service 暴露的 Pod）：

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: star-site
spec:
  ingressClassName: nginx
  rules:
    - host: app.example.com
      http:
        paths:
          - path: /api/health        # 健康域流量
            pathType: Prefix
            backend: { service: { name: health-service,  port: { number: 80 } } }
          - path: /api/finance      # 财务域流量
            pathType: Prefix
            backend: { service: { name: finance-service, port: { number: 80 } } }
```

注意路径路由的边界：`/api` 前缀切流适合"域"级隔离。如果两个域共享一个前缀且依赖细粒度灰度，就要上 controller 自己的注解（如 `nginx.ingress.kubernetes.io/canary-*`）或网关层规则。长连接/SSE 场景还要显式关闭 controller 的响应缓冲，否则流式输出会攒成一坨。

四、存储：PV / PVC / StorageClass
------

存储的矛盾：Pod 是临时的、可漂移的，数据盘是实体的、长在某个节点/存储系统上的。K8s 用三层对象把两边的解耦做实：

```
 Pod（消费者）                    集群管理员 / 存储厂商
   |                                    |
   | 引用（只说容量与模式，不说盘在哪）     |
   v                                    v
 PVC ────────────绑定──────────> PV <──────动态供给────── StorageClass
 （申请：20Gi RWO）              （实体卷）   （provisioner + 参数模板）
```

* **PV**：一块实际存在的卷（云盘、NFS、本地盘），生命周期独立于 Pod；
* **PVC**：用户的申请单——容量 + 访问模式（RWO 单节点读写 / ROX 多节点只读 / RWX 多节点读写）。RWX 基本只有 NFS/对象存储能做到；
* **StorageClass**：把"申请单"自动化——声明 `standard`，provisioner 现场创建 PV 并绑定，免去了管理员预创建的池子。

回收策略（reclaimPolicy）决定 PVC 删除后盘的命运：**Delete**（动态供给默认，盘直接删）vs **Retain**（保留待人工处理）。数据库类卷建议改 Retain——误删 PVC 还能抢救。

StatefulSet 为什么必须存在：Deployment 的 Pod 是"无名字的可替换副本"。而有状态服务要的是**稳定的网络标识 + 独立的持久身份**：

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: mysql
spec:
  serviceName: mysql                    # 配套 Headless Service：DNS 逐 Pod 可寻址
  replicas: 3                           # mysql-0 / mysql-1 / mysql-2，名字永不变
  template:
    spec:
      containers:
        - name: mysql
          volumeMounts:
            - { name: data, mountPath: /var/lib/mysql }
  volumeClaimTemplates:                 # 每个 Pod 一块专属 PVC：mysql-data-mysql-0 ...
    - metadata: { name: data }
      spec:
        accessModes: [ "ReadWriteOnce" ]
        storageClassName: standard
        resources: { requests: { storage: 100Gi } }
```

三个与 Deployment 的本质差异。**有序**（0 起来 1 才起，缩容反序，保证主从拓扑）。**名字与存储绑定**（mysql-1 重建后仍挂回原来那块 PVC，数据不丢）。**逐 Pod DNS**（`mysql-1.mysql` 可直连指定实例，主从复制/选主要靠它）。

追问链
------

> **面试官：同一个 Service 背后的两个 Pod 在不同节点，流量怎么过去？**

kube-proxy 在每个节点维护"ClusterIP → 健康 Pod 列表"的转发规则，负载均衡在内核态完成（iptables 随机 / ipvs 哈希）。跨节点那段由 CNI 负责（overlay 封包或 BGP 路由直达）。所以 Service 流量路径 = 本机 kube-proxy 规则 + CNI 跨节点网络，两段各有各的性能特征。

> **面试官：ClusterIP ping 不通，是坏了吗？**

不是，ClusterIP 从设计上就"只存在于 iptables 规则里"，没有网络接口应答 ICMP。验证要用 `curl`/`nc` 打端口。同理 `traceroute` 也不可靠——正确姿势是查 Endpoints、再从 Pod 内 curl Service DNS。

> **面试官：Deployment 跑数据库会怎样？**

两种死法。第一种：滚动更新时新旧两个"无状态"副本同时抢一块 RWO 盘（多节点挂载被拒，Pending）。第二种：Pod 重建换了名字，PVC 挂不上（Deployment 的 PVC 是模板级共享而非 Pod 级专属），数据"看起来丢了"。所以数据库要么 StatefulSet，要么干脆用集群外的托管服务。

> **面试官：ReadWriteMany 什么时候需要？**

多个 Pod 必须同时读写同一份数据时。例如多个应用实例共享的配置目录、机器学习训练的共享数据集。块存储（云盘）做不到 RWX，能提供的是 NFS / CephFS / 对象存储挂载（s3fs 之类）。多数无状态服务只需要 RWO 的临时盘或干脆 EmptyDir。

面试官视角小结
------

1. **流量四级跳**：CNI 管 Pod 扁平网络。Service（四类型表）管集群内稳定寻址。kube-proxy 落规则（iptables 规模瓶颈 → ipvs）。Ingress 管七层入口，Controller 才是干活的。
2. **Service 不通先查 Endpoints**：空列表 = selector 不匹配或 Pod 未就绪，与网络无关。ClusterIP ping 不通是设计使然。
3. **存储三层解耦**：PVC 申请（容量+访问模式）。PV 实体。StorageClass 动态供给。数据库卷回收策略改 Retain。
4. **StatefulSet 三件**：有序伸缩、稳定名字、volumeClaimTemplates 专属卷。每一条都对应 Deployment 跑有状态服务的死法。
5. **CNI 选型一句话**：flannel 简单够用，calico 换性能与 NetworkPolicy；跨节点 VXLAN 有封包税。

> 2026-10 新增（留白补齐）。姊妹篇：[K8s核心对象与架构](K8s核心对象与架构.md)、[K8s生产排障手册](K8s生产排障手册.md)。
