package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class QqMusicInternalLyricExtractorTest {
    @Test
    public void extractsOffsetWordTiming() {
        Lyric lyric = new Lyric(new Line(
                "你好",
                10_000L,
                2_000L,
                Arrays.asList(
                        new Word(0L, 500L, "你"),
                        new Word(500L, 600L, "好"))));

        TimedLyricDocument document = QqMusicInternalLyricExtractor.extract(lyric);

        assertTrue(document.hasWordTiming());
        assertEquals(1, document.lineCount());
        assertEquals("[00:10.000]<00:10.000>你<00:10.500>好<00:12.000>\n",
                document.toEnhancedLrc());
    }

    @Test
    public void keepsAbsoluteWordTiming() {
        Lyric lyric = new Lyric(new Line(
                "AB",
                10_000L,
                2_000L,
                Arrays.asList(
                        new Word(10_000L, 400L, "A"),
                        new Word(10_500L, 400L, "B"))));

        TimedLyricDocument document = QqMusicInternalLyricExtractor.extract(lyric);

        assertEquals("[00:10.000]<00:10.000>A<00:10.500>B<00:12.000>\n",
                document.toEnhancedLrc());
    }

    @Test
    public void preservesFullChineseLineTextWhenWordFragmentsArePartial() {
        Lyric lyric = new Lyric(new Line(
                "\u8bf4\u4e3a\u4ec0\u4e48\u6ca1\u5173\u7cfb",
                3_000L,
                2_000L,
                Arrays.asList(
                        new Word(0L, 300L, "\u8bf4"),
                        new Word(400L, 600L, "\u4e3a\u4ec0\u4e48"))));

        TimedLyricDocument document = QqMusicInternalLyricExtractor.extract(lyric);

        assertEquals(1, document.lineCount());
        assertEquals("[00:03.000]<00:03.000>\u8bf4<00:03.400>"
                        + "\u4e3a\u4ec0\u4e48\u6ca1\u5173\u7cfb<00:05.000>\n",
                document.toEnhancedLrc());
    }

    @Test
    public void filtersQqCreditLinesBeforeDisplayLyrics() {
        Lyric lyric = new Lyric(Arrays.asList(
                new Line(
                        "The Road Not Taken - HOYO-MiX/Aimer",
                        0L,
                        500L,
                        Collections.singletonList(new Word(0L, 500L, "The"))),
                new Line(
                        "HOYO-MiX/Aimer",
                        420L,
                        500L,
                        Collections.singletonList(new Word(420L, 500L, "HOYO-MiX"))),
                new Line(
                        "\u4e50\u961f Orchestra\uff1aBudapest Scoring Orchestra / \u9f99\u4e4b\u827a\u4ea4\u54cd\u4e50\u56e2",
                        487L,
                        500L,
                        Collections.singletonList(new Word(487L, 500L, "\u4e50\u961f"))),
                new Line(
                        "\u624b\u98ce\u7434 Accordion\uff1a\u674e\u695a\u7136 Churan Li",
                        720L,
                        500L,
                        Collections.singletonList(new Word(720L, 500L, "\u624b\u98ce\u7434"))),
                new Line(
                        "Cello: Ping Zhang",
                        1_020L,
                        500L,
                        Collections.singletonList(new Word(1_020L, 500L, "Cello"))),
                new Line(
                        "Produced by HOYO-MiX",
                        2_000L,
                        500L,
                        Collections.singletonList(new Word(2_000L, 500L, "Produced"))),
                new Line(
                        "I feel you in the last blow of wind.",
                        6_940L,
                        3_000L,
                        Arrays.asList(
                                new Word(6_940L, 500L, "I", 0, 1),
                                new Word(7_500L, 500L, "feel", 2, 6)))),
                Collections.emptyList());

        TimedLyricDocument document = QqMusicInternalLyricExtractor.extract(lyric);

        assertEquals(1, document.lineCount());
        assertEquals("[00:06.940]<00:06.940>I <00:07.500>"
                        + "feel you in the last blow of wind.<00:09.940>\n",
                document.toEnhancedLrc());
    }

    @Test
    public void mergesTimedTranslationLineListFromSiblingField() {
        Lyric lyric = new Lyric(
                new Line(
                        "Hello",
                        1_000L,
                        2_000L,
                        Collections.singletonList(new Word(0L, 1_000L, "Hello"))),
                new Line(
                        "\u4f60\u597d",
                        1_020L,
                        2_000L,
                        Collections.emptyList()));

        TimedLyricDocument document = QqMusicInternalLyricExtractor.extract(lyric);

        assertEquals("[00:01.000]<00:01.000>Hello<00:03.000>\n"
                        + "[00:01.000]\u4f60\u597d\n",
                document.toEnhancedLrc());
    }

    @Test
    public void rejectsSparseSiblingLinesAsInternalTranslations() {
        Lyric lyric = new Lyric(
                Arrays.asList(
                        simpleLine("Line 0", 0L),
                        simpleLine("Line 1", 1_000L),
                        simpleLine("Line 2", 2_000L),
                        simpleLine("Line 3", 3_000L),
                        simpleLine("Line 4", 4_000L),
                        simpleLine("Line 5", 5_000L),
                        simpleLine("Line 6", 6_000L),
                        simpleLine("Line 7", 7_000L)),
                Arrays.asList(
                        simpleLine("Produced by Somebody", 0L),
                        simpleLine("Wrong sparse line", 3_000L)));

        TimedLyricDocument document = QqMusicInternalLyricExtractor.extract(lyric);

        assertEquals(8, document.lineCount());
        assertEquals(0, document.translationCount());
    }

    @Test
    public void restoresQqInternalUtf8TextMisdecodedAsGb18030() {
        Lyric lyric = new Lyric(new Line(
                utf8AsGb18030("\u5e0c\u671b\u6709\u7fbd\u6bdb\u548c\u7fc5\u8180"),
                1_000L,
                1_500L,
                Arrays.asList(
                        new Word(0L, 500L, utf8AsGb18030("\u5e0c\u671b"), 0, 2),
                        new Word(500L, 700L, utf8AsGb18030("\u6709\u7fbd\u6bdb"), 2, 5),
                        new Word(1_200L, 300L, utf8AsGb18030("\u548c\u7fc5\u8180"), 5, 8))));

        TimedLyricDocument document = QqMusicInternalLyricExtractor.extract(lyric);

        assertEquals("[00:01.000]<00:01.000>\u5e0c\u671b<00:01.500>"
                        + "\u6709\u7fbd\u6bdb<00:02.200>\u548c\u7fc5\u8180<00:02.500>\n",
                document.toEnhancedLrc());
    }

    @Test
    public void doesNotRewriteNormalChineseTextThatLooksLegitimate() {
        Lyric lyric = new Lyric(new Line(
                "\u6d93\u6d41\u5165\u6d77",
                1_000L,
                1_000L,
                Collections.singletonList(new Word(0L, 1_000L, "\u6d93\u6d41\u5165\u6d77"))));

        TimedLyricDocument document = QqMusicInternalLyricExtractor.extract(lyric);

        assertEquals("[00:01.000]<00:01.000>\u6d93\u6d41\u5165\u6d77<00:02.000>\n",
                document.toEnhancedLrc());
    }

    @Test
    public void mergesDecryptedTranslationButIgnoresRomajiCandidate() {
        TimedLyricDocument base = new TimedLyricDocument(Collections.singletonList(
                new TimedLyricDocument.Line(
                        43_688L,
                        46_584L,
                        "\u60aa\u970a\u9000\u6563 ICBM",
                        "",
                        Collections.emptyList())));
        TimedLyricDocument romaji = new TimedLyricDocument(Collections.singletonList(
                new TimedLyricDocument.Line(
                        43_688L,
                        46_584L,
                        "a ku ryo u ta i sa n ICBM",
                        "",
                        Collections.emptyList())));
        TimedLyricDocument translation = new TimedLyricDocument(Collections.singletonList(
                new TimedLyricDocument.Line(
                        43_700L,
                        46_584L,
                        "\u6076\u7075\u9000\u6563 ICBM",
                        "",
                        Collections.emptyList())));

        assertEquals(0, base.withUsableTranslationsFrom(romaji, 1_500L).translationCount());
        TimedLyricDocument merged = base.withUsableTranslationsFrom(translation, 1_500L);

        assertEquals(1, merged.translationCount());
        assertEquals("[00:43.688]\u60aa\u970a\u9000\u6563 ICBM\n"
                        + "[00:43.688]\u6076\u7075\u9000\u6563 ICBM\n",
                merged.toEnhancedLrc());
    }

    @Test
    public void supplementsMissingWordTimingOnlyForSameTextLine() {
        TimedLyricDocument base = new TimedLyricDocument(Arrays.asList(
                new TimedLyricDocument.Line(
                        1_000L,
                        3_000L,
                        "Hello",
                        "",
                        Collections.emptyList()),
                new TimedLyricDocument.Line(
                        4_000L,
                        6_000L,
                        "World",
                        "",
                        Collections.emptyList())));
        TimedLyricDocument supplement = new TimedLyricDocument(Arrays.asList(
                new TimedLyricDocument.Line(
                        1_020L,
                        3_000L,
                        "Hello",
                        "",
                        Arrays.asList(
                                new TimedLyricDocument.Word(1_020L, 2_000L, 0, 2),
                                new TimedLyricDocument.Word(2_000L, 3_000L, 2, 5))),
                new TimedLyricDocument.Line(
                        4_020L,
                        6_000L,
                        "Other",
                        "",
                        Arrays.asList(
                                new TimedLyricDocument.Word(4_020L, 5_000L, 0, 2),
                                new TimedLyricDocument.Word(5_000L, 6_000L, 2, 5)))));

        TimedLyricDocument merged = base.withWordTimingFrom(supplement, 100L);

        assertEquals(1, merged.wordTimedLineCount());
        assertEquals("[00:01.000]<00:01.020>He<00:02.000>llo<00:03.000>\n"
                        + "[00:04.000]World\n",
                merged.toEnhancedLrc());
    }

    @Test
    public void readsSongInfoObfuscatedAccessors() {
        SongInfo songInfo = new SongInfo("2054", "17さいのうた。", "Ai Higuchi");

        QqMusicInternalLyricExtractor.SongMetadata metadata =
                QqMusicInternalLyricExtractor.readSongMetadata(songInfo);

        assertEquals("2054", metadata.songId);
        assertEquals("17さいのうた。", metadata.title);
        assertEquals("Ai Higuchi", metadata.artist);
        assertEquals(TrackIdentity.buildKey("17さいのうた。", "Ai Higuchi"),
                metadata.trackHintKey());
    }

    private static String utf8AsGb18030(String value) {
        return Charset.forName("GB18030")
                .decode(StandardCharsets.UTF_8.encode(value))
                .toString();
    }

    private static Line simpleLine(String text, long start) {
        return new Line(
                text,
                start,
                800L,
                Collections.singletonList(new Word(start, 800L, text)));
    }

    static final class Lyric {
        final Iterable<Line> e;
        final Iterable<Line> f;

        Lyric(Line line) {
            this.e = Collections.singletonList(line);
            this.f = Collections.emptyList();
        }

        Lyric(Iterable<Line> lines, Iterable<Line> translations) {
            this.e = lines;
            this.f = translations;
        }

        Lyric(Line line, Line translation) {
            this.e = Collections.singletonList(line);
            this.f = Collections.singletonList(translation);
        }
    }

    static final class Line {
        final String a;
        final long b;
        final long c;
        final Iterable<Word> g;

        Line(String text, long start, long duration, Iterable<Word> words) {
            this.a = text;
            this.b = start;
            this.c = duration;
            this.g = words;
        }
    }

    static final class Word {
        final long a;
        final long b;
        final int c;
        final int d;
        final String e;

        Word(long start, long duration, String text) {
            this(start, duration, text, -1, -1);
        }

        Word(long start, long duration, String text, int textStart, int textEnd) {
            this.a = start;
            this.b = duration;
            this.c = textStart;
            this.d = textEnd;
            this.e = text;
        }
    }

    static final class SongInfo {
        private final String id;
        private final String title;
        private final String artist;

        SongInfo(String id, String title, String artist) {
            this.id = id;
            this.title = title;
            this.artist = artist;
        }

        String v2() {
            return id;
        }

        String X2() {
            return title;
        }

        String C3() {
            return artist;
        }
    }
}
