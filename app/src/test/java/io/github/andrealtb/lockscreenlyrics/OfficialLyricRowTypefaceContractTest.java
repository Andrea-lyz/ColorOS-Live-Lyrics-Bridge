package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OfficialLyricRowTypefaceContractTest {
    @Test
    public void measurementAndDrawingShareOneOfficialTypeface() throws Exception {
        String module = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java");

        assertFalse(module.contains("resolveConfiguredTypeface(textView.getTypeface())"));
        assertEquals(2, count(module, "resolveConfiguredTypeface(officialTypefaceOf(textView))"));
        assertTrue(module.contains("OfficialLyricRowStyleMethodPolicy.matches("));
        assertTrue(module.contains("rowStyleMethods="));
        assertTrue(module.contains("redrawLyricRenderTargets(false, true)"));
    }

    @Test
    public void coloros17PluginEvidenceShowsTypefaceSwapPerRowState() throws Exception {
        String evidence = readProjectFile(
                "app/src/test/resources/fixtures/official-lyrics-row-style-evidence-c17.txt");

        assertTrue(evidence.contains("apkVersionCode=17000002"));
        assertTrue(evidence.contains(
                "signature=public final void h(AppCompatTextView appCompatTextView, boolean z)"));
        assertTrue(evidence.contains("inactiveTypeface=appCompatTextView.setTypeface(D)"));
        assertTrue(evidence.contains("activeTypeface=appCompatTextView.setTypeface(C)"));
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int index = text.indexOf(needle); index >= 0; index = text.indexOf(needle, index + 1)) {
            count++;
        }
        return count;
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
