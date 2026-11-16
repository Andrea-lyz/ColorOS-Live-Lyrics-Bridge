package io.github.andrealtb.lockscreenlyrics.management

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Every store item has a formal "<player> Provider" name in Chinese and English, and its icon. */
class StoreNamesTest {
    private fun names(folder: String): Map<String, String> {
        val file = listOf(File("src/main/res/$folder/strings.xml"), File("app/src/main/res/$folder/strings.xml"))
            .firstOrNull { it.isFile }
        assumeTrue("$folder/strings.xml visible", file != null)
        return Regex("<string name=\"(store_name_[a-z]+)\"[^>]*>([^<]+)</string>").findAll(file!!.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test fun everyDirectoryEntryHasADistinctFormalName() {
        val ids = ProviderDirectory.entries.map { it.nameRes }
        assertTrue(ids.none { it == 0 })
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun everyProviderWithItsOwnLauncherIconShowsIt() {
        // Readify's module uses a platform icon, so it alone shows the generic one.
        val missing = ProviderDirectory.entries.filter { it.iconRes == 0 }.map { it.module }
        assertEquals(listOf("player-readify"), missing)
        val icons = ProviderDirectory.entries.map { it.iconRes }.filter { it != 0 }
        assertEquals(icons.size, icons.toSet().size)
    }

    @Test fun chineseAndEnglishNamesAreFormal() {
        val zh = names("values")
        val en = zh + names("values-en")
        assertEquals(ProviderDirectory.entries.size, zh.size)
        for ((key, value) in zh + en) assertTrue("$key = $value", value.endsWith(" Provider"))
        assertEquals("洛雪音乐 Provider", zh["store_name_lx"])
        assertEquals("LX Music Provider", en["store_name_lx"])
        assertEquals("Salt Player Provider", en["store_name_salt"])
    }
}
