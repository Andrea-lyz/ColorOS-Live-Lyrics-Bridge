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

RC4 本地检查：866 项测试，860 通过、6 项既有条件跳过，0 失败/错误；assembleDebug、lintDebug、lintRelease 均通过，lint 各 0 错误、54 警告。新增 6 项覆盖真实 AndroidX Adapter、不同类名/方法名的宿主结构、无关 holder 重载、默认包装方法、继承 override 和不完整结构。候选交付前还需检查最终 R8 APK 中的实际识别路径，设备效果仍待 RC4 复测。
