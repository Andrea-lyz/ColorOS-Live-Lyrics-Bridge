# 从 3.8.x 迁移到 4.0

[English](MIGRATION-3.8-TO-4.0.md)

4.0 改变了播放器适配边界：Bridge 只运行在 `system` 和 `com.android.systemui`；每个
播放器专属 hook 由独立 Provider 承担，并从播放器自己的 MediaSession 发布标准
`MediaMetadata["lyricInfo"]`。

Bridge 与 Provider 在运行时互不依赖，但首次升级应使用同一批 4.0 Release 资产，避免
scope、包名和 payload 行为混用。

## 1. 升级前

1. 打开主设置页的“Bridge 配置备份与恢复”，复制完整备份。
2. 记录 LSPosed 中每个旧 Provider 当前勾选的播放器。
3. 从同一 4.0 Release 下载 Bridge 和自己需要的 Provider APK。
4. Release 提供 `SHA256SUMS` 时先核对哈希。

Provider ZIP 只是 APK 下载合集，不是 Recovery 刷机包。

## 2. 安装 Bridge 4.0

4.0 Bridge applicationId 不变：

```text
io.github.andrealtb.lockscreenlyrics
```

使用项目正式签名安装的 3.8.1 可以直接覆盖升级。Bridge 只保留两个作用域：

```text
system
com.android.systemui
```

从 Bridge 作用域移除 Salt、Cone 和所有其他播放器包。播放器进程现在归对应 Provider
所有。

## 3. 替换播放器适配

新 Provider 使用全新 applicationId，因此 Android 允许新旧模块同时安装；但同一播放器
不能被两套模块同时 hook。启用 4.0 Provider 前，先卸载旧 Provider，或取消旧模块对应
播放器的全部 scope。

| 播放器 | 旧接入 | 4.0 applicationId | 4.0 资产 |
|---|---|---|---|
| Salt Player | Bridge 内置 / `io.github.proify.lyricon.saltprovider` | `io.github.andrealtb.coloroslyrics.provider.salt` | `ColorOS-Live-Lyrics-Provider-Salt-v4.0.0.apk` |
| ConePlayer / GP | Bridge 内置 | `io.github.andrealtb.coloroslyrics.provider.cone` | `ColorOS-Live-Lyrics-Provider-Cone-v4.0.0.apk` |
| 酷我 | `io.github.proify.lyricon.kwprovider` | `io.github.andrealtb.coloroslyrics.provider.kuwo` | `ColorOS-Live-Lyrics-Provider-KuWo-v4.0.0.apk` |
| LX / Walnut | `io.github.proify.lyricon.lxprovider` | `io.github.andrealtb.coloroslyrics.provider.lx` | `ColorOS-Live-Lyrics-Provider-LX-v4.0.0.apk` |
| Poweramp | `io.github.proify.lyricon.paprovider` | `io.github.andrealtb.coloroslyrics.provider.poweramp` | `ColorOS-Live-Lyrics-Provider-Poweramp-v4.0.0.apk` |
| Metrolist | `io.github.proify.lyricon.metrolistprovider` | `io.github.andrealtb.coloroslyrics.provider.metrolist` | `ColorOS-Live-Lyrics-Provider-Metrolist-v4.0.0.apk` |
| 酷狗 / 概念版 | `io.github.proify.lyricon.kgprovider` | `io.github.andrealtb.coloroslyrics.provider.kugou` | `ColorOS-Live-Lyrics-Provider-KuGou-v4.0.0.apk` |
| QQ 音乐 | `io.github.proify.lyricon.qmprovider` | `io.github.andrealtb.coloroslyrics.provider.qq` | `ColorOS-Live-Lyrics-Provider-QQ-v4.0.0.apk` |
| 网易云 / 荣耀 / 修改版 9.0.40 | `io.github.proify.lyricon.cmprovider` | `io.github.andrealtb.coloroslyrics.provider.netease` | `ColorOS-Live-Lyrics-Provider-NetEase-v4.0.0.apk` |
| Apple Music | `io.github.proify.lyricon.amprovider` | `io.github.andrealtb.coloroslyrics.provider.apple` | `ColorOS-Live-Lyrics-Provider-Apple-v4.0.0.apk` |
| Spotify | `io.github.proify.lyricon.spotifyprovider` | `io.github.andrealtb.coloroslyrics.provider.spotify` | `ColorOS-Live-Lyrics-Provider-Spotify-v4.0.0.apk` |
| 汽水音乐 | `io.github.proify.lyricon.qishuiprovider` | `io.github.andrealtb.coloroslyrics.provider.qishui` | `ColorOS-Live-Lyrics-Provider-QiShui-v4.0.0.apk` |

## 4. Provider 作用域

每个 Provider 只勾选对应宿主：

| Provider | LSPosed 宿主 scope |
|---|---|
| Salt | `com.salt.music` |
| Cone | `ink.trantor.coneplayer`、`ink.trantor.coneplayer.gp` |
| KuWo | `cn.kuwo.player` |
| LX | `cn.toside.music.mobile`、`com.lxwalnut.music.mobile` |
| Poweramp | `com.maxmpz.audioplayer` |
| Metrolist | `com.metrolist.music` |
| KuGou | `com.kugou.android`、`com.kugou.android.lite` |
| QQ | `com.tencent.qqmusic` |
| NetEase | `com.netease.cloudmusic`、`com.hihonor.cloudmusic` |
| Apple | `com.apple.android.music` |
| Spotify | `com.spotify.music` |
| QiShui | `com.luna.music` |

安装或改变 scope 后重启播放器与 SystemUI。首次安装 4.0 后，若任一进程仍加载旧代模块，
直接重启设备。

## 5. 不安装 Bridge 时能做什么

4.0 Provider 将原生 `lyricInfo` 写入播放器会话。在受支持的 ColorOS 上，只安装 Provider
也可以让 SystemUI 显示官方样式的锁屏歌词。额外安装 Bridge 后，才增加通用逐字渲染、
样式、AOD、翻译按钮和兼容策略；Bridge 不会要求 Provider 再提交第二份歌词。

## 6. 不再支持的接入

- 4.0 矩阵不包含 QQ 音乐 HD。
- 4.0 套件不发布 MusicFree、Gramophone 和 Symfonium Provider。
- Flamingo 原 v4-only 路线不再支持；播放器需从自己的 MediaSession 发布标准
  `lyricInfo`。
- Halcyon 发布标准 `lyricInfo` 时仍可使用；旧 v4 fallback 已移除。

本项目不再分发词幕 Provider 功能。需要词幕时请从
[LyricProvider 原项目](https://github.com/tomakino/LyricProvider) 获取。词幕显示/产品链路
的问题应向该项目反馈，不由 Bridge 或 4.0 Provider 仓库受理。

## 7. 配置迁移与降级

Bridge 4.0 使用歌词 UI schema v3。受支持的旧配置会自动迁移，包括视觉参数和逐播放器
翻译偏好；歌词开头清理规则仍保留，但默认关闭。

“Bridge 配置备份与恢复”覆盖两个配置域：

- 主 UI 与视觉设置；
- 全局/逐播放器翻译设置；
- 开头清理规则与逐曲修正；
- 调试设置和设置页语言。

降级到 3.8.x 不能保证无损，因为旧 codec 不认识全部 schema-v3 字段。保留 4.0 完整
备份，并准备在旧版中重置设置。Provider 降级时禁用/卸载新包，再恢复旧包和 scope；
不同 applicationId 不会互相覆盖。

## 8. 升级后的首次验收

选择一首有翻译和一首无翻译歌曲，检查：

1. 媒体卡与封面正确；
2. 不打开播放器歌词页也能显示锁屏歌词；
3. 暂停/恢复、seek 与连续切两首歌保持同步；
4. 只有歌词源提供逐字时间时才出现逐字高亮；
5. 支持翻译按钮的播放器能即时更新开关状态；
6. AOD 进入/退出不残留上一首歌。

仍有问题时，请提供 Bridge 版本、Provider applicationId/版本、播放器版本、Bridge 与
Provider scope、机型/ROM/SystemUI 版本、复现步骤和脱敏 LSPosed 日志。不要上传认证
token、cookie、完整私人歌词或个人媒体路径。
