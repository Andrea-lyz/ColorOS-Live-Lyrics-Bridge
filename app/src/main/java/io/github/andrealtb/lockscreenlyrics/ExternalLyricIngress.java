package io.github.andrealtb.lockscreenlyrics;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;

import io.github.andrealtb.lockscreenlyrics.protocol.ExternalLyricProtocol;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the SystemUI-facing external-lyric process boundary: the static-whitelist direct
 * receiver, snapshot copying, bounded parsing work, generation ordering, and rejection logging.
 */
final class ExternalLyricIngress<T> {
    private static final int PARSE_QUEUE_CAPACITY = 12;
    private static final int MAX_FIELD_CHARS = 1_500_000;
    private static final int MAX_TOTAL_CHARS = 3_000_000;
    private static final int MAX_METADATA_FIELD_CHARS = 16_384;
    private static final long FAILURE_LOG_INTERVAL_MS = 10_000L;

    interface CaptureHandler<T> {
        T parse(CaptureSnapshot snapshot) throws Throwable;

        void apply(T capture);
    }

    interface Reporter {
        void warn(String message);

        void error(String message, Throwable throwable);
    }

    interface SenderPolicy {
        ExternalLyricSenderPolicy.Decision authorize(
                ExternalLyricProtocol.Transport transport,
                CaptureSnapshot snapshot);
    }

    private final Handler mainHandler;
    private final CaptureHandler<T> captureHandler;
    private final Reporter reporter;
    private final SenderPolicy senderPolicy;
    private final Object receiverRegistrationLock = new Object();
    private final AtomicInteger captureGeneration = new AtomicInteger();

    private volatile boolean directReceiverRegistered;
    private BroadcastReceiver directReceiver;
    private volatile ThreadPoolExecutor parseExecutor;
    private volatile int lastAppliedCaptureGeneration;
    private volatile long lastFailureLogAt;

    ExternalLyricIngress(
            Handler mainHandler,
            CaptureHandler<T> captureHandler,
            Reporter reporter,
            SenderPolicy senderPolicy) {
        this.mainHandler = mainHandler;
        this.captureHandler = captureHandler;
        this.reporter = reporter;
        this.senderPolicy = senderPolicy;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    void ensureRegistered(Context context) {
        if (context == null || directReceiverRegistered) {
            return;
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        synchronized (receiverRegistrationLock) {
            if (!directReceiverRegistered) {
                directReceiver = registerReceiver(
                        appContext,
                        ExternalLyricProtocol.Transport.DIRECT,
                        ExternalLyricProtocol.ACTION_DIRECT_LYRIC_CAPTURED,
                        "static-whitelist direct");
                directReceiverRegistered = directReceiver != null;
            }
        }
    }

    void execute(Runnable runnable) {
        if (runnable != null) {
            parseExecutor().execute(runnable);
        }
    }

    void reportFailure(String message, Throwable throwable) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastFailureLogAt < FAILURE_LOG_INTERVAL_MS) {
            return;
        }
        lastFailureLogAt = now;
        if (throwable == null) {
            reporter.warn(message);
        } else {
            reporter.error(message, throwable);
        }
    }

    private BroadcastReceiver registerReceiver(
            Context context,
            ExternalLyricProtocol.Transport transport,
            String action,
            String transportName) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                receive(transport, intent);
            }
        };
        try {
            IntentFilter filter = new IntentFilter(action);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                        receiver,
                        filter,
                        null,
                        mainHandler,
                        Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(
                        receiver,
                        filter,
                        null,
                        mainHandler);
            }
            return receiver;
        } catch (Throwable throwable) {
            reportFailure(
                    "Failed to register " + transportName + " external lyric receiver",
                    throwable);
            return null;
        }
    }

    private void receive(
            ExternalLyricProtocol.Transport transport,
            Intent intent) {
        if (intent == null) {
            return;
        }
        String action = intent.getAction();
        if (ExternalLyricProtocol.transportForAction(action) != transport) {
            return;
        }

        CaptureSnapshot snapshot;
        try {
            snapshot = CaptureSnapshot.fromIntent(intent, captureGeneration.incrementAndGet());
        } catch (Throwable throwable) {
            reportFailure("Rejected malformed direct external lyric broadcast", throwable);
            return;
        }
        if (!snapshot.hasAcceptablePayloadSize()) {
            reportFailure("Rejected oversized direct external lyric broadcast", null);
            return;
        }
        String compatibilityError = ExternalLyricProtocol.compatibilityError(
                snapshot.action,
                snapshot.protocolVersion);
        if (compatibilityError != null) {
            reportFailure("Rejected direct external lyric broadcast: " + compatibilityError, null);
            return;
        }

        ExternalLyricSenderPolicy.Decision decision = senderPolicy.authorize(
                transport,
                snapshot);
        if (!decision.accepted) {
            reportFailure("Rejected direct external lyric broadcast: " + decision.rejection, null);
            return;
        }

        execute(() -> {
            try {
                T parsed = captureHandler.parse(snapshot);
                mainHandler.post(() -> {
                    if (snapshot.generation <= lastAppliedCaptureGeneration) {
                        return;
                    }
                    lastAppliedCaptureGeneration = snapshot.generation;
                    captureHandler.apply(parsed);
                });
            } catch (Throwable throwable) {
                reportFailure("Failed to parse direct external lyric broadcast", throwable);
            }
        });
    }

    private ThreadPoolExecutor parseExecutor() {
        ThreadPoolExecutor executor = parseExecutor;
        if (executor != null) {
            return executor;
        }
        synchronized (this) {
            executor = parseExecutor;
            if (executor == null) {
                executor = new ThreadPoolExecutor(
                        1,
                        1,
                        30L,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(PARSE_QUEUE_CAPACITY),
                        runnable -> {
                            Thread thread = new Thread(
                                    runnable,
                                    "LockscreenLyrics-ProviderParse");
                            thread.setDaemon(true);
                            thread.setPriority(Thread.NORM_PRIORITY - 1);
                            return thread;
                        },
                        new ThreadPoolExecutor.DiscardOldestPolicy());
                executor.allowCoreThreadTimeOut(true);
                parseExecutor = executor;
            }
        }
        return executor;
    }

    static final class CaptureSnapshot {
        final int generation;
        final String action;
        final int protocolVersion;
        final String source;
        final String playerPackage;
        final String senderPackage;
        final String senderKind;
        final String capabilities;
        final String matchPolicy;
        final String identityConfidence;
        final String eventType;
        final boolean hasTrackGeneration;
        final long trackGeneration;
        final String requestId;
        final String mediaId;
        final String mediaUri;
        final String trackKey;
        final String songName;
        final String title;
        final String artist;
        final long duration;
        final String lyric;
        final String rawLyric;
        final String translationLyric;
        final long capturedAt;
        final String lyricInfo;
        final boolean hasPlaybackState;
        final int playbackState;
        final long playbackPosition;
        final float playbackSpeed;
        final long playbackLastPositionUpdateTime;

        private CaptureSnapshot(Intent intent, int generation) {
            this.generation = generation;
            action = nullToEmpty(intent.getAction());
            protocolVersion = intent.getIntExtra(
                    LyricInfoContract.EXTRA_EXTERNAL_PROTOCOL_VERSION,
                    0);
            source = nullToEmpty(intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_SOURCE));
            playerPackage = nullToEmpty(
                    intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_PLAYER_PACKAGE));
            senderPackage = nullToEmpty(
                    intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_SENDER_PACKAGE));
            senderKind = nullToEmpty(
                    intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_SENDER_KIND));
            capabilities = nullToEmpty(
                    intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_CAPABILITIES));
            matchPolicy = nullToEmpty(
                    intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_MATCH_POLICY));
            identityConfidence = nullToEmpty(
                    intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_IDENTITY_CONFIDENCE));
            eventType = nullToEmpty(
                    intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_EVENT_TYPE));
            hasTrackGeneration = intent.hasExtra(LyricInfoContract.EXTRA_EXTERNAL_TRACK_GENERATION);
            trackGeneration = intent.getLongExtra(
                    LyricInfoContract.EXTRA_EXTERNAL_TRACK_GENERATION,
                    0L);
            requestId = nullToEmpty(intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_REQUEST_ID));
            mediaId = nullToEmpty(intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_MEDIA_ID));
            mediaUri = nullToEmpty(intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_MEDIA_URI));
            trackKey = nullToEmpty(intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_TRACK_KEY));
            songName = nullToEmpty(intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_SONG_NAME));
            title = nullToEmpty(intent.getStringExtra("title"));
            artist = nullToEmpty(intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_ARTIST));
            duration = intent.getLongExtra(LyricInfoContract.EXTRA_EXTERNAL_DURATION, 0L);
            lyric = nullToEmpty(intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_LYRIC));
            rawLyric = nullToEmpty(intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_RAW_LYRIC));
            translationLyric = nullToEmpty(
                    intent.getStringExtra(LyricInfoContract.EXTRA_EXTERNAL_TRANSLATION_LYRIC));
            capturedAt = intent.getLongExtra(
                    LyricInfoContract.EXTRA_EXTERNAL_CAPTURED_AT,
                    System.currentTimeMillis());
            lyricInfo = nullToEmpty(intent.getStringExtra(LyricInfoContract.METADATA_KEY));
            hasPlaybackState = intent.hasExtra(LyricInfoContract.EXTRA_EXTERNAL_PLAYBACK_STATE);
            playbackState = intent.getIntExtra(
                    LyricInfoContract.EXTRA_EXTERNAL_PLAYBACK_STATE,
                    -1);
            playbackPosition = intent.getLongExtra(
                    LyricInfoContract.EXTRA_EXTERNAL_PLAYBACK_POSITION,
                    -1L);
            playbackSpeed = intent.getFloatExtra(
                    LyricInfoContract.EXTRA_EXTERNAL_PLAYBACK_SPEED,
                    1f);
            playbackLastPositionUpdateTime = intent.getLongExtra(
                    LyricInfoContract.EXTRA_EXTERNAL_PLAYBACK_LAST_POSITION_UPDATE_TIME,
                    -1L);
        }

        static CaptureSnapshot fromIntent(Intent intent, int generation) {
            return intent == null ? null : new CaptureSnapshot(intent, generation);
        }

        boolean hasAcceptablePayloadSize() {
            int largestMetadataField = source.length();
            largestMetadataField = Math.max(largestMetadataField, playerPackage.length());
            largestMetadataField = Math.max(largestMetadataField, senderPackage.length());
            largestMetadataField = Math.max(largestMetadataField, senderKind.length());
            largestMetadataField = Math.max(largestMetadataField, capabilities.length());
            largestMetadataField = Math.max(largestMetadataField, matchPolicy.length());
            largestMetadataField = Math.max(largestMetadataField, identityConfidence.length());
            largestMetadataField = Math.max(largestMetadataField, eventType.length());
            largestMetadataField = Math.max(largestMetadataField, requestId.length());
            largestMetadataField = Math.max(largestMetadataField, mediaId.length());
            largestMetadataField = Math.max(largestMetadataField, mediaUri.length());
            largestMetadataField = Math.max(largestMetadataField, trackKey.length());
            largestMetadataField = Math.max(largestMetadataField, songName.length());
            largestMetadataField = Math.max(largestMetadataField, title.length());
            largestMetadataField = Math.max(largestMetadataField, artist.length());
            return LockscreenIntegrationPolicy.isExternalLyricPayloadSizeAcceptable(
                    lyricInfo.length(),
                    lyric.length(),
                    rawLyric.length(),
                    translationLyric.length(),
                    largestMetadataField,
                    MAX_FIELD_CHARS,
                    MAX_TOTAL_CHARS,
                    MAX_METADATA_FIELD_CHARS);
        }

        private static String nullToEmpty(String value) {
            return value == null ? "" : value;
        }
    }
}
