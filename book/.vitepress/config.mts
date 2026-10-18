import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import { defineConfig } from "vitepress";
import sidebar from "./sidebar.json" with { type: "json" };

// 书源在仓库根（srcDir '..'），而依赖装在 book/node_modules——Rollup 的模块
// 解析从"源文件所在目录"向上找 node_modules，够不到 book/ 这层。两层保障：
// 仓库根有指向 book/node_modules 的软链（CI 同步创建），此处再显式 alias 兜底。
const req = createRequire(import.meta.url);
const resolveAbs = (id: string) =>
  req.resolve(id, { paths: [fileURLToPath(new URL("./", import.meta.url))] });

export default defineConfig({
  title: "MUYI · 后端工程师的生产级知识体系",
  description:
    "每一篇都从生产代码回写，可验证到真实类名。361 篇实文 · 7 轨道 · 121 题自测闭环。",
  base: "/MUYI/",
  srcDir: "..",
  outDir: "dist",
  cleanUrls: false,
  // 知识库原文大量含 <init>/<T>/Map<String,Integer> 等裸尖括号，若按 HTML/Vue
  // 模板解析会因"未闭合标签"构建失败——书源是纯内容，整体关闭 HTML 解析。
  // 代价：原文中的 <details> 折叠块平铺展示（可接受）。
  markdown: { html: false },
  ignoreDeadLinks: true, // 原文内链存在指向仓库首页等非书页面的情况，先不阻断；M2 收敛
  // 仓库根 README.md 是 GitHub 门面；书的首页用 book/index.md 重写到根路由
  rewrites: { "book/index.md": "index.md", "README.md": "repo/index.md" },
  srcExclude: [
    "book/node_modules/**",
    "book/dist/**",
    "book/.vitepress/**",
    "book/package*.json",
    "node_modules/**",
    ".git/**",
  ],
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
      { text: "仓库说明", link: "/repo/" },
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
