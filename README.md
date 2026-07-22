# ColorOS Live Lyrics Bridge

[![Build Debug APK](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/actions/workflows/build-debug.yml/badge.svg)](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/actions/workflows/build-debug.yml)
[![Latest release](https://img.shields.io/github/v/release/Andrea-lyz/ColorOS-Live-Lyrics-Bridge)](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/releases/latest)

Language: English | [简体中文](README.zh-CN.md)

<p align="center">
  <img src="GIF.gif" alt="ColorOS lock-screen lyrics demo" width="360">
</p>

Bring lyrics from more music apps to the native ColorOS / OPlus lock-screen lyric page.

This is not a floating overlay. It passes a player's full lyric timeline to the system UI, keeping the ColorOS look, transitions, and always-on display while adding word-by-word highlighting, translations, and appearance controls.

> Current release: **v3.4.0**. Update the Bridge and every installed LyricProvider together. Mixing versions can cause track-change and lyric-timing issues.

## What it does

- Shows full lyrics on the native ColorOS lock screen and AOD lyric page.
- Supports line-timed lyrics, word-by-word highlighting, and translations when the player provides the required data.
- Wraps or smoothly browses long lines instead of shrinking them into tiny text.
- Lets you adjust color, opacity, glow, blur, text size, weight, alignment, scaling, and motion, with separate spacing controls for lyric rows and wrapped lines inside a row.
- Includes Default, Soft, Bold, and Minimal presets with a live preview.
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

## Player compatibility

There are two ways a player can work with the module:

- **Built into the Bridge:** install only the main module.
- **LyricProvider required:** install the main module plus the matching Provider APK. The Provider reads lyrics from the music app; the Bridge sends them to the lock screen.

| Player | Extra Provider | Current support |
| --- | --- | --- |
| Salt Player | No | Built into the Bridge; word-timed and translated lyrics when supplied by the player |
| ConePlayer (standard and Google Play packages) | No | Built into the Bridge; full lyric timelines and background recovery |
| QQ Music | `LyricProvider-QQMusic` | Word-timed and translated lyrics |
| NetEase Cloud Music / Honor edition | `LyricProvider-163Music` | Word-timed and translated lyrics |
| Apple Music | `LyricProvider-AppleMusic` | Word-timed and translated lyrics; background-vocal and duet lanes are excluded |
| LX Music (ToSide / Walnut variants) | `LyricProvider-LXMusic` | Full lyric timeline and translations when supplied by the player |
| Poweramp | `LyricProvider-Poweramp` | Embedded local lyrics and lyrics available through provider matching |
| Spotify | `LyricProvider-Spotify` | Standard original lyrics only; translations are not currently supported |
| QiShui Music | `LyricProvider-QiShui` | Word-timed and translated lyrics; proper root hiding and the special setup below are required |
| KuGou Music / Concept | `LyricProvider-KuGou` | Word-timed and translated lyrics |

Music apps can change their private lyric interfaces at any time. This table describes the adapters present in the current code; it is not a promise that every future player release will remain compatible.

Players that publish the public `lyricInfo` protocol usually need neither a dedicated Provider nor Bridge scope in the player process. [Halcyon](https://github.com/Kifranei/Halcyon) is one known native integration.

## Installation

1. Open the [latest release](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge/releases/latest) and install `ColorOS-Live-Lyrics-Bridge-<version>.apk`.
2. Enable **ColorOS Live Lyrics Bridge** in LSPosed and keep its recommended default scope.
3. For QQ Music, NetEase, Apple Music, LX Music, Poweramp, Spotify, QiShui, or KuGou, also install the matching `LyricProvider-*.apk` from the same release.
4. Enable each Provider separately in LSPosed and select only its matching music app as the scope.
5. Reboot the device so the hooks load in SystemUI, system services, and the player.

`LyricProvider-<version>.zip` is simply a bundle containing every Provider APK. It is not a Recovery-flashable package. You only need to install Providers for the players you use.

### Extra step for QiShui Music

Enable **Restore inline hooks** for QiShui Music in your LSP manager and follow its instructions for handling `libart.so` in listed apps. Also ensure root hiding is properly configured. Without these precautions, QiShui may report an unsafe version and the Provider may not work reliably.

## Appearance and behavior

Open **ColorOS Live Lyrics Bridge → Settings** from the module page in LSPosed.

Choose a preset first, then fine-tune it if needed. Changes update only the preview until you tap **Apply and save**. The settings page also includes:

- per-player translation defaults and remembered translation-button state;
- guided cleanup of title, credit, and copyright lines at the start of lyrics;
- progress effects for line-timed lyrics and translations;
- vertical browsing for long lyrics and horizontal scrolling for long translations;
- 60 / 90 / 120 Hz lyric redraw limits;
- keep-screen-awake control with an optional custom duration.

The refresh-rate setting limits lyric drawing only. It does not force the display to remain at a high refresh rate.

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

The Bridge only passes lyrics locally between the player, Android media session, and SystemUI. It does not modify music files. Whether lyrics are fetched online depends on the player or the matching LyricProvider.

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
