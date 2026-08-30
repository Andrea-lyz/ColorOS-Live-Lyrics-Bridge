# TODO

The dynamic render-rate cap and guided opening-lyric cleanup page are implemented
in the versioned lyric settings introduced after v3.0.4. The former display-only
metadata DSL was removed. No deferred items from those work packages remain here.

Bridge 4.0 no longer contains the direct-v4 transport, player-process adapters, or Provider
applicationId/source registries. It consumes only native `MediaMetadata["lyricInfo"]` in
SystemUI. Phase 5 device regression and architecture gates closed on 2026-08-29.

The post-Phase-6 lyric brightness/fade controls, presets, preview, backup/restore, and alignment
pivot repair are implemented and device-validated. Phase 6 method/performance governance is closed.

Phase 7 is complete. v4.0.0 was published by Actions run `33305725280` to the Bridge and LSP
repositories with the same 16 assets. Public checksums, manifest/source identity, 13 APK contracts,
and the 12-APK Provider ZIP were independently reverified. No 4.0 release task remains; future fixes
use a new version and never move or replace the published tags/assets. Source of truth:
`docs/4.0/PHASE-7-RC-RELEASE-PLAN.md`.
