# D1 AM fix13 日志验证与 fix14：小卡解码器不再接力到大卡

日期：2026-10-03。对应 `lyrics-log-20261003-220402.txt`（22:04:03–22:06:12，SystemUI 已重启，AM 为 fix13）。
用户实机反馈：卡住问题应已解决。

## fix13 日志验证

| 项目 | 结果 |
| --- | --- |
| `ARTWORK_AM_PROCESS_PAUSED` | 0 次：请求期间未检测到进程暂停 |
| `ARTWORK_RESOLVE_STALLED` / `stalled_in_` / `network_stage_timeout` | 均为 0 |
| 冷启动解析耗时 | 3.76 s、1.73 s、2.90 s、2.36 s，均一次完成；超过 2 秒的请求不再中途停住 |
| 锁屏周期 | 22:04:50.8 `screen_off` → 22:04:53.1 亮屏首个请求，1.7 s 完成 |
| 缓存命中 | `validated_file_hit` 约 40–70 ms |
| 大卡直接请求 | 1080 档（22:05:34，9.1 MB），或 1080 传输失败后 `VARIANT_FALLBACK` 至 960 档（22:04:54）；`CROSS_SIZE_CACHE` 0 次 |
| 换面 | 11 次 `HANDOVER warmDecoder=true`，`layerHidden=true`；无 `stale_render`、`first_frame_timeout` |
| 崩溃 | 无；`AndroidRuntime` 行属于 libsu root 进程启动与退出 |

日志无法区分绑定标志与心跳二者中哪一个起了作用，只能确认二者合用后没有再出现暂停。

## 发现的遗漏与 fix14

歌曲先在小卡加载时，小卡的低分辨率解码器会被接力到大卡继续播放：

```text
22:04:38.070  ARTWORK_AM_READY width=408（小卡）
22:04:41.937  ARTWORK_SURFACE_HANDOVER surface=IMMERSIVE warmDecoder=true   → 大卡播放 408 视频约 9 秒
22:05:53.750  ARTWORK_AM_READY width=360（小卡）
22:05:56.916  ARTWORK_SURFACE_HANDOVER surface=IMMERSIVE warmDecoder=true   → 之后多次换面一直沿用 360 视频
```

fix13 只修复了缓存路径；换面接力路径同样会让大卡变糊。

实现（仅 Bridge）：

- 记录当前解码器视频是为多大的宿主请求的（`playingForPx`，与 provider 查询相同规则：宿主较长边，上限 1080），
  该值随接力保留。
- 目标宿主需要的尺寸大于 `playingForPx` 时不接力，记录 `ARTWORK_SURFACE_HANDOVER skipped=resolution_upgrade`，
  改为对大卡重新请求：先显示官方静态封面，再显示清晰视频。拿到大卡视频后 `playingForPx=1080`，之后大小卡往返照常接力，不会反复重新请求。
- 同一首歌只有第一次从小卡切到大卡时多一次请求；1080 已缓存时约百毫秒，未缓存时需要下载。

主要源码入口：`app/src/main/java/io/github/andrealtb/lockscreenlyrics/systemui/artwork/ArtworkImmersivePlayback.java`
中的 `upgrade` 判断与 `requestPx()`。

## 本地验证与边界

- Bridge：720 项，714 通过、6 项原有 fixture 跳过，0 失败；Lint 0 errors / 48 warnings。AM 未改动，仍为 fix13。
- fix14 需要设备确认：先在小卡加载一首新歌，再切大卡，期望出现 `skipped=resolution_upgrade`，
  随后大卡请求 1080（或缓存命中），画面清晰；再往返切换应为 `warmDecoder=true`。

## 测试资产

- `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix14-debug.apk`
- AM 沿用 `artifacts/Artwork-Provider-AM-20261003-fix13-debug.apk`

| 资产 | SHA256 |
| --- | --- |
| Bridge fix14 | `2E9B44AF54458E86AB26DA6CDE15B130162B8F114A014BD017D43FB5D4D0123A` |
| AM fix13（未变） | `15C216FDA9EFAB3F42BBD651F401C633DAE3E4CC5A5B8C10162015AFDA7D5C0A` |

Bridge fix14 已用 `adb install -r -d` 覆盖安装，返回 `Success`。归档文件与构建输出哈希一致。
SystemUI 尚需用户手动重启。本地 Debug 测试包，不是正式发布版本。
