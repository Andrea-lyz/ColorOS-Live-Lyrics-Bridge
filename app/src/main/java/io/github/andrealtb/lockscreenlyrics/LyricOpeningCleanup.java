package io.github.andrealtb.lockscreenlyrics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Display-only opening-line cleanup. Parsing, timing and lane semantics stay outside this class. */
final class LyricOpeningCleanup {
    static final int MAX_PREVIEW_LINES = 80;
    static final int MAX_PREVIEW_CHARS = 64 * 1024;
    private static final int OPENING_MAX_LINES = 32;
    private static final long OPENING_MAX_TIME_MILLIS = 30_000L;
    private static final Pattern TIME_TAG = Pattern.compile(
            "[\\[<]([0-9]{1,3}:[0-9]{2}(?:[.:][0-9]{1,3})?)[\\]>]");
    private static final Pattern YRC_LINE_TAG = Pattern.compile("\\[(\\d{1,10}),\\d{1,10}]");

    enum Reason {
        VISIBLE,
        FIXED_PARSING,
        TRACK_OVERRIDE,
        BUILTIN_COPYRIGHT,
        BUILTIN_PRODUCTION,
        BUILTIN_TITLE_ARTIST,
        LEARNED_PREFIX,
        LEARNED_EXACT
    }

    static final class Line {
        final int rawIndex;
        final String raw;
        final String text;
        final long timeMillis;
        final String fingerprint;

        Line(int rawIndex, String raw, String text, long timeMillis) {
            this.rawIndex = rawIndex;
            this.raw = raw == null ? "" : raw;
            this.text = text == null ? "" : text;
            this.timeMillis = timeMillis;
            this.fingerprint = fingerprint(this.text);
        }
    }

    static final class Decision {
        final Line line;
        final Reason reason;
        final boolean hidden;

        Decision(Line line, Reason reason, boolean hidden) {
            this.line = line;
            this.reason = reason;
            this.hidden = hidden;
        }
    }

    static final class Result {
        final String timedText;
        final List<Decision> decisions;

        Result(String timedText, List<Decision> decisions) {
            this.timedText = timedText;
            this.decisions = decisions;
        }
    }

    private LyricOpeningCleanup() {
    }

    static Result clean(
            String timedText,
            String trackKey,
            LyricContentCleanupConfig config) {
        return clean(timedText, "", trackKey, config);
    }

    static Result clean(
            String timedText,
            String referenceTimedText,
            String trackKey,
            LyricContentCleanupConfig config) {
        LyricContentCleanupConfig safeConfig = config == null
                ? LyricContentCleanupConfig.defaults()
                : config;
        List<Line> lines = parseLines(timedText);
        long fallbackCutoffMillis = manualCutoffTime(
                parseLines(referenceTimedText),
                trackKey,
                safeConfig);
        List<Decision> decisions = analyze(
                lines,
                trackKey,
                safeConfig,
                fallbackCutoffMillis);
        if (timedText == null || timedText.isEmpty() || lines.isEmpty()) {
            return new Result(timedText == null ? "" : timedText, decisions);
        }
        StringBuilder filtered = new StringBuilder(timedText.length());
        int decisionIndex = 0;
        String[] rawLines = timedText.split("\\r?\\n", -1);
        for (int index = 0; index < rawLines.length; index++) {
            boolean hidden = false;
            if (decisionIndex < decisions.size()
                    && decisions.get(decisionIndex).line.rawIndex == index) {
                hidden = decisions.get(decisionIndex).hidden;
                decisionIndex++;
            }
            if (!hidden) {
                if (filtered.length() > 0) filtered.append('\n');
                filtered.append(rawLines[index]);
            }
        }
        return new Result(filtered.toString(), decisions);
    }

    static List<Decision> analyze(
            List<Line> lines,
            String trackKey,
            LyricContentCleanupConfig config) {
        return analyze(lines, trackKey, config, -1L);
    }

    private static List<Decision> analyze(
            List<Line> lines,
            String trackKey,
            LyricContentCleanupConfig config,
            long fallbackCutoffMillis) {
        ArrayList<Decision> result = new ArrayList<>();
        if (lines == null || lines.isEmpty()) return result;
        LyricContentCleanupConfig safeConfig = config == null
                ? LyricContentCleanupConfig.defaults()
                : config;
        String firstFingerprint = safeConfig.firstFormalLineByTrack.get(
                trackKey == null ? "" : trackKey);
        int manualCutoff = -1;
        if (firstFingerprint != null) {
            for (int index = 0; index < lines.size(); index++) {
                if (firstFingerprint.equals(lines.get(index).fingerprint)) {
                    manualCutoff = index;
                    break;
                }
            }
        }
        if (manualCutoff < 0 && fallbackCutoffMillis >= 0L) {
            for (int index = 0; index < lines.size(); index++) {
                if (lines.get(index).timeMillis >= fallbackCutoffMillis) {
                    manualCutoff = index;
                    break;
                }
            }
        }
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            if (LyricMetadataFilter.isParsingProtectedLine(line.text)) {
                result.add(new Decision(line, Reason.FIXED_PARSING, true));
                continue;
            }
            if (manualCutoff >= 0) {
                result.add(new Decision(
                        line,
                        index < manualCutoff ? Reason.TRACK_OVERRIDE : Reason.VISIBLE,
                        index < manualCutoff));
                continue;
            }
            if (!isOpeningCandidate(line, index)) {
                result.add(new Decision(line, Reason.VISIBLE, false));
                continue;
            }
            Reason reason = builtInReason(line, safeConfig);
            if (reason == Reason.VISIBLE
                    && safeConfig.titleArtistLeadEnabled
                    && index > 0
                    && result.get(index - 1).reason == Reason.BUILTIN_TITLE_ARTIST
                    && isLikelyArtistContinuation(lines.get(index - 1), line)) {
                reason = Reason.BUILTIN_TITLE_ARTIST;
            }
            if (reason == Reason.VISIBLE) {
                reason = learnedReason(line.text, safeConfig.learnedRules);
            }
            result.add(new Decision(line, reason, reason != Reason.VISIBLE));
        }
        return result;
    }

    private static long manualCutoffTime(
            List<Line> referenceLines,
            String trackKey,
            LyricContentCleanupConfig config) {
        if (referenceLines == null || referenceLines.isEmpty() || config == null) return -1L;
        String fingerprint = config.firstFormalLineByTrack.get(
                trackKey == null ? "" : trackKey);
        if (fingerprint == null) return -1L;
        for (Line line : referenceLines) {
            if (fingerprint.equals(line.fingerprint)) return line.timeMillis;
        }
        return -1L;
    }

    static List<Line> parseLines(String timedText) {
        ArrayList<Line> result = new ArrayList<>();
        if (timedText == null || timedText.isEmpty()) return result;
        String[] rawLines = timedText.split("\\r?\\n", -1);
        for (int index = 0; index < rawLines.length && result.size() < MAX_PREVIEW_LINES; index++) {
            String raw = rawLines[index];
            long time = firstTimeMillis(raw);
            String text = stripTiming(raw);
            if (time < 0L || text.isEmpty()) continue;
            result.add(new Line(index, raw, text, time));
        }
        return result;
    }

    static String previewTimedText(String timedText) {
        if (timedText == null || timedText.isEmpty()) return "";
        StringBuilder result = new StringBuilder(
                Math.min(timedText.length(), MAX_PREVIEW_CHARS));
        String[] rawLines = timedText.split("\\r?\\n", -1);
        int timedLines = 0;
        for (String rawLine : rawLines) {
            if (firstTimeMillis(rawLine) < 0L) continue;
            int required = rawLine.length() + (result.length() == 0 ? 0 : 1);
            if (timedLines >= MAX_PREVIEW_LINES
                    || result.length() + required > MAX_PREVIEW_CHARS) {
                break;
            }
            if (result.length() > 0) result.append('\n');
            result.append(rawLine);
            timedLines++;
        }
        return result.toString();
    }

    static LyricContentCleanupConfig.LearnedRule proposeLearnedRule(String text) {
        String normalized = normalizeForComparison(text);
        if (normalized.isEmpty()) return null;
        int colon = normalized.indexOf(':');
        if (colon >= 1 && colon + 1 <= LyricContentCleanupConfig.MAX_PREFIX_CHARS) {
            return new LyricContentCleanupConfig.LearnedRule(
                    LyricContentCleanupConfig.LearnedType.PREFIX,
                    normalized.substring(0, colon + 1));
        }
        // Copyright-symbol lines already have a dedicated built-in switch. A one-character
        // learned prefix would be too broad, so never propose one here.
        if (normalized.startsWith("©")) return null;
        if (normalized.length() <= 80
                && (normalized.contains("all rights reserved")
                || normalized.contains("permission")
                || normalized.contains("版权所有"))) {
            return new LyricContentCleanupConfig.LearnedRule(
                    LyricContentCleanupConfig.LearnedType.EXACT,
                    normalized);
        }
        return null;
    }

    static String reasonLabel(Reason reason) {
        if (reason == null) return "尚未识别";
        switch (reason) {
            case FIXED_PARSING: return "解析保护规则";
            case TRACK_OVERRIDE: return "本歌曲手动修正";
            case BUILTIN_COPYRIGHT: return "内置：版权与权利声明";
            case BUILTIN_PRODUCTION: return "内置：制作人员与乐器信息";
            case BUILTIN_TITLE_ARTIST: return "内置：开头歌名与歌手";
            case LEARNED_PREFIX:
            case LEARNED_EXACT: return "已学习的格式";
            default: return "尚未识别";
        }
    }

    static String normalizeForComparison(String value) {
        String normalized = LyricMetadataFilter.normalizeLine(value)
                .replace('：', ':')
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length());
        boolean whitespace = false;
        for (int index = 0; index < normalized.length();) {
            int codePoint = normalized.codePointAt(index);
            if (Character.isWhitespace(codePoint)) {
                if (!whitespace && result.length() > 0) result.append(' ');
                whitespace = true;
            } else {
                result.appendCodePoint(codePoint);
                whitespace = false;
            }
            index += Character.charCount(codePoint);
        }
        int length = result.length();
        if (length > 0 && result.charAt(length - 1) == ' ') result.setLength(length - 1);
        return result.toString();
    }

    static String fingerprint(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                    normalizeForComparison(text).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value));
            return result.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean isOpeningCandidate(Line line, int index) {
        return index < OPENING_MAX_LINES && line.timeMillis <= OPENING_MAX_TIME_MILLIS;
    }

    private static Reason builtInReason(
            Line line,
            LyricContentCleanupConfig config) {
        if (config.copyrightNoticesEnabled
                && LyricMetadataFilter.isCopyrightOrRightsLine(line.text)) {
            return Reason.BUILTIN_COPYRIGHT;
        }
        if (config.productionCreditsEnabled
                && LyricMetadataFilter.isDisplayProductionDetailLine(
                line.text,
                line.timeMillis)) {
            return Reason.BUILTIN_PRODUCTION;
        }
        if (config.titleArtistLeadEnabled
                && LockscreenIntegrationPolicy.isLikelyTitleArtistCredit(
                line.text,
                line.timeMillis)) {
            return Reason.BUILTIN_TITLE_ARTIST;
        }
        return Reason.VISIBLE;
    }

    private static boolean isLikelyArtistContinuation(Line previous, Line current) {
        if (previous == null || current == null
                || current.timeMillis > 15_000L
                || current.timeMillis - previous.timeMillis > 2_000L) {
            return false;
        }
        String value = normalizeForComparison(current.text);
        if (value.length() < 2 || value.length() > 80
                || value.indexOf(':') >= 0
                || value.indexOf(',') >= 0
                || value.indexOf('，') >= 0
                || value.endsWith(".")
                || value.endsWith("!")
                || value.endsWith("?")) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isLetter(value.charAt(index))) return true;
        }
        return false;
    }

    private static Reason learnedReason(
            String text,
            List<LyricContentCleanupConfig.LearnedRule> rules) {
        String normalized = normalizeForComparison(text);
        for (LyricContentCleanupConfig.LearnedRule rule : rules) {
            if (rule.type == LyricContentCleanupConfig.LearnedType.PREFIX
                    && normalized.startsWith(rule.value)) {
                return Reason.LEARNED_PREFIX;
            }
            if (rule.type == LyricContentCleanupConfig.LearnedType.EXACT
                    && normalized.equals(rule.value)) {
                return Reason.LEARNED_EXACT;
            }
        }
        return Reason.VISIBLE;
    }

    private static long firstTimeMillis(String raw) {
        if (raw == null) return -1L;
        Matcher matcher = TIME_TAG.matcher(raw);
        if (matcher.find()) return parseLrcTime(matcher.group(1));
        Matcher yrc = YRC_LINE_TAG.matcher(raw);
        if (yrc.find()) {
            try {
                return Long.parseLong(yrc.group(1));
            } catch (RuntimeException ignored) {
                return -1L;
            }
        }
        return -1L;
    }

    private static String stripTiming(String raw) {
        if (raw == null) return "";
        String without = TIME_TAG.matcher(raw).replaceAll("");
        without = YRC_LINE_TAG.matcher(without).replaceAll("");
        // YRC word tags use (offset,duration,flags); remove only tag-shaped groups.
        without = without.replaceAll("\\(\\d{1,10},\\d{1,10},\\d{1,4}\\)", "");
        return LyricMetadataFilter.normalizeLine(without);
    }

    private static long parseLrcTime(String value) {
        if (value == null) return -1L;
        String normalized = value.replace('.', ':');
        String[] parts = normalized.split(":");
        if (parts.length < 2 || parts.length > 3) return -1L;
        try {
            long minutes = Long.parseLong(parts[0]);
            long seconds = Long.parseLong(parts[1]);
            long millis = 0L;
            if (parts.length == 3) {
                String fraction = parts[2];
                if (fraction.length() == 1) millis = Long.parseLong(fraction) * 100L;
                else if (fraction.length() == 2) millis = Long.parseLong(fraction) * 10L;
                else millis = Long.parseLong(fraction.substring(0, Math.min(3, fraction.length())));
            }
            return (minutes * 60L + seconds) * 1_000L + millis;
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }
}
