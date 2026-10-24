看在前面
====

这个轨道回答一个实际的问题。**一个写了近十年 Java 的后端工程师，怎么用最短路径把 TypeScript 写到"面试能讲、生产能扛"的程度？**

我的前端不是玩具：`star-site-app-demo2`（uni-app，微信小程序 + H5 双端）和 `star-site-frontend`（Vue3 Web 管理端）都是真实在跑的代码。前者要处理条件编译和小程序登录态，后者承载家庭健康平台的全部管理界面。写 TS 的过程里我发现一个规律：**让我卡住的概念，几乎都能在 Java 里找到原型**。interface 是契约思维，泛型是参数化类型，api 契约文件是 facade 层的 DTO，请求封装是 integration 层。差异不在"有没有"，在实现哲学：**Java 的类型活在字节码里，TS 的类型编译后全部擦除**。

所以本系列的方法论一句话：**用 Java 概念做锚点，盯住哲学差异，落到生产代码**。每节按"Java 概念 → TS 对应 → 真实项目里的落点"三段推进，文中代码均来自上述两个项目，可追溯。

一张总图：Java 思维到 TS 的映射
====

先给对照表，后面每节就是把其中一行展开讲透：

| Java 概念 | TS 对应 | 关键差异 | 项目落点 |
|---|---|---|---|
| interface（名义类型） | interface / type（结构类型） | 按"形状"匹配不认"名字"；编译后擦除 | `star-site-frontend/src/types/api.d.ts` |
| `Optional<T>` / `@Nullable` | `T \| null`、`field?: T` + strictNullChecks | 可空编进类型系统，不是容器外挂 | api.d.ts 里 30+ 处 `string \| null` |
| 显式泛型 `Result<UserVO>` | 类型推断为主 + 少量显式约束 | 能推断就不写，写是为了约束 | `Promise<PageVO<HealthReport>>` |
| facade 层 VO/DTO | api 契约类型文件 | 手工镜像，靠纪律与工具防漂移 | 两项目的 `types/api` |
| Feign / Retrofit 接口 | api/ 目录的函数式请求层 | 函数 + 类型，不是接口 + 注解 + 动态代理 | `src/api/health.ts` |
| `@Service` 有状态单例 | Pinia setup store | 函数作用域代替类，`ref` 代替字段 | `src/stores/user.ts` |
| Filter / Interceptor 鉴权 | axios 拦截器 / 路由守卫 | 同一条链，挂在客户端 | `src/api/request.ts`、`src/router/index.ts` |

类型系统：interface 是一份"运行时会消失"的契约
====

> **面试官：TS 的 interface 和 Java 的 interface，区别到底在哪？**

**Java 的概念。** Java 的 interface 是名义类型（nominal typing）：类必须显式 `implements LoginVO` 才算数，光长一样不行。它编译成字节码后真实存在于运行时——可以反射、可以代理，Feign 的动态代理就靠这一点。

**TS 的对应。** TS 是结构化类型（structural typing，静态版鸭子类型）：**只看形状，不认名字**，任何具备 `token: string` 和 `member: MemberVO` 的对象都能赋给 `LoginVO`。第二个关键差异：**擦除发生在编译期**，转译成 JS 后 interface 不留痕迹。不能反射、不能运行时校验（要校验得靠 zod 这类库）。所以 TS 类型的定位是：**纯粹写给编译器和读代码的人看的契约**，零运行时开销。

**真实项目里的落点。** `star-site-frontend/src/types/api.d.ts` 是一份 378 行的契约文件，通篇 `export interface`：

```ts
// star-site-frontend/src/types/api.d.ts（片段）
export interface LoginVO {
  token: string
  member: MemberVO
}
```

注意后缀 `.d.ts`：**只含类型声明、不含任何实现的声明文件**，编译产物里整文件消失。这是"类型即文档"的极致形态。而小程序端 `star-site-app-demo2/src/types/api.ts` 用的是 `.ts`，因为里面还可能放 `as const` 常量这类运行时产物。**判断标准就一条：这个文件要不要产生运行时代码**——这本身就是个面试考点。

interface 还是 type：怎么选
----

口诀：**描述对象形状用 interface，要参与类型运算（联合、交叉、条件类型）用 type**。两个真实差异：interface 支持声明合并（同名自动合并，给第三方库补类型时有用也危险）。type 支持联合与工具类型运算。api.d.ts 里 30+ 个类型全用 interface，因为它们都是"后端 VO 的镜像"，形状单一。而 `src/api/health.ts` 里内联的参数类型 `Array<{ itemName: string; ... }>`，就是 type 思维的函数式写法。

联合类型与可空：把 Optional 编进类型系统
====

> **面试官：TS 怎么表达"可能为空"？和 Java 的 Optional 什么关系？**

**Java 的概念。** 空安全在 Java 里是"外挂"：`Optional<T>` 是容器，或者靠 `@Nullable` 注解 + IDE 静态检查。语言本身不拦你调一个 null 引用的方法——NPE 是 Java 的百年难题。

**TS 的对应。** TS 把可空编进了类型代数：`checkupDate: string | null` 声明"要么字符串要么 null"。启用 `strictNullChecks` 后 **null/undefined 不再是所有类型的隐式成员**，不先收窄就不能当 string 用。收窄（narrowing）靠控制流分析：

```ts
if (point.valueNum !== null) {
  point.valueNum.toFixed(2)   // 此分支内 TS 已推窄为 number
}
```

`field?: T` 则是 `T | undefined` 的语法糖，表示"字段可以不存在"。**联合类型比 Optional 表达力强一档**：Optional 只能说"有或没有"，联合还能枚举多种可能。demo2 请求封装里的 `method?: 'GET' | 'POST' | 'PUT' | 'DELETE'` 就是字符串字面量联合，Java 里对应的是枚举。

**真实项目里的落点。** api.d.ts 里大量可空字段直接镜像后端数据库的可空列，是**契约的一部分，不是前端的口味**：

```ts
// star-site-frontend/src/types/api.d.ts（片段）
export interface HealthIndicator {
  valueNum: number | null    // 数值型指标（如血压），文本型指标为 null
  valueText: string | null   // 文本型指标，数值型为 null
  abnormalFlag: number       // 源码注释："与后端 health_indicator.abnormal_flag 对齐"
}
```

`valueNum`/`valueText` 互斥可空是体检指标的领域现实。另一个典型是 `MemberUpdateDTO`：**所有字段都带 `?`，等于 PATCH 语义（字段可省略 = 部分更新）**。对照 Java 里"全字段可空 + `@JsonInclude(NON_NULL)`"的 UpdateDTO 是同一件事。反向的纪律：给字段写清"与后端哪个列对齐"的注释（如 demo2 `health.ts` 里 abnormalFlag 的注释）。这是防契约漂移最便宜的手段。

泛型：能推断就不写，写是为了约束
====

> **面试官：TS 泛型和 Java 泛型比，差异在哪？**

**Java 的概念。** Java 泛型编译期检查 + 运行时擦除，调用侧常要显式声明 `Result<PageVO<HealthReport>> result = ...`。PECS 通配符（`? extends` / `? super`）是每个 Java 面试人的肌肉记忆。

**TS 的对应。** TS 泛型同样擦除，但**推断强得多**。contextual typing 会从上下文反向推出类型参数，多数调用点一个尖括号都不用写。显式写的时机是**约束和兜底**。

定义泛型函数时用 `extends` 画上界（对应 Java 的 `<T extends X>`），或推断失败/想要更强签名时标注。TS 没有型变通配符——结构类型系统里型变冲突天然少，`in/out` 注解存在但极少用。这又是哲学差异的延续。

**真实项目里的落点。** 两层泛型配合，构成前端请求层的骨架：

```ts
// star-site-app-demo2/src/utils/request.ts —— 泛型定义端
export function request<T>(options: { url: string; method?: string; data?: unknown }): Promise<T> {
  return new Promise((resolve, reject) => {
    // uni.request 成功回调里：resolve(body.data as T)
  })
}

// star-site-app-demo2/src/api/health.ts —— 推断调用端
export function listReports(params: {
  familyMemberId?: number; year?: number; page?: number; size?: number
}): Promise<PageVO<HealthReport>> {
  return request({ url: '/health/reports', params })
}
```

`request<T>` 只承诺"我会把响应 data 当作 T 交付"（注意 `body.data as T` 是**断言不是验证**，正确性由 api 层签名背书）。`listReports` 声明返回 `Promise<PageVO<HealthReport>>`，`T` 从返回类型**反向推断**出来。页面里 `const page = await listReports({...})` 之后，`page.records` 自动是 `HealthReport[]`，写错字段名直接红线。`PageVO<T>`（records/total/page/size）能像 Java 的分页泛型一样全站复用，靠的就是这条"`R<T>` 解包 → 泛型推断"管线。

契约一致性：api 契约文件就是前端侧的 facade DTO
====

> **面试官：前后端类型怎么保持一致？靠人肉同步吗？**

**Java 的概念。** 后端契约定义在 facade/API 层：VO 出、DTO 进，统一响应体 `Result<T>`（code/message/data），分页用 `PageVO<T>`。这份契约的消费者除了其他 Java 服务，还有前端。

**TS 的对应。** 前端把契约**手工镜像**成 TS 类型，`star-site-frontend/src/types/api.d.ts` 开头就是证据：

```ts
// star-site-frontend/src/types/api.d.ts（开头）
/** 后端统一响应体 */
export interface R<T = unknown> {
  code: number
  message: string
  data: T
}

export interface PageVO<T> { records: T[]; total: number; page: number; size: number }
```

命名纪律完全延续后端：**VO 结尾是返回值，DTO 结尾是参数**——`LoginVO`/`LoginDTO`、`PolicySaveDTO`、`DocumentSaveDTO`，Java 工程师打开零学习成本。文件里 `/* ===== 家庭域 ===== */`、`/* ===== 保单域 ===== */` 的分组注释，对应后端按领域拆分的 controller。

**真实项目里的落点与风险。** 手工镜像最大的敌人是**漂移**：后端加了字段、改了可空性，前端编译器毫无感知。治理按成本分级：注释锚点（abnormalFlag 旁写"与后端 health_indicator.abnormal_flag 对齐"）→ 后端出 OpenAPI 规范、前端 `openapi-typescript` 生成 d.ts → monorepo 共享类型包。

两个易忽略的点：JSON 反序列化只会产生 `null`，`?:` 的 undefined 语义要谨慎映射。手写镜像"只声明用到的字段"反而是优点，后端冗余字段不会污染前端类型。两项目还有个架构对照：frontend 把全量类型集中在一个 api.d.ts（管理端全量消费）。demo2 只放公共类型、领域类型内聚在 `src/api/health.ts`（小程序对包体积敏感，按域裁剪）——这本身就值得一篇笔记。

组合式 API：用函数组织状态，而不是用类
====

> **面试官：Vue3 组合式 API 和你熟悉的类思维是什么关系？**

**Java 的概念。** Java 的组织单位是类：字段表状态、方法表行为、`@Service` 单例注入依赖、`this` 贯穿始终。我们天然期待前端也长这样——但 Vue2 的 Options API（data/methods/computed 分格子）是"按角色切分的配置对象"，不是类。一个大组件的同一个功能散落在三个格子里。

**TS 的对应。** 组合式 API 换成**按功能组织**：一个功能的状态和方法写在一起，可复用的抽成普通函数（composable）。setup 函数是组件的"构造过程"。状态用 `ref<T>` 包装，ref 的泛型参数就是状态类型——和 `AtomicReference<MemberVO>` 形似而神异（一个为并发安全，一个为响应式追踪）。

**真实项目里的落点。** Pinia 的 setup store 是最"Java 友好"的形态，`star-site-frontend/src/stores/user.ts` 全文就是一个有状态单例 Service：状态字段 `ref<string | null>` / `ref<MemberVO | null>`（ref 的泛型参数即状态类型），行为方法与 public 暴露面在同一个函数作用域里 return 出去。完整代码以[前端状态管理与请求层设计](../工程化/前端状态管理与请求层设计.md)为唯一权威源，此处不重复贴。

对照 Java：`defineStore('user', ...)` ≈ `@Service("user")`，`ref` ≈ 私有字段 + getter，return 的对象 ≈ public 方法表。差异在生命周期——store 是单例，组件不是，每个组件实例独立执行一遍 setup。路由侧同样有对照，`star-site-frontend/src/router/index.ts`：

```ts
// 路由守卫：无 token 一律去登录页（客户端版鉴权过滤器）
router.beforeEach((to) => {
  if (to.meta.requiresAuth && !getToken()) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
})
```

`beforeEach` ≈ 前置过滤器链，`meta.requiresAuth` ≈ 接口上的安全注解，13 个路由全部 `() => import('@/views/...')` 懒加载 ≈ 按需的模块化拆分。对照 Spring Security 的过滤器链讲这段，面试官立刻能对上频道。

请求层封装纪律：前端也有 integration 层
====

> **面试官：你们前端请求层怎么封装的？和后端 Feign 层有什么同构性？**

**Java 的概念。** 后端调远程服务走 integration 层：Feign 接口 + 注解声明，动态代理生成实现，统一处理鉴权头、超时、错误码翻译。调用方拿到的是反序列化好的 DTO，感知不到 HTTP。

**TS 的对应。** 前端同构这一层，"接口 + 注解 + 动态代理"换成"**函数 + 类型 + Promise**"。两个项目各有一份实现，恰好覆盖两种端形态：

```ts
// star-site-app-demo2/src/utils/request.ts —— uni-app 版（回调包装成 Promise）
success: (res) => {
  const body = res.data as R<T>
  if (res.statusCode === 401) { toLogin(); reject(new Error('unauthorized')); return } // 401 统一处理
  if (!body || body.code !== 0) { reject(new Error(body?.message ?? '请求失败')); return } // 业务码翻译
  resolve(body.data)   // 解包 R<T>，交付业务类型
}
```

frontend 的 axios 版（`src/api/request.ts`）是拦截器链形态：响应拦截器里解包 `R`、`return res.data as never` 把 AxiosResponse 掰成 data 本身、401 清 token 跳登录。这份代码以[前端状态管理与请求层设计](../工程化/前端状态管理与请求层设计.md)为唯一权威源，此处不重复贴。

**真实项目里的落点。** 封装之上是**使用纪律**，`star-site-app-demo2/src/api/health.ts` 是范本。每个后端接口一个函数，**函数签名即接口文档**。参数是对象字面量类型（必填/可选一目了然），返回值精确到 `Promise<HealthReport>`、`Promise<PageVO<HealthReport>>`、甚至 `Promise<void>`（DELETE 无返回体）。连 `ABNORMAL_FLAG` 常量都用 `as const` 锁成字面量对象并注释"与后端对齐"。frontend 的 `src/api/` 按领域拆成 10 个文件（auth/family/health/insurance/finance/...），就是后端按领域拆 Feign client 的翻版。

两个值得追问的细节。其一，`return res.data as never` 是刻意利用 never"底部类型可赋给一切"来绕开 axios 拦截器签名与"解包后返回"的冲突。**类型断言是逃生舱不是校验**，正确性由 api 层签名兜底，与 demo2 的 `body.data as T` 同一哲学。其二，demo2 的 BASE_URL 带 uni-app 条件编译（`// #ifndef H5` 走 `/api` 代理，小程序端用 `VITE_API_BASE` 注入完整域名）。这是构建期多端分支，Java 无对应物——留到小程序工程篇展开。

面试官视角小结：30 秒版本
====

1. **interface 对照**：TS 结构化类型（看形状不认名字），类型编译期全擦除、运行时不存在。所以不能反射、不能运行时校验。`.d.ts` 是纯声明文件。Java 是名义类型 + 字节码存活。
2. **可空对照**：Java 用 Optional 容器外挂空安全。TS 用 `T | null` 编进类型 + strictNullChecks + 控制流收窄。`field?: T` ≈ PATCH 语义的可省略字段。
3. **泛型对照**：都擦除。TS 推断强（contextual typing），能不写就不写，显式写是为了约束（`T extends X`）。结构类型系统里没有 PECS 的型变负担。
4. **契约治理**：api 契约文件是前端侧手工镜像的 facade DTO，VO/DTO 命名与后端对齐。漂移靠注释锚点 → OpenAPI 生成 → 共享类型包逐级治理。
5. **分层同构**：api/ 目录的函数即 Feign 接口。request 封装即 integration 层（鉴权头 / 401 / 业务码统一处理）。Pinia setup store 即有状态 @Service。路由守卫即客户端鉴权过滤器链。

开场模板，面试官问"你 TS 怎么上手这么快"时的第一句：

> "我是用 Java 的分层思维去写 TS 的——类型当契约、api 层当 Feign、store 当 Service。最大的哲学差异是 TS 类型编译后全部擦除，所以运行时校验必须另想办法。"

> 2026-10 新增（全栈扩展 · 素材来自 star-site-app-demo2 / star-site-frontend 生产代码）。
