# Repository Guidelines

## Project Structure & Module Organization

This is a Gradle Kotlin DSL Android project for an LSPosed/libxposed API 102 module.

- `app/src/main/java/io/github/andrealtb/lockscreenlyrics/` contains player adapters, lyric parsing, metadata contracts, and SystemUI hooks.
- `app/src/main/resources/META-INF/xposed/` defines the module entry point, metadata, and static scope.
- `app/src/test/java/.../lockscreenlyrics/` contains JVM unit tests.
- `libxposed-api-stubs/` provides compile-only API 102 classes; do not package or add runtime behavior here.
- `docs/` documents the player-facing `lyricInfo` integration contract.
- `.github/workflows/` contains debug and signed-release automation. `GIF.gif` is the README demonstration asset.

## Build, Test, and Development Commands

JDK 21 is required, although Android output targets Java 17 bytecode.

```powershell
.\scripts\gradle-local.cmd :app:assembleDebug
.\scripts\gradle-local.cmd :app:testDebugUnitTest
adb install -r .gradle-local-build\app\outputs\apk\debug\app-debug.apk
adb logcat -v time -s LockscreenLyrics
```

`scripts\gradle-local.cmd` discovers JDK 21 from `SALT_LYRIC_JAVA_HOME`, `JAVA_HOME`, or common local JDK locations, runs Gradle through a temporary ASCII drive letter, bypasses the local PowerShell script execution policy for this helper only, stores the wrapper cache in `.gradle-user-home/`, and writes build outputs to `.gradle-local-build/`. This avoids Windows/Gradle test-worker classpath corruption when the repository path contains Chinese characters and avoids stale `app/build` output locks. `assembleDebug` produces the test APK. `testDebugUnitTest` runs the JUnit 4 suite. After installation, enable the module for System UI and the target player in LSPosed, then restart affected processes.

## Release Process

Before publishing, confirm the intended diff and update all release-facing files in the same commit:

- Bump `defaultVersionName` and `versionCode` in `app/build.gradle.kts`.
- Add `.github/release-notes/<version>.md`; the release workflow uses this file for both the source GitHub release and the LSPosed mirror release.
- Add `docs/releases/v<version>.md`; this is the durable in-repo changelog archive and must not be skipped.
- Update README or `.github/lsposed/` metadata when behavior, packaging, scope, or user-facing installation notes change.

Validate locally before tagging:

```powershell
.\scripts\gradle-local.cmd testDebugUnitTest assembleDebug
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\validate-lsposed-metadata.ps1
git diff --check
```

Publish from a clean, committed `main`:

- Commit the release changes with a short subject such as `Release v2.0.1`.
- Push `main`, then create and push the source tag `v<version>`.
- The `Release APK Bundle` workflow builds the bridge from this repository and builds provider APKs from `Andrea-lyz/LyricProvider`. On manual dispatch, set `lyric_provider_ref` when the provider release must use a branch, tag, or commit other than `master`.
- The release workflow must upload the bridge APK and every release-ready LyricProvider APK to both the source GitHub release and the LSPosed mirror release. Current assets are `ColorOS-Live-Lyrics-Bridge-v<version>.apk`, `LyricProvider-QQMusic-v<version>.apk`, `LyricProvider-163Music-v<version>.apk`, `LyricProvider-Poweramp-v<version>.apk`, and `LyricProvider-Spotify-v<version>.apk`.
- Provider APKs are auxiliary modules, not bundled into the bridge APK. Keep each player provider as a separate LyricProvider module and sign them with the same release keystore used by the bridge.
- The LSPosed release tag is derived as `<versionCode>-<versionName>`, for example `101-2.0.1`.

When a newly adapted player provider is ready to ship:

- Add a provider build job to `.github/workflows/release.yml`, or extend the existing provider job pattern, so it checks out `Andrea-lyz/LyricProvider`, builds the provider module with `:<module-id>:assembleRelease`, copies the APK to `out/LyricProvider-<PlayerName>-${release_tag}.apk`, and uploads it as an artifact.
- Add the new provider job to the `publish` job `needs` list. The existing artifact download and `gh release upload` steps collect all APK artifacts automatically once the new job uploads its APK.
- Update the fallback release note text in `.github/workflows/release.yml` and the release-facing notes files (`.github/release-notes/<version>.md` and `docs/releases/v<version>.md`) so users know the new provider APK is included and must be installed separately.
- Update this release-process checklist and the post-release asset verification list with the new expected APK name.
- Verify the LyricProvider fork contains the provider changes on the ref used by the release workflow, especially when `lyric_provider_ref` is not `master`.

After the workflow succeeds, verify the source release, the LSPosed release, and LSPosed module presentation:

- Confirm all five APK assets exist on `Andrea-lyz/ColorOS-Live-Lyrics-Bridge` tag `v<version>`.
- Confirm all five APK assets exist on `Xposed-Modules-Repo/io.github.andrealtb.lockscreenlyrics` tag `<versionCode>-<versionName>`.
- Sync the LSPosed mirror repo metadata (`README.md`, `SUMMARY`, `SOURCE_URL`, `SCOPE`) from `.github/lsposed/` when needed, push that metadata commit, and ensure the LSPosed release tag points at the latest metadata commit so LSPosed Manager sees the update.
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
