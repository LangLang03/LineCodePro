package cn.lineai.shell;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.ArrayList;

/** Exercises the local and SSH backends of the background task registry. */
public class BackgroundShellRegistryTest {

    // ---------- LOCAL backend (real sh) ----------

    @Test public void localTaskCapturesOutputAndExitCode() {
        BackgroundShellRegistry registry = new BackgroundShellRegistry(null, null);
        String id = registry.start("echo bg-hello; echo __BG_EXIT__:0", "", false);
        assertTrue(id, id.matches("bg-\\d+"));
        String snapshot = registry.output(id, true, 5000);
        assertTrue(snapshot, snapshot.contains("finished (exit 0)"));
        assertTrue(snapshot, snapshot.contains("bg-hello"));
        assertTrue(snapshot, !snapshot.contains("__BG_EXIT__"));
        // The terminal state was observed by the model, so no notice is drained afterwards.
        assertTrue(registry.drainCompleted().isEmpty());
    }

    @Test public void localKillStopsTheProcess() throws Exception {
        BackgroundShellRegistry registry = new BackgroundShellRegistry(null, null);
        String id = registry.start("sleep 30", "", false);
        String result = registry.kill(id);
        assertTrue(result, result.contains("killed"));
        String snapshot = registry.output(id, false, 0);
        assertTrue(snapshot, snapshot.contains("killed"));
        assertTrue(registry.drainCompleted().isEmpty());
    }

    @Test public void drainReportsALocalCompletionExactlyOnce() throws Exception {
        BackgroundShellRegistry registry = new BackgroundShellRegistry(null, null);
        String id = registry.start("sleep 0.3; echo done-marker", "", false);
        Thread.sleep(150);
        assertTrue("still running right after start", registry.drainCompleted().isEmpty());
        Thread.sleep(700);
        List<String> notices = registry.drainCompleted();
        assertEquals(1, notices.size());
        assertTrue(notices.get(0), notices.get(0).contains("call shell_output with id=\"" + id + "\""));
        assertTrue(registry.drainCompleted().isEmpty());
    }

    @Test public void shutdownKillsRunningLocalTasks() throws Exception {
        BackgroundShellRegistry registry = new BackgroundShellRegistry(null, null);
        String id = registry.start("sleep 30", "", false);
        registry.shutdown();
        String snapshot = registry.output(id, false, 0);
        assertTrue(snapshot, snapshot.contains("killed"));
    }

    @Test public void unknownTaskIdsAreRejected() {
        BackgroundShellRegistry registry = new BackgroundShellRegistry(null, null);
        assertTrue(registry.output("bg-404", false, 0).contains("Unknown"));
        assertTrue(registry.kill("bg-404").contains("Unknown"));
    }

    // ---------- SSH backend (scripted remote executor) ----------

    @Test public void sshTaskLaunchesPollsAndCompletes() {
        Deque<String> polls = new ArrayDeque<>();
        polls.add("__BG_RUNNING__\nfirst chunk\n");
        polls.add("__BG_DONE__\nsecond chunk\n__BG_EXIT__:0\n");
        final List<String> sent = new ArrayList<>();
        BackgroundShellRegistry.RemoteExecutor executor = (command, timeoutMs) -> {
            sent.add(command);
            if (command.contains("__BG_PID__")) {
                assertTrue("launch uses setsid: " + command, command.contains("setsid"));
                assertTrue("launch cds into the directory: " + command,
                        command.contains("cd '/srv/app'"));
                return "__BG_PID__:4242 __BG_LOG__:/tmp/bg-4242.log";
            }
            if (command.contains("__BG_ALIVE__")) {
                return "__BG_DEAD__";
            }
            String next = polls.poll();
            return next == null ? "__BG_DONE__\n" : next;
        };
        BackgroundShellRegistry registry = new BackgroundShellRegistry(executor, null);
        String id = registry.start("run-server", "/srv/app", true);
        String snapshot = registry.output(id, true, 5000);
        assertTrue(snapshot, snapshot.contains("finished (exit 0)"));
        assertTrue(snapshot, snapshot.contains("first chunk"));
        assertTrue(snapshot, snapshot.contains("second chunk"));
        assertTrue(snapshot, !snapshot.contains("__BG_"));
        assertTrue(registry.drainCompleted().isEmpty());
        assertTrue("poll tail resumes from the consumed offset",
                sent.toString().contains("tail -c +13"));
    }

    @Test public void sshDrainProducesOneNoticeThenStops() throws Exception {
        Deque<String> polls = new ArrayDeque<>();
        polls.add("__BG_DONE__\noutput\n__BG_EXIT__:3\n");
        BackgroundShellRegistry.RemoteExecutor executor = (command, timeoutMs) -> {
            if (command.contains("__BG_PID__")) {
                return "__BG_PID__:77 __BG_LOG__:/tmp/bg.log";
            }
            if (command.contains("__BG_ALIVE__")) {
                return "__BG_DEAD__";
            }
            String next = polls.poll();
            return next == null ? "__BG_DONE__\n" : next;
        };
        BackgroundShellRegistry registry = new BackgroundShellRegistry(executor, null);
        String id = registry.start("make", "", true);
        Thread.sleep(10);
        List<String> notices = registry.drainCompleted();
        assertEquals(1, notices.size());
        assertTrue(notices.get(0), notices.get(0).contains("exit 3"));
        assertTrue(notices.get(0), notices.get(0).contains("make"));
        assertTrue(registry.drainCompleted().isEmpty());
    }

    @Test public void sshKillSendsGroupKillAndMarksKilled() {
        final List<String> sent = new ArrayList<>();
        BackgroundShellRegistry.RemoteExecutor executor = (command, timeoutMs) -> {
            sent.add(command);
            if (command.contains("__BG_PID__")) {
                return "__BG_PID__:99 __BG_LOG__:/tmp/bg-99.log";
            }
            if (command.contains("__BG_ALIVE__")) {
                return "__BG_DEAD__";
            }
            return "__BG_RUNNING__\n";
        };
        BackgroundShellRegistry registry = new BackgroundShellRegistry(executor, null);
        String id = registry.start("tail -f log", "", true);
        String result = registry.kill(id);
        assertTrue(result, result.contains("killed"));
        assertTrue("kill targets the process group: " + sent,
                sent.toString().contains("kill -KILL -- -99"));
        assertFalse("polls stop after the kill",
                registry.drainCompleted().toString().contains("finished"));
    }

    @Test public void failedSshLaunchSurfacesTheError() {
        BackgroundShellRegistry.RemoteExecutor executor = (command, timeoutMs) -> "";
        BackgroundShellRegistry registry = new BackgroundShellRegistry(executor, null);
        try {
            registry.start("cmd", "", true);
            throw new AssertionError("launch should fail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("background launch failed"));
        }
    }

    @Test public void providerBackendStartReadAndKill() {
        BackgroundShellRegistry.ProviderBackground provider =
                new BackgroundShellRegistry.ProviderBackground() {
                    long handle = 0;
                    BackgroundShellRegistry.OutputSink sink;
                    boolean delivered;
                    boolean killed;

                    @Override public long start(String command, String cwd,
                                                BackgroundShellRegistry.OutputSink outputSink) {
                        sink = outputSink;
                        handle = 71;
                        return handle;
                    }

                    @Override public int[] read(long handleId, long offset,
                                                StringBuilder chunkOut) {
                        if (handleId != handle) return null;
                        if (!delivered) {
                            // Simulate the streaming callback racing with the first read; the
                            // delivered bytes must advance the offset so read never repeats them.
                            delivered = true;
                            sink.onChunk("provider output\n");
                        }
                        return new int[]{killed ? 0 : 1, killed ? 9 : -1};
                    }

                    @Override public boolean kill(long handleId) {
                        killed = true;
                        return handleId == handle;
                    }

                    @Override public void forget(long handleId) { }
                };
        BackgroundShellRegistry registry = new BackgroundShellRegistry(null, provider, () -> true);
        String id = registry.start("serve", "/home", true);
        String snapshot = registry.output(id, false, 0);
        assertTrue(snapshot, snapshot.contains("provider output"));
        assertTrue(snapshot, snapshot.indexOf("provider output")
                == snapshot.lastIndexOf("provider output"));
        assertTrue(registry.kill(id).contains("killed"));
        assertTrue(registry.output(id, false, 0).contains("killed"));
    }

    @Test public void providerPollAppendsOnlyUndeliveredSuffix() {
        final String buffer = "first\nsecond\n";
        BackgroundShellRegistry.ProviderBackground provider =
                new BackgroundShellRegistry.ProviderBackground() {
                    long handle = 0;
                    BackgroundShellRegistry.OutputSink sink;

                    @Override public long start(String command, String cwd,
                                                BackgroundShellRegistry.OutputSink outputSink) {
                        sink = outputSink;
                        handle = 5;
                        return handle;
                    }

                    @Override public int[] read(long handleId, long offset,
                                                StringBuilder chunkOut) {
                        if (handleId != handle) return null;
                        if (offset == 0) {
                            // The streamed callback wins the race while the read is in
                            // flight: the poll must not repeat the streamed bytes.
                            sink.onChunk("first\n");
                        }
                        chunkOut.append(buffer.substring((int) Math.min(offset, buffer.length())));
                        return new int[] {1, -1};
                    }

                    @Override public boolean kill(long handleId) { return handleId == handle; }

                    @Override public void forget(long handleId) { }
                };
        BackgroundShellRegistry registry = new BackgroundShellRegistry(null, provider, () -> true);
        String id = registry.start("serve", "/home", true);
        String snapshot = registry.output(id, false, 0);
        assertEquals(1, countOf(snapshot, "first"));
        assertEquals(1, countOf(snapshot, "second"));
        // The consumed offset must sit exactly at the buffer end; overshooting would
        // silently drop every further polled chunk.
        snapshot = registry.output(id, false, 0);
        assertEquals(1, countOf(snapshot, "second"));
    }

    private static int countOf(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
