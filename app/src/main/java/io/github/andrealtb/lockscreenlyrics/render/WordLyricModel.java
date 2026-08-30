package io.github.andrealtb.lockscreenlyrics.render;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;

/**
 * In-memory model of the active lyric lines (with separate {@code lines} and
 * {@code officialLines} views). Exposes the position / text / index lookup
 * helpers consumed by the official SystemUI recycler binding path and the
 * {@link OfficialLyricTextRenderer}. Promoted from
 * {@code LockscreenLyricsModule} in step 3.1.
 */
public final class WordLyricModel {

    public final ArrayList<WordLine> lines = new ArrayList<>();
    public final ArrayList<WordLine> officialLines = new ArrayList<>();
    final ArrayList<String> renderableTextCountsKeys = new ArrayList<>();
    final ArrayList<Integer> renderableTextCountsValues = new ArrayList<>();
    final LinkedHashMap<String, Integer> renderableTextCounts = new LinkedHashMap<>();
    final IdentityHashMap<WordLine, Integer> lineIndexByIdentity = new IdentityHashMap<>();
    final IdentityHashMap<WordLine, Integer> officialIndexByIdentity = new IdentityHashMap<>();
    public boolean renderableTextIndexBuilt;
    int lineIndexCacheSize = -1;
    WordLine lineIndexCacheFirst;
    WordLine lineIndexCacheLast;
    int officialIndexCacheSize = -1;
    WordLine officialIndexCacheFirst;
    WordLine officialIndexCacheLast;
    public String parserName = "lyrics-core";

    public WordLine findLine(long position, String currentLine) {
        String normalizedCurrent = WordLyricRenderSupport.normalizeLine(currentLine);
        WordLine fallback = null;
        for (WordLine line : lines) {
            if (!isEmpty(normalizedCurrent)
                    && WordLyricRenderSupport.matchesWordLineText(line, normalizedCurrent)) {
                return line;
            }
            if (line.timeMillis <= position) {
                fallback = line;
            }
        }
        return fallback;
    }

    public WordLine findActiveLine(long position) {
        int index = lastLineIndexAtOrBefore(position);
        return index >= 0 ? lines.get(index) : null;
    }

    public WordLine lineAt(int index) {
        return index >= 0 && index < lines.size() ? lines.get(index) : null;
    }

    public WordLine lineAtOfficialIndex(int index) {
        if (!WordLyricRenderConstants.OFFICIAL_SLOT_ALIAS_REUSE_ENABLED) {
            return null;
        }
        return rawOfficialLineAt(index);
    }

    public WordLine rawOfficialLineAt(int index) {
        return index >= 0 && index < officialLines.size()
                ? officialLines.get(index)
                : null;
    }

    public WordLine lineAtAdapterIndex(int index) {
        if (index < 0) {
            return null;
        }
        WordLine indexedLine = lineAt(index);
        if (indexedLine != null) {
            return indexedLine;
        }
        if (WordLyricRenderConstants.OFFICIAL_SLOT_ALIAS_REUSE_ENABLED
                && !officialLines.isEmpty()
                && index < officialLines.size()) {
            WordLine officialLine = officialLines.get(index);
            if (officialLine != null) {
                return officialLine;
            }
        }
        return null;
    }

    public WordLine lineAtOfficialDisplayIndex(int index) {
        WordLine officialLine = lineAtOfficialIndex(index);
        return officialLine != null ? officialLine : lineAtAdapterIndex(index);
    }

    public WordLine lineAtAdapterIndexMatchingText(int index, String normalizedText) {
        if (index < 0 || isEmpty(normalizedText)) {
            return lineAtAdapterIndex(index);
        }
        WordLine indexedLine = lineAt(index);
        WordLine officialLine = WordLyricRenderConstants.OFFICIAL_SLOT_ALIAS_REUSE_ENABLED
                ? rawOfficialLineAt(index)
                : null;
        if (WordLyricRenderSupport.matchesWordLineRenderableText(officialLine, normalizedText)) {
            return officialLine;
        }
        if (WordLyricRenderSupport.matchesWordLineRenderableText(indexedLine, normalizedText)) {
            return indexedLine;
        }
        WordLine adapterLine = lineAtAdapterIndex(index);
        if (adapterLine != indexedLine
                && adapterLine != officialLine
                && WordLyricRenderSupport.matchesWordLineRenderableText(adapterLine, normalizedText)) {
            return adapterLine;
        }
        return adapterLine;
    }

    public int indexOfLine(WordLine target) {
        if (target == null) {
            return -1;
        }
        ensureLineIndexCache();
        Integer index = lineIndexByIdentity.get(target);
        return index == null ? -1 : index;
    }

    public int adapterIndexOfLine(WordLine target) {
        if (target == null) {
            return -1;
        }
        if (WordLyricRenderConstants.OFFICIAL_SLOT_ALIAS_REUSE_ENABLED && !officialLines.isEmpty()) {
            ensureOfficialIndexCache();
            Integer officialIndex = officialIndexByIdentity.get(target);
            if (officialIndex != null) {
                return officialIndex;
            }
        }
        return indexOfLine(target);
    }

    public WordLine firstDisplayLine() {
        if (WordLyricRenderConstants.OFFICIAL_SLOT_ALIAS_REUSE_ENABLED && !officialLines.isEmpty()) {
            for (WordLine line : officialLines) {
                if (line != null && !isEmpty(line.text)) {
                    return line;
                }
            }
        }
        for (WordLine line : lines) {
            if (line != null && !isEmpty(line.text)) {
                return line;
            }
        }
        return null;
    }

    public int firstDisplayLineIndex() {
        return indexOfLine(firstDisplayLine());
    }

    public long firstProgressStartMillis() {
        WordLine firstLine = firstDisplayLine();
        return firstLine == null ? -1L : firstLine.firstProgressStartMillis();
    }

    public boolean isBeforeFirstProgressStart(long position) {
        if (position < 0L) {
            return false;
        }
        long startMillis = firstProgressStartMillis();
        return startMillis >= 0L && position < startMillis;
    }

    public int displayIndexAt(long position) {
        WordLine active = findActiveLine(position);
        int index = indexOfLine(active);
        return index >= 0 ? index : lines.isEmpty() ? -1 : 0;
    }

    public int adapterIndexAt(long position) {
        WordLine active = findActiveLine(position);
        int index = adapterIndexOfLine(active);
        return index >= 0 ? index : lines.isEmpty() ? -1 : 0;
    }

    public WordLine findLineAtTime(long timeMillis) {
        if (timeMillis < 0) {
            return null;
        }
        int index = firstLineIndexAt(timeMillis);
        return index >= 0 ? lines.get(index) : null;
    }

    public WordLine findNearestLineByTime(long timeMillis, long maxDistanceMillis) {
        if (timeMillis < 0 || lines.isEmpty()) {
            return null;
        }
        WordLine best = null;
        long bestDistance = Math.max(0L, maxDistanceMillis) + 1L;
        int insertionIndex = firstLineIndexAfterOrAt(timeMillis);
        int start = Math.max(0, insertionIndex - 1);
        int end = Math.min(lines.size() - 1, insertionIndex + 1);
        for (int i = start; i <= end; i++) {
            WordLine line = lines.get(i);
            long distance = Math.abs(line.timeMillis - timeMillis);
            if (distance < bestDistance) {
                best = line;
                bestDistance = distance;
            }
        }
        return bestDistance <= Math.max(0L, maxDistanceMillis) ? best : null;
    }

    public WordLine findLineByText(String normalizedText) {
        return findLineByText(normalizedText, -1L);
    }

    public WordLine findLineByText(String normalizedText, long position) {
        if (isEmpty(normalizedText)) {
            return null;
        }
        if (position < 0) {
            for (WordLine line : lines) {
                if (WordLyricRenderSupport.matchesWordLineText(line, normalizedText)) {
                    return line;
                }
            }
            return null;
        }
        return nearestLineByPredicate(normalizedText, position, false);
    }

    /**
     * Maps an official ColorOS adapter row onto the word model. Chorus repeats
     * must follow the official timestamp / occurrence, not a radius-2 index
     * search: that search binds {@code It's a cruel summer} at 00:47.744 to the
     * previous occurrence at 00:36.409 and karaoke then uses finished words.
     */
    public WordLine findOfficialAliasLine(
            long timeMillis,
            String normalizedDisplayText,
            int occurrence,
            int officialIndex) {
        if (isEmpty(normalizedDisplayText)) {
            return null;
        }
        WordLine exactTime = findLineAtTime(timeMillis);
        if (WordLyricRenderSupport.matchesWordLineText(exactTime, normalizedDisplayText)) {
            return exactTime;
        }

        WordLine timedText = findLineByText(normalizedDisplayText, timeMillis);
        if (timedText != null) {
            return timedText;
        }

        // The historical findLineByTextOccurrence step was provably dead:
        // it only ran after findLineByText found no matching line at all,
        // and it evaluates the same matchesWordLineText predicate, so it
        // could never find one either. Phase 6 slice 5 removed the extra
        // full-list scan per official alias group.
        WordLine indexedText = findLineByTextNearIndex(
                normalizedDisplayText,
                officialIndex,
                2,
                false);
        if (indexedText != null) {
            return indexedText;
        }

        WordLine nearest = findNearestLineByTime(timeMillis, 650L);
        if (WordLyricRenderSupport.matchesWordLineText(nearest, normalizedDisplayText)) {
            return nearest;
        }
        return nearest;
    }

    public WordLine findLineByTextOccurrence(String normalizedText, int occurrence) {
        if (isEmpty(normalizedText) || occurrence < 0) {
            return null;
        }
        int seen = 0;
        for (WordLine line : lines) {
            if (!WordLyricRenderSupport.matchesWordLineText(line, normalizedText)) {
                continue;
            }
            if (seen++ == occurrence) {
                return line;
            }
        }
        return null;
    }

    public boolean hasRenderableText(String normalizedText) {
        if (isEmpty(normalizedText)) {
            return false;
        }
        ensureRenderableTextIndex();
        if (renderableTextCounts.containsKey(normalizedText)) {
            return true;
        }
        for (WordLine line : lines) {
            if (WordLyricRenderSupport.matchesWordLineText(line, normalizedText)) {
                return true;
            }
            if (line.normalizedTranslation().equals(normalizedText)) {
                return true;
            }
        }
        return false;
    }

    public WordLine findLineByTextNearIndex(
            String normalizedText, int index, int radius, boolean requireTranslation) {
        if (isEmpty(normalizedText) || index < 0 || lines.isEmpty()) {
            return null;
        }
        int anchor = Math.max(0, Math.min(index, lines.size() - 1));
        int start = Math.max(0, anchor - Math.max(0, radius));
        int end = Math.min(lines.size() - 1, anchor + Math.max(0, radius));
        WordLine best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            WordLine line = lines.get(i);
            if (!WordLyricRenderSupport.matchesWordLineText(line, normalizedText)) {
                continue;
            }
            if (requireTranslation && isEmpty(line.translation)) {
                continue;
            }
            int distance = Math.abs(i - anchor);
            if (best == null || distance < bestDistance) {
                best = line;
                bestDistance = distance;
            }
        }
        return best;
    }

    public WordLine findLineByTranslation(String normalizedText) {
        return findLineByTranslation(normalizedText, -1L);
    }

    public WordLine findLineByTranslation(String normalizedText, long position) {
        if (isEmpty(normalizedText)) {
            return null;
        }
        if (position < 0) {
            for (WordLine line : lines) {
                if (line.normalizedTranslation().equals(normalizedText)) {
                    return line;
                }
            }
            return null;
        }
        return nearestLineByPredicate(normalizedText, position, true);
    }

    public WordLine findLineByTranslationNearIndex(String normalizedText, int index, int radius) {
        if (isEmpty(normalizedText) || index < 0 || lines.isEmpty()) {
            return null;
        }
        int anchor = Math.max(0, Math.min(index, lines.size() - 1));
        int start = Math.max(0, anchor - Math.max(0, radius));
        int end = Math.min(lines.size() - 1, anchor + Math.max(0, radius));
        WordLine best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            WordLine line = lines.get(i);
            if (!line.normalizedTranslation().equals(normalizedText)) {
                continue;
            }
            int distance = Math.abs(i - anchor);
            if (best == null || distance < bestDistance) {
                best = line;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Time-ordered outward walk equivalent to the historical full-list
     * nearest scan: candidates are visited in ascending
     * {@code |timeMillis - position|} order, earlier lines first on
     * distance ties, so the first match is exactly the line the old linear
     * scan kept. Phase 6 slice 5 replaced the per-query O(N) scans on the
     * alias, seedling, and frame-resolver paths with this early-exit walk.
     */
    private WordLine nearestLineByPredicate(String normalizedText, long position, boolean byTranslation) {
        if (lines.isEmpty()) {
            return null;
        }
        int size = lines.size();
        int insertion = firstLineIndexAfterOrAt(position);
        int left = insertion - 1;
        int right = insertion;
        while (left >= 0 || right < size) {
            long leftDistance = left >= 0 ? position - lines.get(left).timeMillis : Long.MAX_VALUE;
            long rightDistance = right < size ? lines.get(right).timeMillis - position : Long.MAX_VALUE;
            if (leftDistance <= rightDistance) {
                long clusterTime = lines.get(left).timeMillis;
                int clusterStart = left;
                while (clusterStart > 0 && lines.get(clusterStart - 1).timeMillis == clusterTime) {
                    clusterStart--;
                }
                for (int i = clusterStart; i <= left; i++) {
                    if (matchesLinePredicate(lines.get(i), normalizedText, byTranslation)) {
                        return lines.get(i);
                    }
                }
                left = clusterStart - 1;
            } else {
                long clusterTime = lines.get(right).timeMillis;
                int clusterEnd = right;
                while (clusterEnd + 1 < size && lines.get(clusterEnd + 1).timeMillis == clusterTime) {
                    clusterEnd++;
                }
                for (int i = right; i <= clusterEnd; i++) {
                    if (matchesLinePredicate(lines.get(i), normalizedText, byTranslation)) {
                        return lines.get(i);
                    }
                }
                right = clusterEnd + 1;
            }
        }
        return null;
    }

    private static boolean matchesLinePredicate(WordLine line, String normalizedText, boolean byTranslation) {
        if (line == null) {
            return false;
        }
        return byTranslation
                ? line.normalizedTranslation().equals(normalizedText)
                : WordLyricRenderSupport.matchesWordLineText(line, normalizedText);
    }

    public int translationCount() {
        int count = 0;
        for (WordLine line : lines) {
            if (!isEmpty(line.translation)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Copies a nearby translation across repeated renderable aliases during model assembly.
     * This was historically done from the draw resolver and mutated the model on a frame path.
     */
    public int propagateNearbyTranslations(int radius) {
        if (lines.isEmpty()) {
            return 0;
        }
        int copied = 0;
        int boundedRadius = Math.max(0, radius);
        boolean[] sourceEligible = new boolean[lines.size()];
        for (int index = 0; index < lines.size(); index++) {
            WordLine line = lines.get(index);
            sourceEligible[index] = line != null && !isEmpty(line.translation);
        }
        for (int index = 0; index < lines.size(); index++) {
            WordLine target = lines.get(index);
            if (target == null || !isEmpty(target.translation)) {
                continue;
            }
            String displayText = target.normalizedDisplayText();
            WordLine source = !isEmpty(displayText)
                    ? findNearbyTranslationSource(
                            displayText,
                            index,
                            boundedRadius,
                            sourceEligible)
                    : null;
            if (source == null && !isEmpty(target.normalizedText)) {
                source = findNearbyTranslationSource(
                        target.normalizedText,
                        index,
                        boundedRadius,
                        sourceEligible);
            }
            if (source == null || source == target || isEmpty(source.translation)) {
                continue;
            }
            target.translation = source.translation;
            copied++;
        }
        if (copied > 0) {
            renderableTextIndexBuilt = false;
        }
        return copied;
    }

    private WordLine findNearbyTranslationSource(
            String normalizedText,
            int index,
            int radius,
            boolean[] sourceEligible) {
        if (isEmpty(normalizedText) || index < 0 || sourceEligible == null) {
            return null;
        }
        int anchor = Math.max(0, Math.min(index, lines.size() - 1));
        int start = Math.max(0, anchor - radius);
        int end = Math.min(lines.size() - 1, anchor + radius);
        WordLine best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int candidateIndex = start; candidateIndex <= end; candidateIndex++) {
            if (candidateIndex >= sourceEligible.length || !sourceEligible[candidateIndex]) {
                continue;
            }
            WordLine candidate = lines.get(candidateIndex);
            if (!WordLyricRenderSupport.matchesWordLineText(candidate, normalizedText)) {
                continue;
            }
            int distance = Math.abs(candidateIndex - anchor);
            if (best == null || distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    public boolean hasDuplicateRenderableText(String normalizedText) {
        if (isEmpty(normalizedText)) {
            return false;
        }
        ensureRenderableTextIndex();
        Integer exactCount = renderableTextCounts.get(normalizedText);
        if (exactCount != null) {
            return exactCount > 1;
        }
        int count = 0;
        for (WordLine line : lines) {
            if (WordLyricRenderSupport.matchesWordLineText(line, normalizedText)
                    || line.normalizedTranslation().equals(normalizedText)) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private void ensureRenderableTextIndex() {
        if (renderableTextIndexBuilt) {
            return;
        }
        renderableTextCounts.clear();
        for (WordLine line : lines) {
            String primary = line.normalizedText;
            String display = line.normalizedDisplayText();
            String translation = line.normalizedTranslation();
            incrementRenderableTextCount(primary);
            if (!display.equals(primary)) {
                incrementRenderableTextCount(display);
            }
            if (!translation.equals(primary) && !translation.equals(display)) {
                incrementRenderableTextCount(translation);
            }
        }
        renderableTextIndexBuilt = true;
    }

    private void incrementRenderableTextCount(String normalizedText) {
        if (isEmpty(normalizedText)) {
            return;
        }
        Integer count = renderableTextCounts.get(normalizedText);
        renderableTextCounts.put(normalizedText, count == null ? 1 : count + 1);
    }

    private void ensureLineIndexCache() {
        int size = lines.size();
        WordLine first = size == 0 ? null : lines.get(0);
        WordLine last = size == 0 ? null : lines.get(size - 1);
        if (lineIndexCacheSize == size
                && lineIndexCacheFirst == first
                && lineIndexCacheLast == last) {
            return;
        }
        lineIndexByIdentity.clear();
        for (int i = 0; i < size; i++) {
            WordLine line = lines.get(i);
            if (line != null && !lineIndexByIdentity.containsKey(line)) {
                lineIndexByIdentity.put(line, i);
            }
        }
        lineIndexCacheSize = size;
        lineIndexCacheFirst = first;
        lineIndexCacheLast = last;
    }

    private void ensureOfficialIndexCache() {
        int size = officialLines.size();
        WordLine first = size == 0 ? null : officialLines.get(0);
        WordLine last = size == 0 ? null : officialLines.get(size - 1);
        if (officialIndexCacheSize == size
                && officialIndexCacheFirst == first
                && officialIndexCacheLast == last) {
            return;
        }
        officialIndexByIdentity.clear();
        for (int i = 0; i < size; i++) {
            WordLine line = officialLines.get(i);
            if (line != null && !officialIndexByIdentity.containsKey(line)) {
                officialIndexByIdentity.put(line, i);
            }
        }
        officialIndexCacheSize = size;
        officialIndexCacheFirst = first;
        officialIndexCacheLast = last;
    }

    private int lastLineIndexAtOrBefore(long position) {
        int low = 0;
        int high = lines.size() - 1;
        int best = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            WordLine line = lines.get(mid);
            if (line.timeMillis <= position) {
                best = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return best;
    }

    private int firstLineIndexAt(long timeMillis) {
        int low = 0;
        int high = lines.size() - 1;
        int best = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            long lineTime = lines.get(mid).timeMillis;
            if (lineTime >= timeMillis) {
                if (lineTime == timeMillis) {
                    best = mid;
                }
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return best;
    }

    private int firstLineIndexAfterOrAt(long timeMillis) {
        int low = 0;
        int high = lines.size() - 1;
        int best = lines.size();
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timeMillis >= timeMillis) {
                best = mid;
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return best;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
