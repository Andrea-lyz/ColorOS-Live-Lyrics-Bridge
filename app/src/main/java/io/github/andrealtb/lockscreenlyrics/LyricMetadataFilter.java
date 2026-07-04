package io.github.andrealtb.lockscreenlyrics;

import java.util.Locale;
import java.util.regex.Pattern;

/** Central filter for timed lines that describe lyric metadata instead of lyric content. */
final class LyricMetadataFilter {
    private static final Pattern ANY_LRC_TIME_TAG =
            Pattern.compile("[\\[<][0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?[\\]>]");

    private LyricMetadataFilter() {
    }

    static boolean isNonLyricInfoLine(String text, long timeMillis) {
        String normalized = normalizeLine(text);
        if (normalized.isEmpty()) {
            return false;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "copyright", "all rights reserved")
                || containsAny(
                normalized,
                "\u7248\u6743\u6240\u6709",
                "\u8457\u4f5c\u6743",
                "\u672a\u7ecf\u8bb8\u53ef",
                "\u672a\u7ecf\u6388\u6743",
                "\u7ffb\u8bd1\u4f5c\u54c1")) {
            return true;
        }
        if (isLyricTranslationProviderCreditLine(normalized)) {
            return true;
        }
        if (LockscreenIntegrationPolicy.isProductionDetailLine(normalized, timeMillis)) {
            return true;
        }
        return LockscreenIntegrationPolicy.isLikelyTitleArtistCredit(normalized, timeMillis);
    }

    static String normalizeLine(String line) {
        if (line == null) {
            return "";
        }
        String normalized = line;
        if (normalized.indexOf('[') >= 0 || normalized.indexOf('<') >= 0) {
            normalized = ANY_LRC_TIME_TAG.matcher(normalized).replaceAll("");
        }
        int length = normalized.length();
        int start = 0;
        int end = length;
        while (start < end && normalized.charAt(start) <= ' ') {
            start++;
        }
        while (end > start && normalized.charAt(end - 1) <= ' ') {
            end--;
        }
        boolean collapseWhitespace = false;
        for (int i = start + 1; i < end; i++) {
            char previous = normalized.charAt(i - 1);
            char current = normalized.charAt(i);
            if ((previous == ' ' || previous == '\t')
                    && (current == ' ' || current == '\t')) {
                collapseWhitespace = true;
                break;
            }
        }
        if (!collapseWhitespace) {
            return start == 0 && end == length
                    ? normalized
                    : normalized.substring(start, end);
        }
        StringBuilder result = new StringBuilder(end - start);
        boolean inWhitespaceRun = false;
        for (int i = start; i < end; i++) {
            char ch = normalized.charAt(i);
            boolean whitespace = ch == ' ' || ch == '\t';
            if (whitespace) {
                if (!inWhitespaceRun) {
                    result.append(ch);
                } else if (result.charAt(result.length() - 1) != ' ') {
                    result.setCharAt(result.length() - 1, ' ');
                }
                inWhitespaceRun = true;
            } else {
                result.append(ch);
                inWhitespaceRun = false;
            }
        }
        return result.toString();
    }

    private static boolean isLyricTranslationProviderCreditLine(String normalized) {
        String compact = removeWhitespace(normalized);
        if (compact.isEmpty() || !compact.contains("\u63d0\u4f9b")) {
            return false;
        }
        return compact.startsWith("\u4ee5\u4e0b\u6b4c\u8bcd\u7ffb\u8bd1\u7531")
                || compact.startsWith("\u4ee5\u4e0b\u6b4c\u8a5e\u7ffb\u8b6f\u7531")
                || compact.startsWith("\u672c\u6b4c\u8bcd\u7ffb\u8bd1\u7531")
                || compact.startsWith("\u672c\u6b4c\u8a5e\u7ffb\u8b6f\u7531")
                || compact.startsWith("\u6b4c\u8bcd\u7ffb\u8bd1\u7531")
                || compact.startsWith("\u6b4c\u8a5e\u7ffb\u8b6f\u7531");
    }

    private static String removeWhitespace(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder compact = new StringBuilder(value.length());
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) {
                compact.appendCodePoint(codePoint);
            }
            index += Character.charCount(codePoint);
        }
        return compact.toString();
    }

    private static boolean containsAny(String value, String... needles) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty() && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
