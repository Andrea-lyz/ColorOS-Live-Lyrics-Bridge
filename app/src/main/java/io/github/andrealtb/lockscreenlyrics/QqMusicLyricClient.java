package io.github.andrealtb.lockscreenlyrics;

import android.text.TextUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class QqMusicLyricClient {
    private static final String LYRIC_URL =
            "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final Pattern CDATA_PATTERN_TEMPLATE = Pattern.compile(
            "<%s[^>]*>.*?<!\\[CDATA\\[(.*?)]]>",
            Pattern.DOTALL);
    private static final Pattern LYRIC_CONTENT_PATTERN =
            Pattern.compile("LyricContent\\s*=\\s*\"([\\s\\S]*?)\"(?=\\s*/?>)");
    private static final Pattern QRC_LINE_PATTERN =
            Pattern.compile("\\[(\\d+)\\s*,\\s*(\\d+)]");
    private static final Pattern QRC_WORD_PATTERN =
            Pattern.compile("([^()\\n\\r]*?)\\((\\d+)\\s*,\\s*(\\d+)\\)");

    private QqMusicLyricClient() {
    }

    static TimedLyricDocument download(String songId)
            throws Exception {
        if (TextUtils.isEmpty(songId)) {
            throw new FirstBatchMediaSessionAdapter.NoLyricException("Missing QQ music id");
        }

        String responseXml = postLyricsRequest(songId);
        String rawLyric = decryptQrc(extractCData(responseXml, "content"));
        String rawTranslation = decryptQrcOrEmpty(extractCData(responseXml, "contentts"));

        TimedLyricDocument document = parseQrcOrLrc(rawLyric);
        if (document.isEmpty()) {
            throw new FirstBatchMediaSessionAdapter.NoLyricException("QQ returned empty lyric");
        }
        TimedLyricDocument translations = TimedLyricDocument.fromRawLrc(rawTranslation);
        return document.withTranslationsFrom(translations, 50L);
    }

    static TimedLyricDocument downloadTranslations(String songId)
            throws Exception {
        if (TextUtils.isEmpty(songId)) {
            throw new FirstBatchMediaSessionAdapter.NoLyricException("Missing QQ music id");
        }

        String responseXml = postLyricsRequest(songId);
        String rawTranslation = decryptQrcOrEmpty(extractCData(responseXml, "contentts"));
        return TimedLyricDocument.fromRawLrc(rawTranslation);
    }

    private static String postLyricsRequest(String songId) throws Exception {
        String postData = "version=15"
                + "&miniversion=100"
                + "&lrctype=4"
                + "&musicid=" + URLEncoder.encode(songId, "UTF-8");
        HttpURLConnection connection =
                (HttpURLConnection) URI.create(LYRIC_URL).toURL().openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Referer", "https://y.qq.com/");
            connection.setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded");
            connection.getOutputStream().write(postData.getBytes(StandardCharsets.UTF_8));

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readUtf8(stream);
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("QQ lyric HTTP " + code + ": " + body);
            }
            return body;
        } finally {
            connection.disconnect();
        }
    }

    private static String extractCData(String xml, String tagName) {
        if (TextUtils.isEmpty(xml) || TextUtils.isEmpty(tagName)) {
            return "";
        }
        Pattern pattern = Pattern.compile(
                String.format(Locale.ROOT, CDATA_PATTERN_TEMPLATE.pattern(), tagName),
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    static TimedLyricDocument parseQrcOrLrc(String rawLyric) {
        if (TextUtils.isEmpty(rawLyric)) {
            return TimedLyricDocument.EMPTY;
        }

        ArrayList<TimedLyricDocument.Line> lines = new ArrayList<>();
        Matcher contentMatcher = LYRIC_CONTENT_PATTERN.matcher(rawLyric);
        boolean foundContent = false;
        while (contentMatcher.find()) {
            foundContent = true;
            parseQrcContent(decodeXmlEntities(contentMatcher.group(1)), lines);
        }
        if (!foundContent) {
            parseQrcContent(rawLyric, lines);
        }
        TimedLyricDocument qrc = new TimedLyricDocument(lines);
        if (!qrc.isEmpty()) {
            return qrc;
        }
        return TimedLyricDocument.fromRawLrc(rawLyric);
    }

    private static void parseQrcContent(
            String content,
            ArrayList<TimedLyricDocument.Line> out) {
        if (TextUtils.isEmpty(content)) {
            return;
        }
        Matcher lineMatcher = QRC_LINE_PATTERN.matcher(content);
        ArrayList<LineTag> tags = new ArrayList<>();
        while (lineMatcher.find()) {
            tags.add(new LineTag(
                    lineMatcher.start(),
                    lineMatcher.end(),
                    parseLong(lineMatcher.group(1)),
                    parseLong(lineMatcher.group(2))));
        }
        for (int i = 0; i < tags.size(); i++) {
            LineTag tag = tags.get(i);
            int bodyEnd = i + 1 < tags.size() ? tags.get(i + 1).start : content.length();
            if (tag.end >= bodyEnd) {
                continue;
            }
            String body = content.substring(tag.end, bodyEnd).trim();
            TimedLyricDocument.Line line = parseQrcLine(tag.startMillis, tag.durationMillis, body);
            if (line != null) {
                out.add(line);
            }
        }
    }

    private static TimedLyricDocument.Line parseQrcLine(
            long lineStart,
            long lineDuration,
            String body) {
        if (TextUtils.isEmpty(body)) {
            return null;
        }
        ArrayList<TimedLyricDocument.Word> words = new ArrayList<>();
        StringBuilder text = new StringBuilder(body.length());
        Matcher wordMatcher = QRC_WORD_PATTERN.matcher(body);
        while (wordMatcher.find()) {
            String wordText = LyricTextSanitizer.removeIgnorableCharacters(
                    nullToEmpty(wordMatcher.group(1)));
            if (TextUtils.isEmpty(wordText)) {
                continue;
            }
            long wordStart = normalizeQrcWordStart(
                    lineStart,
                    lineDuration,
                    parseLong(wordMatcher.group(2)));
            long wordDuration = Math.max(0L, parseLong(wordMatcher.group(3)));
            int start = text.length();
            text.append(wordText);
            words.add(new TimedLyricDocument.Word(
                    wordStart,
                    wordStart + wordDuration,
                    start,
                    text.length()));
        }

        String finalText = text.length() > 0
                ? text.toString()
                : body.replaceAll("\\(\\d+\\s*,\\s*\\d+\\)", "").trim();
        finalText = LyricTextSanitizer.removeIgnorableCharacters(finalText).trim();
        if (TextUtils.isEmpty(finalText)) {
            return null;
        }
        long lineEnd = lineDuration > 0L ? lineStart + lineDuration : inferLineEnd(lineStart, words);
        return new TimedLyricDocument.Line(
                lineStart,
                lineEnd,
                finalText,
                "",
                words);
    }

    private static long normalizeQrcWordStart(long lineStart, long lineDuration, long wordStart) {
        if (lineStart > 0L
                && wordStart >= 0L
                && wordStart <= Math.max(lineDuration + 2_000L, 2_000L)) {
            return lineStart + wordStart;
        }
        return wordStart;
    }

    private static long inferLineEnd(long lineStart, ArrayList<TimedLyricDocument.Word> words) {
        long end = lineStart + 3_000L;
        for (TimedLyricDocument.Word word : words) {
            end = Math.max(end, word.endMillis);
        }
        return end;
    }

    private static String decryptQrc(String encrypted) throws Exception {
        if (TextUtils.isEmpty(encrypted)) {
            return "";
        }
        String normalized = encrypted.trim();
        if (!isHexString(normalized)) {
            return normalized;
        }
        return QqQrcDecrypter.decrypt(normalized);
    }

    private static String decryptQrcOrEmpty(String encrypted) {
        try {
            return decryptQrc(encrypted);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String readUtf8(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read > 0) {
                out.write(buffer, 0, read);
            }
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static boolean isHexString(String value) {
        if (TextUtils.isEmpty(value) || value.length() % 2 != 0) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            boolean valid = (ch >= '0' && ch <= '9')
                    || (ch >= 'a' && ch <= 'f')
                    || (ch >= 'A' && ch <= 'F');
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private static String decodeXmlEntities(String input) {
        return nullToEmpty(input)
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&apos;", "'");
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class LineTag {
        final int start;
        final int end;
        final long startMillis;
        final long durationMillis;

        LineTag(int start, int end, long startMillis, long durationMillis) {
            this.start = start;
            this.end = end;
            this.startMillis = startMillis;
            this.durationMillis = durationMillis;
        }
    }
}
