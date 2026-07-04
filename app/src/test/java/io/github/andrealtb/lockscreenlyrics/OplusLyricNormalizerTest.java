package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OplusLyricNormalizerTest {
    @Test
    public void sameTimestampTranslationKeepsOnlyPrimaryLine() {
        String lrc = "[00:01.20]Put your lips close to mine\n"
                + "[00:01.20]\u8bf7\u9760\u8fd1\u6211 \u8f7b\u543b\u6211\u7684\u53cc\u5507";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:01.200]Put your lips close to mine\n"
                + "[00:09.200]\u200B", normalized);
    }

    @Test
    public void repeatedEnglishPhraseRemainsOfficialPrimaryLine() {
        String lrc = "[00:02.67]We are, we are, we are\n"
                + "[00:02.67]\u6211\u4eec\u53ea\u662f\n"
                + "[00:05.10]We are not beautiful\n"
                + "[00:05.10]\u60f3\u62e5\u6709\u5e73\u51e1\u5feb\u4e50\u7684\u4eba";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]We are, we are, we are\n"
                + "[00:05.100]We are not beautiful\n"
                + "[00:13.100]\u200B", normalized);
    }

    @Test
    public void japaneseMainLineWinsOverSameTimestampRomaji() {
        String lrc = "[00:30.436]<00:30.436>\u3053<00:30.676>\u3093<00:30.916>\u306a"
                + "<00:31.413>\u79c1<00:32.526>\u306e<00:33.422>\u672a<00:33.470>\u719f"
                + "<00:34.508>\u306a<00:34.570>\u3046<00:35.033>\u305f<00:35.625>\u3092<00:36.073>\n"
                + "[00:30.436]<00:30.436>\u611f\u8c22\u4f60\u613f\u610f\u8046\u542c<00:36.470>\n"
                + "[00:30.436]<00:30.436>ko <00:30.675>n <00:30.915><00:30.916>na "
                + "<00:31.412>wa <00:31.940>ta <00:31.997>shi <00:32.525>no "
                + "<00:33.421>mi <00:33.470>ju <00:33.723><00:33.964>ku "
                + "<00:34.507>na <00:34.569>u <00:35.032>ta <00:35.624>wo <00:36.073>";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]\u3053\u3093\u306a\u79c1\u306e\u672a\u719f\u306a\u3046\u305f\u3092\n"
                + "[00:38.436]\u200B", normalized);
    }

    @Test
    public void japaneseMainLineWinsWhenRomajiLaneIsMissingForOneTimestamp() {
        String lrc = "[00:00.000]\u4f5c\u8bcd: \u30ca\u30e6\u30bf\u30f3\u661f\u4eba\n"
                + "[00:00.283]\u4f5c\u66f2: \u30ca\u30e6\u30bf\u30f3\u661f\u4eba\n"
                + "[00:00.566]\u7f16\u66f2: \u30ca\u30e6\u30bf\u30f3\u661f\u4eba\n"
                + "[00:00.850]\u3042\u306e\u4e00\u7b49\u661f\u306e\u3055\u3093\u3056\u3081\u304f\u5149\u3067\n"
                + "[00:00.850]a no i tto u se i no sa n za me ku hi ka ri de\n"
                + "[00:00.850]\u5728\u90a3\u4e00\u7b49\u661f\u7684\u55a7\u56a3\u5149\u8292\u4e4b\u4e0b\n"
                + "[00:07.230]\u6211\u304c\u592a\u967d\u7cfb\u306e\u9f13\u52d5\u306b\u5408\u308f\u305b\u3066\n"
                + "[00:07.230]\u8ba9\u6211\u4eec\u6765\u4f34\u7740 \u592a\u9633\u7cfb\u7684\u8109\u52a8";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.850]\u3042\u306e\u4e00\u7b49\u661f\u306e\u3055\u3093\u3056\u3081\u304f\u5149\u3067\n"
                + "[00:07.230]\u6211\u304c\u592a\u967d\u7cfb\u306e\u9f13\u52d5\u306b\u5408\u308f\u305b\u3066\n"
                + "[00:15.230]\u200B", normalized);
    }

    @Test
    public void delayedFirstLineUsesPreRollInsteadOfAddingASecondItem() {
        String lrc = "[00:02.00]Hello";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]Hello\n[00:10.000]\u200B", normalized);
    }

    @Test
    public void repeatedPrimaryTextKeepsOccurrenceOrder() {
        String lrc = "[00:01.00]Stay\n"
                + "[00:05.00]Run\n"
                + "[00:09.00]Stay";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:01.000]Stay\n"
                + "[00:05.000]Run\n"
                + "[00:09.000]Stay\n"
                + "[00:17.000]\u200B", normalized);
    }

    @Test
    public void inlineWordTimingTagsDoNotBecomeOfficialText() {
        String lrc = "[00:01.00]<00:01.20>Hello <00:01.70>world";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:01.000]Hello world\n[00:09.000]\u200B", normalized);
    }

    @Test
    public void zeroWidthSpacerDoesNotBecomeOfficialItem() {
        String lrc = "[00:00.00]Before\n"
                + "[00:38.13]\u200B\n"
                + "[00:38.15]And all of the foes and all of the friends\n"
                + "[00:38.15]\u6240\u6709\u7684\u5bf9\u624b "
                + "\u6240\u6709\u7684\u670b\u53cb\u200B";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]Before\n"
                + "[00:38.150]And all of the foes and all of the friends\n"
                + "[00:46.150]\u200B", normalized);
    }

    @Test
    public void embeddedBracketedTimestampStartsANewOfficialLine() {
        String lrc = "[00:12.16]I walk a lonely road\n"
                + "[00:12.16]\u6211\u8d70\u5728\u5b64\u72ec\u7684\u5c0f\u9053\u4e0a"
                + "[00:14.27]The only one that I have ever known\n"
                + "[00:14.27]\u8fd9\u662f\u6211\u552f\u4e00\u8ba4\u8bc6\u7684\u8def";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]I walk a lonely road\n"
                + "[00:14.270]The only one that I have ever known\n"
                + "[00:22.270]\u200B", normalized);
    }

    @Test
    public void bracketedWordTimingRemainsOneOfficialLyricLine() {
        String lrc = "[00:05.575]You [00:05.799]say \"[00:06.007]I "
                + "[00:06.215]don't [00:06.473]understand\"[00:07.495]\n"
                + "[00:05.575]\u4f60\u8bf4\u6211\u4e0d\u61c2"
                + "[00:14.583]We [00:14.823]thought [00:15.159]a "
                + "[00:15.447]cure[00:15.767]\n"
                + "[00:14.583]\u6211\u4eec\u539f\u4ee5\u4e3a\u4f1a\u6709\u8f6c\u673a";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]You say \"I don't understand\"\n"
                + "[00:14.583]We thought a cure\n"
                + "[00:22.583]\u200B", normalized);
    }

    @Test
    public void chineseTitleAndProductionCreditsDoNotBlockWordTimedLyrics() {
        String lrc = "[00:00.000]\u5927[00:00.141]\u7530[00:00.283]\u540e"
                + "[00:00.425]\u751f[00:00.567]\u4ed4 [00:00.850]- "
                + "[00:01.134]\u6797[00:01.276]\u542f[00:01.418]\u5f97[00:01.560]\n"
                + "[00:01.560]\u8bcd[00:01.874]\uff1a[00:02.188]\u6797"
                + "[00:02.502]\u542f[00:02.816]\u5f97[00:03.130]\n"
                + "[00:07.820]\u9f13[00:08.212]\uff1a[00:08.604]\u80d6"
                + "[00:08.996]\u5c0f[00:09.390]\n"
                + "[00:10.950]\u548c[00:11.146]\u97f3[00:11.342]\u7f16"
                + "[00:11.538]\u5199[00:11.734]\uff1a[00:11.930]\u66fe"
                + "[00:12.126]\u6052[00:12.322]\u4e50[00:12.520]\n"
                + "[00:18.780]\u76d1[00:19.040]\u68da[00:19.300]\uff1a"
                + "[00:19.560]\u6797[00:19.820]\u542f[00:20.080]\u5f97[00:20.340]\n"
                + "[00:21.000]\u6211[00:21.250]\u51fa[00:21.500]\u751f"
                + "[00:21.750]\u7684[00:22.000]\u5730[00:22.250]\u65b9[00:22.600]";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]\u6211\u51fa\u751f\u7684\u5730\u65b9\n"
                + "[00:29.000]\u200B", normalized);
    }

    @Test
    public void lyricTranslationProviderCreditIsRemoved() {
        String lrc = "[00:00.000]I did something bad\n"
                + "[00:00.000]\u6211\u505a\u4e86\u4e00\u4ef6\u574f\u4e8b\n"
                + "[00:04.000]\u4ee5\u4e0b\u6b4c\u8bcd\u7ffb\u8bd1\u7531 Salt Player \u63d0\u4f9b\n"
                + "[00:08.000]Then why's it feel so good?\n"
                + "[00:08.000]\u90a3\u4e3a\u4f55\u611f\u89c9\u5982\u6b64\u7f8e\u597d";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]I did something bad\n"
                + "[00:08.000]Then why's it feel so good?\n"
                + "[00:16.000]\u200B", normalized);
    }

}
