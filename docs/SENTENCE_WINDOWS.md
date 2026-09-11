# Event-driven sentence windows

Readify AI 3.1.0 (`com.readin.app`) can publish a bounded view of the current TTS
utterance and its already-prefetched neighbors through its own `MediaSession`.
The Provider owns text extraction, stable identity, and lifecycle. Bridge owns
SystemUI rendering. The Bridge Xposed scope stays `system` / `com.android.systemui`.

## Payload extension (proposed)

Standard `lyricInfo` identity fields are unchanged. Opt in with:

```json
{
  "songName": "Example book",
  "artist": "",
  "trackKey": "book-id|example book||0",
  "provider": "com.readin.app",
  "displayMode": "sentence-window-v1",
  "currentLine": 1,
  "lyric": "[00:00.000]Previous sentence\n[00:01.000]Current sentence\n[00:02.000]Following sentence\n"
}
```

`currentLine` is zero-based. Accept one to five nonempty rows with sequential
0/1/2/3/4-second slot tags. These tags encode row order, **not audio durations**.
The index remains fixed until the next Provider event; no elapsed-time advance.
Invalid or absent extensions retain normal timed-lyric behavior.

For valid windows, use `lyric` as the render-model source even without `rawLyric`,
skip opening cleanup that could change indices, and align both native lyric
updates and custom focus to the snapshot position. Native boolean/animation
arguments and the actual player PlaybackState remain unchanged. Discover a
renamed `void(boolean,long)` lyric method only when that shape is unique;
ambiguous fallback candidates are not hooked. The legacy name remains supported.

Window rows expand to their wrapped text height instead of the music two-line
sliding window. The text-view recognition limit covers the Provider's bounded
1600-code-point text. Extremely tall paragraphs still exceed a physical screen.
Ordinary music retains its current two-line and playback-clock policies.

## Validation

The tester reported correct current-line hold, sentence changes, and long-text
rendering with Readify AI 3.1.0 online Chunk AI speech on OnePlus 13 (PJZ110),
Android 16, LSPosed 2.2.0 / API 102. Device tests used local test packages based on
Bridge v4.2.0; the final upstream branch has not been installed on that device.
No screenshot/recording of the final working state is attached yet.

Redacted diagnostics (no book titles or body text):

```text
published/readback ok; mode=sentence-window rows=5 current=2
SENTENCE_WINDOW_ACCEPTED: Snapshot accepted; current=2
```

Regression coverage includes protocol parsing, model construction without
rawLyric, every active row, rejecting ambiguous reflected methods, holding the
index while native time advances, and preserving all wrapped Chinese text.
This proposal requires the companion Readify Provider PR and maintainer review
of the new wire extension before release.
