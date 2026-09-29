package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OfficialImmersiveLyricEntryContractTest {
    @Test
    public void immersiveEntryIsObservedAndOnlyItsPositionChanges() throws Exception {
        String module = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java");

        assertTrue(module.contains("OfficialImmersiveLyricEntryPolicy.matches("));
        assertTrue(module.contains("immersiveEntryMethods="));
        assertTrue(module.contains("ImmersiveLyricsModelAccess.withIndex(model, snapshot.lines, targetIndex)"));
        assertTrue(module.contains("IMMERSIVE_ENTRY_INDEXES.put(recycler"));
        // The module never calls the vendor entry itself; it only replaces the model argument.
        assertFalse(module.contains("invoke(recycler, aligned"));
    }

    @Test
    public void officialRowStyleResetsTheCachedBridgeBlur() throws Exception {
        String module = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java");

        assertTrue(module.contains("VIEW_LYRIC_RENDER_EFFECT_MODE.remove(row);"));
        assertTrue(module.contains("VIEW_BLUR_DISABLED.remove(row);"));
    }

    @Test
    public void coloros17EvidenceDocumentsEntryModelAndLookup() throws Exception {
        String evidence = readProjectFile(
                "app/src/test/resources/fixtures/official-lyrics-immersive-entry-evidence-c17.txt");

        assertTrue(evidence.contains("entry=public final void o("));
        assertTrue(evidence.contains("public h(List list, int i)"));
        assertTrue(evidence.contains("lookup=com/heytap/log/strategy/f.java o(List list, long j, int i)"));
        assertTrue(evidence.contains("rowBlur=LyricsRecyclerView.h()"));
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
