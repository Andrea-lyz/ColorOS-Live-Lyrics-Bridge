# 动态封面实施记录

更新日期：2026-10-03。开发分支：`feat/dynamic-artwork-provider`。
从 `feat/coloros17-adaptation` 的 `df7cf393d3a9ed928f4a409911179d989ccf2054` 建立。
依据：[完整方案](DYNAMIC-ARTWORK-PROVIDER-PLAN.zh-CN.md)。

2026-10-05 C17 fix4：光晕颜色新增默认关闭的“跟随封面取色”，C16 隐藏；读取对应媒体宿主的
静态封面 primary80，与原生进度条同源，只替换光晕 RGB。无需动态封面 Provider 或视频开关，
缺失配色时回退手动颜色。配置接入原有保存/同步/备份，见 [fix4 记录](DYNAMIC-ARTWORK-C17-FIX4-TEST.zh-CN.md)，尚未设备验证。

2026-10-05 fix2 断词修复用户已确认正常。后续 fix3 针对普通逐行歌词进度的快速切句衔接，
统一高光所属句与播放时间、取消无词索引时的全亮兜底、句尾提前短暂扫满；未改源时间轴。
见 [fix3 说明](DYNAMIC-ARTWORK-C17-FIX3-TEST.zh-CN.md)。2026-10-05 用户反馈“可以，这版效果满意”，
本次普通逐行歌词进度的句尾与切句衔接效果已设备确认。

2026-10-05 C17 fix2：修复自绘歌词漏认 NBSP 等 Unicode 词间空格而拆开 door/your 的问题；
只调整排版断点与空格裁剪，不改源文本或逐字索引。749 项测试通过、6 项既有跳过，Debug 构建通过，
尚未设备复验。见 [fix2 记录](DYNAMIC-ARTWORK-C17-FIX2-TEST.zh-CN.md)。

2026-10-05 C17 fix1：依据 DSU 实机崩溃日志与当前 framework，修复 TextureView 帧回调中同步拆层
引发的空指针；失效立即撤销，拆层和状态通知移到绘制返回后。C17 大封面模式下的全屏动态背景
接入常亮，歌词模式仍独立控制。见 [fix1 修复与复验](DYNAMIC-ARTWORK-C17-FIX1-TEST.zh-CN.md)，尚未设备复验。

2026-10-05 fix1 后续 DSU 复测：`lyrics-log-20261005-070925.txt` 的 07:09:29–07:12:10 窗口
未见 SystemUI 崩溃；视频播放/暂停恢复、动态封面常亮获取释放与歌词常亮 pulse/释放均有记录。
用户反馈“好像没啥大问题了”，记为基本链路初步通过；本窗口未触发 stale_render，
不扩大为原崩溃分支强制覆盖、长期资源预算或所有 C17 设备验收。

2026-10-05 设计归档：[C17 专项适配计划](DYNAMIC-ARTWORK-C17-PLAN.zh-CN.md)。依据本地 C17
反编译样本，优先 Square，补齐前景双槽与全屏背景的同步镜像、实时渐变模糊和独立过渡。
计划归档时尚未实现；不继承 C16 动态封面的验证结论。

2026-10-05 C17 首轮实现：新增经结构校验的双槽与当前背景 renderer 适配，补齐小卡 shader/布局差异；
单路视频通过 RuntimeShader 同步绘制中央与上下镜像，并保留官方渐变模糊、歌词模式整体模糊和遮罩。
过渡按 controller/pending 状态放行，不用固定等待。新增 C17 状态/几何/字段契约测试。
当前仅为本地测试候选，官方模糊的视频采样与完整设备验收待确认；见 [首轮实现与验证](DYNAMIC-ARTWORK-C17-INITIAL-TEST.zh-CN.md)。

2026-10-04 发布：动态封面 Provider 迁入 Providers 仓库（`artwork-provider-am`，v1.0.0 / versionCode 4，
含 `artwork-contract` 镜像与逐文件哈希校验），Bridge 侧删除该模块、保留协议契约；预览版
[v4.4.0-C16-Artwork](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/releases/tag/v4.4.0-C16-Artwork)
由 `preview.yml` 构建并发布，资产为 Bridge APK、动态封面 Provider APK 与 SHA256SUMS，两者均为正式签名。
Bridge 提交 `7bd2401`，Providers 提交 `bcde41f`；Bridge APK `68d81bd7…`、Provider APK `8b43e54d…`（SHA-256）。
已知限制与用法见发布说明。

2026-10-04 AM fix24：缓存上限由固定 256 MiB 改为可配置（默认 512 MB，预设到 8 GB，可自定义 64–8000 MB），
主页缓存卡片新增“缓存上限”行；用量条与用量文本改读设置值。降低上限时立即按最久未使用清理。用户已设备确认。
见 [fix24 说明](DYNAMIC-ARTWORK-AM-FIX24-TEST.zh-CN.md)。

2026-10-04 fix23（界面）：AM 插件更名为“动态封面 Provider”（包名与组件不变），资源默认英文、中文系统显示中文；
主页与绑定页重做（动态封面预览、匹配环形图、缓存缩略图、状态卡片），新增自适应图标。
搜索结果封面仅在用户搜索时从 Apple 图片 CDN 加载，锁屏下载链路不变。用户已设备确认。见 [fix23 说明](DYNAMIC-ARTWORK-AM-FIX23-TEST.zh-CN.md)。

2026-10-04 AM fix22：失败专辑为两碟的《The Life of a Showgirl: The Encore》，Apple 按碟分为 `track-list - <ID> - 1/2`
两个分区，解析器只认单碟分区。改为合并所有碟分区，并加入该页字段精简快照测试。用户已设备确认。见 [fix22 说明](DYNAMIC-ARTWORK-AM-FIX22-TEST.zh-CN.md)。

2026-10-04 fix21（AM + Bridge 设置页）：040311 日志中通用 Provider（Spotify）请求正常到达插件，但个别专辑页解析失败
（`web_schema_changed`）且未记录到最近专辑。解析改为分步骤记录失败点、单个异常曲目只跳过、未知动态封面资源单独分类；
最近专辑记录所有请求；设置页区分页面无法解析。见 [fix21 说明](DYNAMIC-ARTWORK-AM-FIX21-TEST.zh-CN.md)。

2026-10-04 fix20（Bridge）：033854 日志确认 fix19 大部分正常；两处都关闭期间切歌，淡入开始未被记录且重建时清空记录，
重新开启后大封面一直停在 `native_transition`。改为始终记录过渡开始/重置、重建不清空；“锁屏小卡片”改为“歌词小封面”。
035838 日志与用户确认：关闭期间切歌后重新开启，大封面与歌词小封面均恢复播放。见 [fix20 说明](DYNAMIC-ARTWORK-AM-FIX20-TEST.zh-CN.md)。

2026-10-04 fix19（Bridge，切片 F）：动态封面改为正式设置：独立偏好域与受保护广播，SystemUI 保存副本并在重启后恢复，
接收器不再依赖歌词引导；主设置页新增“动态封面”页（状态卡、总开关、小卡片/大封面、插件选择、保持亮屏）；
调试页只保留预览与固定视频测试；配置备份纳入可选的动态封面域并在恢复后重新校验插件。见 [fix19 说明](DYNAMIC-ARTWORK-AM-FIX19-TEST.zh-CN.md)。

2026-10-04 fix18（Bridge）：014800 日志确认手动绑定生效。新增调试页开关“动态封面播放时保持屏幕常亮”，
仅在大封面视频实际播放时持有独立的亮屏租约，暂停/息屏/AOD/解锁释放，与歌词保活互不影响。
023112 日志与用户确认：保活按大封面播放/息屏/小卡片正确持有与释放，fix17 切歌下载完成入缓存。
见 [fix18 说明](DYNAMIC-ARTWORK-AM-FIX18-TEST.zh-CN.md)。

2026-10-03 AM fix17：Bridge 每次请求都会解绑重绑，插件服务随之销毁，原先 `onDestroy` 中断工作线程使 fix6 的
detached 下载实际未生效；工作线程与 detached 计数改为进程级。新增插件内手动绑定：把 Apple Music 专辑绑定到本地专辑名，
命中时跳过曲目核对，不修改本地文件。见 [fix17 说明](DYNAMIC-ARTWORK-AM-FIX17-TEST.zh-CN.md)。

2026-10-03 AM fix16：按用户提出的“以专辑为核心”原则，在同一专辑名与时长内，去掉客串段落后标题一致、
双方主唱互相出现在对方署名中即视为同一曲目；版本词保留，无专辑名时不放宽。见 [fix16 说明](DYNAMIC-ARTWORK-AM-FIX16-TEST.zh-CN.md)。

2026-10-03 AM fix15：222123 日志验证 fix14 生效；Fortnight 并未卡住，而是歌手匹配失败（播放器列出两位艺人，商店只署主唱、
客串写在标题中）。插件改为比较署名集合，集合须完全相等。见 [fix15 说明](DYNAMIC-ARTWORK-AM-FIX15-TEST.zh-CN.md)。

2026-10-03 AM fix14：220402 日志验证 fix13：无进程暂停、无卡死，冷解析 1.7–3.8 秒完成，锁屏周期后首个请求正常。
同时发现小卡的低分辨率解码器会接力到大卡，Bridge 改为此时对大卡重新请求。见 [fix14 说明](DYNAMIC-ARTWORK-AM-FIX14-TEST.zh-CN.md)。

2026-10-03 AM fix13：214150 日志确认卡住源于 AM 进程被系统后台冻结（本地解析步骤停 8–10 秒，并在 SystemUI 调用时恢复）。
Bridge 请求期间以前台服务级别绑定并每秒心跳；插件新增暂停检测，暂停期间的超时不进失败缓存；
大卡不再复用小卡的低分辨率视频。换面观感保持 fix12，记为已知限制。见 [fix13 说明](DYNAMIC-ARTWORK-AM-FIX13-TEST.zh-CN.md)。

2026-10-03 AM fix12：205833 日志显示锁屏周期后的首个请求仍会卡死，fix11 等待方到期后也没有输出。
插件改为异步 disconnect 并记录卡住位置的栈帧和进程优先级；Bridge 加 10 秒请求卡死看门狗，
换面期间隐藏旧视频层、取消预览帧，稳定后淡入。见 [fix12 说明](DYNAMIC-ARTWORK-AM-FIX12-TEST.zh-CN.md)。

2026-10-03 AM fix11：201239 日志显示专辑页阻塞 30 秒而 fix10 计时器未生效（`disconnect()` 无法结束阻塞），
且 `trackGeneration` 按宿主计数，第二首起小卡与大封面代数不同使接力失效。插件把每个 HTTP 阶段放到可放弃的工作线程，
Bridge 改用会话级歌曲代数。见 [fix11 说明](DYNAMIC-ARTWORK-AM-FIX11-TEST.zh-CN.md)。

2026-10-03 AM fix10：阻塞响应头整阶段计时与换面过渡租约。见 [fix10 说明](DYNAMIC-ARTWORK-AM-FIX10-TEST.zh-CN.md)。

2026-10-03 AM fix9：193740 日志显示 fix8 的专辑页阶段上限生效，但视频档失败仍会中止整次解析，
且切面空档帧会销毁播放器使接力无法触发。插件改为跨档重试，Bridge 在空档帧保留同曲解码器。
见 [fix9 说明](DYNAMIC-ARTWORK-AM-FIX9-TEST.zh-CN.md)。

2026-10-03 AM fix8：192119 日志显示锁屏即小卡时专辑页首次抓取涓流 18.7 秒才失败。
插件加按阶段时长上限与文本 gzip；Bridge 增加同曲换面接力，把播放器直接挂到新 view，
避免重新 prepare 时露出静态封面。见 [fix8 说明](DYNAMIC-ARTWORK-AM-FIX8-TEST.zh-CN.md)。

2026-10-03 AM fix7：191055 日志显示首次缓存已正常，小卡偶发不播放来自“请求未完成即切回大封面”。
同曲换面保留已发起的请求（6 秒窗口）、绑定握手 8 秒时限、插件改为“已开始即完成”、
收窄 AmCache 锁避免慢 I/O 阻塞工作线程。见 [fix7 说明](DYNAMIC-ARTWORK-AM-FIX7-TEST.zh-CN.md)。

2026-10-03 AM fix6：用户指出下载不应与显示状态联动。Bridge 只在功能关闭/detached/binding_not_ready 时
放弃已发起的查询；插件在身份匹配成功后把客户端取消转为 detached，下载完成并落缓存。
见 [fix6 说明](DYNAMIC-ARTWORK-AM-FIX6-TEST.zh-CN.md)。

2026-10-03 AM fix5：185656 日志显示首次缓存被 `lockscreen_inactive` 两次取消、瞬时网络失败整链重来。
门禁原因拆分为具体值并只让“表面仍存在”的暂时状态继续取资源；插件对瞬时 IO 在同请求内有界重试。
见 [fix5 说明](DYNAMIC-ARTWORK-AM-FIX5-TEST.zh-CN.md)。

2026-10-03 AM fix4：184839 日志显示 fix3 的逐候选门禁日志逐帧交替、占满每秒诊断预算，
使挂载/首帧/重试事件被丢弃。改为按 surface 聚合计数并复用判定结果，仅 Bridge 更新；
见 [fix4 说明](DYNAMIC-ARTWORK-AM-FIX4-TEST.zh-CN.md)。

2026-10-03 AM fix3：针对 173246 网络/尺寸回归，加入符合上限的同专辑跨尺寸缓存、默认网络恢复唤醒与传输失败 epoch，
分离同曲短暂布局变化期间的资源获取和视觉挂载，增加具体门禁/HTTP 阶段诊断。
Bridge 仅新增只读网络状态权限，仍无 INTERNET；设备复测步骤见 [fix3](DYNAMIC-ARTWORK-AM-FIX3-TEST.zh-CN.md)。

2026-10-03 AM fix2：按用户要求对同名同艺人的 Explicit/Clean 发行版对采用封面等价策略，
保留精确 ID 与其他版本约束；仅更新独立插件。新测试包及实际 Showgirl 来源证据见 [fix2 测试](DYNAMIC-ARTWORK-AM-FIX2-TEST.zh-CN.md)。

2026-10-03 D1 fix1：根据 055146 日志加入独立 source 切换证据、500ms 后 fresh controller 确认，
修复同艺人同长的无 ID 切歌阻断；增加到期主动重试和单任务取消。
AM 插件新增严格专辑 fallback、整专辑目录/视频索引与分项匹配计数。原生过渡/渲染不改。
本地 Bridge 698 通过/6 跳过，AM 38 通过，Lint 无错误；新行为待真机复测，见 [fix1 测试](DYNAMIC-ARTWORK-AM-FIX1-TEST.zh-CN.md)。

2026-10-02 增强诊断：针对首轮设备日志仅有 21 条 artwork 事件、58 次被统一节流抑制的缺口，
补充宿主/Flow/关联/metadata/会话完整链路，使用 owner 独立去重与有界输出。
绑定、身份判定、生命周期动作与显示门禁保持原逻辑，本次没有修复或宣称闭合反复重建与沉浸绑定问题。
新增 10 个诊断回归测试全部通过；Bridge 合计 657 个测试（651 通过、6 个既有 fixture 跳过），
Lint 0 errors、47 warnings，Debug APK 构建通过。实际复测步骤见 [绑定测试](DYNAMIC-ARTWORK-BINDING-TEST.zh-CN.md)。

2026-10-02 fix3：根据增强日志确认的失败点，沉浸页改用 scoped `mpModule` getter；
将唯一 session 匹配与歌曲文字一致性分离，后者暂态只失效展示，不重建同一 token 的 controller。
精确会话下启用受控无 ID fallback，要求三处文字一致、已知时长、至少 500ms 稳定及后台 fresh metadata 复核；
DISPLAY 字段冲突、同 artist/相近时长仅 title 改变、可靠 ID 暂时丢失均拒绝。
新增 11 个相关回归全部通过，Bridge 合计 668 个测试（662 通过、6 个既有 fixture 跳过）。
Lint 0 errors、47 warnings，Debug 构建通过；实际 getter、异步复核与不重建仍待本包设备复测。

## 当前交付与边界

第一阶段已实现 **独立本地资源 APK → v1 Binder → Bridge App 离线预览**。
第二阶段已接入独立 SystemUI 宿主/会话绑定主链，详情见 [绑定实施报告](DYNAMIC-ARTWORK-BINDING-SURVEY.zh-CN.md)。
本机 C16 user 单槽大封面与小卡片固定视频已有用户基本功能确认；`lyrics-log-20261003-044930.txt` 对应反馈未见明显异常。
这不覆盖全机型、C17 双槽、长期资源预算或该窗口末尾的关闭开关验收。
独立 AM 插件与真实歌曲已在本机端到端确认；fix19 起由正式“动态封面”设置持久控制（默认关闭），尚未发布。
联调期间安装了 Debug 测试包，未修改歌词 Providers，未推送或发布；2026-10-03 起在 `feat/dynamic-artwork-provider` 本地提交。

| 切片 | 实际状态 |
| --- | --- |
| A 协议/边界 | v1 AIDL、严格 Bundle codec、资源声明、身份与文件校验已实现；设备 Binder 互通待验收 |
| B 宿主与会话 | 主链已实现，fix4 在本机日志验证持续更新、退出释放和重进；跨版本/用户及初始化补装仍未全面验收 |
| C 本地固定视频 | 本机大封面与小卡片基本功能已由用户确认；C17 双槽未实现，长期性能未验收 |
| D 资源插件 | Local 与 AM 分开 APK；显式绑定、调用 UID、取消、租约、死亡处理已实现；AM 真机生命周期待验证 |
| E 联网 Resolver | AM 公开 Web adapter、匹配、AVC variants、完整下载、归零重封装与缓存已实现；来源 SLA、正式发布签名未冻结 |
| F 设置与回归 | fix19：正式设置页、独立持久配置域与 SystemUI 副本、状态查询、配置备份已实现；调试页仅留预览与固定视频测试；fix20 后开关切换已由用户本机确认，备份恢复未专项设备验证 |
| G 发布准备 | 未开始；现有版本、签名契约、资产清单和发布流程未修改 |

## 工程布局

本次将共享协议与测试插件放在 Bridge 仓库的独立 Gradle 模块中，便于同一分支核对线协议。
**共同源码仓库不等于共同 APK**；依赖方向为两个 APK 各自依赖协议库，Bridge 不依赖插件实现。

| 模块/路径 | 作用 |
| --- | --- |
| `artwork-contract/` | 通用 AIDL、基础 DTO、Bundle 编码、当前签名集合和只读文件检查；不依赖 Bridge 业务或歌词 core |
| `artwork-provider-local/` | 普通 Android 测试 APK，applicationId 为 `io.github.andrealtb.artwork.local` |
| 动态封面 Provider | 2026-10-04 迁至 Providers 仓库的 `artwork-provider-am/`（v1.0.0，applicationId 仍为 `io.github.andrealtb.artwork.am`）；协议契约仍以本仓库 `artwork-contract/` 为准，Provider 侧保留经校验的镜像 |
| `app/.../systemui/artwork/ArtworkProviderDirectory` | 发现与显式组件/签名/声明版本校验 |
| `app/.../systemui/artwork/ArtworkProviderClient` | 后台 IPC、请求期限、晚结果拒绝、FD 复核与死亡处理 |
| `app/.../systemui/artwork/DynamicArtworkRenderer` | 本地 FD 播放、双重首帧门禁、进程内单播放器与释放 |
| `app/.../systemui/artwork/ArtworkRequestStamp` | 八维请求 stamp；当前已用于宿主/会话/歌曲绑定，client/service/config 与资源播放阶段仍未接入 |
| `app/.../systemui/artwork/ArtworkPlaybackPolicy` | 锁屏/交互/会话/可见/播放/过渡门禁与沉浸优先策略，尚未接入 SystemUI |
| `app/.../DynamicArtworkSettingsActivity` | 仅 Debug 主设置显示入口的离线验证页 |

协议 action 与 package 在此阶段冻结为：

```text
io.github.andrealtb.artwork.action.BIND_PROVIDER
io.github.andrealtb.artwork.contract
protocolMajor = 1
protocolMinor = 0
```

接口细节见 [v1 契约](../artwork-contract/README.md)。
本地插件不是 Apple Music 来源，也不加入歌词 Provider 发布矩阵；不配置生产发布签名，Release 打包任务明确拒绝。
生产插件可以另立仓库并依赖该契约，不需要加载 Bridge 类、Hook 播放器或获取 LSPosed scope。

## 已实现行为

1. Bridge 通过 action 查询服务，验证 exported/enabled、权限可达、major 声明和**当前完整签名集合**。
   用户选定具体组件及签名，之后每次请求/连接/打开资源重新验证。身份变化不自动认可。
2. 测试插件用系统文档选择器导入视频，复制到私有目录，完成校验后以不可变版本原子替换选择指针。
   新导入不修改旧 lease 对应文件。导入并发为一，缓存总量上限 256MiB；清理保护已 pin 的旧版本。
3. 每个服务事务核对真实 Binder UID。Bridge App 必须先在插件页面明确认可当前签名；
   同用户的 SystemUI 还需与 `android` 签名匹配。没有硬编码 UID=1000，也没有给服务加 SystemUI 未必持有的插件签名权限。
4. UID + 随机 requestId + callback Binder 绑定请求。每 UID 最多 4 个、服务总计最多 16 个请求；
   租约最多 60 秒，取消、客户端死亡、服务销毁和周期过期回收都会释放 pin。
   同 UID 的包不具备额外包级隔离；已经交付的只读 FD 不可通过撤销授权追回。
5. Bridge 的能力协商、请求、openAsset、文件检查及远程取消位于进程共享的单线程、有界 IPC 队列，UI 不等待这些事务。
   12 秒期限使当前结果失效，但不会假装能终止已卡住的同步 Binder；不自动创建新线程或无限重连。
6. 双方验证只读普通文件、可 seek、20MiB、真实 MP4 容器、单视频轨、H.264/HEVC、
   最大 1080px、方形 2% 容差、最大 120 秒、无旋转、无加密视频样本、样本完整性与有界扫描。
   使用公开 `fcntlInt` 的检查要求 API 30+；Bridge 原有 minSdk 不变，旧平台该路径明确拒绝。
   样本读取不等于完整解码验收，错误编码与实际首帧仍由播放器验证。
7. renderer 静音循环，不申请音频焦点或 WakeLock。收到渲染开始与 TextureView 更新后才淡入，首帧期限 10 秒。
   MediaPlayer/Surface/FD 的操作和释放在独立媒体线程顺序执行；同一进程最多一个 renderer 播放。
8. 页面离开、重选插件、停止预览、服务断开或超时会失效旧请求并回收资源。再次进入不会自动播放。
   资源交付后继续持有连接/死亡监听至预览停止，即使 asset lease 已释放，插件死亡仍会撤下预览。
   预览只接受能力中 `localTestOnly=true` 的插件；未来 SystemUI 客户端必须拒绝这种测试资源，避免所有歌曲误显示固定视频。

歌曲匹配、session/token 关联、配置同步与 shape/transition 不能从上述能力推出。
当前预览不向插件发送真实歌曲、session、歌词或 media ID；双方均没有 INTERNET 权限。

## 本地验证

执行命令：

```powershell
scripts\dev.cmd bridge :artwork-contract:testDebugUnitTest :artwork-contract:lintDebug `
  :artwork-provider-local:testDebugUnitTest :artwork-provider-local:lintDebug `
  :artwork-provider-local:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

本地结果：全部相关任务成功。Bridge 为 632 个测试（626 通过、6 个已有 fixture 条件跳过），
协议库 6 个、测试插件 5 个测试全部通过；合计 637 通过、6 跳过，其中新增 14 个测试全部通过。
三个模块 Lint 均为 0 errors，保留编译 SDK/target SDK、exported service 与显式生命周期所有者等 warnings；
没有为通过检查添加 baseline 或关闭检查。两个 Debug APK 均已构建，`git diff --check` 通过。

JVM 测试覆盖协议纯值、来源/单位/大小限制、失败分类、
UID 隔离、重复请求/预算、过期、旧死亡回调、服务清理，以及八维 stamp 和播放门禁。
**JVM 测试没有执行真实 Android Bundle/Parcel/Binder、媒体文件校验或解码。**
Android 相关代码的编译、Lint 与真机接受度分别记录，不能互相替代。

Debug 输出：

- Bridge：`app/build/outputs/apk/debug/app-debug.apk`。
- 本地插件：`artwork-provider-local/build/outputs/apk/debug/artwork-provider-local-debug.apk`。

两个 APK 都是本地 Debug 测试包，不是正式候选资产。

## 用户手工预览步骤（尚未执行）

1. 安装上述两个 Debug APK；本地插件无需在 LSPosed 中启用。
2. 打开“动态封面本地测试插件”，导入符合限制的方形 MP4，点击认可当前 Bridge 签名。
3. Bridge Debug 设置的兼容区域打开“动态封面 · 本地测试”，选择插件并点击预览。
4. 验证首帧、静音、停止、退出、重选、不同视频再导入、授权撤销、插件停止/卸载与晚回调。
   实际设备现象和日志由用户提供；当前未授权代理安装、播放或停止进程。

这里验证的是 App 内资源链路。无需重启 SystemUI；它尚未订阅本功能。

## 2026-10-03：fix4 沉浸输入生命周期修复

用户提供的 `lyrics-log-20261003-005854.txt` 证明普通卡与沉浸页均已绑定，且同 token 的 sessionEpoch 保持为 1；
但同一沉浸 surfaceEpoch 下 watchedCount 从 2 增至 16，快速切歌后模型更新滞后。
C16/C17 参考源码确认 scoped Flow accessor 每次创建新的 WhileSubscribed 派生 Flow，不能仅靠 getter Hook 保证持续更新。

新增 `ArtworkModuleSource`，通过普通快照 accessor、已有 section 输入与模块 Map 的对象一致性绑定当前 mpModule；
新增 `ArtworkFlowBinding`，仅在观察生命周期强持有最多两个输入对象，结束时释放 owner 与引用。
更新提交复核 input lease/surfaceEpoch，关闭后的晚回调丢弃；关闭观察也释放全部输入和会话。
不创建派生 Flow，不启动额外协程订阅。普通卡保留原输入路径，fix3 的精确会话与保守歌曲身份门禁继续生效。

本地 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 通过：676 个测试，670 通过、6 个既有 fixture 跳过，
其中新增 8 个测试通过；Lint 0 errors、47 warnings。最后补充关闭/回调竞态保护后，单元测试和 Debug 构建再次通过。
这些结果不证明设备中的 Flow Hook 已持续触发；需按绑定测试文档复测一分钟快速切歌、退出重进与观察关闭。
当前 SystemUI 动态视频仍关闭，测试包仅验证绑定。没有代理安装或执行真机操作。

## 2026-10-03：fix4 设备证据与切片 C1

用户日志 `lyrics-log-20261003-014001.txt` 中，沉浸输入首次绑定为 `stable_section_input watchedCount=2`；
同一 surfaceEpoch=3 / sessionEpoch=1 从 01:40:25 到 01:41:44 持续切歌，generation 从 1 推进至 14。
01:41:48 detach 释放两个输入并归零，01:42:01 重进后恢复并持续更新，01:42:33 再次释放归零。
没有发现 artwork observer error、Flow rejected 或崩溃标记。这证明该设备本轮的绑定更新与 detach 生命周期，
不代表跨版本或视频视觉验收。关闭调试的观察释放未覆盖，用户表示本轮无需补测，不作为推进阻塞。

切片 C1 新增被动宿主几何探测与 renderer 不可复活的时效门禁；
App 预览已接入 previewEpoch 和页面/插件选择门禁，SystemUI 展示仍关闭。
实现、静态证据、设备采样步骤与未实现边界见 [C1 报告](DYNAMIC-ARTWORK-HOST-SURVEY.zh-CN.md)。

## 2026-10-03：本机沉浸过渡与挂载

C1 设备日志 `lyrics-log-20261003-020409.txt` 确认本机为 Material 单槽 + FrameLayout，实际 bounds
从 1312×1312 变为 1312×1284，FIT_XY、无前景/tint，outline 报告半径 80px，并存在 alpha/scale/translation 布局动画。
本轮新增官方 Path + outline 挂载、公开 draw/invalidateSelf 过渡门禁，以及独立的临时 Debug 固定视频测试通道。
静态证据、开关行为、测试包和完整步骤见 [沉浸测试报告](DYNAMIC-ARTWORK-IMMERSIVE-TEST.zh-CN.md)。

本地 683 个测试：677 通过、6 个既有 fixture 跳过；Lint 0 errors、48 warnings；Debug 构建通过。
新增 3 个过渡测试验证长期运行帧仍失效、未知/反向/重置拒绝和中途替换后的晚 draw 拒绝。
没有隐藏 API 抑制、安装或设备操作；实际 smooth 裁剪、Texture 合成、触摸与资源生命周期待验收。

## 2026-10-03：C2 fix1 过渡观察时机

用户日志 `lyrics-log-20261003-030541.txt` 已收到固定视频启用请求，宿主绑定 ready/playing，布局稳定后
transitionComplete 仍始终为 false；未进入 mount/resource/render 链。它证明首个阻断在过渡门禁，不证明视频解码失败。
源码确认旧观察只接受 attached 宿主，attach 前开始可被丢弃；日志尚未区分缺起点与绘制失效。

fix1 加入有界弱引用的早期开始记录，之后按当前沉浸 ImageView 的实际 drawable 实例与 owned draw 认领；
保留未知/反向/重置拒绝和实际终止绘制门禁。增加分阶段、本地 drawableRef 诊断与有界嵌套绘制栈。
687 个测试，681 通过、6 个既有 fixture 跳过；Lint 0 errors、48 warnings。实际挂载、资源与首帧仍待本包复测。
插件和协议未修改，不要求重装插件或重新转换视频；测试步骤见沉浸测试报告。

## 下一实施阶段

2026-10-03 D1 已完成独立 AM provider 和真实歌曲 Debug 接线，测试包与完整步骤见
[AM 联调测试](DYNAMIC-ARTWORK-AM-TEST.zh-CN.md)。下述 C2/C3 段落为阶段历史。
当前下一步先验收 App 匹配/Android 重封装/锁屏首帧和缓存，再处理正式配置与更广机型。

本机 C2 fix1 用户视觉反馈与 `lyrics-log-20261003-031842.txt` 已确认大封面基本展示成功：
暂停还原、切歌先静态后视频、拖进度不改变独立循环。普通卡仍保持静态；用户要求下一切片继续小卡片，
取证、低分辨率策略与实施顺序见 [小卡片计划](DYNAMIC-ARTWORK-CARD-PLAN.zh-CN.md)。小卡片挂载已实现待设备验收，
新测试包和完整步骤见 [C3 小卡片测试](DYNAMIC-ARTWORK-CARD-TEST.zh-CN.md)。本地 683 个测试通过、6 跳过，Lint 无错误。

继续补齐本机单槽的生命周期与性能验收；缺失能力保持静态。不把已有 C17 歌词适配当成动态封面验收。
普通卡的内部特效、C17 双槽/controller、正式展示配置与备份仍需后续实现。

再接受保护的展示配置通道与备份，完成真正的卡片/沉浸页本地视频阶段和 C16/C17 设备验收，
然后实施生产资源插件、Apple 来源匹配/下载/缓存。生产 endpoint、签名与发布安排在对应阶段确定。
