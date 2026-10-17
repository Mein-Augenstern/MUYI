ReAct 与 LangGraph 工程实践
====

> 定位：B 轨 agent 首篇，素材全部来自 star-site ai-service 生产代码（`app/agents/assistant.py`、`app/tools/__init__.py`、`app/api/open_agent.py`，下文路径均相对 `app/`）。讲清"模型自己决定调工具"的原理、工程形态与版本踩坑。身份注入与工具安全另成一篇：[《智能体的身份链与工具安全》](智能体的身份链与工具安全.md)。

一、ReAct 与纯 prompt 链的差异
------

纯 prompt 链（LCEL）是**单段流水线**：`prompt | model | parser`，一次调用定生死。ai-service 的报告解读就是这种形态（`agents/report_interpret.py`：`ChatPromptTemplate | chat_model() | StrOutputParser()`）——它够用，因为输入（报告元数据+指标）已由 Java 侧聚合好塞进模板，模型只负责"读+写"。

ReAct（**Rea**son + **Act**）把"调模型"变成"循环"：模型每轮先推理该做什么，再决定调哪个工具、传什么参数，观察工具结果后继续推理，直到产出不含工具调用的最终回答。家庭助手（`agents/assistant.py`）就是这个形态。

一次真实循环（用户问"我妈妈最近的体检报告有什么异常？"）：

```
1. 推理："妈妈"对应哪个成员 id？           → 调 list_family_members()
2. 观察：id=102 姓名=林晓 关系=mother      → 调 list_recent_reports(family_member_id=102)
3. 观察：report_id=603《林晓 2025年度体检》→ 调 get_report_detail(report_id=603)
4. 观察：空腹血糖 6.8（参考 3.9~6.1）【偏高】→ 信息够了，输出最终回答（循环结束）
```

注意第 1 步：模型没有瞎猜"妈妈"的 id，而是先查成员列表——这是系统提示里"先理解用户提到的是哪位家人，再查对应数据"引导出来的行为。**prompt 质量 = 智能体行为质量**。

| | 纯 prompt 链 | ReAct 智能体 |
|---|---|---|
| 模型调用 | 1 次 | N 次（每轮推理一次，与工具往返数正相关） |
| 数据获取 | 调用方提前备齐 | 模型按需自己拉 |
| 延迟/成本 | 可预算 | 取决于循环轮数，必须设上限 |
| 失败形态 | 输出质量差 | 选错工具 / 参数填错 / 循环不终止 |

选型心法：**输入能一次备齐、输出格式固定 → 链；要"查了才知道下一步查什么" → 智能体**。ai-service 两种并存：解读/诊断用链，对话查询用智能体。

二、工程走读：一张图、四个工具、一条事件流
------

1）图的构建（agents/assistant.py）
------

```python
def _build_react():
    return create_react_agent(
        chat_model(),                  # 大脑：llm.py 工厂出的 ChatOpenAI（bind_tools 由 prebuilt 内部完成）
        tools=TOOLS,                   # 手：4 个查询工具
        state_modifier=SYSTEM_PROMPT,  # 人设与规则（langgraph 0.2.x 的参数名，见第三节踩坑）
        checkpointer=MemorySaver(),    # 多轮记忆
    )
```

`create_react_agent` 是 LangGraph 的预制件，等价于手搭一张双节点状态图：

```
START ──▶ agent 节点（模型推理，产出 AIMessage，可能带 tool_calls）
             │ 条件边：有 tool_calls？
             ├── 有 ──▶ tools 节点（ToolNode 执行，结果包成 ToolMessage 追加回 state）──▶ 回到 agent
             └── 无 ──▶ END（最终回答）
```

状态就是 `{"messages": [...]}`，reducer 是**追加**而非覆盖——每轮的工具结果都拼进历史再喂给模型，这正是"观察"得以累积的原因。入口（`assistant.run`）每轮只传新增的一条 user 消息：

```python
state = {"messages": [("user", message)]}
config = {"configurable": {"thread_id": conversation_id}}
```

历史不是调用方拼的，是 checkpointer 按 thread_id 补的（见下）。

2）工具定义与 docstring 纪律（tools/__init__.py）
------

`@tool` 装饰器把普通函数变成模型可调用的工具，映射关系是：函数名→工具名、**docstring→工具描述**、参数类型标注→填参约束、返回的 str→喂回模型的观察。模型看不到函数体，它对工具的全部认知就是 docstring 原文：

```python
@tool
def list_recent_reports(family_member_id: Optional[int] = None) -> str:
    """列出体检报告摘要（最近 20 份，不含指标明细）。family_member_id 可选——
    指定成员则单查该成员；不传查全家。先用 list_family_members 拿到成员 id。"""
```

这段 docstring 干了三件事：说清用途边界（"摘要，不含明细"）、说清参数语义（可选、单查/全家）、**指路前置工具**（"先用 list_family_members 拿 id"）——第 4 节循环里的第 1→2 步衔接就是它引导的。写 docstring 是智能体工程里 ROI 最高的工作：描述含糊，模型就会该调不调、不该调乱调。纪律：一句话讲清"什么时候用我、需要什么参数"，别写成给人看的 API 注释。

另外注意工具返回的是**人可读的格式化文本**（`- id=102 姓名=林晓 关系=mother …`）而非原始 JSON——模型理解自然语言比嵌套 JSON 可靠，异常标记也预格式化成 `【偏高】/【偏低】`，减少模型的二次推断。

还有一个隐藏工程点：`@tool` 默认生成**同步**工具，而 HTTP 调用是异步的（httpx.AsyncClient），`tools/__init__.py` 用 `asyncio.run()` 起临时事件循环桥接：

```python
def _run(coro):
    import asyncio
    return asyncio.run(coro)      # 同步工具函数里执行异步协程
```

要真正异步工具应该 `@tool` 直接装饰 async 函数；本项目查询都是短平快的内网调用，同步桥接的代价可接受——**先跑通再优化，但要知道代价在哪**。

3）系统提示：每条规则堵一个失败模式
------

`agents/assistant.py` 的 `SYSTEM_PROMPT` 四条规则各有明确用途，不是堆形容词：

```
"必须调用工具查真实数据，禁止编造数值"   → 压制幻觉（编数据是健康/财务场景的死罪）
"先理解用户提到的是哪位家人再查"         → 引导先调 list_family_members 确认对象（第一节循环第 1 步）
"先给结论再给依据"                      → 控制输出结构（可用性）
"健康话题最后提醒遵医嘱"                → 合规兜底（责任边界）
```

写智能体系统提示的心法：**每条规则都应该对应一个观察到的失败模式**。没有失败案例支撑的规则是噪音——既稀释权重，又挤占上下文。

4）MemorySaver 与 thread_id 的记忆隔离
------

`MemorySaver` 按 `thread_id`（=conversation_id）存每个会话的完整消息状态：会话 A 与会话 B 的历史互不可见，用户才能追问"那我爸呢？"——模型从同 thread 的历史里知道"那"指查报告这件事。会话 id 缺省时按用户兜底（`api/open_agent.py`：`conversation_id or f"anon-{member_id}"`），保证同一匿名用户多轮也连续。

要点：checkpointer 是**策略模式接口**，MemorySaver 存内存（重启即失、多副本不共享），生产换 `SqliteSaver`/`PostgresSaver` 只改构造那一个参数——代价最低的"演示→生产"升级路径。

把 `assistant.run` 的完整入口看一遍，thread_id 与身份注入在这里汇合：

```python
async def run(message: str, conversation_id: str, member_id: int) -> str:
    from app.tools import member_ctx
    state = {"messages": [("user", message)]}                 # 只传新增消息
    config = {"configurable": {"thread_id": conversation_id}} # 记忆按会话隔离
    token = member_ctx.set(member_id)                         # 身份按请求隔离
    try:
        result = await graph.ainvoke(state, config)
    finally:
        member_ctx.reset(token)                               # 用完即恢复
    return result["messages"][-1].content
```

两个隔离维度互不干扰：thread_id 管"记住聊过什么"（跨轮次），ContextVar 管"你是谁"（跨整条调用链）——会话与身份是正交的，混在一个变量里迟早出事。

5）astream_events 的 SSE 事件分类
------

`assistant.stream` 订阅 LangGraph 的事件流，只挑两类转发（`agents/assistant.py`）：

```python
async for event in graph.astream_events(state, config, version="v2"):
    if event["event"] == "on_chat_model_stream":      # 模型逐 token 吐字
        yield ("token", event["data"]["chunk"].content)
    elif event["event"] == "on_tool_start":           # 工具调用进度
        yield ("status", f"正在调用工具 {event['name']} …")
```

`api/open_agent.py` 的 `event_source()` 把二元组包成 SSE 帧（`event: {kind}\ndata: {json}`），并在生成器走完后补 `done`（`data: [DONE]`）、异常时发 `error`。对前端就三类语义：**status（工具进度）/ token（增量文本）/ done（正常收尾）**，error 是故障通道。价值：用户在等模型"查家庭成员→查报告→查明细"的十几秒里看到的是进度条+逐字出答案，而不是白屏干等——流式不是炫技，是长链路交互的体验底线。

三、两个实战细节（踩过才知道）
------

1）state_modifier 的版本漂移
------

`create_react_agent` 传系统提示的参数名随 langgraph 版本变过：0.2.x 叫 `state_modifier`（本项目锁定此版本，`assistant.py` 的 docstring 里专门留了注释提醒），0.3 起改名 `prompt` 并将旧参数标记废弃。踩法：按新文档写 `prompt=` 跑在 0.2 环境，直接 `TypeError: unexpected keyword argument`；反之旧代码升 0.3，行为还兼容但埋着废弃警告。教训：**LangChain/LangGraph 迭代快，requirements.txt 锁版本 + 升级时 diff prebuilt 的函数签名**，这类"参数改名"式破坏没有任何运行时预告。

2）ChatOpenAI 的 mock-key 兜底
------

`llm.py` 的模型工厂里有一处不起眼的 `or`：

```python
api_key=settings.llm_api_key or "mock-key",   # 构造函数要求非空，沙箱不真调
```

背景：ChatOpenAI 的 `api_key` 走 pydantic 非空校验，空字符串在**构造时**就抛异常——沙箱环境（没配 `AI_LLM_API_KEY`）会导致进程直接起不来，连降级的机会都没有。占位符让构造通过，真正的分流交给 `config.py` 的 `effective_mock`：无 key 或 `AI_MOCK_ENABLED=true` 时，`assistant.py` 根本不走 `_build_react()`，而是 `_build_mock()` 的简化图：

```python
graph = _build_mock() if settings.effective_mock else _build_react()
```

沙箱降级还有个更深的取舍：假模型（`FakeMessagesListChatModel`）**没有 `bind_tools` 能力**，硬塞进 ReAct 图必挂，所以单独造了个 `StateGraphStub`——与 ReAct 图同接口（`ainvoke`/`astream_events`）、行为退化为单模型节点：

```python
class StateGraphStub:
    """沙箱简化图：与 ReAct 图同接口，行为为单模型节点。"""
    def astream_events(self, state, config=None, version="v2"):
        return self._stream(state)      # 签名对齐，调用方无感
```

输出带【沙箱模拟】标记，**没有密钥也能跑通全链路**（开发/演示不被阻塞）。原则：**降级要降到"形状一致、行为简化"**——接口不变，测的就是同一份链路代码而不是另一条路径，否则沙箱测了等于没测。

四、追问链
------

1. **工具失败怎么办？** 两层兜底。业务失败：工具内部检查 R 信封（`r.get("code") != 0`），把失败**作为文本返回**（`"查询家庭成员失败：..."`）——不抛异常，让模型"观察"到失败并道歉/换路，失败变成循环里的一次普通观察。系统失败：异常冒泡到 api 层，`ask` 返回 `r_fail(500, retryable=True)`，`chat` 发 `event: error` 帧，前端给"稍后再试"。原则：可预期的业务失败喂给模型，不可预期的系统故障拦在模型外。
2. **怎么防工具滥用？** 纵深四道：系统提示立规（"必须调工具查真实数据，禁止编造数值"）；工具集最小化（只有 4 个只读查询，没有写操作）；参数强类型（`report_id: int`，模型填不进畸形值）；身份不经过模型（memberId 从 ContextVar 注入，模型想查别人也没有入口——详见[《智能体的身份链与工具安全》](智能体的身份链与工具安全.md)）。
3. **循环停不下来怎么办？** ReAct 天然有死循环风险（模型反复调同一工具）。LangGraph 图有 `recursion_limit`（默认 25 步）硬上限，超限抛 `GraphRecursionError`，被 api 层的兜底 catch 转成"暂不可用"。工程上再加两道软约束：docstring 指路减少无效往返、系统提示限定"信息够了就回答"。
4. **流式中断怎么恢复？** 先讲清语义：SSE 断在 `done` 之前，前端能靠"没收到 done"识别截断。恢复靠 thread_id：checkpointer 已提交的轮次还在，客户端用同一 conversation_id 重发即可续上下文；但**断点续传（从中断的半个 token 接着吐）做不到也不值得做**——SSE 不可续流，正确姿势是整轮重放。生产增强：checkpointer 换 PostgresSaver，断线重连后从库里取回完整历史。
5. **MemorySaver 有什么坑？** 重启丢记忆（对话上下文断裂）、多副本不共享（负载均衡下两次请求打到不同实例，thread 历史对不上）。所以它只配演示/单实例；上多副本前必须先换持久化 checkpointer，这是部署顺序问题不是优化项。
6. **为什么用 astream_events 而不是直接 astream？** `astream` 产出的是图的**节点级**输出（一个节点一个 chunk，粒度太粗）；`astream_events` 是**事件级**（模型 token、工具起止都各自成事件），才能同时给前端"进度"和"打字机"。`version="v2"` 是当前事件 schema 版本，不传会告警。

五、面试官视角小结
------

面试官在智能体题里打量的四件事：

* **分得清链和智能体**：能说清"输入可预组装→链、按需探索→ReAct"，并知道智能体的新失败面（循环上限、工具误调、成本不可预算）。
* **懂工具协议的本质**：docstring 是给模型的说明书，工具输出是给模型的观察文本——接口设计围绕"模型怎么读"而不是"人怎么读"。
* **状态与记忆说得清**：thread_id 隔离、checkpointer 策略模式、MemorySaver 的内存局限与升级路径，一条线讲完。
* **有实战痕迹**：state_modifier 版本漂移、mock-key 让进程能起、沙箱降级保形状——这些细节背不来，只有真写过才有。

一句话总结：**ReAct 是"推理→调工具→观察"的循环，工程上用 create_react_agent 双节点图落地；docstring 决定工具选对、state_modifier/prompt 看版本、MemorySaver 按 thread_id 隔离记忆、astream_events 按事件分类推 SSE；失败兜底分"喂模型的业务失败"和"拦在模型外的系统故障"两层。**

> 2026-10 新增（P3 AI 栈 · 素材来自 ai-service 生产代码）。
