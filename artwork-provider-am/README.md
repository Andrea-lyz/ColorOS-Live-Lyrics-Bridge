# AM Artwork Provider — local integration candidate

普通 Android APK（API 30+），不是 Xposed 模块，不注入 Apple Music 或 SystemUI。
通过 `artwork-contract` v1 返回 `localTestOnly=false` 和完整本地 MP4 的只读 FD。
仅 Debug 本地联调，尚未配置正式发布签名。

来源链路：iTunes 候选/精确 lookup → 已确认的 Apple Music 专辑公开结构化数据 →
方形 HLS master → AVC SDR variant → 有限、连续、单文件 byterange VOD →
完整下载 → Android MediaExtractor/MediaMuxer 归零重封装 → 文件校验 → 原子缓存/租约。
不抓取 Web token、不需要 Apple 账号，不调用第三方 resolver。

匹配保留 Unicode/版本信息，用歌曲/艺人/专辑/毫秒时长核对；精确 URL 与查询冲突拒绝。
fix2 按用户允许的封面等价策略合并同名同艺人 Explicit/Clean 发行版对，冷查询稳定优先 Explicit，精确 URL 仍优先；其他版本歧义继续拒绝。
多个候选返回 AMBIGUOUS；有界搜索空结果、HTTP 和结构变化不当成永久 NO_MATCH。
首版艺人使用严格规范化相等，没有合作艺人别名评分；缺艺人或时长拒绝。
纯歌曲 URL 在地区 lookup 无结果时暂不可用；带 albumId 的链接可直接核对 Web 曲目表。
无 URL 的 CN 不完整搜索不会静默跨到 US；已知专辑时可经严格 album search 与 Web 曲目表 fallback。
fix1 缓存已验证整专辑曲目表和按 albumId/目标尺寸的视频索引；同专辑新歌仍逐首核对身份，缓存不绕过版本匹配。
fix3 增加已验证视频清单，在查询上限内跨尺寸复用；网络恢复失效旧传输错误，429 等上游退避保留。两包复测见 [fix3](../docs/DYNAMIC-ARTWORK-AM-FIX3-TEST.zh-CN.md)。
fix5 对瞬时 IOException 在同一请求内有界重试（每次尝试使用新输出流），HTTP 状态类错误不重试。见 [fix5](../docs/DYNAMIC-ARTWORK-AM-FIX5-TEST.zh-CN.md)。
fix6 在身份匹配成功后不再因客户端取消而中止下载：请求转为 detached，完成后仍写入缓存（并发上限 2）。见 [fix6](../docs/DYNAMIC-ARTWORK-AM-FIX6-TEST.zh-CN.md)。
fix7 把 detached 条件放宽为“请求已开始执行”（上限 1、工作线程 2），并收窄 AmCache 锁使文件 I/O 不再阻塞工作线程。见 [fix7](../docs/DYNAMIC-ARTWORK-AM-FIX7-TEST.zh-CN.md)。
fix8 增加按阶段总时长上限（web_album 8 秒）与文本 gzip，使慢抓取快速失败并立即重试。见 [fix8](../docs/DYNAMIC-ARTWORK-AM-FIX8-TEST.zh-CN.md)。
fix9 让视频档传输失败改为继续尝试下一个 variant（视频档 15 秒预算且不重试同一 URL），避免被单个慢档卡死。见 [fix9](../docs/DYNAMIC-ARTWORK-AM-FIX9-TEST.zh-CN.md)。
fix22 支持多碟专辑（按碟分区的曲目表合并）；fixture `showgirl-encore-two-disc-public.html` 为该页 2026-10-04 公开数据的字段精简快照。见 [fix22](../docs/DYNAMIC-ARTWORK-AM-FIX22-TEST.zh-CN.md)。
fix21 让专辑页解析分步骤记录失败点、单个异常曲目只跳过，最近请求的专辑记录全部结果。见 [fix21](../docs/DYNAMIC-ARTWORK-AM-FIX21-TEST.zh-CN.md)。
fix17 让工作线程与 detached 计数成为进程级，服务随解绑销毁时已开始的下载继续完成；新增手动绑定页，把 Apple Music 专辑绑定到本地专辑名（可限定歌手），命中时跳过曲目核对。见 [fix17](../docs/DYNAMIC-ARTWORK-AM-FIX17-TEST.zh-CN.md)。

来源默认关闭、默认非计费网络；启用及设置由插件 Activity 管理。
缓存命中可离线使用（来源仍须启用）；成功/确定无动画 TTL 24h，临时故障按短退避。
文件缓存 256MiB LRU、查询索引最多 128；活跃租约 pin 文件，关闭/取消/死亡释放。
所有事务验证实际 UID；Bridge App 必须先在插件授权当前签名，平台签名 SystemUI 单独校验。
来源设置和签名不备份，诊断默认关闭且不记录曲名、歌词、完整 URL、token、原始 media ID。

构建和测试：从工作区根目录使用 `scripts\dev.cmd bridge :artwork-provider-am:testDebugUnitTest :artwork-provider-am:lintDebug :artwork-provider-am:assembleDebug`。
测试 fixture `apple-1989-deluxe-public.html` 是 2026-10-03 公共 Apple 页面中匹配 header/track section 的字段精简快照；另外保留该专辑公开 master 与 AVC 408 子清单，均无个人数据。
Android 原生重封装、解析/解码、UI 和真实网络结果必须真机验收，JVM 测试不代替它们。

用户流程、测试歌曲及日志见 [AM 联调步骤](../docs/DYNAMIC-ARTWORK-AM-TEST.zh-CN.md)。
