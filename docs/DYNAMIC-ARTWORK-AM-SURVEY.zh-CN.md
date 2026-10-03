# AM 实际接口、专辑匹配与动画 variants 调查

调查日期：2026-10-03。状态：公开网络响应与当前源码已核对，尚未实现联网 artwork provider。

## 1. 结论与证据边界

- 可以从 Apple Music 公开专辑页的结构化 JSON 取得专辑 ID、歌曲 ID、毫秒时长和动画 HLS 地址；本次没有登录账号、提取 Web token 或操作设备。
- iTunes Search/Lookup 可取得歌曲候选，但不能独自证明 Apple Music 某地区的曲目缺失；同一 CN 专辑本次 Lookup 只有专辑记录，公开页面却有完整 22 首歌。
- `1989 (Taylor's Version) [Deluxe]` 的真实普通播放 variants 包含 HEVC 360×360；H.264 最小为 408×408。不能承诺所有专辑或所有 codec 均有 360。
- 子清单明确引用同一个 fragmented MP4 的初始化范围与三个媒体范围，可以取得实际 MP4 URI，不必把 `.m3u8` 后缀替换结果当成事实。
- 当前 Bridge 仍发送固定测试查询，并未向插件发送实际歌曲；生产查询、地区配置、匹配、网络缓存和错误分类均需要后续实现。

本次完成了公开页面/JSON、主清单、三个子清单和 MP4 64 字节 Range 读取。
没有下载完整新视频、执行完整解码、验证 Android 原生 fMP4 播放或测量设备功耗。
Catalog API 的字段来自官方文档；`amp-api` 的 `editorialVideo` 解析来自上游源码，未取得认证后的 API 响应。

## 2. 实际来源与接口

| 来源 | 请求/入口 | 本次核实 | 适用范围 |
| --- | --- | --- | --- |
| iTunes Search | `GET https://itunes.apple.com/search?term=Taylor+Swift+1989&media=music&entity=musicTrack&country=us&limit=50` | HTTP 200，`resultCount=51`，有多个专辑版本的歌曲 | 候选发现；不直接取第一个结果，不假设实际数量严格等于 limit |
| iTunes Lookup | `GET https://itunes.apple.com/lookup?id=1713845538&entity=song&country=us&limit=200` | HTTP 200，23 条：专辑 + 22 首歌 | 验证专辑成员关系、精确 ID |
| CN Lookup | 同上，`country=cn` | HTTP 200，仅 1 条专辑 | 来源结果不完整的实测例子，不能写成 NO_MATCH |
| AM 专辑 Web 页面 | `GET https://music.apple.com/{storefront}/album/{slug}/{albumId}` | US/CN 页面均有歌曲与动画结构化数据 | 可作为独立 Web adapter；属于页面内部结构，不是稳定公共 API 合同 |
| 官方 Catalog | `GET https://api.music.apple.com/v1/catalog/{storefront}/albums/{id}`，songs 同类入口 | 官方文档确认地区路径、`include`、`extend`；本次没有认证请求 | 有合法 Developer Token 的独立后端/插件来源 |
| AM Web 内部 Catalog | `GET https://amp-api.music.apple.com/v1/catalog/us/albums/1713845538?extend=editorialVideo` | 无认证实测 HTTP 401；上游 `fetchMotionMasters` 使用 Bearer + Origin | 内部接口，不承诺 `editorialVideo` 为官方稳定字段；401 不能降格为无动画 |

官方专辑 Attributes 文档本次列出 `artwork`、`upc`、`name` 等字段，但未列出 `editorialVideo`。
`extend` 参数本身有文档不代表所有内部扩展名都获得官方稳定保证。
上游 NopXx 通过 Web JS 提取 token 的做法只是实现参考，本次没有使用，也不应移入 Bridge。
正式来源的凭证、运行地区和维护方式需要独立确定；本调查不创建服务承诺。

### 2.1 公开页面中实际读取的位置

读取 `<script type="application/json" id="serialized-server-data">`，本次结构为：

```text
data[0].data.sections[album-detail-header].items[0]
  title
  contentDescriptor.identifiers.storeAdamID
  contentDescriptor.url
  videoArtwork.dictionary.motionDetailSquare.video
  tallVideoArtwork.dictionary.motionDetailTall.video

data[0].data.sections[track-list].items[]
  title / artistName / duration / discNumber / trackNumber
  contentDescriptor.identifiers.storeAdamID
  contentDescriptor.url
```

括号中的 section 名是本次 `id` 的前缀，不是数组索引或 API 固定类型。
实现时须查找匹配 section 并核对 descriptor 的 kind/ID，不能依赖永远是第 0/1 项，也不能递归拿到相关推荐专辑的第一个视频。
页面 `duration` 在本次歌曲上与 iTunes `trackTimeMillis` 精确一致，单位为毫秒。
CN 与 US 本次都列出 22 首歌、相同歌曲 ID，并返回相同 Deluxe 方形 master；这不证明其他专辑的跨区等价或账号播放权限。

## 3. 专辑匹配字段

| 语义 | iTunes 实测字段 | 官方 Catalog 文档字段 | 用途/限制 |
| --- | --- | --- | --- |
| 歌曲身份 | `trackId` | song resource `id` | 优先精确 lookup；普通播放器的 media ID 不能擅自当 Apple ID |
| 专辑身份 | `collectionId` | album resource `id`，song `relationships.albums` | 歌曲到专辑关系；不靠同名或静态图片 URL 推断 |
| 歌曲/专辑名称 | `trackName` / `collectionName` | `name` / `albumName` | 保留 Live、Remaster、Taylor's Version、Deluxe、伴奏等版本语义 |
| 艺人 | `artistId` / `artistName` | `artistName`、artists relationship | 身份优先于展示文本；合作艺人需要别名/集合策略 |
| 时长 | `trackTimeMillis` | `durationInMillis` | 辅助拒绝，不能单独证明相同录音 |
| 版本辅助 | `releaseDate`、`discNumber`、`trackNumber`、`trackExplicitness` | 同类字段、`contentRating`、`isrc` | ISRC 本次仅在文档确认，未取得实际 Catalog 值 |
| 专辑辅助 | `trackCount`、`collectionExplicitness` | `trackCount`、`upc`、`recordLabel` | UPC 本次仅在文档确认；同 ISRC 不保证相同专辑包装 |
| 地区 | 请求 `country`，响应 `country` 为 USA/CHN | 请求 `{storefront}`，可选本地化 `l` | storefront 是市场，l 是语言；不能用系统语言替代市场 |
| Apple URL | `trackViewUrl` / `collectionViewUrl` | `url` | album URL 的 `?i=<songId>` 是歌曲，不要丢失 i 参数 |

### 3.1 已取得的版本冲突样本

| 专辑 | albumId | Style 的 songId | 时长 |
| --- | --- | --- | --- |
| 1989 | 1440935467 | 1440935812 | 231000ms |
| 1989 (Deluxe Edition) | 1440933512 | 1440933520 | 231000ms |
| 1989 (Taylor's Version) | 1708308989 | 1708308996 | 231000ms |
| 1989 (Taylor's Version) [Deluxe] | 1713845538 | 1713845746 | 231000ms |

原版与重录版的 Blank Space 本次也均有 231833ms 的记录。
因此删掉括号后按 title + artist + 3 秒时长容差匹配会选错版本。
三个已抓取的方形动画 master 也不同：原版 `P1392963568`、重录标准版 `P637742511`、重录 Deluxe `P637795736`。
本次证明 URI 不同，未通过画面比较证明内容不同；仍不得跨 albumId 复用未经验证的映射。

匹配顺序建议：

1. 验证 Apple URL 的 HTTPS/精确 host/地区/数字 ID；`album/...?...i=` 优先识别歌曲，纯专辑 URL 不代表指定了某首歌曲。
2. 在同一市场取得歌曲记录、专辑成员关系；核对当前 title/artist/album/时长，URL 与当前歌曲冲突时拒绝，不让旧 URL 覆盖新查询。
3. 无 URL 时搜索候选，用版本标记和专辑约束先排除冲突，再用艺人和时长辅助。不要删除全部括号、不凭搜索排序选第一首。
4. 缺专辑时若多个 albumId 都满足条件，返回 AMBIGUOUS；地区响应不完整进入来源 fallback/临时不可用，不将一个空列表写成永久 NO_MATCH。
5. 已确认具体专辑后才取其动画；同专辑缓存共享仍需保留地区与资源版本，歌曲请求 stamp 继续独立。

以上为待实施策略，不声称已经实现评分器或验证全量别名。

## 4. 动画 variants 实测

样本：[1989 (Taylor's Version) [Deluxe]（US）](https://music.apple.com/us/album/1989-taylors-version-deluxe/1713845538)。
方形 master 文件名 `P637795736_default.m3u8`；竖版 `P637805492_default.m3u8`。
每个 master 各有 28 个 `EXT-X-STREAM-INF` 普通播放 variants；I-frame/trick-play 清单不算播放视频。

| 方形 codec | 实际尺寸集合 | 帧率/范围 |
| --- | --- | --- |
| H.264 `avc1` | 408、456、486、768、960、1080（宽高相同） | 23.976fps，SDR |
| HEVC `hvc1` | 360、456、486、768、960、1080、1920、2160（宽高相同） | 23.976fps，SDR；本次低档标记为 Main 10 profile |

相同宽高/codec 存在多个码率，不能用 `height + "p"` 作为唯一 variant 键。
保留 URI/稳定 ID、width/height、CODECS、VIDEO-RANGE、FRAME-RATE、BANDWIDTH 与 AVERAGE-BANDWIDTH。
HEVC `hvc1.2...` 的 profile 2 是 Main 10；SDR 与 8-bit 是不同概念，不能将 SDR 当成 AVC/8-bit 保证。

竖版普通播放为 30fps，包括 310×414（HEVC）、352×470（AVC）、486×648、1078×1438、1080×1440，以及 HEVC 2048×2732 等。
不能把 tall 名称硬编码成 9:16，也不能将预览图尺寸当视频尺寸。
当前 `ArtworkAsset` 接受近方形，竖版不交付当前 Bridge；保留来源信息供未来独立能力使用。

本次原版和重录标准版的方形 master 各有 29 个普通 variants，尺寸并集也包含 360/408/456/486/768/960/1080/1920/2160。
这仍只是同一艺人的三个专辑样本，不证明全库最低尺寸恒定。

### 4.1 子清单和 MP4 的真实关系

Deluxe HEVC 360 子清单：

```text
#EXT-X-PLAYLIST-TYPE:VOD
#EXT-X-MAP:URI="P637795736_Anull_video_gr505_sdr_360x360-.mp4",BYTERANGE="985@0"
#EXTINF:5.54721,
#EXT-X-BYTERANGE:134643@985
P637795736_Anull_video_gr505_sdr_360x360-.mp4
#EXTINF:5.58892,
#EXT-X-BYTERANGE:141362@135628
P637795736_Anull_video_gr505_sdr_360x360-.mp4
#EXTINF:5.58892,
#EXT-X-BYTERANGE:184531@276990
P637795736_Anull_video_gr505_sdr_360x360-.mp4
#EXT-X-ENDLIST
```

这三个媒体 URI 是同一文件的不同范围。完整文件只下载一次；按 URI 下载三次再拼接会产生三个重复容器。
此前用户 360 文件的 1384563 字节恰为 `461521 × 3`；此前已分析出三份相同内容，这次清单支持该错误机制，但没有调查原下载器内部实现。

| 实测子清单 | 总循环时间（EXTINF 之和） | MP4 Range 响应 | 本次证明程度 |
| --- | --- | --- | --- |
| HEVC 360×360 | 16.72505s | HTTP 206，`bytes 0-63/461521` | video/mp4，前缀 ftyp；未完整解码 |
| AVC 408×408 | 16.72505s | HTTP 206，`bytes 0-63/728484` | 同上 |
| AVC 768×768 | 16.72505s | HTTP 206，`bytes 0-63/4426058` | 同上 |

处理路径应为 master → 普通 variant → 子清单 → 解析相对 URI 和 MAP/BYTERANGE。
只有本次这种同文件、连续完整范围、VOD/ENDLIST 结构才能尝试单文件路径；多文件、加密、live、范围不完整或异常结构不直接拼接。
需限制重定向/host/响应大小和总期限。取得全文件后仍执行 MP4/media 检查，不能以 206、ftyp 或 Content-Type 代替完整可播放证明。
本次未发现三个选中子清单的 `EXT-X-KEY`，不代表所有资源都无加密。

### 4.2 初始 variant 选择建议

- 本机 288px 卡片：优先 AVC 408×408 SDR；360 HEVC 作为经过解码能力验证的备选。已有 360 转 AVC 测试文件是转码产物，不能标成上游原生 AVC 360。
- 大封面：在显示大小、上限与成本约束内选择 AVC 768/960/1080；允许有界降采样显示，不为了本机 1312px 区域自动取 1920/2160。
- 相同尺寸有多个码率时，初版可选最低合适码率并保留回退；该策略尚未设备验证画质。
- HEVC 低带宽不等于更低解码成本；v1 当前只传 mime，未协商 profile/level/bit depth，不能仅凭接受 `video/hevc` 判断兼容。
- HLS 只在 provider/后端处理，Bridge 接收已验证本地只读 FD；不把 master URL 交给 SystemUI。
- 优先验证原生 AVC fMP4 能否通过 Android verifier 与播放；失败才评估 provider/后端 remux。不能在 SystemUI 中转码，也不能把本次前缀检查写成 remux 已不必要。

## 5. 当前源码差距与实施顺序

源码事实：

- `artwork-contract/.../ArtworkQuery.java` 已有 title/artist/album/durationMs/appleMusicUrl 和显示/文件限制，没有 storefront、ISRC、UPC 字段。
- `app/.../systemui/artwork/ArtworkImmersivePlayback.java` 当前发送 `Local artwork mounting test`，真实歌曲字段为空；这是固定测试链路，不是生产 resolver 接线。
- `artwork-contract/.../ArtworkAsset.java` 接受 AVC/HEVC 和近方形；`ArtworkFileVerifier` 复核 MIME、尺寸、时长、sample 与 FD，不验证实际设备解码成功。
- 上游 NopXx `mapITunesItem` 丢弃 `trackTimeMillis`；请求硬编码 US/省略 country，`parseM3U8Variants` 用后缀推导 MP4、按 height 合并 variants。这些不能直接作为本项目实现。

建议下一轮实现：

1. 独立 AM provider 的来源 adapter 和规范化内部 DTO，区分官方 Catalog、公开 Web 和 iTunes 候选；地区先作为 provider 设置，可信 URL 携带地区，不能静默跨区选第一项。是否扩展 Binder query 需单独版本决策，不能假装 v1 已有 storefront。
2. 落地精确 URL/专辑关系与保守候选匹配，建立上述同名同长版本冲突 fixture；错误分别为 AMBIGUOUS、来源不完整、401、429、临时错误、确定无动画。
3. 解析真实 HLS variants 和单文件 byterange VOD，保留 codec/profile/尺寸/码率；完成有界下载、原子缓存、文件验证与取消。
4. 新建独立生产配置链路，将有效会话的真实歌曲快照交给 provider；固定测试链路继续作为离线回归工具。只给 FD、不将 token/网络 URL/客户端身份链混入 Bridge。
5. 本地构建后用原生 AVC 408/768 样本验收，再确认自动匹配、暂停/切歌/过渡、网络错误与缓存；C17 双槽适配和长期 GPU/FD 验收仍是独立未完成项。

本调查未修改业务源码，没有新 APK；文档和公开证据不需要重新执行 Gradle。

## 6. 来源与本地证据

用户补充的播放器参考见 [BitChord 来源与调用链调查](DYNAMIC-ARTWORK-BITCHORD-SURVEY.zh-CN.md)：
直接 Apple Web Catalog + 匿名 token + HLS 播放，已有专辑关系解析，但不提供本项目所需完整 MP4/严格匹配契约。

- [官方获取专辑接口](https://developer.apple.com/documentation/applemusicapi/get-a-catalog-album)
- [官方专辑属性](https://developer.apple.com/documentation/applemusicapi/albums/attributes-data.dictionary)
- [官方歌曲属性](https://developer.apple.com/documentation/applemusicapi/songs/attributes-data.dictionary)
- [官方歌曲关系](https://developer.apple.com/documentation/applemusicapi/songs/relationships-data.dictionary)
- [官方 Developer Token](https://developer.apple.com/documentation/applemusicapi/generating-developer-tokens)
- [NopXx lib/artwork.js](https://github.com/NopXx/apple-music-artwork-search/blob/main/lib/artwork.js)：2026-10-03 main 快照，仅阅读参考，未复制其实现进入业务代码。
- [US Deluxe 页面](https://music.apple.com/us/album/1989-taylors-version-deluxe/1713845538)、[CN Deluxe 页面](https://music.apple.com/cn/album/1989-taylors-version-deluxe/1713845538)
- [US 标准重录版](https://music.apple.com/us/album/1989-taylors-version/1708308989)、[US 原版](https://music.apple.com/us/album/1989/1440935467)

本地原始公开响应位于忽略目录 `artifacts/artwork-analysis/am-survey/`：
`apple-*.html`、`lookup-*.json`、`search-us.json`、`official-*.json`、`*-master.m3u8`、`child-*.m3u8`、`square-variants.json`、`tall-variants.json`。
`evidence-summary.json` 保存文件 SHA256 与有界 Range/未认证响应记录。公开 CDN URI 是本次快照，不作为永久 endpoint 或入库媒体资产。
