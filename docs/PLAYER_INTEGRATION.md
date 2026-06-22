# Player-provided lyricInfo integration

Players that already own timed lyrics do not need an in-module `PlayerAdapter` or a dependency on the module APK. Publish an OPlus-compatible JSON string under the `lyricInfo` metadata key of the active media session. The module discovers the active provider in SystemUI.

## Optional OPlus media-history integration

Players that need a persistent ColorOS media card and media-button delivery after the app has fully stopped can opt in from their own `AndroidManifest.xml`:

```xml
<application>
    <meta-data
        android:name="io.github.andrealtb.lockscreenlyrics.OPLUS_MEDIA_HISTORY"
        android:value="true" />
</application>
```

The system-server hook preserves the original OPlus result, then additionally accepts:

- Every package represented by a built-in `PlayerAdapter`.
- External players that declare the metadata above.

External players do not need to enter this module's LSPosed scope or depend on its APK. The key is also exposed as `LyricInfoContract.MANIFEST_METADATA_OPLUS_MEDIA_HISTORY`.

This declaration only allows the package into the OPlus media-history stack. The player must still create a valid `MediaSession`, provide a media-button receiver or `PendingIntent`, handle `android.intent.action.MEDIA_BUTTON` after process death, start its own playback service, and restore its queue. The module does not guess external service classes or playback actions. Players already accepted by the original ColorOS whitelist do not need this opt-in.

```json
{
  "songName": "Song title",
  "artist": "Artist",
  "songId": "stable-player-song-id",
  "lyric": "[00:00.00]Line one\n[00:05.20]Line two",
  "rawLyric": "[00:00.000]Line[00:00.320] [00:00.440]one[00:05.200]"
}
```

- `lyric` is required and must contain at least one timed LRC tag.
- `songName`, `artist`, and a stable `songId` should be provided.
- `rawLyric` is optional. It enables the module's word-level highlighting and layout renderer.
- Before OPlus consumes `lyric`, the module normalizes same-timestamp bilingual groups to one primary line item. Keep complete translations and word timing in `rawLyric` or a timed translation field.
- Zero-width spacer-only lines are ignored. Use real timed primary lines rather than invisible placeholders to control list position.
- A player that publishes only `lyric` still gets native OPlus line-level lyrics, dynamic provider recognition, lyric-policy handling, and screen-timeout handling. Media-history integration is separate and requires original ColorOS support, a built-in adapter, or the manifest opt-in above.
- The player does not need to be added to the module's LSPosed scope.

## Data-source priority

For a player outside the built-in compatibility-adapter scope, a valid player-provided payload is consumed directly in SystemUI. For Salt Player or ConePlayer, where the module also runs inside the player process, the priority is:

1. A player-provided enhanced payload containing timed `rawLyric` or timed translation data.
2. A timed lyric captured by the module's compatibility adapter and verified for the current track.
3. A simple player/OPlus `lyricInfo` payload containing only line-level lyrics.

This means a future line-only native `lyricInfo` implementation remains a safe fallback, while the adapter can still provide word timing and translations. Once adapter data is available, the module builds a fresh payload instead of merging fields from the simple native payload.

For Media3, place the JSON in `MediaItem.mediaMetadata.extras`. Prefer publishing the first current item with its complete `lyricInfo` already attached. For framework media sessions, use `android.media.MediaMetadata.Builder.putString("lyricInfo", json)` and call `MediaSession.setMetadata`.

Update the whole payload on each track transition, remove it when lyrics are disabled or unavailable, and keep `lyric` and `rawLyric` on the same time offset. `LyricInfoContract.java` is the canonical constants and validation reference; players do not need to link against it.

## Optional translation action

To expose the lock-screen translation toggle, publish this standard action as the first `PlaybackState` custom action:

```kotlin
private const val ACTION_TOGGLE_TRANSLATION =
    "io.github.andrealtb.lockscreenlyrics.action.TOGGLE_TRANSLATION"

val action = PlaybackState.CustomAction.Builder(
    ACTION_TOGGLE_TRANSLATION,
    "Lyric translation",
    R.drawable.any_valid_media_icon // Android requires a non-zero resource ID
).build()
```

The Android API requires a valid icon resource, but the player does not need to create a dedicated translation icon. The module first looks for `ic_translation` in the player package and otherwise uses its bundled Salt-style translation icon. Publish the action only while the current `lyricInfo` contains usable translations, and remove it on track changes or when translations are unavailable. The module enables the required OPlus custom-action slot, handles taps, and persists the toggle state. If the module is absent, Android may deliver the action to the player, which should safely ignore the no-op callback.

## Recommended publication timing

Publish on state changes instead of using a repeating timer:

1. Publish once when the media session is created or the feature is enabled, if current lyrics are ready. The initial metadata, notification, and current `MediaItem` should all describe the same track and carry the same complete `lyricInfo`.
2. Remove the previous payload as soon as a track transition begins.
3. Publish the complete payload once when the new track's asynchronous lyric load finishes. If playback has already started, update the latest current-item metadata and the session/notification metadata as one logical operation.
4. If the session or current `MediaItem` is rebuilt, read its metadata first and republish only when `lyricInfo` is missing or differs from the target JSON.
5. Do not publish a lyric-free track metadata object and then patch only `extras` a few milliseconds later. OPlus may debounce that second update (`within debounce period, ignore`), leaving the first track at `hasLyric=false` until another track change. If lyrics are not ready at track creation, wait for the initial media update burst to settle before the one complete lyric update.
6. For Media3/OPlus builds with propagation races, an optional check about `800 ms` after the first complete publication may republish at most once, and only when the value is still missing. Do not continue periodic retries.
7. Cancel pending retries and remove `lyricInfo` when the feature is disabled, lyrics are unavailable, or the queue becomes empty.

In normal operation this means **one publication per track**, or at most **two** on a system that demonstrably loses the first update. Playback position changes, pause/resume, and ordinary notification refreshes must not rewrite `lyricInfo`.

### First-track checklist

- Build `lyricInfo` from the same title, artist, and stable media ID used by the current media item.
- Attach it before the first metadata/notification publication whenever lyrics are already available.
- When lyrics arrive asynchronously, clone the latest metadata instead of an earlier snapshot, then replace the current item only once with the complete payload.
- Do not rely on an immediate extras-only update to cross the OPlus media pipeline.
- During integration testing, `hasLyric=false` after the player reports that it published `lyricInfo` usually means the update was debounced before SystemUI consumed it; it is not a lyric-format rejection by this module.

See the [Chinese integration guide](PLAYER_INTEGRATION.zh-CN.md) for complete Media3 and framework examples.
