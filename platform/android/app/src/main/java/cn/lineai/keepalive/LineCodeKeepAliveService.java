package cn.lineai.keepalive;

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

import cn.lineai.MainActivity;
import cn.lineai.R;

/** Actuates an effective state computed by the portable C++ policy. */
public final class LineCodeKeepAliveService extends Service {
    private static final String ACTION_APPLY = "cn.lineai.action.APPLY_KEEP_ALIVE_STATE";
    private static final String EXTRA_WAKE_LOCK = "wake_lock";
    private static final String EXTRA_FOREGROUND = "foreground";
    private static final String EXTRA_SILENT_AUDIO = "silent_audio";
    private static final String EXTRA_STATUS = "status";
    private static final String CHANNEL_ID = "linecode_keep_alive";
    private static final int NOTIFICATION_ID = 1001;
    private static final int SAMPLE_RATE = 8000;

    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;
    private AudioTrack silentAudioTrack;
    private boolean foreground;
    private String status;

    @Override public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notification_keep_alive_title),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.keep_alive_notification_title));
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_APPLY.equals(intent.getAction())) return START_NOT_STICKY;
        boolean shouldWake = intent.getBooleanExtra(EXTRA_WAKE_LOCK, false);
        boolean shouldForeground = intent.getBooleanExtra(EXTRA_FOREGROUND, false);
        boolean shouldPlay = intent.getBooleanExtra(EXTRA_SILENT_AUDIO, false);
        status = intent.getStringExtra(EXTRA_STATUS);
        if (shouldWake) acquireWakeLock(); else releaseWakeLock();
        if (shouldPlay) startSilentAudio(); else stopSilentAudio();
        if (shouldForeground) {
            if (!foreground) {
                startForeground(NOTIFICATION_ID, buildNotification());
                foreground = true;
            } else if (notificationManager.areNotificationsEnabled()) {
                notificationManager.notify(NOTIFICATION_ID, buildNotification());
            }
        } else if (foreground) {
            stopForeground(true);
            foreground = false;
        }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        releaseWakeLock();
        stopSilentAudio();
        if (foreground) stopForeground(true);
        super.onDestroy();
    }

    private void acquireWakeLock() {
        if (wakeLock != null) return;
        PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (manager == null) return;
        wakeLock = manager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE,
                "LineCode:EncodingWakeLock");
        wakeLock.acquire();
    }

    private void releaseWakeLock() {
        if (wakeLock == null) return;
        if (wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    private void startSilentAudio() {
        if (silentAudioTrack != null) return;
        int samples = SAMPLE_RATE * 2;
        byte[] silence = new byte[samples * 2];
        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(silence.length).build();
            if (track.write(silence, 0, silence.length) <= 0) { track.release(); return; }
            track.setLoopPoints(0, samples, -1);
            track.play();
            silentAudioTrack = track;
        } catch (RuntimeException error) {
            if (track != null) track.release();
        }
    }

    private void stopSilentAudio() {
        AudioTrack track = silentAudioTrack;
        silentAudioTrack = null;
        if (track == null) return;
        try { track.pause(); track.flush(); } catch (IllegalStateException ignored) { }
        track.release();
    }

    private Notification buildNotification() {
        String text = status == null || status.isEmpty()
                ? getString(R.string.notification_keep_alive_text) : status;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_keepalive_notification)
                .setContentTitle(getString(R.string.notification_keep_alive_title))
                .setContentText(text).setContentIntent(open).setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setTicker(getString(R.string.notification_keep_alive_ticker))
                .setPriority(Notification.PRIORITY_LOW).build();
    }

    public static void apply(Context context, boolean wakeLock, boolean foreground,
                             boolean silentAudio, String status) {
        Intent intent = new Intent(context, LineCodeKeepAliveService.class);
        if (!wakeLock && !foreground && !silentAudio) {
            context.stopService(intent);
            return;
        }
        intent.setAction(ACTION_APPLY);
        intent.putExtra(EXTRA_WAKE_LOCK, wakeLock);
        intent.putExtra(EXTRA_FOREGROUND, foreground);
        intent.putExtra(EXTRA_SILENT_AUDIO, silentAudio);
        intent.putExtra(EXTRA_STATUS, status);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && foreground) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
