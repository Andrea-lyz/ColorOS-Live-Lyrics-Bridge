package io.github.andrealtb.artwork.am;

import java.util.List;
import java.util.TreeMap;

/** User-authorized artwork equivalence, never a claim that the audio recordings are identical. */
final class AmEdition {
    record Info(String rating, String releaseDay, int trackCount) {
        static final Info UNKNOWN = new Info("", "", 0);
        Info {
            rating = "clean".equals(rating) ? "cleaned" : rating == null ? "" : rating;
            releaseDay = releaseDay != null && releaseDay.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}.*") ? releaseDay.substring(0, 10) : "";
        }
    }
    record Candidate(String id, String artist, String album, Info info) {}

    static String explicitCleanChoice(List<Candidate> candidates) {
        TreeMap<String, Candidate> distinct = new TreeMap<>();
        for (Candidate candidate : candidates) {
            Candidate previous = distinct.putIfAbsent(candidate.id(), candidate);
            if (previous != null && !previous.equals(candidate)) return null;
        }
        if (distinct.size() != 2) return null;
        Candidate a = distinct.firstEntry().getValue(), b = distinct.lastEntry().getValue();
        Info x = a.info(), y = b.info();
        if (x == null || y == null || AmIdentity.normalize(a.artist()).isEmpty() || AmIdentity.normalize(a.album()).isEmpty()
                || !AmIdentity.normalize(a.artist()).equals(AmIdentity.normalize(b.artist()))
                || !AmIdentity.normalize(a.album()).equals(AmIdentity.normalize(b.album()))
                || x.releaseDay().isEmpty() || !x.releaseDay().equals(y.releaseDay())
                || x.trackCount() <= 0 || x.trackCount() != y.trackCount()) return null;
        if ("explicit".equals(x.rating()) && "cleaned".equals(y.rating())) return a.id();
        if ("cleaned".equals(x.rating()) && "explicit".equals(y.rating())) return b.id();
        return null;
    }
}
