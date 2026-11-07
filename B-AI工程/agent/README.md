Agent 系列导读（书籍式目录：从零到一做企业级 Agent）
====

> 本目录按「范式 → 手写构建 → 专项深潜 → 评测上线 → 编排选型」的渐进路径组织成两章八篇：
> **第一章 从零到一**（范式全景 → 手写最小 Agent → 评测与可观测 → 多智能体编排与框架选型）是体系主线；
> **第二章 生产深潜**（ReAct/LangGraph、MCP、结构化输出、身份链与工具安全）是 ai-service 实战反哺的专项篇。
> 两章互链：主线篇引用深潜篇的机制，深潜篇引用主线篇的定位。

## 第一章 · 从零到一（体系主线）

| # | 篇目 | 回答的问题 |
|---|---|---|
| 1 | [Agent 范式全景-workflow与agent的边界](Agent范式全景-workflow与agent的边界.md) | 该不该用 agent？五种 workflow 模式怎么选？ |
| 2 | [从零手写最小Agent-无框架的模型循环](从零手写最小Agent-无框架的模型循环.md) | agent 的本质——60 行无框架循环跑通 |
| 3 | [Agent评测与可观测-guards与成本](Agent评测与可观测-guards与成本.md) | 从"能跑"到"敢上线"：三层评测/四道护栏/成本三巨头 |
| 4 | [多智能体编排与框架选型](多智能体编排与框架选型.md) | 什么时候多代理、Supervisor 拓扑、框架决策表 |

## 第二章 · 生产深潜（ai-service 实战反哺）

| # | 篇目 | 回答的问题 |
|---|---|---|
| 5 | [ReAct与LangGraph工程实践](ReAct与LangGraph工程实践.md) | ReAct 图怎么落生产：状态机/记忆隔离/SSE 流式 |
| 6 | [MCP协议与企业落地](MCP协议与企业落地.md) | 工具生态协议与企业落地四件套 |
| 7 | [结构化输出工程-Pydantic与LLM](结构化输出工程-Pydantic与LLM.md) | 模型输出怎么变成下游敢用的强类型契约 |
| 8 | [智能体的身份链与工具安全](智能体的身份链与工具安全.md) | 身份链"模型无法伪造"设计与工具权限边界 |

## 推荐阅读路径

* **体系学习**：第一章 1→4 顺序读（每篇文末链下一章），再进第二章按需深潜；
* **生产救火**：按问题直达——输出不可靠→篇 7，权限→篇 8，不收敛→篇 4；
* **面试冲刺**：篇 1 的五种模式 + 篇 3 的三层评测 + 各篇追问链。

## 参考资源（学习整理的公开出处）

* [Building Effective Agents — Anthropic](https://www.anthropic.com/research/building-effective-agents)（五种 workflow 模式的业界共识）
* [Demystifying evals for AI agents — Anthropic Engineering](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents)（agent 评测方法论）
* [A Practical Guide to Building Agents — OpenAI](https://openai.com/business/guides-and-resources/a-practical-guide-to-building-ai-agents)（[中文译版 PDF](https://assets.jimmysong.io/books/a-practical-guide-to-building-agents-zh.pdf)）
* [深入理解AI Agent：设计原理与工程实践 — 李博杰](https://github.com/bojieli/ai-agent-book)（Agent = LLM + 上下文 + 工具 十章开源）
* [从零开始构建智能体 — Datawhale hello-agents](https://github.com/datawhalechina/hello-agents)（中文开源动手教程）
