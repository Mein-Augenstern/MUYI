# A 轨 · TypeScript（全栈扩展）

> 2026-10 新建。站长方向是全栈：Java 后端为主，前端用 TS——`star-site-app-demo2`（uni-app 微信小程序 + H5）
> 与 `star-site-frontend`（Vue3 Web 管理端）两个生产项目滚动反哺。
> 定位：不从零学语法，用 Java 工程师已有的类型/契约/分层心智去锚 TS，深度对齐 A 轨面试标准。

## 为什么需要这个轨道

后端面试官现在常顺着项目追问："你说前端也是你写的？那 TS 的类型系统和 Java 差在哪？前后端接口类型怎么保持一致？"
这类问题背概念没用——`strictNullChecks` 的收窄、api 契约漂移、`as never` 断言的边界，只有真实写过才会遇到。
本轨道的所有结论都要求能落到两个项目的真实文件路径上，和 star-site 后端篇同一纪律。

## 目录大纲

| 目录 | 范围 | 首批素材（来自实战） |
|---|---|---|
| 语言核心/ | interface vs type 与结构化类型、联合/交叉/字面量类型（`as const`）、可空性与控制流收窄、泛型推断与 `extends` 约束、工具类型（Partial/Pick/Omit/Record）、`.d.ts` 与 declare | 两项目的 `types/api` 契约文件；`request<T>` 泛型封装 |
| 工程化/ | tsconfig 逐项拆解（strict 全家桶 / moduleResolution / paths 别名）、`@vue/tsconfig` 继承、`vue-tsc --noEmit` 类型检查、Vite 构建链与 `import.meta.env` 注入 | 两项目 tsconfig 差异：demo2（TS 4.9 + uni 条件编译）vs frontend（TS 6.0 + `vue-tsc -b` 门禁） |
| 框架/ | Vue3 组合式 API：`ref<T>` 类型推演、setup 与 `<script setup>`、Pinia setup store、Vue Router 4（懒加载 / meta / 守卫） | `star-site-frontend/src/stores/user.ts`、`star-site-frontend/src/router/index.ts` |
| 小程序工程/ | uni-app + TS：条件编译（`#ifdef`）、`VITE_API_BASE` 构建期注入、`uni.request` 回调包装 Promise、storage 封装与 key 隔离（d2 前缀） | `star-site-app-demo2/src/utils/request.ts`、`src/utils/auth.ts` |
| 前端工程实践/ | 请求层封装纪律（对照后端 Feign / integration 层）、`R<T>` 统一解包、401 统一治理、按领域拆 api 文件、token 持久化与 store 同步、类型断言（`as T` / `as never`）的逃生舱边界 | `star-site-app-demo2/src/api/health.ts`、`star-site-frontend/src/api/request.ts` 及 10 个领域 api 文件 |

## 写作计划

| 批次 | 篇目 | 素材锚点 |
|---|---|---|
| 第一批（开篇+类型系统） | 从Java视角看TypeScript-开篇（已完成）、interface-vs-type与结构化类型、联合类型与可空收窄 | api.d.ts 全量 interface、`valueNum: number \| null`、`method?: 'GET' \| 'POST'...` |
| 第二批（泛型+契约） | 泛型推断与工具类型实战、api契约文件治理（手工镜像/OpenAPI生成/共享包三级方案） | `request<T>` + `Promise<PageVO<HealthReport>>` 推断层、VO/DTO 命名对齐 |
| 第三批（分层实践） | 请求层封装两实现对照（uni.request 包装 vs axios 拦截器）、Pinia状态管理与setup-store、路由鉴权守卫 | demo2/frontend 两份 request 封装、stores/user.ts、router/index.ts |
| 第四批（工程化+小程序） | tsconfig与strict迁移、vue-tsc与Vite构建链、uni-app多端类型工程（条件编译/env注入） | 两项目 tsconfig 与 package.json scripts、`#ifndef H5` 分支 |

## 素材来源（生产项目，先读再写）

| 项目 | 技术栈 | 角色 |
|---|---|---|
| `~/develop/star-site-app-demo2` | uni-app 3.0 + Vue 3.4 + TS 4.9 + Vite 5（pnpm） | 微信小程序 + H5 双端（`build:mp-weixin`） |
| `~/develop/star-site-frontend` | Vue 3.5 + TS 6.0 + Vite 8 + Element Plus + Pinia + Vue Router 4 | Web 管理端（`vue-tsc -b` 构建门禁） |

核心素材文件（写作引用时的真实路径）：

- `star-site-app-demo2/src/types/api.ts` —— 小程序侧公共契约（注意是 `.ts` 不是 `.d.ts`，内含运行时产物）
- `star-site-app-demo2/src/api/health.ts` —— 请求层范本：函数签名即接口文档
- `star-site-app-demo2/src/utils/request.ts` —— `uni.request` 回调包装 Promise + `R<T>` 解包 + 401 治理 + 条件编译
- `star-site-frontend/src/types/api.d.ts` —— 378 行全量领域契约（`R<T>`/`PageVO<T>`/VO/DTO 命名与后端对齐）
- `star-site-frontend/src/router/index.ts` —— 组合式路由 + 懒加载 + `beforeEach` 鉴权守卫
- `star-site-frontend/src/api/request.ts` —— axios 拦截器版请求封装（`as never` 断言案例）
- `star-site-frontend/src/stores/user.ts` —— Pinia setup store（对照有状态 @Service）

## 写作规范

1. 沿用 A 轨 `====`/`----` 分层 + 面试官追问体；技术对照类章节固定"Java 概念 → TS 对应 → 真实项目里的落点"三段式。
2. 必须引用真实文件路径与片段；片段有删减要注明，禁止虚构"看起来像"的代码。
3. 空目录不建：写出第一篇时再建目录，避免占位噪音。
4. 提交纪律与全库一致：一篇一 commit，格式 `新增: typescript/<篇名>`。
