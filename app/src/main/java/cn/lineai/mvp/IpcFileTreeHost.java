package cn.lineai.mvp;

class IpcFileTreeHost extends HostBase implements IpcFileTreeController.Host {
    IpcFileTreeHost(MainCoordinator coordinator) {
        super(coordinator);
    }

    @Override
    public boolean isTerminalProviderExecutionMode() {
        return coordinator().isTerminalProviderExecutionMode();
    }

    @Override
    public String projectPath() {
        return super.projectPath();
    }

    @Override
    public String projectLabel() {
        return super.projectLabel();
    }

    @Override
    public boolean isExpanded(String path) {
        return coordinator().fileTreeInteractionController.isExpanded(path);
    }

    @Override
    public void addExpandedPath(String path) {
        coordinator().fileTreeInteractionController.addExpandedPath(path);
    }

    @Override
    public void setProjectPathFromIpcRoot(String path) {
        coordinator().projectState().applyPathFromRemoteRoot(path);
    }
}
