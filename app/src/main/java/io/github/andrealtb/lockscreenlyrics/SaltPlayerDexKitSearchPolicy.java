package io.github.andrealtb.lockscreenlyrics;

/**
 * Runtime package roots used by Salt Player for its obfuscated lyric model.
 *
 * <p>Salt 12.0.x placed the model under {@code androidx.obf}. Starting with 12.2.0-alpha02,
 * the same model and final publication coroutine are relocated under {@code androidx.media3}.
 * DexKit still resolves the concrete classes from their structural markers.</p>
 */
final class SaltPlayerDexKitSearchPolicy {
    private static final String[] LYRIC_MODEL_PACKAGES = {
            "androidx.obf",
            "androidx.media3"
    };

    private SaltPlayerDexKitSearchPolicy() {
    }

    static String[] lyricModelPackages() {
        return LYRIC_MODEL_PACKAGES.clone();
    }
}
