# TODO

## AOD lyric transition research

Current known-good build is acceptable and should remain the baseline. Future
AOD work should move toward a read-only structural snapshot model, not toward
visibility/alpha/readiness gates or more active layout correction.

### Goal

Split AOD handling into two independent timelines:

- Structural timeline: follow official SystemUI state by reading and caching
  index, scroll, child positions, item heights, adapter positions, and stable
  lyric keys.
- Visual timeline: render scale, highlight, blur, word progress, and custom
  drawing only from cached snapshots in `TextView.onDraw`.

The module should be a follower of official `LyricsRecyclerView` state during
AOD transitions, not a second controller of official scroll/layout state.

### Proposed first step: observation-only snapshot build

- Hook `LyricsRecyclerView.onLayout` after official layout completes.
- Set a lightweight `structuralReady` marker for diagnostics only. Do not use
  it to block first prime.
- Cache a frame snapshot containing official index, first visible position,
  first visible top, child adapter positions, child top/height, and lyric key.
- Keep existing prime, slot-height, and draw behavior unchanged in this first
  observation build.
- Add logs to compare the layout snapshot against current draw-time decisions.

### Follow-up experiments, only if logs support them

- Let `TextView.onDraw` prefer the latest stable layout snapshot instead of
  querying live `RecyclerView` / `LayoutManager` state during AOD transition.
- If a row's adapter position or lyric key does not match the stable snapshot,
  skip module custom drawing for that frame or reuse the last stable draw frame.
- Move more visual-only behavior to draw-layer transforms and avoid writing
  layout params during the AOD transition window.
- Gradually reduce active calls into official `setCurrentLyric` / scroll APIs
  only after the snapshot path proves stable.

### Long-term fix direction: RecyclerView overlay renderer

The edge flicker root cause is now considered a rendering-layer mismatch:
`TextView.onDraw` can stabilize text content, but it cannot control
`LyricsRecyclerView.dispatchDraw`, fading-edge clipping, child recycling, or
whether an edge child is drawn at all in a given AOD frame.

Future root-cause work should move complex lyric rendering from per-`TextView`
hooks to a `LyricsRecyclerView`-level `ItemDecoration` or overlay renderer:

- Official `LyricsRecyclerView` remains the single owner of layout, scroll,
  active index, child clipping, fading edge, and AOD direct alignment.
- The module reads official child geometry after layout and draws custom
  visuals in RecyclerView coordinates as a pure overlay.
- Official TextViews should keep their layout-anchor role. If their text is
  made transparent to avoid double drawing, the fallback path must restore
  default drawing immediately when overlay state is unavailable.
- Overlay drawing may use `Canvas`, `Paint`, `StaticLayout`, alpha, clip, and
  scale, but must not call `setLayoutParams`, `requestLayout`, scroll APIs, or
  private AOD state setters.
- Multi-line main lyrics, translations, pseudo word progress, blur, and visual
  scale should be treated as overlay-only effects, not as RecyclerView item
  height changes.
- In AOD, the overlay should degrade to the official-like steady state:
  active/inactive rows only, no complex edge animation, no blur, no pseudo word
  or translation progress animation, and no extra layout mutation.

This should be a separate experimental branch, not a patch layered onto the
current release-candidate baseline.

### Release-candidate mitigation: reduce AOD flicker visibility

For the current acceptable build, only small mitigations are allowed:

- Do not add edge-row special cases.
- Do not change official scroll, prime, or slot-height timing.
- In AOD low-frame-rate mode, reduce continuous redraw pressure and expensive
  edge-sensitive effects:
  - throttle active lyric refresh cadence;
  - keep inactive blur as a static official-like row state when the user enabled
    it, but do not animate it;
  - disable pseudo word progress, translation progress, translation horizontal
    scrolling, and translated-line sliding animation;
  - keep translated main-line window tracking so wrapped lyrics longer than two
    lines can still switch to the third line in AOD, but make that window switch
    directly instead of animating every frame;
  - when entering AOD from word-progress mode, briefly fade the active line from
    partial word highlight to whole-line highlight so the visual downgrade does
    not pop in a single frame.
- Keep bright-screen behavior unchanged.

### Follow-up fix plan: edge-row flicker

The remaining hard case is clipped inactive rows near the top/bottom edge of
`LyricsRecyclerView`. Do not treat edge rows as a separate rendering class.
Instead, all rows should use the same frame-stability pipeline; edge rows will
naturally fall into the unstable-frame path more often because official
RecyclerView clipping and recycling are changing around them.

#### Phase 0: keep the known-good baseline

- Keep the current active/inactive two-state visual model:
  - active scale: `1.0`
  - inactive scale: `0.9`
  - inactive fade/blur: one shared inactive value, no near/far tiers
- Do not reintroduce edge-row hiding, edge-row height freezing, or edge-row
  draw skipping.
- Keep first prime timing unchanged. Do not gate it on alpha, size, measured
  dimensions, or AOD state.

#### Phase 1: observation-only geometry snapshots

- Hook or observe `LyricsRecyclerView.onLayout` after official layout finishes.
- Cache per-frame official geometry without changing behavior:
  - recycler identity
  - layout pass sequence
  - first visible position and first visible top
  - child adapter position
  - child top, bottom, height
  - text view width/height
  - stable lyric key
- Log geometry diffs for flicker cases:
  - item top/bottom delta
  - adapter position change
  - lyric key change
  - whether the row was clipped at the top or bottom
- This phase must not change drawing, slot height, scroll, alpha, or visibility.

#### Phase 2: frame-stability classifier

- Add a small per-row state keyed by stable lyric identity, not only adapter
  position.
- Mark a frame stable when:
  - lyric key matches the previous frame
  - adapter position is unchanged or maps to the same lyric key
  - item top/bottom delta is within a tiny threshold, initially `2px`
  - text view width/height are unchanged
- Mark a frame unstable when geometry jumps, adapter position changes to a
  different lyric key, or the row is inside an official layout/scroll settle
  window.
- Keep this classifier independent from AOD state. AOD can increase the chance
  of unstable frames, but it should not be the gate itself.

#### Phase 3: draw-layer snapshot fallback

- Stable frame:
  - render normally through the module custom renderer
  - update the row's last stable draw snapshot
- Unstable frame with matching lyric key and usable snapshot:
  - do not hide the row
  - do not fall back to alternating official/module drawing
  - draw the cached bitmap content, while still following official current
    geometry for placement/clipping
- First frame or identity mismatch:
  - avoid reusing old content
  - use the current safe rendering path and build a new snapshot only after the
    frame becomes stable
- Snapshot invalidation keys should include:
  - lyric key
  - text and translation text
  - active/inactive state
  - progress/render position bucket
  - text size/typeface/width/height
  - scroll-scale, inactive-blur, line-timed-progress, translation-progress
    settings

#### Phase 4: layout-param write boundary

- During unstable official geometry, avoid new layout-param writes from draw.
- If slot height must be updated, do it only from the existing known-good timing
  path, not from an edge-row special case.
- Prefer canvas transforms and bitmap snapshot reuse over `setLayoutParams`.
- Any layout write inside the AOD/settle window must log the reason and the
  affected lyric key.

#### Validation checklist

- Compare against the known-good build and the current two-state visual build.
- Test at least:
  - first AOD transition after SystemUI/plugin attach
  - repeated AOD transitions after cache warm-up
  - clipped top row
  - clipped bottom row
  - active line change while in AOD
  - track switch near the beginning of a song
- Required log points:
  - `Observed LyricsRecyclerView attachment`
  - `Stabilized LyricsRecyclerView scroll`
  - `Primed LyricsRecyclerView`
  - `Official lyric layout height changed`
  - `Official lyric row scale`
  - new geometry snapshot diff logs
  - new draw snapshot hit/miss/invalidated logs
- Success criteria:
  - no alternating official/module blanking on clipped rows
  - no third-line sudden inactive visual tier change
  - no new SystemUI text rendering regressions outside `LyricsRecyclerView`
  - no repeated layout-param writes caused by the snapshot fallback

### Boundaries

- Do not delay first `LyricsRecyclerView` prime based on `alpha`, `size`, or
  measured dimensions.
- Do not gate row scale animation on recycler visual readiness.
- Do not broaden custom drawing or height mutation to ordinary SystemUI
  `TextView`s.
- Do not hook or force private official AOD state unless logs prove there is no
  safer lyric-local path.
- Do not layer fixes over a worse AOD experiment. Revert the failed experiment
  first, then try a smaller change.
- Do not special-case clipped edge rows by hiding them, freezing their height,
  or skipping their draw path. Use the shared frame-stability classifier and
  snapshot fallback instead.
