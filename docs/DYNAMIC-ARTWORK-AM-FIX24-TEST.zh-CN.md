# D1 fix24：动态封面 Provider 缓存上限可调

日期：2026-10-04。只改动态封面 Provider（AM 插件）：把固定的缓存上限改成可配置，并在主页加设置入口。Bridge 未改动。

## 用户需求

“缓存上限新增一个自定义？268mb少了点”

## 改动

- 缓存上限由固定 256 MiB（主页显示 268 MB）改为可配置。单位用 MB（10^6 字节），与系统 `Formatter.formatShortFileSize` 的显示单位一致，
  所以选项标签、“已用 X，上限 Y”和自定义输入框说的是同一个数字。
- 默认 512 MB（原 268 MB）；预设 512 MB / 1 GB / 2 GB / 4 GB / 8 GB（选项文字与数值一致，不含 4.1 GB 这类取整误差），另有“自定义…”可输入 64–8000 MB。
  超过范围会提示，不写入。
- 位置：主页“动态封面缓存”卡片，用量条下方新增一行“缓存上限”，右侧显示当前值，点按弹出选择；
  自定义项是数字输入框，默认填入当前值并全选，右侧带 MB 单位。
- 生效方式：修改立即保存并保留；降低上限会立刻按最久未使用顺序清理未被占用的视频，升高上限不删除任何内容。
- 用量条与“已用 X，上限 Y”改读同一份设置，替换原先硬编码的 `AmCache.BUDGET_BYTES`。
- 仍受保护的约束不变：持有使用租约（pin）的封面不会被清理；接口索引仍限制 128 条；来源关闭时不清缓存。

## 实现位置

- `artwork-provider-am/src/main/java/io/github/andrealtb/artwork/am/AmSettings.java`：
  新增 `MIN/MAX/DEFAULT_CACHE_LIMIT_MB`（64 / 8000 / 512）与 `cacheLimitMb(Context)`、`cacheLimitBytes(Context)`，读取时收敛到范围内。
- `.../AmCache.java`：上限改为实例字段 `budgetBytes`（volatile，进程内共享），新增 `setBudget(long)` 与 `budgetBytes()`；
  `get(Context)` 每次读取设置并应用；`cleanup()` 按该上限 LRU 清理。`setBudget` 忽略非正数，避免误传导致清空缓存。
- `.../AmArtworkActivity.java`：缓存卡片新增“缓存上限”行、预设/自定义对话框、`saveCacheLimit`（保存后立即清理一次并刷新）；
  `showCache` 改用实时上限。Activity 与 Service 同进程，改完立即对锁屏下载生效。
- `values/strings.xml`、`values-zh/strings.xml`：新增 8 条文案（标题、说明、对话框标题、自定义、提示、单位、范围提示、已设置提示）。
- 版本 0.2.0 (versionCode 2) → 0.2.1 (versionCode 3)。

## 本地验证

- AM：91 项测试全部通过（新增 1 项：上限降低后按最久未使用清理、pin 保留、非正数上限不会清空缓存）。
- Lint：0 errors / 5 warnings，与 fix23 相同，未新增告警。
- Bridge 未改动，未构建。

## 设备验证

已完成（adb 打开设置页并模拟点击，不是用户验收）：

1. 缓存卡片显示“已用 23 MB，上限 512 MB”，新增行显示“缓存上限 / 缓存超过上限后… / 512 MB”。
2. 点按该行弹出的选项为：512 MB（预选中）、1.0 GB、2.0 GB、4.0 GB、8.0 GB、自定义…、取消，文字与数值一致。
3. 选“2.0 GB”后右侧值与“已用 X，上限 Y”同步变为 2.0 GB；选回 512 MB 恢复；全过程应用无崩溃。
4. “自定义…”弹出输入框，预填当前值 512，右侧显示 MB；输入 32 并确定后提示范围，上限保持 512 MB 未写入。

用户已设备确认：上限可选与自定义生效并保留，降到低值能触发清理且正在播放的封面不消失，退出重进、重启手机后设置保留，锁屏动态封面不受影响。

## 测试资产

| 资产 | SHA256 |
| --- | --- |
| 动态封面 Provider fix24 `Dynamic-Artwork-Provider-20261004-fix24-debug.apk` | `9DFEAF80E5F605319385503A635CFC07D33B75402F53D3D96EFC8DE5D949D947` |

已用 `adb install -r -d` 覆盖安装，返回 `Success`；设备上版本为 `0.2.1 (versionCode 3)`。本轮未重新安装 Bridge（未改动）。
上面“设备验证”中的已完成的四项是安装后在这台设备上做的（adb 输入点击），之后用户已在这台设备上确认全部正常。
本地 Debug 测试包，不是正式发布版本。
