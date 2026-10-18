---
# 书首页（hero + 七轨 Features）——VitePress 默认 home 布局，Vue 文档同款
layout: home

hero:
  name: MUYI
  text: 后端工程师的生产级知识体系
  tagline: 每一篇都从生产代码回写，可验证到真实类名。361 篇实文 · 7 轨道 · 121 题自测闭环 · 零空壳零死链
  actions:
    - theme: brand
      text: 开始阅读
      link: /A-后端内核/
    - theme: alt
      text: 面试自测清单（121 题）
      link: /自测清单
    - theme: alt
      text: GitHub 仓库
      link: https://github.com/Mein-Augenstern/MUYI

features:
  - icon: ☕
    title: A · 后端内核
    details: Java（并发/JVM/Spring）× Python · MySQL/Redis/Kafka/Netty · 计算机基础 · 面经复盘——近十年主体
    link: /A-后端内核/
  - icon: 🤖
    title: B · AI 工程
    details: LLM 基础 / RAG / Agent 工程与安全 / Prompt——全部产自 LangChain 服务实战
    link: /B-AI工程/
  - icon: 🏛️
    title: C · 架构
    details: 分布式系统 / 微服务 / DDD 与方法论（三套系列 20 篇）/ 系统设计四场景
    link: /C-架构/
  - icon: 🏦
    title: G · 业务领域
    details: 金融信贷域内四层 15 篇（双样本参照，样本资料已移除公开库）· 行业认知（宏观框架）
    link: /G-业务领域/
  - icon: ☁️
    title: D · 云原生
    details: Docker / Kubernetes / Serverless——云原生运行形态独立成轨
    link: /D-云原生/
  - icon: 🧮
    title: E · 算法
    details: LeetCode Hot100 高频题解，代码全部编译验证，每周 3 题滚动
    link: /E-算法/
  - icon: 🖥️
    title: F · 前端
    details: PC 端（Vue3）/ 无线端（小程序双线程）/ 浏览器与性能 / 工程化 / TypeScript
    link: /F-前端/
---
