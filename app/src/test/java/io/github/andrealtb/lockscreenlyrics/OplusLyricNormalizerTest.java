package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OplusLyricNormalizerTest {
    @Test
    public void sameTimestampTranslationKeepsOnlyPrimaryLine() {
        String lrc = "[00:01.20]Put your lips close to mine\n"
                + "[00:01.20]请靠近我 轻吻我的双唇";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:01.200]Put your lips close to mine\n"
                + "[00:09.200]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "", normalized);
    }

    @Test
    public void repeatedEnglishPhraseRemainsOfficialPrimaryLine() {
        String lrc = "[00:02.67]We are, we are, we are\n"
                + "[00:02.67]我们只是\n"
                + "[00:05.10]We are not beautiful\n"
                + "[00:05.10]想拥有平凡快乐的人";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]We are, we are, we are\n"
                + "[00:05.100]We are not beautiful\n"
                + "[00:13.100]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "", normalized);
    }

    @Test
    public void japaneseMainLineWinsOverSameTimestampRomaji() {
        String lrc = "[00:30.436]<00:30.436>こ<00:30.676>ん<00:30.916>な"
                + "<00:31.413>私<00:32.526>の<00:33.422>未<00:33.470>熟"
                + "<00:34.508>な<00:34.570>う<00:35.033>た<00:35.625>を<00:36.073>\n"
                + "[00:30.436]<00:30.436>感谢你愿意聆听<00:36.470>\n"
                + "[00:30.436]<00:30.436>ko <00:30.675>n <00:30.915><00:30.916>na "
                + "<00:31.412>wa <00:31.940>ta <00:31.997>shi <00:32.525>no "
                + "<00:33.421>mi <00:33.470>ju <00:33.723><00:33.964>ku "
                + "<00:34.507>na <00:34.569>u <00:35.032>ta <00:35.624>wo <00:36.073>";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]こんな私の未熟なうたを\n"
                + "[00:38.436]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "", normalized);
    }

    @Test
    public void japaneseMainLineWinsWhenRomajiLaneIsMissingForOneTimestamp() {
        String lrc = "[00:00.000]作词: ナユタン星人\n"
                + "[00:00.283]作曲: ナユタン星人\n"
                + "[00:00.566]编曲: ナユタン星人\n"
                + "[00:00.850]あの一等星のさんざめく光で\n"
                + "[00:00.850]a no i tto u se i no sa n za me ku hi ka ri de\n"
                + "[00:00.850]在那一等星的喧嚣光芒之下\n"
                + "[00:07.230]我が太陽系の鼓動に合わせて\n"
                + "[00:07.230]让我们来伴着 太阳系的脉动";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]作词: ナユタン星人\n"
                + "[00:00.283]作曲: ナユタン星人\n"
                + "[00:00.566]编曲: ナユタン星人\n"
                + "[00:00.850]あの一等星のさんざめく光で\n"
                + "[00:07.230]我が太陽系の鼓動に合わせて\n"
                + "[00:15.230]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "", normalized);
    }

    @Test
    public void delayedFirstLineUsesPreRollInsteadOfAddingASecondItem() {
        String lrc = "[00:02.00]Hello";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]Hello\n[00:10.000]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "", normalized);
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
                + "[00:17.000]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "", normalized);
    }

    @Test
    public void inlineWordTimingTagsDoNotBecomeOfficialText() {
        String lrc = "[00:01.00]<00:01.20>Hello <00:01.70>world";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:01.000]Hello world\n[00:09.000]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "", normalized);
    }

    @Test
    public void zeroWidthSpacerDoesNotBecomeOfficialItem() {
        String lrc = "[00:00.00]Before\n"
                + "[00:38.13]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "\n"
                + "[00:38.15]And all of the foes and all of the friends\n"
                + "[00:38.15]所有的对手 "
                + "所有的朋友" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]Before\n"
                + "[00:38.150]And all of the foes and all of the friends\n"
                + "[00:46.150]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "", normalized);
    }

    @Test
    public void embeddedBracketedTimestampStartsANewOfficialLine() {
        String lrc = "[00:12.16]I walk a lonely road\n"
                + "[00:12.16]我走在孤独的小道上"
                + "[00:14.27]The only one that I have ever known\n"
                + "[00:14.27]这是我唯一认识的路";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]I walk a lonely road\n"
                + "[00:14.270]The only one that I have ever known\n"
                + "[00:22.270]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "", normalized);
    }

    @Test
    public void bracketedWordTimingRemainsOneOfficialLyricLine() {
        String lrc = "[00:05.575]You [00:05.799]say \"[00:06.007]I "
                + "[00:06.215]don't [00:06.473]understand\"[00:07.495]\n"
                + "[00:05.575]你说我不懂"
                + "[00:14.583]We [00:14.823]thought [00:15.159]a "
                + "[00:15.447]cure[00:15.767]\n"
                + "[00:14.583]我们原以为会有转机";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]You say \"I don't understand\"\n"
                + "[00:14.583]We thought a cure\n"
                + "[00:22.583]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "", normalized);
    }

    @Test
    public void chineseTitleAndProductionCreditsRemainAvailableForConfigurableCleanup() {
        String lrc = "[00:00.000]大[00:00.141]田[00:00.283]后"
                + "[00:00.425]生[00:00.567]仔 [00:00.850]- "
                + "[00:01.134]林[00:01.276]启[00:01.418]得[00:01.560]\n"
                + "[00:01.560]词[00:01.874]：[00:02.188]林"
                + "[00:02.502]启[00:02.816]得[00:03.130]\n"
                + "[00:07.820]鼓[00:08.212]：[00:08.604]胖"
                + "[00:08.996]小[00:09.390]\n"
                + "[00:10.950]和[00:11.146]音[00:11.342]编"
                + "[00:11.538]写[00:11.734]：[00:11.930]曾"
                + "[00:12.126]恒[00:12.322]乐[00:12.520]\n"
                + "[00:18.780]监[00:19.040]棚[00:19.300]："
                + "[00:19.560]林[00:19.820]启[00:20.080]得[00:20.340]\n"
                + "[00:21.000]我[00:21.250]出[00:21.500]生"
                + "[00:21.750]的[00:22.000]地[00:22.250]方[00:22.600]";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]大田后生仔 - 林启得\n"
                + "[00:01.560]词：林启得\n"
                + "[00:07.820]鼓：胖小\n"
                + "[00:10.950]和音编写：曾恒乐\n"
                + "[00:18.780]监棚：林启得\n"
                + "[00:21.000]我出生的地方\n"
                + "[00:29.000]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "", normalized);
    }

    @Test
    public void lyricTranslationProviderCreditIsRemoved() {
        String lrc = "[00:00.000]I did something bad\n"
                + "[00:00.000]我做了一件坏事\n"
                + "[00:04.000]以下歌词翻译由 Salt Player 提供\n"
                + "[00:08.000]Then why's it feel so good?\n"
                + "[00:08.000]那为何感觉如此美好";

        String normalized = OplusLyricNormalizer.normalizeForOfficialList(lrc);

        assertEquals("[00:00.000]I did something bad\n"
                + "[00:08.000]Then why's it feel so good?\n"
                + "[00:16.000]" + LyricTextSanitizer.ZERO_WIDTH_SPACE + "", normalized);
    }

}
