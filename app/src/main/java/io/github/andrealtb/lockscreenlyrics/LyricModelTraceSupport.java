package io.github.andrealtb.lockscreenlyrics;

import java.util.ArrayList;
import java.util.Locale;

import io.github.andrealtb.lockscreenlyrics.render.InlineTimedLyricLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderSupport;
import io.github.andrealtb.lockscreenlyrics.render.WordRange;

/**
 * Shared text helpers and verbose parse-trace formatting for the native
 * lyric model pipeline. Promoted out of {@code LockscreenLyricsModule} in
 * Phase 6 slice 5 together with {@link NativeLyricModelAssembler}; kept
 * Android-free so the pipeline stays unit-testable.
 */
final class LyricModelTraceSupport {
    private LyricModelTraceSupport() {
    }

    static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static String limitTraceValue(String value, int maxLength) {
        String safe = nullToEmpty(value);
        if (maxLength <= 0 || safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    static String formatLrcTime(long timeMillis) {
        long minutes = timeMillis / 60_000L;
        long seconds = (timeMillis % 60_000L) / 1_000L;
        long millis = timeMillis % 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }

    static long parseLrcTimeMillis(String time) {
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

    static long safeParseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    static boolean containsLatinLetter(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                return true;
            }
        }
        return false;
    }

    static boolean containsNonAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return true;
            }
        }
        return false;
    }

    static boolean containsLyricLeadSeparator(String text) {
        if (isEmpty(text)) {
            return false;
        }
        String normalized = WordLyricRenderSupport.normalizeLine(text);
        return normalized.indexOf(':') >= 0 || normalized.indexOf('\uff1a') >= 0;
    }

    static String cleanPlainLyricText(String text) {
        if (isEmpty(text)) {
            return "";
        }
        text = WordLyricRenderSupport.ANY_LRC_TIME_TAG.matcher(text).replaceAll("");
        text = LyricTextSanitizer.removeIgnorableCharacters(text).trim();
        return text.replaceAll("[ \\t]{2,}", " ");
    }

    static void traceWordLyricModel(
            LyricParseTraceSink sink,
            WordLyricModel model,
            String stage,
            String source) {
        if (sink == null || !sink.traceEnabled() || model == null) {
            return;
        }
        sink.trace("model stage=" + nullToEmpty(stage)
                + " source=" + nullToEmpty(source)
                + " parser=" + nullToEmpty(model.parserName)
                + " lines=" + model.lines.size()
                + " officialLines=" + model.officialLines.size()
                + " translations=" + model.translationCount());
        for (int index = 0; index < model.lines.size(); index++) {
            sink.trace("final-line#" + index + " " + describeWordLine(model.lines.get(index), true));
        }
    }

    static void traceInlineGroup(
            LyricParseTraceSink sink,
            long timeMillis,
            ArrayList<InlineTimedLyricLine> group,
            int primaryIndex,
            String stage) {
        if (sink == null || !sink.traceEnabled() || group == null) {
            return;
        }
        sink.trace("inline-group stage=" + nullToEmpty(stage)
                + " time=" + formatLrcTime(timeMillis)
                + " size=" + group.size()
                + " primaryIndex=" + primaryIndex);
        for (int index = 0; index < group.size(); index++) {
            sink.trace("inline-group-candidate#" + index
                    + (index == primaryIndex ? " primary " : " ")
                    + describeInlineTimedLyricLine(group.get(index)));
        }
    }

    static String describeInlineTimedLyricLine(InlineTimedLyricLine line) {
        if (line == null) {
            return "null";
        }
        return "order=" + line.order
                + " time=" + formatLrcTime(line.timeMillis)
                + " end=" + formatLrcTime(line.endTimeMillis)
                + " inline=" + line.inlineTiming
                + " sourceSegments=" + line.sourceTimedSegmentCount
                + " words=" + (line.words == null ? 0 : line.words.size())
                + " text=\"" + limitTraceValue(line.text, 360) + "\""
                + " ranges=" + describeWordRanges(line.text, line.words);
    }

    static String describeWordLine(WordLine line, boolean includeRanges) {
        if (line == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("time=").append(formatLrcTime(line.timeMillis))
                .append(" end=").append(formatLrcTime(line.endTimeMillis))
                .append(" mode=").append(line.timingMode)
                .append(" words=").append(line.words == null ? 0 : line.words.size())
                .append(" text=\"").append(limitTraceValue(line.text, 420)).append("\"");
        if (!isEmpty(line.displayText)) {
            builder.append(" display=\"").append(limitTraceValue(line.displayText, 420)).append("\"");
        }
        if (!isEmpty(line.translation)) {
            builder.append(" translation=\"")
                    .append(limitTraceValue(line.translation, 420))
                    .append("\"");
        }
        if (includeRanges) {
            builder.append(" ranges=").append(describeWordRanges(line.text, line.words));
        }
        return builder.toString();
    }

    static String describeWordRanges(String text, ArrayList<WordRange> words) {
        if (words == null || words.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int index = 0; index < words.size(); index++) {
            if (index > 0) {
                builder.append(", ");
            }
            WordRange word = words.get(index);
            if (word == null) {
                builder.append(index).append(":null");
                continue;
            }
            builder.append(index)
                    .append(':')
                    .append(formatLrcTime(word.timeMillis))
                    .append('(')
                    .append(word.start)
                    .append('-')
                    .append(word.end)
                    .append(")=\"")
                    .append(limitTraceValue(safeTraceSubstring(text, word.start, word.end), 80))
                    .append('"');
        }
        builder.append(']');
        return limitTraceValue(builder.toString(), 1800);
    }

    static String safeTraceSubstring(String text, int start, int end) {
        if (isEmpty(text)) {
            return "";
        }
        int safeStart = Math.max(0, Math.min(start, text.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
        return text.substring(safeStart, safeEnd);
    }
}
