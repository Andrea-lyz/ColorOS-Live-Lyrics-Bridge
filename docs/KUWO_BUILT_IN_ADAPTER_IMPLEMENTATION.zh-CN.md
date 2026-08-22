# 酷我音乐阶段 1：稳定歌词按钮与 Provider 原生 lyricInfo

> 状态：阶段 1 已完成静态实现，真机稳定性待验证。  
> Bridge 基线：release 3.7.3，仅叠加本阶段的 MediaData policy 规范化。  
> 目标播放器：cn.kuwo.player。

## 1. 阶段 1 的真实目标

本阶段解决的是之前已经确认的“酷我歌词按钮不稳定”问题：SystemUI 在每次
MediaData 重建和 dispatch 前，都必须把酷我歌词入口策略稳定为 52。

这里的 52 是歌词入口组合值（4 + 16 + 32）。阶段 1 不负责逐字渲染、翻译、
暂停后的歌词页面动画，也不通过 UI 保留来掩盖数据问题。

酷我歌词内容由 LyricProvider\kuwo-music 在酷我进程内生成官方 lyricInfo；Bridge
只负责阶段 1 的入口策略稳定和 3.7.3 原有通用歌词消费链路。

## 2. Bridge 阶段 1 实现

### 2.1 唯一新增的酷我 Hook

Bridge 从已解析的 SystemUI lyric loader 所在类向上查找：

onMediaDataLoaded(String, String, MediaData)

在该共同入口中只处理 packageName 为 cn.kuwo.player 的数据：

1. 读取 MediaData.getMediaDataEx()；不存在时退回 MediaData 本身。
2. 读取 lyricData；没有 lyricData 时完全交给官方流程。
3. 当 displayPolicy 为 0 时，反射调用 lyricData 的四参数 copy 方法，创建
   displayPolicy=52 的新快照。
4. 把源对象中的可复制非 final 状态同步到副本，避免官方 copy 丢失 lines、
   oriLyric 和当前播放索引等状态。
5. 通过 setLyricData 或字段回写替换 MediaData 中的 lyricData，然后继续原始
   dispatch。

即使 lyricData 没有歌词行，只要对象存在且 policy 为 0，也执行 0 -> 52；阶段 1
不以歌词内容是否为空决定是否显示歌词按钮。

### 2.2 Bridge 明确不保留的旧实验路径

除上述 MediaData 共同入口规范化外，本次不恢复以下任何酷我专属处理：

- f7.t、f7.d3 / f7.b3、f7.o3 / f7.n3 的 hasLyric、空列表或模式 Hook；
- 酷我进程内 e0.f 歌词捕获、LRC/LRCX 解析和 MediaSession#setMetadata Hook；
- OplusMediaDataEx 下游保词、暂停/seek 重放、RecyclerView visibility/alpha/scroll；
- RUS/SharedPreferences 伪造或仅修改 lyricEntrance getter；
- lyricprovider/kuwo-music 外部歌词广播和 SystemUI replay。

阶段 1 的 Bridge 代码只有：

- ColorOS-Live-Lyrics-Bridge/app/src/main/java/io/github/andrealtb/lockscreenlyrics/KuWoMediaDataPolicyNormalizer.java
- ColorOS-Live-Lyrics-Bridge/app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java

## 3. Provider 的歌词来源

LyricProvider\kuwo-music 是酷我歌词内容的唯一写入者：

1. 在 cn.kuwo.player Hook cn.kuwo.mod.lyrics.e0#f(Music, boolean, Music)。
2. 从 LyricsInfo 读取 lyricsData、lyricsType、offset 和可用状态。
3. 用 rid、MediaSession mediaId、标题和歌手做串歌门控。
4. 解析普通 LRC / KuWo LRCX，并使用原生 MediaSession#setMetadata 的 Builder
   追加官方 lyricInfo。
5. 酷我自然重建 metadata 时继续叠加同曲 lyricInfo，同时保留原有封面、URI、标题、
   歌手和时长。

Provider 不向 SystemUI 发送外部歌词广播，不修改播放状态或播放进度，也不承担
阶段 1 的 displayPolicy 修复。

## 4. 阶段 1 验收

### 4.1 前置条件

1. Bridge 使用 3.7.3 基线加本阶段补丁。
2. 仅启用当前 LyricProvider\kuwo-music，不同时启用旧 KuWo Provider APK。
3. 重启酷我和 SystemUI 后采集完整 logcat；重启动作由测试者执行。
4. 覆盖一首有歌词歌曲和一首无歌词歌曲。

### 4.2 必须观察的日志和操作

| 场景 | 必须观察 |
| --- | --- |
| 首曲 / 首次媒体卡 | 出现 KuWo media-data lyric policy observed，随后只出现一次 0->52 规范化 |
| 播放、暂停、继续、播放中 seek、暂停中 seek | 同一播放链路的酷我 MediaData 不再出现末尾 policy=0，歌词按钮不消失 |
| 切歌 | 新 MediaData 仍在共同入口规范化；不复用上一首 lyricInfo |
| 无歌词歌曲 | policy 仍保持 52，但歌词内容由官方状态机决定，不注入上一首歌词 |

Provider 内容链路应同时出现 lyricReady 和带有效 lyricInfo 的原生 metadata 日志。

### 4.3 通过条件

阶段 1 只有在真机日志证明以下条件后才算通过：

- 酷我 MediaData 的 0 -> 52 修复发生在 dispatch 前，而不是 getter 末端；
- 暂停、继续和 seek 后没有新的酷我 policy=0；
- 媒体卡片“歌词”按钮保持可用；
- Provider 写入的 lyricInfo 与当前曲目匹配；
- 没有 f7.* 保词、MediaSession 二次改写、RecyclerView 补救或外部广播 replay。

静态测试和 APK 构建不能替代上述设备证据。若按钮仍受设备 RUS 或 SystemUI 版本
差异影响，应记录实际 owner、原始 policy 和替换结果，不继续叠加下游 UI 补丁。

## 5. 当前静态验证

- KuWoMediaDataPolicyNormalizer 单元测试；
- LyricProvider\kuwo-music 的 LRCX、身份门控和官方 lyricInfo 测试；
- Bridge testDebugUnitTest、assembleDebug；
- LyricProvider\kuwo-music testDebugUnitTest、assembleDebug。

阶段 2 再讨论歌词内容、逐字显示和翻译，不在阶段 1 扩大 Bridge Hook 范围。

## 6. 2026-08-22 封面与身份基线

本节是 `logs\kuwo-mainline-23.txt` 之后设备验证通过的主线基线；与阶段 1 的
“只做 displayPolicy”描述冲突时，以本节为准。

固定不变量：

1. Provider 只在酷我自然 `setMetadata` 上叠加同曲 `lyricInfo`，不发送第二份
   metadata，不改写封面、URI、标题、歌手和时长。
2. SystemUI 不把酷我的 `1x1` metadata bitmap 当成有效封面；锁屏 Seedling 收到空或
   过小封面时，只允许用严格同曲身份的真实封面快照修复。
3. 同曲封面快照按 mediaId 和 title|artist 双 key 记录，来源包括 SystemUI 已解析的
   有效封面和酷我原生通知 largeIcon；不得跨曲复用。
4. 酷我车载歌词同曲判定必须使用 `KuWoMediaIdentityPolicy`：精确同曲；稳定身份可
   合并进 title 或 artist 字段。多艺人拼写差异（例如
   `HOYO-MiX` 与 `胡夏&HOYO-MiX`）是真实换曲，不得合并身份。
5. 真实换曲必须清空歌词保留、插件模型和封面身份；暂停、seek 和恢复播放可以保留
   同曲状态。

回归门禁：

- `KuWoMediaIdentityPolicyTest` 必须覆盖同名不同艺人的真实换曲；
- Bridge `testDebugUnitTest assembleDebug` 必须通过；
- 真机验收必须覆盖首播、切歌到同名不同艺人、暂停、继续和 seek 后的歌词与封面；
- 新日志若出现错误归一化、上一首封面顶号、纯色封面或上一首歌词，应先回查本节
  不变量，而不是在下游 UI 继续叠补丁。
