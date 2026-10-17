结构化输出工程：Pydantic 与 LLM
====

> 定位：B 轨 agent 篇，讲"模型吐的自由文本怎么变成下游敢用的强类型数据"。素材来自 ai-service 生产代码（`app/agents/report_ocr.py`、`app/agents/report_interpret.py`、`app/schemas.py`，路径相对服务根）。智能体的循环与工具调用见[《ReAct 与 LangGraph 工程实践》](ReAct与LangGraph工程实践.md)——那篇讲"模型决定做什么"，这篇讲"模型的输出怎么接得住"。

一、为什么 LLM 输出需要结构化
------

模型的原生输出是自然语言文本，人读没问题；但拍照 OCR 的下游是 Java health 服务和 `health_indicator` 表——每行要落库、数值要画趋势图、参考区间要判偏高偏低。若让模型"把报告读给你听"，消费方就得用正则/关键词从散文里抠数值，每个抠取点都是一个失败面，而且错误是静默的：抠漏、抠错单位、"偏高"标错位置，往往到用户看到乱码的数据才暴露。

自由文本的三个典型失败面：

```
格式漂移：同一 prompt，两次输出的字段顺序/名称/有无开场白都不同
单位混入数值："140mmHg" 落库成字符串，趋势图直接报废
没有"我不知道"通道：看不清的字段模型会编一个或整段跳过，消费方分不清"没有"和"没识别出"
```

结构化把"解析"从下游 N 处启发式变成上游一处契约。OCR 链的出口是强类型的 `OcrDraft`：拿到的对象要么字段有值、要么显式 `None`，Java 侧按 camelCase 字段直接映射 DTO，不用猜。而且结构化在 ai-service 是**双向**的——`report_interpret.py` 的 `_render_indicators` 把结构化指标渲染成行式文本喂模型（`- 空腹血糖: 5.6mmol/L（参考 3.9~6.1）【偏高】`），`report_ocr.py` 再把模型文本解析回结构化。喂模型用"模型易读的行式文本"（嵌套 JSON 模型反而读不稳），接模型用"程序易读的 JSON"，两个方向各有最优格式。

二、Pydantic 模型即契约
------

1）类型、必选可选、默认值
------

`app/schemas.py` 里的单项指标模型（对应表一行）：

```python
class OcrIndicator(BaseModel):
    itemName: str = Field(description="指标名（如 空腹血糖）")                      # 唯一必填
    itemGroup: Optional[str] = Field(default=None, description="指标分组（如 血脂四项）")
    valueNum: Optional[float] = Field(default=None, description="数值结果（与 valueText 二选一）")
    valueText: Optional[str] = Field(default=None, description="文本结果（定性指标如 尿蛋白±）")
    unit: Optional[str] = Field(default=None, description="单位")
    refLow: Optional[float] = Field(default=None, description="参考区间下限")
    refHigh: Optional[float] = Field(default=None, description="参考区间上限")
```

四个工程点：

* **必选/可选是运行时强制，不是文档约定**：itemName 缺失 pydantic 直接抛 ValidationError；其余缺了给 None。必填多少是取舍——OCR 场景"指标名以外都可能看不清"，只留一个必填是承认现实。
* **类型做静默纠偏**：pydantic v2 默认宽松模式，模型给 `"5.6"`（字符串）会矫正成 5.6（float）；给 `"5.6mmol/L"` 则报错。类型校验是语义错误的第一道网。
* **二选一用两个可空字段表达**，而不是 `value: Union[float, str]` 一个字段混装——下游画趋势图只读 valueNum，不用再判类型；定性指标（尿蛋白 ±）走 valueText 只展示不画图。
* **camelCase 字段名对齐 Java DTO**（schemas.py 模块 docstring 明说），省掉一层字段名映射。

同一套模型还管三件事：FastAPI 入参校验（OcrRequest 收 HTTP body，类型不符自动 422）、模型输出的解析目标、Java 侧的接口契约对照——契约只写一份，三个消费方共享。

2）模型看得见的是哪份 schema
------

注意：本项目里模型**看不到** Pydantic 模型——它看见的是 `report_ocr.py` SYSTEM_PROMPT 里手写的 JSON 格式说明，即 schema 的"模型侧投影"。两份契约逐字段对齐，且约束语义也成对：

```
prompt: "看不清的字段用 null"            ↔  schema: Optional + default=None
prompt: "数值去掉单位和逗号"             ↔  schema: valueNum 是 float（防类型报错）
prompt: "reportYear":2026（示例不加引号） ↔  schema: reportYear 是 int
prompt: indicators 数组元素的字段清单     ↔  schema: List[OcrIndicator]
```

风险也随之而来：改 schema 不改 prompt，模型就按旧字段名输出（键缺失全变 None，静默降级）。更彻底的工程化是把 `OcrDraft.model_json_schema()` 序列化直接进 prompt、或走结构化输出 API（见第五节），让契约只有一份。`Field(description=...)` 在本项目主要服务两拨人类读者——FastAPI /docs 的接口文档与 Java 同事。

三、宽容解析：模型输出要剥壳
------

SYSTEM_PROMPT 明令"只输出一个 JSON 对象，不要任何其他文字或 Markdown 代码块标记"，但 `_parse` 仍然先找代码围栏。原因：模型被海量 markdown 训练数据浸过，"输出 JSON"强烈激活 ```json 围栏行为——指令能压低概率，压不到零。线上常见的包裹形态：

```
好的，以下是提取结果：          ← 前置客套
```json
{"title": "...", "indicators": [...]}
```                             ← 围栏
希望对你有帮助！               ← 后缀杂讯
```

`report_ocr.py` 的剥壳实现（逐行有讲究）：

```python
def _parse(raw: str) -> OcrDraft:
    text = raw.strip()                                    # 去首尾空白
    fence = re.search(r"```(?:json)?\s*(.+?)\s*```", text, re.S)
    if fence:
        text = fence.group(1)                             # 有围栏取围栏内的
    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:
        raise ValueError(f"模型输出不是合法 JSON: {text[:120]}") from exc
    indicators = [OcrIndicator(**item) for item in data.get("indicators") or []]
    return OcrDraft(title=data.get("title"), hospital=data.get("hospital"),
                    checkupDate=data.get("checkupDate"),
                    reportYear=data.get("reportYear"), indicators=indicators)
```

* `re.S` 让 `.+?` 跨行匹配——JSON 是多行的，没有它匹配在第一行就断；`(?:json)?` 兼容 ```json 与裸 ``` 两种围栏头；惰性 `.+?` 从第一个围栏头配到最近的围栏尾，防贪婪误吞。
* 失败时错误消息带原文前 120 字符——排障时能直接看到模型到底吐了什么；`from exc` 保留因果链。**可诊断性是解析器的一部分**。
* `data.get(...)`、`or []`：键缺失不炸，交给 pydantic 默认值兜底（见第四节）。

这是"指令先行、剥壳殿后"的双保险：prompt 约束把围栏概率压低，剥壳把漏网的接住。只做前者，线上会周期性炸；只做后者，等于放任脏输入。更重的宽容手段还有首尾大括号截取、json_repair 修尾逗号——本场景围栏剥壳 + json.loads 够用，**宽容度按观察到的失败形态加，不做预防性过度设计**。

剥壳的覆盖边界也要心里有数：前后杂讯在有围栏时随围栏一起剥掉（正则只取围栏内，客套话根本进不了 json.loads）；但**无围栏、纯前后杂讯**的输出（`好的：{...}，希望有帮助`）剥壳帮不上忙，json.loads 直接失败进 ValueError。这是当前实现的已知缝隙——真观察到了再加大括号截取，不为没见过的失败写代码。

四、失败分层：解析失败 / 语义失败 / 字段缺失
------

| 层级 | 症状 | 检测点 | 处理策略 |
|---|---|---|---|
| 解析失败 | 不是合法 JSON（围栏漏剥/输出截断/夹叙夹议） | json.JSONDecodeError | 转 ValueError，整单失败，可重试 |
| 语义失败 | JSON 合法但内容错：幻觉数值、日期格式错、高低限互换 | 解析器测不出 | prompt 约束 + 职责拆分 + 人工核对 |
| 字段缺失 | 键不存在或为 null | pydantic 校验 | 默认值兜底，前端逐字段降级 |

**解析失败**：快速失败 + 可重试（模型输出有随机性，重试常能过）。特殊亚型是**截断**——指标太多超出输出 token 上限，JSON 断在数组中间，重试也一样断，只能靠降 schema 复杂度或分段（见追问链 3）。

**语义失败最阴险**：校验全绿、数据是错的。本项目三道防线：其一，prompt 约束"只提取图片中真实存在的内容"压制编造；其二，**职责拆分**——abnormalFlag（偏高/偏低）不由模型判，由 Java 侧按参考区间确定性计算（schemas.py 里 InterpretIndicator 的注释明说"AI 只需要解读临床含义，不需要自己判断是否异常"），能确定性算的绝不交给概率模型；其三，**产品层兜底**——OcrDraft 定位是"草稿"，docstring 原话"AI 识别可能出错（尤其是手写体和模糊图片），所以必须人工确认"，AI 只做预填表单，用户核对后才入库。

**字段缺失**：粒度最细，逐字段降级——checkupDate 为 None 前端让用户手填，title 为 None 展示"未命名报告"，indicators 为空给空表单。缺字段不缺整单。

五、与 function calling / JSON mode 的关系
------

| 路线 | 保证什么 | 代价 | 适用 |
|---|---|---|---|
| prompt + schema + 宽容解析（本项目 OCR） | 无硬保证，靠剥壳与默认值兜 | 零依赖，任何模型任何 API 可跑 | 链路简单、下游有人工核对 |
| JSON mode | 合法 JSON | 不保证符合 schema，字段名/类型仍可漂 | 只要 JSON 不要形状 |
| tool calling / 结构化输出（json_schema strict） | schema 级约束（字段名/类型/必选） | 依赖模型支持；工具调用引入"模型选不选调"的自由度 | 输出直接驱动动作/落库、无人兜底 |

项目里的分工正好跨两路：OCR 走第一路——视觉模型 + `StrOutputParser()` 最朴素的链，且有"草稿 + 人工核对"兜底，宽容解析的残余故障率可接受；家庭助手（ReAct 篇）的 `bind_tools` 走第三路——tool_calls 的参数直接驱动 HTTP 查询，参数错了整个循环跑偏，必须 schema 硬约束。

选型心法：**约束强度跟"错了有没有人拦"成反比**。人工核对在 → prompt + 宽容解析够；输出无人审、直接触发动作 → 上 tool calling 或 strict 结构化输出。另注意 tool calling 是为"模型选动作"设计的，纯粹要固定形状的抽取，用各家 structured outputs 的 json_schema 参数更直白——不必为结构化硬造一个假工具。上了结构化输出还能顺带消灭第二节的契约双维护：`model_json_schema()` 直接作 API 参数。

六、追问链
------

1. **嵌套结构/数组的稳定性怎么保证？** 数组是最脆的部位：模型会漏项、并项、在数组外多发挥字段。本项目两层稳：prompt 给出数组元素的完整字段清单（含 null 语义），解析侧逐项 `OcrIndicator(**item)`——每个元素独立过校验，坏在哪一项报哪一项。嵌套更深时（元素套元素）稳定性急剧下降，实践中 schema 控制在两层内，更深的拆成多次调用。
2. **某一项畸形怎么办？** 当前实现一项坏整单失败（列表推导没有逐项 try/except）。预填表单场景更优的取舍是逐项 try/except 跳过坏项 + 日志计数——牺牲完整性保可用性：少一项用户自己补，全灭用户重拍。宽容粒度是三档选择题：字段级（.get）、对象级（逐项 try）、整单级（现状），按下游容忍度选。
3. **token 预算怎么约束 schema 复杂度？** 两头都吃：prompt 里的格式说明占输入 token，字段越多输出越长——一份完整体检报告上百项指标，输出上限一到 JSON 就断在数组中间，解析必失败且重试无用。对策按成本排序：砍字段（只留下游真消费的）、超长报告分段识别再合并、最后才是提高 max_tokens（花钱）。**每个字段都在花预算和稳定性，schema 不是免费的**。
4. **为什么不用 LangChain 的 PydanticOutputParser？** 生态有现成的（自动把 model_json_schema 塞 prompt + 解析修复）。手写 `_parse` 的价值在剥壳逻辑可见可改——黑盒 parser 出问题时排障慢。等多条链共用一套解析时再抽公共库不迟。
5. **格式类约束（如日期）怎么落地？** checkupDate 在 schema 里只是 `Optional[str]`，"YYYY-MM-DD" 的约束活在 prompt 里靠模型自觉。要硬保证得升级 schema 类型（date + 重试）或解析后二次校验——本场景反正用户要核对，软约束够。
6. **为什么不端到端——图片直接出解读？** 一次调用"图片→白话解读"省一条链，但丢掉的不只是指标落库：OcrDraft 这个中间结构是**人工核对点**，端到端把识别错误直接走私进解读文本，用户无从发现。结构化中间态 = 可核对、可落库、可复用（解读、趋势图、异常判定三个下游共享同一份数据），这正是"草稿"定位的价值。

七、面试官视角小结
------

* **分得清三层失败**：解析/语义/缺失，各自的处理位置不在一层——解析器、职责设计与产品兜底、pydantic 默认值，答成一锅"解析失败"暴露没做过。
* **懂"模型看得见什么"**：prompt 里的格式说明才是模型侧 schema，pydantic 是程序侧 schema，两份要同步；类型/必选可选/description 各约束谁说得清。
* **宽容解析有真实案例**：围栏剥壳的正则细节（re.S、(?:json)?、惰性匹配）、错误消息带原文片段的可诊断性、指令与兜底的双保险关系。
* **有取舍意识**：约束强度与兜底人力成反比；逐项失败 vs 整单失败；schema 复杂度花 token 预算。

一句话总结：**结构化输出的工程本质是"把契约显式化"——pydantic 定义程序侧契约，prompt 里的格式说明是模型侧投影，宽容解析（剥围栏 + 默认值）吸收两份契约的缝隙；失败按解析/语义/缺失三层分流，约束手段（prompt / JSON mode / tool calling）按"错了有没有人拦"选型。**

> 2026-10 新增（P3 AI 栈 · 素材来自 ai-service 生产代码）。
