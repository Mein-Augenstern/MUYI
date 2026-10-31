Go上手实战-gin-gRPC服务骨架与部署
====

场景全景（见 [Go实战场景全景-从云原生到AI工程](./Go实战场景全景-从云原生到AI工程.md)）看完，本篇动手：**用 Javaer 的肌肉记忆搭一个 Go 生产件的最小骨架**——gin HTTP + gRPC 双协议服务 + GORM 访问 MySQL + Docker scratch 部署，每一步都对照 Spring Boot 的"等价物"，最后给压测体感与常见翻车点。目标是照着本文一天内能跑起来第一个可部署的 Go 服务。

1 工程初始化：go mod 就是你的 pom
------

```bash
mkdir demo-svc && cd demo-svc
go mod init github.com/me/demo-svc
go get github.com/gin-gonic/gin@latest
go get google.golang.org/grpc
go get gorm.io/gorm gorm.io/driver/mysql
```

对照关系：`go.mod`≈pom.xml、`go get`≈增加依赖坐标、`go.sum`≈lockfile（提交入库）、`go mod tidy`≈清理未用依赖。**没有中央仓库的 settings.xml 心智**，模块直连 proxy.golang.org（国内配 `GOPROXY=https://goproxy.cn,direct`，对应 Maven 镜像源的角色）。

目录骨架（Kratos 风格的极简版，Javaer 可理解为 controller/service/dto 分层的平铺）：

```
demo-svc/
├── cmd/server/main.go     # 入口（≈Application.java + 装配）
├── internal/handler/      # HTTP/RPC 处理器（≈Controller）
├── internal/service/      # 业务逻辑（≈Service）
├── internal/repo/         # GORM 数据访问（≈Mapper/Repository）
└── api/proto/             # protobuf 契约（≈api 模块）
```

2 HTTP 层：gin 十分钟对齐 Spring MVC 手感
------

```go
r := gin.Default()                                  // ≈ @SpringBootApplication 自带日志与 recover 中间件
r.GET("/api/orders/:id", func(c *gin.Context) {     // ≈ @GetMapping("/api/orders/{id}")
    id := c.Param("id")
    order, err := service.GetOrder(c.Request.Context(), id)
    if err != nil {                                 // Go 没有 @ControllerAdvice，错误就地翻译
        c.JSON(500, gin.H{"error": err.Error()})    // 生产骨架应收敛为统一错误码映射中间件
        return
    }
    c.JSON(200, order)
})
r.Run(":8080")
```

体感差异三条：**没有注入容器**——依赖全部显式构造后传入（`service := NewService(repo)`），要接口mock 时把接口定义在消费方（隐式实现的红利）；**中间件即函数链**（`r.Use(Logger(), Recovery())`），对照 Filter/Interceptor 概念一一对应但无注册魔法；**参数校验用 validator tag**（`binding:"required"`），手感回到 Bean Validation。

3 gRPC 层：契约先行，protoc 生成代码
------

```protobuf
syntax = "proto3";
package order.v1;
option go_package = "github.com/me/demo-svc/api/orderpb";

message GetOrderRequest { string id = 1; }
message Order { string id = 1; int64 amount = 2; string status = 3; }
service OrderService { rpc GetOrder(GetOrderRequest) returns (Order); }
```

`protoc --go_out=. --go-grpc_out=. api/proto/order.proto` 生成桩代码——对照 Dubbo 的 interface+序列化，gRPC 把**契约即代码**做成了默认（契约变更的 diff 就是 API 变更评审现场）。服务端实现生成的 `UnimplementedOrderServiceServer` 内嵌——组合替代继承的活例子（见 [从Java视角看Go-开篇](./从Java视角看Go-开篇.md) 差异①）。

超时与取消用 `context.Context` 全链路传递——**这是 Go 服务端的心脏**：入口 `c.Request.Context()` 自动带 HTTP 取消信号，向下穿透 repo 层的 `db.WithContext(ctx)`，客户端一断全链路释放。Javaer 对应理解：一个语言级强制的、不可绕过的 RequestScope 取消令牌。

4 数据层：GORM 的"够用但别信魔法"
------

```go
type OrderPO struct {            // ≈ MyBatis 的 DO
    ID     string `gorm:"primaryKey;size:32"`
    Amount int64
    Status string `gorm:"size:16;index"`
}
db, _ := gorm.Open(mysql.Open(dsn), &gorm.Config{})
db.WithContext(ctx).Where("status = ?", "PAID").Limit(100).Find(&orders)
```

与 MyBatis/JPA 的关键差异：**GORM 的链式 API 会生成 SQL，复杂查询建议显式 `Raw("...")`**——对照库里的教训是同一课：慢 SQL 的坑不分语言（联合索引、范围条件、回表的原理见 [线上慢SQL复盘-范围条件断前缀与回表风暴](../框架与中间件/数据库/线上慢SQL复盘-范围条件断前缀与回表风暴.md)，索引原理见 [MySQL索引详解](../框架与中间件/数据库/MySQL索引详解.md)）。**连接池用 `database/sql` 原生的 SetMaxOpenConns/SetMaxIdleConns**——名字不同语义同 Hikari，池满事故的防线思路照搬即可。

5 部署：scratch 镜像是 Go 的"开屏暴击"
------

```dockerfile
# 多阶段：构建期用完整工具链
FROM golang:1.22 AS build
WORKDIR /src
COPY . .
RUN CGO_ENABLED=0 go build -ldflags="-s -w" -o /out/demo ./cmd/server

# 运行期：空镜像 + 一个二进制
FROM scratch
COPY --from=build /out/demo /demo
ENTRYPOINT ["/demo"]
```

`CGO_ENABLED=0` 产出**纯静态二进制**，scratch（空）镜像跑得动——典型体积 **15~20MB**，对照 Spring Boot 镜像 300MB+ 起步。这一段就是"Go 为什么适合云原生"的具象化（场景论证见场景全景篇）。K8s 侧注意：scratch 没有 shell，探针用 HTTP 而非 exec；时区与 CA 证书要显式 COPY 进去，这是新手第一大翻车点。

6 压测体感与三个常见翻车点
------

* 单实例 4C8G、gin+GORM 的简单查询接口，**1w+ QPS 量级、P99 个位数毫秒**是正常预期（同一逻辑 Spring Boot 约打七折但同量级——差距在大内存高并发下的稳定性曲线，不在峰值）；
* **翻车点一：全局 map 并发写**——panic 不可 recover，上 `sync.RWMutex` 或分片锁；
* **翻车点二：goroutine 泄漏**——每个 `go func()` 必须能回答"谁在什么条件下让它退出"（context 取消/channel 关闭），`pprof` 的 goroutine 剖析是定位工具；
* **翻车点三：rows 未关闭 / resp.Body 未关闭**——`defer rows.Close()` 是 JDBC try-with-resources 的纪律对应物，漏了连接池慢慢漏干（与 [SQL limit 与 limit offset 用法区别](../框架与中间件/数据库/SQL%20limit%20与%20limit%20offset%20用法区别.md) 同源的池资源意识）。

7 从骨架到生产的清单
------

配置（viper，≈application.yml 多环境）→ 结构化日志（zap/slog，JSON 行）→ 指标（prometheus/client_golang，/metrics）→ 链路（OpenTelemetry-Go）→ 优雅关停（`signal.NotifyContext` + `server.Shutdown`，对照 Spring 的 graceful shutdown）→ CI（`go vet`/`golangci-lint`/`go test -race`）。`-race` 检测器是 Go 送的 Detector 级福利——**测试期开它，数据竞争在上线前现形**。

面试追问链
------

* gin 和标准库 net/http 怎么选？→ 中间件生态与路由分组需求决定；1.22 路由增强后纯标准库的门槛进一步降低；
* context 的取消是怎么传到 SQL 执行的？→ db.WithContext 挂到驱动层，ctx.Done 时中断查询——所以每个 IO 调用都该收 ctx 参数；
- GORM 与 MyBatis 怎么看待彼此？→ 链式 API vs 显式 SQL，复杂查询两边终点都是手写 SQL；ORM 不豁免索引知识；
* scratch 镜像丢了什么？→ shell（探针换 HTTP）、CA（证书要 COPY）、tzdata（时区要 COPY）；
* go test -race 的原理？→ happens-before 向量时钟插桩，只报真竞争但只报跑到过的路径——生产前必须压测流量下也开过；
* 优雅关停的完整链路？→ SIGTERM → 停收新请求 → context 取消让 handler 主动收尾 → 强杀兜底（K8s terminationGracePeriod）。

面试官视角小结
------

判断"真写过 Go 服务"的信号全在细节：主动提 context 全链路传递、`-race`、scratch 镜像的 CA/tzdata 坑、goroutine 退出条件设计——这四点每一个都踩过才会说。能同时对照 Spring 讲清"哪个概念对应哪个、哪个对应不上"（比如没有 ControllerAdvice 就要自己做错误中间件），说明是带着工程判断迁移而不是抄教程。

> 2026-10 新增（Go 轨第三篇）。
