# 逐字同步与普通逐行进度的字符上浮动画（Salt 式 Float-Up 阶跃模型）改造计划

状态：**实施中（分支 `feat/word-sync-char-lift`）。Slice A–D + F + F.1 已合；
Slice G 已落源码、契约测试与文案，`testDebugUnitTest` / `lintDebug` /
`assembleDebug` 已通过，待用户按 §8 #21/#22 实机验收。**

模型经历三次修订：2026-09-11 评审把 canvas-x 前沿改为流坐标（换行段尾字素
常驻峰值）；实机测试后整个动画模型由钟形鼓包改为 Salt Player 式阶跃
（Slice F，原因见 §3.1 决策记录）；2026-09-12 实机 trace 发现全行前沿的
"各段取最大值"公式把换行行的前沿钉在首段末端，改为各段揭示宽度求和
（Slice F.1，见 §3.2 与 §7）。

启动门槛：当前 renderer 处于 Phase 6 之后的稳定基线（`OfficialLyricTextRenderer`
仍在 `LockscreenLyricsModule` 内、`OfficialLyricDrawCoordinator` 已收口 draw 编排）。
本计划所有切片默认关闭新效果，零改动路径必须与现基线逐像素一致。

---

## 1. 目标

在 Bridge 侧逐字（word-timed）高亮与普通逐行（line-timed）进度的基础上，复刻 Salt
Player 的字符 Float-Up：
**未唱字符沉在正常基线下方，被揭示前沿经过时沿升余弦升到正常基线并停在那里**。
要求：

- 与现有逐字/普通逐行揭示（clip-reveal + 羽化前沿 + glow）完全同步，共用同一个进度时钟；
- 已唱/未唱的高度差是常驻状态而非瞬时动画，前沿再快也读得出来；
- 纯视觉增强，**不需要任何新的歌词时间数据**——逐字歌词沿用 `WordRange` 词级
  时间戳，普通逐行歌词沿用既有行起点与 display-end；字符级相位由当前揭示前沿的
  几何插值得出；
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
- 下沉方向的障碍是不随主行下沉的翻译行：`drawTranslationPass`（:12993）把翻译
  字形外顶边放在 `state.top + state.mainHeight + state.translationGap`，而
  `translationGap` 只有 2dp。沉底量必须 clamp 在这条线之上（§5.5）。

### 2.5 配置链路

`LyricUiConfig`（不可变 + Builder，schema v3）→ `LyricUiConfigCodec`
encode/decode（未知 key 容忍，decode 只读已知 key）→
`LyricUiConfigRepository` → `ACTION_STYLE_CHANGED` 定向广播 →
`refreshLyricUiStyleSettingsIfNeeded()` → renderer `uiConfig` 快照。
契约测试 `LyricUiConfigContractTest` 断言 `encode` 的 key 总数
（`FIELDS.size() + 1`），新增字段必须同步更新；
`LyricVisualControlsDocumentationTest` 锁 README/文档字符串，若新增用户可见
视觉控制项需同步文档。Bridge 备份/恢复经 codec map 自动携带新 key。

## 3. 动画模型设计（Salt 式 Float-Up 阶跃）

### 3.1 核心模型：未唱沉底、已唱归位的升余弦阶跃

复刻 Salt Player 的 `LyricsLinePosition.floatUps`（取证见
`PlayerSource/SaltPlayer/SALT-LYRICS-FLOAT-ANIMATION-REPORT.md` §3 与
`wb1.m8152`）。前沿与字素中心仍取流坐标（定义见 §3.2）：

```
S      = inactivePaint.getTextSize()
H      = S * CHAR_LIFT_MAX_FACTOR(0.10) * charLiftStrengthPercent / 100
W      = S * CHAR_LIFT_WAVE_WIDTH_FACTOR(3.0)        // 过渡窗口总宽
u_i    = (flowCenter_i - flowFront + W/2) / W        // 窗口归一化坐标
w(u)   = 1                     (u <= 0，已唱)
       | 0                     (u >= 1，未唱)
       | (1 + cos(u*pi)) / 2   (过渡)
sink_i = H * (1 - w(u_i)) * sinkRamp
```

绘制时字素 `translate(0, +sink_i)`：未唱字符停在正常基线**下方** H，被前沿
经过时沿升余弦升到正常基线并**永久停在那里**。

**为什么换掉原来的钟形鼓包（决策记录）。** 初版用"抬起→回落"的不对称钟形包络，
已唱与未唱字符最终都回到同一基线，动画只有过程没有状态。实机反馈"逐字进度一快
就没动画"：前沿快时每个字素的起落只占几帧，肉眼来不及看见，而任何时刻的静态画面
里已唱与未唱毫无高度差。阶跃模型的高度差是常驻状态，与前沿速度无关，rap 段落
同样可读。次因是幅度：原 5% 太小，Salt 默认 10%。

用 `cos` 而不是 `smoothStep`，与 Salt 手感一致。

### 3.2 字素切分与坐标

- 字素边界：`java.text.BreakIterator.getCharacterInstance()` 按 grapheme
  切分（正确处理 emoji/代理对/组合字符）；CJK 退化为逐 code point。
- 每个字素的段内 advance 区间 `[gx0, gx1)` 用
  `paint.measureText(text, drawLine.start, boundary)` 前缀宽度差得到，与
  `resolveSegmentRevealWidth` 同一把尺子（`inactivePaint`），保证与前沿坐标
  严格一致。流坐标 `flowCenter = Σ(该段之前各段 width) + (gx0 + gx1) / 2`，
  段前缀和直接取 `LyricDrawLine.width` 累加，无需额外测量。
- **不能用 canvas x 做前沿坐标。** `resolveSegmentRevealWidth` 对"整段位于激活词
  之前"的换行段返回整段宽度（:13955-13959），若每段自算本段前沿，该段前沿会钉死
  在段右缘。各段 canvas x 原点还随居中/右对齐不同，段间不可比。
- **全行前沿是各段揭示宽度的求和，不是 `max(flowSegStart + 段内 revealWidth)`。**
  `CharLiftGeometry.accumulateFlowFront` 逐段累加 `min(revealWidth, width)`。
  取最大值的写法看似等价，实际不成立：前沿之后的段 revealWidth 为 0，但它的
  `flowSegStart` 等于前面所有段的总宽，`max` 会把前沿顶到前一段末端。实机表现
  是换行行的前两内行只有最后一两个字母（过渡窗口末端）在动，只有末段整段有波浪
  （2026-09-12 Anti-Hero trace：首段 reveal 40→862px 期间 flowFront 恒为 936 =
  首段宽）。回归测试 `flowFrontFollowsThePartialRevealOfTheFirstWrappedLine`。
- 切分与测量结果必须缓存（`GraphemeLayoutCache`），绝不能每帧跑 BreakIterator。

### 3.3 关键技巧：clip-box + translate 绘制，不逐字符 drawText

不把字符串拆成单字符 `drawText`（会丢 kerning/shaping）。对过渡区每个字素：

```java
int save = canvas.save();
try {
    canvas.clipRect(gx0, top, gx1, bottom);   // 该字素的横向盒
    canvas.translate(0f, +sink_i);
    //（在平移后的坐标系里，用与原来完全相同的 x/y 重画）
    canvas.drawText(text, drawLine.start, drawLine.end, x, y, inactivePaint);
} finally {
    canvas.restoreToCount(save);
}
```

要点：
- 全段字符串按原始 x 绘制、只靠 clip 盒选出该字素 → 字形位置、kerning、shaping
  与整段绘制完全一致，字素只是整体位移；
- 必须写成 `save()` / `restoreToCount()`：`BridgeArchitectureGuardTest`
  （:153-154）断言 `LockscreenLyricsModule.java` 不含字面量 `canvas.restore();`；
- **不加横向出血**：底色 `inactivePaint` 带 alpha，相邻 clip 盒重叠会把同一笔
  半透明墨迹叠两次、把接缝压暗，比它要治的发丝缝更糟。字素盒严格相接。

### 3.4 三区绘制

```
[段起点 …… front-W/2)   已唱：sink=0，走现有整段路径，canvas 不动
[front-W/2 … front+W/2) 过渡：逐字素 clip + translate(0,+sink_i)
[front+W/2 …… 段终点]   未唱：sink 恒为 H，整段一次 clip + translate(0,+H)
```

- 尾区不逐字素：过渡窗口之后每个字素位移完全相同，一次 `drawText` 即可。
- 揭示层只存在于头区与过渡区——前沿是窗口中心，窗口之后没有任何已揭示内容。
- 底色与揭示层分两趟画，不是一个 clip 里连画两层：glow 在原实现里夹在这两层
  之间，合成一趟会改变叠放次序。
- 头区整段已唱时 `sink=0`，此时整个 segment 走原路径 → **行唱完后的稳态与关闭
  效果逐像素一致**。
- 各段流区间与过渡区求交、对齐字素边界的逻辑见 `prepareCharLiftZone`。
- 换行段之间不需要防重叠：任一时刻至多一个段含过渡区，它上面的段全部已唱
  （sink=0）、下面的段全部未唱（sink=H），下沉方向一致，相对位置不变。
- glow：**保持现状**，`GlowSegmentCache` 位图仍画在固定 baseline。
- RTL：流坐标与书写方向无关；方向只影响"流区间 → 段内 canvas x"的映射，而该
  映射沿用 `drawRevealedText` 既有的左→右揭示方向（§10）。

### 3.5 行首下沉与行尾收尾

两个纯函数补足模型两端，都是 `position` 的函数，无每行状态：

- **下沉斜坡** `sinkRamp = smoothStep((position - line.timeMillis) / CHAR_LIFT_SINK_IN_MS)`
  （240ms）。否则行激活瞬间整行未唱文本会突然出现在下方。行在
  `line.timeMillis` 之后才激活时 ramp 已到 1、直接沉底，可接受——行激活本身带
  0.9→1.0 缩放与滑动，会吸收这点。
- **收尾推进** `finishOvershoot = W/2 * smoothStep((position - lineRevealEnd) / CHAR_LIFT_FINISH_MS)`
  （240ms），加到 `flowFront` 上。**这一条是本计划相对 Salt 的必要补充**：前沿停在
  行尾时，最后约 1.5 个字号的字符仍落在过渡窗口内，会永久停在半沉位置。Salt 的行
  会滚走所以不暴露，我们的行会一直停着。推进半个窗口后全行归位，稳态才真正等于
  §3.4 说的"与关闭效果逐像素一致"。`lineRevealEnd` 由
  `resolveCharLiftLineRevealEnd(model, line)` 按 timing mode 取值：逐字歌词仍用
  `WordLyricRenderSupport.wordRevealEndMillis(model, line, 末词下标)`（含下一行起点
  截断）；开启进度的普通逐行歌词改用 `resolveLineDisplayEndMillis(model, line)`，与其
  线性揭示的终点完全一致。

### 3.6 触发与帧续命门控

sink 仅在以下全部成立时计算：

1. `activeLine && drawProgress && !aodLowFrameRateMode`——`drawProgress` 并不
   蕴含非 AOD：`shouldDrawWordProgressForVisual`（:13549）在
   `aodLineFillAmount < 0.999f` 时即使低帧率模式也返回真，必须单独判；
2. `line.timingMode == WORD_TIMED`，或 `line.timingMode == LINE_TIMED` 且
   `lineTimedProgressEnabled`，并且 `words` 非空；其余 timing mode 仍直接 return；
3. `!shouldUseTimestampHighlight(model, line)`；
4. `fullLineOverlayAmount <= 0.001f`（淡入叠层会重画整行未位移版本，浮起只会拖影）；
5. `uiConfig.charLiftEnabled` 且 `maxSink > 0`。

**不要求 `activeWord != null`**：首词开始前整行未唱，必须已经沉底，否则首词一响
整行会突然下坠。此时 `flowFront = 0`（前沿停在文本起点）。

帧续命：sink 是 `position` 的纯函数，揭示进行中已有 `progress < 1` 的 invalidate
循环，本效果只需额外负责两段有硬时限的窗口：
`lineActive && (sinkRamp < 1 || 仍在 finishOvershoot 窗口内)`。唱完的行完全静止。


## 4. 纯函数拆分（可单测的新代码）

新增 `render/CharLiftGeometry.java`（纯静态，无 Android 依赖除 BreakIterator）：

所有距离量均为流坐标（§3.1），因此没有 `rightToLeft` 形参；跨度常量由函数内部
绑定，调用方不再传 span，避免 `liftFor` / `liftZone*` 与包络用不同跨度。

| 函数 | 职责 |
| --- | --- |
| `graphemeBoundaries(String text, int start, int end)` | 字素边界 int[]，BreakIterator 封装 |
| `floatWeight(float u)` | §3.1 升余弦权重，1=已升到基线，0=沉底 |
| `waveCoordinate(float flowFront, float flowCenter, float waveWidth)` | 字素在过渡窗口内的归一化坐标 u |
| `sinkFor(float flowFront, float flowCenter, float waveWidth, float maxSink, float ramp)` | 单字素相对正常基线的下沉像素 |
| `sinkRamp(long position, long lineBeginMillis, long sinkInMillis)` | 行激活时的下沉斜坡 0..1 |
| `finishOvershoot(long position, long lineRevealEndMillis, long finishMillis, float waveWidth)` | 行尾把波形推离文本末端的额外前沿距离 |
| `clampSink(float sink, float glyphBottomY, float floorY, float minClearance)` | §5.5 底部余量 clamp |
| `waveZoneStart/End(float flowFront, float waveWidth, float flowSegStart, float flowSegEnd)` | 段内过渡区流区间交集（`end <= start` 即空） |
| `accumulateFlowFront(float frontSoFar, float segmentRevealWidth, float segmentWidth)` | 全行前沿逐段求和（§3.2，禁止 max） |

常量放 `render/WordLyricRenderConstants.java`（与现有渲染常量同居）：
`CHAR_LIFT_MAX_FACTOR = 0.10f`（Salt `floatUpPercentage` 默认值）、
`CHAR_LIFT_WAVE_WIDTH_FACTOR = 3.0f`（Salt 窗口宽）、
`CHAR_LIFT_SINK_IN_MS = 240L`、`CHAR_LIFT_FINISH_MS = 240L`、
`CHAR_LIFT_MIN_CLEARANCE_DP = 1f`。

测试放 `app/src/test/java/.../render/CharLiftGeometryTest.java`，覆盖：
`floatWeight` 与 Salt 公式 `(1+cos(uπ))/2` 逐点一致（步长 0.01）及 u≤0 / u≥1 /
u=0.5 边界、单调性；sink 在头区为 0、尾区为 H、过渡区单调；`sinkRamp` 与
`finishOvershoot` 的边界与单调性；`clampSink`；字素切分（CJK / Latin / emoji /
代理对 / 组合字符）；waveZone 交集空/全覆盖/跨换行两段同时开区；以及"已唱字符
必须停在正常基线"的回归（旧鼓包模型下会有非零位移）。

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

### 5.5 底部余量 clamp
下沉的障碍是不随主行下沉的翻译行。`clampSink(sink, y + inactivePaint.descent(),
floorY, 1dp)`，其中 `floorY` 在 group 布局阶段算出：有翻译时
`state.top + state.mainHeight + state.translationGap`（即 `drawTranslationPass`
放置翻译外顶边的位置），无翻译时取 canvas 底。**不改 `resolveSlotHeight` /
LayoutParams 几何**——改行槽高度会波及原生 RecyclerView 布局与 AOD 基线。
换行段之间不需要 clamp，理由见 §3.4。若实机发现有翻译时幅度被压得没意义，
再议是否为激活的 word-timed 行加 H 的 `translationGap`（那会动 slot 几何，
本轮不做）。

### 5.6 配置消费
renderer 读 `uiConfig.charLiftEnabled` / `charLiftStrengthPercent`，经现有
`refreshLyricUiStyleSettingsIfNeeded()` 快照，无新链路。强度映射
`maxSink = textSize * CHAR_LIFT_MAX_FACTOR * strengthPercent / 100`，再过
`clampSink`（§5.5）。

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
- `strings.xml`（zh + 默认）：标题"字符上浮动画"；提示明确逐字歌词可用，普通
  逐行歌词需要同时开启"普通逐行歌词进度"，AOD 低帧率下自动关闭。
- 依赖提示：不新增或强制联动设置；字符上浮总开关与既有普通逐行歌词进度开关共同
  决定 LINE_TIMED 是否启用。
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

新增源码契约测试 `CharLiftRendererContractTest`（8 条）：AOD/门控、普通逐行模式
受 `lineTimedProgressEnabled` 约束、LINE_TIMED 的 reveal-end 走 display-end、异常不外抛
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

### Slice F：按实机反馈重做动画模型（Salt 式阶跃）

触发：Slice A–D 装机实测后反馈"逐字进度一快就没动画"。根因与决策见 §3.1。

- `CharLiftGeometry` 换模型：`floatWeight` / `waveCoordinate` / `sinkFor` /
  `sinkRamp` / `finishOvershoot` / `clampSink` / `waveZoneStart|End`；
  删除钟形 `liftEnvelope` / `liftFor` / `revealDecay` / `clampLift` /
  `liftZoneStart|End` 及 `CHAR_LIFT_BUMP_WIDTH_FACTOR` /
  `CHAR_LIFT_RISE_SPAN` / `CHAR_LIFT_SETTLE_SPAN` / `CHAR_LIFT_SETTLE_TAIL_MS`。
- renderer：三区改为头/过渡/尾（尾区一次整段 translate），门控放开
  `activeWord == null`（pre-roll 沉底），顶边 clamp 换成底边 clamp，
  续帧条件换成下沉斜坡与收尾窗口。
- 设置页补 `charLiftStrengthPercent` 滑块（0–200，默认 100，仅开关打开时显示）。
- **相对评审规格的一处必要补充**：`finishOvershoot`。评审认为"已唱 sink=0 是稳定
  终态、行末不再有衰减问题"，但前沿停在行尾时最后约 1.5 个字号的字符仍在过渡窗口
  内、永久半沉；单测 `aFullySungSegmentStandsStillAtTheBaseline` 抓到了这一点。
- 出口：全量单测 + `lintDebug` + `assembleDebug` 绿；设备按 §8 复跑，重点 #19。

### Slice F.1：换行行前沿钉死修复（实机 trace 驱动）

触发：Slice F 装机后反馈"同一 item ≥2 内行时只有最后一内行有动画，前面的内行
只有尾巴一两个字母在动"。

- 取证：在 `drawSegment` 临时加逐段 WARN trace（已移除），Anti-Hero 两段行
  936/522px：首段 reveal 40→862px 全程 `flowFront=936`，过渡区钉在 782–936。
- 根因：`resolveLineFlowFront` 用 `max(front, flow + revealWidth)`，后续未唱段
  `flow + 0` 等于前面各段总宽。§3.4 原评审公式本身错误。
- 修复：`CharLiftGeometry.accumulateFlowFront` 逐段求和；renderer 改用之；
  `charLiftUnavailable` 被置位处补 `CHAR_LIFT_UNAVAILABLE` WARN（原为静默吞错，
  取证盲区）。
- 验证：修复后 trace 中首段 `flowFront == reveal` 逐帧相等、过渡区从段首扫到段末；
  三段行中间段在前沿逼近换行处时首字预抬；用户实机确认。

### Slice G：扩展到普通逐行歌词进度

触发：Salt Player 式阶跃模型已在逐字歌曲实机验收；普通逐行歌词在开启"普通逐行歌词
进度"时已复用同一条整行线性 reveal 管线，故只放开同一动画的适用范围，不改变模型、
幅度、时序或设置 schema。

- `beginCharLiftLine` 继续允许 `WORD_TIMED`；新增 `LINE_TIMED &&
  lineTimedProgressEnabled` 分支，其余 timing mode、空 `words`、AOD 低帧率、淡入叠层等
  原有门控不变。
- 不新增设置开关：普通逐行歌词的上浮仅在"字符上浮动画"与"普通逐行歌词进度"都开启时
  生效；关闭任一开关均保持既有渲染。
- 新私有 `resolveCharLiftLineRevealEnd(model, line)` 明确分叉：`WORD_TIMED` 保持
  `wordRevealEndMillis`；`LINE_TIMED` 取 `resolveLineDisplayEndMillis`，与
  `resolveLineElapsedProgress` 的线性揭示终点一致。
- **可接受差异（不改映射）**：普通逐行歌词的 reveal 恰在下一行起点达到 1；末段最后约
  1.5 个字号在行切换前仍会处于过渡窗口，随后由既有行切换动画吸收。若提前推进或改写
  reveal 映射，会使 lift 与羽化前沿失步，因此本切片不处理。
- 契约测试锁定 LINE_TIMED 的 `lineTimedProgressEnabled` 门控与 display-end 分叉；中英文
  设置提示、README 和视觉控制文档同步说明依赖关系。
- 出口：`testDebugUnitTest`、`lintDebug`、`assembleDebug` 全绿；设备按 #21/#22 验收，且
  逐字歌曲的既有上浮行为不变。

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
| 4 | 行尾最后一词 | 揭示完成后 ~240ms 内全行归位到正常基线，无半沉残留、无常驻刷新 |
| 5 | 一词短行（timestamp-highlight） | 无浮动，整行点亮行为与基线一致 |
| 6 | 普通逐行歌词（进度开关开/关） | 开：随整行线性揭示前沿上浮；关：无上浮、保持既有整行状态 |
| 7 | 翻译行 | 不浮动，译文进度/marquee 与基线一致 |
| 8 | AOD 低帧率模式 | 无浮动、无额外 invalidate；息屏功耗无回退 |
| 9 | 首行贴顶 | lift 被 clamp，无顶边裁切 |
| 10 | 切歌 / track handoff / fade 窗口 | 无残留 lift 状态、无闪烁；`failedBindings` 无新增 |
| 11 | RTL 歌词 | 浮动方向与现有揭示方向一致（含 RTL） |
| 12 | 开关关 | 与 Slice C 前基线目视 + 性能一致 |
| 13 | 性能 | `TEXT_VIEW_DRAW` p95 相对基线增幅 < 10%；`maxRefreshRateHz` 限帧下动画随限帧降采样而非卡顿 |
| 14 | 备份/恢复 | 新 key 往返一致；旧备份导入默认关 |
| 15 | 行内长停顿（≥500ms） | 字符持续缓慢浮动而非静止悬浮；下一词开始无跳变（前沿在停顿期间本就匀速走完当前词，见下） |
| 16 | 字素接缝（在 #1 CJK / #2 Latin 各盯一次） | 相邻字素位移相同时完全无缝；位移不同时允许 ≤1px 错位，但不得出现发丝亮缝或接缝压暗 |
| 17 | 行激活瞬间 | 未唱文本在 240ms 内平滑沉到下方，首词开始无突跳 |
| 18 | 有翻译行 | 未唱主行字形不与翻译行字形重叠；若幅度被 clamp 到几乎不可见，记录实测值 |
| 19 | 快速 rap 段（<120ms/字） | 已唱/未唱仍有清晰可辨的高度差（本次重做的验收点） |
| 20 | 强度滑块 0 / 100 / 200 | 0 等同关闭；200 幅度约为字号 20%，无裁切、无与翻译行重叠 |
| 21 | 逐行歌词单段行 | 前沿线性扫过；已唱/未唱高度差常驻 |
| 22 | 逐行歌词多段行含滑动窗口 | 窗口切换时 lift 不错位、不闪 |
| 23 | 背靠背行（行间无空档） | 行切换瞬间末尾字符不得出现可见弹跳 |

\#23 的成因：`wordRevealEndMillis`（`WordLyricRenderSupport.java:103-118`）对末词
用 `Math.min(end, 下一行 timeMillis)` 截断。两行背靠背时 `lineRevealEnd` 等于下一行
开始，§3.5 的收尾窗口刚要开始本行就转为非激活行、按正常基线绘制，末尾 1~2 个字从
半沉直接回到基线，收尾动画一帧都没播。行间有空档时不受影响。该弹跳（≤H/2，32sp 下
约 1.6px）与行切换本身的 1.0→0.9 缩放和滑动同帧发生，大概率被吸收，故先只验证不改。

若实机可见，备选修法是把收尾提前到末词内部：overshoot 窗口改为
`[lineRevealEnd - min(CHAR_LIFT_FINISH_MS, 末词揭示时长/2), lineRevealEnd + CHAR_LIFT_FINISH_MS]`，
仍用 smoothStep 从 0 推到 W/2——末词后半段波形就开始加速推离文本末端，行尾时刻已到位；
代价是末词字符略早于揭示上浮。

另一项同样只作备选的对称打磨：若行首 1.5 字号的半浮（§3.6，按 Salt 保留）显得突兀，
可在首词前 240ms 让 `flowFront` 从 `-W/2` 平滑推到 0（锚点
`words[0].timeMillis - CHAR_LIFT_SINK_IN_MS`，同样无状态），效果是波形"从左侧到达"。
两项都等实机反馈再决定。

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
| 有翻译行时 2dp `translationGap` 把沉底量压到不可见 | §5.5 clamp 保证不重叠；#18 实测幅度，必要时再议 slot 几何（本轮不做） |
| 极短词（词距 < `MIN_WORD_REVEAL_MS` 80ms）交接帧前沿小幅前跳 | **现基线行为，不处理**：`wordEndMillis` 的 `max(begin+80, 下一词 begin)` 夹取会越过下一词起点，交接那一帧 `revealWidth` 从部分跳到全揭示。跳变量不足一个字形，lift 随之抖一帧。设备回归遇到极快音节时不要误判为 lift 缺陷 |

回退开关：任何设备回归失败，`charLiftEnabled` 默认值即为总开关；最坏情况
revert Slice C 单提交即可回基线（A/B/D 均为无行为变化或 UI 层）。

## 10. 非目标

- 不引入字符级时间数据、不改 Provider / `lyricInfo` / 解析层；
- 不做水平位移、缩放、模糊等其他字符动效（仅垂直位移）；
- 不做 Salt 的单字离屏位图缓存（`CharacterBitmapCache`）：我们复用整段
  `drawText` + clip 盒，shaping 由 HWUI 自己缓存，不需要这一层；
- 不作用于 line-timed 进度模式、翻译行、`drawCompactLine` 紧凑单行变体、
  timestamp-highlight 短行（后续如需另立切片）；
- 不改 AOD 行为、不改 `resolveSlotHeight` / LayoutParams 几何；
- 不 bump `LyricUiConfig` SCHEMA_VERSION、不新增迁移逻辑；
- 不在设置预览里做实时动画预览；
- RTL 歌词的揭示方向沿用现基线（`drawRevealedText` 从 `segmentLeft` 向右 clip，
  视觉左→右），本计划不修正它；lift 跟随该方向以保证与羽化前沿同步。
