package cn.lineai;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/** Records exception locations without persisting messages or user content. */
public final class LineCodeLogger {
    private static final String TAG = "LineCodePro";
    private static final AtomicLong NEXT_ID = new AtomicLong();
    private static boolean installed;

    private LineCodeLogger() { }

    public static synchronized void install(Context context) {
        if (installed) {
            return;
        }
        Context application = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                record(application, "uncaught_exception", error);
            } catch (RuntimeException loggingFailure) {
                Log.e(TAG, "Failed to record uncaught exception");
            } finally {
                if (previous != null) {
                    previous.uncaughtException(thread, error);
                }
            }
        });
        installed = true;
    }

    public static void record(Context context, String event, Throwable error) {
        String kind = error == null ? "unknown" : error.getClass().getName();
        Log.e(TAG, event + " (" + kind + ")");
        try {
            File directory = new File(context.getFilesDir(), "error_logs");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                return;
            }
            File file = new File(directory, "java-" + System.currentTimeMillis()
                    + "-" + NEXT_ID.getAndIncrement() + ".log");
            try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
                writer.write("Java ");
                writer.write(event);
                writer.write(' ');
                writer.write(kind);
                writer.write('\n');
                if (error != null) {
                    StackTraceElement[] frames = error.getStackTrace();
                    for (int i = 0; i < Math.min(frames.length, 64); i++) {
                        StackTraceElement frame = frames[i];
                        writer.write(frame.getClassName());
                        writer.write('.');
                        writer.write(frame.getMethodName());
                        writer.write(':');
                        writer.write(Integer.toString(frame.getLineNumber()));
                        writer.write('\n');
                    }
                }
            }
        } catch (IOException | RuntimeException failure) {
            Log.e(TAG, "Failed to persist exception location");
        }
    }
}
