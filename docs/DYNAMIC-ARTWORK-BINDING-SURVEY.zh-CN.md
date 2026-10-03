# 动态封面：宿主与精确会话绑定实施报告

日期：2026-10-02。分支：`feat/dynamic-artwork-provider`。
依据：[完整方案](DYNAMIC-ARTWORK-PROVIDER-PLAN.zh-CN.md)；总进度见 [实施记录](DYNAMIC-ARTWORK-IMPLEMENTATION.zh-CN.md)。

## 实际交付

已接入独立的 SystemUI artwork bootstrap、C16/C17 模型解析、普通卡/沉浸 section 生命周期观察、
当前模型 Flow 观察、活动 entry 精确匹配、标准 MediaController 回调与歌曲身份门控。
**本阶段建立绑定并提供只读诊断，尚未向 SystemUI 宿主请求或播放视频。**

产品启用标志默认 false，保护的展示配置通道尚未接入；当前可以通过已有 MEDIA debug 进行只读绑定诊断。
关闭诊断且产品未启用时注销会话观察。诊断不会修改 metadata、PlaybackState、布局、手势、官方动画或歌词输出。

## 闭合的静态证据

本地样本位置与完整 SHA-256 仍以完整方案第 4 节为准。此次直接读取 APK DEX，
使用已有 apktool 中的 DEX reader/baksmali 导出指定类；未修改 PlayerSource 参考树。
仅保留类型/条件总结，临时反汇编和分析 helper 位于 Git 忽略的 `artifacts/artwork-analysis/`。

### 分流前源

以下三份 SystemUI APK 的精确签名均通过静态复核：

- `PlayerSource/SystemUI/系统界面_16.99.12.apk`。
- `PlayerSource/SystemUI/系统界面_16.99.12_New.apk`。
- `PlayerSource/C17/C17-SystemUI.apk`。

源类型：`com.oplus.systemui.media.seedling.OplusSeedlingMediaDataCombineLatest`。
核对了 `getSortedEntriesExceptNonActive():List`、`onMediaDataLoaded(String,SeedlingMediaData,int):void`、
`onMediaDataRemoved(String):void` 和 `release():void`。
DTO 的 playerId/package/song/artist/userId/token/duration/active getter 均核对了名称、返回类型和实例方法属性。

这些入口位于 UMS/LiveAlert 分流之前。实现不以 `mediaDataToBundle()` 或 `lyricInfo` 存在为条件。

两代 `OplusMediaSeedlingController.access$createMediaClient` 将传入的 **MediaData entry key** 原样写入 playerId，
不是歌曲 media ID，也没有证据可以把它当 Apple Music ID。
上游监听按当前用户和 OPlus multi-app 用户筛选；`AbsMediaClient` 保留实际 userId 和 token。
本实现不解析 playerId 字符串格式、不硬编码用户 UID、不用 SystemUI 进程用户替代 entry 用户。

### 卡片到模型

两代 LiveAlert 将 `SeedlingMediaData.getPlayerId()` 写入 card Bundle 的 `uniqueId`。
C16 Java 占位由已有 smali 补证，C17 Java 与真实 DEX 一致。

另从真实插件 DEX 补齐 parser：

- C16 user：`n6.f` 从 `m6.c.a` 读取 card uniqueId，经 `v14 → v28` 传入 `m6.t` 构造器的第一个 String 参数。
  `m6.t` 构造器将它写入模型的 uniqueId 字段，再进入 `m6.l` 的 mediaModel。
- C17：`...data.media.parse.f` 从 `model.d.a` 读取 uniqueId，经 `v4 → v27` 传入 `model.t` 的第一个 String 参数。
  该 MediaInfo 经 `model.u(info,progress)` 包装后进入 `model.k` 的 mediaModel。
- 沉浸输入是 `LAImmersiveCardModel`，持有唯一 LACardModel；不会将内容/进度子模型当 session identity。

实现从 labeled DTO 的合成探针绑定字段。真实歌曲模型不调用 `toString()` 进行字段解析；
不依赖 JADX 的 `f1234a` 名称、反射字段声明顺序或“最后一个 boolean”。
探针只构造已匹配的模型 DTO，使用各自唯一的 String 哨兵和空容器；新增不可满足的必需字段会令该能力失败。
核对了既有 KuWo 构造器 Hook 的包名门禁，合成哨兵不进入对应播放器兼容分支。

### 五个插件样本的唯一匹配

| 样本 | 身份/包装 | 普通 section | 沉浸 section | 构造器结构 |
| --- | --- | --- | --- | --- |
| C16 old | flat `m6.t`、card `m6.l`、immersive `m6.m` | `f7.m5` | `f7.n3` | Context、VM、ViewGroup |
| C16 new | 同上 | `f7.p5` | `f7.o3` | Context、VM、ViewGroup |
| C16 new10 | 同上 | `f7.p5` | `f7.o3` | Context、VM、ViewGroup |
| C16 user | 同上 | `f7.l5` | `f7.n3` | Context、VM、ViewGroup |
| C17 | info `model.t`、wrapper `model.u`、card `model.k`、immersive `model.l` | `...media.s1` | `...media.Y0` | 普通：Context、VM、ViewGroup、entity；沉浸：Context、hostContext、VM、ViewGroup |

表中混淆名只说明特定样本证据，runtime 不以这些名字匹配。
user1 与 user 的二进制重复关系沿用完整方案，不重复宣称为第六种独立 profile。

静态扫描使用与 resolver 相同的 AND/OR 字符串组合，五个样本各 role 均唯一。
C17 的 LACardModel 将 `, progressBarModel=null, mediaModel=` 合并为一个 DEX 常量；
resolver 明确覆盖这一分支，没有依照 JADX 展示拆成 C16 的精确常量条件。

这验证了本地 DEX 条件和结构，**没有执行设备中的 DexKit、Xposed、View 或 MediaController**。

## 运行时边界与失效处理

- 模块先初始化独立 artwork 源、共同 loader 入口，再进入原歌词 bootstrap；歌词 Targets 失败不会取消 artwork 源解析。
- 在已就绪 loader 回调返回前进行一次本地解析并安装 section 构造器 Hook，避免异步解析漏掉首次 section 创建。
  这一点会增加本地 bootstrap 解析成本；设备启动时延仍需测量，不能用本地构建估算。
- 每个宿主验证自己构造器传入的 VM、Root 与实际封面资源。只观察匹配 section 的 `img_album_art` 或 `img_large_album_art`，
  不全局遍历 ImageView，也不 reparent 官方 View。
- VM 输入须同时符合 StateFlow getter/挂起 collect 的结构，且只有一条当前 LACard/immersive Card 输入。
  fix4 的沉浸页通过普通 `(Class,String)` 快照 accessor 获取 `mpModule`，沿有界 VM delegate 找到已有 section 输入，
  使用唯一 Map getter 的 `mpModule` 与快照对象相同这一条件核对来源。C16 `g7.m.k` / C17 section VM `p.n`
  均读取已有输入；对应 `l` / `o` 每次创建新的 WhileSubscribed 派生 Flow，不再调用这些工厂。
  只读 wrapper 的唯一 delegate 按同一当前对象核对；原始 Flow getter、修改方法用实例门禁观察，捕捉同封面切歌。
  attach 期间强持有输入和最多一个 delegate，detach/dispose/观察关闭时移除 owner 并释放；
  主线程提交须仍属于当前 input lease 与 surfaceEpoch，避免晚回调恢复旧模型。不新增协程订阅。
- source 快照最多 32 条，宿主最多 8 个；source 和宿主更新合并后提交 Main Handler，不为每帧排队远程工作。
- 完整活动列表中要求 playerId 与包名候选唯一，并复核 card/source 的 title、artist。
  重复、跨用户重复、空 token、非 active 或缺失 session 字段使关联失效；
  fix3 中只有歌曲文字/字段暂态时保留同一 controller，展示继续静态。
- 实际 entry 的 userId、包名和 token 构成 controller key。同包多 session 不使用“第一个 active session”。
- 标准 controller 的创建、注册、初始 metadata/state 读取和注销在进程共享、有界单线程队列执行。
  初始化期限 5 秒，失败按 key 退避 30 秒；同步事务卡住不会创建替代线程。
- 初始 IPC 快照不覆盖已到达的较新 callback。session 释放后的晚 callback 由引用与 active 状态丢弃。
- 普通/沉浸同一 token 共享 controller，按各宿主独立核对歌曲观测。
  detach、section dispose、source release 或观察关闭撤销对应的宿主/会话观察；
  loader generation 更换还卸载旧 section/Flow Hook。共享 Flow Hook 按 loader 持有，不随单个宿主销毁误卸载。
- metadata ID 改变增加 track generation；同曲 album/duration 补齐增加 request revision；
  同 ID 但 title/artist 投影或暂态不一致先失效，不制造伪切歌。
- 无 media ID 时，fix3 在精确 token 下启用受控 fallback：三处文字一致、已知时长与至少 500ms 后 fresh metadata 复核。
  DISPLAY 与普通字段冲突、同 artist/相近时长仅 title 改变、可靠 ID 暂时消失均拒绝。
  这些是保守门禁，不能证明所有未知播放器从首次观测起就没有 TITLE 投影；真实接受度需对应设备复测。

当前 binding stamp 使用 plugin/surface/session/track/request 域；client/service/config 域尚未接入资源请求，保持 0。
不能据此宣称 v1 IPC 到首帧的八维门控已经闭合。

## 验证结果与剩余工作

本次新增 15 个纯策略/反射契约测试，覆盖同包多个 token、重复/跨用户候选、非 active、晚字段与曲目信息不一致、
同专辑切歌、字段补齐、TITLE 投影、默认拒绝无 ID fallback、字段顺序变化、split/immersive 包装与歧义拒绝。

```powershell
scripts\dev.cmd bridge :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

结果：647 个 Bridge 测试，641 通过、6 个既有 fixture 条件跳过；Lint 0 errors，47 warnings；Debug APK 构建通过。
未改变契约/本地插件代码，所以没有重复它们的构建，也没有运行歌词 Provider 全矩阵。
JVM 测试不证明实际 Flow、Binder callback 注册/注销、首宿主捕获或跨用户运行成功。

下一切片 C 仍需完成形状、前景、裁剪、C16 Drawable/C17 双槽过渡完成信号、保护配置、
有效 surface 的 FD 请求与实际首帧门控，然后按明确授权进行设备验收。
已经在当前 loader 下记录的宿主可在观察开启时补读 Flow；
**loader 与 section 都早于模块初始化、且以后没有 loader 回调的情形尚未覆盖**，不能用歌词 View attach 代替这项能力。
当前没有联网 Resolver、AOD 动画或 SystemUI 动态视频展示。
