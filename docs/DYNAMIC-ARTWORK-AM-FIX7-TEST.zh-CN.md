# D1 AM fix7：首次缓存通过，小卡偶发不播放

日期：2026-10-03。对应 `lyrics-log-20261003-191055.txt`。两个 APK 都需要更新。

## 日志实测

本轮 14 次挂载里 4 次小卡、其余为大封面；小卡挂载中 epoch 5、13、15 完全没有产生任何
`ARTWORK_TEST_RENDER_STATE`，即资源从未交付。其余小卡（epoch 9/19/23/27）都在 0.3–0.5 秒内播放，
且都走 `ARTWORK_AM_CROSS_SIZE_CACHE`（复用已缓存的 1080）。

三次失败的原因各不相同，但都发生在“小卡请求还没拿到资源，布局就切回大封面”的窗口内：

| 挂载 | 现象 |
| --- | --- |
| epoch 5 · 19:11:48.5 | 插件记录 `ARTWORK_AM_RESOLVE started` 后 **7.5 秒没有任何输出**（同窗口 SystemUI 仍在正常记录），随后被大封面取代并取消 |
| epoch 13 · 19:12:03.8 | 客户端停在 `state=connecting`，从未进入 `resolving`，5 秒后被大封面取代 |
| epoch 15 · 19:12:16.9 | 已匹配并开始下载 360 档，被大封面取代后 `ARTWORK_AM_DETACHED`（fix6 生效，后台完成并缓存） |

也就是说，小卡能否播放取决于“请求能否在布局切走之前拿到资源”，而这些请求本身没有缓存兜底，
所以表现为偶发。

## 修复

1. **同曲换面不再取消已发起的请求。**同一 session/generation 只是在大封面与小卡之间切换时，
   已发出的查询保留到完成（上限 6 秒的保留窗口，避免卡死的请求反过来阻塞另一面）。
   超过窗口或换了歌才按原逻辑取代。日志新增 `ARTWORK_REQUEST_RETAINED`。
2. **绑定握手加时限。**客户端从 `connecting` 到真正把请求交给插件超过 8 秒即判 `handshake_timeout` 并快速重试，
   不再等到 45 秒租约到期才失败。
3. **插件侧：只要请求已经开始执行，客户端取消不再中止它。**原先只有“匹配成功”才 detached，
   现在改为已开始执行即完成并落缓存（并发 detached 上限 1）。队列中尚未开始的请求取消时仍立即中止，
   不会为跳过的歌曲白下载。工作线程从 1 提到 2，收尾任务不会挡住当前歌曲。日志 `ARTWORK_AM_DETACHED`。
4. **收窄缓存锁。**`AmCache` 原先用同一个 `synchronized` 包住所有文件读写，任一慢 I/O 都会阻塞插件唯一的
   工作线程数秒——与 epoch 5 的 7.5 秒静默吻合。现在只有内存 pin 表加锁，文件读写不再持锁，
   索引仍用临时文件加原子重命名写入。

## 测试包

- `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix7-debug.apk`
- `artifacts/Artwork-Provider-AM-20261003-fix7-debug.apk`

| 资产 | 文件 SHA256 |
| --- | --- |
| Bridge fix7 | `E3256FF8B760662B22CC6E2505B45B649A73F9AA52189DAE6EA8F17818837A94` |
| AM fix7 | `E88A51B1DE0DF784F1E3B122EB5150A461E93299F458FEA6CC5A6852AE6AD3F7` |

apksigner 验证通过，Debug signer 与 fix6 相同：
`5a6fd43fc11b4c70ff12b6f82a7261c2ed9c4945692b3d818ca50bc64c8dd869`。

用户自行重启 SystemUI 后重新开启 AM 锁屏联调。

## 复测重点

1. 清插件缓存后，反复在大封面与小卡之间切换，重点观察首次进入小卡是否播放。
   期望不再出现“小卡一直静态、切走再切回才有”。
2. 日志确认 `ARTWORK_REQUEST_RETAINED` 只在同曲换面时出现；换歌不应出现。
3. 快速连切多首不同歌曲：确认当前歌曲不被旧任务拖慢，`ARTWORK_AM_DETACHED` 最多 1 个。
4. 回归首次缓存、恢复网络、同长切歌、Explicit/Clean、关闭联调后无残留。

已开始执行的请求在客户端离开后仍会下载并缓存，这是本轮有意放宽的边界；单请求仍有 38 秒/20MiB/租约上限。

## 本地验证

Bridge 713 项（707 通过、6 个既有 fixture 跳过），AM 61 项全部通过；Lint 均 0 errors。
新增用例覆盖同曲换面保留、换歌不保留、卡死请求超窗后取代，以及“已开始即完成 / 未开始即中止”。
真实锁屏切换时序与 FUSE I/O 行为必须在设备上确认。

192119 日志进一步定位到“锁屏即小卡”是专辑页首次抓取涓流 18.7 秒所致，
并在 [fix8](DYNAMIC-ARTWORK-AM-FIX8-TEST.zh-CN.md) 加入阶段时长上限、gzip 与换面接力。
