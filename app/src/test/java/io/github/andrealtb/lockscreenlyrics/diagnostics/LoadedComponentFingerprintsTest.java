package io.github.andrealtb.lockscreenlyrics.diagnostics;
import android.os.Bundle;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk = 34, manifest = Config.NONE)
public class LoadedComponentFingerprintsTest {
    private Bundle plugin(LoadedComponentFingerprints state) {
        Bundle b = new Bundle(); state.putStatus(b);
        return b.<Bundle>getParcelableArrayList(LoadedComponentFingerprints.KEY).get(1);
    }
    @Test public void observationAndQueryNeverStartFullApkReads() {
        List<Runnable> queue = new ArrayList<>();
        LoadedComponentFingerprints state = new LoadedComponentFingerprints(() -> 1L, loader -> { throw new AssertionError("query must not resolve"); }, queue::add);
        ClassLoader loader = new ClassLoader(null) {};
        state.observed("plugin", loader); assertTrue(queue.isEmpty());
        assertEquals("OBSERVED_NOT_HASHED", plugin(state).getString("state")); assertTrue(queue.isEmpty());
        state.collect(); assertEquals(1, queue.size());
    }
    @Test public void duplicateLoaderDoesNotManufactureNewEpoch() {
        LoadedComponentFingerprints state = new LoadedComponentFingerprints(() -> 1L, loader -> List.of(), Runnable::run);
        ClassLoader loader = new ClassLoader(null) {};
        state.observed("plugin", loader); state.observed("plugin", loader);
        assertEquals(1L, plugin(state).getLong("loaderEpoch"));
    }
    @Test public void oldFingerprintCompletionCannotOverwriteNewLoader() throws Exception {
        var a = Files.createTempFile("cll-loaded-a", ".apk"); var b = Files.createTempFile("cll-loaded-b", ".apk");
        try {
            Files.write(a, "old".getBytes(StandardCharsets.UTF_8)); Files.write(b, "new".getBytes(StandardCharsets.UTF_8));
            ClassLoader old = new ClassLoader(null) {}, next = new ClassLoader(null) {};
            List<Runnable> queue = new ArrayList<>();
            LoadedComponentFingerprints state = new LoadedComponentFingerprints(() -> 5L,
                    loader -> List.of((loader == old ? a : b).toString()), queue::add);
            state.observed("plugin", old); state.collect();
            state.observed("plugin", next); state.collect();
            queue.get(0).run(); assertEquals("PENDING", plugin(state).getString("state"));
            queue.get(1).run(); assertEquals("READABLE", plugin(state).getString("state"));
            assertEquals(2L, plugin(state).getLong("loaderEpoch"));
        } finally { Files.deleteIfExists(a); Files.deleteIfExists(b); }
    }
    @Test public void fingerprintsAreContentSetsWithoutExportingPrivatePaths() throws Exception {
        var a = Files.createTempFile("cll-private-source", ".apk");
        try {
            Files.write(a, "code".getBytes(StandardCharsets.UTF_8));
            LoadedComponentFingerprints state = new LoadedComponentFingerprints(() -> 5L, loader -> List.of(a.toString()), Runnable::run);
            ClassLoader loader = new ClassLoader(null) {}; state.observed("plugin", loader); state.collect();
            Bundle result = plugin(state); assertEquals(64, result.getString("contentSetSha256").length());
            assertFalse(result.toString().contains(a.toString())); assertEquals(1, result.getInt("apkCount"));
        } finally { Files.deleteIfExists(a); }
    }
    // Reflection-shaped stand-ins for BaseDexClassLoader.pathList.dexElements[].path.
    private static final class FakeElement { final java.io.File path; FakeElement(String p) { path = new java.io.File(p); } }
    private static final class FakePathList { final Object[] dexElements; FakePathList(Object... e) { dexElements = e; } }
    private static class FakeDexLoader extends ClassLoader {
        final FakePathList pathList;
        FakeDexLoader(String... apks) {
            super(null);
            Object[] elements = new Object[apks.length];
            for (int i = 0; i < apks.length; i++) elements[i] = new FakeElement(apks[i]);
            pathList = new FakePathList(elements);
        }
    }
    /** Shape of OPlusPluginClassLoader: its own dex path is the plugin, mBase is SystemUI. */
    private static final class FakeOplusPluginLoader extends FakeDexLoader {
        final ClassLoader mBase;
        FakeOplusPluginLoader(ClassLoader base, String... apks) { super(apks); mBase = base; }
    }
    @Test public void pluginLoaderFingerprintsItsOwnPathNotTheSystemUiBase() {
        ClassLoader systemUi = new FakeDexLoader("/system_ext/priv-app/SystemUI/SystemUI.apk");
        ClassLoader plugin = new FakeOplusPluginLoader(systemUi, "/data/app/plugins/base.apk", "/data/app/plugins/split.apk");
        assertEquals(List.of("/data/app/plugins/base.apk", "/data/app/plugins/split.apk"),
                LoadedComponentFingerprints.discover(plugin).stream()
                        .map(p -> p.replace('\\', '/').replaceFirst("^[A-Za-z]:", "")).collect(java.util.stream.Collectors.toList()));
        assertEquals(1, LoadedComponentFingerprints.discover(systemUi).size());
    }
    @Test public void unavailableBackingsRemainUnknownNotUnsupported() {
        LoadedComponentFingerprints state = new LoadedComponentFingerprints(() -> 5L, loader -> List.of(), Runnable::run);
        ClassLoader loader = new ClassLoader(null) {}; state.observed("plugin", loader); state.collect();
        assertEquals("UNKNOWN", plugin(state).getString("state")); assertEquals("unknown", plugin(state).getString("contentSetSha256"));
    }
}
