# Player-provided lyricInfo integration

Players that already own timed lyrics do not need an in-module `PlayerAdapter` or a dependency on the module APK. Publish an OPlus-compatible JSON string under the `lyricInfo` metadata key of the active media session. The module discovers the active provider in SystemUI.

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
- A player that publishes only `lyric` still gets native OPlus line-level lyrics, dynamic provider recognition, whitelist bypass, and screen-timeout handling.
- The player does not need to be added to the module's LSPosed scope.

For Media3, place the JSON in `MediaItem.mediaMetadata.extras`, update the current item, and republish it after asynchronous lyrics load. For framework media sessions, use `android.media.MediaMetadata.Builder.putString("lyricInfo", json)` and call `MediaSession.setMetadata`.

Update the whole payload on each track transition, remove it when lyrics are disabled or unavailable, and keep `lyric` and `rawLyric` on the same time offset. `LyricInfoContract.java` is the canonical constants and validation reference; players do not need to link against it.

## Recommended publication timing

Publish on state changes instead of using a repeating timer:

1. Publish once when the media session is created or the feature is enabled, if current lyrics are ready.
2. Remove the previous payload as soon as a track transition begins.
3. Publish the complete payload once when the new track's asynchronous lyric load finishes.
4. If the session or current `MediaItem` is rebuilt, read its metadata first and republish only when `lyricInfo` is missing or differs from the target JSON.
5. For Media3/OPlus builds with propagation races, an optional check about `800 ms` after the first publication may republish at most once, and only when the value is still missing. Do not continue periodic retries.
6. Cancel pending retries and remove `lyricInfo` when the feature is disabled, lyrics are unavailable, or the queue becomes empty.

In normal operation this means **one publication per track**, or at most **two** on a system that demonstrably loses the first update. Playback position changes, pause/resume, and ordinary notification refreshes must not rewrite `lyricInfo`.

See the [Chinese integration guide](PLAYER_INTEGRATION.zh-CN.md) for complete Media3 and framework examples.
