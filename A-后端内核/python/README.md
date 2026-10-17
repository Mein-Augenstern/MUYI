# Python 轨道：Java 工程师的全栈扩展

> **站长背景**：Java 为主力语言（并发/JVM/集合源码级，见 [java/](../java/)），2026 年起深入 Python 并已有两个生产项目落地。
> **这条轨道的写法**：不写语法扫盲——每个主题都用「Java 对照表」切入：*Java 里对应物是什么 → Python 的差异是什么 → 差异背后的生态原因 → 我的生产项目里怎么落的*。
> 所有代码段可追溯到真实仓库，不接受纸上谈兵。

## 学习方法：对照式迁移，而非从零学

Java 工程师学 Python 最大的坑不是语法，是**用 Java 直觉写 Python**（翻译腔）：万事皆类、getter/setter、防御性 if 而不是 try。对照表式学习法就是先建立概念映射，再刻意找差异点：

| Java 世界 | Python 世界 | 差异要点 |
|---|---|---|
| `pom.xml` + GAV 坐标 | `pyproject.toml`（PEP 621） | 依赖是区间约束 + 平台 wheel，不是跨平台 jar |
| Maven profile / `<optional>` | extras 可选依赖组 `.[tencent]` | 安装方决定装不装 |
| record / Lombok `@Value` | `@dataclass(frozen=True)` | 自动生成样板代码 + 不可变 |
| `@ConfigurationProperties` | 环境变量 + dataclass 模块单例 | 12-Factor，启动即求值 |
| 受检异常 + `throws` 声明 | 一切非受检，EAFP 风格 | 异常是控制流的一部分 |
| `javac` 编译期强类型 | 类型注解 + mypy 渐进类型 | 运行时不校验，CI 门禁才硬 |
| Checkstyle / SpotBugs | ruff（lint + format 一体） | 配置直接进 pyproject |
| JUnit5 + Mockito | pytest + monkeypatch / fixtures | 见测试篇 |
| JVM 线程 + JMM | GIL + asyncio 事件循环 | 见异步篇 |
| Spring MVC + Bean Validation | FastAPI + pydantic | 见常用库篇 |

对照表的完整版与逐条展开见开篇：[从Java视角看Python工程化-开篇.md](从Java视角看Python工程化-开篇.md)。

## 目录大纲与写作计划

### 1. 语言基础 —— 语义差异优先

| 拟写篇目 | Java 锚点 | 素材落点 |
|---|---|---|
| 数据模型与 dunder 协议 | `Object.equals/hashCode/toString`、Comparable | voice-utils `domain/` |
| 推导式与生成器 | Stream API 惰性求值 / 短路操作 | voice-utils `pipeline/` |
| 装饰器 | 注解 + AOP 动态代理的本质区别 | ai-service 路由与日志装饰 |
| 模块与包、import 机制 | classpath / JPMS / 类加载 | 两个项目的 src 布局（src layout vs flat layout） |
| 可变默认参数等「Python 味」陷阱 | Java 无此语义，靠对照强化记忆 | 踩坑复盘 |

写作要点：每篇开头一张 Java↔Python 行为对照表，中间讲 CPython 实现层面的为什么（对照 JVM），结尾附面试追问。

### 2. 工程化 —— 本轨道主干（首篇已发）

| 拟写篇目 | Java 锚点 | 素材落点 |
|---|---|---|
| **开篇：从 Java 视角看 Python 工程化**（已发） | 总纲：pyproject/异常/配置/类型/钉版 | 两项目混合 |
| pyproject 依赖管理深读 | Maven 依赖调解 nearest-wins、scope | voice-utils `pyproject.toml` 的钉版注释 |
| 异常体系设计 | 受检异常之争、BusinessException 军规 | voice-utils `exceptions.py` |
| 配置管理三态（dev/test/prod） | profile + 配置中心 | ai-service `config.py` 沙箱模式开关 |
| 类型注解与 mypy 实战 | javac 强类型、泛型擦除 | 两项目注解风格 |
| ruff 与代码规范门禁 | Checkstyle/SpotBugs 进 CI | voice-utils `[tool.ruff]` + Makefile |

### 3. 异步 —— 对照 Java 并发模型是主线索

| 拟写篇目 | Java 锚点 | 素材落点 |
|---|---|---|
| GIL 是什么、为什么还在 | JVM 线程模型 + JMM 可见性 | 概念篇，对照虚拟线程前景 |
| asyncio 事件循环 | Netty EventLoop / 线程池模型 | ai-service uvicorn 启动链路 |
| async/await 与 FastAPI 并发 | Servlet 线程模型 vs Reactive | ai-service `api/`（真实 async 端点） |
| httpx 异步客户端 | WebClient / HttpClient | ai-service LLM 调用 |

写作要点：站长已有 Java 并发深度（AQS/虚拟线程），这一部分的差异化价值是「**同一并发问题两种语言的两套答案**」，追问要打到调度层面。

### 4. 测试

| 拟写篇目 | Java 锚点 | 素材落点 |
|---|---|---|
| pytest fixtures | `@BeforeEach` + Spring 测试上下文 | ai-service 沙箱模式（mock LLM 零费用）可测性 |
| monkeypatch 与打桩 | Mockito mock/stub | 环境变量注入的测试法 |
| 参数化与数据驱动 | `@ParameterizedTest` | voice-utils ASR 多引擎 |
| 覆盖率与 CI 门禁 | JaCoCo 阈值 | ruff + pytest 进 Makefile/CI |

### 5. 常用库 —— 只写生产在用的

| 拟写篇目 | Java 锚点 | 素材落点 |
|---|---|---|
| FastAPI + pydantic | Spring MVC + Bean Validation | ai-service `api/`、`schemas.py` |
| pydantic v2 数据校验 | Hibernate Validator 注解体系 | ai-service 请求/响应建模 |
| httpx / aiohttp | RestTemplate/WebClient 选型 | ai-service LLM 接入层 |
| LangChain/LangGraph 工程侧 | ——（与 [B-AI工程](../../B-AI工程/README.md) 联动，不重复） | ai-service `agents/`（5 个真实智能体） |
| sqlite3 与本地存储 | JDBC / MyBatis | voice-utils `storage/` 底账 |

## 素材来源（两个生产项目）

**原则与 java 轨道一致：每个结论都要能在生产代码里指出对应文件。**

### voice-utils（终端语音工程，即库 README 中的 voice-zcode，项目已更名、类名仍保留 VoiceZcodeError）

- 路径：`/Users/guoyangyang/develop/voice-utils`
- 可挖素材：`pyproject.toml`（依赖区间约束 + 平台 wheel 钉版注释、`[tencent]`/`[macos]` 可选依赖组、ruff 配置、CLI 入口）、`src/voice_utils/domain/exceptions.py`（两层异常体系，message/detail 分流）、`src/voice_utils/`（asr/tts/agent/pipeline/storage 分层）、`Makefile` + `.venv` + `install.sh`（虚拟环境工作流）、`config.yaml` + pyyaml（本地配置加载）

### star-site-platform-ai-service（家庭健康平台 AI 服务，Java 微服务体系中的 Python 节点）

- 路径：`/Users/guoyangyang/develop/star-site-platform-ai-service`
- 可挖素材：`app/config.py`（frozen dataclass + 环境变量驱动 + 无 key 自动沙箱模式）、`requirements.txt`（8 依赖全量 `==` 钉版，Docker 部署可复现）、`app/agents/`（assistant/报告解读/财务诊断/OCR 等 5 个 LangGraph 智能体）、`app/core/security.py`（JWT 自行验签，与 Java 网关体系对接）、`Dockerfile`
- 诚实备注：该服务尚未迁移 pyproject（仍是 requirements.txt），voice-utils 也还没有锁文件——**现状本身就是写作素材**：两种钉版策略的取舍会写成复盘，改进后再回写对比篇。

## 维护规约

1. 一篇一 commit，格式 `新增: python/<篇名>`。
2. 每篇文末标注 `> 2026-xx 新增（全栈扩展 · 素材来自 voice-zcode / ai-service 生产代码）。`
3. LangChain/Agent/RAG 的模型侧深读归 [B-AI工程](../../B-AI工程/README.md)，本轨道只写其「Python 工程侧」（依赖、异步、测试）。
4. 季度自测时对照 [自测清单.md](../../自测清单.md) 补 Python 条目。

> 2026-10 新增（全栈扩展）。
