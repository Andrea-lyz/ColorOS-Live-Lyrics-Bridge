# D1 AM fix5：首次下载被取消与瞬时网络失败

日期：2026-10-03。对应 `lyrics-log-20261003-185656.txt` 的“首次缓存很久不出动态封面”。
两个 APK 都需要更新。

## 日志实测

同一首歌（231833ms）首次缓存，四次尝试：

| 挂载 | 结果 |
| --- | --- |
| 18:57:26.683 epoch=1 | 18:57:28.649 确认专辑，master playlist 拉到 18:57:39.050 失败 `playlist_network_io` |
| 18:57:41.528 epoch=3 | 18:57:41.866 开始下载 1080 视频，18:57:51.414 被取消（未完成） |
| 18:57:53.347 epoch=5 | 18:57:53.724 开始，18:57:54.197 完成，54.327 READY，54.431 playing |

18:57:39.045 与 18:57:51.414 两次门禁判定为 `lockscreen_inactive`，随即 `selected=none` → `stop()` →
取消正在进行的 provider 请求，已下载进度与临时文件全部丢弃。
第二次成功下载同一 1080 文件只用了 0.47 秒，说明资源本身没问题。
整个窗口内歌词音频持续播放：暂停回调直到 18:57:54.475 才出现。

也就是说，卡住的是取资源，不是显示：READY 之后 104ms 就进入 `playing`。
暂停再播放之所以“立刻成功”，是因为那时视频已经缓存（18:57:56.2 附近直接命中缓存）。

## 修复

1. **锁屏锁定标志抖动不再丢弃已开始的下载。**门禁原因从笼统的 `lockscreen_inactive` 拆成
   `bridge_inactive`、`paused`、`screen_off`、`display_off`、`keyguard_open` 等具体值。
   只有 `keyguard_open` 与 `no_bounds`/`geometry_transition`/`native_transition` 属于“表面仍存在、只是暂时不可显示”，
   此时撤下视频层但让同一首歌的取资源继续完成。
   （fix6 已进一步把下载与显示完全解耦：只有功能关闭、`detached`、`binding_not_ready` 才放弃查询，
   息屏/暂停/锁屏抖动不再取消。见 [fix6](DYNAMIC-ARTWORK-AM-FIX6-TEST.zh-CN.md)。）
2. **瞬时网络错误在同一请求内重试。**插件对 IOException（含连接/响应头/正文超时、连接重置、正文截断）
   在上限 3 次内以 300/600ms 退避重试，仍受原 38 秒请求期限、20MiB 与取消检查约束，
   不再因为一次重置就丢掉已确认的专辑与 playlist 结果。
   每次尝试使用新的输出流，部分正文不会被追加成损坏文件。
   HTTP 状态类错误（429、401/403、404）仍按原语义直接返回，不参与该重试。

`ARTWORK_AM_HTTP` 继续记录 `*_started/_complete/_network_*`，并新增 `retry_after_*_attemptN` 与 `exhausted_*`。

## 测试包

- `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix5-debug.apk`
- `artifacts/Artwork-Provider-AM-20261003-fix5-debug.apk`

| 资产 | 文件 SHA256 |
| --- | --- |
| Bridge fix5 | `8D3014D173070199D8420E17D54EA9DB5AB217DF9CD5070E3588BE17322B0B04` |
| AM fix5 | `9781FCD5BA035132815C6093648E86953B3F22EA6D4736614E46FF0A2F80522A` |

已通过 apksigner 验证，Debug signer 与 fix4 相同：
`5a6fd43fc11b4c70ff12b6f82a7261c2ed9c4945692b3d818ca50bc64c8dd869`。

用户自行重启 SystemUI 后重新开启 AM 锁屏联调。

## 复测重点

1. **首次缓存（本轮主项）。**清一次插件缓存，锁屏播放一首未缓存的 AM 歌曲，全程不暂停、不解锁。
   期望首次就能出现动态封面；日志中门禁应显示具体原因，取资源不再因 `keyguard_open` 中断。
   出现 `retry_after_network_io_attempt1` 说明瞬时重试生效且没有整链重来。
2. 恢复网络后不手动暂停即可自动恢复；断网时同专辑同尺寸缓存仍可回放。
3. 暂停、解锁进桌面、息屏/AOD、关闭联调：确认请求确实被取消，不出现息屏后仍在下载。
4. 回归：同长切歌、Explicit/Clean、大小封面复用、快切与迟到结果。
5. 如果仍卡住，请提供 `ARTWORK_GATE_*` 的聚合计数（区分 `screen_off` 还是 `keyguard_open`）与 `ARTWORK_AM_HTTP` 阶段。

JVM 不能覆盖真实网络抖动与锁屏状态回调；本轮需以设备日志确认取消与重试的实际时机。
