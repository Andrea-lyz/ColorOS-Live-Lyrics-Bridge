package io.github.andrealtb.lockscreenlyrics;

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine;
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics;
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine;
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable;
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine;
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small Java-facing boundary around Accompanist Lyrics Core.
 *
 * <p>Keeping third-party model types here lets the Xposed hook use its existing renderer model
 * and gives us one place to absorb upstream API changes.</p>
 */
final class LyricsCoreAdapter {
    private static final AutoParser AUTO_PARSER = new AutoParser();

    private LyricsCoreAdapter() {
    }

    static ParsedLyrics parse(String content) {
        if (content == null || content.trim().isEmpty()) {
            return ParsedLyrics.EMPTY;
        }

        if (!AUTO_PARSER.canParse(content)) {
            return ParsedLyrics.EMPTY;
        }

        SyncedLyrics parsed = AUTO_PARSER.parse(content);
        ArrayList<ParsedLine> lines = new ArrayList<>();
        for (ISyncedLine sourceLine : parsed.getLines()) {
            ParsedLine line = toParsedLine(sourceLine);
            if (line != null && !line.text.trim().isEmpty()) {
                lines.add(line);
            }
        }
        return lines.isEmpty()
                ? ParsedLyrics.EMPTY
                : new ParsedLyrics(lines);
    }

    private static ParsedLine toParsedLine(ISyncedLine sourceLine) {
        if (sourceLine instanceof KaraokeLine) {
            KaraokeLine karaokeLine = (KaraokeLine) sourceLine;
            ArrayList<ParsedSyllable> syllables = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (KaraokeSyllable syllable : karaokeLine.getSyllables()) {
                String content = nullToEmpty(syllable.getContent());
                int start = text.length();
                text.append(content);
                syllables.add(new ParsedSyllable(
                        syllable.getStart(),
                        syllable.getEnd(),
                        start,
                        text.length()));
            }
            return new ParsedLine(
                    sourceLine.getStart(),
                    sourceLine.getEnd(),
                    text.toString(),
                    nullToEmpty(karaokeLine.getTranslation()),
                    syllables);
        }

        if (sourceLine instanceof SyncedLine) {
            SyncedLine syncedLine = (SyncedLine) sourceLine;
            String text = nullToEmpty(syncedLine.getContent());
            return new ParsedLine(
                    sourceLine.getStart(),
                    sourceLine.getEnd(),
                    text,
                    nullToEmpty(syncedLine.getTranslation()),
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
