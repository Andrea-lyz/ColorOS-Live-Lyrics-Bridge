# ColorOS Live Lyrics Bridge

[![Build Debug APK](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/actions/workflows/build-debug.yml/badge.svg)](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/actions/workflows/build-debug.yml)
[![Latest release](https://img.shields.io/github/v/release/Andrea-lyz/ColorOS-Live-Lyrics-Bridge)](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/releases/latest)

Language: English | [简体中文](README.zh-CN.md)

<p align="center">
  <img src="GIF.gif" alt="ColorOS lock-screen lyrics demo" width="360">
</p>

Bring lyrics from more music apps to the native ColorOS / OPlus lock-screen lyric page.

This is not a floating overlay. It passes a player's full lyric timeline to the system UI, keeping the ColorOS look, transitions, and always-on display while adding word-by-word highlighting, translations, and appearance controls.

> Current release: **v3.8.0**. Update the Bridge and the Providers you use from the same release. Mixing versions can cause track-change and lyric-timing issues.

## What it does

- Shows full lyrics on the native ColorOS lock screen and AOD lyric page.
- Supports line-timed lyrics, word-by-word highlighting, and translations when the player provides the required data.
- Wraps or smoothly browses long lines instead of shrinking them into tiny text.
- Lets you adjust active, unrevealed, translation, and inactive lyric brightness independently, including top/bottom edge fading and an optional extra inactive-row fade.
- Also controls color, glow, blur, text size, weight, alignment, scaling, motion, lyric-row spacing, and wrapped-line spacing.
- Includes Default, Soft, Vivid, and Minimal presets with a live preview.
- Remembers translation choices per player and can hide leading title, credit, and copyright lines.
- Can keep the screen awake while lyrics are visible, either indefinitely or for a chosen duration.
- Preserves the media card's original previous, play/pause, next, and other controls.
- Handles rapid track changes, pause/resume, AOD transitions, repeated lines, and long CJK text more reliably.

## Before you install

This module is intended for devices that meet all of these requirements:

- Root access and an LSPosed / LSP manager with **libxposed API 102** support.
- A ColorOS / OPlus system image that already includes the native lock-screen lyric page.
- An understanding that a major system or player update may require a compatibility update.

The APK has a minimum Android API level of 26, but that does not mean every Android 8.0+ device is compatible. The deciding factor is the private OPlus SystemUI implementation on the device. Development and current testing mainly target the ColorOS 16 lyric path. OnePlus, OPPO, and realme devices may behave differently even when their system interfaces look similar.

If the ROM has no native lock-screen lyric page, this module will not create a separate floating lyric window.

## 4.0 architecture and player compatibility

Bridge 4.0 runs only in `system` and `com.android.systemui`. It no longer enters player processes or receives private Provider broadcasts. Each independent Provider writes lyrics into the player's own `MediaSession` / `MediaMetadata["lyricInfo"]`; Bridge consumes that native ColorOS data and adds styling, word rendering, translation controls, AOD support, and compatibility enhancements.

Providers and Bridge can be installed independently:

- Provider only: ColorOS SystemUI can consume the player's native `lyricInfo` directly.
- Provider plus Bridge: Bridge adds generic enhancements without submitting a second lyric payload.
- 4.0 Providers are independent Root / LSPosed modules.

| Player | 4.0 Provider module | Lyric capability |
| --- | --- | --- |
| Salt Player | `player-salt` | Word timing, translations, public translation CustomAction |
| ConePlayer (standard and Google Play) | `player-cone` | Full timeline, translations, public translation CustomAction |
| KuWo Music | `kuwo-music` | Appends word timing and translations to official `lyricInfo` |
| LX Music (ToSide / Walnut) | `player-lx` | Word timing, translations, Bluetooth identity and artwork compatibility |
| Poweramp | `player-poweramp` | Sidecar `.lrc` / embedded tags and translations |
| [Metrolist](https://github.com/metrolistgroup/metrolist) | `player-metrolist` | BetterLyrics / LrcLib / KuGou; no translations |
| KuGou Music / Concept | `player-kugou` | Appends word timing and type-1 translations to the official payload |
| QQ Music | `player-qq` | Appends word timing and translations; QQ Music HD is out of scope |
| NetEase official / Honor / modified 9.0.40 | `player-netease` | Official append or profile-selected constructed word timing and translations |
| Apple Music | `player-apple` | JNI TTML word timing and translations |
| Spotify | `player-spotify` | Line- or word-timed Color Lyrics; no translations |
| QiShui Music | `player-qishui` | Host TrackLyric / cache fallback with word timing and translations |

Private player interfaces can change after app updates. This table describes the current 4.0 implementation and device-validation matrix, not permanent compatibility with every future player release.

Halcyon remains compatible when it publishes standard `lyricInfo`; its old in-app v4 broadcast fallback has been removed. Flamingo's former v4-only integration is not supported by 4.0 until it publishes standard `lyricInfo` through its own MediaSession.

## Installation

1. Install the required 4.0 Provider APK, enable it in LSPosed, and select only its matching music app.
2. If you want Bridge enhancements, install `ColorOS-Live-Lyrics-Bridge-<version>.apk` and keep its scope limited to `system` and `com.android.systemui`.
3. Restart the player and SystemUI. Reboot after the first installation or any scope change.
4. Do not let an old Lyricon Provider and a 4.0 Provider hook the same player.

The Provider ZIP in a release is only an APK download bundle, not a Recovery-flashable package.
## Appearance and behavior

Open **ColorOS Live Lyrics Bridge → Settings** from the module page in LSPosed.

Choose a preset first, then fine-tune it if needed. Changes update only the preview until you tap **Apply and save**. The settings page also includes:

- a dedicated **Lyric brightness & fading** page for active/unrevealed text, active translation and translation progress, inactive lyric/translation brightness, a follow-main switch, native RecyclerView edge-fade enable/length, and extra inactive-row fading;
- per-player translation defaults and remembered translation-button state;
- guided cleanup of title, credit, and copyright lines at the start of lyrics;
- progress effects for line-timed lyrics and translations;
- vertical browsing for long lyrics and horizontal scrolling for long translations;
- 60 / 90 / 120 Hz lyric redraw limits;
- keep-screen-awake control with an optional custom duration.

The refresh-rate setting limits lyric drawing only. It does not force the display to remain at a high refresh rate.

The four presets explicitly own the new brightness and fading fields. Changing any preset-owned color, typography, motion, brightness, or fading value marks the result as **Custom**. Soft and Vivid retain the legacy 90% inactive-row fade that previously followed blur/scaling; Default and Minimal leave the extra row fade off.

The main settings page has a separate **Bridge configuration backup & restore** entry. It copies and restores both Bridge preference domains, covering the main UI, global/per-player translation settings, opening-cleanup rules and corrections, debug settings, and settings language. A full reset is available there after confirmation. Downgrading from a schema-v3 build is not lossless: older codecs may reject the saved configuration, so create a complete Bridge backup first and be prepared to reset the older app's settings.

See [Bridge 4.0 lyric visual controls](docs/4.0/LYRIC-VISUAL-CONTROLS.md) for the exact defaults, preset matrix, migration behavior, and downgrade boundary.

## Troubleshooting

### No lyrics appear at all

Check that the ROM has the native lyric page, that the Bridge keeps its recommended scope, and that the target player does not need a separate Provider. Force stop and reopen the player; reboot the device if the issue remains.

### Line lyrics work, but word highlighting or translations do not

The current player or track probably supplies line-timed lyrics only. Word timing and translations cannot be generated from nothing; they must exist in the lyric source. The current Spotify Provider intentionally supplies original lyrics only.

### The previous track remains visible, or “No lyrics” appears after switching tracks

Make sure the Bridge and all Providers came from the same release. Version mismatches are a common cause of inconsistent track timing. Then force stop the player and restart SystemUI; reboot after any scope change.

### A system update breaks the module

The ColorOS lyric page is a private vendor SystemUI feature and can change between updates. Open an [issue](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/issues) with the device model, OS version, SystemUI version, player version, and relevant LSPosed logs.

### Does the module modify music files or upload lyrics?

Bridge only reads native lyrics from the player's MediaSession and enhances their SystemUI presentation. It does not modify music files. Whether lyrics are fetched online depends on the player or its Provider.

## For player developers

If your player already has a full lyric timeline, the preferred integration is to publish the public `MediaMetadata["lyricInfo"]` payload. Users then need only the Bridge, with no player-specific Provider, and your app does not need a compile-time dependency on the module APK.

- [Player integration protocol](docs/PLAYER_INTEGRATION.md)
- [播放器接入协议（中文）](docs/PLAYER_INTEGRATION.zh-CN.md)
- [Bridge and LyricProvider responsibilities (Chinese)](docs/LYRIC_PROVIDER_BRIDGE.zh-CN.md)

## Building locally

JDK 21 is required. The app still emits Java 17 bytecode for Android compatibility.

```powershell
.\scripts\gradle-local.cmd testDebugUnitTest assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

## Support the project

If the project is useful to you, you can support future compatibility work through WeChat or Alipay.

<p align="center">
  <img src="PY_QR.png" alt="WeChat and Alipay support QR code" width="600" height="400">
</p>

## License and acknowledgements

Copyright 2026 Andrea-lyz. Licensed under the [Apache License 2.0](LICENSE).

The project uses [Accompanist Lyrics Core](https://github.com/6xingyv/accompanist-lyrics-core) to parse lyric timelines. Optional Providers build on the [tomakino/LyricProvider](https://github.com/tomakino/LyricProvider) ecosystem. Thanks to the authors and contributors of both projects.

Android, ColorOS, OPlus, LSPosed, and all music-app names are trademarks of their respective owners. This project is not affiliated with or endorsed by those vendors.
