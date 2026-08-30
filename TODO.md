# TODO

The dynamic render-rate cap and guided opening-lyric cleanup page are implemented
in the versioned lyric settings introduced after v3.0.4. The former display-only
metadata DSL was removed. No deferred items from those work packages remain here.

Bridge 4.0 no longer contains the direct-v4 transport, player-process adapters, or Provider
applicationId/source registries. It consumes only native `MediaMetadata["lyricInfo"]` in
SystemUI. Phase 5 device regression and architecture gates closed on 2026-08-29.

Deferred plan — **not implemented; do not start before Phase 6 closes**:

- Fully expose lyric visual alpha layers, translation brightness, inactive-translation follow,
  Recycler edge fade enable/length, and inactive-row fade in a dedicated settings sub-page.
- Source of truth:
  `docs/4.0/POST-PHASE-6-LYRIC-VISUAL-CONTROLS-PLAN.md`.

The remaining 4.0 release workflow is tracked in the workspace-level `todo.md` and
`docs/4.0/PHASE-7-RC-RELEASE-PLAN.md`. The Phase 7 document is an unimplemented
review draft until its release decisions are approved.
