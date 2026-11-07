<!--
  MUYI 书首页自定义 Hero——替代 VitePress 默认 home hero。
  设计目标：不是模板脸，是"技术人品味"——终端风开场、大数字、轨道矩阵、精选路径。
-->
<template>
  <section class="muyi-hero">
    <div class="hero-inner">
      <!-- 左：终端风开场 -->
      <div class="hero-left">
        <div class="term-card">
          <div class="term-bar">
            <span class="dot red"></span><span class="dot yellow"></span><span class="dot green"></span>
            <span class="term-title">~/knowledge — zsh</span>
          </div>
          <div class="term-body mono">
            <p><span class="prompt">$</span> cat README.md</p>
            <p class="dim"># 把每个结论追到源码与生产</p>
            <p><span class="prompt">$</span> find . -name "*.md" | <span class="accent">wc -l</span></p>
            <p class="result">479</p>
            <p><span class="prompt">$</span> grep -r "追问链" --include="*.md" -l | <span class="accent">wc -l</span></p>
            <p class="result">85</p>
            <p><span class="prompt">$</span> grep -r "死链" . | <span class="accent">wc -l</span></p>
            <p class="result ok">0</p>
            <p><span class="prompt">$</span> <span class="cursor">▊</span></p>
          </div>
        </div>
      </div>

      <!-- 右：标题 + 数据 + CTA -->
      <div class="hero-right">
        <p class="kicker mono">BACKEND × AI · 2016 →NOW</p>
        <h1 class="title">MUYI</h1>
        <p class="subtitle">后端工程师的<br/><em>生产级</em>知识体系</p>
        <p class="desc">每一篇从上线代码回写，真实类名可点击验证。<br/>479 篇 · 7 轨道 · 171 题自测闭环 · 零死链。</p>
        <div class="cta-row">
          <a href="/A-后端内核/" class="cta primary">从后端内核开始 →</a>
          <a href="/B-AI工程/agent/" class="cta">Agent 从零到一</a>
        </div>
        <div class="cta-row secondary">
          <a href="/自测清单" class="link">面试自测 171 题</a>
          <span class="sep">·</span>
          <a href="https://github.com/Mein-Augenstern/MUYI" class="link" target="_blank">GitHub ↗</a>
        </div>
      </div>
    </div>

    <!-- 数据条 -->
    <div class="stats-bar">
      <div class="stat" v-for="s in stats" :key="s.label">
        <span class="stat-num mono">{{ s.num }}</span>
        <span class="stat-label">{{ s.label }}</span>
      </div>
    </div>
  </section>
</template>

<script setup>
const stats = [
  { num: '479', label: '篇实文' },
  { num: '85', label: '追问链' },
  { num: '171', label: '自测题' },
  { num: '7', label: '轨道' },
  { num: '41', label: '目录导读' },
  { num: '0', label: '死链' },
];
</script>

<style scoped>
.muyi-hero {
  max-width: 1152px;
  margin: 0 auto;
  padding: 80px 24px 40px;
}
.hero-inner {
  display: flex;
  gap: 48px;
  align-items: center;
}
.hero-left { flex: 1; min-width: 0; }
.hero-right { flex: 1; min-width: 0; }

/* 终端卡片 */
.term-card {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--vp-c-border);
  box-shadow: 0 8px 40px rgba(0,0,0,.12);
  background: var(--vp-c-bg-alt);
}
.term-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--vp-c-border);
  background: var(--vp-c-bg);
}
.dot { width: 12px; height: 12px; border-radius: 50%; }
.dot.red { background: #ff5f57; }
.dot.yellow { background: #febc2e; }
.dot.green { background: #28c840; }
.term-title { margin-left: auto; font-size: 12px; color: var(--vp-c-text-3); }
.term-body {
  padding: 20px 18px;
  font-size: 13px;
  line-height: 1.9;
  color: var(--vp-c-text-1);
}
.term-body p { margin: 0; }
.term-body .prompt { color: var(--vp-c-brand); margin-right: 8px; }
.term-body .dim { color: var(--vp-c-text-3); }
.term-body .accent { color: var(--vp-c-brand); }
.term-body .result { color: var(--vp-c-text-2); padding-left: 20px; }
.term-body .result.ok { color: #28c840; font-weight: 600; }
.term-body .cursor { animation: blink 1s step-end infinite; }
@keyframes blink { 50% { opacity: 0; } }

/* 右侧标题区 */
.kicker {
  font-size: 12px;
  letter-spacing: 3px;
  color: var(--vp-c-brand);
  margin: 0 0 12px;
}
.title {
  font-size: 64px;
  font-weight: 800;
  line-height: 1;
  margin: 0 0 12px;
  letter-spacing: -2px;
  background: linear-gradient(135deg, var(--vp-c-brand-1), var(--vp-c-brand-3));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.subtitle {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  margin: 0 0 16px;
  color: var(--vp-c-text-1);
}
.subtitle em { color: var(--vp-c-brand); font-style: normal; }
.desc {
  font-size: 15px;
  line-height: 1.7;
  color: var(--vp-c-text-2);
  margin: 0 0 24px;
}
.cta-row { display: flex; gap: 12px; margin-bottom: 16px; }
.cta {
  display: inline-block;
  padding: 10px 22px;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 600;
  text-decoration: none;
  transition: all .15s;
}
.cta.primary {
  background: var(--vp-c-brand);
  color: var(--vp-c-white);
}
.cta.primary:hover { background: var(--vp-c-brand-2); transform: translateY(-1px); }
.cta:not(.primary) {
  border: 1px solid var(--vp-c-border);
  color: var(--vp-c-text-1);
}
.cta:not(.primary):hover { border-color: var(--vp-c-brand); color: var(--vp-c-brand); }
.cta-row.secondary { gap: 8px; align-items: center; margin-bottom: 0; }
.cta-row.secondary .link { font-size: 14px; color: var(--vp-c-text-2); text-decoration: none; }
.cta-row.secondary .link:hover { color: var(--vp-c-brand); }
.cta-row.secondary .sep { color: var(--vp-c-border); }

/* 数据条 */
.stats-bar {
  display: flex;
  gap: 0;
  margin-top: 56px;
  border-top: 1px solid var(--vp-c-border);
  border-bottom: 1px solid var(--vp-c-border);
  padding: 24px 0;
}
.stat {
  flex: 1;
  text-align: center;
  position: relative;
}
.stat:not(:last-child)::after {
  content: '';
  position: absolute;
  right: 0;
  top: 15%;
  height: 70%;
  width: 1px;
  background: var(--vp-c-border);
}
.stat-num {
  display: block;
  font-size: 32px;
  font-weight: 800;
  color: var(--vp-c-brand);
  line-height: 1.2;
}
.stat-label {
  display: block;
  font-size: 13px;
  color: var(--vp-c-text-3);
  margin-top: 4px;
}

@media (max-width: 960px) {
  .hero-inner { flex-direction: column-reverse; gap: 32px; }
  .title { font-size: 48px; }
  .subtitle { font-size: 22px; }
  .stats-bar { flex-wrap: wrap; gap: 16px; }
  .stat { flex: 1 1 30%; }
  .stat:not(:last-child)::after { display: none; }
}
</style>
