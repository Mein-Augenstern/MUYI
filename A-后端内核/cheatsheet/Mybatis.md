# MyBatis 速查卡

定位
====

1. 半 ORM 框架：内部封装 JDBC，只需关注 SQL 本身，不处理驱动/连接/Statement。
2. XML 或注解配置映射，POJO ↔ 数据库记录，省掉几乎所有 JDBC 样板代码。
3. 原生 SQL 可控、灵活度高；适用高性能要求、需求变化多的互联网项目。

MyBatis vs Hibernate
----

| 维度 | MyBatis | Hibernate |
|------|---------|-----------|
| ORM 程度 | 半自动（手写 SQL） | 全自动（按对象关系模型生成） |
| 灵活性 | SQL 可控、性能可调 | 关系映射能力强 |
| 数据库无关性 | 差（SQL 依赖方言） | 好 |
| 适用场景 | 需求多变、性能敏感 | 关系模型稳定的企业软件 |

优缺点速记
----

1. 优点：SQL 与代码解耦（XML 统一管理）、动态 SQL 标签、比 JDBC 省约 50% 代码、JDBC 支持的库都支持、与 Spring 集成好。
2. 缺点：SQL 工作量大、依赖数据库方言导致移植性差。

高频考点
====

#{} 与 ${}
----

| 项 | #{} | ${} |
|----|-----|-----|
| 本质 | 预编译，替换为 `?` + PreparedStatement set | 字符串直接替换 |
| 安全 | 防 SQL 注入 | 有注入风险 |
| 场景 | 默认首选 | 表名/列名/排序字段等无法预编译处 |

字段名与属性名不一致
----

1. SQL 别名：`select order_id id ...` 使别名 = 属性名。
2. `<resultMap>` 逐一映射：`<id>` 映射主键，`<result>` 映射普通列。

模糊查询 like
----

1. Java 侧拼通配符后用 `#{value}`（推荐）。
2. SQL 侧 `like "%"${value}"%"`（会注入，不推荐）。

Mapper 接口原理
----

1. 接口全限名 = namespace，方法名 = statement id，参数 = SQL 参数。
2. 无实现类：`全限名+方法名` 唯一定位 MappedStatement，JDK 动态代理生成代理对象拦截方法执行 SQL。
3. **方法不可重载**（key 是全限名+方法名）。

分页
----

1. `RowBounds`：ResultSet 内存分页，非物理分页。
2. 物理分页：SQL 手写 limit / 分页插件（PageHelper）。
3. 插件原理：实现 Interceptor 拦截待执行 SQL，按 dialect 重写追加物理分页语句。

结果映射与关联
----

1. 两种映射形式：`<resultMap>` 或 SQL 列别名；底层反射创建对象并逐属性赋值。
2. 一对一：`<association>`；一对多：`<collection>`。
3. 联合查询（一次 SQL）与嵌套查询（先查主表再按外键查关联，select 属性配置）两种实现。

缓存
----

| 项 | 一级缓存 | 二级缓存 |
|----|---------|---------|
| 作用域 | SqlSession | Mapper（Namespace） |
| 结构 | PerpetualCache + HashMap | 同左，可换 Ehcache 等 |
| 默认 | 开启 | 关闭（`<cache/>` 开启，需 Serializable） |
| 失效 | flush/close 或 C/U/D 后清空 | 按配置判断刷新 |

其他高频
----

1. 批量插入：`openSession(ExecutorType.BATCH)` 循环 insert 后 commit。
2. 主键回填：`useGeneratedKeys="true" keyProperty="id"`，插入后从参数对象取。
3. 多参数传递：`#{0}/#{1}`（已过时）、`@Param` 注解（推荐）、封装 Map。
4. 动态 SQL 9 标签：`if / where / set / trim / foreach / choose / when / otherwise / bind`，原理 = 按表达式值逻辑判断动态拼 SQL。
5. XML 其余标签：`<resultMap> <parameterMap> <sql> <include> <selectKey>`。
6. 不同 XML 的 id 可否重复：配置了 namespace 即可重复（namespace+id 是 key）；新版 namespace 必填。
7. 延迟加载：仅 association/collection 支持，配置 `lazyLoadingEnabled`；原理 = CGLIB 代理，调用 `a.getB()` 发现为 null 时才发 SQL 查 B 并 setB。
8. 接口绑定两种方式：注解（`@Select` 等，简单 SQL）/ XML（namespace = 接口全路径，复杂 SQL 常用）。
9. Mapper 接口要求：方法名 = statement id；入参类型 = parameterType；返回类型 = resultType；namespace = 接口全路径。
10. 插件只能拦截 4 种对象：ParameterHandler、ResultSetHandler、StatementHandler、Executor；实现 Interceptor 接口 + 注解指定拦截点 + 配置注册。

> 内容来源：[原文](../框架与中间件/MyBatis/Mybatis常见基本面试题.md) · 2026-10 整理回填。
