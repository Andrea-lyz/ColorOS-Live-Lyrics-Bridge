package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class OplusPkgActionsRuleReplayContractTest {
    @Test
    public void bridgeReplaysNeverReplaceTheRawVendorSnapshot() throws Exception {
        String module = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java");
        int hook = module.indexOf("private Object onOplusMediaUpdatePkgActionsRule(");
        int guard = module.indexOf("if (Boolean.TRUE.equals(replayingOplusPkgActionsRule.get())) {", hook);
        int snapshot = module.indexOf(
                "rawOplusPkgActionsRuleArgs = TranslationActionRulePolicy.snapshotRawArgs(rawArgs);", hook);

        assertTrue(hook > 0 && guard > hook && snapshot > guard);
        // Every Bridge call of the vendor method goes through the guarded replay helper.
        assertEquals(1, count(module, "updateMethod.invoke("));
        assertTrue(module.contains("replayOplusPkgActionsRule(updateMethod, selector, cleanArgs);"));
        assertTrue(module.contains("replayOplusPkgActionsRule(updateMethod, selector, refreshArgs);"));
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
