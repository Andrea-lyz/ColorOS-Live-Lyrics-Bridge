package io.github.andrealtb.lockscreenlyrics.render;

import io.github.andrealtb.lockscreenlyrics.LyricTextMatchPolicy;
import io.github.andrealtb.lockscreenlyrics.LyricTextSanitizer;

import java.util.ArrayList;
import java.util.regex.Pattern;

/**
 * Pure helpers used by {@link WordLyricModel}, {@link WordLine}, and the
 * surrounding render pipeline. They were extracted from
 * {@code LockscreenLyricsModule} as part of step 3.1 (splitting that 19,000
 * line module). The constants and helpers must stay public so that
 * {@code LockscreenLyricsModule} (which lives in the parent package) and
 * callers in the surrounding bridge app can still reach them.
 */
public final class WordLyricRenderSupport {

    private WordLyricRenderSupport() {
    }

    public static final Pattern ANY_LRC_TIME_TAG =
            Pattern.compile("[\\[<]([0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?)[\\]>]");

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * Word-timed karaoke, including the translation overlay, must stay at 0 until
     * the first word timestamp. QRC {@code t.b} / ColorOS {@code line.begin} can
     * sit hundreds of milliseconds to two seconds earlier; using that as
     * line-elapsed progress walks the translation during the intro and then
     * snaps back when word mapping starts.
     */
    public static boolean shouldHoldWordTimedReveal(WordLine line, long position) {
        if (line == null || line.timingMode != LyricTimingMode.WORD_TIMED) {
            return false;
        }
        if (line.words == null || line.words.isEmpty()) {
            return false;
        }
        return line.findWordIndex(position) < 0;
    }

    public static final long MIN_WORD_REVEAL_MS = 80L;
    public static final long DEFAULT_LAST_WORD_REVEAL_MS = 280L;
    public static final long MAX_LAST_WORD_REVEAL_MS = 400L;

    public static long inferWordLineEndMillis(long timeMillis, ArrayList<WordRange> words) {
        if (words == null || words.isEmpty()) {
            return timeMillis + 600L;
        }
        return Math.max(timeMillis + 600L, words.get(words.size() - 1).timeMillis + 520L);
    }

    /**
     * Median adjacent-word gap in {@code words}, used so the last syllable
     * reveals at the same pace as the rest of the line. QRC/KRC trailing time
     * tags mark the next line, not last-word duration.
     */
    public static long typicalWordRevealDurationMillis(ArrayList<WordRange> words) {
        if (words == null || words.size() < 2) {
            return DEFAULT_LAST_WORD_REVEAL_MS;
        }
        long[] gaps = new long[words.size() - 1];
        int count = 0;
        for (int i = 1; i < words.size(); i++) {
            long gap = words.get(i).timeMillis - words.get(i - 1).timeMillis;
            if (gap > 0L) {
                gaps[count++] = gap;
            }
        }
        if (count <= 0) {
            return DEFAULT_LAST_WORD_REVEAL_MS;
        }
        java.util.Arrays.sort(gaps, 0, count);
        long median = (count & 1) == 1
                ? gaps[count / 2]
                : (gaps[count / 2 - 1] + gaps[count / 2]) / 2L;
        return Math.max(
                MIN_WORD_REVEAL_MS,
                Math.min(MAX_LAST_WORD_REVEAL_MS, median));
    }

    /**
     * Visual end of the last word. Line {@code endTimeMillis} is kept as the
     * row lifetime; karaoke must not interpolate across the rest / next-line
     * pre-roll or the last word crawls and the next row replaces it unfinished.
     */
    public static long lastWordRevealEndMillis(
            long lastWordBegin,
            long lineEndMillis,
            ArrayList<WordRange> words) {
        long duration = typicalWordRevealDurationMillis(words);
        long visualEnd = lastWordBegin + duration;
        if (lineEndMillis > lastWordBegin) {
            visualEnd = Math.min(visualEnd, lineEndMillis);
        }
        return Math.max(lastWordBegin + MIN_WORD_REVEAL_MS, visualEnd);
    }

    public static long wordRevealEndMillis(WordLyricModel model, WordLine line, int index) {
        if (line == null || line.words == null || index < 0 || index >= line.words.size()) {
            return line == null ? 0L : line.timeMillis + 600L;
        }
        long end = line.wordEndMillis(index);
        if (model == null || index != line.words.size() - 1) {
            return end;
        }
        long begin = line.words.get(index).timeMillis;
        int lineIndex = model.indexOfLine(line);
        WordLine next = lineIndex >= 0 ? model.lineAt(lineIndex + 1) : null;
        if (next != null && next.timeMillis > begin) {
            end = Math.min(end, next.timeMillis);
        }
        return Math.max(begin + 1L, end);
    }

    public static float wordRevealProgress(
            WordLyricModel model,
            WordLine line,
            int index,
            long position) {
        if (line == null || line.words == null || index < 0 || index >= line.words.size()) {
            return 0f;
        }
        long begin = line.words.get(index).timeMillis;
        long end = wordRevealEndMillis(model, line, index);
        if (position <= begin) {
            return 0f;
        }
        if (position >= end) {
            return 1f;
        }
        return (float) (position - begin) / (float) Math.max(1L, end - begin);
    }

    /**
     * A one-word interjection whose whole lifetime is shorter than a useful
     * karaoke sweep should use timestamp-gated full-row highlighting instead.
     * The real next-line handoff is preserved; only the visual mode changes.
     */
    public static boolean shouldUseTimestampHighlight(
            WordLyricModel model,
            WordLine line) {
        if (model == null
                || line == null
                || line.timingMode != LyricTimingMode.WORD_TIMED
                || line.words == null
                || line.words.size() != 1) {
            return false;
        }
        long begin = line.words.get(0).timeMillis;
        long end = wordRevealEndMillis(model, line, 0);
        return end > begin && end - begin < MIN_WORD_REVEAL_MS;
    }

    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    public static String normalizeLine(String line) {
        if (line == null) {
            return "";
        }
        String normalized = LyricTextSanitizer.removeIgnorableCharacters(line);
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

    public static String lyricMatchKey(String text) {
        return lyricMatchKeyFromNormalized(normalizeLine(text));
    }

    public static String lyricMatchKeyFromNormalized(String normalized) {
        StringBuilder key = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                key.append(Character.toLowerCase(ch));
            }
        }
        return key.toString();
    }

    public static boolean sameWordLine(WordLine left, WordLine right) {
        return left != null && right != null && left == right;
    }

    public static boolean matchesWordLineText(WordLine line, String normalizedText) {
        if (line == null || isEmpty(normalizedText)) {
            return false;
        }
        String normalizedDisplayText = line.normalizedDisplayText();
        if (line.normalizedText.equals(normalizedText)
                || normalizedDisplayText.equals(normalizedText)) {
            return true;
        }
        if (isLyricPrefixMatchCached(
                normalizedText,
                line.normalizedText,
                line.textMatchKey)) {
            return true;
        }
        return !isEmpty(normalizedDisplayText)
                && isLyricPrefixMatchCached(
                normalizedText,
                normalizedDisplayText,
                line.displayMatchKey());
    }

    public static boolean matchesWordLineRenderableText(WordLine line, String normalizedText) {
        return matchesWordLineText(line, normalizedText)
                || (line != null
                && !isEmpty(line.translation)
                && line.normalizedTranslation().equals(normalizedText));
    }

    public static boolean matchesLyricText(String fullText, String normalizedText) {
        if (isEmpty(normalizedText)) {
            return false;
        }
        String normalizedFullText = normalizeLine(fullText);
        if (normalizedFullText.equals(normalizedText)) {
            return true;
        }
        return isLyricPrefixMatch(normalizedText, normalizedFullText);
    }

    public static boolean isLyricPrefixMatch(String visibleText, String fullText) {
        if (isEmpty(visibleText) || isEmpty(fullText)) {
            return false;
        }
        String visibleKey = lyricMatchKey(visibleText);
        String fullKey = lyricMatchKey(fullText);
        return LyricTextMatchPolicy.hasSubstantialPrefix(
                visibleText,
                fullText,
                visibleKey,
                fullKey);
    }

    public static boolean isLyricPrefixMatchCached(
            String visibleText, String fullText, String fullKey) {
        if (isEmpty(visibleText) || isEmpty(fullText)) {
            return false;
        }
        String visibleKey = lyricMatchKeyFromNormalized(visibleText);
        return LyricTextMatchPolicy.hasSubstantialPrefix(
                visibleText,
                fullText,
                visibleKey,
                fullKey);
    }
}
