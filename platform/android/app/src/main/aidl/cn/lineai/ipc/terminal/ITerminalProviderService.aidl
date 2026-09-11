package cn.lineai.ipc.terminal;

import cn.lineai.ipc.terminal.ITerminalProviderCallback;

interface ITerminalProviderService {
    String getProviderType();
    String getProviderInfo();
    boolean isAvailable();
    int executeShell(String command, String cwd, long timeoutMs,
                     ITerminalProviderCallback callback);
    byte[] readFile(String path);
    boolean writeFile(String path, in byte[] data);
    boolean deleteFile(String path);
    String[] listDir(String path);
    boolean fileExists(String path);
    long fileSize(String path);
    byte[] readFileChunk(String path, long offset, int size);
    boolean writeFileChunk(String path, long offset, in byte[] data);
    long getFileSize(String path);
    String listDirDetailed(String path);
}
