从 Java 视角看 Python 工程化（开篇）
====

> 源码出处：`voice-utils`（终端语音工程，即库总 README 里的 voice-zcode——项目已更名，异常类名仍保留 VoiceZcodeError）。以及 `star-site-platform-ai-service`（家庭健康平台的 Python AI 服务，下文简称 ai-service）。
> 关键文件：`voice-utils/pyproject.toml`、`voice-utils/src/voice_utils/domain/exceptions.py`、`star-site-platform-ai-service/app/config.py`、`star-site-platform-ai-service/requirements.txt`。
> 写法：每节三段式——**Java 怎么做 / Python 怎么做 / 我的生产项目里怎么落的**。这不是语法扫盲，是「带着 Java 肌肉记忆把 Python 写上生产」的迁移笔记。

Java 工程师写 Python，最先翻车的是「翻译腔」
------

用 Java 直觉写 Python，典型症状三种：

- **万事皆类**：写个配置要抽象工厂，两行功能包五层。Python 的模块（module）本身就是命名空间 + 单例，`from app.config import settings` 就是依赖注入。
- **LBYL 防御式编程**：先 `if key in dict` 再取值。Python 文化是 EAFP（Easier to Ask Forgiveness than Permission）——直接取，`except KeyError` 兜底，这是官方文档背书的主流风格。
- **拿 static 的警惕心看待一切全局**：Java 里全局可变状态是事故源。Python 的模块级 `settings = Settings()` 单例反而是惯用法——前提是 frozen dataclass 保证了不可变。

所以本轨道的方法论是对照表式迁移：先建立概念映射，再刻意找差异点。最后追问差异背后的生态原因。本文覆盖工程化五件套：依赖、异常、配置、类型、环境与钉版。

一、依赖管理：pyproject.toml 对照 Maven
------

**Java 怎么做**：`pom.xml` 用 GAV 坐标声明依赖，`dependencyManagement` 统一版本。`scope`（provided/test）与 `<optional>` 控制传递性，profile 按环境切换依赖集合。`mvn package` 产出跨平台的 jar——JVM 字节码不挑操作系统，二进制兼容性是 Maven 几乎不用操心的事。

**Python 怎么做**：PEP 621 把元信息标准化进 `pyproject.toml` 的 `[project]` 表。依赖是**区间约束**（`>=0.60`）而非精确版本。`[project.optional-dependencies]` 定义可选依赖组（extras），安装方用 `pip install voice-utils[tencent]` 决定装不装。构建后端声明在 `[build-system]`，类比 Maven 插件体系但可替换（hatchling/uv 等）。

**我的生产项目里怎么落的**（`voice-utils/pyproject.toml`，节选）：

```toml
[project]
name = "voice-utils"
version = "0.2.0"
requires-python = ">=3.10"      # 类比 maven.compiler，但管的是解释器版本
dependencies = [
    "textual>=0.60",
    # onnxruntime 1.20+ 无 x86_64 wheel，钉 1.19
    "onnxruntime>=1.17,<1.20",
    # av 的 13+ 无 Intel Mac wheel，钉在 12.x（faster-whisper 的传递依赖）
    "av>=11,<13",
    "edge-tts>=6.1",
]

[project.optional-dependencies]
# 腾讯云一句话识别（微信同源引擎，1~2s；需 TENCENT_SECRET_ID/TENCENT_SECRET_KEY 环境变量）
tencent = ["tencentcloud-sdk-python-asr>=3.0.1300"]
dev = ["ruff>=0.4"]

[project.scripts]               # 装完包直接有 CLI 命令，类比 fat jar + 启动脚本的替代物
voice-utils = "voice_utils.__main__:main"
vu = "voice_utils.__main__:main"
```

三处需要重建认知的点：

- **extras ≠ Maven profile**。profile 是构建期开关，extras 是安装期选择——`pip install .[tencent]` 之后，运行时代码靠 `try: import ... except ImportError` 走降级路径。voice-utils 的本地 whisper 兜底 / 腾讯云增强两条识别链路就是这么切的。
- **钉版注释写的是平台 wheel 缺失**。`onnxruntime>=1.17,<1.20` 不是版本洁癖：Intel Mac 上 1.20+ 没有 x86_64 wheel，装不上就是装不上。jar 没有这个问题，Python 的 C 扩展是平台二进制——Java 转 Python 最容易低估的差异。
- **`[project.scripts]` 生成命令行入口**。装完包 `vu` 命令直接可用；对照 Java 打 fat jar + 启动脚本（或 jpackage）的链路，轻了一个量级。

二、异常体系：自定义根异常，对照「受检异常之争」
------

**Java 怎么做**：受检异常强制 `throws` 声明或捕获，编译器把关。但社区二十年的结论是它导致过度包装、破坏 Lambda 与流式 API，Kotlin/Scala 直接不实现。业务侧成熟实践：`BusinessException` 根异常 + `ErrorCode` 枚举 + 全局 `@RestControllerAdvice` 统一出口（star-site 的 CommonError 军规同源）。

**Python 怎么做**：**所有异常都是非受检的**，没有 throws 声明，没有编译器逼你处理。官方文化是 EAFP——`KeyError`/`FileNotFoundError`/`StopIteration` 这些在 Java 里算事故的异常，在 Python 里是正常控制流。代价是：异常出口完全靠工程自觉，不设计就会满屏裸抛第三方异常。

**我的生产项目里怎么落的**（`voice-utils/src/voice_utils/domain/exceptions.py`，节选）：

```python
class VoiceZcodeError(Exception):
    """产品根异常。message 面向用户可读（中文），detail 保留底层信息供日志。"""

    def __init__(self, message: str, *, detail: str = "") -> None:
        super().__init__(message)   # 让 str(exc) 和 traceback 直接可读
        self.message = message      # 给用户看的：UI 直接展示
        self.detail = detail        # 给排查者看的：原始异常文本，只进日志

class ConfigError(VoiceZcodeError):
    """配置缺失/非法（找不到文件、YAML 解析失败、顶层类型不对等）。"""

    pass

class AudioError(VoiceZcodeError):
    """麦克风/播放设备问题（系统未授权、设备打不开等）。"""

    pass

# AsrError（语音识别）/ AgentError（指令派发）/ StorageError（底账读写）同构，均为空壳子类
```

设计要点逐条对照 Java：

- **两层就够**：一个根 + 一层分类。UI 层 `except VoiceZcodeError` 一个根类型兜住全部预期内错误（类比全局异常处理器只认 BusinessException）。想精细处理再 except 具体子类——粗细两档，收放自如。新增错误类别 = 加一个空子类，零成本。
- **message/detail 分流 == Java 的 errMsg + cause**：用户和排查者要的信息完全不同，分流后 UI 永远干净、日志永远完整。Python 版多做了一步：`detail` 放在 `*` 之后（keyword-only 参数），调用处必须写 `detail=...`，两个都是 str 的参数不会被位置传反。Java 没有对应语法，只能靠构造器重载或 builder 防。
- **根异常划清「预期内错误」与「程序 bug」的边界**：能被 VoiceZcodeError 家族接住的是业务错误。穿透出去的裸异常就是 bug。两者的处理策略（提示用户 vs 修代码）完全不同。与 Java 侧「不许裸抛底层异常到 UI 层」的军规完全同构。

三、配置管理：frozen dataclass 对照 Lombok/record
------

**Java 怎么做**：Spring 体系用 `@ConfigurationProperties` + `@Validated`，启动时从 Environment（yml/环境变量/Nacos）绑定到 POJO。不可变诉求用 record 或 Lombok `@Value`。单例语义由容器保证。

**Python 怎么做**：没有容器，惯用法是 `@dataclass(frozen=True)` + 模块级实例。dataclass 自动生成 `__init__/__repr__/__eq__`（等价 Lombok 一揽子样板代码）。`frozen=True` 让实例不可变，赋值直接抛 `FrozenInstanceError`（等价 record 的 final 语义）。模块底部的 `settings = Settings()` 就是全局单例，各处 `from app.config import settings` 导入同一个对象。模块天然单例，等价 Spring 单例 Bean。

**我的生产项目里怎么落的**（`star-site-platform-ai-service/app/config.py`，节选）：

```python
def _env(key: str, default: str = "") -> str:
    value = os.environ.get(key)
    if value is not None:
        value = value.strip()
    return value if value else default    # 空串视为未设置：K8s 里 ENV_VAR= 是常态

@dataclass(frozen=True)                    # frozen=True：运行期改配置直接抛异常
class Settings:
    service_name: str = _env("SERVICE_NAME", "star-site-platform-ai")
    port: int = int(_env("AI_PORT", "8091"))
    jwt_secret: str = _env("JWT_SECRET", "...")          # 必须与 Java 网关一致，验签才过
    llm_api_key: str = _env("AI_LLM_API_KEY", "")        # 为空自动进沙箱模式
    llm_vision_model: str = _env("AI_LLM_VISION_MODEL", "glm-4v-flash")

settings = Settings()   # 模块级单例：改环境变量重启即生效，代码不用动
```

值得展开的三个点：

- **`_env` 把空串当未设置**是真实生产坑：Docker Compose/K8s 里很容易出现 `ENV_VAR=`（等号后为空）。`os.environ.get` 返回 `""` 而非 `None`，「已设置」的假象就这么来的。还有引号残留问题，所以顺手 `strip()`。统一在一个函数兜底，胜过每个调用点各自判。
- **字段默认值在 import 时求值**——与 Spring 最大的语义差异：Spring 绑定时机可控，而 Python 的 `settings` 在第一次 import 时定型。好处是直给（改环境变量重启即生效）。代价是测试里改配置得重新构造实例或 monkeypatch，不会因为改了 `os.environ` 就自动重绑。
- **无 key 自动沙箱**：`effective_mock` 属性按优先级判断。显式开关（`AI_MOCK_ENABLED`）优先，未设置时看有无 API key，没 key 就自动沙箱（不调真实模型、零费用），全链路照样跑通。对照 Spring 的 `@ConditionalOnProperty` 条件装配：Python 没有注解魔法，一个 property 方法把装配逻辑写明白，反而更好调试。

四、类型注解与 mypy：渐进类型对照 javac 强类型
------

**Java 怎么做**：`javac` 编译期强类型，类型错误编不过去，IDE 红线即门禁；泛型运行时擦除但编译期已校验。强类型是强制项，不是选择项。

**Python 怎么做**：类型注解是**渐进类型（gradual typing）**——PEP 484 注解在运行时只是元数据，解释器完全不校验。要强度靠 mypy/pyright 静态检查 + CI 门禁。`int | None`（3.10+ 语法）对应 Java 的可空性焦虑，但 None 是真单例对象，不存在 Optional 强制拆箱。

**我的生产项目里怎么落的**：两个项目都是「全量注解 + ruff 静态检查」组合。`config.py` 每个字段显式标类型（`port: int = int(_env(...))`、`detail: str = ""`）。`exceptions.py` 首行 `from __future__ import annotations` 让注解延迟求值（存为字符串、运行时零开销，才能前向引用尚未定义的类型）。工具链配置直接住在依赖声明隔壁：

```toml
# voice-utils/pyproject.toml
[tool.ruff]
line-length = 110
target-version = "py310"

[tool.ruff.lint]
select = ["E", "F", "I", "UP", "B"]   # pycodestyle / pyflakes / import 排序 / 语法升级 / 常见 bug
```

ruff 一个工具吃了 Checkstyle（E/F）+ SpotBugs（B）+ import order 的活，而且快到能进 Makefile 每次提交前跑（`.venv/bin/ruff check src/`）。实践结论：注解的价值不在「编译」（Python 没有这关），在 **IDE 补全/重构/跳转**与 **review 时类型即文档**。写不写注解，`settings.` 之后有没有补全是两种语言。

五、虚拟环境与钉版：.venv 与 requirements==，对照锁文件
------

**Java 怎么做**：Maven 没有 lock 文件——因为不需要：版本锁在 `dependencyManagement`，依赖是每个项目自带的 jar。classpath 天然按项目隔离，冲突靠 nearest-wins 调解。「全局共享依赖」这个概念在 JVM 世界不存在。

**Python 怎么做**：`pip install` 默认装进**全局 site-packages**，多项目互相污染——所以虚拟环境（`.venv`）是生存必需而非最佳实践。依赖复现靠钉版：`requirements.txt` 全量 `==`（传统派），或 pyproject + 锁文件 `uv.lock`/`poetry.lock`（新一代，锁传递依赖、跨平台）。

**我的生产项目里怎么落的**：两个项目两种风格，恰好是钉版光谱的两端：

```text
# star-site-platform-ai-service/requirements.txt —— 8 个依赖全量 ==，Docker 部署追求可复现
fastapi==0.115.6
uvicorn[standard]==0.34.0
httpx==0.28.1
PyJWT==2.10.1
pydantic==2.10.4
langchain-core==0.3.29
langchain-openai==0.3.0
langgraph==0.2.62
```

- ai-service：`==` 全钉 + Dockerfile 构建镜像。LangChain 系 API 变动快，半锁定等于不锁定；服务器跑的版本和 CI 测的版本必须一致——这套组合等价于「dependencyManagement 锁版本 + 镜像固化」。注意 `uvicorn[standard]` 的方括号就是第一节说的 extras，同一个机制在声明依赖时也能用。
- voice-utils：pyproject 写区间约束 + 注释写钉版原因（平台 wheel）。`.venv` + `install.sh` 一键装环境（Makefile 的 `install` 目标）。Makefile 里所有命令走 `.venv/bin/...` 绝对路径，不依赖用户激活了环境。CI 与本地共用一套入口。
- 诚实欠账：voice-utils 没有锁文件，传递依赖（faster-whisper 带进来的一串）未锁，换机器可能漂移。单人工具项目的有意取舍（依赖少、平台单一），但协作者或 CI 矩阵一上来就必须补 `uv.lock`——复盘另写一篇。

总结与面试自测
------

工程化五件套的一句话版：

- **依赖**：pyproject 区间约束 + extras 按需安装；C 扩展是平台二进制，wheel 缺失时钉版没得商量（jar 无此问题）。
- **异常**：无非受检异常、无 throws，EAFP 是官方文化。出口纪律靠自定义根异常——两层层级 + message/detail 分流，与 Java BusinessException 军规同构。
- **配置**：frozen dataclass + 模块单例，对照 record + Spring 单例 Bean；差异在 import 期求值、无容器重绑。
- **类型**：注解不校验运行时，价值在 IDE 与协作；mypy/ruff 是自己的 javac。
- **环境**：全局 site-packages 决定了 .venv 是必需品；钉版光谱从 requirements== 到 uv.lock，Docker 场景全钉保可复现。

面试追问（自测）：

- pyproject 的 extras 和 Maven profile/optional 的本质区别？（安装期选择 vs 构建期开关）
- Python 为什么敢不设受检异常？EAFP 与 LBYL 各自适用什么场景？
- `@dataclass(frozen=True)` 与 Java record 的异同？（样板生成 vs 语言级；相等性语义；import 期求值）
- 类型注解运行时不校验，为什么生产项目还要求全量注解？
- 为什么 Python 必须虚拟环境而 Java 不用？`==` 钉版与锁文件的差距在哪？
- onnxruntime 为什么钉 `<1.20`？（x86_64 wheel 缺失——C 扩展平台二进制 vs JVM 字节码跨平台）

> 2026-10 新增（全栈扩展 · 素材来自 voice-zcode / ai-service 生产代码）。
