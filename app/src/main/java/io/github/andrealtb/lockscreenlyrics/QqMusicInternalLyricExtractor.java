package io.github.andrealtb.lockscreenlyrics;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class QqMusicInternalLyricExtractor {
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final Pattern SONG_INFO_VALUE =
            Pattern.compile("([a-zA-Z]+)=([^,)]*)");
    private static final long EARLY_METADATA_WINDOW_MS = 30_000L;
    private static final String[] CREDIT_ROLE_KEYWORDS = {
            "lyrics",
            "lyricist",
            "composer",
            "arranger",
            "producer",
            "produced",
            "production",
            "vocal",
            "vocals",
            "cello",
            "violin",
            "orchestra",
            "band",
            "choir",
            "conductor",
            "accordion",
            "strings",
            "guitar",
            "bass",
            "drums",
            "piano",
            "mix",
            "mixed",
            "master",
            "mastered",
            "recording",
            "recorded",
            "publisher",
            "copyright",
            "op",
            "sp",
            "作词",
            "作詞",
            "作曲",
            "编曲",
            "編曲",
            "制作",
            "製作",
            "出品",
            "监制",
            "監製",
            "混音",
            "母带",
            "母帶",
            "录音",
            "錄音",
            "和声",
            "和聲",
            "人声",
            "人聲",
            "乐队",
            "樂隊",
            "管弦乐",
            "管弦樂",
            "交响乐团",
            "交響樂團",
            "合唱",
            "指挥",
            "指揮",
            "手风琴",
            "手風琴",
            "吉他",
            "贝斯",
            "貝斯",
            "鼓手",
            "钢琴",
            "鋼琴",
            "大提琴",
            "小提琴",
            "弦乐",
            "弦樂",
            "演唱",
            "演奏"
    };
    private static final String[] UTF8_AS_GB18030_MOJIBAKE_TOKENS = {
            "銇",
            "銆",
            "銈",
            "銉",
            "倐",
            "仟",
            "亪",
            "亀",
            "涓",
            "鏄",
            "寮",
            "鐍",
            "甯",
            "屾",
            "鏈",
            "窘",
            "姣",
            "涘",
            "缈",
            "呃",
            "閾",
            "鐵",
            "鍜",
            "宕",
            "澖",
            "鏂",
            "鍒",
            "瀫",
            "剜",
            "闄",
            "潤",
            "浼",
            "鐸",
            "闇",
            "閹"
    };
    private static final String[] WORD_TEXT_FIELDS = {
            "e",
            "c",
            "d",
            "text",
            "word"
    };

    private QqMusicInternalLyricExtractor() {
    }

    static TimedLyricDocument extract(Object lyricObject) {
        Iterable<?> sourceLines = asIterable(readField(lyricObject, "e"));
        if (sourceLines == null) {
            return TimedLyricDocument.EMPTY;
        }

        ArrayList<TimedLyricDocument.Line> lines = new ArrayList<>();
        for (Object sourceLine : sourceLines) {
            TimedLyricDocument.Line line = parseLine(sourceLine);
            if (line != null) {
                lines.add(line);
            }
        }
        lines = filterLikelyMetadataLines(lines);
        mergeTranslationCandidates(lyricObject, lines);
        return new TimedLyricDocument(lines);
    }

    static TimedLyricDocument withoutLikelyMetadataLines(TimedLyricDocument document) {
        if (document == null || document.isEmpty()) {
            return document == null ? TimedLyricDocument.EMPTY : document;
        }
        ArrayList<TimedLyricDocument.Line> filtered =
                filterLikelyMetadataLines(document.lines());
        return filtered.size() == document.lineCount()
                ? document
                : new TimedLyricDocument(filtered);
    }

    static SongMetadata readSongMetadata(Object songInfo) {
        String stringValue = songInfo == null ? "" : String.valueOf(songInfo);
        String title = firstNonPlaceholder(
                invokeString(songInfo, "X2"),
                parseSongInfoValue(stringValue, "title"),
                readStringField(songInfo, "c"),
                readStringField(songInfo, "title"),
                invokeString(songInfo, "getTitle"));
        String artist = firstNonPlaceholder(
                invokeString(songInfo, "C3"),
                parseSongInfoValue(stringValue, "artist"),
                readStringField(songInfo, "d"),
                readStringField(songInfo, "artist"),
                invokeString(songInfo, "getArtist"));
        String songId = firstNonEmpty(
                invokeString(songInfo, "v2"),
                parseSongInfoValue(stringValue, "id"),
                readStringField(songInfo, "a"),
                readStringField(songInfo, "id"),
                invokeString(songInfo, "getId"));
        return new SongMetadata(
                clean(songId),
                clean(title),
                clean(artist));
    }

    private static TimedLyricDocument.Line parseLine(Object sourceLine) {
        if (sourceLine == null) {
            return null;
        }
        String lineText = clean(readStringField(sourceLine, "a"));
        long lineStart = readLongField(sourceLine, "b", -1L);
        long lineDuration = readLongField(sourceLine, "c", 0L);
        if (lineStart < 0L) {
            return null;
        }

        ArrayList<RawWord> rawWords = readRawWords(sourceLine);
        boolean useOffset = shouldTreatWordStartAsOffset(lineStart, lineDuration, rawWords);
        String finalText = firstNonEmpty(lineText, joinedWordText(rawWords));
        ArrayList<TimedLyricDocument.Word> words =
                buildWordRanges(finalText, lineStart, rawWords, useOffset);
        if (isEmpty(finalText)) {
            return null;
        }
        long lineEnd = lineDuration > 0L
                ? lineStart + lineDuration
                : inferLineEnd(lineStart, words);
        return new TimedLyricDocument.Line(
                lineStart,
                lineEnd,
                finalText,
                "",
                words);
    }

    private static String joinedWordText(ArrayList<RawWord> rawWords) {
        if (rawWords == null || rawWords.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (RawWord rawWord : rawWords) {
            if (rawWord != null && !isEmpty(rawWord.text)) {
                builder.append(rawWord.text);
            }
        }
        return clean(builder.toString());
    }

    private static ArrayList<TimedLyricDocument.Word> buildWordRanges(
            String finalText,
            long lineStart,
            ArrayList<RawWord> rawWords,
            boolean useOffset) {
        ArrayList<TimedLyricDocument.Word> words = new ArrayList<>();
        if (isEmpty(finalText) || rawWords == null || rawWords.isEmpty()) {
            return words;
        }

        int cursor = 0;
        int fallbackCursor = 0;
        for (RawWord rawWord : rawWords) {
            if (rawWord == null || isEmpty(rawWord.text) || rawWord.durationMillis <= 0L) {
                continue;
            }
            int start;
            int end;
            if (rawWord.hasTextRange(finalText.length())) {
                start = rawWord.start;
                end = rawWord.end;
            } else {
                start = finalText.indexOf(rawWord.text, cursor);
                if (start < 0) {
                    start = finalText.indexOf(rawWord.text);
                }
                if (start < 0) {
                    start = Math.min(fallbackCursor, finalText.length());
                }
                end = Math.min(finalText.length(), start + rawWord.text.length());
            }
            if (start >= end) {
                continue;
            }
            long wordStart = useOffset
                    ? lineStart + rawWord.startMillis
                    : rawWord.startMillis;
            words.add(new TimedLyricDocument.Word(
                    wordStart,
                    wordStart + rawWord.durationMillis,
                    start,
                    end));
            cursor = Math.max(cursor, end);
            fallbackCursor = end;
        }
        return words;
    }

    private static ArrayList<RawWord> readRawWords(Object sourceLine) {
        Iterable<?> sourceWords = asIterable(readField(sourceLine, "g"));
        if (sourceWords == null) {
            return new ArrayList<>();
        }
        ArrayList<RawWord> words = new ArrayList<>();
        for (Object sourceWord : sourceWords) {
            long start = readLongField(sourceWord, "a", -1L);
            long duration = readLongField(sourceWord, "b", 0L);
            int textStart = (int) readLongField(sourceWord, "c", -1L);
            int textEnd = (int) readLongField(sourceWord, "d", -1L);
            String text = clean(firstStringField(sourceWord, WORD_TEXT_FIELDS));
            if (start >= 0L && !isEmpty(text)) {
                words.add(new RawWord(start, duration, text, textStart, textEnd));
            }
        }
        return words;
    }

    private static void mergeTranslationCandidates(
            Object lyricObject,
            ArrayList<TimedLyricDocument.Line> targetLines) {
        if (lyricObject == null || targetLines == null || targetLines.isEmpty()) {
            return;
        }

        ArrayList<TimedLyricDocument.Line> bestCandidate = null;
        int bestScore = 0;
        Class<?> current = lyricObject.getClass();
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if ("e".equals(field.getName())) {
                    continue;
                }
                Object value;
                try {
                    field.setAccessible(true);
                    value = field.get(lyricObject);
                } catch (Throwable ignored) {
                    continue;
                }
                ArrayList<TimedLyricDocument.Line> candidate =
                        filterLikelyMetadataLines(parseTranslationField(value));
                int score = countTranslationMatches(targetLines, candidate);
                if (score > bestScore) {
                    bestScore = score;
                    bestCandidate = candidate;
                }
            }
            current = current.getSuperclass();
        }

        if (bestCandidate == null
                || !hasEnoughTranslationMatches(bestScore, targetLines.size())) {
            return;
        }
        for (int i = 0; i < targetLines.size(); i++) {
            TimedLyricDocument.Line target = targetLines.get(i);
            TimedLyricDocument.Line translation = findClosestTranslation(
                    target,
                    bestCandidate,
                    120L);
            if (translation == null
                    || isEmpty(translation.text)
                    || sameTextVariant(target.text, translation.text)) {
                continue;
            }
            targetLines.set(i, new TimedLyricDocument.Line(
                    target.startMillis,
                    target.endMillis,
                    target.text,
                    translation.text,
                    target.words));
        }
    }

    private static ArrayList<TimedLyricDocument.Line> parseTranslationField(Object value) {
        if (value instanceof String && LyricInfoContract.containsTimedLrc((String) value)) {
            return copyLines(TimedLyricDocument.fromRawLrc((String) value));
        }
        Iterable<?> sourceLines = asIterable(value);
        return sourceLines == null
                ? new ArrayList<>()
                : parsePlainTimedLines(sourceLines);
    }

    private static ArrayList<TimedLyricDocument.Line> copyLines(TimedLyricDocument document) {
        ArrayList<TimedLyricDocument.Line> lines = new ArrayList<>();
        if (document == null) {
            return lines;
        }
        for (TimedLyricDocument.Line line : document.lines()) {
            if (line != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static ArrayList<TimedLyricDocument.Line> parsePlainTimedLines(Iterable<?> sourceLines) {
        ArrayList<TimedLyricDocument.Line> lines = new ArrayList<>();
        for (Object sourceLine : sourceLines) {
            if (sourceLine == null) {
                continue;
            }
            String text = clean(readStringField(sourceLine, "a"));
            long start = readLongField(sourceLine, "b", -1L);
            long duration = readLongField(sourceLine, "c", 0L);
            if (start < 0L || isEmpty(text)) {
                continue;
            }
            lines.add(new TimedLyricDocument.Line(
                    start,
                    duration > 0L ? start + duration : start + 3_000L,
                    text,
                    "",
                    Collections.emptyList()));
        }
        return lines;
    }

    private static ArrayList<TimedLyricDocument.Line> filterLikelyMetadataLines(
            List<TimedLyricDocument.Line> sourceLines) {
        ArrayList<TimedLyricDocument.Line> filtered = new ArrayList<>();
        if (sourceLines == null || sourceLines.isEmpty()) {
            return filtered;
        }
        boolean removedEarlyCredit = false;
        for (TimedLyricDocument.Line line : sourceLines) {
            if (line == null) {
                continue;
            }
            if (isLikelyMetadataLine(line, removedEarlyCredit)) {
                if (line.startMillis <= EARLY_METADATA_WINDOW_MS) {
                    removedEarlyCredit = true;
                }
                continue;
            }
            filtered.add(line);
        }
        return filtered;
    }

    private static boolean isLikelyMetadataLine(
            TimedLyricDocument.Line line,
            boolean removedEarlyCredit) {
        String text = clean(line.text);
        if (isEmpty(text)) {
            return true;
        }
        // Presentation cleanup now runs once in SystemUI with versioned user settings.
        // Keep only parsing-protected notices out of the candidate document here.
        return LyricMetadataFilter.isParsingProtectedLine(text);
    }

    private static boolean looksLikeTitleArtistCredit(String text) {
        String value = normalizeSpaces(text);
        if (value.length() < 5 || value.length() > 180) {
            return false;
        }
        int separator = creditSeparatorIndex(value);
        if (separator <= 0 || separator + 1 >= value.length()) {
            return false;
        }
        String title = value.substring(0, separator).trim();
        String artist = value.substring(separator + 1).trim();
        if (artist.startsWith("-")
                || artist.startsWith("–")
                || artist.startsWith("—")) {
            artist = artist.substring(1).trim();
        }
        return !title.isEmpty()
                && !artist.isEmpty()
                && containsLetter(title)
                && containsLetter(artist)
                && !endsLikeSentence(value);
    }

    private static int creditSeparatorIndex(String value) {
        String[] separators = {
                " - ",
                " – ",
                " — ",
                "- ",
                "– ",
                "— "
        };
        int best = -1;
        for (String separator : separators) {
            int index = value.lastIndexOf(separator);
            if (index > 0 && index + separator.length() < value.length()) {
                best = Math.max(best, index);
            }
        }
        return best;
    }

    private static boolean looksLikeCreditRoleLine(String text) {
        String value = normalizeSpaces(text);
        if (value.length() > 160) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        int colon = firstColonIndex(value);
        if (colon > 0 && colon <= 48) {
            String prefix = lower.substring(0, colon).trim();
            if (containsCreditRoleKeyword(prefix)) {
                return true;
            }
        }
        if (startsWithCreditRoleKeyword(lower)) {
            return true;
        }
        return lower.startsWith("op ")
                || lower.startsWith("sp ")
                || lower.startsWith("op:")
                || lower.startsWith("sp:");
    }

    private static boolean looksLikeArtistCreditContinuation(String text) {
        String value = normalizeSpaces(text);
        if (value.length() < 3 || value.length() > 96 || endsLikeSentence(value)) {
            return false;
        }
        return (value.indexOf('/') >= 0
                || value.indexOf('&') >= 0
                || value.indexOf(',') >= 0
                || value.indexOf('、') >= 0)
                && containsLetter(value)
                && countWhitespaceRuns(value) <= 4;
    }

    private static boolean hasEnoughTranslationMatches(int score, int targetLineCount) {
        if (score <= 0 || targetLineCount <= 0) {
            return false;
        }
        int required = targetLineCount <= 3
                ? targetLineCount
                : Math.max(6, (targetLineCount + 1) / 2);
        return score >= required;
    }

    private static int countTranslationMatches(
            ArrayList<TimedLyricDocument.Line> targetLines,
            ArrayList<TimedLyricDocument.Line> candidates) {
        if (targetLines == null || candidates == null || candidates.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (TimedLyricDocument.Line target : targetLines) {
            TimedLyricDocument.Line candidate = findClosestTranslation(target, candidates, 120L);
            if (candidate != null
                    && !isEmpty(candidate.text)
                    && !sameTextVariant(target.text, candidate.text)) {
                count++;
            }
        }
        return count;
    }

    private static TimedLyricDocument.Line findClosestTranslation(
            TimedLyricDocument.Line target,
            ArrayList<TimedLyricDocument.Line> candidates,
            long maxDistanceMillis) {
        if (target == null || candidates == null) {
            return null;
        }
        TimedLyricDocument.Line best = null;
        long bestDistance = Math.max(0L, maxDistanceMillis) + 1L;
        for (TimedLyricDocument.Line candidate : candidates) {
            long distance = Math.abs(candidate.startMillis - target.startMillis);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return bestDistance <= Math.max(0L, maxDistanceMillis) ? best : null;
    }

    private static boolean sameTextVariant(String first, String second) {
        return normalizeForComparison(first).equals(normalizeForComparison(second))
                || LockscreenIntegrationPolicy.sameLyricVariant(first, second);
    }

    private static String normalizeForComparison(String value) {
        String clean = clean(value).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(clean.length());
        for (int i = 0; i < clean.length(); i++) {
            char ch = clean.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static boolean shouldTreatWordStartAsOffset(
            long lineStart,
            long lineDuration,
            ArrayList<RawWord> words) {
        if (lineStart <= 0L || lineDuration <= 0L || words.isEmpty()) {
            return false;
        }
        int directMatches = countWordsNearLine(lineStart, lineDuration, words, false);
        int offsetMatches = countWordsNearLine(lineStart, lineDuration, words, true);
        return offsetMatches > directMatches;
    }

    private static int countWordsNearLine(
            long lineStart,
            long lineDuration,
            ArrayList<RawWord> words,
            boolean addLineStart) {
        long min = lineStart - 500L;
        long max = lineStart + lineDuration + 2_000L;
        int count = 0;
        for (RawWord word : words) {
            long start = addLineStart ? lineStart + word.startMillis : word.startMillis;
            if (start >= min && start <= max) {
                count++;
            }
        }
        return count;
    }

    private static long inferLineEnd(long lineStart, ArrayList<TimedLyricDocument.Word> words) {
        long end = lineStart + 3_000L;
        for (TimedLyricDocument.Word word : words) {
            end = Math.max(end, word.endMillis);
        }
        return end;
    }

    private static Iterable<?> asIterable(Object value) {
        if (value instanceof Iterable) {
            return (Iterable<?>) value;
        }
        if (value instanceof Object[]) {
            ArrayList<Object> values = new ArrayList<>();
            Collections.addAll(values, (Object[]) value);
            return values;
        }
        return null;
    }

    private static String parseSongInfoValue(String value, String key) {
        if (isEmpty(value) || isEmpty(key)) {
            return "";
        }
        Matcher matcher = SONG_INFO_VALUE.matcher(value);
        while (matcher.find()) {
            if (key.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return "";
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null || isEmpty(fieldName)) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static String firstStringField(Object target, String... fieldNames) {
        if (fieldNames == null) {
            return "";
        }
        for (String fieldName : fieldNames) {
            String value = readStringField(target, fieldName);
            if (!isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private static String readStringField(Object target, String fieldName) {
        Object value = readField(target, fieldName);
        return value == null ? "" : String.valueOf(value);
    }

    private static long readLongField(Object target, String fieldName, long fallback) {
        Object value = readField(target, fieldName);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (Throwable ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String invokeString(Object target, String methodName) {
        if (target == null || isEmpty(methodName)) {
            return "";
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                if (method.getParameterCount() != 0) {
                    return "";
                }
                method.setAccessible(true);
                Object value = method.invoke(target);
                return value == null ? "" : String.valueOf(value);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return "";
            }
        }
        return "";
    }

    private static String clean(String value) {
        String clean = LyricTextSanitizer.removeIgnorableCharacters(nullToEmpty(value)).trim();
        return restoreUtf8TextMisdecodedAsGb18030(clean);
    }

    private static String normalizeSpaces(String value) {
        return clean(value).replaceAll("\\s+", " ").trim();
    }

    private static int firstColonIndex(String value) {
        int ascii = value.indexOf(':');
        int fullWidth = value.indexOf('：');
        if (ascii < 0) {
            return fullWidth;
        }
        if (fullWidth < 0) {
            return ascii;
        }
        return Math.min(ascii, fullWidth);
    }

    private static boolean startsWithCreditRoleKeyword(String lowerValue) {
        for (String keyword : CREDIT_ROLE_KEYWORDS) {
            String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
            if (lowerValue.equals(lowerKeyword)
                    || lowerValue.startsWith(lowerKeyword + ":")
                    || lowerValue.startsWith(lowerKeyword + "：")) {
                return true;
            }
            if (lowerValue.startsWith(lowerKeyword + " by ")
                    || (isStrongStandaloneCreditRole(lowerKeyword)
                    && lowerValue.startsWith(lowerKeyword + " "))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isStrongStandaloneCreditRole(String lowerKeyword) {
        return lowerKeyword.length() > 3
                && ("lyrics".equals(lowerKeyword)
                || "lyricist".equals(lowerKeyword)
                || "composer".equals(lowerKeyword)
                || "arranger".equals(lowerKeyword)
                || "producer".equals(lowerKeyword)
                || "produced".equals(lowerKeyword)
                || "production".equals(lowerKeyword)
                || "publisher".equals(lowerKeyword)
                || "copyright".equals(lowerKeyword)
                || "作词".equals(lowerKeyword)
                || "作詞".equals(lowerKeyword)
                || "作曲".equals(lowerKeyword)
                || "编曲".equals(lowerKeyword)
                || "編曲".equals(lowerKeyword)
                || "制作".equals(lowerKeyword)
                || "製作".equals(lowerKeyword)
                || "出品".equals(lowerKeyword)
                || "监制".equals(lowerKeyword)
                || "監製".equals(lowerKeyword));
    }

    private static boolean containsCreditRoleKeyword(String lowerValue) {
        for (String keyword : CREDIT_ROLE_KEYWORDS) {
            if (lowerValue.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsLetter(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            if (Character.isLetter(codePoint)) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean endsLikeSentence(String value) {
        if (isEmpty(value)) {
            return false;
        }
        char last = value.charAt(value.length() - 1);
        return last == '.'
                || last == '!'
                || last == '?'
                || last == '。'
                || last == '！'
                || last == '？';
    }

    private static int countWhitespaceRuns(String value) {
        int count = 0;
        boolean inWhitespace = false;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                if (!inWhitespace) {
                    count++;
                    inWhitespace = true;
                }
            } else {
                inWhitespace = false;
            }
        }
        return count;
    }

    private static String restoreUtf8TextMisdecodedAsGb18030(String value) {
        if (isEmpty(value)) {
            return "";
        }
        int originalMojibakeScore = mojibakeScore(value);
        if (originalMojibakeScore < 2) {
            return value;
        }
        String restored;
        try {
            restored = new String(value.getBytes(GB18030), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return value;
        }
        restored = LyricTextSanitizer.removeIgnorableCharacters(restored).trim();
        if (isEmpty(restored) || restored.equals(value)) {
            return value;
        }
        int restoredMojibakeScore = mojibakeScore(restored);
        int originalQuality = textQualityScore(value, originalMojibakeScore);
        int restoredQuality = textQualityScore(restored, restoredMojibakeScore);
        if (restoredMojibakeScore < originalMojibakeScore
                && restoredQuality >= originalQuality - 4) {
            return restored;
        }
        return value;
    }

    private static int mojibakeScore(String value) {
        if (isEmpty(value)) {
            return 0;
        }
        int score = 0;
        for (String token : UTF8_AS_GB18030_MOJIBAKE_TOKENS) {
            int index = value.indexOf(token);
            while (index >= 0) {
                score++;
                index = value.indexOf(token, index + token.length());
            }
        }
        if (value.indexOf('�') >= 0) {
            score += 2;
        }
        return score;
    }

    private static int textQualityScore(String value, int mojibakeScore) {
        int score = -mojibakeScore * 4;
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                score += 3;
            } else if (Character.isLetterOrDigit(codePoint)) {
                score += 1;
            } else if (codePoint == 0xFFFD) {
                score -= 6;
            } else if (Character.isISOControl(codePoint)) {
                score -= 8;
            }
            index += Character.charCount(codePoint);
        }
        return score;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private static String firstNonPlaceholder(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isEmpty(value) && !isSongInfoPlaceholder(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean isSongInfoPlaceholder(String value) {
        String normalized = nullToEmpty(value).trim();
        return "0".equals(normalized) || "1".equals(normalized);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static final class SongMetadata {
        final String songId;
        final String title;
        final String artist;

        SongMetadata(String songId, String title, String artist) {
            this.songId = nullToEmpty(songId).trim();
            this.title = nullToEmpty(title).trim();
            this.artist = nullToEmpty(artist).trim();
        }

        String trackHintKey() {
            return isEmpty(title) ? "" : TrackIdentity.buildKey(title, artist);
        }
    }

    private static final class RawWord {
        final long startMillis;
        final long durationMillis;
        final String text;
        final int start;
        final int end;

        RawWord(long startMillis, long durationMillis, String text, int start, int end) {
            this.startMillis = Math.max(0L, startMillis);
            this.durationMillis = Math.max(0L, durationMillis);
            this.text = nullToEmpty(text);
            this.start = start;
            this.end = end;
        }

        boolean hasTextRange(int textLength) {
            return start >= 0 && end > start && end <= Math.max(0, textLength);
        }
    }
}
