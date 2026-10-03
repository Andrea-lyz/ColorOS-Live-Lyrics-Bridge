package io.github.andrealtb.lockscreenlyrics.systemui.artwork;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ArtworkAssociationDiagnosticsTest {
    private final ArtworkCardIdentity card = new ArtworkCardIdentity("private-entry", "pkg", "Private Song", "Private Artist");
    private ArtworkSessionAssociation.Entry<String> entry(String title, String token) {
        return new ArtworkSessionAssociation.Entry<>("private-entry", "pkg", 0, token, title, "Private Artist", 120000, true);
    }

    @Test public void reportsAmbiguityAndMismatchWithoutExposingValues() {
        String ambiguous = ArtworkAssociationDiagnostics.describe(card, List.of(entry("Private Song", "secret-token"), entry("Private Song", "secret-token")));
        assertTrue(ambiguous.contains("reason=entry_ambiguous"));
        assertTrue(ambiguous.contains("candidateCount=2"));
        for (String privateValue : List.of("private-entry", "Private Song", "Private Artist", "secret-token")) {
            assertFalse(ambiguous.contains(privateValue));
        }
        assertTrue(ArtworkAssociationDiagnostics.describe(card, List.of(entry("Other Song", "secret-token")))
                .contains("reason=source_title_mismatch"));
    }

    @Test public void missingTokenIsDistinctFromEmptySourceOrIncompleteCard() {
        assertTrue(ArtworkAssociationDiagnostics.describe(card, List.of(entry("Private Song", null))).contains("reason=token_missing"));
        assertTrue(ArtworkAssociationDiagnostics.describe(card, List.of()).contains("reason=no_exact_entry"));
        assertTrue(ArtworkAssociationDiagnostics.describe(null, List.of()).contains("reason=card_incomplete"));
    }
}
