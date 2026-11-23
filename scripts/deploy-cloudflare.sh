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
cd "$BOOK_DIR"

echo ">>> 重建书源与侧边栏（仓库原文 → book/src + .vitepress/sidebar.json）..."
node scripts/prepare-src.mjs

echo ">>> 构建 VitePress（base=/ 已在 config.mts 配置）..."
npx vitepress build

echo ">>> 部署到 Cloudflare Pages..."
wrangler pages deploy dist --project-name=muyi --branch=main --commit-dirty=true

echo ">>> 完成！访问 https://muyi-axl.pages.dev"
