# ColorOS Live Lyrics Bridge

[![Build Debug APK](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/actions/workflows/build-debug.yml/badge.svg)](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/actions/workflows/build-debug.yml)

Languages: [English](README.md) | [简体中文](README.zh-CN.md)

<p align="center">
  <img src="GIF.gif" alt="ColorOS Live Lyrics Bridge demo" width="360">
</p>

An LSPosed/libxposed API 102 module that bridges timed lyrics from supported Android music players into the ColorOS/OPlus lock-screen lyric pipeline.

The module currently ships DexKit-based compatibility adapters for Salt Player and ConePlayer plus SystemUI renderer hooks. Other players should integrate by publishing the `lyricInfo` contract themselves.

## What It Hooks

Player process:

```text
android.media.session.MediaSession#setMetadata(android.media.MediaMetadata)
```

For built-in compatibility adapters, a valid lyric captured for the current track takes priority over a simple player-provided `MediaMetadata["lyricInfo"]` payload. The simple payload remains a fallback until capture succeeds. A player payload containing `rawLyric` or timed translation data is treated as an explicit enhanced integration and is kept. Self-integrating players should publish the same payload themselves.

```json
{
  "songName": "...",
  "artist": "...",
  "songId": "lockscreen-lyrics-...",
  "lyric": "[00:00.00]...",
  "rawLyric": "[00:00.000]word[00:00.120]..."
}
```

SystemUI process:

- Reads `lyricInfo` from OPlus media data.
- Normalizes the official line-level LRC so each timestamp produces one primary OPlus list item, while translations and word timing remain in the complete model.
- Builds a word-level timeline from `rawLyric` when available.
- Merges timed translation lines from the original `lyricInfo` into the word-level model.
- Resolves private OPlus media and lyric targets through DexKit, with legacy class-name fallback.
- Draws inside the official lock-screen lyric `TextView.onDraw(Canvas)` path.
- Maps official items by timestamp, normalized text, and occurrence order so repeated lyrics and pre-roll lines remain stable.
- Keeps `80dp` lyric slots with `6dp` spacing, tightens short-line density, uses a moving two-line window for long main lyrics, and places the active line about `48dp` below the viewport center.
- Recovers lyric rendering after transient visibility changes without changing item geometry during playback.
- Dynamically recognizes player-provided `lyricInfo` without a hard-coded package name.
- Keeps the screen from timing out while the recognized provider's lock-screen lyric UI is actively visible.

## Screen Timeout Keep-Awake

The keep-awake logic runs only in the SystemUI process. It is intentionally tied to the official OPlus lock-screen lyric UI, not to playback alone.

SystemUI hooks used by this feature:

- `android.util.Log.i(String, String)`
- `android.util.Log.println(int, String, String)`
- OPlus Seedling media playback position/state hooks
- Visible official lyric `TextView` tracking
- `ACTION_SCREEN_OFF`, `ACTION_SCREEN_ON`, and `ACTION_USER_PRESENT` broadcasts inside SystemUI

The module watches OPlus `PluginSeedling--Template` logs for supported player packages and checks fields such as:

```text
lyricUiMode=true
lockImmersiveMode: true
containerView.isShown=true
hasLyric=false
```

It holds a 15-second `SCREEN_BRIGHT_WAKE_LOCK` lease only when all of these are true:

- The current package is either a built-in compatibility adapter or the active provider of a valid `lyricInfo` payload.
- OPlus lyric UI mode is active.
- Playback is playing.
- There is lyric evidence from a recently visible official lyric view, with only a short grace window from fresh lyric metadata.
- The screen is interactive and the keyguard is still showing.

While active, the module renews the wake-lock lease and pulses `PowerManager.userActivity(...)` about every 8 seconds so the system treats the lock-screen lyric view as user-visible activity. The wake lock is released on screen off, true keyguard dismissal, playback stop, missing visible lyric evidence, unsupported package, or any condition change. `ACTION_USER_PRESENT` is followed by a short keyguard recheck so face unlock can keep the lock-screen lyric UI awake when the keyguard remains visible.

Self-integrating players are recognized from the current media session and do not need to be added to `scope.list` or `PLAYER_ADAPTERS`. If OPlus changes the `PluginSeedling--Template` log format for a device/ROM, the keep-awake detection may need a small SystemUI-side update.

## Player-provided lyricInfo

This is the preferred integration for players that already own timed lyrics. Publish a valid `lyricInfo` JSON string in the active media session; the module dynamically binds that session in SystemUI. A timed `lyric` field enables native line-level lyrics, while optional `rawLyric` enables this module's word-level renderer.

Known self-integrating players:

- [Halcyon](https://github.com/Kifranei/Halcyon) — `lyricInfo` integration completed.

See the [player integration contract](docs/PLAYER_INTEGRATION.md). No module APK dependency, package-name registration, or LSPosed player scope is required.

## Compatibility Adapters

Compatibility adapters hook legacy players whose native metadata does not expose complete lyric timing through the `lyricInfo` contract.

Built-in compatibility adapters are:

```java
new SaltPlayerAdapter()
new ConePlayerAdapter("ink.trantor.coneplayer")
new ConePlayerAdapter("ink.trantor.coneplayer.gp")
```

The Salt adapter has been verified against Salt Player 12.0.0 official and alpha07 builds. The ConePlayer adapter has been verified across versions 1.1.3 through 1.1.5 for the formal package, with Google Play package scope included.

Prefer the player-provided `lyricInfo` contract for new players. Add a `PlayerAdapter` only for compatibility cases where the player cannot publish `lyricInfo` itself.

To add another compatibility adapter:

1. Add the package name to `src/main/resources/META-INF/xposed/scope.list`.
2. Implement a new `PlayerAdapter` next to `SaltPlayerAdapter`.
3. Capture that player's real timed lyric source and call `module.cacheTimedLyric(source, rawLyric)`.
4. Add the adapter to `PLAYER_ADAPTERS`.
5. Keep `com.android.systemui` in `scope.list`; it is required for lock-screen rendering and screen-timeout keep-awake.

If a player outside the built-in adapter scope already writes a valid OPlus `lyricInfo` metadata field by itself, a source hook is normally unnecessary. For a built-in adapter package, a line-only payload is a fallback; captured `rawLyric` replaces it once the adapter has data for the current track.

## Why API 102

The local `../LSP_api` folder is libxposed API `102.0.0`. This project follows its current module layout:

- Entry class extends `io.github.libxposed.api.XposedModule`
- Entry list: `src/main/resources/META-INF/xposed/java_init.list`
- Module config: `src/main/resources/META-INF/xposed/module.prop`
- Static scope: `src/main/resources/META-INF/xposed/scope.list`
- Hook API: `hook(method).setId(...).setExceptionMode(...).intercept(...)`

`libxposed-api-stubs` is compile-only and is not packaged into the APK. It exists so the project can compile without downloading `io.github.libxposed:api:102.0.0`; LSPosed provides the real API classes at runtime.

## Build

```powershell
.\scripts\gradle-local.cmd testDebugUnitTest assembleDebug
```

APK output:

```text
.gradle-local-build\app\outputs\apk\debug\app-debug.apk
```

JDK 21 is required to compile the Lyrics Core dependency. The helper discovers it from `SALT_LYRIC_JAVA_HOME`, `JAVA_HOME`, or common local JDK locations, and maps the repository to a temporary ASCII drive so Gradle works reliably when the checkout path contains non-ASCII characters. The app itself still targets Java 17 bytecode for Android compatibility.

## GitHub Actions

- `Build Debug APK`: runs on pushes to `main` and pull requests when project source or build files change. The generated debug APK is uploaded as a workflow artifact.
- `Release APK`: manually triggered after pushing a tag such as `v1.8.0`. The workflow checks out that tag, reads `docs/releases/<tag>.md`, builds a release-signed APK, sets the APK `versionName` from the tag, publishes the GitHub Release, and mirrors the release to the LSPosed module repository.

The manual release workflow expects these repository secrets:

- `SIGNING_KEY`: base64-encoded keystore file content.
- `KEY_STORE_PASSWORD`: keystore password.
- `KEY_ALIAS`: signing key alias.
- `KEY_PASSWORD`: signing key password.
- `LSP_REPO_TOKEN`: PAT with release write access to `Xposed-Modules-Repo/io.github.andrealtb.lockscreenlyrics`.

The release APK is published as `ColorOS-Live-Lyrics-Bridge-<tag>.apk`.

Install and test with a built-in adapter:

```powershell
adb install -r .gradle-local-build\app\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.salt.music
# Or: adb shell am force-stop ink.trantor.coneplayer
```

Enable the module in LSPosed for the target player package and System UI, then reboot or restart System UI. Restart the player, play a song, then lock the screen.

Useful logs:

```powershell
adb logcat -v time -s LockscreenLyrics
adb logcat -v time | Select-String -Pattern "LockscreenLyrics|OplusMediaDataManagerEx|loadLyricInBg|Failed to parse lyric data|LyricsRecyclerView|hasLyric"
```

Expected module log:

```text
LockscreenLyrics: Hooked MediaSession#setMetadata
LockscreenLyrics: Hooked Salt Player lyric result constructors via DexKit: result=..., source=..., scroll=..., count=2
LockscreenLyrics: Hooked ConePlayer lyric parser via DexKit: ...
LockscreenLyrics: Hooked SystemUI official lyric TextView draw hooks
LockscreenLyrics: Registered SystemUI screen timeout receiver
LockscreenLyrics: Cached real timed lyric from LRC_FILE, rawChars=..., oplusChars=...
LockscreenLyrics: Injected real LRC_FILE lyricInfo for title=...
LockscreenLyrics: Cached SystemUI word lyric model, lines=...
LockscreenLyrics: Lockscreen lyric UI keep-awake ON
LockscreenLyrics: Acquired bright screen timeout wake lock lease=15000ms
LockscreenLyrics: Pulsed screen timeout user activity without changing lights
LockscreenLyrics: Hooked LyricsRecyclerView#setCurrentLyric, methods=...
LockscreenLyrics: LyricsRecyclerView current index=...
LockscreenLyrics: Seedling playback state=3, playing=true, storedPosition=..., computedPosition=..., speed=...
LockscreenLyrics: Custom-drew official lyric TextView at position=..., playing=true, focused=true, line=...
LockscreenLyrics: Refreshed active lyric renderer at position=..., line=...
```

If you only see `Skip lyricInfo injection because no fresh real lyric is cached`, the adapter has not captured a timed LRC result in the current process yet, or the current song only has untimed lyrics.

## License and acknowledgements

Copyright 2026 Andrea-lyz. This project is released under the [Apache License 2.0](LICENSE).

This project uses [Accompanist Lyrics Core](https://github.com/6xingyv/accompanist-lyrics-core) `0.4.5` (`com.mocharealm.accompanist:lyrics-core-jvm`), maintained by [6xingyv](https://github.com/6xingyv), for timed-lyric parsing. Accompanist Lyrics Core is also distributed under the [Apache License 2.0](https://github.com/6xingyv/accompanist-lyrics-core/blob/main/LICENSE).

Android, ColorOS, OPlus, LSPosed, Salt Player, ConePlayer, and other product names are trademarks of their respective owners. This project is not affiliated with or endorsed by those owners.
