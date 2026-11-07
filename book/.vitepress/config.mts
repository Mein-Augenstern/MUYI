// MUYI 书站配置：Cloudflare Pages（base=/）+ Mermaid 画图
import { defineConfig } from "vitepress";
import { withMermaid } from "vitepress-plugin-mermaid";
import sidebar from "./sidebar.json" with { type: "json" };

export default withMermaid(defineConfig({
  title: "MUYI · 后端工程师的生产级知识体系",
  description:
    "每一篇都从生产代码回写，可验证到真实类名。479 篇实文 · 7 轨道 · 171 题自测闭环。",
  base: "/",
  srcDir: "src",
  outDir: "dist",
  cleanUrls: false,
  markdown: { html: false },
  ignoreDeadLinks: true,
  srcExclude: ["node_modules/**", ".git/**"],
  mermaid: {
    theme: "neutral",
    fontFamily: '"Inter", ui-sans-serif, system-ui, sans-serif',
  },
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
}));
