// 自定义主题：默认主题 + 文末留言（giscus · GitHub Discussions）
// 评论数据落在 MUYI 仓库 Discussions 的 Announcements 分类；
// 需仓库安装 giscus App（https://github.com/apps/giscus）后生效
import DefaultTheme from "vitepress/theme";
import { h } from "vue";
import Comment from "./Comment.vue";

export default {
  extends: DefaultTheme,
  Layout: () =>
    h(DefaultTheme.Layout, null, {
      "doc-after": () => h(Comment),
    }),
};
