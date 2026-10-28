package cn.lineai.runtime;

import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Writes uncaught exceptions to disk and opens the crash screen before the process dies. */
public final class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String DIRECTORY = "crash";
    private static final String FILE = "last-crash.txt";

    private final Context context;
    private final Thread.UncaughtExceptionHandler previous;

    private CrashHandler(Context context, Thread.UncaughtExceptionHandler previous) {
        this.context = context;
        this.previous = previous;
    }

    public static void install(Context context) {
        Context app = context.getApplicationContext();
        Thread.setDefaultUncaughtExceptionHandler(
                new CrashHandler(app, Thread.getDefaultUncaughtExceptionHandler()));
    }

    public static File crashFile(Context context) {
        return new File(new File(context.getFilesDir(), DIRECTORY), FILE);
    }

    public static File pendingFile(Context context) {
        return new File(new File(context.getFilesDir(), DIRECTORY), "pending-log.txt");
    }

    @Override public void uncaughtException(Thread thread, Throwable error) {
        File file = crashFile(context);
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            StringWriter buffer = new StringWriter();
            PrintWriter writer = new PrintWriter(buffer);
            writer.println("LineCode crash report");
            writer.println("time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    Locale.ROOT).format(new Date()));
            writer.println("thread: " + thread.getName());
            writer.println();
            error.printStackTrace(writer);
            writer.flush();
            try (FileWriter output = new FileWriter(file)) {
                output.write(buffer.toString());
            }
            Intent intent = new Intent();
            intent.setClassName(context, "cn.lineai.app.CrashActivity");
            intent.putExtra("crash_file", file.getAbsolutePath());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
        } catch (Throwable failure) {
            System.err.println("Crash handler failed: " + failure);
        }
        if (previous != null) {
            previous.uncaughtException(thread, error);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }
    }
}
