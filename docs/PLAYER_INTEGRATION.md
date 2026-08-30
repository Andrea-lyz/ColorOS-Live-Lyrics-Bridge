# Player-owned `lyricInfo` integration

[简体中文](PLAYER_INTEGRATION.zh-CN.md)

This document is for player developers who can publish lyrics from their own playback process.
The preferred 4.0 integration is direct:

```text
player lyric model
        ↓
player-owned MediaSession / MediaMetadata["lyricInfo"]
        ↓
ColorOS SystemUI native lyric page
        ↓ optional
Bridge rendering, AOD, translation controls, and compatibility enhancements
```

The player does not compile against the Bridge APK, send a Bridge broadcast, or enter the Bridge
LSPosed scope. `lyricInfo` is a JSON string stored in the player's existing platform
`MediaMetadata`. ColorOS can consume it without Bridge; Bridge is an optional SystemUI-side
enhancement.

## 1. When to use this path

Use direct integration when the player already owns a stable lyric model or can publish a complete
timeline itself. A separate Provider is useful only when the player cannot be changed and private
runtime APIs must be adapted externally.

Before implementation, identify:

- the one MediaSession that owns the notification/media-card playback state;
- a stable track ID, or at least title + artist + duration;
- the authoritative lyric-load completion event;
- whether the source is line-timed, word-timed, translated, or pronunciation-only;
- the host's real artwork and playback-position update paths.

Do not create a second MediaSession only for lyrics. ColorOS may select the wrong session or show a
paused duplicate card.

## 2. Metadata key and minimum payload

Write a JSON string to:

```text
MediaMetadata["lyricInfo"]
```

For the broadest native ColorOS compatibility, publish at least:

| Field | Requirement | Meaning |
|---|---|---|
| `songName` | recommended | Current display title. |
| `artist` | recommended | Current display artist. |
| `songId` | recommended | Stable host track ID when available. |
| `lyricType` | recommended | Use `0` for the standard timed-lyric payload. |
| `lyric` | required for native display | Line-timed LRC used by the stock lyric list. |
| `noLyric` | recommended | `false` when a usable timeline is present. |

Bridge accepts a payload only when `lyric` or `rawLyric` contains a valid timed tag. A raw-only
payload can enter the Bridge parser, but players should still publish line-timed `lyric`, because
the stock SystemUI list is the primary consumer.

Example:

```json
{
  "songName": "Example Song",
  "artist": "Example Artist",
  "songId": "track-42",
  "lyricType": 0,
  "lyric": "[00:10.000]First line\n[00:14.500]Second line\n",
  "rawLyric": "[00:10.000]<00:10.000>First <00:10.700>line<00:12.800>\n[00:14.500]<00:14.500>Second <00:15.200>line<00:17.000>\n",
  "translationLyric": "[00:10.000]第一行\n[00:14.500]第二行\n",
  "provider": "com.example.player",
  "source": "com.example.player-v5",
  "trackKey": "track-42|example song|example artist|180",
  "sessionGeneration": 12,
  "noLyric": false
}
```

Unknown JSON fields are allowed. Bridge never requires a Provider application ID or a private
envelope marker.

## 3. Optional extension fields

| Field | Format | Use |
|---|---|---|
| `rawLyric` | enhanced LRC | Word-level timing for karaoke rendering. |
| `translationLyric` | line-timed LRC | Canonical translation lane. |
| `provider` | string | Diagnostic owner; normally the host package. |
| `source` | string | Diagnostic source/profile. |
| `trackKey` | string | Stable identity key used to reject stale same-session payloads. |
| `sessionGeneration` | positive integer | Monotonic generation incremented on a real track change. |
| `album` | string | Optional identity/display context. |

For compatibility with existing official payloads, Bridge also recognizes `translatedLyric`,
`translateLyric`, `transLyric`, `lyricTranslation`, `translationLrc`, `transLrc`, and
`translation`. New integrations should write only `translationLyric` unless the player's official
writer already owns another alias.

## 4. Timeline formats

### 4.1 Line-timed lane

Use absolute playback time in milliseconds:

```text
[00:10.000]First line
[00:14.500]Second line
```

Tags may use `[]` or `<>`; `mm:ss.mmm` is preferred. Keep line starts non-decreasing and remove
empty/ad/promotional rows before aligning translation.

### 4.2 Word-timed lane

`rawLyric` uses one line tag followed by absolute word tags and an optional terminal tag:

```text
[00:10.000]<00:10.000>First <00:10.700>line<00:12.800>
```

Rules:

1. Word starts are absolute media positions, not offsets from the line start.
2. Word times never move backward within a line.
3. Preserve meaningful spaces in word text; do not invent spaces between CJK tokens.
4. A terminal tag should mark the visual end of the last word when the source provides it.
5. If the source is line-timed only, omit `rawLyric`; do not fabricate a karaoke sweep.

### 4.3 Translation lane

Translation lines use the main line's absolute start time:

```text
[00:10.000]第一行
[00:14.500]第二行
```

Align each translation to one primary line and consume it once. Pronunciation, romaji,
transliteration, and phonetic HTML are not translations and must not enter this lane. When no real
translation exists, omit the field instead of duplicating the primary lyric.

## 5. Track identity and generation

The payload must belong to the metadata currently visible on the same MediaSession.

Recommended identity order:

1. stable media ID;
2. title + artist + duration;
3. a player-specific immutable key.

Maintain a monotonically increasing generation:

- increment only when the real track changes;
- merge late ID/title/artist fields for the same track without incrementing;
- capture the generation when lyric loading starts;
- discard completion callbacks whose track or generation is no longer current;
- clear only payloads known to be owned by your integration.

Title-only Bluetooth/car-lyric projection is not a track change. Restore or preserve the stable
song identity before SystemUI consumes projected metadata.

## 6. Publication lifecycle

A safe sequence is:

```text
authoritative track observed
        ↓ generation++
lyric load starts with track + generation token
        ↓
result returns and still matches live track/generation
        ↓
copy current host metadata, preserving identity/artwork
        ↓
putString("lyricInfo", json)
        ↓
setMetadata on the same player-owned MediaSession
```

Important boundaries:

- Do not publish queue preload lyrics as the current song.
- Do not attach a late result to whatever metadata happens to be live.
- Do not use an extras-only update if the target ColorOS ignores it; publish a real metadata object.
- Do not continuously rewrite metadata for lyric progress. SystemUI reads progress from
  `PlaybackState`.
- Replay the same payload only when the host replaces metadata and drops your field.

## 7. Preserve host metadata and artwork

Lyrics are an overlay on the player's metadata, not a replacement for it. Preserve:

- media ID, title, artist, album, duration, track/disc numbers, ratings, and unknown host keys;
- artwork bitmap and artwork URI fields;
- the current MediaSession and PlaybackState;
- player-defined custom actions.

On affected ColorOS builds, `MediaMetadata.Builder(existing)` can collapse bitmap state. If this
occurs, create an empty typed builder and copy keys by type before adding `lyricInfo`. Never fetch,
invent, or restore artwork from a stale track merely to make lyric publication succeed.

Before committing very large lyrics, measure the complete candidate metadata Parcel. The reference
Provider implementation rejects lyric publication above 512 KiB while leaving original host
metadata untouched.

## 8. Playback clock

Publish the player's real `PlaybackState`:

- correct PLAYING/PAUSED/BUFFERING state;
- current position;
- playback speed;
- monotonic `lastPositionUpdateTime`.

Do not manufacture PLAYING or reset position to zero to wake SystemUI. Such writes can break the
media-card play icon, seeking, and lock-screen lyric visibility.

## 9. Optional translation action

Players that want Bridge's public media-card translation toggle may expose a
`PlaybackState.CustomAction` with this action ID:

```text
io.github.andrealtb.lockscreenlyrics.action.TOGGLE_TRANSLATION
```

Declaring the string, placing it in extras, or defining a constant does not create a button. The
player must build a real `PlaybackState.CustomAction`, add it to the current `PlaybackState`, and
publish a new PlaybackState object through the existing MediaSession.

`CustomAction.Builder` requires a real, non-zero icon resource ID:

```kotlin
val placeholderIcon = applicationContext.applicationInfo.icon.takeIf { it != 0 }
    ?: android.R.drawable.ic_menu_manage

val translationAction = PlaybackState.CustomAction.Builder(
    "io.github.andrealtb.lockscreenlyrics.action.TOGGLE_TRANSLATION",
    "Translate",
    placeholderIcon
).build()

playbackStateBuilder.addCustomAction(translationAction)
mediaSession.setPlaybackState(playbackStateBuilder.build())
```

`PlaybackState` is immutable; adding the Action only on first play is insufficient. Every later
`setPlaybackState()` caused by pause/resume, seek, favorite changes, control-row refresh, or other
state synchronization must copy the host state and re-add the public Action. Otherwise the new
PlaybackState silently replaces the object that contained the translation button. Remove it only
when the current track truly has no translation.

After every publication, query the same MediaSession that owns the lock-screen card instead of
checking only the local Builder:

```kotlin
val actionPublished = mediaSession.controller.playbackState
    ?.customActions
    ?.any {
        it.action ==
            "io.github.andrealtb.lockscreenlyrics.action.TOGGLE_TRANSLATION"
    } == true

check(actionPublished) {
    "Translation CustomAction is missing from the active MediaSession"
}
```

Development builds must report `actionPublished=true`. Bridge Debug should likewise report
`hasPublicAction=true` and include the complete action ID in `actions=[...]`. A false result means
construction failed, the Action was published to the wrong MediaSession, or a later PlaybackState
rebuild removed it; Bridge cannot recover an Action that is absent from the active session.

An icon ID of `0` cannot produce a valid Action. A non-zero ID that does not resolve can still fail
while SystemUI materializes the action, before Bridge can intervene. No dedicated translation
artwork is required: use the app icon, an existing CustomAction icon, or another valid system
resource as a placeholder. Only after SystemUI creates the Action does Bridge replace that
placeholder with its bundled `ic_translation` and apply sizing, white tint, and enabled/disabled
alpha.

The public action is discovered dynamically by action ID and does not require the player to enter
Bridge's package compatibility list. Remove it for tracks without translations. For translated
tracks, keep publishing the complete `translationLyric`; disabling the button affects Bridge
presentation and must not make the player remove translation data.

SystemUI/Bridge binds the visible action. Preserve all host actions and all PlaybackState fields
when adding it. The player callback must not reinterpret it as a player command. If your player's
official action row has a different ownership model, omit this action and keep native controls.

## 10. Optional OPlus media-history opt-in

Some ColorOS versions filter player packages before their sessions reach the OPlus media pipeline.
An external player can opt in through its manifest:

```xml
<application>
    <meta-data
        android:name="io.github.andrealtb.lockscreenlyrics.OPLUS_MEDIA_HISTORY"
        android:value="true" />
</application>
```

This opt-in affects OPlus media-history/blacklist policy only. It does not grant lyric capability,
create a Provider, or add the player to Bridge scope.

4.0 also predeclares package-only lyric-entrance, media-history, and AOD SystemUI compatibility for
the known native integrations Halcyon (`com.ella.music`), Flamingo (`yos.music.player`), QZ Music
(`love.qz.music`), and PrismMusic (`com.lg.sllocalmusic`). This list neither validates nor admits a
lyric source: all four players must still publish `lyricInfo` through this protocol, and old v4
broadcasts are ignored. Other players do not need an admission PR when standard payloads already
work; request package compatibility only with device evidence that vendor SystemUI still blocks the
lyric entrance or AOD.

## 11. Validation checklist

Validate on the exact APK that will be shipped:

- [ ] Provider/Bridge absent: stock SystemUI consumes `lyricInfo`.
- [ ] Bridge installed: no duplicate lyric publication.
- [ ] first play, pause/resume, seek, skip, rapid three-track skip, and same-track replay;
- [ ] line-only, word-timed, translated, and no-lyric tracks;
- [ ] after initial insertion and every later `setPlaybackState()`, the current controller's
      customActions still contain the complete `TOGGLE_TRANSLATION` action ID;
- [ ] lock screen, unlock/re-enter, screen off, and AOD;
- [ ] artwork URI first frame and later bitmap frame;
- [ ] metadata churn does not bump generation;
- [ ] an old asynchronous result cannot overwrite the new track;
- [ ] payload remains below the device's Binder/Parcel limit;
- [ ] logs contain no complete lyrics, tokens, cookies, or private local paths.

When reporting a compatibility problem, include player version, device/ROM/SystemUI versions,
MediaSession owner process, a sanitized `lyricInfo` field summary, PlaybackState summary, and the
smallest reproducible track-change sequence.

## 12. Reference implementation

For an external module that adapts an unmodified player, see the bilingual Provider guide:

- [Provider adaptation guide (English)](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers/blob/4.0/docs/4.0/PROVIDER-ADAPTATION-GUIDE.md)
- [Provider 适配技术指南（中文）](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Providers/blob/4.0/docs/4.0/PROVIDER-ADAPTATION-GUIDE.zh-CN.md)

The public JSON contract remains player-owned and has no compile-time dependency on either
repository.
