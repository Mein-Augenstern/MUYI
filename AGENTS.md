# AGENTS.md

本仓库是个人知识库（MUYI）。给在此仓库工作的 AI 编码智能体的约定：

## B-AI工程（AI 模块）的导航同步义务

B 轨以 `B-AI工程/README.md` 为总阅读导航（依赖图 + 四条阅读路径 + 全量篇目索引）。
**任何新增/移动/改名 B 轨文章的操作，必须同步以下四处，缺一不可：**

1. `B-AI工程/README.md`：顶部实况篇数、全量篇目索引、涉及的阅读路径与"生产救火"表；
2. 所属子目录的 `README.md`（没有则先建）；
3. 仓库根 `README.md` 轨道总表里的 B 轨篇数；
4. agent 系列文章另需更新 `B-AI工程/agent/README.md` 的章表编号与"两章 N 篇"表述。

其余轨道（A/C/D/E/F/G）新增文章只需更新所在目录导航与根 README 统计。

## 在线书构建与部署（踩过的坑）

- 构建唯一正确命令（在 `book/` 目录）：`node scripts/prepare-src.mjs && npx vitepress build`。
  `prepare-src` 做两件事：从仓库原文重建 `book/src`，并在末尾重新生成侧边栏
  `.vitepress/sidebar.json`（gitignore 的构建副本，`config.mts` 静态引入）。新增文章后不跑它，
  线上就是"正文里有、侧边栏没有"（2026-11-23 踩过：侧边栏停在 11-07，之后新增的 4 篇不可见）。
  **必须用无参 `vitepress build`**——带 `src` 参数会丢失 `.vitepress/config.mts`（html:false、
  ignoreDeadLinks、assetsInclude、mermaid 全在里面），产生大量误报构建失败。
- `book/src`、`book/dist`、`book/.vitepress/sidebar.json` 是构建副本（gitignore，每次构建重新生成），
  永不手改；仓库源文是唯一事实源。
- 部署：`./scripts/deploy-cloudflare.sh`（自带 prepare + build + 上传；需 `CLOUDFLARE_API_TOKEN` /
  `CLOUDFLARE_ACCOUNT_ID`）。已有 wrangler 登录态时也可手动两步：在 `book/` 下跑完构建，再
  `wrangler pages deploy dist --project-name=muyi --branch=main --commit-dirty=true`。
- **部署后验证必须查侧边栏**：抓线上页面 HTML，确认新篇标题出现在侧边栏区块
  （`<aside class="VPSidebar">`）里，不能只看状态码和正文——正文来自页面 SSR，侧边栏来自
  sidebar.json，两者会不一致。

## 提交约定

- 提交信息用中文简述 + 前缀（如 `B轨agent:`、`ci:`、`scripts:`、`设计:`），直接提交 master。
