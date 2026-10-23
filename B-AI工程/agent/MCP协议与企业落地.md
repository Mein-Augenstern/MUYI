MCP 协议与企业落地
====

MCP（Model Context Protocol，Anthropic 2024-11 开源）是 Agent 与外部世界之间的接入层开放协议——把"模型接工具"从各应用私有造轮子收敛成一套标准，地位类比 USB-C 之于外设。一句话定位：**工具接入的 M×N 难题被降为 M+N，2026 年已是事实标准**——官方 Registry 约 9650 个 server，TS+Python SDK 月下载约 9700 万，约 28% 的 Fortune 500 已部署。面试价值双高：协议模型（角色/原语/传输）考理解深度，企业落地（网关/鉴权/审计/版本治理）考架构功力，一题两层。"模型怎么决定调工具"的循环机制先看 [ReAct与LangGraph工程实践](./ReAct与LangGraph工程实践.md)，本篇讲"工具从哪来、长什么样、怎么安全地接进来"。

1 为什么需要 MCP：从 M×N 到 M+N
------

没有标准协议时，M 个应用（Host）要接 N 个数据源与工具，每个应用为每个工具写一套私有接入：M×N 份胶水代码，格式、鉴权、部署形态互不兼容，工具方也要为每个平台重复适配。MCP 把它降到 M+N：

* 工具方只实现一次 **MCP Server**，按协议暴露能力；
* 应用方只实现一次 **MCP Client**，即可发现并调用任意 Server；
* 换模型、换应用，接入层资产不动。

面试第一道分水岭，是把它和两个近亲概念讲清：

| | function calling | 平台插件机制 | MCP |
|---|---|---|---|
| 本质 | 模型能力：决定"调什么、传什么参" | 单一平台私有的分发机制 | 开放协议：工具如何被发现、描述、调用、鉴权 |
| 归属 | 各厂商模型 API 各自的格式 | 绑定特定平台/商店 | 中立于模型厂商与应用平台 |
| 与 MCP 关系 | 互补：MCP 把工具递给模型，function calling 决定怎么调 | 被开放标准挤压 | 底座 |

一句话：**function calling 在推理层（模型会不会用工具），MCP 在接入层（工具从哪来、怎么安全接入）**——不是竞争关系，是上下层。

2 核心模型：角色、传输与三原语
------

消息格式是 JSON-RPC 2.0，角色三分：

* **Host**：用户直接使用的应用（编程助手、IDE 插件、企业门户），拥有会话与最终决策权；
* **Client**：Host 内部的连接器，与一个 Server 保持 1:1 连接，负责协议消息收发与能力协商；
* **Server**：能力提供方，封装数据库、文件系统、内部 API，也可以只是薄薄一层代理。

```
┌─────────────────── Host（编程助手 / 企业门户）───────────────────┐
│      会话循环：模型推理 + function calling                        │
│           │ tools/list ── tools/call                            │
│   ┌───────┴──────┐  ┌──────────────┐  ┌──────────────┐         │
│   │   Client A   │  │   Client B   │  │   Client C   │  各 1:1  │
│   └───────┬──────┘  └───────┬──────┘  └───────┬──────┘  能力协商│
└───────────┼─────────────────┼─────────────────┼─────────────────┘
      stdio │        streamable│HTTP       streamable│HTTP
    ┌───────┴──────┐  ┌────────┴───────┐  ┌────────┴───────┐
    │  Server A    │  │    Server B    │  │    Server C    │
    │ 本地文件/git  │  │   内部 API 封装 │  │  企业 MCP 网关  │
    └──────────────┘  └────────────────┘  └────────────────┘
```

初始化握手时双方交换 capabilities，做**能力协商**：各自声明支持什么（是否提供 tools/resources/prompts、是否支持 sampling、roots 等扩展），之后的交互只落在交集内——这是协议向前兼容的锚点，也是第 4 节版本治理的抓手。

三大原语，区别在**控制权归属**：

| 原语 | 谁控制 | 定位 | 典型例子 |
|---|---|---|---|
| tools | 模型决定调用 | 可执行操作（可含写副作用） | 查体检报告、创建提醒 |
| resources | 应用决定加载 | 只读数据，注入上下文 | 文件内容、DB 记录、配置 |
| prompts | 用户主动触发 | 预置提示词模板 | "/总结这个文件" |

传输两种形态：

* **stdio**：Host 以子进程方式拉起 Server，JSON-RPC 走标准输入输出——本地工具最简形态，无网络暴露面；
* **Streamable HTTP**：远程 Server 的标准形态，单一 HTTP 端点承载请求与流式响应（2025-06-18 版规范引入，取代初版 HTTP+SSE 双端点设计；当前规范版本为 2026-07-28）。

3 Java 侧工程：Spring AI 的 MCP 一等支持
------

生态位先钉死：**MCP 官方 Java SDK 由 Spring 团队官方维护（当前 1.0.x），Python SDK 已演进到 ~1.27**；Spring AI 自 1.0（2025-05 GA）起提供 client/server 一等 starter 支持，Spring AI 2.0（2026-06 GA）明确要求 MCP Java SDK 1.0+。对 Java 团队的含义：接 MCP 不需要引入 Python 设施，Spring Boot 的自动装配套路直接适用。

客户端要点（spring-ai-starter-mcp-client）：

* starter 自动配置连接与 **McpSyncClient**（McpAsyncClient 的同步门面；多数业务拿同步封装即可，要非阻塞再下沉 async）；
* 连接配置按 transport 分族：stdio 指定命令拉起本地进程，streamable HTTP 指定 endpoint（属性族随版本演进，以所用版本文档为准）；
* Server 暴露的 tools 映射为 Spring AI 的 ToolCallback，直接注入 ChatClient——对上层而言，MCP 工具与本地 @Tool 工具无差别。

```yaml
spring:
  ai:
    mcp:
      client:
        stdio:                          # 本地进程型 server
          connections:
            star-site:
              command: uv
              args: [ "run", "servers/star-site/server.py" ]
        # 远程 server 走 streamable HTTP 连接族，配置 endpoint 即可
```

服务端要点（spring-ai-starter-mcp-server / -server-webmvc）：

* **@Tool 注解方法直接变成 MCP tools**：函数名映射工具名，description 映射工具描述，@ToolParam 描述参数；
* 工具描述纪律与 LangChain 的 docstring 纪律同构——模型对工具的全部认知就是这段描述，含糊则该调不调、不该调乱调（见 [ReAct与LangGraph工程实践](./ReAct与LangGraph工程实践.md)）；
* 纯 stdio 适合本机集成，webmvc/webflux starter 提供 HTTP 传输形态。

```java
@Component
public class ReminderTools {

    @Tool(description = "为指定家庭成员创建提醒，成功返回提醒 id。需要成员 id 与提醒内容。")
    public String createReminder(
            @ToolParam(description = "家庭成员 id") int memberId,
            @ToolParam(description = "提醒内容") String content) { /* ... */ }
}

@Bean
ToolCallbackProvider reminders(ReminderTools tools) {
    return MethodToolCallbackProvider.builder().toolObjects(tools).build();
}

// 客户端侧：starter 把 Server 发现的工具汇成 ToolCallbackProvider，直接进 ChatClient
@Bean
ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider mcpTools) {
    return builder.defaultTools(mcpTools).build();
}
```

4 企业落地四件套：网关、鉴权、审计、版本治理
------

个人把 server 用 stdio 拉起就完事；企业里若 N 个 server 直接暴露给每个 Host，等于把接入、鉴权、审计、升级四件脏活重复 M 次。标准落法：

* **网关聚合**：Host 只连一个企业 MCP 网关，网关后聚合多个内部 server——工具名加前缀防碰撞、按团队/场景分组下发、健康检查与熔断；接入配置从"M 应用 × N server"收敛为"一处网关"；
* **鉴权边界**：远程传输按规范走 OAuth2 授权框架；企业典型边界是 MCP server 只信任网关/IdP 签发的 token，用户 SSO 身份经网关透传，工具调用以"用户 + 应用"双主体记权。身份不经过模型、不进提示词，这条红线详见 [智能体的身份链与工具安全](./智能体的身份链与工具安全.md)；
* **审计日志**：tools 调用是特权操作：记"哪个用户/哪次会话/哪个工具/入参出参摘要/耗时/结果码"，写操作单独告警，入参出参做脱敏（凭据、健康数据）；网关与 server 两层各记一份，防单点绕过；
* **版本治理**：规范两代并存是现实——2025-06-18 与 2026-07-28（当前版），靠 initialize 能力协商互通；工程动作：SDK 版本锁死（Java 侧 1.0.x 对齐 Spring AI 2.0）、升级前 diff 规范 changelog、旧 HTTP+SSE transport 排下线时间表、协议版本差异尽量在网关收敛。

5 生产反哺：一个单人维护的 MCP 服务
------

四件套看着重，缩小一号就是个人项目的最佳实践。作者自己维护的一个 MCP 服务，文件形态是 `servers/star-site/server.py`：

* **FastMCP 单文件**：一个 server.py 承载全部逻辑，装饰器注册工具，7 个工具覆盖 member/health/reminder 三类域的读与写；
* **PEP 723 内联依赖**：依赖声明写在文件头的 script metadata 块，`uv run servers/star-site/server.py` 直接拉起——不建 venv、不写 requirements，环境自描述、任何机器可复现；
* **默认网关 8080**：MCP 服务不直接对外，统一收在站点默认网关（8080）入口之后。

```python
# servers/star-site/server.py（形态示意）
# /// script
# dependencies = ["fastmcp>=2"]     # PEP 723：内联依赖，uv run 直接跑
# ///
from fastmcp import FastMCP

mcp = FastMCP("star-site")

@mcp.tool
def list_members() -> str:
    """列出家庭成员及 id。"""          # 描述给模型读，纪律同 docstring
    ...
```

拿它反照第 4 节，四个要点全部成立，只是尺寸不同：

* 网关收敛：一个 8080 入口比多个散落端口好管——个人版"网关聚合"；
* 鉴权外置：7 个工具全部复用网关既有会话鉴权，MCP 服务自身零鉴权代码；
* 最小权限的粗粒度版：读工具放开，写工具走网关侧确认，读写分开注册；
* 版本治理微缩版：PEP 723 锁依赖 + uv run 可复现，等价于"SDK 版本锁死"。

uv 与 PEP 723 的工程化展开见 A 轨 [从Java视角看Python工程化-开篇](../../A-后端内核/python/从Java视角看Python工程化-开篇.md)。

面试追问链
------

* MCP 解决什么问题？→ 把工具接入的 M×N 矩阵降为 M+N，类比 USB-C；
* 和 function calling 什么关系？→ 接入层 vs 推理层，互补不竞争；
* 三个角色？→ Host/Client/Server；Client 与 Server 是 1:1 连接；
* 三原语及控制方？→ tools 模型控制、resources 应用控制、prompts 用户控制；
* 传输有几种？→ stdio（本地子进程）与 streamable HTTP（远程；2025-06-18 取代 HTTP+SSE，当前规范 2026-07-28）；
* 版本兼容怎么保证？→ initialize 能力协商，交互只落在双方声明的交集内；
* Java 侧怎么接？→ Spring AI 1.0（2025-05 GA）起 starter 一等支持，McpSyncClient + @Tool 映射；Spring AI 2.0（2026-06 GA）要求 Java SDK 1.0+，SDK 由 Spring 团队官方维护；
* 企业落地第一件事？→ 网关聚合，把 N 个 server 收敛成单入口；
* 鉴权怎么做？→ HTTP 传输走 OAuth2，SSO 边界在网关，双主体记权，身份不进模型；
* 审计记什么？→ 用户/会话/工具/入参出参摘要/耗时/结果码，网关与 server 双层各记；
* 生态规模多大？→ Registry 约 9650 个 server，TS+Python SDK 月下载约 9700 万，约 28% 的 Fortune 500 已部署。

面试官视角小结
------

这题分层信号很清晰：只会说"MCP 是接工具的协议"是及格线；能画出 Host/Client/Server、说清三原语的控制权差异与能力协商，说明读过规范；能把 function calling、平台插件与 MCP 的层次讲对，并给出 stdio 与 streamable HTTP 的选型理由，是有架构视角的信号；再往下，主动展开企业四件套——网关聚合、OAuth2/SSO 边界、双层审计、两代规范的版本治理——并能落到 spring-ai-starter-mcp-client/server、McpSyncClient、@Tool 映射的具体用法，才是"真接过"的证据。最强的候选人还会把个人级实践（FastMCP 单文件、PEP 723 可复现、8080 网关收敛）与企业级方案对上号，说明他理解四件套不是教条，而是同一组问题在不同规模下的同构解。

> 2026-10 新增（2026 技术栈补全）。
