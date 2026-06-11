package cn.lineai.mvp;

import cn.lineai.model.FileTreeNode;

public interface WorkspaceController {
    void onProjectClick();

    void onPermissionClick();

    void onCurrentProjectRemoveRequested();

    void onFileNodeSelected(String path, boolean directory);

    void onFileNodeLongPressed(String path, String name, boolean directory, boolean root);

    void onFileTreeActivated();

    void onFileTreeRefresh();

    void onDirectoryPickerNodeSelected(String path);

    void onDirectoryPickerConfirmed();

    void onDirectoryPickerCancelled();

    FileTreeNode getFileTree();

    boolean canRemoveCurrentProject();

    void onExternalProjectTreePicked(String treeUri);

    void onExternalProjectPickerCancelled();

    void onStoragePermissionResult();
}
