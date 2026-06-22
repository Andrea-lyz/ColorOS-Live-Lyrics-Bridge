package io.github.andrealtb.lockscreenlyrics;

import java.util.Locale;

/**
 * Classifies a source callback that did not contain usable timed lyrics.
 *
 * <p>Parse failure is intentionally not inferred from arbitrary non-empty text. Only an adapter
 * that hooks a real parser failure signal can report {@link LyricSourceEvent.Outcome#PARSE_FAILED}.
 * Constructor placeholders, source labels and intermediate misses must not enter the terminal
 * transaction queue.</p>
 */
final class LyricResultClassifier {
    private LyricResultClassifier() {
    }

    static LyricSourceEvent.Outcome classifyEmptyResult(String source) {
        String normalized = source == null
                ? ""
                : source.toUpperCase(Locale.ROOT).replace(' ', '_');
        if (normalized.contains("NOT_FOUND")
                || normalized.contains("NO_LYRIC")
                || normalized.contains("EMPTY")
                || normalized.contains("NONE")) {
            return LyricSourceEvent.Outcome.NO_LYRIC;
        }
        return LyricSourceEvent.Outcome.SOURCE_MISS;
    }
}
