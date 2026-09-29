package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public final class TranslationActionRedirectContractTest {
    @Test
    public void coloros17BindsVisibilityBeforeTheNewLabel() throws Exception {
        String evidence = readProjectFile(
                "app/src/test/resources/fixtures/translation-action-bind-order-evidence-c17.txt");

        assertTrue(evidence.contains("apkVersionCode=17000002"));
        assertTrue(evidence.contains("view.setVisibility(icon present ? 0 : 8); I(view, action.b, play);"
                + " view.setContentDescription(action.g)"));
        assertTrue(evidence.contains("no setVisibility in the same pass"));
    }

    @Test
    public void reboundSlotGetsItsRedirectedVisibilityBack() throws Exception {
        String module = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LockscreenLyricsModule.java");
        int forget = module.indexOf("private void forgetTranslationActionViewIfRebound(View view) {");
        int restore = module.indexOf("restoreRedirectedTranslationActionVisibility(view);", forget);
        int forgotten = module.indexOf("forgetTranslationActionView(view);", forget);

        assertTrue(forget > 0);
        // Forget first so the restoring write is not redirected again.
        assertTrue(forgotten > forget && restore > forgotten);
        assertTrue(module.contains("translationActionRedirectedViews.put(view, Boolean.TRUE);"));
        assertTrue(module.contains("translationActionRedirectedViews.remove(view);"));
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
