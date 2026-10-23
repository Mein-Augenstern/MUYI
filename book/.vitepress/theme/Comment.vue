<!--
  文末留言组件（giscus）：评论存 MUYI 仓库 GitHub Discussions（Announcements 分类）。
  - 仅在内容页挂载（首页跳过）；路由切换时按新 pathname 重建 iframe；
  - 主题跟随 VitePress 明暗切换（MutationObserver → postMessage 同步给 giscus）；
  - App 未安装/接口不可达时 giscus 官方脚本自身渲染占位，无需额外兜底。
-->
<script setup lang="ts">
import { useRoute } from "vitepress";
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";

const route = useRoute();
const host = ref<HTMLElement | null>(null);

const GISCUS_CFG = {
  "data-repo": "Mein-Augenstern/MUYI",
  "data-repo-id": "MDEwOlJlcG9zaXRvcnkxNDY3MDUxMzY=",
  "data-category": "Announcements",
  "data-category-id": "DIC_kwDOCL6K8M4DJanc",
  "data-mapping": "pathname",
  "data-strict": "0",
  "data-reactions-enabled": "1",
  "data-emit-metadata": "0",
  "data-input-position": "top",
  "data-theme": "light",
  "data-lang": "zh-CN",
  crossorigin: "anonymous",
  async: "",
};

function isContentPage(path: string): boolean {
  // 首页（书籍封面）不放留言；其余内容页（含 /repo 仓库说明页）都放
  const normalized = path.replace(/\/index\.html?$/, "/").replace(/\.html?$/, "");
  return normalized !== "/" && normalized !== "";
}

function mountGiscus() {
  const el = host.value;
  if (!el || !isContentPage(route.path)) return;
  el.innerHTML = "";
  const script = document.createElement("script");
  script.src = "https://giscus.app/client.js";
  for (const [k, v] of Object.entries(GISCUS_CFG)) script.setAttribute(k, String(v));
  el.appendChild(script);
}

let observer: MutationObserver | null = null;

function currentTheme(): string {
  return document.documentElement.classList.contains("dark") ? "dark" : "light";
}

function syncTheme() {
  const iframe = host.value?.querySelector<HTMLIFrameElement>("iframe.giscus-frame");
  iframe?.contentWindow?.postMessage(
    { giscus: { setConfig: { theme: currentTheme() } } },
    "https://giscus.app",
  );
}

onMounted(() => {
  mountGiscus();
  observer = new MutationObserver(syncTheme);
  observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
});

onBeforeUnmount(() => observer?.disconnect());

watch(
  () => route.path,
  () => nextTick(mountGiscus),
);
</script>

<template>
  <div v-if="isContentPage(route.path)" class="comment-wrap">
    <div ref="host" class="giscus-host" />
  </div>
</template>

<style scoped>
.comment-wrap {
  margin-top: 48px;
  padding-top: 24px;
  border-top: 1px solid var(--vp-c-divider);
}
</style>
