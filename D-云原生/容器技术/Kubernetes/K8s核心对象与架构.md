K8s 核心对象与架构
====

看在前面
------

D 轨 K8s 三篇之首：这篇讲集群怎么组成、核心对象各管什么、"声明式"到底是什么。[K8s网络与存储](K8s网络与存储.md)接网络与卷，[K8s生产排障手册](K8s生产排障手册.md)接线上救火。面试口径：能徒手画出架构图、讲清调和循环的候选人，比背对象字段的多一个层级。

一、整体架构：控制面与节点
------

```
                +---------------------- 控制面 Control Plane ----------------------+
                |                                                                  |
                |   kube-apiserver  <======= 集群所有读写的唯一入口                 |
                |      ^     ^                 （REST + 鉴权 + 准入控制）           |
                |      |     |                       |                            |
                |      |     +------------------->   etcd                         |
                |      |                （期望/观察状态的唯一事实源，分布式 KV）      |
                |      |                                                           |
                |  kube-controller-manager            kube-scheduler               |
                |  （副本/节点/端点等一堆调和回路）     （为未落地的 Pod 挑节点）      |
                +------|----------------------------------------------------------+
                       |   watch / list 双向长连接
         +-------------+---------------------+-----------------+
         v                                   v                 v
  +--------------+                   +--------------+   +--------------+
  |    node-1    |                   |    node-2    |   |    node-3    |
  |   kubelet    |                   |   kubelet    |   |   kubelet    |
  |  kube-proxy  |                   |  kube-proxy  |   |  kube-proxy  |
  | 容器运行时    |                   | 容器运行时    |   | 容器运行时    |
  |  +--------+  |                   |  +--------+  |   |  +--------+  |
  |  | Pod A  |  |                   |  | Pod B  |  |   |  | Pod C  |  |
  |  +--------+  |                   |  +--------+  |   |  +--------+  |
  +--------------+                   +--------------+   +--------------+
```

控制面组件各一句话：

* **kube-apiserver**：唯一入口。kubectl、控制回路、kubelet 全只跟它说话；自身无状态，可多副本横向扩。
* **etcd**：只存"期望状态 + 当前状态"。apiserver 是它唯一客户端——绕过 apiserver 直改 etcd 等于绕过全部校验与审计。
* **kube-scheduler**：只做一件事——watch 尚未绑定节点的 Pod，过滤 + 打分选一个 node，写回 `spec.nodeName`。它不创建容器。
* **kube-controller-manager**：几十个控制回路进程合体（Deployment/Node/Endpoint…），每个回路都在"对比期望与实际，补差"。

节点侧两个组件：

* **kubelet**：节点代理。watch 归属本节点的 Pod，指挥容器运行时（containerd）拉起容器，持续上报状态与健康。
* **kube-proxy**：把 Service 虚拟 IP 的转发规则写进本机（iptables 或 ipvs），让 ClusterIP 在任何节点可达。

高频追问：**控制面全挂，已运行的 Pod 会死吗？** 不会——工作负载由 kubelet 本地执行，控制面只负责"变更"；但调度、扩缩容、自愈全部停摆。所以生产要求 apiserver 多副本 + etcd 多成员。

二、核心对象职责一览
------

| 对象 | 一句话职责 | 关键机制 |
| --- | --- | --- |
| Pod | 最小调度单元，一或多个共享网络/IPC/存储卷的容器 | sidecar 模式；Pod 不重启则 IP 不变，重建即换 |
| ReplicaSet | 保住"副本数"这个不变量 | `replicas` + label selector |
| Deployment | 管 ReplicaSet，提供滚动更新与回滚 | 新 RS 涨、旧 RS 降，全程可 `rollout undo` |
| Service | 给一组动态变化的 Pod 一个稳定入口（虚拟 IP + DNS 名） | Endpoints 按 selector 跟踪 Pod |
| Ingress | 七层入口：按 Host/Path 把外部流量路由到 Service | 只是规则，需 Ingress Controller 落地 |
| ConfigMap/Secret | 配置与敏感配置，与镜像解耦 | 挂文件或注入环境变量 |

层次关系一句话：**Deployment 管"几个副本"，Service 管"怎么找到它们"，Ingress 管"外部怎么进来"**。面试画图就按这条链画：Ingress → Service → Pod（label selector 串联）。

三、声明式与调和循环
------

声明式 API 的本质：**你只描述终态（"我要 3 个副本跑 v2 镜像"），不描述步骤**。命令式（`docker run` × 3）描述动作，挂了就没人管；声明式把终态存在 etcd 里，由调和循环（reconcile loop）持续维护：

```
 kubectl apply（期望状态 replicas: 3）
      |
      v
 etcd 落盘 Desired = 3 ──> apiserver 广播变更（watch）
      |
      v
 Deployment 控制器：对比 期望副本数 vs 实际 ReplicaSet
      |  差 1 个
      v
 创建 Pod（只有 spec，无 node）──> Scheduler 绑定 node-2
      |
      v
 node-2 kubelet 拉起容器，回传状态 Observed = 3
      |
      v
 期望 == 实际，回路收敛（安静等待下一次偏差）
```

三条容易被追问的性质：

* **水平触发而非边缘触发**：控制器不只响应"变更事件"，而是周期性比对全量状态。错过一次事件也能在下轮比对中补上，这是自愈能力的来源；
* **幂等收敛**：任何时刻中断（控制器重启、节点宕机），回路从当前状态继续向终态收敛，不需要人工"续跑"；
* **一切都是对象**：滚动更新 = Deployment 控制器造一个新 ReplicaSet 再缩旧的，而不是 in-place 改容器。不可变基础设施落到对象模型上。

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: health-service
spec:
  replicas: 3                     # 期望状态的全部秘密就这一行
  selector:
    matchLabels: { app: health }  # Service 也靠这个 selector 找到这批 Pod
  template:
    metadata:
      labels: { app: health }
    spec:
      containers:
        - name: app
          image: registry/health-service:1.8.0   # 永远用不可变 tag，不用 latest
          resources:
            requests: { cpu: 250m, memory: 512Mi }
            limits:   { cpu: "1",  memory: 1Gi  }
```

四、kubectl 排查链
------

按"从外到内"的固定顺序走，不要一上来就 `exec` 进容器翻日志：

```
 ① 状态总览   kubectl get pods -o wide        # Pending/CrashLoop 在哪台节点
      |
 ② 事件为证   kubectl describe pod <name>     # Events 段是金矿：调度失败、镜像拉取失败、探针杀进程
      |
 ③ 应用日志   kubectl logs <name> [--previous]  # --previous 看上一次崩溃前的日志
      |
 ④ 进入验证   kubectl exec -it <name> -- sh   # 容器内 curl 依赖、看环境变量
      |
 ⑤ 集群事件   kubectl get events --sort-by=.lastTimestamp
      |
 ⑥ 资源水位   kubectl top pod / node         # 结合 requests 判断是否挤压
```

两条经验。**describe 的 Events 段先于 logs**——调度类问题（Pending、镜像拉取失败）应用日志里什么都没有。**`--previous` 是 CrashLoopBackOff 的钥匙**——当前容器刚重启，`logs` 只能看到重启后的空日志。

排查手法按对象分层记忆：Pod 层 `describe/logs/exec`；网络层 `get svc/endpoints`（Endpoints 为空 = selector 选不到 Pod，流量必 503）；集群层 `get nodes` + `top`。

追问链
------

> **面试官：Deployment 滚动更新时流量会断吗？怎么保证不断？**

默认可能断：新 Pod 未就绪就接流量，或旧 Pod 直接杀掉在途请求。两道保险：readinessProbe 保证"没就绪不进 Endpoints"；`terminationGracePeriodSeconds` + preStop 钩子保证"先摘流量、等在途请求跑完再退"。生产必须两件都配，缺一个就有小概率 502。

> **面试官：etcd 挂了 apiserver 还能读吗？**

不能。apiserver 无状态、全靠 etcd 存数据，etcd 不可用则读写全停（缓存里可能短暂答一部分 list）。这也是 etcd 要 3/5 成员奇数部署的原因——Raft 多数派可用。

> **面试官：为什么说"K8s 一切都是控制回路"，举个例子？**

Endpoints 控制器：watch Service 和 Pod 两边，Pod 没就绪就从 Endpoints 摘掉、就绪了加回来；Node 控制器：节点 NotReady 超时后驱逐 Pod。Operator 把这个模式开放给业务——CRD 定义新对象，自定义控制器负责调和，本质和内置控制器一模一样。

> **面试官：Service 和 Endpoints 的关系？**

Service 是"稳定入口的声明"，Endpoints 是"实际转发目标列表"（一组 Pod IP:port）。kube-proxy watch Endpoints 变化刷写节点转发规则。排查 Service 不通的第一步永远是 `kubectl get ep`——空 Endpoints 说明 selector/label 对不上或 Pod 全部未就绪，和网络无关。

面试官视角小结
------

1. **架构图必画得出**。控制面四件套：apiserver 唯一入口、etcd 唯一存储、scheduler 只管绑定、controller-manager 一堆回路。节点两件套：kubelet 落地、kube-proxy 写规则；
2. **对象层次一句话**：Deployment 管副本、Service 管寻址、Ingress 管入口，label selector 串联；
3. **声明式三性质**：终态描述、水平触发（错过事件也能补）、幂等收敛（自愈的来源）。滚动更新 = 新旧 ReplicaSet 一涨一缩，不是原地改；
4. **排查链背成肌肉记忆**：get → describe(Events) → logs(--previous) → exec → events → top；Service 不通先查 Endpoints；
5. **控制面挂了业务不死但停摆**——一句话同时答可用性与高可用设计。

> 2026-10 新增（留白补齐）。姊妹篇：[K8s网络与存储](K8s网络与存储.md)、[K8s生产排障手册](K8s生产排障手册.md)。
