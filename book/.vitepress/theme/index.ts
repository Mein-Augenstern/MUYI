// MUYI 书站主题：默认主题 + MuyiHero 首页 + 文末留言
// v3: 恢复 Comment（giscus），custom.css 隐藏默认 hero/features
import DefaultTheme from "vitepress/theme";
import { h } from "vue";
import MuyiHero from "./MuyiHero.vue";
import Comment from "./Comment.vue";

import "./custom.css";

export default {
  extends: DefaultTheme,
  Layout: () =>
    h(DefaultTheme.Layout, null, {
      "home-hero-before": () => h(MuyiHero),
      "doc-after": () => h(Comment),
    }),
};
