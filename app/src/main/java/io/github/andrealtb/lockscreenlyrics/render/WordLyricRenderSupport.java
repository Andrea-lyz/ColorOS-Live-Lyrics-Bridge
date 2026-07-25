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

    public static long inferWordLineEndMillis(long timeMillis, ArrayList<WordRange> words) {
        if (words == null || words.isEmpty()) {
            return timeMillis + 600L;
        }
        return Math.max(timeMillis + 600L, words.get(words.size() - 1).timeMillis + 520L);
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
