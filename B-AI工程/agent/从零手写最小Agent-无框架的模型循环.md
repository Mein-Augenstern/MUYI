从零手写最小Agent-无框架的模型循环
====

> 定位：agent"从零到一"主线第二章。范式选完（见[Agent 范式全景](Agent范式全景-workflow与agent的边界.md)），这一篇**不借任何框架**手写一个能跑的最小 agent——OpenAI 官方指南的核心论断："agent 本质是模型在环境中循环运行，用很少的抽象手写实现"。手写过循环，LangGraph 的状态机、[本库 ai-service 的 ReAct 图](ReAct与LangGraph工程实践.md)才读得懂——框架只是给你管状态的循环。
>
> **内容来源**：本篇为学习整理，综合参考（版权归原作者所有）：
> - [A Practical Guide to Building Agents — OpenAI](https://openai.com/business/guides-and-resources/a-practical-guide-to-building-ai-agents)（[中文译版 PDF](https://assets.jimmysong.io/books/a-practical-guide-to-building-agents-zh.pdf)）
> - [Build an AI Agent From Scratch in Python, No Framework](https://buildingagenticai.com/blog/build-ai-agent-from-scratch-python)
> 表述与代码编排为本库原创加工，勘误以原文为准。

最小完备：agent 循环的四行伪代码
------

剥掉所有营销词，agent 循环就这四步：

```
while True:
    response = model(messages, tools)     # ① 模型看历史 + 可用工具，做决策
    if not response.tool_calls:           # ② 模型不再要工具 = 认为做完了
        return response.text              #    最终答案
    for call in response.tool_calls:      # ③ 执行模型点的工具
        result = tools[call.name](**call.args)
    messages.append(tool_results)         # ④ 结果回填历史，回到 ①
```

**①④ 两步是全部秘密**：模型不执行任何东西，它只"点名"工具和参数；真正动手的是你的循环代码，动手完把观察结果塞回对话。所谓 agent 框架，本质就是替你管这个 `messages` 列表的状态机。

Python 骨架（~60 行，可直接跑）
------

```python
import json
from openai import OpenAI

client = OpenAI()
MODEL = "gpt-4o-mini"

# ---- 工具注册表：名字 → 可调用函数 + schema（给模型看的说明书）----
TOOLS = {
    "get_order": lambda order_id: f"订单 {order_id}: 已发货, EMS, 预计明日达",
    "get_refund_policy": lambda: "30 天内无理由退款, 运费自理",
}

TOOL_SCHEMAS = [{
    "type": "function",
    "function": {
        "name": "get_order",
        "description": "按订单号查询订单状态",
        "parameters": {"type": "object", "properties": {
            "order_id": {"type": "string"}}, "required": ["order_id"]},
    },
}, {
    "type": "function",
    "function": {
        "name": "get_refund_policy",
        "description": "查询退货退款政策",
        "parameters": {"type": "object", "properties": {}},
    },
}]

def agent(system: str, user: str, max_turns: int = 8) -> str:
    messages = [{"role": "system", "content": system},
                {"role": "user", "content": user}]
    for _ in range(max_turns):                        # 有界自主：防死循环
        r = client.chat.completions.create(
            model=MODEL, messages=messages, tools=TOOL_SCHEMAS)
        msg = r.choices[0].message
        messages.append(msg)                          # 模型的决策进历史
        if not msg.tool_calls:                        # ② 不要工具 = 完成
            return msg.content
        for tc in msg.tool_calls:                     # ③ 执行点名工具
            result = TOOLS[tc.function.name](**json.loads(tc.function.arguments))
            messages.append({"role": "tool",
                             "tool_call_id": tc.id,
                             "content": str(result)}) # ④ 观察回填
    return "达到最大轮数仍未完成"                        # 兜底出口

print(agent("你是电商客服，只回答与订单和售后有关的问题。",
            "帮我看看订单 A001 能不能退"))
```

**这 60 行里每个设计点都有讲究**：

* **工具 schema 的 description 是写给模型看的**——模型按 description 决定什么时候点名这个工具，它就是函数的"prompt"；
* **`max_turns` 有界自主**——模型陷入循环（反复调同一工具）是最常见的线上事故，硬上限是最后防线；生产里配合[护栏与成本告警](Agent评测与可观测-guards与成本.md)；
* **工具结果用 `str()` 兜底**——模型什么格式都读得懂，先跑通再优化结构化（见[结构化输出篇](结构化输出工程-Pydantic与LLM.md)）；
* **system prompt 划边界**——"只回答订单相关问题"是第一道护栏，模型越界时用它拉回。

从这里到生产：还差什么
------

60 行骨架与生产 agent 之间的四个台阶（每个都有本库专篇）：

| 台阶 | 问题 | 去处 |
|---|---|---|
| 状态与检查点 | 多轮历史只在内存，进程挂了全丢；要人审中间态 | LangGraph 检查点（[ReAct与LangGraph实践](ReAct与LangGraph工程实践.md)） |
| 结构化输出 | `str(result)` 模型读得懂、下游 Java 读不懂 | [Pydantic 契约](结构化输出工程-Pydantic与LLM.md) |
| 身份与权限 | agent 用谁的身份调工具？能碰哪些资源？ | [身份链与工具安全](智能体的身份链与工具安全.md) |
| 评测与护栏 | 上线前怎么知道它靠谱？ | [评测与可观测](Agent评测与可观测-guards与成本.md) |

什么时候该换框架（诚实答案）
------

手写循环跑到出现这三个信号，才值得引入框架：**需要检查点回溯**（人工介入后从中间态续跑）、**需要分支编排的图**（不是线性循环）、**需要多 agent 状态隔离**。三个信号都没出现就上 LangGraph，是用框架复杂度换简历亮点。

追问链
------

* agent 和 function calling 的关系？→ function calling 是模型侧的"点单能力"，agent 是你的循环侧把点单变成执行再回填——两者合起来才是 agent；
* 为什么模型不执行工具？→ 模型只输出文本（点单凭据），执行发生在你的运行时——这决定了工具执行的安全边界完全在你的代码里（见[身份链篇](智能体的身份链与工具安全.md)）；
* max_turns 设多少？→ 按"正常完成需要几步"×2~3 定，到了就降级为人工工单——不是越大越可靠；
* 手写循环最大的坑？→ 工具结果回填的角色和 tool_call_id 对不上——模型会"幻觉"工具结果；每一轮的 messages 结构必须严格对齐 API 规范；
* 什么时候需要状态机而不是 while 循环？→ 出现"从中间态人工恢复"或"多个 agent 共享/隔离状态"时（LangGraph 的 checkpointer 与 thread）。

面试官视角小结
------

这题的区分度极狠：让候选人"不用框架写一个最简 agent"，写得出四步循环 + tool_call_id 回填的，是真理解；开口就 `AgentExecutor.from_agent_and_tools` 的，是被框架养废的。再追问"你的循环怎么防死循环"——答 max_turns 只是及格，答出"轮数上限 + 单工具调用频次限制 + 异常工具的降级返回"三层的，是趟过生产雷的。

> 2026-11 新增（agent"从零到一"主线第二章；学习整理综合参考 OpenAI 指南与无框架教程，见文首来源块；代码骨架为本库原创编排，可直接跑）。上一篇：[Agent 范式全景](Agent范式全景-workflow与agent的边界.md)；下一篇：[Agent评测与可观测](Agent评测与可观测-guards与成本.md)。
