package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.util.Set;

import io.github.andrealtb.lockscreenlyrics.ArtworkSettingsStatus.Kind;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgeDebugConfig;
import io.github.andrealtb.lockscreenlyrics.systemui.artwork.ArtworkDisplaySettings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ArtworkSettingsStatusTest {
    @Test
    public void liveStatesAndProviderAnswersBecomeUserFacingKinds() {
        assertEquals(Kind.PLAYING, ArtworkSettingsStatus.classify("playing"));
        assertEquals(Kind.RESOLVING, ArtworkSettingsStatus.classify("resolving"));
        assertEquals(Kind.PLAYED, ArtworkSettingsStatus.classify("played"));
        assertEquals(Kind.WAITING, ArtworkSettingsStatus.classify("waiting"));
        assertEquals(Kind.WAITING, ArtworkSettingsStatus.classify(null));
        assertEquals(Kind.OFF, ArtworkSettingsStatus.classify("off"));
        assertEquals(Kind.NO_MOTION, ArtworkSettingsStatus.classify("no_motion:confirmed_album_no_motion"));
        assertEquals(Kind.NO_MOTION, ArtworkSettingsStatus.classify("unsupported:no_square_motion_asset"));
        assertEquals(Kind.PLAYBACK_FAILED, ArtworkSettingsStatus.classify("unsupported:media_manifest_mismatch"));
        assertEquals(Kind.UNMATCHED, ArtworkSettingsStatus.classify("retry_later:catalog_match_unconfirmed"));
        assertEquals(Kind.UNMATCHED, ArtworkSettingsStatus.classify("retry_later:catalog_album_unconfirmed"));
        assertEquals(Kind.UNMATCHED, ArtworkSettingsStatus.classify("ambiguous:multiple_catalog_matches"));
        assertEquals(Kind.NETWORK, ArtworkSettingsStatus.classify("retry_later:network_stage_timeout"));
        assertEquals(Kind.SOURCE_UNREADABLE, ArtworkSettingsStatus.classify("retry_later:web_schema_changed"));
        assertEquals(Kind.MOTION_UNSUPPORTED, ArtworkSettingsStatus.classify("unsupported:motion_asset_unrecognized"));
        assertEquals(Kind.NETWORK, ArtworkSettingsStatus.classify("network_blocked:network_policy"));
        assertEquals(Kind.SOURCE_DISABLED, ArtworkSettingsStatus.classify("network_blocked:provider_disabled"));
        assertEquals(Kind.TEST_ONLY, ArtworkSettingsStatus.classify("provider_mode_mismatch"));
        assertEquals(Kind.PROVIDER_REJECTED, ArtworkSettingsStatus.classify("artwork_caller_denied"));
        assertEquals(Kind.PROVIDER_UNAVAILABLE, ArtworkSettingsStatus.classify("selection_failed"));
        assertEquals(Kind.PROVIDER_FAILED, ArtworkSettingsStatus.classify("bind_failed"));
        assertEquals(Kind.PLAYBACK_FAILED, ArtworkSettingsStatus.classify("first_frame_timeout"));
        assertEquals(Kind.FAILED, ArtworkSettingsStatus.classify("error:resolver_failed"));
    }

    @Test
    public void backupAcceptsOlderBackupsWithoutArtworkButNothingUnknown() {
        assertTrue(BridgeConfigBackupRepository.acceptsNamespaces(
                Set.of(LyricUiSettings.PREFERENCES_NAME, BridgeDebugConfig.PREFS_NAME)));
        assertTrue(BridgeConfigBackupRepository.acceptsNamespaces(Set.of(LyricUiSettings.PREFERENCES_NAME,
                BridgeDebugConfig.PREFS_NAME, ArtworkDisplaySettings.PREFERENCES)));
        assertFalse(BridgeConfigBackupRepository.acceptsNamespaces(
                Set.of(LyricUiSettings.PREFERENCES_NAME, ArtworkDisplaySettings.PREFERENCES)));
        assertFalse(BridgeConfigBackupRepository.acceptsNamespaces(Set.of(LyricUiSettings.PREFERENCES_NAME,
                BridgeDebugConfig.PREFS_NAME, "someone_else")));
    }
}
