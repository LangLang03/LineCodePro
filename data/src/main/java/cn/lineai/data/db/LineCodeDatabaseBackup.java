package cn.lineai.data.db;

import android.content.Context;
import android.util.Log;
import cn.lineai.data.importer.LineCodeArchiveCodec;
import cn.lineai.data.importer.LineCodeDatabaseArchive;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * 本地自动备份：将数据库全量（含聊天正文）导出到
 * {@code files/backups/linecode/}，并用 {@code latest.json} 记录最新一份。
 *
 * <p>设计目标：当数据库因系统杀死进程导致损坏、被 {@link LineCodeDatabaseErrorHandler}
 * 或 {@link LineCodeDatabase#onOpen} 检测到时，可从这里回退到最近一次正常备份，
 * 避免聊天记录永久丢失。备份失败仅记日志，不影响主流程。
 */
public final class LineCodeDatabaseBackup {
    private static final String TAG = "LineCodeDatabaseBackup";
    static final int MAX_BACKUPS = 3;
    private static final String LATEST_NAME = LineCodeArchiveCodec.ENTRY_DATABASE;

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();
    /**
     * 全量快照导出会把整个数据库读进一个 JSONObject 并写两份文件，成本随对话长度线性增长。
     * 而 {@code saveConversation} 在每次收发/工具批次/流式步骤后都会触发，不做限流时一次长会话
     * 会排队数十次全量备份：后台 CPU/磁盘持续饱和、与主线程的读库争锁，同时产生大量
     * JSON 临时对象引发 GC 卡顿——表现为长对话“加载慢 / 上滑下滑卡”。
     *
     * <p>备份只是损坏时的回退手段（恢复前会征询用户），因此限流到最小间隔，并且同一时刻
     * 最多一份在途；在途期间到达的新请求合并为一次收尾备份，不丢最新状态。
     */
    static final long MIN_BACKUP_INTERVAL_NANOS = 3L * 60L * 1_000_000_000L;
    private final java.util.concurrent.atomic.AtomicBoolean backupInFlight = new java.util.concurrent.atomic.AtomicBoolean();
    private volatile boolean backupRequestedWhileRunning;
    private volatile long lastBackupNanos;

    public LineCodeDatabaseBackup(Context context) {
        this.context = context.getApplicationContext();
    }

    File backupDir() {
        return new File(context.getDir("backups", Context.MODE_PRIVATE), "linecode");
    }

    /**
     * 异步保存一次全量备份（带最小间隔限流）。重复调用由单线程 executor 串行化，避免并发写坏文件。
     */
    public void saveAsync(LineCodeDatabase database) {
        long now = System.nanoTime();
        if (lastBackupNanos != 0L && now - lastBackupNanos < MIN_BACKUP_INTERVAL_NANOS) {
            return;
        }
        enqueue(database);
    }

    private void enqueue(final LineCodeDatabase database) {
        if (!backupInFlight.compareAndSet(false, true)) {
            backupRequestedWhileRunning = true;
            return;
        }
        executor.execute(() -> runBackup(database));
    }

    private void runBackup(LineCodeDatabase database) {
        try {
            save(database);
            lastBackupNanos = System.nanoTime();
        } catch (Throwable e) { // 备份失败绝不能影响主流程
            Log.e(TAG, "自动备份失败（已忽略）: " + e.getMessage(), e);
        } finally {
            backupInFlight.set(false);
            if (backupRequestedWhileRunning) {
                // 在途期间又有新写入：再补一次，保证最后几次修改也能落盘。
                backupRequestedWhileRunning = false;
                enqueue(database);
            }
        }
    }

    void save(LineCodeDatabase database) throws Exception {
        synchronized (lock) {
            File dir = backupDir();
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "无法创建备份目录: " + dir);
                return;
            }
            long ts = System.currentTimeMillis();
            File target = new File(dir, "backup-" + ts + ".json");
            JSONObject snapshot = new LineCodeDatabaseArchive().exportFullSnapshot(database);
            writeJson(target, snapshot);
            writeJson(new File(dir, LATEST_NAME), snapshot);
            pruneOldBackups(dir);
            Log.i(TAG, "自动备份完成: " + target.getName());
        }
    }

    /**
     * 从最新备份恢复数据到给定数据库。返回是否成功恢复。
     */
    boolean restoreLatest(LineCodeDatabase database) {
        synchronized (lock) {
            File latest = new File(backupDir(), LATEST_NAME);
            if (!latest.isFile()) {
                Log.w(TAG, "无可用备份，无法恢复");
                return false;
            }
            try {
                JSONObject snapshot = new LineCodeDatabaseArchive().readSnapshot(backupDir());
                new LineCodeDatabaseArchive().importSnapshot(database, snapshot);
                Log.i(TAG, "已从备份恢复: " + latest.getName());
                return true;
            } catch (Throwable e) {
                Log.e(TAG, "从备份恢复失败: " + e.getMessage(), e);
                return false;
            }
        }
    }

    private void writeJson(File file, JSONObject json) throws Exception {
        try (Writer writer = new FileWriter(file)) {
            writer.write(json.toString());
        }
    }

    private void pruneOldBackups(File dir) {
        File[] backups = dir.listFiles((d, name) -> name.startsWith("backup-") && name.endsWith(".json"));
        if (backups == null || backups.length <= MAX_BACKUPS) {
            return;
        }
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < backups.length - MAX_BACKUPS; i++) {
            if (!backups[i].delete()) {
                Log.w(TAG, "删除旧备份失败: " + backups[i].getName());
            }
        }
    }
}
