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
        int romanizationIndex = findLikelyJapaneseRomanizationIndex(texts);
        if (romanizationIndex >= 0) {
            int cjkBeforeRomanization = firstCjkTextBefore(texts, romanizationIndex);
            if (cjkBeforeRomanization >= 0) {
                return cjkBeforeRomanization;
            }
            for (int index = 0; index < texts.size(); index++) {
                if (index != romanizationIndex && containsCjkScript(texts.get(index))) {
                    return index;
                }
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
        if ((!containsJapaneseScript(primary) && !containsJapaneseMarker(primary))
                || !isLikelyJapaneseRomanizationLine(candidate)) {
            return false;
        }
        return true;
    }

    static boolean isLikelyJapaneseRomanizationLine(String candidate) {
        String trimmed = candidate == null ? "" : candidate.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (!containsLatinLetter(trimmed) || containsCjkScript(trimmed)) {
            return false;
        }
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 3) {
            return false;
        }

        int latinTokens = 0;
        int syllableTokens = 0;
        int totalLetters = 0;
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
            totalLetters += letters;
            if (letters <= 3) {
                syllableTokens++;
            }
        }
        if (latinTokens < 3 || syllableTokens * 2 < latinTokens) {
            return false;
        }
        return totalLetters <= latinTokens * 3;
    }

    static boolean isLikelyJapaneseRomanizationVariant(
            List<String> texts,
            int primaryIndex,
            String candidate) {
        if (!isLikelyJapaneseRomanizationLine(candidate)) {
            return false;
        }
        String primary = primaryIndex >= 0 && primaryIndex < texts.size()
                ? nullToEmpty(texts.get(primaryIndex)).trim()
                : "";
        if (isLikelyJapaneseRomanization(primary, candidate)) {
            return true;
        }
        return containsCjkScript(primary)
                && hasOtherCjkTranslationCandidate(texts, primaryIndex, candidate);
    }

    static String findSharedTrailingLatinToken(List<String> texts, int primaryIndex) {
        if (texts == null || primaryIndex < 0 || primaryIndex >= texts.size()) {
            return "";
        }
        String primaryToken = trailingUpperLatinToken(texts.get(primaryIndex));
        if (!primaryToken.isEmpty()) {
            return "";
        }

        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (int index = 0; index < texts.size(); index++) {
            if (index == primaryIndex) {
                continue;
            }
            String token = trailingUpperLatinToken(texts.get(index));
            if (token.isEmpty()) {
                continue;
            }
            Integer count = counts.get(token);
            counts.put(token, count == null ? 1 : count + 1);
        }
        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= 2) {
                return entry.getKey();
            }
        }
        return "";
    }

    static String appendLatinSuffix(String text, String suffix) {
        String base = nullToEmpty(text).trim();
        String token = nullToEmpty(suffix).trim();
        if (base.isEmpty()
                || token.isEmpty()
                || trailingUpperLatinToken(base).equals(token)) {
            return base;
        }
        char last = base.charAt(base.length() - 1);
        return Character.isWhitespace(last) ? base + token : base + ' ' + token;
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

    private static int findLikelyJapaneseRomanizationIndex(List<String> texts) {
        for (int index = 0; index < texts.size(); index++) {
            if (isLikelyJapaneseRomanizationLine(texts.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static int firstCjkTextBefore(List<String> texts, int boundaryIndex) {
        for (int index = 0; index < boundaryIndex && index < texts.size(); index++) {
            if (containsCjkScript(texts.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static boolean containsJapaneseMarker(String text) {
        if (text == null) {
            return false;
        }
        for (int index = 0; index < text.length();) {
            int codePoint = text.codePointAt(index);
            if (codePoint == 0x3005
                    || codePoint == 0x30F6
                    || codePoint == 0x30F5
                    || codePoint == 0x309D
                    || codePoint == 0x309E
                    || codePoint == 0x30FD
                    || codePoint == 0x30FE) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean hasOtherCjkTranslationCandidate(
            List<String> texts,
            int primaryIndex,
            String romanizationCandidate) {
        for (int index = 0; index < texts.size(); index++) {
            if (index == primaryIndex) {
                continue;
            }
            String text = nullToEmpty(texts.get(index));
            if (text.equals(nullToEmpty(romanizationCandidate))) {
                continue;
            }
            if (containsCjkScript(text) && !isLikelyJapaneseRomanizationLine(text)) {
                return true;
            }
        }
        return false;
    }

    private static String trailingUpperLatinToken(String text) {
        String value = nullToEmpty(text).trim();
        if (value.isEmpty()) {
            return "";
        }
        int end = value.length();
        int start = end;
        while (start > 0) {
            char ch = value.charAt(start - 1);
            if ((ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '.'
                    || ch == '-'
                    || ch == '+') {
                start--;
                continue;
            }
            break;
        }
        if (start >= end) {
            return "";
        }
        String token = value.substring(start, end);
        int strongChars = 0;
        for (int index = 0; index < token.length(); index++) {
            char ch = token.charAt(index);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) {
                strongChars++;
            }
            if (ch >= 'a' && ch <= 'z') {
                return "";
            }
        }
        return strongChars >= 2 ? token : "";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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
