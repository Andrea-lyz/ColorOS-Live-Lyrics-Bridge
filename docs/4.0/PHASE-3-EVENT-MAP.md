# Bridge 4.0 Phase 3 legacy log → [CLL] event map

新格式固定为：

```text
[CLL] level=INFO component=bridge area=aod event=RECYCLER_ATTACHED process=com.android.systemui message="Observed LyricsRecyclerView attachment"
```

旧句保留在 `message=` 中，AOD 对照仍可用原文检索。`event` 是稳定类型，不再用整句充当事件名。

## 1. Bridge 标准事件

| event | area | 何时 |
|---|---|---|
| `SYSTEMUI_BOOTSTRAP` | bootstrap | 模块加载、DexKit 解析、hook 装配、receiver 注册 |
| `HOOK_INSTALLED` | bootstrap | `Hooked ...` |
| `HOOK_FAILED` | bootstrap | hook 安装失败（ERROR） |
| `DEBUG_CONFIG_APPLIED` | bootstrap | Debug 配置被 SystemUI 读取；相同配置的 1.5s UI 轮询不得重复宣布 |
| `SETTINGS_APPLIED` | bootstrap | 外观/翻译/清理设置应用 |
| `NATIVE_LYRIC_RECEIVED` | lyric | 原生 lyricInfo / 官方 payload |
| `LYRIC_PARSED` | lyric | 解析成功 |
| `LYRIC_PARSE_REJECTED` | lyric | 解析拒绝 |
| `SESSION_REDUCED` | media | session/metadata reducer |
| `SURFACE_STATE_CHANGED` | lyric | 歌词 surface 可见性/children |
| `RENDER_STATE_CHANGED` | renderer | 绘制决策、frame |
| `AOD_TRANSITION` | aod | AOD 切换过程（非对照专用句时） |
| `PERF_SAMPLE` | performance | 耗时/队列 |

## 2. AOD / Recycler 对照句（必须可检索）

| 旧日志片段 | event | area | always-on |
|---|---|---|---|
| `Observed LyricsRecyclerView attachment` | `RECYCLER_ATTACHED` | aod | 否（需打开 Debug 且 aod） |
| `Stabilized LyricsRecyclerView scroll` | `RECYCLER_SCROLL_STABILIZED` | aod | 否 |
| `Primed LyricsRecyclerView` | `RECYCLER_PRIMED` | aod | 否 |
| `Official lyric layout height changed` | `OFFICIAL_LAYOUT_HEIGHT_CHANGED` | aod | 否 |
| `Official lyric row scale` | `OFFICIAL_ROW_SCALE` | aod | 否 |
| `LyricsRecyclerView setCurrentLyric geometry` | `SET_CURRENT_LYRIC_GEOMETRY` | aod | 否 |

## 3. 启动摘要 / 关键状态（Debug 关闭仍输出 INFO）

| 旧日志片段 | event | area |
|---|---|---|
| `Loaded in ` / `Loaded in system_server` | `SYSTEMUI_BOOTSTRAP` | bootstrap |
| `Skip process ` | `SYSTEMUI_BOOTSTRAP` | bootstrap |
| `Resolved SystemUI private hooks` | `SYSTEMUI_BOOTSTRAP` | bootstrap |
| `Hooked ` | `HOOK_INSTALLED` | bootstrap |
| `Registered protected SystemUI lyric settings receiver` | `SYSTEMUI_BOOTSTRAP` | bootstrap |
| `Official lyric render pipeline` | `SYSTEMUI_BOOTSTRAP` | bootstrap |
| `Loaded lyric UI settings` / `Received lyric UI settings` | `SETTINGS_APPLIED` | bootstrap |
| `Applied bridge debug logging` | `DEBUG_CONFIG_APPLIED` | bootstrap |

## 4. 分类回退（ERROR / `emitLegacyInfo`）

INFO 调用点已传入显式 `event`。下面的关键词分类只用于 `LegacyLogEventMap`：ERROR 路径，以及测试里的 `emitLegacyInfo`。

| 关键词 | area |
|---|---|
| aod / ambient | aod |
| recyclerview / prime / scroll | aod |
| draw / render / frame / glow | renderer |
| translation / 翻译 | player-special |
| provider / external lyric（Phase 5 已删除） | lyric |
| screen timeout / wake lock / screen-state | media |
| parser / lrc / ttml / yrc | lyric |
| setting / preference / style | bootstrap |
| hooked / hook / dexkit / classloader | bootstrap |
| playback / mediasession / metadata | media |
| systemui / official lyric / seedling / oplus | media |
| perf / durationMs / queue | performance |

WARN / ERROR 不受 Debug 总开关关闭影响；ERROR 的 event 为 `FAILURE`。
