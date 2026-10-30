OSGi 速查卡
====

一句话定位
----

* OSGi（Open Service Gateway initiative）：Java 上的**动态模块化规范**，比 JPMS 早约 20 年（1999 起），核心是"Bundle 化 + 热部署 + 服务注册"。
* 面试通常只问"了解吗"：答出 Bundle/Import-Export/生命周期三层 + 为什么没落即可拿分。

三大分层（标准答案骨架）
----

| 层 | 解决什么 | 关键概念 |
|---|---|---|
| Module 层 | 代码隔离与可见性 | Bundle、Export/Import-Package、版本区间 |
| Life Cycle 层 | 动态安装/启停 | BundleActivator、状态机、框架 API |
| Service 层 | 运行时协作 | ServiceRegistry、注册/发现/ServiceTracker |

模块化机制（Module 层）
----

* Bundle = 普通 jar + `META-INF/MANIFEST.MF` 中的 OSGi 元数据：

```
Bundle-SymbolicName: com.muyi.order
Bundle-Version: 1.2.0
Export-Package: com.muyi.order.api;version="1.2"
Import-Package: com.muyi.common;version="[1.0,2.0)"
Bundle-Activator: com.muyi.order.Activator
```

* 与 classpath 的本质区别：包级**显式**依赖（Import 必须匹配 Export），不是扁平全可见 → 解决"JAR Hell / classpath hell"。
* 类加载模型：**每个 Bundle 一个 ClassLoader**，加载器之间按 Import-Package 网状互查而非树状 → **打破了双亲委派**。
* 版本化：Import 可带版本区间，同一包多版本共存；`uses` 约束保证传递一致性（也是出名的复杂度来源）。

Bundle 生命周期（Life Cycle 层）
----

* 状态机（必背）：

```
            install
  (不存在) --------> INSTALLED
                        |  依赖解析成功
                        v
                    RESOLVED <---------
                        |  start          | stop
                        v                 |
                    STARTING ----------> STOPPING
                        |  Activator        |
                        |  .start() 完成    |
                        v                   |
                     ACTIVE ----------------+
                        | uninstall（多数状态可达 UNINSTALLED）
                        v
                   UNINSTALLED
```

* `BundleActivator`：`start(BundleContext)` / `stop(BundleContext)` 两个钩子，类似"模块级初始化/销毁"。
* 关键点：**stop ≠ uninstall**——stop 后回到 RESOLVED 可再 start；升级 = 卸旧装新，ClassLoader 随之替换，实现"热更新"。

服务注册（Service 层）
----

* 发布：`bundleContext.registerService(MyService.class, impl, props)`；消费：`getServiceReference()` + `ServiceTracker` 监听服务上下线。
* 服务是**动态的**：提供方 Bundle 停止则服务自动注销，消费方须处理"服务消失" → OSGi 最强也最烧脑的能力。

与 SPI 的关系（对比题眼）
----

| 维度 | JDK SPI（`META-INF/services`） | OSGi Service Registry |
|---|---|---|
| 发现方式 | classpath 扫描文件，一次性全加载 | 运行时注册表，动态增删 |
| 类可见性 | 单一 classpath，扁平无隔离 | Bundle 各自私有 ClassLoader，显式 Import |
| 版本管理 | 无 | 包/服务版本区间，多版本共存 |
| 生命周期 | 无（静态） | 与 Bundle 启停联动，可热插拔 |
| 侵入性 | 低（ServiceLoader.load） | 高（BundleActivator/BundleContext） |

* 桥接：OSGi R4.2 的 **Service Loader Mediation** 规范把 JDK SPI 包装成 OSGi 服务。思想一脉相承——"面向接口 + 运行时找实现"，SPI 是极简形态。

为什么被淘汰（面试收尾句）
----

* **复杂度失控**：Import/Export/uses 约束 + 网状类加载，排错成本极高（跨 Bundle 的 ClassCastException、"包未导出"是经典坑）。
* **工具链脱节**：Maven/Gradle 面向扁平 classpath，OSGi 元数据要靠 bnd 等额外维护，生态始终小众。
* **JPMS 接管**：Java 9 Project Jigsaw 提供语言级 module（`module-info.java`），虽无热部署但足够轻，覆盖大部分诉求。
* **架构范式转移**：微服务/容器化用"进程隔离 + 重启发布"替代"同 JVM 热插拔"，模块化单机收益缩水。
* 遗产：Eclipse IDE 基于 Equinox（OSGi 实现）起家；Apache Felix、Karaf 仍在小范围使用。

一句话对比记忆
----

* classpath = 大杂烩；OSGi = 带门禁的独立房间 + 广播台（服务注册）；JPMS = 只加了门禁；SPI = 门口的传单堆。

> 2026-10 依据主题重写（原文件为空壳）。
