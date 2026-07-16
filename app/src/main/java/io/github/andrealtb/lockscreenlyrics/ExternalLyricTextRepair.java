package io.github.andrealtb.lockscreenlyrics;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class ExternalLyricTextRepair {
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final String LYRIC_PROVIDER_PREFIX = "lyricprovider/";
    private static final String[] UTF8_AS_GB18030_MOJIBAKE_TOKENS = {
            "鈈",
            "鈙",
            "鈥",
            "銇",
            "銆",
            "銈",
            "銉",
            "銒",
            "銖",
            "倐",
            "仟",
            "亪",
            "亀",
            "宕",
            "剜",
            "夈",
            "夌",
            "寮",
            "屾",
            "呃",
            "呰",
            "唨",
            "姣",
            "拰",
            "檓",
            "浼",
            "瀚",
            "瀫",
            "涓",
            "涘",
            "湜",
            "潤",
            "潰",
            "澖",
            "瑼",
            "璧",
            "甯",
            "疆",
            "疯",
            "矾",
            "窘",
            "繈",
            "缈",
            "般",
            "迷",
            "鎬",
            "鍒",
            "鍜",
            "鏄",
            "鏈",
            "鏂",
            "鐍",
            "鐘",
            "鐸",
            "鐵",
            "闁",
            "闄",
            "闇",
            "閹",
            "閾",
            "�"
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
        if (value.indexOf('�') >= 0) {
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
