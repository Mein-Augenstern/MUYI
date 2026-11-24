#!/usr/bin/env bash
# MUYI 一键发布在线书到 Cloudflare Pages。
# 流程：三道门禁 → 重建书源与侧边栏 → 构建 → 产物体检 → 上传。
# 任何一步不过就中止，线上停在最后一个好版本。
# 前置：book/ 已 npm install；PATH 里有 wrangler。
# 凭据二选一（环境变量优先）：
#   ① 环境变量 CLOUDFLARE_API_TOKEN / CLOUDFLARE_ACCOUNT_ID（CI 用最小权限 token）
#   ② 本机 macOS 钥匙串（service=muyi-cloudflare-pages）
# 用法：
#   ./scripts/deploy-cloudflare.sh                     发布（自动部署与手动部署都是这一条）
#   DEPLOY_DRY_RUN=1 ./scripts/deploy-cloudflare.sh    演练：门禁、构建、体检全跑，只不上传
set -euo pipefail

# 先换成绝对路径再 cd。CI 用相对路径 ./scripts/... 调用，$0 就是相对的；
# cd 进 book/ 之后再拿它拼路径就找不着了（2026-11-24 首次自动部署就栽在这里）。
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BOOK_DIR="$REPO_DIR/book"
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

if [ "${DEPLOY_DRY_RUN:-}" = "1" ]; then
  echo ">>> 演练模式：门禁、构建、体检全部通过，未上传。"
  exit 0
fi

# 凭据：环境变量优先，缺失时回落到钥匙串（密钥服务）。token 字面量不进仓库。
kc() { security find-generic-password -s muyi-cloudflare-pages -a "$1" -w 2>/dev/null || true; }
: "${CLOUDFLARE_API_TOKEN:=$(kc CLOUDFLARE_API_TOKEN)}"
: "${CLOUDFLARE_ACCOUNT_ID:=$(kc CLOUDFLARE_ACCOUNT_ID)}"
export CLOUDFLARE_API_TOKEN CLOUDFLARE_ACCOUNT_ID
: "${CLOUDFLARE_API_TOKEN:?缺 CLOUDFLARE_API_TOKEN（设环境变量，或存入钥匙串 service=muyi-cloudflare-pages）}"
: "${CLOUDFLARE_ACCOUNT_ID:?缺 CLOUDFLARE_ACCOUNT_ID（同上）}"

echo ">>> 部署到 Cloudflare Pages..."
wrangler pages deploy dist --project-name=muyi --branch=main --commit-dirty=true

echo ">>> 完成！访问 https://muyi-axl.pages.dev"
