package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import java.util.List;

/** Pure, value-free explanation of the existing exact matcher. Does not select a session. */
public final class ArtworkAssociationDiagnostics {
    private ArtworkAssociationDiagnostics() {}

    public static String describe(ArtworkCardIdentity card, List<? extends ArtworkSessionAssociation.Entry<?>> entries) {
        int total = entries == null ? 0 : entries.size();
        boolean complete = card != null && card.complete();
        int active = 0;
        int samePackage = 0;
        int candidates = 0;
        ArtworkSessionAssociation.Entry<?> selected = null;
        if (entries != null) for (var entry : entries) {
            if (!entry.active()) continue;
            active++;
            if (card == null || !card.packageName().equals(entry.packageName())) continue;
            samePackage++;
            if (!card.playerId().equals(entry.playerId())) continue;
            candidates++;
            selected = entry;
        }
        String reason = !complete ? "card_incomplete" : total > 32 ? "source_budget"
                : candidates == 0 ? "no_exact_entry" : candidates > 1 ? "entry_ambiguous"
                : selected.token() == null ? "token_missing" : selected.userId() < 0 ? "user_invalid"
                : !card.title().equals(ArtworkCardIdentity.clean(selected.title())) ? "source_title_mismatch"
                : !card.artist().equals(ArtworkCardIdentity.clean(selected.artist())) ? "source_artist_mismatch" : "exact";
        return "reason=" + reason + " sourceCount=" + total + " activeCount=" + active
                + " samePackageCount=" + samePackage + " candidateCount=" + candidates
                + " cardPresent=" + (card != null) + " cardComplete=" + complete
                + " tokenRef=" + (candidates == 1 ? ArtworkTrace.tokenRef(selected.token()) : 0);
    }
}
