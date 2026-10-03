# D1 AM fix11：不可中断的网络阻塞与跨宿主歌曲代数

日期：2026-10-03。对应 `lyrics-log-20261003-201239.txt`。fix10 未解决小卡首次加载卡住和换面接力。

## 本次证据

### 小卡首次加载

```text
20:13:19.904  ARTWORK_AM_HTTP reason=web_album_started      （线程 18465）
20:13:50.482  ARTWORK_AM_DETACHED reason=started_download_completes
20:13:50.708  web_album_started → 20:13:50.837 web_album_complete（新请求，129 ms）
```

专辑页预算 8 秒，但从 `web_album_started` 到用户切大封面共 30.6 秒，线程 18465 期间没有
`network_stage_timeout`、重试或结果日志，切换后也再无输出。fix10 的计时器到期只调用
`connection.disconnect()`，等待线程本身仍阻塞在 `HttpURLConnection` 内，要等阻塞调用返回后才检查期限。
`disconnect()` 无法结束的阶段（最可能是域名解析）会让整次解析一直挂起。fix10 日志里 22.3 秒才报告的响应头超时也属于这类问题。
具体卡在哪个阶段，本日志无法区分。

### 换面接力

```text
20:14:32.201  ownerId=7 LOCKSCREEN_CARD  sessionEpoch=1 trackGeneration=2
20:14:33.567  ownerId=5 IMMERSIVE        sessionEpoch=1 trackGeneration=1
20:15:09.948  ownerId=7 LOCKSCREEN_CARD  sessionEpoch=1 trackGeneration=3
20:15:11.346  ownerId=5 IMMERSIVE        sessionEpoch=1 trackGeneration=1
```

`trackGeneration` 由每个宿主自己的 `ArtworkTrackIdentityPolicy` 计数。小卡宿主跨歌曲长期存在，
大封面宿主每次切歌重新 attach 后从 1 开始。第一首两者恰好都是 1，日志中有 9 次
`ARTWORK_SURFACE_HANDOVER warmDecoder=true`，`clientEpoch=3` 保持不变；从第二首起
`sameSong` 永远不成立，预览和接力均被跳过，每次换面都重新挂载、解析和准备（`clientEpoch` 5→7→9→11、13→15→17）。
`ARTWORK_SURFACE_HELD` 只出现一次，是因为相同文本被诊断去重，并非保留只发生一次。

## 实现

### AM 插件

- 每个 HTTP 阶段（解析、连接、响应头、重定向、正文）在有界工作线程池（最多 4 个）中执行，解析线程用自身期限等待。
  到期后断开连接、取消并放弃该线程，立即按原有规则重试或换档，不再依赖阻塞调用返回。
- 域名解析单独作为 `dns` 阶段，日志新增 `<stage>_stalled_in_<phase>`，复测时可直接看出卡在哪个阶段。
- `Task.cancel()` 同时放弃当前阶段，客户端取消不必等待阻塞结束。
- 所有工作线程都卡住时记录 `<stage>_workers_busy`，原因码仍为 `network_stage_timeout`，Bridge 契约不变。
- 各阶段预算、重试次数、视频档回退和“已开始即完成”策略不变。

### Bridge

- 新增会话级 `SessionSongs`：同一 MediaSession 上的所有宿主对同一首歌使用同一个 `trackGeneration`。
  歌曲键与身份策略一致：有媒体 ID 用 ID，没有则用标题+艺人。重播之前的歌曲仍是新代数。
- 每个宿主各自的 `requestRevision`、`surfaceEpoch` 和完整 stamp 比较不变，过期结果仍会被丢弃。
- `ARTWORK_SURFACE_HELD` 增加 `surface=`；歌曲不同而未接力时记录 `ARTWORK_SURFACE_HANDOVER skipped=different_song`。

主要源码入口（仓库相对路径）：

- `artwork-provider-am/src/main/java/io/github/andrealtb/artwork/am/AmNetwork.java:106`：阶段等待与放弃。
- `artwork-provider-am/src/main/java/io/github/andrealtb/artwork/am/AmNetwork.java:217`：`Exchange` 期限与取消。
- `app/src/main/java/io/github/andrealtb/lockscreenlyrics/systemui/artwork/ArtworkTrackIdentityPolicy.java:28`：会话级歌曲代数。
- `app/src/main/java/io/github/andrealtb/lockscreenlyrics/systemui/artwork/ArtworkSessionRegistry.java:188`：stamp 使用会话代数。

## 本地验证与边界

- Bridge：720 项，714 通过、6 项原有 fixture 跳过，0 失败；Lint 0 errors / 48 warnings。
- AM：69 项全部通过；Lint 0 errors / 4 warnings。
- 新测试模拟忽略 disconnect 和 interrupt 的解析阻塞：三次尝试约 1.2 秒内结束，第二次可成功，取消可立即释放；
  模拟长期存在的小卡（代数 2）与新 attach 的大封面（代数 1），验证二者得到同一会话代数且 `sameSong` 成立。
- JVM 测试不能证明真实设备上具体卡在哪个阶段，也不能证明画面无闪烁；需要设备复测。

## 复测顺序

1. **先手动重启 SystemUI**，重新开启 Bridge 的“AM 锁屏联调”。
2. **小卡首次加载**：在 AM 插件内清缓存，锁屏初始为小卡，保持播放 30 秒，不切大小卡。
   期望小卡自动出现动画。若某阶段卡住，约 8 秒（专辑页）/ 6 秒（目录、playlist）/ 15 秒（视频）后应出现
   `<stage>_stalled_in_<phase>`、`retry_after_network_stage_timeout_attempt1` 或 `ARTWORK_AM_VARIANT_FALLBACK`，随后 `ARTWORK_AM_READY`。
3. **换面接力，至少覆盖第二、第三首歌**：切歌后先等动画稳定，大→小、小→大各至少 5 次，再补一轮快速切换。
   期望每首歌都出现 `ARTWORK_SURFACE_PREVIEW` 与 `ARTWORK_SURFACE_HANDOVER warmDecoder=true`，
   同一首歌内 `clientEpoch` 不变，不再每次换面重新 `resolving`/`preparing`；新歌第一次挂载时 `clientEpoch` 增加属于正常。
   注意观察换面时是否仍会露出歌曲原静态封面。
4. 回归暂停/继续、切歌、解锁、息屏/AOD、关闭联调，检查静态回退与无残留层。

## 测试资产

- `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix11-debug.apk`
- `artifacts/Artwork-Provider-AM-20261003-fix11-debug.apk`

| 资产 | SHA256 |
| --- | --- |
| Bridge fix11 | `0F9A92D6F08DD2587636F5AC3C79209A4B768698DEDE41602AF5412FA068697A` |
| AM fix11 | `441B06A8AD826E912C1A46D5998AF919A70F107D9ACAAF78ADFD318619566E10` |

两包已通过 `scripts\dev.cmd install` 覆盖安装到已连接设备，adb 均返回 `Success`。
归档文件与对应构建输出哈希一致。SystemUI 尚需用户手动重启。
本地 Debug 测试包，不是正式发布版本。
