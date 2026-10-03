package io.github.andrealtb.artwork.contract;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import io.github.andrealtb.artwork.contract.IArtworkCallback;

interface IArtworkProvider {
    Bundle getCapabilities();
    oneway void resolve(String requestId, in Bundle query, IArtworkCallback callback);
    oneway void cancel(String requestId);
    ParcelFileDescriptor openAsset(String requestId, String assetId);
    oneway void releaseAsset(String requestId, String assetId);
}
