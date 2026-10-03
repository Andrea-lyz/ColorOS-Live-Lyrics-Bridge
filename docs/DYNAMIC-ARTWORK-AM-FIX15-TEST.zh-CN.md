# D1 AM fix15：合作歌曲的歌手匹配

日期：2026-10-03。对应 `lyrics-log-20261003-222123.txt`（Bridge fix14 + AM fix13）。
用户反馈：第一首正常，第二首 Fortnight 的大小封面都“卡住”。

## 本次证据

- 第一首验证了 fix14：小卡 360 → 切大卡时 `skipped=resolution_upgrade playingForPx=288 neededPx=1080`，
  大卡请求 1080 档（10.7 MB），1.2 秒后播放。无进程暂停、无卡死。
- Fortnight 并未卡住，而是匹配失败：

```text
22:22:11.897  itunes_song candidates=22 titleMatches=5 artistMatches=0 albumMatches=7 durationMatches=7 completeMatches=0
22:22:11.930  RESULT retry_later_catalog_album_unconfirmed → Bridge 每 30 秒重试，大小卡相同
```

公开 iTunes 数据中该曲为艺人 `Taylor Swift`、标题 `Fortnight (feat. Post Malone)`。标题、专辑、时长都已匹配，
只有歌手不一致，说明播放器上报的歌手同时列出了两位艺人。原规则要求歌手字段规范化后完全相等。
日志按设计不记录原始元数据，具体分隔形式无法从日志确认。

## 实现（仅 AM）

- `AmIdentity.credits()`：按 `/ ; , 、 & feat. ft. featuring` 拆分歌手字段（全角先经 NFKC 折叠），
  并加入标题中 `(feat. …)`/`[ft. …]`/`(with …)` 的客串艺人。
- 歌手字段相同，或双方的“署名集合”完全相等，才视为同一歌手。集合必须相等，不能只是有交集：
  单独的主唱不会匹配署名两人的合唱，多出或缺少一位艺人都不匹配。标题、专辑、±3 秒时长的要求不变。
- 专辑搜索兜底（`albumIds`）额外接受首位署名艺人相同；之后仍按曲目表严格校验。
- 诊断中的 `artistMatches` 计数改用同一规则。

## 本地验证与边界

- AM：74 项全部通过（含原有“歌手不同不得匹配”测试）；Lint 0 errors / 4 warnings。
  新测试覆盖 `/`、`;`、`&`、`、`、全角 `／`、`feat.` 六种写法，以及多一位艺人、主唱与客串互换等反例。
- 若播放器使用未覆盖的写法（如 `and`、`x`），仍会匹配失败，需要对应日志确认。
- 需要设备复测：播放 Fortnight（AM 失败缓存约 30 秒过期），期望 `artistMatches>0`、`completeMatches>=1` 并出现动画。

## 测试资产

- Bridge 沿用 `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix14-debug.apk`
- `artifacts/Artwork-Provider-AM-20261003-fix15-debug.apk`

| 资产 | SHA256 |
| --- | --- |
| Bridge fix14（未变） | `2E9B44AF54458E86AB26DA6CDE15B130162B8F114A014BD017D43FB5D4D0123A` |
| AM fix15 | `A2FE1D5E111C559FEEAB39EF3F65A320FA5BE2CC485B1236BD1463ECE4E73525` |

AM fix15 已用 `adb install -r -d` 覆盖安装，返回 `Success`；Bridge 未改动，无需重启 SystemUI。
本地 Debug 测试包，不是正式发布版本。
