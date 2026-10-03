# BitChord 动画封面来源与调用链调查

日期：2026-10-03。只读源码调查，无安装、账号操作或设备验证。
仓库：[kushagrasinghx/BitChord](https://github.com/kushagrasinghx/BitChord)。
固定快照：`85d1918300aff63a009bb6df2da7056cf1b00a43`，GitHub main tree 未截断。
以下行号均对应本次固定快照，不是对未来 main 的承诺。

## 1. 来源结论

AM 动画封面直接来自 Apple 的 Web 内部 Catalog `amp-api.music.apple.com`，不是 boidu、NopXx 实例或其代理服务。
凭证来自 Apple Web 播放器 JS bundle 内的匿名 JWT；动画地址来自 `attributes.editorialVideo`。
返回的是 HLS URL，交由 Media3/ExoPlayer 播放并缓存网络数据，不先生成完整本地 MP4。
这说明接口调用路线有公开客户端实现参考，但本次未运行 BitChord 或验证其 token 抓取/API 成功率。

播放器还有 Tidal、社区和 Spotify Canvas 来源；不能把它看到的所有视频都归为 AM。

## 2. 完整调用链

```text
NowPlayingScreen.kt:662
  -> PlayerState.kt:49 rememberCanvasArtwork(song)
  -> CanvasRepository.kt:74 canvasFor(song)
  -> AppleMusicCanvas.kt:46 search(title, artist, album)
     -> token(): 获取 Apple Web JS 中 JWT
     -> amp-api /{storefront}/search，types=songs
     -> score(): 评分排序候选
     -> inline editorialVideo 或 albums relationship -> fetchAlbum()
     -> motionUrls(): square / raw / tall HLS 地址
  -> CanvasArtwork.matches(): 再核对 title/artist/album
  -> CanvasArtworkPlayer.kt:194 ExoPlayer
     -> MediaItem(uri=HLS URL)
     -> CanvasCache / OkHttpDataSource
```

`PlayerState.kt:50–91` 读取动画开关、计费网络和 Spotify 优先设置；专辑未知时等待补齐。
UI 使用 title/artist 保留部分状态并尝试缓存/旧资源，不等同本项目的会话、generation 和结果 stamp 隔离。

默认来源顺序（`CanvasRepository.kt:93–105`）：

```text
AppleMusic -> Tidal -> Community -> Spotify
```

开启 Spotify 优先后为 `Spotify -> AppleMusic -> Tidal -> Community`。
每个来源惰性请求，并用 `matches()` 验收后取首个成功结果。
Tidal 静态入口为 `https://api.tidal.com/v1/search`；社区清单为
`https://vivimusicanvas.mkmdevilmi.workers.dev/canvas.json`。
本次只确认这些入口出现在对应源文件，未调用这些服务或审计 Spotify 登录流程。

## 3. AM 请求、认证与字段

`AppleMusicCanvas.kt:36–42`：

- API base：`https://amp-api.music.apple.com/v1/catalog`。
- token 页面：`https://music.apple.com/us/browse`。
- storefront：取 `Locale.getDefault().country` 的两字符国家码，小写，否则 US；不是 Apple 账号市场查询。

歌曲搜索（`:59–66`）：

```text
GET /{storefront}/search
  ?term=<artist + title + optional album>
  &types=songs&limit=10&extend=editorialVideo&include=albums
```

响应读取 `results.songs.data[]`。
先尝试 song `attributes.editorialVideo`；没有时取
`relationships.albums.data[0].id`（`:252–266`），缺关系才从 song URL 的 album 路径提取 ID。
随后请求 `/{storefront}/albums/{albumId}?extend=editorialVideo`（`:269–300`）。
专辑页还有直接 `types=albums` 搜索路径（`:113–158`）。

认证实现（`:348–385`）：页面定位 `/assets/index...js`，遍历 JWT 候选、解码 exp，优先选择 header `WebPlayKid` 或 payload `AMPWebPlay` 标记。
优先候选不存在时仍会尝试第一个未过期候选，所以注释所述“只接受 Web token”不是严格保证。
token 缓存至到期前一分钟；抓取失败退避 30 分钟。
请求附 Bearer、Origin、Referer、浏览器 UA（`:438–443`）。
401 将当前 token 标记 rejected 并清缓存（`:395–418`）；后续查询再抓。
本次只阅读这套机制，没有提取 token；它是依赖 Apple Web 内部结构的方案，不能当成正式 Developer Token 的稳定替代。

## 4. 匹配能力与本项目差距

源码已有艺人门禁、title/album 评分、编辑版关键词、playlist/compilation 标记过滤，之后再次 `matches()`。
`motionUrls()` 使用方形优先，对本项目有参考价值。
但以下行为不能直接沿用：

| 静态确认 | 位置 | 本项目应如何处理 |
| --- | --- | --- |
| 只接收 title/artist/album，没有时长、ISRC、UPC 或可信 Apple URL 参数 | `AppleMusicCanvas.kt:46`、`:175–231` | 补精确 ID/URL lookup、时长与版本冲突核对 |
| MIN_SCORE=12；artist+精确 title 可达 25，即使 album 不同仍可通过评分 | `AppleMusicCanvas.kt:168`、`:201–223` | 专辑冲突先拒绝；不能只依赖总分 |
| 没有平分/接近分数的 AMBIGUOUS 分支，按排序寻找有动画候选 | `AppleMusicCanvas.kt:74–103` | 先确认唯一歌曲/专辑，再检查其动画，不能换成另一版本以求有视频 |
| `matches()` 在请求专辑缺失时放宽 album；候选 title/artist 缺失也允许通过 | `CanvasArtwork.kt:47–61` | 显式置信等级，缺字段不能自动认定匹配 |
| normalization 只保留 `[a-z0-9\s]`，中文/日文等会被删除 | `CanvasArtwork.kt:69–75` | 保留 Unicode 字母/数字，空规范化结果不能参与相等匹配 |
| `cleaned()` 移除所有方括号内容，可能删掉 Deluxe 等身份信息 | `CanvasRepository.kt:217–225` | 仅移除已确认展示噪声，版本语义保留 |
| 已匹配结果始终可复用；专辑后来补齐不会触发 cache 中命中的重新匹配 | `CanvasRepository.kt:167–183` | 查询字段变化须复核成功缓存，不能只重试旧负缓存 |
| HTTP/解析失败与确定无动画多数归为 null，缓存 null 无时间 TTL | `AppleMusicCanvas.kt:395–418`、`CanvasRepository.kt:162–183` | 分离 401/429/临时故障/NO_MOTION，使用对应退避与过期策略 |

“评分可通过”与“最终展示”不同：album 双方已知时后续 `matches()` 会拒绝不相同专辑。
但它不在 AM 候选循环内，首个返回结果被 repository 拒绝后会尝试其他来源，而非继续查找同来源的正确候选。
这些是源码边界，不是声称已经在 BitChord 真机复现了错配。

## 5. variants、播放器和缓存

`AppleMusicCanvas.kt:307–318` 选字段：

```text
square: motionDetailSquare / motionSquareVideo1x1
raw:    motionDetailRaw
tall:   motionDetailTall / motionTallVideo3x4
asset:  video / videoUrl / hlsUrl / url
primary = square ?: raw ?: tall
fallback = 另一个不同 URL
```

这些是不同构图/来源资产的 HLS URL，不是显式选择出的 360/768/1080 variants。
本次 AM 源文件不解析 master 的 RESOLUTION/CODECS/BYTERANGE，不下载完整 MP4，不检查宽高是否方形。
播放器使用 Media3 HLS 扩展（`app/build.gradle.kts:292`）；
`CanvasArtworkPlayer.kt:194–253` 静音、禁用音轨、REPEAT_MODE_ONE、直接以 URL 创建 MediaItem。
该段没有按目标封面尺寸设置最大视频宽高/码率；具体选到何种 HLS variant 本次未运行确认。
播放失败只尝试一次 alternate，不是重新选择低分辨率方形版本。

`CanvasCache.kt:38–69` 用 SimpleCache + 150MiB LRU + CacheDataSource，循环读取可复用已缓存网络范围；cache 错误时允许走网络。
缓存大小和代码不能证明每个资源完整下载或只下载一次；其注释里的流量投诉未独立核实。

本项目继续采用 provider 解析/验证/落盘，Bridge 只接只读 FD：
参考它的字段和 album relationship；variants 和完整 MP4 沿用本次
[AM 实际清单调查](DYNAMIC-ARTWORK-AM-SURVEY.zh-CN.md)的路径。
不把 ExoPlayer 联网和匿名 token 抓取迁入 SystemUI，也不以 tall fallback 绕过当前方形资源契约。

## 6. 快照、许可和实施影响

- [AM 源文件（固定版本）](https://github.com/kushagrasinghx/BitChord/blob/85d1918300aff63a009bb6df2da7056cf1b00a43/app/src/main/java/com/music/bitchord/data/canvas/AppleMusicCanvas.kt)
- [Repository（固定版本）](https://github.com/kushagrasinghx/BitChord/blob/85d1918300aff63a009bb6df2da7056cf1b00a43/app/src/main/java/com/music/bitchord/data/canvas/CanvasRepository.kt)
- [播放器（固定版本）](https://github.com/kushagrasinghx/BitChord/blob/85d1918300aff63a009bb6df2da7056cf1b00a43/app/src/main/java/com/music/bitchord/ui/player/CanvasArtworkPlayer.kt)

本地参考快照在忽略目录 `artifacts/artwork-analysis/am-survey/bitchord/`；保留原文件名与源码行号。
仓库 LICENSE 和 GitHub license 字段均为 GPL-3.0，本轮没有复制代码进入业务实现。

这项调查补强了直接 Catalog 搜索/歌曲到专辑关系/多种 motion 字段的实现线索；不改变先前独立 provider 的架构决策。
下一轮仍需实现严格匹配、明确错误、variant 选择、完整媒体验证和缓存，然后接生产歌曲查询。
