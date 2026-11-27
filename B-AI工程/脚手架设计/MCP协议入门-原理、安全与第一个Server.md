MCP协议入门-原理、安全与第一个Server
====

> 定位：「脚手架设计」系列第 6 篇。上一篇讲技能，扩展的是智能体"知道什么"；这一篇讲 MCP（Model Context Protocol），扩展的是它"能调什么"。本库已经有一篇从企业落地角度讲 MCP 的文章（[MCP协议与企业落地](../agent/MCP协议与企业落地.md)：为什么要它、三种角色、网关与鉴权、审计、版本治理），本篇不重复那些，补的是协议本身：一次连接怎么工作、2026 年这次大改改了什么、传输和授权、官方的安全清单，最后动手写一个能跑的 server。文中的协议输出都是我在 2026-11-27 用 Python SDK 实际跑出来的。
>
> **内容来源**：本篇为学习整理，综合参考（版权归原作者所有）：
> - MCP 官方文档：[介绍](https://modelcontextprotocol.io/docs/2026-07-28/getting-started/intro)、[规范 2026-07-28（当前版）](https://modelcontextprotocol.io/specification/2026-07-28)、[规范 2025-11-25（上一版）](https://modelcontextprotocol.io/specification/2025-11-25)
> - [Introducing the Model Context Protocol — Anthropic](https://www.anthropic.com/news/model-context-protocol)（2024-11）、[Code execution with MCP — Anthropic](https://www.anthropic.com/engineering/code-execution-with-mcp)（2025-11）
> - [Linux Foundation：成立 Agentic AI Foundation 的公告](https://www.linuxfoundation.org/press/linux-foundation-announces-the-formation-of-the-agentic-ai-foundation)（2025-12）、[OWASP MCP Top 10](https://owasp.org/www-project-mcp-top-10/)
> - [MCP Python SDK](https://github.com/modelcontextprotocol/python-sdk)（v2 的 README 与文档）、Claude Code 官方文档 [Connect Claude Code to tools via MCP](https://code.claude.com/docs/en/mcp)
>
> 实测环境：Python 3.11.15，`mcp` 2.3.0。规范每半年更新一次，文中细节以 2026-07-28 版为准，勘误请对照官方文档。

一、MCP 是什么
------

官方的定义只有一句：**MCP 是一个开源标准，用来把 AI 应用连接到外部系统。** 官方文档打的比方是 AI 应用的 USB-C 接口：就像 USB-C 给电子设备提供了一种统一的连接方式，MCP 给 AI 应用提供了一种连接外部系统的统一方式。

它由 Anthropic 在 2024 年 11 月开源。没有它的时候，每接一个数据源都要单独写一套接入，系统一多就难以为继；有了它，工具方实现一次 server，应用方实现一次 client，之后任意两边都能对接。规范自己说，这个设计借鉴了语言服务器协议（LSP）——后者让一门编程语言的支持在整个开发工具生态里通用。

一年多以后，Anthropic 把 MCP 捐给了 Linux 基金会新成立的 Agentic AI Foundation。基金会的白金会员包括 AWS、Anthropic、Block、Bloomberg、Cloudflare、Google、Microsoft 和 OpenAI。成立公告里的数字是：已发布的 MCP server 超过 1 万个，Claude、Cursor、Microsoft Copilot、Gemini、VS Code、ChatGPT 等平台都已采用。

还有一点常被误解：MCP 管的只是上下文交换这一层，**不规定应用怎么使用模型、怎么管理上下文**。会话循环、提示词、工具结果怎么用，仍然是宿主自己的事——也就是前面几篇讲的脚手架。

二、一次连接的生命周期：先分清两代协议
------

读 MCP 的资料时最容易迷路的地方是：网上多数教程、以及现网的大量 SDK 和 server，讲的是 2025-11-25 及更早的做法；而当前规范是 2026-07-28，这一版做了一次不兼容的大改。规范自己把这两代叫作"旧版"（legacy）和"新版"（modern）：

| | 旧版（2025-11-25 及更早） | 新版（2026-07-28 起） |
|---|---|---|
| 怎么开始 | 先握手：客户端发 `initialize` 请求，服务器回答自己支持的版本和能力，客户端再发一条 `initialized` 通知 | 没有握手。每个请求自带协议版本和客户端能力 |
| 状态 | 有会话。HTTP 传输下用 `Mcp-Session-Id` 头标识 | 无状态。每个请求自包含，服务器不能从之前的请求推断状态 |
| 服务器能否主动发请求 | 能。让模型补全、读取工作目录、向用户要信息，都靠这个 | 不能。需要更多信息时，在结果里返回一个"需要输入"，客户端带着信息重试原请求 |
| HTTP | 请求走 POST，另有 GET 流 | 只有 POST，GET 和 DELETE 返回 405 |
| 版本不支持时 | 服务器回答自己支持的另一个版本 | 返回"不支持的协议版本"错误 |

同一份 server 代码可以同时服务两代。我用 SDK 2.3.0 写的示例（见第五节）就是这样：用 `server/discover` 打开，它报告支持的是新版的 `2026-07-28`；用 `initialize` 打开，它就按旧版的 `2025-11-25` 应答。

**新版的打开方式。** 服务器必须实现 `server/discover`，用它公布自己支持的协议版本、能力和身份。调用它是可选的，客户端也可以直接发请求、出了版本错误再处理。实测的返回（节选）：

```json
{"supportedVersions": ["2026-07-28"],
 "capabilities": {"prompts": {"listChanged": true}, "resources": {"subscribe": true, "listChanged": true}, "tools": {"listChanged": true}},
 "instructions": "订单查询演示服务：只能按订单号查状态。",
 "resultType": "complete"}
```

`resultType` 是新版新加的必填字段，`complete` 表示请求成功结束、结果里是最终内容。之后不需要任何握手，直接 `tools/list`、`tools/call` 就行。

**旧版的打开方式。** 生命周期分三步：初始化、运行、关闭。初始化必须是双方的第一次交互，实测的返回（节选）：

```json
{"protocolVersion": "2025-11-25",
 "capabilities": {"prompts": {"listChanged": false}, "resources": {"subscribe": false, "listChanged": false}, "tools": {"listChanged": false}},
 "serverInfo": {"name": "order-demo", "version": ""}}
```

这一步做的是能力协商：双方声明各自支持什么，之后的交互只落在交集里。

**兼容性要心里有数。** 规范里的兼容矩阵写得很直白：新版客户端对只支持旧版的 server，失败；旧版客户端对只支持新版的 server，也失败，因为旧版客户端没有向前兼容的机制。想两头都支持，server 可以两套都实现，按客户端怎么打开来决定行为；stdio 传输下，两代的客户端应该先用 `server/discover` 探一下，出错了再退回 `initialize`。

**还有一批功能被废弃了。** 2026-07-28 版把 Roots、Sampling 和 Logging 标为废弃，把任务（tasks）移出了核心协议。规范承诺被废弃的功能至少保留十二个月，才谈得上移除。规范的发布节奏是每半年一次，一般是三月和九月的最后一个星期二；下一次发布提议定在 2027 年 3 月 30 日。到目前为止的版本依次是：2024-11-05、2025-03-26、2025-06-18、2025-11-25、2026-07-28。

三、三种原语，三种控制权
------

服务器对外提供三种东西，区别在于**由谁决定什么时候用**：

| 原语 | 谁控制 | 是什么 | 怎么用 |
|---|---|---|---|
| 工具（tools） | 模型 | 可执行的函数 | `tools/list` 发现，`tools/call` 调用 |
| 资源（resources） | 应用 | 提供给模型的数据，每个用一个 URI 标识 | `resources/list` 发现，`resources/read` 读取 |
| 提示词（prompts） | 用户 | 预置的提示词模板，由用户挑选 | `prompts/list` 发现，`prompts/get` 取用 |

企业落地那篇有更细的对照表，这里只补三点和写 server 直接相关的：

* **工具的执行错误要交还给模型。** 规范区分了两类错误：协议错误是请求本身的结构有问题；工具执行错误是工具跑起来之后出的错，用结果里的 `isError: true` 报告。客户端应该把执行错误交给模型，让它有机会自己纠正。
* **注解只是提示，不是安全边界。** 工具可以带几个注解：`readOnlyHint`（默认为假）、`destructiveHint`、`idempotentHint`、`openWorldHint`。除非 server 是可信的，客户端必须把它们当作不可信的数据。
* **始终留一个能否决的人。** 规范的建议是，工具调用应该始终有一个能拒绝它的人在环里；宿主在调用任何工具之前，应该取得用户的明确同意。

客户端这一侧也有能力可以提供给服务器，新版里它们的形态都变了：

* **Sampling**（服务器借客户端的模型做补全）和 **Roots**（客户端告诉服务器哪些目录可以碰）都已废弃。Roots 本来也只是提示，不是访问控制，协议并不强制服务器待在目录里。
* **Elicitation**（服务器向用户要信息）保留了，但改为在结果里返回"需要输入"，不再由服务器主动发请求。表单模式不得用来索取密码、API 密钥、访问令牌、支付凭据这类敏感信息；用户的回应有三种：接受、拒绝、取消。

四、两种传输，一套授权
------

**stdio：本地的默认选择。** 客户端把 server 作为子进程拉起，双方通过它的标准输入输出通信，一行一条消息。server 可以把日志写到标准错误，但标准输出上除了合法的 MCP 消息什么都不能写。我调试时就能看到，server 的报错和堆栈都出现在标准错误上，没有混进协议通道。

**Streamable HTTP：远程的部署方式。** server 暴露一个只接受 POST 的端点，客户端把每个请求作为一次 POST 发过去，server 用一个 JSON 对象或者一段事件流来回答。它是在 2025-03-26 版引入的，用来取代最初的 HTTP+SSE 传输；当前版又去掉了 GET 流和会话。两条安全要求是必须遵守的：server 要校验所有连接的 `Origin` 头，不合法的返回 403；在本机运行时只绑定到 localhost，不要绑到所有网卡。否则一个网页就可以借 DNS 重绑定访问到你本机的 server。

规范只定义了这两种，但协议本身不依赖传输，可以自定义。Claude Code 还支持远程的 WebSocket server，不过那种方式不支持 OAuth。

**授权是可选的，只针对 HTTP 传输。** stdio 的实现不走这套，而是从环境里读凭据。HTTP 传输的授权基于 OAuth 2.1，几条硬性要求：

* 客户端必须实现 PKCE，并且先确认授权服务器支持它。
* 客户端必须带上资源指示（RFC 8707），让令牌明确是发给哪个 MCP server 的；server 必须校验令牌的受众就是自己。
* **禁止令牌透传。** server 只能接受为自己签发的令牌；它去调上游接口时，用的是上游授权服务器签发的另一枚令牌，不能把客户端给的转手递过去。
* 令牌放在每个请求的 `Authorization` 头里，不能放进 URL 的查询串。

五、动手：一个能跑的 server
------

环境要求是 Python 3.11 以上，安装命令是 `uv add "mcp[cli]"`（或者 `pip install "mcp[cli]"`）。注意现在装到的是 2.x：这是一个有大改动的主版本，原来叫 `FastMCP` 的类改名为 `MCPServer`，旧的导入路径已经不存在。网上大量的 `from mcp.server.fastmcp import FastMCP` 在 2.x 上会直接报错。

下面是完整的 server，一个只读的订单查询工具：

```python
from mcp.server import MCPServer
from mcp.server.mcpserver.exceptions import ToolError
from mcp.types import ToolAnnotations

mcp = MCPServer("order-demo", instructions="订单查询演示服务：只能按订单号查状态。")

ORDERS = {"A1001": "已发货", "A1002": "待支付"}


@mcp.tool(annotations=ToolAnnotations(read_only_hint=True))
def get_order_status(order_id: str) -> str:
    """按订单号查询订单状态。order_id 是订单号，形如 A1001。"""
    status = ORDERS.get(order_id)
    if status is None:
        raise ToolError(f"订单不存在：{order_id}。可查询的订单号：{'、'.join(ORDERS)}")
    return f"订单 {order_id} 的状态是：{status}"


if __name__ == "__main__":
    mcp.run()
```

`mcp.run()` 默认走 stdio。几个细节是 SDK 替你做的：工具的名字、描述和参数 schema 都从函数本身读出来——函数名、docstring、类型标注。**返回值的类型标注就是输出的 schema。** 实测 `tools/list` 返回的内容（节选，省略了几个 `title` 字段）：

```json
{"name": "get_order_status",
 "description": "按订单号查询订单状态。order_id 是订单号，形如 A1001。",
 "inputSchema": {"type": "object", "properties": {"order_id": {"type": "string"}}, "required": ["order_id"]},
 "outputSchema": {"type": "object", "properties": {"result": {"type": "string"}}, "required": ["result"]},
 "annotations": {"readOnlyHint": true}}
```

三次 `tools/call` 的结果，正好对应三种情况：

```text
查存在的订单   isError: false  "订单 A1001 的状态是：已发货"
查不存在的订单 isError: true   "…订单不存在：NOPE。可查询的订单号：A1001、A1002"
参数类型不对   isError: true   "…1 validation error … Input should be a valid string …"
```

第二条值得多说一句。SDK 把工具里的异常分成两种：预料之中的失败抛 `ToolError`，原因会原样交给模型；其他异常一律当作崩溃，模型只能看到一句笼统的"执行工具出错"，真正的堆栈只写进 server 的日志。所以"查不到订单"这种事应该抛 `ToolError`，并且顺手告诉模型有哪些合法的订单号——这呼应了[工具设计篇](Agent工具设计-写给模型看的接口.md)讲的"报错要带修法"。

同一套 SDK 也支持另外两种原语：`@mcp.resource("greeting://{name}")` 把函数变成资源模板，URI 里的 `{name}` 就是参数；`@mcp.prompt()` 把函数返回的字符串变成一条用户消息。这两种我也在同一环境里跑通过了。

README 给的几个常用命令（这几条我没有实际执行）：`uv run mcp dev server.py` 在 MCP Inspector 里交互式调试，它是个 Node.js 程序，需要有 `npx`；`uv run mcp run server.py --transport streamable-http` 改用 HTTP 传输；`uv run mcp install server.py --name "Bookshop"` 把 server 注册到 Claude Desktop（名字随你定）。

接进 Claude Code 用 `claude mcp add`，本地 server 的写法里用 `--` 把 Claude Code 自己的选项和启动 server 的命令隔开：

```bash
claude mcp add order-demo -- python /path/to/server.py
claude mcp add --transport http <name> <url>     # 远程 server
```

接进来之后，工具在 Claude Code 里的名字是 `mcp__<server>__<tool>`。server 可以配在三个作用域：本地的只对当前项目生效、不共享；项目的写在项目根目录的 `.mcp.json` 里、随版本控制共享；用户的对自己的所有项目生效。出于安全考虑，交互式会话在使用项目级的 server 之前会要求确认。

六、MCP 不负责安全
------

规范里有一句必须记住的话：**MCP 本身无法在协议层面强制这些安全原则，要靠实现者自己做到。** 它列的三条原则是：用户必须明确同意并理解所有的数据访问和操作；宿主在把用户数据交给 server 之前必须取得明确同意；工具是任意的代码执行，工具描述和注解除非来自可信的 server，否则都是不可信的。

规范的"安全最佳实践"页在 2026-07-28 版列了 11 类攻击。其中和多数团队最相关的几类：

| 攻击 | 一句话 | 防法 |
|---|---|---|
| 混淆代理人 | 恶意客户端利用静态客户端 ID、动态注册和同意 cookie，在没有用户同意的情况下拿到授权码 | 每个客户端单独取得同意 |
| 令牌透传 | server 不校验令牌是不是发给自己的，就转手交给下游接口 | 只接受为自己签发的令牌（上一节） |
| 状态句柄劫持 | 未授权的人拿到或猜到某个句柄，去访问、修改别人的状态 | 手里有句柄不等于通过了认证 |
| 服务端请求伪造 | server 诱导客户端去请求不该请求的地址，比如内网资源、云的元数据接口 | 部署在服务端的客户端抓取 OAuth 地址时要考虑这一点 |
| 作用域过大 | 权限定义松散，令牌一旦泄露影响面就大，还抬高用户摩擦、模糊审计线索 | 采用渐进式的最小权限作用域 |

完整清单还包括本地 server 被攻破、OAuth 授权地址校验、代理场景下的 stdio 传输安全、授权服务器混淆攻击（Mix-Up）、本地回环重定向地址被冒充、客户端元数据文档的信任策略。OWASP 另有一个处于测试阶段的"MCP Top 10"，从令牌管理、权限扩大、工具投毒、提示注入到影子 server，可以作为自查表的起点。

七、工具一多就贵：消费端的三道减法
------

接上 MCP 很容易，接多了问题就来了。Anthropic 的工程文章描述了两种代价：多数客户端把所有工具定义一次性全放进上下文，接了几千个工具的智能体，在读到请求之前就要处理几十万个 token；工具的中间结果都要经过模型，一份两小时的会议纪要多出 5 万个 token。文章给的解法是把 MCP server 当成代码接口，让智能体写代码去调：只在需要时加载工具，数据在执行环境里先过滤一遍，再把结果交给模型。文中的例子里，token 用量从 15 万降到了 2000，少了 98.7%；模型看到的是 5 行数据，而不是 1 万行。文章自己也提醒，执行模型生成的代码需要沙箱、资源限制和监控。

Claude Code 作为消费端，默认做了三道减法：

* **工具搜索默认开启。** MCP 工具延迟加载、用到才取，会话开始时只有工具名和 server 说明进上下文。这一点只对较新的模型成立，更早的模型仍然全量加载。
* **输出有上限。** MCP 工具输出默认最多 25,000 个 token（可用 `MAX_MCP_OUTPUT_TOKENS` 调整）；成功的结果超限时存成文件，对话里只留文件路径。纯文本结果超过 50,000 个字符也会这样处理。
* **描述被截短。** 常驻的工具描述和 server 说明最多 4,096 个字符，经工具搜索加载的工具描述最多 16,384 个字符；全名超过 128 个字符的工具干脆不发给模型。

这三道的道理和[上下文分层篇](../上下文工程/Agent上下文分层-常驻按需与工具太多怎么办.md)、[工具设计篇](Agent工具设计-写给模型看的接口.md)讲的是同一套：少而准、控制返回、关键词写在前面。自己写 server 时照这个标准来，接进去才好用。

八、追问链
------

* MCP 解决什么问题？→ 把"每个应用对每个工具各写一套接入"变成"工具方实现一次 server、应用方实现一次 client"。它是 AI 应用连接外部系统的开源标准。
* MCP 管哪些、不管哪些？→ 只管上下文交换这一层。应用怎么用模型、怎么管理上下文，是宿主自己的事。
* 一次连接怎么开始？→ 先分清两代。旧版先 `initialize` 握手、建会话；新版（2026-07-28）无状态，没有握手，每个请求自带版本和能力，用 `server/discover` 查询服务器支持什么。
* 三种原语的区别？→ 在控制权：工具由模型控制，资源由应用控制，提示词由用户控制。
* 工具注解能当权限用吗？→ 不能。除非 server 可信，注解必须当作不可信的数据；始终应该有一个能否决工具调用的人。
* 写 server 时报错怎么处理？→ 预料之中的失败抛 `ToolError`，原因会原样交给模型，让它自己纠正；其他异常会被当作崩溃，模型只能看到一句笼统的话。
* MCP 自带安全保证吗？→ 不带。规范明说它无法在协议层面强制安全原则。要做的是：校验 Origin、只绑定本机、令牌不透传、最小作用域。
* 接了很多 MCP server 之后变慢变贵怎么办？→ 让工具定义按需加载、给输出设上限、让智能体写代码去调工具并在交给模型之前先过滤数据。

一句话总结
------

**MCP 是智能体接外部工具的通用接口：三种原语按"模型、应用、用户"分配控制权，传输只有 stdio 和 Streamable HTTP 两种，授权基于 OAuth 2.1；2026-07-28 版把它改成了无状态协议，而现网的软件多数还按旧版工作，两代都要认得。协议本身不保证安全，注解不是权限，接进来之后还得靠消费端省着用上下文。**

> 2026-11 新增（学习整理篇 · 「脚手架设计」系列第 6 篇 · 素材来自 MCP 官方文档与规范、Anthropic、Linux Foundation 与 OWASP 的公开资料、Python SDK 文档；协议输出为本机实测）。
