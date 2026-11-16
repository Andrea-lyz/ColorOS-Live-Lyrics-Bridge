package io.github.andrealtb.lockscreenlyrics.management.catalog

/** Release builds never trust a test catalog key (see the debug source set). */
internal object StoreTestTrust {
    val keys: Map<String, String> = emptyMap()
}
