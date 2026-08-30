package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class BridgeConfigBackupContractTest {
    @Test
    public void globalBackupLivesOnItsOwnMainPageEntryNotTheVisualSubpage() throws Exception {
        String main = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/LyricUiSettingsActivity.java");
        String visual = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/"
                        + "LyricVisualLayersSettingsActivity.java");
        String manifest = readProjectFile("app/src/main/AndroidManifest.xml");

        assertTrue(main.contains("BridgeConfigBackupSettingsActivity.class"));
        assertTrue(main.contains("R.string.link_config_backup"));
        assertTrue(manifest.contains(".BridgeConfigBackupSettingsActivity"));
        assertFalse(visual.contains("BridgeConfigBackupRepository"));
        assertFalse(visual.contains("config_backup_"));
    }

    @Test
    public void repositoryCoversEveryBridgePreferenceDomain() throws Exception {
        String repository = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/"
                        + "BridgeConfigBackupRepository.java");

        assertTrue(repository.contains("LyricUiSettings.PREFERENCES_NAME"));
        assertTrue(repository.contains("BridgeDebugConfig.PREFS_NAME"));
        assertTrue(repository.contains("restoreAll"));
        assertTrue(repository.contains("clearAll"));
        assertTrue(repository.contains("previous = snapshot(context)"));
    }

    @Test
    public void restoreReplaysEveryRuntimeConfigurationChannel() throws Exception {
        String sync = readProjectFile(
                "app/src/main/java/io/github/andrealtb/lockscreenlyrics/"
                        + "BridgeConfigRuntimeSync.java");

        assertTrue(sync.contains("ACTION_STYLE_CHANGED"));
        assertTrue(sync.contains("ACTION_PLAYER_TRANSLATION_SETTINGS_CHANGED"));
        assertTrue(sync.contains("ACTION_CONTENT_CLEANUP_CHANGED"));
        assertTrue(sync.contains("ACTION_DEBUG_SETTINGS_CHANGED"));
        assertTrue(sync.contains("SOURCE_CONFIG_BACKUP"));
    }

    @Test
    public void bothLocalesDescribeFullScopeRestoreAndDestructiveReset() throws Exception {
        String chinese = readProjectFile("app/src/main/res/values/strings.xml");
        String english = readProjectFile("app/src/main/res/values-en/strings.xml");
        String[] keys = {
                "config_backup_title",
                "config_backup_scope_body",
                "config_backup_restore_confirm_message",
                "config_backup_reset_confirm_message"
        };
        for (String key : keys) {
            assertTrue(chinese.contains("name=\"" + key + "\""));
            assertTrue(english.contains("name=\"" + key + "\""));
        }
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
