package cn.lineai.ipc.terminal;

public interface TerminalShellCallback {
    void onOutput(String content);

    void onError(String error);

    void onComplete(int exitCode);
}
