热key治理-发现与处置三板斧
====

看在前面
----

[缓存穿透-击穿-雪崩](缓存穿透-击穿-雪崩.md)里所有击穿/热 key 方案，前提都是**先知道谁是热 key**——总不能等线上打挂了才知道。探测分服务端与客户端两派（看见的视角不同），处置有三板斧。姊妹篇：[缓存一致性-CacheAside与延迟双删](缓存一致性-CacheAside与延迟双删.md)。


> **面试官：你一直说"热 key"，那热 key 你是怎么发现的？总不能等线上打挂了才知道吧。**


服务端探测：问 Redis 自己
----

```bash
# Redis 4.0+ 自带，基于 LFU 访问频次统计（前提：maxmemory-policy 设为 allkeys-lfu）
redis-cli --hotkeys

# 辅助手段
redis-cli info commandstats      # 各命令的调用频次分布
redis-cli --bigkeys              # 大 key（热 + 大 = 带宽杀手）
```

`--hotkeys` 的原理值得说一句：Redis 在 LFU 模式下，每个对象的元信息里存了一个对数衰减的访问计数（`LFUDecrAndReturn`）。counter 越大代表越热，且随时间衰减以反映"近期热度"。`--hotkeys` 就是遍历采样把这个 counter 排序取 TopN。

**局限**：改淘汰策略需要重启或 `CONFIG SET`（影响线上行为）。看到的是"整个 Redis 实例"的视角，分不清是哪个业务、哪个上游在打。至于 `MONITOR` 命令——能看全量命令流。但会**显著拖慢 Redis**（单线程里每条命令多一层解析输出），生产禁用。说出来面试官会点头。

客户端探测：在 SDK 里埋点（JD hotkey 的思想）
----

服务端只能看到聚合结果，而**热 key 的应对动作（提升本地缓存）恰恰发生在客户端**。所以业界主流是在客户端埋点统计再上报。京东开源的 hotkey 框架是标杆架构：

```
每个应用实例内嵌 agent（SDK 拦截 Redis 访问，滑动窗口计数）
        │  netty 长连接异步上报（只报超阈值的，避免上报流量本身成为负担）
        ▼
hotkey server 集群（汇总多实例计数，滑动窗口判定是否达到"热"标准）
        │  推送热 key 变更事件
        ▼
所有实例收到推送，把该 key 提升进本地内存缓存 → 后续读取不再碰 Redis
```

自己手写一个最简版（面试画这个足够了）：

```java
// 1. 计数：拦截读路径
ConcurrentHashMap<String, LongAdder> counter = new ConcurrentHashMap<>();
public String get(String key) {
    counter.computeIfAbsent(key, k -> new LongAdder()).increment();
    return redis.get(key);
}

// 2. 定时汇总：超阈值的 key 提升为本地缓存，计数清零重新观察
scheduler.scheduleAtFixedRate(() ->
    counter.forEachEntry(1024, e -> {
        if (e.getValue().sumThenReset() >= HOT_THRESHOLD_PER_5S) {
            l1.put(e.getKey(), loadFromRedis(e.getKey()));  // 就地提升
        }
    }), 5, 5, TimeUnit.SECONDS);
```

JDK 里还有个取巧的近似 TopN：`LinkedHashMap` 的 `accessOrder=true` 模式。

```java
// accessOrder=true：get 也会把节点移到链表尾部，头部即最久未访问 → 有限容量下自动保留"最近最热"
Map<String, String> topN = new LinkedHashMap<String, String>(16, 0.75f, true) {
    @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
        return size() > 10_000;    // 只留最热的 1 万个 key，近似热 key 榜单
    }
};
```

注意它是**非线程安全**的（要包一层同步或换 `ConcurrentLinkedHashMap`）。且反映的是"新近访问"而非"频次"。生产用 Caffeine（W-TinyLFU，频率+新近）更准，但面试提 LinkedHashMap 能体现你对 JDK 容器的熟悉度。

处置三板斧
----

1. **本地缓存化**：热 key 提升进 Caffeine（上一节的 L1），读取不再出进程。这是收益最大的一招；
2. **key 打散**：把 `hot:key` 复制成 `hot:key:1 ... hot:key:N`，读写随机挑一个副本。这样把单分片压力摊到 N 个分片。适合 value 只读场景；写时要广播更新所有副本。
3. **配合防击穿**：热 key 一旦要过期，就是准击穿现场。提前安排上逻辑过期或永不过期，防护就完整了。


追问链
----

* 为什么客户端探测是主流？→ 服务端只见聚合结果，而应对动作（提升本地缓存）恰恰发生在客户端；
* MONITOR 为什么生产禁用？→ 单线程里每条命令多一层解析输出，显著拖慢 Redis；
* key 打散后写怎么办？→ 广播更新所有副本——只读场景才适用打散。