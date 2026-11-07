// 自定义主题：默认主题 + MuyiHero 首页替换 + 文末留言（giscus）
// hero 插槽仅在 layout:home 页面注入（其他页面走默认 Layout）
import DefaultTheme from "vitepress/theme";
import { h } from "vue";
import Comment from "./Comment.vue";
import MuyiHero from "./MuyiHero.vue";
import "./custom.css";

export default {
  extends: DefaultTheme,
  Layout: () =>
    h(DefaultTheme.Layout, null, {
      "home-hero-before": () => h(MuyiHero),
      "doc-after": () => h(Comment),
    }),
};
