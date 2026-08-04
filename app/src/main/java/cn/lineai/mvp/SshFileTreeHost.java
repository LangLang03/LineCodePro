package cn.lineai.mvp;

class SshFileTreeHost extends HostBase implements SshFileTreeController.Host {
    SshFileTreeHost(MainCoordinator coordinator) {
        super(coordinator);
    }

    @Override
    public boolean isSshExecutionMode() {
        return coordinator().isSshExecutionMode();
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
    public void setProjectPathFromSshRoot(String path) {
        coordinator().projectState().applyPathFromRemoteRoot(path);
    }
}
