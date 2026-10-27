# `:ipc` —— LineCode 的可插拔 Provider 协议

> `:ipc` 是一个很小的 Android 库，定义了 LineCode 与**第三方、进程隔离**的 Shell Provider 之间的契约。仓库自带的 `:terminal-provider` 是参考实现；你也可以自己写一个 Provider App，LineCode 会在运行时自动发现、绑定、并把 Shell + 文件操作路由过去。

[English](README.md) · [中文](README_CN.md)

---

## 目录

1. [为什么用 IPC？](#为什么用-ipc)
2. [架构一览](#架构一览)
3. [核心概念](#核心概念)
4. [协议接口](#协议接口)
5. [权限与安全模型](#权限与安全模型)
6. [5 分钟写一个自己的 Provider](#5-分钟写一个自己的-provider)
7. [从客户端侧调用 Provider](#从客户端侧调用-provider)
8. [生命周期、状态机与状态监听](#生命周期状态机与状态监听)
9. [打包与构建](#打包与构建)
10. [版本与兼容性](#版本与兼容性)
11. [常见问题排查](#常见问题排查)
12. [API 速查](#api-速查)

---

## 为什么用 IPC？

LineCode 让模型跑 Shell、读 / 写文件。在 Android 上这件事有三种做法：

| Shell 跑在哪里 | 取舍 |
| -------------- | ---- |
| **App 自己的进程** | 最简单。但每条命令都拿着 App 的全部权限、UID 与数据目录，模型一旦出 Bug / 被劫持就能清空用户数据。 |
| **Termux（用户空间）** | 隔一个包做沙箱，但 Termux 是外部依赖，得让用户先装、再授 `RUN_COMMAND`。 |
| **专门的 Provider App 走 IPC** | 普通 Android App，独立 **UID、独立数据目录、独立进程**。调用方（LineCode）只能调到 AIDL 暴露的方法，且必须持有对应权限。 |

`:ipc` 让第三种方案变得简单：定义一个 AIDL 接口，发布一个很小的 App，LineCode 就会在运行时发现它。

举几个实际用途：

* **特权"root" Provider** —— 通过 `su` 以 `root` 跑命令，UID 与 LineCode 完全不同。适合做系统魔改类工作流。
* **Docker / 沙箱 Provider** —— 每条命令丢进一次性容器，模型乱来也只是一个容器。
* **只读 Provider** —— 只暴露 `listDir` / `readFile` / `fileExists`，其他一律拒绝。

不管什么场景，协议都是同一份。

---

## 架构一览

```
┌─────────────────────────────────┐                                ┌─────────────────────────────────────┐
│  :app  (cn.lineai)              │                                │  :ipc 库 (cn.lineai.ipc)            │
│  ─────────────────────────────  │                                │  ─────────────────────────────────  │
│                                 │   addStateListener(...)        │                                     │
│  IpcProviderManager ◄───────────┼─────────── 观察 ────────────────┤  IpcProviderStateListener            │
│   │                             │                                │  IpcProviderConnectionState          │
│   │ registerAndBind(config)     │   客户端抽象基类               │  IpcProviderType                     │
│   │                             │                                │  IpcProviderConfig (不可变)          │
│   ▼                             │                                │  IpcProviderRegistry / Factory       │
│  BaseIpcProvider (abstract)     │                                │  IpcProviderScanner                  │
│   │                             │                                │  AbstractIpcProviderService (server) │
│   │ getProviderType()           │                                │  IpcServerExecutors                  │
│   │ requiresConfirmation()      │                                │                                     │
│   ▼                             │                                └────────────────▲────────────────────┘
│  TerminalIpcProvider (client)   │                                                 │
│   │ executeShell / readFile /   │                                                 │  共享 AIDL 契约
│   │   writeFile / listDir / …   │                                                 │  (同时编进两端)
│   │                             │                                                 │
│   │  ┌──────────────────────┐   │   通过 Binder 走 IPC (Android Service)          │
│   │  │  ITerminalProvider   │◄──┼─────────────────────────────────────────────────┘
│   │  │  Service.Stub.asInt.│   │
│   │  └──────────────────────┘   │
└─────────────────────────────────┘
                                                                                ┌─────────────────────────────────────┐
                                                                                │  第三方 Provider App                │
                                                                                │  ─────────────────────────────────  │
                                                                                │  <service android:permission=…>      │
                                                                                │  ITerminalProviderService.Stub {    │
                                                                                │      executeShell, readFile, …      │
                                                                                │  }                                  │
                                                                                │  共享线程池：                        │
                                                                                │  IpcServerExecutors.shared()        │
                                                                                └─────────────────────────────────────┘
```

三方职责：

* **`:app`** —— 运行时。构造 `IpcProviderManager`、用 `IpcProviderScanner` 发现已安装的 Provider、把用户选择持久化到 `IpcProviderRepository`、驱动 bind / unbind、并响应 `IpcProviderStateListener` 回调保持 UI 同步。
* **`:ipc`** —— 协议本身。AIDL 定义、抽象 `BaseIpcProvider`（客户端）、抽象 `AbstractIpcProviderService`（服务端）、状态机、类型 / 配置 / 注册表 / 扫描器 / 工厂。
* **第三方 Provider App** —— 只依赖 `:ipc`（具体地，依赖抽象的 `AbstractIpcProviderService` 和 AIDL），实现一个具体的 `ITerminalProviderService.Stub`，用一个带正确 `android:permission` 的 `<service>` 暴露出去。就这些。

---

## 核心概念

| 概念 | 在哪 | 是什么 |
| ---- | ---- | ------ |
| `IpcProviderType` | `:ipc` | 一个逻辑 Provider 类型（目前有 `TERMINAL`）。定义一个 `intent action` 和 Provider 必须在 `<service>` 上声明的 `permission`。新增类型 = 新增一个枚举值 + 自己的 AIDL 包。 |
| `IpcProviderConfig` | `:ipc` | 不可变值对象（id、enabled、providerType、name、packageName、serviceClass、createdAt、updatedAt），即"已安装 Provider 的地址"。通过 `IpcProviderConfig.builder()` 构造。 |
| `IpcProviderStateListener` | `:ipc` | 观察者回调 `(BaseIpcProvider, IpcProviderConnectionState, Throwable) -> void`。 |
| `IpcProviderConnectionState` | `:ipc` | 枚举：`DISCONNECTED`、`CONNECTING`、`CONNECTED`、`FAILED`。 |
| `BaseIpcProvider` | `:ipc` | 客户端抽象基类。子类声明 `getProviderType()`，对外暴露带类型的方法（如 `executeShell`、`readFile`）。基类负责 bind / unbind、状态机、监听器扇出。 |
| `IpcProviderFactory` | `:ipc` | `BaseIpcProvider create(IpcProviderConfig config)`。让 Manager 不用知道具体类型就能创建子类。 |
| `IpcProviderRegistry` | `:ipc` | `IpcProviderFactory` × `IpcProviderType` 的单例注册表。默认注册 `TerminalProviderServiceFactory`。新增类型只需注册一个工厂，符合 OCP。 |
| `IpcProviderScanner` | `:ipc` | 用 `PackageManager.queryIntentServices(intent)` 找到所有声明了目标 action **并且**持有对应 `<uses-permission>` 的 Service，返回 `List<ScannedProvider>`。 |
| `IpcProviderManager` | `:ipc` | 运行时外观：`registerAndBind` / `unregisterAndUnbind` / `getProvider(id|type)` / `addStateListener` / `removeStateListener`。聚合所有 Provider 的状态变化并转发给全局监听列表。 |
| `AbstractIpcProviderService` | `:ipc` | 服务端抽象 `Service` 基类。子类实现 `createBinder()`，可选覆盖 `onProviderUnbind` / `onProviderDestroy`。 |
| `IpcServerExecutors` | `:ipc` | 进程级共享线程池。守护线程；**不要在 `onDestroy` 里 `shutdown`**。 |
| `IpcPermission` | `:ipc` | 常量持有者。`IpcPermission.TERMINAL_PROVIDER = "cn.lineai.permission.IPC_TERMINAL_PROVIDER"`。 |

---

## 协议接口

### AIDL — `IBaseIpcService`

每个 Provider 都要实现这层最薄的握手。

```aidl
// ipc/src/main/aidl/cn/lineai/ipc/IBaseIpcService.aidl
package cn.lineai.ipc;

interface IBaseIpcService {
    String getProviderType();   // 必须等于 IpcProviderType.TERMINAL.getId()（对终端 Provider 而言）
    String getProviderInfo();   // JSON 字符串，详见下文
    boolean isAvailable();      // 快速存活探针；返回 false 时选择器里会灰掉
}
```

`getProviderInfo()` 返回 JSON 字符串。终端约定的格式（客户端会按字段读）是：

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

* `name` / `version` —— LineCode UI 上展示。
* `shell` —— `executeShell` 实际要调起的 Shell 二进制路径。
* `home` —— Provider 声明的"工作区根目录"绝对路径（LineCode 通过 `TerminalIpcProvider.getHomePath()` 读回来填到项目选择器里）。
* `capabilities` —— Provider 实现的 AIDL 方法清单，纯展示用。

### AIDL — `ITerminalProviderService`

终端专用 AIDL，同时编进 `:ipc` 和 Provider App。客户端侧在 `BaseIpcProvider.getService()` 里拿到后转成 `ITerminalProviderService.Stub.asInterface(binder)`，再以强类型方法暴露给上层（`executeShell`、`readFile`、`writeFile`、`listDirDetailed`…）。

```aidl
// ipc/src/main/aidl/cn/lineai/ipc/terminal/ITerminalProviderService.aidl
package cn.lineai.ipc.terminal;
import cn.lineai.ipc.terminal.ITerminalProviderCallback;

interface ITerminalProviderService {
    // 从 IBaseIpcService 继承
    String getProviderType();
    String getProviderInfo();
    boolean isAvailable();

    // shell
    int executeShell(String command, String cwd, long timeoutMs,
                     ITerminalProviderCallback callback);

    // 类 SFTP 的文件操作
    byte[] readFile(String path);
    boolean writeFile(String path, in byte[] data);
    boolean deleteFile(String path);
    String[] listDir(String path);
    boolean fileExists(String path);
    long fileSize(String path);

    // 大文件分块读写
    byte[] readFileChunk(String path, long offset, int size);
    boolean writeFileChunk(String path, long offset, in byte[] data);
    long getFileSize(String path);

    // 结构化目录列表
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

新语义不用动协议——走 `executeShell` 即可。如果想要更丰富的接口，请在你的包里自建 AIDL，再给 `IpcProviderType` 加一个枚举值，然后在 `IpcProviderRegistry` 里注册新工厂。

---

## 权限与安全模型

信任边界落在 **Android 框架层**，不落在 App 代码里：

1. Provider App 在 manifest 里声明权限：

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

2. LineCode 声明同名权限并申请：

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

3. `bindService` 时 Android 校验调用方（LineCode）持有 `IPC_TERMINAL_PROVIDER`。没持有就抛 `SecurityException`。Provider 自己**不用**再写权限校验。

4. 扫描器额外检查包是否真的**声明**了对应 `uses-permission`（`PackageManager.GET_PERMISSIONS`），让选择器能灰掉那些"活不过 bind"的 Provider。

> 经验法则：只要权限字符串在 `IpcPermission.*` 里，就直接用 `android:permission` 写到 service 标签上；如果是别的，就当不可信对待。

> 不要省掉 `<queries>` —— Android 11+ 下 `queryIntentServices` 只会返回调用方显式声明过兴趣的 Intent 匹配的 Service。

---

## 5 分钟写一个自己的 Provider

下面我们发布一个 `cn.lineai.myprovider` App，每次 `executeShell` 仅打印命令并返回退出码 `0`。模型能调 `shell_execute` 时就会路由到它。

### 第 1 步 —— 新建模块

`settings.gradle.kts` 里加：

```kotlin
include(":my-provider")
```

`my-provider/build.gradle.kts`：

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

### 第 2 步 —— Manifest

`my-provider/src/main/AndroidManifest.xml`：

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

注意 `android:permission` 是阻止任何 App 来 bind 你的关键。少了它，谁都能调。

### 第 3 步 —— 准备 AIDL

`:ipc` 已经声明了你需要的 AIDL 契约，两种走法：

* **方式 A —— 通过 `:ipc` 依赖**（推荐）。已经做完，AIDL 会随依赖被自动编译进你的 APK。
* **方式 B —— 把 AIDL 复制到自己的工程里**（适合想完全自给自足、不想依赖 `:ipc` 的场景）：

  ```
  my-provider/src/main/aidl/cn/lineai/ipc/IBaseIpcService.aidl
  my-provider/src/main/aidl/cn/lineai/ipc/terminal/ITerminalProviderService.aidl
  my-provider/src/main/aidl/cn/lineai/ipc/terminal/ITerminalProviderCallback.aidl
  ```

  同包名、同内容，从 `:ipc` 源码里拷过来。AGP 在 `buildFeatures { aidl = true }` 下会编译它们。

### 第 4 步 —— Service 实现

```java
package cn.lineai.myprovider;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import cn.lineai.ipc.IBaseIpcService;          // 由 AIDL 生成
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

    // 用共享线程池——不要自己 new。
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

            // 其余 AIDL 方法打空——返回空 / false。
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
        // 不要关 IpcServerExecutors——它进程级共享。
        Log.i(TAG, "MyProviderService destroyed");
    }
}
```

整个 Provider 就这些。它把 AIDL 契约暴露出来。构建安装：

```bash
./gradlew :my-provider:assembleDebug
adb install -r my-provider/build/outputs/apk/debug/my-provider-debug.apk
```

### 第 5 步 —— 在 LineCode 里启用

1. 打开 LineCode → **设置 → MCP execution mode → Terminal Provider → Add provider**。
2. 扫描器会自动把 `cn.lineai.myprovider` 捡出来（有正确的 `<service>` 和 `<uses-permission>`）。也可以点 **Scan** 强制刷新。
3. 启用该条目。LineCode 调 `bindService`，状态机走 `DISCONNECTED → CONNECTING → CONNECTED`，监听器触发，把项目路径设到你的 Provider 的 `home`，文件树 / 附件选择器 / 模型 Shell 工具全部开始走你这边的 App。

可以用 logcat 验证：

```bash
adb logcat -s BaseIpcProvider AbstractIpcProviderService MyProvider
# 期望看到："BaseIpcProvider 状态迁移: CONNECTING -> CONNECTED"
```

### 第 6 步 —— 跟它对话

在 LineCode 聊天里问模型：

> "在终端 Provider 里跑一下 `uname -a`，告诉我内核版本。"

模型会调 `shell_execute`。LineCode 把请求路由到 `MyProviderService.executeShell(...)`，我们这边打印命令、返回内核版本，模型把结果回传。

---

## 从客户端侧调用 Provider

如果你想自己写**客户端**（比如集成测试、或者别的 App 想和终端 Provider 对话），最小模式：

```java
// 用已知 package + service 构造 config。
IpcProviderConfig config = IpcProviderConfig.builder()
        .enabled(true)
        .providerType(IpcProviderType.TERMINAL.getId())
        .name("My Provider")
        .packageName("cn.lineai.myprovider")
        .serviceClass("cn.lineai.myprovider.MyProviderService")
        .build();

// 构造 manager。
IpcProviderManager manager = new IpcProviderManager(context);
manager.addStateListener((provider, state, cause) -> {
    Log.i("Client", "state = " + state);
    if (state == IpcProviderConnectionState.CONNECTED) {
        // 可以调了
    } else if (state == IpcProviderConnectionState.FAILED) {
        Log.e("Client", "bind failed", cause);
    }
});

// Bind。监听器异步触发。
BaseIpcProvider base = manager.registerAndBind(config);
if (base instanceof TerminalIpcProvider) {
    TerminalIpcProvider term = (TerminalIpcProvider) base;
    try {
        String home = term.getHomePath();
        Log.i("Client", "provider home = " + home);
        // term.executeShell(...)、term.readFile(...) 等
    } catch (RemoteException e) {
        Log.e("Client", "IPC call failed", e);
    }
}

// 用完拆除。
manager.unregisterAndUnbind(config.getId());
```

`TerminalIpcProvider` 是终端 Provider 的强类型客户端，在 `:ipc/src/main/java/cn/lineai/ipc/terminal/TerminalIpcProvider.java`。如果以后要写新 Provider 类型，照着它写一份就行。

---

## 生命周期、状态机与状态监听

`BaseIpcProvider.bind(context)` 走的是下面这台状态机：

```
            bind()
DISCONNECTED ──────► CONNECTING ──────► CONNECTED
       ▲                  │                  │
       │                  └── bind 返回 false → FAILED
       │                                      │
       │     unbind() / onServiceDisconnected │
       └──────────────────────────────────────┘
```

每一次状态变化都会发到两个地方：

1. **Provider 级监听** —— `provider.addStateListener(l)`，工具卡片要做绑定进度展示时用。
2. **全局监听** —— `manager.addStateListener(l)`，由 `providerStateForwarder` 转发。`:app` 启动时正好注册一个全局监听 —— 项目路径、文件树、冷启动自动重连、`DISCONNECTED/FAILED` 时的清理钩子，全都靠它。

新 Provider 类型的推荐模式：

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

// 构造：manager.addStateListener(myListener);
// 销毁：manager.removeStateListener(myListener);
```

`IpcProviderManager.removeStateListener` 走的是 `List.remove(Object)`，比对的是**同一个引用**。引用相等坑是"监听器没被反注册"最常见的原因——务必把 lambda 存成字段，`:app` 的 `MainCoordinator.ipcStateListener` 就是这么做的。

---

## 打包与构建

`./gradlew :ipc:assembleDebug` 出库。`./gradlew :terminal-provider:assembleDebug` 出参考 Provider APK。`./gradlew :app:assembleDebug` 出 LineCode。

一旦有了自己的 Provider APK：

```bash
# 1. 安装两个 APK。
adb install -r linecode/app/build/outputs/apk/debug/app-debug.apk
adb install -r my-provider/build/outputs/apk/debug/my-provider-debug.apk

# 2. 打开 LineCode，扫描器会在
#    设置 → MCP execution mode → Terminal Provider → Scan 里找到你的 Provider。
```

Provider APK 就是普通的签名 Android APK。Release 构建要私钥 —— `:terminal-provider` 模块自带 `validateReleaseSigning` 任务，会拒绝用 debug 证书签 release；你自己的模块里也照搬一份。

### Manifest 自检清单

- [ ] Provider App 里 `<uses-permission android:name="cn.lineai.permission.IPC_TERMINAL_PROVIDER" />`
- [ ] `<service android:exported="true" android:permission="cn.lineai.permission.IPC_TERMINAL_PROVIDER">`
- [ ] 该 service 上挂 `<intent-filter><action android:name="cn.lineai.action.IPC_TERMINAL_PROVIDER" /></intent-filter>`
- [ ] （调用方）`<permission>` + `<uses-permission>` + `<queries><intent><action .../></intent></queries>`

---

## 版本与兼容性

* `ipc/src/main/aidl/` 下的 AIDL 是**稳定**接口。新增方法是兼容的（旧 Provider 不实现就罢了；客户端必须容忍 Stub 层的 `NoSuchMethodError`，或者先判断方法存在）。
* 删除 / 改名方法是 breaking change —— 升模块 `version` 并在 changelog 里点名。
* `getProviderInfo()` 是 JSON —— 自由加字段，绝不复用 / 删旧字段。
* `IpcProviderType` 枚举值按协议版本稳定。要加新协议，加一个枚举值（新 `intentAction` + `permissionName`），再在自己的包下出 AIDL。

---

## 常见问题排查

**扫描器找不到我的 Provider。**
按顺序检查：(1) `<service>` 上的 `intent-filter` 正确；(2) `android:exported` 是 `true`；(3) `<uses-permission>` 与 action 的 permission 对应；(4) 调用方 manifest 里写了 `<queries>` 块；(5) 安装真的成功了（`adb shell pm list packages | grep myprovider`）。

**Bind 抛 `SecurityException`。**
调用方（LineCode）没拿到 `IPC_TERMINAL_PROVIDER` 权限 —— 大概率是 `protectionLevel` 写成了 `signature` 而非 `normal`，或调用方 manifest 里漏了。

**状态是 FAILED，cause 是 `IllegalStateException("bindService 返回 false")`。**
Intent 没解析到。检查 config 里的 `packageName` 和 `serviceClass` 与已装 Provider 完全一致，`<service>` 是 exported。

**Provider 跑起来了但模型找不到 home 路径。**
确认 `getProviderInfo()` 里写了 `"home": getFilesDir().getAbsolutePath()`。客户端从 `TerminalIpcProvider.getHomePath()` 读它。

**线程堆积 / App 关不掉。**
不要在 Service 里 `newCachedThreadPool`。用 `IpcServerExecutors.shared()` —— 它返回守护线程池，卡住的 Shell 调用不会让 App 退不出去。

**测试。**
`app/src/test/java/cn/lineai/...` 下的单测覆盖了客户端状态机和注册表。服务端 AIDL 方法最好在真机上同时跑 Provider 和客户端，用 `adb logcat -s ...` 调试。

---

## API 速查

### 客户端

```java
// 构造 config
IpcProviderConfig config = IpcProviderConfig.builder()
        .id("ipc_myprovider_1")               // 可空；为空则自动生成 UUID
        .enabled(true)
        .providerType(IpcProviderType.TERMINAL.getId())
        .name("My Provider")
        .packageName("cn.lineai.myprovider")
        .serviceClass("cn.lineai.myprovider.MyProviderService")
        .build();

// Manager 生命周期
IpcProviderManager manager = new IpcProviderManager(context);
manager.addStateListener(listener);
BaseIpcProvider provider = manager.registerAndBind(config);
manager.unregisterAndUnbind(config.getId());
manager.unregisterAll();
manager.removeStateListener(listener);

// 发现
IpcProviderScanner scanner = new IpcProviderScanner();
List<ScannedProvider> hits = scanner.scan(context, IpcProviderType.TERMINAL);

// 注册表（例如新增一个类型）
IpcProviderRegistry.getInstance().register(new MyProviderFactory());

// 状态查询
IpcProviderConnectionState state = provider.getConnectionState();
boolean bound = provider.isBound();
IpcProviderConfig cfg = provider.getConfig();
```

### 服务端

```java
// 继承 AbstractIpcProviderService
public final class MyProviderService extends AbstractIpcProviderService {
    @Override
    protected IBinder createBinder() {
        return new ITerminalProviderService.Stub() {
            // override AIDL 方法
        };
    }
    // 可选钩子
    @Override protected void onProviderUnbind(Intent intent) { /* ... */ }
    @Override protected void onProviderDestroy()             { /* ... */ }
}

// 共享线程池
ExecutorService pool = IpcServerExecutors.shared(); // 守护线程；不要 shutdown
```

### Manifest（Provider 侧）

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

### Manifest（调用方）

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

整个协议面就这么大：3 个 AIDL 方法、客户端 1 个抽象类、服务端 1 个抽象类、1 个权限、1 个 Intent action、1 个共享线程池。
