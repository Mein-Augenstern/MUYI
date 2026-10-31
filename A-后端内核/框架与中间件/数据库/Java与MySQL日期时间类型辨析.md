Java 与 MySQL 日期时间类型辨析
====

「为什么存进去的时间查出来差 8 小时」是后端面试与线上事故的双料常客。这道题表面考 API，实际考一条完整链路的时区语义：**Unix 时间戳 → java.util.Date / java.time → JDBC 驱动 → MySQL datetime 与 timestamp**。本篇自底向上把每一层的存储本质、转换规则和翻车条件讲清。MySQL 执行链路背景见 [MySQL基础-架构与SQL执行流程](MySQL基础-架构与SQL执行流程.md)。

1 一切的基准：Unix 时间戳
------

时间戳是理解所有日期时间类型的锚点：一个整数，表示自 Unix 纪元 **1970-01-01 00:00:00 UTC** 起经过的秒数（或毫秒数）。

* 它是**绝对值，与时区无关**：同一时刻，全球任何服务器执行 `new Date().getTime()` 拿到同一个数；
* 本文讨论的 unix timestamp 不含闰秒，单调线性，Java 与 MySQL 语义一致；
* 同一时间戳 0，各地看到的本地时间不同：UTC+0 是 1970-01-01 00:00:00，UTC+8（北京）是 1970-01-01 08:00:00，UTC-5（纽约）是 1969-12-31 19:00:00。

一句话：**时间戳只有一个值，本地时间有无数种表示**。后面所有"差 8 小时"的事故，本质都是某两层用了不同的换算规则。

2 java.util.Date：一个 long 的包装
------

从 Java 1.0 活到今天的元老，内部极其简单：

```java
public class Date {
    private transient long fastTime; // 毫秒时间戳
}
```

* `new Date()` 记录当前时刻的毫秒时间戳，`date.getTime()` 原样取出；
* **Date 本身不含任何时区信息**，它就是时间戳的包装器。`date.toString()` 显示本地时间，只是 toString 内部借 JVM 默认时区做了格式化，属于"展示层"行为；
* 设计混乱、大量方法已 @Deprecated，新代码不该用；但老代码和诸多框架仍依赖它。

3 java.time 五件套
------

Java 8 的 java.time 重造了整套体系，按"带不带时区"分两族：

| 类型 | 内部 | 时区 | 典型场景 |
| ---- | ---- | ---- | ---- |
| Instant | epochSecond + nanos | 本质 UTC | 记录事件发生的时刻 |
| LocalDate | 年月日 | 无 | 生日、节假日、有效期 |
| LocalTime | 时分秒 | 无 | 营业时间、每日固定时刻 |
| LocalDateTime | 年月日+时分秒 | 无 | 日历时间，不关心时区 |
| ZonedDateTime | LocalDateTime + ZoneId | 有 | 跨时区业务的完整表达 |

```java
Instant now = Instant.now();                       // 2026-02-25T00:00:00Z，Z 即 UTC
LocalDate d = LocalDate.now();                     // 2026-02-25
LocalTime t = LocalTime.of(10, 30, 0);             // 10:30:00
LocalDateTime ldt = LocalDateTime.of(2026, 2, 25, 10, 30, 0); // 2026-02-25T10:30:00

ZoneId shanghai = ZoneId.of("Asia/Shanghai");
ZonedDateTime zdt = ldt.atZone(shanghai);          // 2026-02-25T10:30:00+08:00[Asia/Shanghai]
```

关键认知三条：

* **Instant 和 Date 是同一种东西**：都是 UTC 时间戳，都无时区，可互转（`date.toInstant()` / `Date.from(instant)`）；区别只是 Instant 纳秒精度、不可变、线程安全；
* **LocalDateTime 是模糊的**："2026-02-25 10:30" 没说是哪个时区的 10:30，北京的 10:30 与纽约的 10:30 是两个不同时刻。它像一张没有时区的时间标签；
* **ZonedDateTime 才能唯一定位时刻**，与 Instant 互转：`zdt.toInstant()`、`instant.atZone(zoneId)`。

选型口诀：存"时刻"用 Instant；"纯日期/纯时刻"用 Local*；跨时区展示用 ZonedDateTime；LocalDateTime 用于全链路约定同一时区的日历时间。

4 MySQL：datetime vs timestamp
------

| 维度 | datetime | timestamp |
| ---- | ---- | ---- |
| 存储 | 字面量 YYYY-MM-DD HH:MM:SS，不做时区转换 | 内部存 UTC（4 字节秒级整数，高精度加字节） |
| 范围 | 1000-01-01 ~ 9999-12-31 | 1970 ~ 2038-01-19 03:14:07 UTC（2038 问题） |
| 时区影响 | 存什么读什么，完全不受影响 | 写入按 session 时区转 UTC，读出再转回 |
| 自动时间 | 支持 DEFAULT / ON UPDATE CURRENT_TIMESTAMP | 同样支持 |

毫秒精度用 `(3)` 声明，不写则精度为秒：

```sql
CREATE TABLE t (
    id          INT PRIMARY KEY,
    create_time datetime(3) DEFAULT CURRENT_TIMESTAMP(3),
    update_time datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
);
```

时区实验：MySQL 服务器时区为 Asia/Shanghai 时，dt、ts 两列都写入 '2026-02-25 10:30:00'；把服务器时区改成 UTC+0 再查——dt 仍显示 10:30:00（字面量原样），ts 显示 02:30:00（内部是 UTC，减了 8 小时）。**datetime 存的是"墙上的钟"，timestamp 存的是"绝对时刻"**。

5 JDBC 映射：serverTimezone 决定换算
------

Java 类型与 MySQL 字段之间由 MySQL Connector/J 负责转换，核心变量是**驱动的 serverTimezone**：

* 显式指定（推荐）：`jdbc:mysql://localhost:3306/db?serverTimezone=Asia/Shanghai`；
* 自动检测：驱动查询 `SELECT @@time_zone, @@system_time_zone`，但时区缩写有歧义——CST 既可以是 China Standard Time（UTC+8）也可以是 Central Standard Time（UTC-6），Connector/J 8.x 遇到歧义直接报错；
* 8.x 更省心的写法：`connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`，驱动顺带把 session 时区也统一成 UTC，一步消除两层不一致。

6 无时区类型透传，带时区类型两阶段转换
------

**LocalDate / LocalTime / LocalDateTime（无时区族）**：驱动把字面量字符串直接透传给 MySQL，不做任何时区换算——LocalDate 对 date、LocalTime 对 time、LocalDateTime 对 datetime，所见即所得。

**java.util.Date / Instant / ZonedDateTime（带时区族，前两者本质是 UTC 时间戳）**：写入分两阶段。

阶段一，驱动统一转字符串——不管目标列是什么类型，先取 UTC 时间戳，按 serverTimezone 转成本地时间字符串发给 MySQL。以 serverTimezone=Asia/Shanghai 写入时间戳 0 为例：

```java
ps.setTimestamp(1, new Timestamp(0));                       // Date：直接转
ps.setObject(1, Instant.EPOCH);                             // Instant：同为时间戳
ps.setObject(1, ZonedDateTime.of(1970,1,1,0,0,0,0,ZoneId.of("UTC"))); // 先算出 UTC 时间戳
// 三者同一时刻，驱动最终都发出字符串 "1970-01-01 08:00:00"（上海时间）
```

阶段二，MySQL 收到字符串后按列类型落地——datetime 原样存储 "1970-01-01 08:00:00"；timestamp 把它理解为 session 时区的时间，转成 UTC 存储，session 为 Asia/Shanghai 时内部即时间戳 0。

读取是逆过程：datetime 原样返回字符串、驱动按 serverTimezone 解析；timestamp 先按 session 时区转成字符串再由驱动解析——配置一致时两条路殊途同归，都能还原正确时刻。

7 什么时候会错、工程上怎么防
------

* **datetime 怕 serverTimezone 变**：写入时用 Asia/Shanghai，读出时改 UTC，驱动把 "08:00:00" 当 UTC 解析，时间偏 8 小时。datetime 要求写读两端的 serverTimezone 必须一致；
* **timestamp 怕 serverTimezone 与 session 时区不一致**：驱动和 MySQL 各用各的时区换算，错误可能暂时抵消，任一配置一变数据就错乱。反过来这是它的优势：只要两者一致且一起变（今天 UTC、明天 Asia/Shanghai），内部存的是 UTC 时刻，数据依然正确。

工程建议（把模糊性压到零）：

* 服务器、JVM（`-Duser.timezone=UTC`）、MySQL 统一 UTC；
* JDBC URL 显式 `serverTimezone=UTC`；MySQL 8.x 用 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`；
* 业务展示层的时区转换交给前端或网关，存储层只认 UTC。

面试追问链
------

* Date 和 Instant 的区别？→ 本质同构（UTC 时间戳），差在精度（毫秒 vs 纳秒）、不可变性与 API 设计；
* LocalDateTime 带时区吗？→ 不带，是模糊的日历时间；要定位时刻需补 ZoneId 转成 ZonedDateTime 或 Instant；
* datetime 和 timestamp 选哪个？→ 要"所见即所得"的墙上时间选 datetime；要跨时区语义一致的绝对时刻选 timestamp，但受 2038 上限约束；
* 时间差 8 小时的根因？→ 链路中两层换算规则不一致：常见为 serverTimezone 与实际不符、或写读两端 serverTimezone 不同；
* CST 是什么时区？→ 中国标准时间（UTC+8）与美国中部时间（UTC-6）撞缩写，Connector/J 8.x 检测到直接报错，所以必须显式指定；
* timestamp 2038 问题？→ 4 字节秒级存储上限 2038-01-19，存量系统要提前规划迁移到 datetime(3)；
* 为什么推荐全链路 UTC？→ 把"绝对时刻"的表示统一，本地时间只出现在展示层，转换唯一且可回退。

面试官视角小结
------

时间题是最容易制造区分度的题之一：背 API 的候选人止步于「datetime 不带时区、timestamp 带时区」；真正理解的人会从「时间戳是唯一的绝对值」出发，把驱动转字符串、MySQL 转_utc 两阶段讲成一条流水线，并且能指出翻车的精确条件——datetime 怕 serverTimezone 变、timestamp 怕 session 与 serverTimezone 不一致。再能给出「全链路 UTC + connectionTimeZone 统一」的工程方案，就是高级信号。答不出 Instant 与 Date 的同构关系、或认为 LocalDateTime 里存着时区的，基本可以判断没有处理过跨时区数据。与 [数据库表设计要点](数据库表设计要点.md) 的时间列设计互相参照。

> 来源：javadoop《理清 Java 与 MySQL 中的日期时间类型》（https://javadoop.com/post/java-date-time）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
