# D1 AM fix3：网络恢复、跨尺寸缓存与布局中的资源请求

日期：2026-10-03。对应 `lyrics-log-20261003-173246.txt`，本地 Debug 候选，端到端需新包设备复测。

## 本轮证据与修复

日志中的 READY 均随即进入播放，未证实“READY 已到却等待暂停”。
阻断窗口有 network_io/NETWORK_BLOCKED，以及退避期间候选变为 none；没有 RETRY_FIRED。
no body, no crime 小卡片已被选中并核对歌曲，但小尺寸请求命中 network_io；
大封面 1080 缓存不满足旧的“目标尺寸索引命中”，直到 17:35:55 新下载 360 档才播放。

fix3 的具体变化：

1. **符合上限的同专辑视频跨尺寸复用。**增加按市场/albumId 的已验证视频清单（最多 8 个条目，24h TTL）。
   每次仍先逐首核对当前歌曲与确认过的专辑曲目表，再按真实宽高、字节数、文件存在性选缓存并复验 MP4。
   优先最小足够尺寸；没有足够尺寸时可用允许范围内最大的较低尺寸。
   未缓存小档时可直接用已缓存大档，且必须满足查询 maxWidth/maxHeight/maxFileBytes。
   不把大档强写成小档的优选索引，不新增后台下载或并行解码器。
2. **保留旧缓存的迁移。**既有尺寸索引命中后登记视频清单；小卡请求也会检查旧 1080×1080 大封面索引。
   只把通过文件复验、属于已确认 albumId 的文件登记，不遍历任意外部路径，不要求清数据。
3. **网络恢复主动唤醒。**Bridge 监听默认网络从未验证到验证、或切换至另一已验证网络，合并 500ms 内的通知。
   当前正在解析或等待网络的请求会重新检查最新锁屏/会话/宿主门禁；已经播放的视频不因重复网络通知重启。
   若布局暂不可挂载，恢复信号保留到当前有效候选再次出现。
4. **只重置传输错误缓存。**AM 插件以默认网络的验证状态/实例维护进程内 epoch，过期 epoch 的 network_io/连接/响应头/正文超时缓存可重新查询。
   网络恢复过程中产生的旧 epoch 失败不写入新网络缓存；429、访问拒绝、目录歧义和无动画仍保留既有策略。
5. **资源获取与视觉挂载分离。**同宿主同 stamp、ready/playing、仍在交互锁屏中的可见视图，仅因尺寸/几何/原生过渡暂不可显示时，
   撤下临时视频层但允许既有资源请求完成。
   资源到达时重新核对完整绑定和门禁，可显示则重建挂载；否则关闭 FD、清理请求并安排一次有界重检查。
   不持有等待展示的 FD，不让没有资源的旧挂载长期阻止重试。
6. **重试与撤销边界。**同曲短暂布局变化保留退避截止时间；计时器唤醒时重新收集候选，不直接复活旧挂载。
   暂停、换歌/换绑定、解锁、息屏、detach、关闭仍取消相应任务和播放器。
   网络恢复可提前重试传输问题；普通计时器严格检查截止时间，提前唤醒会再次安排剩余时间。
7. **诊断。**每个 surface 单独去重记录具体门禁原因；HTTP 日志区分 catalog/web_album/playlist/video_file 阶段与连接/响应头/正文超时。
   不输出曲名、完整 URL、网络标识、网络 epoch token 或原始 media ID。

Bridge 新增普通 `ACCESS_NETWORK_STATE` 权限用于只读状态监听，仍没有 INTERNET。
下载仍只在独立插件执行；scope 保持 system/com.android.systemui，Binder major/minor、歌词 Providers 不变。

## 测试包

- `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix3-debug.apk`
- `artifacts/Artwork-Provider-AM-20261003-fix3-debug.apk`

184839 实测发现 fix3 的逐候选门禁日志会占用满每秒诊断预算，导致挂载/播放事件被丢弃；
该诊断问题在 [fix4](DYNAMIC-ARTWORK-AM-FIX4-TEST.zh-CN.md) 修复，AM 插件无需更新。

**两个都更新**，用户自行重启 SystemUI，再重新开启 AM 锁屏联调；无需清数据或导入视频。
插件来源、市场、计费网络策略和脱敏诊断仍独立设置。
fix2 的 Explicit/Clean 封面等价和 fix1 的同长切歌确认继续保留。

已通过 apksigner 验证，signer SHA256 与此前候选相同：
`5a6fd43fc11b4c70ff12b6f82a7261c2ed9c4945692b3d818ca50bc64c8dd869`。

| 资产 | 文件 SHA256 |
| --- | --- |
| Bridge fix3 | `C161004AB4F71B9CFB5B88488777802B58ADFF657DAD32A3023BCB247F25E03F` |
| AM fix3 | `61757F2BA0801302374FB7A32E0D2EF7B17E2B04091FB294512A678150E20CB2` |

## 最小复测

1. **先缓存大封面，再切小卡。**联网播放 no body, no crime，等大封面 playing 后进入小卡布局。
   原先未缓存低档也应从已有同专辑缓存播放，日志可见 CROSS_SIZE_CACHE。
   不暂停、不解锁。如果已存在低档，优先低档命中同样算通过。
2. **断网切大小封面。**保持已缓存且身份可确认的同专辑，断网来回切换大/小封面，确认不因尺寸索引缺失长期静态。
   从未确认的专辑仍不直接套用别的专辑视频。
3. **网络恢复。**对未缓存专辑断网查询，保持播放与锁屏，再恢复满足插件策略的网络。
   不手动暂停，观察 NETWORK_RECOVERED、HTTP/资源请求和 playing。
   只有物理连上 Wi-Fi、尚未完成系统验证或仍是禁止的计费网络，不代表可以下载。
4. **首次下载的短暂布局变化。**联网选未缓存专辑，播放不暂停，观察资源请求是否在同曲过渡期间继续。
   可快速切大小封面验证取消/重选，最终只允许当前有效位置播放。
5. **回归。**Style/Blank Space 同长互切、Showgirl Explicit/Clean、A→B→C 快切、暂停、解锁、息屏/AOD、关闭联调。
   暂停或退出之后不得继续准备旧曲播放器；重复网络通知不得重启已播放动画。

卡片可能复用 1080 档，仍受 1080px/20MiB 上限约束；本轮没有后台自动补下载小档。
跨尺寸回放的画质/解码成本、实际 SDK 网络回调与布局收尾仍需本机观察，JVM 不能替代它们。
C17 双槽、正式持久开关和长期 GPU/FD 预算继续未完成。

## 日志

插件脱敏诊断与 Bridge MEDIA 调试均开启，使用完整新窗口：

```powershell
.\scripts\capture-lyrics-log.ps1 -Provider bridge
```

关键新增事件：

```text
ARTWORK_GATE_IMMERSIVE / ARTWORK_GATE_LOCKSCREEN_CARD
  reason=eligible|binding_not_ready|paused|hidden|no_bounds|geometry_transition|native_transition|lockscreen_inactive|...
ARTWORK_RESOURCE_WAIT sameBinding=true visualSuspended=true
ARTWORK_ASSET_DROPPED phase=mount_gate currentEpoch=...
ARTWORK_RETRY_CONTEXT retained=...
ARTWORK_NETWORK_RECOVERED retryCurrent=true
ARTWORK_RETRY_SCHEDULED / ARTWORK_RETRY_FIRED
ARTWORK_AM_CROSS_SIZE_CACHE width=... height=... bytes=...
ARTWORK_AM_HTTP reason=catalog_started|playlist_complete|video_file_network_headers_timeout|...
```

最终以 ARTWORK_TEST_RENDER_STATE state=playing 与用户画面为准。
提供首次静态卡住时间，注明首次下载/恢复网络/大小切换，以及期间是否暂停或息屏。

## 本地验证

Bridge 708 项（702 通过、6 个已有 fixture 跳过），AM 55 项全部通过；
Lint 分别 0 errors/48 warnings、0 errors/4 warnings，无新增 baseline、检查禁用或权限警告抑制。
Debug 构建通过。测试覆盖网络恢复边沿/重复回调、不绕过 429、旧 transport 失败失效、缓存尺寸/字节限制、市场/专辑隔离、选择优先级和 pin 生命周期。
真实 ConnectivityManager/controller/视图/媒体行为必须完成本轮设备验收。
本次没有安装、操作设备、提交、推送或发布。
