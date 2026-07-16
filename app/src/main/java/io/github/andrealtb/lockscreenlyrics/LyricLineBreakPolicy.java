package io.github.andrealtb.lockscreenlyrics;

final class LyricLineBreakPolicy {
    private static final String PROHIBITED_LINE_START =
            "、。，．！？：；"
                    + "）」』】〕〉》〙〗"
                    + "’”…ー"
                    + "ぁぃぅぇぉっゃゅょゎ"
                    + "ァィゥェォッャュョヮ"
                    + "ヵヶ";
    private static final String PROHIBITED_LINE_END =
            "（「『【〔〈《〘〖"
                    + "‘“";

    interface WidthMeasurer {
        float measure(String text, int start, int end);
    }

    private LyricLineBreakPolicy() {
    }

    static boolean shouldBalanceUntranslatedText(
            String text,
            int start,
            int end,
            float availableWidth,
            WidthMeasurer measurer) {
        if (text == null
                || measurer == null
                || start < 0
                || end > text.length()
                || start >= end
                || availableWidth <= 0f
                || measurer.measure(text, start, end) <= availableWidth) {
            return false;
        }
        if (!textContainsSpace(text, start, end)) {
            return false;
        }
        int visibleCharacters = 0;
        for (int i = start; i < end; i++) {
            char character = text.charAt(i);
            if (character == ':' || character == '：') {
                return false;
            }
            if (!Character.isWhitespace(character)) {
                visibleCharacters++;
            }
        }
        return visibleCharacters >= 8;
    }

    static int chooseWrapEnd(
            String text,
            int start,
            int end,
            float availableWidth,
            WidthMeasurer measurer) {
        if (text == null
                || measurer == null
                || start < 0
                || end > text.length()
                || start >= end
                || availableWidth <= 0f) {
            return start;
        }

        int bestCharacterBoundary = -1;
        int bestWhitespaceBoundary = -1;
        int index = start;
        while (index < end) {
            int codePoint = text.codePointAt(index);
            int next = index + Character.charCount(codePoint);
            if (measurer.measure(text, start, next) > availableWidth) {
                break;
            }
            bestCharacterBoundary = next;
            if (Character.isWhitespace(codePoint)) {
                bestWhitespaceBoundary = next;
            }
            index = next;
        }

        int boundary = bestCharacterBoundary == end
                ? end
                : bestWhitespaceBoundary > start
                ? bestWhitespaceBoundary
                : bestCharacterBoundary;
        if (boundary <= start) {
            boundary = Math.min(end, start + Character.charCount(text.codePointAt(start)));
        }
        if (bestWhitespaceBoundary <= start) {
            boundary = adjustForCjkPunctuation(text, start, end, boundary);
        }
        return Math.max(start + 1, Math.min(end, boundary));
    }

    private static boolean textContainsSpace(String text, int start, int end) {
        for (int i = start; i < end; i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static int adjustForCjkPunctuation(
            String text,
            int start,
            int end,
            int boundary) {
        int adjusted = boundary;
        while (adjusted < end && adjusted > start) {
            int nextCodePoint = text.codePointAt(adjusted);
            int previousCodePoint = text.codePointBefore(adjusted);
            if (!isProhibitedLineStart(nextCodePoint)
                    && !isProhibitedLineEnd(previousCodePoint)) {
                break;
            }
            adjusted = text.offsetByCodePoints(adjusted, -1);
        }
        return adjusted > start ? adjusted : boundary;
    }

    private static boolean isProhibitedLineStart(int codePoint) {
        return containsCodePoint(PROHIBITED_LINE_START, codePoint);
    }

    private static boolean isProhibitedLineEnd(int codePoint) {
        return containsCodePoint(PROHIBITED_LINE_END, codePoint);
    }

    private static boolean containsCodePoint(String characters, int codePoint) {
        return characters.indexOf(codePoint) >= 0;
    }
}
