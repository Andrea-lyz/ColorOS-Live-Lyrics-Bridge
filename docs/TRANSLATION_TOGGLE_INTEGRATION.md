# 播放器翻译按钮接入约定（Translation Toggle Integration）

锁屏/通知媒体卡固定 5 个槽位（收藏、上一首、暂停/播放、下一首、扬声器切换），因此翻译开关按钮
**不能新增，只能替换该播放器的“收藏”按钮**。本文定义接入该能力的约定，供后续新适配播放器复用。

## 1. 代码分层

- PlayerTranslationTogglePolicy：纯决策（能否替换、是否需要替换），不接触 SystemUI 对象。
- TranslationToggleMediaActionBinder：媒体卡模型操作（定位收藏、原地替换、提升、图标/文案呈现、
  点击绑定），不决策。
- LockscreenLyricsModule：只保留 Hook 入口与运行时状态（翻译显隐记忆、按钮偏好、视图追踪）。

## 2. 收藏定位顺序（SystemUI 侧）

替换动作前先定位该播放器的收藏按钮：

1. PlaybackState.CustomAction 映射（网易云/Apple/汽水/酷狗 Lite 等走此路）；
2. OPlus heart 标记（OplusMediaButtonEx#getHeartAction / MediaActionEx#hasHeartFromRating）；
3. Rule0 自定义动作列表唯一动作兜底（QQ 音乐、原版酷狗实测走此路，其收藏不携带
   PlaybackState.CustomAction）。

定位失败或当前曲目无翻译内容时不替换；替换仅发生在“用户开启该播放器的媒体卡翻译按钮”且
“该播放器是当前歌词 Provider”且“当前曲目有翻译”三者同时成立时。

## 3. Provider 接入要求

- 能提供翻译的 v4 Provider：在直达广播中声明 translationToggle 能力，并持续发送合法的
  translationLyric（带时间戳的 LRC）。
- 拿不到翻译的播放器（Spotify、Metrolist）：不得声明 translationToggle。
- 外部 v4 Provider 不需要播放器进程注入；按钮由 SystemUI 侧替换收藏槽位实现。
- 原生 `lyricInfo` 播放器若收藏不在 CustomAction 首位（Salt、Cone，以及汽水 / Poweramp / LX）：
  在播放器进程的 `MediaSession#setPlaybackState` 把
  `io.github.andrealtb.lockscreenlyrics.action.TOGGLE_TRANSLATION` 插到自定义动作列表首位，
  走公开按钮（public）路径。播放器必须忽略该动作回调。Salt / Cone 的收藏 / 桌面歌词槽位
  不受影响。Poweramp 没有原生收藏 CustomAction：暂停后再切歌时 ColorOS 会按
  `PAUSED → PLAYING` 重建五键行，可见收藏槽变成 OPlus heart，public action 仍留在 Rule0
  却不再显示。Bridge 在 `protocol=public` 之后把 heart **原地**绑成翻译按钮
  （`protocol=public-heart`），不得把 heart 提升进 Rule0，也不得对 Salt / Cone / LX
  做同样的 heart 叠加。
- 4.0 Salt / Cone Provider 使用 `provider-core` 的 `PlaybackStateTranslationToggle`。
  不得把 Bridge `installInjectedTranslationToggleActionHook` 或 Salt/Cone `PlayerAdapter`
  接回去，也不得把 `protocol=salt-legacy`（占用 `com.salt.music.desktop_lyrics`）当主路径。

## 4. 设置项

- “默认显示翻译”与“清除翻译记忆”：控制翻译行显隐（显示链路，独立于按钮）。
- “锁屏媒体卡翻译按钮”（默认开）：开 = 收藏槽位换成翻译开关；关 = 恢复播放器自带收藏。
  存储键 lyric_info_translation_button.<playerPackage>，随
  ACTION_PLAYER_TRANSLATION_SETTINGS_CHANGED 广播以
  EXTRA_TRANSLATION_BUTTON_PACKAGES / EXTRA_TRANSLATION_BUTTON_VALUES 传输。
- 无翻译源播放器（Spotify、Metrolist）：卡片保留用于检测 Provider 安装状态，显示
  “不支持翻译”，不显示任何开关。

## 4.1 已知特殊案例：Apple Music

Apple Music 的收藏是评分式槽位（MediaMetadata USER_RATING/RATING），其渲染图标来自媒体卡
模型之外的来源：模型层图标替换确认生效（MediaAction.icon 与 mediaActionEx.icon 均已写入），
但界面仍渲染播放器自带图标，静态替换不可达。定版决策：图标保留 AM 自带（与翻译功能匹配），
不再强制替换；按钮功能与其余行为正常。

## 5. 调试与验证

- 按钮诊断日志只在 debug 构建存在（TRANSLATION_BUTTON_DIAGNOSTICS_ENABLED = BuildConfig.DEBUG），
  必须安装 assembleDebug 产物并抓 LockscreenLyrics Tag。
- 关键日志行：inspect Rule0 actions、override fallback located ...、configure translation
  action、bind OPlus heart alongside public translation action、Configured lyricInfo
  translation toggle（`protocol=public` / `public-heart`）、no translation action candidate。
  Poweramp 暂停后再切歌应同时看到 `heart=` 非 null 与 `protocol=public-heart`。
- 真机验证矩阵：开关开 → 收藏槽位变为翻译按钮（日志确认替换的是收藏而非扬声器切换）、点击切换
  翻译行、重启保持；开关关 → 收藏恢复、无翻译按钮；扬声器切换全程不受影响。
- Poweramp 验收：首次播放有按钮；播放中连切按钮仍在；暂停一次再切歌，下一首必须直接出现按钮，
  不必再暂停刷新。暂停时歌词/进度仍停住（无延迟 `setPlaybackState`）。
- Salt / Cone 验收：debug Bridge 日志应为 `hasPublicAction=true` 且
  `configure translation action … protocol=public`。不得把 `protocol=salt-legacy` 当作主路径，
  也不得出现 `protocol=public-heart`。
  Provider 侧应出现一次 `event=TRANSLATION_ACTION_INJECTED reason=public`。
