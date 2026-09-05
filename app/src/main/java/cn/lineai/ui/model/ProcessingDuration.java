package cn.lineai.ui.model;

/** Compact elapsed time for a single processing section. */
public final class ProcessingDuration {
    private ProcessingDuration() {}

    public static String format(long elapsedMillis) {
        long seconds = Math.max(0, elapsedMillis) / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m " + seconds % 60 + "s";
        return minutes / 60 + "h " + minutes % 60 + "m " + seconds % 60 + "s";
    }
}
