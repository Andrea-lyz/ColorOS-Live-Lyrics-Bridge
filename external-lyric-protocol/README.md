# External Lyric Protocol

## Direct v4

Version 4 is the only supported external-lyric transport.

1. Code injected into a supported player process prepares an `Intent` payload.
2. LyricProvider's shared `SystemUiBroadcastSender` sets the v4 action, explicitly targets
   `com.android.systemui`, fills `source`, `playerPackage`, `senderPackage`, and `senderKind`,
   then sends the broadcast from that player process.
3. The SystemUI-side module receives only `EXTERNAL_LYRIC_DIRECT_V4` and accepts a provider
   payload only when its declared `source -> playerPackage` pair is in the static registry and
   `senderPackage == playerPackage`.

ColorOS does not expose a reliable broadcast sender UID on the target devices. The whitelist is
therefore an admission rule for known injected integrations, not a cryptographic sender-identity
check. Do not represent it as one.

## Required payload fields

Every direct v4 payload must include `protocolVersion`, `source`, `playerPackage`,
`senderPackage`, `senderKind`, and `eventType`, plus the applicable track or lyric fields. A lyric
document normally includes `trackGeneration`, `mediaId` or `trackKey`, `songName`, `artist`,
`lyric`, and optional `rawLyric` and `translationLyric`.

SystemUI rejects unknown actions, protocol versions other than 4, unknown source/player pairs,
mismatched sender/player claims, and oversized payloads.

## Fixture

`src/test/resources/fixtures/external-lyric-direct-v4.json` records the only supported SystemUI
ingress shape and is verified by `ExternalLyricProtocolFixtureTest`.
