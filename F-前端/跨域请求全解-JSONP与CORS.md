跨域请求全解-JSONP与CORS
====

跨域是浏览器同源策略与生俱来的约束，也是前后端联调的第一现场。网上大量 CORS 教程"差一两个关键配置"就跑不通——最典型的就是带 cookie 时 `Access-Control-Allow-Origin: *` 被浏览器直接拒绝。本篇按"同源策略 → JSONP → CORS 标准配置 → 带 cookie 的坑"推进，覆盖 get/post/cookie 三类场景，配置全部可落地。开发期免 CORS 的另一条路（dev proxy 同源）见 [模块化与构建工具演进](工程化/模块化与构建工具演进.md)，浏览器执行模型背景见 [浏览器渲染与事件循环](浏览器与性能/浏览器渲染与事件循环.md)，Web 安全面全景见 [Web安全三攻三防](../A-后端内核/框架与中间件/安全/Web安全三攻三防.md)。

1 同源策略：跨域问题的源头
------

同源 = 协议 + 域名 + 端口三者完全一致，任一不同即跨域。两条要点先钉住：

* 同源策略限制的是**浏览器读取响应**，不是请求发不出去——请求正常到达后端、后端正常返回，是浏览器把响应拦了。所以"跨域问题"前端报错、配置在后端解决；
* `<script>`、`<img>` 标签加载资源不受同源策略限制——这正是 JSONP 的突破口，也是历史上 XSS 风险的来源。

本文只讨论 get 与 post（post 代表 get 以外的所有请求方式）。

2 JSONP：script 标签的旁路
------

原理：前端请求静态资源不存在跨域问题 → 动态插入 script 标签，让后端返回一段 `callback(data)` 的 JS，前端预埋同名函数收数据。**只支持 get**；后端必须返回真正的 json 数据，不能随意返回字符串。

前端 jQuery 写法，关键就是 `dataType: "jsonp"`，其余与普通请求无异：

```javascript
$.ajax({
    type: "get",
    url: baseUrl + "/jsonp/get",
    dataType: "jsonp",
    success: function(response) {
        $("#response").val(JSON.stringify(response));
    }
});
```

后端 SpringMVC（>= 3.2）配一个 Advice，Controller 无需感知 jsonp：

```java
@ControllerAdvice
public class JsonpAdvice extends AbstractJsonpResponseBodyAdvice {
    public JsonpAdvice() {
        // 请求中带 callback 参数，Spring 就知道是 jsonp 请求
        super("callback");
    }
}
```

非 SpringMVC 的等价逻辑——返回前端前包一层：

```java
String callback = request.getParameter("callback");
if (StringUtils.isBlank(callback)) {
    return result;
} else {
    return callback + "(" + JSON.toJSONString(result) + ")";
}
```

现代注脚：`AbstractJsonpResponseBodyAdvice` 在 Spring 5.1 废弃、6.0（Boot 3）已移除——JSONP 自带 XSS 与数据泄露面，官方态度就是改用 CORS。存量系统要认得它，新代码不要再选。

3 CORS：标准做法与后端配置
------

JSONP 只支持 get，其余请求方式交给 CORS（Cross-Origin Resource Sharing），主流浏览器早已全部支持，兼容性不构成选型因素。补充一个背景：非简单请求（如带自定义头的 post）会先发一个 OPTIONS 预检，后端要一并应答 `Access-Control-Allow-Methods`/`Access-Control-Allow-Headers`——下面 Filter 方案里那两个头就是为它准备的。

前端 jQuery 写法，三个要点：

```javascript
$.ajax({
    type: "POST",
    url: baseUrl + "/jsonp/post",
    dataType: 'json',                  // 是 json，不是 jsonp
    crossDomain: true,                 // 声明跨域请求
    xhrFields: {
        withCredentials: true          // 携带 cookie，不配则连 session 都无法维护
    },
    data: { name: "name_from_frontend" },
    success: function (response) {
        $("#response").val(JSON.stringify(response));
    }
});
```

后端 SpringMVC（>= 4.2）正规军做法（`WebMvcConfigurerAdapter` 自 Spring 5 起废弃，直接实现 `WebMvcConfigurer`）：

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**/*").allowedOrigins("*");
    }
}
```

低版本 Spring 的曲线救国——Filter 手动加响应头，注册到 `/*`：

```java
public class CrossDomainFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Credentials", "true");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type");
        filterChain.doFilter(request, response);
    }
}
```

原生 JS 不走 jQuery 时，标准路线是 `XMLHttpRequest` 的 withCredentials 分支判断；IE 时代的 XDomainRequest 已成历史，直接忽略。

4 带 cookie 跨域：`*` 失效的经典坑
------

很多人栽在这里：前端配了 `withCredentials: true`，后端回 `Access-Control-Allow-Origin: *`，浏览器直接报"不能使用 *"。原因是**带凭证的跨域请求，origin 不允许是通配符**——浏览器要求服务端明确信任这个具体来源。两种解法：

解法一：从 referer 动态构造 origin 回写：

```java
String referer = request.getHeader("referer");
if (StringUtils.isNotBlank(referer)) {
    URL url = new URL(referer);
    String origin = url.getProtocol() + "://" + url.getHost();
    response.addHeader("Access-Control-Allow-Origin", origin);
} else {
    response.addHeader("Access-Control-Allow-Origin", "*");
}
```

解法二：跨域不传 cookie——前端把 sessionId/accessKey 放到 header 或请求参数中传递，绕开凭证语义。另注 Spring 5.3+ 的硬约束：`allowedOrigins("*")` 与 `allowCredentials(true)` 组合直接抛 IllegalArgumentException，需改用 `allowedOriginPatterns`。生产上更稳的做法是维护显式 origin 白名单再回写命中项，避免 referer 伪造面。

5 选型小结
------

* JSONP：原理简单但只支持 get、有安全面，Spring 6 已移除官方支持——仅作为存量手段认识；
* CORS：覆盖所有请求方式；SpringMVC 4.2+ 用 `addCorsMappings`，低版本用 Filter 加头；
* 带 cookie：`Allow-Origin` 不能是 `*`，动态回写 origin 或改用 header/参数传凭证；
* 开发期 vite/webpack 的 dev proxy 让前端同源免 CORS，生产由网关/nginx 做同一件事，前端代码对代理无感知。

面试追问链
------

* 同源策略限制的是什么？→ 浏览器读取响应，不是请求发出；所以前端报错、配置在后端解决；
* JSONP 原理与局限？→ script 标签不受同源限制，回调包裹数据；只支持 get、有 XSS 面，Spring 6 已移除支持；
* CORS 预检请求是什么？→ 非简单请求先发 OPTIONS，后端应答 Allow-Methods/Allow-Headers；
* withCredentials 的坑？→ 带 cookie 时 Allow-Origin 不能为 *；Spring 5.3+ 组合即抛异常，用 allowedOriginPatterns；
* Spring 里 CORS 有几种配法？→ addCorsMappings 全局、@CrossOrigin 局部、Filter 兜底（低版本）；
* 生产怎么做？→ 网关/nginx 统一加头 + 显式 origin 白名单，不裸用 *；
* 除了 CORS 还有什么跨域手段？→ dev proxy/反向代理做成同源、postMessage（iframe 场景）。

面试官视角小结
------

跨域是"小而准"的分层筛选题：能说出"浏览器拦的是响应不是请求"，已经跑赢背配置的；能讲清 withCredentials 与 `*` 互斥的原因、预检请求的触发条件，说明真踩过；能进一步给出网关统一加头 + origin 白名单的生产方案，并知道 JSONP 已被 Spring 6 移除的，是有版本意识和安全视野的信号。

> 2026-10 新增（F 轨前端）。

> 来源：javadoop《搞定所有的跨域请求问题》（https://javadoop.com/post/cross-domain）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
