# TODO

## Dynamic lyric maximum render rate

- Add a user setting for the maximum dynamic lyric redraw rate: Follow display,
  60 Hz, 90 Hz, or 120 Hz.
- Discover the display modes reported by the device and show only supported
  fixed-rate options.
- Treat the selected value as a content-rendering cap, never as a request to
  force the device display refresh rate. The effective rate is the lower of
  the selected cap and the current display refresh rate.
- Keep the current Follow display behavior as the default.
- Preserve the existing AOD low-frame-rate path; this setting must not raise
  its cadence.
- Use a frame-aligned, allocation-free throttle for the active lyric redraw
  loop. Verify smooth 60/90/120 Hz behavior on bright lockscreen and no AOD,
  track-change, or seek regressions.

## Custom lyric metadata cleanup rules

- Add a user-editable cleanup-rule field for lyric metadata such as title,
  artist, album, and lyric display text.
- Define a small, safe rule format with a clear processing order and a preview
  using the current media metadata before the rule is saved.
- Apply rules only to Bridge-produced display metadata; do not mutate player
  MediaSession metadata, provider payloads, or Bluetooth/car media metadata.
- Validate size and pattern complexity, reject invalid rules without affecting
  the active lyric, and provide a one-tap reset to the default empty rules.
- Apply a changed rule on the next metadata/lyric refresh without triggering a
  repeating SystemUI redraw or media-session update loop.
