脚手架设计（系列导读：向 Claude Code 学做智能体）
====

> 本系列是 [agent 系列](../agent/)的延伸，共七篇，2026-11 新增。agent 系列回答的是"一个智能体由哪些部件组成"；这一系列换个问法：同一个模型，为什么放进 Claude Code 就好用，自己拼出来的就不行？答案在模型之外的那一层——脚手架（harness）。
> 前四篇讲脚手架本身：循环、工具、验证、长任务。第 5、6 篇讲两种扩展机制：技能（Skills）给智能体扩展"知道什么"，MCP 给它扩展"能调什么"。最后一篇收成清单和面试追问。
> 七篇全部是学习整理篇：以 Claude Code 为主样本，OpenAI 的 Codex 实践做对照，素材来自官方文档和两家公司的工程文章。
> 读之前建议先过[上下文工程系列](../上下文工程/)：脚手架管的头一件事就是模型看到什么，常驻层怎么写小、工具定义怎么按需取，都在那里；没读过也能看，碰到术语回查即可。只想知道在 Claude Code 里怎么配置，见 [ClaudeCode使用实践](../工程工具/ClaudeCode使用实践.md)；MCP 的企业落地（网关、鉴权、审计、版本治理）见 agent 的[第 6 篇](../agent/MCP协议与企业落地.md)。

## 目录（建议顺序）

| # | 篇目 | 回答的问题 |
|---|---|---|
| 1 | [Agent脚手架总览-ClaudeCode好用在模型之外](Agent脚手架总览-ClaudeCode好用在模型之外.md) | 模型之外的那层是什么：三段循环 / 五种扩展部件 / 让模型决定还是由脚手架强制 |
| 2 | [Agent工具设计-写给模型看的接口](Agent工具设计-写给模型看的接口.md) | 少而准 / 高信号返回 / 报错带修法 / 描述即提示词 / 评测驱动 |
| 3 | [Agent验证回路-让智能体自己发现错了](Agent验证回路-让智能体自己发现错了.md) | 可跑的通过/失败检查 / Stop 钩子 / 三类测试 / 检查不被篡改 |
| 4 | [Agent长任务-进度状态与增量交付](Agent长任务-进度状态与增量交付.md) | 进度文件与功能清单 / 一次一个 / 会话恢复与检查点 / 常驻智能体五个原语 |
| 5 | [Agent技能入门-按需加载的知识与流程](Agent技能入门-按需加载的知识与流程.md) | 技能是什么 / 三层加载各占多少 / 描述怎么写 / 和 `CLAUDE.md`、钩子、MCP 的分界 / 怎么评测 |
| 6 | [MCP协议入门-原理、安全与第一个Server](MCP协议入门-原理、安全与第一个Server.md) | 两代协议的差别 / 三种原语 / 传输与授权 / 官方安全清单 / 动手写一个 server |
| 7 | [Agent脚手架复刻清单与面试追问](Agent脚手架复刻清单与面试追问.md) | 复刻清单 / 从症状出发的落地顺序 / 十四个连环追问 |

## 推荐阅读路径

* **系统学习**：1→7 顺序读。第 1 篇是地图，中间五篇各展开一块，第 7 篇收成清单；
* **生产救火**：按问题直达——工具老被调错、返回太大、报错看不懂→第 2 篇；做完不自查、说完成了其实没好→第 3 篇；长任务跑偏、做一半断了→第 4 篇；一段规范不知道该写成技能还是写进 `CLAUDE.md`→第 5 篇；要接外部工具、自己写 MCP server→第 6 篇；上下文太乱、工具太多→上下文工程系列的[上下文分层篇](../上下文工程/Agent上下文分层-常驻按需与工具太多怎么办.md)；高危操作不敢放手、确认框弹个不停→agent 的[人机协同篇](../agent/Agent人机协同-从头部智能体看审批与权限设计.md)；
* **面试冲刺**：第 7 篇的十四个追问直接对应"怎么做才像 Claude Code 一样好用"这个话题，每题后面链到讲细节的篇目。

## 参考资源（学习整理的公开出处）

* [How Claude Code works](https://code.claude.com/docs/en/how-claude-code-works)与 [Best practices for Claude Code](https://code.claude.com/docs/en/best-practices)（脚手架的定义、确定性/非确定性指令，第 1 篇主源）
* [Building agents with the Claude Agent SDK — Anthropic](https://claude.com/blog/building-agents-with-the-claude-agent-sdk)（三段循环与三种反馈，第 1、3 篇）
* [Writing effective tools for agents — Anthropic](https://www.anthropic.com/engineering/writing-tools-for-agents)（工具设计五原则，第 2 篇主源）
* [How the Claude Code team thinks about verification — claude.dev](https://claude.dev/blog/how-the-claude-code-team-thinks-about-verification/)（验证回路与三类测试，第 3 篇主源）
* [Effective harnesses for long-running agents — Anthropic](https://www.anthropic.com/engineering/effective-harnesses-for-long-running-agents)与 [Building always-on agents — Anthropic](https://claude.com/blog/building-always-on-agents)（长任务脚手架与常驻智能体，第 4 篇主源）
* [Agent Skills 开放标准](https://agentskills.io/specification)（规范、[编写指南](https://agentskills.io/skill-creation/best-practices)、[描述优化](https://agentskills.io/skill-creation/optimizing-descriptions)）与 [Equipping agents for the real world with Agent Skills — Anthropic](https://www.anthropic.com/engineering/equipping-agents-for-the-real-world-with-agent-skills)（第 5 篇主源）
* [MCP 官方规范](https://modelcontextprotocol.io/specification/2026-07-28)与 [MCP Python SDK](https://github.com/modelcontextprotocol/python-sdk)（第 6 篇主源）；[Code execution with MCP — Anthropic](https://www.anthropic.com/engineering/code-execution-with-mcp)（工具太多时的省法，见上下文工程系列的上下文分层篇）
* [Harness engineering: leveraging Codex in an agent-first world — OpenAI](https://openai.com/index/harness-engineering/)（纪律放在脚手架里，第 1 至 4 篇的对照）
