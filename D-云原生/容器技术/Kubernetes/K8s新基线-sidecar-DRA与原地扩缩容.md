K8s 新基线：原生 sidecar、DRA 与原地扩缩容
====

接 [K8s核心对象与架构](./K8s核心对象与架构.md)。K8s 当前最新 1.37（2026-08-26 发布，已有 1.37.1 补丁；EKS 2026-10-02 跟进支持）。一句话定位：**1.33–1.35 落地的三件事——原生 sidecar、原地扩缩容、DRA——把 K8s 从"容器编排器"推进成"AI 负载基座"**。面试价值：这是 2026 年云原生面试的新分层题。考的不是背版本号，而是"这三个特性各自补上了 AI 推理负载的哪块短板"。

1 三年特性主线：从"能跑"到"AI 负载基座"
------

时间线（只列转正节点）：

| 版本 | 特性 | 状态 | 补的短板 |
| ---- | ---- | ---- | ---- |
| 1.33 | 原生 sidecar 容器 | stable | 伴生组件的启动/终止顺序保证 |
| 1.33 → 1.35 | 原地扩缩容（in-place resize） | beta → GA | 资源在线调整不重建 Pod |
| 1.34 | DRA 动态资源分配 | GA | GPU/MIG 等硬件的结构化调度 |
| 1.37（2026-08-26） | 当前最新 | — | — |

主线一句话：**传统负载要的是"把容器放上去"，AI 负载要的是"把算力管起来"**。GPU 怎么精细分（DRA）、资源怎么在线调（原地 resize）、伴生组件怎么有序跑（原生 sidecar）。三块拼图合成推理服务的基座。逐个展开。

2 原生 sidecar：initContainer + restartPolicy: Always
------

写法只有一行差别：把 sidecar 写进 `initContainers`，并给它 `restartPolicy: Always`：

```yaml
spec:
  initContainers:
  - name: log-agent
    image: fluent-bit:3.x
    restartPolicy: Always          # <- 就这一行，把 init 容器变成 sidecar
    volumeMounts:
    - { name: varlog, mountPath: /var/log }
  containers:
  - name: app
    image: app:1.0
```

语义上它横跨两个世界：**像 init 容器一样先于主容器启动，又像普通容器一样活到 Pod 结束**。换来两个顺序保证：

* **启动顺序**：init 容器按序启动，sidecar（若配了 startupProbe）启动成功后，后续容器才开始。**日志 agent、服务网格 proxy 天然"先于业务就绪"**，不再靠 postStart sleep 赌时序；
* **终止顺序**：Pod 终止按逆序——先停普通容器，**再停 sidecar**。日志 agent 能把业务容器最后几行日志采完再退，这是塞在 containers 里的旧 sidecar 做不到的。

与旧姿势对照：把 agent 硬塞 containers[0] 再用启动 hack 对齐时序，在赌启动顺序，且没有任何终止顺序保证。PostStart 钩子并不保证先于容器内进程生效。

**与 Job 的坑**（两方向都要记）：

* 旧姿势：Job 的完成判定看主容器退出码，而 containers 里的 sidecar 永不退出，**Job 永远完不成**，只能外挂钩子杀 sidecar。迁移为原生 sidecar 后，主容器完成即 Job 成功，sidecar 收 SIGTERM 收尾；
* 新姿势的坑：**sidecar 必须正确响应 SIGTERM**，否则 Pod 卡在 Terminating，最终被 terminationGracePeriodSeconds 强杀丢尾日志。

3 原地扩缩容：改 resources 不重建 Pod
------

1.33 进入 beta（InPlacePodVerticalScaling），**1.35 GA**。能力：直接修改**运行中 Pod** 的 `resources.requests/limits`（CPU/内存），kubelet 在线调整 cgroup——Pod 不重建、进程不重启、本地缓存与长连接不丢。

先纠正一个高频混淆：**别与 DRA 的 `resources.claims` 弄混**——claims 声明的是硬件设备（下节）。本节改的是 CPU/内存这两个计算资源字段。

```yaml
containers:
- name: inference
  resources:
    requests: { cpu: "4", memory: 16Gi }
    limits:   { cpu: "8", memory: 16Gi }
  resizePolicy:                        # 改不动时怎么办（按资源粒度声明）
  - { resourceName: cpu,    restartPolicy: NotRequired }      # 尽量原地
  - { resourceName: memory, restartPolicy: RestartContainer } # 必要时重启容器
```

改法就是改活 Pod 的 resources 字段（`kubectl edit` 或 `kubectl patch --subresource=resize`），kubelet 在 Pod status 里回报 resize 进度。边界条件：

* 只能 resize CPU/内存；改动其他字段仍会触发重建；requests/limits 的变化还可能改变 Pod 的 QoS 等级；
* CPU 双向灵活；**内存下调要求当前实际用量低于新值**，否则失败；
* resize 失败留事件——排障入口仍是 [K8s生产排障手册](./K8s生产排障手册.md) 的 describe Events 纪律。

与 HPA/VPA 的协同：

* **HPA 管水平、resize 管垂直**，互不冲突：突发流量用 HPA 加副本，单实例临时抬 CPU 用 resize；
* VPA 传统模式是"改推荐值 → 驱逐重建"，代价是断连接。resize GA 后 VPA 有了原地路线（InPlaceOrRecreate 一类模式），推荐值可直接热生效；
* 对大模型推理这种**启动代价极高**（加载权重动辄数分钟）的负载，"不重建"本身就是最大的收益。

4 DRA：GPU 从"整数计数"到"结构化调度"
------

旧模型里 GPU 是 extended resource（`nvidia.com/gpu: 1`）：device plugin 报数、调度器做整数减法——**表达不了 MIG 切片、拓扑亲和、按显存/算力细分配**。DRA（Dynamic Resource Allocation）在 1.34 GA，用三个对象重构供给关系：

| 对象 | 谁写 | 表达什么 | 类比 |
| ---- | ---- | ---- | ---- |
| DeviceClass | 管理员 | 一类设备怎么给（驱动参数、选择器） | StorageClass |
| ResourceSlice | 节点驱动 | 本节点有哪些设备/切片（供给清单） | PV 的容量供给 |
| ResourceClaim | 工作负载 | 要几个、要什么规格（需求声明） | PVC |

```yaml
# 工作负载侧精简示意（API 组 resource.k8s.io，字段随版本以官方 API 为准）
kind: ResourceClaim
spec:
  devices:
    requests:
    - name: gpu
      exactly:
        count: 1
        deviceClassName: mig-1g-10gb   # 要一个 1g.10gb 的 MIG 切片
---
spec:
  resourceClaims:
  - name: gpu
    resourceClaimName: gpu-claim
  containers:
  - name: inference
    resources:
      claims:
      - name: gpu        # <- 与 requests/limits 平行的新维度
```

GPU 整卡与 MIG 切片：一张卡可按 MIG 切成多个硬件隔离实例（如 7 个 1g.10gb），DRA 能按切片粒度调度。**对推理服务多租户的意义很直接**。小模型实例不再被迫整卡独占，一张卡可以跑多个租户/多个实例。GPU 装载密度上来、单位 token 成本下去。配合拓扑约束还能把多卡张量并行的实例钉在同一 NVLink 域内。

5 AI 推理负载特性组合：一份清单式示例
------

把三件事拼到同一个工作负载上（精简骨架）：

```yaml
apiVersion: apps/v1
kind: Deployment
spec:
  resourceClaimTemplates:            # (3) DRA：每个 Pod 一份 GPU 切片声明
  - metadata: { name: gpu }
    spec:
      devices:
        requests:
        - name: gpu
          exactly: { count: 1, deviceClassName: mig-1g-10gb }
  template:
    spec:
      initContainers:
      - name: metrics-agent          # (1) 原生 sidecar：先起、后停
        restartPolicy: Always
      containers:
      - name: vllm
        resources:
          requests: { cpu: "4", memory: 16Gi }
          limits:   { cpu: "8", memory: 16Gi }
          claims: [{ name: gpu }]    # (2) DRA：消费 GPU 切片
        resizePolicy:                # (4) 原地扩缩容：在线抬配额不重建
        - { resourceName: cpu,    restartPolicy: NotRequired }
        - { resourceName: memory, restartPolicy: NotRequired }
```

读法：sidecar 保证指标/日志采集先于引擎就绪。DRA 保证每个实例拿到明确规格的切片而不是抢整卡。resize 保证压测或高峰时在线抬 CPU/内存而不触发重建。权重加载要数分钟的推理容器，重建一次就是一次可用性事件。网络与存储的配套（Service/Ingress、模型权重的 PVC 分发）见 [K8s网络与存储](./K8s网络与存储.md)。

6 升级节奏建议与版本偏差策略
------

* **按特性倒推最低版本**：原生 sidecar >= 1.33；DRA GA >= 1.34；原地 resize GA >= 1.35；三特性组合整体要求 **>= 1.35**；
* **生产不必追最新**：1.37 是 2026-08-26 才发布的，托管平台刚跟上（EKS 2026-10-02 支持 1.37）。生产落在 N-1/N-2 更稳，新特性先在非核心节点池灰度验证；
* **偏差纪律**：禁止 kubelet 新于 kube-apiserver，只允许等于或落后（落后窗口近年已放宽到数个小版本）。滚动升级永远"先控制面、后节点池"，节点分批推进；
* 每版 release notes 盯三样：API 弃用、默认值变化、特性 gate 默认开关。这三样才是升级事故的常客，新特性反而不是。

面试追问链
------

* 原生 sidecar 怎么写？→ initContainers + restartPolicy: Always；为什么这么设计？→ 复用"容器按序启动"的既有语义，不动 containers 主列表与既有工作流；
* 与塞在 containers 里的旧 sidecar 差在哪？→ "先于业务启动、晚于业务终止"的顺序保证；Job 场景还有什么坑？→ 旧姿势 Job 永不完成；新姿势 sidecar 必须响应 SIGTERM，否则卡 Terminating；
* Pod 删除时的终止顺序？→ 逆序：普通容器先停，sidecar 最后停——日志 agent 采完尾日志再退；
* 原地 resize 改哪个字段？→ 运行中 Pod 的 resources.requests/limits，配 resizePolicy；什么做不到？→ 只限 CPU/内存，内存下调不能低于当前用量，其他字段改动仍触发重建；
* resize 会取代 HPA/VPA 吗？→ 不会：水平归 HPA，垂直才轮到 resize；VPA 借它从"驱逐重建"变"原地生效"；
* DRA 三个对象分别谁写？→ DeviceClass 管理员、ResourceSlice 节点驱动、ResourceClaim 工作负载；比 extended resource 强在哪？→ MIG 切片、拓扑、结构化属性，不再只会整数计数；
* 这三个特性分别哪版转正？→ sidecar 1.33、DRA 1.34、原地 resize 1.35；当前最新？→ 1.37（2026-08-26）；
* kubelet 能比 kube-apiserver 新吗？→ 不能，永远只允许等于或落后。

面试官视角小结
------

及格线是能说出"sidecar 转正了、GPU 调度有新 API 了"。能讲清 sidecar 的双向顺序保证、resize 的字段边界（只动 CPU/内存）与 QoS 连带影响、DRA 三对象的供需关系。能讲到这一层，说明真用过。能串成"AI 推理负载基座三件套"——可观测采集有序（sidecar）、算力精细供给（DRA）、资源在线调整（resize）。并说出"推理容器重建一次等于一次可用性事件"这种成本直觉，才是架构级理解。

反直觉点记两条：Job 完成判定只看主容器（sidecar 不挡路是特性不是巧合）。内存原地缩容有"不能低于当前用量"的硬边界。基础不牢先补 [K8s核心对象与架构](./K8s核心对象与架构.md)，实战排障看 [K8s生产排障手册](./K8s生产排障手册.md)。

> 2026-10 新增（2026 技术栈补全）。
