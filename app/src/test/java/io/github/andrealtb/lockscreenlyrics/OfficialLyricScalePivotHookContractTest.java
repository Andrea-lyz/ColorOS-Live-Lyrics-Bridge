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
        String plugin = readWorkspaceFile(
                "PlayerSource/SystemUIPlugin/jadx-user/sources/com/oplus/systemui/plugins/shared/"
                        + "template/component/media/view/LyricsRecyclerView.java");

        assertTrue(plugin.contains("void k(AppCompatTextView appCompatTextView)"));
        assertTrue(plugin.contains("appCompatTextView.setPivotX(0.0f)"));
        assertTrue(plugin.contains("appCompatTextView.setScaleX"));
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String readWorkspaceFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File fromProject = new File(".." + File.separator + relativePath);
        File fromBuild = new File(".." + File.separator + ".." + File.separator + relativePath);
        File file = direct.isFile() ? direct : (fromProject.isFile() ? fromProject : fromBuild);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
