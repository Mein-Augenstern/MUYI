Parquet 深入理解（三）：编码技术
====

系列第三篇。上一篇（[Parquet二-文件结构剖析](./Parquet二-文件结构剖析.md)）拆到了 Page，这一篇进到 Page 里看数据到底怎么存：**先编码、再压缩**，两层配合才有 5~20 倍乃至更高的压缩比。这也是列存最"值钱"的一层——同列数据的特征（低基数、递增、重复）被逐列兑现成空间和速度。导航：[总起](./Parquet深入理解-总起.md)、下一篇 [Schema与嵌套结构](./Parquet四-Schema与嵌套结构.md)。

1 编码 vs 压缩：先分清两件事
------

* **编码**改变表示方式，结果仍是"明文"，可以直接参与计算（过滤、聚合可在编码数据上做）；**压缩**（Snappy/Zstd 等）产出必须解压才能用。
* Parquet 流水线：**先编码、再压缩**。理由：编码能利用数据特征（低基数、递增性），通用压缩吃不到这些；编码处理特征冗余，压缩再兜住剩余冗余。
* 一句话例：`[北京, 上海, 北京, 北京, 广州]` → 字典 `{0:北京,1:上海,2:广州}` + 数据 `[0,1,0,0,2]`。

2 示例数据：一列一个性格
------

orders 表 1000 万行：order_id **递增整数**；user_id 随机整数；amount 随机小数；status **低基数（5 种值）**；city 中低基数（几百个）；create_time **递增时间戳**。

不同列特征不同，所以 Parquet **每列独立选编码**——这是它压缩率的结构性来源。

3 字典编码（DICTIONARY）
------

* 原理：值建字典，字符串变整数 ID。status 列 5 种值 → 0~4。
* 空间账：原始 80MB（均 8 字节/条）→ 编码后约 3.75MB（每值 3 bit，字典本身仅 40 字节），**约 21 倍**。
* 文件落位：一个 Column Chunk 只有一个 Dictionary Page 存字典，各 Data Page 存整数索引（见[上篇](./Parquet二-文件结构剖析.md)）。
* **Fallback 机制**：字典默认上限 1MB，超限后当前 Page 写完即切换 PLAIN——同一 Chunk 里前面 Page 是字典编码、后面是 PLAIN，是正常现象不是损坏。
* 适合：状态、枚举、城市、用户等级；不适合：主键、毫秒时间戳、随机金额、长文本。经验法则：**distinct 值 ≤ 10 万通常有效**。

4 RLE + Bit-Packing 混合编码
------

* RLE 存"连续重复"：8 个连续的 0 → `(值0, 次数8)`。**效果强依赖排序**，乱序数据几乎无游程可吃。
* Bit-Packing 按实际取值范围用最少 bit：5 种值只需 3 bit，`[0,1,2,0,3,4,0,1]` 从 256 bit 降到 24 bit（约 10.7 倍）。
* 混合策略：**run 之间动态切换**——连续重复走 RLE，交替值走 Bit-Packed。控制字节规则：
  - header 最低位 0 → RLE run：`header = (count << 1)`，payload 是重复 bit-width 位的值；
  - header 最低位 1 → Bit-Packed run：`header = (num_groups << 1) | 1`，一个 group 8 个值，共 `num_groups × 8` 个值按 bit-width 紧排。
* 与字典组合是标准连招：`[北京×3, 上海×2, 广州×4]` → 字典 {0,1,2} + Data Page `[(0,3),(1,2),(2,4)]`。

5 Delta 编码（DELTA_BINARY_PACKED）
------

* 原理：存首值 + 差值。order_id `[1..10]` → 首值 1 + 差值全 1；差值全 1 再配 RLE：`(1, 10000000)`——**1000 万个 64 位整数（80MB）塌缩成几个字节**。
* 时间戳同理：毫秒级 13 位时间戳的差值常常只有 100~150，位宽骤降。
* DELTA_BINARY_PACKED 的实现要点：按 block 分组（默认 **128 值/block**）；每 block 记 min_delta，实际存 `delta - min_delta` 进一步压位宽；差值再走 Bit-Packing。block 结构含 block_size、num_mini_blocks、total_value_count、first_value、min_delta、bit_widths——**支持随机访问、差值不全相同也能压**。
* 适合：自增 ID、时间戳、序列号；不适合：UUID、随机金额、哈希值。

6 Bit-Packing 与 PLAIN
------

* bit_width = `ceil(log2(max+1))`：0~1 用 1 bit 省 97%；0~255 用 8 bit 省 75%；0~65535 用 16 bit 省 50%。紧密排布示例：bit_width=3 存 8 个值 → 3 字节。
* 它还是配角专业户：RLE/Bit-Packed 混合里的交替段、Definition/Repetition Level（见[下篇](./Parquet四-Schema与嵌套结构.md)）、Delta 的差值段。
* **PLAIN 就是不编码**，直接存原始二进制。用于：随机浮点、UUID、字典超限 fallback、Boolean（0/1 没必要折腾）。

7 编码怎么选、效果怎么叠
------

Parquet 的自动策略（类型 → 首选 → 失败回退）：

| 类型 | 策略 |
|---|---|
| BOOLEAN | RLE |
| INT32 / INT64 | 试 DELTA → 回退 PLAIN |
| FLOAT / DOUBLE | PLAIN |
| BINARY / STRING | 试 DICTIONARY → 回退 PLAIN |

* **编码 + 压缩叠加**：status 列 100MB → 字典+RLE 编码后 4MB → Snappy 后 1.5MB，总压缩比约 **67:1**。
* 通用压缩四选一：Snappy（2~4x，很快，默认）；Zstd（3~8x，中速，省存储）；Gzip（3~6x，慢，兼容性）；LZ4（2~3x，极快，实时分析）。经验：查询优先 Snappy/LZ4，存储成本优先 Zstd。
* **编码数据直接算**：`city='北京'` 的过滤先查字典得 0，直接在整数序列上比对，不解码字符串，效率数倍提升；GROUP BY 同理可对字典 ID 做整数哈希聚合、最后翻译回字符串。

面试追问链
------

* 编码和压缩的区别？→ 编码后仍是明文可参与计算，压缩后必须解压；Parquet 顺序？→ 先编码再压缩，两层各吃各的冗余；
* 字典编码什么时候失效？→ distinct 太多（>10 万或字典超 1MB），fallback 到 PLAIN；fallback 后文件什么样？→ 同一 Chunk 前面 Page 字典编码、后面 PLAIN；
* RLE 靠什么发挥？→ 连续重复，即数据按该列排过序；Bit-Packing 位宽怎么定？→ ceil(log2(max+1))，按值域不是按类型宽度；
* 自增 ID 和时间戳用什么？→ DELTA_BINARY_PACKED，差值小且 block 内再提 min_delta 压位宽；随机浮点呢？→ PLAIN，没特征可用；
* 为什么排序对 Parquet 这么重要？→ 排序放大 RLE/Delta 游程，同时让 min/max 统计能跳 Row Group，空间和查询两头受益；
* 67:1 怎么来的？→ 字典+RLE 100MB→4MB，Snappy 再到 1.5MB，编码与压缩乘法叠加。

面试官视角小结
------

编码题最能试出"是否理解列存本质"。只会说"列存压缩好"是背结论；能按列的数据特征（基数、有序性、递增性）配出对应编码、算得出 21 倍/67 倍这类账、讲得出字典 fallback 的机制，才说明理解了"为什么压缩比能这么高"。加分点有二：主动提排序的双重收益（编码游程 + 谓词下推）；知道过滤聚合可以在编码数据上直接做——这一步直接通向向量化执行引擎的设计。

> 来源：javadoop《深入理解 Parquet（三）：编码技术》（https://javadoop.com/post/parquet-03-parquet-encoding）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
