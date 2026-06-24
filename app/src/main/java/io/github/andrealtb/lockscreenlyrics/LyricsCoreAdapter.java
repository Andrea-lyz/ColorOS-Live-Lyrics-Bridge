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
    private static final Pattern LRC_TIME_TAG = Pattern.compile(
            "\\[(\\d{1,3}):([0-5]?\\d)(?:[\\.:](\\d{1,3}))?\\]");

    private LyricsCoreAdapter() {
    }

    static ParsedLyrics parse(String content) {
        if (content == null || content.trim().isEmpty()) {
            return ParsedLyrics.EMPTY;
        }

        ArrayList<ParsedLine> lines = new ArrayList<>();
        try {
            // AutoParser keeps mutable parser-selection state. SystemUI may deliver overlapping
            // lyricInfo updates while a track is changing, so keep one parse transaction atomic.
            synchronized (AUTO_PARSER) {
                if (AUTO_PARSER.canParse(content)) {
                    SyncedLyrics parsed = AUTO_PARSER.parse(content);
                    for (ISyncedLine sourceLine : parsed.getLines()) {
                        ParsedLine line = toParsedLine(sourceLine);
                        if (line != null && !line.text.trim().isEmpty()) {
                            lines.add(line);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // The plain LRC fallback below must remain available when format auto-detection fails.
        }

        ParsedLyrics fallback = parsePlainLrc(content);
        if (lines.isEmpty()
                || (!fallback.lines.isEmpty()
                && (lines.size() * 2 < fallback.lines.size()
                || hasReversedBilingualPrimary(lines, fallback.lines)))) {
            return fallback;
        }
        return lines.isEmpty()
                ? ParsedLyrics.EMPTY
                : new ParsedLyrics(lines);
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
            Matcher firstTag = LRC_TIME_TAG.matcher(line);
            if (firstTag.find() && firstTag.start() == 0) {
                long timeMillis = parseTimeMillis(firstTag);
                String text = stripLrcTimeTags(line, firstTag.end());
                if (!text.isEmpty()) {
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

            int primaryIndex = findPrimaryTextIndex(texts);
            String text = texts.get(primaryIndex);
            String translation = "";
            for (int textIndex = 0; textIndex < texts.size(); textIndex++) {
                if (textIndex == primaryIndex) {
                    continue;
                }
                String candidate = texts.get(textIndex);
                if (LyricLineVariantSelector.isLikelyJapaneseRomanization(text, candidate)) {
                    continue;
                }
                if (!candidate.equals(text)) {
                    translation = candidate;
                    break;
                }
            }

            long startMillis = group.getKey();
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

    private static int findPrimaryTextIndex(List<String> texts) {
        return LyricLineVariantSelector.findPrimaryTextIndex(texts);
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

    private static String stripLrcTimeTags(String line, int startIndex) {
        Matcher matcher = LRC_TIME_TAG.matcher(line);
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
            return new ParsedLine(
                    sourceLine.getStart(),
                    sourceLine.getEnd(),
                    text.toString(),
                    cleanLyricText(karaokeLine.getTranslation()),
                    syllables);
        }

        if (sourceLine instanceof SyncedLine) {
            SyncedLine syncedLine = (SyncedLine) sourceLine;
            String text = cleanLyricText(syncedLine.getContent());
            return new ParsedLine(
                    sourceLine.getStart(),
                    sourceLine.getEnd(),
                    text,
                    cleanLyricText(syncedLine.getTranslation()),
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
