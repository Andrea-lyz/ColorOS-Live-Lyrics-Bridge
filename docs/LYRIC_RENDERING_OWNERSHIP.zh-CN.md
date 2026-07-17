# 锁屏歌词渲染职责边界

本文件记录 ColorOS 16 官方实现与模块实现的职责划分，避免后续通过叠加滚动补丁修复时序问题。

## 官方实现基线

审查范围：

- `SystemUIPlugin_16.001.002` 中的 `LyricsRecyclerView`、歌词 Adapter、行状态与沉浸页控制器。
- `系统界面_16.99.12` 中的媒体状态、歌词位置更新与插件调用链。
- 官方歌词 item、沉浸页布局及对应尺寸资源。

官方链路的关键行为：

1. `setLyrics` 更新 Adapter 数据、回到位置 0，并把当前索引复位为 -1。
2. 播放位置由官方控制器统一传入 `LyricsRecyclerView.l(animate, position)`。
3. 亮屏时由官方复用的 `LinearSmoothScroller` 完成定位；AOD 或禁止动画时使用官方固定偏移直接定位。
4. 进入沉浸歌词页时，官方先以当前播放位置执行一次无动画定位，再运行整个页面的出现动画。
5. item 使用 `wrap_content`；行间距由 Adapter 的 bottom margin 提供。

## 模块职责

| 能力 | 所有者 | 说明 |
|---|---|---|
| Adapter 数据、通知、绑定与回收 | SystemUI | 模块只观察绑定后的歌词 TextView，不拦截或吞掉 Adapter notify。 |
| 当前索引、seek 定位、平滑滚动 | SystemUI | 亮屏阶段模块原样放行官方 `l(animate, position)`，不改写动画参数，不另行调用私有当前行方法、停止滚动或强制偏移。自定义活动态以所属 RecyclerView 的官方 `n` 为准；只有 `n` 尚未建立时才用播放进度兜底。 |
| 页面可见性与沉浸页进出动画 | SystemUI | 模块不得取消 `ViewPropertyAnimator`，也不得写 RecyclerView 的 alpha、visibility 或 translation。 |
| 逐字、颜色、光晕、模糊、字号、字重、文本对齐 | 模块 | 全部限制在歌词 RecyclerView 内的自定义像素绘制；不得改变 RecyclerView 的当前索引和滚动事务。 |
| 活动/非活动行物理缩放 | SystemUI | 模块只把用户的 75%–100% 配置写入官方非活动缩放字段，并按左/中/右对齐修正官方缩放 pivot；实际缩放、取消和动画仍由官方状态机完成，Canvas 不再叠加第二套缩放。 |
| 字号、翻译与内容决定的行高 | 模块 | 在绑定、配置或统一翻译布局阶段更新，不在 `onDraw` 中改 LayoutParams。 |
| 自定义行间距 | 模块 | 通过官方 Adapter 间距字段及已绑定 item margin 应用。 |
| AOD 首次 prime | 模块兜底 | 保留已验证的一次性低动态路径，不把它扩展到亮屏。 |

## 允许的结构例外

- AOD/非交互屏幕保留现有一次性 prime 和稳定化逻辑，以维持已验证基线。
- 用户主动展开或收起翻译时，允许在统一行高动画中做小范围锚点补偿；它不能复用于 seek、界面切换或普通逐行更新。
- 页面交叉淡入淡出时，RecyclerView 注册引用可跨越短暂 detach 保留，但刷新快照只返回当前已附着实例；TextView 父级缓存和活动刷新目标必须重新验证当前祖先确属同一 RecyclerView。
- 如果缓存的歌词 TextView 全部属于已隐藏表面，允许从当前已附着的歌词 RecyclerView 重新发现已绑定行；该恢复只更新绘制引用和 invalidate，不改变索引、滚动或 AOD prime 时序。
- SystemUI 会在 RecyclerView `alpha=0` 时预先绑定和绘制新页面。此时模块仍须生成完整自定义 display list；有效可见性只控制刷新频率和常亮状态，不控制渲染所有权。
- 任意绑定行在自定义模型有效期间都保持自定义渲染所有权；瞬时匹配失败只能留空并在当前绑定 epoch 内安排一次下一帧重建，禁止同一行在自定义与官方绘制之间逐帧切换。adapter position 仅用于消歧重复歌词，唯一文本不能因 RecyclerView pre-layout 的瞬时位置错位而被拒绝。
- RecyclerView attach、其自身重新可见和模型提交均安排一次最多 6 帧/120ms 的纯像素 surface render pass。不得通过追踪祖先 View 的 visibility 重复启动该 pass。handoff 只有在当前 surface 的全部可映射绑定行都已为同一模型、RecyclerView、绑定 epoch 和文本生成自定义帧后才可提前结束；否则沿用既有超时兜底。
- 异常恢复只能更新绘制引用并 invalidate，不得 request layout、重写行高、修改亮屏 RecyclerView 的当前索引或滚动位置。内容行高只在 bind、配置变更、模型提交或用户主动切换翻译时更新。
- 亮屏切歌的新模型只允许在槽位几何提交后对模块自绘像素执行一次淡入；不得通过 RecyclerView `alpha` 或 `ViewPropertyAnimator` 实现。关闭动效和 AOD 路径不执行该淡入。

## 诊断要求

调试日志中的 `LyricsRecyclerView ownership snapshot` 应满足：

- 亮屏时 `owner=systemui`。
- AOD/非交互屏幕才允许 `owner=module-aod-fallback`。
- 解锁界面进入歌词页及 seek 后，`officialIndex`、`children`、`first`、`last` 应由官方 `setCurrentLyric` 链路自然收敛。
- 亮屏日志中不应再出现模块 `Primed LyricsRecyclerView`、`Force-aligned LyricsRecyclerView` 或 `Stabilized LyricsRecyclerView scroll`。

涉及 AOD 的后续修改仍需按照项目 `AGENTS.md` 中的 AOD 验证边界比较 attachment、prime、slot height、row scale、scroll 和 current lyric geometry。
