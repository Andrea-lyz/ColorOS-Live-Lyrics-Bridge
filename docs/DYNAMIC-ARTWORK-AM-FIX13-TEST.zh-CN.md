# D1 AM fix13：插件进程被系统冻结与大卡分辨率

日期：2026-10-03。对应 `lyrics-log-20261003-214150.txt`。用户反馈：小卡首次加载仍有问题，大卡也容易卡住；
大卡视频分辨率偏低、很糊。换面观感按用户决定保持 fix12 现状，记为已知限制。

## 本次证据

### 卡住的根因：AM 进程被系统暂停

```text
21:43:57.831  ARTWORK_AM_HTTP web_album_complete          （线程 1462）
21:44:07.376  ARTWORK_AM_DETACHED（SystemUI 解绑/取消）
21:44:07.386  ARTWORK_AM_MATCH_CHECK stage=web_song_album （线程 1462，本地解析）

21:44:15.625  ARTWORK_AM_HTTP web_album_complete          （线程 4264）
21:44:23.593  ARTWORK_RESOLVE_STALLED restart=true（Bridge 看门狗重新绑定）
21:44:23.616  ARTWORK_AM_MATCH_CHECK stage=web_song_album （线程 4264）
```

`web_album_complete` 与 `MATCH_CHECK` 之间只有本地解析，正常为毫秒级，这里却分别相隔 9.5 秒和 8 秒，
并且都在 SystemUI 发来调用的 10–20 ms 后恢复。说明整个 AM 进程被暂停，收到外部调用才恢复，
符合 ColorOS 后台冻结的特征，与网络无关。每次恢复后约 2 秒又被暂停，2 秒内完成的请求正常，超过的就卡住，
大卡、小卡同样受影响。这也解释了 fix10–fix12 的现象：进程暂停时所有线程都停止，计时器和日志都不会出现；
恢复时若请求总期限（38 秒）已过，失败没有日志。

### 大卡模糊

小卡下载 408×408 后，大卡（1312×1284）通过 `ARTWORK_AM_CROSS_SIZE_CACHE width=408`（其他歌曲为 360）
直接复用了小卡视频。跨尺寸复用原本会在没有足够大的缓存时退而使用较小的视频。

## 实现

### Bridge

- 请求期间以隐藏的 `BIND_FOREGROUND_SERVICE` 标志绑定插件，让系统把插件视为绑定的前台服务；若被拒绝，自动退回普通绑定。
  只在等待结果期间持有该绑定，本地 fixture 模式不变。
- 等待结果期间每秒发起一次轻量调用 `getCapabilities()`，利用“外部调用即恢复”的行为，避免请求中途被暂停。
  使用独立线程，上一次调用未返回时跳过本次；结果到达或请求结束即停止。
- fix12 的 10 秒卡死看门狗保留，作为最后兜底。

### AM 插件

- 新增进程暂停检测：请求进行中由守护线程每 250 ms 打点，迟到超过 1.5 秒即记录 `ARTWORK_AM_PROCESS_PAUSED gapMs=…`。
- 暂停期间产生的网络超时不再写入失败缓存（`ARTWORK_AM_FAILURE_NOT_CACHED …_process_paused`），避免下一次请求被拦 30 秒。
- 阶段卡住时先记录 `stalled_in` 再检查取消和总期限；已分离（detached）请求的结果记录为 `ARTWORK_AM_DETACHED_RESULT`。
- 跨尺寸缓存只复用分辨率足够的视频（以 1080 上限封顶），否则下载对应尺寸；只有下载失败时才退回较小的视频，
  并记录 `ARTWORK_AM_LOWER_RESOLUTION_FALLBACK`。

主要源码入口（仓库相对路径）：

- `app/src/main/java/io/github/andrealtb/lockscreenlyrics/systemui/artwork/ArtworkProviderClient.java:132`：绑定标志与退回。
- `app/src/main/java/io/github/andrealtb/lockscreenlyrics/systemui/artwork/ArtworkProviderClient.java:164`：心跳调用。
- `artwork-provider-am/src/main/java/io/github/andrealtb/artwork/am/AmPauseDetector.java`：进程暂停检测。
- `artwork-provider-am/src/main/java/io/github/andrealtb/artwork/am/AmCache.java:174`：只复用分辨率足够的缓存。
- `artwork-provider-am/src/main/java/io/github/andrealtb/artwork/am/AmResolver.java:182`：下载失败后的低分辨率退回。

## 本地验证与边界

- Bridge：720 项，714 通过、6 项原有 fixture 跳过，0 失败；Lint 0 errors / 48 warnings。
- AM：72 项全部通过；Lint 0 errors / 4 warnings。新测试：408 缓存不会被 1312 大卡复用，下载失败时可退回；
  1080 视为满足超过上限的宿主。
- 冻结判断依据日志时间戳，需要设备复测验证。本版是否足以阻止冻结（绑定标志、心跳二者谁生效）同样需要设备日志确认；
  若仍卡住，`ARTWORK_AM_PROCESS_PAUSED` 会直接显示暂停时长。
- 已知限制：换面过程显示官方静态封面，稳定后视频淡入（fix12 行为，用户确认不再调整）。
- 已知现象：部分歌曲 `catalog_album_unconfirmed`（如本日志 21:43:07，候选歌手名全部不匹配），属于匹配不到专辑，会显示静态封面，
  不是卡住。

## 复测顺序

1. **先手动重启 SystemUI**，重新开启“AM 锁屏联调”。
2. 清 AM 缓存，按“电源键 → AOD → 手动点亮”切几首歌，大卡、小卡都试。期望 2–3 秒内出动画。
   需要关注的日志：`ARTWORK_AM_PROCESS_PAUSED`（若出现，说明仍被暂停）、`ARTWORK_RESOLVE_STALLED`、`stalled_in_`。
3. 大卡清晰度：清缓存后先在小卡加载，再切大卡。期望出现一次 1080 档下载（`ARTWORK_AM_VARIANT width=1080`），
   不再出现 `CROSS_SIZE_CACHE width=360/408` 后直接播放；画面应明显清晰。
4. 若仍卡住，可尝试在系统设置中为 AM 插件开启“允许后台行为/不限制电池”，再测一轮，用于确认冻结来源。

## 测试资产

- `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix13-debug.apk`
- `artifacts/Artwork-Provider-AM-20261003-fix13-debug.apk`

| 资产 | SHA256 |
| --- | --- |
| Bridge fix13 | `8EA9C46BB7E100E8385F0CF239B24B6E1607CE9BC126CB31CA7B64CF62F50C40` |
| AM fix13 | `15C216FDA9EFAB3F42BBD651F401C633DAE3E4CC5A5B8C10162015AFDA7D5C0A` |

两包已通过 `scripts\dev.cmd install` 覆盖安装到已连接设备，adb 均返回 `Success`。
归档文件与对应构建输出哈希一致。SystemUI 尚需用户手动重启。
本地 Debug 测试包，不是正式发布版本。
