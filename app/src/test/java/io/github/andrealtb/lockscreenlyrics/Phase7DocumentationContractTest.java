package io.github.andrealtb.lockscreenlyrics;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class Phase7DocumentationContractTest {
    @Test
    public void activeIntegrationGuidesDescribeTheNativeContract() throws Exception {
        String english = readProjectFile("docs/PLAYER_INTEGRATION.md");
        String chinese = readProjectFile("docs/PLAYER_INTEGRATION.zh-CN.md");

        for (String field : new String[]{
                "songName", "artist", "songId", "lyric", "rawLyric",
                "translationLyric", "trackKey", "sessionGeneration"
        }) {
            assertTrue(english.contains(field));
            assertTrue(chinese.contains(field));
        }
        assertTrue(english.contains("MediaMetadata[\"lyricInfo\"]"));
        assertTrue(chinese.contains("MediaMetadata[\"lyricInfo\"]"));
        assertTrue(english.contains(LyricInfoContract.ACTION_TOGGLE_TRANSLATION));
        assertTrue(chinese.contains(LyricInfoContract.ACTION_TOGGLE_TRANSLATION));
        assertTrue(english.contains(LyricInfoContract.MANIFEST_METADATA_OPLUS_MEDIA_HISTORY));
        assertTrue(chinese.contains(LyricInfoContract.MANIFEST_METADATA_OPLUS_MEDIA_HISTORY));
        assertTrue(english.contains("Provider adaptation guide"));
        assertTrue(chinese.contains("Provider 适配技术指南"));
    }

    @Test
    public void migrationAndReadmesOwnTheBreakingUpgradeBoundary() throws Exception {
        String englishReadme = readProjectFile("README.md");
        String chineseReadme = readProjectFile("README.zh-CN.md");
        String englishMigration = readProjectFile("docs/4.0/MIGRATION-3.8-TO-4.0.md");
        String chineseMigration = readProjectFile("docs/4.0/MIGRATION-3.8-TO-4.0.zh-CN.md");

        assertTrue(englishReadme.contains("MIGRATION-3.8-TO-4.0.md"));
        assertTrue(chineseReadme.contains("MIGRATION-3.8-TO-4.0.zh-CN.md"));
        assertTrue(englishMigration.contains("io.github.proify.lyricon.cmprovider"));
        assertTrue(chineseMigration.contains("io.github.proify.lyricon.cmprovider"));
        assertTrue(englishMigration.contains("io.github.andrealtb.coloroslyrics.provider.netease"));
        assertTrue(chineseMigration.contains("io.github.andrealtb.coloroslyrics.provider.netease"));
        assertTrue(englishMigration.contains("tomakino/LyricProvider"));
        assertTrue(chineseMigration.contains("tomakino/LyricProvider"));
        assertFalse(englishReadme.matches("(?is).*(npatch|non-root).*"));
        assertFalse(chineseReadme.matches("(?is).*(npatch|non-root).*"));
    }

    @Test
    public void releaseDocumentsUseTheNewRepositoryAndCanonicalAssets() throws Exception {
        String process = readProjectFile("docs/RELEASE_PROCESS.md");
        String notes = readProjectFile(".github/release-notes/4.1.0.md");
        String archive = readProjectFile("docs/releases/v4.1.0.md");

        assertTrue(process.contains("Andrea-lyz/ColorOS-Live-Lyrics-Providers"));
        assertTrue(process.contains("mode=rc"));
        assertTrue(process.contains("16"));
        assertFalse(process.contains("Andrea-lyz/LyricProvider"));
        assertTrue(notes.contains("## 中文"));
        assertTrue(notes.contains("## English"));
        assertTrue(notes.contains("libxposed API 102"));
        assertTrue(notes.contains("ColorOS-Live-Lyrics-Provider-Salt-v4.1.0.apk"));
        assertTrue(notes.contains("ColorOS-Live-Lyrics-Provider-QiShui-v4.1.0.apk"));
        assertTrue(notes.contains("release-assets-v4.1.0.json"));
        assertTrue(notes.contains("SHA256SUMS"));
        assertFalse(notes.matches("(?is).*(npatch|non-root).*"));
        assertTrue(archive.contains("137-4.1.0"));
    }

    private static String readProjectFile(String relativePath) throws Exception {
        File direct = new File(relativePath);
        File file = direct.isFile()
                ? direct
                : new File(".." + File.separator + relativePath);
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }
}
