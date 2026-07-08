package io.github.andrealtb.lockscreenlyrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class TimedLyricDocument {
    static final TimedLyricDocument EMPTY = new TimedLyricDocument(Collections.emptyList());

    private final List<Line> lines;

    TimedLyricDocument(List<Line> lines) {
        ArrayList<Line> copy = new ArrayList<>();
        if (lines != null) {
            for (Line line : lines) {
                if (line != null && !isEmpty(line.text)) {
                    copy.add(line);
                }
            }
        }
        copy.sort(Comparator.comparingLong(line -> line.startMillis));
        this.lines = Collections.unmodifiableList(copy);
    }

    static TimedLyricDocument fromRawLrc(String rawLyric) {
        if (!LyricInfoContract.containsTimedLrc(rawLyric)) {
            return EMPTY;
        }
        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(rawLyric);
        ArrayList<Line> converted = new ArrayList<>();
        for (LyricsCoreAdapter.ParsedLine parsedLine : parsed.lines) {
            ArrayList<Word> words = new ArrayList<>();
            for (LyricsCoreAdapter.ParsedSyllable syllable : parsedLine.syllables) {
                words.add(new Word(
                        syllable.startMillis,
                        syllable.endMillis,
                        clamp(syllable.start, 0, parsedLine.text.length()),
                        clamp(syllable.end, 0, parsedLine.text.length())));
            }
            words = sanitizeSuspiciousWordTiming(
                    parsedLine.startMillis,
                    parsedLine.endMillis,
                    parsedLine.text,
                    words);
            converted.add(new Line(
                    parsedLine.startMillis,
                    parsedLine.endMillis,
                    parsedLine.text,
                    parsedLine.translation,
                    words));
        }
        return new TimedLyricDocument(converted);
    }

    boolean isEmpty() {
        return lines.isEmpty();
    }

    int lineCount() {
        return lines.size();
    }

    List<Line> lines() {
        return lines;
    }

    boolean hasWordTiming() {
        for (Line line : lines) {
            if (line.words.size() > 1) {
                return true;
            }
        }
        return false;
    }

    int translationCount() {
        int count = 0;
        for (Line line : lines) {
            if (!isEmpty(line.translation)) {
                count++;
            }
        }
        return count;
    }

    int wordTimedLineCount() {
        int count = 0;
        for (Line line : lines) {
            if (line.words.size() > 1) {
                count++;
            }
        }
        return count;
    }

    TimedLyricDocument withTranslationsFrom(TimedLyricDocument translations, long maxDistanceMs) {
        if (isEmpty() || translations == null || translations.isEmpty()) {
            return this;
        }
        ArrayList<Line> merged = new ArrayList<>(lines.size());
        for (Line line : lines) {
            Line translation = translations.findClosest(line.startMillis, maxDistanceMs);
            merged.add(new Line(
                    line.startMillis,
                    line.endMillis,
                    line.text,
                    translation == null ? line.translation : translation.text,
                    line.words));
        }
        return new TimedLyricDocument(merged);
    }

    TimedLyricDocument withUsableTranslationsFrom(
            TimedLyricDocument translations,
            long maxDistanceMs) {
        if (isEmpty() || translations == null || translations.isEmpty()) {
            return this;
        }
        ArrayList<Line> merged = new ArrayList<>(lines.size());
        for (Line line : lines) {
            Line candidate = translations.findClosest(line.startMillis, maxDistanceMs);
            String translation = usableTranslationText(line, candidate);
            merged.add(new Line(
                    line.startMillis,
                    line.endMillis,
                    line.text,
                    isEmpty(translation) ? line.translation : translation,
                    line.words));
        }
        return new TimedLyricDocument(merged);
    }

    TimedLyricDocument withWordTimingFrom(TimedLyricDocument supplement, long maxDistanceMs) {
        if (isEmpty() || supplement == null || supplement.isEmpty()) {
            return this;
        }
        ArrayList<Line> merged = new ArrayList<>(lines.size());
        for (Line line : lines) {
            Line candidate = supplement.findClosest(line.startMillis, maxDistanceMs);
            if (line.words.size() <= 1
                    && candidate != null
                    && candidate.words.size() > 1
                    && line.text.equals(candidate.text)) {
                merged.add(new Line(
                        line.startMillis,
                        Math.max(line.endMillis, candidate.endMillis),
                        line.text,
                        line.translation,
                        candidate.words));
                continue;
            }
            merged.add(line);
        }
        return new TimedLyricDocument(merged);
    }

    int usableTranslationMatchCount(TimedLyricDocument translations, long maxDistanceMs) {
        if (isEmpty() || translations == null || translations.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Line line : lines) {
            if (!isEmpty(usableTranslationText(
                    line,
                    translations.findClosest(line.startMillis, maxDistanceMs)))) {
                count++;
            }
        }
        return count;
    }

    String toPlainLrc() {
        StringBuilder builder = new StringBuilder(lines.size() * 48);
        for (Line line : lines) {
            appendPlainLine(builder, line.startMillis, line.text);
        }
        return builder.toString();
    }

    String toEnhancedLrc() {
        StringBuilder builder = new StringBuilder(lines.size() * 64);
        for (Line line : lines) {
            if (!line.words.isEmpty()) {
                builder.append('[')
                        .append(formatLrcTime(line.startMillis))
                        .append(']');
                int cursor = 0;
                for (Word word : line.words) {
                    int start = clamp(word.start, 0, line.text.length());
                    int end = clamp(word.end, start, line.text.length());
                    if (cursor < start) {
                        builder.append(line.text, cursor, start);
                    }
                    builder.append('<')
                            .append(formatLrcTime(word.startMillis))
                            .append('>');
                    if (start < end) {
                        builder.append(line.text, start, end);
                    }
                    cursor = end;
                }
                if (cursor < line.text.length()) {
                    builder.append(line.text.substring(cursor));
                }
                if (line.endMillis > line.startMillis) {
                    builder.append('<')
                            .append(formatLrcTime(line.endMillis))
                            .append('>');
                }
                builder.append('\n');
            } else {
                appendPlainLine(builder, line.startMillis, line.text);
            }
            if (!isEmpty(line.translation)) {
                appendPlainLine(builder, line.startMillis, line.translation);
            }
        }
        return builder.toString();
    }

    private Line findClosest(long startMillis, long maxDistanceMs) {
        Line best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Line line : lines) {
            long distance = Math.abs(line.startMillis - startMillis);
            if (distance < bestDistance) {
                best = line;
                bestDistance = distance;
            }
            if (line.startMillis > startMillis && distance > bestDistance) {
                break;
            }
        }
        return bestDistance <= Math.max(0L, maxDistanceMs) ? best : null;
    }

    private static String usableTranslationText(Line primaryLine, Line candidateLine) {
        if (primaryLine == null || candidateLine == null) {
            return "";
        }
        String translation = firstUsableSupplementalTranslation(
                primaryLine.text,
                candidateLine.translation,
                candidateLine.text);
        return translation;
    }

    private static String firstUsableSupplementalTranslation(
            String primaryText,
            String... candidates) {
        if (candidates == null) {
            return "";
        }
        for (String candidate : candidates) {
            String clean = LyricTextSanitizer.removeIgnorableCharacters(
                    nullToEmpty(candidate)).trim();
            if (isUsableSupplementalTranslation(primaryText, clean)) {
                return clean;
            }
        }
        return "";
    }

    private static boolean isUsableSupplementalTranslation(
            String primaryText,
            String candidate) {
        String primary = LyricTextSanitizer.removeIgnorableCharacters(
                nullToEmpty(primaryText)).trim();
        if (isEmpty(candidate)
                || candidate.equals(primary)
                || LyricMetadataFilter.isNonLyricInfoLine(candidate, -1L)
                || LockscreenIntegrationPolicy.sameLyricVariant(primary, candidate)
                || LyricLineVariantSelector.isLikelyJapaneseRomanization(primary, candidate)) {
            return false;
        }
        return !(containsCjkScript(primary)
                && LyricLineVariantSelector.isLikelyPhoneticVariant(
                Arrays.asList(primary, candidate),
                0,
                candidate));
    }

    private static void appendPlainLine(StringBuilder builder, long startMillis, String text) {
        String clean = LyricTextSanitizer.removeIgnorableCharacters(nullToEmpty(text)).trim();
        if (isEmpty(clean)) {
            return;
        }
        builder.append('[')
                .append(formatLrcTime(startMillis))
                .append(']')
                .append(clean)
                .append('\n');
    }

    private static String formatLrcTime(long timeMillis) {
        long safeTime = Math.max(0L, timeMillis);
        long minutes = safeTime / 60_000L;
        long seconds = (safeTime % 60_000L) / 1_000L;
        long millis = safeTime % 1_000L;
        return String.format(Locale.ROOT, "%02d:%02d.%03d", minutes, seconds, millis);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static ArrayList<Word> sanitizeSuspiciousWordTiming(
            long lineStartMillis,
            long lineEndMillis,
            String text,
            ArrayList<Word> words) {
        if (words == null || words.size() < 2) {
            return words;
        }
        ArrayList<Word> timingWords = lyricTimingWords(text, words);
        if (timingWords.size() < 2) {
            return words;
        }
        long maxGap = 0L;
        boolean strictlyIncreasing = true;
        for (int index = 1; index < timingWords.size(); index++) {
            long gap = timingWords.get(index).startMillis - timingWords.get(index - 1).startMillis;
            if (gap <= 0L) {
                strictlyIncreasing = false;
            }
            maxGap = Math.max(maxGap, gap);
        }
        if (!LyricTimingRepair.shouldDowngradeWordTiming(
                timingWords.size(),
                timingWords.get(0).startMillis,
                timingWords.get(timingWords.size() - 1).startMillis,
                maxGap,
                strictlyIncreasing)) {
            return words;
        }
        ArrayList<Word> lineTimedWords = new ArrayList<>();
        lineTimedWords.add(new Word(
                lineStartMillis,
                Math.max(lineStartMillis, lineEndMillis),
                0,
                nullToEmpty(text).length()));
        return lineTimedWords;
    }

    private static ArrayList<Word> lyricTimingWords(String text, ArrayList<Word> words) {
        ArrayList<Word> timingWords = new ArrayList<>();
        for (Word word : words) {
            if (hasLyricTimingText(text, word)) {
                timingWords.add(word);
            }
        }
        return timingWords;
    }

    private static boolean hasLyricTimingText(String text, Word word) {
        String safeText = nullToEmpty(text);
        int start = clamp(word.start, 0, safeText.length());
        int end = clamp(word.end, 0, safeText.length());
        while (start < end) {
            int codePoint = safeText.codePointAt(start);
            if (Character.isLetterOrDigit(codePoint)) {
                return true;
            }
            start += Character.charCount(codePoint);
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

    static final class Line {
        final long startMillis;
        final long endMillis;
        final String text;
        final String translation;
        final List<Word> words;

        Line(
                long startMillis,
                long endMillis,
                String text,
                String translation,
                List<Word> words) {
            this.startMillis = Math.max(0L, startMillis);
            this.endMillis = Math.max(this.startMillis, endMillis);
            this.text = LyricTextSanitizer.removeIgnorableCharacters(nullToEmpty(text)).trim();
            this.translation =
                    LyricTextSanitizer.removeIgnorableCharacters(nullToEmpty(translation)).trim();
            this.words = words == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(words));
        }
    }

    static final class Word {
        final long startMillis;
        final long endMillis;
        final int start;
        final int end;

        Word(long startMillis, long endMillis, int start, int end) {
            this.startMillis = Math.max(0L, startMillis);
            this.endMillis = Math.max(this.startMillis, endMillis);
            this.start = Math.max(0, start);
            this.end = Math.max(this.start, end);
        }
    }
}
