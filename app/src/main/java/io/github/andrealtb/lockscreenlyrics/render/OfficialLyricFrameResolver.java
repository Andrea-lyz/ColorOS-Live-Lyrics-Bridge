package io.github.andrealtb.lockscreenlyrics.render;

/**
 * Pure line-selection policy shared by official lyric drawing and active-row invalidation.
 * Runtime View/Recycler reads, strict slot suppression, logging, and draw scheduling remain in
 * the Bridge composition root.
 */
public final class OfficialLyricFrameResolver {
    private static final String MATCH_NONE = "none";

    public void resolveInto(
            WordLyricModel model,
            String normalizedText,
            int adapterPosition,
            int officialIndex,
            WordLine indexedLine,
            WordLine activeLine,
            long position,
            boolean duplicateText,
            String rememberedActiveText,
            long rememberedActiveTimeMillis,
            Selection result) {
        if (result == null) {
            return;
        }
        result.reset(indexedLine, duplicateText);
        if (model == null) {
            return;
        }

        if (isEmpty(normalizedText)) {
            if (adapterPosition >= 0
                    && indexedLine != null
                    && indexedLine == activeLine) {
                result.complete(indexedLine, null, model.indexOfLine(indexedLine),
                        "active-empty-slot");
            }
            return;
        }

        WordLine line = null;
        WordLine translationLine = null;
        String matchReason = MATCH_NONE;
        if (indexedLine != null) {
            if (WordLyricRenderSupport.matchesWordLineText(indexedLine, normalizedText)) {
                line = indexedLine;
                matchReason = "indexed-main";
            } else if (!isEmpty(indexedLine.translation)
                    && indexedLine.normalizedTranslation().equals(normalizedText)) {
                translationLine = indexedLine;
                matchReason = "indexed-translation";
            }
        }
        if (line == null
                && translationLine == null
                && indexedLine != null
                && adapterPosition >= 0
                && model.lineAtOfficialIndex(adapterPosition) == indexedLine) {
            // A verified opening title/artist credit may deliberately reuse the first real word
            // line. The official TextView still contains the credit text, so text matching cannot
            // select it; the explicit official-slot mapping is the authorization to draw it.
            line = indexedLine;
            matchReason = "mapped-official-alias";
        }

        WordLine mappedAnchor = indexedLine != null
                ? indexedLine
                : model.lineAtOfficialIndex(
                        adapterPosition >= 0 ? adapterPosition : officialIndex);
        int mappedAnchorIndex = model.indexOfLine(mappedAnchor);
        int anchorIndex = mappedAnchorIndex >= 0
                ? mappedAnchorIndex
                : model.displayIndexAt(position);

        if (line == null && translationLine == null && anchorIndex >= 0) {
            if (duplicateText) {
                line = model.findLineByText(normalizedText, position);
                translationLine = model.findLineByTranslation(normalizedText, position);
                if (line != null) {
                    matchReason = "timed-duplicate-main";
                } else if (translationLine != null) {
                    matchReason = "timed-duplicate-translation";
                }
            } else {
                line = model.findLineByTextNearIndex(normalizedText, anchorIndex, 2, false);
                translationLine = model.findLineByTranslationNearIndex(
                        normalizedText,
                        anchorIndex,
                        2);
                if (line != null) {
                    matchReason = "near-main";
                } else if (translationLine != null) {
                    matchReason = "near-translation";
                }
            }
        }

        if (line == null && translationLine == null && duplicateText) {
            if (activeLine != null) {
                if (normalizedText.equals(activeLine.normalizedText)) {
                    line = activeLine;
                    matchReason = "duplicate-active-main";
                } else if (normalizedText.equals(activeLine.normalizedTranslation())) {
                    translationLine = activeLine;
                    matchReason = "duplicate-active-translation";
                }
            }
            if (line == null && translationLine == null) {
                result.complete(null, null, anchorIndex, matchReason);
                return;
            }
        }

        if (line == null && translationLine == null) {
            if (activeLine != null
                    && WordLyricRenderSupport.matchesWordLineText(
                            activeLine,
                            normalizedText)) {
                line = activeLine;
                matchReason = "active-main";
            } else {
                line = model.findLineByText(normalizedText, position);
                if (line != null) {
                    matchReason = "timed-main";
                }
            }
            if (activeLine != null
                    && !isEmpty(activeLine.translation)
                    && activeLine.normalizedTranslation().equals(normalizedText)) {
                translationLine = activeLine;
                if (line == null) {
                    matchReason = "active-translation";
                }
            } else {
                translationLine = model.findLineByTranslation(normalizedText, position);
                if (line == null && translationLine != null) {
                    matchReason = "timed-translation";
                }
            }
        }

        if (line == null
                && translationLine == null
                && !duplicateText
                && normalizedText.equals(nullToEmpty(rememberedActiveText))) {
            line = model.findLineAtTime(rememberedActiveTimeMillis);
            if (line != null) {
                matchReason = "remembered-active-line";
            }
        }
        result.complete(line, translationLine, anchorIndex, matchReason);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Mutable caller-owned result; reuse avoids adding one allocation to every draw frame. */
    public static final class Selection {
        public WordLine indexedLine;
        public WordLine line;
        public WordLine translationLine;
        public int anchorIndex = -1;
        public boolean duplicateText;
        public String matchReason = MATCH_NONE;

        private void reset(WordLine nextIndexedLine, boolean nextDuplicateText) {
            indexedLine = nextIndexedLine;
            line = null;
            translationLine = null;
            anchorIndex = -1;
            duplicateText = nextDuplicateText;
            matchReason = MATCH_NONE;
        }

        private void complete(
                WordLine nextLine,
                WordLine nextTranslationLine,
                int nextAnchorIndex,
                String nextMatchReason) {
            line = nextLine;
            translationLine = nextTranslationLine;
            anchorIndex = nextAnchorIndex;
            matchReason = isEmpty(nextMatchReason) ? MATCH_NONE : nextMatchReason;
        }
    }
}
