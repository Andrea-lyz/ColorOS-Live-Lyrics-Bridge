# AM fix2：Explicit/Clean 封面等价

日期：2026-10-03。用户已明确允许忽略 Explicit/Clean 这一类封面来源歧义。
这是封面选择策略，不声明两个音频录音或两个 CDN 视频文件字节一致。
本轮仅修改独立 AM APK；Bridge 沿用 D1 fix1。
后续网络恢复与大小封面缓存修复见 [fix3](DYNAMIC-ARTWORK-AM-FIX3-TEST.zh-CN.md)，该轮需更新两个 APK。

## 实施

- iTunes song/album adapter 读取专辑级 `collectionExplicitness`、release date 和 track count。
  不使用歌曲的 `trackExplicitness` 推断专辑版本：Showgirl 的 The Fate of Ophelia 与 Elizabeth Taylor 在两版均为 notExplicit。
- 完整匹配结果恰为两个不同 albumId，属于同名、同艺人的 Explicit/Clean 对，且发行日期/曲目数一致、歌曲时长差不超过原 3 秒容差时，按用户授权作为等价封面来源。
- 冷查询稳定优先 Explicit；不依赖搜索排序。精确 Apple 歌曲或专辑 ID 仍按指定版处理；已有验证缓存可继续使用。
- 歌曲搜索与严格专辑 fallback 采用同一规则。普通同名重复、缺失分类、多余第三个候选，以及 Live/Remaster/Deluxe/重录的文本或发行差异仍保留既有匹配限制。
- 匹配索引升级 v3，旧 AMBIGUOUS 缓存不阻断新算法；专辑目录/视频资源索引保持兼容，不要求清数据。
- 原视频下载、归零重封装、验证、租约、锁屏身份与原生过渡不变。

## Showgirl 证据

2026-10-03 的公共目录快照：

| 版本 | albumId | album rating | 日期/曲目数 |
| --- | --- | --- | --- |
| Explicit | 1838810949 | explicit | 2025-10-03 / 12 |
| Clean | 1842897453 | cleaned | 2025-10-03 / 12 |

The Fate of Ophelia 两版均为 226074ms；Elizabeth Taylor 两版均为 208292ms。
两版各自提供动画 URI，URI 不同；本轮采用用户指定的封面等价政策，没有下载两份视频进行内容等同比较。
Explicit 主清单实测有 AVC SDR 360/408/456/486/768/960/1080 方形档。
AVC 360 子清单为无 KEY 的单文件 byterange VOD，完整资源范围 450783 字节，EXTINF 总时长 14.93158 秒。
App/普通卡 288px 查询预期选 AVC 360；大封面沿用现有尺寸上限与排序。

## 新测试包

`artifacts/Artwork-Provider-AM-20261003-fix2-debug.apk`

文件 SHA256：`5464BA9D36225C615DAF418ECFF45188FCB1CA3F9C67C63F150E1D51EF41D911`。
apksigner 验证通过，Debug signer 与 fix1 相同：
`5a6fd43fc11b4c70ff12b6f82a7261c2ed9c4945692b3d818ca50bc64c8dd869`。

仅更新这个 AM 插件，Bridge 保留已安装 fix1。用户自行关闭再重新开启 AM 锁屏联调，
确保旧 provider 连接与本地 attempted 状态失效；无需重新导入视频或清数据。
若用户自行重启 SystemUI，仍须重新开启临时联调。

## 复测

1. AM 插件来源/脱敏诊断开启，保持联网，市场沿用原设置（本次 public fixture 为 US）。
2. 开始完整日志抓取：`scripts\capture-lyrics-log.ps1 -Provider bridge`。
3. 播放 The Life of a Showgirl 的 The Fate of Ophelia、Elizabeth Taylor 及其他曲目。
   这类 Explicit/Clean 对不应再返回 multiple_catalog_matches；首次匹配应继续到下载/READY/playing。
4. `ARTWORK_AM_MATCH_CHECK stage=itunes_song` 可出现 `completeMatches=2 explicitCleanEquivalent=true`。
   后续专辑缓存命中通常只有已选版本的曲目表，不要求每次都重新记录双候选等价。
5. 同专辑互切、大小封面切换、暂停/恢复及快切；原生过渡、取消和迟到结果行为继续按 fix1。
6. 保留 1989 原版/重录/Deluxe 的回归；这些其他版本限制未放宽。最后关闭联调并提供日志。

网络错误、目录字段缺失、格式不支持仍可能静态回退；新选来源的 Android 下载/重封装/首帧需真机复测。

## 本地验证

AM 单元测试 48 项全部通过；Lint 0 errors/4 warnings；Debug 构建通过。
新增测试覆盖稳定选择、显式链接优先、专辑级分类、其他版本/第三候选继续歧义、真实 Showgirl 公共目录和 Web 曲目表、AVC 360 清单。
公共 fixture 仅保留接口匹配所需字段，无个人数据。JVM 不执行 Android 媒体播放。
没有修改或重建 Bridge、歌词 Providers，没有安装、操作设备、提交、推送或发布。
