// MUYI 书站配置：Cloudflare Pages（base=/）
import { defineConfig } from "vitepress";
import sidebar from "./sidebar.json" with { type: "json" };
import stats from "./stats.json" with { type: "json" };

// sidebar.json / stats.json 都是构建副本（gitignore），由 scripts/prepare-src.mjs 每次构建前生成。

// ```mermaid 代码块 → <pre class="mermaid">，由主题在浏览器里按需渲染（见 theme/index.ts）。
// 原先用的 vitepress-plugin-mermaid 把整套图表库静态打进主包：每个页面首屏多加载约 400KB，
// 而全库只有 3% 的页面真的有图。v-pre 让图源码里的 {{ }} 不被当成 Vue 插值。
function mermaidFence(md: any) {
  const fence = md.renderer.rules.fence.bind(md.renderer.rules);
  md.renderer.rules.fence = (tokens: any[], idx: number, options: any, env: any, self: any) => {
    const token = tokens[idx];
    if (token.info.trim() === "mermaid") {
      return `<pre class="mermaid" v-pre>${md.utils.escapeHtml(token.content)}</pre>`;
    }
    return fence(tokens, idx, options, env, self);
  };
}

export default defineConfig({
  title: "MUYI · 后端工程师的生产级知识体系",
  description:
    `每一篇都从生产代码回写，可验证到真实类名。` +
    `${stats.articles} 篇实文 · ${stats.tracks} 轨道 · ${stats.quiz} 题自测闭环。`,
  base: "/",
  srcDir: "src",
  outDir: "dist",
  cleanUrls: false,
  markdown: { html: false, config: mermaidFence },
  // 书内死链不在这里报错：VitePress 的检查在 macOS 上不区分大小写、也认不出“目标含裸空格”。
  // 真正的门禁是 scripts/check_links.py --dist（区分大小写，部署前必跑）。
  ignoreDeadLinks: true,
  srcExclude: ["node_modules/**", ".git/**"],
  vite: {
    assetsInclude: ["**/*.PNG", "**/*.JPG", "**/*.JPEG", "**/*.GIF"],
  },
  themeConfig: {
    nav: [
      { text: "首页", link: "/" },
      { text: "仓库说明", link: "/repo" },
      { text: "个人主页", link: "https://homepage-4k6.pages.dev/" },
      { text: "GitHub", link: "https://github.com/Mein-Augenstern/MUYI" },
    ],
    outline: { level: [2, 3] },
    search: {
      provider: "local",
      options: {
        translations: {
          button: { buttonText: "搜索", placeholder: "搜索全书内容" },
          modal: {
            noResultsText: "没有找到相关结果",
            resetButtonTitle: "清除查询",
          },
        },
      },
    },
    sidebar,
  },
});
