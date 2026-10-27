package cn.lineai.ipc.terminal;

public final class TerminalShellResult {
    private final int exitCode;

    public TerminalShellResult(int exitCode) {
        this.exitCode = exitCode;
    }

    public int getExitCode() {
        return exitCode;
    }

    public boolean isSuccess() {
        return exitCode == 0;
    }
}
