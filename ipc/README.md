# `:ipc` — Pluggable provider protocol for LineCode

> The `:ipc` Gradle module is a small Android library that defines the contract LineCode uses to talk to **third-party, in-process-isolated shell providers**. The bundled `:terminal-provider` is the reference implementation; you can write your own provider app and LineCode will auto-detect, bind, and route shell + file ops through it.

[English](README.md) · [中文](README_CN.md)

---

## Table of contents

1. [Why IPC?](#why-ipc)
2. [Architecture at a glance](#architecture-at-a-glance)
3. [Concepts](#concepts)
4. [Protocol surface](#protocol-surface)
5. [Permissions & security model](#permissions--security-model)
6. [Tutorial: build your own provider in 5 minutes](#tutorial-build-your-own-provider-in-5-minutes)
7. [Tutorial: consume a provider from the client side](#tutorial-consume-a-provider-from-the-client-side)
8. [Lifecycle, state machine & state listeners](#lifecycle-state-machine--state-listeners)
9. [Packaging & building](#packaging--building)
10. [Versioning & compatibility](#versioning--compatibility)
11. [Troubleshooting](#troubleshooting)
12. [API reference (cheat sheet)](#api-reference-cheat-sheet)

---

## Why IPC?

LineCode lets the model run shell commands and read / write files. On Android there are three ways to host that:

| Where shell runs | Trade-off |
| ---------------- | --------- |
| **In the app's own process** | Simplest. But every command runs with the app's full permissions, the app's UID, and direct access to the app's data. A buggy or compromised model can wipe user data. |
| **In Termux (user-space)** | Sandboxed in a separate package, but Termux is an external dependency the user has to install and grant `RUN_COMMAND` to. |
| **In a dedicated provider app via IPC** | A normal Android app you (or anyone) ships independently. It runs in **its own UID, its own data directory, its own process**. The caller (LineCode) can only invoke the methods exposed by AIDL, and only if it holds the right permission. |

The `:ipc` module makes option three trivial: define a new AIDL interface, ship a tiny app, and LineCode discovers it at runtime.

Two examples:

* **A privileged "root" provider** — runs commands as `root` via `su`, in a different UID than LineCode. Useful for system-modding workflows.
* **A Docker / sandbox provider** — runs every command inside a disposable container, so even if the model misbehaves the blast radius is a fresh container.
* **A read-only provider** — exposes only `listDir`, `readFile`, `fileExists` and rejects everything else.

The contract is the same for all of them.

---

## Architecture at a glance

```
┌─────────────────────────────────┐                                ┌─────────────────────────────────────┐
│  :app  (cn.lineai)              │                                │  :ipc library (cn.lineai.ipc)       │
│  ─────────────────────────────  │                                │  ─────────────────────────────────  │
│                                 │   addStateListener(...)        │                                     │
│  IpcProviderManager ◄───────────┼───────────── observes ─────────┤  IpcProviderStateListener            │
│   │                             │                                │  IpcProviderConnectionState          │
│   │ registerAndBind(config)     │   base class for clients       │  IpcProviderType                     │
│   │                             │                                │  IpcProviderConfig (immutable)       │
│   ▼                             │                                │  IpcProviderRegistry / Factory       │
│  BaseIpcProvider (abstract)     │                                │  IpcProviderScanner                  │
│   │                             │                                │  AbstractIpcProviderService (server) │
│   │ getProviderType()           │                                │  IpcServerExecutors                  │
│   │ requiresConfirmation()      │                                │                                     │
│   ▼                             │                                └────────────────▲────────────────────┘
│  TerminalIpcProvider (client)   │                                                 │
│   │ executeShell / readFile /   │                                                 │
│   │   writeFile / listDir / …   │                                                 │
│   │                             │                                                 │  shared AIDL contract
│   │  ┌──────────────────────┐   │   IPC over Binder (Android Service)            │  (compiled into both sides)
│   │  │  ITerminalProvider   │◄──┼─────────────────────────────────────────────────┘
│   │  │  Service.Stub.asInt.│   │
│   │  └──────────────────────┘   │
└─────────────────────────────────┘
                                                                                ┌─────────────────────────────────────┐
                                                                                │  Third-party provider app           │
                                                                                │  ─────────────────────────────────  │
                                                                                │  <service android:permission=…>      │
                                                                                │  ITerminalProviderService.Stub {    │
                                                                                │      executeShell, readFile, …      │
                                                                                │  }                                  │
                                                                                │  Shared thread pool:                │
                                                                                │  IpcServerExecutors.shared()        │
                                                                                └─────────────────────────────────────┘
```

The split:

* **`:app`** — owns the runtime. Builds `IpcProviderManager`, asks the `IpcProviderScanner` to discover installed providers, persists user choices in `IpcProviderRepository`, drives bind / unbind, and reacts to `IpcProviderStateListener` callbacks to keep the UI in sync.
* **`:ipc`** — owns the *protocol*. AIDL definitions, the abstract `BaseIpcProvider` (client side), the abstract `AbstractIpcProviderService` (server side), the connection state machine, the type / config / registry / scanner / factory.
* **Third-party provider app** — depends on `:ipc` (only the *abstract* `AbstractIpcProviderService` + the AIDL), implements a concrete `ITerminalProviderService.Stub`, exposes it through a `<service>` tag with the right `android:permission`. That's it.

---

## Concepts

| Concept | Lives in | What it is |
| ------- | -------- | ---------- |
| `IpcProviderType` | `:ipc` | A logical provider type (currently `TERMINAL`). Defines an `intent action` and a `permission` that the provider must declare on its `<service>`. New types register a new enum value + their AIDL package. |
| `IpcProviderConfig` | `:ipc` | Immutable value object (id, enabled, providerType, name, packageName, serviceClass, createdAt, updatedAt) — the *address* of an installed provider. Built via `IpcProviderConfig.builder()`. |
| `IpcProviderStateListener` | `:ipc` | Observer callback `(BaseIpcProvider, IpcProviderConnectionState, Throwable) -> void`. |
| `IpcProviderConnectionState` | `:ipc` | Enum: `DISCONNECTED`, `CONNECTING`, `CONNECTED`, `FAILED`. |
| `BaseIpcProvider` | `:ipc` | Client-side abstract base class. Subclasses declare `getProviderType()` and expose typed methods (e.g. `executeShell`, `readFile`). The base handles `bind / unbind`, the state machine, and the listener fan-out. |
| `IpcProviderFactory` | `:ipc` | `BaseIpcProvider create(IpcProviderConfig config)`. Lets the manager create subclasses without knowing their concrete types. |
| `IpcProviderRegistry` | `:ipc` | Singleton registry of `IpcProviderFactory` per `IpcProviderType`. Defaults to `TerminalProviderServiceFactory`. OCP-friendly: add a new type, register a factory, the manager works. |
| `IpcProviderScanner` | `:ipc` | Uses `PackageManager.queryIntentServices(intent)` to find every app that has a `<service>` with the action for the requested type **and** has the matching `<uses-permission>`. Returns `List<ScannedProvider>`. |
| `IpcProviderManager` | `:ipc` | The runtime façade: `registerAndBind`, `unregisterAndUnbind`, `getProvider(id|type)`, `addStateListener`, `removeStateListener`. Aggregates state changes from all providers and forwards to a single global listener list. |
| `AbstractIpcProviderService` | `:ipc` | Server-side abstract `Service` base class. Subclasses implement `createBinder()` and (optionally) `onProviderUnbind` / `onProviderDestroy`. |
| `IpcServerExecutors` | `:ipc` | Process-wide shared thread pool for server-side work. Daemon threads; **do not shut it down in `onDestroy`**. |
| `IpcPermission` | `:ipc` | Constant holder. `IpcPermission.TERMINAL_PROVIDER = "cn.lineai.permission.IPC_TERMINAL_PROVIDER"`. |

---

## Protocol surface

### AIDL — `IBaseIpcService`

Every provider implements this. It's a tiny handshake.

```aidl
// ipc/src/main/aidl/cn/lineai/ipc/IBaseIpcService.aidl
package cn.lineai.ipc;

interface IBaseIpcService {
    String getProviderType();   // must equal IpcProviderType.TERMINAL.getId() for terminal providers
    String getProviderInfo();   // JSON blob — see below
    boolean isAvailable();      // fast liveness probe; false makes the scanner grey out the entry
}
```

`getProviderInfo()` returns a JSON string. The terminal convention (recognised by the client) is:

```json
{
  "name": "Android Shell Terminal Provider",
  "version": "1.0",
  "shell": "/system/bin/sh",
  "home": "/data/user/0/cn.lineai.terminalprovider/files",
  "capabilities": ["executeShell", "readFile", "writeFile", "deleteFile",
                    "listDir", "fileExists", "fileSize"]
}
```

* `name` and `version` are shown in the LineCode UI.
* `shell` is the path to the shell binary to invoke for `executeShell`.
* `home` is the absolute path the provider advertises as its "workspace root" (LineCode's `TerminalIpcProvider.getHomePath()` reads this back to populate the project picker).
* `capabilities` is an informational list of AIDL methods the provider implements.

### AIDL — `ITerminalProviderService`

The terminal-specific AIDL, compiled into both `:ipc` and the provider app. The client side receives these calls as `BaseIpcProvider.getService()` (cast to `ITerminalProviderService.Stub.asInterface(binder)`) and exposes typed Java methods (`executeShell`, `readFile`, `writeFile`, `listDirDetailed`, …).

```aidl
// ipc/src/main/aidl/cn/lineai/ipc/terminal/ITerminalProviderService.aidl
package cn.lineai.ipc.terminal;
import cn.lineai.ipc.terminal.ITerminalProviderCallback;

interface ITerminalProviderService {
    // inherited from IBaseIpcService
    String getProviderType();
    String getProviderInfo();
    boolean isAvailable();

    // shell
    int executeShell(String command, String cwd, long timeoutMs,
                     ITerminalProviderCallback callback);

    // SFTP-style file ops
    byte[] readFile(String path);
    boolean writeFile(String path, in byte[] data);
    boolean deleteFile(String path);
    String[] listDir(String path);
    boolean fileExists(String path);
    long fileSize(String path);

    // chunked for large files
    byte[] readFileChunk(String path, long offset, int size);
    boolean writeFileChunk(String path, long offset, in byte[] data);
    long getFileSize(String path);

    // structured listing
    String listDirDetailed(String path);
}
```

```aidl
// ipc/src/main/aidl/cn/lineai/ipc/terminal/ITerminalProviderCallback.aidl
package cn.lineai.ipc.terminal;
interface ITerminalProviderCallback {
    void onOutput(String content);
    void onError(String error);
    void onComplete(int exitCode);
}
```

Implementers do **not** need to add anything to the protocol to support new shell semantics — just route them through `executeShell`. If you want a richer interface, define a new AIDL under your own package and a new `IpcProviderType` enum value, then register a new factory in `IpcProviderRegistry`.

---

## Permissions & security model

The trust boundary is enforced **at the Android framework layer**, not in app code:

1. The provider app declares the permission in its manifest:

   ```xml
   <uses-permission android:name="cn.lineai.permission.IPC_TERMINAL_PROVIDER" />

   <service
       android:name=".TerminalProviderService"
       android:exported="true"
       android:permission="cn.lineai.permission.IPC_TERMINAL_PROVIDER">
       <intent-filter>
           <action android:name="cn.lineai.action.IPC_TERMINAL_PROVIDER" />
       </intent-filter>
   </service>
   ```

2. LineCode declares the permission and asks for it:

   ```xml
   <permission
       android:name="cn.lineai.permission.IPC_TERMINAL_PROVIDER"
       android:protectionLevel="normal"
       android:description="@string/permission_ipc_terminal_provider_desc" />
   <uses-permission android:name="cn.lineai.permission.IPC_TERMINAL_PROVIDER" />

   <queries>
       <intent>
           <action android:name="cn.lineai.action.IPC_TERMINAL_PROVIDER" />
       </intent>
   </queries>
   ```

3. At `bindService` time, Android checks the caller (LineCode) holds `IPC_TERMINAL_PROVIDER`. If not, the bind is rejected with `SecurityException`. The provider never has to write a permission check of its own.

4. The scanner additionally verifies the package actually **requests** the permission (`PackageManager.GET_PERMISSIONS`), so the picker can grey out providers that wouldn't survive a bind.

**Rule of thumb:** if the permission string is in `IpcPermission.*`, use it as `android:permission` on the service tag. If it's something else, treat the provider as untrusted.

> Don't skip the `<queries>` block — on Android 11+ `queryIntentServices` only returns installed services whose intent the app has explicitly declared an interest in.

---

## Tutorial: build your own provider in 5 minutes

We're going to ship a `cn.lineai.myprovider` app that, on every `executeShell` call, just logs the command and returns exit code `0`. After that, the model can call `shell_execute` and it'll be routed here.

### Step 1 — Create the module

Add to `settings.gradle.kts`:

```kotlin
include(":my-provider")
```

`my-provider/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cn.lineai.myprovider"
    compileSdk = 36

    defaultConfig {
        applicationId = "cn.lineai.myprovider"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation(project(":ipc"))
}
```

### Step 2 — Manifest

`my-provider/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="cn.lineai.permission.IPC_TERMINAL_PROVIDER" />

    <application
        android:allowBackup="false"
        android:label="My Provider"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".MyProviderService"
            android:exported="true"
            android:permission="cn.lineai.permission.IPC_TERMINAL_PROVIDER">
            <intent-filter>
                <action android:name="cn.lineai.action.IPC_TERMINAL_PROVIDER" />
            </intent-filter>
        </service>
    </application>
</manifest>
```

Note: the `android:permission` is what stops random apps from binding. Without it, anything can call you.

### Step 3 — Copy the AIDL

The `:ipc` library already declares the AIDL contracts you need. You have two options:

* **Option A — depend on the AIDL via `:ipc`.** Already done. The AIDL gets compiled into your APK on its own.
* **Option B — vendor the AIDL files** if you want a fully self-contained app (e.g. you don't want to depend on `:ipc`):

  ```
  my-provider/src/main/aidl/cn/lineai/ipc/IBaseIpcService.aidl
  my-provider/src/main/aidl/cn/lineai/ipc/terminal/ITerminalProviderService.aidl
  my-provider/src/main/aidl/cn/lineai/ipc/terminal/ITerminalProviderCallback.aidl
  ```

  Same package, same contents, copied from the `:ipc` source tree. AGP compiles them with `buildFeatures { aidl = true }`.

### Step 4 — The Service

```java
package cn.lineai.myprovider;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import cn.lineai.ipc.IBaseIpcService;          // generated from the AIDL
import cn.lineai.ipc.terminal.ITerminalProviderCallback;
import cn.lineai.ipc.terminal.ITerminalProviderService;
import cn.lineai.ipc.service.AbstractIpcProviderService;
import cn.lineai.ipc.service.IpcServerExecutors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class MyProviderService extends AbstractIpcProviderService {

    private static final String TAG = "MyProvider";
    private static final String SHELL = "/system/bin/sh";

    // Use the shared thread pool — never spawn your own.
    private final ExecutorService executor = IpcServerExecutors.shared();

    @Override
    protected IBinder createBinder() {
        return new ITerminalProviderService.Stub() {

            @Override
            public String getProviderType() {
                return "terminal";
            }

            @Override
            public String getProviderInfo() {
                JSONObject info = new JSONObject();
                try {
                    info.put("name", "My Provider");
                    info.put("version", "0.1.0");
                    info.put("shell", SHELL);
                    info.put("home", getFilesDir().getAbsolutePath());
                    info.put("capabilities", new JSONArray()
                            .put("executeShell")
                            .put("readFile")
                            .put("listDir")
                            .put("fileExists")
                            .put("fileSize"));
                } catch (Exception ignored) {
                }
                return info.toString();
            }

            @Override
            public boolean isAvailable() {
                return new File(SHELL).exists();
            }

            @Override
            public int executeShell(String command, String cwd, long timeoutMs,
                                    ITerminalProviderCallback callback) {
                Log.i(TAG, "executeShell: " + command);
                File workingDir = (cwd != null && !cwd.isEmpty())
                        ? new File(cwd)
                        : getFilesDir();
                if (!workingDir.exists()) workingDir = getFilesDir();
                File finalCwd = workingDir;

                Future<Integer> future = executor.submit(() -> {
                    try {
                        Process p = new ProcessBuilder(SHELL, "-c", command)
                                .directory(finalCwd)
                                .redirectErrorStream(true)
                                .start();
                        // drain stdout to callback
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = p.getInputStream().read(buf)) > 0) {
                            if (callback != null) {
                                callback.onOutput(new String(buf, 0, n));
                            }
                        }
                        int code = p.waitFor();
                        if (callback != null) callback.onComplete(code);
                        return code;
                    } catch (Exception e) {
                        if (callback != null) {
                            try { callback.onError(e.getMessage()); } catch (RemoteException ignored) {}
                        }
                        return -1;
                    }
                });

                try {
                    long effective = timeoutMs > 0 ? timeoutMs : 30000L;
                    return future.get(effective, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    future.cancel(true);
                    if (callback != null) {
                        try { callback.onError("timeout"); } catch (RemoteException ignored) {}
                    }
                    return -2;
                }
            }

            // Stub the rest of the interface — return empty / false.
            @Override public byte[] readFile(String path)         { return new byte[0]; }
            @Override public boolean writeFile(String p, byte[] d){ return false; }
            @Override public boolean deleteFile(String path)      { return false; }
            @Override public String[] listDir(String path)        { return new String[0]; }
            @Override public boolean fileExists(String path)      { return false; }
            @Override public long fileSize(String path)           { return -1; }
            @Override public byte[] readFileChunk(String p, long o, int s) { return new byte[0]; }
            @Override public boolean writeFileChunk(String p, long o, byte[] d) { return false; }
            @Override public long getFileSize(String path)        { return -1; }
            @Override public String listDirDetailed(String path)  { return "[]"; }
        };
    }

    @Override
    protected void onProviderDestroy() {
        // Don't shut down IpcServerExecutors — it's process-scoped and shared.
        Log.i(TAG, "MyProviderService destroyed");
    }
}
```

That's the entire provider. It exposes the AIDL contract. Now build & install:

```bash
./gradlew :my-provider:assembleDebug
adb install -r my-provider/build/outputs/apk/debug/my-provider-debug.apk
```

### Step 5 — Enable it in LineCode

1. Open LineCode → **Settings → MCP execution mode → Terminal Provider → Add provider**.
2. The scanner picks up `cn.lineai.myprovider` automatically (it has the right `<service>` + `<uses-permission>`). You can also tap **Scan** to refresh.
3. Enable the entry. LineCode calls `bindService`, the state machine goes `DISCONNECTED → CONNECTING → CONNECTED`, the listener fires, the project path is set to your provider's `home`, and the file tree / attachment picker / model shell tool all start routing through your app.

To verify, watch logcat:

```bash
adb logcat -s BaseIpcProvider AbstractIpcProviderService MyProvider
# Look for: "BaseIpcProvider 状态迁移: CONNECTING -> CONNECTED"
```

### Step 6 — Talk to it

In the LineCode chat, ask the model to run a command:

> "Run `uname -a` in the terminal provider and tell me what the kernel version is."

The model will call `shell_execute`. LineCode will route it through `MyProviderService.executeShell(...)`, which logs the command, returns the kernel version, and the model relays it back.

---

## Tutorial: consume a provider from the client side

If you want to write *your own* client (e.g. an integration test, or a different app that wants to talk to terminal providers), here's the minimal client pattern.

```java
// Build a config from a known package + service.
IpcProviderConfig config = IpcProviderConfig.builder()
        .enabled(true)
        .providerType(IpcProviderType.TERMINAL.getId())
        .name("My Provider")
        .packageName("cn.lineai.myprovider")
        .serviceClass("cn.lineai.myprovider.MyProviderService")
        .build();

// Set up a manager.
IpcProviderManager manager = new IpcProviderManager(context);
manager.addStateListener((provider, state, cause) -> {
    Log.i("Client", "state = " + state);
    if (state == IpcProviderConnectionState.CONNECTED) {
        // ready to call
    } else if (state == IpcProviderConnectionState.FAILED) {
        Log.e("Client", "bind failed", cause);
    }
});

// Bind. The listener fires asynchronously.
BaseIpcProvider base = manager.registerAndBind(config);
if (base instanceof TerminalIpcProvider) {
    TerminalIpcProvider term = (TerminalIpcProvider) base;
    try {
        String home = term.getHomePath();
        Log.i("Client", "provider home = " + home);
        // term.executeShell(...), term.readFile(...), etc.
    } catch (RemoteException e) {
        Log.e("Client", "IPC call failed", e);
    }
}

// Later, tear down.
manager.unregisterAndUnbind(config.getId());
```

`TerminalIpcProvider` is the typed client for terminal providers — it lives in `:ipc/src/main/java/cn/lineai/ipc/terminal/TerminalIpcProvider.java` and is the model your code should follow if you write a new provider type.

---

## Lifecycle, state machine & state listeners

`BaseIpcProvider.bind(context)` walks the state machine:

```
            bind()
DISCONNECTED ──────► CONNECTING ──────► CONNECTED
       ▲                  │                  │
       │                  └── bind returned  │
       │                     false → FAILED   │
       │                                      │
       │     unbind() / onServiceDisconnected │
       └──────────────────────────────────────┘
```

Every transition is published to:

1. **Provider-level listeners** — `provider.addStateListener(l)`, useful for toolcards that need to render bind progress.
2. **Global listeners** — `manager.addStateListener(l)`, fed via the `providerStateForwarder`. `:app` registers exactly one of these on construction; that's how the project path, the file tree, the auto-rebind on cold start, and the `DISCONNECTED/FAILED` cleanup hook all work.

The recommended pattern for new provider types:

```java
private final IpcProviderStateListener myListener = (provider, state, cause) -> {
    switch (state) {
        case CONNECTED:
            applyRemoteWorkspace(provider);
            break;
        case DISCONNECTED:
        case FAILED:
            if (isRelevant(provider)) {
                clearRemoteWorkspace();
            }
            break;
    }
};

// In ctor: manager.addStateListener(myListener);
// In destroy: manager.removeStateListener(myListener);
```

`IpcProviderManager.removeStateListener` uses `List.remove(Object)` — make sure you compare the **same** listener reference. The reference equality pitfall is the most common source of "listener didn't unregister" bugs; store the lambda in a field, as `:app`'s `MainCoordinator.ipcStateListener` does.

---

## Packaging & building

`./gradlew :ipc:assembleDebug` builds the library. `./gradlew :terminal-provider:assembleDebug` builds the reference provider APK. `./gradlew :app:assembleDebug` builds LineCode.

Once you have a provider APK of your own:

```bash
# 1. Install both APKs.
adb install -r linecode/app/build/outputs/apk/debug/app-debug.apk
adb install -r my-provider/build/outputs/apk/debug/my-provider-debug.apk

# 2. Open LineCode. The scanner should find your provider in
#    Settings → MCP execution mode → Terminal Provider → Scan.
```

Provider APKs are normal signed Android APKs. For release builds use a private keystore — the `:terminal-provider` module ships a `validateReleaseSigning` task that refuses to sign release artifacts with the debug certificate; replicate this in your own module.

### Manifest essentials checklist

- [ ] `<uses-permission android:name="cn.lineai.permission.IPC_TERMINAL_PROVIDER" />` in the provider app
- [ ] `<service android:exported="true" android:permission="cn.lineai.permission.IPC_TERMINAL_PROVIDER">`
- [ ] `<intent-filter><action android:name="cn.lineai.action.IPC_TERMINAL_PROVIDER" /></intent-filter>` on the service
- [ ] (Caller side) `<permission>` + `<uses-permission>` + `<queries><intent><action .../></intent></queries>`

---

## Versioning & compatibility

* The AIDL interface under `ipc/src/main/aidl/` is the **stable** surface. Adding a new method is backwards compatible (existing providers just don't implement it; the client must tolerate `NoSuchMethodError` at the Stub level, or check for the method's existence).
* Removing or renaming a method is a breaking change — bump the module's `version` and call it out in the changelog.
* `getProviderInfo()` is JSON — add new fields freely, never repurpose or remove existing ones.
* `IpcProviderType` enum values are stable per protocol version. To add a new protocol, add a new enum value with a new `intentAction` and `permissionName`, then ship matching AIDL under a new package.

---

## Troubleshooting

**The scanner doesn't find my provider.**
Check, in order: (1) the `<service>` tag has the right `intent-filter`; (2) the `android:exported` is `true`; (3) your `<uses-permission>` matches the action's permission; (4) you have the `<queries>` block in the *caller*'s manifest; (5) the install actually succeeded (`adb shell pm list packages | grep myprovider`).

**Bind throws `SecurityException`.**
The caller (LineCode) doesn't hold the `IPC_TERMINAL_PROVIDER` permission — most likely the permission is `protectionLevel="signature"` instead of `"normal"`, or it's missing from the caller's manifest.

**`State is FAILED, cause = IllegalStateException("bindService 返回 false")`.**
The intent didn't resolve. Check that `packageName` and `serviceClass` in the config match the installed provider exactly, and that the `<service>` is exported.

**Provider runs but model can't find the home path.**
Make sure `getProviderInfo()` includes `"home": getFilesDir().getAbsolutePath()`. The client reads it back via `TerminalIpcProvider.getHomePath()`.

**Threads piling up / app stuck on shutdown.**
Don't `newCachedThreadPool` inside the service. Use `IpcServerExecutors.shared()` — it returns a daemon pool, so a stuck shell call won't block the app from exiting.

**Tests.**
The unit tests under `app/src/test/java/cn/lineai/...` cover the client-side state machine and the registry. Server-side AIDL methods are best tested by running both the provider and the client on a connected device with `adb logcat -s ...`.

---

## API reference (cheat sheet)

### Client side

```java
// Build a config
IpcProviderConfig config = IpcProviderConfig.builder()
        .id("ipc_myprovider_1")               // optional; auto-generated UUID if blank
        .enabled(true)
        .providerType(IpcProviderType.TERMINAL.getId())
        .name("My Provider")
        .packageName("cn.lineai.myprovider")
        .serviceClass("cn.lineai.myprovider.MyProviderService")
        .build();

// Manager lifecycle
IpcProviderManager manager = new IpcProviderManager(context);
manager.addStateListener(listener);
BaseIpcProvider provider = manager.registerAndBind(config);
manager.unregisterAndUnbind(config.getId());
manager.unregisterAll();
manager.removeStateListener(listener);

// Discovery
IpcProviderScanner scanner = new IpcProviderScanner();
List<ScannedProvider> hits = scanner.scan(context, IpcProviderType.TERMINAL);

// Registry (e.g. for adding a new type)
IpcProviderRegistry.getInstance().register(new MyProviderFactory());

// State polling
IpcProviderConnectionState state = provider.getConnectionState();
boolean bound = provider.isBound();
IpcProviderConfig cfg = provider.getConfig();
```

### Server side

```java
// Subclass AbstractIpcProviderService
public final class MyProviderService extends AbstractIpcProviderService {
    @Override
    protected IBinder createBinder() {
        return new ITerminalProviderService.Stub() {
            // override AIDL methods
        };
    }
    // optional hooks
    @Override protected void onProviderUnbind(Intent intent) { /* ... */ }
    @Override protected void onProviderDestroy()             { /* ... */ }
}

// Shared thread pool
ExecutorService pool = IpcServerExecutors.shared(); // daemon threads; do NOT shutdown
```

### Manifest (provider side)

```xml
<uses-permission android:name="cn.lineai.permission.IPC_TERMINAL_PROVIDER" />

<service
    android:name=".MyProviderService"
    android:exported="true"
    android:permission="cn.lineai.permission.IPC_TERMINAL_PROVIDER">
    <intent-filter>
        <action android:name="cn.lineai.action.IPC_TERMINAL_PROVIDER" />
    </intent-filter>
</service>
```

### Manifest (caller side)

```xml
<permission
    android:name="cn.lineai.permission.IPC_TERMINAL_PROVIDER"
    android:protectionLevel="normal" />

<uses-permission android:name="cn.lineai.permission.IPC_TERMINAL_PROVIDER" />

<queries>
    <intent>
        <action android:name="cn.lineai.action.IPC_TERMINAL_PROVIDER" />
    </intent>
</queries>
```

That's the whole surface. Three AIDL methods, one abstract class on the client, one abstract class on the server, one permission, one intent action, one shared thread pool.
