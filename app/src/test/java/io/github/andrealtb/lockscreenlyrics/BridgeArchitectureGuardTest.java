package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import io.github.andrealtb.lockscreenlyrics.bootstrap.SystemUiRuntimeBootstrap;
import io.github.andrealtb.lockscreenlyrics.diagnostics.BridgePerformanceSampler;
import io.github.andrealtb.lockscreenlyrics.diagnostics.StructuredBridgeLog;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoArtworkSnapshotStore;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoCoverPolicy;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoMediaIdentityPolicy;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoPluginMediaModelReader;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoSameTrackLyricRetention;
import io.github.andrealtb.lockscreenlyrics.players.kuwo.KuWoSystemUiRuntime;
import io.github.andrealtb.lockscreenlyrics.render.OfficialLyricFrameResolver;
import io.github.andrealtb.lockscreenlyrics.render.LyricDrawLayoutEngine;
import io.github.andrealtb.lockscreenlyrics.systemui.lyrics.LyricsRecyclerPolicy;
import io.github.andrealtb.lockscreenlyrics.systemui.lyrics.LyricsRecyclerFieldAccessor;
import io.github.andrealtb.lockscreenlyrics.systemui.lyrics.SystemUiLoadLyricPolicy;

public final class BridgeArchitectureGuardTest {
    private static final List<String> FORBIDDEN_V4_MARKERS = Arrays.asList(
            "io.github.andrealtb.coloroslyrics.provider.",
            "io.github.proify.lyricon.",
            "EXTERNAL_LYRIC_DIRECT_V4",
            "lyricprovider/",
            "ExternalLyricProtocol",
            "ExternalLyricIngress",
            "ExternalLyricProviderRegistry",
            "ExternalLyricSenderPolicy",
            "senderKind",
            "lockscreen-lyrics-module",
            "PlayerRuntimeBootstrap",
            "PlayerAdapterRegistry");

    @Test
    public void compositionTypesLiveInTargetPackages() {
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics",
                NativeLyricModelAssembler.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics",
                SupplementalTranslationIndex.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics",
                LyricModelTraceSupport.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics",
                OfficialLyricDrawCoordinator.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics.bootstrap",
                SystemUiRuntimeBootstrap.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics.diagnostics",
                StructuredBridgeLog.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics.diagnostics",
                BridgePerformanceSampler.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics.players.kuwo",
                KuWoMediaIdentityPolicy.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics.players.kuwo",
                KuWoCoverPolicy.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics.players.kuwo",
                KuWoArtworkSnapshotStore.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics.players.kuwo",
                KuWoSameTrackLyricRetention.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics.players.kuwo",
                KuWoPluginMediaModelReader.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics.players.kuwo",
                KuWoSystemUiRuntime.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics.render",
                OfficialLyricFrameResolver.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics.render",
                LyricDrawLayoutEngine.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics.systemui.lyrics",
                SystemUiLoadLyricPolicy.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics.systemui.lyrics",
                LyricsRecyclerPolicy.class);
        assertEqualsPackage(
                "io.github.andrealtb.lockscreenlyrics.systemui.lyrics",
                LyricsRecyclerFieldAccessor.class);
    }

    @Test
    public void appMainContainsNoV4TransportOrProviderApplicationIds() throws Exception {
        File main = locateFromProject("app/src/main");
        assumeTrue("app/src/main is visible to unit tests", main.isDirectory());
        for (File file : Files.walk(main.toPath())
                .filter(Files::isRegularFile)
                .map(java.nio.file.Path::toFile)
                .collect(Collectors.toList())) {
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            // 5.0 plan §10: the ordinary-App management store may name the official Provider
            // set (and the manifest may declare directed <queries> for it). Hook, bootstrap and
            // render code may not, and every other v4 transport marker stays forbidden everywhere.
            boolean managementUi = file.getPath().contains(File.separator + "lockscreenlyrics"
                    + File.separator + "management" + File.separator);
            if (file.getName().equals("AndroidManifest.xml")) {
                source = source.replaceAll("(?s)<queries>.*?</queries>", "");
            }
            for (String marker : FORBIDDEN_V4_MARKERS) {
                if (managementUi && marker.equals(PROVIDER_APPLICATION_ID_MARKER)) {
                    continue;
                }
                assertFalse(file + " contains forbidden Phase 5 marker " + marker,
                        source.contains(marker));
            }
        }
    }

    private static final String PROVIDER_APPLICATION_ID_MARKER =
            "io.github.andrealtb.coloroslyrics.provider.";

    /**
     * Compose/Kotlin management UI (5.0 plan §8.4) lives only in the management package. The
     * same APK is loaded into SystemUI, so no other source may reference Compose, the
     * management package, or start its Activity from Hook, bootstrap or render code.
     */
    /** Ordinary-App settings Activities allowed to open the management UI (never Hook code). */
    private static final List<String> MANAGEMENT_UI_ENTRY_POINTS = Arrays.asList(
            "BridgeDebugSettingsActivity.java");

    private static final List<String> MANAGEMENT_ONLY_MARKERS = Arrays.asList(
            "HttpURLConnection", "HttpsURLConnection", "java.net.URL", "openConnection(",
            "PackageInstaller");

    @Test
    public void composeManagementUiStaysOutOfHookAndRenderCode() throws Exception {
        File main = locateFromProject("app/src/main");
        assumeTrue("app/src/main is visible to unit tests", main.isDirectory());
        String managementDir = File.separator + "lockscreenlyrics" + File.separator
                + "management" + File.separator;
        int managementSources = 0;
        for (File file : Files.walk(main.toPath())
                .filter(Files::isRegularFile)
                .map(java.nio.file.Path::toFile)
                .filter(f -> f.getName().endsWith(".java") || f.getName().endsWith(".kt"))
                .collect(Collectors.toList())) {
            if (file.getPath().contains(managementDir)) {
                managementSources++;
                continue;
            }
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            assertFalse(file + " references Compose outside the management UI",
                    source.contains("androidx.compose"));
            // Store networking and package installation (5.0 plan §6.4) are management-only;
            // the INTERNET permission must never be exercised from SystemUI/system code.
            for (String marker : MANAGEMENT_ONLY_MARKERS) {
                assertFalse(file + " uses " + marker + " outside the management UI",
                        source.contains(marker));
            }
            if (MANAGEMENT_UI_ENTRY_POINTS.contains(file.getName())) {
                continue;
            }
            assertFalse(file + " references the management UI package",
                    source.contains("io.github.andrealtb.lockscreenlyrics.management")
                            || source.contains("ManagementPreviewActivity"));
        }
        assertTrue("management UI sources expected under app/src/main", managementSources > 0);
        assertFalse("Xposed entry must stay the Java module class",
                readProjectFile("app/src/main/resources/META-INF/xposed/java_init.list")
                        .contains("management"));
    }

    @Test
    public void gradleAndScopeExposeOnlySystemUiBridge() throws Exception {
        String settings = readProjectFile("settings.gradle.kts");
        String appGradle = readProjectFile("app/build.gradle.kts");
        List<String> scope = Files.readAllLines(
                locateFromProject("app/src/main/resources/META-INF/xposed/scope.list").toPath(),
                StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
        assertFalse(settings.contains("external-lyric-protocol"));
        assertFalse(appGradle.contains("external-lyric-protocol"));
        assertEquals(Arrays.asList("system", "com.android.systemui"), scope);
    }

    @Test
    public void systemUiRuntimeContainsNoNetworkClientOrLeakyClassCache() throws Exception {
        String module = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java");
        String coordinator = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/"
                        + "OfficialLyricDrawCoordinator.java");
        String accessor = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/systemui/lyrics/"
                        + "LyricsRecyclerFieldAccessor.java");
        assertFalse(module.contains("java.net.URL"));
        assertFalse(module.contains("HttpURLConnection"));
        assertFalse(module.contains("openConnection("));
        assertFalse(module.contains("WeakReference<MediaController>"));
        assertFalse(module.contains("WeakHashMap<Class<?>, Method"));
        assertFalse(accessor.contains("WeakHashMap<Class<?>, Field"));
        assertTrue(accessor.contains("WeakReference<Field>"));
        assertFalse(module.contains("canvas.restore();"));
        assertFalse(module.contains("target.canvas.restore();"));
        assertTrue(coordinator.contains("failedBindings.put(textView, model)"));
        assertTrue(coordinator.contains("return proceed.proceed();"));
    }

    private static void assertEqualsPackage(String expected, Class<?> type) {
        assertEquals(expected, type.getPackage().getName());
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File file = locateFromProject(relativePath);
        assumeTrue(relativePath + " is visible to unit tests", file.isFile());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static File locateFromProject(String relativePath) {
        File direct = new File(relativePath);
        if (direct.exists()) {
            return direct;
        }
        return new File(".." + File.separator + relativePath);
    }
}
