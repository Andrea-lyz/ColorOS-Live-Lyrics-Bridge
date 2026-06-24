package io.github.andrealtb.lockscreenlyrics;

import java.util.List;

/** Selects the source line from same-timestamp multilingual lyric variants. */
final class LyricLineVariantSelector {
    private LyricLineVariantSelector() {
    }

    static int findPrimaryTextIndex(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return 0;
        }

        // Japanese enhanced LRC commonly has source Japanese, Chinese translation, and
        // Latin-script romaji at one timestamp. Romaji must not win the general
        // Latin-priority rule used for English + Chinese lyrics.
        for (int index = 0; index < texts.size(); index++) {
            if (containsJapaneseScript(texts.get(index))) {
                return index;
            }
        }
        for (int index = 0; index < texts.size(); index++) {
            if (containsLatinLetter(texts.get(index))) {
                return index;
            }
        }
        for (int index = 0; index < texts.size(); index++) {
            String text = texts.get(index);
            if (text != null && !text.trim().isEmpty()) {
                return index;
            }
        }
        return 0;
    }

    static boolean isLikelyJapaneseRomanization(String primary, String candidate) {
        if (!containsJapaneseScript(primary)
                || !containsLatinLetter(candidate)
                || containsCjkScript(candidate)) {
            return false;
        }

        String trimmed = candidate == null ? "" : candidate.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 3) {
            return false;
        }

        int latinTokens = 0;
        int syllableTokens = 0;
        for (String token : tokens) {
            int letters = 0;
            for (int index = 0; index < token.length(); index++) {
                char value = token.charAt(index);
                if ((value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z')) {
                    letters++;
                }
            }
            if (letters == 0) {
                continue;
            }
            latinTokens++;
            if (letters <= 3) {
                syllableTokens++;
            }
        }
        return latinTokens >= 3 && syllableTokens * 2 >= latinTokens;
    }

    static boolean containsJapaneseScript(String text) {
        if (text == null) {
            return false;
        }
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean containsCjkScript(String text) {
        if (text == null) {
            return false;
        }
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL
                    || script == Character.UnicodeScript.BOPOMOFO) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean containsLatinLetter(String text) {
        if (text == null) {
            return false;
        }
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if ((value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z')) {
                return true;
            }
        }
        return false;
    }
}
