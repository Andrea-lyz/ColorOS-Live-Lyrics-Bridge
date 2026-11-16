package io.github.andrealtb.lockscreenlyrics.management

import android.content.pm.PackageManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeExtrasTest {
    @Test fun whatsNewOnlyForUpgradesAndOnlyOnce() {
        assertTrue(WhatsNew.shouldShow(seen = false, firstInstallTime = 1_000L, lastUpdateTime = 2_000L))
        assertFalse(WhatsNew.shouldShow(seen = true, firstInstallTime = 1_000L, lastUpdateTime = 2_000L))
        // A fresh install has equal times: no card.
        assertFalse(WhatsNew.shouldShow(seen = false, firstInstallTime = 1_000L, lastUpdateTime = 1_000L))
    }

    @Test fun theIconIsShownUnlessExplicitlyDisabled() {
        assertTrue(LauncherIcon.isShown(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT))
        assertTrue(LauncherIcon.isShown(PackageManager.COMPONENT_ENABLED_STATE_ENABLED))
        assertFalse(LauncherIcon.isShown(PackageManager.COMPONENT_ENABLED_STATE_DISABLED))
    }
}
