# AGENTS.md

本仓库是个人知识库（MUYI）。给在此仓库工作的 AI 编码智能体的约定：

## 导航同步义务（全部轨道）

每个轨道的 README.md 是读者入口（从哪进 / 目录 / 推荐阅读路径），体量大的目录另有自己的导读页。
口径与约定全文见 `docs/治理/协作与统计口径约定.md`。

**新增、移动、改名任何一篇正文时，必须做到：**

1. 被本轨至少一个 README.md 链接（通常是它所在目录的导读页）；
2. 所在目录 README 与轨道 README 的篇数同步；
3. 根 `README.md` 轨道表的篇数同步；
4. 链接目标里的空格写成 `%20`，路径大小写与文件名一致。

漏了哪条都会被门禁拦住，不需要靠记忆。改完先跑这两条：

```
python3 scripts/check_links.py --src    # 第 1、4 条
python3 scripts/stats.py --check        # 第 2、3 条中的根 README 与轨道篇数
```

- **想让某篇文章在书里排前面**：调它在 README 目录里的位置。README 的目录顺序就是侧边栏和「上一篇 / 下一篇」的顺序，不要去改 `book/.vitepress/build-sidebar.mjs`。
- **数字**：篇数、题数、配图数一律以 `python3 scripts/stats.py` 为准。在线书首页与站点描述在构建时自动读取，不要把数字手写进 `book/.vitepress/theme/MuyiHero.vue` 或 `config.mts`。

## 在线书构建与部署

- **push 到 master 会自动部署**（`.github/workflows/deploy-site.yml`），平时不需要手动部署。
- 手动部署只有一条命令：`./scripts/deploy-cloudflare.sh`（重建书源 → 构建 → 上传 Cloudflare Pages），自带三道门禁，不过关不发布。
- 分步排查时（在 `book/` 目录）：`node scripts/prepare-src.mjs && npx vitepress build`。
  - `prepare-src` 重建 `book/src`、侧边栏 `sidebar.json`、统计 `stats.json`——三个都是 gitignore 的构建副本，必须跟着书源一起重建。漏了 sidebar，线上就是"正文里有、侧边栏没有"（2026-11-23 踩过）。
  - **必须用无参 `vitepress build`**——带 `src` 参数会丢掉 `.vitepress/config.mts`，产生大量误报。
  - `prepare-src` 还做只针对读者的改写（只动构建副本）：目录链接 / README 链接 / 书外内容链接的处理、去掉「写作规范」段、裸空格编码。规则见脚本内注释。
- 部署后验证要查**侧边栏**（`<aside class="VPSidebar">`），不能只看状态码和正文。
- Mermaid 图由主题按需渲染（`book/.vitepress/theme/index.ts`）。不要引回 `vitepress-plugin-mermaid`——它会把整套图表库打进每个页面的首屏（约 400KB，而只有 3% 的页面有图）。
- 小写 `readme.md` 是配图目录说明，刻意不进书；不要把它改成大写（会变成目录页），也不要在它所在的目录里再建 `README.md`（macOS 上两者是同一个文件）。

## 凭据

- **CI 部署用的是最小权限 token**（只有 Pages Write，2027-11-24 到期），存在 GitHub Secrets：`CF_PAGES_DEPLOY_TOKEN`、`CF_ACCOUNT_ID`。到期前提醒用户在 Cloudflare Dashboard 重新生成并更新 Secret。
- **本机钥匙串里的 token（service=`muyi-cloudflare-pages`）是智能体用的账户级 token，权限极大**（170 多个写权限组）。只用于本机手动操作；**不要写进仓库、对话、CI 或任何第三方服务**。不需要向用户要 token。
- wrangler 的 OAuth 登录态是另一条备用通道，不保证长期有效。
- 校验账户级 token 用 `/accounts/<账户ID>/tokens/verify`；`/user/tokens/verify` 对它恒报无效，别被它误导。

## 提交约定

- 提交信息用中文简述 + 前缀（如 `B轨agent:`、`ci:`、`scripts:`、`设计:`、`治理B10:`），直接提交 master。
- 提交只用显式文件名或 `git add -u`（改动全部属于本次工作时），先 `git status --short` 确认没有别的会话的改动。
