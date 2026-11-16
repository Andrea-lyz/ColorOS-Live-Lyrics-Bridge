package io.github.andrealtb.lockscreenlyrics.management.root

import io.github.andrealtb.lockscreenlyrics.management.Enablement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LsposedConfigTest {
    private val salt = "io.github.andrealtb.coloroslyrics.provider.salt"

    @Test fun detectsBothKnownLayouts() {
        // LSPosed 2.2.1 (7941) on PJZ110, as read from the device.
        val perUser = mapOf(
            "modules" to setOf("module_pkg_name", "apk_path"),
            "modules_state" to setOf("module_pkg_name", "user_id", "enabled", "scope_request_blocked"),
            "scope" to setOf("module_pkg_name", "app_pkg_name", "user_id"),
        )
        assertEquals(LsposedLayout.PER_USER_STATE, detectLayout(perUser))
        // LSPosed 1.x / Vector 2.2 source.
        val moduleId = mapOf(
            "modules" to setOf("mid", "module_pkg_name", "apk_path", "enabled", "auto_include"),
            "scope" to setOf("mid", "app_pkg_name", "user_id"),
        )
        assertEquals(LsposedLayout.MODULE_ID, detectLayout(moduleId))
    }

    @Test fun unknownLayoutsAreRejected() {
        assertNull(detectLayout(emptyMap()))
        assertNull(detectLayout(mapOf("modules" to setOf("module_pkg_name"), "scope" to setOf("app_pkg_name", "user_id"))))
        assertNull(detectLayout(mapOf(
            "modules_state" to setOf("module_pkg_name", "user_id", "enabled"),
            "scope" to setOf("mid", "app_pkg_name", "user_id"),
        )))
    }

    @Test fun stateRowsMapToEnablement() {
        assertEquals(true, LsposedQueries.enabledValue(1, registered = true))
        assertEquals(false, LsposedQueries.enabledValue(0, registered = true))
        // Registered with LSPosed but never enabled for this user.
        assertEquals(false, LsposedQueries.enabledValue(null, registered = true))
        assertNull(LsposedQueries.enabledValue(null, registered = false))
        val snapshot = LsposedSnapshot(mapOf(salt to true), mapOf(salt to setOf("com.salt.music")))
        assertEquals(Enablement.ENABLED, snapshot.reportFor(salt).state)
        assertEquals(Enablement.UNKNOWN, snapshot.reportFor("io.github.andrealtb.coloroslyrics.provider.qq").state)
        assertEquals(Enablement.NOT_ENABLED, LsposedSnapshot(mapOf(salt to false), emptyMap()).reportFor(salt).state)
    }

    @Test fun rootOnlyCountsAsGrantedForUidZero() {
        assertEquals(RootState.GRANTED, rootStateOf(0, "0\n"))
        assertEquals(RootState.DENIED, rootStateOf(1, ""))
        assertEquals(RootState.DENIED, rootStateOf(0, "10234"))
    }
}
