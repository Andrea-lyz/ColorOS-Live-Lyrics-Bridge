package io.github.andrealtb.lockscreenlyrics.guard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where each area of the SystemUI hook code lives (E-0 ②, plan §15.3). Source-text contract
 * tests read an area through this table instead of naming {@code LockscreenLyricsModule.java},
 * so moving an area out of the entry class (E-1 to E-7) only edits this table: the assertions
 * stay as they are. An area that spans several files is read as their concatenation, in order.
 */
public final class BridgeSourceMap {
    /** Areas follow the E-series slices that will move them. */
    public enum Area {
        /** E-1: the lyric text renderer, char lift, typeface and glow drawing. */
        RENDERER,
        /** E-2: system_server / OPlus media policy hooks and AOD support. */
        OPLUS_POLICY,
        /** E-4: translation action view tracking and redirect. */
        TRANSLATION_ACTION,
        /** E-5: plugin class loader and plugin media-info compatibility. */
        PLUGIN_COMPAT,
        /** E-6: LyricsRecyclerView hooks, fields and immersive entry. */
        RECYCLER,
        /** Config consumers across the hook side (the settings UI is read separately). */
        CONFIG_CONSUMERS,
        /** Every hook-side class the architecture guard must keep free of network/caches. */
        SYSTEMUI_RUNTIME,
    }

    public static final String SOURCE_ROOT = "app/src/main/java/io/github/andrealtb/lockscreenlyrics/";
    /** The entry class whose size the budget guard pins. */
    public static final String ENTRY_CLASS = SOURCE_ROOT + "LockscreenLyricsModule.java";

    private static final Map<Area, List<String>> FILES = new EnumMap<>(Area.class);

    static {
        // Everything still lives in the entry class (baseline 2026-11-15). Update a row when its
        // slice moves code; never edit the contract assertions to follow a move.
        for (Area area : Area.values()) FILES.put(area, Collections.singletonList(ENTRY_CLASS));
        // E-1 (2026-11-15): the renderer moved verbatim to its own file; the rest of the
        // renderer-facing code (row effects, slot heights, typeface learning) is still in the entry.
        FILES.put(Area.RENDERER, Arrays.asList(SOURCE_ROOT + "OfficialLyricTextRenderer.java", ENTRY_CLASS));
        FILES.put(Area.SYSTEMUI_RUNTIME, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "OfficialLyricTextRenderer.java"));
        FILES.put(Area.CONFIG_CONSUMERS, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "OfficialLyricTextRenderer.java"));
        // E-2d1 (2026-11-15): allow-list hooks moved to OplusMediaPolicyHooks; the translation
        // pkg-actions handler stays in the entry until E-4.
        FILES.put(Area.OPLUS_POLICY, Arrays.asList(SOURCE_ROOT + "OplusMediaPolicyHooks.java", ENTRY_CLASS));
        FILES.put(Area.SYSTEMUI_RUNTIME, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "OfficialLyricTextRenderer.java",
                SOURCE_ROOT + "OplusMediaPolicyHooks.java"));
        // E-3 (2026-11-15): screen-timeout keep-awake moved to ScreenTimeoutController; no contract
        // test reads it, but the runtime guard must cover the new file.
        FILES.put(Area.SYSTEMUI_RUNTIME, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "OfficialLyricTextRenderer.java",
                SOURCE_ROOT + "OplusMediaPolicyHooks.java", SOURCE_ROOT + "ScreenTimeoutController.java"));
        // E-4a (2026-11-15): translation action view tracking moved to TranslationActionViewTracker;
        // the View hooks that feed it (setVisibility redirect, content description) stay in the entry.
        FILES.put(Area.TRANSLATION_ACTION, Arrays.asList(SOURCE_ROOT + "TranslationActionViewTracker.java", ENTRY_CLASS));
        FILES.put(Area.SYSTEMUI_RUNTIME, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "OfficialLyricTextRenderer.java",
                SOURCE_ROOT + "OplusMediaPolicyHooks.java", SOURCE_ROOT + "ScreenTimeoutController.java",
                SOURCE_ROOT + "TranslationActionViewTracker.java"));
        // E-4b (2026-11-15): the pkg-actions rule hook, rule-0 sets and translation preference store
        // moved to TranslationActionController.
        FILES.put(Area.OPLUS_POLICY, Arrays.asList(SOURCE_ROOT + "OplusMediaPolicyHooks.java",
                SOURCE_ROOT + "TranslationActionController.java", ENTRY_CLASS));
        FILES.put(Area.TRANSLATION_ACTION, Arrays.asList(SOURCE_ROOT + "TranslationActionViewTracker.java",
                SOURCE_ROOT + "TranslationActionController.java", ENTRY_CLASS));
        FILES.put(Area.SYSTEMUI_RUNTIME, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "OfficialLyricTextRenderer.java",
                SOURCE_ROOT + "OplusMediaPolicyHooks.java", SOURCE_ROOT + "ScreenTimeoutController.java",
                SOURCE_ROOT + "TranslationActionViewTracker.java", SOURCE_ROOT + "TranslationActionController.java"));
        // E-5a (2026-11-15): the MediaController binding moved to SystemUiPlaybackController.
        FILES.put(Area.SYSTEMUI_RUNTIME, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "OfficialLyricTextRenderer.java",
                SOURCE_ROOT + "OplusMediaPolicyHooks.java", SOURCE_ROOT + "ScreenTimeoutController.java",
                SOURCE_ROOT + "TranslationActionViewTracker.java", SOURCE_ROOT + "TranslationActionController.java",
                SOURCE_ROOT + "SystemUiPlaybackController.java"));
        // E-6a (2026-11-15): the lyric RecyclerView / root-view registry moved to LyricViewRegistry.
        FILES.put(Area.RECYCLER, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "LyricViewRegistry.java"));
        FILES.put(Area.SYSTEMUI_RUNTIME, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "OfficialLyricTextRenderer.java",
                SOURCE_ROOT + "OplusMediaPolicyHooks.java", SOURCE_ROOT + "ScreenTimeoutController.java",
                SOURCE_ROOT + "TranslationActionViewTracker.java", SOURCE_ROOT + "TranslationActionController.java",
                SOURCE_ROOT + "SystemUiPlaybackController.java", SOURCE_ROOT + "LyricViewRegistry.java"));
        // E-6b (2026-11-15): the lyric surface mode state moved to LyricSurfaceModeController.
        FILES.put(Area.SYSTEMUI_RUNTIME, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "OfficialLyricTextRenderer.java",
                SOURCE_ROOT + "OplusMediaPolicyHooks.java", SOURCE_ROOT + "ScreenTimeoutController.java",
                SOURCE_ROOT + "TranslationActionViewTracker.java", SOURCE_ROOT + "TranslationActionController.java",
                SOURCE_ROOT + "SystemUiPlaybackController.java", SOURCE_ROOT + "LyricViewRegistry.java",
                SOURCE_ROOT + "LyricSurfaceModeController.java"));
        // E-7a (2026-11-15): the track handoff state moved to LyricSurfaceHandoffController.
        FILES.put(Area.SYSTEMUI_RUNTIME, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "OfficialLyricTextRenderer.java",
                SOURCE_ROOT + "OplusMediaPolicyHooks.java", SOURCE_ROOT + "ScreenTimeoutController.java",
                SOURCE_ROOT + "TranslationActionViewTracker.java", SOURCE_ROOT + "TranslationActionController.java",
                SOURCE_ROOT + "SystemUiPlaybackController.java", SOURCE_ROOT + "LyricViewRegistry.java",
                SOURCE_ROOT + "LyricSurfaceModeController.java", SOURCE_ROOT + "LyricSurfaceHandoffController.java"));
        // E-7b (2026-11-15): the active lyric line / renderer targets moved to ActiveLyricState.
        FILES.put(Area.SYSTEMUI_RUNTIME, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "OfficialLyricTextRenderer.java",
                SOURCE_ROOT + "OplusMediaPolicyHooks.java", SOURCE_ROOT + "ScreenTimeoutController.java",
                SOURCE_ROOT + "TranslationActionViewTracker.java", SOURCE_ROOT + "TranslationActionController.java",
                SOURCE_ROOT + "SystemUiPlaybackController.java", SOURCE_ROOT + "LyricViewRegistry.java",
                SOURCE_ROOT + "LyricSurfaceModeController.java", SOURCE_ROOT + "LyricSurfaceHandoffController.java",
                SOURCE_ROOT + "ActiveLyricState.java"));
        // E-5c (2026-11-15): the plugin ClassLoader generation moved to PluginHookGenerationManager;
        // the plugin ClassLoader hooks and KuWo plugin compatibility stay in the entry class.
        FILES.put(Area.PLUGIN_COMPAT, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "PluginHookGenerationManager.java"));
        FILES.put(Area.SYSTEMUI_RUNTIME, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "OfficialLyricTextRenderer.java",
                SOURCE_ROOT + "OplusMediaPolicyHooks.java", SOURCE_ROOT + "ScreenTimeoutController.java",
                SOURCE_ROOT + "TranslationActionViewTracker.java", SOURCE_ROOT + "TranslationActionController.java",
                SOURCE_ROOT + "SystemUiPlaybackController.java", SOURCE_ROOT + "LyricViewRegistry.java",
                SOURCE_ROOT + "LyricSurfaceModeController.java", SOURCE_ROOT + "LyricSurfaceHandoffController.java",
                SOURCE_ROOT + "ActiveLyricState.java", SOURCE_ROOT + "PluginHookGenerationManager.java"));
        // F-1a/F-1b (2026-11-15): static recycler helpers -> LyricsRecyclerViews; recycler position and
        // animation state -> LyricsRecyclerState.
        FILES.put(Area.RECYCLER, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "LyricViewRegistry.java",
                SOURCE_ROOT + "LyricsRecyclerViews.java", SOURCE_ROOT + "LyricsRecyclerState.java"));
        FILES.put(Area.SYSTEMUI_RUNTIME, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "OfficialLyricTextRenderer.java",
                SOURCE_ROOT + "OplusMediaPolicyHooks.java", SOURCE_ROOT + "ScreenTimeoutController.java",
                SOURCE_ROOT + "TranslationActionViewTracker.java", SOURCE_ROOT + "TranslationActionController.java",
                SOURCE_ROOT + "SystemUiPlaybackController.java", SOURCE_ROOT + "LyricViewRegistry.java",
                SOURCE_ROOT + "LyricSurfaceModeController.java", SOURCE_ROOT + "LyricSurfaceHandoffController.java",
                SOURCE_ROOT + "ActiveLyricState.java", SOURCE_ROOT + "PluginHookGenerationManager.java",
                SOURCE_ROOT + "LyricsRecyclerViews.java", SOURCE_ROOT + "LyricsRecyclerState.java",
                SOURCE_ROOT + "ActiveLyricRefreshSchedule.java", SOURCE_ROOT + "KuWoSystemUiCompat.java",
                SOURCE_ROOT + "LyricMetadataSupport.java"));
        // F-2a (2026-11-15): KuWo SystemUI / plugin media-model compatibility moved to KuWoSystemUiCompat.
        FILES.put(Area.PLUGIN_COMPAT, Arrays.asList(ENTRY_CLASS, SOURCE_ROOT + "PluginHookGenerationManager.java",
                SOURCE_ROOT + "KuWoSystemUiCompat.java"));
    }

    private BridgeSourceMap() {
    }

    public static List<String> files(Area area) {
        return FILES.get(area);
    }

    public static String read(Area area) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String path : FILES.get(area)) text.append(readProjectFile(path)).append('\n');
        return text.toString();
    }

    public static String readProjectFile(String relativePath) throws IOException {
        return new String(Files.readAllBytes(locate(relativePath).toPath()), StandardCharsets.UTF_8);
    }

    /** Unit tests run from the module directory; paths in this table are repository-relative. */
    public static File locate(String relativePath) {
        File direct = new File(relativePath);
        if (direct.exists()) return direct;
        return new File(".." + File.separator + relativePath);
    }

    /** Repository root (the directory holding settings.gradle.kts). */
    public static File repositoryRoot() {
        for (File dir : Arrays.asList(new File("."), new File(".."))) {
            if (new File(dir, "settings.gradle.kts").isFile()) return dir;
        }
        return new File("..");
    }
}
