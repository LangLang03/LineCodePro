package cn.lineai.platform;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Foreground keep-alive service owned by the platform module.
 *
 * <p>The service merges two independent keep-alive requests — a manual one from settings toggles
 * and an automatic one tied to model generation — into a single notification, wake lock and silent
 * audio track. Request flags are additive: whichever request asks for a resource keeps it alive
 * until that request is withdrawn, so overlapping requests cannot steal each other's resources.
 * Every mutation goes through {@code onStartCommand} actions; the static helpers are the only
 * supported way for other modules to drive the service, keeping intent construction out of feature
 * code.</p>
 */
public final class KeepAliveService extends Service {
    /** Starts (or reconfigures) the manual keep-alive request. */
    public static final String ACTION_START = "cn.lineai.action.START_KEEP_ALIVE";
    /** Withdraws the manual keep-alive request. */
    public static final String ACTION_STOP = "cn.lineai.action.STOP_KEEP_ALIVE";
    /** Replaces the notification status text. */
    public static final String ACTION_UPDATE_STATUS = "cn.lineai.action.UPDATE_STATUS";
    /** Starts the generation-scoped keep-alive request. */
    public static final String ACTION_START_GENERATION = "cn.lineai.action.START_GENERATION_KEEP_ALIVE";
    /** Withdraws the generation-scoped keep-alive request. */
    public static final String ACTION_STOP_GENERATION = "cn.lineai.action.STOP_GENERATION_KEEP_ALIVE";

    /** Boolean extra enabling the partial wake lock. */
    public static final String EXTRA_WAKE_LOCK = "wake_lock";
    /** Boolean extra enabling the foreground notification. */
    public static final String EXTRA_FOREGROUND = "foreground";
    /** Boolean extra enabling the looping silent audio track. */
    public static final String EXTRA_FAKE_AUDIO = "fake_audio";
    /** String extra carrying the notification status line. */
    public static final String EXTRA_STATUS_TEXT = "status_text";

    private static final String CHANNEL_ID = "linecode_keep_alive";
    private static final int NOTIFICATION_ID = 1001;
    private static final int SILENT_AUDIO_SAMPLE_RATE = 8000;
    private static final int SILENT_AUDIO_SECONDS = 2;

    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;
    private AudioTrack silentAudioTrack;
    private boolean isForeground = false;
    private String currentStatus;
    private boolean manualWakeLock;
    private boolean manualForeground;
    private boolean manualFakeAudio;
    private boolean generationFakeAudio;
    private boolean generationActive;

    /** Creates the low-importance notification channel used for the whole service lifetime. */
    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    /**
     * Applies one keep-alive action and keeps the service alive only while a request is active.
     *
     * @param intent command carrying the action and its extras; a null intent (system restart
     *     delivery) is ignored
     * @param flags standard start flags, unused
     * @param startId standard start id, unused
     * @return {@code START_STICKY} for state-changing actions worth restarting, otherwise
     *     {@code START_NOT_STICKY}
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if (ACTION_STOP.equals(action)) {
            manualWakeLock = false;
            manualForeground = false;
            manualFakeAudio = false;
            applyKeepAliveState();
            if (!hasActiveKeepAlive()) {
                stopSelf();
            }
            return START_NOT_STICKY;
        }

        if (ACTION_STOP_GENERATION.equals(action)) {
            generationActive = false;
            generationFakeAudio = false;
            applyKeepAliveState();
            if (!hasActiveKeepAlive()) {
                stopSelf();
            }
            return START_NOT_STICKY;
        }

        if (ACTION_START_GENERATION.equals(action)) {
            generationActive = true;
            generationFakeAudio = intent.getBooleanExtra(EXTRA_FAKE_AUDIO, false);
            String status = intent.getStringExtra(EXTRA_STATUS_TEXT);
            if (status != null && status.length() > 0) {
                currentStatus = status;
            }
            applyKeepAliveState();
            return START_STICKY;
        }

        if (ACTION_UPDATE_STATUS.equals(action)) {
            String status = intent.getStringExtra(EXTRA_STATUS_TEXT);
            if (status != null && status.length() > 0) {
                currentStatus = status;
                updateNotification();
            }
            return START_STICKY;
        }

        if (ACTION_START.equals(action)) {
            manualWakeLock = intent.getBooleanExtra(EXTRA_WAKE_LOCK, false);
            manualForeground = intent.getBooleanExtra(EXTRA_FOREGROUND, false);
            manualFakeAudio = intent.getBooleanExtra(EXTRA_FAKE_AUDIO, false);
            String status = intent.getStringExtra(EXTRA_STATUS_TEXT);
            if (status != null && status.length() > 0) {
                currentStatus = status;
            }
            applyKeepAliveState();
            return START_STICKY;
        }

        return START_NOT_STICKY;
    }

    /** Not bindable; all interaction goes through start intents. */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Releases every keep-alive resource before the service is destroyed. */
    @Override
    public void onDestroy() {
        stopKeepAlive();
        super.onDestroy();
    }

    /** Builds the launch intent for the fixed main activity class. */
    private Intent launchIntent() {
        Intent launch = new Intent();
        launch.setClassName(getPackageName(), "cn.lineai.app.MainActivity");
        return launch;
    }

    /** Creates the low-importance channel on API 26 and above; older systems need no channel. */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_keep_alive_title),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.keep_alive_notification_title));
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Reconciles the actual resources with the merged request flags. Foreground is implied when
     * fake audio runs, because the silent track alone would not justify a background service on
     * modern Android.
     */
    private void applyKeepAliveState() {
        boolean wakeLockEnabled = manualWakeLock || generationActive;
        boolean fakeAudioEnabled = manualFakeAudio || (generationActive && generationFakeAudio);
        boolean foregroundEnabled = manualForeground || generationActive || fakeAudioEnabled;

        if (wakeLockEnabled) {
            acquireWakeLock();
        } else {
            releaseWakeLock();
        }

        if (fakeAudioEnabled) {
            startSilentAudio();
        } else {
            stopSilentAudio();
        }

        if (foregroundEnabled) {
            if (!isForeground) {
                startForeground(NOTIFICATION_ID, buildNotification());
                isForeground = true;
            } else {
                updateNotification();
            }
        } else if (isForeground) {
            stopForeground(true);
            isForeground = false;
        }
    }

    /** Clears all request flags and releases wake lock, audio and foreground state. */
    private void stopKeepAlive() {
        manualWakeLock = false;
        manualForeground = false;
        manualFakeAudio = false;
        generationFakeAudio = false;
        generationActive = false;
        releaseWakeLock();
        stopSilentAudio();

        if (isForeground) {
            stopForeground(true);
            isForeground = false;
        }
    }

    /** Acquires the tagged partial wake lock once; repeated calls are no-ops. */
    private void acquireWakeLock() {
        if (wakeLock != null) {
            return;
        }
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                    "LineCode:EncodingWakeLock"
            );
            wakeLock.acquire();
        }
    }

    /** Releases the wake lock when held and drops the reference; safe to call repeatedly. */
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    /**
     * Starts a looping two-second silent PCM track that convinces the system audio focus of media
     * playback is active. Creation failures are swallowed and leave the field null so later
     * reconciliations simply retry.
     */
    private void startSilentAudio() {
        if (silentAudioTrack != null) {
            return;
        }
        int sampleCount = SILENT_AUDIO_SAMPLE_RATE * SILENT_AUDIO_SECONDS;
        byte[] silence = new byte[sampleCount * 2];
        AudioTrack track = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setSampleRate(SILENT_AUDIO_SAMPLE_RATE)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .setBufferSizeInBytes(silence.length)
                        .build();
            } else {
                track = new AudioTrack(
                        android.media.AudioManager.STREAM_MUSIC,
                        SILENT_AUDIO_SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        silence.length,
                        AudioTrack.MODE_STATIC
                );
            }
            int written = track.write(silence, 0, silence.length);
            if (written <= 0) {
                track.release();
                return;
            }
            track.setLoopPoints(0, sampleCount, -1);
            track.play();
            silentAudioTrack = track;
        } catch (RuntimeException e) {
            if (track != null) {
                track.release();
            }
            silentAudioTrack = null;
        }
    }

    /** Pauses, flushes and releases the silent track; safe to call when none is running. */
    private void stopSilentAudio() {
        AudioTrack track = silentAudioTrack;
        silentAudioTrack = null;
        if (track == null) {
            return;
        }
        try {
            track.pause();
            track.flush();
        } catch (IllegalStateException error) {
            android.util.Log.w("LineCodeKeepAlive", "silent audio stop failed", error);
        }
        track.release();
    }

    /** Builds the ongoing low-priority notification with the current status text and launch intent. */
    private Notification buildNotification() {
        String status = currentStatus == null || currentStatus.length() == 0
                ? getString(R.string.notification_keep_alive_text)
                : currentStatus;
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_keepalive_notification)
                    .setContentTitle(getString(R.string.notification_keep_alive_title))
                    .setContentText(status)
                    .setContentIntent(contentIntent)
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setTicker(getString(R.string.notification_keep_alive_ticker))
                    .setPriority(Notification.PRIORITY_LOW)
                    .build();
        } else {
            return new Notification.Builder(this)
                    .setSmallIcon(R.drawable.ic_keepalive_notification)
                    .setContentTitle(getString(R.string.notification_keep_alive_title))
                    .setContentText(status)
                    .setContentIntent(contentIntent)
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setTicker(getString(R.string.notification_keep_alive_ticker))
                    .build();
        }
    }

    /** Reposts the notification when in foreground; respects the user's notification setting. */
    private void updateNotification() {
        if (isForeground && notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    /** Reports whether any request still needs a keep-alive resource. */
    private boolean hasActiveKeepAlive() {
        return manualWakeLock || manualForeground || manualFakeAudio || generationActive || generationFakeAudio;
    }

    /**
     * Starts or reconfigures the manual keep-alive request.
     *
     * @param context context used to start the service
     * @param wakeLock true to hold a partial wake lock
     * @param foreground true to run as a foreground service with a notification
     * @param fakeAudio true to loop silent audio; implies foreground on modern Android
     */
    public static void start(Context context, boolean wakeLock, boolean foreground, boolean fakeAudio) {
        if (!wakeLock && !foreground && !fakeAudio) {
            stop(context);
            return;
        }
        Intent intent = new Intent(context, KeepAliveService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_WAKE_LOCK, wakeLock);
        intent.putExtra(EXTRA_FOREGROUND, foreground);
        intent.putExtra(EXTRA_FAKE_AUDIO, fakeAudio);
        intent.putExtra(EXTRA_STATUS_TEXT, context.getString(R.string.notification_keep_alive_text));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && (foreground || fakeAudio)) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Withdraws the manual keep-alive request; the service stops itself when no other request
     * remains.
     *
     * @param context context used to start the service
     */
    public static void stop(Context context) {
        Intent intent = new Intent(context, KeepAliveService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    /**
     * Replaces the notification status text while any keep-alive is running.
     *
     * @param context context used to start the service
     * @param status new status line; empty or null is ignored by the service
     */
    public static void updateStatus(Context context, String status) {
        Intent intent = new Intent(context, KeepAliveService.class);
        intent.setAction(ACTION_UPDATE_STATUS);
        intent.putExtra(EXTRA_STATUS_TEXT, status);
        context.startService(intent);
    }

    /**
     * Starts the generation-scoped keep-alive, reading the fake-audio preference so generation
     * survives doze on devices where the user opted in.
     *
     * @param context context used to start the service and read preferences
     */
    public static void startGeneration(Context context) {
        boolean fakeAudio = context.getSharedPreferences("linecode_keep_alive", Context.MODE_PRIVATE).getBoolean("fake_audio_enabled", false);
        Intent intent = new Intent(context, KeepAliveService.class);
        intent.setAction(ACTION_START_GENERATION);
        intent.putExtra(EXTRA_STATUS_TEXT, context.getString(R.string.notification_keep_alive_text));
        intent.putExtra(EXTRA_FAKE_AUDIO, fakeAudio);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Withdraws the generation-scoped keep-alive; the service stops itself when no manual request
     * remains.
     *
     * @param context context used to start the service
     */
    public static void stopGeneration(Context context) {
        Intent intent = new Intent(context, KeepAliveService.class);
        intent.setAction(ACTION_STOP_GENERATION);
        context.startService(intent);
    }
}
