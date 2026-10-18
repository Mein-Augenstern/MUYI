// 扫描 7 轨道目录生成 sidebar 结构（构建期运行，书导航自动跟随仓库生长）
// 用法：node build-sidebar.mts  →  输出 .vitepress/sidebar.json
import { readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { join, relative } from "node:path";

const ROOT = join(import.meta.dirname, "..", ".."); // 仓库根
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

function walk(dir, base) {
  // 返回该目录下的 { pages, groups }：md 文件为页，子目录递归为组
  const pages = [];
  const groups = [];
  for (const name of readdirSync(dir).sort((a, b) => a.localeCompare(b, "zh"))) {
    if (name.startsWith(".") || name === "node_modules") continue;
    const full = join(dir, name);
    const rel = "/" + relative(ROOT, full).replaceAll("\\", "/");
    if (statSync(full).isDirectory()) {
      const sub = walk(full, rel);
      if (sub.pages.length || sub.groups.length) {
        groups.push({
          text: name,
          collapsed: true,
          ...sub,
        });
      }
    } else if (name.endsWith(".md")) {
      pages.push({ text: titleOf(full), link: rel });
    }
  }
  return { pages, groups };
}

const sidebar = TRACKS.map((track) => {
  const abs = join(ROOT, track);
  const { pages, groups } = walk(abs, "");
  return {
    text: track,
    collapsed: false,
    items: [...pages, ...groups].filter(
      (i) => !(i.link && i.link.endsWith("/README")) || true
    ),
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
