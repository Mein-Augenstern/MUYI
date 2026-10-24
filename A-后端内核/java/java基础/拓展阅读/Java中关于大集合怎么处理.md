Java中关于大集合怎么处理
====

> outside-reading 深度篇。场景：一次查询/处理几十万到上亿条数据时，"直接 `select * 全表` 塞进 `List` 再内存处理"的写法为什么必挂。以及四类正统解法（分批、流式、内存映射、并行）各自的原理与取舍。

一、问题到底出在哪
------

```java
// 反面教材：千万级表的"全量导出"
List<Order> orders = orderMapper.selectAll();   // 一次性拉全表
orders.stream().filter(...).forEach(...);        // 内存里过滤
```

这段代码会依次撞上四堵墙：

* **JDBC 层**：MySQL 默认会把**结果集全部加载进 JVM 堆内存**再返回，千万行 × 每行 1 KB ≈ 10 GB，直接 OOM（`OutOfMemoryError: Java heap space`）。
* **GC 层**：就算勉强装下，一个巨型 List 让 Old 区瞬间填满，触发连绵 Full GC。表现为接口卡死、整个进程的所有请求一起抖。
* **DB 层**：大结果集长时间占用连接 + 服务端资源，拖垮连接池，殃及所有同库业务。
* **响应层**：处理 10 分钟才返回，上游早就超时重试，形成雪崩。

核心结论：**大集合处理的本质不是"Java 怎么装得下"，而是"让数据不全部驻留内存"**。要么分批拿、要么边读边处理、要么干脆不经过 JVM（下推到 DB 或走堆外/映射）。

二、方案一：分批处理（最常用）
------

2.1 内存中分批
------

数据已经拿回来了、只是单次处理太重（比如逐条 RPC），按批切开：

```java
// Guava
List<List<Order>> partitions = Lists.partition(orders, 500);

// JDK 原生写法（subList 视图切分）
for (int i = 0; i < orders.size(); i += BATCH) {
    List<Order> batch = orders.subList(i, Math.min(i + BATCH, orders.size()));
    process(batch);
}
```

`subList` 的三个坑：

1）返回的是**原 List 的视图**，不是拷贝——原 List 结构性修改（add/remove）后再访问 subList，抛 `ConcurrentModificationException`。
2）**持有 subList 引用会阻止原 List 被 GC**（视图持有原引用）。大集合场景要小心内存拖尾。
3）subList 序列化的经典 bug：序列化的是视图。反序列化出来只有那一页数据，且可能返回错误。如果需要独立副本，用 `new ArrayList<>(subList)`。

2.2 查询侧分批：深分页用游标，别用 offset
------

```sql
-- 深分页写法：offset 越大越慢（扫过并丢弃前面所有行）
SELECT * FROM orders ORDER BY id LIMIT 1000000, 1000;

-- 游标写法：每次从上一批最大 id 继续，每批都是索引定位，恒定快
SELECT * FROM orders WHERE id > #{lastMaxId} ORDER BY id LIMIT 1000;
```

游标分页的前提：排序键有序且唯一（id，或 created_time + id 复合）。这是千万级表批处理（迁移、对账、跑批）的标准姿势。

2.3 写入侧分批：JDBC batch
------

```java
// 批量插入 1 万条：逐条 execute 是 1 万次网络往返
PreparedStatement ps = conn.prepareStatement("INSERT INTO t VALUES(?,?)");
for (Order o : batch) {
    ps.setLong(1, o.getId());
    ps.setString(2, o.getName());
    ps.addBatch();
}
ps.executeBatch();          // 一次网络往返
```

MySQL 下想真正合并成 multi-values insert，连接串要加 `rewriteBatchedStatements=true`，否则驱动仍逐条发送，batch 白开。

2.4 容量预估：不给 ArrayList 留扩容机会
------

```java
// 已知结果集 10 万条时，预估容量，避免多次扩容的数组拷贝
List<Order> result = new ArrayList<>(100_000);
```

ArrayList 默认容量 10，扩容按 1.5 倍增长。10 万元素要扩容 ~18 次，每次 `Arrays.copyOf` 全量拷贝。大集合场景值得在构造时把容量算好。

三、方案二：流式查询（不把结果集搬进内存）
------

3.1 JDBC 流式：fetchSize 的两种姿势
------

```java
// 姿势一：MySQL 流式（fetchSize 设为 Integer.MIN_VALUE 并关自动提交）
Statement st = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
st.setFetchSize(Integer.MIN_VALUE);
conn.setAutoCommit(false);
ResultSet rs = st.executeQuery("SELECT * FROM big_table");
while (rs.next()) {          // 每次循环时驱动才从网络读一行
    process(rs);
}
conn.commit();               // 流式期间必须保持事务/连接不释放
```

```java
// 姿势二：游标查询（useCursorFetch=true + 正常数组 fetchSize）
// jdbc:mysql://...?useCursorFetch=true
st.setFetchSize(1000);       // 服务端游标，每次网络传 1000 行
```

区别：方式一服务端一次性生成结果流，客户端读多快传多快，**连接被独占到读完为止**。方式二服务端按 cursor 分批算，服务器压力更平滑。两者都要求：处理期间不能关闭连接、不能在同一个连接上执行其它语句。

3.2 MyBatis 层：Cursor
------

```java
try (Cursor<Order> cursor = sqlSession.selectCursor("selectAll")) {
    for (Order o : cursor) {   // 惰性逐行拉取
        process(o);
    }
}
```

必须配 JDBC 侧的 fetchSize 设置才生效（否则驱动还是全量加载），且 Cursor 必须 try-with-resources 关闭，否则连接泄漏。

3.3 一个容易忽略的点
------

```java
cursor.stream().collect(Collectors.toList());   // 又全量装回内存了——白流式
```

流式的价值在于**边读边处理、处理后即可丢弃**（写文件、发消息、聚合计数）。如果终点是 collect 成一个大 List，说明问题应该用别的方案解。

四、方案三：内存映射与堆外（大文件绕开堆）
------

处理大**文件**（而非 DB 结果集）时，不必让数据经过 Java 堆：

```java
// mmap：文件映射到虚拟内存，由操作系统按页缺页加载，堆内零拷贝
try (FileChannel fc = FileChannel.open(path, StandardOpenOption.READ)) {
    MappedByteBuffer buf = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
    while (buf.hasRemaining()) {
        byte b = buf.get();     // 触发缺页中断时才真正读磁盘（PageCache）
        // ...
    }
}
```

要点：

* `MappedByteBuffer` 走的是**操作系统 PageCache + 虚拟地址映射**，读文件不占用堆。也不发生"内核缓冲区 → JVM 堆"的第二次拷贝（对比 `InputStream.read(byte[])`）。
* 代价：映射区域受虚拟地址空间限制。`unmap` 不可控（JDK9+ 建议用 try-with-resources 让 FileChannel 关闭时释放）。对映射 buffer 的修改是**异步刷盘**，崩溃一致性要自己保证。
* 堆外内存（`DirectByteBuffer`）是同思路的另一形态：网络 IO 场景避免堆内堆外来回拷贝，NIO/Netty 的基础。注意堆外不受 GC 管理，泄漏比堆内更难发现（靠 `-XX:MaxDirectMemorySize` 兜底）。
* Kafka 之类的高吞吐中间件，本质就是"磁盘顺序读 + PageCache + 零拷贝（sendfile）"的组合。大集合/大数据量问题的终极答案往往在"别用 JVM 堆装"。

五、方案四：并行流与并行的取舍
------

5.1 parallelStream 的工作机制
------

```java
orders.parallelStream()
      .filter(o -> o.getAmount() > 100)
      .map(this::enrich)
      .forEach(this::notify);
```

* 底层是 **ForkJoinPool.commonPool()**，全局单例，默认并行度 = CPU 核数 - 1。
* 数据会被递归切分（fork），各线程处理完再合并（join）。

5.2 什么时候不该用（比什么时候该用更重要）
------

* **任务含阻塞 IO（查库/RPC/HTTP）**：commonPool 线程数只有 CPU 核数量级。一个 IO 密集任务会把公共池占满，**同一 JVM 里所有其它 parallelStream 一起饿死**。这是 parallelStream 最著名的事故形态。
* **任务本身极轻**（如纯求和但数据量小）：任务切分与调度的开销超过计算本身，并行反而更慢。
* **有共享可变状态**：forEach 里往非线程安全的 ArrayList/HashMap 写——数据竞争，结果错乱。
* **依赖相遇顺序**：`findFirst`/`limit` 在并行下要约束拆分顺序，收益大减；`forEach` 的执行顺序不再确定。
* **装箱流**：`Stream<Long>` 装箱开销可能吞掉并行收益，改用 `LongStream`。

5.3 想自定义并行度
------

```java
// 交给自己的 ForkJoinPool执行，并行度可控，且不污染 commonPool
ForkJoinPool pool = new ForkJoinPool(32);
try {
    pool.submit(() ->
        orders.parallelStream().forEach(this::process)
    ).get();
} finally {
    pool.shutdown();
}
```

（这是一个依赖实现细节的技巧：parallelStream 用提交它的线程所在的 ForkJoinPool。JDK 版本升级后行为需验证。）

5.4 判断准则
------

* **CPU 密集 + 数据量大 + 无共享状态 + 无需保序** → 并行流合适。
* **IO 密集** → 别用并行流，用**自定义线程池 + 分批提交**（CompletableFuture.allOf 每批 500 条），并行度按下游承受能力定。
* 一句话：并行的收益上限是核数，阻塞 IO 会把并行退化成串行排队。**IO 密集的场景，并行度是资源问题，不是 CPU 问题**。

六、方案选型速查
------

| 场景 | 首选 | 备注 |
|---|---|---|
| 批处理/跑批千万行（迁移、对账） | 游标分页 + 分批处理 | 恒定内存，可断点续传 |
| 实时接口要全量读（配置/字典加载） | 重新审视设计：改 DB 方案或预加载到本地缓存 | "实时全量"本身就是错的设计 |
| 大报表导出 | 流式查询 + 边读边写文件（CSV 流式输出） | 禁止 collect 到内存再写 |
| 大文件解析/转换 | MappedByteBuffer 或 BufferedReader 逐行 | 堆内不装整文件 |
| 逐条处理太慢想加速 | CPU 密集用 parallelStream；IO 密集用自建线程池分批 | commonPool 不能碰 IO |
| 批量写入 | JDBC batch + rewriteBatchedStatements | 分批 size 500~1000 为常见起点 |

七、总结
------

* 大集合处理的第一性问题：**让数据不全部驻留堆内存**。
* 四板斧：**分批（游标分页是深分页唯一正解）。流式（fetchSize/Cursor，边读边处理）。内存映射（大文件走 PageCache 绕开堆）。并行（CPU 密集才用 parallelStream，IO 密集自建池）**。
* 所有方案的前置动作：先问能不能**下推**——过滤/聚合/排序让 DB 做，只取需要的结果。拉一百万行到内存里 `filter`，不如一条带 where 的 SQL。

> 2026-10 依据主题重写（原文件为空壳）。
