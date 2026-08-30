# Migrating from 3.8.x to 4.0

[简体中文](MIGRATION-3.8-TO-4.0.zh-CN.md)

4.0 changes the player integration boundary. Bridge now runs only in `system` and
`com.android.systemui`; every player-specific hook is delivered by an independent Provider that
publishes standard `MediaMetadata["lyricInfo"]` from the player's own MediaSession.

Bridge and Providers remain runtime-independent, but the first 4.0 upgrade should use artifacts
from one coordinated release so scope, package names, and payload behavior match.

## 1. Before upgrading

1. Open **Bridge configuration backup & restore** and copy a complete backup.
2. Record the old Providers enabled for each player in LSPosed.
3. Download the Bridge APK and only the Provider APKs you need from the same 4.0 release.
4. Verify `SHA256SUMS` when it is included with the release.

The Provider ZIP is an APK download bundle, not a Recovery package.

## 2. Install Bridge 4.0

The 4.0 Bridge APK keeps the same application ID:

```text
io.github.andrealtb.lockscreenlyrics
```

It can update an official 3.8.1 installation signed by the project release key. Keep exactly these
Bridge scopes:

```text
system
com.android.systemui
```

Remove Salt, Cone, and every other player package from the Bridge scope. Player processes now
belong to their Provider modules.

## 3. Replace player integrations

New Providers use new application IDs, so Android can install them beside old modules. Coexistence
does not make it safe to hook the same player twice. Before enabling a 4.0 Provider, uninstall the
old Provider or remove all of its player scopes.

| Player | Previous integration | 4.0 application ID | 4.0 asset |
|---|---|---|---|
| Salt Player | Bridge built-in / `io.github.proify.lyricon.saltprovider` | `io.github.andrealtb.coloroslyrics.provider.salt` | `ColorOS-Live-Lyrics-Provider-Salt-v4.0.0.apk` |
| ConePlayer / GP | Bridge built-in | `io.github.andrealtb.coloroslyrics.provider.cone` | `ColorOS-Live-Lyrics-Provider-Cone-v4.0.0.apk` |
| KuWo | `io.github.proify.lyricon.kwprovider` | `io.github.andrealtb.coloroslyrics.provider.kuwo` | `ColorOS-Live-Lyrics-Provider-KuWo-v4.0.0.apk` |
| LX / Walnut | `io.github.proify.lyricon.lxprovider` | `io.github.andrealtb.coloroslyrics.provider.lx` | `ColorOS-Live-Lyrics-Provider-LX-v4.0.0.apk` |
| Poweramp | `io.github.proify.lyricon.paprovider` | `io.github.andrealtb.coloroslyrics.provider.poweramp` | `ColorOS-Live-Lyrics-Provider-Poweramp-v4.0.0.apk` |
| Metrolist | `io.github.proify.lyricon.metrolistprovider` | `io.github.andrealtb.coloroslyrics.provider.metrolist` | `ColorOS-Live-Lyrics-Provider-Metrolist-v4.0.0.apk` |
| KuGou / Concept | `io.github.proify.lyricon.kgprovider` | `io.github.andrealtb.coloroslyrics.provider.kugou` | `ColorOS-Live-Lyrics-Provider-KuGou-v4.0.0.apk` |
| QQ Music | `io.github.proify.lyricon.qmprovider` | `io.github.andrealtb.coloroslyrics.provider.qq` | `ColorOS-Live-Lyrics-Provider-QQ-v4.0.0.apk` |
| NetEase / Honor / modified 9.0.40 | `io.github.proify.lyricon.cmprovider` | `io.github.andrealtb.coloroslyrics.provider.netease` | `ColorOS-Live-Lyrics-Provider-NetEase-v4.0.0.apk` |
| Apple Music | `io.github.proify.lyricon.amprovider` | `io.github.andrealtb.coloroslyrics.provider.apple` | `ColorOS-Live-Lyrics-Provider-Apple-v4.0.0.apk` |
| Spotify | `io.github.proify.lyricon.spotifyprovider` | `io.github.andrealtb.coloroslyrics.provider.spotify` | `ColorOS-Live-Lyrics-Provider-Spotify-v4.0.0.apk` |
| QiShui | `io.github.proify.lyricon.qishuiprovider` | `io.github.andrealtb.coloroslyrics.provider.qishui` | `ColorOS-Live-Lyrics-Provider-QiShui-v4.0.0.apk` |

## 4. Provider scopes

Enable each Provider only for its listed host packages:

| Provider | LSPosed host scope |
|---|---|
| Salt | `com.salt.music` |
| Cone | `ink.trantor.coneplayer`, `ink.trantor.coneplayer.gp` |
| KuWo | `cn.kuwo.player` |
| LX | `cn.toside.music.mobile`, `com.lxwalnut.music.mobile` |
| Poweramp | `com.maxmpz.audioplayer` |
| Metrolist | `com.metrolist.music` |
| KuGou | `com.kugou.android`, `com.kugou.android.lite` |
| QQ | `com.tencent.qqmusic` |
| NetEase | `com.netease.cloudmusic`, `com.hihonor.cloudmusic` |
| Apple | `com.apple.android.music` |
| Spotify | `com.spotify.music` |
| QiShui | `com.luna.music` |

After installing or changing scopes, restart the player and SystemUI. Reboot after the first 4.0
installation if either process remains on an older module generation.

## 5. What works without Bridge

A 4.0 Provider writes native `lyricInfo` into the player session. On a supported ColorOS build,
SystemUI can display the stock lock-screen lyric page with only the Provider installed. Adding
Bridge supplies generic word rendering, appearance controls, AOD handling, translation controls,
and compatibility policies; it does not request a second lyric copy from the Provider.

## 6. Removed and unsupported integrations

- QQ Music HD is not part of the 4.0 matrix.
- MusicFree, Gramophone, and Symfonium Providers are not shipped in the 4.0 suite.
- Halcyon, Flamingo, QZ Music, and PrismMusic are present in the package-only SystemUI lyric-
  entrance, media-history, and AOD compatibility policy. This does not admit any old v4 source.
- All four players must publish standard `lyricInfo` from their own MediaSession; the old v4
  fallback is gone, and no additional Provider APK is required.

This project no longer distributes Lyricon/词幕 Provider functionality. Obtain it from the
[LyricProvider original project](https://github.com/tomakino/LyricProvider). Problems with the
Lyricon display/product path belong in that project's issue tracker, not the Bridge or 4.0 Provider
repositories.

## 7. Configuration migration and downgrade

Bridge 4.0 uses lyric UI schema v3. Existing supported values are migrated automatically, including
visual settings and per-player translation preferences. Opening cleanup rules remain available but
default to disabled.

The 4.0 **Bridge configuration backup & restore** page covers both Bridge preference domains:

- main UI and visual settings;
- global/per-player translation settings;
- opening-cleanup rules and per-track corrections;
- debug settings and settings language.

Downgrading to 3.8.x is not lossless because the older codec does not understand every schema-v3
field. Keep the complete 4.0 backup, then be prepared to reset settings in the older app. A Provider
downgrade requires disabling/uninstalling the new package and restoring the old package/scope; the
different application IDs do not overwrite each other.

## 8. First validation after upgrading

For one translated and one non-translated track, check:

1. the media card and artwork remain correct;
2. lock-screen lyrics appear without opening the player's lyric page;
3. pause/resume, seek, and two consecutive track changes stay synchronized;
4. word highlighting appears only when the source contains word timing;
5. translation toggle state updates immediately when supported;
6. AOD enters and leaves without retaining the previous track.

If a problem remains, include Bridge version, Provider application ID/version, host version,
Bridge and Provider scopes, device/ROM/SystemUI versions, reproduction steps, and sanitized LSPosed
logs. Do not upload authentication tokens, cookies, complete private lyrics, or personal media paths.
