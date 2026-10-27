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

public final class KeepAliveService extends Service {
    public static final String ACTION_START = "cn.lineai.action.START_KEEP_ALIVE";
    public static final String ACTION_STOP = "cn.lineai.action.STOP_KEEP_ALIVE";
    public static final String ACTION_UPDATE_STATUS = "cn.lineai.action.UPDATE_STATUS";
    public static final String ACTION_START_GENERATION = "cn.lineai.action.START_GENERATION_KEEP_ALIVE";
    public static final String ACTION_STOP_GENERATION = "cn.lineai.action.STOP_GENERATION_KEEP_ALIVE";

    public static final String EXTRA_WAKE_LOCK = "wake_lock";
    public static final String EXTRA_FOREGROUND = "foreground";
    public static final String EXTRA_FAKE_AUDIO = "fake_audio";
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

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

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

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopKeepAlive();
        super.onDestroy();
    }

    private Intent launchIntent() {
        Intent launch = new Intent();
        launch.setClassName(getPackageName(), "cn.lineai.app.MainActivity");
        return launch;
    }

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

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

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

    private void updateNotification() {
        if (isForeground && notificationManager.areNotificationsEnabled()) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification());
        }
    }

    private boolean hasActiveKeepAlive() {
        return manualWakeLock || manualForeground || manualFakeAudio || generationActive || generationFakeAudio;
    }

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

    public static void stop(Context context) {
        Intent intent = new Intent(context, KeepAliveService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    public static void updateStatus(Context context, String status) {
        Intent intent = new Intent(context, KeepAliveService.class);
        intent.setAction(ACTION_UPDATE_STATUS);
        intent.putExtra(EXTRA_STATUS_TEXT, status);
        context.startService(intent);
    }

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

    public static void stopGeneration(Context context) {
        Intent intent = new Intent(context, KeepAliveService.class);
        intent.setAction(ACTION_STOP_GENERATION);
        context.startService(intent);
    }
}
