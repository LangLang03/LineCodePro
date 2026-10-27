// ITerminalProviderService.aidl
//
// 接口版本：v1
//
// 调用方权限：调用方必须持有 cn.lineai.permission.IPC_TERMINAL_PROVIDER
// （protectionLevel="normal"，在 app 模块 AndroidManifest.xml 中声明）。
// 提供方 Service 须在 <service> 标签上通过 android:permission 属性强制校验，
// 参见 terminal-provider 模块 AndroidManifest.xml 中的 TerminalProviderService 声明。
package cn.lineai.ipc.terminal;

import cn.lineai.ipc.terminal.ITerminalProviderCallback;

interface ITerminalProviderService {
    // 通用 IPC 基类方法
    String getProviderType();
    String getProviderInfo();
    boolean isAvailable();

    // SHELL 执行
    int executeShell(String command, String cwd, long timeoutMs, ITerminalProviderCallback callback);

    // 文件操作（类似 SFTP）
    // readFile/writeFile 保留作为兼容入口，内部通过分块接口实现；
    // 大文件场景应优先使用 readFileChunk/writeFileChunk + getFileSize。
    byte[] readFile(String path);
    boolean writeFile(String path, in byte[] data);
    boolean deleteFile(String path);
    String[] listDir(String path);
    boolean fileExists(String path);
    long fileSize(String path);

    // 大文件分块读写。
    // - readFileChunk: 从 offset 开始读取最多 size 字节，size<=0 时实现端按上限截断。
    // - writeFileChunk: 从 offset 开始覆盖写入 data；offset==-1 表示追加。
    // - getFileSize: 返回文件字节长度；不存在或非常规文件返回 -1。
    byte[] readFileChunk(String path, long offset, int size);
    boolean writeFileChunk(String path, long offset, in byte[] data);
    long getFileSize(String path);

    // 列出目录下的文件与子目录，返回 JSON 数组字符串。
    // 格式：[{"name":"文件名","dir":是否为目录,"size":字节数}, ...]
    // 空目录或无效路径返回 "[]"。
    String listDirDetailed(String path);
}
