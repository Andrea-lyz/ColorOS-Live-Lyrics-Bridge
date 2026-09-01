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
            for (String marker : FORBIDDEN_V4_MARKERS) {
                assertFalse(file + " contains forbidden Phase 5 marker " + marker,
                        source.contains(marker));
            }
        }
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
