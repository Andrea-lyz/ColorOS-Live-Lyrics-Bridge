package io.github.andrealtb.lockscreenlyrics;

import android.os.Parcel;
import android.os.ResultReceiver;

/** Keeps app callback classes out of parcels delivered to SystemUI. */
final class ResultReceiverTransport {
    private ResultReceiverTransport() {}

    static ResultReceiver forRemoteProcess(ResultReceiver localReceiver) {
        if (localReceiver == null) {
            return null;
        }
        Parcel parcel = Parcel.obtain();
        try {
            // Write only ResultReceiver's Binder, without writeParcelable's app class name.
            // The framework proxy still calls the original receiver on its original Handler.
            localReceiver.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return ResultReceiver.CREATOR.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }
}
