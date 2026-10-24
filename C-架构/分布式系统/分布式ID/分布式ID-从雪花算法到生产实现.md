分布式 ID：从雪花算法到生产实现
====

看在前面
------

库内已有两篇理论文。一篇是《雪花算法的原理和Java实现》（A-后端内核/框架与中间件/数据库/）。另一篇是《美团-Leaf——美团点评分布式ID生成系统》（同目录）。它们回答的是"雪花算法是什么、Leaf 怎么做"。

这篇是**生产实践反哺**。star-site-backend 落地了一套分布式 ID 生产组件（雪花生成器 + 数据库 sequence 表分配机器位）。本文把实战中真正踩到的问题回写进知识库。这些问题包括：workerId 怎么分、时钟回拨怎么办、多服务怎么隔离、启动风暴怎么扛。

组件对应源码（阅读顺序即依赖顺序）：

* `star-site-common/src/main/java/com/xp/starsite/common/util/IdGenerator.java`——雪花生成器本体
* `star-site-common/src/main/java/com/xp/starsite/common/id/SequenceWorkerIdAllocator.java`——机器位分配器（本文主角）
* `star-site-common/src/main/java/com/xp/starsite/common/log/StarSiteLogConfig.java`——Spring 装配与降级阶梯
* `docker/mysql-init/22-sys-sequence.sql`——`sys_sequence` 表 DDL

一、方案全景对比
------

先把主流方案放到一张表里。评价维度选了四个生产里真正会疼的点：

* **有序性**：影响 InnoDB 聚簇索引写入性能
* **长度**：主键越短越好，二级索引都带主键
* **依赖**：发号是否被第三方组件卡脖子
* **时钟回拨**：雪花系的命门

| 方案 | 有序性 | 长度 | 依赖 | 时钟回拨 |
| --- | --- | --- | --- | --- |
| UUID | 无序（v4 随机；v1 时间在前但含 MAC） | 128 位 / 36 字符 | 无 | 无影响 |
| 数据库自增（单机 / 步长分流） | 单机单调递增；多机步长只有趋势递增 | 8 字节（BIGINT） | 强依赖 DB，DB 挂即停发 | 无影响 |
| 号段模式（segment） | 趋势递增（buffer 内近似单调） | 8 字节 | 弱依赖 DB（号段缓冲扛短期故障） | 无影响 |
| 雪花（snowflake） | 趋势递增 | 8 字节 | 无外部依赖，但要解决 workerId 分配 | 强依赖时钟，必须处理 |
| Leaf-snowflake | 趋势递增 | 8 字节 | 弱依赖 ZK（workerId 本地文件缓存兜底） | 启动时校验 + 回拨拒绝/等待 |

几条点评：

* **UUID 当主键**在 InnoDB 下是双重伤害：36 字符太长（所有二级索引都隐含主键），且无序导致页分裂频繁。除非用 UUIDv7 这类时间有序变体，否则直接排除出"物理主键"候选。
* **数据库自增**的问题不是性能而是**耦合与扩展**。发号能力和业务库绑死。多机步长分流（Flickr Ticket Server）扩容要重排步长，机器多了是运维噩梦。
* **号段模式**把 DB 访问摊薄成 1/step，是"每次发号都打 DB"的正确改良。但 DB 仍在发号热路径上，还引入了 TP999 尖刺（号段切换）和双 buffer 的复杂度。
* **雪花**是唯一"运行期零外部依赖"的方案：号在内存里组装，DB/ZK 只在启动时用一次。代价是两个必须自己回答的问题：workerId 怎么分、时钟回拨怎么办。

star-site 的选型结论：**运行期零依赖 > 一切**。家庭服务体系的发号 QPS 远够不到号段模式的收益区，而"DB 故障时连 id 都发不出来"是不可接受的。所以选雪花本体 + 复用现有 MySQL 在**启动时**解决 workerId——这正是本文第四章的主题。

二、雪花算法原理回顾（简）
------

位布局细节库内专文已覆盖，这里只摆生产相关的几个数字（详见《雪花算法的原理和Java实现》）：

```
| 1 bit |    41 bit     |   10 bit   |  12 bit   |
| 符号位 |    时间戳     |   机器位    |   序列位   |
| 恒 0  | 毫秒，69 年   | 0 ~ 1023   | 每毫秒 4096 |
```

* 时间戳在高位、序列在低位，整个 id **趋势递增**，对 InnoDB 聚簇索引友好；
* 10 位机器位 = 全平台最多 **1024 个并发实例**，这就是 `MAX_WORKER_ID = 1023` 的来源，也是后文回绕问题的根源；
* 12 位序列 = 单实例单毫秒 4096 个，理论 QPS 上限 **409.6w/s**——这个余量后面会被"借"去抵消时钟回拨。

三、生产实现的三个真实问题
------

理论文讲位布局，生产里真正让方案成立的是三件事。

### 3.1 workerId 分配：为什么不能写死、不能随机

**写死的问题不是"忘了改配置"，是"全平台默认撞号"。** 旧的写法是配置项 `star.snowflake.worker-id`，默认值 1：

```java
public IdGenerator(@Value("${star.snowflake.worker-id:1}") long workerId) {
```

单服务单实例时一切正常。但 star-site 是一组微服务（finance、payment、archive、insurance、recheck……）共享这个 common 包——**每个服务都用默认值 1**。两个不同 JVM 拿同一个 workerId，同一毫秒各自从序列 0 开始递增，撞号是必然事件。只是概率上被低流量掩盖。更阴险的是：撞出来的重复主键在 insert 时报 duplicate key 还算走运，若是先落消息/缓存后落库，就是数据串号。StarSiteLogConfig 的注释把这件事钉在案底上："根治全平台共享 workerId=1 的跨服务撞号问题"。

**随机器哈希/随机数也不行**：机器位只有 1024 个槽，随机数的生日悖论来得很快。约 40 个实例就有 50% 概率两两撞号。而且随机分配无凭据，撞了号排查时连"谁和谁撞了"都查不出来。

所以分配必须满足三个条件：

* **全局互斥**：同一号至多一个活实例
* **可审计**：谁在何时占了哪个号
* **无新增基础设施**：不想为发号引入 ZK/独立发号服务集群

数据库一行计数 + 原子 SQL 恰好同时满足三条。

### 3.2 时钟回拨：检测与三种策略

NTP 校时、宿主机迁移、闰秒，都会让 `System.currentTimeMillis()` 倒着走。雪花的时间戳来自本机时钟，回拨意味着可能重新生成"过去某毫秒"的 id。这样会与那毫秒已发出的 id 撞号。业界三种应对：

1. **拒绝**：检测到回拨直接抛异常/返回错误码，时钟追平前停发。最安全，可用性最差；
2. **等待**：回拨幅度小（Leaf 的策略是 ≤5 ms 等待 2 倍偏差时长，追不平再抛异常）；
3. **借序列位**。把时间戳钳制在上次发号毫秒不动，用序列位的 4096 个余量继续发。效果是等物理时钟自己追上来。

star-site 的 `IdGenerator.nextId()` 用的是 **3 号策略 + 2 号兜底**：

```java
public synchronized long nextId() {
    long timestamp = System.currentTimeMillis();
    if (timestamp < lastTimestamp) {
        // 时钟回拨：等待追平（回拨秒级内可接受）
        timestamp = lastTimestamp;
    }
    if (timestamp == lastTimestamp) {
        sequence = (sequence + 1) & SEQUENCE_MASK;
        if (sequence == 0) {
            // 序列耗尽，自旋到下一毫秒
            while ((timestamp = System.currentTimeMillis()) <= lastTimestamp) {
                // busy wait
            }
        }
    } else {
        sequence = 0L;
    }
    lastTimestamp = timestamp;
    return ((timestamp - EPOCH) << TIMESTAMP_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;
}
```

读代码要读出三层语义：

* `timestamp = lastTimestamp` 这行**钳制**是全部关键——回拨发生后，虚拟时间冻结在上次发号毫秒，id 的时间戳字段不再倒退；
* 随后落入 `timestamp == lastTimestamp` 分支，序列继续 +1，即**用序列位余量吸收回拨窗口内的发号需求**。回拨 500 ms、期间发号速率低于 4096/ms，就无感扛过；
* 序列耗尽（`sequence == 0` 翻转）才 busy wait 自旋等物理时钟追平——这是**用吞吐换正确性**的最后防线。

对比 Leaf-snowflake 的"≤5 ms 等 2 倍偏差、否则抛异常"：它是发号服务视角（不能让自旋打满 CPU）。我们是业务进程内嵌视角（`synchronized` 纳秒级临界区，自旋窗口极短）。**两种都对，前提不同。** 这个策略的适用边界要记住：回拨幅度 ≫ 秒级且高并发时，自旋会卡住发号。到那个量级就该上 Leaf 的"启动期多节点时钟互校 + 拒绝启动"了。

### 3.3 多服务的隔离：一张共享的 sys_sequence 表

多服务场景下，workerId 的"全局"必须是**全平台**全局——finance 的实例 3 和 payment 的实例 3 是撞号的。star-site 的答案是单库一张共享表 `sys_sequence`，`service_name` 做隔离键：

* **一行是全局池**：`service_name = '__global__'`，`worker_id` 字段 = 全平台已分配到的最大值，所有服务所有实例的号都从这一行原子取出；
* **每服务一行所有权记录**：记录"这个服务最近的占用实例是谁、何时启动"，纯审计用途，不参与分配。`recordOwnership` 的注释原话是"不影响分配"。

注意这里没有"每服务独立计数"——各服务独立从 0 数，跨服务立刻撞号。**分配必须单点（`__global__` 行），审计才分服务。**

四、star-site 生产实现解析
------

### 4.1 表结构与"双保险"建表

`docker/mysql-init/22-sys-sequence.sql`：

```sql
CREATE TABLE IF NOT EXISTS `sys_sequence` (
  `service_name` VARCHAR(64) NOT NULL COMMENT '__global__=全局池计数行，其余为实例占用记录',
  `worker_id`    INT NOT NULL DEFAULT 0 COMMENT '该行当前的机器位（全局池行=已分配到的最大值）',
  `instance`     VARCHAR(128) DEFAULT NULL COMMENT '最近占用实例标识(服务@pid@启动戳)',
  `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`service_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='雪花 worker-id 分配序列表';

-- 全局池计数行（幂等 seed）
INSERT IGNORE INTO `sys_sequence` (`service_name`, `worker_id`, `instance`)
VALUES ('__global__', 0, 'pool');
```

一个值得记下的工程细节：**DDL 存在两处且保持同步**——init 目录一份（新环境初始化用），`SequenceWorkerIdAllocator` 里内嵌一份：

```java
private void ensureTable(Connection conn) throws SQLException {
    try (Statement stmt = conn.createStatement()) {
        stmt.execute(DDL);   // CREATE TABLE IF NOT EXISTS
        try (PreparedStatement seed = conn.prepareStatement(
                "INSERT IGNORE INTO sys_sequence (service_name, worker_id, instance) VALUES (?, 0, ?)")) {
            seed.setString(1, GLOBAL_POOL_KEY);
            seed.setString(2, "pool");
            seed.executeUpdate();
        }
    }
}
```

原因写在 SQL 文件注释里："MySQL 容器 volume 已初始化过的话，本文件不会自动执行"。**组件自带 `IF NOT EXISTS` 建表 + `INSERT IGNORE` 幂等 seed**，存量环境升级零迁移脚本。这套"DDL 双保险"适合所有随组件走的辅助小表。

### 4.2 核心 trick：`LAST_INSERT_ID(worker_id + 1)` 原子租约

分配的核心 SQL（`takeFromGlobalPool`）：

```java
private long takeFromGlobalPool(Connection conn) throws SQLException {
    try (PreparedStatement update = conn.prepareStatement(
            "UPDATE sys_sequence SET worker_id = LAST_INSERT_ID(worker_id + 1), "
                    + "instance = ?, updated_at = NOW() WHERE service_name = ?");
         PreparedStatement select = conn.prepareStatement("SELECT LAST_INSERT_ID()")) {
        update.setString(1, instanceTag);
        update.setString(2, GLOBAL_POOL_KEY);
        update.executeUpdate();
        try (ResultSet rs = select.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("LAST_INSERT_ID() 无返回");
            }
            return rs.getLong(1);
        }
    }
}
```

拆开讲为什么这一条 UPDATE + 一次回读是**原子的**：

**第一，`LAST_INSERT_ID(expr)` 是 MySQL 的会话函数，不是列。** 带参调用 `LAST_INSERT_ID(expr)` 的语义是"把 expr 存进当前会话的 last_insert_id 槽位并返回 expr"。于是 `SET worker_id = LAST_INSERT_ID(worker_id + 1)` 在**同一条语句内**完成"自增 + 把自增结果抄进会话"两件事。它和常见的"自增列 + `LAST_INSERT_ID()`"（Flickr Ticket Server 的 `REPLACE INTO` 模式）是两码事。后者要求专门的 auto_increment 表。前者**对任意表的任意列生效**，不需要表有任何自增属性。

**第二，读-改-写在单条 UPDATE 内完成，天然串行。** InnoDB 执行 UPDATE 时对 `__global__` 行加排他行锁，`worker_id + 1` 的求值发生在锁内。并发启动的多个实例在这行上排队，各自看到前一个的落盘结果再加一。语句结束（autocommit 提交）锁才释放。

**不存在"两个连接同时读到 7、都写回 8"的窗口**。那个窗口只存在于"UPDATE 完再 SELECT"的两语句写法里。UPDATE 的锁在语句提交后就释放了，随后的 SELECT 读到的是别人加过一的值，租约串号。

**第三，回读不读表，读会话。** 随后的 `SELECT LAST_INSERT_ID()` 取的是会话槽位里那份抄本，不发生表访问、不加锁、不受并发干扰。代价是一个强约束：**两条语句必须在同一个 Connection 上**。这就是方法签名收 `Connection conn`、两条 PreparedStatement 共用它、整体在 `try (Connection conn = dataSource.getConnection())` 里完成的原因。`LAST_INSERT_ID()` 是 per-connection 的会话状态，换连接读到的是 0 或别人的值。

**这个约束是隐形的**。哪天有人好心把这段重构成 `jdbcTemplate.update(...)` + `jdbcTemplate.query(...)` 两调用，编译器不显示错误，测试小流量也过。生产上才会随机串号——重构前请回看这段。

**第四，审计顺路捎带。** `instance = ?, updated_at = NOW()` 搭同一条 UPDATE，一次往返同时完成"取号 + 记录最近取号者"。

对比 Leaf-segment 取号段的 SQL（`UPDATE table SET max_id = max_id + step WHERE biz_tag = xxx` + `SELECT`，包在事务里）：语义同为"原子推水位"。区别在于 Leaf 一次取一个号段（step 个），我们一次取一个号。因为我们整个生命周期只在启动时取一次，号段毫无意义。

### 4.3 回绕：机器位只有 10 位

全局池单调递增，总会越过 1023。`allocate()` 的处理：

```java
public long allocate() throws SQLException {
    try (Connection conn = dataSource.getConnection()) {
        ensureTable(conn);
        long workerId = takeFromGlobalPool(conn);
        recordOwnership(conn);
        if (workerId > MAX_WORKER_ID) {
            // 回绕：雪花机器位只有 10 位。旧实例若已死亡则无碰撞；存活实例同 id 的
            // 碰撞需要「同毫秒 + 同序列」同时命中，概率可忽略但必须可见
            log.warn("[id] workerId 回绕: service={} 分配值 {} 超上限 {}，重置全局池为 0",
                    serviceName, workerId, MAX_WORKER_ID);
            resetGlobalPool(conn);
            return 0;
        }
        return workerId;
    }
}
```

策略是**告警 + 重置 + 回绕**，注释把风险算得很清楚。回绕后与"存活旧实例"同 id 的碰撞需要"同毫秒 + 同序列"同时命中，概率可忽略。**但必须可见**（`log.warn` 大声喊）。1024 个槽、每实例一个号、重启即新号，意味着回绕发生在约 1024 次实例启动之后。对个人量级的服务是永不会触发的分支。对弹性扩缩频繁的场景就是真实分支——所以它存在，且带告警。

### 4.4 重启后的租约语义与所有权记录行

先说清楚这套设计的租约语义：**号是"终身制"的，只借不还**。

* 实例重启**不复用**旧号，而是从全局池取**新号**（计数只增不减）。因此重启后的实例与"重启前的自己"是不同 workerId，天然不撞；
* 不需要心跳续约、不需要释放、不需要 TTL——没有租约过期这个故障模式。代价是号池单调消耗，最终走到 4.3 的回绕分支；
* 所有权记录行（`recordOwnership`）是**事后审计的凭据**：

```java
/** 记录服务实例的占用（各自 sequence 行，审计可见；不影响分配） */
private void recordOwnership(Connection conn) throws SQLException {
    try (PreparedStatement stmt = conn.prepareStatement(
            "INSERT INTO sys_sequence (service_name, worker_id, instance, updated_at) "
                    + "VALUES (?, 0, ?, NOW()) "
                    + "ON DUPLICATE KEY UPDATE instance = VALUES(instance), updated_at = NOW()")) {
        stmt.setString(1, serviceName);
        stmt.setString(2, instanceTag);
        stmt.executeUpdate();
    }
}
```

实例标识由装配层拼好传入：`appName + "@" + PID + "@" + System.currentTimeMillis()`（服务@pid@启动戳）。排查"这个时间点哪个实例在跑"时，`updated_at` + `instance` 就是第一现场。配合 `__global__` 行的 `worker_id`（=已分配最大值，天然水位线）能框定当时的号段范围。

**如实记录一个已知边界**：细读上面的 SQL 会发现，服务行的 `worker_id` 列插入的是常量 0。`ON DUPLICATE KEY UPDATE` 也只更新 `instance` 和 `updated_at`。**分配到的实际号并没有落进服务行**，只在启动日志 `[id] workerId 经 sequence 表分配: service={}, workerId={}` 里。即"哪个服务哪个实例拿的是几号"要靠 grep 日志，表里的服务行只能回答"谁在何时启动过"。修复很轻：`recordOwnership` 加个参数，把 `workerId` 一起 upsert 进去。记在这里，作为"注释宣称的审计能力 vs SQL 实际写入"的一次教训——**javadoc 承诺的行为要和 SQL 逐字段对账**。

### 4.5 装配与降级阶梯

`StarSiteLogConfig#idGenerator` 是组件入口，也是可用性设计的集中体现：

```java
@Bean
public com.xp.starsite.common.util.IdGenerator idGenerator(Environment env,
                                                           ObjectProvider<DataSource> dataSourceProvider) {
    String appName = env.getProperty("spring.application.name", "unknown");
    Long explicit = env.getProperty("star.snowflake.worker-id", Long.class);
    if (explicit != null) {
        log.info("[id] workerId 使用显式配置: service={}, workerId={}", appName, explicit);
        return new com.xp.starsite.common.util.IdGenerator(explicit);
    }
    DataSource dataSource = dataSourceProvider.getIfAvailable();
    if (dataSource == null) {
        log.warn("[id] 无可用 DataSource，workerId 回落 hash: service={}", appName);
        return new com.xp.starsite.common.util.IdGenerator(fallbackWorkerId(appName));
    }
    try {
        long workerId = new SequenceWorkerIdAllocator(dataSource, appName,
                appName + "@" + env.getProperty("PID", "0") + "@" + System.currentTimeMillis()).allocate();
        log.info("[id] workerId 经 sequence 表分配: service={}, workerId={}", appName, workerId);
        return new com.xp.starsite.common.util.IdGenerator(workerId);
    } catch (Exception e) {
        long fallback = fallbackWorkerId(appName);
        log.warn("[id] workerId sequence 分配失败，回落 hash({}): service={}, err={}", fallback, appName, e.getMessage());
        return new com.xp.starsite.common.util.IdGenerator(fallback);
    }
}

/** 兜底机器位：按服务名 hash（同服务确定、跨服务离散），把撞号面从「全平台同一值」缩到「同服务多实例」 */
private long fallbackWorkerId(String appName) {
    return Math.floorMod(appName.hashCode(), 1024);
}
```

三级降级阶梯，优先级从高到低：

1. **显式配置** `star.snowflake.worker-id`：单测和特殊部署要确定性时的逃生门；
2. **sequence 表分配**：正常路径，全局唯一；
3. **hash(服务名) % 1024 兜底**：无 DataSource 或分配 SQL 失败时启用。注意兜底注释里的措辞——它没有消除撞号，只是**把撞号面从"全平台同一值"缩到"同服务多实例"**。降级设计要像这样诚实：明说兜底牺牲了什么。

配套单测（`IdGeneratorTest`）验证两条公理。一条是不同机器位实例间不撞号（两个 generator 交错各发 1 万个，HashSet 去重后仍 2 万）。另一条是同实例严格单调递增。

### 4.6 业务侧使用约定

`IdGenerator` 的 javadoc 写明了边界："需要「insert 前拿到 id」的场景（事务消息事件载荷、跨服务引用）在业务层显式 `entity.setId(IdGenerator.nextId())`；**纯自增表不适用（DB 自增）**"。即：不是全盘替换自增主键，只在"id 要先于 insert 存在"时出手。典型如转账的两腿流水共享一个组 id（`TransactionServiceImpl#doTransfer`）：

```java
// 转账组 id 也是雪花（两腿共享，成对查询用）
long groupId = idGenerator.nextId();
```

五、与美团 Leaf 号段模式的对比：为什么本项目不用号段
------

Leaf 两条产品线（详见库内《美团-Leaf——美团点评分布式ID生成系统》）。Leaf-segment 用号段 + 双 buffer 把 DB 访问摊薄到 1/step。Leaf-snowflake 用 ZK 顺序节点分 workerId + 多节点时钟互校。star-site 的方案可以概括为"**Leaf-snowflake 的低配同构：把 ZK 换成了一张 MySQL 表**"。逐点对比：

| 维度 | Leaf-snowflake | star-site（sequence 表 + 雪花） |
| --- | --- | --- |
| workerId 分配 | ZK 持久顺序节点 + 本地文件缓存 | MySQL 单行计数 + `LAST_INSERT_ID` 原子租约 |
| 重启复用 workerId | 是（读回自己的顺序节点） | 否（取新号，见 4.4 的"只借不还"） |
| 时钟回拨 | 启动期多节点时钟互校，偏移超阈值拒绝启动；运行期 ≤5ms 等待、超则抛异常 | 钳制 + 借序列位 + 自旋（见 3.2） |
| 新增基础设施 | ZK 集群 + Leaf 服务集群 | 无（复用业务 MySQL，启动时打一次） |
| 发号 DB 依赖 | 无（运行期纯内存） | 无（运行期纯内存） |

不选号段模式的理由，本质是**量级与依赖的权衡**：

1. **号段解决的问题我们没有。** 号段模式优化的是"每次发号打一次 DB"的热路径压力。前提是发号 QPS 高到 DB 成瓶颈。star-site 的发号量是记账/日程/归档级别。雪花内存发号连这个问题的边都摸不着；
2. **号段引入的复杂度我们不想要。** 复杂度包括：step 调优（美团经验值：高峰期 QPS 的 600 倍）与双 buffer 异步预取。还包括号段切换的 TP999 尖刺监控、DB 一主两从半同步 + DBProxy。每一项都是为海量发号服务的。个人项目背这套，纯负担；
3. **雪花 + sequence 表把 DB 压到了最小接触面：启动时一次。** 运行期 DB 彻底崩掉，已启动实例照常发号。可用性等价于 Leaf-snowflake，基础设施成本为零。代价是 workerId 全局池也是单行单点。但它只在启动窗口被写，且失败有 hash 降级（4.5）；
4. **多租隔离语义不同。** Leaf 的 `biz_tag` 隔离的是"各业务的号段水位"。我们的 `service_name` 隔离的是"各服务的审计行"，分配水位只有 `__global__` 一份。因为我们分的是机器位（全局只有 1024 个，必须单点），不是业务流水号。

一句话总结：**Leaf 是发号基础设施（要养），我们是业务进程内嵌组件（要省）。** 量级涨到需要独立发号服务时，迁移路径也清晰：`IdGenerator` 的位布局与 Leaf-snowflake 完全一致，换分配源即可。

六、踩坑记录
------

### 6.1 Hikari 连接风暴时，启动期租约在等什么

租约发生在 Spring 上下文刷新、`idGenerator` Bean 创建时——即**启动关键路径**上。docker compose 整体拉起或滚动重启风暴时，七八个服务同时走到 `dataSource.getConnection()`，随后全部在 `__global__` 单行上排队等行锁。两层等待要分清：

* **行锁排队**：每个租约是"一次 UPDATE（亚毫秒）"，即便 10 个服务串行通过，总耗时也在毫秒级。这不是风险，是设计预期（串行才保证原子）；
* **连接池排队才是真风险**：启动期连接被 schema 校验、Flyway、预热这类启动任务占用。`idGenerator` 可能在 Hikari `connectionTimeout`（默认 30 s）窗口里拿不到连接。此时整条 `allocate()` 抛 SQLException——**接住它的是 4.5 的降级阶梯**：回落 `hash(服务名) % 1024` 并 `log.warn`，服务照常起。

这条坑的通用教训：**启动路径上的可选依赖必须带超时 + 降级，且降级日志要可报警**。宁可带着缩水的 workerId 唯一性上线，也不能让发号组件卡死启动。另一个隐性约束再强调一次：租约的 UPDATE 与 `SELECT LAST_INSERT_ID()` 必须同连接（4.2）。改造这段代码时注意：**任何把两条语句拆到两次 `jdbcTemplate` 调用的重构都会引入串号 bug**。

### 6.2 id 生成放在事务临界区（的 SQL 部分）外

`doTransfer` 里 `nextId()` 虽写在 `@Transactional` 方法内，但执行在**第一条业务 SQL 之前**、且是纯内存操作。要守的规矩是：**不要让 id 获取成为事务内的一次 DB 往返**。三个理由：

1. **不拉长锁持有时间。** 事务内先 `SELECT LAST_INSERT_ID()` 再 insert 的写法，把发号往返计入行锁持有窗口。高并发下放大锁竞争。雪花的 `synchronized nextId()` 是纳秒级，无感；
2. **id 先于 insert 存在，事务消息/事件载荷才写得进去。** 事务性消息（outbox 模式）要求事件载荷在业务事务内就带上实体 id。数据库自增主键做不到"insert 前知道 id"，只能 insert 后回查。这多一次往返，还把事件发布时机搅乱。这正是 javadoc 里"事务消息事件载荷"场景的由来；
3. **回滚无代价。** 业务事务回滚时，已发的雪花 id 只是内存里的一个空洞（gap）。这个 id 不涉及任何外部状态。号段模式下若在取段后回滚，烧掉的是真金白银的 DB 水位推移。

配套纪律：`entity.setId(IdGenerator.nextId())` 只用于"id 需先于 insert 存在"的场景。纯自增主键的表继续用 DB 自增。**两套发号机制按场景分工，不搞一刀切**。

### 6.3 改进清单（回写时的再思考）

* 服务审计行回写实际分配到的 `worker_id`（4.4 已述，一行 SQL 的事）；
* 回绕分支目前是"重置 + 返回 0"，若未来弹性实例多，可加"存活实例上报 + 死号回收"。但那是把终身租约改成心跳租约，复杂度上一个台阶，量级不到不做；
* `IdGenerator` 的时钟回拨自旋在"大幅回拨 + 高并发"下会停发。量级临近时可参考 Leaf 加"回拨超阈值直接抛异常"的保险丝。

参考
------

* 库内：《雪花算法的原理和Java实现》（A-后端内核/框架与中间件/数据库/）
* 库内：《美团-Leaf——美团点评分布式ID生成系统》（A-后端内核/框架与中间件/数据库/），原文 https://tech.meituan.com/2017/04/21/mt-leaf.html
* Flickr Ticket Servers（步长自增多机方案）: https://code.flickr.net/2010/02/08/ticket-servers-distributed-unique-primary-keys-on-the-cheap/
* 源码：star-site-backend `star-site-common` 的 `common/util/IdGenerator.java`、`common/id/SequenceWorkerIdAllocator.java`、`common/log/StarSiteLogConfig.java`、`docker/mysql-init/22-sys-sequence.sql`
