package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public final class OfficialLyricsRecyclerFieldsContractTest {
    @Test
    public void mutableRowCountersNeverCompeteWithTheFinalLineSpacing() throws Exception {
        String resolver = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/OfficialLyricsRecyclerDexKitResolver.java");

        assertTrue(resolver.contains("preferFinalFields(spacingCandidates)"));
        assertTrue(resolver.contains("Modifier.isFinal(candidate.getModifiers())"));
    }

    @Test
    public void currentRowFieldIsWrittenByTheEntryAndReadByTheRowRestyleMethod() throws Exception {
        String resolver = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/OfficialLyricsRecyclerDexKitResolver.java");
        String module = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java");

        assertTrue(resolver.contains("OfficialImmersiveLyricEntryPolicy.matches(method, recyclerClass)"));
        assertTrue(resolver.contains("OfficialLyricRowStyleMethodPolicy.matches(method, recyclerClass)"));
        assertTrue(resolver.contains("field.getWriters()"));
        // The official row wins over the row the module last sent through the entry.
        int fieldRead = module.indexOf("Integer immersiveIndex = readImmersiveCurrentIndexField(recycler);");
        int recordRead = module.indexOf("ImmersiveEntryIndex entry = IMMERSIVE_ENTRY_INDEXES.get(recycler);");
        assertTrue(fieldRead > 0 && recordRead > fieldRead);
        assertTrue(module.contains("rememberLyricsRecyclerTargetIndex(observedTargetIndex);"));
    }

    @Test
    public void coloros17EvidenceDocumentsFieldRolesAndTheirReaders() throws Exception {
        String evidence = readProjectFile(
                "app/src/test/resources/fixtures/official-lyrics-recycler-fields-evidence-c17.txt");

        assertTrue(evidence.contains("apkVersionCode=17000002"));
        assertTrue(evidence.contains("spacing=public final int j;"));
        assertTrue(evidence.contains("currentIndex=public int c;"));
        assertTrue(evidence.contains("never by g(m, int)"));
        assertTrue(evidence.contains("bindReadsInt=n.onBindViewHolder reads j, c, u, d, e"));
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
