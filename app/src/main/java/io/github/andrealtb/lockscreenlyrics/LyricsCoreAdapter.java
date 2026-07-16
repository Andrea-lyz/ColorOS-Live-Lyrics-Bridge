package io.github.andrealtb.lockscreenlyrics;

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine;
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics;
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine;
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable;
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine;
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small Java-facing boundary around Accompanist Lyrics Core.
 *
 * <p>Keeping third-party model types here lets the Xposed hook use its existing renderer model
 * and gives us one place to absorb upstream API changes.</p>
 */
final class LyricsCoreAdapter {
    private static final AutoParser AUTO_PARSER = new AutoParser();
    private static final Pattern LRC_OR_WORD_TIME_TAG = Pattern.compile(
            "[\\[<](\\d{1,3}):([0-5]?\\d)(?:[\\.:](\\d{1,3}))?[\\]>]");

    private LyricsCoreAdapter() {
    }

    static ParsedLyrics parse(String content) {
        if (content == null || content.trim().isEmpty()) {
            return ParsedLyrics.EMPTY;
        }

        String parserContent = normalizeBracketInlineTiming(content);
        ArrayList<ParsedLine> lines = new ArrayList<>();
        try {
            // AutoParser keeps mutable parser-selection state. SystemUI may deliver overlapping
            // lyricInfo updates while a track is changing, so keep one parse transaction atomic.
            synchronized (AUTO_PARSER) {
                if (AUTO_PARSER.canParse(parserContent)) {
                    SyncedLyrics parsed = AUTO_PARSER.parse(parserContent);
                    for (ISyncedLine sourceLine : parsed.getLines()) {
                        ParsedLine line = toParsedLine(sourceLine);
                        if (line != null && !line.text.trim().isEmpty()) {
                            lines.add(line);
                        }
                    }
                    if (!lines.isEmpty()) {
                        lines = mergeSameTimestampVariants(lines);
                    }
                }
            }
        } catch (Throwable ignored) {
            // The plain LRC fallback below must remain available when format auto-detection fails.
        }

        ParsedLyrics fallback = parsePlainLrc(content);
        if (!lines.isEmpty() && !fallback.lines.isEmpty()) {
            lines = restoreCollapsedOpeningInfoLines(lines, fallback.lines);
        }
        if (lines.isEmpty()
                || (!fallback.lines.isEmpty()
                && (lines.size() * 2 < fallback.lines.size()
                || hasReversedBilingualPrimary(lines, fallback.lines)
                || hasRomanizationTranslationMismatch(lines, fallback.lines)))) {
            return fallback;
        }
        return lines.isEmpty()
                ? ParsedLyrics.EMPTY
                : new ParsedLyrics(lines);
    }

    /**
     * Accompanist may treat two very close LRC timestamps as one bilingual line. That is useful
     * for slightly offset translations, but it must not collapse independently timed opening
     * credits such as {@code 作词} and {@code 作曲}. The official OPlus list keeps those timestamps
     * as separate rows, so a collapsed parser model shifts every later RecyclerView index.
     *
     * <p>Only parser lines that demonstrably absorbed a non-lyric information line are replaced.
     * All unaffected parsed lines retain their upstream word timing.</p>
     */
    private static ArrayList<ParsedLine> restoreCollapsedOpeningInfoLines(
            List<ParsedLine> parsedLines,
            List<ParsedLine> fallbackLines) {
        ArrayList<ParsedLine> restored = new ArrayList<>(parsedLines.size());
        for (int index = 0; index < parsedLines.size(); index++) {
            ParsedLine parsed = parsedLines.get(index);
            long nextParsedStart = index + 1 < parsedLines.size()
                    ? parsedLines.get(index + 1).startMillis
                    : Long.MAX_VALUE;
            ArrayList<ParsedLine> fallbackWindow = new ArrayList<>();
            boolean containsParsedMain = false;
            boolean containsAbsorbedTranslation = false;
            boolean containsOpeningInfo = false;
            for (ParsedLine fallback : fallbackLines) {
                if (fallback.startMillis < parsed.startMillis
                        || fallback.startMillis >= nextParsedStart) {
                    continue;
                }
                fallbackWindow.add(fallback);
                if (sameCleanText(fallback.text, parsed.text)) {
                    containsParsedMain = true;
                }
                if (!parsed.translation.isEmpty()
                        && fallback.startMillis != parsed.startMillis
                        && sameCleanText(fallback.text, parsed.translation)) {
                    containsAbsorbedTranslation = true;
                }
                if (LyricMetadataFilter.isNonLyricInfoLine(
                        fallback.text,
                        fallback.startMillis)) {
                    containsOpeningInfo = true;
                }
            }
            if (fallbackWindow.size() > 1
                    && containsParsedMain
                    && containsAbsorbedTranslation
                    && containsOpeningInfo) {
                restored.addAll(fallbackWindow);
            } else {
                restored.add(parsed);
            }
        }
        restored.sort(Comparator.comparingLong(line -> line.startMillis));
        return restored;
    }

    private static boolean sameCleanText(String left, String right) {
        return cleanLyricText(left).equals(cleanLyricText(right));
    }

    private static String normalizeBracketInlineTiming(String content) {
        StringBuilder normalized = new StringBuilder(content.length());
        int lineStart = 0;
        int length = content.length();
        while (lineStart < length) {
            int lineEnd = lineStart;
            while (lineEnd < length) {
                char value = content.charAt(lineEnd);
                if (value == '\n' || value == '\r') {
                    break;
                }
                lineEnd++;
            }

            normalized.append(normalizeBracketInlineTimingLine(
                    content.substring(lineStart, lineEnd)));
            if (lineEnd < length) {
                char newline = content.charAt(lineEnd);
                normalized.append(newline);
                if (newline == '\r'
                        && lineEnd + 1 < length
                        && content.charAt(lineEnd + 1) == '\n') {
                    lineEnd++;
                    normalized.append('\n');
                }
            }
            lineStart = lineEnd + 1;
        }
        return normalized.toString();
    }

    private static String normalizeBracketInlineTimingLine(String line) {
        Matcher matcher = LRC_OR_WORD_TIME_TAG.matcher(line);
        StringBuilder normalized = null;
        int segmentStart = 0;
        boolean seenLyricText = false;
        while (matcher.find()) {
            if (containsLyricTimingText(line, segmentStart, matcher.start())) {
                seenLyricText = true;
            }
            boolean bracketTag = line.charAt(matcher.start()) == '['
                    && line.charAt(matcher.end() - 1) == ']';
            boolean inlineBracketTag = seenLyricText && bracketTag;
            if (inlineBracketTag && normalized == null) {
                normalized = new StringBuilder(line.length());
                normalized.append(line, 0, segmentStart);
            }
            if (normalized != null) {
                normalized.append(line, segmentStart, matcher.start());
                if (inlineBracketTag) {
                    normalized.append(toAngleTimeTag(matcher));
                } else {
                    normalized.append(line, matcher.start(), matcher.end());
                }
            }
            segmentStart = matcher.end();
        }
        if (normalized == null) {
            return line;
        }
        normalized.append(line, segmentStart, line.length());
        return ensureFirstBracketWordTiming(normalized.toString());
    }

    private static String ensureFirstBracketWordTiming(String line) {
        Matcher firstTag = LRC_OR_WORD_TIME_TAG.matcher(line);
        if (!firstTag.find()
                || firstTag.start() != 0
                || line.charAt(firstTag.start()) != '['
                || line.charAt(firstTag.end() - 1) != ']') {
            return line;
        }

        Matcher nextTag = LRC_OR_WORD_TIME_TAG.matcher(line);
        nextTag.region(firstTag.end(), line.length());
        if (!nextTag.find()
                || !containsLyricTimingText(line, firstTag.end(), nextTag.start())) {
            return line;
        }
        return line.substring(0, firstTag.end())
                + toAngleTimeTag(firstTag)
                + line.substring(firstTag.end());
    }

    private static String toAngleTimeTag(Matcher matcher) {
        StringBuilder tag = new StringBuilder();
        tag.append('<').append(matcher.group(1)).append(':').append(matcher.group(2));
        if (matcher.group(3) != null) {
            tag.append('.').append(matcher.group(3));
        }
        return tag.append('>').toString();
    }

    static ParsedLyrics parsePlainLrc(String content) {
        if (content == null || content.trim().isEmpty()) {
            return ParsedLyrics.EMPTY;
        }

        LinkedHashMap<Long, ArrayList<String>> grouped = new LinkedHashMap<>();
        int lineStart = 0;
        int length = content.length();
        while (lineStart < length) {
            int lineEnd = lineStart;
            while (lineEnd < length) {
                char value = content.charAt(lineEnd);
                if (value == '\n' || value == '\r') {
                    break;
                }
                lineEnd++;
            }

            String line = content.substring(lineStart, lineEnd).trim();
            Matcher firstTag = LRC_OR_WORD_TIME_TAG.matcher(line);
            if (firstTag.find() && firstTag.start() == 0) {
                long timeMillis = parseTimeMillis(firstTag);
                String text = stripLrcTimeTags(line, firstTag.end());
                if (!text.isEmpty()
                        && !LyricMetadataFilter.isParsingProtectedLine(text)) {
                    grouped.computeIfAbsent(timeMillis, ignored -> new ArrayList<>()).add(text);
                }
            }

            if (lineEnd < length && content.charAt(lineEnd) == '\r'
                    && lineEnd + 1 < length && content.charAt(lineEnd + 1) == '\n') {
                lineEnd++;
            }
            lineStart = lineEnd + 1;
        }
        if (grouped.isEmpty()) {
            return ParsedLyrics.EMPTY;
        }

        ArrayList<Map.Entry<Long, ArrayList<String>>> groups =
                new ArrayList<>(grouped.entrySet());
        groups.sort(Comparator.comparingLong(Map.Entry::getKey));
        ArrayList<ParsedLine> lines = new ArrayList<>(groups.size());
        for (int index = 0; index < groups.size(); index++) {
            Map.Entry<Long, ArrayList<String>> group = groups.get(index);
            ArrayList<String> texts = group.getValue();
            if (texts.isEmpty()) {
                continue;
            }

            long startMillis = group.getKey();
            LyricLaneClassifier.Result lanes =
                    LyricLaneClassifier.classify(texts, startMillis);
            String text = lanes.primaryText();
            String translation = lanes.firstTranslation();
            long endMillis = index + 1 < groups.size()
                    ? Math.max(startMillis + 80L, groups.get(index + 1).getKey())
                    : startMillis + 3_000L;
            lines.add(new ParsedLine(
                    startMillis,
                    endMillis,
                    text,
                    translation,
                    Collections.singletonList(new ParsedSyllable(
                            startMillis,
                            endMillis,
                            0,
                            text.length()))));
        }
        return lines.isEmpty() ? ParsedLyrics.EMPTY : new ParsedLyrics(lines);
    }

    private static boolean containsLatinLetter(String text) {
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if ((value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z')) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasReversedBilingualPrimary(
            List<ParsedLine> parsedLines,
            List<ParsedLine> fallbackLines) {
        if (parsedLines.isEmpty() || fallbackLines.isEmpty()) {
            return false;
        }
        LinkedHashMap<Long, ParsedLine> parsedByStart = new LinkedHashMap<>();
        for (ParsedLine line : parsedLines) {
            parsedByStart.putIfAbsent(line.startMillis, line);
        }
        for (ParsedLine fallback : fallbackLines) {
            if (fallback.translation.isEmpty()
                    || !containsLatinLetter(fallback.text)
                    || containsCjkCharacter(fallback.text)
                    || !containsCjkCharacter(fallback.translation)) {
                continue;
            }
            ParsedLine parsed = parsedByStart.get(fallback.startMillis);
            if (parsed != null && parsed.text.equals(fallback.translation)) {
                return true;
            }
        }
        return false;
    }

    private static ArrayList<ParsedLine> mergeSameTimestampVariants(List<ParsedLine> sourceLines) {
        LinkedHashMap<Long, ArrayList<ParsedLine>> grouped = new LinkedHashMap<>();
        for (ParsedLine line : sourceLines) {
            grouped.computeIfAbsent(line.startMillis, ignored -> new ArrayList<>()).add(line);
        }

        ArrayList<ParsedLine> merged = new ArrayList<>(grouped.size());
        for (ArrayList<ParsedLine> group : grouped.values()) {
            if (group.size() == 1) {
                merged.add(group.get(0));
                continue;
            }
            ArrayList<String> texts = new ArrayList<>(group.size());
            for (ParsedLine line : group) {
                texts.add(line.text);
            }
            LyricLaneClassifier.Result lanes =
                    LyricLaneClassifier.classify(texts, group.get(0).startMillis);
            int primaryIndex = lanes.primaryIndex();
            ParsedLine primary = group.get(primaryIndex);
            String translation = selectMergedTranslation(group, texts, lanes);
            merged.add(new ParsedLine(
                    primary.startMillis,
                    primary.endMillis,
                    primary.text,
                    translation,
                    primary.syllables));
        }
        return merged;
    }

    private static String selectMergedTranslation(
            ArrayList<ParsedLine> group,
            ArrayList<String> texts,
            LyricLaneClassifier.Result lanes) {
        int primaryIndex = lanes.primaryIndex();
        ParsedLine primary = group.get(primaryIndex);
        String translation = "";
        if (isUsableTranslationCandidate(texts, primaryIndex, primary.translation)) {
            translation = primary.translation;
        }
        for (int index = 0; index < group.size(); index++) {
            if (index == primaryIndex) {
                continue;
            }
            ParsedLine line = group.get(index);
            if (lanes.laneAt(index) == LyricLaneClassifier.Lane.TRANSLATION
                    && isUsableTranslationCandidate(texts, primaryIndex, line.text)) {
                return line.text;
            }
            if (translation.isEmpty()
                    && isUsableTranslationCandidate(texts, primaryIndex, line.translation)) {
                translation = line.translation;
            }
        }
        return translation;
    }

    private static boolean hasRomanizationTranslationMismatch(
            List<ParsedLine> parsedLines,
            List<ParsedLine> fallbackLines) {
        if (parsedLines.isEmpty() || fallbackLines.isEmpty()) {
            return false;
        }
        LinkedHashMap<Long, ParsedLine> fallbackByStart = new LinkedHashMap<>();
        for (ParsedLine line : fallbackLines) {
            fallbackByStart.putIfAbsent(line.startMillis, line);
        }
        for (ParsedLine parsed : parsedLines) {
            boolean parsedTranslationIsRomanization =
                    !parsed.translation.isEmpty()
                            && LyricLineVariantSelector.isLikelyJapaneseRomanizationLine(
                            parsed.translation);
            boolean parsedTextIsRomanization =
                    LyricLineVariantSelector.isLikelyJapaneseRomanizationLine(parsed.text);
            if (!parsedTranslationIsRomanization && !parsedTextIsRomanization) {
                continue;
            }
            ParsedLine fallback = fallbackByStart.get(parsed.startMillis);
            if (fallback != null
                    && !fallback.translation.isEmpty()
                    && containsCjkCharacter(fallback.translation)
                    && !LyricLineVariantSelector.isLikelyJapaneseRomanizationLine(
                    fallback.translation)) {
                if (parsedTranslationIsRomanization
                        || (parsedTextIsRomanization
                        && containsCjkCharacter(fallback.text))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isLikelyRomanizationVariant(
            List<String> texts,
            int primaryIndex,
            String candidate) {
        return LyricLineVariantSelector.isLikelyPhoneticVariant(
                texts,
                primaryIndex,
                candidate);
    }

    private static boolean isUsableTranslationCandidate(
            List<String> texts,
            int primaryIndex,
            String candidate) {
        String cleanCandidate = nullToEmpty(candidate).trim();
        if (cleanCandidate.isEmpty()) {
            return false;
        }
        if (LyricMetadataFilter.isParsingProtectedLine(cleanCandidate)) {
            return false;
        }
        String primary = primaryIndex >= 0 && primaryIndex < texts.size()
                ? nullToEmpty(texts.get(primaryIndex)).trim()
                : "";
        if (cleanCandidate.equals(primary)) {
            return false;
        }
        return !isLikelyRomanizationVariant(texts, primaryIndex, cleanCandidate);
    }

    private static boolean containsCjkCharacter(String text) {
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean containsLyricTimingText(String text, int start, int end) {
        int safeStart = Math.max(0, Math.min(start, text.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
        while (safeStart < safeEnd) {
            int codePoint = text.codePointAt(safeStart);
            if (Character.isLetterOrDigit(codePoint)) {
                return true;
            }
            safeStart += Character.charCount(codePoint);
        }
        return false;
    }

    private static String stripLrcTimeTags(String line, int startIndex) {
        Matcher matcher = LRC_OR_WORD_TIME_TAG.matcher(line);
        matcher.region(startIndex, line.length());
        if (!matcher.find()) {
            return cleanLyricText(line.substring(startIndex));
        }

        StringBuilder stripped = new StringBuilder(line.length() - startIndex);
        int segmentStart = startIndex;
        do {
            stripped.append(line, segmentStart, matcher.start());
            segmentStart = matcher.end();
        } while (matcher.find());
        stripped.append(line, segmentStart, line.length());
        return cleanLyricText(stripped.toString());
    }

    private static long parseTimeMillis(Matcher matcher) {
        long minutes = Long.parseLong(matcher.group(1));
        long seconds = Long.parseLong(matcher.group(2));
        String fraction = matcher.group(3);
        long millis = 0L;
        if (fraction != null && !fraction.isEmpty()) {
            if (fraction.length() == 1) {
                millis = Long.parseLong(fraction) * 100L;
            } else if (fraction.length() == 2) {
                millis = Long.parseLong(fraction) * 10L;
            } else {
                millis = Long.parseLong(fraction.substring(0, 3));
            }
        }
        return minutes * 60_000L + seconds * 1_000L + millis;
    }

    private static ParsedLine toParsedLine(ISyncedLine sourceLine) {
        if (sourceLine instanceof KaraokeLine) {
            KaraokeLine karaokeLine = (KaraokeLine) sourceLine;
            ArrayList<ParsedSyllable> syllables = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (KaraokeSyllable syllable : karaokeLine.getSyllables()) {
                String content = LyricTextSanitizer.removeIgnorableCharacters(
                        nullToEmpty(syllable.getContent()));
                int start = text.length();
                text.append(content);
                if (text.length() > start) {
                    syllables.add(new ParsedSyllable(
                            syllable.getStart(),
                            syllable.getEnd(),
                            start,
                            text.length()));
                }
            }
            String cleanText = text.toString();
            if (LyricMetadataFilter.isParsingProtectedLine(cleanText)) {
                return null;
            }
            String translation = cleanLyricText(karaokeLine.getTranslation());
            if (LyricMetadataFilter.isParsingProtectedLine(translation)) {
                translation = "";
            }
            return new ParsedLine(
                    sourceLine.getStart(),
                    sourceLine.getEnd(),
                    cleanText,
                    translation,
                    syllables);
        }

        if (sourceLine instanceof SyncedLine) {
            SyncedLine syncedLine = (SyncedLine) sourceLine;
            String text = cleanLyricText(syncedLine.getContent());
            if (LyricMetadataFilter.isParsingProtectedLine(text)) {
                return null;
            }
            String translation = cleanLyricText(syncedLine.getTranslation());
            if (LyricMetadataFilter.isParsingProtectedLine(translation)) {
                translation = "";
            }
            return new ParsedLine(
                    sourceLine.getStart(),
                    sourceLine.getEnd(),
                    text,
                    translation,
                    Collections.singletonList(new ParsedSyllable(
                            sourceLine.getStart(),
                            sourceLine.getEnd(),
                            0,
                            text.length())));
        }
        return null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String cleanLyricText(String value) {
        return LyricTextSanitizer.removeIgnorableCharacters(nullToEmpty(value)).trim();
    }

    static final class ParsedLyrics {
        static final ParsedLyrics EMPTY = new ParsedLyrics(Collections.emptyList());

        final List<ParsedLine> lines;

        ParsedLyrics(List<ParsedLine> lines) {
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
        }
    }

    static final class ParsedLine {
        final long startMillis;
        final long endMillis;
        final String text;
        final String translation;
        final List<ParsedSyllable> syllables;

        ParsedLine(
                long startMillis,
                long endMillis,
                String text,
                String translation,
                List<ParsedSyllable> syllables) {
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.text = text;
            this.translation = translation;
            this.syllables = Collections.unmodifiableList(new ArrayList<>(syllables));
        }
    }

    static final class ParsedSyllable {
        final long startMillis;
        final long endMillis;
        final int start;
        final int end;

        ParsedSyllable(long startMillis, long endMillis, int start, int end) {
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.start = start;
            this.end = end;
        }
    }
}
