package cn.lineai.ipc.terminal;

import android.os.RemoteException;
import cn.lineai.ipc.BaseIpcProvider;
import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.IpcProviderType;
import org.json.JSONObject;

public final class TerminalIpcProvider extends BaseIpcProvider {

    public TerminalIpcProvider(IpcProviderConfig config) {
        super(config);
    }

    @Override
    public IpcProviderType getProviderType() {
        return IpcProviderType.TERMINAL;
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    private ITerminalProviderService getService() {
        if (!isBound() || serviceBinder == null) {
            throw new IllegalStateException("终端提供者服务未绑定");
        }
        return ITerminalProviderService.Stub.asInterface(serviceBinder);
    }

    public TerminalShellResult executeShell(String command, String cwd, long timeoutMs,
                                            TerminalShellCallback callback) throws RemoteException {
        ITerminalProviderService service = getService();
        ITerminalProviderCallback aidlCallback = new ITerminalProviderCallback.Stub() {
            @Override
            public void onOutput(String content) {
                if (callback != null) {
                    callback.onOutput(content);
                }
            }

            @Override
            public void onError(String error) {
                if (callback != null) {
                    callback.onError(error);
                }
            }

            @Override
            public void onComplete(int exitCode) {
                if (callback != null) {
                    callback.onComplete(exitCode);
                }
            }
        };
        int exitCode = service.executeShell(command, cwd, timeoutMs, aidlCallback);
        return new TerminalShellResult(exitCode);
    }

    public byte[] readFile(String path) throws RemoteException {
        return getService().readFile(path);
    }

    public boolean writeFile(String path, byte[] data) throws RemoteException {
        return getService().writeFile(path, data);
    }

    public byte[] readFileChunk(String path, long offset, int size) throws RemoteException {
        return getService().readFileChunk(path, offset, size);
    }

    public boolean writeFileChunk(String path, long offset, byte[] data) throws RemoteException {
        return getService().writeFileChunk(path, offset, data);
    }

    public long getFileSize(String path) throws RemoteException {
        return getService().getFileSize(path);
    }

    public boolean deleteFile(String path) throws RemoteException {
        return getService().deleteFile(path);
    }

    public String[] listDir(String path) throws RemoteException {
        return getService().listDir(path);
    }

    public boolean fileExists(String path) throws RemoteException {
        return getService().fileExists(path);
    }

    public long fileSize(String path) throws RemoteException {
        return getService().fileSize(path);
    }

    public String listDirDetailed(String path) throws RemoteException {
        return getService().listDirDetailed(path);
    }

    public String getHomePath() throws RemoteException {
        String info = getService().getProviderInfo();
        if (info == null || info.length() == 0) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(info);
            return json.optString("home", "");
        } catch (Exception e) {
            return "";
        }
    }
}
