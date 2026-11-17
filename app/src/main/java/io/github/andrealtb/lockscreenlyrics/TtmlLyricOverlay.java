package io.github.andrealtb.lockscreenlyrics;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Parsed view of the optional {@code lyricInfo.ttml} document.
 *
 * <p>The document never replaces the timed LRC lanes. It is used in two ways: as an annotation
 * source (duet side and background vocals, matched onto model lines by time and text after the
 * regular assembly and opening cleanup have run), and as a fallback producer of the enhanced-LRC
 * and translation lanes when a payload carries TTML but not those lanes. Keeping the LRC lanes
 * authoritative means cleanup, official-row aliases and translation merging stay on the one
 * existing path.</p>
 *
 * <p>Nothing here throws: an oversized or unparsable document is {@link #EMPTY}.</p>
 */
final class TtmlLyricOverlay {
    /** Documents above this size are ignored rather than parsed inside SystemUI. */
    static final int MAX_DOCUMENT_CHARS = 512 * 1024;
    static final TtmlLyricOverlay EMPTY = new TtmlLyricOverlay(Collections.emptyList());

    private static final Object CACHE_LOCK = new Object();
    private static String cachedDocument;
    private static TtmlLyricOverlay cachedOverlay;

    final List<LyricsCoreAdapter.ParsedLine> lines;
    /** True only when both sides occur, so a single-singer document never moves any line. */
    final boolean hasDuet;
    final int accompanimentCount;
    private String enhancedLrc;
    private String translationLrc;

    private TtmlLyricOverlay(List<LyricsCoreAdapter.ParsedLine> lines) {
        this.lines = lines;
        boolean start = false;
        boolean end = false;
        int accompaniment = 0;
        for (LyricsCoreAdapter.ParsedLine line : lines) {
            start |= line.side == LyricsCoreAdapter.SIDE_START;
            end |= line.side == LyricsCoreAdapter.SIDE_END;
            accompaniment += line.accompaniment.size();
        }
        hasDuet = start && end;
        accompanimentCount = accompaniment;
    }

    /**
     * Returns the overlay for one document. The same {@code lyricInfo} is parsed several times
     * per update, so the most recent document is kept.
     */
    static TtmlLyricOverlay of(String ttml) {
        if (ttml == null || ttml.isEmpty() || ttml.length() > MAX_DOCUMENT_CHARS) {
            return EMPTY;
        }
        synchronized (CACHE_LOCK) {
            if (ttml.equals(cachedDocument)) {
                return cachedOverlay;
            }
        }
        LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parseTtml(ttml);
        TtmlLyricOverlay overlay = parsed.lines.isEmpty() ? EMPTY : new TtmlLyricOverlay(parsed.lines);
        synchronized (CACHE_LOCK) {
            cachedDocument = ttml;
            cachedOverlay = overlay;
        }
        return overlay;
    }

    boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Word-timed lines as {@code [line]<word>text...<end>}; line-timed lines as plain LRC. */
    synchronized String enhancedLrc() {
        if (enhancedLrc == null) {
            StringBuilder builder = new StringBuilder();
            for (LyricsCoreAdapter.ParsedLine line : lines) {
                String text = singleLine(line.text);
                if (text.trim().isEmpty()) {
                    continue;
                }
                builder.append('[').append(formatTime(line.startMillis)).append(']');
                if (line.syllables.size() > 1) {
                    long lastEnd = line.startMillis;
                    for (LyricsCoreAdapter.ParsedSyllable syllable : line.syllables) {
                        builder.append('<').append(formatTime(syllable.startMillis)).append('>')
                                .append(text, syllable.start, syllable.end);
                        lastEnd = Math.max(lastEnd, Math.max(syllable.startMillis, syllable.endMillis));
                    }
                    builder.append('<').append(formatTime(lastEnd)).append('>');
                } else {
                    builder.append(text.trim());
                }
                builder.append('\n');
            }
            enhancedLrc = builder.toString();
        }
        return enhancedLrc;
    }

    synchronized String translationLrc() {
        if (translationLrc == null) {
            StringBuilder builder = new StringBuilder();
            for (LyricsCoreAdapter.ParsedLine line : lines) {
                String translation = singleLine(line.translation).trim();
                if (!translation.isEmpty()) {
                    builder.append('[').append(formatTime(line.startMillis)).append(']')
                            .append(translation).append('\n');
                }
            }
            translationLrc = builder.toString();
        }
        return translationLrc;
    }

    /** Keeps the length unchanged so syllable offsets into the line text stay valid. */
    private static String singleLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
    }

    private static String formatTime(long millis) {
        long safe = Math.max(0L, millis);
        return String.format(Locale.ROOT, "%02d:%02d.%03d",
                safe / 60_000L, (safe % 60_000L) / 1_000L, safe % 1_000L);
    }
}
