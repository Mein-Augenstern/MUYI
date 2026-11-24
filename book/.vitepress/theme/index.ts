// MUYI 书站主题：默认主题 + MuyiHero 首页 + 文末留言 + Mermaid 按需渲染
// v3: 恢复 Comment（giscus），custom.css 隐藏默认 hero/features
// v4: Mermaid 不再进主包——只有页面上真的有图时才加载图表库
import type { EnhanceAppContext } from "vitepress";
import DefaultTheme from "vitepress/theme";
import { h } from "vue";
import MuyiHero from "./MuyiHero.vue";
import Comment from "./Comment.vue";

import "./custom.css";

// 渲染页面上所有 Mermaid 图。切换深浅色主题时，把上一次画好的 svg 换回源码重画。
async function renderMermaid() {
  const nodes = Array.from(document.querySelectorAll<HTMLElement>(".vp-doc pre.mermaid"));
  if (!nodes.length) return;
  const mermaid = (await import("mermaid")).default;
  const dark = document.documentElement.classList.contains("dark");
  mermaid.initialize({
    startOnLoad: false,
    securityLevel: "loose",
    theme: dark ? "dark" : "neutral",
    fontFamily: '"Inter", ui-sans-serif, system-ui, sans-serif',
  });
  for (const node of nodes) {
    if (node.dataset.src === undefined) node.dataset.src = node.textContent ?? "";
    else node.textContent = node.dataset.src;
    node.removeAttribute("data-processed");
  }
  await mermaid.run({ nodes });
}

export default {
  extends: DefaultTheme,
  Layout: () =>
    h(DefaultTheme.Layout, null, {
      "home-hero-before": () => h(MuyiHero),
      "doc-after": () => h(Comment),
    }),
  enhanceApp({ app }: EnhanceAppContext) {
    if (typeof window === "undefined") return;
    document.documentElement.classList.add("mmd"); // 配合 CSS：图画出来之前先不露出源码
    let timer: number | undefined;
    const schedule = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(renderMermaid, 0);
    };
    // 页面组件挂载（首屏与每次路由切换）之后再画：过早动手会和服务端渲染的水合打架
    app.mixin({
      mounted() {
        if (document.querySelector(".vp-doc pre.mermaid")) schedule();
      },
    });
    let dark = document.documentElement.classList.contains("dark");
    new MutationObserver(() => {
      const now = document.documentElement.classList.contains("dark");
      if (now !== dark) {
        dark = now;
        schedule();
      }
    }).observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
  },
};
