package io.github.andrealtb.lockscreenlyrics;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class ExternalLyricTextRepair {
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final String LYRIC_PROVIDER_PREFIX = "lyricprovider/";
    private static final String[] UTF8_AS_GB18030_MOJIBAKE_TOKENS = {
            "\u9208",
            "\u9219",
            "\u9225",
            "\u9287",
            "\u9286",
            "\u9288",
            "\u9289",
            "\u9292",
            "\u9296",
            "\u5010",
            "\u4EDF",
            "\u4EAA",
            "\u4E80",
            "\u5B95",
            "\u525C",
            "\u5908",
            "\u590C",
            "\u5BEE",
            "\u5C7E",
            "\u5443",
            "\u5470",
            "\u5528",
            "\u59E3",
            "\u62F0",
            "\u6A93",
            "\u6D7C",
            "\u701A",
            "\u702B",
            "\u6D93",
            "\u6D98",
            "\u6E5C",
            "\u6F64",
            "\u6F70",
            "\u6F96",
            "\u747C",
            "\u74A7",
            "\u752F",
            "\u7586",
            "\u75AF",
            "\u77FE",
            "\u7A98",
            "\u7E48",
            "\u7F08",
            "\u822C",
            "\u8FF7",
            "\u93AC",
            "\u9352",
            "\u935C",
            "\u93C4",
            "\u93C8",
            "\u93C2",
            "\u940D",
            "\u9418",
            "\u9438",
            "\u9435",
            "\u95C1",
            "\u95C4",
            "\u95C7",
            "\u95B9",
            "\u95BE",
            "\uFFFD"
    };

    private ExternalLyricTextRepair() {
    }

    static String restoreProviderMojibake(String source, String value) {
        if (isEmpty(value)
                || isEmpty(source)
                || !source.startsWith(LYRIC_PROVIDER_PREFIX)) {
            return nullToEmpty(value);
        }
        return restoreUtf8MisdecodedAsGb18030(value);
    }

    private static String restoreUtf8MisdecodedAsGb18030(String value) {
        int originalMojibakeScore = mojibakeScore(value);
        if (originalMojibakeScore < 2) {
            return value;
        }

        String restored;
        try {
            restored = new String(value.getBytes(GB18030), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return value;
        }
        restored = LyricTextSanitizer.removeIgnorableCharacters(restored).trim();
        if (isEmpty(restored) || restored.equals(value)) {
            return value;
        }

        int restoredMojibakeScore = mojibakeScore(restored);
        int originalQuality = textQualityScore(value, originalMojibakeScore);
        int restoredQuality = textQualityScore(restored, restoredMojibakeScore);
        if (restoredMojibakeScore < originalMojibakeScore) {
            return restored;
        }
        if (restoredMojibakeScore == originalMojibakeScore - 1
                && restoredQuality >= originalQuality) {
            return restored;
        }
        return value;
    }

    private static int mojibakeScore(String value) {
        if (isEmpty(value)) {
            return 0;
        }
        int score = 0;
        for (String token : UTF8_AS_GB18030_MOJIBAKE_TOKENS) {
            int index = value.indexOf(token);
            while (index >= 0) {
                score++;
                index = value.indexOf(token, index + token.length());
            }
        }
        if (value.indexOf('\uFFFD') >= 0) {
            score += 2;
        }
        return score;
    }

    private static int textQualityScore(String value, int mojibakeScore) {
        int score = -mojibakeScore * 4;
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                score += 3;
            } else if (Character.isLetterOrDigit(codePoint)) {
                score += 1;
            } else if (codePoint == 0xFFFD) {
                score -= 6;
            } else if (Character.isISOControl(codePoint)) {
                score -= 8;
            }
            index += Character.charCount(codePoint);
        }
        return score;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
