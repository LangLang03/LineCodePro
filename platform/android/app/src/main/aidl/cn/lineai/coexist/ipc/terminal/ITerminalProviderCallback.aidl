package cn.lineai.coexist.ipc.terminal;

interface ITerminalProviderCallback {
    void onOutput(String content);
    void onError(String error);
    void onComplete(int exitCode);
}
