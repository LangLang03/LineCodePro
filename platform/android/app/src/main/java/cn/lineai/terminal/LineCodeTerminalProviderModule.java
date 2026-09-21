package cn.lineai.terminal;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.RemoteException;

import cn.lineai.ipc.terminal.ITerminalProviderCallback;
import cn.lineai.ipc.terminal.ITerminalProviderService;

import org.huxerui.HuxerUIPlatformChannel;
import org.huxerui.HuxerUIPlatformModule;
import org.huxerui.PlatformPayload;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Android-only adapter for LineCode terminal-provider discovery and AIDL calls. */
public final class LineCodeTerminalProviderModule
        implements HuxerUIPlatformModule.Factory {
    private static final String ACTION = "cn.lineai.action.IPC_TERMINAL_PROVIDER";
    private static final String PERMISSION = "cn.lineai.permission.IPC_TERMINAL_PROVIDER";

    @Override
    public HuxerUIPlatformModule create(
            Context context,
            PlatformPayload options,
            HuxerUIPlatformChannel.Events events) {
        options.requireNull();
        return new Module(context.getApplicationContext());
    }

    private static final class Module implements HuxerUIPlatformModule {
        private interface Invocation {
            HuxerUIPlatformChannel.Cancellation invoke(
                    PlatformPayload arguments,
                    HuxerUIPlatformChannel.Result result);
        }

        private interface RemoteOperation {
            PlatformPayload invoke(ITerminalProviderService service) throws RemoteException;
        }

        private static final HuxerUIPlatformChannel.Cancellation NO_CANCELLATION = () -> { };
        private static final long BIND_TIMEOUT_SECONDS = 8L;
        private final Context context;
        private final ExecutorService executor = Executors.newCachedThreadPool();
        private final Map<String, Invocation> invocations = new HashMap<>();

        Module(Context context) {
            this.context = context;
            invocations.put("scan", this::scan);
            invocations.put("executeShell", (arguments, result) ->
                    remote(arguments, result, service -> executeShell(service, arguments)));
            invocations.put("readFile", (arguments, result) ->
                    remote(arguments, result, service -> PlatformPayload.bytes(
                            service.readFile(arguments.requireField("path").requireString()))));
            invocations.put("writeFile", (arguments, result) ->
                    remote(arguments, result, service -> requireTrue(
                            service.writeFile(
                                    arguments.requireField("path").requireString(),
                                    arguments.requireField("data").requireBytes()),
                            "Terminal provider could not write the file")));
            invocations.put("deleteFile", (arguments, result) ->
                    remote(arguments, result, service -> requireTrue(
                            service.deleteFile(
                                    arguments.requireField("path").requireString()),
                            "Terminal provider could not delete the file")));
            invocations.put("listDirectory", (arguments, result) ->
                    remote(arguments, result, service -> PlatformPayload.string(
                            service.listDirDetailed(
                                    arguments.requireField("path").requireString()))));
            invocations.put("providerInfo", (arguments, result) ->
                    remote(arguments, result, Module::providerInfo));
            invocations.put("fileExists", (arguments, result) ->
                    remote(arguments, result, service -> PlatformPayload.booleanValue(
                            service.fileExists(
                                    arguments.requireField("path").requireString()))));
            invocations.put("fileSize", (arguments, result) ->
                    remote(arguments, result, service -> PlatformPayload.int64(
                            service.fileSize(
                                    arguments.requireField("path").requireString()))));
            invocations.put("readFileChunk", (arguments, result) ->
                    remote(arguments, result, service -> PlatformPayload.bytes(
                            service.readFileChunk(
                                    arguments.requireField("path").requireString(),
                                    arguments.requireField("offset").requireInt64(),
                                    Math.toIntExact(arguments.requireField("size").requireInt64())))));
            invocations.put("writeFileChunk", (arguments, result) ->
                    remote(arguments, result, service -> requireTrue(
                            service.writeFileChunk(
                                    arguments.requireField("path").requireString(),
                                    arguments.requireField("offset").requireInt64(),
                                    arguments.requireField("data").requireBytes()),
                            "Terminal provider could not write the file chunk")));
            invocations.put("getFileSize", (arguments, result) ->
                    remote(arguments, result, service -> PlatformPayload.int64(
                            service.getFileSize(
                                    arguments.requireField("path").requireString()))));
        }

        @Override
        public HuxerUIPlatformChannel.Cancellation invoke(
                String method,
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            Invocation invocation = invocations.get(method);
            if (invocation == null) {
                result.fail(
                        "linecode/terminal-provider/not-implemented",
                        "Unknown terminal-provider method: " + method,
                        PlatformPayload.nullValue());
                return NO_CANCELLATION;
            }
            try {
                return invocation.invoke(arguments, result);
            } catch (RuntimeException error) {
                fail(result, error);
                return NO_CANCELLATION;
            }
        }

        @Override
        public void dispose() {
            invocations.clear();
            executor.shutdownNow();
        }

        private HuxerUIPlatformChannel.Cancellation scan(
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result) {
            arguments.requireNull();
            PackageManager packageManager = context.getPackageManager();
            List<ResolveInfo> services = packageManager.queryIntentServices(new Intent(ACTION), 0);
            List<PlatformPayload> values = new ArrayList<>();
            if (services != null) {
                for (ResolveInfo info : services) {
                    ServiceInfo service = info.serviceInfo;
                    if (service == null || !declaresPermission(packageManager, service.packageName)) {
                        continue;
                    }
                    CharSequence loadedLabel = info.loadLabel(packageManager);
                    Map<String, PlatformPayload> fields = new HashMap<>();
                    fields.put("packageName", PlatformPayload.string(service.packageName));
                    fields.put("serviceClass", PlatformPayload.string(service.name));
                    fields.put("label", PlatformPayload.string(
                            loadedLabel == null ? service.packageName : loadedLabel.toString()));
                    values.add(PlatformPayload.object(fields));
                }
            }
            result.complete(PlatformPayload.list(values));
            return NO_CANCELLATION;
        }

        private HuxerUIPlatformChannel.Cancellation remote(
                PlatformPayload arguments,
                HuxerUIPlatformChannel.Result result,
                RemoteOperation operation) {
            String packageName = arguments.requireField("packageName").requireString();
            String serviceClass = arguments.requireField("serviceClass").requireString();
            Future<?> future = executor.submit(() -> {
                RemoteBinding binding = null;
                try {
                    binding = new RemoteBinding(packageName, serviceClass);
                    ITerminalProviderService service = binding.connect();
                    if (!service.isAvailable()) {
                        throw new IllegalStateException("Terminal provider is unavailable");
                    }
                    result.complete(operation.invoke(service));
                } catch (RuntimeException | RemoteException error) {
                    fail(result, error);
                } finally {
                    if (binding != null) {
                        binding.close();
                    }
                }
            });
            return () -> future.cancel(true);
        }

        private PlatformPayload executeShell(
                ITerminalProviderService service,
                PlatformPayload arguments) throws RemoteException {
            StringBuffer stdout = new StringBuffer();
            StringBuffer stderr = new StringBuffer();
            AtomicReference<Integer> completedExit = new AtomicReference<>();
            ITerminalProviderCallback callback = new ITerminalProviderCallback.Stub() {
                @Override
                public void onOutput(String content) {
                    if (content != null) {
                        stdout.append(content);
                    }
                }

                @Override
                public void onError(String error) {
                    if (error != null) {
                        stderr.append(error);
                    }
                }

                @Override
                public void onComplete(int exitCode) {
                    completedExit.set(exitCode);
                }
            };
            int exitCode = service.executeShell(
                    arguments.requireField("command").requireString(),
                    arguments.requireField("cwd").requireString(),
                    arguments.requireField("timeoutMs").requireInt64(),
                    callback);
            Integer callbackExit = completedExit.get();
            Map<String, PlatformPayload> fields = new HashMap<>();
            fields.put("exitCode", PlatformPayload.int64(
                    callbackExit == null ? exitCode : callbackExit));
            fields.put("stdout", PlatformPayload.string(stdout.toString()));
            fields.put("stderr", PlatformPayload.string(stderr.toString()));
            return PlatformPayload.object(fields);
        }

        private static PlatformPayload providerInfo(ITerminalProviderService service)
                throws RemoteException {
            String providerType = service.getProviderType();
            String rawJson = service.getProviderInfo();
            String home = "";
            if (rawJson != null && !rawJson.isEmpty()) {
                try {
                    home = new JSONObject(rawJson).optString("home", "");
                } catch (Exception ignored) {
                    // Keep the raw provider response available to C++ even when
                    // an older third-party implementation returns non-JSON text.
                }
            }
            Map<String, PlatformPayload> fields = new HashMap<>();
            fields.put("providerType", PlatformPayload.string(
                    providerType == null ? "" : providerType));
            fields.put("rawJson", PlatformPayload.string(
                    rawJson == null ? "" : rawJson));
            fields.put("home", PlatformPayload.string(home));
            return PlatformPayload.object(fields);
        }

        private PlatformPayload requireTrue(boolean value, String message) {
            if (!value) {
                throw new IllegalStateException(message);
            }
            return PlatformPayload.nullValue();
        }

        private boolean declaresPermission(PackageManager packageManager, String packageName) {
            try {
                PackageInfo info = packageManager.getPackageInfo(
                        packageName, PackageManager.GET_PERMISSIONS);
                if (info.requestedPermissions == null) {
                    return false;
                }
                for (String permission : info.requestedPermissions) {
                    if (PERMISSION.equals(permission)) {
                        return true;
                    }
                }
                return false;
            } catch (PackageManager.NameNotFoundException ignored) {
                return false;
            }
        }

        private void fail(HuxerUIPlatformChannel.Result result, Throwable error) {
            String message = error.getMessage();
            result.fail(
                    error instanceof SecurityException
                            ? "linecode/terminal-provider/permission-denied"
                            : "linecode/terminal-provider/android-error",
                    message == null ? error.getClass().getSimpleName() : message,
                    PlatformPayload.nullValue());
        }

        private final class RemoteBinding implements ServiceConnection {
            private final ComponentName component;
            private final CountDownLatch connected = new CountDownLatch(1);
            private final AtomicReference<IBinder> binder = new AtomicReference<>();
            private boolean bound;

            RemoteBinding(String packageName, String serviceClass) {
                component = new ComponentName(packageName, serviceClass);
            }

            ITerminalProviderService connect() {
                Intent intent = new Intent(ACTION).setComponent(component);
                bound = context.bindService(intent, this, Context.BIND_AUTO_CREATE);
                if (!bound) {
                    throw new IllegalStateException(
                            "Could not bind terminal provider " + component.flattenToShortString());
                }
                try {
                    if (!connected.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "Timed out binding terminal provider "
                                        + component.flattenToShortString());
                    }
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Terminal provider binding was cancelled", error);
                }
                IBinder value = binder.get();
                if (value == null) {
                    throw new IllegalStateException(
                            "Terminal provider disconnected before use");
                }
                return ITerminalProviderService.Stub.asInterface(value);
            }

            void close() {
                if (!bound) {
                    return;
                }
                bound = false;
                try {
                    context.unbindService(this);
                } catch (RuntimeException ignored) {
                    // Android may already have removed a dead provider binding.
                }
            }

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                binder.set(service);
                connected.countDown();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                binder.set(null);
                connected.countDown();
            }

            @Override
            public void onNullBinding(ComponentName name) {
                binder.set(null);
                connected.countDown();
            }
        }
    }
}
