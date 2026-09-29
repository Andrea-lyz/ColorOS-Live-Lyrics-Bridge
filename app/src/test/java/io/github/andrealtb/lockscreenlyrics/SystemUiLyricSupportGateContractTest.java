package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public final class SystemUiLyricSupportGateContractTest {
    @Test
    public void gateEvidenceCoversBothBuildsThatCarryIt() throws Exception {
        String evidence = readProjectFile(
                "app/src/test/resources/fixtures/systemui-lyric-support-gate-evidence.txt");

        assertTrue(evidence.contains("versionCode=179902"));
        assertTrue(evidence.contains("versionCode=169912"));
        assertTrue(evidence.contains("getLyricEnable(pkg) != 1 -> true"));
        assertTrue(evidence.contains("builtInLyricEnable1=com.kugou.android.lite"));
    }

    @Test
    public void onlyModuleManagedPlayersGetTheGateLifted() throws Exception {
        String module = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java");
        int handler = module.indexOf("private Object onOplusMediaResolveLyricSupport(");
        int next = module.indexOf("private Object onOplusMediaUpdatePkgActionsRule(", handler);
        assertTrue(handler > 0 && next > handler);
        String body = module.substring(handler, next);

        assertTrue(body.contains("if (!Boolean.FALSE.equals(result)) {"));
        assertTrue(body.contains("if (!isModuleManagedPlayerPackage(packageName)) {"));
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
