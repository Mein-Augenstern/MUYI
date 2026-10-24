看在前面
====

Vue3 的响应式系统，一句话概括：**用 Proxy 拦截读写，把"谁在读我"记下来，写的时候通知他们重跑**。本质是一套自动化的观察者模式。Java 工程师对它有天然亲近感：**Proxy ≈ JDK 动态代理，effect ≈ 注册的监听回调，track/trigger ≈ Guava EventBus 的 register/post**。差异在哲学：Java 代理是你显式创建、显式调用。Vue 的代理由 `reactive()` 一包就生成，依赖收集藏在 getter 里全自动发生。

本篇从代理层讲到调度层，最后落到组件与状态组织。素材来自 `star-site-frontend` 生产代码（`src/stores/user.ts`、`src/views/LoginView.vue`）。风格对齐 [typescript/从Java视角看TypeScript-开篇](../typescript/从Java视角看TypeScript-开篇.md)。状态管理全景见姊妹篇 [前端状态管理与请求层设计](../工程化/前端状态管理与请求层设计.md)。

Proxy vs defineProperty：为什么 Vue3 换芯
====

> **面试官：Vue3 为什么用 Proxy 重写响应式？Vue2 的 defineProperty 差在哪？**

**Vue2 的方案。** `Object.defineProperty` 只能劫持**已存在**的属性，变成一对 getter/setter。三个天生缺陷：新增/删除属性监测不到（要靠 `$set/$delete` 补丁 API）。数组下标赋值和 length 修改拦截不了（只好重写七个变异方法）。每个属性都要遍历定义一遍，大对象初始化贵。

**Vue3 的方案。** Proxy 代理**整个对象**，任何属性的读写、新增、删除、has、遍历都是一次"trap 拦截"。且**懒代理**——嵌套对象被访问到才包一层：

```ts
// Vue3 响应式核心的极简骨架（约 20 行就能讲清原理）
function reactive<T extends object>(target: T): T {
  return new Proxy(target, {
    get(target, key, receiver) {
      track(target, key)                        // 读时登记：谁在依赖我
      const res = Reflect.get(target, key, receiver)
      return typeof res === 'object' && res !== null ? reactive(res) : res  // 懒代理嵌套
    },
    set(target, key, value, receiver) {
      const old = (target as any)[key]
      const res = Reflect.set(target, key, value, receiver)
      if (old !== value) trigger(target, key)   // 写时通知：依赖我的人重跑
      return res
    },
  })
}
```

对照 Java：defineProperty 像**编译期织入**（逐属性改写字节码，改不了结构）。Proxy 像**运行时动态代理**（`InvocationHandler` 一个入口拦截所有方法调用）。前者改不动的结构，后者天然全拦——新增属性、数组下标、delete 全覆盖，`$set` 补丁整个消失。

effect：track 与 trigger 的依赖收集回路
====

> **追问：track 到底记了什么？trigger 怎么知道通知谁？**

```
  effect(() => render)              reactive({ count: 0 })
  ┌─────────────────┐                ┌─────────────────┐
  │ 执行副作用函数    │ ──读 count──▶ │ get trap        │
  │ 正在运行 = 全局   │               │  track(target,'count') │
  │ 指针 activeEffect │               │  把 activeEffect 塞进  │
  └────────┬────────┘               │  targetMap[key] 的 Set │
           │                        └────────┬────────┘
           │ count.value = 1 触发 set trap     │
           ▼                                 ▼
  ┌─────────────────┐    trigger    ┌─────────────────┐
  │ 副作用重新执行    │◀────────────│ 从 Set 取出全部 │
  │ （组件重渲染）    │              │ effect 逐个调度  │
  └─────────────────┘              └─────────────────┘
```

三步流程：effect 执行前把自己设为全局 `activeEffect`。getter 里 `track` 把 activeEffect 存进 `targetMap: Map<target, Map<key, Set<effect>>>`（三层 Map/Set，依赖去重靠 Set）。setter 里 `trigger` 按 key 取出 Set 全部重新调度。

**这和 Guava EventBus 完全同构**：targetMap 是订阅表，track 是 register，trigger 是 post。只是 Vue 的注册完全隐式——副作用函数"读到谁"就自动订阅谁。组件渲染本身就是最大的 effect：模板里用到的响应式数据变了，组件重渲染。这就是"数据驱动视图"的机器级解释。

两个工程级追问：**computed 是带缓存的 effect**。它依赖没变直接返回旧值，用脏标记实现惰性求值，等价于 Spring `@Cacheable` 的方法级缓存。**watch 是带回调的 effect**（依赖变化执行你的函数而非重渲染，可配 deep/immediate）。调度细节：trigger 默认把 effect 放进微任务队列去重后批量执行。所以同一轮改十个状态只重渲染一次。这也解释了 `nextTick` 为什么能拿到更新后的 DOM（微任务时序见 [浏览器渲染与事件循环](../浏览器与性能/浏览器渲染与事件循环.md)）。

ref vs reactive：原始值与代理的裂缝
====

> **面试官：ref 和 reactive 该用哪个？为什么有 .value？**

Proxy 只能代理对象，原始值（number/string）包不了。`ref` 就是用一个带 value 属性的盒子（RefImpl 类实例）绕开限制，读写 `.value` 走类的 getter/setter 触发 track/trigger。`reactive` 适合结构化对象，`ref` 通吃一切。

| 维度 | ref | reactive |
|---|---|---|
| 适配类型 | 任意（原始值/对象都行） | 仅对象/数组 |
| 访问 | script 里 `.value`，模板自动解包 | 直接点属性 |
| 整体替换 | `obj.value = {...}` 响应不丢 | `state = {...}` 直接丢响应（变量指向新对象，代理被扔掉） |
| 解构 | 解包后仍是响应式（新版） | 解构即断线（解到的是裸值），要 `toRefs` 摊平 |

**纪律一句话：一律 ref，对象结构稳定且成组变更时用 reactive。** 解构断线是面试高频坑：`const { token } = userStore` 拿到的是快照——Pinia store 返回的对象整体是 reactive 的，解构要配 `storeToRefs`。

组合式 API：用函数组织状态，而不是用类
====

> **面试官：组合式 API 相比 Options API 解决什么问题？**

Options API（data/methods/computed 三格子）是**按角色切分**：一个功能的代码散落三处。跨组件复用只能靠 mixin（命名冲突、来源不明，臭名昭著）。组合式 API **按功能组织**：一个功能的状态+方法+副作用写在同一段，抽复用就是抽个普通函数（composable）。对照 Java：Options 像"全字段堆一个 God Class 再按类型分区"，组合式像**按领域内聚的 Service + 可组合的组件库**。真实落点，登录表单一屏看全：

```ts
// star-site-frontend/src/views/LoginView.vue（片段，逐行可考）
const activeTab = ref<'login' | 'register'>('login')   // 状态：字面量联合类型
const loading = ref(false)                             // 状态：按钮加载态
const formData = reactive({ username: '', password: '', nickname: '' })  // 成组表单用 reactive

async function handleSubmit() {                        // 行为：校验→请求→写 store→跳转
  await formRef.value?.validate()
  loading.value = true
  try {
    const data = await login({ username: formData.username, password: formData.password })
    userStore.setLogin(data.token, data.member)        // 状态提升到 store（下篇展开）
    router.push((route.query.redirect as string) || '/member')
  } finally {
    loading.value = false                              // finally 保证复位——和后端 try-finally 同款
  }
}
```

`loading` 的 try-finally 复位、`route.query.redirect` 的登录后回跳，都是后端工程师熟悉的味道。Pinia setup store 是组合式的极致形态。`star-site-frontend/src/stores/user.ts` 全文 27 行就是一个有状态单例 Service（`defineStore('user') ≈ @Service("user")`，return 即 public 面），细节留给姊妹篇展开。

副作用生命周期：注册与清理必须成对
----

组合式 API 把生命周期也函数化：`onMounted/onUnmounted` 对应初始化/销毁回调。工程纪律与后端完全一致——**`@PostConstruct` 里开的资源必须 `@PreDestroy` 里关**，前端同理：

```ts
onMounted(() => {
  window.addEventListener('resize', onResize)      // 注册
  timer = setInterval(poll, 30_000)                // 注册
})
onUnmounted(() => {
  window.removeEventListener('resize', onResize)   // 清理：漏掉就是内存泄漏 + 幽灵回调
  clearInterval(timer)
})
```

`watchEffect(onCleanup => ...)` 的清理函数、组合式函数里"谁注册谁返回停止句柄"（VueUse 的 `useEventListener` 自动清理），都是把成对纪律工程化。小程序端生命周期模型不同（页面级 onLoad/onShow/onHide，uni-app 再包一层），见 [小程序双线程模型](../无线端/小程序双线程模型.md)。

组件通信：按距离选工具
====

> **面试官：父子、跨层、全局状态分别用什么通信？**

| 场景 | 手段 | Java 直觉 |
|---|---|---|
| 父→子传数据 | props（单向数据流） | 构造参数注入，只读约定 |
| 子→父通知 | emit 自定义事件 | 监听器回调 |
| 父调子方法 | `defineExpose` + 模板 ref | 持有引用调方法（少用，破坏单向流） |
| 父孙跨层透传 | provide / inject | 上下文传递（类似 ThreadLocal 传递链） |
| 兄弟/全局 | 事件总线（vue3 已移除）→ 升级为 Pinia store | 从回调地狱升级到全局 Bean |
| 任意层 | Pinia store | 容器管理的共享 Service |

两条纪律：**数据流向单向**（props 向下、事件向上，改 props 报警）。**状态放的位置 = 被谁共享就上提到谁的最近公共层**。单组件私有用 ref，兄弟共享提父级，全局（登录态/主题）进 store。这就是"状态上提"的判断树，对应 Spring 里"Bean 的作用域按消费方层级选 singleton/prototype/request"。

面试官视角小结：30 秒版本
====

1. **Proxy vs defineProperty**：Vue2 逐属性劫持、改不了结构（$set/数组 hack）。Vue3 整对象代理 + 懒代理嵌套，新增/删除/下标全覆盖。
2. **track/trigger**：activeEffect 全局指针 + targetMap 三层订阅表。组件渲染是最大的 effect。computed 是带脏标记缓存的 effect。
3. **ref vs reactive**：ref 用盒子解决原始值不能代理，`.value` 是盒子接口。reactive 怕整体替换和解构。纪律是一律 ref。
4. **组合式 API**：按功能组织替代按角色分区，composable 对应可复用 Service。Pinia setup store = 有状态单例。
5. **通信**：props/emit 单向流，provide/inject 跨层，Pinia 管全局；状态上提到最近公共消费层。

开场模板：

> "Vue3 响应式我用动态代理理解：reactive 是 InvocationHandler，track 是订阅注册，trigger 是事件广播。最大的哲学差异是订阅完全隐式——副作用读到谁就自动依赖谁。所以新增属性、数组下标这些 Vue2 的补丁场景全都消失了。"

> 2026-10 新增（F 轨前端 · 首批）。
