# 从零到一做企业级 AI Agent：先抄 Anthropic 和 OpenAI 的作业，再谈框架（附开源知识库）

> 掘金引流稿（存稿）——目标读者：正在做/想做 LLM Agent 的后端工程师。
> 发布时替换末尾链接的在线书地址即可；全文约 2800 字，阅读 8 分钟。

## 一、2025 年最贵的弯路：上了 LLM 就要套 Agent 框架

我做了一个健康报告解读的 AI 服务（FastAPI + LangGraph，生产跑了一年），期间见过太多团队一开口就是"我们用多智能体协作"。真正写代码的人都知道一个尴尬的事实：**大多数所谓"agent 需求"，拆开看是三段 workflow**。

2025 年业界已经收敛出共识。Anthropic 的《Building Effective Agents》把分界线画得非常干净：

- **workflow**：用代码预先编排好 LLM 与工具的执行路径——什么时候调模型、调几次、结果怎么流转，都是写死的；
- **agent**：LLM 自己决定执行路径——模型看目标、看反馈，自主决定下一步，循环直到完成。

一句话记忆：**流程写死的是 workflow，模型写流程的是 agent**。这条分界决定后面一切工程属性：workflow 可预测、可审计、成本低；agent 灵活，但每一步都是概率性的。

## 二、五种 workflow 模式：90% 的需求在前四档

Anthropic 归纳的五档，按复杂度递增（也是我的选型顺序）：

| 模式 | 一句话 | 企业例子 |
|---|---|---|
| 提示链 | A 输出是 B 输入，中间加程序检查点 | 报告抽取 → 渲染 → 解读 |
| 路由 | 先分类再分发 | 工单分：退款/咨询/投诉 |
| 并行化 | 多路独立评估再聚合 | 代码审查三路：安全/性能/风格 |
| 编排者-执行者 | 模型动态拆任务分发 | 多文件复杂改动 |
| 评估者-优化者 | 一个生成一个评，循环改进 | 翻译的译-评-改 |
| （Agent） | 开放任务自主循环 | 修一个复现条件复杂的 bug |

我自己服务的体检链路就是**三段提示链**：结构化抽取（Pydantic 契约）→ 行式渲染 → 解读生成——每步可测可审计，至今没用到第五档。**生产里大多数"agent 需求"拆开看是三段 workflow**，这句话能帮你省掉大半预算。

什么时候才真的上 agent？三问：步数可预知吗？环境要真交互吗？错了能撤吗？三个答案分别是"不可知/要/能"才上——支付、删库、对外发邮件这类操作，agent 化之前先问"错了能不能撤"。

## 三、60 行手写一个最小 Agent（不用框架）

OpenAI 的官方指南有个反直觉的论断：agent 本质就是"模型在环境中循环运行"，**用很少的抽象手写即可**。剥掉所有营销词，核心就这四步：

```python
while True:
    r = model(messages, tools)          # ① 模型看历史+工具，做决策
    if not r.tool_calls:                # ② 不再要工具 = 做完了
        return r.text
    for tc in r.tool_calls:             # ③ 执行模型点名的工具
        messages.append(observation)    # ④ 观察回填历史，回到 ①
```

框架不执行任何东西——**模型只"点名"工具和参数**，真正动手的是你的循环代码。这个认知决定了安全边界完全在你的运行时里（模型无法伪造执行）。

手写完这 60 行，你才有资格回答"什么时候该上框架"：**需要检查点回溯**（人工介入后从中间态续跑）、**需要图编排**（不是线性循环）、**需要多 agent 状态隔离**——三个信号都没出现就上 LangGraph，是用框架复杂度换简历亮点。

## 四、从"能跑"到"敢上线"：评测与护栏

agent 是概率性系统，上线前最后一关是评测——它比传统单测难一个量级：输出是自然语言（同一答案一百种说法）、路径不确定（这次 2 次工具调用下次 4 次）、状态会变（第 3 步依赖第 1 步改了什么）。

三层评测金字塔（从便宜到贵）：

1. **单步评测**：单次生成的质量，LLM 当裁判打分——但**裁判自己要先被测**（50 个人工标注样本校准，裁判不准整个体系是沙上城堡）；
2. **轨迹评测**：中间步骤合理性——调了哪些工具、顺序、参数；
3. **端到端**：任务完成度用代码判定器（查 DB 断言），不评文本相似度。

运行时四道护栏（全在你的代码里，模型拒绝越权是"建议"，代码校验才是"法律"）：输入注入检测 → 工具参数 schema 校验 → 执行沙箱 → 输出敏感过滤。人审（HITL）是第五道：高危操作挂起等确认。

指标只盯三个：**任务完成率、平均轮数（步数失控信号）、单任务 token 成本**。agent 不可观测就不叫上线。

## 五、多智能体：适用面比博客窄得多

2025 年的另一个共识：**能用单 agent + 合适工具解决的，不要上多智能体**。代价清单：上下文分裂（交接必损）、错误放大（误差沿链复利）、成本翻倍、调试难度指数级。

多代理的真实信号只有一个：**单一上下文装不下**——工具太多挤爆 prompt、职责需要对立视角（生成 vs 审查）、物理权限隔离。三信号缺一个都先单 agent。

拓扑首选 Supervisor（一个主 agent 持全局状态分发回收）——保留灵活性又把权限、成本、预算收在可控点。自由对话式多代理（demo 里最常见的形态）不可控不收敛，生产慎用。

## 六、写在最后

这四段认知来自我整理的 agent 体系知识库（两章八篇：范式 → 手写 → 深潜 → 评测 → 编排），全部开源：

- 在线阅读：https://muyi-axl.pages.dev/B-AI工程/agent/
- 仓库：https://github.com/Mein-Augenstern/MUYI

下一篇预告：多代理的轨迹回放排障实践。有问题欢迎 issue 交流。

**参考**：[Anthropic — Building Effective Agents](https://www.anthropic.com/research/building-effective-agents) · [Anthropic — Demystifying evals for AI agents](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents) · [OpenAI — A Practical Guide to Building Agents](https://openai.com/business/guides-and-resources/a-practical-guide-to-building-ai-agents) · [Datawhale hello-agents](https://github.com/datawhalechina/hello-agents) · [李博杰 — 深入理解AI Agent](https://github.com/bojieli/ai-agent-book)

---

*发布备注（不随文发布）：①文末在线书链接发布时确认可达；②配图建议复用 MUYI agent 目录各篇的 mermaid 图（五模式流程图/评测金字塔可重绘竖版）；③标签：AI Agent、LLM、架构设计、Python。*
