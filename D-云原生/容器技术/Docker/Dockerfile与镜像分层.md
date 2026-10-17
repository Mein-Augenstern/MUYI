Dockerfile 与镜像分层
====

看在前面
------

镜像是 Docker 的核心抽象：**分层、只读、可复用**——这三词展开就是缓存命中、多阶段构建、瘦身三板斧，也正好是面试 Docker 的三连问。后半接 compose 编排，素材取自 star-site 的 16 容器编排实践（`docker-compose.yml` + `docker/mysql-init/*.sql`），K8s 侧见[《K8s核心对象与架构》](../Kubernetes/K8s核心对象与架构.md)。

一、分层与缓存：为什么 Dockerfile 的行序是性能问题
------

镜像 = 一叠只读层 + 一个可写层的"洋葱"。Dockerfile 每条指令（FROM/RUN/COPY...）产出一层，层被内容寻址地存于本地，**两个镜像共享同底层**——同一台机器拉 10 个 Java 服务镜像，temurin 基础层只存一份：

```
 FROM eclipse-temurin:17     层 1  ┐
 RUN apt-get install ...     层 2  │  上层依赖下层；改动某行，该行以下的缓存全部作废
 COPY pom.xml                层 3  │
 RUN mvn dependency:go-offline 层4 │  依赖层：pom 很少变 → 长期命中
 COPY src                    层 5  │  源码层：天天变 → 只重建最后几层
 RUN mvn package             层 6  ┘
```

缓存判定规则（BuildKit）：**指令内容 + 父层 ID + COPY 文件的内容校验和**全部一致才命中。推论就是那句军规——**"变的行放后面，不变的行放前面"**：先 COPY 依赖清单再 COPY 源码，日常改动只重建最末两层；反着写（先 COPY src）则每次提交都从依赖下载开始重来。

```
 好的顺序                          坏的顺序
 COPY pom.xml                      COPY src        ← 每次提交都变
 RUN mvn dep:go-offline  命中✓     RUN mvn package      缓存全灭✗
 COPY src                重建       COPY pom.xml
 RUN mvn package         重建       RUN mvn dep:go-offline
```

调试技巧：`docker history <image>` 逐层看大小，找出门毯层（通常是那个 `RUN curl | tar` 装了工具又没删的层）；层是只读叠加，**层内删文件不减体积**（上层的删除标记盖不住下层的 blob）——清理必须在同一条 RUN 里完成。

二、多阶段构建：构建工具不进运行镜像
------

构建需要 Maven/JDK/源码，运行只需要一个 JRE 和 jar。多阶段用两段 FROM 解决"工具与产物分离"：

```dockerfile
# ---- 构建段：重工具，最后整体丢弃 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline          # 依赖层：pom 不变即命中
COPY src ./src
RUN mvn -q package -DskipTests

# ---- 运行段：只搬产物 ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app                                   # 非 root 运行
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
```

收益清单：镜像从 JDK+Maven 缓存（800MB+）降到 JRE+jar（~200MB）；攻击面同步缩小（没有 mvn、没有编译器、没有源码）；`MaxRAMPercentage` 顺手接上容器内存感知（配[排障手册](../Kubernetes/K8s生产排障手册.md)的 OOMKilled 一节）。

三、镜像瘦身清单
------

| 手段 | 做法 | 典型收益 |
| --- | --- | --- |
| 换基础镜像 | full → `slim`/`alpine`/`distroless` | 数百 MB → 数十 MB |
| 多阶段构建 | 见上，工具链不进运行镜像 | 60%+ 体积 |
| .dockerignore | 排除 .git/target/node_modules | 避免构建上下文膨胀+缓存失效 |
| RUN 合并并同层清理 | `apt-get install ... && apt-get clean && rm -rf /var/lib/apt/lists/*`（一条 RUN 内） | 防止"装了又删"层照样占体积 |
| 固定 tag | 不用 `latest`，钉 `17-jre-alpine@sha256:...` | 可复现构建，缓存不被暗更新打断 |
| 非 root 用户 | adduser + USER | 安全基线，配 K8s runAsNonRoot 一致 |

`.dockerignore` 常被漏掉，它同时管两件事——构建上下文传输量与 COPY 缓存失效范围：

```
.git
target/
node_modules/
*.log
.idea/
docker-compose*.yml     # 编排文件进镜像没有意义，只会让上下文变大
README.md
```

一句话原则：**镜像体积 = 拉取时长 = 攻击面 = 启动速度**，四件事同一件事。

四、compose 编排要点：star-site 16 容器实践
------

star-site 本地一键起全栈：MySQL/Redis/Nacos 等基础设施 + 11 个 Java 微服务（gateway/auth/health/finance/payment...）+ Python ai-service + 前端，共 16 个容器。编排的核心不是"写 16 个 service"，而是把**启动顺序、健康门槛、配置注入**三件事声明清楚：

```yaml
services:
  mysql:
    image: mysql:8.0
    volumes:
      - mysql-data:/var/lib/mysql                      # 命名卷：数据活过容器
      - ./docker/mysql-init:/docker-entrypoint-initdb.d  # 首启按序执行建库 SQL
    healthcheck:                                        # 只有"能连"才算健康
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 5s
      retries: 10
  nacos:
    image: nacos/nacos-server:v2.3.0
    depends_on: { mysql: { condition: service_healthy } }
    healthcheck: { test: ["CMD", "curl", "-f", "http://localhost:8848/nacos"], interval: 10s, retries: 12 }
  gateway:
    build: { context: ./star-site-platform-gateway }    # 源码即编排的一部分
    environment:
      SPRING_PROFILES_ACTIVE: docker                    # 配置随环境注入，不烧进镜像
      NACOS_ADDR: nacos:8848
    depends_on: { nacos: { condition: service_healthy } }
    ports: [ "8080:8080" ]                              # 只有入口暴露宿主端口
    restart: unless-stopped
volumes:
  mysql-data:
```

四条要点展开：

* **`depends_on` 带 `condition: service_healthy`**：等 MySQL"健康"而不只是"启动"——MySQL 进程起来到可写入之间有数十秒空窗，只依赖启动顺序的 Java 服务会在注册中心里带着连不上的数据源反复崩溃（compose 里就是 CrashLoop 的翻版）；
* **healthcheck 是全编排的时钟**：MySQL → Nacos → Java 服务逐级 gate，链路上每一环用它自己的"可用性判据"（mysqladmin ping / HTTP 探活）；
* **镜像不含环境，环境变量注入**：同一镜像跑本地/测试/生产，差异全在 environment 与外部配置中心（Nacos），这也是 K8s ConfigMap 思想的单机版；
* **网络与暴露面**：compose 默认单 bridge 网络，容器名即 DNS；只有 gateway/前端映射宿主端口，数据库不对外暴露——最小暴露面原则。

追问链
------

> **面试官：RUN、CMD、ENTRYPOINT 什么区别？**

RUN 是**构建期**执行并产生新层（装依赖）；CMD/ENTRYPOINT 是**运行期**启动命令。ENTRYPOINT 定住"这个镜像是什么程序"，CMD 给默认参数——容器启动时命令行传参会**整体替换 CMD**但只**追加到 ENTRYPOINT**。所以 ENTRYPOINT 用 exec 数组形式写 `["java","-jar","app.jar"]`，让 java 成为 PID 1 信号接收者（能收到 SIGTERM 优雅退出）；写成 shell 形式则 java 挂在 sh 下面，docker stop 的信号到不了应用。

> **面试官：镜像里删了文件为什么体积没变小？**

层是叠加的只读 blob：下层 100MB 的文件，上层一条 `rm` 只是加了条"删除标记"，拉取时两层都要下。要么把删除并进产生它的那条 RUN（同层内删除才是真删除），要么多阶段构建只 COPY 需要的产物。

> **面试官：COPY 和 ADD 用哪个？**

默认 COPY——语义纯粹（本地→镜像）。ADD 额外做两件事：自动解压本地 tar 进镜像、支持 URL 远程拉取，两者都是"读 Dockerfile 的人猜不到的行为"。URL 下载这种事应该在 RUN 里 curl + 校验 + 清理完成。只有"本地 tar 自动解压进镜像"（如预置静态资源）算 ADD 的正当用途。

> **面试官：容器一启动就退，怎么排查？**

先 `docker run -it --entrypoint sh <image>` 进去手动跑 ENTRYPOINT 命令看报错；再查两件事：前台进程是否后台化了（主进程退出容器就退）；PID 1 是否是 shell 包装（信号问题）。这在 K8s 里就是 CrashLoopBackOff 秒退的镜像侧排查，见[排障手册](../Kubernetes/K8s生产排障手册.md)。

面试官视角小结
------

1. **分层三性质**：只读叠加、内容寻址缓存、跨镜像共享底层的判定是"指令+父层+文件校验和"——所以行序即性能，"变的放后面"；
2. **多阶段一句话**：构建工具与运行产物分离，体积、攻击面、缓存粒度三收；层内删文件不减体积，清理必须同 RUN 完成；
3. **瘦身清单按序背**：基础镜像换小 → 多阶段 → .dockerignore → RUN 合并清理 → 固定 tag → 非 root；
4. **compose 编排三件事**：healthcheck 做时钟、depends_on condition 做门槛、环境变量做配置注入；star-site 16 容器靠"MySQL 健康才起 Nacos、Nacos 健康才起 Java 服务"的链式 gate 一键起全栈；
5. **RUN/CMD/ENTRYPOINT**：构建期 vs 运行期；exec 形式让应用当 PID 1，信号才通。

> 2026-10 新增（留白补齐）。姊妹篇：[K8s核心对象与架构](../Kubernetes/K8s核心对象与架构.md)。
