package io.github.andrealtb.lockscreenlyrics.render;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public final class OfficialLyricDatasetTest {
    @Test public void sharedCreditsDoNotIdentifyTheWholeSong() {
        assertNotEquals(dataset("Maroon", "When the morning came"),
                dataset("Anti-Hero", "I have this thing"));
        assertNotEquals(dataset("Maroon", "When the morning came"), source(
                "[00:01.00]Lyrics by: Jack\n[00:02.00]Composed by: Jack\n[00:03.00]Produced by: Jack"));
    }

    @Test public void nativeRowFieldsAreResolvedByTypeRatherThanObfuscatedName() {
        assertEquals(source("[00:01.23]hello"), OfficialLyricDataset.fromNativeRows(
                Collections.singletonList(new NativeRow("hello", 1_230L))));
    }

    @Test public void lrcPrecisionMatchesTheVerifiedNativeParser() {
        assertEquals(source("[00:01.239]hello"), OfficialLyricDataset.fromNativeRows(
                Collections.singletonList(new NativeRow("hello", 1_230L))));
        assertEquals(source("[00:01.1]hello"), source("[00:01.01]hello"));
    }

    @Test public void repeatedLeadingTimestampsAreExpandedAndStablySorted() {
        assertEquals(source("[00:02.00]two\n[00:01.00][00:03.00]one"),
                OfficialLyricDataset.fromNativeRows(Arrays.asList(new NativeRow("one", 1_000L),
                        new NativeRow("two", 2_000L), new NativeRow("one", 3_000L))));
    }

    @Test public void sameTimestampTranslationOrderIsPartOfIdentity() {
        assertEquals(source("[00:01.00]hello\n[00:01.00]你好"),
                OfficialLyricDataset.fromNativeRows(Arrays.asList(
                        new NativeRow("hello", 1_000L), new NativeRow("你好", 1_000L))));
        assertNotEquals(source("[00:01.00]hello\n[00:01.00]你好"),
                source("[00:01.00]你好\n[00:01.00]hello"));
    }

    @Test public void jsonSegmentsAreConcatenatedWithoutChangingTheirTiming() {
        assertTrue(source("{\"t\":1234,\"c\":[{\"tx\":\"hello \"},{\"tx\":\"world\"}]}").matchesNative(
                OfficialLyricDataset.fromNativeRows(Collections.singletonList(
                        new NativeRow("hello world", 1_234L)))));
    }

    @Test public void blankMetadataAndMalformedLinesDoNotInventNativeItems() {
        assertEquals(0, source("[ar:artist]\n[00:00.000] \nnot timed\n{broken}\n[xx:yy.zz]text").size());
        assertEquals(source("[00:00.00]first"), source("[ar:artist][00:00.00]first"));
    }

    @Test public void unicodeWhitespaceAndCrLfMatchNativeTrim() {
        assertEquals(source("[00:01.00]hello\n[00:02.00]world"),
                source("\u00a0[00:01.00] hello\u3000\r\n [00:02.00]world \r"));
    }

    @Test public void timestampsArePartOfDatasetIdentity() {
        assertNotEquals(source("[00:01.00]same"), source("[00:02.00]same"));
    }

    @Test public void emptyNativeDataAndUnsupportedReflectionAreDifferentStates() {
        assertEquals(source(""), OfficialLyricDataset.fromNativeRows(Collections.emptyList()));
        assertNull(OfficialLyricDataset.fromNativeRows(null));
        assertNull(OfficialLyricDataset.fromNativeRows(Collections.singletonList(new Object())));
        assertNull(OfficialLyricDataset.fromNativeRows(Collections.singletonList(new AmbiguousRow())));
    }

    @Test public void snapshotDoesNotRetainTheMutableAdapterList() {
        java.util.ArrayList<NativeRow> rows = new java.util.ArrayList<>();
        rows.add(new NativeRow("first", 0L));
        OfficialLyricDataset captured = OfficialLyricDataset.fromNativeRows(rows);
        rows.clear();
        assertEquals(source("[00:00.00]first"), captured);
    }

    @Test public void directAdapterReadsOnlyTheRealRowListMatchingItsItemCount() {
        DirectAdapter adapter = new DirectAdapter();
        adapter.data.add(new NativeRow("first", 0L));
        assertEquals(source("[00:00.00]first"), OfficialLyricDataset.fromAdapter(adapter));
        adapter.data.clear();
        assertEquals(source(""), OfficialLyricDataset.fromAdapter(adapter));
    }

    @Test public void asyncAdapterExposesCommittedListRatherThanPendingSubmission() {
        AsyncAdapter adapter = new AsyncAdapter();
        adapter.current = Collections.singletonList(new NativeRow("old", 0L));
        adapter.pending = Collections.singletonList(new NativeRow("new", 0L));
        assertEquals(source("[00:00.00]old"), OfficialLyricDataset.fromAdapter(adapter));
        adapter.current = adapter.pending;
        assertEquals(source("[00:00.00]new"), OfficialLyricDataset.fromAdapter(adapter));
    }

    @Test public void conflictingNativeListsDoNotGuessWhichOneIsTheDataset() {
        assertNull(OfficialLyricDataset.fromAdapter(new AmbiguousAdapter()));
    }

    @Test public void nativeFormatterIsAppliedToExpectedDisplayTextOnly() {
        OfficialLyricDataset actual = OfficialLyricDataset.fromNativeRows(Collections.singletonList(
                new NativeRow("HELLO", 0L))).withExpectedTextNormalizer(text -> text.replace("hello", "HELLO"));
        assertTrue(source("[00:00.00]hello").matchesNative(actual));
        assertFalse(source("[00:00.00]unrelated").matchesNative(actual));
        assertFalse(source("[00:00.00]hello").matchesNative(
                OfficialLyricDataset.fromNativeRows(Collections.singletonList(new NativeRow("HELLO", 0L)))));
    }

    @Test public void nativeJsonNormalizationRetainsPerSegmentBoundaries() {
        OfficialLyricDataset actual = OfficialLyricDataset.fromNativeRows(Collections.singletonList(
                new NativeRow("[ab][cd]", 0L))).withExpectedTextNormalizer(text -> "[" + text + "]");
        assertTrue(source("{\"t\":0,\"c\":[{\"tx\":\"ab\"},{\"tx\":\"cd\"}]}").matchesNative(actual));
        assertFalse(source("{\"t\":0,\"c\":[{\"tx\":\"abcd\"}]}").matchesNative(actual));
    }

    static OfficialLyricDataset source(String value) {
        return OfficialLyricDataset.fromDisplaySource(value);
    }

    static OfficialLyricDataset dataset(String title, String verse) {
        return source("[00:00.00]" + title + "\n[00:01.00]Lyrics by: Jack\n"
                + "[00:02.00]Composed by: Jack\n[00:03.00]Produced by: Jack\n[00:04.00]" + verse);
    }

    private static final class NativeRow {
        private final String b;
        private final long a;
        NativeRow(String text, long time) { b = text; a = time; }
    }

    private static final class AmbiguousRow {
        String first = "one";
        String second = "two";
        long time;
    }

    public static final class DirectAdapter {
        private final java.util.ArrayList<NativeRow> data = new java.util.ArrayList<>();
        private final java.util.ArrayList<String> callbacks = new java.util.ArrayList<>();
        public int getItemCount() { return data.size(); }
    }

    public static final class AsyncAdapter {
        java.util.List<NativeRow> current;
        java.util.List<NativeRow> pending;
        public java.util.List<NativeRow> getCurrentList() { return current; }
    }

    public static final class AmbiguousAdapter {
        private final java.util.List<NativeRow> first = Collections.singletonList(new NativeRow("one", 0L));
        private final java.util.List<NativeRow> second = Collections.singletonList(new NativeRow("two", 0L));
        public int getItemCount() { return 1; }
    }
}
