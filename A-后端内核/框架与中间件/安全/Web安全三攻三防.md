Web 安全三攻三防：XSS / CSRF / SQL 注入（附越权）
====

看在前面
------

Web 安全题的答法不是报菜名，是**每类攻击说清"攻击载荷从哪进、利用了什么信任、防线设计在哪一层"**。这篇取三攻三防主干：XSS（转义 + CSP）、CSRF（token + SameSite）、SQL 注入（预编译机制层原理），外加一节越权（鉴权收口）——它是 OWASP 排名前列却最少被讲透的一类。同目录姊妹篇：[支付回调的HMAC验签与幂等设计.md](支付回调的HMAC验签与幂等设计.md)——那篇讲"服务端之间怎么互信"，这篇讲"浏览器与用户怎么被骗"，互为表里。

一、XSS：攻击者代码跑在你的页面上
------

### 1.1 三种类型

| 类型 | 载荷进入路径 | 触发点 | 典型场景 |
| --- | --- | --- | --- |
| 存储型 | 提交内容存库（评论/个性签名） | 任何受害者浏览该页面时 | 评论区存 `<script>`，所有看评论的人中招，杀伤最大 |
| 反射型 | 恶意 URL 参数被原样回显 | 受害者点开特制链接时 | 搜索结果页回显 `?q=<script>...` |
| DOM 型 | JS 从 URL/location 取值直接写 DOM | 前端代码执行时 | `el.innerHTML = location.hash`，全程不过服务端 |

本质一句话：**不可信数据被当成代码执行**。防线也就两条路线：让数据永远是数据（转义），或让注入的代码跑不动（CSP）。

### 1.2 防御

```text
第一层：输出编码（按上下文选规则——核心考点）
  HTML 上下文      < → &lt;  > → &gt;  & → &amp;  " → &quot;   （模板引擎默认开：Thymeleaf/Vue 的 {{}}）
  属性上下文      属性值加引号 + 转义引号，禁 javascript: 协议
  JS 上下文       JSON.parse(字符串) 优于把数据拼进 <script>，拼接必须 JSONEncoder
  URL 上下文      参数编码 + 白名单协议（http/https）
  唯一例外：富文本场景不能全转义 → 白名单过滤（标签/属性/协议逐一放行），OWASP Java HtmlPolicyFactory

第二层：CSP 内容安全策略（纵深，非唯一防线）
  Content-Security-Policy: default-src 'self'; script-src 'self' 'nonce-4AEemGb'
  → 即使注入成功，inline script / 第三方域脚本也不执行
  → 注意：CSP 防不了 DOM 型里同源 script src 的逻辑滥用，也挡不住无需执行 JS 的 HTML 注入（如伪装表单）

配套：Cookie 加 HttpOnly（JS 读不到会话 Cookie，盗 session 这条最肥的路被堵死）
```

`innerHTML`/`v-html`/`dangerouslySetInnerHTML` 是三个语言里同名的大坑，出现处就要过上面这套上下文检查。

二、CSRF：借你的 Cookie 用你的身份
------

### 2.1 攻击模型

```text
1. 用户登录 bank.com，浏览器存下 bank.com 的会话 Cookie
2. 用户访问 evil.com，页面里藏着：
   <img src="https://bank.com/transfer?to=hacker&amount=10000">
   （或自动提交的隐藏表单 POST）
3. 浏览器对 bank.com 发请求时【按域名】自动带上 Cookie —— 身份是用户的！
4. bank.com 只验 Cookie 通过，转账执行
```

利用的信任是：**浏览器对"用户主动发起"和"第三方页面诱导发起"不加区分地自动携带凭据**。XSS 偷身份，CSRF 是借身份——后者甚至拿不到凭据本身。

### 2.2 防御三件套

* **CSRF Token**：服务端下发随机 token（绑定会话），写请求必须原样带回，evil.com 无法读取 bank.com 的 token（同源策略）——**跨站读不到，这就是防线成立的根据**；
* **SameSite Cookie**（低成本首选）：`Set-Cookie: SESSION=xxx; SameSite=Lax`，跨站请求默认不带 Cookie；Lax 放行顶级导航 GET、拦跨站 POST，`Strict` 连导航都拦。老浏览器兼容是历史问题，2026 年主流全支持；
* **自定义头 + JSON**：`X-Requested-With` 或 JSON-only API 要求预检（CORS preflight），evil.com 的简单表单/图片请求伪造不出自定义头——本质是"把请求复杂化到必须过 CORS 检查"。

注意边界：**SameSite 不能完全替代 token**（同站子域被控、旧浏览器、Lax 下的 GET 副作用接口仍可能中招）；GET 必须无副作用是这条防线的前置纪律。JWT 放 Authorization 头天然免疫 CSRF（不走 Cookie），但引入 XSS 盗 token 的新风险——两难的权衡要在面试里讲出来。

三、SQL 注入：预编译为什么在机制上防住
------

### 3.1 原理层

```java
// 预编译：SQL 结构与数据分两次传输
PreparedStatement ps = conn.prepareStatement("select * from user where name = ? and status = ?");
ps.setString(1, "zhangsan' or '1'='1");   // 整段只是 name 字段的【值】
// 数据库先完成 parse（语法树/权限检查）再 bind 参数：
// 值永远只能是值 —— ' or '1'='1 被当作 8 个普通字符去和 name 列比较，匹配不到就是查不到
// 注入的本质是【用户输入改变了 SQL 语法树】，预编译把语法树的构建时机提前到输入到达之前
```

### 3.2 MyBatis 的 ${} 危险

`#{}` → `?` + setXxx（预编译，安全）；`${}` → **解析成 SQL 文本之前的字符串替换**，用户输入直接进语法树：

```xml
<!-- 注入现场：order by / 表名 不能用 #{}（占位符只能出现在值位置），常见写法直接 ${} 裸奔 -->
<select id="list" resultType="User">
    select * from user order by ${orderBy} ${direction}
</select>
<!-- 传参 orderBy="id; drop table user--" → 拼进语法树，游戏结束 -->

<!-- 正确姿势：白名单收口，替换前校验 -->
```

```java
private static final Set<String> ORDER_WHITELIST = Set.of("id", "created_at", "amount");
private static final Set<String> DIR_WHITELIST = Set.of("asc", "desc");
if (!ORDER_WHITELIST.contains(orderBy) || !DIR_WHITELIST.contains(direction)) {
    throw new IllegalArgumentException("非法排序参数");
}
```

配套纵深：最小权限账号（业务库账号不给 DDL/DROP）、错误信息不回显 SQL（报错注入的燃料）、ORM/QE 并不天然安全（QueryWrapper 的 `last()`、`apply()` 拼接同样是 `${}` 语义）。

四、越权：鉴权收口是唯一答案
------

* **水平越权**：同权限级用户互访——改 URL 里的 `orderId` 看别人的订单。利用的是"接口只验证了登录态，没验证资源归属"。
* **垂直越权**：低权限用户调高权限接口——前端隐藏按钮但接口裸奔，普通用户直接 POST `/admin/deleteUser`。利用的是"权限只做在了 UI 层"。

```java
// 错误示范：只判登录，不判归属 —— 水平越权现场
@GetMapping("/order/{id}")
public Order detail(@PathVariable Long id) {
    return orderService.getById(id);        // 任何登录用户可看任何订单
}

// 正确姿势：资源归属校验收在数据访问条件里（不是查出再比对）
@GetMapping("/order/{id}")
public Order detail(@PathVariable Long id, @AuthUser Long userId) {
    return orderService.getOwnedOrder(id, userId);   // where id=? and user_id=? 查不到即拒绝
}
```

收口原则三条：**鉴权在服务端统一层做**（网关/拦截器验登录，注解 `@PreAuthorize` 验角色，数据层验归属——三层各管一段）；**资源归属是查询条件不是事后 if**（避免 TOCTOU 与漏判）；**管理接口独立路径 + 独立鉴权域**（`/admin/**` 单独拦截器与审计），"前端不显示"不算权限。

追问链
------

> **面试官：HttpOnly 的 Cookie是不是就不用担心 XSS 了？**

不是。HttpOnly 只保住"会话 Cookie 不被偷"，XSS 还能在页面上下文里**直接发起请求**（XHR 带 Cookie），改密码、下单、转发消息全能干——这叫无需偷凭据的"就地利用"。XSS 的根治还是输出编码 + CSP，HttpOnly 是止损项。

> **面试官：如果网站已经有 XSS 漏洞，CSRF Token 还有意义吗？**

没有。XSS 在同源上下文里能直接读 DOM/token、甚至替用户发起任意请求——CSRF 的所有防线（token、自定义头）都建立在"攻击者进不来页面"这个前提上。安全有依赖序：XSS 是地基级的洞，先堵它。

> **面试官：预编译能防所有注入吗？**

不能的场景要能数出来：动态表名/列名/排序字段（占位符只能出现在值位置，必须白名单 + 拼接）；`LIKE` 的通配符注入（`%_` 影响结果集语义，需转义，虽不是权限问题）；存储过程里拼接动态 SQL（prepare 的语义到不了里面）。答出"预编译防的是值位置注入，结构位置只能靠白名单"，这题就到位了。

> **面试官：JWT 是不是就不需要防 CSRF 了？**

看存储位置。JWT 放 Authorization 头（localStorage/内存）：跨站自动携带的机制不存在，CSRF 天然免疫——但 localStorage 的 JWT 会被 XSS 读取，且服务端无法主动吊销。JWT 放 Cookie：回到 CSRF 射程内，SameSite/Token 照配。没有免费午餐，是存储位置决定威胁模型。

> **面试官：水平越权和垂直越权，哪个更容易漏？**

水平。垂直越权有明确的"管理接口清单"可对照测试，路径收敛；水平越权散布在每个"按 id 查资源"的接口里，量大且日常迭代容易漏（新接口复制旧模板漏掉归属条件）。治理靠规范：资源访问一律走"当前身份 + 资源 id"的收口方法，代码评审把这个当 checklist 项。

面试官视角小结
------

1. **XSS**：存储/反射/DOM 三型按"载荷进入路径 × 触发点"区分；防 = 按上下文输出编码（富文本走白名单）+ CSP 纵深 + HttpOnly 止损；`innerHTML/v-html` 是高危点；
2. **CSRF**：借身份不偷身份，根因是浏览器跨站自动带 Cookie；防 = token（同源读不到）+ SameSite（默认不带）+ 自定义头/JSON（强制过预检）；SameSite 不能全替 token，GET 必须无副作用；
3. **SQL 注入**：预编译 = 语法树先于用户输入构建，值永远是值；`${}` 是文本替换只能配白名单；ORDER BY/表名是经典露馅点；配套最小权限与不回显；
4. **越权**：水平（归属）/垂直（角色），防 = 服务端三层收口 + 归属作为查询条件 + 管理域隔离；
5. **依赖序**：XSS 是地基（破了它 CSRF 全破），传输层有 HTTPS 另算，服务端间互信走验签——与支付回调篇的 HMAC 是一套纵深的不同层。

> 2026-10 新增（Round1 补缺）。
