// 构建期书源准备：从仓库根复制白名单内容到 book/src/，并做 HTML→Markdown 预处理。
// 铁律：仓库原文永不改动——本脚本的产物是构建副本（gitignore），每次构建重新生成。
//
// 为什么要预处理：config 里 markdown.html=false（防 Java 内容的 <init>/<T> 裸尖括号
// 被 Vue 模板编译吞掉），副作用是原文中合法的 HTML（449 处 <a>、172 处标题等）
// 被当纯文本显示。预处理把白名单标签转成 markdown 等价；其余任何尖括号内容
// 保持原样（继续安全转义显示）。
//
// 用法：node scripts/prepare-src.mjs   （在 book/ 目录下）
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const BOOK = join(import.meta.dirname, "..");
const REPO = join(BOOK, "..");
const SRC = join(BOOK, "src");

// 进书的内容白名单（轨道目录 + 共享图片 + 顶层文档）
const WHITELIST_DIRS = [
  "A-后端内核",
  "B-AI工程",
  "C-架构",
  "D-云原生",
  "E-算法",
  "F-前端",
  "G-业务领域",
  "images", // 根相对路径 /images/... 的全库共享图片
];
const WHITELIST_FILES = { "自测清单.md": "自测清单.md", "README.md": "repo.md" };

// ---- 第 1 步：重建 src 副本 ----
rmSync(SRC, { recursive: true, force: true });
mkdirSync(SRC, { recursive: true });
for (const d of WHITELIST_DIRS) {
  cpSync(join(REPO, d), join(SRC, d), {
    recursive: true,
    filter: (p) =>
      !p.includes("node_modules") &&
      !p.includes("/.") &&
      // 小写 readme.md 是配图目录说明（几十字），不是文章——不进书；
      // 大写 README.md 是轨道导航页，保留
      !/\/readme\.md$/.test(p),
  });
}
for (const [from, to] of Object.entries(WHITELIST_FILES)) {
  cpSync(join(REPO, from), join(SRC, to));
}
// 书首页（book/index.md → src/index.md，成为站点根路由）
cpSync(join(BOOK, "index.md"), join(SRC, "index.md"));

// ---- 第 2 步：HTML → Markdown 预处理（只动 .md，跳过 fenced 代码块） ----
const convert = (md) => {
  const lines = md.split("\n");
  let inFence = false;
  return lines
    .map((line) => {
      if (/^\s*```/.test(line)) {
        inFence = !inFence;
        return line;
      }
      if (inFence) return line;
      let s = line;
      // <a href="URL">TEXT</a> → [TEXT](URL)。两个现实形态都要兼容：
      // href= 后带空格（历史排版）；TEXT 含泛型尖括号（<? super T>）——
      // TEXT 用非贪婪任意字符，内部尖括号按 html:false 字面显示（与原意一致）。
      for (let i = 0; i < 3; i++) {
        s = s.replace(/<a\s+href\s*=\s*"([^"]+)"[^>]*>(.+?)<\/a>/g, "[$2]($1)");
      }
      // 目录链接降级为纯文本：[xxx/](A-后端内核/.../) → xxx/
      // 本地 GitHub 浏览目录链接有效，但 VitePress 构建后目录无 index.html → 线上 404
      // （link-doctor 实测自测清单页 9 处）。外链（http/# 开头）不受影响；
      // 仓库原文不动，只改构建副本——与本书"预处理铁律"一致。
      s = s.replace(/\[([^\]]+)\]\((?!https?:|#)([^)]*\/)\)/g, "$1");
      // <img src="URL" ...> → ![图](URL)（自闭合两种形态）
      s = s.replace(/<img\s+src="([^"]+)"[^>]*\/?>/g, "![图]($1)");
      // 标题 h2-h5（h4/h5 降为 ###/####，避免书内层级过深被 outline 忽略）
      s = s.replace(/<h([2-5])[^>]*>(.+?)<\/h\1>/g, (_, lv, t) => "#".repeat(Number(lv)) + " " + t);
      // 行内代码与强调
      s = s.replace(/<code>(.+?)<\/code>/g, "`$1`");
      s = s.replace(/<(strong|b)>(.+?)<\/\1>/g, "**$2**");
      s = s.replace(/<(em|i)>(.+?)<\/\1>/g, "*$2*");
      // 列表项（ol/li 统一转无序列表，编号损失可接受）
      s = s.replace(/^\s*<li>(.+?)<\/li>\s*$/g, "- $1");
      // 结构标签直接剥掉（保留内部文本；p/div 等换行由原换行承担）
      s = s.replace(/<\/?(p|div|span|ol|ul|details|summary|blockquote|sup|sub|font)[^>]*>/g, "");
      s = s.replace(/<br\s*\/?>/g, "  ");
      return s;
    })
    .join("\n");
};

let files = 0;
const walkConvert = (dir) => {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) walkConvert(full);
    else if (name.endsWith(".md")) {
      writeFileSync(full, convert(readFileSync(full, "utf8")));
      files++;
    }
  }
};
walkConvert(SRC);

// ---- 第 2.5 步：轨道目录导航页生成 ----
// 首页 frontmatter 与 repo 轨道表都用 /轨道名/ 目录链接——静态站目录无 index.html 必 404
// （link-doctor 实测首页 7 处）。为每个轨道目录生成 index.md：二级结构列出全部文章
// （.md 链接由 VitePress 自动重写为 .html），目录链接从此指向真实导航页。
const listDir = (dir, prefix) => {
  let out = [];
  for (const name of readdirSync(dir).sort((a, b) => a.localeCompare(b, "zh"))) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) {
      out.push(`\n### ${name}\n`);
      out.push(...listDir(full, `${prefix}${name}/`));
    } else if (name.endsWith(".md") && name !== "index.md" && name !== "README.md") {
      const title = readFileSync(full, "utf8").match(/^#([^\n]*)\n/);
      const t = title ? title[1].trim() : name.replace(/\.md$/, "");
      out.push(`- [${t}](${prefix}${name})`);
    }
  }
  return out;
};
for (const d of WHITELIST_DIRS) {
  const dir = join(SRC, d);
  if (!statSync(dir).isDirectory()) continue;
  // 有轨道导航页（大写 README.md，白名单保留项）直接复用——比机械列表更有信息量；
  // 没有的轨道退化为自动生成的文章清单。两种形态下 /轨道名/ 都有 index.html 可达。
  const nav = join(dir, "README.md");
  if (existsSync(nav)) {
    writeFileSync(join(dir, "index.md"), readFileSync(nav, "utf8") + `\n> （本页由轨道 README.md 生成，文章全目录见侧边栏）\n`);
  } else {
    const lines = [`# ${d}`, "", ...listDir(dir, "./")];
    writeFileSync(join(dir, "index.md"), lines.join("\n") + "\n");
  }
}

// ---- 第 3 步：敏感词断言（未来防线）----
// 书是公开 Pages：书源里出现下述词即中断构建（历史教训：组织研究文、
// 银行真名曾误入公开书）。改动若触发断言，先判断是脱敏遗漏还是白名单词。
const SENSITIVE = ["网商银行", "微众银行", "WeBank", "MYbank", "蚁蚁集团", "蚁蚁金服", "技术组织全景"];
const offenders = [];
const walkScan = (dir) => {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) walkScan(full);
    else if (name.endsWith(".md")) {
      const text = readFileSync(full, "utf8");
      for (const kw of SENSITIVE) if (text.includes(kw)) offenders.push(`${name}: ${kw}`);
    }
  }
};
walkScan(SRC);
if (offenders.length) {
  console.error("敏感词断言失败，构建中止：\n" + offenders.join("\n"));
  process.exit(1);
}

console.log(`书源就绪：${SRC}（预处理 ${files} 个 md，敏感词断言通过）`);
