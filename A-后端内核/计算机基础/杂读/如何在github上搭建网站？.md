看在前面
------

* <a href="https://www.jianshu.com/p/88aa5afd918c">如何在github上搭建网站？</a>

GitHub 不仅是代码托管平台，还免费提供静态网站托管服务 GitHub Pages。配合 Jekyll、Hexo 等静态站点生成器，几分钟就能拥有一个免费的个人博客/项目官网，地址形如 `https://用户名.github.io`。

基本原理
====

GitHub Pages 本质是一个静态文件服务器：你把 HTML/CSS/JS/图片提交到仓库的约定分支（或 /docs 目录），GitHub 自动发布并通过 CDN 分发。它只支持静态内容，没有数据库和后端运行时，动态能力需借助第三方服务（评论用 Disqus/Giscus、表单用 Formspree 等）。

两种站点类型
====

| 类型 | 仓库 | 分支 | 访问地址 |
|---|---|---|---|
| 用户站点 | 用户名.github.io | main（或 master） | https://用户名.github.io |
| 项目站点 | 任意仓库 | gh-pages（或 main、/docs 目录） | https://用户名.github.io/仓库名 |

用户站点一个账号只能有一个，作为个人主页；项目站点每个仓库都可以有，适合给开源项目做文档官网。

方式一：纯手写 HTML
====

1. 新建仓库 `用户名.github.io`（必须是这个精确名字）；

2. 克隆到本地，添加首页：

```bash
git clone https://github.com/用户名/用户名.github.io.git
cd 用户名.github.io
echo "<h1>Hello GitHub Pages</h1>" > index.html
git add . && git commit -m "init site"
git push origin main
```

3. 浏览器访问 `https://用户名.github.io`，几分钟后生效（首次发布有延迟）。

项目站点则在仓库 Settings → Pages 里选择来源分支（如 gh-pages），保存后即开通。

方式二：Jekyll（GitHub 原生支持）
====

Jekyll 是 GitHub Pages 内置的静态站点生成器，提交 Markdown 文件即可自动构建成网站，无需本地安装任何东西。典型目录结构：

```
.
├── _config.yml      # 站点配置：标题、主题、插件
├── _posts/          # 博文目录，文件名必须为 YYYY-MM-DD-title.md
│   └── 2026-10-17-hello-world.md
├── _layouts/        # 页面模板
├── _includes/       # 可复用片段（头部、页脚）
└── index.md         # 首页
```

博文只需在文件头加上 front matter：

```markdown
---
layout: post
title: "我的第一篇博客"
date: 2026-10-17
---

正文内容，支持 Markdown。
```

本地预览：`gem install bundler jekyll && jekyll serve`，访问 http://127.0.0.1:4000。也可以选择一个现成主题（在 _config.yml 中指定 `theme: jekyll-theme-cayman`），完全交给 GitHub 构建时则通过远程主题引用。

方式三：Hexo（Node.js 生态）
====

Hexo 生成速度快、主题丰富，是中文社区最流行的方案：

```bash
npm install -g hexo-cli
hexo init blog && cd blog
npm install
hexo generate        # 生成静态文件到 public/
hexo server          # 本地预览 http://localhost:4000
```

部署只需安装部署插件并在 _config.yml 配置：

```yaml
deploy:
  type: git
  repo: https://github.com/用户名/用户名.github.io.git
  branch: main
```

执行 `hexo clean && hexo deploy` 即可一键发布。换主题：`git clone` 主题到 themes/ 目录（如 hexo-theme-next），并在 _config.yml 中启用。

绑定自定义域名与 HTTPS
====

1. 仓库根目录添加 CNAME 文件，内容为你的域名（如 `blog.example.com`）；

2. 到域名服务商添加 DNS 解析记录：

```
类型   主机记录   记录值
CNAME  blog      用户名.github.io
A      @        185.199.108.153   （裸域名时，共4个IP）
```

3. Settings → Pages 勾选 Enforce HTTPS（GitHub 免费签发证书）。

常见问题
====

* **修改后不生效**：CDN 缓存约 10 分钟；可在仓库 Settings → Pages 查看构建状态；

* **404 排查**：确认仓库名、分支名、index 文件位置正确，用户站点必须用 main/master 根目录；

* **国内访问慢**：GitHub Pages 的 CDN 节点在境内访问不稳定，可接入 Cloudflare 或镜像加速；

* **仓库想开源又不想暴露站点源码**：可以把生成后的静态文件推到 `用户名.github.io`，源码放私有仓库，用 GitHub Actions 自动构建发布。

总结
====

纯 HTML 适合极简落地页；Jekyll 零构建、适合文档；Hexo 本地构建、主题生态好、适合博客。三者都免费、都托管在 GitHub 上，选一个写起来最顺手的即可。

> 原链接已失效，2026-10 依据主题重写。
