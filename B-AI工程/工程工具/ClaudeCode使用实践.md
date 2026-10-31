ClaudeCode使用实践
====

Claude Code 的价值不在"会用 CLI"，而在**给它喂什么**。两条底层原则贯穿全篇：模型输出质量很大程度取决于输入质量；上下文越长，输出质量越差。所以这篇不讲基本操作，讲配置资产——CLAUDE.md、Skills、Slash Commands、Sub-agents，以及状态栏与 MCP 的取舍。上下文预算的通用方法论见 [上下文工程-Context Engineering](../llm基础/上下文工程-Context Engineering.md)，工具接入层的协议化全景见 [MCP协议与企业落地](../agent/MCP协议与企业落地.md)。

1 CLAUDE.md 与 AGENTS.md：项目记忆放哪
------

* 项目根目录的 `CLAUDE.md` **每次会话自动发送**，建议 100-200 行，只放架构、技术栈、核心模块组织方式——它常驻上下文，写多等于自我稀释（第二条原则的直接应用）；
* 现有项目用 `/init` 生成初始版本再手工裁剪；
* `CLAUDE.md` 只有 Claude Code 读取；团队混用 Cursor/Codex 时统一维护 `AGENTS.md`，`CLAUDE.md` 只写一句"详见 @AGENTS.md"；
* 分层加载：用户级 `~/.claude/CLAUDE.md` 影响所有会话（放个人偏好）；子目录的 CLAUDE.md 在读到该目录时按需加载（适合描述单个 package 的局部规则）。

判断标准：安全红线、架构约束这类**强制项**放 CLAUDE.md；团队规范放 Skills（下一节）。

2 Skills：按需加载的团队规范
------

定位：**减少 token、按需加载 context**。目录两处：全局 `~/.claude/skills/<name>/SKILL.md`、项目 `.claude/skills/<name>/SKILL.md`。

机制是关键：SKILL.md 开头的 frontmatter 是"索引"，固定发送给模型，由模型判断是否加载全文——**书的目录常驻，正文按需取**：

```markdown
---
name: redis-specification
description: 如果需要使用 redis，需要遵守这个文件描述的Redis规范以及最佳实践
tags: [redis, performance]
---
```

正文写团队个性化规范，例如"Kafka consumer group 必须命名 `{topicName}_{applicationName}`"。取舍判断：AI 已经很懂的通用最佳实践（Spring Kafka 怎么用）不必写；**只有你们团队这么定的才值得写**——这既省 token，也让 description 成为可靠的触发条件。

与 Rules 对比：`.claude/rules/` 下的多个 markdown 会话开始就全量加载，无论是否需要都占 context。作者判断 Rules 应被 Skills 替代——需要"始终生效"的放 CLAUDE.md，按需的放 Skills，Rules 是两头不占的中间态。

3 Slash Commands：把流程固化成一条命令
------

全局 `~/.claude/commands/`（code-review.md、bug-fix.md）、项目 `.claude/commands/`（deploy-check.md、api-test.md）。支持 `$ARGUMENTS` 传参，也可以让模型先询问再执行。

以 code-review.md 为例，提示词要点：对 `$ARGUMENTS` 做全面审查——代码质量（规范/命名/复杂度）、潜在问题（空指针/SQL 注入/并发安全/资源泄漏）、性能、最佳实践（事务边界/异常处理/日志）。价值在于把"审查维度清单"固化：人工 review 的维度不稳定，提示词稳定。

4 Sub-agents：独立上下文干杂活
------

核心收益是几个"独立"：**独立提示词、独立权限、独立上下文、可并行**——审查一个大 diff 不再污染主对话。目录 `~/.claude/agents/`（全局）与 `.claude/agents/`（项目），或用 `/agent` 交互式创建。

```markdown
---
name: code-reviewer
description: 专业代码审查员。审查代码质量、安全性和可维护性。在编写或修改代码后立即使用。
tools: Read, Grep, Glob, Bash
model: sonnet
---
```

frontmatter 里 tools 收紧权限（审查员只读为主）、model 可降档省钱。这个 reviewer 的提示词：先跑 `git diff HEAD`、只聚焦修改文件、按质量/安全/并发/性能检查、输出带文件行号的问题与建议。

用法：显式触发（"请使用 code-reviewer 审查未提交的变更"）；也可并行——code-reviewer、security-auditor、performance-optimizer 同时执行再汇总。命名逻辑：主会话本身就是 agent，调度 sub-agents 干杂活、只取结果。管理建议：**agent 不要拆太细**，太多反而不好管理。

5 配件取舍：状态栏与 MCP
------

状态栏：`~/.claude/settings.json` 配 statusLine 指向 shell 脚本，脚本用 jq 从 stdin 的 JSON 提取模型名、目录、context 使用百分比、成本、时长，拼成一行常驻显示——**context 百分比常驻可见，是"上下文越长质量越差"这条原则的仪表盘**：

```json
"statusLine": {
  "type": "command",
  "command": "bash ~/.claude/statusline-command.sh"
}
```

MCP 的取舍是这篇最反直觉的结论：**即使不用某个 MCP server，它的工具定义也占 context**——github MCP 一家就能吃掉几万 token。Github 场景用本地 `gh` 命令替代。Skills 兴起后，MCP 的定位退化为**访问企业内网资源**（confluence、jira 这类没有 CLI 替代物的）。配置在 `~/.claude.json`，协议机制见 [MCP协议与企业落地](../agent/MCP协议与企业落地.md)。

6 配置资产总览：一张表定去处
------

回到两条底层原则做收敛——每类资产按"加载时机"与"context 成本"对号入座：

| 资产 | 位置 | 加载时机 | 放什么 | context 成本 |
|---|---|---|---|---|
| CLAUDE.md | 项目根/子目录/用户级 | 每次会话常驻 | 架构、技术栈、模块组织、强制项 | 100-200 行，写了就常驻 |
| Rules | .claude/rules/ | 会话开始全量常驻 | 原则上应被 Skills 替代 | 无论是否需要都占 |
| Skills | .claude/skills/*/SKILL.md | frontmatter 常驻，正文按需 | 团队个性化规范 | 索引级，极低 |
| Commands | .claude/commands/*.md | 显式 / 触发 | 高频流程（review、部署检查） | 触发时才占 |
| Sub-agents | .claude/agents/*.md | 显式调度、可并行 | 独立任务（审查、审计） | 主会话零占用 |
| MCP | ~/.claude.json | 工具定义常驻 | 企业内网资源接入 | 一家可达数万 token |

选型口诀：**强制项常驻（CLAUDE.md），规范做索引（Skills），流程做命令（Commands），脏活外包（Sub-agents），工具定义能省则省（MCP 换 CLI）**。

实践 checklist 与常见坑
------

**落地 checklist（按优先级）**：

* `/init` 生成 CLAUDE.md，手工裁到 100-200 行，只留架构/技术栈/模块组织；
* 强制项（安全红线、架构约束）进 CLAUDE.md；团队规范进 Skills，从 redis/kafka 这类高频中间件写起，description 写成可判定的触发条件；
* 高频流程固化成 Slash Commands（从 code-review 起步，预留 $ARGUMENTS）；
* 第二个迭代再加 sub-agents（code-reviewer 起步，不拆细）；
* 状态栏打开 context 百分比；MCP 逐个审计——有 CLI 替代品的换成命令。

**常见坑**：

* CLAUDE.md 写成产品文档，几百行常驻——主上下文被自我稀释；
* 规范写进 Skills 正文，但 description 含糊——模型永远不触发加载，等于没写；
* 用 Rules 承载一切——无论是否需要都占 context；
* MCP 装了一排一个不用——工具定义静默吃掉几万 token；
* sub-agent 拆得过细——管理成本超过收益。

面试官视角小结
------

考察 AI 工程化素养时，"用了 Claude Code"只是及格线。能讲清 CLAUDE.md/Skills/Rules 三者的加载时机差异（常驻 vs 索引按需 vs 全量常驻），并给出"什么该写、什么不该写"的判断标准，说明理解上下文经济学。能进一步讲 sub-agents 的独立上下文收益、MCP 的 token 成本与 CLI 替代方案，是把工具问题上升为架构问题的信号——这套思维与企业级 LLM 应用的上下文预算管理（见 [上下文工程-Context Engineering](../llm基础/上下文工程-Context Engineering.md)）完全同构。

> 2026-10 新增（B 轨工程工具首篇）。

> 来源：javadoop《Claude Code 使用实践》（https://javadoop.com/post/claude-code-best-practice）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
