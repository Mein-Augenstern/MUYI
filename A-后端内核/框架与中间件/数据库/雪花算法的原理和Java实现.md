雪花算法的原理和Java实现
====

看在前面
------

> * 原文：<a href="https://mp.weixin.qq.com/s/0kzisisJmEDDyEZiWebkVQ">雪花算法的原理和Java实现</a>（公众号"芋道源码"转载，原发 CSDN lq18050010830）

* 本篇是**理论篇**：位布局、生成流程、Java 实现与优缺点。生产落地（workerId 分配、时钟回拨策略、sequence 表租约）见库内《C-架构与云原生/分布式ID-从雪花算法到生产实现》；号段方案见《美团-Leaf——美团点评分布式ID生成系统》。

一、概述：为什么需要它
----

* SnowFlake 算法是 **Twitter 开源的分布式 id 生成算法**，核心思想：**使用一个 64 bit 的 long 型数字作为全局唯一 id**。

* 分布式系统广泛使用；应用内基本是直接引入 jar、稍作配置即可——代码量极小。

* 相比 UUID：UUID 无序且 36 字符太长，做主键对索引不友好；雪花 id 是 8 字节 long、**趋势递增**（引入了时间戳），对数据库索引友好。

* 相比数据库自增：多实例同时发号不需要依赖同一个 DB 步长分配，无单点、无网络开销。

二、核心：64 bit 的位布局
------

```
| 1 bit |    41 bit     |   10 bit   |   12 bit   |
| 符号位 |    时间戳     |   机器位    |   序列位   |
| 恒为0 | 毫秒，够用69年 | 最多1024节点 | 每毫秒4096个 |
```

* **第 1 部分 1 bit**：基本不用、恒为 0。因为二进制里最高位是符号位，1 表示负数，生成的 id 都要是正数，这一位固定死。

* **第 2 部分 41 bit**：毫秒级时间戳，**不是存当前时间戳，而是存"当前时间戳减去一个自定义起始时间戳（EPOCH/twepoch）"的差值**。41 bit 可表示 2^41−1 个毫秒，换算成年：(1<<41) / (1000*60*60*24*365) ≈ **69 年**。

* **第 3 部分 10 bit**：记录工作机器 id，代表 2^10 = **最多 1024 个节点**（原文拆成 5 bit 机房 id + 5 bit 机器 id，即 32 机房 × 32 机器）。

* **第 4 部分 12 bit**：同一毫秒内的序列号，2^12 = **每毫秒每节点可发 4096 个**。

* 三个数字连乘即可感受容量：1024 节点 × 4096/ms ≈ **400 万+/秒**，且 id 趋势递增。

三、Java 实现（Twitter IdWorker 改编版）
------

核心逻辑只有 `nextId()` 一个方法（`synchronized` 保证线程安全）：

```java
public class SnowflakeIdWorker {
    private final long twepoch = 1420041600000L;             // 自定义起始时间戳 2015-01-01
    private final long workerIdBits = 5L;                    // 机器标识 5 位
    private final long datacenterIdBits = 5L;                // 机房标识 5 位
    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);        // 31
    private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);// 31
    private final long sequenceBits = 12L;                   // 序列号 12 位

    private final long workerIdShift = sequenceBits;                       // 12
    private final long datacenterIdShift = sequenceBits + workerIdBits;    // 17
    private final long timestampLeftShift = sequenceBits + workerIdBits
            + datacenterIdBits;                                            // 22
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);         // 4095

    private long workerId;      // 机器 id：0~31
    private long datacenterId;  // 机房 id：0~31
    private long sequence = 0L; // 同一毫秒内的序列号
    private long lastTimestamp = -1L; // 上次发号时间戳，初始 -1

    public SnowflakeIdWorker(long workerId, long datacenterId) {
        if (workerId > maxWorkerId || workerId < 0)
            throw new IllegalArgumentException("worker Id can't be greater than %d or less than 0");
        if (datacenterId > maxDatacenterId || datacenterId < 0)
            throw new IllegalArgumentException("datacenter Id can't be greater than %d or less than 0");
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    public synchronized long nextId() {
        long timestamp = timeGen();                          // 当前时间（毫秒）
        if (timestamp < lastTimestamp)                       // ① 时钟回拨：直接抛异常拒绝发号
            throw new RuntimeException(
                "Clock moved backwards. Refusing for %d ms");
        if (lastTimestamp == timestamp) {                    // ② 同一毫秒：序列号自增
            sequence = (sequence + 1) & sequenceMask;        // 溢出则归 0
            if (sequence == 0)                               // ③ 本毫秒 4096 个用尽
                timestamp = tilNextMillis(lastTimestamp);    //    自旋等到下一毫秒
        } else {
            sequence = 0L;                                   // 新毫秒，序列归 0
        }
        lastTimestamp = timestamp;
        // ④ 按位或拼接：时间差左移22位 | 机房左移17 | 机器左移12 | 序列
        return ((timestamp - twepoch) << timestampLeftShift)
                | (datacenterId << datacenterIdShift)
                | (workerId << workerIdShift)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {         // 自旋等待下一毫秒
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp)
            timestamp = timeGen();
        return timestamp;
    }

    private long timeGen() { return System.currentTimeMillis(); }
}
```

* 读代码的三个要点：**位运算拼 id**（各字段左移到位后按位或）、**同毫秒序列自增**（`& sequenceMask` 到 4095 后翻 0）、**序列耗尽自旋到下一毫秒**。

* 生产实现的 `IdGenerator` 在 ① 处策略不同：不是抛异常而是把时间戳钳制在上次发号毫秒、用序列位余量吸收回拨窗口——见库内生产篇 3.2 节。

四、SnowFlake 的优点
------

* **高性能高可用**：生成时不依赖数据库等外部组件，纯内存、一按位或就出结果。

* **容量大**：每毫秒能生成 4096 个 id，1024 个节点并发，单机每秒百万级。

* **ID 自增**：时间戳在高位，id 整体趋势递增，存入数据库索引效率高（对比 UUID 的随机写放大）。

五、SnowFlake 的缺点（必考）
------

* **强依赖系统时钟的一致性**：如果时钟发生**回拨**（NTP 校时、宿主机迁移），可能发出与历史重复的 id。

* 通用应对：回拨幅度小则等待追平或借序列位余量，幅度大则拒绝发号；启动期做时钟校验；workerId 不允许不同实例重复。

* 原文给的改造思路：机器位 10 bit 的划分可按业务定制——机房数量少时，5 bit 机房够用，富余位数可挪作业务分片标识；workerId 的**分配**（不能写死、不能随机撞号）是落地时真正的问题，解法见库内生产篇的 `sys_sequence` 表租约方案。

六、面试速答
------

* "雪花算法是什么"：Twitter 开源的分布式 id 算法，64 位 long = 1 符号 + 41 毫秒时间戳 + 10 机器位 + 12 序列位，单机每毫秒 4096 个，趋势递增、无外部依赖。

* "缺点是什么"：依赖时钟，回拨会重复；workerId 要自行保证全局唯一。

* "69 年怎么算的"：41 bit 毫秒 ≈ 2^41 ms ÷ 一年毫秒数 ≈ 69 年；起始点用自定义 EPOCH，所以是从启用日起算 69 年。

> 内容来源：[原文](https://mp.weixin.qq.com/s/0kzisisJmEDDyEZiWebkVQ) · 2026-10 整理回填。
