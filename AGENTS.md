# Repository Guidelines

## Project Structure & Module Organization

This is a Gradle Kotlin DSL Android project for an LSPosed/libxposed API 102 module.

- `app/src/main/java/io/github/andrealtb/lockscreenlyrics/` contains player adapters, lyric parsing, metadata contracts, and SystemUI hooks.
- `app/src/main/resources/META-INF/xposed/` defines the module entry point, metadata, and static scope.
- `app/src/test/java/.../lockscreenlyrics/` contains JVM unit tests.
- `libxposed-api-stubs/` provides compile-only API 102 classes; do not package or add runtime behavior here.
- `docs/` documents the player-facing `lyricInfo` integration contract and the LyricProvider bridge contract used by external provider APKs.
- `release/bridge-release-contract.json` is the machine-owned 4.0 version, scope, signing, Provider source, and asset-count contract.
- `.github/workflows/` contains debug and signed-release automation. `GIF.gif` is the README demonstration asset.

## Build, Test, and Development Commands

JDK 21 is required, although Android output targets Java 17 bytecode.

```powershell
.\scripts\gradle-local.cmd :app:assembleDebug
.\scripts\gradle-local.cmd :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb logcat -v time -s LockscreenLyrics
```

`scripts\gradle-local.cmd` discovers a real JDK 21 from `SALT_LYRIC_JAVA_HOME` or common local JDK locations, runs Gradle through a temporary ASCII drive letter, bypasses the local PowerShell script execution policy for this helper only, keeps the wrapper cache in `.gradle-user-home/`, and leaves build outputs in the standard module directories such as `app/build/outputs/`. This avoids Windows/Gradle test-worker classpath corruption when the repository path contains Chinese characters while keeping the APK path predictable. If existing Gradle lock files, project cache directories, or `local.properties` have restrictive ACLs, the script falls back to writable temp locations, mirrors the project to `%TEMP%\salt-lyric-project-overlay`, maps the workspace `android-sdk` to an ASCII drive letter, and prints the fallback paths it used. `assembleDebug` produces the test APK. `testDebugUnitTest` runs the JUnit 4 suite. After installation, enable Bridge only for `system` and `com.android.systemui`; player scopes belong to independent Provider APKs.

## Release Process

Before publishing, confirm the intended diff and update all release-facing files in the same commit:

- Bump `defaultVersionName` and `versionCode` in `app/build.gradle.kts`.
- Add `.github/release-notes/<version>.md`; the release workflow uses this file for both the source GitHub release and the LSPosed mirror release.
- Add `docs/releases/v<version>.md`; this is the durable in-repo changelog archive and must not be skipped.
- Update README when behavior, packaging, scope, or user-facing installation notes change. Public LSPosed metadata is owned only by the independent `LSPRepo` checkout.

## Provider 4.0 Integration

The Bridge owns only `system` / `com.android.systemui` hooks and generic rendering,
AOD, translation, and OPlus compatibility enhancements. Independent Providers own every
player-process hook and publish through the player's own
`MediaSession` / `MediaMetadata["lyricInfo"]`.

When adding or changing a Provider-backed player:

- Keep every player package out of the Bridge `scope.list`.
- Do not add Provider applicationIds, source ids, private broadcasts, sender kinds, or payload
  registries to the Bridge.
- Publish a ColorOS-compatible native `lyricInfo` payload from the player's own MediaSession.
- Keep track identity, generation, replay, artwork, and player-specific reflection inside the
  Provider.
- Add a player package to `PlayerSystemUiPolicy` only for a device-proven SystemUI/OPlus
  compatibility requirement or translation action policy.
- Verify the Provider without Bridge first, then verify that Bridge adds enhancement without a
  second lyric submission.

Provider APKs are independent Root / LSPosed modules. They are not bundled into the Bridge APK,
do not use NPatch, and should be signed with the release keystore selected for the Provider suite.

Validate locally before tagging:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\validate-release-contract.ps1 -ProviderRepoRoot ..\ColorOS-Live-Lyrics-Providers
git diff --check
```

Publish from clean, committed Bridge and Provider sources:

- `release/bridge-release-contract.json` and Provider `release/v5-provider-matrix.json` own the version, immutable Provider source tag, 12-module matrix, scopes, canonical asset names, release certificate, and exact asset counts. Do not duplicate these lists in workflow jobs.
- RC uses the manual `Build 4.0 RC and Release` workflow in `rc` mode with an immutable full Provider SHA or exact Provider tag. RC uploads one private complete artifact and must not create a GitHub or LSP Release.
- The Provider job checks out `Andrea-lyz/ColorOS-Live-Lyrics-Providers`, runs `testV5Matrix` and `assembleV5MatrixRelease`, and collects exactly the 12 explicit contract modules.
- The package job verifies all 13 APKs with `aapt2`, `apksigner`, and `zipalign`, requires the frozen release certificate, builds the 12-APK Provider ZIP, and emits `SHA256SUMS` plus a source/asset manifest.
- Public release mode is tag-only. Push the Provider source tag first, then the LSPRepo metadata commit and `<versionCode>-<version>` tag, verify that remote tag, and only then push Bridge `v<version>`.
- Existing public Releases are never overwritten by workflow reruns. Fixes after publication use a new version and tag.

After the workflow succeeds, verify the source release, the LSPosed release, and LSPosed module presentation:

- Confirm the exact contract-owned 16 assets exist on both the Bridge and LSP Releases: 1 Bridge APK, 12 Provider APKs, the Provider ZIP, `SHA256SUMS`, and the release asset manifest.
- Update `LSPRepo/README.md`, `SUMMARY`, `SOURCE_URL`, and `SCOPE` directly; ensure the LSP tag points at that metadata commit so LSPosed Manager sees the update.
- Download both public asset sets again and verify them against `SHA256SUMS`; do not infer success from the workflow summary alone.
- Check the public LSPosed module page ordering after tag or metadata fixes.

## Coding Style & Naming Conventions

Use four-space indentation and standard Java brace placement. Prefer `final` for immutable values, `UPPER_SNAKE_CASE` for constants, `lowerCamelCase` for methods and fields, and descriptive class names such as `SaltPlayerAdapter`. Keep reflection and hook failures guarded: SystemUI must degrade safely instead of crashing. Preserve fixed lyric-item geometry unless a change explicitly addresses scroll stability.

No formatter is enforced; run `git diff --check` before committing.

## Testing Guidelines

Use JUnit 4 and name test classes `*Test.java`; test methods should describe behavior, for example `explicitSuffixDoesNotChangeTrackIdentity`. Add deterministic parser or identity regressions for bug fixes. Fixture-dependent tests must use an explicit system property and `Assume` when the fixture is absent. There is no formal coverage threshold.

## Commit & Pull Request Guidelines

History uses short, imperative subjects such as `Fix lockscreen lyric rendering` and `Build lyrics core with JDK 21`. Use `[skip ci]` only for documentation-only changes. Pull requests should explain affected processes, list build/test results, link relevant issues, and include screenshots or a short recording for visual lyric changes. Include focused `adb logcat` excerpts for hook or timing changes.

## Security & Configuration

Keep signing credentials in environment variables or repository secrets. Never commit keystores, passwords, device logs containing personal media paths, `local.properties`, or generated APKs.
