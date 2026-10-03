# D1：独立 AM 来源与锁屏真实查询测试

日期：2026-10-03。仅本地 Debug 联调，不是正式发布。
本页保留 D1 初始包记录；针对切歌恢复问题的新包见 [fix1 步骤](DYNAMIC-ARTWORK-AM-FIX1-TEST.zh-CN.md)。
实现阶段：公开 Web adapter、保守匹配、AVC variants、完整下载、无损重封装、缓存和真实歌曲查询已接线。
Android 原生重封装/首帧及锁屏端到端尚待本包真机确认。

## 1. 测试 APK

- Bridge：`artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-debug.apk`
- 独立插件：`artifacts/Artwork-Provider-AM-20261003-debug.apk`

本轮不需要转换或导入 AM 视频；离线 Local Provider 保留，可用于 C3 回归，但 AM 联调选择 AM 插件。
AM APK 是普通 App，不需要加入 LSPosed scope；Bridge 继续使用已有 system/SystemUI scope。
两个 APK 均为 Debug 签名，Bridge 版本仍为 4.4.0/144，AM 为 0.1.0-am-test/1。
本次没有代为安装、重启 SystemUI、播放或抓取设备日志。

已通过 apksigner 验证，两包 signer SHA256 均为
`5a6fd43fc11b4c70ff12b6f82a7261c2ed9c4945692b3d818ca50bc64c8dd869`。

| 资产 | 文件字节数 | 文件 SHA256 |
| --- | --- | --- |
| Bridge D1 AM | 9816777 | `B761A8312446DDD8BC7C89CB5A4D1F7FB7E4B99EBCB73AA15BFED2B47277C60B` |
| AM Provider | 173852 | `552F66F2E340268548D9E93BC504AD2B1D59994808F924F36EE83A2568F0A38E` |

已核对合并 Manifest：只有独立 AM APK 新增联网权限；Bridge 没有 INTERNET，scope 保持 system/com.android.systemui。

## 2. 先验证来源与 App 预览

1. 安装/更新两个测试 APK，Bridge 按原方式启用；用户自行重启 SystemUI，使新代码生效。
2. 打开“AM 动态封面插件（测试）”：勾选“启用 AM 封面来源”和“开启脱敏诊断日志”，市场先填 `us`，保存。
   使用有互联网的非计费网络；计费网络仅在明确勾选允许后下载。
3. 点击“授权当前 Bridge 签名进行预览”。来源未启用时返回 NETWORK_BLOCKED；未授权当前 Bridge 签名时 App 预览连接会被拒绝。
4. Bridge → 动态封面 → 选择 AM 插件。填写以下公开样本：

   ```text
   歌曲：Style (Taylor's Version)
   艺人：Taylor Swift
   专辑：1989 (Taylor's Version) [Deluxe]
   时长：231000
   Apple 链接：https://music.apple.com/us/album/1989-taylors-version-deluxe/1713845538?i=1713845746
   ```

5. 点击“预览 AM 查询结果”。该入口目标显示 288px，预期选择真实 AVC 408×408；等待网络、归零重封装和首帧。
   不要点“预览本地测试视频”，它只接受 Local Provider 的固定视频能力。
6. 停止并再次预览相同查询，预期 `ARTWORK_AM_CACHE reason=validated_file_hit`，不重复下载。
   再断网重复同一预览，已验证、未过期缓存可以回放；从未匹配的歌曲离线保持静态。

首版严格匹配，输入曲名必须保留 Taylor's Version 等版本信息；`231000` 是毫秒，不是秒。
链接可以精确约束身份，但仍须与输入文字和时长一致，不靠链接绕过核对。

## 3. 验证锁屏自动匹配

1. 保持 AM 插件选中，点击“开启 AM 锁屏联调”，确认发送当前歌曲查询的说明。
   “开启锁屏固定视频测试”仍属于 Local Provider，不能用于 AM。
2. 播放上述专辑的一首歌曲并锁屏；这次来自播放器实际 metadata，不使用 App 预览输入。
   若名称、艺人、时长不完整或版本无法唯一确认，静态回退是预期，需要日志区分来源失败。
3. 大封面与小卡片都应在原生过渡完成、歌曲绑定稳定且视频首帧就绪后显示动画。
   普通卡优先 AVC 408；本机约 1312px 的大封面查询上限 1080，选择满足预算的 AVC 1080 或更低回退。
4. 同专辑切歌：核对新歌先确认身份，但同一市场/专辑/variant 可复用已验证视频，日志应有 `album_variant_hit`。
   相同原封面无翻转时不强制造翻转；不同原封面保留原生动画。
5. 快速 A→B→C、切换原版/重录版/Deluxe、不带动画的专辑：旧结果不得盖到新歌，歧义保留原封面。
6. 暂停/恢复、拖动进度、切换大小封面、解锁、息屏/AOD、再次亮屏：沿用 C3 生命周期；视频不跟随歌曲 seek。
7. 最后点击“关闭锁屏动画联调”。重启 SystemUI 后也应关闭，须手动重新开启。

这仍是本机已验证单槽/C16 user profile 的 Debug 入口，不声明 C17 双槽或其他机型可用。
正式持久开关、备份和生产发布尚未完成。

## 4. 错误与网络回归

- 缺艺人或时长：AMBIGUOUS/identity_fields_missing；不搜索一个“像是”的专辑。
- title/artist 一致但多个专辑版本无法区分：AMBIGUOUS；不按谁有视频来选择版本。
- CN iTunes 查询不完整：RETRY_LATER/catalog_match_unconfirmed；不当成永久 NO_MATCH，不静默改 US。
  预览可用包含 albumId 的准确 CN 链接直接核对公开专辑曲目表。
- 公开页面结构、401/403、404、429、网络失败分别保留原因和短退避；只有确认匹配专辑缺动画才 NO_MOTION。
- 默认禁计费网络、未启用来源：NETWORK_BLOCKED；未过期完整缓存仍可离线使用。
- 下载或重封装过程中取消/关闭预览：临时文件不得成为 READY，旧回调不得显示，租约应释放。
- 清除缓存会保留仍在租约中的文件；已经打开 FD 的播放器仍有独立持有关系。
- 修改市场/网络设置后，重新开启 Bridge 联调；当前成功播放不会因插件配置变动自动撤下。

首版不处理加密、多文件 HLS、竖版、HEVC、艺人别名/复杂合作名，也不在 SystemUI 转码。
没有后台账号或 token；公开来源变化需要 adapter 更新，不代表其 SLA 获得保证。

## 5. 日志抓取

在插件开启脱敏诊断，Bridge 开启既有 MEDIA 调试。使用完整新复现窗口：

```powershell
.\scripts\capture-lyrics-log.ps1 -Provider bridge
```

脚本已将 `CLL-Artwork-AM` 放入公共 Tag 集合；setprop 不替代插件诊断开关。
不使用旧 ArtworkProbe 过滤模式。用户自行开始抓取，然后重新开启联调/播放复现。

关键链：

```text
ARTWORK_TEST_CONFIG mode=live_query
ARTWORK_AM_RESOLVE / ARTWORK_AM_CACHE
ARTWORK_AM_MATCH
ARTWORK_AM_VARIANT width=408|768|960|1080
ARTWORK_AM_DOWNLOAD -> ARTWORK_AM_REMUX -> ARTWORK_AM_READY
ARTWORK_AM_RESULT
ARTWORK_TEST_RENDER_STATE state=playing
ARTWORK_PROVIDER_RESULT status=... reason=... retryAfterMs=...
```

Provider 不记录曲名、完整 URL、原始媒体 ID、token；尺寸和字节数可用于判断具体 variant 与重封装进度。
请提供日志、首次失败时间，以及是 App 预览还是锁屏自动匹配失败。

## 6. 本地验证与设备边界

执行 Bridge 与 AM 模块相关单元测试、Lint、Debug 构建。
Bridge 691 个测试：685 通过、6 个已有 fixture 跳过；AM 31 个测试全部通过。
Lint：Bridge 0 errors/48 warnings，AM 0 errors/4 warnings；未新增 baseline 或关闭检查。
覆盖同名同长版本冲突、Unicode、URL 身份、真实公开页面/清单、byteranges、尺寸/codec 选择、租约、取消及缓存 pin。
源码 diff/XML/PowerShell 语法检查通过，歌词 Providers 仓库未修改。

下载的公开 AVC 408 样本实测 728484 字节、H.264 High/yuv420p、起点 10.041708s、容器 duration 26.725042s；
HLS 循环 16.72505s。因此插件无损复制压缩 samples、归零 PTS，再用既有 verifier 核对完整 MP4。
保留 B-frame 的 presentation 重排序，不强制 PTS 单调，不创建编码器/解码器。
FFprobe 的本地来源检查和 JVM 测试不证明 Android MediaMuxer、SystemUI 首帧及耗电已验收。
