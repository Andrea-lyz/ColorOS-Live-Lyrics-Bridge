# D1 AM fix10：阻塞响应头与换面租约冲突

日期：2026-10-03。对应 `lyrics-log-20261003-194310.txt`。fix9 尚未解决这两个设备问题。

## 本次证据

小卡已通过宿主门禁，歌曲与专辑也已匹配成功。问题出现在所选 360×360 视频档的传输：

```text
19:43:41.975  ARTWORK_AM_VARIANT width=360 height=360
19:43:42.002  video_file_started
19:44:04.268  video_file_network_headers_timeout
19:44:04.269  retry_after_network_headers_timeout_attempt1
```

等待响应头约 22.3 秒。fix9 的 15 秒上限只在读到正文之后检查，因此未限制这个阻塞阶段；
响应头超时后还会重复请求同一资源。用户切到大封面后，同一已匹配专辑的 1080 档于
`19:44:04.653` 开始下载、`19:44:05.319` 完成。这支持“所选档的传输失败”的解释，
不能据此认定某专辑永久不可用。日志外其他专辑的表现仍以用户设备观察为准。

换面则是保留后被旧租约销毁：

```text
19:44:06.734  ARTWORK_SURFACE_HELD sameSong=true decoderKept=true
19:44:06.760  ARTWORK_TEST_RENDER_STATE state=stale_render clientEpoch=3
19:44:07.180  ARTWORK_TEST_MOUNTED surface=LOCKSCREEN_CARD clientEpoch=5
```

旧 `ArtworkRenderGuard` 仍要求可见表面满足完整显示条件。过渡期间 `current=null`，下一次视频帧回调
便判定它失效。后续每次换面都重新请求资源、准备播放器；`ARTWORK_SURFACE_HANDOVER` 为 0。

## 实现

### AM 插件

- 为整次 HTTP 阶段增加独立计时器，覆盖连接、响应头、正文和重定向。到期断开该阶段的连接，
  并将断连产生的 EOF/reset 统一识别为 `network_stage_timeout`。
- 视频传输错误直接交回 variant 循环，避免重试同一慢视频耗尽请求预算。目录与文本请求保留原有重试。
- 首选仍是最小足够的 AVC SDR 方形档；该档传输失败后，优先尝试另一尺寸的较大可用档，
  同尺寸的其他码率档排后。最大尺寸、文件上限及最多 4 档的约束保留。
- 新增 `ARTWORK_AM_VARIANT_FALLBACK reason=...`。仅传输错误调整排序；格式错误继续原有换档，
  429、访问拒绝等上游状态继续原有处理。

### Bridge

- 过渡空档使用新的同曲租约，保留播放器与原视频层。原显示租约保持不可复活的语义。
- 租约仅接受同 session、同 track generation、同插件实例的几何、隐藏或无尺寸空档，
  最多 3 秒。暂停、解锁、息屏、身份失效和未验证的原生效果不能凭过渡租约继续播放。
- 原视频层跟随原生视图的位移、缩放、透明度和旋转；新表面进入时使用一张 512×512 视频帧作临时预览，
  沿用该表面的原生形状与遮罩。原生效果仍未通过门禁时，继续保留其官方画面。
- 新表面稳定后接管同一个播放器；首个实际视频帧到达时同步设为完全不透明，再移除预览。
  首帧等待也有 3 秒上限。首帧成功、失败、超时或生命周期终止均清理临时预览。
- 新增 `ARTWORK_SURFACE_PREVIEW`、`ARTWORK_SURFACE_HOLD_EXPIRED`、`ARTWORK_SURFACE_HOLD_FAILED`，
  渲染日志使用当前接管表面的名称，成功换面时 `clientEpoch` 保持不变。

网络仍仅在独立 AM 插件内；Bridge 未增加 INTERNET 或下载逻辑。已触发下载完成缓存的策略沿用 fix6。

主要源码入口（仓库相对路径，行号对应本次实现）：

- `artwork-provider-am/src/main/java/io/github/andrealtb/artwork/am/AmNetwork.java:162`：整阶段计时器。
- `artwork-provider-am/src/main/java/io/github/andrealtb/artwork/am/AmHls.java:65`：传输失败后的档位排序。
- `app/src/main/java/io/github/andrealtb/lockscreenlyrics/systemui/artwork/ArtworkImmersivePlayback.java:360`：过渡租约与预览。
- `app/src/main/java/io/github/andrealtb/lockscreenlyrics/systemui/artwork/DynamicArtworkRenderer.java:71`：替换租约、保留解码器。
- `app/src/main/java/io/github/andrealtb/lockscreenlyrics/systemui/artwork/DynamicArtworkRenderer.java:229`：首帧同步接管。

## 本地验证与边界

- Bridge：718 项，712 通过、6 项原有 fixture 跳过，0 失败；Lint 0 errors / 48 warnings。
- AM：66 项全部通过；Lint 0 errors / 4 warnings。
- 新测试实际模拟阻塞响应头、读到一半的正文，验证计时器断连、停止重复当前视频请求、
  后续文件覆盖部分下载；验证 variant 回退和换面租约的身份、生命周期及时间上限。
- 两个 Debug APK 构建成功后安装。本次未代为重启 SystemUI、播放或清除设备缓存。
- JVM 测试不证明 ColorOS 画面无闪烁，也不证明真实 CDN 在任意网络下都能响应；最终需要设备复测。

## 复测顺序

1. **先手动重启 SystemUI**，重新开启 Bridge 的“AM 锁屏联调”。插件设置保持启用。
2. **先测问题专辑的小卡首次缓存。**在 AM 插件内清缓存，让锁屏初始就是小卡；选此前复现曲，
   保持播放，不暂停、不切大小卡，观察 30 秒。期望小卡自动出现动画。
   慢档应在约 15 秒出现 `video_file_network_stage_timeout`、`ARTWORK_AM_VARIANT_FALLBACK` 和另一个尺寸，
   然后 `ARTWORK_AM_READY`、小卡 `state=playing`。调度与连接关闭可能有少量误差。
3. **视频播放后测换面。**大→小、小→大各至少 5 次，先等每次动画稳定，再补一轮快速切换。
   期望日志出现 `ARTWORK_SURFACE_HELD`、`ARTWORK_SURFACE_PREVIEW`、
   `ARTWORK_SURFACE_HANDOVER warmDecoder=true`，同一轮 `clientEpoch` 保持不变，
   不再出现保留之后马上 `stale_render` 和重新 `preparing`。
   新视频首帧前允许短暂保持上一视频帧，设备画面应检查是否仍露出歌曲原静态封面。
4. 回归暂停/继续、切歌、解锁、息屏/AOD、关闭联调，检查静态回退与无残留层。
   下载期间离开画面后，已触发资源仍应完成缓存。

## 测试资产

- `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix10-debug.apk`
- `artifacts/Artwork-Provider-AM-20261003-fix10-debug.apk`

| 资产 | SHA256 |
| --- | --- |
| Bridge fix10 | `43561933CFC993F64E228B43A1C433044E53DCDBE4983D4BBF0D9C117E1FCC33` |
| AM fix10 | `E2D2C8608158B17240B17839F69142B0A98F4D5A281F28A911460D5D36CE13C9` |

两包已通过 `scripts\dev.cmd install` 覆盖安装到已连接设备，adb 均返回 `Success`。
归档文件与对应构建输出哈希一致。SystemUI 尚需用户手动重启。
本地 Debug 测试包，不是正式发布版本。
