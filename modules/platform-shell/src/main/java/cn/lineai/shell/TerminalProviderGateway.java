package cn.lineai.shell;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.model.TerminalProviderConfig;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.data.api.TerminalProviderStore;
import cn.lineai.ipc.BaseIpcProvider;
import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.IpcProviderConnectionState;
import cn.lineai.ipc.IpcProviderManager;
import cn.lineai.ipc.IpcProviderScanner;
import cn.lineai.ipc.IpcProviderStateListener;
import cn.lineai.ipc.IpcProviderType;
import cn.lineai.ipc.ScannedProvider;
import cn.lineai.ipc.terminal.TerminalIpcProvider;
import cn.lineai.ipc.terminal.TerminalShellBackgroundState;
import cn.lineai.ipc.terminal.TerminalShellCallback;
import cn.lineai.ipc.terminal.TerminalShellResult;

/**
 * Binds installed terminal providers and routes shell execution through them.
 *
 * <p>Trusted providers are discovered automatically (no manual scan or enable step): a provider
 * whose signature matches LineCode is always loaded, and a provider with a different signature is
 * loaded when the user enables the security trust switch. The first connected provider serves the
 * terminal provider execution mode.</p>
 */
public final class TerminalProviderGateway {

    /** The class itself must not implement :ipc types: callers (runtime) resolve the
     * whole type hierarchy and would need :ipc on their compile classpath. */
    private final IpcProviderStateListener stateListener = new IpcProviderStateListener() {
        @Override public void onStateChanged(BaseIpcProvider provider,
                                             IpcProviderConnectionState newState,
                                             Throwable cause) {
            TerminalProviderGateway.this.onStateChanged(provider, newState, cause);
        }
    };

    /** Connection states surfaced to settings pages. */
    static final int STATE_NONE = 0;
    static final int STATE_DISCONNECTED = 1;
    static final int STATE_CONNECTED = 2;

    private static final int PROBE_TIMEOUT_MS = 20_000;
    private static final String TAG = "TerminalProviderGateway";

    private final Context context;
    private final SettingsStore settings;
    private final TerminalProviderStore store;
    /** Whether the user selected the terminal provider execution target (wired by the app graph). */
    private final java.util.function.BooleanSupplier terminalModeActive;
    private final IpcProviderManager manager;
    private final IpcProviderScanner scanner = new IpcProviderScanner();
    /**
     * Mutated on the main thread ({@link #reconcileBindings}) and iterated from binder
     * threads ({@link #pickConnectedProvider} via onStateChanged); a plain LinkedHashMap
     * would throw ConcurrentModificationException on the binder thread.
     */
    private final Map<String, TerminalIpcProvider> boundById = new ConcurrentHashMap<>();

    private volatile TerminalIpcProvider active;
    private volatile String activeHome = "";

    /**
     * Creates the gateway and registers its connection state listener.
     *
     * @param context any context; only the application context is retained
     * @param settings settings store backing the signature trust switch
     * @param store persisted provider configuration list
     * @param terminalModeActive whether the terminal provider execution target is selected
     */
    public TerminalProviderGateway(Context context, SettingsStore settings,
                                   TerminalProviderStore store,
                                   java.util.function.BooleanSupplier terminalModeActive) {
        this.context = context.getApplicationContext();
        this.settings = settings;
        this.store = store;
        this.terminalModeActive = terminalModeActive;
        this.manager = new IpcProviderManager(this.context);
        this.manager.addStateListener(stateListener);
    }

    /** Entry point after graph construction: discovers and binds trusted providers. */
    public void start() {
        refresh();
    }

    /** Detaches the listener, unbinds every provider and clears the active connection. */
    public void shutdown() {
        manager.removeStateListener(stateListener);
        manager.unregisterAll();
        boundById.clear();
        active = null;
        activeHome = "";
    }

    /** Discovers trusted providers, adds missing ones and reconciles bindings. Idempotent. */
    public void refresh() {
        autoDiscover();
        reconcileBindings();
    }

    /** Whether the terminal provider execution target is currently selected. */
    public boolean terminalMode() {
        return terminalModeActive.getAsBoolean();
    }

    /**
     * Trust decision for one provider package: same signing certificate, or the user
     * enabled the global trust switch.
     *
     * @param packageName package under evaluation
     * @return {@code true} when the provider may be loaded
     */
    public boolean trusted(String packageName) {
        return trustEnabled() || ProviderSignature.sameSignature(context, packageName);
    }

    /**
     * Aggregated connection state for the settings UI: none when terminal mode is off,
     * connected when a provider is bound, disconnected when only enabled configs exist.
     *
     * @return one of the {@code STATE_*} constants
     */
    public int state() {
        if (!terminalMode()) {
            return STATE_NONE;
        }
        TerminalIpcProvider provider = active;
        if (provider != null && provider.isBound()) {
            return STATE_CONNECTED;
        }
        for (TerminalProviderConfig config : store.list()) {
            if (config.enabled) {
                return STATE_DISCONNECTED;
            }
        }
        return STATE_NONE;
    }

    /** Display name of the active or first enabled provider; empty when none exists. */
    public String providerName() {
        TerminalIpcProvider provider = active;
        if (provider != null && provider.getConfig() != null) {
            return provider.getConfig().getName();
        }
        for (TerminalProviderConfig config : store.list()) {
            if (config.enabled) {
                return config.name;
            }
        }
        return "";
    }

    /** Home path reported by the active provider; empty when unknown or unbound. */
    public String home() {
        return activeHome;
    }

    /**
     * Executes one foreground shell command through the connected provider and blocks the
     * calling thread until completion; never call from the main thread.
     *
     * @param command the command line to execute in the provider shell
     * @param timeoutMs timeout applied to the AIDL call
     * @return the captured standard output
     * @throws cn.lineai.core.kernel.error.DomainException with {@code INVALID_INPUT} when no
     *         provider is connected, {@code TOOL_FAILED} on a non-zero exit or error output,
     *         or {@code NETWORK} for transport failures
     */
    public String execute(String command, int timeoutMs) {
        TerminalIpcProvider provider = active;
        if (provider == null || !provider.isBound()) {
            throw new DomainException(ErrorCode.INVALID_INPUT,
                    "terminal provider is not connected; enable a provider in terminal settings");
        }
        StringBuilder output = new StringBuilder();
        StringBuilder failure = new StringBuilder();
        TerminalShellCallback callback = new TerminalShellCallback() {
            @Override
            public void onOutput(String content) {
                synchronized (output) {
                    output.append(content);
                }
            }

            @Override
            public void onError(String error) {
                synchronized (failure) {
                    if (failure.length() > 0) {
                        failure.append('\n');
                    }
                    failure.append(error);
                }
            }

            @Override
            public void onComplete(int exitCode) {
            }
        };
        try {
            TerminalShellResult result = provider.executeShell(command, activeHome,
                    timeoutMs, callback);
            if (failure.length() > 0) {
                throw new DomainException(ErrorCode.TOOL_FAILED, failure.toString());
            }
            if (!result.isSuccess()) {
                throw new DomainException(ErrorCode.TOOL_FAILED, "exit " + result.getExitCode()
                        + "\n" + output);
            }
            return output.toString();
        } catch (DomainException error) {
            throw error;
        } catch (android.os.RemoteException error) {
            throw new DomainException(ErrorCode.NETWORK, detail(error), error);
        } catch (IllegalStateException error) {
            throw new DomainException(ErrorCode.INVALID_INPUT, detail(error), error);
        }
    }

    /** Runs a short command to verify that shell and guest filesystem work on the device. */
    public String probe() {
        return execute("uname -a; id; cat /etc/os-release | head -2; pwd", PROBE_TIMEOUT_MS);
    }

    /** Terminal provider background shell (AIDL v2); every call resolves the active provider. */
    public BackgroundShellRegistry.ProviderBackground providerBackground() {
        return new BackgroundShellRegistry.ProviderBackground() {
            @Override public long start(String command, String cwd,
                                        BackgroundShellRegistry.OutputSink sink) {
                TerminalIpcProvider provider = active;
                if (provider == null || !provider.isBound()) {
                    return -1;
                }
                try {
                    return provider.startShellBackground(command, cwd, new TerminalShellCallback() {
                        @Override public void onOutput(String content) {
                            if (sink != null) sink.onChunk(content);
                        }

                        @Override public void onError(String error) {
                            // Launch failures return -1; later errors are also surfaced by
                            // onComplete(-1), so the text only needs to reach the buffer.
                            if (sink != null) sink.onChunk(error == null ? "" : error + "\n");
                        }

                        @Override public void onComplete(int exitCode) {
                            if (sink != null) sink.onComplete(exitCode);
                        }
                    });
                } catch (Exception error) {
                    Log.w(TAG, "startShellBackground failed: " + detail(error));
                    return -1;
                }
            }

            @Override public int[] read(long handle, long offset, StringBuilder chunkOut) {
                TerminalIpcProvider provider = active;
                if (provider == null || !provider.isBound()) {
                    return null;
                }
                try {
                    TerminalShellBackgroundState state =
                            provider.readShellBackground(handle, offset);
                    chunkOut.append(state.chunk);
                    return new int[]{state.running ? 1 : 0, state.running ? -1 : state.exitCode};
                } catch (Exception error) {
                    return null;
                }
            }

            @Override public boolean kill(long handle) {
                try {
                    TerminalIpcProvider provider = active;
                    return provider != null && provider.isBound()
                            && provider.killShellBackground(handle);
                } catch (Exception error) {
                    return false;
                }
            }

            @Override public void forget(long handle) {
                try {
                    TerminalIpcProvider provider = active;
                    if (provider != null && provider.isBound()) {
                        provider.forgetShellBackground(handle);
                    }
                } catch (Exception ignored) {
                    // The provider may have died between the check and the call; dropping the
                    // buffer is the provider's problem to GC, not a shell-output failure.
                    System.err.println("shell background forget failed: " + ignored);
                }
            }
        };
    }

    /** Connection listener: activates a newly connected provider and fails over on loss. */
    private void onStateChanged(BaseIpcProvider provider, IpcProviderConnectionState newState,
                               Throwable cause) {
        if (!(provider instanceof TerminalIpcProvider)) {
            return;
        }
        if (newState == IpcProviderConnectionState.CONNECTED) {
            TerminalIpcProvider terminal = (TerminalIpcProvider) provider;
            if (active == null || !active.isBound()) {
                activate(terminal);
            }
            return;
        }
        if (newState == IpcProviderConnectionState.DISCONNECTED
                || newState == IpcProviderConnectionState.FAILED) {
            if (active == provider) {
                active = null;
                activeHome = "";
                pickConnectedProvider();
            }
        }
    }

    /** Adds freshly scanned trusted providers to the persisted config store. */
    private void autoDiscover() {
        boolean trust = trustEnabled();
        List<ScannedProvider> scanned = scanner.scan(context, IpcProviderType.TERMINAL);
        for (ScannedProvider provider : scanned) {
            if (!trust && !ProviderSignature.sameSignature(context, provider.getPackageName())) {
                continue;
            }
            Log.i(TAG, "auto-load terminal provider " + provider.getPackageName());
            store.add(new TerminalProviderConfig("", true, provider.getLabel(),
                    provider.getPackageName(), provider.getServiceClass(), provider.getProviderType(),
                    0L, 0L));
        }
    }

    /** Unbinds providers that disappeared and binds desired ones that are missing. */
    private void reconcileBindings() {
        boolean trust = trustEnabled();
        Map<String, TerminalProviderConfig> desired = new LinkedHashMap<>();
        List<TerminalProviderConfig> installed = store.list();
        for (TerminalProviderConfig config : installed) {
            if (!config.enabled) {
                continue;
            }
            if (!trust && !ProviderSignature.sameSignature(context, config.packageName)) {
                Log.i(TAG, "skip untrusted terminal provider " + config.packageName);
                continue;
            }
            desired.put(config.id, config);
        }
        for (String id : new ArrayList<>(boundById.keySet())) {
            if (!desired.containsKey(id)) {
                boundById.remove(id);
                manager.unregisterAndUnbind(id);
            }
        }
        for (TerminalProviderConfig config : desired.values()) {
            TerminalIpcProvider existing = boundById.get(config.id);
            if (existing != null && existing.isBound()) {
                continue;
            }
            Log.i(TAG, "binding terminal provider " + config.packageName);
            BaseIpcProvider provider = manager.registerAndBind(ipcConfig(config));
            if (provider instanceof TerminalIpcProvider) {
                boundById.put(config.id, (TerminalIpcProvider) provider);
            }
        }
        pickConnectedProvider();
    }

    /** Promotes the first bound provider to active, or clears the active connection. */
    private void pickConnectedProvider() {
        TerminalIpcProvider current = active;
        if (current != null && current.isBound()) {
            return;
        }
        for (TerminalIpcProvider provider : boundById.values()) {
            if (provider.isBound()) {
                activate(provider);
                return;
            }
        }
        active = null;
        activeHome = "";
    }

    /** Sets the active provider and caches its home path for shell executions. */
    private void activate(TerminalIpcProvider provider) {
        active = provider;
        activeHome = readHome(provider);
    }

    /** Reads the user's trust-all-extension-signatures security setting. */
    private boolean trustEnabled() {
        return Boolean.parseBoolean(settings.get(
                UiPreferences.Field.TRUST_EXTENSION_SIGNATURES.key, "false"));
    }

    /** Converts a persisted config into the {@code :ipc} manager binding config. */
    private static IpcProviderConfig ipcConfig(TerminalProviderConfig config) {
        return IpcProviderConfig.builder()
                .id(config.id)
                .enabled(config.enabled)
                .providerType(config.providerType)
                .name(config.name)
                .packageName(config.packageName)
                .serviceClass(config.serviceClass)
                .build();
    }

    /** Best-effort home path read; empty when the provider call fails. */
    private static String readHome(TerminalIpcProvider provider) {
        try {
            return provider.getHomePath();
        } catch (Exception ignored) {
            return "";
        }
    }

    /** Message or simple class name of a throwable, for user-presentable errors. */
    private static String detail(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }
}
