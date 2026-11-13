package cn.lineai.runtime;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.model.TerminalProviderConfig;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.data.api.TerminalProviderStore;
import cn.lineai.feature.settings.McpPreferences;
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
final class TerminalProviderGateway implements IpcProviderStateListener {

    static final int STATE_NONE = 0;
    static final int STATE_DISCONNECTED = 1;
    static final int STATE_CONNECTED = 2;

    private static final int PROBE_TIMEOUT_MS = 20_000;
    private static final String TAG = "TerminalProviderGateway";

    private final Context context;
    private final SettingsStore settings;
    private final TerminalProviderStore store;
    private final IpcProviderManager manager;
    private final IpcProviderScanner scanner = new IpcProviderScanner();
    private final Map<String, TerminalIpcProvider> boundById = new LinkedHashMap<>();

    private volatile TerminalIpcProvider active;
    private volatile String activeHome = "";

    TerminalProviderGateway(Context context, SettingsStore settings, TerminalProviderStore store) {
        this.context = context.getApplicationContext();
        this.settings = settings;
        this.store = store;
        this.manager = new IpcProviderManager(this.context);
        this.manager.addStateListener(this);
    }

    void start() {
        refresh();
    }

    void shutdown() {
        manager.removeStateListener(this);
        manager.unregisterAll();
        boundById.clear();
        active = null;
        activeHome = "";
    }

    /** Discovers trusted providers, adds missing ones and reconciles bindings. Idempotent. */
    void refresh() {
        autoDiscover();
        reconcileBindings();
    }

    boolean terminalMode() {
        return McpPreferences.MODE_TERMINAL_PROVIDER.equals(new McpPreferences(settings).mode());
    }

    boolean trusted(String packageName) {
        return trustEnabled() || ProviderSignature.sameSignature(context, packageName);
    }

    int state() {
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

    String providerName() {
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

    String home() {
        return activeHome;
    }

    String execute(String command, int timeoutMs) {
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
    String probe() {
        return execute("uname -a; id; cat /etc/os-release | head -2; pwd", PROBE_TIMEOUT_MS);
    }

    /** Terminal provider background shell (AIDL v2); every call resolves the active provider. */
    BackgroundShellRegistry.ProviderBackground providerBackground() {
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
                }
            }
        };
    }

    @Override
    public void onStateChanged(BaseIpcProvider provider, IpcProviderConnectionState newState,
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

    private void activate(TerminalIpcProvider provider) {
        active = provider;
        activeHome = readHome(provider);
    }

    private boolean trustEnabled() {
        return Boolean.parseBoolean(settings.get(
                UiPreferences.Field.TRUST_EXTENSION_SIGNATURES.key, "false"));
    }

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

    private static String readHome(TerminalIpcProvider provider) {
        try {
            return provider.getHomePath();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String detail(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }
}
