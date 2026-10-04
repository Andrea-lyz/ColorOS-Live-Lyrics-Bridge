# D1 AM fix17：切歌后下载不再中断；手动绑定专辑

日期：2026-10-03。只改 AM 插件，Bridge 不变。

## 1. 已开始的下载在切歌后被中断

fix6/fix7 设计为：请求开始执行后，即使客户端取消，下载也继续完成并写入缓存（detached，上限 1）。
静态检查发现这一点实际没有做到：

- Bridge 每次请求都会先解绑旧连接，再绑定新连接（`ArtworkProviderClient.close()` → `finish()` → `unbindService`）。
  最后一个绑定被移除时，系统会立即销毁插件服务，所以**每次切歌都会销毁并重建 AM 服务**。
  设备佐证：222123 日志中同一进程的每次 `ARTWORK_AM_RESOLVE reason=started` 都在新的线程 ID 上执行；
  核心线程不会超时退出，只有线程池随服务实例重建才会如此。
- 服务的 `onDestroy` 调用 `worker.shutdownNow()`，会中断正在下载的线程；detached 计数也随服务实例重置。
  214150 日志中 3 次 `ARTWORK_AM_DETACHED` 之后均没有对应的完成日志。

修复：

- 解析工作线程（2 条）和 detached 计数改为进程级静态对象，不再随服务销毁。`onDestroy` 只停止该实例的 reaper，
  并照常处理租约：已开始的请求转为 detached 继续完成，排队未开始的请求被移除。
- detached 上限仍为 1：同时有两个已开始的请求时，只保留一个，另一条线程留给新请求。
- 服务与解析器改用 application context，跨实例运行的下载不再持有已销毁的服务。

边界：服务销毁后进程可能被系统冻结或回收，此时下载仍会暂停或丢失；暂停期间的传输超时按 fix12 不写入失败缓存。

## 2. 手动绑定专辑

用于本地音乐的专辑名/歌手与 Apple Music 不一致（如本地多了“(Deluxe)”、中文译名、艺人写法不同），
又不想修改本地文件元信息的情况。

入口：AM 插件主页 →“手动绑定专辑封面”。

1. **本地专辑名**：需与播放器上报的一致（大小写、标点不敏感）。可点选下方“最近请求的专辑”自动填入。
2. **仅限本地歌手**（可选）：留空则对该专辑名下所有歌曲生效；填写后只对署名中包含该歌手的歌曲生效，
   用于区分“Greatest Hits”之类的通用专辑名。限定歌手的绑定优先于不限歌手的绑定。
3. **搜索**：输入关键词搜索当前市场的 Apple Music 专辑，或直接粘贴 `music.apple.com` 专辑链接（使用链接中的市场）。
   单曲链接不接受。搜索只发送用户输入的关键词，按钮触发，不受“允许计费网络下载”限制。
4. **点选专辑**：插件会先抓取专辑页确认存在方形动态封面，确认后才保存；没有动态封面时提示且不保存。

解析行为：

- 请求不带 Apple 链接且命中绑定时，跳过曲目核对，直接使用所绑定专辑的动态封面；时长、歌手字段不再参与匹配。
- 视频缓存按专辑 ID 和尺寸共享，与自动匹配路径通用；绑定专用的失败缓存键包含专辑 ID，改绑后立即使用新键。
- 绑定后生效于下一次请求。Bridge 对当前歌曲的失败有退避（未确认 30–60 秒，歧义直到切歌），
  因此通常在播放该专辑的下一首歌时生效。
- 绑定和最近请求记录仅保存在插件私有存储中，不备份、不写日志；最近记录最多 30 条，可一键清除。
  日志只出现 `ARTWORK_AM_BINDING reason=user_album`、`ARTWORK_AM_MATCH reason=user_bound_album` 和阶段名称。

## 3. 本地验证

- AM：84 项单元测试全部通过（新增 8 项：绑定匹配与优先级、改绑与删除、存储容错、绑定缓存键、
  最近记录去重与结果分类、专辑搜索解析、署名首位）；Lint 0 errors / 4 warnings（与 fix16 相同）。
- 进程级工作线程、服务销毁时的 detached 行为、绑定页面 UI 与真实网络结果属于 Android 运行时行为，需要设备验证。

## 4. 设备验证

1. 覆盖安装 AM fix17，Bridge 保持 fix14，无需重启 SystemUI。
2. **切歌下载**：清除插件缓存后播放一首未缓存的歌曲，在下载进行中切到下一首。
   期望出现 `ARTWORK_AM_DETACHED reason=started_download_completes`，随后是 `ARTWORK_AM_READY`
   和 `ARTWORK_AM_DETACHED_RESULT reason=ready_cached`；切回该歌曲时命中缓存，不再下载。
   按 fix16 代码静态推断，同一场景下载被中断，日志为 `ARTWORK_AM_DETACHED_RESULT reason=error_cancelled` 或没有完成日志。
3. **手动绑定**：选一张自动匹配失败的本地专辑（最近记录中显示“未匹配”），搜索并绑定正确的 Apple Music 专辑。
   切到该专辑的下一首，期望 `ARTWORK_AM_BINDING reason=user_album` 并出现动画；最近记录状态变为“已绑定”。
4. 删除绑定后，该专辑恢复自动匹配。

## 测试资产

- Bridge 沿用 `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix14-debug.apk`
- `artifacts/Artwork-Provider-AM-20261003-fix17-debug.apk`

| 资产 | SHA256 |
| --- | --- |
| Bridge fix14（未变） | `2E9B44AF54458E86AB26DA6CDE15B130162B8F114A014BD017D43FB5D4D0123A` |
| AM fix17 | `E2D169B9FC8C5D4A477AE3E950444ECB3046E13A03558FC92DEE1621E20D6EA1` |

设备结果：014800 日志确认手动绑定生效；023112 日志确认切歌后 detached 下载完成并写入缓存（`ready_cached`）。用户已设备确认。

AM fix17 已用 `adb install -r -d` 覆盖安装，返回 `Success`；Bridge 未改动，无需重启 SystemUI。
本地 Debug 测试包，不是正式发布版本。
