package io.github.andrealtb.lockscreenlyrics;

final class LockscreenIntegrationPolicy {
    enum LyricInfoSource {
        PLAYER_INTEGRATION,
        MODULE_CAPTURE,
        PLAYER_FALLBACK,
        NONE
    }

    private LockscreenIntegrationPolicy() {
    }

    static LyricInfoSource chooseLyricInfoSource(
            boolean hasUsablePlayerLyricInfo,
            boolean hasPlayerIntegrationData,
            boolean hasCapturedLyricForCurrentTrack) {
        if (hasPlayerIntegrationData) {
            return LyricInfoSource.PLAYER_INTEGRATION;
        }
        if (hasCapturedLyricForCurrentTrack) {
            return LyricInfoSource.MODULE_CAPTURE;
        }
        return hasUsablePlayerLyricInfo ? LyricInfoSource.PLAYER_FALLBACK : LyricInfoSource.NONE;
    }

    static boolean shouldEnableOplusHistoryIntegration(
            boolean alreadyWhitelisted,
            boolean builtInAdapter,
            boolean manifestOptIn) {
        return alreadyWhitelisted || builtInAdapter || manifestOptIn;
    }

    static boolean shouldAcceptDebouncedEvent(
            long nowElapsedRealtime,
            long lastAcceptedElapsedRealtime,
            long debounceMillis) {
        if (debounceMillis <= 0L || lastAcceptedElapsedRealtime <= 0L) {
            return true;
        }
        return nowElapsedRealtime < lastAcceptedElapsedRealtime
                || nowElapsedRealtime - lastAcceptedElapsedRealtime >= debounceMillis;
    }

    static boolean activeTextMatches(String renderedText, String activeText) {
        return renderedText != null
                && !renderedText.isEmpty()
                && renderedText.equals(activeText);
    }

    static int parseTaggedNonNegativeInt(String message, String marker) {
        if (message == null || marker == null || marker.isEmpty()) {
            return -1;
        }
        int start = message.indexOf(marker);
        if (start < 0) {
            return -1;
        }
        start += marker.length();
        while (start < message.length() && Character.isWhitespace(message.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < message.length() && Character.isDigit(message.charAt(end))) {
            end++;
        }
        if (end == start) {
            return -1;
        }
        try {
            return Integer.parseInt(message.substring(start, end));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static long extrapolatePlaybackPosition(
            boolean inMotion,
            long storedPosition,
            long lastPositionUpdateTime,
            float speed,
            long nowElapsedRealtime) {
        if (storedPosition < 0L) {
            return -1L;
        }
        if (!inMotion
                || speed <= 0f
                || lastPositionUpdateTime <= 0L
                || nowElapsedRealtime <= lastPositionUpdateTime) {
            return storedPosition;
        }
        long elapsed = nowElapsedRealtime - lastPositionUpdateTime;
        return Math.max(0L, storedPosition + (long) (speed * elapsed));
    }

    static boolean isLikelyPlaybackTrackRestart(long previousPosition, long nextPosition) {
        return previousPosition >= 8_000L
                && nextPosition >= 0L
                && nextPosition <= 1_500L
                && previousPosition - nextPosition >= 6_000L;
    }

    static int clampSlidingWindowStart(
            int activeSegmentIndex,
            int totalSegments,
            int visibleSegments) {
        if (activeSegmentIndex < 0 || totalSegments <= 0 || visibleSegments <= 0) {
            return 0;
        }
        return Math.max(
                0,
                Math.min(activeSegmentIndex, Math.max(0, totalSegments - visibleSegments)));
    }

    static int lineTimedSlidingWindowStart(
            long positionMillis,
            long lineStartMillis,
            long lineEndMillis,
            int totalSegments,
            int visibleSegments) {
        if (totalSegments <= visibleSegments
                || visibleSegments <= 0
                || lineEndMillis <= lineStartMillis) {
            return 0;
        }
        float progress = Math.max(
                0f,
                Math.min(
                        1f,
                        (positionMillis - lineStartMillis)
                                / (float) (lineEndMillis - lineStartMillis)));
        return lineTimedSlidingWindowStartForProgress(
                progress,
                null,
                totalSegments,
                visibleSegments);
    }

    static int lineTimedSlidingWindowStart(
            long positionMillis,
            long lineStartMillis,
            long lineEndMillis,
            float[] segmentWidths,
            int totalSegments,
            int visibleSegments) {
        if (totalSegments <= visibleSegments
                || visibleSegments <= 0
                || lineEndMillis <= lineStartMillis) {
            return 0;
        }
        float progress = Math.max(
                0f,
                Math.min(
                        1f,
                        (positionMillis - lineStartMillis)
                                / (float) (lineEndMillis - lineStartMillis)));
        return lineTimedSlidingWindowStartForProgress(
                progress,
                segmentWidths,
                totalSegments,
                visibleSegments);
    }

    private static int lineTimedSlidingWindowStartForProgress(
            float progress,
            float[] segmentWidths,
            int totalSegments,
            int visibleSegments) {
        int activeSegment = activeSegmentForLineProgress(
                progress,
                segmentWidths,
                totalSegments);
        // Keep the currently revealed line visible at the bottom of the window.
        // Moving as soon as its predecessor starts makes line-timed (pseudo word-level)
        // lyrics jump a full visual line ahead of the progress highlight.
        return clampSlidingWindowStart(
                activeSegment - visibleSegments + 1,
                totalSegments,
                visibleSegments);
    }

    private static int activeSegmentForLineProgress(
            float progress,
            float[] segmentWidths,
            int totalSegments) {
        float totalWidth = 0f;
        if (segmentWidths != null) {
            for (int i = 0; i < totalSegments && i < segmentWidths.length; i++) {
                totalWidth += Math.max(0f, segmentWidths[i]);
            }
        }
        if (totalWidth <= 0f) {
            return Math.min(
                    totalSegments - 1,
                    (int) Math.floor(progress * totalSegments));
        }

        float revealWidth = progress * totalWidth;
        float consumedWidth = 0f;
        for (int i = 0; i < totalSegments; i++) {
            float segmentWidth = i < segmentWidths.length
                    ? Math.max(0f, segmentWidths[i])
                    : 0f;
            consumedWidth += segmentWidth;
            if (revealWidth < consumedWidth || i == totalSegments - 1) {
                return i;
            }
        }
        return totalSegments - 1;
    }

    static boolean shouldPreserveStableLyricInfoForRelay(
            boolean hasStableModuleLyricInfo,
            boolean hasFreshIncomingTrackLyric,
            boolean sameDuration,
            boolean incomingTitleMatchesCurrentLyric,
            boolean incomingArtistReferencesStableTrack) {
        return hasStableModuleLyricInfo
                && !hasFreshIncomingTrackLyric
                && sameDuration
                && incomingTitleMatchesCurrentLyric
                && incomingArtistReferencesStableTrack;
    }

    static boolean hasProgressiveInlineTiming(
            int timedSegmentCount,
            long firstSegmentStartMillis,
            long lastSegmentStartMillis,
            long lineStartMillis,
            long explicitEndMillis) {
        if (timedSegmentCount <= 0) {
            return false;
        }
        if (timedSegmentCount == 1) {
            return explicitEndMillis > lineStartMillis;
        }
        return lastSegmentStartMillis > firstSegmentStartMillis;
    }

    static boolean isLikelyTitleArtistCredit(String text, long timeMillis) {
        if (text == null
                || text.trim().isEmpty()
                || timeMillis < 0L
                || timeMillis > 15_000L) {
            return false;
        }
        String normalized = text.trim();
        if (normalized.length() > 96 || containsSentenceEndingPunctuation(normalized)) {
            return false;
        }

        int separator = findSpacedTitleArtistSeparator(normalized);
        if (separator <= 0) {
            return false;
        }
        String title = normalized.substring(0, separator).trim();
        String artist = normalized.substring(separator + 1).trim();
        return title.length() >= 2
                && artist.length() >= 2
                && containsLatinLetter(title)
                && containsLatinLetter(artist);
    }

    static boolean shouldAttachDelayedTranslation(
            boolean previousHasWordTiming,
            boolean candidateLooksLikeTranslation,
            long previousStartMillis,
            long previousEndMillis,
            long candidateTimeMillis,
            long nextPrimaryTimeMillis) {
        if (!previousHasWordTiming
                || !candidateLooksLikeTranslation
                || candidateTimeMillis < previousStartMillis) {
            return false;
        }

        boolean nearPreviousEnd = previousEndMillis >= previousStartMillis
                && candidateTimeMillis <= previousEndMillis + 1_500L;
        boolean immediatelyBeforeNextPrimary = nextPrimaryTimeMillis >= candidateTimeMillis
                && nextPrimaryTimeMillis - candidateTimeMillis <= 1_000L;
        return nearPreviousEnd || immediatelyBeforeNextPrimary;
    }

    static boolean sameLyricVariant(String first, String second) {
        String firstKey = lyricIdentityKey(first);
        String secondKey = lyricIdentityKey(second);
        if (firstKey.isEmpty() || secondKey.isEmpty()) {
            return false;
        }
        if (firstKey.equals(secondKey)) {
            return true;
        }

        // Enhanced LRC sources sometimes publish both a word-timed line and a plain line with
        // an extra parenthetical/backing-vocal suffix. That is still one source lyric, not a
        // translation. Restrict fuzzy prefix matching to Latin text so genuine CJK translation
        // lines that happen to share a few characters are not discarded.
        return containsLatinLetter(first)
                && containsLatinLetter(second)
                && Math.min(firstKey.length(), secondKey.length()) >= 5
                && (firstKey.startsWith(secondKey) || secondKey.startsWith(firstKey));
    }

    static boolean isProductionDetailLine(String text, long timeMillis) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String normalized = text.trim();
        int separator = normalized.indexOf(':');
        if (separator < 0) {
            separator = normalized.indexOf('\uFF1A');
        }
        String label = (separator >= 0 ? normalized.substring(0, separator) : normalized)
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
        if (label.isEmpty() || label.length() > 40 || !isProductionDetailLabel(label)) {
            return false;
        }

        // A labelled credit is unambiguous even after a long intro. For legacy unlabelled
        // "Lyrics by ..." lines, retain the conservative opening-only window.
        return separator >= 0
                ? separator + 1 < normalized.length()
                : timeMillis >= 0L && timeMillis <= 15_000L;
    }

    private static boolean isProductionDetailLabel(String label) {
        String[] englishLabels = {
                "lyrics by", "lyric by", "written by", "composed by", "composer",
                "produced by", "producer", "arranged by", "performed by", "mixed by",
                "mastered by", "recorded by", "engineered by", "vocals by"
        };
        for (String candidate : englishLabels) {
            if (label.equals(candidate) || label.startsWith(candidate + " ")) {
                return true;
            }
        }

        String[] cjkLabels = {
                "\u4f5c\u8bcd", // 作词
                "\u4f5c\u66f2", // 作曲
                "\u7f16\u66f2", // 编曲
                "\u5236\u4f5c", // 制作
                "\u6f14\u5531", // 演唱
                "\u6b4c\u624b", // 歌手
                "\u539f\u5531", // 原唱
                "\u7ffb\u5531", // 翻唱
                "\u6df7\u97f3", // 混音
                "\u6bcd\u5e26", // 母带
                "\u5f55\u97f3", // 录音
                "\u76d1\u5236", // 监制
                "\u914d\u5531", // 配唱
                "\u4eba\u58f0", // 人声
                "\u5409\u4ed6", // 吉他
                "\u8d1d\u65af", // 贝斯
                "\u5f26\u4e50", // 弦乐
                "\u548c\u58f0", // 和声
                "\u4e50\u8c31"  // 乐谱
        };
        for (String candidate : cjkLabels) {
            if (label.contains(candidate)) {
                return true;
            }
        }
        return label.equals("\u8bcd") || label.equals("\u66f2"); // 词 / 曲
    }

    private static String lyricIdentityKey(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder key = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                key.append(Character.toLowerCase(character));
            }
        }
        return key.toString();
    }

    private static boolean containsLatinLetter(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if ((character >= 'A' && character <= 'Z')
                    || (character >= 'a' && character <= 'z')) {
                return true;
            }
        }
        return false;
    }

    private static int findSpacedTitleArtistSeparator(String text) {
        for (int index = 1; index < text.length() - 1; index++) {
            if (!isTitleArtistSeparator(text.charAt(index))
                    || !Character.isWhitespace(text.charAt(index - 1))
                    || !Character.isWhitespace(text.charAt(index + 1))) {
                continue;
            }
            return index;
        }
        return -1;
    }

    private static boolean isTitleArtistSeparator(char value) {
        return value == '-'
                || value == '\u2010'
                || value == '\u2011'
                || value == '\u2012'
                || value == '\u2013'
                || value == '\u2014'
                || value == '\u2212';
    }

    private static boolean containsSentenceEndingPunctuation(String text) {
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '.'
                    || value == '?'
                    || value == '!'
                    || value == '\u3002'
                    || value == '\uFF1F'
                    || value == '\uFF01') {
                return true;
            }
        }
        return false;
    }

}
