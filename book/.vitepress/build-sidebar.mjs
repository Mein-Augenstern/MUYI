// 扫描书源副本（book/src/，由 prepare-src.mjs 生成）生成 sidebar 结构。
// 用法：node build-sidebar.mjs  →  输出 .vitepress/sidebar.json
import { readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { join, relative } from "node:path";

const ROOT = join(import.meta.dirname, "..", "src"); // 书源副本根
const TRACKS = [
  "A-后端内核",
  "B-AI工程",
  "C-架构",
  "G-业务领域",
  "D-云原生",
  "E-算法",
  "F-前端",
];

function titleOf(mdPath) {
  // 取 md 一级标题做侧栏名；没有则退化为文件名
  try {
    const m = readFileSync(mdPath, "utf8").match(/^#\s+(.+)$/m);
    if (m) return m[1].trim();
  } catch {}
  return mdPath.split("/").pop().replace(/\.md$/, "");
}

function walk(dir) {
  // 返回 VitePress SidebarGroup 的 items 数组：md 文件为页项（{text,link}），
  // 子目录递归为组项（{text,collapsed,items}）。统一用 items 字段——
  // VitePress 只认 items，早前版本误用 pages/groups 导致子目录点击无反应。
  const items = [];
  for (const name of readdirSync(dir).sort((a, b) => a.localeCompare(b, "zh"))) {
    if (name.startsWith(".") || name === "node_modules") continue;
    const full = join(dir, name);
    const rel = "/" + relative(ROOT, full).replaceAll("\\", "/");
    if (statSync(full).isDirectory()) {
      const subItems = walk(full);
      if (subItems.length) {
        items.push({ text: name, collapsed: true, items: subItems });
      }
    } else if (name.endsWith(".md")) {
      items.push({ text: titleOf(full), link: rel });
    }
  }
  return items;
}

const sidebar = TRACKS.map((track) => {
  const abs = join(ROOT, track);
  return {
    text: track,
    collapsed: false,
    items: walk(abs),
  };
});

writeFileSync(
  join(import.meta.dirname, "sidebar.json"),
  JSON.stringify(sidebar, null, 2)
);
console.log(
  "sidebar 生成完成：",
  sidebar.map((t) => `${t.text}(${countItems(t)})`).join(" ")
);
function countItems(t) {
  return (t.items || []).reduce(
    (n, i) => n + 1 + (i.items ? countItems({ items: i.items }) : 0),
    0
  );
}
