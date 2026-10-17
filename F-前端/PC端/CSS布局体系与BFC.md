看在前面
====

CSS 是我作为 Java 工程师最不适应的一块：**它不是命令式编程，是一套声明式约束求解系统**——你描述"想要什么"，浏览器布局算法求解"每个盒子放哪"。布局体系的演进史，就是一部"从骗浏览器到正面表达意图"的历史：浮动是拿文字环绕特效冒充布局，flex/grid 才是正牌布局系统，BFC 则是这套求解系统里"独立计算的隔离域"。

本篇按"盒模型 → 标准流与浮动 → flex/grid → 定位 → BFC → 响应式"推进，全程 ASCII 图 + 代码。风格对齐 [typescript/从Java视角看TypeScript-开篇](../typescript/从Java视角看TypeScript-开篇.md)，布局如何影响渲染性能见 [浏览器渲染与事件循环](../浏览器与性能/浏览器渲染与事件循环.md)。

盒模型：一切布局的度量单位
====

> **面试官：box-sizing 两个值的区别？为什么全站都写 border-box？**

```
content-box（默认，W3C 盒）：width 只算内容
┌─ margin ─────────────────────┐
│ ┌─ border ─────────────────┐ │
│ │ ┌─ padding ────────────┐ │ │
│ │ │      content         │ │ │
│ │ │      width           │ │ │
│ └─┴──────────────────────┴─┘ │
└──────────────────────────────┘
实际占宽 = width + padding*2 + border*2

border-box（IE 盒）：width 含 padding 和 border
width 就是最终占宽，加内边距不会"撑破"容器
```

`* { box-sizing: border-box }` 是事实标准开局：**让 width 语义从"内容宽"变成"占位宽"**，写 `width: 33.33%` 的三等分列不会被 1px 边框挤换行。对照 Java：这是度量口径统一——像把"金额"统一到含税口径，避免每处都手工加减。追问链里还有一问：margin 不属于盒模型可见部分且**永不计入 width**，且垂直方向相邻盒子的 margin 会合并（后面 BFC 细说）。

标准流与浮动简史：为什么老代码里全是 clearfix
====

**标准流（normal flow）**：块级盒子纵向堆叠、行内盒子横向排布、auto margin 吸收剩余空间——这是默认布局算法。**浮动（float）**最初只为一个场景设计：**文字环绕图片**（图片浮到一侧，文字绕着排）。上古前端没有布局系统，只好用 float + width% 硬凑多列，代价是两个著名副作用：

```
浮动的坑：父容器高度塌陷
┌─ .container（没写高度）─────────┐
│  ┌─float────┐  ┌─float────┐     │   浮动元素脱离标准流，
│  │ 左列 1/3 │  │ 右列 2/3 │     │   父容器"看不见"它们，
│  └──────────┘  └──────────┘     │   高度塌成 0
└────────────────────────────────┘   后面的元素直接顶上来
```

于是每代老项目里都有一段祖传 clearfix（伪元素清除浮动）。这段历史的意义在于理解**演进方向**：布局问题该交给布局系统（flex/grid），浮动回归文字环绕本职。读老代码认得 clearfix，写新代码别再制造它——面试讲清这条脉络比背触发条件更显功力。

flex 与 grid：一维与二维的分工
====

> **面试官：flex 和 grid 怎么选？**

| 维度 | flex（一维） | grid（二维） |
|---|---|---|
| 思维 | 一条轴上分配空间 | 先画格子再放内容 |
| 适用 | 导航条、工具栏、卡片行 | 整页框架、表单栅格、仪表盘 |
| 典型代码 | `display:flex; gap:12px` | `grid-template-columns: 220px 1fr` |
| 内容驱动 | 内容大小决定轨道 | 容器定义轨道，内容填格 |
| Java 直觉 | `FlowLayout` 单轴排 | `GridLayout` 先定行列 |

```css
/* 管理端顶栏：flex 一维分配（logo 左、菜单中、用户右） */
.header { display: flex; align-items: center; gap: 16px; }
.header .user { margin-left: auto; }        /* auto margin 吃掉剩余空间 */

/* 管理端骨架：grid 二维定轨（侧边栏 + 主区，PC 端经典两栏） */
.layout { display: grid; grid-template-columns: 220px 1fr; min-height: 100vh; }
```

我的项目里管理端（`star-site-frontend`）几乎全部 flex + 少量 grid 定骨架，浮动只出现在文字排版场景。高频细节追三条：`flex: 1` 是 `flex-grow:1; flex-shrink:1; flex-basis:0%` 的缩写（基准归零才能真均分）；`gap` 取代了 margin 兄弟间距的老套路（不会在边缘多出一份）；flex 容器里子项 `min-width: auto` 会导致长文本撑破——挤不动的元凶，需显式 `min-width: 0`。

定位：脱离标准流的另一种方式
====

五档定位一句话：**static** 默认在流里；**relative** 偏移但保留原占位（流内微调）；**absolute** 相对最近非 static 祖先定位、脱离流（悬浮徽标、下拉面板）；**fixed** 相对视口（吸底按钮，注意祖先有 transform 时会降级成 absolute 的坑）；**sticky** 流内滚动到达阈值后钉住（表头吸顶——`position: sticky; top: 0`，替代了当年一坨 JS 滚动监听）。口诀：**relative 是"占着位挪一下"，absolute 是"离开流找锚点"，锚点 = 最近的 positioned 祖先**。

层叠上下文：z-index 生效的前提
----

> **追问：z-index: 9999 为什么压不住人家 z-index: 1？**

因为 z-index 只在**层叠上下文（stacking context）内部**比较。根元素是第一个层叠上下文，而 `z-index` 非 auto 的定位元素、`opacity<1`、`transform`、`filter` 都会新开一个。两个不同上下文里的元素比高低，先比**上下文本身的层级**，再在各自内部比 z-index——所以"9999 压不住 1"，通常是 9999 所在的上下文整体就比对方低。这与 BFC 是一对镜像概念：**BFC 隔离布局，层叠上下文隔离绘制顺序**（对应渲染管线里"绘制→合成"阶段），弹窗被裁的诡异问题九成出在这里，解法是让弹窗挂到 body（脱离局部上下文）。

BFC：块级格式化上下文——布局里的"事务隔离域"
====

> **面试官：什么是 BFC？怎么触发？能解决什么问题？**

**BFC（Block Formatting Context）是页面上一块独立计算的布局区域**：内部布局对外不可渗透，外部规则不干扰内部。对照 Java：**BFC ≈ 布局事务的隔离级别**——内部怎么排不影响外部对齐与合并规则，像事务内修改不对外可见，提交（布局完成）前互不干扰。

```
触发条件（任一即成 BFC）：
  overflow: hidden / auto / scroll（非 visible）
  display: flow-root（现代专用，无副作用，推荐）
  float 非 none；position: absolute / fixed
  display: flex / grid 的直接子项（它们自己成 BFC，注意容器不是）

隔离效果：
  ┌─ 外部 ────────────────────────┐
  │  ┌─ BFC ───────────────────┐  │
  │  │ 内部 margin 合并不外泄   │  │   ← 三个用途都源于这条边界
  │  │ 不与外部浮动重叠          │  │
  │  │ 内部浮动参与高度计算      │  │
  │  └─────────────────────────┘  │
  └───────────────────────────────┘
```

三个经典用途，每个都是一道面试题：

```css
/* ① 清除浮动（治高度塌陷）：容器自成 BFC，内部浮动被算进高度 */
.container { display: flow-root; }        /* 现代写法；老代码用 overflow:hidden */

/* ② 防 margin 合并：相邻块级盒子的垂直 margin 取大者而非相加，
      包一层 BFC（或父级自成 BFC）即可隔断合并 */
.wrapper { display: flow-root; }

/* ③ 自适应两栏：右栏自成 BFC 就不会钻到左栏浮动底下 */
.sidebar { float: left; width: 220px; }
.main { overflow: hidden; }               /* 成 BFC，不与浮动重叠 */
```

margin 合并的规则本身值得一背：**相邻兄弟取 max；父与首个/末个子元素合并（border/padding/BFC 可隔断）；空块自身上下 margin 合并**。这是标准流算法的一部分，不是 bug——理解它是"规则"之后，防合并的手段（BFC 隔断）就顺理成章。

响应式：媒体查询与 rem 的配合
====

> **面试官：rem 方案和媒体查询怎么配合做响应式？**

两层分工：**媒体查询改"断点形态"**（几栏变一栏），**rem/相对单位管"缩放"**（同一形态下整体缩放）。rem 相对根字号，`html { font-size: 16px }` 时 `1rem = 16px`；把尺寸写成 rem，改根字号即全站等比缩放——vw 方案（`font-size: calc(100vw / 设计稿宽 * 16)`）让缩放全自动，细节与移动端坑见 [移动端适配与踩坑](../无线端/移动端适配与踩坑.md)。

```css
/* PC 管理端典型断点策略：宽屏三栏 → 平板两栏 → 窄屏单栏 */
.layout { grid-template-columns: 220px 1fr 280px; }
@media (max-width: 1200px) { .layout { grid-template-columns: 180px 1fr; } }  /* 收起右栏 */
@media (max-width: 768px)  { .layout { grid-template-columns: 1fr; } }        /* 单栏，侧栏转抽屉 */
```

纪律两条：**移动优先**（默认写窄屏样式，min-width 逐级增强，产物里增强规则只在宽屏生效）；优先用内在响应式能力（`auto-fill/minmax/max-width:100%`）少写断点——容器查询（container query）让组件随容器宽度响应，比媒体查询"只认视口"更贴近组件化思维。

面试官视角小结：30 秒版本
====

1. **盒模型**：content-box 宽度只算内容，border-box 含 padding/border；全站 border-box 是度量口径统一。
2. **演进史**：浮动本是文字环绕特效，被上古布局硬用导致高度塌陷与 clearfix；布局归 flex/grid，浮动回归本职。
3. **flex vs grid**：一维内容驱动 vs 二维容器驱动；`flex:1` 基准归零、`gap` 替代 margin、`min-width:0` 治挤破。
4. **BFC**：独立布局隔离域，`display:flow-root` 最干净地触发；三大用途 = 清浮动、防 margin 合并、不与浮动重叠的两栏。
5. **响应式**：媒体查询管断点形态，rem/vw 管等比缩放，移动优先 + 容器查询。

开场模板：

> "我把 CSS 布局当约束求解系统看：标准流是默认解，flex/grid 是声明式布局约束，BFC 是隔离域——所以我讲 BFC 时先讲隔离语义，再讲触发条件，三个用途都是隔离的直接推论。"

> 2026-10 新增（F 轨前端 · 首批）。
