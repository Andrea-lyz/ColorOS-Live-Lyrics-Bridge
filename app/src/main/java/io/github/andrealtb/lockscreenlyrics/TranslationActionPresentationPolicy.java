package io.github.andrealtb.lockscreenlyrics;

/** Shared model/view presentation for the media-card translation action. */
final class TranslationActionPresentationPolicy {
    static final String CANONICAL_ICON_PACKAGE = "io.github.andrealtb.lockscreenlyrics";
    static final int ENABLED_IMAGE_ALPHA = 255;
    static final int DISABLED_IMAGE_ALPHA = 135;
    static final float ACTION_ICON_INSET_FRACTION = 0.175f;
    private static final String DESCRIPTION_PREFIX = "翻译：";

    private TranslationActionPresentationPolicy() {
    }

    static int imageAlpha(boolean enabled) {
        return enabled ? ENABLED_IMAGE_ALPHA : DISABLED_IMAGE_ALPHA;
    }

    static String contentDescription(boolean enabled) {
        return DESCRIPTION_PREFIX + (enabled ? "开启" : "关闭");
    }
}
