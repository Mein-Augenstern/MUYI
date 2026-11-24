# D 轨 · 云原生

> 实况 **6 篇**。起步轨：容器与 K8s 为主战场。读 K8s 源码的 informer/workqueue 模式，
> 是 [Go 并发三件套](../A-后端内核/go/从Java视角看Go-并发三件套-goroutine-channel-context.md)的教科书应用——两轨互为犄角。

## 目录

| 目录 | 篇数 | 内容 |
|---|---|---|
| [容器技术/](容器技术/) | 5 | Docker 镜像分层；K8s 核心对象、网络与存储、生产排障、版本新基线 |
| Serverless | 1 | [Serverless：形态、研究与未来十年](Serverless/Serverless：形态、研究与未来十年.md) |

## 推荐阅读路径

* **上手**：[Dockerfile与镜像分层](容器技术/Docker/Dockerfile与镜像分层.md) → [K8s核心对象与架构](容器技术/Kubernetes/K8s核心对象与架构.md) → [K8s网络与存储](容器技术/Kubernetes/K8s网络与存储.md)；
* **生产救火**：[K8s生产排障手册](容器技术/Kubernetes/K8s生产排障手册.md)——滚动更新丢流量、探针误杀、preStop 与终止 grace 的手序问题，均来自真实事故复盘；
* **跟进版本**：[K8s新基线-sidecar-DRA与原地扩缩容](容器技术/Kubernetes/K8s新基线-sidecar-DRA与原地扩缩容.md)。

## 写作规范

1. 云原生轨与 Go 轨联动：K8s 机制篇讲"为什么这样设计"时回链 Go 并发原语篇。
