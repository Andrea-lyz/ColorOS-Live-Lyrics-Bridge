package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public final class ConePlayerAdapterTest {
    @Test
    public void extractsVorbisLyricsFromSelectedAudioTrack() throws Exception {
        String lyric = "[00:01.00]Style";
        FakeTracks tracks = new FakeTracks(Collections.singletonList(
                new FakeGroup(
                        1,
                        true,
                        new boolean[]{true},
                        new FakeFormat(new FakeMetadata(
                                new FakeVorbisComment("LYRICS", lyric))))));

        assertEquals(lyric, ConePlayerAdapter.findSelectedAudioLyric(tracks));
    }

    @Test
    public void extractsId3UsltValues() throws Exception {
        String lyric = "[00:02.10]Out of the woods";

        assertEquals(
                lyric,
                ConePlayerAdapter.extractTimedLyricFromMetadataEntry(
                        new FakeTextInformationFrame("USLT", Collections.singletonList(lyric))));
    }

    @Test
    public void ignoresTimedTextFromNonLyricMetadata() throws Exception {
        assertEquals(
                "",
                ConePlayerAdapter.extractTimedLyricFromMetadataEntry(
                        new FakeTextInformationFrame(
                                "COMMENT",
                                Collections.singletonList("[00:02.10]not a lyric field"))));
    }

    @Test
    public void ignoresUnselectedAndNonAudioTracks() throws Exception {
        String lyric = "[00:03.00]Stay";
        FakeFormat format = new FakeFormat(new FakeMetadata(
                new FakeVorbisComment("LYRICS", lyric)));
        FakeTracks tracks = new FakeTracks(Arrays.asList(
                new FakeGroup(1, false, new boolean[]{true}, format),
                new FakeGroup(2, true, new boolean[]{true}, format)));

        assertEquals("", ConePlayerAdapter.findSelectedAudioLyric(tracks));
    }

    @Test
    public void rejectsConeNoLyricPlaceholder() {
        assertFalse(ConePlayerAdapter.isUsableTimedLyric("[00:00.00]暂无歌词"));
        assertFalse(ConePlayerAdapter.isUsableTimedLyric("[00:00.00]No lyrics"));
        assertTrue(ConePlayerAdapter.isUsableTimedLyric("[00:01.00]Welcome to New York"));
    }

    public static final class FakeTracks {
        private final List<FakeGroup> groups;

        FakeTracks(List<FakeGroup> groups) {
            this.groups = groups;
        }

        public List<FakeGroup> getGroups() {
            return groups;
        }
    }

    public static final class FakeGroup {
        public final int length;
        private final int type;
        private final boolean selected;
        private final boolean[] selectedTracks;
        private final FakeFormat format;

        FakeGroup(
                int type,
                boolean selected,
                boolean[] selectedTracks,
                FakeFormat format) {
            this.type = type;
            this.selected = selected;
            this.selectedTracks = selectedTracks;
            this.format = format;
            this.length = selectedTracks.length;
        }

        public int getType() {
            return type;
        }

        public boolean isSelected() {
            return selected;
        }

        public boolean isTrackSelected(int index) {
            return selectedTracks[index];
        }

        public FakeFormat getTrackFormat(int index) {
            return format;
        }
    }

    public static final class FakeFormat {
        public final FakeMetadata metadata;

        FakeFormat(FakeMetadata metadata) {
            this.metadata = metadata;
        }
    }

    public static final class FakeMetadata {
        private final Object[] entries;

        FakeMetadata(Object... entries) {
            this.entries = entries;
        }

        public int length() {
            return entries.length;
        }

        public Object get(int index) {
            return entries[index];
        }
    }

    public static final class FakeVorbisComment {
        public final String key;
        public final String value;

        FakeVorbisComment(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    public static final class FakeTextInformationFrame {
        public final String id;
        public final List<String> values;

        FakeTextInformationFrame(String id, List<String> values) {
            this.id = id;
            this.values = values;
        }
    }
}
