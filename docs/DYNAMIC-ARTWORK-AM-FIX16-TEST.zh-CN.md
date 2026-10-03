# D1 AM fix16：以专辑为核心的曲目匹配

日期：2026-10-03。来源：用户指出动态封面属于专辑而非单曲的署名。例如一张专辑绝大多数曲目署名 Taylor Swift，
其中一首在播放器里是 `Taylor Swift/Ed Sheeran/Future`，fix15 仍然绑不上。

## 问题

以 reputation 的 End Game 为例（公开 iTunes 数据）：

| 来源 | 标题 | 歌手 | 专辑 |
| --- | --- | --- | --- |
| 商店 | `End Game (feat. Ed Sheeran & Future)` | `Taylor Swift` | `reputation` |
| 播放器常见写法 | `End Game` | `Taylor Swift/Ed Sheeran/Future` | `reputation` |

fix15 要求规范化后标题相同、署名集合相同。播放器标题不带 feat 时，标题和署名集合都对不上。

## 实现（仅 AM）

- 原有严格规则保留：标题相同、署名相同（fix15 集合规则）时直接匹配。
- **专辑锚定**（仅当查询带专辑名，且专辑名、±3 秒时长都一致）时放宽为：
  - 去掉客串段落后标题相同：`coreTitle()` 只去除 `(feat./ft./featuring …)` 及末尾的 `feat. …`。
    `Taylor's Version`、`Remix`、`Live`、`[Karaoke Version]` 等版本词保留，重录版和伴奏版不会混淆。
  - 双方主唱互相出现在对方的署名里：商店主唱（专辑艺人）在播放器署名中，播放器主唱也在商店署名（含标题客串）中。
    客串者单独署名的版本、其他艺人都无法占用这张专辑。
- 没有专辑名时仍按严格规则，不放宽。
- 专辑搜索兜底：完整歌手串搜不到专辑时，再用主唱搜一次（专辑署名通常只写主唱）；找到后仍按曲目表校验。
- 诊断计数 `titleMatches`/`artistMatches` 同步计入专辑锚定匹配。

fix15 中“`Taylor Swift/Ed Sheeran` 不得匹配 `Taylor Swift + feat. Post Malone`”的断言按新原则调整：
有专辑锚定时视为同一曲目、客串标签不准，可以绑定；无专辑时仍不匹配。

## 本地验证与边界

- AM：76 项全部通过；Lint 0 errors / 4 warnings。新测试覆盖 End Game 的五种播放器写法（含主唱顺序互换、
  纯主唱、末尾 feat.），以及无专辑、同名卡拉 OK 专辑、客串者单独署名、版本词等反例。
- 放宽只在同一专辑名之内生效；若播放器专辑名与商店不同（如带“(Deluxe)”而商店没有），仍会匹配不上。
- 需要设备复测：播放专辑内的合作曲目（如 End Game、Fortnight），期望 `completeMatches>=1` 并出现动画。

## 测试资产

- Bridge 沿用 `artifacts/ColorOS-Live-Lyrics-Bridge-artwork-D1-AM-20261003-fix14-debug.apk`
- `artifacts/Artwork-Provider-AM-20261003-fix16-debug.apk`

| 资产 | SHA256 |
| --- | --- |
| Bridge fix14（未变） | `2E9B44AF54458E86AB26DA6CDE15B130162B8F114A014BD017D43FB5D4D0123A` |
| AM fix16 | `7F806FB08559170E2D9BB260FF59FD523386DF94F8A4148A9D00831982BEABDA` |

AM fix16 已用 `adb install -r -d` 覆盖安装，返回 `Success`；Bridge 未改动，无需重启 SystemUI。
本地 Debug 测试包，不是正式发布版本。
