# Provider 4.0 与 Bridge 的原生交界

## 唯一数据边界

Provider 4.0 与 Bridge 不建立 Gradle、广播、Binder、ContentProvider 或文件中继关系。
两者唯一的数据交界是目标播放器自己的：

```text
MediaSession
  -> MediaMetadata["lyricInfo"]
  -> ColorOS SystemUI
  -> 可选 Bridge 通用增强
```

Provider 必须在播放器进程内完成歌词获取、解析、track generation、异步串曲门控、
封面安全拷贝和必要 replay，然后把标准 `lyricInfo` 写回播放器主 MediaSession。

Bridge 只运行在：

```text
system
com.android.systemui
```

Bridge 不知道 Provider applicationId、source id、sender 类型或内部 Hook 实现，也不会
接收、缓存、提升或重播 Provider 私有歌词广播。

## Provider 发布要求

1. 保留宿主 title、artist、album、duration、artwork、URI 与未知 metadata。
2. 使用空 typed Builder 拷贝 ColorOS 需要的字段，避免设备不兼容的 copy constructor。
3. `lyric` 提供官方逐行 lane；`rawLyric` 提供可信逐字 lane；翻译写入
   `translationLyric` 或宿主已有兼容字段。
4. 罗马音、音译和 pronunciation 不得进入翻译 lane。
5. payload 必须绑定稳定曲目身份和当前 generation；广告、投屏、预览与旧 session
   不得注入。
6. 无歌词、解析失败或 Parcel 过大时 fail-open，保留宿主原 metadata。
7. Provider 不改写与歌词无关的播放状态语义。

## Bridge 侧允许的播放器知识

Bridge 可以在 `PlayerSystemUiPolicy` 或具名 `players/<player>/` policy 中保留
有真机证据的 SystemUI/OPlus 特例，例如歌词入口、翻译 action 或厂商封面消费问题。
这些 policy 只能按播放器包名工作，不能依赖 Provider applicationId 或 payload source。

## 验收顺序

1. 不安装/禁用 Bridge，只启用 Provider，确认 ColorOS 原生锁屏歌词可消费。
2. 再启用 Bridge，确认逐字、翻译、AOD、样式和封面增强正常。
3. 日志中只能看到 native `lyricInfo` 消费，不得出现第二份 Bridge lyric 提交。
4. 静态检查 Bridge APK/DEX 不含旧直达 action、source 前缀、sender 字段或 Provider
   applicationId。
