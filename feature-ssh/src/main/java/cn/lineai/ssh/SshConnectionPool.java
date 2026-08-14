package cn.lineai.ssh;

import android.util.Log;
import cn.lineai.model.SshConfig;
import com.jcraft.jsch.Session;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 简单的 SSH 会话连接池，按 (host, port, username) 复用 jsch 的 {@link Session}。
 *
 * <p>jsch 的 {@code Session} 非线程安全，因此池中每个 entry 同时只允许一个借用方使用。
 * 借用方在用完后必须调用 {@link #release(Session, SshConfig)} 将连接归还。
 * 如果忘记归还，{@link IdleEvictor} 后台线程会在空闲超过 {@link #IDLE_TIMEOUT_MS} 后主动关闭，
 * 避免资源泄漏。{@link #closeAll()} 会在 SshService 生命周期结束时被调用。</p>
 *
 * <p>连接复用失败时（网络抖动、密钥变化等）应通过 {@link #discard(Session)} 丢弃坏连接，
 * 下次借用会重新建立。</p>
 */
public final class SshConnectionPool {
    public static final String TAG = "SshConnectionPool";
    public static final long IDLE_TIMEOUT_MS = 5L * 60L * 1000L;
    private static final long EVICTOR_PERIOD_MS = 60L * 1000L;

    /**
     * 池中的一个连接条目，{@code inUse} 用于支持"借出即移出空闲链表"的乐观借用。
     */
    private static final class Entry {
        final Session session;
        final ReentrantLock lock = new ReentrantLock();
        volatile long lastUsedAtMs;
        volatile boolean inUse;
        volatile boolean closed;

        Entry(Session session) {
            this.session = session;
            this.lastUsedAtMs = System.currentTimeMillis();
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final SessionFactory factory;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final ScheduledExecutorService evictor;
    private final ScheduledFuture<?> evictorHandle;

    public SshConnectionPool(SessionFactory factory) {
        this.factory = factory;
        evictor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "linecode-ssh-evictor");
            thread.setDaemon(true);
            return thread;
        });
        evictorHandle = evictor.scheduleWithFixedDelay(
                this::evictIdleEntries,
                EVICTOR_PERIOD_MS,
                EVICTOR_PERIOD_MS,
                TimeUnit.MILLISECONDS);
    }

    /**
     * 获取或创建与 config 对应的 Session。已借出或不可用时会重新建立。
     * 调用方必须在使用完毕后调用 {@link #release(Session, SshConfig)} 归还。
     */
    public Session getOrCreate(SshConfig config, int timeoutMs) throws Exception {
        if (config == null) {
            throw new IllegalArgumentException("config is null");
        }
        if (shutdown.get()) {
            throw new IllegalStateException("connection pool is closed");
        }
        String key = keyOf(config);
        Entry entry = entries.get(key);
        if (entry != null && !entry.closed && entry.lock.tryLock()) {
            if (!entry.session.isConnected() || entry.closed) {
                try {
                    closeQuietly(entry);
                    entries.remove(key, entry);
                } finally {
                    entry.lock.unlock();
                }
            } else {
                entry.inUse = true;
                entry.lastUsedAtMs = System.currentTimeMillis();
                return entry.session;
            }
        }
        Session session = factory.createSession(config, timeoutMs);
        Entry created = new Entry(session);
        created.lock.lock();
        created.inUse = true;
        Entry prior = entries.putIfAbsent(key, created);
        if (prior != null) {
            // 竞争失败：另一个线程已抢先放入同 key 的 entry，本次新建的会话被丢弃。
            // 有限次尝试借用已有 entry；都不行则回退为用新建 entry 强制替换。
            Entry candidate = prior;
            for (int attempt = 0; attempt < 2 && candidate != null; attempt++) {
                if (candidate.closed || !candidate.lock.tryLock()) {
                    if (candidate.closed) {
                        entries.remove(key, candidate);
                    }
                    // 无效或正在被占用的 entry，读取下次尝试的最新 entry。
                    candidate = entries.get(key);
                    continue;
                }
                if (!candidate.closed && candidate.session.isConnected()) {
                    // 借用成功，丢弃本次新建的 entry。
                    candidate.inUse = true;
                    candidate.lastUsedAtMs = System.currentTimeMillis();
                    created.inUse = false;
                    created.lock.unlock();
                    closeQuietly(created);
                    return candidate.session;
                }
                // 已有 entry 失效：解锁移除，读取下次尝试的最新 entry。
                candidate.lock.unlock();
                entries.remove(key, candidate);
                candidate = entries.get(key);
            }
            // 回退：用新建 entry 强制替换（保持原有的 put 语义）。
            entries.put(key, created);
            return created.session;
        }
        return session;
    }

    /**
     * 归还一个借用过的 Session。如果连接已经断开则直接关闭丢弃。
     */
    public void release(Session session, SshConfig config) {
        if (session == null || config == null) {
            return;
        }
        Entry entry = entries.get(keyOf(config));
        if (entry == null || entry.session != session) {
            // 池中已被替换，直接关闭传入的 session
            closeQuietlySession(session);
            return;
        }
        try {
            entry.inUse = false;
            entry.lastUsedAtMs = System.currentTimeMillis();
            if (!session.isConnected() || entry.closed) {
                closeQuietly(entry);
                entries.remove(keyOf(config), entry);
            }
        } finally {
            if (entry.lock.isHeldByCurrentThread()) {
                entry.lock.unlock();
            }
        }
    }

    /**
     * 强制丢弃一个连接（例如出现 JSchException）。下次借用会自动重建。
     */
    public void discard(Session session, SshConfig config) {
        if (session == null || config == null) {
            return;
        }
        String key = keyOf(config);
        Entry entry = entries.get(key);
        if (entry != null && entry.session == session) {
            try {
                closeQuietly(entry);
                entries.remove(key, entry);
                entry.inUse = false;
            } finally {
                if (entry.lock.isHeldByCurrentThread()) {
                    entry.lock.unlock();
                }
            }
        } else {
            closeQuietlySession(session);
        }
    }

    /**
     * 关闭并清空池中所有连接。
     */
    public void closeAll() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        try {
            if (evictorHandle != null) {
                evictorHandle.cancel(false);
            }
            evictor.shutdownNow();
        } catch (Exception ignored) {
        }
        for (Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Entry> mapEntry = iterator.next();
            Entry entry = mapEntry.getValue();
            iterator.remove();
            closeQuietly(entry);
        }
    }

    /**
     * 测试/调试用：当前活跃连接数。
     */
    public int size() {
        return entries.size();
    }

    private void evictIdleEntries() {
        try {
            long now = System.currentTimeMillis();
            for (Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<String, Entry> mapEntry = iterator.next();
                Entry entry = mapEntry.getValue();
                if (entry == null) {
                    iterator.remove();
                    continue;
                }
                if (entry.inUse) {
                    continue;
                }
                if (!entry.lock.tryLock()) {
                    continue;
                }
                try {
                    if (entry.inUse) {
                        continue;
                    }
                    if (now - entry.lastUsedAtMs < IDLE_TIMEOUT_MS) {
                        continue;
                    }
                    iterator.remove();
                    closeQuietly(entry);
                } finally {
                    entry.lock.unlock();
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "idle evictor iteration failed", t);
        }
    }

    private static String keyOf(SshConfig config) {
        return config.getHost() + "|" + config.getPort() + "|" + config.getUsername();
    }

    private static void closeQuietly(Entry entry) {
        if (entry == null) {
            return;
        }
        entry.closed = true;
        closeQuietlySession(entry.session);
    }

    private static void closeQuietlySession(Session session) {
        if (session == null) {
            return;
        }
        try {
            if (session.isConnected()) {
                session.disconnect();
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 由 {@link SshService} 提供的 Session 工厂，让池不感知 SshService 内部的创建细节。
     */
    public interface SessionFactory {
        Session createSession(SshConfig config, int timeoutMs) throws Exception;
    }
}
