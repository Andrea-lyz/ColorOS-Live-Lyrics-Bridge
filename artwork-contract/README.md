# Artwork Provider contract v1

该 Android library 只提供封面资源协议与文件契约，不依赖 Bridge、歌词 Provider、网络库或播放器 Hook。
双方各自编译本库；通过系统 Binder 交换 framework 类型，不在宿主加载插件 DEX。

## 服务声明与身份

- 发现 action：`io.github.andrealtb.artwork.action.BIND_PROVIDER`。
- AIDL namespace：`io.github.andrealtb.artwork.contract`。
- service metadata：`io.github.andrealtb.artwork.PROTOCOL_MAJOR=1`。
- 可选 settings metadata：`io.github.andrealtb.artwork.SETTINGS_ACTIVITY`，必须是同包可访问显式 Activity。
- 消费者固定所选组件和当前签名集合，major 不兼容/签名变化拒绝；minor 可增添未知基础值字段。
- 服务每个事务验证实际 `Binder.getCallingUid()`；`onBind` 不能证明最终调用者身份。
- 不假定 SystemUI 拥有第三方签名权限，也不声称同 UID 不同包可以隔离。

## AIDL

```text
getCapabilities() -> Bundle
oneway resolve(requestId, queryBundle, callback)
oneway cancel(requestId)
openAsset(requestId, assetId) -> ParcelFileDescriptor
oneway releaseAsset(requestId, assetId)
oneway callback.onResult(requestId, resultBundle)
```

同步调用只能从后台 IPC executor 执行。服务 resolve 入口验证后排队，不在 Binder 线程下载或解码。
每个请求只接受一次最终结果；取消与死亡不是唯一正确性保障，客户端在各异步阶段复核本地绑定。
requestId 为进程实例内不复用的随机不透明字符串；assetId 是有期限租约句柄，**不是路径或 URL**。
租约绑定实际 UID、requestId 与 callback Binder；同 UID 客户端依赖不可猜 requestId 区分。
服务必须限制并发与租约数量，客户端必须限制同步调用线程数、队列和请求期限。

## Bundle 字段

所有 Bundle 都包含 `protocolMajor:int`、`protocolMinor:int`。只允许 String/int/long/boolean，
最多 48 个字段、key 最多 64 字符、Parcel 编码最多 16KiB；拒绝 FD、嵌套 Bundle、自定义 Parcelable/Serializable。
未知基础值字段在相同 major 下可忽略，已知字段须匹配精确类型，不默默转换数字或使用缺失默认值。

| 消息 | 字段（required） |
| --- | --- |
| capabilities | `acceptedMime:String=video/mp4`、`orientation:String=square`、`localTestOnly:boolean` |
| query | `title/artist/album/appleMusicUrl:String`、`durationMs:long`、`displayWidthPx/displayHeightPx/maxWidth/maxHeight:int`、`maxFileBytes:long`、`acceptedMime/orientation:String` |
| 所有 result | `status:String`、`reason:String`、`retryAfterMs:long` |
| READY 额外字段 | `assetId/resourceVersion/mime/codec:String`、`width/height:int`、`videoDurationMs/fileBytes/validForMs:long` |

query 文本单项最多 512 字符。title 不得为空；可选字段用空字符串和 `durationMs=0` 表示未知。
时长单位始终为毫秒。Apple URL 仅允许无 userinfo/port/fragment 的 `https://music.apple.com/<地区>/album|song/...`，
最多 2048 字符；URL 来源仍需调用端验证，结构校验不能证明它属于当前歌曲。

status：`READY / NO_MATCH / NO_MOTION / AMBIGUOUS / RETRY_LATER / NETWORK_BLOCKED / UNSUPPORTED / ERROR`。
只有 READY 含资源；只有 RETRY_LATER 可以有正的 retryAfterMs（最多一天）。
reason 只允许最多 64 字符的 `[a-z0-9_]*` 原因码，不放曲名、凭证、URL 或私人路径。
READY 不重复放 requestId，callback 第一参数即对应请求。

## FD 与视频

只允许完整缓存的只读、可 seek 普通文件；不得传可写描述符、pipe/socket、外部任意路径或网络流。
发送端与接收端各自关闭自己持有的 FD。消费者在拿到 FD 后重新验证，不能信任服务声明。

v1.1 媒体限制为 MP4、H.264/HEVC、方形 2% 容差、宽高 ≤1280px、文件 ≤20MiB、时长 ≤120 秒；
v1.0 消费者的 1080px 请求上限仍受尊重，不交付超出其查询限制的资源。
拒绝旋转、多个视频轨和加密视频样本。消费者可进一步降低查询尺寸和文件上限。
文件校验需 API 30+（公开的访问模式检查）；旧平台拒绝本路径，不使用隐藏 API 绕过。
视频样本扫描最多 10,000 个、单样本 4MiB、总压缩字节不超过文件大小、扫描期限 3 秒。
原生媒体解析的一次阻塞调用不能由该扫描期限强行中止，因此消费者还必须限制整体 IPC/验证线程数量。

validForMs 从回复时刻起计，最大 60 秒；消费者及时打开，不把它当永久缓存标识。
openAsset 交付只读 FD 后可以 releaseAsset；文件即使被缓存淘汰，已打开 FD 仍持有对应不可变版本 inode。
实际解码支持与首帧由 renderer 确认，失败回退静态。onPrepared 不能单独作为显示就绪信号。

生产插件必须返回 `localTestOnly=false`。本地 fixture 返回 true 并对任意查询提供同一测试视频；
该能力只能用于显式预览，SystemUI 自动展示必须拒绝它。

歌词、token、原始 media ID、设备标识、通知 key、Bitmap 和 ClassLoader 均不属于该协议。
歌曲/宿主 identity 与各 epoch 始终保留在 Bridge 本地。
