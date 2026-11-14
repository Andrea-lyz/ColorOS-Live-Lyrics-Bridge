package io.github.andrealtb.lockscreenlyrics.diagnostics;
import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk = 34, manifest = Config.NONE)
public class BuildIdentityRuntimeStatusTest {
    @Test public void actualInstalledIdentityNotSettingsCodeDeterminesComparison() {
        Bundle reply = new Bundle();
        reply.putString(BuildIdentity.RUNTIME_BUILD_ID, "old-process");
        String text = BuildIdentity.runtimeReport(reply, "new-installed");
        assertTrue(text.contains("MISMATCH"));
        assertTrue(BuildIdentity.runtimeReport(reply, "old-process").contains("MATCH"));
        assertFalse(BuildIdentity.runtimeReport(reply).contains("：MATCH"));
    }
    @Test public void oldReplyCannotProveNewCodeLoaded() {
        Bundle old = new Bundle();
        old.putInt("module_version_code", 149);
        assertTrue(BuildIdentity.runtimeReport(old, "installed-id").contains("安装/注入对照：unknown"));
        assertTrue(BuildIdentity.runtimeReport(old, "installed-id").contains("revision：-1"));
    }
    @Test public void capabilityQueryDoesNotPromotePreparationToDrawOrRefreshTime() {
        final long[] time = { 10L };
        CompatibilityStatus status = new CompatibilityStatus(() -> time[0], 1000L);
        Object owner = new Object();
        status.liftPrepared(owner);
        Bundle reply = new Bundle();
        time[0] = 50L;
        status.putStatus(reply);
        String text = CompatibilityStatus.formatStatus(reply);
        assertTrue(text.contains("outcome=PREPARED"));
        assertTrue(text.contains("observedAt=10"));
        assertFalse(text.contains("outcome=DRAW_COMMANDS"));
        assertEquals(10L, status.snapshot().get(CompatibilityStatus.Feature.CHAR_LIFT.ordinal()).observedAt);
    }
    @Test public void unknownCapabilitySchemaStaysUnknown() {
        Bundle reply = new Bundle();
        reply.putInt(CompatibilityStatus.SCHEMA, 999);
        assertTrue(CompatibilityStatus.formatStatus(reply).contains("unknown"));
    }
}
