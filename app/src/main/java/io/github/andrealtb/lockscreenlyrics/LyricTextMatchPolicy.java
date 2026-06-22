package io.github.andrealtb.lockscreenlyrics;

final class LyricTextMatchPolicy {
    private LyricTextMatchPolicy() {
    }

    static boolean hasSubstantialPrefix(String visibleText, String fullText) {
        return hasSubstantialPrefix(
                visibleText,
                fullText,
                matchKey(visibleText),
                matchKey(fullText));
    }

    static boolean hasSubstantialPrefix(
            String visibleText,
            String fullText,
            String visibleKey,
            String fullKey) {
        if (visibleKey.length() < 5
                || fullKey.length() < visibleKey.length()
                || visibleKey.length() * 5 < fullKey.length() * 3) {
            return false;
        }
        return fullText.startsWith(visibleText) || fullKey.startsWith(visibleKey);
    }

    private static String matchKey(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder key = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                key.append(Character.toLowerCase(character));
            }
        }
        return key.toString();
    }
}
