package io.github.andrealtb.lockscreenlyrics;

import static io.github.andrealtb.lockscreenlyrics.LyricModelTraceSupport.cleanPlainLyricText;
import static io.github.andrealtb.lockscreenlyrics.LyricModelTraceSupport.containsLatinLetter;
import static io.github.andrealtb.lockscreenlyrics.LyricModelTraceSupport.formatLrcTime;
import static io.github.andrealtb.lockscreenlyrics.LyricModelTraceSupport.isEmpty;
import static io.github.andrealtb.lockscreenlyrics.LyricModelTraceSupport.parseLrcTimeMillis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.andrealtb.lockscreenlyrics.render.InlineTimedLyricLine;
import io.github.andrealtb.lockscreenlyrics.render.LyricTimingMode;
import io.github.andrealtb.lockscreenlyrics.render.NormalizedWordLineText;
import io.github.andrealtb.lockscreenlyrics.render.TagMatch;
import io.github.andrealtb.lockscreenlyrics.render.WordLine;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricModel;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderConstants;
import io.github.andrealtb.lockscreenlyrics.render.WordLyricRenderSupport;
import io.github.andrealtb.lockscreenlyrics.render.WordRange;

/**
 * Assembles the native SystemUI word lyric model from one
 * {@code lyricInfo} payload: parses the raw word-timed lyric, applies
 * official display-text aliases, merges supplemental translations from the
 * display and translation payloads through time/text indexes, and finally
 * propagates nearby translations. Promoted out of
 * {@code LockscreenLyricsModule} in Phase 6 slice 5.
 *
 * <p>Each payload string is parsed once per assembly: the display lyric is
 * split into raw lines a single time and the resulting list feeds both the
 * official alias groups and the supplemental parse. Supplemental matching
 * uses {@link SupplementalTranslationIndex} instead of the historical
 * nested full-list scans. Assembly stays synchronous so the caller keeps
 * its publish timing under its own lock; nothing here is threaded.</p>
 */
final class NativeLyricModelAssembler {
    static final long SUPPLEMENTAL_MATCH_WINDOW_MS = 120L;

    private final LyricParseTraceSink traceSink;

    NativeLyricModelAssembler(LyricParseTraceSink traceSink) {
        this.traceSink = traceSink;
    }

    static final class AssemblyResult {
        final WordLyricModel model;
        final int aliasesApplied;
        final String firstAlias;
        final int supplementalDisplayAdded;
        final int supplementalTranslationAdded;
        final int propagatedTranslations;

        AssemblyResult(
                WordLyricModel model,
                int aliasesApplied,
                String firstAlias,
                int supplementalDisplayAdded,
                int supplementalTranslationAdded,
                int propagatedTranslations) {
            this.model = model;
            this.aliasesApplied = aliasesApplied;
            this.firstAlias = firstAlias;
            this.supplementalDisplayAdded = supplementalDisplayAdded;
            this.supplementalTranslationAdded = supplementalTranslationAdded;
            this.propagatedTranslations = propagatedTranslations;
        }
    }

    AssemblyResult assemble(
            String rawLyric,
            boolean rawHasTimedLrc,
            String displayLyric,
            boolean displayHasTimedLrc,
            String translationLyric) {
        WordLyricModel model = parseWordLyric(rawLyric, rawHasTimedLrc, null, true, true);
        int aliasesApplied = 0;
        String firstAlias = "";
        int displayAdded = 0;
        int translationAdded = 0;
        if (!model.lines.isEmpty()) {
            ArrayList<String> displaySplitLines =
                    displayHasTimedLrc ? splitRawLyricLines(displayLyric) : null;
            AliasResult aliasResult = applyOfficialDisplayTextAliases(
                    model,
                    displayLyric,
                    displayHasTimedLrc,
                    displaySplitLines);
            aliasesApplied = aliasResult.applied;
            firstAlias = aliasResult.firstAlias;
            displayAdded = mergeSupplementalTranslations(
                    model,
                    displayLyric,
                    rawLyric,
                    false,
                    displayHasTimedLrc,
                    displaySplitLines);
            translationAdded = mergeSupplementalTranslations(
                    model,
                    translationLyric,
                    rawLyric,
                    true,
                    LyricInfoContract.containsTimedLrc(translationLyric),
                    null);
        }
        int propagatedTranslations = model.propagateNearbyTranslations(6);
        LyricModelTraceSupport.traceWordLyricModel(traceSink, model, "final-systemui", "systemui");
        return new AssemblyResult(
                model,
                aliasesApplied,
                firstAlias,
                displayAdded,
                translationAdded,
                propagatedTranslations);
    }

    // ------------------------------------------------------------------
    // Supplemental translation merge (indexed)
    // ------------------------------------------------------------------

    private int mergeSupplementalTranslations(
            WordLyricModel target,
            String supplemental,
            String rawLyric,
            boolean allowTextAsTranslation,
            boolean supplementalHasTimedLrc,
            ArrayList<String> sharedSplitLines) {
        if (target == null
                || target.lines.isEmpty()
                || !supplementalHasTimedLrc
                || supplemental.equals(rawLyric)) {
            return 0;
        }

        WordLyricModel supplementalModel =
                parseWordLyric(supplemental, supplementalHasTimedLrc, sharedSplitLines, false, true);
        if (supplementalModel.lines.isEmpty()) {
            return 0;
        }

        SupplementalTranslationIndex targetIndex = new SupplementalTranslationIndex(target.lines);
        ArrayList<WordLine> supplementalLines = supplementalModel.lines;
        long[] supplementalTimes = new long[supplementalLines.size()];
        for (int i = 0; i < supplementalLines.size(); i++) {
            WordLine line = supplementalLines.get(i);
            supplementalTimes[i] = line == null ? 0L : line.timeMillis;
        }

        int before = target.translationCount();
        for (WordLine targetLine : target.lines) {
            if (targetLine == null || !isEmpty(targetLine.translation)) {
                continue;
            }

            WordLine supplementalLine = findSupplementalTranslationLine(
                    targetIndex,
                    supplementalLines,
                    supplementalTimes,
                    targetLine,
                    allowTextAsTranslation);
            if (supplementalLine == null) {
                continue;
            }

            String translation = cleanPlainLyricText(supplementalLine.translation);
            if (isEmpty(translation) && allowTextAsTranslation) {
                translation = cleanPlainLyricText(supplementalLine.text);
            }
            if (!isEmpty(translation)
                    && !WordLyricRenderSupport.normalizeLine(translation).equals(
                    WordLyricRenderSupport.normalizeLine(targetLine.text))) {
                targetLine.translation = translation;
            }
        }

        return target.translationCount() - before;
    }

    private static WordLine findSupplementalTranslationLine(
            SupplementalTranslationIndex targetIndex,
            ArrayList<WordLine> supplementalLines,
            long[] supplementalTimes,
            WordLine targetLine,
            boolean allowTextAsTranslation) {
        WordLine best = null;
        long bestDistance = Long.MAX_VALUE;
        String targetText = WordLyricRenderSupport.normalizeLine(targetLine.text);
        int start = SupplementalTranslationIndex.lowerBound(
                supplementalTimes,
                targetLine.timeMillis - SUPPLEMENTAL_MATCH_WINDOW_MS);
        for (int i = start; i < supplementalLines.size(); i++) {
            WordLine candidate = supplementalLines.get(i);
            if (candidate == null) {
                continue;
            }
            if (candidate.timeMillis - targetLine.timeMillis > SUPPLEMENTAL_MATCH_WINDOW_MS) {
                break;
            }
            long distance = Math.abs(candidate.timeMillis - targetLine.timeMillis);
            if (distance > SUPPLEMENTAL_MATCH_WINDOW_MS || distance > bestDistance) {
                continue;
            }

            String candidateTranslation = cleanPlainLyricText(candidate.translation);
            String candidateText = WordLyricRenderSupport.normalizeLine(candidate.text);
            String proposedTranslation = !isEmpty(candidateTranslation)
                    ? candidateTranslation
                    : allowTextAsTranslation ? candidate.text : "";
            boolean usable = !isEmpty(candidateTranslation)
                    || (allowTextAsTranslation
                    && !isEmpty(candidate.text)
                    && !candidateText.equals(targetText));
            if (!usable
                    || !targetIndex.isNearestPrimaryLineForTimestamp(
                    targetLine,
                    candidate.timeMillis,
                    SUPPLEMENTAL_MATCH_WINDOW_MS)
                    || targetIndex.matchesNearbyPrimaryLine(
                    targetLine,
                    proposedTranslation,
                    SUPPLEMENTAL_MATCH_WINDOW_MS)) {
                continue;
            }

            best = candidate;
            bestDistance = distance;
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Official display-text aliases
    // ------------------------------------------------------------------

    private static final class AliasResult {
        static final AliasResult EMPTY = new AliasResult(0, "");
        final int applied;
        final String firstAlias;

        AliasResult(int applied, String firstAlias) {
            this.applied = applied;
            this.firstAlias = firstAlias;
        }
    }

    private AliasResult applyOfficialDisplayTextAliases(
            WordLyricModel model,
            String officialLyric,
            boolean officialHasTimedLrc,
            ArrayList<String> sharedSplitLines) {
        if (model == null || model.lines.isEmpty() || !officialHasTimedLrc) {
            return AliasResult.EMPTY;
        }

        int applied = 0;
        String firstAlias = "";
        LinkedHashMap<String, Integer> textOccurrences = new LinkedHashMap<>();
        model.officialLines.clear();
        model.renderableTextIndexBuilt = false;
        ArrayList<String> splitLines = sharedSplitLines != null
                ? sharedSplitLines
                : splitRawLyricLines(officialLyric);
        for (TimedLyricGroup group : parseTimedTextGroups(splitLines)) {
            if (group == null) {
                continue;
            }
            if (group.texts.isEmpty()) {
                if (group.blankPlaceholder) {
                    int officialIndex = model.officialLines.size();
                    model.officialLines.add(null);
                    if (traceSink != null && traceSink.traceEnabled()) {
                        traceSink.trace("official-placeholder#" + officialIndex
                                + " time=" + formatLrcTime(group.timeMillis));
                    }
                }
                continue;
            }
            LyricLaneClassifier.Result lanes =
                    LyricLaneClassifier.classify(group.texts, group.timeMillis);
            int primaryIndex = lanes.primaryIndex();
            String displayText = cleanPlainLyricText(group.texts.get(primaryIndex));
            if (isEmpty(displayText)) {
                continue;
            }
            String normalizedDisplayText = WordLyricRenderSupport.normalizeLine(displayText);
            int occurrence = textOccurrences.containsKey(normalizedDisplayText)
                    ? textOccurrences.get(normalizedDisplayText)
                    : 0;
            textOccurrences.put(normalizedDisplayText, occurrence + 1);
            int officialIndex = model.officialLines.size();
            WordLine wordLine = findOfficialWordLine(
                    model,
                    group.timeMillis,
                    normalizedDisplayText,
                    occurrence,
                    officialIndex);
            boolean displayMatchesMainText =
                    WordLyricRenderSupport.matchesWordLineText(wordLine, normalizedDisplayText);
            model.officialLines.add(
                    WordLyricRenderConstants.OFFICIAL_SLOT_ALIAS_REUSE_ENABLED && displayMatchesMainText
                            ? wordLine
                            : null);
            boolean usableTranslationAlias = wordLine != null
                    && !displayMatchesMainText
                    && isEmpty(wordLine.translation)
                    && isUsableOfficialTranslationAlias(
                    wordLine,
                    displayText,
                    group.timeMillis);
            traceOfficialAliasMapping(
                    model,
                    model.officialLines.size() - 1,
                    group,
                    primaryIndex,
                    displayText,
                    occurrence,
                    wordLine,
                    displayMatchesMainText,
                    usableTranslationAlias);
            if (wordLine == null) {
                continue;
            }
            if (displayMatchesMainText) {
                wordLine.displayText = displayText;
                applied++;
                if (isEmpty(firstAlias)) {
                    firstAlias = displayText;
                }
            } else if (usableTranslationAlias) {
                wordLine.translation = displayText;
            }
            for (int i = 0; i < group.texts.size(); i++) {
                if (i == primaryIndex || !isEmpty(wordLine.translation)) {
                    continue;
                }
                String translation = cleanPlainLyricText(group.texts.get(i));
                if (!isEmpty(translation)
                        && lanes.laneAt(i) == LyricLaneClassifier.Lane.TRANSLATION
                        && !LockscreenIntegrationPolicy.sameLyricVariant(
                        displayText,
                        translation)) {
                    wordLine.translation = translation;
                }
            }
        }
        return new AliasResult(applied, firstAlias);
    }

    private void traceOfficialAliasMapping(
            WordLyricModel model,
            int officialIndex,
            TimedLyricGroup group,
            int primaryIndex,
            String displayText,
            int occurrence,
            WordLine wordLine,
            boolean displayMatchesMainText,
            boolean usableTranslationAlias) {
        if (traceSink == null || !traceSink.traceEnabled()) {
            return;
        }
        traceSink.trace("official-alias#" + officialIndex
                + " time=" + (group == null ? "" : formatLrcTime(group.timeMillis))
                + " primaryIndex=" + primaryIndex
                + " occurrence=" + occurrence
                + " mappedIndex=" + (model == null ? -1 : model.indexOfLine(wordLine))
                + " matchMain=" + displayMatchesMainText
                + " useAsTranslation=" + usableTranslationAlias
                + " display=\"" + LyricModelTraceSupport.limitTraceValue(displayText, 360) + "\""
                + " mapped=" + LyricModelTraceSupport.describeWordLine(wordLine, false)
                + " texts=" + LyricModelTraceSupport.limitTraceValue(
                group == null ? "" : String.valueOf(group.texts),
                900));
    }

    private static boolean isUsableOfficialTranslationAlias(
            WordLine wordLine,
            String displayText,
            long displayTimeMillis) {
        if (wordLine == null || isEmpty(displayText)) {
            return false;
        }
        String normalizedDisplayText = WordLyricRenderSupport.normalizeLine(displayText);
        if (isEmpty(normalizedDisplayText)
                || LyricMetadataFilter.isNonLyricInfoLine(displayText, displayTimeMillis)
                || normalizedDisplayText.equals(wordLine.normalizedText)
                || LockscreenIntegrationPolicy.sameLyricVariant(
                wordLine.text,
                displayText)
                || LyricLineVariantSelector.isLikelyJapaneseRomanization(
                wordLine.text,
                displayText)
                || LyricLineVariantSelector.isLikelyPhoneticVariant(
                java.util.Arrays.asList(wordLine.text, displayText),
                0,
                displayText)) {
            return false;
        }
        return !containsLatinLetter(wordLine.text) || !containsLatinLetter(displayText);
    }

    private static WordLine findOfficialWordLine(
            WordLyricModel model,
            long timeMillis,
            String normalizedDisplayText,
            int occurrence,
            int officialIndex) {
        if (model == null || isEmpty(normalizedDisplayText)) {
            return null;
        }
        return model.findOfficialAliasLine(
                timeMillis,
                normalizedDisplayText,
                occurrence,
                officialIndex);
    }

    private static ArrayList<TimedLyricGroup> parseTimedTextGroups(ArrayList<String> splitLines) {
        LinkedHashMap<Long, TimedLyricGroup> groups = new LinkedHashMap<>();
        if (splitLines == null) {
            return new ArrayList<>();
        }
        for (String rawLine : splitLines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            java.util.regex.Matcher firstTag = WordLyricRenderSupport.ANY_LRC_TIME_TAG.matcher(line);
            if (!firstTag.find() || firstTag.start() != 0) {
                continue;
            }

            long timeMillis = parseLrcTimeMillis(firstTag.group(1));
            String rawText = line.substring(firstTag.end());
            rawText = WordLyricRenderSupport.ANY_LRC_TIME_TAG.matcher(rawText).replaceAll("");
            boolean blankPlaceholder = isIgnorableOnlyPlaceholder(rawText);
            String text = cleanPlainLyricText(rawText);
            if (!isEmpty(text) || blankPlaceholder) {
                TimedLyricGroup group = groups.get(timeMillis);
                if (group == null) {
                    group = new TimedLyricGroup(timeMillis);
                    groups.put(timeMillis, group);
                }
                if (!isEmpty(text)) {
                    group.texts.add(text);
                } else {
                    group.blankPlaceholder = true;
                }
            }
        }
        return new ArrayList<>(groups.values());
    }

    private static boolean isIgnorableOnlyPlaceholder(String value) {
        if (isEmpty(value)) {
            return false;
        }
        boolean foundIgnorable = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (LyricTextSanitizer.isIgnorableCharacter(character)) {
                foundIgnorable = true;
            } else if (!Character.isWhitespace(character)) {
                return false;
            }
        }
        return foundIgnorable;
    }

    private static final class TimedLyricGroup {
        final long timeMillis;
        final ArrayList<String> texts = new ArrayList<>();
        boolean blankPlaceholder;

        TimedLyricGroup(long timeMillis) {
            this.timeMillis = timeMillis;
        }
    }

    // ------------------------------------------------------------------
    // Lyric parsing (inline enhanced LRC with lyrics-core fallback)
    // ------------------------------------------------------------------

    private WordLyricModel parseWordLyric(
            String lyric,
            boolean lyricHasTimedLrc,
            ArrayList<String> sharedSplitLines,
            boolean primarySource,
            boolean allowDelayedInlineTranslations) {
        boolean traceParse = traceSink != null && traceSink.traceEnabled();
        if (traceParse) {
            traceSink.trace("parse-start source=" + (primarySource ? "primary" : "supplemental")
                    + " rawChars=" + (lyric == null ? 0 : lyric.length())
                    + " rawHash=" + (lyric == null ? 0 : lyric.hashCode())
                    + " delayedInlineTranslations=" + allowDelayedInlineTranslations);
        }
        WordLyricModel inlineModel = parseInlineWordLrc(
                lyric,
                lyricHasTimedLrc,
                sharedSplitLines,
                allowDelayedInlineTranslations);
        if (!inlineModel.lines.isEmpty()) {
            if (traceParse) {
                LyricModelTraceSupport.traceWordLyricModel(
                        traceSink,
                        inlineModel,
                        "inline-result",
                        primarySource ? "primary" : "supplemental");
            }
            return inlineModel;
        }

        WordLyricModel model = new WordLyricModel();
        model.parserName = "lyrics-core";
        try {
            LyricsCoreAdapter.ParsedLyrics parsed = LyricsCoreAdapter.parse(lyric);
            LinkedHashMap<String, WordLine> uniqueLines = new LinkedHashMap<>();
            for (LyricsCoreAdapter.ParsedLine parsedLine : parsed.lines) {
                WordLine line = toWordLine(parsedLine);
                if (line == null || line.words.isEmpty()) {
                    continue;
                }

                String key = line.timeMillis + "|" + WordLyricRenderSupport.normalizeLine(line.text);
                WordLine existing = uniqueLines.get(key);
                if (existing == null) {
                    uniqueLines.put(key, line);
                } else if (isEmpty(existing.translation)
                        && !isEmpty(line.translation)) {
                    existing.translation = line.translation;
                }
            }
            model.lines.addAll(uniqueLines.values());
            model.lines.sort((left, right) -> Long.compare(left.timeMillis, right.timeMillis));
            mergeSameTimestampLyricLines(model);
            if (traceParse) {
                LyricModelTraceSupport.traceWordLyricModel(
                        traceSink,
                        model,
                        "lyrics-core-result",
                        primarySource ? "primary" : "supplemental");
            }
        } catch (Throwable t) {
            // Do not let a parser or dependency failure crash the injected process. An empty
            // model deliberately leaves the original ColorOS lyric renderer untouched.
            if (traceSink != null) {
                traceSink.onCoreParseFailure(primarySource, t);
            }
        }
        return model;
    }

    private WordLyricModel parseInlineWordLrc(
            String rawLyric,
            boolean rawHasTimedLrc,
            ArrayList<String> sharedSplitLines,
            boolean allowDelayedInlineTranslations) {
        WordLyricModel model = new WordLyricModel();
        model.parserName = "inline-lrc";
        boolean traceParse = traceSink != null && traceSink.traceEnabled();
        if (isEmpty(rawLyric) || !rawHasTimedLrc) {
            if (traceParse) {
                traceSink.trace("inline-skip reason=no-timed-lrc");
            }
            return model;
        }

        LinkedHashMap<Long, ArrayList<InlineTimedLyricLine>> groups = new LinkedHashMap<>();
        ArrayList<InlineTimedLyricLine> orphanTranslations = new ArrayList<>();
        int order = 0;
        int parsedTimedLineCount = 0;
        int inlineTimedLineCount = 0;
        ArrayList<String> rawLines = sharedSplitLines != null
                ? sharedSplitLines
                : splitRawLyricLines(rawLyric);
        if (traceParse) {
            traceSink.trace("inline-raw-lines count=" + rawLines.size());
        }
        int rawLineIndex = 0;
        for (String rawLine : rawLines) {
            if (traceParse) {
                traceSink.trace("raw-split#" + rawLineIndex + " " + rawLine);
            }
            for (String expandedLine : OplusLyricNormalizer.splitEmbeddedTimedLines(rawLine)) {
                InlineTimedLyricLine line = parseInlineTimedLyricLine(expandedLine, order++);
                if (line == null) {
                    if (traceParse) {
                        traceSink.trace(
                                "inline-line rejected raw#" + rawLineIndex + " " + expandedLine);
                    }
                    continue;
                }
                parsedTimedLineCount++;
                if (traceParse) {
                    traceSink.trace("inline-line raw#" + rawLineIndex + " "
                            + LyricModelTraceSupport.describeInlineTimedLyricLine(line)
                            + " raw=" + expandedLine);
                }
                if (line.inlineTiming) {
                    inlineTimedLineCount++;
                }
                ArrayList<InlineTimedLyricLine> group = groups.get(line.timeMillis);
                if (group == null) {
                    group = new ArrayList<>();
                    groups.put(line.timeMillis, group);
                }
                group.add(line);
            }
            rawLineIndex++;
        }
        if (inlineTimedLineCount <= 0 || groups.isEmpty()) {
            if (traceParse) {
                traceSink.trace("inline-empty inlineTimedLineCount=" + inlineTimedLineCount
                        + " groups=" + groups.size());
            }
            model.lines.clear();
            return model;
        }
        if (LockscreenIntegrationPolicy.shouldFallbackToLineTimedLrcForSparseInlineTiming(
                parsedTimedLineCount,
                inlineTimedLineCount)) {
            if (traceParse) {
                traceSink.trace("inline-empty reason=sparse-inline-timing parsedLines="
                        + parsedTimedLineCount
                        + " inlineTimedLineCount=" + inlineTimedLineCount
                        + " groups=" + groups.size());
            }
            model.lines.clear();
            return model;
        }

        for (Map.Entry<Long, ArrayList<InlineTimedLyricLine>> entry : groups.entrySet()) {
            ArrayList<InlineTimedLyricLine> group = entry.getValue();
            InlineTimedLyricLine primary = choosePrimaryInlineTimedLyricLine(group);
            if (primary == null) {
                if (traceParse) {
                    traceSink.trace("inline-group time=" + formatLrcTime(entry.getKey())
                            + " skipped reason=no-primary size=" + group.size());
                }
                continue;
            }
            ArrayList<String> groupTexts = inlineTimedLyricLineTexts(group);
            LyricLaneClassifier.Result lanes =
                    LyricLaneClassifier.classify(groupTexts, entry.getKey());
            int primaryIndex = indexOfInlineTimedLyricLine(group, primary);
            if (traceParse) {
                LyricModelTraceSupport.traceInlineGroup(
                        traceSink, entry.getKey(), group, primaryIndex, "before-restore");
            }
            primary = restoreSharedTrailingLatinToken(primary, group);
            if (LockscreenIntegrationPolicy.shouldTreatAsDelayedInlineTranslation(
                    allowDelayedInlineTranslations,
                    primary.inlineTiming,
                    primary.sourceTimedSegmentCount,
                    group.size(),
                    containsLatinLetter(primary.text))) {
                // In a mixed enhanced-LRC payload, a lone non-inline non-Latin line is almost
                // always a delayed translation for the preceding word-timed line.
                orphanTranslations.add(primary);
                if (traceParse) {
                    traceSink.trace("inline-group time=" + formatLrcTime(entry.getKey())
                            + " orphan-translation "
                            + LyricModelTraceSupport.describeInlineTimedLyricLine(primary));
                }
                continue;
            }

            WordLine wordLine = new WordLine(
                    primary.timeMillis,
                    primary.text,
                    primary.words,
                    primary.endTimeMillis,
                    primary.inlineTiming
                            ? LyricTimingMode.WORD_TIMED
                            : LyricTimingMode.LINE_TIMED);
            for (int candidateIndex = 0; candidateIndex < group.size(); candidateIndex++) {
                InlineTimedLyricLine candidate = group.get(candidateIndex);
                if (candidate == null || candidateIndex == primaryIndex) {
                    continue;
                }
                String translation = cleanPlainLyricText(candidate.text);
                if (isEmpty(translation)
                        || lanes.laneAt(candidateIndex) != LyricLaneClassifier.Lane.TRANSLATION
                        || LockscreenIntegrationPolicy.sameLyricVariant(
                        primary.text,
                        translation)) {
                    continue;
                }
                if (isEmpty(wordLine.translation)) {
                    wordLine.translation = translation;
                }
            }
            if (traceParse) {
                traceSink.trace("inline-word-line "
                        + LyricModelTraceSupport.describeWordLine(wordLine, true));
            }
            model.lines.add(wordLine);
        }

        model.lines.sort((left, right) -> Long.compare(left.timeMillis, right.timeMillis));
        attachDelayedInlineTranslations(model, orphanTranslations);
        if (traceParse) {
            traceSink.trace("inline-built lines=" + model.lines.size()
                    + " translations=" + model.translationCount()
                    + " orphanTranslations=" + orphanTranslations.size()
                    + " delayedInlineTranslations=" + allowDelayedInlineTranslations);
        }
        return model;
    }

    private static void attachDelayedInlineTranslations(
            WordLyricModel model,
            ArrayList<InlineTimedLyricLine> translations) {
        if (model == null || model.lines.isEmpty() || translations == null || translations.isEmpty()) {
            return;
        }
        for (InlineTimedLyricLine candidate : translations) {
            if (candidate == null || isEmpty(candidate.text)) {
                continue;
            }

            WordLine previous = null;
            WordLine next = null;
            for (WordLine line : model.lines) {
                if (line.timeMillis < candidate.timeMillis) {
                    previous = line;
                    continue;
                }
                if (line.timeMillis > candidate.timeMillis) {
                    next = line;
                    break;
                }
            }
            if (previous == null || !isEmpty(previous.translation)) {
                continue;
            }

            boolean previousHasWordTiming = previous.words.size() > 1
                    || previous.endTimeMillis > previous.timeMillis + 600L;
            boolean candidateLooksLikeTranslation =
                    !containsLatinLetter(candidate.text)
                            && !LockscreenIntegrationPolicy.sameLyricVariant(
                            previous.text,
                            candidate.text);
            long nextTime = next == null ? -1L : next.timeMillis;
            if (LockscreenIntegrationPolicy.shouldAttachDelayedTranslation(
                    previousHasWordTiming,
                    candidateLooksLikeTranslation,
                    previous.timeMillis,
                    previous.endTimeMillis,
                    candidate.timeMillis,
                    nextTime)) {
                previous.translation = cleanPlainLyricText(candidate.text);
            }
        }
    }

    private static InlineTimedLyricLine parseInlineTimedLyricLine(String rawLine, int order) {
        String line = rawLine == null ? "" : rawLine.trim();
        if (isEmpty(line)) {
            return null;
        }

        java.util.regex.Matcher matcher = WordLyricRenderSupport.ANY_LRC_TIME_TAG.matcher(line);
        ArrayList<TagMatch> tags = new ArrayList<>();
        while (matcher.find()) {
            tags.add(new TagMatch(matcher.start(), matcher.end(), parseLrcTimeMillis(matcher.group(1))));
        }
        if (tags.isEmpty() || tags.get(0).start != 0) {
            return null;
        }

        StringBuilder text = new StringBuilder(line.length());
        ArrayList<WordRange> words = new ArrayList<>();
        boolean previousSegmentEndedWithSpace = false;
        long explicitEndMillis = -1L;
        for (int i = 0; i < tags.size(); i++) {
            TagMatch tag = tags.get(i);
            int segmentStart = tag.end;
            int segmentEnd = i + 1 < tags.size() ? tags.get(i + 1).start : line.length();
            String rawSegment = segmentStart < segmentEnd
                    ? line.substring(segmentStart, segmentEnd)
                    : "";
            boolean segmentStartsWithSpace = startsWithWhitespace(rawSegment);
            boolean segmentEndsWithSpace = endsWithWhitespace(rawSegment);
            String segment = cleanInlineTimedLyricSegment(rawSegment);
            if (isEmpty(segment)) {
                if (i == tags.size() - 1 && tags.size() > 1 && tag.timeMillis > tags.get(0).timeMillis) {
                    explicitEndMillis = tag.timeMillis;
                }
                continue;
            }

            if (shouldInsertInlineSegmentSpace(
                    text,
                    segment,
                    segmentStartsWithSpace,
                    previousSegmentEndedWithSpace)) {
                text.append(' ');
            }
            int start = text.length();
            text.append(segment);
            int end = text.length();
            if (start < end) {
                words.add(new WordRange(tag.timeMillis, start, end));
            }
            previousSegmentEndedWithSpace = segmentEndsWithSpace;
        }

        if (isEmpty(text.toString()) || words.isEmpty()) {
            return null;
        }

        NormalizedWordLineText normalized = normalizeTimedWordText(text.toString(), words);
        if (isEmpty(normalized.text)
                || normalized.words.isEmpty()) {
            return null;
        }

        long inferredEnd = WordLyricRenderSupport.inferWordLineEndMillis(tags.get(0).timeMillis, normalized.words);
        long endTimeMillis = explicitEndMillis > tags.get(0).timeMillis
                ? Math.max(explicitEndMillis, normalized.words.get(normalized.words.size() - 1).timeMillis + 80L)
                : inferredEnd;
        boolean progressiveTiming = LockscreenIntegrationPolicy.hasProgressiveInlineTiming(
                normalized.words.size(),
                normalized.words.get(0).timeMillis,
                normalized.words.get(normalized.words.size() - 1).timeMillis,
                tags.get(0).timeMillis,
                explicitEndMillis);
        boolean inlineTiming = progressiveTiming
                && !hasSuspiciousInlineTimingGap(normalized.words);
        ArrayList<WordRange> renderedWords = normalized.words;
        if (!inlineTiming && normalized.words.size() > 1) {
            renderedWords = new ArrayList<>();
            renderedWords.add(new WordRange(
                    tags.get(0).timeMillis,
                    0,
                    normalized.text.length()));
        }
        return new InlineTimedLyricLine(
                tags.get(0).timeMillis,
                endTimeMillis,
                normalized.text,
                renderedWords,
                inlineTiming,
                normalized.words.size(),
                order);
    }

    private static InlineTimedLyricLine choosePrimaryInlineTimedLyricLine(
            ArrayList<InlineTimedLyricLine> group) {
        ArrayList<String> texts = inlineTimedLyricLineTexts(group);
        long timeMillis = group == null || group.isEmpty() || group.get(0) == null
                ? 0L
                : group.get(0).timeMillis;
        int selectedIndex = LyricLaneClassifier.findPrimaryTextIndex(texts, timeMillis);
        if (selectedIndex >= 0 && selectedIndex < group.size()) {
            InlineTimedLyricLine selected = group.get(selectedIndex);
            if (selected != null && !isEmpty(selected.text)) {
                return selected;
            }
        }

        boolean hasJapaneseSource = false;
        if (group != null) {
            for (InlineTimedLyricLine line : group) {
                if (line != null && LyricLineVariantSelector.containsJapaneseScript(line.text)) {
                    hasJapaneseSource = true;
                    break;
                }
            }
        }
        InlineTimedLyricLine best = null;
        int bestScore = Integer.MIN_VALUE;
        if (group == null) {
            return null;
        }
        for (InlineTimedLyricLine line : group) {
            if (line == null || isEmpty(line.text)) {
                continue;
            }
            if (hasJapaneseSource
                    && !LyricLineVariantSelector.containsJapaneseScript(line.text)) {
                continue;
            }
            int score = Math.min(120, line.words == null ? 0 : line.words.size()) * 12
                    + Math.min(120, WordLyricRenderSupport.normalizeLine(line.text).length());
            if (line.inlineTiming) {
                score += 1_000;
            }
            if (containsLatinLetter(line.text)) {
                score += 500;
            }
            score -= Math.max(0, line.order);
            if (best == null || score > bestScore) {
                best = line;
                bestScore = score;
            }
        }
        return best;
    }

    private static ArrayList<String> inlineTimedLyricLineTexts(
            ArrayList<InlineTimedLyricLine> group) {
        ArrayList<String> texts = new ArrayList<>();
        if (group == null) {
            return texts;
        }
        for (InlineTimedLyricLine line : group) {
            texts.add(line == null ? "" : line.text);
        }
        return texts;
    }

    private static int indexOfInlineTimedLyricLine(
            ArrayList<InlineTimedLyricLine> group,
            InlineTimedLyricLine target) {
        if (group == null || target == null) {
            return -1;
        }
        for (int index = 0; index < group.size(); index++) {
            if (group.get(index) == target) {
                return index;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Raw lyric line splitting (shared by alias groups and inline parse)
    // ------------------------------------------------------------------

    private ArrayList<String> splitRawLyricLines(String rawLyric) {
        ArrayList<String> result = new ArrayList<>();
        if (isEmpty(rawLyric)) {
            return result;
        }

        String[] lines = rawLyric.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String rawLine : lines) {
            appendSplitRawLyricLine(result, rawLine == null ? "" : rawLine.trim());
        }
        return result;
    }

    private void appendSplitRawLyricLine(ArrayList<String> out, String rawLine) {
        if (isEmpty(rawLine)) {
            return;
        }
        String[] split = splitMixedTranslationAndWordLine(rawLine);
        if (split == null) {
            out.add(rawLine);
            return;
        }
        appendSplitRawLyricLine(out, split[0]);
        appendSplitRawLyricLine(out, split[1]);
    }

    private String[] splitMixedTranslationAndWordLine(String rawLine) {
        java.util.regex.Matcher matcher = WordLyricRenderSupport.ANY_LRC_TIME_TAG.matcher(rawLine);
        ArrayList<TagMatch> tags = new ArrayList<>();
        while (matcher.find()) {
            tags.add(new TagMatch(matcher.start(), matcher.end(), parseLrcTimeMillis(matcher.group(1))));
        }
        if (tags.size() < 2 || tags.get(0).start != 0) {
            return null;
        }

        for (int i = 1; i < tags.size(); i++) {
            TagMatch splitTag = tags.get(i);
            String prefixText = cleanPlainLyricText(rawLine.substring(tags.get(0).end, splitTag.start));
            if (isEmpty(prefixText)
                    || prefixText.length() < 4
                    || !LyricModelTraceSupport.containsNonAscii(prefixText)) {
                continue;
            }
            if (containsLatinLetter(prefixText)
                    && LyricModelTraceSupport.containsLyricLeadSeparator(prefixText)) {
                continue;
            }
            if (isShortLatinInterjectionBeforeInlineTimingSuffix(rawLine, tags, i, prefixText)) {
                continue;
            }
            if (looksLikeInlineTimedPrefixBeforeSplit(rawLine, tags, i)) {
                if (traceSink != null && traceSink.traceEnabled()) {
                    traceSink.trace("raw-split skip=inline-prefix tagIndex=" + i
                            + " prefix=" + prefixText
                            + " raw=" + rawLine);
                }
                continue;
            }

            int segmentStart = splitTag.end;
            int segmentEnd = i + 1 < tags.size() ? tags.get(i + 1).start : rawLine.length();
            if (segmentStart >= segmentEnd) {
                continue;
            }
            String suffixText = cleanPlainLyricText(rawLine.substring(segmentStart, segmentEnd));
            if (LockscreenIntegrationPolicy.isShortLatinTailAfterMainLyric(
                    prefixText,
                    suffixText)) {
                if (traceSink != null && traceSink.traceEnabled()) {
                    traceSink.trace("raw-split skip=latin-tail tagIndex=" + i
                            + " prefix=" + prefixText
                            + " suffix=" + suffixText
                            + " raw=" + rawLine);
                }
                continue;
            }
            if (!containsLatinLetter(suffixText)) {
                continue;
            }

            String firstLine = "[" + formatLrcTime(tags.get(0).timeMillis) + "]" + prefixText;
            String secondLine = rawLine.substring(splitTag.start).trim();
            if (!isEmpty(secondLine)) {
                if (traceSink != null && traceSink.traceEnabled()) {
                    traceSink.trace("raw-split apply tagIndex=" + i
                            + " first=" + firstLine
                            + " second=" + secondLine
                            + " raw=" + rawLine);
                }
                return new String[]{firstLine, secondLine};
            }
        }
        return null;
    }

    private static boolean looksLikeInlineTimedPrefixBeforeSplit(
            String rawLine,
            ArrayList<TagMatch> tags,
            int splitTagIndex) {
        if (isEmpty(rawLine)
                || tags == null
                || splitTagIndex <= 1
                || splitTagIndex >= tags.size()) {
            return false;
        }
        if (startsWithLineStartAndFirstWordTag(rawLine, tags)) {
            return true;
        }

        int visibleSegments = 0;
        int compactSegments = 0;
        long firstVisibleSegmentStartMillis = -1L;
        long lastVisibleSegmentStartMillis = -1L;
        for (int index = 0; index < splitTagIndex; index++) {
            TagMatch current = tags.get(index);
            TagMatch next = tags.get(index + 1);
            if (current == null || next == null || current.end > next.start) {
                return false;
            }
            String segment = cleanPlainLyricText(rawLine.substring(current.end, next.start));
            if (isEmpty(segment)) {
                continue;
            }
            visibleSegments++;
            if (current.timeMillis >= 0L) {
                if (firstVisibleSegmentStartMillis < 0L) {
                    firstVisibleSegmentStartMillis = current.timeMillis;
                }
                lastVisibleSegmentStartMillis = current.timeMillis;
            }
            if (WordLyricRenderSupport.normalizeLine(segment).length() <= 2) {
                compactSegments++;
            }
        }
        return LockscreenIntegrationPolicy.isLikelyInlineTimedMainLyricPrefix(
                visibleSegments,
                compactSegments,
                firstVisibleSegmentStartMillis,
                lastVisibleSegmentStartMillis);
    }

    private static boolean startsWithLineStartAndFirstWordTag(
            String rawLine,
            ArrayList<TagMatch> tags) {
        if (isEmpty(rawLine) || tags == null || tags.size() < 2) {
            return false;
        }
        TagMatch lineStart = tags.get(0);
        TagMatch firstWord = tags.get(1);
        if (lineStart == null
                || firstWord == null
                || lineStart.start != 0
                || firstWord.start >= rawLine.length()
                || rawLine.charAt(firstWord.start) != '<'
                || lineStart.end > firstWord.start) {
            return false;
        }
        String prefix = rawLine.substring(lineStart.end, firstWord.start);
        return isEmpty(prefix.trim());
    }

    private static boolean isShortLatinInterjectionBeforeInlineTimingSuffix(
            String rawLine,
            ArrayList<TagMatch> tags,
            int splitTagIndex,
            String prefixText) {
        if (isEmpty(rawLine)
                || tags == null
                || splitTagIndex <= 0
                || splitTagIndex >= tags.size()) {
            return false;
        }
        TagMatch lineStart = tags.get(0);
        TagMatch splitTag = tags.get(splitTagIndex);
        if (lineStart == null
                || splitTag == null
                || lineStart.timeMillis < 0L
                || splitTag.timeMillis < 0L) {
            return false;
        }
        if (containsLatinLetter(prefixText) && prefixText.trim().indexOf(' ') >= 0) {
            return false;
        }
        if (prefixText.length() > 6) {
            return false;
        }
        long gapMillis = splitTag.timeMillis - lineStart.timeMillis;
        if (gapMillis < 0L || gapMillis > 1500L) {
            return false;
        }
        int segmentStart = splitTag.end;
        int segmentEnd = splitTagIndex + 1 < tags.size()
                ? tags.get(splitTagIndex + 1).start
                : rawLine.length();
        if (segmentStart >= segmentEnd) {
            return false;
        }
        String suffixText = cleanPlainLyricText(rawLine.substring(segmentStart, segmentEnd));
        if (isEmpty(suffixText)
                || !containsLatinLetter(suffixText)
                || LyricModelTraceSupport.containsNonAscii(suffixText)
                || suffixText.length() <= prefixText.length()) {
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Same-timestamp merge and lyrics-core line conversion
    // ------------------------------------------------------------------

    private static void mergeSameTimestampLyricLines(WordLyricModel model) {
        if (model == null || model.lines.size() < 2) {
            return;
        }

        ArrayList<WordLine> merged = new ArrayList<>(model.lines.size());
        int index = 0;
        while (index < model.lines.size()) {
            WordLine first = model.lines.get(index);
            if (first == null) {
                index++;
                continue;
            }

            ArrayList<WordLine> group = new ArrayList<>();
            group.add(first);
            int next = index + 1;
            while (next < model.lines.size()) {
                WordLine candidate = model.lines.get(next);
                if (candidate == null || candidate.timeMillis != first.timeMillis) {
                    break;
                }
                group.add(candidate);
                next++;
            }

            WordLine primary = choosePrimaryWordLine(group);
            if (primary == null) {
                index = next;
                continue;
            }
            ArrayList<String> groupTexts = wordLineTexts(group);
            LyricLaneClassifier.Result lanes =
                    LyricLaneClassifier.classify(groupTexts, first.timeMillis);
            int primaryIndex = indexOfWordLine(group, primary);
            primary = restoreSharedTrailingLatinToken(primary, group);
            for (int candidateIndex = 0; candidateIndex < group.size(); candidateIndex++) {
                WordLine candidate = group.get(candidateIndex);
                if (candidate == null || candidateIndex == primaryIndex) {
                    continue;
                }
                String translation = cleanPlainLyricText(candidate.translation);
                if (isEmpty(translation)) {
                    translation = cleanPlainLyricText(candidate.text);
                }
                if (isEmpty(translation)
                        || lanes.laneAt(candidateIndex) != LyricLaneClassifier.Lane.TRANSLATION
                        || LockscreenIntegrationPolicy.sameLyricVariant(
                        primary.text,
                        translation)) {
                    continue;
                }
                if (isEmpty(primary.translation)) {
                    primary.translation = translation;
                }
            }
            merged.add(primary);
            index = next;
        }

        model.lines.clear();
        model.lines.addAll(merged);
    }

    private static WordLine choosePrimaryWordLine(ArrayList<WordLine> group) {
        ArrayList<String> texts = wordLineTexts(group);
        long timeMillis = group == null || group.isEmpty() || group.get(0) == null
                ? 0L
                : group.get(0).timeMillis;
        int selectedIndex = LyricLaneClassifier.findPrimaryTextIndex(texts, timeMillis);
        if (selectedIndex >= 0 && selectedIndex < group.size()) {
            WordLine selected = group.get(selectedIndex);
            if (selected != null && !isEmpty(selected.text)) {
                return selected;
            }
        }

        boolean hasJapaneseSource = false;
        for (WordLine line : group) {
            if (line != null && LyricLineVariantSelector.containsJapaneseScript(line.text)) {
                hasJapaneseSource = true;
                break;
            }
        }
        WordLine best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < group.size(); i++) {
            WordLine line = group.get(i);
            if (line == null || isEmpty(line.text)) {
                continue;
            }
            if (hasJapaneseSource
                    && !LyricLineVariantSelector.containsJapaneseScript(line.text)) {
                continue;
            }
            int score = Math.min(80, line.words == null ? 0 : line.words.size()) * 4
                    + Math.min(80, WordLyricRenderSupport.normalizeLine(line.text).length());
            if (containsLatinLetter(line.text)) {
                score += 1_000;
            }
            // Earlier same-timestamp lines usually carry the source/main lyric.
            score -= i;
            if (best == null || score > bestScore) {
                best = line;
                bestScore = score;
            }
        }
        return best;
    }

    private static InlineTimedLyricLine restoreSharedTrailingLatinToken(
            InlineTimedLyricLine primary,
            ArrayList<InlineTimedLyricLine> group) {
        if (primary == null || group == null || group.size() < 2) {
            return primary;
        }
        String suffix = LyricLineVariantSelector.findSharedTrailingLatinToken(
                inlineTimedLyricLineTexts(group),
                indexOfInlineTimedLyricLine(group, primary));
        if (isEmpty(suffix)) {
            return primary;
        }
        String text = LyricLineVariantSelector.appendLatinSuffix(primary.text, suffix);
        if (text.equals(primary.text)) {
            return primary;
        }
        return new InlineTimedLyricLine(
                primary.timeMillis,
                primary.endTimeMillis,
                text,
                extendLastWordRange(primary.words, text.length()),
                primary.inlineTiming,
                primary.sourceTimedSegmentCount,
                primary.order);
    }

    private static WordLine restoreSharedTrailingLatinToken(
            WordLine primary,
            ArrayList<WordLine> group) {
        if (primary == null || group == null || group.size() < 2) {
            return primary;
        }
        String suffix = LyricLineVariantSelector.findSharedTrailingLatinToken(
                wordLineTexts(group),
                indexOfWordLine(group, primary));
        if (isEmpty(suffix)) {
            return primary;
        }
        String text = LyricLineVariantSelector.appendLatinSuffix(primary.text, suffix);
        if (text.equals(primary.text)) {
            return primary;
        }
        WordLine restored = new WordLine(
                primary.timeMillis,
                text,
                extendLastWordRange(primary.words, text.length()),
                primary.endTimeMillis,
                primary.timingMode);
        restored.translation = primary.translation;
        restored.displayText = isEmpty(primary.displayText)
                ? primary.displayText
                : LyricLineVariantSelector.appendLatinSuffix(primary.displayText, suffix);
        return restored;
    }

    private static ArrayList<WordRange> extendLastWordRange(
            ArrayList<WordRange> words,
            int textLength) {
        ArrayList<WordRange> restored = new ArrayList<>();
        if (words != null) {
            restored.addAll(words);
        }
        if (restored.isEmpty()) {
            restored.add(new WordRange(0L, 0, Math.max(0, textLength)));
            return restored;
        }
        WordRange last = restored.remove(restored.size() - 1);
        restored.add(new WordRange(
                last.timeMillis,
                last.start,
                Math.max(last.end, textLength)));
        return restored;
    }

    private static ArrayList<String> wordLineTexts(ArrayList<WordLine> group) {
        ArrayList<String> texts = new ArrayList<>();
        if (group == null) {
            return texts;
        }
        for (WordLine line : group) {
            texts.add(line == null ? "" : line.text);
        }
        return texts;
    }

    private static int indexOfWordLine(ArrayList<WordLine> group, WordLine target) {
        if (group == null || target == null) {
            return -1;
        }
        for (int index = 0; index < group.size(); index++) {
            if (group.get(index) == target) {
                return index;
            }
        }
        return -1;
    }

    private static WordLine toWordLine(LyricsCoreAdapter.ParsedLine parsedLine) {
        if (parsedLine == null || isEmpty(parsedLine.text)) {
            return null;
        }

        ArrayList<WordRange> sourceWords = new ArrayList<>();
        for (LyricsCoreAdapter.ParsedSyllable syllable : parsedLine.syllables) {
            int start = Math.max(0, Math.min(parsedLine.text.length(), syllable.start));
            int end = Math.max(start, Math.min(parsedLine.text.length(), syllable.end));
            while (start < end && Character.isWhitespace(parsedLine.text.charAt(start))) {
                start++;
            }
            while (end > start && Character.isWhitespace(parsedLine.text.charAt(end - 1))) {
                end--;
            }
            if (start < end) {
                sourceWords.add(new WordRange(syllable.startMillis, start, end));
            }
        }
        if (sourceWords.isEmpty()) {
            sourceWords.add(new WordRange(
                    parsedLine.startMillis,
                    0,
                    parsedLine.text.length()));
        }

        NormalizedWordLineText normalized = normalizeTimedWordText(parsedLine.text, sourceWords);
        if (isEmpty(normalized.text)
                || normalized.words.isEmpty()) {
            return null;
        }

        long inferredEnd = WordLyricRenderSupport.inferWordLineEndMillis(parsedLine.startMillis, normalized.words);
        long endTimeMillis = parsedLine.endMillis > parsedLine.startMillis
                && parsedLine.endMillis - parsedLine.startMillis <= 120_000L
                ? parsedLine.endMillis
                : inferredEnd;
        boolean wordTimed = parsedLine.syllables.size() > 1
                && !hasSuspiciousInlineTimingGap(normalized.words);
        ArrayList<WordRange> renderedWords = normalized.words;
        if (!wordTimed && normalized.words.size() > 1) {
            renderedWords = new ArrayList<>();
            renderedWords.add(new WordRange(
                    parsedLine.startMillis,
                    0,
                    normalized.text.length()));
        }
        WordLine line = new WordLine(
                parsedLine.startMillis,
                normalized.text,
                renderedWords,
                endTimeMillis,
                wordTimed
                        ? LyricTimingMode.WORD_TIMED
                        : LyricTimingMode.LINE_TIMED);
        String translation = cleanPlainLyricText(parsedLine.translation);
        line.translation = LockscreenIntegrationPolicy.sameLyricVariant(
                normalized.text,
                translation)
                ? ""
                : translation;
        return line;
    }

    private static boolean hasSuspiciousInlineTimingGap(ArrayList<WordRange> words) {
        if (words == null || words.size() < 2) {
            return false;
        }
        long maxGap = 0L;
        for (int index = 1; index < words.size(); index++) {
            maxGap = Math.max(maxGap, words.get(index).timeMillis - words.get(index - 1).timeMillis);
        }
        return LyricTimingRepair.shouldDowngradeWordTiming(
                words.size(),
                words.get(0).timeMillis,
                words.get(words.size() - 1).timeMillis,
                maxGap,
                hasStrictlyIncreasingTiming(words));
    }

    private static boolean hasStrictlyIncreasingTiming(ArrayList<WordRange> words) {
        if (words == null || words.size() < 2) {
            return true;
        }
        for (int index = 1; index < words.size(); index++) {
            if (words.get(index).timeMillis <= words.get(index - 1).timeMillis) {
                return false;
            }
        }
        return true;
    }

    private static String cleanInlineTimedLyricSegment(String segment) {
        if (isEmpty(segment)) {
            return "";
        }
        String cleaned = WordLyricRenderSupport.ANY_LRC_TIME_TAG.matcher(segment).replaceAll("");
        cleaned = LyricTextSanitizer.removeIgnorableCharacters(cleaned).replace('\t', ' ');
        return cleaned.trim().replaceAll(" {2,}", " ");
    }

    private static boolean shouldInsertInlineSegmentSpace(
            StringBuilder current,
            String segment,
            boolean segmentStartsWithSpace,
            boolean previousSegmentEndedWithSpace) {
        if (current == null || current.length() == 0 || isEmpty(segment)) {
            return false;
        }
        if (segmentStartsWithSpace || previousSegmentEndedWithSpace) {
            return true;
        }
        char previous = current.charAt(current.length() - 1);
        char first = segment.charAt(0);
        return isAsciiWordLike(previous) && isAsciiWordLike(first);
    }

    private static boolean startsWithWhitespace(String value) {
        return !isEmpty(value) && Character.isWhitespace(value.charAt(0));
    }

    private static boolean endsWithWhitespace(String value) {
        return !isEmpty(value) && Character.isWhitespace(value.charAt(value.length() - 1));
    }

    private static boolean isAsciiWordLike(char value) {
        return (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9');
    }

    private static NormalizedWordLineText normalizeTimedWordText(String text, ArrayList<WordRange> words) {
        if (isEmpty(text) || words.isEmpty()) {
            return new NormalizedWordLineText("", new ArrayList<>());
        }

        int length = text.length();
        int[] boundaryMap = new int[length + 1];
        StringBuilder normalized = new StringBuilder(length);
        boolean emittedText = false;
        boolean pendingSpace = false;
        for (int i = 0; i < length; i++) {
            int timingTagEnd = findTimingTagEnd(text, i);
            if (timingTagEnd > i) {
                int mapped = normalized.length();
                for (int j = i; j <= timingTagEnd && j <= length; j++) {
                    boundaryMap[j] = mapped;
                }
                i = timingTagEnd - 1;
                continue;
            }
            char value = text.charAt(i);
            if (LyricTextSanitizer.isIgnorableCharacter(value)) {
                boundaryMap[i] = normalized.length();
                continue;
            }
            if (value == ' ' || value == '\t') {
                boundaryMap[i] = normalized.length();
                if (emittedText) {
                    pendingSpace = true;
                }
                continue;
            }
            if (pendingSpace && normalized.length() > 0) {
                normalized.append(' ');
                pendingSpace = false;
            }
            boundaryMap[i] = normalized.length();
            normalized.append(value);
            emittedText = true;
        }
        boundaryMap[length] = normalized.length();

        ArrayList<WordRange> normalizedWords = new ArrayList<>(words.size());
        for (WordRange word : words) {
            int start = word.start >= 0 && word.start <= length ? boundaryMap[word.start] : normalized.length();
            int end = word.end >= 0 && word.end <= length ? boundaryMap[word.end] : normalized.length();
            if (start < end) {
                normalizedWords.add(new WordRange(word.timeMillis, start, end));
            }
        }
        return new NormalizedWordLineText(normalized.toString(), normalizedWords);
    }

    private static int findTimingTagEnd(String text, int start) {
        if (isEmpty(text) || start < 0 || start >= text.length()) {
            return -1;
        }
        char open = text.charAt(start);
        char close;
        if (open == '[') {
            close = ']';
        } else if (open == '<') {
            close = '>';
        } else {
            return -1;
        }

        int maxEnd = Math.min(text.length() - 1, start + 18);
        int end = -1;
        for (int i = start + 1; i <= maxEnd; i++) {
            if (text.charAt(i) == close) {
                end = i;
                break;
            }
        }
        if (end <= start) {
            return -1;
        }

        String candidate = text.substring(start, end + 1);
        return WordLyricRenderSupport.ANY_LRC_TIME_TAG.matcher(candidate).matches() ? end + 1 : -1;
    }

    private void traceLyricParse(String message) {
        if (traceSink != null && traceSink.traceEnabled()) {
            traceSink.trace(message);
        }
    }
}
