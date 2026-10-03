# D1 AM fix9：小卡选档失败 与 接力未触发

日期：2026-10-03。对应 `lyrics-log-20261003-193740.txt`。fix8 的两个改动都没有生效，原因是各自还差一步。
两个 APK 都需要更新（本次已通过 `scripts\dev.cmd install` 安装）。

## 缺陷一：小卡仍然要手动切换

fix8 的阶段上限生效了：专辑页这次只用 1.3 秒（上一版是 18.7 秒）。但请求随后倒在这里：

```text
19:38:07.811  video_file_started   （360×360 档）
19:38:42.480  video_file_network_read_timeout   34.7 秒后
19:38:42.490  新请求 → 19:38:43.243 1080 档下载完成（0.46 秒）
```

小卡按“最小足够档”选了 360，而这个 360 资源在该 CDN 上涓流了 34.7 秒；同一时刻 1080 档只用了 0.46 秒。
代码在视频传输失败时会 `throw`，直接中止整次解析，**不会去试下一个分辨率档**，所以小卡只能等死。

修复：

- 视频档传输失败或格式不支持时改为**继续尝试下一个 variant**（上限从 3 提到 4）。
- `network_stage_timeout` 在视频档不再对同一 URL 重试，直接换档，避免把时间耗在同一个慢资源上。
- 视频档单次预算从 30 秒收紧到 15 秒，使“换档”来得及发生。
- 专辑 API 层面的错误（429、401/403 等）仍然直接返回，不会去逐个试档。

按本次数据，360 档会在 15 秒被截断，随后 408/486/768/1080 中任意一个可用档即可完成，小卡无需手动切换。

## 缺陷二：接力一次都没触发

`ARTWORK_SURFACE_HANDOVER` 与 `ARTWORK_REQUEST_RETAINED` 都是 0。原因是**切面必然经过一帧 `selected=none`**：

```text
19:38:47.039  小卡 no_bounds → selected=none → stop() 销毁播放器
19:38:48.133  大封面 eligible → 新请求 → 重新 prepare
```

fix8 的接力条件要求 `mount != null`，而空档帧已经把播放器与视频层都销毁了，所以永远走不到接力分支。

修复：

- 空档帧不再无条件销毁。只要**同一首歌、同一身份、仍在播放、锁屏仍激活且该歌曲的视图仍挂载**，
  就释放画面但保留解码器（`ARTWORK_SURFACE_HELD`，新增 `DynamicArtworkRenderer.detachSurface()`）。
- 接力判断改用歌曲身份（sessionEpoch + trackGeneration）与上一次显示的 stamp，不再要求上一帧有 mount；
  旧层在新层真正接管后才释放。
- 暂停、解锁、切歌、息屏仍会真正销毁解码器，保持此前验收过的行为。

## 测试包

- `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix9-debug.apk`
- `artifacts/Artwork-Provider-AM-20261003-fix9-debug.apk`

| 资产 | 文件 SHA256 |
| --- | --- |
| Bridge fix9 | `257C393A93C7271FBC88B43E9B9E88E79AD10A799436522BDF863EE7994A60C4` |
| AM fix9 | `77527D2EA9F2D57AB84B7B7F226F0D619CA74B80C171B3FA3CCC135D270BFB1C` |

Debug signer 与 fix8 相同：`5a6fd43fc11b4c70ff12b6f82a7261c2ed9c4945692b3d818ca50bc64c8dd869`。
本次已通过 adb 安装到设备；SystemUI 仍需手动重启。

## 复测重点

1. **锁屏即小卡。**清插件缓存后让锁屏直接停在小卡，不要手动切面。
   期望小卡自己出现动态封面；日志中 360 档若涓流应看到 `video_file_network_stage_timeout` 后紧跟另一个 `ARTWORK_AM_VARIANT`。
2. **换面接力。**视频播放中来回切大小卡，期望不再露出静态封面，日志出现
   `ARTWORK_SURFACE_HELD` 与 `ARTWORK_SURFACE_HANDOVER surface=... warmDecoder=true`；
   若只有 `fallback=true` 或两者都缺，请反馈日志。
3. 回归：暂停、解锁、息屏/AOD、换歌、关闭联调，确认解码器会被真正销毁、无残留画面。

## 本地验证

Bridge 714 项（708 通过、6 个已有 fixture 跳过），AM 63 项全部通过；Lint 均 0 errors。
跨 view 的 Surface 接管与真实 CDN 表现只能由设备画面确认。
