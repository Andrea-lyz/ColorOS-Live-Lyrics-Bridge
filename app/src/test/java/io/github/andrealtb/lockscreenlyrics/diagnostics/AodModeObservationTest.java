package io.github.andrealtb.lockscreenlyrics.diagnostics;

import android.os.Bundle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk = 34, manifest = Config.NONE)
public class AodModeObservationTest {
    private Bundle status(AodModeObservation observation) {
        Bundle b = new Bundle();
        observation.putStatus(b);
        return b;
    }

    @Test public void neverSwitchedIsUnknownNotOff() {
        AodModeObservation observation = new AodModeObservation();
        Bundle b = status(observation);
        assertEquals("unknown", b.getString(AodModeObservation.CURRENT));
        assertEquals(0, b.getInt(AodModeObservation.TRANSITIONS));
        assertEquals(-1L, b.getLong(AodModeObservation.CHANGED_AT));
    }

    @Test public void onlyRealTransitionsAreCounted() {
        AodModeObservation observation = new AodModeObservation();
        observation.changed(true, 100L);
        observation.changed(true, 200L);
        observation.changed(false, 300L);
        observation.changed(false, 400L);
        observation.changed(true, 500L);
        Bundle b = status(observation);
        assertEquals(3, b.getInt(AodModeObservation.TRANSITIONS));
        assertEquals(500L, b.getLong(AodModeObservation.CHANGED_AT));
        assertEquals("true", b.getString(AodModeObservation.CURRENT));
    }
}
