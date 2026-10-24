# 切歌过渡残影：歌词行绑定归属检查

## 证据与结论边界

用户提供的约 30 秒连续切歌录屏中，源视频 25.4 秒的歌词区已清空，25.6 秒却再次单独出现旧的 `Recorded by...` 行，25.8 秒开始出现新歌的歌词列表。约 24.8、25.0、27.4 秒还有相邻文字重叠的画面。此处只记录视频中的可见现象，不将制作人员行本身当作异常歌词。

静态确认：原绘制流程只要求 TextView 属于歌词 RecyclerView，再按当前歌词模型中的文字或最近缓存帧选行。RecyclerView 的退场 holder 仍可能 attached；新旧歌曲包含相同制作人员或版权文本时，文字匹配不能证明这个 holder 仍是当前行。原 surface readiness 统计也没有排除这些退场 holder。

参考代码为 `PlayerSource/SystemUI/系统界面_16.99.12/java_src/androidx/recyclerview/widget/RecyclerView.java`：`getChildAdapterPosition`（约 4397 行）通过 holder 返回当前 adapter position；无效、移除或未绑定的 holder 返回 `NO_POSITION`。`findViewHolderForAdapterPosition`（约 4236 行）优先返回非隐藏 holder，允许区分更替动画中暂时占用同一位置的新旧两份行。

合理推断：录屏中的孤立残留与上述两种 holder 生命周期窗口一致。录屏本身不能确认残留 View 的 position/holder 状态，最终效果需要相同切歌场景的设备复测。

## 最小修复

- 增加 `LyricRecyclerRowBinding`：排除明确返回 `NO_POSITION` 的行；如果 `getChildAdapterPosition` 仍返回有效位置，但该位置的当前 holder 已是另一实例，也排除旧行。
- 将检查放在 `OfficialLyricDrawCoordinator` 的缓存帧、原生绘制失败回退及自绘之前。被排除的行清掉最近帧，并复用既有的、绑定 epoch 限定的有限重绘重试。相同 TextView 恢复有效绑定后即可继续绘制。
- surface readiness 不再把已失去绑定归属的行计入新歌词准备完成的统计。
- 只在有自绘模型且确认属于歌词列表时应用。反射入口缺失、调用失败或暂时没有可查询的 holder 时保留原兼容路径；layout position 不与 adapter position 混用。透明列表中的有效新行仍可提前生成自绘像素。

## 验证

回归覆盖明确失去位置、位置恢复、同一位置新旧 holder、缺失/失败的反射入口、layout/adapter position 分离，以及使用真实 Android View 的绘制协调流程：旧行与新歌文字相同、缓存帧、AOD/settle 窗口、已有 renderer 失败回退、透明列表预绘制、普通 TextView 与无自绘模型回退。

本地执行 `scripts/dev.cmd bridge :app:testDebugUnitTest :app:assembleDebug` 通过。共 828 项测试，822 通过、6 项条件跳过、0 失败/错误；其中新增的行绑定测试 12 项、真实 Android View 绘制协调测试 7 项全部通过。`git diff --check` 通过。

`lintDebug`、`lintRelease` 均通过，分别为 0 错误、54 个既有警告；Bridge/Provider 4.5.3 发布契约校验通过。

设备状态：待用户复测；这些本地结果不代表设备视觉验收。
