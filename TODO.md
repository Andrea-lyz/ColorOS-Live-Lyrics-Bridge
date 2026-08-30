# TODO

The dynamic render-rate cap and guided opening-lyric cleanup page are implemented
in the versioned lyric settings introduced after v3.0.4. The former display-only
metadata DSL was removed. No deferred items from those work packages remain here.

Bridge 4.0 no longer contains the direct-v4 transport, player-process adapters, or Provider
applicationId/source registries. It consumes only native `MediaMetadata["lyricInfo"]` in
SystemUI. Phase 5 device regression and architecture gates closed on 2026-08-29.

The post-Phase-6 lyric brightness/fade controls, presets, preview, backup/restore, and alignment
pivot repair are implemented and device-validated. Phase 6 method/performance governance is closed.

Phase 7 Slice 7A–7F is complete. RC5 (`33301880289`) passed user device validation, but the
pre-release package-only SystemUI compatibility addition for Halcyon, Flamingo, QZ Music, and
PrismMusic is limited to four package constants and passed 481 tests, lint, and release-contract
checks. The two old v4 PRs were closed without merge after their authors received the 4.0
native-integration requirements. The project owner explicitly waived RC6 and authorized Slice 7G;
formal tags, Releases, and public-asset revalidation are now in progress. Source of truth:
`docs/4.0/PHASE-7-RC-RELEASE-PLAN.md`.
