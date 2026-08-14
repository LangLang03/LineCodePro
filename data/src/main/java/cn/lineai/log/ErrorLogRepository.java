package cn.lineai.log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ErrorLogRepository {
    private static final String DIR_NAME = "error_logs";
    private static final SimpleDateFormat FILE_TIME_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT);
    private static final SimpleDateFormat DISPLAY_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private final String errorLogDirPath;

    public ErrorLogRepository(String errorLogDirPath) {
        this.errorLogDirPath = errorLogDirPath;
    }

    public File directory() {
        File dir = new File(errorLogDirPath, DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public File record(String type, String summary, Throwable throwable, String details) {
        long now = System.currentTimeMillis();
        File file = new File(directory(), FILE_TIME_FORMAT.format(new Date(now)) + "-" + safeName(type) + ".log");
        StringBuilder builder = new StringBuilder();
        builder.append("LineCode Error Log\n");
        builder.append("Time: ").append(DISPLAY_TIME_FORMAT.format(new Date(now))).append('\n');
        builder.append("Type: ").append(type == null ? "error" : type).append('\n');
        builder.append("Summary: ").append(summary == null ? "" : ErrorLogRedactor.redact(summary)).append("\n\n");
        if (details != null && details.length() > 0) {
            builder.append("Details:\n").append(ErrorLogRedactor.redact(details)).append("\n\n");
        }
        if (throwable != null) {
            builder.append("Stacktrace:\n").append(ErrorLogRedactor.redact(stackTrace(throwable))).append('\n');
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(builder.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
        return file;
    }

    public List<ErrorLogEntry> list() {
        File[] files = directory().listFiles((dir, name) -> name.endsWith(".log"));
        ArrayList<ErrorLogEntry> entries = new ArrayList<>();
        if (files == null) {
            return entries;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (File file : files) {
            entries.add(new ErrorLogEntry(file, file.getName(), DISPLAY_TIME_FORMAT.format(new Date(file.lastModified())), file.lastModified()));
        }
        return entries;
    }

    public void clear() {
        File[] files = directory().listFiles((dir, name) -> name.endsWith(".log"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            file.delete();
        }
    }

    private static String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private static String safeName(String value) {
        String raw = value == null || value.length() == 0 ? "error" : value;
        return raw.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
