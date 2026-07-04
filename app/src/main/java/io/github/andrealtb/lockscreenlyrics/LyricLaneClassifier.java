package io.github.andrealtb.lockscreenlyrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Classifies same-timestamp lyric variants into stable display lanes. */
final class LyricLaneClassifier {
    enum Lane {
        MAIN,
        TRANSLATION,
        ROMANIZATION,
        SOURCE_VARIANT,
        CREDIT
    }

    private LyricLaneClassifier() {
    }

    static Result classify(List<String> texts, long timeMillis) {
        if (texts == null || texts.isEmpty()) {
            return new Result(Collections.emptyList(), Collections.emptyList(), 0);
        }

        ArrayList<String> cleanTexts = new ArrayList<>(texts.size());
        ArrayList<String> lyricTexts = new ArrayList<>();
        ArrayList<Integer> lyricIndexes = new ArrayList<>();
        for (int index = 0; index < texts.size(); index++) {
            String clean = clean(texts.get(index));
            cleanTexts.add(clean);
            if (!clean.isEmpty() && !LyricMetadataFilter.isNonLyricInfoLine(clean, timeMillis)) {
                lyricTexts.add(clean);
                lyricIndexes.add(index);
            }
        }

        int primaryIndex = findFallbackPrimaryIndex(cleanTexts);
        if (!lyricTexts.isEmpty()) {
            int lyricPrimaryIndex = LyricLineVariantSelector.findPrimaryTextIndex(lyricTexts);
            if (lyricPrimaryIndex >= 0 && lyricPrimaryIndex < lyricIndexes.size()) {
                primaryIndex = lyricIndexes.get(lyricPrimaryIndex);
            }
        }

        ArrayList<Lane> lanes = new ArrayList<>(texts.size());
        String primary = primaryIndex >= 0 && primaryIndex < cleanTexts.size()
                ? cleanTexts.get(primaryIndex)
                : "";
        for (int index = 0; index < cleanTexts.size(); index++) {
            String candidate = cleanTexts.get(index);
            if (candidate.isEmpty()
                    || LyricMetadataFilter.isNonLyricInfoLine(candidate, timeMillis)) {
                lanes.add(Lane.CREDIT);
            } else if (index == primaryIndex) {
                lanes.add(Lane.MAIN);
            } else if (candidate.equals(primary)
                    || LockscreenIntegrationPolicy.sameLyricVariant(primary, candidate)) {
                lanes.add(Lane.SOURCE_VARIANT);
            } else if (isRomanization(cleanTexts, primaryIndex, primary, candidate)) {
                lanes.add(Lane.ROMANIZATION);
            } else {
                lanes.add(Lane.TRANSLATION);
            }
        }
        return new Result(cleanTexts, lanes, primaryIndex);
    }

    static int findPrimaryTextIndex(List<String> texts, long timeMillis) {
        return classify(texts, timeMillis).primaryIndex();
    }

    static boolean isTranslationLane(
            List<String> texts,
            int primaryIndex,
            String candidate,
            long timeMillis) {
        if (texts == null || primaryIndex < 0 || primaryIndex >= texts.size()) {
            return false;
        }
        ArrayList<String> values = new ArrayList<>(texts);
        values.add(candidate);
        Result result = classify(values, timeMillis);
        return result.laneAt(values.size() - 1) == Lane.TRANSLATION;
    }

    private static boolean isRomanization(
            List<String> texts,
            int primaryIndex,
            String primary,
            String candidate) {
        return LyricLineVariantSelector.isLikelyJapaneseRomanization(primary, candidate)
                || LyricLineVariantSelector.isLikelyPhoneticVariant(
                texts,
                primaryIndex,
                candidate);
    }

    private static int findFallbackPrimaryIndex(List<String> texts) {
        for (int index = 0; index < texts.size(); index++) {
            String text = texts.get(index);
            if (text != null && !text.isEmpty()) {
                return index;
            }
        }
        return 0;
    }

    private static String clean(String value) {
        return LyricTextSanitizer.removeIgnorableCharacters(value == null ? "" : value).trim();
    }

    static final class Result {
        private final List<String> texts;
        private final List<Lane> lanes;
        private final int primaryIndex;

        Result(List<String> texts, List<Lane> lanes, int primaryIndex) {
            this.texts = Collections.unmodifiableList(new ArrayList<>(texts));
            this.lanes = Collections.unmodifiableList(new ArrayList<>(lanes));
            this.primaryIndex = primaryIndex;
        }

        int primaryIndex() {
            return primaryIndex;
        }

        String primaryText() {
            return primaryIndex >= 0 && primaryIndex < texts.size() ? texts.get(primaryIndex) : "";
        }

        Lane laneAt(int index) {
            return index >= 0 && index < lanes.size() ? lanes.get(index) : Lane.CREDIT;
        }

        String firstTranslation() {
            for (int index = 0; index < lanes.size(); index++) {
                if (lanes.get(index) == Lane.TRANSLATION) {
                    return texts.get(index);
                }
            }
            return "";
        }
    }
}
