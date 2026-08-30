package io.github.andrealtb.lockscreenlyrics;

import java.util.ArrayList;

/** Pure option builder for the main settings refresh-rate selector. */
final class LyricRefreshRateOptionsPolicy {
    private static final int[] KNOWN_RATES = {60, 90, 120};

    private LyricRefreshRateOptionsPolicy() {
    }

    static Options build(boolean has60, boolean has90, boolean has120, int preservedRate) {
        int safePreserved = LyricUiConfig.sanitizeRefreshRate(preservedRate);
        ArrayList<Integer> values = new ArrayList<>();
        ArrayList<Boolean> available = new ArrayList<>();
        values.add(0);
        available.add(true);
        for (int rate : KNOWN_RATES) {
            boolean supported = rate == 60 ? has60 : rate == 90 ? has90 : has120;
            if (supported || rate == safePreserved) {
                values.add(rate);
                available.add(supported);
            }
        }
        int[] valueArray = new int[values.size()];
        boolean[] availableArray = new boolean[available.size()];
        for (int index = 0; index < values.size(); index++) {
            valueArray[index] = values.get(index);
            availableArray[index] = available.get(index);
        }
        return new Options(valueArray, availableArray);
    }

    static final class Options {
        final int[] values;
        final boolean[] available;

        Options(int[] values, boolean[] available) {
            this.values = values;
            this.available = available;
        }
    }
}
