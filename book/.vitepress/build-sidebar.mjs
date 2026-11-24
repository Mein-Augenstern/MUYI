// 扫描书源副本（book/src/，由 prepare-src.mjs 生成）生成 sidebar 结构。
// 输出 .vitepress/sidebar.json。scripts/prepare-src.mjs 末尾会自动调用本脚本——
// 书源重建一次，侧边栏跟着重建一次（也可手动：node build-sidebar.mjs）。
//
// 组织规则（2026-11-24 定）：
//   1. 轨道按字母序 A→G，默认折叠，VitePress 只自动展开读者当前所在的那一条
//   2. 每个目录的第一项是它的导读页（index.md），标签统一——原先直接取页面 H1，
//      全库有四种叫法，还被拼音序埋在文章中间
//   3. 其余条目按 README 里出现的先后排：README 的目录顺序就是书里的阅读顺序，
//      也是「上一篇 / 下一篇」的顺序。README 没提到的排在后面（先子目录后文章，各按标题序）
import { readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { join, posix } from "node:path";

const ROOT = join(import.meta.dirname, "..", "src"); // 书源副本根
const TRACKS = readdirSync(ROOT)
  .filter((name) => /^[A-G]-/.test(name))
  .sort();

// 取 md 一级标题做侧栏名：先剥离围栏代码块再匹配（2026-11-04 修：Dockerfile 片段
// 里的 "# 多阶段…" 注释曾被当成 H1，Go 上手实战侧栏错文案即此 bug）；同时兼容
// ATX（# 标题）与 setext（标题行 + ====）两种 H1 写法。没有则退化为文件名。
function titleOf(mdPath) {
  try {
    const stripped = readFileSync(mdPath, "utf8").replace(/```[\s\S]*?```/g, "");
    const atx = stripped.match(/^#\s+(.+)$/m);
    if (atx) return atx[1].trim();
    const setext = stripped.match(/^(.+)\n=+\s*$/m);
    if (setext) return setext[1].trim();
  } catch {}
  return posix.basename(mdPath).replace(/\.md$/, "");
}

const decode = (s) => {
  try {
    return decodeURIComponent(s);
  } catch {
    return s;
  }
};

// 一篇 md 里行内链接的目标，按出现先后（跳过代码围栏；目标可含空格编码与成对括号）
function linkTargets(md) {
  const out = [];
  let fence = false;
  for (const line of md.split("\n")) {
    if (/^\s*```/.test(line)) {
      fence = !fence;
      continue;
    }
    if (fence) continue;
    for (const m of line.matchAll(/\]\(/g)) {
      const i = m.index + 2;
      if (line[i] === "<") {
        const k = line.indexOf(">", i);
        if (k > 0) out.push(line.slice(i + 1, k));
        continue;
      }
      let depth = 0;
      let j = i;
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
      if (j < line.length) out.push(line.slice(i, j).replace(/\s+"[^"]*"\s*$/, "").trim());
    }
  }
  return out;
}

// README（书里是 index.md）给直接子项定的顺序：子目录名或直接文章名 → 首次出现的序号。
// 指向子目录内部任意深度的链接，都算作给该子目录排序。
function readmeOrder(dir, dirRel) {
  const rank = new Map();
  if (!readdirSync(dir).includes("index.md")) return rank;
  let order = 0;
  for (const raw of linkTargets(readFileSync(join(dir, "index.md"), "utf8"))) {
    if (/^(https?:|mailto:|#|\/\/)/i.test(raw)) continue;
    const path = decode(raw.split(/[?#]/)[0]);
    const target = posix.normalize(path.startsWith("/") ? path.slice(1) : posix.join(dirRel, path));
    if (target.startsWith("..")) continue;
    const child = target.slice(dirRel.length).replace(/^\//, "").split("/")[0];
    if (child && !rank.has(child)) rank.set(child, order++);
  }
  return rank;
}

const byTitle = (zh) => (a, b) => a.text.localeCompare(b.text, "zh");

function walk(dir, dirRel, depth) {
  // 返回 VitePress SidebarGroup 的 items 数组：md 文件为页项（{text,link}），
  // 子目录递归为组项（{text,collapsed,items}）。统一用 items 字段——
  // VitePress 只认 items，早前版本误用 pages/groups 导致子目录点击无反应。
  const rank = readmeOrder(dir, dirRel);
  const names = readdirSync(dir).filter((name) => !name.startsWith(".") && name !== "node_modules" && name !== "picture");
  const groups = [];
  const pages = [];
  for (const name of names) {
    const full = join(dir, name);
    const rel = posix.join(dirRel, name);
    if (statSync(full).isDirectory()) {
      const items = walk(full, rel, depth + 1);
      if (items.length) groups.push({ text: name, collapsed: true, items, _name: name });
    } else if (name.endsWith(".md") && name !== "index.md") {
      pages.push({ text: titleOf(full), link: "/" + rel, _name: name });
    }
  }
  const listed = [...groups, ...pages].filter((it) => rank.has(it._name)).sort((a, b) => rank.get(a._name) - rank.get(b._name));
  const rest = [...groups.filter((g) => !rank.has(g._name)), ...pages.filter((p) => !rank.has(p._name))].sort(byTitle("zh"));
  const items = listed.concat(rest).map(({ _name, ...it }) => it);
  if (names.includes("index.md")) {
    items.unshift({ text: depth === 0 ? "轨道导读" : "目录导读", link: "/" + posix.join(dirRel, "index.md") });
  }
  return items;
}

const sidebar = TRACKS.map((track) => ({
  text: track,
  collapsed: true,
  items: walk(join(ROOT, track), track, 0),
}));

writeFileSync(
  join(import.meta.dirname, "sidebar.json"),
  JSON.stringify(sidebar, null, 2)
);
function countItems(t) {
  return (t.items || []).reduce(
    (n, i) => n + 1 + (i.items ? countItems({ items: i.items }) : 0),
    0
  );
}
console.log(
  "sidebar 生成完成：",
  sidebar.map((t) => `${t.text}(${countItems(t)})`).join(" ")
);
