# 动态音乐封面：独立 Artwork Provider 与 Bridge 展示适配完整方案

- 归档日期：2026-10-02。
- 状态：**设计归档；后续已开始实施离线协议与 App 预览，SystemUI 动态封面尚未实现或验收。** 实际进度与验证见 [实施记录](DYNAMIC-ARTWORK-IMPLEMENTATION.zh-CN.md)。
- 最终架构：独立、可选的 Artwork Provider APK 提供资源；Bridge 负责 SystemUI 封面宿主适配与播放。
- Bridge 源码基线：`df7cf393d3a9ed928f4a409911179d989ccf2054`。
- 歌词 Providers 参考基线：`c86d2a363d4e490f2acda6ad9fc559ae48c31f5c`。
- 本文保留归档时的方案与静态调查。下文“拟新增”等描述为设计基线；当前已实现类型、冻结协议与剩余差距以实施记录及源码为准。
- 不包含安装、真机操作、推送、发版或长期记忆写入授权。

## 1. 决策与目标

### 1.1 最终决策

不把查询、网络库、下载、缓存、Apple token 处理或后端服务放进 Bridge。
新增独立的 Artwork Provider 普通 Android APK，通过版本化 Binder 协议向 Bridge 提供已准备好的视频资源。
用户在 Bridge 设置中选择并启用插件，未安装插件或未启用时保持现有静态封面行为。

| 决策 | 内容 |
| --- | --- |
| AD-01 | Artwork Provider 独立安装、更新和卸载；不是现有播放器歌词 Provider 的新发布 lane |
| AD-02 | Bridge 保持 `system` / `com.android.systemui` scope；动态封面只在 SystemUI 展示端运行 |
| AD-03 | Bridge 不增加 INTERNET 权限，不加载插件 DEX，不把第三方网络库注入 SystemUI |
| AD-04 | 第一款资源插件面向 Apple Music 动态封面，数据源通过可替换 Resolver 接口隔离 |
| AD-05 | 首版资源格式为缓存完成的方形 MP4，通过只读、可 seek 的文件描述符传递 |
| AD-06 | C16/C17 用实际结构与能力匹配；ROM 名称、Android SDK 和 APK 版本仅作候选提示与诊断信息 |
| AD-07 | 普通锁屏媒体卡和沉浸页分别启用；AOD 使用官方静态封面，首版不扩展到控制中心、胶囊或全屏动态背景 |
| AD-08 | 任何不支持、错误、资源过期或身份不明确的状态均回退到当前宿主静态封面 |

### 1.2 目标与首版范围

目标是在 SystemUI 已展示的有效媒体卡上，为匹配到动态专辑封面的当前歌曲播放静音循环视频。
不要求播放器安装歌词 Provider，不要求当前歌曲具有 `lyricInfo`，也不要求宿主有某个特定包名。
实际覆盖范围仍受宿主 View、会话关联和资源匹配能力限制，不能提前宣称“所有播放器全局接管”。

首版包含：

1. Bridge 设置页插件发现、选择、启用与能力状态。
2. C16/C17 普通锁屏媒体卡、沉浸页大封面的独立能力解析。
3. 当前卡片与精确会话绑定、歌曲身份与异步结果过期门控。
4. 独立资源插件的匹配、下载、缓存与取消。
5. framework `MediaPlayer + TextureView`、首帧门禁、暂停、息屏和资源回收。
6. 静态回退、协议版本检查、脱敏诊断和设备验收。

首版不包含：HLS 直连播放、Media3 注入、视频写入 artwork URI、修改播放器 metadata、
伪造 PlaybackState、自动换歌词模式、强制 AOD 状态、常驻前台服务、后台预加载整个播放列表。

## 2. 术语与架构边界

| 名称 | 定义 |
| --- | --- |
| Lyric Provider | 现有播放器进程模块，向播放器自己的 MediaSession 追加原生 `lyricInfo` |
| Artwork Provider | 拟新增的普通 Android 资源插件，解析并提供动态封面；没有播放器 Hook 或 LSPosed scope |
| Android ContentProvider | Android framework 组件；不等同于本文的 Artwork Provider。首版优先 bound service |
| SystemUIPlugin | OPlus 系统自带插件，持有媒体卡 View 与模型；不是动态封面资源插件 |
| Artwork host | 经过验证的媒体 section、封面 View、容器和当前显示槽位 |
| Song identity | 当前歌曲身份；不同于通知 key、Seedling playerId 或卡片 uniqueId |

现有歌词链路保持：

```text
Lyric Provider -> 播放器 MediaSession[lyricInfo] -> ColorOS SystemUI -> 可选 Bridge 歌词增强
```

新增封面链路：

```text
播放器自己的 MediaSession
          |
          v
ColorOS SystemUI 媒体对象 / 当前媒体卡
          |
          v
Bridge ArtworkHostBinding + ArtworkSessionRegistry
          | 标准化歌曲查询 + 显示要求 + requestId
          v
独立 Artwork Provider Service
          | 匹配 / 联网 / 下载 / 校验 / 缓存
          v
资源状态 + assetId -> 只读可 seek 的 ParcelFileDescriptor
          |
          v
Bridge 复核会话、歌曲、请求和 surface -> TextureView 播放
          |
          +-- 失败 / 切歌 / 息屏 / 销毁 -> 当前宿主静态封面
```

### 2.1 职责分工

| Bridge | Artwork Provider |
| --- | --- |
| 当前媒体卡、会话和封面宿主识别 | 歌曲候选与专辑版本匹配 |
| 展示 generation、绑定 epoch 和取消 | Apple/其他来源 Resolver、限流、重试 |
| C16/C17 加载、模型、View 和过渡适配 | 分辨率与视频资源选择、下载、文件验证 |
| 显示尺寸、支持格式和资源上限提示 | Wi-Fi 策略、缓存容量、资源设置和清理 |
| 复核结果、播放和生命周期 | 按请求取消，提供只读资源租约 |
| 插件选择和展示配置 | 插件自身设置页、数据源信息及联网说明 |

现有 Lyric Providers 不新增封面广播、Binder 调用、Bridge 类依赖或第二份歌曲发布通道。
Artwork Provider 可以独立安装和打开设置/预览页面，Bridge 是其可选展示消费者。
Bridge 只知道用户选定的插件组件与协议，不维护播放器包到资源插件的固定映射。

### 2.2 项目规则的待实施契约变化

当前工作区 `AGENTS.md:35-36` 与 `docs/LYRIC_PROVIDER_BRIDGE.zh-CN.md` 禁止歌词 Provider 与 Bridge 的私有 transport。
新增封面 Binder 接口是一项明确的架构扩展，不能因为称为“插件”就视为已有通道。

实施时应同步准确区分：

> Lyric Provider 与 Bridge 的唯一歌词数据交界仍是播放器原生 `lyricInfo`。
> 独立 Artwork Provider 可以使用公开、版本化的封面资源服务协议，由用户选择并启用。
> 该协议不承载歌词、播放器 Hook 实现或 Provider source 映射，不恢复旧私有歌词传输。

以上为规则更新草案，**本次没有修改 AGENTS.md 或现有架构约束文件，也没有使接口生效**。
新增能力不得以删除既有架构守卫的方式通过检查。

## 3. 当前源码事实与可复用入口

以下行号基于归档源码基线，后续以符号和实际文件内容重新核对。
Bridge 源码目录记为 `B = app/src/main/java/io/github/andrealtb/lockscreenlyrics/`。

| 证据 | 静态事实 | 设计影响 |
| --- | --- | --- |
| `app/src/main/AndroidManifest.xml:1-8` | 没有 INTERNET 权限；已有签名级设置变更权限 | Bridge 保持无网络权限，插件独立申请网络能力 |
| `app/src/main/resources/META-INF/xposed/scope.list` | 只有 system、com.android.systemui | 动态封面不扩大播放器 scope |
| `B/LockscreenLyricsModule.java:4035-4087` | 现有 controller 绑定受当前歌词 Provider 门禁限制；callback 只处理状态及会话销毁 | 新建独立封面 session registry，不扩大歌词门禁 |
| `B/SystemUiDexKitAdapter.java:525` | Targets 面向歌词/动作/Seedling Bundle | artwork resolver 独立输出能力，不能依赖歌词 Targets 全部成功 |
| `B/bootstrap/SystemUiRuntimeBootstrap.java:55-60` | 歌词 Targets 为空时提前返回 | 封面 bootstrap 与歌词解析独立，插件缺失不影响歌词 |
| `B/diagnostics/ArtworkDiagnostics.java:74-79` | 资源名命中或尺寸 >=200 会被视为可能封面 | 只作探针；正式接管验证 section、模型、View 和会话 |
| `B/LockscreenLyricsModule.java:6881-7020` | 已有 ClassLoader 构造器/工厂入口、ready 检查、旧 Hook 注销 | 复用生命周期，插件 generation 切换时额外释放封面资源 |
| `B/OplusPluginDexKitAdapter.java:72-110,182-216` | 已兼容 MediaModel/MediaInfo 歌词字段；artwork 重建仍要求 C16 StaticIcon 形状 | 现有 C17 歌词适配不是动态封面适配，不能复制 C16 模型重建 |
| `B/LockscreenLyricsModule.java:10589-10600` | 设置接收器使用签名权限保护 | 新展示配置沿用保护方式，保持独立配置域 |
| `B/LyricContentCleanupConfigProvider.java:30-47` | 配置通过 pipe 输出 | 不复用该 pipe 作为 MP4；视频必须支持 seek |

参考 Providers 的 `provider-core/.../policy/TrackIdentityPolicy.kt`、`TrackGenerationPolicy.kt`，
复用稳定 ID 优先、时长容差、提交复核的设计思想；不引入 Provider runtime/Gradle 依赖。

## 4. C16/C17 静态样本与证据

### 4.1 本地路径

下列路径相对包含 Bridge、Providers 和 PlayerSource 的工作区根目录，不依赖本地盘符。

| 标记 | 工作区相对路径 |
| --- | --- |
| S16 | `PlayerSource/SystemUI/系统界面_16.99.12/java_src/` |
| P16 | `PlayerSource/SystemUIPlugin/jadx-user/sources/` |
| R16 | `PlayerSource/SystemUIPlugin/jadx-user/resources/res/` |
| S17 | `PlayerSource/C17/jadx-systemui/sources/` |
| P17 | `PlayerSource/C17/jadx-plugin/sources/` |
| R17 | `PlayerSource/C17/jadx-plugin/resources/res/` |

这些是反编译参考，不是可构建的官方源码。JADX 占位方法不作为完整实现证明；
上次静态调查对 C16 封面入口和 TransitionDrawable 调用另外解析了真实 DEX 指令。

### 4.2 APK 身份与 SHA-256

2026-10-02 再次用本地 `aapt2 dump badging` 和 SHA-256 核对下列样本。
compile/target SDK 是 APK 声明，不是当前设备 Android/ROM 版本取证。

| 工作区相对 APK 路径 | versionName / versionCode | SHA-256 |
| --- | --- | --- |
| `PlayerSource/SystemUI/系统界面_16.99.12.apk` | 16.99.12 / 169912 | `907D315850AE0D697C5DBE36D11104360D73AC8CFD24B383C14ED36BBD5358C2` |
| `PlayerSource/SystemUI/系统界面_16.99.12_New.apk` | 16.99.12 / 169912 | `6616C58A19708BB7255F7DFF83D6A646961CDC441B2AFE70196492B1D9457AD4` |
| `PlayerSource/SystemUIPlugin/SystemUIPlugin_16.001.002_old.apk` | 16.001.002 / 16001002 | `B4E5FBA2E3ADB6D45CB03D68D06A7B0BE95CBAB0976B030E07750D0F3586ED7D` |
| `PlayerSource/SystemUIPlugin/SystemUIPlugin_16.001.002_new.apk` | 16.001.002 / 16001002 | `AD74975487C99D7A9ED29EBF8F5F644283A3AD096F89B75E2BCA3003CCA1BE7A` |
| `PlayerSource/SystemUIPlugin/SystemUIPlugin_16.001.002_new10.apk` | 16.001.002 / 16001002 | `C68BF570B8580690AB2761A73DB6B5B23315B4F4ADD89E42BFE25BAA45D72B74` |
| `PlayerSource/SystemUIPlugin/SystemUIPlugin_16.001.002_user.apk` | 16.001.002 / 16001002 | `102574EDA9B856F8F12729AB96CE5B1E7E5994635FB072F02B543536DE6EBEC7` |
| `PlayerSource/SystemUIPlugin/SystemUIPlugin_16.001.002_user1.apk` | 16.001.002 / 16001002 | `102574EDA9B856F8F12729AB96CE5B1E7E5994635FB072F02B543536DE6EBEC7` |
| `PlayerSource/C17/C17-SystemUI.apk` | 17.99.02 / 179902 | `DA83AE63B6CFC43A4CE57F21EE7A54444E14DADA85B24AD36D2FE74181F0A927` |
| `PlayerSource/C17/C17-SystemUIPlugin.apk` | 17.000.002 / 17000002 | `2CDD17E165FCFD2E257BF863C70D85F3978534381F7019038F1A5FAD912667EB` |

DEX 比对已确认：两份 16.99.12 SystemUI 的代码不同；C16 插件 old/new/new10/user 的代码不同；
user1 与 user 完全相同。文件名 old/new/user 只是本地样本标签，不是运行时版本契约。
没有证明上述 SystemUI 与所有插件样本均为官方可任意交叉组合的安装配对。

### 4.3 普通封面方法与资源 ID

| 样本 | DEX 中普通封面更新方法 | 大封面更新线索 |
| --- | --- | --- |
| C16 user/user1 | `f7.l5.x(m6.v):void` | `f7.b3.a(Object,Continuation):Object` |
| C16 old | `f7.m5.x(m6.v):void` | `f7.c3.a(Object,Continuation):Object` |
| C16 new/new10 | `f7.p5.x(m6.v):void` | `f7.d3.b(Object,Continuation):Object` |
| C17 | `com.oplus.systemui.plugins.shared.template.section.media.s1.F(model.y):void` | DEX `...section.media.e.e(Drawable,boolean)`；JADX 文件名 `C0890e.java` |

普通卡共同日志锚点：`updateAlbumArtIcon iconBitmap is null`。
C16 大封面线索为 `updateAlbumArtIcon lyricLines:` 并实际调用 `TransitionDrawable.startTransition(int)`；
C17 为 `[updateAlbumArtIcon] animate:`。协程 collector 是静态线索，正式适配优先定位其 owner 和宿主生命周期，
不能只拦截 collector 就假定已获得当前卡片绑定。

| 资源 | C16 插件样本 | C17 插件样本 |
| --- | --- | --- |
| img_album_art | `0x7f090104` | `0x7f0900e5` |
| img_large_album_art | `0x7f090109` | `0x7f0900e9` |
| img_large_album_art_container | `0x7f09010a` | `0x7f0900ea` |
| img_large_album_art_next | 上述 C16 样本资源表未见同名项 | `0x7f0900eb` |

正式代码不硬编码这些数字 ID 或混淆名。资源名、section 字符串、字段类型、参数类型及实际父容器共同验证。
普通卡封面更新会在 bitmap 相同时提前返回（P16 `f7/l5.java:922`；P17 `.../s1.java:550`），
因此同专辑切歌不能只靠图片更新 Hook 检测。

### 4.4 模型、加载入口与几何差异

| 能力 | C16 静态事实 | C17 静态事实 |
| --- | --- | --- |
| 媒体模型 | `m6.t` 平铺身份、封面、duration、currentTime、isPlayingState | `u` 包装 `t MediaInfo` 与 `v MediaProgress`，时长及状态在 progress |
| StaticIcon | 有 Icon、Drawable、Bitmap 与 icon model | `A` 没有 Icon，新增 artResult；不能用 C16 构造器重建 |
| ClassLoader | S16 `OPlusPluginClassLoader.java:15` 构造器内赋 mBase/mPackages | S17 `PluginInstance.java:206-225` 工厂返回前赋值；实际 DEX 没有 loader 自身构造器 |
| user 大封面父容器 | R16 `layout/normal_card_media_player_immersive.xml:63` RelativeLayout，单 ImageView | R17 `layout/media_immersive_card_section.xml:45` FrameLayout，双 ImageView |
| 大封面形状类 | P16 SmartShapeableImageView 继承 Material ShapeableImageView | P17 同名类继承 CustomLottieView，具有 setAlbumAlpha/形状变化接口 |
| 大封面过渡 | C16 入口的真实 DEX 调用 TransitionDrawable.startTransition | P17 `.../C0890e.java:241-287` 管理双 View、pending drawable、250ms alpha 过渡 |

真实 DEX 字段表进一步表明 C16 本身存在形状布局差异：old/new/new10 的 SmartShapeableImageView 有
`J:Drawable`、`K:Function1` 等字段；user 的 `J/K` 为 int，完整字段结构不同。
这证明必须检查实际能力，但不单凭字段类型推断未读取的方法语义。

C16 user 普通卡封面直接位于 ConstraintLayout 派生根布局（R16 `normal_card_media_player_page_root.xml:17`）；
C17 普通卡位于 header RelativeLayout（R17 `media_card_section.xml:19`）。
视频层的挂载参数不能复用同一份容器布局假设。

### 4.5 取歌入口与身份关联

S16 `media/seedling/MediaSeedling.java:389-418`、S17 同类 `:360-388` 均按 UMS 开关分流。
两代 `media/seedling/utils/FeatureOption.java` 的 `persist.debug.mc.seedling` 默认值为 false。
UMS 关闭走 LiveAlert，只有另一分支调用 `mediaDataToBundle()`；不能把 Bundle Hook 作为唯一输入。
实际设备开关值未读取，不主动改写该开关。

优先闭合分流之前的媒体对象链：

```text
OplusMediaSeedlingController / MediaData 更新
  -> SeedlingMediaData(playerId, token, userId, package, song, artist, duration)
  -> LiveAlert 当前卡片 uniqueId / section 当前模型
  -> 精确 MediaSession.Token
  -> MediaController 的标准 metadata / playback callback
```

两代 `OplusMediaSeedlingController.getSortedEntries()` 都返回当前媒体对象列表：S16 `:888`、S17 `:662`。
该列表排除非 active 项，不能据此假定恢复历史卡片也能动态播放；关联不到有效会话时保留静态。
S17 `MediaLiveAlertController.java:1154` 将 playerId 写入卡片 uniqueId；
两代 `AbsMediaClient` 从 MediaData 保留 token、userId 和时长（S16 `:643-647`；S17 `:1163-1167`）。

Seedling Bundle 的 `mediaId` 同样来自 playerId：S16 `SeedlingMediaDataHandleUtils.java:398`、S17 同类 `:458`。
它不是歌曲 media ID。C17 parser 部分方法存在 JADX 占位，卡片到模型的完整映射仍须补字节码与设备证据，
不能把上述关联候选链写成已完成的 runtime。

### 4.6 能力匹配与失败隔离

`ArtworkCompatibilityResolver` 拟输出独立 capability：

```text
loaderEntry       = constructor | factory | verified-host-fallback
mediaSource       = pre-UMS-seedling | exact-card-controller
modelShape        = flat | info-and-progress
cardHost          = supported | unresolved
immersiveHost     = supported | unresolved
containerShape    = relative | frame | verified-other
transition        = drawable-crossfade | dual-image
shapeAccess       = verified-material | verified-custom | unsupported
sessionAssociation= exact | unavailable
```

规则：

1. 记录实际 SystemUI 与插件包版本、Android SDK、样本/能力标识；版本仅缩小候选，不决定成功。
2. 方法/字段匹配要求唯一、类型正确、owner 正确，不使用“最后一个 boolean”或模糊尺寸猜测。
3. 宿主实例创建后验证资源、父容器、封面槽位和当前模型，不把所有同名 View 全局接管。
4. 普通卡和沉浸页能力独立。形状或 session 关联缺失时关闭该 surface，不影响歌词和其他 surface。
5. 未知 APK 只有通过已定义完整能力契约才启用；否则报告不支持并保留静态，不能随意选择最相似指纹。
6. 按实际 ClassLoader generation 缓存；更换 loader 清理 Hook、反射结果、View、回调和播放器。
7. 官方插件已缓存、构造早于注册等情况，补装必须通过有效 artwork host，不能只等 LyricsRecyclerView attach。
8. 裁剪类型是插件 ClassLoader 中的对象，禁止把它强制 cast 为 Bridge 自带 Material 类型；输出中立几何描述。

静态识别、实现存在、本地验证和用户设备验收是四种状态，设置页不能用“版本命中”显示“已兼容”。

## 5. 插件发现、选择与生命周期

拟定发现 action：`io.github.andrealtb.artwork.action.BIND_PROVIDER`。
拟定协议包：`io.github.andrealtb.artwork.contract`。这些名称在接口实现阶段冻结，目前不增加组件或发布身份。

1. 插件通过 exported service + 统一 action + 协议 metadata 声明能力。
2. Bridge 设置 App 增加该 action 的 package visibility `<queries>`，通过 PackageManager 查询服务。
3. 展示插件名称、来源、协议版本、签名身份和自身设置入口；用户明确选择一个组件。
4. 记录组件、已接受的签名身份与配置 revision，通过当前受保护设置通道同步至 SystemUI。
5. SystemUI 使用精确 ComponentName 绑定，不使用隐式 Intent 选择任意返回服务。
6. 在有效且启用的封面 surface 上持有绑定；短暂过渡可保留连接，无需求后注销回调、释放请求并解绑。
7. 更新、卸载、组件移除、签名改变或协议不兼容：停止资源使用并提示重新选择/启用；不自动换到另一插件。

支持多来源通过选择不同插件或插件内部 Resolver 实现，不新增逐播放器的 Bridge 注册表。
首版同时只选择一个资源插件，不做失败后自动逐个插件联网。

服务使用 `BIND_AUTO_CREATE` 等正常 bound service 机制，网络权限只在插件中。
首版不要求前台服务、通知监听授权、Root 或常驻进程；绑定启动与后台限制在 C16/C17 设备上单独验证。
系统停止插件进程是可恢复失败，不能为了可用性盲目拉起常驻服务。

## 6. Binder 与资源契约 v1（拟定）

### 6.1 接口形状

```text
getCapabilities() -> Bundle
resolve(requestId, queryBundle, callback) -> 异步
cancel(requestId) -> 异步
openAsset(requestId, assetId) -> ParcelFileDescriptor
releaseAsset(requestId, assetId) -> 异步
```

协议契约可以独立发布少量 AIDL/常量供双方实现，不依赖 Bridge 业务类或 Lyric Provider core。
传输限定 framework 类型、基础值、Bundle、Binder callback 和 ParcelFileDescriptor；
不传插件自定义 Parcelable/Serializable、ClassLoader、Bitmap 大包或歌词。

能力和 openAsset 即便采用同步 Binder，也只能从 Bridge IPC executor 调用；
SystemUI 主线程、draw/layout、View Hook 和 BroadcastReceiver 不等待远程调用。
resolve/cancel 使用异步接口；服务入口只完成验证与排队，不在 Binder 线程联网或解码。
客户端超时只使当前请求无效，不保证能够中止已经卡住的远程 Binder 调用；限制并发并避免重连风暴。

### 6.2 请求

| 字段 | 语义 |
| --- | --- |
| protocolMajor / protocolMinor | 版本协商；major 不兼容拒绝，minor 只新增可忽略字段 |
| requestId | 当前客户端实例内唯一的不透明请求 ID；进程重建不复用旧请求空间 |
| title / artist / album | 标准 metadata 的查询信息，长度有界；缺失字段显式表示 |
| durationMs | 毫秒，未知为 0；不得与第三方秒单位混淆 |
| appleMusicUrl | 可选，只有经过来源与域名验证的 URL；不把普通数字 media ID 当 Apple ID |
| displayWidthPx / displayHeightPx | 实际封面展示尺寸，用于资源选择 |
| maxWidth / maxHeight / maxFileBytes | Bridge 可接受的资源限制 |
| acceptedMime / orientation | v1 为 video/mp4、square；尺寸比按协商容差验证 |

token、原始稳定 media ID、通知 key、设备标识与歌词不发送给后端。
Bridge 在本地保留展示绑定 stamp，插件只需回传 requestId，无需知道 SystemUI 私有 epoch。
请求字段建议上限：文本单项 512 字符、受信 URL 2048 字符、Bundle 总量 16KiB；实现时验证真实编码大小。

### 6.3 结果

| 状态 | 含义 | 客户端行为 |
| --- | --- | --- |
| READY | 资源已完整缓存且通过插件检查 | 复核请求，后台打开文件，复核 surface，再 prepare |
| NO_MATCH | 当前查询无可靠歌曲匹配 | 保留静态，允许插件负缓存 |
| NO_MOTION | 已匹配专辑明确没有动态资源 | 保留静态，允许插件负缓存 |
| AMBIGUOUS | 版本/候选无法可靠区分 | 不接管，不等同永久无匹配 |
| RETRY_LATER | 429、暂时上游故障或短期退避 | 遵守 retryAfter，不因 metadata 高频刷新重试 |
| NETWORK_BLOCKED | 当前插件网络策略禁止下载 | 保留静态，可使用已验证缓存 |
| UNSUPPORTED | 请求格式、资源或能力不支持 | 关闭该资源尝试，报告原因 |
| ERROR | 当前请求失败 | 释放当前请求，静态回退 |

READY 包含 requestId、assetId、资源版本、mime、width、height、视频时长、编码信息、
文件长度、匹配摘要和有限有效期。assetId 不透明，不是文件路径或下载 URL。
准备阶段反馈可用于设置状态，不允许网络进度逐帧进入 SystemUI。

### 6.4 调用者、资源与断连

- 服务验证真实 `Binder.getCallingUid()`；允许当前用户范围内已认可的 Bridge App UID 与 SystemUI UID。
- `onBind()` 可能由系统代发，不能在那里把系统 Binder 身份当最终调用者；每个暴露事务检查调用者。
- 共享 UID 无法精确隔离同 UID 的包，文档和实现不能声称包级安全边界；不硬编码 UID=1000。
- Bridge 校验所选组件/签名/协议；插件校验可接受的 Bridge 身份。官方同签名与第三方用户选择分别处理。
- 不能直接用插件签名级 service permission 假定 SystemUI 自动持有该权限；跨签名授权方案需真实验证。
- 请求和 asset lease 绑定注册客户端、实际 UID、requestId 与期限，不能让其他客户端猜 assetId 读取缓存。
- openAsset 只打开插件私有缓存中的只读普通文件，不接受任意路径/URL，不返回 pipe、socket 或可写描述符。
- Bridge 在工作线程校验可 seek、文件类型/长度、视频维度与格式；插件自报信息不能代替本地上限检查。
- 发送端、接收端分别负责自身描述符；过期回调、取消、超时、prepare 失败和 disconnect 都关闭持有资源。
- release/death/超时释放 lease；缓存淘汰保护当前租约。不能永久 pin 下载文件。
- Binder 死亡恢复静态；只在有效 surface 仍需功能时按有界退避重连，并建立新 service/client epoch。

## 7. 当前歌曲与异步串曲门控

### 7.1 三层身份

| 层 | 内容 |
| --- | --- |
| HostBinding | userId、MediaEntry/playerId/卡片 key、播放器包、精确 token、本地 sessionEpoch |
| SongIdentity | 可靠的宿主 METADATA_KEY_MEDIA_ID；不足时 title/artist/duration 的稳定观测 |
| ResolverQuery | title/artist/album/duration/storefront/可验证 Apple URL，不包含展示控制信息 |

规则：

1. token 更换或 user/host 改变时建立新 sessionEpoch，立即撤下旧视频。
2. 可靠歌曲 ID 改变，或稳定 fallback 身份改变时增加 trackGeneration。
3. 相同歌曲的 album/duration 补齐增加 requestRevision，重新校验/查询，不制造伪切歌。
4. artwork 更新、进度、播放状态和歌词 lane 变化不作为新的歌曲 generation。
5. metadata 与当前卡片身份不一致、字段混合或缺失时先撤下旧视频，等待稳定来源，不用旧曲快照补新曲。
6. 无可靠 ID 的宿主，仅当足够字段稳定并与当前卡片一致才查询；不能把 Bluetooth 歌词 TITLE 无条件作为新歌。
7. 无法判定暂态/真实新曲时显示静态；不为了命中率在 Bridge 加未经证据支持的播放器特例。
8. 媒体 ID 的来源命名空间、session token 和 user 共同约束身份；不同播放器相同数字 ID不相同。

拟定本地请求 stamp：

```text
clientEpoch / serviceEpoch
pluginEpoch / surfaceEpoch
sessionEpoch / trackGeneration / requestRevision / configRevision
```

在 resolver 回调、openAsset 完成、prepare 完成、首帧和真正显示前复核 stamp。
取消不是正确性的唯一保证，晚到结果仍必须丢弃。切歌先隐藏旧层，再处理异步取消/回收。
相同资源的下载可在插件内去重，但每个消费请求单独核对当前绑定。

## 8. Apple 数据源、匹配与缓存

2026-10-03 的公开接口、专辑版本冲突和 HLS/MP4 实测见
[AM 接口与 variants 调查](DYNAMIC-ARTWORK-AM-SURVEY.zh-CN.md)。
该调查更新了来源事实：360px 为样本的 HEVC 档，AVC 最小 408px；子清单明确给出 MP4 URI/字节范围。
以下后端归一化接口仍为设计，不代表已经部署或接入。

### 8.1 来源选择与已核实限制

2026-10-01 阅读了下列上游 README 与 NopXx 的 `server.js`、`lib/artwork.js`；
这里只确认公开实现快照，没有验证线上实例 SLA、地区可达性或全量歌曲覆盖率。

| 来源 | 已核实内容 | 使用方式 |
| --- | --- | --- |
| [boidu](https://github.com/boidushya/artwork.boidu.dev) | s/a/al/d 查询，d 为整秒；返回最高质量 MP4；区分确定性结果、429、502/503；Node/Hono/Postgres | 接口与缓存设计参考、原型来源适配；不直接复制未明确授权的代码 |
| [NopXx](https://github.com/NopXx/apple-music-artwork-search) | iTunes/amp-api、多分辨率；映射结果未保留歌曲时长；从 HLS 地址替换推导 MP4 | 自建统一 Resolver 的解析参考，补严格匹配与视频可用性检查 |

boidu 根目录调查未见 LICENSE；NopXx README 声明 MIT，取用时核对并保留归属与许可材料。
两者不是可直接拼接后就满足本项目契约的完整插件。

首款插件实现可替换 `ArtworkResolver`：原型可以适配公开实例，正式优先使用具备明确错误分类与匹配结果的统一 API。
后端自建/运营是独立交付，不在 Bridge 中部署 Node、抓 Web token 或保管后端凭证。
尚未选择的正式 endpoint、运行地区和运营责任不得写成已有服务承诺。
未来本地视频映射或其他来源实现同一 Binder 契约，不要求修改 Bridge 的播放器策略。

### 8.2 后端归一化接口（拟定）

```text
POST /v1/artwork/resolve
输入：title、artist、album、durationMs、storefront、可选已验证 Apple URL
输出：status、matchedTrack、matchReason、albumId、variants、expiresAt、retryAfterMs
variant：width、height、codec、fps、bytes（未知则显式）、url
```

插件 adapter 负责秒/毫秒转换，不要求 Bridge 了解第三方 API 细节。
Apple JWT 或用户 token 不进入 Binder、Bridge 配置、日志或分发包。

匹配：可信 Apple URL 精确 lookup 优先；否则搜索候选并核对 title、artist、版本标记、专辑和时长。
保留 Live、Remaster、伴奏等版本信息，不粗暴删除所有括号内容。
时长已知时可从 3 秒容差起步，但它仅是条件之一，不能证明不同版本相同。
候选时长缺失、多人合作名或专辑缺失降低可信度；多个版本无法区分返回 AMBIGUOUS。
资源通常为专辑级，先匹配正确歌曲版本/albumId，再共享对应专辑动画。

2026-10-03 用户允许忽略 Explicit/Clean 的封面来源歧义；独立 AM fix2 对同名同艺人、
发行日期与曲目数一致的 Explicit/Clean 对采用封面等价选择，冷查询稳定优先 Explicit。
此规则不声明音频身份或视频字节相同，精确 Apple ID 与其他版本限制保留，见 [fix2 测试](DYNAMIC-ARTWORK-AM-FIX2-TEST.zh-CN.md)。

MP4 URL 规则推导不等于资源真实存在。通过有界实际下载/读取及媒体检查确认，
不把 HEAD 成功作为唯一证明；不依赖 URL 中的“1080”代替视频实测。
只有 HLS 而没有经过验证的 MP4，v1 返回不支持或静态回退；不向 SystemUI 交付网络流。
依据真实宽高选择方形，不把来源的 tall 标签当固定 3:4 或 9:16 契约。

### 8.3 分辨率、下载与缓存

初始建议参数均待设备测量，不是已验证的性能指标：

| 项目 | 初始策略 |
| --- | --- |
| 普通小封面 | 从实际 variants 选满足尺寸的低分辨率方形资源 |
| 普通大封面 | 优先约 768px 内的合适版本，较大区域最多 1080px |
| 高分辨率 | 不自动选 1920/2160px；存在更低版本优先降级 |
| 单文件上限 | 20MiB；超限尝试更低版本，否则静态 |
| 视频缓存容量 | 默认 256MiB LRU，可在插件设置修改 |
| 网络 | 默认仅 Wi-Fi 下载，允许使用已验证离线缓存 |
| 确定性无匹配/无动画 | 初始负缓存 24 小时 |
| 429 | Retry-After 或有界退避，不写永久 NO_MOTION |
| 超时、502/503、协议变化 | 短暂退避，可保留已有验证资源；不污染确定性负缓存 |

不得假设所有专辑具有 486p/768p。Bridge 的上限、显示尺寸与插件实际 variants 共同决定选择。
下载并发默认 1、有界队列；仅在当前有效请求需要时下载，不在 metadata 高频回调中重复请求。
分别设置连接、读取与总任务期限；HTTP 错误和取消均保留官方封面。

查询缓存键包含规范化查询、地区、匹配算法版本；视频缓存键包含 albumId、地区、资源版本和分辨率。
完善字段后的查询不得继续复用旧的负缓存；过期 URL 可以重解析，不把 albumId 当永久可用 URL。
临时文件有大小上限，完成媒体校验后原子进入缓存。中断文件不是 READY。
下载/去重结果可以缓存，当前展示是否接收仍由独立请求 stamp 决定。

## 9. 渲染、过渡与生命周期

### 9.1 视频层

使用 framework MediaPlayer 与 TextureView，数据源为本地只读可 seek 的 FD。
静音循环，不申请音频焦点，不联网 prepare，不改写宿主播放状态。
原封面 View 的静态绑定、点击、长按、无障碍和官方过渡继续工作。

只在验证过的容器内增加非触摸、非焦点、非无障碍目标的视频层，不全局遍历并覆盖所有 ImageView。
不 reparent 官方封面，不移除布局 ID，不强改宿主尺寸，也不覆盖歌词区。
不同父容器用独立挂载方式；视频 bounds 依据真实 View/布局事件同步。
适配器输出中立的支持形状、裁剪、scale/crop 与前景策略；无法保持官方边框、前景或形状时不接管。
TextureView 在目标设备上的实际裁剪与合成必须验证，不能认为添加圆角 wrapper 就天然匹配 OPlus smooth corner。

### 9.2 首帧与状态机

```text
STATIC -> RESOLVING -> DOWNLOADING -> PREPARING -> WAIT_FIRST_FRAME -> PLAYING
                     |                  |              |
                     +------------------+--------------+--失败--> STATIC
PLAYING -> PAUSED -> 在同一有效绑定上恢复
任意状态 -> 切歌 / session销毁 / detach / 插件更换 -> STATIC 或 RELEASED
```

onPrepared 不等于首帧已经显示。监听 rendering-start 和 TextureView 实际更新，
确认当前 surface 已可显示后再淡入；首帧等待有上限，超时静态回退。
初始视频淡入可采用约 200ms，但只在官方过渡完成且 stamp 有效时执行，不改官方动画时长。
显示前再次检查 config、session、track、surface 与可见状态。

### 9.3 C16/C17 过渡策略

- C16：识别宿主 Drawable 过渡；切歌立即隐藏视频，静态过渡由官方继续执行，完成后等待当前资源首帧。
- C17：识别当前/下一槽位和 controller 动画状态；pending drawable 未提交前不显示新视频。
- 不用统一固定延迟假装已完成过渡。无法可靠读取过渡完成状态的 profile 不启用该 surface。
- RotatableImageView 有内部 bitmap 特效，不仅是 View.rotation；不能用 getRotation() 假装复制完整特效。
- 首版在尚未支持的特效/布局过渡期间恢复官方封面，不取消、扩大或重设官方动画。
- 几何变化通过布局/形状事件同步；只有有效播放 surface 允许必要的短期观察，不全局逐帧反射。

### 9.4 播放门禁与资源回收

| 场景 | 策略 |
| --- | --- |
| 已启用、当前锁屏、目标可见、真实 PLAYING、有效 session | 允许播放 |
| PAUSED / BUFFERING | 停止视频动画，可在同曲有效 surface 短暂保留当前帧 |
| STOPPED / NONE / session 无效 | 静态回退 |
| 息屏 / AOD / 非交互状态 | 隐藏视频、释放解码与 Surface；显示官方静态封面 |
| 解锁进入首版不支持区域 | 隐藏视频并结束该展示绑定 |
| 切歌 / user 切换 | 先撤下旧层，再取消/关闭资源，建立新身份 |
| detach / section dispose / ClassLoader 更换 | 注销回调与监听，释放播放器、Surface、FD、lease、View 关联 |
| 插件死亡 / 更新 / 卸载 / 签名或协议改变 | 静态回退，旧 callback 失效；按策略有限恢复 |
| 元数据或会话关联不明确 / 无资源 / 解码失败 | 保留当前宿主静态封面 |

一个 MediaPlayer 只有一个输出 Surface。首版进程内最多一处播放，优先有效沉浸页，再普通锁屏卡；
多个可见卡片竞争时根据精确宿主选择，不为所有卡片各建解码器。
不调用歌词 keep-awake，不通过假 PLAYING/position/speed 唤醒 SystemUI。默认不持有 WakeLock；
2026-10-04 起按用户需求提供默认关闭的“动态封面播放时保持屏幕常亮”，仅在大封面视频实际播放时持有独立锁，
暂停、息屏/AOD、解锁即释放，与歌词保活互不影响（见 [fix18](DYNAMIC-ARTWORK-AM-FIX18-TEST.zh-CN.md)）。
播放速度不跟随歌曲倍速，视频是专辑循环资源，不按歌曲 position seek。

## 10. 设置、隐私与备份

### 10.1 Bridge 展示配置（独立域）

```text
enabled=false
selectedProviderComponent / acceptedSigningIdentity / protocolMajor
lockscreenCardEnabled=true
immersiveEnabled=true
pauseWithPlayback=true
maxResolutionPx=1080
maxFileBytes=20MiB
configRevision
```

两个位置默认值只有总开关开启且能力通过后生效。
不把封面配置或 debug 混入 LyricUiConfig，不用歌词样式轮询传递资源。
设置页展示未安装、未启用、协议不支持、宿主未识别、无匹配、下载/首帧等待等真实状态。
Bridge 保持现有设置页视觉约束；打开插件自身设置使用经验证的显式 Activity 组件。

### 10.2 Provider 资源设置

Wi-Fi/移动数据策略、缓存容量与清理、Resolver endpoint、地区、手工来源/本地映射、
数据源凭证等属于插件自己的配置。Bridge 不维护重复缓存设置，也不替插件保管凭证。
Bridge 可展示能力摘要并打开插件设置；v1 Binder 不增加 SystemUI 任意缓存删除接口。

启用时说明会向选定来源发送歌曲名、歌手、专辑、时长等查询信息。
不发送 session token、设备 ID、歌词或原始宿主 media ID；日志不输出 query URL、token/cookie、完整标题或私人路径。

Bridge 配置备份只包含展示配置及插件选择参考，恢复时重新验证安装组件、签名和协议。
缺失/身份变化的插件保持未启用状态，不能根据旧包名自动允许另一个实现。
不备份歌曲缓存、映射历史、FD/assetId、下载 URL、用户 token 或其他秘密。

## 11. 拟新增代码与修改范围

所有以下类型均为拟新增，不应在实施前按类名引用或标记完成。
Bridge 基目录：`app/src/main/java/io/github/andrealtb/lockscreenlyrics/`。

| 位置/类型 | 职责 |
| --- | --- |
| `artwork/contract/` 与少量 AIDL | 协议版本、Bundle codec、callback 与中立 DTO |
| `systemui/artwork/DynamicArtworkRuntime` | 组合根、状态机和展示需求调度 |
| `systemui/artwork/ArtworkSessionRegistry` | 精确卡片-session 关联、标准 controller 回调与销毁 |
| `systemui/artwork/ArtworkTrackIdentityPolicy` | 展示身份、generation、request revision |
| `systemui/artwork/ArtworkCompatibilityResolver` | capability 组合、结构验证与分支能力失败 |
| `systemui/artwork/ArtworkHostBinding` | section、View、容器、槽位与 surface epoch |
| `systemui/artwork/ColorOs16ArtworkHostAdapter` | C16 实际 profile、Drawable 过渡与形状 |
| `systemui/artwork/ColorOs17ArtworkHostAdapter` | C17 split model、双槽位过渡与形状 |
| `systemui/artwork/ArtworkProviderClient` | 显式绑定、协议协商、请求、FD、死亡和重连 |
| `systemui/artwork/DynamicArtworkRenderer` | MediaPlayer、TextureView、首帧与完整释放 |
| `systemui/artwork/ArtworkPlaybackPolicy` | 锁屏、可见性、状态和单 surface 预算 |
| 独立 `DynamicArtworkSettings` / repository / 设置页 | 插件发现、展示开关、保护同步和备份 |

C16 adapter 内部继续按字段/方法能力拆分 profile，不假定同一版本只有一套布局。
网络、下载与缓存类不进入上述 Bridge 包。

现有文件只增加委托与必要配置：

- `LockscreenLyricsModule`：封面 bootstrap、插件 generation 和屏幕/会话生命周期委托，不继续内嵌业务巨型方法。
- bootstrap：共同加载入口可复用，封面初始化不依赖歌词 Targets 成功。
- Manifest：插件查询与设置页；保持无 INTERNET、不新增播放器 scope。
- Gradle：按需启用 AIDL；不引入媒体网络栈或旧歌词 transport module。
- 设置同步/备份：独立封面配置域及所选插件身份恢复验证。
- diagnostics：artwork area/events、脱敏与节流。

独立 Artwork Provider 的仓库/目录、applicationId、签名与发布方式已冻结（2026-10-04）：代码在
Providers 仓库 `artwork-provider-am/`，applicationId 保持 `io.github.andrealtb.artwork.am`，
版本从 1.0.0 起，使用与歌词 Provider 相同的发布签名；协议契约仍以本仓库 `artwork-contract/` 为准，
Providers 侧保留镜像并由 `scripts/verify-artwork-contract.ps1` 逐文件校验。
其代码包含 resolver、candidate matching、download、cache、asset lease、service 和自身设置/预览。
它不是当前播放器歌词矩阵的一员，正式发布必须单独说明可选安装和签名。
当前只作为 Bridge 预览版的附加资产发布（`previewArtworkProvider`），v5 资产数量与版本契约不变；
是否进入正式套件资产在后续发版任务中决定。

## 12. 日志与诊断

沿用 `[CLL] level=... component=bridge area=... event=...`，新功能 debug 独立且默认关闭。
插件使用可区分的结构化 component/tag，并纳入统一抓取脚本的可选过滤；不开启日志不改变业务行为。

拟定事件：

```text
ARTWORK_CAPABILITY_RESOLVED / ARTWORK_CAPABILITY_UNSUPPORTED
ARTWORK_HOST_BOUND / ARTWORK_SESSION_BOUND
ARTWORK_REQUEST_STARTED / ARTWORK_RESOLVE_RESULT
ARTWORK_ASSET_OPENED / ARTWORK_PREPARE_STARTED / ARTWORK_FIRST_FRAME
ARTWORK_STALE_DROPPED / ARTWORK_FALLBACK / ARTWORK_RELEASED
ARTWORK_PROVIDER_CONNECTED / ARTWORK_PROVIDER_DIED
```

记录 profile/capability、脱敏 request/identity 摘要、epoch、分辨率、文件长度、耗时和有限原因码。
不记录完整歌词、曲名、原始 media ID、token/cookie、完整 URL query、私人路径或每帧字符串。
性能采样汇总首帧时间、解码器/FD/Surface 存量、下载次数与失败原因，不能只记录“接口成功”。
抓取遵循工作区 `LOGGING-DEBUG-CAPTURE.md` 和 `scripts/capture-lyrics-log.ps1`，
同一最新复现窗口覆盖资源插件、Bridge、SystemUI/AndroidRuntime。静态样本不能替代设备日志。

## 13. 实施切片与完成标准

| 切片 | 交付 | 完成条件 |
| --- | --- | --- |
| A：协议/边界 | 冻结 v1、发现声明、规则边界、失败分类 | 明确 Lyric/Artwork 分离；正常路径无网络/歌词传输进入 Bridge |
| B：宿主与会话 | C16 各 profile/C17 resolver、host 生命周期、精确关联 | 无歌词歌曲可识别；多 session 不按包名猜测；无法关联显式静态 |
| C：本地固定视频 | 协议 mock/测试插件、普通卡/沉浸页渲染 | 首帧、裁剪、触摸、官方过渡、切歌和息屏稳定 |
| D：资源插件 | Binder、缓存 FD、取消、lease 与死亡 | 插件退出/晚回调不串歌、不拖垮 SystemUI；资源完全回收 |
| E：联网 Resolver | 候选匹配、variants、下载与缓存 | 错版本不接管；上游故障不写 NO_MOTION；受控网络和文件上限 |
| F：设置与回归 | 默认关闭、组件选择、身份变更、独立配置、备份 | 开关可回退，原歌词、翻译动作、封面和 AOD 无回归 |
| G：发布准备 | 设备记录、协议文档、插件签名/安装说明 | 满足对应发布契约与最终设备门禁；不能用 Debug 包冒充正式插件 |

先用固定本地视频验证展示与生命周期，再接联网数据源；网络成功不能掩盖宿主绑定失败。
每个切片记录实际状态、代码提交、最小相关验证及剩余设备项。切片不是逐步例行审批清单。
必要的设备操作、安装或发布必须在后续任务获得对应授权。

## 14. 验证矩阵与设备验收

### 14.1 静态与单元验证

- C16 old/new/new10/user 的真实 DEX/资源匹配，加 C17；同版本不同字段类型不得误命中。
- 方法唯一性、owner、参数、容器、形状及缺失能力的安全回退。
- A -> B -> C 快速切歌、A 的迟到结果、同专辑切歌、同歌曲补齐专辑/时长。
- 相同包多个 token、不同播放器相同 ID、session/user 更换和历史非 active 卡片。
- 部分 metadata、旧 title/新 artist 混合、歌词 TITLE 投影和不可靠身份。
- callback/openAsset/prepare/首帧/配置变更每一阶段的 stale drop 与 FD 关闭。
- 协议 major 不兼容、插件签名/组件改变、取消、死亡、重连预算与 lease 回收。
- NO_MATCH/NO_MOTION/AMBIGUOUS/429/临时故障分别缓存；字段补齐不复用旧负缓存。
- 文件过大、pipe/不可 seek、维度越界、残缺 MP4、推导 URL 不存在、编码不支持。
- renderer 适用门禁与单解码器预算；功能关闭不新增视频请求或影响歌词。

Bridge 相关实现后的最小常用验证：

```powershell
scripts\dev.cmd bridge :app:testDebugUnitTest :app:assembleDebug
```

插件使用自己的构建入口和有意义的协议/匹配/缓存测试。
没有修改 Lyric Providers 时不做全播放器矩阵构建。检查通过后不无故重复或扩大构建。

### 14.2 设备矩阵

| 维度 | 必需验收 |
| --- | --- |
| 系统 | 实际 C16/C17 的 SystemUI+插件版本配对；记录 SDK、版本和能力 profile |
| 布局 | 普通锁屏卡、沉浸页；竖屏/横屏；平板/折叠屏未知布局先禁用 |
| 身份 | 无歌词歌曲、同专辑快速切歌、多会话、播放器切换、session 重建 |
| 状态 | 播放、暂停、BUFFERING、STOPPED、锁屏/解锁、息屏/AOD、重新亮屏 |
| 资源 | 缓存命中、离线、Wi-Fi 策略、无匹配、无动画、错误版本、下载/解码失败 |
| 生命周期 | section dispose、插件 loader 更换、资源插件停止/更新/卸载、SystemUI 重建 |
| 视觉 | 首帧无黑闪、裁剪/形状、官方单槽/双槽过渡、触摸和无障碍、无旧曲残帧 |
| 预算 | 长时间循环后的 FD/Surface/解码器存量、首帧时延、发热/功耗与 UI 流畅度 |
| 回归 | 当前歌词渲染、翻译 CustomAction、播放动作、静态封面、原 AOD 和 keep-awake 行为 |

本地四种 C16 二进制都做静态匹配，不等于拥有四种对应设备；缺少设备的变体保留“静态识别/待设备验证”。
不能将任意 APK 交叉配对安装当成验收，不主动切 ROM 或替换系统插件。
采样/录屏/日志由用户执行，或在明确授权范围内协助；不自动执行设备重启或 SystemUI 重启。

### 14.3 验收结论分层

| 等级 | 允许表述 |
| --- | --- |
| 静态确认 | 特定样本的字段、入口、资源、模型与候选关系 |
| 已实现 | 当前提交中存在实际功能代码，不能据计划文件判定 |
| 本地构建/测试通过 | 明确任务、结果及 fixture 跳过范围 |
| 需要设备验证 | 显示、过渡、session 关联、生命周期、性能与真实绑定启动 |
| 用户已设备确认 | 对应系统/插件/profile 的明确确认记录 |

当前动态封面状态仅为方案与相关静态调查，不继承既有歌词 C17 Preview 的验证结果。

## 15. 失败回退、回滚与尚未闭合事项

总开关关闭：先隐藏视频、使全部 stamp 失效，再取消请求、释放解码/FD/lease、移除新增层并解绑。
官方 ImageView 始终保留正常静态更新，不需要重放播放器 metadata 才能回退。
不因插件故障清理播放器数据、改歌曲、重启 SystemUI 或扩大 Hook scope。

实施前需闭合的事项：

1. 卡片模型/uniqueId 到精确 SeedlingMediaData/token 的完整映射，尤其 JADX 占位链路的字节码证据。
2. 各 C16 形状 profile、C17 CustomLottieView 的裁剪/前景，以及官方过渡完成信号。
3. 已缓存系统插件与功能运行中启用时的 artwork host 补装方式。
4. 资源插件身份认可、SystemUI/模块 App 调用者校验、跨用户与后台绑定在目标系统上的行为。
5. 插件仓库/applicationId/签名与 v1 namespace 冻结；正式数据源 endpoint、地区和运营安排。
6. 本地视频阶段的首帧、资源回收、单解码预算与真实设备性能。

上述输入不足时可继续协议、静态解析与纯策略实现，但依赖它们的 surface 不应强行开启。
新增其他数据源、HLS、AOD 动画、控制中心、全屏背景或多解码器属于后续范围，单独设计与验证。

## 16. 参考与复查入口

- [当前 Lyric Provider / Bridge 原生边界](LYRIC_PROVIDER_BRIDGE.zh-CN.md)
- [Phase 5 私有歌词链路删除报告](4.0/PHASE-5-BRIDGE-V4-REMOVAL-REPORT.md)
- [Phase 6 性能与生命周期台账](4.0/PHASE-6-PERFORMANCE-LEDGER.md)
- [原动态封面讨论](https://chatgpt.com/share/6abe5ddc-a6ec-83ed-b8ff-8e9b603a83a5)
- [boidu README](https://github.com/boidushya/artwork.boidu.dev/blob/master/README.md)
- [NopXx README](https://github.com/NopXx/apple-music-artwork-search/blob/main/README.md)
- [NopXx 实际资源解析](https://github.com/NopXx/apple-music-artwork-search/blob/main/lib/artwork.js)

本地跨仓库参考：工作区 `COLOROS17-BRIDGE-ADAPTATION-SURVEY.zh-CN.md`、`LOGGING-DEBUG-CAPTURE.md`、
`scripts/capture-lyrics-log.ps1` 及第 4 节样本目录。这些文件不假定存在于单独克隆的 Bridge 仓库。
后续实现应将必要的脱敏静态契约 fixture 放入测试目录，不提交 APK、个人设备日志、下载视频或凭证。
