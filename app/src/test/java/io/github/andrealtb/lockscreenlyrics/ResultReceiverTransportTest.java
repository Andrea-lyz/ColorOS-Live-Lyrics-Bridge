package io.github.andrealtb.lockscreenlyrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Intent;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.ResultReceiver;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises real Android Parcel/Binder code with a host unable to load app callbacks. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
public final class ResultReceiverTransportTest {
    private static final ClassLoader SYSTEM_UI_CLASS_LOADER =
            new ClassLoader(ResultReceiver.class.getClassLoader()) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve)
                        throws ClassNotFoundException {
                    if (name.startsWith("io.github.andrealtb.lockscreenlyrics.")) {
                        throw new ClassNotFoundException(name);
                    }
                    return super.loadClass(name, resolve);
                }
            };

    @Test
    public void frameworkProxySurvivesHostClassLoaderAndRepliesOnOriginalHandler() {
        AtomicInteger receivedCode = new AtomicInteger(-1);
        AtomicReference<Bundle> receivedData = new AtomicReference<>();
        ResultReceiver local = new AppCallback(receivedCode, receivedData);

        ResultReceiver remote = receiveAsSystemUi(
                ResultReceiverTransport.forRemoteProcess(local));

        assertEquals(ResultReceiver.class, remote.getClass());
        Bundle reply = new Bundle();
        reply.putLong("revision", 123L);
        reply.putBoolean("applied", true);
        // Robolectric's default send shadow skips Binder and directly calls onReceiveResult.
        // Run Android's implementation to cover the actual remote proxy and Handler path.
        Shadow.directlyOn(
                remote,
                ResultReceiver.class,
                "send",
                ClassParameter.from(int.class, 7),
                ClassParameter.from(Bundle.class, reply));
        assertEquals(-1, receivedCode.get());
        ShadowLooper.idleMainLooper();
        assertEquals(7, receivedCode.get());
        assertEquals(123L, receivedData.get().getLong("revision"));
        assertEquals(true, receivedData.get().getBoolean("applied"));
    }

    @Test
    public void unwrappedAppCallbackReproducesBadParcelableInHost() {
        ResultReceiver local = new AppCallback(new AtomicInteger(), new AtomicReference<>());

        assertThrows(BadParcelableException.class, () -> receiveAsSystemUi(local));
    }

    @SuppressWarnings("deprecation")
    private static ResultReceiver receiveAsSystemUi(ResultReceiver receiver) {
        Intent request = new Intent("settings-request").putExtra("receiver", receiver);
        Parcel wire = Parcel.obtain();
        try {
            request.writeToParcel(wire, 0);
            wire.setDataPosition(0);
            Intent delivered = Intent.CREATOR.createFromParcel(wire);
            delivered.setExtrasClassLoader(SYSTEM_UI_CLASS_LOADER);
            return delivered.getParcelableExtra("receiver");
        } finally {
            wire.recycle();
        }
    }

    private static final class AppCallback extends ResultReceiver {
        private final AtomicInteger receivedCode;
        private final AtomicReference<Bundle> receivedData;

        AppCallback(AtomicInteger receivedCode, AtomicReference<Bundle> receivedData) {
            super(new Handler(Looper.getMainLooper()));
            this.receivedCode = receivedCode;
            this.receivedData = receivedData;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            receivedCode.set(resultCode);
            receivedData.set(resultData);
        }
    }
}
