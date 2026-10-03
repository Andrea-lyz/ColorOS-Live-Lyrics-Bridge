# D1 AM fix8：锁屏即小卡不加载 与 换面接力

日期：2026-10-03。对应 `lyrics-log-20261003-192119.txt`。两个 APK 都需要更新。

## 缺陷一：锁屏时已在小卡，小卡不加载

时间线（epoch 1，小卡第一次请求）：

```text
19:22:04.164  web_album_started        （抓专辑页）
19:22:22.874  web_album_network_io     （18.7 秒后失败）
19:22:22.874  AM_DETACHED / retry_after_network_io_attempt1
```

同一个专辑页在 19:22:23.157 的第二次抓取只用 **0.16 秒**。也就是说第一次抓取在网络刚恢复/屏幕刚亮时
长时间涓流，18.7 秒后才失败；期间客户端没有任何进展信号，用户切走后才由大封面请求补上。
这不是换面取消导致的，而是**单次慢抓取把整个请求拖死**。

修复：

- **按阶段设总时长上限。**`video_file` 30 秒，`web_album` 8 秒，`catalog`/`playlist` 6 秒。
  超时按可重试传输错误处理（`network_stage_timeout`），由 fix5 的请求内重试立即换一条连接重来。
  按本次数据，8 秒超时后重试约 0.16 秒即可完成，而不是等 18.7 秒。
- **文本响应启用 gzip。**专辑页是 HTML，压缩后传输量显著下降（视频仍用 identity，避免与字节范围校验冲突）。
- 顺带把 detach 日志文案从 `matched_download_completes` 改成 `started_download_completes`，与 fix7 的规则一致。

## 优化二：换面接力，不再回静态封面

原行为：切面 → 销毁播放器与视频层 → 重新请求 → 重新 prepare → 首帧，期间露出原生静态封面。
按本机日志，这段约 200–250ms（例如 19:22:50.214 挂载 → 50.429 playing）。

现在：已经播放中的视频在**同一首歌**换面时不再重建解码，而是把播放器挂到新的 `TextureView` 上：

- 只对同一 session/generation 且正在播放的视频启用；换歌仍走原来的请求流程。
- 旧的视频层在新层真正接管之后才释放，避免空档。
- 接力路径不播 200ms 淡入（`handover` 时时长 0），因此直接显示下一帧。
- 任何一步不满足（非同一首歌、播放器未就绪、guard 不通过、挂载失败）即回退到原有流程，
  新增 `ARTWORK_SURFACE_HANDOVER` 记录是否走了接力。

## 测试包

- `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix8-debug.apk`
- `artifacts/Artwork-Provider-AM-20261003-fix8-debug.apk`

| 资产 | 文件 SHA256 |
| --- | --- |
| Bridge fix8 | `7DB4A16636C4D1233124F02D47907284805AAB312D56DC1D3D73B5F3FAAFBA05` |
| AM fix8 | `A0549C513B09A206FA35CB71F99C22DEB0BA04262A89442AFE03379F07A2769D` |

apksigner 验证通过，Debug signer 与 fix7 相同：
`5a6fd43fc11b4c70ff12b6f82a7261c2ed9c4945692b3d818ca50bc64c8dd869`。

用户自行重启 SystemUI 后重新开启 AM 锁屏联调。

## 复测重点

1. **锁屏即小卡。**清插件缓存，让锁屏直接停在小卡布局并播放音乐，不要手动切面。
   期望小卡自己出现动态封面；日志中 `web_album` 若涓流会被 8 秒上限截断并立即重试。
2. **换面接力。**视频播放中来回切大小卡，期望不再长时间露出静态封面。
   日志出现 `ARTWORK_SURFACE_HANDOVER surface=... warmDecoder=true` 即走了接力；
   出现 `fallback=true` 说明回退了原流程，请一并反馈。
3. 回归：换歌、暂停、恢复、息屏/AOD、解锁、关闭联调；确认没有残留视频层或卡死的播放器。
4. 快速连续切面与切歌交替，确认接力不会把上一首的画面带到新歌。

接力是新增优化路径，风险点是播放器跨 view 的 Surface 切换与旧层释放时机；
这部分只能用真机画面确认，JVM 无法覆盖。

## 本地验证

Bridge 714 项（708 通过、6 个已有 fixture 跳过），AM 63 项全部通过；Lint 均 0 errors。
新增用例覆盖同曲换面保留、换歌不保留、超窗取代，以及阶段时长上限与慢抓取快速失败。

193740 复测显示阶段上限已生效（专辑页 18.7s→1.3s），但视频档传输失败会中止整次解析、
且切面的空档帧仍会销毁播放器，因此两项都未达预期；已在 [fix9](DYNAMIC-ARTWORK-AM-FIX9-TEST.zh-CN.md) 补齐。
