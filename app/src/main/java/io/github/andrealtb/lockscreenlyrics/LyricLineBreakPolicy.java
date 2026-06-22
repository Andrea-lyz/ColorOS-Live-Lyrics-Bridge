package io.github.andrealtb.lockscreenlyrics;

final class LyricLineBreakPolicy {
    private static final String PROHIBITED_LINE_START =
            "\u3001\u3002\uff0c\uff0e\uff01\uff1f\uff1a\uff1b"
                    + "\uff09\u300d\u300f\u3011\u3015\u3009\u300b\u3019\u3017"
                    + "\u2019\u201d\u2026\u30fc"
                    + "\u3041\u3043\u3045\u3047\u3049\u3063\u3083\u3085\u3087\u308e"
                    + "\u30a1\u30a3\u30a5\u30a7\u30a9\u30c3\u30e3\u30e5\u30e7\u30ee"
                    + "\u30f5\u30f6";
    private static final String PROHIBITED_LINE_END =
            "\uff08\u300c\u300e\u3010\u3014\u3008\u300a\u3018\u3016"
                    + "\u2018\u201c";

    interface WidthMeasurer {
        float measure(String text, int start, int end);
    }

    private LyricLineBreakPolicy() {
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

        int boundary = bestWhitespaceBoundary > start
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
