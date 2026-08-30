# 播放器主动发布 `lyricInfo` 接入协议

[English](PLAYER_INTEGRATION.md)

本文面向能够修改播放器自身播放进程的开发者。4.0 推荐播放器直接发布标准
`lyricInfo`：

```text
播放器内部歌词模型
        ↓
播放器自己的 MediaSession / MediaMetadata["lyricInfo"]
        ↓
ColorOS SystemUI 原生锁屏歌词页面
        ↓ 可选
Bridge 的逐字渲染、AOD、翻译按钮、样式和兼容增强
```

播放器不需要依赖 Bridge APK、不发送 Bridge 私有广播，也不进入 Bridge 的 LSPosed
作用域。`lyricInfo` 是写入播放器现有平台 `MediaMetadata` 的 JSON 字符串。未安装 Bridge
时 ColorOS 仍可原生消费；Bridge 只是 SystemUI 侧的可选增强。

## 1. 何时采用主动接入

播放器已经拥有稳定歌词模型，或能在自身进程构造完整时间轴时，优先主动接入。只有
无法修改播放器、必须从私有运行时接口取词时，才需要独立 Provider。

实施前先确认：

- 哪一个 MediaSession 真正拥有通知栏/媒体卡播放状态；
- 稳定歌曲 ID，或至少歌名 + 歌手 + 时长；
- 权威歌词加载完成事件；
- 歌词源是逐行、逐字、翻译还是仅罗马音/注音；
- 宿主真实封面与 PlaybackState 更新链。

不要为歌词另建第二个 MediaSession。ColorOS 可能选中错误会话，产生暂停的重复媒体卡。

## 2. Metadata 键与最小 payload

将 JSON 字符串写入：

```text
MediaMetadata["lyricInfo"]
```

为了获得最广泛的 ColorOS 原生兼容，至少发布：

| 字段 | 要求 | 含义 |
|---|---|---|
| `songName` | 建议 | 当前显示歌名。 |
| `artist` | 建议 | 当前显示歌手。 |
| `songId` | 建议 | 宿主有稳定 ID 时填写。 |
| `lyricType` | 建议 | 标准时间轴使用 `0`。 |
| `lyric` | 原生显示必需 | 供系统官方列表使用的逐行 LRC。 |
| `noLyric` | 建议 | 有合法歌词时为 `false`。 |

Bridge 只有在 `lyric` 或 `rawLyric` 含合法时间标签时才接收 payload。虽然 raw-only payload
可进入 Bridge 解析器，播放器仍应发布逐行 `lyric`，因为首要消费者是 SystemUI 官方列表。

示例：

```json
{
  "songName": "示例歌曲",
  "artist": "示例歌手",
  "songId": "track-42",
  "lyricType": 0,
  "lyric": "[00:10.000]第一句\n[00:14.500]第二句\n",
  "rawLyric": "[00:10.000]<00:10.000>第一<00:10.700>句<00:12.800>\n[00:14.500]<00:14.500>第二<00:15.200>句<00:17.000>\n",
  "translationLyric": "[00:10.000]First line\n[00:14.500]Second line\n",
  "provider": "com.example.player",
  "source": "com.example.player-v5",
  "trackKey": "track-42|示例歌曲|示例歌手|180",
  "sessionGeneration": 12,
  "noLyric": false
}
```

可以保留未知 JSON 字段。Bridge 不要求 Provider applicationId，也不要求私有 envelope
标记。

## 3. 可选扩展字段

| 字段 | 格式 | 用途 |
|---|---|---|
| `rawLyric` | 增强 LRC | 供逐字卡拉 OK 渲染使用。 |
| `translationLyric` | 逐行 LRC | 规范翻译 lane。 |
| `provider` | 字符串 | 诊断所有者，通常写宿主包名。 |
| `source` | 字符串 | 诊断来源/运行 profile。 |
| `trackKey` | 字符串 | 拒绝同一会话中过期 payload 的稳定身份键。 |
| `sessionGeneration` | 正整数 | 真实换曲时单调递增的代次。 |
| `album` | 字符串 | 可选身份与展示上下文。 |

为了兼容已有官方 payload，Bridge 还识别 `translatedLyric`、`translateLyric`、
`transLyric`、`lyricTranslation`、`translationLrc`、`transLrc` 和 `translation`。
新接入统一写 `translationLyric`；只有播放器官方 writer 已拥有其他字段时才保留别名。

## 4. 时间轴格式

### 4.1 逐行 lane

使用绝对播放毫秒时间：

```text
[00:10.000]第一句
[00:14.500]第二句
```

时间标签可用 `[]` 或 `<>`，推荐 `mm:ss.mmm`。行起点不得倒退；推广/空白行应在翻译
对齐前移除。

### 4.2 逐字 lane

`rawLyric` 每行由行标签、绝对逐字标签和可选末尾标签组成：

```text
[00:10.000]<00:10.000>第一<00:10.700>句<00:12.800>
```

规则：

1. 逐字时间是绝对媒体位置，不是相对本行的 offset。
2. 同一行逐字起点不得倒退。
3. 保留有意义的空格；不要在 CJK token 之间凭空插空格。
4. 歌词源提供结束时间时，用末尾标签标记最后一个字的视觉结束点。
5. 只有逐行时间时省略 `rawLyric`，不要伪造逐字扫光。

### 4.3 翻译 lane

翻译使用对应主句的绝对起点：

```text
[00:10.000]First line
[00:14.500]Second line
```

每条翻译只对齐并消费一次。罗马音、音译、注音和 pronunciation HTML 都不是翻译，
不得写入此 lane。没有真实翻译时省略字段，不要复制主歌词冒充翻译。

## 5. 曲目身份与 generation

payload 必须属于同一个 MediaSession 当前展示的 metadata。

推荐身份优先级：

1. 稳定 media ID；
2. 歌名 + 歌手 + 时长；
3. 播放器自有不可变歌曲键。

维护单调 generation：

- 只在真实换曲时递增；
- 同曲迟到的 ID/歌名/歌手补全只合并，不递增；
- 发起取词时捕获 generation；
- 结果返回后再次核对当前 track + generation；
- 只清理由本接入明确写入的旧 payload。

蓝牙/车载歌词把 TITLE 改成当前歌词行不等于换曲。SystemUI 消费前应保留或恢复稳定歌曲
身份。

## 6. 发布时序

安全链路：

```text
观察到权威歌曲
        ↓ generation++
携带 track + generation 发起取词
        ↓
结果返回且仍匹配当前歌曲/代次
        ↓
复制当前宿主 metadata，保留身份与封面
        ↓
putString("lyricInfo", json)
        ↓
写回同一个播放器 MediaSession
```

边界：

- 队列预加载歌词不能绑定成当前歌曲。
- 异步结果不能“回来时看到谁就写给谁”。
- 目标 ColorOS 丢弃 extras-only 更新时，应发布真实 metadata 对象。
- 歌词进度不靠持续重写 metadata；SystemUI 从 PlaybackState 取时钟。
- 只有宿主覆盖 metadata 丢掉字段时，才按同曲条件 replay。

## 7. 保留宿主 metadata 与封面

歌词是播放器 metadata 上的附加字段，不是替代物。必须保留：

- media ID、歌名、歌手、专辑、时长、序号、评分和未知宿主字段；
- 封面 bitmap 与 URI；
- 当前 MediaSession、PlaybackState 和播放器 CustomAction。

部分 ColorOS 上 `MediaMetadata.Builder(existing)` 会丢失 bitmap 状态。遇到该行为时，用空
typed Builder 按类型复制字段，再写 `lyricInfo`。不要为了发歌词联网补封面、伪造封面，
也不要用上一首缓存封面覆盖当前歌曲。

写回前应测量完整候选 metadata Parcel。参考 Provider 在超过 512 KiB 时只拒绝歌词注入，
原 metadata 继续 fail-open。

## 8. 播放时钟

使用播放器真实 PlaybackState：

- 正确的 PLAYING / PAUSED / BUFFERING；
- 当前 position；
- playback speed；
- 单调的 `lastPositionUpdateTime`。

不要为“唤醒”SystemUI 伪造 PLAYING 或把位置归零，这会破坏媒体卡播放图标、seek 与锁屏
歌词可见性。

## 9. 可选翻译 CustomAction

需要 Bridge 公共翻译按钮时，可在 PlaybackState 中提供：

```text
io.github.andrealtb.lockscreenlyrics.action.TOGGLE_TRANSLATION
```

SystemUI/Bridge 负责绑定显示。添加时必须保留全部 PlaybackState 字段和宿主 action；
播放器 callback 不应把它解释为播放器业务命令。若播放器官方 action row 所有权不同，
应省略该 action，保留原生控件。

## 10. 可选 OPlus 媒体历史声明

部分 ColorOS 会在媒体会话进入 OPlus 管线前过滤播放器包。外部播放器可在 manifest
声明：

```xml
<application>
    <meta-data
        android:name="io.github.andrealtb.lockscreenlyrics.OPLUS_MEDIA_HISTORY"
        android:value="true" />
</application>
```

它只影响 OPlus media-history / blacklist policy，不会生成歌词、安装 Provider，也不会把
播放器加入 Bridge 作用域。

## 11. 验收清单

必须用实际准备发布的 APK 验证：

- [ ] 未安装 Bridge 时，SystemUI 原生消费 `lyricInfo`；
- [ ] 安装 Bridge 后没有第二份重复歌词提交；
- [ ] 首次播放、暂停/恢复、seek、切歌、连续三首快速切换和同曲重播；
- [ ] 逐行、逐字、翻译和无歌词歌曲；
- [ ] 锁屏、解锁再进入、熄屏与 AOD；
- [ ] 首帧封面 URI 与后续 bitmap；
- [ ] metadata churn 不递增 generation；
- [ ] 旧异步结果不能覆盖新歌；
- [ ] payload 不超过设备 Binder / Parcel 边界；
- [ ] 日志不含完整歌词、token、cookie 或私人本地路径。

反馈兼容问题时，请提供播放器版本、机型/ROM/SystemUI 版本、MediaSession 所在进程、
脱敏的 `lyricInfo` 字段摘要、PlaybackState 摘要和最短复现切歌序列。

## 12. Provider 参考实现

若需要通过外部模块适配不能修改的播放器，参阅：

- [Provider 适配技术指南（中文）](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers/blob/4.0/docs/4.0/PROVIDER-ADAPTATION-GUIDE.zh-CN.md)
- [Provider adaptation guide (English)](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers/blob/4.0/docs/4.0/PROVIDER-ADAPTATION-GUIDE.md)

公开 JSON 契约始终由播放器拥有，不对任一仓库建立编译期依赖。
