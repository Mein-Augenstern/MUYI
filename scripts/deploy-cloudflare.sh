#!/usr/bin/env bash
# MUYI 一键部署到 Cloudflare Pages（替代 GitHub Pages 的大文件超时问题）
# 前置：book/ 目录可构建 + CLOUDFLARE_API_TOKEN / CLOUDFLARE_ACCOUNT_ID 环境变量
# 用法：CLOUDFLARE_API_TOKEN=xxx CLOUDFLARE_ACCOUNT_ID=xxx ./scripts/deploy-cloudflare.sh
set -euo pipefail

: "${CLOUDFLARE_API_TOKEN:?缺 CLOUDFLARE_API_TOKEN}"
: "${CLOUDFLARE_ACCOUNT_ID:?缺 CLOUDFLARE_ACCOUNT_ID}"

BOOK_DIR="$(dirname "$0")/../book"
cd "$BOOK_DIR"

echo ">>> 构建 VitePress（base=/ 已在 config.mts 配置）..."
npx vitepress build

echo ">>> 部署到 Cloudflare Pages..."
wrangler pages deploy dist --project-name=muyi --branch=main --commit-dirty=true

echo ">>> 完成！访问 https://muyi-axl.pages.dev"
