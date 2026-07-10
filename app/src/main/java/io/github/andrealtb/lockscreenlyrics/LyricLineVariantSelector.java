package io.github.andrealtb.lockscreenlyrics;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Selects the source line from same-timestamp multilingual lyric variants. */
final class LyricLineVariantSelector {
    private static final Set<String> CJK_PHONETIC_LATIN_TOKENS =
            new HashSet<>(Arrays.asList(
                    "ai", "ba", "bat", "bin", "bing", "bi", "bou", "bun",
                    "can", "cai", "ci", "cin", "coeng", "cui", "cun", "cung", "cvn",
                    "da", "dan", "dang", "di", "dong", "dou", "doi",
                    "fong", "fu", "ga", "gai", "gei", "gi", "gou", "gwo",
                    "ha", "ho", "hoi", "hong", "hou", "lo", "loe", "loeng",
                    "loi", "long", "lou", "lui", "lvn", "man", "mei",
                    "min", "miu", "mong", "mu", "nang", "nei", "ng", "o",
                    "oi", "san", "se", "si", "sing", "soeng", "sou", "sv",
                    "tong", "wa", "wai", "wing", "wui", "wun", "ya", "yau",
                    "yi", "yiu", "yong", "you", "yung", "zai", "zi", "ziu",
                    "zoi", "zong", "zou", "zui"));
    private static final Set<String> COMMON_ENGLISH_LYRIC_TOKENS =
            new HashSet<>(Arrays.asList(
                    "a", "am", "an", "and", "are", "be", "but", "cause",
                    "coz", "did", "do", "dont", "dream", "false", "for", "had",
                    "has", "hate", "have", "he", "her", "him", "his", "how",
                    "i", "in", "is", "it", "just", "love", "me", "my", "not",
                    "now", "of", "on", "over", "real", "saw", "she", "that", "the",
                    "they", "this",
                    "to", "was", "we", "were", "what", "when", "where", "who",
                    "why", "with", "yeah", "you", "your", "blind"));

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
        if (romanizationIndex >= 0
                && isLikelyWesternLyricLine(texts.get(romanizationIndex))
                && hasOtherCjkTranslationCandidate(texts, romanizationIndex, texts.get(romanizationIndex))) {
            romanizationIndex = -1;
        }
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
        int cjkPhoneticIndex = findLikelyCjkPhoneticVariantIndex(texts);
        if (cjkPhoneticIndex >= 0) {
            int cjkBeforePhonetic = firstCjkTextBefore(texts, cjkPhoneticIndex);
            if (cjkBeforePhonetic >= 0) {
                return cjkBeforePhonetic;
            }
            int cjkIndex = firstCjkTextIndex(texts);
            if (cjkIndex >= 0) {
                return cjkIndex;
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
        if (!hasRomajiSyllableDensity(latinTokens, syllableTokens)) {
            return false;
        }
        return hasRomajiTokenLengthProfile(latinTokens, syllableTokens, totalLetters);
    }

    private static boolean hasRomajiSyllableDensity(int latinTokens, int syllableTokens) {
        return latinTokens >= 3 && syllableTokens * 3 >= latinTokens * 2;
    }

    private static boolean hasRomajiTokenLengthProfile(
            int latinTokens,
            int syllableTokens,
            int totalLetters) {
        if (latinTokens < 6) {
            return latinTokens >= 4
                    && syllableTokens == latinTokens
                    && totalLetters * 2 <= latinTokens * 4;
        }
        return totalLetters * 2 <= latinTokens * 5;
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

    static boolean isLikelyPhoneticVariant(
            List<String> texts,
            int primaryIndex,
            String candidate) {
        String primary = primaryIndex >= 0 && texts != null && primaryIndex < texts.size()
                ? nullToEmpty(texts.get(primaryIndex)).trim()
                : "";
        return isLikelyJapaneseRomanizationVariant(texts, primaryIndex, candidate)
                || (containsCjkScript(primary) && isLikelyJapaneseRomanizationLine(candidate))
                || isLikelyCjkPhoneticVariant(texts, primaryIndex, candidate);
    }

    static boolean isLikelyCjkPhoneticVariant(
            List<String> texts,
            int primaryIndex,
            String candidate) {
        if (texts == null || primaryIndex < 0 || primaryIndex >= texts.size()) {
            return false;
        }
        String primary = nullToEmpty(texts.get(primaryIndex)).trim();
        String value = nullToEmpty(candidate).trim();
        if (primary.isEmpty()
                || value.isEmpty()
                || !containsCjkScript(primary)
                || !containsLatinLetter(value)
                || containsCjkScript(value)
                || hasOnlyCommonEnglishLyricTokens(value)) {
            return false;
        }

        Set<String> primaryLatinTokens = latinTokenSet(primary);
        int checkedTokens = 0;
        int phoneticTokens = 0;
        for (String token : latinTokens(value)) {
            if (primaryLatinTokens.contains(token)) {
                continue;
            }
            checkedTokens++;
            if (isLikelyCjkPhoneticToken(token)) {
                phoneticTokens++;
            }
        }
        if (checkedTokens < 2 || phoneticTokens < 2) {
            return false;
        }
        return phoneticTokens * 3 >= checkedTokens * 2;
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

    private static boolean isLikelyWesternLyricLine(String text) {
        String value = nullToEmpty(text).trim();
        if (value.isEmpty()
                || !containsLatinLetter(value)
                || containsCjkScript(value)
                || containsJapaneseMarker(value)) {
            return false;
        }
        List<String> tokens = latinTokens(value);
        if (tokens.size() < 2) {
            return false;
        }
        int commonTokens = 0;
        int commonWordTokens = 0;
        int repeatedTokens = 0;
        java.util.HashMap<String, Integer> counts = new java.util.HashMap<>();
        for (String token : tokens) {
            if (COMMON_ENGLISH_LYRIC_TOKENS.contains(token)) {
                commonTokens++;
                if (token.length() > 1) {
                    commonWordTokens++;
                }
            }
            Integer count = counts.get(token);
            counts.put(token, count == null ? 1 : count + 1);
        }
        for (Integer count : counts.values()) {
            if (count != null && count >= 2) {
                repeatedTokens += count;
            }
        }
        return commonWordTokens >= 2
                && commonTokens >= 2
                && (commonTokens * 2 >= tokens.size() || repeatedTokens >= 2);
    }

    private static boolean hasOnlyCommonEnglishLyricTokens(String text) {
        List<String> tokens = latinTokens(text);
        if (tokens.size() < 2) {
            return false;
        }
        for (String token : tokens) {
            if (!COMMON_ENGLISH_LYRIC_TOKENS.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private static int findLikelyCjkPhoneticVariantIndex(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < texts.size(); index++) {
            String candidate = texts.get(index);
            if (!containsLatinLetter(candidate) || containsCjkScript(candidate)) {
                continue;
            }
            int primaryIndex = firstCjkTextBefore(texts, index);
            if (primaryIndex < 0) {
                primaryIndex = firstCjkTextIndex(texts);
            }
            if (primaryIndex >= 0 && isLikelyCjkPhoneticVariant(texts, primaryIndex, candidate)) {
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

    private static int firstCjkTextIndex(List<String> texts) {
        for (int index = 0; index < texts.size(); index++) {
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

    private static Set<String> latinTokenSet(String text) {
        return new HashSet<>(latinTokens(text));
    }

    private static List<String> latinTokens(String text) {
        java.util.ArrayList<String> tokens = new java.util.ArrayList<>();
        String value = nullToEmpty(text).toLowerCase(Locale.ROOT);
        int index = 0;
        while (index < value.length()) {
            while (index < value.length() && !isAsciiLetter(value.charAt(index))) {
                index++;
            }
            int start = index;
            while (index < value.length() && isAsciiLetter(value.charAt(index))) {
                index++;
            }
            if (start < index) {
                tokens.add(value.substring(start, index));
            }
        }
        return tokens;
    }

    private static boolean isLikelyCjkPhoneticToken(String token) {
        String value = nullToEmpty(token).toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return false;
        }
        if (CJK_PHONETIC_LATIN_TOKENS.contains(value)) {
            return true;
        }
        return value.length() >= 2
                && value.length() <= 4
                && value.indexOf('v') >= 0
                && !COMMON_ENGLISH_LYRIC_TOKENS.contains(value);
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
            if (isAsciiLetter(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAsciiLetter(char value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
    }
}
