package io.github.andrealtb.lockscreenlyrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OplusLyricNormalizer {
    static final long LEADING_PRE_ROLL_THRESHOLD_MS = 1_500L;
    static final long TAIL_SPACER_DELAY_MS = 8_000L;

    private static final Pattern ANY_LRC_TIME_TAG =
            Pattern.compile("[\\[<]([0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?)[\\]>]");
    private static final Pattern BRACKETED_LRC_TIME_TAG =
            Pattern.compile("\\[([0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?)]");

    private OplusLyricNormalizer() {
    }

    static String normalizeForOfficialList(String rawLyric) {
        if (isEmpty(rawLyric)) {
            return "";
        }

        LinkedHashMap<Long, TimedLyricGroup> groups = new LinkedHashMap<>();
        for (String rawLine : splitRawLyricLines(rawLyric)) {
            for (String expandedLine : splitEmbeddedTimedLines(rawLine)) {
                String line = expandedLine == null ? "" : expandedLine.trim();
                if (line.isEmpty()) {
                    continue;
                }

                Matcher firstTag = ANY_LRC_TIME_TAG.matcher(line);
                if (!firstTag.find() || firstTag.start() != 0) {
                    continue;
                }

                long timeMillis = parseLrcTimeMillis(firstTag.group(1));
                String text = cleanPlainLyricText(line.substring(firstTag.end()));
                if (text.isEmpty() || isNonLyricInfoLine(text, timeMillis)) {
                    continue;
                }

                TimedLyricGroup group = groups.get(timeMillis);
                if (group == null) {
                    group = new TimedLyricGroup(timeMillis);
                    groups.put(timeMillis, group);
                }
                group.texts.add(text);
            }
        }

        ArrayList<TimedLyricGroup> groupedLines = new ArrayList<>(groups.values());
        StringBuilder out = new StringBuilder(rawLyric.length());
        boolean preRollFirstLine = !groupedLines.isEmpty()
                && groupedLines.get(0).timeMillis > LEADING_PRE_ROLL_THRESHOLD_MS;
        if (preRollFirstLine) {
            TimedLyricGroup first = groupedLines.get(0);
            appendLyricLine(out, 0L, first.texts.get(findPrimaryTextIndex(first.texts)));
        }
        for (int i = preRollFirstLine ? 1 : 0; i < groupedLines.size(); i++) {
            appendGroupedLyricLine(out, groupedLines.get(i));
        }
        appendTailSpacerLyricLine(out, groupedLines);
        return out.toString();
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
        if (LockscreenIntegrationPolicy.isProductionDetailLine(normalized, timeMillis)) {
            return true;
        }
        return LockscreenIntegrationPolicy.isLikelyTitleArtistCredit(normalized, timeMillis);
    }

    private static void appendGroupedLyricLine(StringBuilder out, TimedLyricGroup group) {
        if (group == null || group.texts.isEmpty()) {
            return;
        }
        appendLyricLine(out, group.timeMillis, group.texts.get(findPrimaryTextIndex(group.texts)));
    }

    private static void appendLyricLine(StringBuilder out, long timeMillis, String text) {
        if (isEmpty(text)) {
            return;
        }
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append('[').append(formatLrcTime(timeMillis)).append(']').append(text);
    }

    private static void appendTailSpacerLyricLine(
            StringBuilder out, ArrayList<TimedLyricGroup> groups) {
        if (groups.isEmpty()) {
            return;
        }
        TimedLyricGroup last = groups.get(groups.size() - 1);
        if (last == null) {
            return;
        }
        appendLyricLine(out, last.timeMillis + TAIL_SPACER_DELAY_MS, "\u200B");
    }

    private static int findPrimaryTextIndex(List<String> texts) {
        for (int i = 0; i < texts.size(); i++) {
            if (containsLatinLetter(texts.get(i))) {
                return i;
            }
        }
        return 0;
    }

    private static String cleanPlainLyricText(String text) {
        if (isEmpty(text)) {
            return "";
        }
        String cleaned = ANY_LRC_TIME_TAG.matcher(text).replaceAll("");
        cleaned = LyricTextSanitizer.removeIgnorableCharacters(cleaned).trim();
        return cleaned.replaceAll("[ \\t]{2,}", " ");
    }

    private static String normalizeLine(String line) {
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

    private static String[] splitRawLyricLines(String rawLyric) {
        return rawLyric.replace('\r', '\n').split("\n");
    }

    private static List<String> splitEmbeddedTimedLines(String rawLine) {
        ArrayList<String> lines = new ArrayList<>();
        String line = rawLine == null ? "" : rawLine;
        Matcher matcher = BRACKETED_LRC_TIME_TAG.matcher(line);
        if (!matcher.find()) {
            lines.add(line);
            return lines;
        }

        int segmentStart = matcher.start();
        int previousTagEnd = matcher.end();
        while (matcher.find()) {
            String textBeforeTag = line.substring(previousTagEnd, matcher.start());
            if (!LyricTextSanitizer.removeIgnorableCharacters(textBeforeTag).trim().isEmpty()) {
                lines.add(line.substring(segmentStart, matcher.start()));
                segmentStart = matcher.start();
            }
            previousTagEnd = matcher.end();
        }
        lines.add(line.substring(segmentStart));
        return lines;
    }

    private static boolean containsLatinLetter(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String value, String... needles) {
        if (isEmpty(value)) {
            return false;
        }
        for (String needle : needles) {
            if (!isEmpty(needle) && value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static long parseLrcTimeMillis(String time) {
        String[] minuteAndRest = time.split(":", 2);
        if (minuteAndRest.length != 2) {
            return 0L;
        }
        long minutes = safeParseLong(minuteAndRest[0]);
        String rest = minuteAndRest[1].replace(':', '.');
        String[] secondAndFraction = rest.split("\\.", 2);
        long seconds = safeParseLong(secondAndFraction[0]);
        long millis = 0L;
        if (secondAndFraction.length == 2) {
            String fraction = secondAndFraction[1];
            if (fraction.length() > 3) {
                fraction = fraction.substring(0, 3);
            }
            while (fraction.length() < 3) {
                fraction = fraction + "0";
            }
            millis = safeParseLong(fraction);
        }
        return minutes * 60_000L + seconds * 1_000L + millis;
    }

    private static long safeParseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String formatLrcTime(long timeMillis) {
        long minutes = timeMillis / 60_000L;
        long seconds = (timeMillis % 60_000L) / 1_000L;
        long millis = timeMillis % 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static final class TimedLyricGroup {
        final long timeMillis;
        final ArrayList<String> texts = new ArrayList<>();

        TimedLyricGroup(long timeMillis) {
            this.timeMillis = timeMillis;
        }
    }
}
