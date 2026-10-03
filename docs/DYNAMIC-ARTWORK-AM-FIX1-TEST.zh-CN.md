# D1 AM fix1：切歌身份、专辑目录缓存与自动重试

日期：2026-10-03。针对 `lyrics-log-20261003-055146.txt` 的同专辑切歌反馈。
后续 Showgirl 的 Explicit/Clean 封面歧义按用户新规则在 [AM fix2](DYNAMIC-ARTWORK-AM-FIX2-TEST.zh-CN.md) 处理；Bridge 继续沿用本页 fix1。
状态：源码与本地验证完成，下面行为仍需 fix1 真机复测；两个包均为 Debug 本地候选。

## 日志确认的问题

- 05:55:42、05:55:55：唯一同会话来源与当前卡片/controller 文字一致，但均无 media ID、同艺人且两首均 231000ms，
  `fallback_title_change_ambiguous` 拒绝新歌曲，没有进入 AM 请求。
- 05:57:34：`catalog_match_unconfirmed`，60 秒短退避；05:57:40 再次切回命中同一失败缓存。
- 05:57:53、05:58:07：`network_io` 的缓存退避分别还剩约 27/13 秒。
- 窗口内 11 次锁屏 `playing`（9 次大封面、2 次小卡片），两个实际尺寸均有下载/重封装与复用成功；
  未见首帧超时、挂载拒绝或崩溃标记。05:58:23 有关闭联调设置。
  这不证明全机型、全部歌曲或长期资源预算。

## 修复内容

1. **同长歌曲的显式来源切换证据。**只有当前精确会话中的 SystemUI source 从此前确认歌曲变到新歌曲，
   且新 source/card/controller 一致、时长已知、专辑非空、DISPLAY 无冲突，才允许解除同长标题歧义。
   新完整 snapshot 稳定至少 500ms 后，必须发起一次后台 controller metadata 读取再确认；
   计时器或读取迟到时按 host/session/recheck epoch、candidate/source 复核，不让 B 的结果确认 C。
   未见 source 切换的标题投影，以及可靠 media ID 暂时丢失，继续静态。
2. **稳定确认不会被连续观察无限推迟。**每个 host 至多一个待发起计时器和一个在途复核。
   来源/卡片变化仍失效旧展示，detach/unbind 取消；同会话复用原 controller，不造新会话。
3. **完整专辑曲目表与视频索引。**缓存已经确认的公开专辑曲目表，按市场、艺人和完整专辑名隔离，24h TTL。
   同专辑新歌先按 title/artist/album/duration 和可选精确 ID 逐首匹配，再按 albumId 与目标尺寸复用完整视频。
   确认过的同专辑同尺寸视频可离线复用；无完整身份、错版本、缓存表中不存在的歌曲不直接套旧视频。
4. **精确专辑 fallback。**歌曲候选不足时，已知专辑的查询增加同市场 album search；
   只有专辑名/艺人严格一致且 albumId 唯一，才读取实际 Web 曲目表并验证当前歌曲。
   不从谁有动画选版本，不跨区选第一项；歧义继续 AMBIGUOUS。
5. **到期主动唤醒。**有限 retry hint 挂一个可取消的主线程定时任务，到期重新收集宿主候选、复核当前 stamp/锁屏/过渡/可见性，
   不依赖新 PreDraw、暂停恢复或再次切歌。暂停、息屏、换歌、失去候选、关闭和配置重建都会取消旧任务。
   定时任务即便已经出队，旧 revision/epoch 也不能复活旧请求。
6. **诊断与缓存升级。**增加各匹配字段的数量统计，不记录标题、原始 ID、URL；
   匹配索引版本升级到 v2，旧错误索引不再挡住新算法，现有验证视频仍可按资源索引复用。
   取消/任务失效在保存失败状态前再次检查；实际网络失败仍按有限退避处理。

未改变 C3 原生过渡、圆角/shader 挂载、静音循环、首帧门禁、单解码器或 Bridge scope。
不配置正式持久展示、不安装或操作设备，不修改歌词 Providers。

## 测试 APK 与更新

- `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix1-debug.apk`
- `artifacts/Artwork-Provider-AM-20261003-fix1-debug.apk`

**两个都更新。**签名、包名和版本号沿用 D1；旧 APK 保留，无需清数据或重新导入视频。
用户安装后自行重启 SystemUI，再确认 AM 插件来源/诊断已启用、市场为预期值、Bridge 已选择 AM 插件，
重新开启“AM 锁屏联调”。完整初始设置见 [D1 测试](DYNAMIC-ARTWORK-AM-TEST.zh-CN.md)。

apksigner 已验证两包签名与 D1 相同，signer SHA256：
`5a6fd43fc11b4c70ff12b6f82a7261c2ed9c4945692b3d818ca50bc64c8dd869`。

| 测试包 | 文件 SHA256 |
| --- | --- |
| Bridge fix1 | `B1838137CCBE29499BDBBF909E9BEA4D84CA1131E7E32791374BF6DFE5ECC938` |
| AM Provider fix1 | `9A75E997981D2BCEF8976505F6067E8D44029E2C9B7B96BECAC71B00276F72E0` |

## 本轮最小复测

1. 先保持联网，播放 `1989 (Taylor's Version) [Deluxe]`。
   在 **Style (Taylor's Version)** 与 **Blank Space (Taylor's Version)** 之间互切 5 次，
   不暂停、不切第三首；两首都约 231 秒，应在身份稳定与原生过渡完成后恢复视频。
2. 再顺序切到该专辑其他歌曲，检查 `catalog_cache` 和 `verified_album_file_hit`，
   同 albumId/目标尺寸不应逐首重做歌曲搜索与视频下载。首次用 fix1 建立目录索引可能仍需联网。
3. 切大封面/小卡片、快切 A→B→C、切原版/重录版/Deluxe，检查最终歌曲与静态原封面关联正确，不能出现旧歌迟到覆盖。
4. 已缓存且完成目录核对的同专辑同尺寸曲目可断网切换；从未确认的专辑断网应静态。
5. 若复现临时网络/接口失败：恢复网络并保持歌曲播放，等待日志中的 delayMs 到期；
   不手动暂停，观察自动重试。单纯 NETWORK_BLOCKED 通常 30 秒，RETRY_LATER 按返回时间。
   再验证暂停/息屏/关闭期间不会继续下载，恢复或重新开启后只请求当前歌曲。
6. 最后关闭“锁屏动画联调”，检查恢复静态；提供完整日志和首次异常时间。

网络故障和目录缺失仍可能保持静态；fix1 修复的是确认/缓存/恢复链，不承诺所有上游请求成功。
C17 双槽与正式开关继续未完成，本轮以已验证的本机 profile 为范围。

## 日志

插件与 Bridge 诊断仍需独立开启，使用完整窗口：

```powershell
.\scripts\capture-lyrics-log.ps1 -Provider bridge
```

新增标记：

```text
ARTWORK_SOURCE_REPLACEMENT sourceChanged=true freshConfirmationRequired=true
ARTWORK_METADATA_CHECK reason=fallback_source_recheck_required
ARTWORK_FALLBACK_RECHECK
ARTWORK_AM_MATCH_CHECK stage=catalog_cache|itunes_song|web_album_fallback|web_song_album
  candidates=... titleMatches=... artistMatches=... albumMatches=... durationMatches=... completeMatches=...
ARTWORK_AM_CACHE reason=verified_album_file_hit
ARTWORK_RETRY_SCHEDULED delayMs=... clientEpoch=...
ARTWORK_RETRY_FIRED clientEpoch=...
```

原 `ARTWORK_TEST_RENDER_STATE state=playing` 才是视频实际播放证据。
source replacement/retry fired 本身不代表挂载或首帧成功。

## 本地验证边界

相关 Gradle 任务覆盖 Bridge 与 AM 单元测试、Lint、Debug APK。
Bridge 704 个测试（698 通过、6 个已有 fixture 跳过），AM 38 个测试全部通过；
Lint 分别为 0 errors/48 warnings 与 0 errors/4 warnings，无新 baseline 或检查禁用。
新增回归覆盖同长切歌、无独立 source 的投影、可靠 ID 消失、快速切歌与迟到复核、过早读取、时长补齐、
到期独立唤醒/取消/关闭，以及专辑 fallback、错版本、跨市场缓存与脱敏诊断。
JVM 不执行真实 SystemUI/controller/Handler、MediaMuxer 或网络请求，必须完成上述设备复测。
