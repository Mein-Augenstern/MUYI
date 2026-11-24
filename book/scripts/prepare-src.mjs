// 构建期书源准备：从仓库根复制白名单内容到 book/src/，并做面向读者的预处理。
// 铁律：仓库原文永不改动——本脚本的产物是构建副本（gitignore），每次构建重新生成。
//
// 做五件事：
//   1. 重建 src 副本（白名单：轨道目录 + 共享图片 + 顶层文档）
//   2. HTML → Markdown（config 里 markdown.html=false，原文里合法的 HTML 要转成 markdown 等价）
//   2.5 目录导航页（README.md）挂载为所在目录的 index.md，并去掉只给维护者看的「写作规范」段
//   2.6 链接改写（仓库链接按 GitHub 写，到了书里有四种对不上，见 route）
//   3. 敏感词断言
//   4. 重建侧边栏、统计数字（都是 gitignore 的构建副本，由 config / 首页在构建时读取）
//
// 用法：node scripts/prepare-src.mjs   （在 book/ 目录下）
import { execFileSync } from "node:child_process";
import { cpSync, mkdirSync, readdirSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { join, parse, posix } from "node:path";

const BOOK = join(import.meta.dirname, "..");
const REPO = join(BOOK, "..");
const SRC = join(BOOK, "src");
const GITHUB = "https://github.com/Mein-Augenstern/MUYI";

// 区分大小写的存在性检查。macOS 的 existsSync 不区分大小写：`README.md` 能匹配到小写的
// `readme.md`（配图说明），本机构建因此多出 CI（Linux）里没有的页面，两边产物必须一致。
// 逐级核对目录清单里的真实文件名；清单带缓存，链接多的时候不必反复读盘。
const lsCache = new Map();
const ls = (dir) => {
  if (!lsCache.has(dir)) {
    try {
      lsCache.set(dir, readdirSync(dir));
    } catch {
      lsCache.set(dir, []);
    }
  }
  return lsCache.get(dir);
};
const exactExists = (p) => {
  const names = [];
  for (let cur = parse(p); cur.base; cur = parse(cur.dir)) names.unshift(cur.base); // 结尾斜杠不影响：base 取最后一段
  let abs = parse(p).root;
  for (const name of names) {
    if (!ls(abs).includes(name)) return false;
    abs = join(abs, name);
  }
  return true;
};

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
      // 大写 README.md 是目录导航页——不直接拷贝，第 2.5 步以 index.md 形态
      // 挂载到所在目录（目录 URL 的唯一页面，避免 README/index 双页重复）
      !/\/readme\.md$/i.test(p),
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

// ---- 第 2.5 步：目录导航页统一挂载为 index.md ----
// 规则：README.md（轨道级或子目录级导航页）以 index.md 形态挂载到所在目录——
// 它就是该目录的唯一页面（目录 URL /xxx/ 自然可达），不再单独产出 README 页，
// 避免同目录出现 README/index 两份同源页面、侧边栏双条目（用户实测指出的怪象）。
// 不挂载的两种：配图目录（picture）的说明文件、内容为空的 README——挂上去就是空白页。
const SKIP_MOUNT = new Set(["picture", "node_modules"]);

// 正文里的标题序列 [{ i, lv, text }]：识别 ATX 与 setext 两种写法，跳过代码围栏
const headingsOf = (lines) => {
  const out = [];
  let fence = false;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (/^\s*```/.test(line)) {
      fence = !fence;
      continue;
    }
    if (fence) continue;
    const atx = /^(#{1,6})\s+(.+?)\s*$/.exec(line);
    if (atx) {
      out.push({ i, lv: atx[1].length, text: atx[2] });
      continue;
    }
    const next = lines[i + 1] ?? "";
    if (line.trim() && /^(-{2,}|={2,})\s*$/.test(next)) out.push({ i, lv: next[0] === "=" ? 1 : 2, text: line.trim() });
  }
  return out;
};

// 去掉只给维护者看的段落：从「写作规范 / 写作约定 / 写作纪律」标题起，到下一个同级或更高级标题止。
// 这些约定在 GitHub 上照旧可见（仓库原文不动），只是不该出现在读者目录页的底部。
const stripMaintainer = (md) => {
  const lines = md.split("\n");
  const heads = headingsOf(lines);
  const start = heads.findIndex((h) => /^写作(规范|约定|纪律)/.test(h.text));
  if (start < 0) return md;
  const from = heads[start];
  const until = heads.slice(start + 1).find((h) => h.lv <= from.lv);
  lines.splice(from.i, (until ? until.i : lines.length) - from.i);
  return lines.join("\n").replace(/\s+$/, "") + "\n";
};

const walkMountReadmes = (repoDir, srcDir) => {
  for (const name of readdirSync(repoDir)) {
    if (name.startsWith(".") || SKIP_MOUNT.has(name)) continue;
    const repoFull = join(repoDir, name);
    if (statSync(repoFull).isDirectory()) walkMountReadmes(repoFull, join(srcDir, name));
  }
  const readme = join(repoDir, "README.md");
  if (!ls(repoDir).includes("README.md")) return; // 大小写必须精确：小写 readme.md 是配图说明，不挂载
  const page = stripMaintainer(convert(readFileSync(readme, "utf8")));
  if (!page.trim()) return;
  writeFileSync(join(srcDir, "index.md"), page);
  files++;
};
for (const d of WHITELIST_DIRS) walkMountReadmes(join(REPO, d), join(SRC, d));

// ---- 第 2.6 步：链接改写（只动构建副本）----
// 仓库原文的链接是按 GitHub 写的，到了书里有四种对不上：
//   a. 指向目录（[基础内功](01-基础内功/)）——目录有 README 时书里有落地页，保留可点；
//      没有 README 时书里没有这一页，降级为纯文本（原先不分情况一律降级，
//      导读页的章节表因此在书里整张点不动）
//   b. 指向 README.md（[...](agent/README.md)）——书里它就是目录页，改写成目录 URL
//   c. 指向不进书的内容（docs/治理/*、AGENTS.md…）——改写成 GitHub 地址，不在书里 404
//   d. 目标里有裸空格——Markdown 不会把它渲染成链接，编码成 %20
const decode = (s) => {
  try {
    return decodeURIComponent(s);
  } catch {
    return s;
  }
};
const encSpace = (p) => p.replace(/ /g, "%20"); // 仓库惯例：只编码空格
const encUrl = (p) => p.split("/").map(encodeURIComponent).join("/");
const inBook = (rel) => rel === "自测清单.md" || (/^(images|[A-G]-)/.test(rel) && !/\/readme\.md$/i.test(rel));

// 返回 { kind: "keep" | "plain" | "dest", dest? }
const route = (raw, repoDir, isImage) => {
  if (/^(https?:|mailto:|ftp:|\/\/)/i.test(raw)) return { kind: "keep" };
  const hashAt = raw.indexOf("#");
  const path = hashAt >= 0 ? raw.slice(0, hashAt) : raw;
  const hash = hashAt >= 0 ? raw.slice(hashAt) : "";
  if (!path.trim()) return { kind: "keep" };
  const decoded = decode(path);
  // 相对路径以源文件所在目录为基准；以 / 开头的是仓库根相对路径
  const rel = posix.normalize(decoded.startsWith("/") ? decoded.slice(1) : posix.join(repoDir, decoded));
  const abs = join(REPO, rel.replace(/\/+$/, ""));
  if (rel.startsWith("..")) return { kind: "keep" }; // 指到仓库外：原样保留，交体检报错
  if (exactExists(abs) && statSync(abs).isDirectory()) {
    if (exactExists(join(abs, "README.md"))) return { kind: "dest", dest: encSpace(path) + hash }; // a. 目录有导读页
    return { kind: "plain" }; // a. 书里没有这一页
  }
  if (rel === "README.md") return { kind: "dest", dest: "/repo" + hash }; // 仓库根 README 在书里是「仓库说明」
  if (/readme\.md$/i.test(rel)) {
    if (!exactExists(abs)) return { kind: "keep" };
    const dir = path.replace(/readme\.md$/i, ""); // b. 保留 ./ ../ 等前缀
    return { kind: "dest", dest: (dir === "" ? "./" : dir) + hash };
  }
  if (exactExists(abs)) {
    if (inBook(rel)) return { kind: "dest", dest: encSpace(path) + hash }; // d. 进书的文件
    return { kind: "dest", dest: `${GITHUB}/${isImage ? "raw" : "blob"}/master/${encUrl(rel)}` }; // c. 改指 GitHub
  }
  return { kind: "keep" }; // 仓库里也没有：原样保留，交体检报错
};

// 一行里的 markdown 行内链接/图片（跳过行内代码）。下标均为行内位置：
// textS/textE 是链接文字，destS/destE 是目标（尖括号形式不含括号本身），closeE 是右括号之后。
const findLinks = (line) => {
  const inCode = new Array(line.length).fill(false);
  let tick = false;
  for (let i = 0; i < line.length; i++) {
    if (line[i] === "`") tick = !tick;
    inCode[i] = tick;
  }
  const out = [];
  for (let i = 0; i + 1 < line.length; i++) {
    if (line[i] !== "]" || line[i + 1] !== "(" || inCode[i]) continue;
    let j = i + 2;
    let destE = j;
    let angle = false;
    if (line[j] === "<") {
      const k = line.indexOf(">", j);
      if (k < 0) continue;
      destE = k;
      j = k + 1;
      angle = true;
    } else {
      let depth = 0;
      for (; j < line.length; j++) {
        const c = line[j];
        if (c === "\\") {
          j++;
          continue;
        }
        if (c === "(") depth++;
        else if (c === ")") {
          if (depth === 0) break;
          depth--;
        }
      }
      if (j >= line.length) continue;
      destE = j;
    }
    if (line[j] !== ")") continue;
    let raw = line.slice(i + 2, destE);
    const title = /\s+"[^"]*"\s*$/.exec(raw); // 可选 title 不属于目标
    if (title && title.index > 0) {
      destE = i + 2 + title.index;
      raw = line.slice(i + 2, destE);
    }
    let depth = 0;
    let s = i - 1;
    for (; s >= 0; s--) {
      if (line[s] === "]") depth++;
      else if (line[s] === "[") {
        if (depth === 0) break;
        depth--;
      }
    }
    if (s < 0) continue;
    out.push({ textS: s + 1, textE: i, destS: i + 2, destE, closeE: j + 1, raw, isImage: s > 0 && line[s - 1] === "!" });
  }
  return out;
};

let relinked = 0;
const fixLinks = (md, repoDir) => {
  let fence = false;
  const out = [];
  for (const line of md.split("\n")) {
    if (/^\s*```/.test(line)) {
      fence = !fence;
      out.push(line);
      continue;
    }
    if (fence || !line.includes("](")) {
      out.push(line);
      continue;
    }
    let s = line;
    for (const l of findLinks(line).reverse()) {
      const r = route(l.raw, repoDir, l.isImage);
      if (r.kind === "keep" || (r.kind === "dest" && r.dest === l.raw)) continue;
      relinked++;
      if (r.kind === "dest") {
        s = s.slice(0, l.destS) + r.dest + s.slice(l.destE);
      } else {
        const from = l.isImage ? l.textS - 2 : l.textS - 1; // 连同 "![" 或 "[" 一起摘掉
        s = s.slice(0, from) + s.slice(l.textS, l.textE) + s.slice(l.closeE);
      }
    }
    out.push(s);
  }
  return out.join("\n");
};

// 构建副本 → 仓库源文件（用于确定链接的相对基准）。index.md 是 README 挂载的；
// 书首页（src/index.md）没有仓库源文，按仓库根算。
const walkRelink = (dir) => {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) walkRelink(full);
    else if (name.endsWith(".md")) {
      const rel = posix.relative(SRC, full);
      const src = rel === "repo.md" ? "README.md" : rel.replace(/index\.md$/, "README.md");
      writeFileSync(full, fixLinks(readFileSync(full, "utf8"), posix.dirname(src)));
    }
  }
};
walkRelink(SRC);

// ---- 第 3 步：敏感词断言 ----
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

console.log(`书源就绪：${SRC}（预处理 ${files} 个 md，改写链接 ${relinked} 处，敏感词断言通过）`);

// ---- 第 4 步：重建侧边栏 ----
// sidebar.json 是 gitignore 的构建副本，config.mts 静态引入它。它必须跟着书源一起重建，
// 否则新增文章"正文里有、侧边栏没有"（2026-11-23 实测：Cloudflare 部署路径漏跑这一步，
// 线上侧边栏停在 11-07，之后新增的 4 篇在侧边栏不可见）。
// 必须动态 import：静态 import 会被提升到本模块体之前执行，扫到的是上一轮的旧书源。
await import("../.vitepress/build-sidebar.mjs");

// ---- 第 5 步：统计数字 ----
// stats.json 同理是构建副本，由首页 Hero 与站点描述在构建时读取——首页数字不再手抄。
const STATS = join(BOOK, ".vitepress", "stats.json");
try {
  execFileSync("python3", [join(REPO, "scripts", "stats.py"), "--json", STATS], { stdio: ["ignore", "ignore", "inherit"] });
  console.log(`统计数字已生成：${STATS}`);
} catch (err) {
  console.error(`生成统计数字失败（需要 python3）：${err.message}`);
  process.exit(1);
}
