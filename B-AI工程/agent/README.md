Agent 系列导读（书籍式目录：从零到一做企业级 Agent）
====

> 本目录按「范式 → 手写构建 → 专项深潜 → 评测上线 → 编排选型」的渐进路径组织成两章九篇：
> **第一章 从零到一**（范式全景 → 手写最小 Agent → 评测与可观测 → 多智能体编排与框架选型）是体系主线；
> **第二章 生产深潜**（ReAct/LangGraph、MCP、结构化输出、身份链与工具安全、人机协同）以 ai-service 实战反哺为主，含公开好文的学习整理篇。
> 两章互链：主线篇引用深潜篇的机制，深潜篇引用主线篇的定位。
> **延伸系列**（各自独立目录，是本目录两章的下一站）：
> * [上下文工程](../上下文工程/)：窗口里该放什么、放多久、花多少钱。单次调用的预算、循环里的四板斧、脚手架的分层供给、跨会话的记忆，都在那里；
> * [脚手架设计](../脚手架设计/)：以 Claude Code 为主样本，回答"怎么造一个像它一样好用的智能体"。

## 第一章 · 从零到一（体系主线）

| # | 篇目 | 回答的问题 |
|---|---|---|
| 1 | [Agent 范式全景-workflow与agent的边界](Agent范式全景-workflow与agent的边界.md) | 该不该用 agent？五种 workflow 模式怎么选？ |
| 2 | [从零手写最小Agent-无框架的模型循环](从零手写最小Agent-无框架的模型循环.md) | agent 的本质——60 行无框架循环跑通 |
| 3 | [Agent评测与可观测-guards与成本](Agent评测与可观测-guards与成本.md) | 从"能跑"到"敢上线"：三层评测/四道护栏/成本三巨头 |
| 4 | [多智能体编排与框架选型](多智能体编排与框架选型.md) | 什么时候多代理、Supervisor 拓扑、框架决策表 |

## 第二章 · 生产深潜（ai-service 实战反哺与学习整理）

| # | 篇目 | 回答的问题 |
|---|---|---|
| 5 | [ReAct与LangGraph工程实践](ReAct与LangGraph工程实践.md) | ReAct 图怎么落生产：状态机/记忆隔离/SSE 流式 |
| 6 | [MCP协议与企业落地](MCP协议与企业落地.md) | 工具生态协议与企业落地四件套（协议入门与动手见[脚手架设计系列第 6 篇](../脚手架设计/MCP协议入门-原理、安全与第一个Server.md)） |
| 7 | [结构化输出工程-Pydantic与LLM](结构化输出工程-Pydantic与LLM.md) | 模型输出怎么变成下游敢用的强类型契约 |
| 8 | [智能体的身份链与工具安全](智能体的身份链与工具安全.md) | 身份链"模型无法伪造"设计与工具权限边界 |
| 9 | [Agent人机协同-从头部智能体看审批与权限设计](Agent人机协同-从头部智能体看审批与权限设计.md) | 什么时候问人、问什么、怎么少问：审批疲劳/权限模式/沙箱/审查者 |

## 延伸系列：上下文工程与脚手架设计

第二章讲的是各个部件。上下文这一块（一次调用的预算怎么配、循环里怎么记账、脚手架怎么分层供给、哪些东西带到下一次会话）单独成了[上下文工程系列](../上下文工程/)，本目录第 9 篇讲的权限设计、脚手架设计系列里的验证回路，都默认读者有上下文分层的概念。

想把部件拼成一个好用的智能体，接着读[脚手架设计系列](../脚手架设计/)：七篇，以 Claude Code 为主样本、Codex 做对照，依次是总览、工具设计、验证回路、长任务、技能、MCP，最后一篇收成复刻清单和面试追问。其中的 MCP 入门篇与本目录第 6 篇的企业落地互为前后：前者讲协议本身和动手写 server，后者讲网关、鉴权、审计和版本治理。

## 推荐阅读路径

* **体系学习**：第一章 1→4 顺序读（每篇文末链下一章），再进第二章按需深潜；上下文这条线读[上下文工程系列](../上下文工程/)，看部件怎么拼成整体读[脚手架设计系列](../脚手架设计/)（1→7）；
* **生产救火**：按问题直达——输出不可靠→篇 7，权限→篇 8，不收敛→篇 4，高危操作不敢放手/确认框弹个不停→篇 9；窗口膨胀/成本失控→上下文工程系列的[四板斧篇](../上下文工程/Agent上下文工程-注意力预算与四板斧.md)，工具太多/常驻指令太长→该系列的[上下文分层篇](../上下文工程/Agent上下文分层-常驻按需与工具太多怎么办.md)，跨会话失忆/记忆污染→该系列的[记忆系统篇](../上下文工程/Agent记忆系统-从会话状态到跨会话记忆.md)；工具老被调错、做完不自查、长任务跑偏，进[脚手架设计系列](../脚手架设计/)按症状直达；
* **面试冲刺**：篇 1 的五种模式 + 篇 3 的三层评测 + 各篇追问链；被问"怎么做才像 Claude Code 一样好用"，直接看脚手架设计系列的[收尾篇](../脚手架设计/Agent脚手架复刻清单与面试追问.md)。

## 参考资源（学习整理的公开出处）

* [Building Effective Agents — Anthropic](https://www.anthropic.com/research/building-effective-agents)（五种 workflow 模式的业界共识）
* [Demystifying evals for AI agents — Anthropic Engineering](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents)（agent 评测方法论）
* [Claude Code 权限文档](https://code.claude.com/docs/en/permissions)与[权限模式](https://code.claude.com/docs/en/permission-modes)（权限规则、六种模式、auto mode 的评估顺序，篇 9 主源）
* [How we built Claude Code auto mode — Anthropic](https://www.anthropic.com/research/claude-code-auto-mode)（审查者设计与评测数据，篇 9 主源）
* [Agent approvals & security — OpenAI Codex](https://learn.chatgpt.com/docs/agent-approvals-security)（沙箱与审批两层模型、自动审查，篇 9 副源）
* 上下文工程系列的出处（Anthropic 的上下文工程文章、记忆研究、Claude Code 文档）见[该系列的导读页](../上下文工程/)；脚手架设计系列的出处（Claude Code 官方文档、Anthropic 与 OpenAI 的工程文章）见[该系列的导读页](../脚手架设计/)。
* [A Practical Guide to Building Agents — OpenAI](https://openai.com/business/guides-and-resources/a-practical-guide-to-building-ai-agents)（[中文译版 PDF](https://assets.jimmysong.io/books/a-practical-guide-to-building-agents-zh.pdf)）
* [深入理解AI Agent：设计原理与工程实践 — 李博杰](https://github.com/bojieli/ai-agent-book)（Agent = LLM + 上下文 + 工具 十章开源）
* [从零开始构建智能体 — Datawhale hello-agents](https://github.com/datawhalechina/hello-agents)（中文开源动手教程）
