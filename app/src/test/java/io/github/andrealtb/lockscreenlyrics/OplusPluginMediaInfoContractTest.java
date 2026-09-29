package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public final class OplusPluginMediaInfoContractTest {
    @Test
    public void adapterFindsTheColorOs16AndColorOs17LyricModel() throws Exception {
        String adapter = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/OplusPluginDexKitAdapter.java");
        String evidence = readProjectFile(
                "app/src/test/resources/fixtures/oplus-plugin-media-info-evidence-c17.txt");

        assertTrue(adapter.contains(
                "\"MediaModel(uniqueId=\", \", lyricModel=\", \", isLyricSupported=\""));
        assertTrue(adapter.contains(
                "\"MediaInfo(uniqueId=\", \", lyricModel=\", \", isLyricSupported=\""));
        assertTrue(evidence.contains("apkVersionCode=17000002"));
        assertTrue(evidence.contains("\"MediaModel(uniqueId=\" is used by no class"));
        // One constructor, so the model hook runs once per MediaInfo.
        assertTrue(evidence.contains("constructors=1 "));
    }

    @Test
    public void coloros17EvidencePutsAnotherBooleanAfterTheLyricFlag() throws Exception {
        String evidence = readProjectFile(
                "app/src/test/resources/fixtures/oplus-plugin-media-info-evidence-c17.txt");
        String module = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java");

        assertTrue(evidence.contains("p:s q:Z r:a s:Z t:Object"));
        assertTrue(evidence.contains("s artworkFullBgEnable (the last boolean)"));
        // Every flag write is checked against the model label and undone when it misses.
        assertTrue(module.contains("supportedField.setBoolean(model, previous);"));
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
