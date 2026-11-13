package cn.lineai.terminalprovider;

import android.system.Os;
import android.system.OsConstants;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Terminates a process and all of its descendants: builds the ppid chain from
 * {@code /proc/<pid>/stat}, kills children first, then the shell itself.
 */
final class ProcessKiller {

    private ProcessKiller() {
    }

    static void killTree(Process process) {
        if (process == null) {
            return;
        }
        int pid = pid(process);
        if (pid <= 0) {
            process.destroy();
            return;
        }
        for (int descendant : collectDescendants(pid)) {
            safeKill(descendant);
        }
        safeKill(pid);
        process.destroy();
    }

    /**
     * Android's {@link Process} does not expose pid(); reflect the internal
     * {@code pid} field of the concrete implementation.
     */
    private static int pid(Process process) {
        try {
            Field field = process.getClass().getDeclaredField("pid");
            field.setAccessible(true);
            Object value = field.get(process);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } catch (Throwable ignored) {
            android.util.Log.w("ProcessKiller", "pid reflection unavailable", ignored);
        }
        return -1;
    }

    /** Collects every descendant pid of rootPid by walking /proc stat files. */
    private static Set<Integer> collectDescendants(int rootPid) {
        Map<Integer, Integer> ppidByPid = new HashMap<>();
        File[] dirs = new File("/proc").listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                int pid;
                try {
                    pid = Integer.parseInt(dir.getName());
                } catch (NumberFormatException notAPid) {
                    continue;
                }
                int ppid = ppidOf(dir);
                if (ppid > 0 && pid != rootPid) {
                    ppidByPid.put(pid, ppid);
                }
            }
        }
        Set<Integer> descendants = new HashSet<>();
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<Integer, Integer> entry : ppidByPid.entrySet()) {
                int child = entry.getKey();
                int parent = entry.getValue();
                if ((parent == rootPid || descendants.contains(parent)) && descendants.add(child)) {
                    changed = true;
                }
            }
        } while (changed);
        return descendants;
    }

    /** stat format: pid (comm) state ppid ... */
    private static int ppidOf(File procDir) {
        String stat = statLine(procDir);
        if (stat == null) {
            return -1;
        }
        int close = stat.lastIndexOf(')');
        if (close < 0) {
            return -1;
        }
        String rest = stat.substring(close + 1).trim();
        if (rest.isEmpty()) {
            return -1;
        }
        String[] fields = rest.split("\\s+");
        if (fields.length < 2) {
            return -1;
        }
        try {
            return Integer.parseInt(fields[1]);
        } catch (NumberFormatException malformedStat) {
            return -1;
        }
    }

    private static String statLine(File procDir) {
        File stat = new File(procDir, "stat");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(stat), StandardCharsets.UTF_8))) {
            return reader.readLine();
        } catch (IOException vanished) {
            return null;
        }
    }

    private static void safeKill(int pid) {
        if (pid <= 0) {
            return;
        }
        try {
            Os.kill(pid, OsConstants.SIGKILL);
        } catch (Throwable restricted) {
            // The process may have exited between the walk and the kill, or the uid
            // may not be permitted to signal it; nothing more can be done.
            android.util.Log.w("ProcessKiller", "SIGKILL failed for pid " + pid, restricted);
        }
    }
}
