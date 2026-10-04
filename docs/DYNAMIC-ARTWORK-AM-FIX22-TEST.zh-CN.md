# D1 AM fix22：多碟专辑的曲目表

日期：2026-10-04。只改 AM 插件，Bridge 沿用 fix21。

## 问题

用户确认 040311/042351 中的歌曲属于 Taylor Swift《The Life of a Showgirl: The Encore》，并提供链接
`https://music.apple.com/it/album/the-life-of-a-showgirl-the-encore/6814997249`；插件内手动绑定提示页面无法解析。

本地抓取该专辑公开页面（US 与 IT 市场，Explicit `6814997249` 与 Clean `6814995859`）：这是两碟专辑，
曲目表分为 `track-list - <专辑ID> - 1`（12 首）和 `track-list - <专辑ID> - 2`（4 首）两个分区。
解析器只识别单碟的 `track-list - <专辑ID>`，因此找不到曲目表（fix21 的步骤名为 `track_list_missing`），
自动匹配与手动绑定都失败。头部与动态封面（`motionDetailSquare`，mvod 域名）均正常。

## 修复

- 同时接受 `track-list - <专辑ID>` 与 `track-list - <专辑ID> - <碟号>`（1–3 位数字），按页面顺序合并全部碟的曲目，
  总数上限仍为 250。分区必须属于预期专辑 ID，其他专辑或格式不符的分区不纳入。
- 新增测试 fixture `showgirl-encore-two-disc-public.html`：2026-10-04 公开页面的字段精简快照，只保留头部、
  两碟曲目（标题、艺人、时长、ID、链接）和动态封面 master，无个人数据。

## 本地验证

- AM：89 项测试全部通过（新增两碟快照解析为 16 首并能唯一匹配 226000 ms 的 The Fate of Ophelia；碟分区归属校验）；
  Lint 0 errors / 4 warnings。

## 设备验证

1. AM fix22 已覆盖安装；Bridge 不变，无需重启 SystemUI。
2. 上次失败结果在插件内最多缓存 5 分钟；如刚测试过，可在插件中“清除未使用的缓存”或稍等后再试。
3. 用通用 Provider 播放该专辑歌曲，期望出现 `unique_track_album_confirmed`、`ARTWORK_AM_READY` 并显示动画。
4. 插件手动绑定：粘贴上述专辑链接应能确认动态封面并绑定。

用户已设备确认（“欧克了”）：该两碟专辑可正常匹配并显示动态封面。

## 测试资产

| 资产 | SHA256 |
| --- | --- |
| Bridge fix21（未变）`ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261004-fix21-debug.apk` | `CBA3EF9C62E99E694C274D28F0931FD4E5924BAA4FD2D55FF117B97A35643160` |
| AM fix22 `Artwork-Provider-AM-20261004-fix22-debug.apk` | `0530E45B35136CA5463F49F0FDC2C48EDD1CB1EDD5D22B00A8135B3053B1E0E8` |

AM fix22 已用 `adb install -r -d` 覆盖安装，返回 `Success`。本地 Debug 测试包，不是正式发布版本。
