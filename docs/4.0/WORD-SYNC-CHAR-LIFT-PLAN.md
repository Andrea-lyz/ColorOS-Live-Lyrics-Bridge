# 逐字同步的字符上浮动画（Per-character Vertical Lift）改造计划

状态：**实施中（分支 `feat/word-sync-char-lift`）。Slice A–D 已合，
本地 `testDebugUnitTest` / `lintDebug` / `assembleDebug` 全绿；
§8 的设备回归矩阵一条都没跑，Slice E 未开始。** 2026-09-11 评审发现原 §3.1 的
canvas-x 前沿模型在换行段上会让段尾字素常驻峰值，已改为流坐标模型
（§3.1/§3.3/§3.4/§4 同步修订）。

启动门槛：当前 renderer 处于 Phase 6 之后的稳定基线（`OfficialLyricTextRenderer`
仍在 `LockscreenLyricsModule` 内、`OfficialLyricDrawCoordinator` 已收口 draw 编排）。
本计划所有切片默认关闭新效果，零改动路径必须与现基线逐像素一致。

---

## 1. 目标

在 Bridge 侧逐字（word-timed）高亮的基础上，为"揭示前沿正在经过的字符"增加
Apple Music 风格的轻微垂直上浮动画：字符随逐字进度接近而抬起、经过时到达峰值、
经过后带缓动回落。要求：

- 与现有逐字揭示（clip-reveal + 羽化前沿 + glow）完全同步，共用同一个进度时钟；
- 缓动自然（非线性起落、可不对称：起快落慢）；
- 纯视觉增强，**不需要任何新的歌词时间数据**——沿用 `WordRange` 词级时间戳，
  字符级相位由词内几何插值得出；
- 用户可开关，默认关闭；关闭时渲染路径与现基线完全一致；
- AOD 低帧率模式下强制禁用；
- 不修改 Provider、`lyricInfo` 契约、歌词解析、Recycler ownership、AOD 时序。

## 2. 现状取证（源码证据）

本节结论均为源码静态确认，行号基于 2026-09-11 工作区。

### 2.1 渲染归属：官方原生 View 画布内接管，非悬浮窗

- Hook 点是 SystemUI 进程内 `TextView.onDraw(Canvas)`
  （`LockscreenLyricsModule.java:2402-2407`），配套 `setText` /
  `onAttachedToWindow` / `setVisibility` 等 hook。全模块无
  `WindowManager.addView` / overlay 窗口。
- `OfficialLyricDrawCoordinator.draw()`（`OfficialLyricDrawCoordinator.java:119`）
  在官方 `LyricsRecyclerView` 每行歌词 TextView 自己的 canvas 上调用
  `host.drawWithOfficialRenderer(canvas, textView, frame)` 后 **`return null`
  跳过原生绘制**；未命中/异常时 `proceed()` 回落原生，单次 draw 异常后该
  binding+model 进入 `failedBindings` 拉黑并永久回落（:277-289）。
- 结论：canvas 完全归我们所有，可以自由做逐字符几何变换；但绘制发生在原生
  行 TextView 的 RenderNode 内，**默认 clipToBounds，上浮不能越过行 View 顶边**。

### 2.2 逐字揭示的现有实现：整段绘制 + clipRect 水平扫描

- 主入口 `OfficialLyricTextRenderer.draw()`（:12185）→ 行窗口
  `drawMainLineWindow`（:13546，遍历 `drawLines` 换行段）→
  `drawSegment`（:13789）。
- `drawSegment` 先整段画未激活底色（:13809），再：
  - `resolveSegmentRevealWidth`（:13948）把词进度换算为该段内的揭示宽度，
    **已处理激活词跨换行段的情况**（:13977-13999 遍历所有段汇总
    `totalActiveWidth`）；
  - `drawProgressGlow`（:14040）画进度光晕，底层是按
    `(line, text, start, end, textSize, width, typeface)` keyed 的
    `GlowSegmentCache` **固定 baseline 位图缓存**（:14240-14328）；
  - `drawRevealedText`（:14376）用 `clipRect(segmentLeft → revealRight)` +
    `activeFeatherShader`（LinearGradient 羽化前沿，:14359）画已揭示部分。
- **没有逐字符独立 draw call，字符没有独立几何通道**——这是本计划要补的唯一
  结构性缺口。
- 单行紧凑变体 `drawCompactLine`（:13623）走同一套
  `drawRevealedText`/`drawProgressGlow`，首期明确排除（见 §10 非目标）。

### 2.3 进度时钟与帧驱动：现成可用

- 词级时间：`WordRange { timeMillis, start, end }`（`render/WordRange.java`），
  词内线性插值 `WordLyricRenderSupport.wordRevealProgress`（:120），末词时长由
  相邻词距中位数推断（`typicalWordRevealDurationMillis` :62、
  `lastWordRevealEndMillis` :91）。
- 播放位置逐帧外推：`LockscreenIntegrationPolicy.extrapolatePlaybackPosition`
  + `SystemClock.elapsedRealtime()`（`LockscreenLyricsModule.java:4032`、:4176），
  每帧进度平滑连续。
- 帧循环：绘制中 `progress < 1` 即 `textView.postInvalidateOnAnimation()`
  自续帧（:13070、:13195 等），vsync 对齐。
- 缓动 helper 现成：`smoothStep`（:13608）、`smootherStep`（:13618）、
  `interpolate`（:13613）。
- 门控现成：`shouldDrawWordProgress`（:13531）已排除
  `aodLowFrameRateMode`；一词短行的 timestamp-highlight 降级
  （`shouldUseTimestampHighlight`，`WordLyricRenderSupport.java:144`）。
- `DrawFrame` 复用（`DRAW_FRAME_REUSE_ENABLED`，
  `OfficialLyricDrawCoordinator.java:216-231`）不冻结动画：进度在绘制时刻从
  外推 position 现算。

### 2.4 垂直空间

- `drawRevealedText` 的 clip 纵向是 `0..canvas.getHeight()`，canvas 内无额外
  纵向裁剪；行槽几何由模块自算（`resolveSlotHeight` :13201，
  `LYRIC_SLOT_VERTICAL_PADDING_DP = 12f` 等常量在
  `render/WordLyricRenderConstants.java`）。
- 风险点仅剩行 View 自身 bounds：上浮量必须 clamp 到
  `字形顶(y + fontMetrics.top)` 距 View 顶边的余量内（§5.5）。

### 2.5 配置链路

`LyricUiConfig`（不可变 + Builder，schema v3）→ `LyricUiConfigCodec`
encode/decode（未知 key 容忍，decode 只读已知 key）→
`LyricUiConfigRepository` → `ACTION_STYLE_CHANGED` 定向广播 →
`refreshLyricUiStyleSettingsIfNeeded()` → renderer `uiConfig` 快照。
契约测试 `LyricUiConfigContractTest` 断言 `encode` 的 key 总数
（`FIELDS.size() + 1`），新增字段必须同步更新；
`LyricVisualControlsDocumentationTest` 锁 README/文档字符串，若新增用户可见
视觉控制项需同步文档。Bridge 备份/恢复经 codec map 自动携带新 key。

## 3. 动画模型设计

### 3.1 核心模型：揭示前沿距离驱动的"移动鼓包"（front-distance bump）

不给每个字符单独造时间窗，而是复用已经算出的**揭示前沿**（与 clip-reveal、
羽化、glow 天然同源同步）。前沿与字素中心都取**流坐标**（flow coordinate）：
从该行文本起点累计、跨换行段连续的 advance 宽度，量尺与 `resolveSegmentRevealWidth`
同为 `inactivePaint`。

```
flowFront  = Σ(激活段之前各段 width) + 激活段内 revealWidth
对激活行中每个字素 g（流坐标中心 flowCenter）：
  d      = (flowFront - flowCenter) / bumpWidth   // 有符号归一化距离
  lift   = maxLift * envelope(d) * decay
```

**不能用 canvas x 做前沿坐标。** `resolveSegmentRevealWidth` 对"整段位于激活词
之前"的换行段返回整段宽度（:13955-13959），若每段自算本段前沿，该段的前沿会
钉死在段右缘，其行尾字素 `d ≈ 0` → 包络常驻峰值，只要该行还是激活行就一直悬浮，
只能靠行末 decay 落下——这是行尾常亮式悬浮，不是"换行处按段截断"。另外各段
canvas x 原点随居中/右对齐而不同，段间根本不可比。流坐标两个问题都不存在。

- `envelope(d)`：钟形包络，支持不对称。建议
  - 前沿未到（d < 0，字符在前沿右侧）：`smootherStep(1 + d / RISE_SPAN)`
    —— 前沿逼近时预抬起（RISE_SPAN ≈ 0.6，起得快）；
  - 前沿已过（d ≥ 0）：`1 - smootherStep(d / SETTLE_SPAN)`
    —— 回落（SETTLE_SPAN ≈ 1.4，落得慢）；
  - 两侧在 d=0 处 C¹ 连续（峰值 1）。
- `bumpWidth`：以字号为尺度，建议 `textSize * 1.6f`（覆盖 1~3 个 CJK 字或
  一个中等英文单词的宽度）。
- `maxLift`：建议 `textSize * 0.05f`（32sp 主字号 ≈ 1.6dp），并按 §5.5
  clamp。
- `decay`：行揭示完成后的时基衰减。前沿在行尾停住后鼓包不再移动，若无
  decay 尾部字符会悬停在抬起状态。定义
  `decay = 1 - smoothStep((position - lineRevealEndMs) / LIFT_SETTLE_TAIL_MS)`，
  `LIFT_SETTLE_TAIL_MS = 200L`。行切换（activeLine 变更）直接归零。

选择该模型的理由：
1. 与 clip-reveal / 羽化 / glow 用同一个 `revealWidth`，任何时序修正
   （末词推断、下一行截断、`shouldHoldWordTimedReveal` 起始保持）自动继承，
   不会出现"字亮了但没浮 / 浮了但没亮"的失步；
2. 不同词长、不同语言（CJK 逐字 vs Latin 整词时间戳）无需分支——前沿速度
   本身就编码了节奏；
3. 只有 3 个可调参数（bumpWidth / maxLift / 不对称比），纯函数可单测。

### 3.2 字素切分与坐标

- 字素边界：`java.text.BreakIterator.getCharacterInstance()` 按 grapheme
  切分（正确处理 emoji/代理对/组合字符）；CJK 退化为逐 code point。
- 每个字素的段内 advance 区间 `[gx0, gx1)` 用
  `paint.measureText(text, drawLine.start, boundary)` 前缀宽度差得到，与
  `resolveSegmentRevealWidth` 同一把尺子（`inactivePaint`），保证与前沿坐标
  严格一致。流坐标 `flowCenter = Σ(该段之前各段 width) + (gx0 + gx1) / 2`，
  段前缀和直接取 `LyricDrawLine.width` 累加，无需额外测量。
- **切分与测量结果必须缓存**：按 `(line, drawLine.start, drawLine.end,
  textSizeKey, typeface)` keyed，随 `clearGlowCache` / bind epoch 一起失效。
  绝不能每帧跑 BreakIterator + N 次 measureText。

### 3.3 关键技巧：clip-box + translate 绘制，不逐字符 drawText

不把字符串拆成单字符 `drawText`（会丢 kerning/shaping，Latin 明显）。
对每个需要上浮的字素：

```java
int save = canvas.save();
try {
    canvas.clipRect(gx0 - bleed, top, gx1 + bleed, bottom);   // 该字素的横向盒
    canvas.translate(0f, -lift);
    //（在平移后的坐标系里，用与原来完全相同的 x/y 重画）
    canvas.drawText(text, drawLine.start, drawLine.end, x, y, inactivePaint); // 底色
    drawRevealedText(canvas, text, start, end, x, y, segmentWidth, revealWidth, ...); // 揭示层+羽化
} finally {
    canvas.restoreToCount(save);
}
```

必须写成 `save()` / `restoreToCount()`：`BridgeArchitectureGuardTest`
（:153-154）断言 `LockscreenLyricsModule.java` 不含字面量 `canvas.restore();`，
现有 `drawRevealedText` / `drawProgressGlow` 也是这个写法。

要点：
- 全段字符串按原始 x 绘制、只靠 clip 盒选出该字素 → **字形位置、kerning、
  shaping 与整段绘制完全一致**，字素只是整体位移，不会重排；
- 揭示层在同一个 translate 里重画并沿用同一 `revealWidth` → 半揭示字素
  （前沿正穿过字形中间）的填充边界与字形一起上浮，羽化前沿在浮起字素内
  保持连续；
- `bleed`：横向出血 0.5px 防 AA 接缝；相邻字素 lift 差 <1px 时视觉无缝。

### 3.4 分区绘制

`drawSegment` 的进度分支改为三区：

```
[段起点 …… liftZoneStart) —— 现有整段路径（底色 + glow + clip-reveal）原样
[liftZoneStart … liftZoneEnd) —— §3.3 逐字素 clip+translate 绘制
[liftZoneEnd …… 段终点]   —— 现有整段路径原样
```

- liftZone 在**流坐标**里算：`[flowFront - SETTLE_SPAN*bump, flowFront +
  RISE_SPAN*bump]` 与该段流区间 `[flowSegStart, flowSegEnd)` 求交，再映射回该段
  canvas x（LTR `x + (flow - flowSegStart)`，RTL `x + segmentWidth - (flow -
  flowSegStart)`），最后对齐到字素边界。典型帧内 1~6 个字素走逐字素路径，
  其余 90%+ 文本零额外开销。
- 区 1/3 的整段绘制加横向 clip（排除 liftZone），避免与区 2 重像。
- 激活词跨换行段：`flowFront` 全行只算一次（各段 `flowSegStart + 段内
  revealWidth` 取最大值，`resolveSegmentRevealWidth` 已给出段内 revealWidth），
  鼓包跨换行连续——上一段行尾字素按真实距离继续回落、下一段行首字素抬起，
  两段各自与 liftZone 求交即可，没有段边界钉死问题。
- glow：**保持现状**，`GlowSegmentCache` 位图仍画在固定 baseline。lift ≤ 2dp
  且 glow 为高斯模糊晕影，偏移不可辨；避免为浮起字素破坏位图缓存或每帧跑
  `BlurMaskFilter`。
- RTL：流坐标与书写方向无关，包络与 `d` 的符号都不变；方向只影响"流区间 →
  段内 canvas x"这一层映射（见上）。

### 3.5 触发与帧续命门控

lift 仅在以下全部成立时计算：

1. `activeLine && drawProgress`（后者已含 `!aodLowFrameRateMode`，:13531）；
2. `line.timingMode == WORD_TIMED`（首期不做 line-timed 进度模式，见 §10）；
3. `!shouldUseTimestampHighlight(model, line)`（一词短行整行点亮，无前沿
   可言）；
4. `uiConfig.charLiftEnabled`。

帧续命：现有激活行 invalidate 循环在 `progress >= 1` 后停止，但 decay 尾巴
（§3.1）还需 ~200ms。在 lift 分支内补充：

```
activeLine && (anyLift > 0.01f
        || position < lineRevealEndMs + CHAR_LIFT_SETTLE_TAIL_MS)
```

时 `postInvalidateOnAnimation()`；`activeLine` 为 false 立即停。该条件有硬时限，
不会形成常驻刷新循环。`lineRevealEndMs` 取
`WordLyricRenderSupport.wordRevealEndMillis(model, line, 末词下标)`（含下一行
起点截断），**不能用 `line.endTimeMillis`**——后者含休止/下一行 pre-roll，
decay 会拖过头。

## 4. 纯函数拆分（可单测的新代码）

新增 `render/CharLiftGeometry.java`（纯静态，无 Android 依赖除 BreakIterator）：

所有距离量均为流坐标（§3.1），因此没有 `rightToLeft` 形参；跨度常量由函数内部
绑定，调用方不再传 span，避免 `liftFor` / `liftZone*` 与包络用不同跨度。

| 函数 | 职责 |
| --- | --- |
| `graphemeBoundaries(String text, int start, int end)` | 字素边界 int[]，BreakIterator 封装 |
| `liftEnvelope(float signedDistance)` | §3.1 不对称钟形包络，返回 0..1 |
| `liftFor(float flowFront, float flowCenter, float bumpWidth, float maxLift, float decay)` | 单字素 lift 值 |
| `revealDecay(long position, long lineRevealEndMs, long tailMs)` | 行末衰减 0..1 |
| `clampLift(float lift, float glyphTopY, float topBoundY)` | §5.5 顶边余量 clamp |
| `liftZoneStart/End(float flowFront, float bumpWidth, float flowSegStart, float flowSegEnd)` | 段内浮动流区间交集（`end <= start` 即空） |

常量放 `render/WordLyricRenderConstants.java`（与现有渲染常量同居）：
`CHAR_LIFT_MAX_FACTOR = 0.05f`、`CHAR_LIFT_BUMP_WIDTH_FACTOR = 1.6f`、
`CHAR_LIFT_RISE_SPAN = 0.6f`、`CHAR_LIFT_SETTLE_SPAN = 1.4f`、
`CHAR_LIFT_SETTLE_TAIL_MS = 200L`。

测试放 `app/src/test/java/.../render/CharLiftGeometryTest.java`，覆盖：
包络连续性（d=0 峰值、两侧单调、C¹）、decay 边界、字素切分
（CJK / Latin / emoji / 代理对 / 组合字符）、liftZone 交集空/全覆盖/跨换行两段
同时开区、clamp，以及跨换行段流坐标回归（整段已揭示的段尾字素按真实距离继续
回落，而非钉在段右缘的常驻峰值）。

## 5. Renderer 改造点（`LockscreenLyricsModule` 内）

按现有 Phase 6 风格，改动集中、不新增巨型方法：

### 5.1 `drawSegment`（:13789）
进度分支尾部（现 `drawRevealedText` 调用处）替换为
`drawSegmentWithLift(...)`：lift 关闭或不满足 §3.5 门控时走原路径
（**字节级等价，不重排现有调用顺序**）；开启时按 §3.4 三区绘制。

### 5.2 新私有方法 `drawLiftedGraphemes(...)`
实现 §3.3 循环（`save()` / `restoreToCount()`，不用 `canvas.restore()`）。
所有几何取自 `CharLiftGeometry` 纯函数 + 字素缓存。
异常安全：该方法内 catch 一切 `RuntimeException`，放弃 lift 走整段回退，
**绝不向上抛**——coordinator 的 draw 异常会把整个 binding 拉黑回落原生渲染
（:286-289），一个动画增强不允许触发这个降级。

### 5.3 字素缓存
`GraphemeLayoutCache`（仿 `GlowSegmentCache` 的小型 LRU，2~4 槽，key =
`(line, drawLine.start, drawLine.end, textSizeKey, typeface)`，存字素边界 +
段内前缀宽度），失效时机与 `clearGlowCache()`、`forgetLyricTextViewCaches`
对齐。**禁止每帧跑 BreakIterator 或 N 次 measureText。**

### 5.4 帧续命
§3.5 的补充 invalidate 条件，加在 lift 分支内部（与 :13070 的既有模式一致）。

### 5.5 顶边余量 clamp
`liftClamp = max(0, y + mainFontMetrics.top - viewTopInset)`；lift 取
`min(lift, liftClamp)`。**不改 `resolveSlotHeight` 几何**——改行槽高度会波及
原生 RecyclerView 布局与 AOD 基线，收益（≤2dp）不值。首行贴顶时 lift 自动
压缩为可用余量。

### 5.6 配置消费
renderer 读 `uiConfig.charLiftEnabled` / `charLiftStrengthPercent`，经现有
`refreshLyricUiStyleSettingsIfNeeded()` 快照，无新链路。强度映射
`maxLift = textSize * CHAR_LIFT_MAX_FACTOR * strengthPercent / 100`，再过
`clampLift`（§5.5）。

## 6. 配置与设置 UI

### 6.1 `LyricUiConfig`（schema v3 内追加，不 bump schema）
- 新字段：`charLiftEnabled`（boolean，默认 `false`）、
  `charLiftStrengthPercent`（int，默认 100，范围 0-200，映射 maxLift 缩放；
  首期可只上 enabled，strength 留 Builder 但不出 UI）。
- Builder / `equals` / `hashCode` / `buildUpon` / `defaults` 全套照现有字段
  模式补齐。
- codec：`LyricUiConfigCodec` 新 key `char_lift_enabled` /
  `char_lift_strength_percent`，encode 追加、decode `containsKey` 读取。
  旧版本 decode 新 map 时忽略未知 key（decode 只读已知 key），无迁移逻辑。
- **契约测试**：`LyricUiConfigContractTest` 的 FIELDS 枚举与
  `encoded.size()` 断言同步 +2；`LyricUiConfigTest`、
  `BridgeConfigBackupContractTest` 若枚举字段一并更新。

### 6.2 设置界面
- 开关放 `LyricUiSettingsActivity` 现有"进度/动效"分组（紧邻
  `line_timed_progress_enabled` 开关），复用其 pref 写入 + 定向广播模式；
  key 常量进 `LyricUiSettings`（对应 `KEY_*` = codec key 的现有模式）。
- `strings.xml`（zh + 默认）：标题"字符上浮动画"、摘要"逐字歌词高亮经过时
  字符轻微上浮（Apple Music 风格）。仅对逐字歌词生效，AOD 下自动关闭"。
- 依赖提示：非逐字歌词（LINE_TIMED 未开进度）时开关无效果——照
  `shouldShowTranslationProgressDependencyHint` 的现有提示模式给一行说明，
  不做强制联动。
- 预览：`LyricVisualLayersPreviewView` 首期不加动画预览（预览是静态帧），
  仅文案说明；如后续需要，预览动画单独立项。

### 6.3 备份 / 文档
- Bridge 备份经 codec map 自动携带，无额外代码；降级到旧版本时新 key 被
  忽略（与 schema v3"不承诺无损降级"口径一致）。
- README.md / README.zh-CN.md 视觉控制清单各加一行；
  `docs/4.0/LYRIC-VISUAL-CONTROLS.md` 增补该开关条目与默认值；
  若 `LyricVisualControlsDocumentationTest` 需要锁定新文案，在 Slice D 同步。

## 7. 实施切片

每个切片独立可合、可测、默认零行为变化。

### Slice A：纯函数几何 + 单测（无行为变化）
- 新增 `render/CharLiftGeometry.java`、`WordLyricRenderConstants` 新常量、
  `CharLiftGeometryTest`。
- 出口：`testDebugUnitTest` 全绿；无任何调用方。

### Slice B：配置模型（默认关，无行为变化）
- `LyricUiConfig` + Builder + codec + `LyricUiSettings` key 常量；
  契约测试更新。
- 出口：`LyricUiConfigContractTest` / `LyricUiConfigTest` /
  `BridgeConfigBackupCodecTest` 全绿；renderer 尚未消费。

### Slice C：renderer 消费（开关驱动，默认关 = 逐像素零 diff）
- §5.1-5.6 全部落地。

实施时相对本文的四处调整（已在代码注释里就地说明）：

1. **取消横向出血。** §3.3 原写 0.5px bleed 防 AA 接缝，但底色用的
   `inactivePaint` 带 44% 左右的 alpha，相邻 clip 盒重叠会把同一笔半透明
   墨迹叠两次、把接缝压暗——比它要治的发丝缝更糟。改为字素盒严格相接，
   每列像素只画一次。若设备验证真出现接缝，再单独处理。
2. **底色与揭示层分两趟画**，不是 §3.3 那样一个 clip 里连画两层。glow 在原
   实现里夹在这两层之间，合成一趟会把浮起字素的底色画到 glow 上面，改变叠
   放次序。
3. **AOD 显式门控。** `drawProgress` 并不蕴含 `!aodLowFrameRateMode`——
   `shouldDrawWordProgressForVisual` 在 AOD 填充过渡期间照样为真，所以
   `beginCharLiftLine` 单独判了 `aodLowFrameRateMode`。
4. **`fullLineOverlayAmount > 0.001f` 时整行不浮。** 淡入叠层会把整行未浮
   版本重画一遍，浮起只会拖影。

新增源码契约测试 `CharLiftRendererContractTest`（6 条）：AOD/门控、异常不外抛
且置 `charLiftUnavailable`、decay 锚点、字素几何只经缓存、`clearGlowCache`
一并清理、`drawCompactLine` 不接入。
- 出口（本地）：单测全绿；`assembleDebug` 通过。
- 出口（设备，开关关）：逐字 / line-timed / 翻译 / AOD / 换行长行 五场景
  与基线目视一致，`BridgePerformanceSampler` 的 `TEXT_VIEW_DRAW` 指标无回退。
- 出口（设备，开关开）：QRC/KRC 逐字歌曲上浮效果符合 §3 模型；
  logcat `LockscreenLyrics` 无 draw-error / fallback 事件（coordinator 拉黑
  即为失败）。

### Slice D：设置 UI + 文档
- §6.2 / §6.3 全部落地。
- 出口：开关即时生效（广播链路）；备份导出/导入含新 key；文档测试全绿。

实施记录：开关放在主设置页“兼容性”卡片内，紧邻逐行进度/翻译进度，附一行说明
（开启时才显示）。`docs/4.0/LYRIC-VISUAL-CONTROLS.md` 没有按 §6.3 原话新增
“开关条目与默认值”——那篇文档是“歌词亮度与渐隐”子页的 owner 说明，把一个主页面
开关列进它的字段清单会误导所有权；改为在“不开放的边界”一节说明该开关不属于本页、
其形状参数仍是 renderer 常量。`charLiftStrengthPercent` 按 §6.1 只留在 Builder /
codec，不出 UI。

### Slice E：设备回归矩阵收口（见 §8），完成后在本文件顶部改状态并归档。

## 8. 验证矩阵

### 本地
```powershell
.\scripts\gradle-local.cmd :app:testDebugUnitTest
.\scripts\gradle-local.cmd :app:assembleDebug
```

### 设备（开关开，除非注明）
| # | 场景 | 期望 |
| --- | --- | --- |
| 1 | QRC/KRC 逐字（CJK） | 前沿经过处字符抬起-回落，与高亮/羽化/glow 同步无失步 |
| 2 | 逐字 Latin 整词时间戳 | 词内字符按前沿速度依次浮动，kerning 无跳变 |
| 3 | 激活词跨换行段 | 换行处上一段回落、下一段抬起，无双像/漏画 |
| 4 | 行尾最后一词 | 揭示完成后 ~200ms 内全部落定，无字符悬停、无常驻刷新 |
| 5 | 一词短行（timestamp-highlight） | 无浮动，整行点亮行为与基线一致 |
| 6 | line-timed 歌词（进度开关开/关） | 均无浮动（首期范围外） |
| 7 | 翻译行 | 不浮动，译文进度/marquee 与基线一致 |
| 8 | AOD 低帧率模式 | 无浮动、无额外 invalidate；息屏功耗无回退 |
| 9 | 首行贴顶 | lift 被 clamp，无顶边裁切 |
| 10 | 切歌 / track handoff / fade 窗口 | 无残留 lift 状态、无闪烁；`failedBindings` 无新增 |
| 11 | RTL 歌词 | 浮动方向与现有揭示方向一致（含 RTL） |
| 12 | 开关关 | 与 Slice C 前基线目视 + 性能一致 |
| 13 | 性能 | `TEXT_VIEW_DRAW` p95 相对基线增幅 < 10%；`maxRefreshRateHz` 限帧下动画随限帧降采样而非卡顿 |
| 14 | 备份/恢复 | 新 key 往返一致；旧备份导入默认关 |
| 15 | 行内长停顿（≥500ms） | 字符持续缓慢浮动而非静止悬浮；下一词开始无跳变（前沿在停顿期间本就匀速走完当前词，见下） |
| 16 | 字素接缝（在 #1 CJK / #2 Latin 各盯一次） | 相邻字素 lift 相同时完全无缝；lift 不同时允许 ≤1px 错位，但不得出现发丝亮缝或接缝压暗 |

\#15 的前提已用单测锁定（`WordRevealFrontContinuityTest`）：`WordLine.wordEndMillis`
对非末词返回**下一个词的开始时刻**（:205-206），所以词间停顿会把当前词的揭示拉长
覆盖整个停顿，前沿全程匀速前进、在下一词开始的那一刻恰好走完当前词。全行唯一会
让前沿停住的是末词揭示结束到行切换之间，正是 §3.1 `decay` 锚定的那一段。因此不需要
词级的"停滞衰减/恢复"机制；若将来 `wordEndMillis` 改成按词自身时长收尾、词间留真空档，
该测试会红，那时才需要补。

\#16 是去出血后的接缝质量（§7 Slice C 实施记录第 1 条），取决于 HWUI 对 float
`clipRect` 的像素对齐。

## 9. 风险与回退

| 风险 | 缓解 |
| --- | --- |
| lift 路径异常触发 coordinator 拉黑 → 整行回落原生渲染 | §5.2 内部吞错回退整段路径；设备回归 #10 盯 fallback 日志 |
| 逐字素 clip+draw 帧耗 | 只对前沿 ±bumpWidth 内 1~6 字素生效；字素缓存；#13 性能门 |
| glow 位图与浮起字形 2dp 错位 | 保持 glow 固定 baseline（模糊晕影不可辨）；若设备验证可辨，降 maxLift 而非改缓存 |
| 顶边裁切 | §5.5 clamp，不改 slot 几何 |
| 行尾 decay 忘记停帧 → 常驻刷新 | decay 条件带硬时限；#4/#8 验证 |
| 契约测试漏改导致 CI 红 | Slice B 出口明确列出三个契约测试 |
| 极短词（词距 < `MIN_WORD_REVEAL_MS` 80ms）交接帧前沿小幅前跳 | **现基线行为，不处理**：`wordEndMillis` 的 `max(begin+80, 下一词 begin)` 夹取会越过下一词起点，交接那一帧 `revealWidth` 从部分跳到全揭示。跳变量不足一个字形，lift 随之抖一帧。设备回归遇到极快音节时不要误判为 lift 缺陷 |

回退开关：任何设备回归失败，`charLiftEnabled` 默认值即为总开关；最坏情况
revert Slice C 单提交即可回基线（A/B/D 均为无行为变化或 UI 层）。

## 10. 非目标

- 不引入字符级时间数据、不改 Provider / `lyricInfo` / 解析层；
- 不做水平位移、缩放、模糊等其他字符动效（仅垂直 lift）；
- 不作用于 line-timed 进度模式、翻译行、`drawCompactLine` 紧凑单行变体、
  timestamp-highlight 短行（后续如需另立切片）；
- 不改 AOD 行为、不改 `resolveSlotHeight` / LayoutParams 几何；
- 不 bump `LyricUiConfig` SCHEMA_VERSION、不新增迁移逻辑；
- 不在设置预览里做实时动画预览；
- RTL 歌词的揭示方向沿用现基线（`drawRevealedText` 从 `segmentLeft` 向右 clip，
  视觉左→右），本计划不修正它；lift 跟随该方向以保证与羽化前沿同步。
