package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkFailureRetryTest {
    private static ArtworkRequestStamp song(long session, long generation) {
        return new ArtworkRequestStamp(1, 1, 1, 1, session, generation, 1, 1);
    }

    @Test public void providerProcessLossIsRetried() {
        for (String reason : new String[]{"provider_died", "provider_disconnected", "binding_died", "null_binding", "handshake_timeout"}) {
            assertEquals(reason, 5_000, ArtworkFailureRetry.delayMs(reason));
        }
        assertEquals(30_000, ArtworkFailureRetry.delayMs("request_timeout"));
        assertEquals(30_000, ArtworkFailureRetry.delayMs("provider_or_asset_failed"));
    }

    @Test public void decoderFailuresAreRetried() {
        for (String reason : new String[]{"decode_failed", "prepare_failed", "first_frame_timeout", "surface_handover_failed"}) {
            assertNotEquals(reason, ArtworkFailureRetry.NONE, ArtworkFailureRetry.delayMs(reason));
        }
    }

    @Test public void permanentRejectionsAndProviderAnswersKeepTheirOwnPolicy() {
        for (String reason : new String[]{"mount_unsupported", "live_identity_incomplete", "provider_mode_mismatch",
                "provider_identity_changed", "artwork_caller_denied", "no_match", "network_blocked", "retry_later", "stale_render"}) {
            assertEquals(reason, ArtworkFailureRetry.NONE, ArtworkFailureRetry.delayMs(reason));
        }
    }

    @Test public void budgetIsPerSongAndResetByPlayback() {
        ArtworkFailureRetry retry = new ArtworkFailureRetry();
        for (int i = 0; i < ArtworkFailureRetry.MAX_PER_SONG; i++) assertEquals(5_000, retry.next("decode_failed", song(1, 1)));
        assertEquals(ArtworkFailureRetry.NONE, retry.next("decode_failed", song(1, 1)));
        // Another song starts its own budget.
        assertEquals(5_000, retry.next("decode_failed", song(1, 2)));
        retry.played();
        for (int i = 0; i < ArtworkFailureRetry.MAX_PER_SONG; i++) assertEquals(5_000, retry.next("provider_died", song(1, 2)));
        assertEquals(ArtworkFailureRetry.NONE, retry.next("provider_died", song(1, 2)));
    }

    @Test public void nonRetryableReasonsDoNotSpendTheBudget() {
        ArtworkFailureRetry retry = new ArtworkFailureRetry();
        assertEquals(ArtworkFailureRetry.NONE, retry.next("mount_unsupported", song(1, 1)));
        for (int i = 0; i < ArtworkFailureRetry.MAX_PER_SONG; i++) assertEquals(5_000, retry.next("provider_died", song(1, 1)));
    }
}
