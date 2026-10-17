K8s 生产排障手册
====

看在前面
------

接[《K8s核心对象与架构》](K8s核心对象与架构.md)与[《K8s网络与存储》](K8s网络与存储.md)。排障题是云原生面试的实战题：面试官丢一个现象（CrashLoopBackOff / Pending / OOMKilled / 流量 503），看你能不能按决策树一步步收敛。这篇就按"现象 → 第一条命令 → 分叉"来写。

一、总决策树：从现象到根因
------

```
 kubectl get pods（异常状态入口）
 |
 +-- Pending：调度器没把它放上去（容器根本没创建）
 |     `-> kubectl describe pod <p> 看 Events
 |          +-- FailedScheduling: Insufficient cpu/memory
 |          |      -> requests 超过节点可分配量：降 requests / 扩节点 / 清理占用
 |          +-- node(s) didn't match PodAffinity / taints
 |          |      -> 亲和性写错 或 污点没容忍：核对 labels / tolerations
 |          +-- unbound immediate PersistentVolumeClaims
 |                 -> PVC 没绑上 PV：StorageClass 名字错 / 容量/访问模式不匹配 / 无 provisioner
 |
 +-- ContainerCreating 卡住：调度成功但起不来
 |     `-> describe 看 Events
 |          +-- ImagePullBackOff：tag 写错 / 私仓凭证（imagePullSecrets）缺失
 |          +-- 挂载卷失败 / CNI 分配 IP 失败（node 级问题，看 kubelet 日志）
 |
 +-- CrashLoopBackOff：起了就崩，kubelet 按指数退避重试（10s/20s/40s...上限 5min）
 |     `-> kubectl logs <p> --previous（上一条命看崩溃前日志）
 |          +-- exit 137 + reason OOMKilled：内存超 limit 被内核杀 -> 见三、QoS
 |          +-- exit 137 无 OOM：liveness 探针失败被杀 -> describe 里有探针 kill 事件
 |          +-- exit 1 / 应用异常栈：配置错、依赖连不上、启动顺序假设被打破
 |          +-- 秒退且无日志：ENTRYPOINT 命令写错 / 应用日志写文件而非 stdout
 |
 +-- Running 但没流量 / 一直被重启
       `-> describe 看 Liveness/Readiness 事件
            +-- Readiness 失败：探针路径或端口错；依赖未就绪（DB 没连上就不该 ready）
            +-- 启动慢被 Liveness 杀：慢应用缺 startupProbe -> 见四、探针
```

使用纪律：**describe 的 Events 段永远先看**——调度、镜像、探针、卷的问题全在里面，且带 Last State 的 exit code；应用内问题才轮到 `logs --previous`。

二、三大高频故障逐个击破
------

**Pending（调度不出去）**。本质是"Pod 的 requests 与节点剩余可分配资源的匹配失败"。注意 kubelet 预留（kube-reserved/system-reserved）之后节点可分配量比总量小，`kubectl describe node` 的 Allocated resources 段才是真相。排查顺序：describe Events → 节点资源水位 → 亲和性/污点 → PVC 绑定。

**CrashLoopBackOff（起来了就崩）**。三个 exit code 记牢：**137 = 128+9（SIGKILL）**，被谁杀要看 reason——OOMKilled 是内核（内存），否则是 kubelet（探针）；**1 = 应用自身报错退出**，日志里通常有栈；**0 但反复重启**是主进程跑完就退（前台命令写成了后台命令）。另外退避是指数的——改完配置后 Pod 不会立刻重试，可以 `kubectl rollout restart` 主动触发。

**OOMKilled（内存爆了）**。链条固定：JVM/运行时按容器 limit 感知内存失败（JVM 老版本按宿主机内存算堆，8G 宿机 512Mi 容器必被杀）→ 进程超 limit → cgroup 内存耗尽 → 内核 OOM killer 选中它 → kubelet 记 OOMKilled 并重建。修复两选一：调大 limit；让运行时尊重容器内存（JDK 8u191+/JDK 10+ 默认容器感知，`-XX:MaxRAMPercentage=70` 是现代写法）。

三、资源限额与 QoS：驱逐顺序的真相
------

requests 是调度的承诺，limits 是运行的天花板。两者怎么配，直接决定节点内存吃紧时**谁先被驱逐**：

| QoS 等级 | 判定条件 | 驱逐顺序 | 定位 |
| --- | --- | --- | --- |
| Guaranteed | 每个容器 requests == limits（cpu 可不等，内存必须等） | 最后 | 核心在线服务 |
| Burstable | 有 requests 但不满足 Guaranteed | 中间，按"超出 request 的比例"排序 | 一般业务 |
| BestEffort | 完全不配 | 最先 | 无状态批处理、可丢任务 |

```yaml
resources:
  requests: { cpu: 500m, memory: 512Mi }   # 调度依据：节点必须"付得起"这份承诺
  limits:   { cpu: "1",  memory: 512Mi }   # 内存 requests==limits => Guaranteed
```

再区分两个容易混淆的"被动刀"：**驱逐（Eviction）** 是 kubelet 在节点内存/磁盘压力下的自保动作，按上表顺序挑牺牲者，Pod 会被重建（`STATUS` 短暂 Evicted）；**抢占（Preemption）** 是调度器为高优先级 Pod 腾地方，主动删除低优先级 Pod（PriorityClass 决定）。排障时看到 Pod 消失重建，先 `get events` 分清是哪一种——治理手段完全不同（前者治节点水位，后者治优先级设计）。

两条实践：**在线服务一律配 Guaranteed（至少内存等额）**——不是为性能，是为驱逐顺序；**CPU limit 慎配过紧**——CPU 是可压缩资源，超限只节流（throttling，表现为毛刺延迟）不杀进程，配得比峰值低会造成周期性卡顿，很多团队干脆只配 cpu requests。

四、探针三件套设计
------

| 探针 | 失败后果 | 回答的问题 | 典型配置 |
| --- | --- | --- | --- |
| startupProbe | 不启动另两个探针 | "起来了吗" | 慢启动应用：failureThreshold×period 覆盖最坏启动时间 |
| livenessProbe | **重启容器** | "活着吗（死锁/僵住）" | 宽松：检查进程级自检端点 |
| readinessProbe | **摘除流量**（不重启） | "能接流量吗" | 严格：带上关键依赖（DB 连通） |

```
 startup 通过 ──> liveness + readiness 开始工作
                  |                    |
                  失败：杀掉重启        失败：从 Endpoints 摘除（Service 不再转发）
                  （进程级自愈）        （保护用户，不保护自己）
```

三条设计军规：**liveness 不要检查下游依赖**——DB 抖一下就把全部实例连环重启（雪崩放大器），依赖健康归 readiness 管；**readiness 失败前先想"摘掉实例集群还剩几成容量"**——maxUnavailable 配不好会同时摘光；**慢启动必须上 startupProbe**——否则只能把 liveness 的 initialDelaySuccessive 拉很大，快的实例陪着慢的等。

五、命令速查与一次真实时间线
------

把决策树压成一张可背的速查表（面试口述 = 按这张表报命令）：

| 现象 | 第一条命令 | 第二条命令 | 关键看点 |
| --- | --- | --- | --- |
| Pending | `describe pod` | `describe node` | Events 的 FailedScheduling 原文 |
| ImagePullBackOff | `describe pod` | - | 镜像 tag / imagePullSecrets |
| CrashLoopBackOff | `logs --previous` | `get pod -o yaml` | exit code、Last State |
| OOMKilled | `describe pod` | `top pod` | reason 与 limit 差值 |
| 503 / 无流量 | `get ep` | `get endpointslices` | Endpoints 是否为空 |
| NotReady 反复 | `describe pod` | `get events` | 探针失败事件 |

一次真实排查的时间线（口述案例模板，把现象-动作-结论按分钟排开）：

```
 T+0   告警：health-service 可用率 97%，间歇 503
 T+2   kubectl get pods：3/3 Running，但 1 个 READY 0/1   ← 不是挂，是"隐身"
 T+3   describe 该 Pod：Readiness probe failed: connection refused 8081
 T+4   根因线索一：探针打的是 8081，容器监听 8080        ← 改配置时端口漂移
 T+5   logs --previous：正常启动栈，无异常               ← 应用无辜
 T+7   修复：探针 port 改 8080 + rollout restart
 T+9   Endpoints 恢复 3 个地址，可用率回升
 复盘：探针端口与容器端口写两处，收敛为同一 env 变量引用
```

这个模板的价值在最后一行：**复盘不写"下次小心"，写"结构上怎么让它不可能再发生"**（配置收敛、端口单源），这是 SRE 与运维的分界。

追问链
------

> **面试官：CrashLoopBackOff 里 logs 是空的，怎么回事？**

三种可能：日志写到文件而非 stdout（K8s 只收集 stdout/stderr，改打 stdout 或挂 sidecar 收文件）；崩溃发生在 entrypoint 阶段（命令/参数错，`--previous` 也无输出，靠 describe 的 exit code 定位）；应用缓冲未刷新就死了（行缓冲 vs 全缓冲差异）。先 describe 拿 exit code，再决定往哪查。

> **面试官：Pod 显示 OOMKilled 但应用日志说堆内存很充裕，为什么？**

被杀的是 cgroup 用量而非 JVM 堆：堆外（直接内存、元空间、线程栈、页缓存）+ 容器 limit 的账没算平。JVM 总用量 ≈ 堆 + Metaspace + DirectBuffer + 线程数×栈 + GC 开销。解法是压低 MaxRAMPercentage 给堆外留量（70% 是常见起点），并让 limit 覆盖真实峰值。

> **面试官：节点内存紧张时，K8s 怎么决定驱逐哪个 Pod？**

先按 QoS 等级（BestEffort 先走），同级再按"实际用量超出 requests 的比例"排序——超得最多的先被驱逐。所以 requests 不仅影响调度，还影响死亡顺序：完全没配的 Pod 等于宣布"我先死"。

> **面试官：readiness 一直失败会发生什么？replicas 还是 3 吗？**

是。readiness 只摘流量不动副本数，Deployment 看到的是"3 个副本都在"，不会补新的——这是它和 CrashLoop 的关键区别：CrashLoop 触发重建，NotReady 只是隐身。若所有副本 NotReady，Service 的 Endpoints 清空，整体 503。

面试官视角小结
------

1. **决策树先背框架**：Pending 查调度（describe Events：资源/亲和/PVC）、CrashLoop 查上一次日志（`--previous`）+ exit code（137=被杀，1=应用错，0=跑完就退）、OOMKilled 查运行时对容器的内存感知；
2. **QoS 三档与驱逐顺序**：Guaranteed（内存等额）给在线服务，BestEffort 等于"先死我"；CPU 超限是节流不是杀；
3. **探针分工一句话**：startup 管"何时开始查"，liveness 管"要不要重启"（别查下游），readiness 管"要不要流量"（带上依赖）；
4. **两个反直觉点**：liveness 查依赖 = 雪崩放大器；全副本 NotReady 时副本数不变但整体 503；
5. **排障纪律**：describe Events 永远第一步，logs 第二步，exec 最后——顺序反了就是在容器里瞎逛；
6. **复盘写结构不写态度**：探针端口漂移的修复是"端口收敛为同一 env 变量引用"，不是"下次仔细点"。

> 2026-10 新增（留白补齐）。姊妹篇：[K8s核心对象与架构](K8s核心对象与架构.md)、[K8s网络与存储](K8s网络与存储.md)。
