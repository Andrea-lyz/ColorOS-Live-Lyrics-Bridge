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

        if (isCopyrightOrRightsLine(normalized)) {
            return true;
        }
        if (isParsingProtectedLine(normalized)) {
            return true;
        }
        if (isDisplayProductionDetailLine(normalized, timeMillis)) {
            return true;
        }
        return LockscreenIntegrationPolicy.isLikelyTitleArtistCredit(normalized, timeMillis);
    }

    /**
     * Lines whose presence would change lane classification rather than only presentation.
     * These remain fixed and are deliberately not exposed as user-editable cleanup rules.
     */
    static boolean isParsingProtectedLine(String text) {
        String normalized = normalizeLine(text);
        return !normalized.isEmpty() && isLyricTranslationProviderCreditLine(normalized);
    }

    static boolean isCopyrightOrRightsLine(String text) {
        String normalized = normalizeLine(text);
        if (normalized.isEmpty()) return false;
        String lower = normalized.toLowerCase(Locale.ROOT);
        return normalized.startsWith("©")
                || containsAny(lower, "copyright", "all rights reserved", "used by permission")
                || containsAny(
                normalized,
                "版权所有",
                "著作权",
                "未经许可",
                "未经授权",
                "翻译作品");
    }

    static boolean isDisplayProductionDetailLine(String text, long timeMillis) {
        String normalized = normalizeLine(text);
        if (normalized.isEmpty()) return false;
        if (LockscreenIntegrationPolicy.isProductionDetailLine(normalized, timeMillis)) {
            return true;
        }
        if (timeMillis < 0L || timeMillis > 30_000L) return false;
        String lower = normalized.toLowerCase(Locale.ROOT).replace('：', ':');
        int separator = lower.indexOf(':');
        String label = (separator >= 0 ? lower.substring(0, separator) : lower).trim();
        if (label.isEmpty() || label.length() > 120) return false;
        String[] roleStarts = {
                "vocals recorded", "background vocal", "background vocals",
                "backing vocal", "backing vocals", "orchestration", "percussion",
                "synth", "synthesizer",
                "viola", "violin", "piano", "acoustic guitar", "electric guitar",
                "drum", "drums", "drum programming", "digital edited", "digital editing",
                "mixed in dolby atmos", "orchestra", "band", "choir", "conductor",
                "accordion", "strings", "guitar", "bass", "cello",
                "original publisher", "original publishers", "sub-publisher", "sub-publishers",
                "publisher", "乐队", "樂隊", "管弦乐", "管弦樂", "交响乐团",
                "交響樂團", "合唱", "指挥", "指揮", "手风琴", "手風琴",
                "钢琴", "鋼琴", "大提琴", "小提琴", "弦乐", "弦樂"
        };
        boolean role = false;
        for (String candidate : roleStarts) {
            if (label.startsWith(candidate)) {
                role = true;
                break;
            }
        }
        if (!role) return false;
        return separator >= 0 || lower.contains(" by") || lower.contains(" recorded by");
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
        if (compact.isEmpty() || !compact.contains("提供")) {
            return false;
        }
        return compact.startsWith("以下歌词翻译由")
                || compact.startsWith("以下歌詞翻譯由")
                || compact.startsWith("本歌词翻译由")
                || compact.startsWith("本歌詞翻譯由")
                || compact.startsWith("歌词翻译由")
                || compact.startsWith("歌詞翻譯由");
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
