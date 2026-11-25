Agent 系列导读（书籍式目录：从零到一做企业级 Agent）
====

> 本目录按「范式 → 手写构建 → 专项深潜 → 脚手架设计 → 评测上线 → 编排选型」的渐进路径组织成三章十七篇：
> **第一章 从零到一**（范式全景 → 手写最小 Agent → 评测与可观测 → 多智能体编排与框架选型）是体系主线；
> **第二章 生产深潜**（ReAct/LangGraph、MCP、结构化输出、身份链与工具安全、上下文工程、记忆系统、人机协同）以 ai-service 实战反哺为主，含公开好文的学习整理篇；
> **第三章 脚手架设计**（总览 → 工具设计 → 上下文分层 → 验证回路 → 长任务 → 复刻清单与面试追问）以 Claude Code 为主样本、Codex 做对照，回答"怎么造一个像它一样好用的智能体"，全部为学习整理篇。
> 三章互链：主线篇引用深潜篇的机制，深潜篇引用主线篇的定位，第三章把前两章的部件拼成一个整体。

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
| 6 | [MCP协议与企业落地](MCP协议与企业落地.md) | 工具生态协议与企业落地四件套 |
| 7 | [结构化输出工程-Pydantic与LLM](结构化输出工程-Pydantic与LLM.md) | 模型输出怎么变成下游敢用的强类型契约 |
| 8 | [智能体的身份链与工具安全](智能体的身份链与工具安全.md) | 身份链"模型无法伪造"设计与工具权限边界 |
| 9 | [Agent上下文工程-注意力预算与四板斧](Agent上下文工程-注意力预算与四板斧.md) | 窗口里该装什么：注意力预算/JIT 检索/压缩/笔记/子代理 |
| 10 | [Agent记忆系统-从会话状态到跨会话记忆](Agent记忆系统-从会话状态到跨会话记忆.md) | 跨会话学到什么：分层记忆栈/dreaming 整理/记忆污染治理 |
| 11 | [Agent人机协同-从头部智能体看审批与权限设计](Agent人机协同-从头部智能体看审批与权限设计.md) | 什么时候问人、问什么、怎么少问：审批疲劳/权限模式/沙箱/审查者 |

## 第三章 · 脚手架设计（向 Claude Code 学做智能体）

| # | 篇目 | 回答的问题 |
|---|---|---|
| 12 | [Agent脚手架总览-ClaudeCode好用在模型之外](Agent脚手架总览-ClaudeCode好用在模型之外.md) | 模型之外的那层是什么：三段循环/五种扩展部件/让模型决定还是由脚手架强制 |
| 13 | [Agent工具设计-写给模型看的接口](Agent工具设计-写给模型看的接口.md) | 少而准/高信号返回/报错带修法/描述即提示词/评测驱动 |
| 14 | [Agent上下文分层-常驻按需与工具太多怎么办](Agent上下文分层-常驻按需与工具太多怎么办.md) | 常驻层写小/技能三层/工具搜索/为缓存排序/中间结果不进上下文 |
| 15 | [Agent验证回路-让智能体自己发现错了](Agent验证回路-让智能体自己发现错了.md) | 可跑的通过/失败检查/Stop 钩子/三类测试/检查不被篡改 |
| 16 | [Agent长任务-进度状态与增量交付](Agent长任务-进度状态与增量交付.md) | 进度文件与功能清单/一次一个/会话恢复与检查点/常驻智能体五个原语 |
| 17 | [Agent脚手架复刻清单与面试追问](Agent脚手架复刻清单与面试追问.md) | 复刻清单/从症状出发的落地顺序/十二个连环追问 |

## 推荐阅读路径

* **体系学习**：第一章 1→4 顺序读（每篇文末链下一章），再进第二章按需深潜，最后读第三章 12→17，看这些部件怎么拼成一个好用的智能体；
* **生产救火**：按问题直达——输出不可靠→篇 7，权限→篇 8，不收敛→篇 4，窗口膨胀/成本失控→篇 9，跨会话失忆/记忆污染→篇 10，高危操作不敢放手/确认框弹个不停→篇 11，工具老被调错/返回太大→篇 13，上下文太乱/工具太多→篇 14，做完不自查/说完成了其实没好→篇 15，长任务跑偏/做一半断了→篇 16；
* **面试冲刺**：篇 1 的五种模式 + 篇 3 的三层评测 + 各篇追问链；被问"怎么做才像 Claude Code 一样好用"，直接看篇 17 的十二个追问。

## 参考资源（学习整理的公开出处）

* [Building Effective Agents — Anthropic](https://www.anthropic.com/research/building-effective-agents)（五种 workflow 模式的业界共识）
* [Demystifying evals for AI agents — Anthropic Engineering](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents)（agent 评测方法论）
* [Effective context engineering for AI agents — Anthropic Engineering](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents)（上下文工程方法论，篇 9 主源）
* [Context Engineering for AI Agents: A Practical Guide — The Daily Agent](https://dev.to/thedailyagent/context-engineering-for-ai-agents-a-practical-guide-5989)（预算测量/工具路由实操，篇 9 副源）
* [The components of agent memory — Anthropic](https://www.anthropic.com/research/components-of-agent-memory)（记忆分层与实验数据，篇 10 主源）
* [Agent Memory — Letta](https://www.letta.com/blog/agent-memory)（四层记忆栈与 memory blocks，篇 10 副源）
* [Claude Code 权限文档](https://code.claude.com/docs/en/permissions)与[权限模式](https://code.claude.com/docs/en/permission-modes)（权限规则、六种模式、auto mode 的评估顺序，篇 11 主源）
* [How we built Claude Code auto mode — Anthropic](https://www.anthropic.com/research/claude-code-auto-mode)（审查者设计与评测数据，篇 11 主源）
* [Agent approvals & security — OpenAI Codex](https://learn.chatgpt.com/docs/agent-approvals-security)（沙箱与审批两层模型、自动审查，篇 11 副源）
* [How Claude Code works](https://code.claude.com/docs/en/how-claude-code-works)与 [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices)（脚手架的定义、确定性/非确定性指令，篇 12 主源）
* [Writing effective tools for agents — Anthropic](https://www.anthropic.com/engineering/writing-tools-for-agents)与 [Introducing advanced tool use — Anthropic](https://www.anthropic.com/engineering/advanced-tool-use)（工具设计五原则、工具搜索，篇 13、14 主源）
* [How the Claude Code team thinks about verification — claude.dev](https://claude.dev/blog/how-the-claude-code-team-thinks-about-verification/)（验证回路与三类测试，篇 15 主源）
* [Effective harnesses for long-running agents — Anthropic](https://www.anthropic.com/engineering/effective-harnesses-for-long-running-agents)与 [Building always-on agents — Anthropic](https://claude.com/blog/building-always-on-agents)（长任务脚手架与常驻智能体，篇 16 主源）
* [Harness engineering: leveraging Codex in an agent-first world — OpenAI](https://openai.com/index/harness-engineering/)（纪律放在脚手架里，篇 12 至 15 的对照）
* [A Practical Guide to Building Agents — OpenAI](https://openai.com/business/guides-and-resources/a-practical-guide-to-building-ai-agents)（[中文译版 PDF](https://assets.jimmysong.io/books/a-practical-guide-to-building-agents-zh.pdf)）
* [深入理解AI Agent：设计原理与工程实践 — 李博杰](https://github.com/bojieli/ai-agent-book)（Agent = LLM + 上下文 + 工具 十章开源）
* [从零开始构建智能体 — Datawhale hello-agents](https://github.com/datawhalechina/hello-agents)（中文开源动手教程）
