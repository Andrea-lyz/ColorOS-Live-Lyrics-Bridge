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

    static boolean shouldPreserveExternalRendererForSameTrackSeek(
            boolean likelyTrackRestart,
            boolean hasExternalModel,
            boolean externalModelReady,
            boolean mediaStillMatchesModel) {
        return likelyTrackRestart
                && hasExternalModel
                && externalModelReady
                && mediaStillMatchesModel;
    }

    static boolean shouldRetainLyricModeForTransientSurfaceMiss(
            boolean lyricModeActive,
            boolean hasLyricModel,
            boolean surfaceReactivationPending,
            long elapsedSinceSurfaceSignalMillis,
            long transitionGraceMillis) {
        if (!lyricModeActive || !hasLyricModel) {
            return false;
        }
        boolean recentSurfaceSignal = elapsedSinceSurfaceSignalMillis >= 0L
                && elapsedSinceSurfaceSignalMillis <= Math.max(0L, transitionGraceMillis);
        return surfaceReactivationPending
                || recentSurfaceSignal;
    }

    static boolean shouldModulePositionLyricsRecycler(
            boolean aodLowFrameRateMode,
            boolean screenInteractive) {
        return aodLowFrameRateMode || !screenInteractive;
    }

    static boolean isPlaybackPositionJump(
            int state,
            long previousPosition,
            long nextPosition,
            long jumpThresholdMillis) {
        if (previousPosition < 0L
                || nextPosition < 0L
                || jumpThresholdMillis < 0L
                || Math.abs(nextPosition - previousPosition) < jumpThresholdMillis) {
            return false;
        }
        // Scrubbing is reported while paused or buffering before the following PLAYING state.
        return state == 2 || state == 3 || state == 4 || state == 5 || state == 6;
    }

    static boolean shouldSearchAttachedRecyclerForLyricCandidates(
            int rememberedCandidateCount,
            int effectivelyVisibleCandidateCount) {
        return rememberedCandidateCount <= 0 || effectivelyVisibleCandidateCount <= 0;
    }

    static int chooseOfficialLyricVisualIndex(
            int officialIndex,
            int playbackIndex,
            int fallbackIndex) {
        if (officialIndex >= 0) {
            return officialIndex;
        }
        return playbackIndex >= 0 ? playbackIndex : fallbackIndex;
    }

    static float officialInactiveRowScale(boolean scaleEnabled, int inactiveScalePercent) {
        if (!scaleEnabled) {
            return 1f;
        }
        return Math.max(0.75f, Math.min(1f, inactiveScalePercent / 100f));
    }

    static boolean shouldDeferBrightLyricPixelsForGeometryCommit(
            boolean modelGeometryPending,
            boolean aodLowFrameRateMode) {
        return modelGeometryPending && !aodLowFrameRateMode;
    }

    static boolean shouldRevealCommittedBrightLyricModel(
            boolean hasAttachedBrightSurface,
            boolean aodLowFrameRateMode,
            int motionMode) {
        return hasAttachedBrightSurface
                && !aodLowFrameRateMode
                && motionMode != LyricUiConfig.MOTION_OFF;
    }

    static boolean shouldPreservePowerampPositionForSameTrackReattach(
            boolean previousExternalTrackKnown,
            boolean sameExternalTrack,
            boolean payloadMatchesTrack,
            boolean powerampModelMatchesTrack) {
        return (!previousExternalTrackKnown || sameExternalTrack)
                && payloadMatchesTrack
                && powerampModelMatchesTrack;
    }

    static boolean shouldTrustPowerampNativePosition(
            long nowElapsedRealtime,
            long authorityUntilElapsedRealtime) {
        return authorityUntilElapsedRealtime > 0L
                && nowElapsedRealtime <= authorityUntilElapsedRealtime;
    }

    static boolean isLyricsRecyclerComputingLayoutException(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message != null
                && message.contains("LyricsRecyclerView")
                && message.contains("computing a layout");
    }

    static boolean isExternalLyricPayloadSizeAcceptable(
            int lyricInfoChars,
            int lyricChars,
            int rawLyricChars,
            int translationChars,
            int largestMetadataFieldChars,
            int maxPayloadFieldChars,
            int maxTotalPayloadChars,
            int maxMetadataFieldChars) {
        if (lyricInfoChars < 0
                || lyricChars < 0
                || rawLyricChars < 0
                || translationChars < 0
                || largestMetadataFieldChars < 0
                || maxPayloadFieldChars < 0
                || maxTotalPayloadChars < 0
                || maxMetadataFieldChars < 0) {
            return false;
        }
        int largestPayloadField = Math.max(
                Math.max(lyricInfoChars, lyricChars),
                Math.max(rawLyricChars, translationChars));
        long totalPayloadChars = (long) lyricInfoChars
                + lyricChars
                + rawLyricChars
                + translationChars;
        return largestPayloadField <= maxPayloadFieldChars
                && totalPayloadChars <= maxTotalPayloadChars
                && largestMetadataFieldChars <= maxMetadataFieldChars;
    }

    static boolean shouldAllowGenerationScopedExternalLyricPromotion(
            boolean sourceAllowsGenerationScopedPromotion,
            long trackGeneration,
            boolean currentGeneratedDocument,
            boolean activePlayerContext) {
        return sourceAllowsGenerationScopedPromotion
                && trackGeneration > 0L
                && currentGeneratedDocument
                && activePlayerContext;
    }

    static boolean shouldReplaySystemUiLyricLoadAfterExternalPromotion(
            boolean sourceRequiresRefresh,
            boolean currentGeneratedDocument,
            boolean contextMatchesPlayer,
            boolean contextMatchesTrack,
            boolean alreadyCommitted,
            long contextAgeMillis,
            long maxContextAgeMillis) {
        return sourceRequiresRefresh
                && currentGeneratedDocument
                && contextMatchesPlayer
                && contextMatchesTrack
                && !alreadyCommitted
                && contextAgeMillis >= 0L
                && contextAgeMillis <= Math.max(0L, maxContextAgeMillis);
    }

    static boolean shouldUseRecentSystemUiTrackContext(
            boolean sourceRequiresRefresh,
            boolean contextMatchesPlayer,
            boolean contextMatchesTrack,
            long contextAgeMillis,
            long maxContextAgeMillis) {
        return sourceRequiresRefresh
                && contextMatchesPlayer
                && contextMatchesTrack
                && contextAgeMillis >= 0L
                && contextAgeMillis <= Math.max(0L, maxContextAgeMillis);
    }

    static boolean shouldIgnoreExternalPlaybackStateForRecentSystemUiTrack(
            boolean sourceRequiresRefresh,
            boolean contextMatchesPlayer,
            boolean contextMatchesTrack,
            boolean playbackMatchesCurrentLyricTrack,
            long contextAgeMillis,
            long maxContextAgeMillis) {
        return sourceRequiresRefresh
                && contextMatchesPlayer
                && !contextMatchesTrack
                && playbackMatchesCurrentLyricTrack
                && contextAgeMillis >= 0L
                && contextAgeMillis <= Math.max(0L, maxContextAgeMillis);
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

    static float passiveLinePanProgress(float lineProgress) {
        final float startProgress = 0.215f;
        final float endProgress = 0.785f;
        float rawProgress = Math.max(
                0f,
                Math.min(
                        1f,
                        (lineProgress - startProgress) / (endProgress - startProgress)));
        return rawProgress * rawProgress * rawProgress
                * (rawProgress * (rawProgress * 6f - 15f) + 10f);
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

    static long estimateFinalLineTimedDurationMillis(
            long previousLineIntervalMillis,
            String text) {
        int readingUnits = 0;
        String safeText = text == null ? "" : text;
        for (int offset = 0; offset < safeText.length();) {
            int codePoint = safeText.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!Character.isLetterOrDigit(codePoint)) {
                continue;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            boolean denseScript = script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL
                    || script == Character.UnicodeScript.BOPOMOFO;
            readingUnits += denseScript ? 2 : 1;
        }

        long textEstimateMillis = Math.max(
                2_800L,
                Math.min(8_000L, 1_800L + Math.min(70, readingUnits) * 90L));
        if (previousLineIntervalMillis < 1_000L || previousLineIntervalMillis > 12_000L) {
            return textEstimateMillis;
        }
        long cadenceEstimateMillis = Math.max(
                2_800L,
                Math.min(8_000L, previousLineIntervalMillis));
        return Math.max(textEstimateMillis, cadenceEstimateMillis);
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

    static boolean hasSuspiciousInlineTimingGap(
            int timedSegmentCount,
            long firstSegmentStartMillis,
            long lastSegmentStartMillis,
            long maxAdjacentStartGapMillis) {
        return LyricTimingRepair.hasSuspiciousInlineTimingGap(
                timedSegmentCount,
                firstSegmentStartMillis,
                lastSegmentStartMillis,
                maxAdjacentStartGapMillis);
    }

    static boolean shouldUseTranslationReplacementTransition(
            boolean hasLineWindow,
            int totalMainLineCount,
            int visibleMainLineCount,
            float layoutTranslationAmount) {
        return false;
    }

    static boolean isLikelyInlineTimedMainLyricPrefix(
            int visibleSegmentCount,
            int compactSegmentCount,
            long firstVisibleSegmentStartMillis,
            long lastVisibleSegmentStartMillis) {
        if (visibleSegmentCount < 2) {
            return false;
        }
        if (compactSegmentCount == visibleSegmentCount) {
            return true;
        }
        if (firstVisibleSegmentStartMillis < 0L || lastVisibleSegmentStartMillis < 0L) {
            return false;
        }
        return hasProgressiveInlineTiming(
                visibleSegmentCount,
                firstVisibleSegmentStartMillis,
                lastVisibleSegmentStartMillis,
                -1L,
                -1L);
    }

    static boolean shouldFallbackToLineTimedLrcForSparseInlineTiming(
            int parsedLineCount,
            int inlineTimedLineCount) {
        if (parsedLineCount < 12 || inlineTimedLineCount <= 0) {
            return false;
        }
        return inlineTimedLineCount * 100 < parsedLineCount * 35;
    }

    static boolean isShortLatinTailAfterMainLyric(String prefixText, String suffixText) {
        String prefix = normalizeSimpleLyricText(prefixText);
        String suffix = normalizeSimpleLyricText(suffixText);
        if (prefix.isEmpty()
                || suffix.isEmpty()
                || !containsNonAscii(prefix)
                || containsLyricLeadSeparator(prefix)
                || !containsLatinLetter(suffix)
                || containsNonAscii(suffix)) {
            return false;
        }

        int tokenCount = 0;
        int index = 0;
        while (index < suffix.length()) {
            while (index < suffix.length() && !isAsciiWordLike(suffix.charAt(index))) {
                index++;
            }
            int start = index;
            while (index < suffix.length() && isAsciiWordLike(suffix.charAt(index))) {
                index++;
            }
            if (start >= index) {
                continue;
            }
            if (++tokenCount > 2
                    || !isShortLatinTailToken(suffix.substring(start, index))) {
                return false;
            }
        }
        return tokenCount > 0;
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
                && containsLetter(title)
                && containsLetter(artist);
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

    static boolean shouldTreatAsDelayedInlineTranslation(
            boolean delayedTranslationsEnabled,
            boolean hasUsableInlineTiming,
            int sourceTimedSegmentCount,
            int sameTimestampVariantCount,
            boolean containsLatinText) {
        return delayedTranslationsEnabled
                && !hasUsableInlineTiming
                && sourceTimedSegmentCount <= 1
                && sameTimestampVariantCount == 1
                && !containsLatinText;
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
        // translation. Do not treat ordinary Latin prefixes as the same line: "He did" and
        // "He did it" are different lyric text, and collapsing them can split bilingual rows.
        return containsLatinLetter(first)
                && containsLatinLetter(second)
                && (isParentheticalLatinSourceVariant(first, second, firstKey, secondKey)
                || isParentheticalLatinSourceVariant(second, first, secondKey, firstKey));
    }

    private static boolean isParentheticalLatinSourceVariant(
            String shorterText,
            String longerText,
            String shorterKey,
            String longerKey) {
        if (shorterKey.length() < 5
                || longerKey.length() <= shorterKey.length()
                || !longerKey.startsWith(shorterKey)) {
            return false;
        }
        String shorter = normalizeSimpleLyricText(shorterText);
        String longer = normalizeSimpleLyricText(longerText);
        if (shorter.isEmpty()
                || longer.length() <= shorter.length()
                || !longer.regionMatches(true, 0, shorter, 0, shorter.length())) {
            return false;
        }
        String suffix = longer.substring(shorter.length()).trim();
        return suffix.startsWith("(")
                || suffix.startsWith("[")
                || suffix.startsWith("（")
                || suffix.startsWith("【");
    }

    private static boolean isShortLatinTailToken(String token) {
        String key = lyricIdentityKey(token);
        if (key.length() < 2 || key.length() > 8) {
            return false;
        }
        if (isUpperLatinAcronymToken(token, key)) {
            return true;
        }
        return isShortLatinVocalToken(key);
    }

    private static boolean isUpperLatinAcronymToken(String token, String key) {
        if (key.length() < 3 || key.length() > 6) {
            return false;
        }
        boolean hasUpper = false;
        for (int index = 0; index < token.length(); index++) {
            char ch = token.charAt(index);
            if (ch >= 'a' && ch <= 'z') {
                return false;
            }
            if (ch >= 'A' && ch <= 'Z') {
                hasUpper = true;
            }
        }
        return hasUpper;
    }

    private static boolean isShortLatinVocalToken(String key) {
        switch (key) {
            case "ah":
            case "ahh":
            case "ha":
            case "haa":
            case "hm":
            case "mm":
            case "na":
            case "la":
            case "da":
            case "oh":
            case "ooh":
            case "oooh":
            case "hoh":
            case "hey":
            case "ya":
            case "yeah":
            case "woo":
            case "wooh":
            case "whoa":
                return true;
            default:
                return false;
        }
    }

    static boolean isProductionDetailLine(String text, long timeMillis) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        String normalized = text.trim();
        int separator = normalized.indexOf(':');
        if (separator < 0) {
            separator = normalized.indexOf('：');
        }
        String label = (separator >= 0 ? normalized.substring(0, separator) : normalized)
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
        if (separator < 0) {
            return timeMillis >= 0L
                    && timeMillis <= 15_000L
                    && containsLatinLetter(label)
                    && isProductionDetailLabel(label);
        }
        if (label.isEmpty() || label.length() > 40 || !isProductionDetailLabel(label)) {
            return false;
        }

        // A labelled credit is unambiguous even after a long intro.
        return separator + 1 < normalized.length();
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
                "作词", // 作词
                "作曲", // 作曲
                "编曲", // 编曲
                "制作", // 制作
                "演唱", // 演唱
                "歌手", // 歌手
                "原唱", // 原唱
                "翻唱", // 翻唱
                "混音", // 混音
                "母带", // 母带
                "录音", // 录音
                "监制", // 监制
                "配唱", // 配唱
                "人声", // 人声
                "吉他", // 吉他
                "贝斯", // 贝斯
                "鼓", // 鼓
                "和音", // 和音
                "监棚", // 监棚
                "弦乐", // 弦乐
                "和声", // 和声
                "乐谱"  // 乐谱
        };
        for (String candidate : cjkLabels) {
            if (label.contains(candidate)) {
                return true;
            }
        }
        return label.equals("词") || label.equals("曲"); // 词 / 曲
    }

    private static boolean containsLetter(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isLetter(value.charAt(index))) {
                return true;
            }
        }
        return false;
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

    private static String normalizeSimpleLyricText(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(trimmed.length());
        boolean inWhitespace = false;
        for (int index = 0; index < trimmed.length(); index++) {
            char ch = trimmed.charAt(index);
            if (ch == ' ' || ch == '\t') {
                if (!inWhitespace) {
                    result.append(' ');
                }
                inWhitespace = true;
            } else {
                result.append(ch);
                inWhitespace = false;
            }
        }
        return result.toString();
    }

    private static boolean containsNonAscii(String value) {
        if (value == null) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) > 0x7F) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsLyricLeadSeparator(String value) {
        return value != null
                && (value.indexOf(':') >= 0 || value.indexOf('：') >= 0);
    }

    private static boolean isAsciiWordLike(char value) {
        return (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9');
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
                || value == '‐'
                || value == '‑'
                || value == '‒'
                || value == '–'
                || value == '—'
                || value == '−';
    }

    private static boolean containsSentenceEndingPunctuation(String text) {
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '.'
                    || value == '?'
                    || value == '!'
                    || value == '。'
                    || value == '？'
                    || value == '！') {
                return true;
            }
        }
        return false;
    }

}
