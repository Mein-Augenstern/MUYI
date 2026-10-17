MyBatis 执行流程与缓存体系
====

看在前面
------

同目录的 [Mybatis常见基本面试题.md](Mybatis常见基本面试题.md) 覆盖了"是什么"层面的 27 问，速查版见 [cheatsheet/Mybatis.md](../../cheatsheet/Mybatis.md)。这篇补"为什么/怎么运转"：**一条 SQL 从 Mapper 代理到 ResultSet 映射的完整执行链（四大对象）、一二级缓存的作用域与失效条件、生产为什么普遍禁用二级缓存、插件 Interceptor 能拦什么**。面试里能把四对象顺序与职责说顺、并讲出二级缓存的分布式脏读场景，这题就从背诵区进了实战区。

一、执行链全景：四大对象
------

Mapper 接口没有实现类，`getMapper` 返回 JDK 动态代理；方法调用被 `MapperProxy` 拦截，转成 `sqlSession.selectList(namespace + "." + methodId)`，之后进入四大对象流水线：

```text
MapperProxy（JDK 动态代理，方法 → MappedStatement 定位）
    │
    ▼
SqlSession（门面：selectOne/selectList/insert...，DefaultSqlSession）
    │  按 statement id 找 MappedStatement（解析 XML 时建好的 Map）
    ▼
Executor（执行器：一级缓存、事务提交/回滚在这里）
    │  SIMPLE 复用 Statement / REUSE / BATCH 批处理 / CACHING 二级缓存代理
    │  1) 生成 BoundSql（动态 SQL 拼接 + #{} → ? 占位）
    ▼
StatementHandler（Statement 处理器：创建并执行 java.sql.Statement）
    │  ├─ ParameterHandler：把入参按 JDBC 类型 setObject 进 ? 占位符
    │  ├─ 执行 stmt.executeQuery() / executeUpdate()
    │  └─ ResultSetHandler：ResultSet → 目标对象（resultMap 映射/延迟加载代理）
    ▼
Database
```

四对象的职责一句话版（面试按此顺序说）：

| 对象 | 职责 | 创建时机 |
| --- | --- | --- |
| Executor | 调度 + 一级缓存 + 事务 | 开 SqlSession 时（configuration.newExecutor） |
| StatementHandler | 创建 Statement、设置 fetchSize/超时、执行 | 每次 SQL（SimpleStatementHandler/PreparedStatementHandler/CallableStatementHandler 三选一，按 statementType） |
| ParameterHandler | 参数绑定：`#{}` 的值 → `ps.setXxx(i, value)` | StatementHandler 内部创建 |
| ResultSetHandler | 结果映射：行 → 对象（含嵌套 resultMap、延迟加载） | StatementHandler 内部创建 |

两个高频细节：**`#{}` 替换发生在 Executor 生成 BoundSql 阶段**——SQL 变成带 `?` 的预编译语句，值由 ParameterHandler 填充，这是防 SQL 注入的机制层解释；**插件代理的是四大对象**——每个对象创建后都要过 `interceptorChain.pluginAll(target)`，层层 JDK 动态代理包回去，所以分页插件能拦 StatementHandler 改写 SQL。

二、一级缓存：Session 级，默认开
------

* **结构**：`PerpetualCache`（HashMap），挂在 `BaseExecutor` 上；key = statement id + 分页偏移 + SQL + 参数值 + 环境id 的 hash。
* **作用域**：SqlSession（会话）级。同一 SqlSession 内相同查询直接命中，不走数据库。
* **失效条件**（面试必背四条）：
  1. 同一 SqlSession 执行 **insert/update/delete**（不限 namespace，本会话全清——注意是 `flushCacheRequired`/本地缓存整体 clear）；
  2. 调用 `sqlSession.commit()/close()`；
  3. 手动 `clearCache()`；
  4. **Spring 集成下基本失效**：无事务时每次 Mapper 调用都新开 SqlSession，缓存活不过一次调用；有 `@Transactional` 时同一事务共享 SqlSession，一级缓存才真正生效——这解释了"为什么一级缓存默认开却很少被感知"。

三、二级缓存：namespace 级，默认关
------

```text
开启条件（三个都满足）：
  1. 全局开关 localCacheScope 之上 setting: cacheEnabled=true（默认 true，只算"允许"）
  2. mapper.xml 声明 <cache/>（eviction=FIFO/LRU, flushInterval, size, readOnly）
  3. 实体实现 Serializable（默认按序列化存副本）
命中顺序：CachingExecutor（TransactionalCache 装饰）
  → 二级缓存（namespace 级，跨 SqlSession）
  → 一级缓存（session 级）
  → 数据库
写入时机：SqlSession commit/close 时才把 TransactionalCacheManager 暂存的
  结果真正放进二级缓存——回滚的查询结果不进缓存
```

失效条件：该 **namespace** 内任意 insert/update/delete 执行并提交后，**整个 namespace 的二级缓存全清**（粒度是 namespace，不是单条 SQL）。

四、生产为什么多禁用二级缓存
------

这是本题的实战分水岭，三个理由按杀伤力排序：

1. **多表关联查询的脏缓存（致命）。** 缓存 key 只知道自己这条 SQL，失效只清自己 namespace。`OrderMapper` 里一条 SQL join 了 `user` 表，`UserMapper` 执行 update 只清 `UserMapper` 的缓存——`OrderMapper` 缓存里还是旧 user 数据，**读到已提交更新之前的数据**。跨 namespace 通知（`<cache-ref>`）能部分缓解，但 cache-ref 是"共用同一块缓存"，多对多引用关系会织成蜘蛛网，没人敢改。
2. **分布式多节点不一致。** 二级缓存在**每个应用节点本地内存**里，节点 A 提交更新只清了 A 的缓存，节点 B~N 的缓存全是脏的，要等 `flushInterval` 或重启。除非配 Ehcache/Rredis 集群版实现——但那不如直接把缓存职责上移到 Redis 层，SQL 结果与业务对象一起设计 TTL/失效，可控得多。
3. **收益本就有限。** 命中粒度是"完全相同的 SQL + 参数"，互联网场景参数组合爆炸，命中率天然低；而命中率高的热点数据本来就该走专门缓存层。

结论句式（面试可直接用）：**"MyBatis 的缓存是 ORM 层的顺手优化，不是缓存系统。生产把缓存语义交给 Redis/本地缓存库做，MyBatis 保持 `cacheEnabled=false` + 只依赖一级缓存的会话语义，可预期性最好。"**

五、插件 Interceptor：四个拦截点
------

```java
@Intercepts({
    @Signature(type = Executor.class,        method = "query",        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = StatementHandler.class, method = "prepare",      args = {Connection.class, Integer.class}),
    @Signature(type = ParameterHandler.class, method = "setParameters", args = {PreparedStatement.class}),
    @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {Statement.class})
})
public class SqlStatPlugin implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 拦 StatementHandler.prepare： BoundSql 拿到最终 SQL，改写后放回（分页插件的做法）
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            System.out.println("sql cost=" + (System.currentTimeMillis() - start));
        }
    }
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this); // 只代理注解声明的接口
    }
}
```

* **能且只能拦四个对象**：Executor（缓存/事务/改 SQL 的上游）、StatementHandler（分页改写最常用）、ParameterHandler（参数脱敏审计）、ResultSetHandler（结果改写/加密字段解密）。
* **原理**：`Plugin.wrap` 用 JDK 动态代理逐层包 target，调用命中 `@Signature` 方法时进 `intercept`，不命中直接放行——所以一个对象可能被多个插件包成"洋葱"，**多个插件拦截同一方法时后注册的先执行（代理在外层）**。
* **典型应用**：PageHelper（拦 Query 的 Executor 层改写 `LIMIT`，本质是把内存分页 RowBounds 换成物理分页）、慢 SQL 打点、字段加解密。

追问链
------

> **面试官：为什么插件只能拦这四个对象？**

四大对象全部由 Configuration 工厂方法创建，创建后统一过 `interceptorChain.pluginAll(target)`；而 SqlSession/MapperProxy 是入口组件，MappedStatement 是静态解析产物——插件要做的是"在执行链上插入逻辑"，插入点自然只能是链上的对象。想拦更细的点就得学 MyBatis-Plus 直接扩展体系而不是写插件。

> **面试官：一级缓存会不会读到脏数据？**

会——同会话内别的进程/别的会话改了库，本会话的一级缓存不知道。但一级缓存的作用域是"一个会话/一个事务"，事务内可重复读本来就是常见语义，脏读窗口被会话生命周期封顶；真正的脏读放大器是二级缓存（跨会话、跨节点），所以结论又回到"关二级缓存"。

> **面试官：#{} 防注入的机制层解释？**

`#{}` 在 BoundSql 生成阶段变成 `?`，最终走 PreparedStatement **预编译**：SQL 结构先发给数据库完成语法/权限解析，参数只作为纯数据传输，**永远不可能改变已解析的语法树**。`${}` 是字符串替换发生在解析成 SQL 文本之前，值直接拼进 SQL 文本——`' or '1'='1` 就成了语法的一部分。表名/列名/排序字段不能用 `#{}`（预编译占位符只能出现在值位置），必须白名单校验后再 `${}`。

> **面试官：分页插件的原理？和 RowBounds 什么区别？**

RowBounds 是**内存分页**：JDBC 把结果集全取回，MyBatis 按 offset/limit 跳过与截断——大表等于全量拉取。分页插件（PageHelper）在 Executor/StatementHandler 层拦截，读取分页参数后**改写 SQL 加 LIMIT/OFFSET（物理分页）**，并发一条 count SQL 供分页条；数据库方言不同通过 dialect 适配。

> **面试官：二级缓存我非要开，什么场景勉强可以？**

满足三个条件：单表或"缓存归属单一 namespace"的封闭查询、应用单实例或缓存实现集中式（JRedis/Ehcache 集群）、数据更新频率低且容忍 `flushInterval` 内的延迟。典型的如字典表/配置表读多写少——但说实话，这类场景直接 Redis 更省心，所以这个问题的正确答案是"说得出风险，选得出替代"。

> **面试官：Executor 有哪几种，怎么选？**

三种 + 一种代理：SIMPLE（默认，每次新建 Statement）、REUSE（复用 Statement，同 SQL 场景省创建）、BATCH（批量攒批，`ExecutorType.BATCH` 开 SqlSession，insert 万条配 `flushStatements` 分批提交，比循环单条快一个数量级）、CACHING（`cacheEnabled=true` 时包在真正 Executor 外面的二级缓存代理——装饰器模式，执行链上是"代理调真身"）。选型一句话：默认 SIMPLE，批量写入 BATCH，其余交给配置不换类型。

> **面试官：Spring 整合下 SqlSession 的生命周期是怎样的？**

`SqlSessionTemplate` 是单例线程安全的，但它内部通过 `SqlSessionUtils` 按事务绑定真实 SqlSession（TransactionSynchronizationManager 的资源 Map）：无事务时每次操作"开-用-关"一个 SqlSession（一级缓存形同虚设）；有 `@Transactional` 时同一事务全程复用一个（一级缓存生效、与 Spring 事务提交回滚联动）。所以"SqlSession 是不是线程安全"的标准答案：**模板线程安全，原生 DefaultSqlSession 非线程安全、生命周期应为方法级**。

面试官视角小结
------

1. **执行链一句话**：Mapper 代理 → SqlSession 门面 → Executor（缓存/事务）→ StatementHandler（建/执行 Statement）→ ParameterHandler 填参 → ResultSetHandler 映射——顺序与职责说顺，配上 pluginAll 的代理插入点，就是源码级答案；
2. **一级缓存**：PerpetualCache 挂在 Executor、Session 级、增删改/commit/close/手动清即失效；Spring 无事务时几乎无感，有事务时同一 SqlSession 才生效；
3. **二级缓存**：namespace 级、commit 时才写入（TransactionalCache）、默认关；致命伤是跨 namespace join 的脏缓存与多节点不一致——生产普遍禁用，缓存语义上移 Redis；
4. **插件**：只拦四大对象、JDK 动态代理洋葱包裹、后注册先执行；分页插件 = 拦截 + SQL 改写 + count，RowBounds 是内存分页反面教材；
5. **#{} vs ${}**：预编译语法树不可被数据改变 vs 文本拼接，动态表名/排序字段走白名单 + `${}`。

> 2026-10 新增（Round1 补缺）。
