import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import { defineConfig } from "vitepress";
import { withMermaid } from "vitepress-plugin-mermaid";
import sidebar from "./sidebar.json" with { type: "json" };

// 书源是构建副本 book/src/（由 scripts/prepare-src.mjs 从仓库原文生成并做
// HTML→MD 预处理，src 不入 git，单一事实源仍是仓库 markdown）。
// markdown.html=false 的原因：防 Java 内容的 <init>/<T> 裸尖括号被 Vue 模板
// 编译器当未闭合标签；合法 HTML 已在预处理中转 markdown 等价。
const req = createRequire(import.meta.url);
const resolveAbs = (id: string) =>
  req.resolve(id, { paths: [fileURLToPath(new URL("./", import.meta.url))] });

// Mermaid 画图（2026-11-04 引入，开源 MIT）：```mermaid 代码块在书里渲染成 SVG。
// 图随文走（文本即图）：GitHub 仓页原生渲染同一份代码块，书与仓库两侧一致，
// 不产生图片文件——与"无外链图片"库规约兼容（库内零二进制新增）。
export default withMermaid(
  defineConfig({
    title: "MUYI · 后端工程师的生产级知识体系",
    description:
      "每一篇都从生产代码回写，可验证到真实类名。361 篇实文 · 7 轨道 · 121 题自测闭环。",
    base: "/MUYI/",
    srcDir: "src",
    outDir: "dist",
    cleanUrls: false,
    markdown: { html: false },
    ignoreDeadLinks: true, // 原文内链存在指向外站/仓库首页的情况，先不阻断；M2 收敛
    srcExclude: ["node_modules/**", ".git/**"],
    vite: {
      assetsInclude: ["**/*.PNG", "**/*.JPG", "**/*.JPEG", "**/*.GIF"],
      resolve: {
        alias: [
          {
            find: /^vue\/server-renderer$/,
            replacement: resolveAbs("vue/server-renderer"),
          },
        ],
      },
    },
    themeConfig: {
      nav: [
        { text: "首页", link: "/" },
        { text: "仓库说明", link: "/repo" },
        { text: "个人主页", link: "https://mein-augenstern.github.io/" },
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
  }),
  {
    mermaid: {
      // 客户端水合渲染（首屏出 <pre class="mermaid">，加载后转 SVG）
      startOnLoad: true,
      // 深浅色主题随书站主题切换由插件注入的 CSS 变量处理，这里保底 neutral
      theme: "neutral",
      fontFamily: '"Inter", ui-sans-serif, system-ui, sans-serif',
    },
  },
);
