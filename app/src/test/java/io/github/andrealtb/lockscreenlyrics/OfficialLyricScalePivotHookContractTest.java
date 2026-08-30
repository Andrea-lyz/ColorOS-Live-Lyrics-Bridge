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

        assertTrue(module.contains("parameterTypes.length == 1"));
        assertTrue(module.contains("TextView.class.isAssignableFrom(parameterTypes[0])"));
        assertTrue(module.contains("currentMethods="));
        assertTrue(module.contains("pivotMethods="));
        assertFalse(module.contains("isVerifiedOfficialScalePivotMethodName"));
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
