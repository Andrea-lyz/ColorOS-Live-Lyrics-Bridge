# D1 fix21：专辑页解析失败可定位、单曲异常不再整页作废、请求专辑全部列出

日期：2026-10-04。AM 插件与 Bridge 设置页各有改动。

## 设备反馈与日志（040311）

用户顺手测试通用 Provider（日志中的播放器为 Spotify）：动态封面未加载，插件手动绑定页的“最近请求的专辑”为空。

- Bridge 侧正常：Spotify 会话绑定成功（`ARTWORK_SESSION_BOUND`，标题、歌手、时长一致），查询已发到 AM 插件。
  问题与通用 Provider 无关。
- AM 侧：iTunes 匹配成功（`candidates=54 completeMatches=2 explicitCleanEquivalent=true`），专辑页也下载完成，
  但解析失败，返回 `retry_later_web_schema_changed`（5 分钟退避）。
- 本地抓取多张专辑页（含 US/CN 市场、带与不带动态封面）结构都正常，说明是这张专辑页面的个别差异。
  原解析器任何一处异常都整页作废，且不记录失败步骤，无法从日志定位。
- “最近请求的专辑”原先只记录已匹配/已绑定/无动态封面/未匹配，`web_schema_changed` 不在其中，所以没有列出。

## 改动

AM 插件：

- 专辑页解析分步骤：页面数据、分区、头部、曲目、动态封面。失败时日志 `ARTWORK_AM_FAILURE_DETAIL` 写出失败步骤和异常类型，
  例如 `web_schema_changed step=header_missing/IllegalArgumentException`。只含步骤名，不含页面或歌曲内容。
- 单个异常曲目条目（链接不属于本专辑、单曲链接、缺少时长等）只跳过该条，不再让整张专辑作废。跳过的曲目不会被当作本专辑曲目，
  最多导致该曲未匹配，不会误绑；匹配日志 `stage=... skippedTracks=N` 记录跳过数量。
- 动态封面资源无法识别（如视频不在已允许的 Apple 媒体域名）时改为 `UNSUPPORTED motion_asset_unrecognized`，
  并记录 `step=video_host=<域名>`，不再误报为页面结构变化。
- “最近请求的专辑”记录所有请求过的专辑（取消的请求除外），新增状态“获取失败”，便于对任何失败的专辑手动绑定。
- 绑定页对页面无法解析、动态封面格式不支持给出对应提示。

Bridge 设置页：

- 状态卡新增“无法解析当前专辑的 Apple Music 页面”（提示会自动重试，或手动绑定到同一专辑的其他版本）
  和“当前专辑的动态封面格式暂不支持”，不再把页面解析失败显示成“网络暂不可用”。

## 本地验证

- AM：87 项测试全部通过（新增：单曲异常只跳过、失败步骤命名、未知动态封面域名分类、获取失败也列出专辑）；Lint 0 errors / 4 warnings。
- Bridge：737 项测试，0 失败，6 跳过；Lint 0 errors / 48 warnings。

## 设备验证

1. AM 与 Bridge 已覆盖安装；本次 Bridge 只改设置页，无需重启 SystemUI。
2. 用通用 Provider 播放上次失败的歌曲，抓取日志：若仍失败，`ARTWORK_AM_FAILURE_DETAIL` 会给出具体步骤；
   若是个别曲目条目异常，现在应能直接匹配并播放。
3. 打开 AM 插件 → 手动绑定专辑封面：该专辑应出现在“最近请求的专辑”中（状态为“获取失败”或其他实际结果）。
4. 动态封面设置页的状态卡显示对应说明。

## 测试资产

| 资产 | SHA256 |
| --- | --- |
| Bridge fix21 `ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261004-fix21-debug.apk` | `CBA3EF9C62E99E694C274D28F0931FD4E5924BAA4FD2D55FF117B97A35643160` |
| AM fix21 `Artwork-Provider-AM-20261004-fix21-debug.apk` | `5BA98DB449BE4DEC6D0F55CA60E8F2F91A25144B502D36A003AEEFD585EA502F` |

两者均已用 `adb install -r -d` 覆盖安装，返回 `Success`。本地 Debug 测试包，不是正式发布版本。
