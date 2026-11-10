# 切歌过渡残影：模型、原生列表与像素的事务交接

## 复测结果与证据边界

RC2 的行位置检查未解决残影。新录屏中，约 2.5 秒歌词区已清空，2.6–2.7 秒单独出现三条共有制作信息，2.8 秒淡出，然后才出现新曲列表。新旧歌曲 Maroon / Anti-Hero 均有相同的制作信息。制作信息本身是合法歌词内容，不应按文字黑名单删除。

源码确认：RC2 的 `publishWordLyricModel` 在后台解析完成后立即替换 `currentWordLyricModel` 并重置可变 renderer 缓存。原生 adapter 的整份数据稍后才更新；绘制只检查行位置，再按当前模型中的文字或最近缓存帧选行。一个仍绑定旧列表的 holder 完全可能具有有效 position。旧曲非共有文字无法匹配新模型，共有文字却继续匹配，于是同一份旧列表只剩几行能被画出。仅排除 `NO_POSITION` 或同位置被替换的 holder 无法修正这个交接顺序。

录屏与上述源码缺口吻合，但录屏不能证明每一帧的 adapter/holder 内部状态。新的设备视觉验收仍须使用最终签名 RC；此前“官方渲染闪现已修复”的反馈不能替代本次残影修复验收。

## 原生数据与绑定契约

使用两个 SystemUIPlugin 样本核对实际提交点：

| 样本 | 原生列表提交与绑定 |
| --- | --- |
| UI7 `SystemUIPlugin.apk`，`com.oplus.systemui.plugins` 16.001.002，SHA256 `a63e68702113ec0ae2701d9042fb10010a49c07f04d40a368c8dd2a07ff15b83` | `LyricsRecyclerView.setLyrics(List<m6.r>)` 更新 adapter `x6.h` 的列表；`x6.h.onBindViewHolder` 执行 TextView 绑定。`m6.r` 保存 long 时间与 String 内容。 |
| C17 `SystemUIPlugin.apk`，17.000.002 | `LyricsRecyclerView.o(media.h, boolean)` 后还有异步 fade / submitList，方法返回不等于提交。adapter `com.oplus.systemui.plugins.shared.template.component.media.view.n` 的 `getCurrentList()` 才是已提交列表，`onBindViewHolder` 执行行绑定。 |

UI7 反编译参考位于工作区 `.codex-tmp/ui7-plugin-jadx/sources/`，样本位于 `PlayerSource/SystemUI/UI7/SystemUIPlugin.apk`；C17 样本位于 `logs/OP16/SystemUIPlugin.apk`。这些均为只读参考，不作为可构建源码。

完整显示数据按原生解析契约匹配：有序时间与文字、LRC 百分秒精度、多时间标签、稳定排序，以及 JSON `t/c/tx` 分段。已验证的 C17 行类型使用固件自己的 `com.oplus.systemui.plugins.shared.utils.e.a(String)` 规范化长文字；JSON 按段规范化，不能先合并再调用。未知布局明确回退原生，不用模糊文字相似度认领新模型。

## 事务实现

1. 后台只产生候选模型。`publishLyricRenderBinding` 在主线程核对 load epoch 和模型指针，再一起提交绘制模型、完整显示数据期待以及可变 renderer 缓存重置。绘制 Host 读取已经发布的模型，并把该模型显式传给 frame resolver；不同模型的缓存帧不能混入。
2. `OfficialLyricDataset` 表示不可变的整份原生显示数据。`LyricRenderTransactions` 只在原生提交的数据完整匹配期待后允许继续；不能由某一条共有文字或一个有效 position 推断整曲已交接。
3. 观察歌词 adapter 具体类的真实 `onBindViewHolder`，将行与 dataset epoch、position owner 关联。旧 bind 不能认领新 epoch，嵌套 payload bind 不能被外层旧完成事件覆盖；同位置新 owner 接管后，旧 holder 失去绘制权。
4. DiffUtil 可能保留无需重绑的未变行。只有整份新列表已经确认匹配后，才按原生行的完整时间与内容迁移这些行；临时 `NO_POSITION` 保留来源记录，恢复后可重新核对。lineage 为独立标记，不形成持有历史整曲模型/数据的 token 链。
5. 在应用新模型的高度前先核对、迁移现有绑定 cohort。geometry、prime、prebind、surface readiness 与普通绘制共用归属检查，避免其中一条旁路提前操作旧列表。
6. 歌词 RecyclerView 的一次 draw 作为整批像素提交边界。完整 dataset、可见行绑定与 geometry 尚未一致时，原生 draw 仍执行，但使用临时空 Canvas clip；`finally` 恢复 Canvas。一致后整批直接绘制，并使旧/隐藏 holder 的硬件 display list 失效，避免其被再次回放。

此机制不新增固定等待、不改 View alpha/visibility、不改原生动画和滚动时序，也不创建 offscreen layer。普通 TextView 不进入该流程。删除 RC2 的 `LyricRecyclerRowBinding` 位置启发式和对应测试，避免同时维护两套归属规则。

## 本地验证

执行 `scripts/dev.cmd bridge :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:lintRelease`：

- 860 项单元测试：854 通过、6 项既有外部样例条件测试跳过，0 失败、0 错误。
- 完整数据匹配 16 项、事务/归属 22 项、真实 Android View 切换绘制 10 项、native Canvas 像素事务 3 项，均通过。
- 回归包含三条共有制作信息、模型先到/列表先到、旧与新 holder、DiffUtil 未重绑行、乱序/嵌套 bind、NO_POSITION 恢复、AOD/settle、透明预绘制、失败回退、普通 TextView，以及 clip/save count/异常恢复。
- native Canvas 测试实际执行位图绘制，确认未提交时三行均无像素，提交后一起可见；没有以源码字符串断言代替像素测试。
- assembleDebug 成功；lintDebug / lintRelease 各 0 错误、54 个警告；`git diff --check` 通过。

## 发布与设备状态

RC1 已被后续代码替代。RC2 已在设备复测中确认仍有三行残留。RC3 出现下述正式混淆包回归，同样不可发布。后续使用新签名 RC4 验证连续切歌、共有制作信息、展开/亮屏、逐字/翻译与 AOD 恢复。

## RC3 正式包回归：R8 改写了外部类型标识

用户复测出现原生大字、高亮与翻译自绘失效。最新同窗日志确认模型正常解析、发布，但连续出现 `LYRIC_RENDER_FALLBACK reason=dataset-unsupported`，活跃行刷新均为 `updated=0`，没有成功安装 adapter binding transactions 的记录。这是 Bridge 自绘主动回退原生，不是 Provider 未送达歌词。

独立检查实际签名 RC3 的 DEX：入口为 `r2.y1`，`t3(Object)` 中原先用于识别宿主 `RecyclerView$Adapter` 的类名比较被 R8 改写为 `"a1.k0".equals(superclass.getName())`。`a1.k0` 是本模块打包的 AndroidX 类型名，不属于 SystemUI 的 ClassLoader。原生 C17 APK 的真实 DEX 类型仍为 `androidx.recyclerview.widget.RecyclerView$Adapter`，两个标识不匹配，导致 `tryInstallLyricAdapterBindHook` 始终失败，原生 dataset 被标成 unsupported。仅检查 debug 测试、签名、包名、哈希，不能证明这条 release 反射契约成立。

改为 `LyricRecyclerAdapterContract` 从宿主自己的层级和反射签名发现 Adapter 基类：公开抽象的 count、bind、create 方法必须使用同一宿主 holder 类型，holder 必须包含公开 final View 字段。绑定方法也从该宿主基类的实际签名解析到具体 override，不依赖本模块的 AndroidX 类名、类身份或混淆名；基类默认 payload 包装方法不进入 Hook。类型发现与 Hook 安装按类缓存，并为不支持的契约记录一次明确诊断。

RC4 本地检查：866 项测试，860 通过、6 项既有条件跳过，0 失败/错误；assembleDebug、lintDebug、lintRelease 均通过，lint 各 0 错误、54 警告。新增 6 项覆盖真实 AndroidX Adapter、不同类名/方法名的宿主结构、无关 holder 重载、默认包装方法、继承 override 和不完整结构。最终签名 APK 的实际 R8 识别路径及全部资产均已独立核验。

用户于 2026-10-25 确认正式签名 RC4 测试通过，切歌效果满意，切换过程中无歌词残留，并明确授权正式发布。该结论对应 Bridge d82babddae1fda14ebf04856fff6947633df2327、batch 4.5.3-rc.4-d82babd-48305d0；Bridge APK SHA256 为 aa5564f5428d25b13cd32bde4fe9fa973f81b847cdc97bdd9d50c40ec4bc8da2。

## 2026-11-10：重复歌词行出现持续空帧

网易云 9.6.20 的同曲复测仍在约两分钟后出现空行。最新复现窗口中，Provider
已记录 `officialLyricRepair=true`，显示歌词字符数由 2121 变为 2330；原始逐字
模型为 62 行、61 条翻译。不能再将空白归因于 Provider 没加载更新或没有收到歌词。

Bridge 同窗记录了两类可直接定位的错误决策：

- `slot-mismatch`：原生显示行文字属于后一个模型行，严格 slot 检查持续返回空帧。
  此记录本身不能证明 holder 字符串过期；完整触发原因由下述后续复测定位。
- `mapped-official-alias`：普通正文和 slot 文本不同，却因为 slot 指针相同被误认为
  是已授权的开头标题别名，选中了前一行。

修复继续以完整原生 dataset、当前 publication 和真实 bind owner 为门禁。只有
`readyRowBinding` 成功时，测量、匹配和绘制才读取该绑定不可变的原生正文及时间，
不再把此时可变的 TextView 字符串当成行身份。重复正文按该行的绑定时间区分，而非
按当前播放时间猜测另一次重复。旧 epoch、被回收的行和 unsupported 布局均不能借此
取得新模型的绘制权。开头标题别名另行记录允许的 slot 与确切文字，普通行不得走此旁路。

没有修改原生 TextView 内容、滚动、alpha、布局配置或动画时序，也没有关闭切歌事务。
诊断新增 `action=bound-row-text-recovered`，仅包含行号、时间及字符数，不输出歌词正文。

证据纠正：原生 C17 LyricParser 会按时间排序；Bridge normalizer 还会主动在末行后
8 秒追加零宽尾部 spacer。因此，原始行序差异、尾部 placeholder 和模型行数差本身
不能直接证明网易云提供了空白歌词，更不能单凭它们判断设备修复成功。

本地回归新增普通行误别名、明确标题授权、错位重复句、按绑定时间选择同文异时行、
旧/retired/unsupported 绑定不可读，以及既有标题首行兼容断言。最终设备外观仍待用户复测，
本地构建成功不代表设备验收。

本次 `:app:testDebugUnitTest :app:assembleDebug` 最终通过：872 项测试，866 通过、6 项
既有条件跳过，0 失败。测试 APK 已于 2026-11-10 04:45 覆盖安装，保留数据；设备具备
用户已有的签名兼容环境，未触发 CI、未推送分支、未创建 tag 或 Release。需重启
SystemUI/设备后由用户手动复测约 1:50–2:40、拖动进度和普通切歌。

### 后续复测：方括号正文导致原生列表吞行

用户确认上一包已显示全部歌词，但从 `The daily things like this` 开始先缺实时高光，
随后当前行持续偏向上边界。最新同窗日志在 position=97945 时记录 adapterPosition=20、
selectedIndex=21、activeIndex=20；模型第 20 行是 01:31.440 的 `[so simple and clean]`，
原生第 20 行却已是 01:36.660 的下一句。之后各行持续相差 1。此窗没有出现
`bound-row-text-recovered`，不能把这些错位继续解释为已证明的旧 View 文本残留。

只读核对 C17 SystemUI 的 `LyricParser.parse`：解析完首个时间标签后，其循环会继续
消费所有紧接的 `[...]`，即使括号内不是时间也会推进 cursor。于是
`[01:31.440][so simple and clean]` 的正文被全部当作标签消费，整行不进入原生列表；
Bridge 模型仍保留它，导致后续 adapter/model 索引、高光和当前行定位持续错开。

在 `OplusLyricNormalizer` 输出每行官方显示文本时，仅当正文以 `[` 开头，才在时间
标签与正文之间插入已有的零宽分隔符。原生标签扫描在此停止，方括号歌词和后面的
歌词均保留；自绘已有 sanitizer 会移除此字符。该编码不新增歌词项、不修改显示
文字、原始逐字时间、翻译、滚动、高度或动画。重复 normalization 也不会累计字符。

先增加 4 项行为回归再执行旧实现，4 项均失败，明确复现整行丢失、括号前缀丢失和
原生/模型索引不一致；之后再改输出编码复验。测试复用已核对的原生解析契约，包含
后续句的原生行时间/正文、与模型 adapterIndex 对齐，以及不可见分隔与幂等性。
修复后 `:app:testDebugUnitTest :app:assembleDebug` 通过：876 项测试，870 通过、6 项既有
条件跳过，0 失败；新增 4 项全部转绿。测试包于 2026-11-10 04:57:52 覆盖安装，
设备与本地产物 SHA-256 核对一致，数据保留。最终设备高光及定位仍需重启 SystemUI
后由用户复测约 1:25–2:20；未代用户操作播放、重启，未推送或触发发布。

用户于 2026-11-10 用正式签名 RC（Bridge 4.5.4/149、网易云 Provider 1.2.2/5）在设备复测
通过：歌词完整显示、同步正确、实时行不再偏移，确认可以正式发布。发布由 tag v4.5.4 触发
（workflow 42756912963），19 项资产在 Bridge 与 LSP mirror 两个 Release 间逐字节一致。
