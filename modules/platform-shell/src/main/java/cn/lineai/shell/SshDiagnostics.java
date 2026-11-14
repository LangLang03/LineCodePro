package cn.lineai.shell;

import java.util.function.Consumer;
import java.util.function.Supplier;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.SshConfig;

/**
 * SSH connection diagnostics shared by the settings screens: runs a test connection on the
 * io scheduler and reports a structured outcome on main, so callers only map the outcome to
 * page copy.
 */
public final class SshDiagnostics {

    /** Outcome of one diagnostic run; delivered on the scheduler's main thread. */
    public static final class Report {
        public final boolean success;
        /** Connection output on success; the error detail (or code name) on failure. */
        public final String output;

        /** Use {@link #report} to build instances. */
        private Report(boolean success, String output) {
            this.success = success;
            this.output = output;
        }
    }

    /** Utility class; instantiation is not permitted. */
    private SshDiagnostics() {
    }

    /**
     * Runs one test connection on the io scheduler and delivers the outcome on main.
     *
     * @param service the SSH service used for the test connection
     * @param config the credentials to test
     * @param scheduler scheduler providing the io and main dispatchers
     * @param onReport consumer invoked exactly once with the outcome on the main thread
     */
    public static void test(SshService service, SshConfig config, Scheduler scheduler,
                            Consumer<Report> onReport) {
        runOnIoDeliverOnMain(scheduler, () -> report(service, config, scheduler), onReport);
    }

    /**
     * Runs a probe on the io scheduler and delivers its result on the main lane.
     *
     * <p>The probe blocks on network and file work, while the consumer mutates views, so the
     * hand-off must marshal: invoking the consumer inside the io task is what makes Android
     * reject the view update with a {@code CalledFromWrongThreadException}.</p>
     *
     * @param scheduler scheduler providing the io and main dispatchers
     * @param probe value supplier executed on the io lane
     * @param onResult consumer invoked with the probe result on the main lane
     * @param <T> probe result type
     */
    static <T> void runOnIoDeliverOnMain(Scheduler scheduler, Supplier<T> probe,
                                         Consumer<T> onResult) {
        scheduler.io(() -> {
            T result = probe.get();
            scheduler.main(() -> onResult.accept(result));
        });
    }

    /**
     * Runs one test connection synchronously on the calling thread; intended for callers
     * that are already on an io thread.
     *
     * @param service the SSH service used for the test connection
     * @param config the credentials to test
     * @param scheduler used only to classify error delivery; the run itself is synchronous
     * @return a report whose {@code output} carries connection output or the error detail
     */
    public static Report report(SshService service, SshConfig config, Scheduler scheduler) {
        try {
            String output = service.testConnection(config);
            return new Report(true, output == null ? "" : output.trim());
        } catch (DomainException error) {
            return new Report(false, error.detail().isEmpty()
                    ? error.code().name() : error.detail());
        }
    }
}
