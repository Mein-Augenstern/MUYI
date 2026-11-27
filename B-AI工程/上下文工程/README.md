上下文工程（系列导读：窗口里该放什么）
====

> 本系列把散在各处的"上下文"收成一条线。agent 系列回答的是"一个智能体由哪些部件组成"；这一系列只盯一件事：**上下文窗口是智能体最稀缺的资源**——里面放什么、什么时候放、放多久、花多少钱。
> 四篇按"由内到外"排：先看一次调用，再看一个循环，再看整个脚手架怎么分层供给，最后看跨会话的记忆。检索（RAG）是上下文的一个来源，单独成系列，见 [rag](../rag/)。
> 第 2 篇是 ai-service 实战反哺，其余三篇是学习整理篇，素材来自 Anthropic 的公开文章与 Claude Code 官方文档。
> 系列规划六篇：第 1 篇总览与第 6 篇提示词缓存还在写，写好后补进目录。

## 目录（建议顺序）

| # | 篇目 | 管的范围 | 回答的问题 |
|---|---|---|---|---|
| 2 | [上下文工程-Context Engineering](上下文工程-Context%20Engineering.md) | 一次调用 | 窗口预算怎么配比、长对话怎么压缩、内容放哪里更管用 |
| 3 | [Agent上下文工程-注意力预算与四板斧](Agent上下文工程-注意力预算与四板斧.md) | 一个循环 | 预算怎么记账、按需检索、压缩、笔记、子代理 |
| 4 | [Agent上下文分层-常驻按需与工具太多怎么办](Agent上下文分层-常驻按需与工具太多怎么办.md) | 整个脚手架 | 常驻层写小、按需加载、工具搜索、为缓存排序 |
| 5 | [Agent记忆系统-从会话状态到跨会话记忆](Agent记忆系统-从会话状态到跨会话记忆.md) | 跨会话 | 分层记忆栈、读回时只进索引、记忆污染治理 |

## 推荐阅读路径

* **系统学习**：按 2→5 读。先弄清一次调用的账怎么算，再看循环里账会怎么膨胀，然后是脚手架怎么从结构上省，最后是哪些东西值得带到下一次会话；
* **生产救火**：按症状直达——多轮对话越来越慢、越来越贵→第 2 篇；工具结果把窗口撑爆、长任务中途失忆→第 3 篇；工具一多就乱、装了很多技能不知道占多少→第 4 篇；想让 agent 记住用户偏好、又怕记错→第 5 篇；
* **面试冲刺**：每篇文末都有追问链，从"上下文工程和提示词工程什么关系"一路问到"记忆污染怎么治"。

## 参考资源（学习整理的公开出处）

* [Effective context engineering for AI agents — Anthropic Engineering](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents)（注意力预算与四板斧，第 3 篇主源）
* [Context Engineering for AI Agents: A Practical Guide — The Daily Agent](https://dev.to/thedailyagent/context-engineering-for-ai-agents-a-practical-guide-5989)（预算测量与工具路由实操，第 3 篇副源）
* Claude Code 官方文档：[Explore the context window](https://code.claude.com/docs/en/context-window)、[Extend Claude Code](https://code.claude.com/docs/en/features-overview)、[How Claude Code uses prompt caching](https://code.claude.com/docs/en/prompt-caching)（第 4 篇主源）
* [Introducing advanced tool use on the Claude Developer Platform — Anthropic](https://www.anthropic.com/engineering/advanced-tool-use)与 [Code execution with MCP — Anthropic](https://www.anthropic.com/engineering/code-execution-with-mcp)（工具搜索、中间结果不进上下文，第 4 篇）
* [The components of agent memory — Anthropic](https://www.anthropic.com/research/components-of-agent-memory)与 [Agent Memory — Letta](https://www.letta.com/blog/agent-memory)（第 5 篇主源与副源）
