package io.github.andrealtb.artwork.contract;

import android.os.Bundle;

oneway interface IArtworkCallback {
    void onResult(String requestId, in Bundle result);
}
