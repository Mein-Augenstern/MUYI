Parquet 深入理解（四）：Schema 与嵌套结构
====

系列收官篇。前几篇的数据都是扁平表，但真实业务数据几乎都是嵌套的——用户有地址数组、订单里有商品明细。**列式存储怎么存"结构"**？答案 Repetition Level 与 Definition Level，源自 Google Dremel 论文，是 Parquet 最精妙的部分。后端只要被问过"JSON 状的嵌套数据怎么进数仓"，这一篇就是答案。导航：[总起](./Parquet深入理解-总起.md)、[列式存储](./Parquet一-理解列式存储.md)、[文件结构](./Parquet二-文件结构剖析.md)、[编码](./Parquet三-编码技术.md)。

1 类型系统：物理类型少、语义类型多
------

* 原始类型（物理存储）：BOOLEAN、INT32、INT64、INT96（旧时间戳，不推荐）、FLOAT、DOUBLE、BINARY、FIXED_LEN_BYTE_ARRAY——就这几种。
* 逻辑类型是叠加其上的语义标注：STRING→BINARY、DECIMAL→INT32/64/BINARY/FIXED、DATE→INT32（距 1970-01-01 天数）、TIMESTAMP→INT64/INT96、UUID→FIXED(16)。
* 好处：**存储层只伺候少数物理类型，语义层随意扩展**，新增逻辑类型不破坏文件格式。

2 Schema 定义：三个修饰符 + group
------

```text
message User {
  required int64 user_id;                 // 必有，不允许 NULL
  optional group address {                // 可 NULL 的嵌套对象
    optional binary city (STRING);
    optional binary street (STRING);
  }
  repeated group addresses { ... }        // 数组：出现 0 次或多次
}
```

* `message` 是根（相当于表）；`required/optional/repeated` 是重复性修饰；`group` 是嵌套结构体；`(STRING)` 这类括号是逻辑类型注解。
* 关键认知：**再深的嵌套最终都被拍平成普通列**——上面的 address 变成 `address.city`、`address.street` 两列，跟扁平列一起独立压缩、独立读取。

3 NULL 怎么办：Definition Level 的动机
------

* 列存**不物理存储 NULL**（省空间）：3 行里 name 有一个是 NULL，物理上只存 `[张三, 王五]` 两个值。
* 但必须知道"谁缺席"。辅助数组 D：值 `[张三, 王五]` + D=`[1, 0, 1]`，完整还原三行。

4 Definition Level：定义到了哪一层
------

* 含义：**该值在 schema 路径上"定义到了第几层"**——路径上有几个 optional/repeated 祖先被实际定义了，值才算到位。它同时回答两个问题：有没有值；若缺失，缺在哪一层。
* 只有 optional/repeated 贡献 level，required 不算。

* 例：`required user_id → optional order → optional card → required card_id`，路径 order.card.card_id 有两个可选层：

| D 值 | 含义 |
|---|---|
| 0 | 连 order 都没有 |
| 1 | 有 order，card 为 NULL |
| 2 (max) | card 存在，card_id 是 required 必有值 |

* 还原示例：存储值 `[1234, 5678]` + D=`[2, 0, 1, 2]` → 4 个用户：第 1 个 card_id=1234；第 2 个没有 order；第 3 个有 order 没 card；第 4 个 card_id=5678。

5 Repetition Level：数组怎么切分
------

* 拍平后只剩一个值序列，**怎么知道值属于哪个用户的哪个列表**——这就是 RL 的工作。
* 计算规则三句话：**新记录（新 User）开始，R=0；同一个列表里继续重复，取"在该层重复"的 level；同层开新列表（如新 group），取中间 level**。

* 单层 repeated cities，三个用户：`["上海","北京","厦门"]`、`[]`、`["上海","深圳","广州","杭州"]`：

```
拍平值: [上海, 北京, 厦门, 上海, 深圳, 广州, 杭州]
R     : [0,    1,    1,    0,    1,    1,    1]
        ↑新用户       ↑空列表不写任何值  ↑新用户
```

* 多层嵌套（user → repeated groups → repeated cities）需要 0/1/2 三个值：0=新 user，1=同 user 新 group，2=同 group 新 city。R=`[0,2,0,0,1,1,2]` 就能还原出"第 3 个用户有 3 个 group"这样的结构。
* 注意：**空列表/空记录归属问题 RL 答不了，要 DL 补位**——两个 level 是一套机制的两半。

6 完整示例：Document 与 links
------

```text
message Document {
  required int64 doc_id;
  repeated group links { optional binary url (STRING); }
}
```

三个文档：doc1 links=[{a.com},{b.com}]；doc2 links=[]；doc3 links=[{NULL},{c.com}]。links.url 列最终存成：

```
值: [a.com, b.com, NULL, NULL, c.com]   (NULL 不实际落盘)
R : [0,     1,     0,    0,    1   ]
D : [2,     2,     0,    1,    2   ]
```

重建五步，r/d 联合判决：

```
1. R=0, D=2, "a.com" → 新文档    doc1.links[0].url
2. R=1, D=2, "b.com" → 同文档新link doc1.links[1].url
3. R=0, D=0, NULL    → 新文档, links 整个缺席 → doc2.links=[]
4. R=0, D=1, NULL    → 新文档, links 存在但 url 为 NULL → doc3.links[0].url=NULL
5. R=1, D=2, "c.com" → 同文档新link doc3.links[1].url
```

* max definition level = 路径上 optional/repeated 祖先数；max repetition level = 路径上 repeated 祖先数。**扁平全 required 的表 r/d 恒为 0，直接不存**——前面几篇"感觉不到它们存在"的原因就在这。

7 为什么说这个设计精妙
------

* **完全列式**：嵌套结构照样拍平成独立列（如 `users.orders.amount`），独立压缩、独立读取，"嵌套"没有破坏列存根基；
* **列裁剪依然有效**：查 amount 只读这一列，其余结构一概不碰；
* **r/d 值域极小**（通常 0~3）且大量重复——恰好是 RLE + Bit-Packing 的最佳客户（见[编码篇](./Parquet三-编码技术.md)），无 NULL 时 D 全是 max 值，一个长游程就完事。

面试追问链
------

* 嵌套数据怎么列存？→ 拍平成列 + 两个 level 记结构信息；这套机制来自哪？→ Google Dremel 论文；
* Definition Level 记什么？→ 路径上可选层被定义到第几层，同时回答有无值与缺失在哪层；required 为什么不算？→ 必然存在，不产生不确定性；
* Repetition Level 记什么？→ 值在哪个层级上重复，用于把拍平序列切回各用户的列表；R=0 是什么？→ 新记录开始；空数组怎么办？→ 不写值，靠 D 补位说明缺席；
* 扁平表有 r/d 吗？→ 全 required 时恒 0，不落盘；r/d 存储代价为什么低？→ 值域 0~3 且重复多，RLE+Bit-Packing 高效；
* 字典/谓词下推对嵌套列还有效吗？→ 有效，拍平后就是普通列，一切列存优化照常适用。

面试官视角小结
---

r/d level 是列存体系里区分度最高的一问，因为它没法靠"用过"蒙混。能准确说出 D 管"定义到哪层/NULL 归属"、R 管"在哪层重复/列表切分"，已经超过 90% 的候选人；能现场走完 links.url 的重建五步、点出"r/d 值域小所以 RLE 友好"的联动，说明真正吃透了 Dremel 的思路。这一问过关，ORC、ClickHouse 嵌套类型、Arrow 的 List/Struct 布局（见 [ApacheArrow列存内存格式](./ApacheArrow列存内存格式.md)）都是同一套心智的变体。

> 来源：javadoop《深入理解 Parquet（四）：Schema 与嵌套结构》（https://javadoop.com/post/parquet-04-parquet-schema）——本文为研读消化后的重写笔记，原文版权归 javadoop，引用请遵原文。
