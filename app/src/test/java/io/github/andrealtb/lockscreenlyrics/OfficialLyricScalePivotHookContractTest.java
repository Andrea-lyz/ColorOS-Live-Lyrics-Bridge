package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OfficialLyricScalePivotHookContractTest {
    @Test
    public void pivotHookUsesStableMethodShapeInsteadOfObfuscatedName() throws Exception {
        String module = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java");

        assertTrue(module.contains(
                "OfficialLyricScalePivotMethodPolicy.textViewArgumentIndex(method)"));
        assertTrue(module.contains("chain.getArg(textViewIndex)"));
        assertTrue(module.contains("currentMethods="));
        assertTrue(module.contains("pivotMethods="));
        assertFalse(module.contains("isVerifiedOfficialScalePivotMethodName"));
    }

    @Test
    public void coloros17PluginEvidenceShowsStaticHelperOnEveryLayout() throws Exception {
        String evidence = readProjectFile(
                "app/src/test/resources/fixtures/official-lyrics-recycler-pivot-evidence-c17.txt");

        assertTrue(evidence.contains("apkVersionCode=17000002"));
        assertTrue(evidence.contains(
                "sourceSha256=149E49653C882478A080911C62A0A5720B12F6D22652C62C947ACEA40E78BD69"));
        assertTrue(evidence.contains("public static final void d(LyricsRecyclerView lyricsRecyclerView,"
                + " AppCompatTextView appCompatTextView)"));
        assertTrue(evidence.contains("layoutListenerCall="));
    }

    @Test
    public void verifiedPluginEvidenceShowsNameDriftAndLeftPivotReset() throws Exception {
        String evidence = readProjectFile(
                "app/src/test/resources/fixtures/official-lyrics-recycler-pivot-evidence.txt");

        assertTrue(evidence.contains(
                "sourceSha256=09F27293E450AC517F03C020D6912C1EC0682C58895F4F5EEBB761D7D779B664"));
        assertTrue(evidence.contains("void k(AppCompatTextView appCompatTextView)"));
        assertTrue(evidence.contains("appCompatTextView.setPivotX(0.0f)"));
        assertTrue(evidence.contains("appCompatTextView.setScaleX"));
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

}
