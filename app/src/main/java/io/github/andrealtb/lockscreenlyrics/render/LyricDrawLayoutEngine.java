package io.github.andrealtb.lockscreenlyrics.render;

import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.andrealtb.lockscreenlyrics.LyricLineBreakPolicy;

/** Owns wrapped-line planning, pooling, and the per-WordLine layout cache. */
public final class LyricDrawLayoutEngine implements LyricLineBreakPolicy.WidthMeasurer {
    private static final int MAX_WRAPPED_DRAW_LINES = 256;

    private final LyricLineBreakPolicy.WidthMeasurer widthMeasurer;
    private final ArrayList<LyricDrawLine> drawLines = new ArrayList<>(8);
    private final List<LyricDrawLine> readOnlyDrawLines =
            Collections.unmodifiableList(drawLines);
    private final LyricDrawLine[] drawLinePool =
            new LyricDrawLine[MAX_WRAPPED_DRAW_LINES];

    public LyricDrawLayoutEngine(LyricLineBreakPolicy.WidthMeasurer widthMeasurer) {
        if (widthMeasurer == null) {
            throw new IllegalArgumentException("widthMeasurer == null");
        }
        this.widthMeasurer = widthMeasurer;
    }

    public List<LyricDrawLine> lines() {
        return readOnlyDrawLines;
    }

    public void build(
            WordLine line,
            String text,
            float availableWidth,
            boolean singleLine,
            boolean balanceUntranslatedText,
            int textSizeKey,
            Typeface typeface) {
        drawLines.clear();
        if (line == null || isEmpty(text)) {
            return;
        }
        int widthKey = Math.max(1, Math.round(availableWidth));
        int boundedTextSizeKey = Math.max(1, textSizeKey);
        if (line.rendererLayoutWidthKey == widthKey
                && line.rendererLayoutTextSizeKey == boundedTextSizeKey
                && line.rendererLayoutTypeface == typeface
                && line.rendererLayoutSingleLine == singleLine
                && line.rendererLayoutBalanceUntranslatedText == balanceUntranslatedText) {
            restoreCachedLines(line);
            return;
        }

        int textStart = firstNonSpace(text, 0, text.length());
        int textEnd = lastNonSpace(text, textStart, text.length());
        if (textStart >= textEnd) {
            cacheLines(
                    line,
                    widthKey,
                    boundedTextSizeKey,
                    typeface,
                    singleLine,
                    balanceUntranslatedText);
            return;
        }

        float fullTextWidth = measure(text, textStart, textEnd);
        if (singleLine || fullTextWidth <= availableWidth) {
            addLine(textStart, textEnd, fullTextWidth);
            cacheLines(
                    line,
                    widthKey,
                    boundedTextSizeKey,
                    typeface,
                    singleLine,
                    balanceUntranslatedText);
            return;
        }

        if (balanceUntranslatedText
                && LyricLineBreakPolicy.shouldBalanceUntranslatedText(
                        text,
                        textStart,
                        textEnd,
                        availableWidth,
                        this)) {
            int balancedSplit = chooseBalancedSplit(
                    text,
                    textStart,
                    textEnd,
                    availableWidth);
            if (balancedSplit > textStart && balancedSplit < textEnd) {
                int leftEnd = lastNonSpace(text, textStart, balancedSplit);
                int rightStart = firstNonSpace(text, balancedSplit, textEnd);
                float leftWidth = measure(text, textStart, leftEnd);
                float rightWidth = measure(text, rightStart, textEnd);
                if (leftEnd > textStart
                        && rightStart < textEnd
                        && leftWidth <= availableWidth
                        && rightWidth <= availableWidth) {
                    addLine(textStart, leftEnd, leftWidth);
                    addLine(rightStart, textEnd, rightWidth);
                    cacheLines(
                            line,
                            widthKey,
                            boundedTextSizeKey,
                            typeface,
                            singleLine,
                            true);
                    return;
                }
            }
        }

        int lineStart = textStart;
        while (lineStart < textEnd && drawLines.size() < MAX_WRAPPED_DRAW_LINES) {
            int lineEnd = LyricLineBreakPolicy.chooseWrapEnd(
                    text,
                    lineStart,
                    textEnd,
                    availableWidth,
                    this);
            if (lineEnd <= lineStart) {
                break;
            }
            int cleanEnd = lastNonSpace(text, lineStart, lineEnd);
            if (lineStart < cleanEnd) {
                addLine(lineStart, cleanEnd, measure(text, lineStart, cleanEnd));
            }
            lineStart = firstNonSpace(text, lineEnd, textEnd);
        }
        cacheLines(
                line,
                widthKey,
                boundedTextSizeKey,
                typeface,
                singleLine,
                balanceUntranslatedText);
    }

    @Override
    public float measure(String text, int start, int end) {
        return widthMeasurer.measure(text, start, end);
    }

    private void restoreCachedLines(WordLine line) {
        for (int index = 0; index < line.rendererLayoutCount; index++) {
            addLine(
                    line.rendererLayoutStarts[index],
                    line.rendererLayoutEnds[index],
                    line.rendererLayoutWidths[index]);
        }
    }

    private void addLine(int start, int end, float width) {
        int index = drawLines.size();
        if (index >= drawLinePool.length) {
            return;
        }
        LyricDrawLine drawLine = drawLinePool[index];
        if (drawLine == null) {
            drawLine = new LyricDrawLine();
            drawLinePool[index] = drawLine;
        }
        drawLine.start = start;
        drawLine.end = end;
        drawLine.width = width;
        drawLines.add(drawLine);
    }

    private void cacheLines(
            WordLine line,
            int widthKey,
            int textSizeKey,
            Typeface typeface,
            boolean singleLine,
            boolean balanceUntranslatedText) {
        line.rendererLayoutWidthKey = widthKey;
        line.rendererLayoutTextSizeKey = textSizeKey;
        line.rendererLayoutTypeface = typeface;
        line.rendererLayoutSingleLine = singleLine;
        line.rendererLayoutBalanceUntranslatedText = balanceUntranslatedText;
        line.rendererLayoutCount = drawLines.size();
        line.ensureRendererLayoutCapacity(drawLines.size());
        for (int index = 0; index < drawLines.size(); index++) {
            LyricDrawLine drawLine = drawLines.get(index);
            line.rendererLayoutStarts[index] = drawLine.start;
            line.rendererLayoutEnds[index] = drawLine.end;
            line.rendererLayoutWidths[index] = drawLine.width;
        }
    }

    private int chooseBalancedSplit(String text, int start, int end, float availableWidth) {
        float bestScore = Float.MAX_VALUE;
        int bestSplit = -1;
        for (int index = start + 1; index < end - 1; index++) {
            if (!Character.isWhitespace(text.charAt(index))) {
                continue;
            }
            int leftEnd = lastNonSpace(text, start, index);
            int rightStart = firstNonSpace(text, index + 1, end);
            if (leftEnd <= start || rightStart >= end) {
                continue;
            }
            float leftWidth = measure(text, start, leftEnd);
            float rightWidth = measure(text, rightStart, end);
            float maxWidth = Math.max(leftWidth, rightWidth);
            float overflowPenalty = Math.max(0f, maxWidth - availableWidth) * 4f;
            float balancePenalty = Math.abs(leftWidth - rightWidth) * 0.7f;
            float score = overflowPenalty + balancePenalty + maxWidth;
            if (score < bestScore) {
                bestScore = score;
                bestSplit = index + 1;
            }
        }
        return bestSplit;
    }

    private static int firstNonSpace(String text, int start, int end) {
        int index = Math.max(0, start);
        int limit = Math.min(text.length(), end);
        while (index < limit && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int lastNonSpace(String text, int start, int end) {
        int index = Math.min(text.length(), end);
        int limit = Math.max(0, start);
        while (index > limit && Character.isWhitespace(text.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
