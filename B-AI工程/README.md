# B 轨 · AI 应用工程

> 2026-10 新建。与 star-site ai-service（Python/LangChain）和 voice-zcode 实战滚动同步，
> 每落地一个技术组件回写一篇。定位：面试深度标准的 AI 工程知识体系。

## 目录规划

| 目录 | 范围 | 首批素材（来自实战） |
|---|---|---|
| llm基础/ | Transformer 直觉、token 与上下文窗口、温度与解码策略、幻觉成因与缓解 | ai-service 的 chat_model 工厂、temperature 选型 |
| rag/ | 文档切分策略、嵌入模型选型、向量库（pgvector/Milvus）、检索重排、评测 | 体检报告解读链路（结构化指标→LLM） |
| agent/ | ReAct 模式、工具调用设计、LangGraph 状态机、记忆系统、SSE 流式 | ai-service 的 ReAct 图 + ContextVar 身份注入 + astream_events 流式 |
| prompt/ | 吴恩达 Prompt Engineering 课程笔记、系统提示词工程 | 财务诊断/报告解读的系统提示词演进 |
| 面试题/ | LLM 应用岗高频：上下文工程、多轮状态、提示注入防护、成本控制 | — |

## 写作规范

1. 每篇遵循 A 轨深度标准：原理 + 真实代码 + 追问链 + "面试官视角小结"。
2. 实战反哺的文章必须引用真实类名/文件路径（如 `app/agents/assistant.py`），可追溯。
3. 空目录不建：写出第一篇时再建目录，避免占位噪音。
