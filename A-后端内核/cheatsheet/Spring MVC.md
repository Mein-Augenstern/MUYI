Spring MVC 速查卡
====

一句话定位
----

* Spring MVC = 基于 Servlet 的请求驱动 Web 框架，核心是**前端控制器模式**：所有请求进 `DispatcherServlet`，由它分派组件协作。

DispatcherServlet 请求流程（必背，按编号说）
----

```
浏览器 ──> ① DispatcherServlet（统一入口/前端控制器）
             │
             ├─② HandlerMapping：按 url/方法找 Handler
             │     返回 HandlerExecutionChain = Handler + 拦截器链
             │
             ├─③ HandlerAdapter：适配并反射调用 Controller 方法
             │     （preHandle → 参数解析 → 业务 → 返回值处理 → postHandle）
             │
             ├─④ 返回 ModelAndView（@ResponseBody 则跳过 ⑤⑥ 直接写响应）
             │
             ├─⑤ ViewResolver：逻辑视图名 → View
             │
             ├─⑥ View.render()：模型数据渲染 → HTML
             │
             └─⑦ afterCompletion（倒序，异常也执行）；异常交给
                 HandlerExceptionResolver 解析
```

* 口语版 7 步：**收请求 → 找映射 → 适配执行 → 拿 MV → 解析视图 → 渲染 → 拦截器收尾**。
* 两个细节加分点：
    * 参数绑定由 `HandlerMethodArgumentResolver` 完成，`@ResponseBody` 序列化由 `HttpMessageConverter` 完成。
    * 拦截器顺序：preHandle 正序 → postHandle/afterCompletion **倒序**；postHandle 在异常时不执行，afterCompletion 必执行。

核心组件表
----

| 组件 | 职责 | 常见实现 |
|---|---|---|
| DispatcherServlet | 前端控制器，流程总调度 | 唯一，继承 FrameworkServlet |
| HandlerMapping | url → Handler 映射 | RequestMappingHandlerMapping（注解）、BeanNameUrlHandlerMapping |
| HandlerAdapter | 适配不同类型 Handler 并调用 | RequestMappingHandlerAdapter、HttpRequestHandlerAdapter |
| HandlerInterceptor | 前置/后置/完成拦截 | 自定义 implements HandlerInterceptor |
| HandlerExceptionResolver | 全局异常 → 响应 | ExceptionHandlerExceptionResolver（@ExceptionHandler） |
| ViewResolver | 逻辑视图 → View | InternalResourceViewResolver、ThymeleafViewResolver |
| View | 最终渲染 | JstlView、ThymeleafView |
| HttpMessageConverter | 请求/响应体 ⇄ 对象 | MappingJackson2HttpMessageConverter 等 |
| MultipartResolver | 文件上传 | StandardServletMultipartResolver |
| LocaleResolver / FlashMapManager | 国际化 / 重定向参数透传 | —— |

常用注解表
----

| 注解 | 作用 | 备注 |
|---|---|---|
| @Controller / @RestController | 声明控制器 | @RestController = @Controller + @ResponseBody |
| @RequestMapping | 类/方法级路由 | 属性：value、method、params、consumes、produces |
| @GetMapping / @PostMapping / @PutMapping / @DeleteMapping / @PatchMapping | 方法级快捷路由 | 等价 @RequestMapping(method=...) |
| @RequestParam | 取 query/form 参数 | 支持 defaultValue、required |
| @PathVariable | 取路径占位符 `/user/{id}` | —— |
| @RequestBody | 请求体反序列化成对象 | 走 HttpMessageConverter，POST JSON 必用 |
| @ResponseBody | 返回值直接写响应体 | 不走视图解析 |
| @RequestHeader / @CookieValue | 取请求头 / Cookie | —— |
| @ModelAttribute | 方法：进 Controller 前填充公共模型；参数：绑定对象 | —— |
| @SessionAttributes / @SessionAttribute | 模型属性暂存 Session / 读 Session 属性 | —— |
| @RequestPart | 取 multipart 文件部件 | 配合 MultipartFile |
| @ExceptionHandler | 方法级异常处理 | 一般放 @ControllerAdvice 里 |
| @ControllerAdvice / @RestControllerAdvice | 全局拦截所有 Controller | 做全局异常/数据绑定/编码 |
| @InitBinder | 注册自定义参数编辑器 | 如日期转换 |
| @CrossOrigin | 跨域放行 | 或全局 WebMvcConfigurer |
| @ResponseStatus | 标注响应状态码 | —— |

高频追问速答
----

* **过滤器 vs 拦截器**：Filter 是 Servlet 规范、依赖容器、能改请求/响应流；Interceptor 是 Spring 机制、能拿到 HandlerMethod、可注入 Bean。
* **无 web.xml 怎么启动**：Servlet 3.0 SPI——容器扫描 `ServletContainerInitializer`，Spring 提供 `WebApplicationInitializer` 注册 DispatcherServlet；SpringBoot 打 war 时用 `SpringBootServletInitializer`。
* **一个请求多个匹配**：更精确的 pattern 优先（精确 > 路径 > 通配）。
* **DispatcherServlet 初始化时机**：默认随容器首次请求懒加载（load-on-startup=1 则随容器启动），先建父子容器（Root MVC 时代）——SpringBoot 已是单容器。

> 2026-10 依据主题重写（原文件为空壳）。
