#!/usr/bin/env bash
# MUYI 一键部署到 Cloudflare Pages（替代 GitHub Pages 的大文件超时问题）
# 前置：book/ 已 npm install。凭据二选一：
#   ① 环境变量 CLOUDFLARE_API_TOKEN / CLOUDFLARE_ACCOUNT_ID（CI 用）
#   ② 本机 macOS 钥匙串（service=muyi-cloudflare-pages，已存，无需任何参数）
# 用法：./scripts/deploy-cloudflare.sh
set -euo pipefail

# 凭据：环境变量优先，缺失时回落到钥匙串（密钥服务）。token 字面量不进仓库。
kc() { security find-generic-password -s muyi-cloudflare-pages -a "$1" -w 2>/dev/null || true; }
: "${CLOUDFLARE_API_TOKEN:=$(kc CLOUDFLARE_API_TOKEN)}"
: "${CLOUDFLARE_ACCOUNT_ID:=$(kc CLOUDFLARE_ACCOUNT_ID)}"
export CLOUDFLARE_API_TOKEN CLOUDFLARE_ACCOUNT_ID
: "${CLOUDFLARE_API_TOKEN:?缺 CLOUDFLARE_API_TOKEN（设环境变量，或存入钥匙串 service=muyi-cloudflare-pages）}"
: "${CLOUDFLARE_ACCOUNT_ID:?缺 CLOUDFLARE_ACCOUNT_ID（同上）}"

BOOK_DIR="$(dirname "$0")/../book"
REPO_DIR="$(dirname "$0")/.."
cd "$BOOK_DIR"

# 发不出去的三道门：仓库链接与导航收录、README 数字、构建产物里的链接。
# 前两道在构建前查源文，第三道在构建后查产物——VitePress 自带的死链检查在 macOS 上
# 不区分大小写，也认不出"目标含裸空格"，所以门禁放在这里而不是 ignoreDeadLinks。
echo ">>> 体检：仓库链接 / 导航收录 / README 数字 ..."
python3 "$REPO_DIR/scripts/check_links.py" --src
python3 "$REPO_DIR/scripts/stats.py" --check

echo ">>> 重建书源与侧边栏（仓库原文 → book/src + .vitepress/sidebar.json + stats.json）..."
node scripts/prepare-src.mjs

echo ">>> 构建 VitePress（base=/ 已在 config.mts 配置）..."
npx vitepress build

echo ">>> 体检：构建产物里的链接 ..."
python3 "$REPO_DIR/scripts/check_links.py" --dist dist

echo ">>> 部署到 Cloudflare Pages..."
wrangler pages deploy dist --project-name=muyi --branch=main --commit-dirty=true

echo ">>> 完成！访问 https://muyi-axl.pages.dev"
