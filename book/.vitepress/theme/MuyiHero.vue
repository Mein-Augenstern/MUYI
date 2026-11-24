<!--
  MUYI 书首页 v4——全接管式自定义布局（替代 VitePress home 布局的 hero + features）
  设计语言：深色工坊 · 终端开场 · 轨道矩阵卡片 · 路径导引
  v4：数字不再手抄，全部来自 .vitepress/stats.json（scripts/stats.py 在每次构建前生成）；
      终端里演示的命令与输出都是仓库里真实存在的；手机端标题在前、终端在后。
-->
<template>
  <section class="mx-hero">
    <!-- ===== 顶部：终端 + 标题 ===== -->
    <div class="hero-grid">
      <div class="hero-left">
        <div class="term">
          <div class="term-bar">
            <span class="dot r"></span><span class="dot y"></span><span class="dot g"></span>
            <span class="tt mono">~/MUYI — zsh</span>
          </div>
          <div class="term-body mono">
            <p><span class="p">$</span> cat README.md</p>
            <p class="dim"># 把每个结论追到源码与生产</p>
            <p><span class="p">$</span> python3 scripts/stats.py --check</p>
            <p class="out">README 数字与实算一致。</p>
            <p><span class="p">$</span> python3 scripts/check_links.py --src</p>
            <p class="out ok">体检通过：死链 0</p>
            <p><span class="p">$</span> <span class="cur">▊</span></p>
          </div>
        </div>
      </div>

      <div class="hero-right">
        <p class="kicker mono">BACKEND × AI · SINCE 2016</p>
        <h1 class="name">MUYI</h1>
        <p class="sub">后端工程师的<em>生产级</em>知识体系</p>
        <p class="desc mono">
          每篇从上线代码回写 · 真实类名可追溯<br/>
          {{ stats.articles }} 篇 · {{ stats.tracks }} 轨道 · {{ stats.quiz }} 题自测闭环 · 零死链
        </p>
        <div class="cta">
          <a href="/A-后端内核/" class="btn primary">从后端内核开始 →</a>
          <a href="/B-AI工程/agent/" class="btn ghost">Agent 从零到一</a>
        </div>
        <div class="sub-links mono">
          <a href="/自测清单">面试自测 {{ stats.quiz }} 题</a>
          <span>/</span>
          <a href="https://github.com/Mein-Augenstern/MUYI" target="_blank">GitHub ↗</a>
        </div>
      </div>
    </div>

    <!-- ===== 数据条 ===== -->
    <div class="bar mono">
      <div class="bar-item" v-for="s in statBar" :key="s.k">
        <span class="bar-n" :class="{ green: s.green }">{{ s.n }}</span>
        <span class="bar-l">{{ s.k }}</span>
      </div>
    </div>

    <!-- ===== 精选路径 ===== -->
    <div class="section">
      <h2 class="sec-title"><span class="sec-num mono">01</span> 从这里开始</h2>
      <div class="paths">
        <a v-for="p in paths" :key="p.title" :href="p.link" class="path-card">
          <span class="path-icon">{{ p.icon }}</span>
          <div class="path-body">
            <h3>{{ p.title }}</h3>
            <p>{{ p.desc }}</p>
          </div>
          <span class="path-arrow">→</span>
        </a>
      </div>
    </div>

    <!-- ===== 七轨矩阵 ===== -->
    <div class="section">
      <h2 class="sec-title"><span class="sec-num mono">02</span> 七条轨道</h2>
      <div class="tracks">
        <a v-for="t in tracks" :key="t.title" :href="t.link" class="track-card" :class="{ major: t.count > 50 }">
          <div class="track-head">
            <span class="track-icon">{{ t.icon }}</span>
            <span class="track-count mono">{{ t.count }}篇</span>
          </div>
          <h3>{{ t.title }}</h3>
          <p>{{ t.desc }}</p>
        </a>
      </div>
    </div>

    <!-- ===== 信任锚点 ===== -->
    <div class="section">
      <h2 class="sec-title"><span class="sec-num mono">03</span> 为什么可信</h2>
      <div class="trust">
        <div class="trust-card" v-for="t in trust" :key="t.title">
          <span class="trust-icon">{{ t.icon }}</span>
          <h3>{{ t.title }}</h3>
          <p>{{ t.desc }}</p>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import stats from "../stats.json";

const statBar = [
  { n: stats.articles, k: "篇实文" },
  { n: stats.zhuiwen, k: "追问链" },
  { n: stats.quiz, k: "自测题" },
  { n: stats.tracks, k: "轨道" },
  { n: stats.readmes, k: "目录导读" },
  { n: "0", k: "死链", green: true }, // 由 check_links.py 在部署前把关，不为 0 就发不出去
];

const paths = [
  { icon: "🎯", title: "面试冲刺", desc: `${stats.quiz} 道题 × 答案定位，按题直达深度篇`, link: "/自测清单" },
  { icon: "🤖", title: "AI Agent 从零到一", desc: "范式 → 手写 60 行循环 → 评测护栏 → 多智能体编排", link: "/B-AI工程/agent/" },
  { icon: "🏗️", title: "分布式与选型", desc: 'CAP 与共识 → 协调与同步 → 任务调度 → 异步命令框架', link: "/C-架构/分布式系统/" },
];

const count = (letter) => stats.per_track[letter] ?? 0;
const tracks = [
  { icon: "☕", title: "A · 后端内核", count: count("A"), desc: "Java 并发/JVM/Spring · MySQL/Redis/Kafka/Netty · Go/Python/Rust", link: "/A-后端内核/" },
  { icon: "🧠", title: "B · AI 工程", count: count("B"), desc: "LLM 基础 · RAG · Agent · Prompt · 推理部署", link: "/B-AI工程/" },
  { icon: "🏛", title: "C · 架构", count: count("C"), desc: "分布式系统 · 微服务 · DDD 与方法论 · 系统设计", link: "/C-架构/" },
  { icon: "☁️", title: "D · 云原生", count: count("D"), desc: "Docker · Kubernetes · Serverless", link: "/D-云原生/" },
  { icon: "🧮", title: "E · 算法", count: count("E"), desc: "LeetCode 高频题解 · 双指针/链表/哈希/栈队列/滑动窗口", link: "/E-算法/" },
  { icon: "🖥", title: "F · 前端", count: count("F"), desc: "Vue3 · 小程序 · 浏览器性能 · 工程化 · TypeScript", link: "/F-前端/" },
  { icon: "🏦", title: "G · 业务领域", count: count("G"), desc: "金融信贷（业务知识/架构/系统/模块）· 行业认知", link: "/G-业务领域/" },
];

const trust = [
  { icon: "🔍", title: "每篇可验证", desc: "反哺文章引用真实类名（DbLockClient / ExactlyOnceRunner），GitHub 可搜可追溯——不是背书是源码" },
  { icon: "🛡️", title: "工程化治理", desc: "链接与数字由脚本把关，不过关发不出去 · 七维体检每周巡检 · 转载来源合规标注" },
  { icon: "📚", title: "书籍式章节", desc: "redis 四章 · 数据库三章 · Spring 四章 · agent 两章——渐进路径而非平铺" },
];
</script>

<style scoped>
.mx-hero {
  max-width: 1152px;
  margin: 0 auto;
  padding: 64px 24px 80px;
}

/* ===== Hero 区 ===== */
.hero-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 56px;
  align-items: center;
  margin-bottom: 48px;
}
.term {
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid var(--vp-c-border);
  box-shadow: 0 12px 48px rgba(0,0,0,.14);
}
.term-bar {
  display: flex; align-items: center; gap: 7px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--vp-c-border);
}
.dot { width: 11px; height: 11px; border-radius: 50%; }
.dot.r { background: #ff5f57; } .dot.y { background: #febc2e; } .dot.g { background: #28c840; }
.tt { margin-left: auto; font-size: 11px; color: var(--vp-c-text-3); }
.term-body {
  padding: 22px 18px;
  font-size: 13px;
  line-height: 2;
  color: var(--vp-c-text-1);
}
.term-body p { margin: 0; }
.term-body .p { color: var(--vp-c-brand); margin-right: 8px; }
.term-body .dim { color: var(--vp-c-text-3); }
.term-body .hl { color: var(--vp-c-brand); }
.term-body .out { padding-left: 20px; color: var(--vp-c-text-2); }
.term-body .out.ok { color: #28c840; font-weight: 700; }
.cur { animation: cblink 1s step-end infinite; }
@keyframes cblink { 50% { opacity: 0; } }

.kicker {
  font-size: 11px;
  letter-spacing: 4px;
  color: var(--vp-c-brand);
  margin: 0 0 14px;
}
.name {
  font-size: 72px;
  font-weight: 900;
  line-height: 1;
  margin: 0 0 14px;
  letter-spacing: -3px;
  background: linear-gradient(135deg, var(--vp-c-brand-1) 0%, var(--vp-c-brand-2) 50%, var(--vp-c-brand-3) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.sub {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.35;
  margin: 0 0 14px;
  color: var(--vp-c-text-1);
}
.sub em { color: var(--vp-c-brand); font-style: normal; }
.desc {
  font-size: 14px;
  line-height: 1.8;
  color: var(--vp-c-text-2);
  margin: 0 0 28px;
}
.cta { display: flex; gap: 14px; margin-bottom: 16px; }
.btn {
  display: inline-block;
  padding: 12px 26px;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 700;
  text-decoration: none;
  transition: all .18s;
}
.btn.primary {
  background: var(--vp-c-brand);
  color: var(--vp-button-brand-text);
  box-shadow: 0 4px 16px var(--vp-c-brand-soft);
}
.btn.primary:hover {
  background: var(--vp-c-brand-2);
  transform: translateY(-2px);
  box-shadow: 0 6px 24px var(--vp-c-brand-soft);
}
.btn.ghost {
  border: 1.5px solid var(--vp-c-border);
  color: var(--vp-c-text-1);
}
.btn.ghost:hover {
  border-color: var(--vp-c-brand);
  color: var(--vp-c-brand);
}
.sub-links {
  display: flex; gap: 10px; align-items: center;
  font-size: 13px;
}
.sub-links a { color: var(--vp-c-text-3); text-decoration: none; }
.sub-links a:hover { color: var(--vp-c-brand); }
.sub-links span { color: var(--vp-c-border); }

/* ===== 数据条 ===== */
.bar {
  display: flex;
  border: 1px solid var(--vp-c-border);
  border-radius: 14px;
  overflow: hidden;
  margin-bottom: 72px;
}
.bar-item {
  flex: 1;
  text-align: center;
  padding: 20px 0;
  position: relative;
}
.bar-item:not(:last-child)::after {
  content: '';
  position: absolute; right: 0; top: 20%; height: 60%; width: 1px;
  background: var(--vp-c-border);
}
.bar-n {
  display: block;
  font-size: 30px;
  font-weight: 800;
  color: var(--vp-c-brand);
  line-height: 1.1;
}
.bar-n.green { color: #28c840; }
.bar-l {
  display: block;
  font-size: 12px;
  color: var(--vp-c-text-3);
  margin-top: 6px;
}

/* ===== 通用 section ===== */
.section { margin-bottom: 64px; }
.sec-title {
  font-size: 20px;
  font-weight: 800;
  margin: 0 0 24px;
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--vp-c-text-1);
}
.sec-num {
  font-size: 13px;
  font-weight: 700;
  color: var(--vp-c-brand);
  background: var(--vp-c-brand-soft);
  padding: 3px 10px;
  border-radius: 6px;
}

/* ===== 精选路径 ===== */
.paths {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.path-card {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 20px 24px;
  border: 1px solid var(--vp-c-border);
  border-radius: 14px;
  text-decoration: none;
  transition: all .18s;
  background: var(--vp-c-bg);
}
.path-card:hover {
  border-color: var(--vp-c-brand);
  background: var(--vp-c-brand-soft);
  transform: translateX(4px);
}
.path-icon { font-size: 32px; flex-shrink: 0; }
.path-body { flex: 1; min-width: 0; }
.path-body h3 {
  font-size: 17px;
  font-weight: 700;
  margin: 0 0 4px;
  color: var(--vp-c-text-1);
}
.path-body p {
  font-size: 13px;
  margin: 0;
  color: var(--vp-c-text-2);
  line-height: 1.5;
}
.path-arrow {
  font-size: 20px;
  color: var(--vp-c-text-3);
  transition: color .18s, transform .18s;
  flex-shrink: 0;
}
.path-card:hover .path-arrow { color: var(--vp-c-brand); transform: translateX(4px); }

/* ===== 七轨矩阵 ===== */
.tracks {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.track-card {
  padding: 22px;
  border: 1px solid var(--vp-c-border);
  border-radius: 14px;
  text-decoration: none;
  transition: all .18s;
  background: var(--vp-c-bg);
  position: relative;
  overflow: hidden;
}
.track-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: var(--vp-c-brand);
  opacity: 0;
  transition: opacity .18s;
}
.track-card:hover {
  border-color: var(--vp-c-brand);
  background: var(--vp-c-bg-alt);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0,0,0,.06);
}
.track-card:hover::before { opacity: 1; }
.track-card.major::before { opacity: .4; }
.track-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.track-icon { font-size: 28px; }
.track-count {
  font-size: 12px;
  font-weight: 700;
  color: var(--vp-c-text-3);
  background: var(--vp-c-bg-alt);
  padding: 3px 8px;
  border-radius: 6px;
}
.track-card:hover .track-count { color: var(--vp-c-brand); }
.track-card h3 {
  font-size: 16px;
  font-weight: 700;
  margin: 0 0 8px;
  color: var(--vp-c-text-1);
}
.track-card p {
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
  color: var(--vp-c-text-2);
}

/* ===== 信任锚点 ===== */
.trust {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.trust-card {
  padding: 24px;
  border: 1px solid var(--vp-c-border);
  border-radius: 14px;
  background: var(--vp-c-bg-alt);
}
.trust-icon { font-size: 28px; display: block; margin-bottom: 14px; }
.trust-card h3 {
  font-size: 15px;
  font-weight: 700;
  margin: 0 0 8px;
  color: var(--vp-c-text-1);
}
.trust-card p {
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
  color: var(--vp-c-text-2);
}

/* ===== 响应式 ===== */
@media (max-width: 960px) {
  /* 窄屏单列：标题在前、终端在后。原先终端占满首屏，站名和入口按钮被挤到第一屏以下 */
  .hero-grid { grid-template-columns: 1fr; gap: 32px; }
  .hero-right { order: -1; }
  .term-body { padding: 14px 16px; font-size: 12px; line-height: 1.8; }
  .mx-hero { padding-top: 40px; }
  .name { font-size: 48px; }
  .sub { font-size: 20px; }
  .bar { flex-wrap: wrap; }
  .bar-item { flex: 1 1 30%; border-bottom: 1px solid var(--vp-c-border); }
  .bar-item:not(:last-child)::after { display: none; }
  .bar { margin-bottom: 48px; }
  .tracks { grid-template-columns: 1fr; }
  .trust { grid-template-columns: 1fr; }
  .cta { flex-direction: column; }
}
</style>
