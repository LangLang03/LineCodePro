package cn.lineai.mvp;

import cn.lineai.data.repository.SshFileTreeRepository;
import cn.lineai.model.FileTreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SshFileTreeController {
    private static final int PREFETCH_MAX_ROOT_CHILDREN = 80;
    private static final int PREFETCH_MAX_DIRECTORIES = 24;

    interface BackgroundRunner {
        void execute(String name, Runnable runnable);
    }

    interface DirectoryStore {
        FileTreeNode listDirectory(String path) throws Exception;
    }

    interface Host {
        boolean isSshExecutionMode();

        String projectPath();

        String projectLabel();

        boolean isExpanded(String path);

        void addExpandedPath(String path);

        void setProjectPathFromSshRoot(String path);

        String basename(String path);

        void render();
    }

    interface UiDispatcher {
        void post(Runnable runnable);
    }

    private static final class RepositoryDirectoryStore implements DirectoryStore {
        private final SshFileTreeRepository repository;

        RepositoryDirectoryStore(SshFileTreeRepository repository) {
            this.repository = repository;
        }

        @Override
        public FileTreeNode listDirectory(String path) throws Exception {
            return repository.listDirectory(path);
        }
    }

    private final DirectoryStore directoryStore;
    private final Host host;
    private final BackgroundRunner backgroundRunner;
    private final UiDispatcher uiDispatcher;
    private FileTreeNode cachedFileTree;
    private boolean loading;
    private String error = "";
    private int generation;
    private String rootPath = "";
    private final HashMap<String, ArrayList<FileTreeNode>> childrenByPath = new HashMap<>();
    private final HashMap<String, String> nameByPath = new HashMap<>();
    private final Set<String> loadedPaths = new HashSet<>();
    private final Set<String> loadingPaths = new HashSet<>();

    public SshFileTreeController(
            SshFileTreeRepository repository,
            Host host,
            BackgroundRunner backgroundRunner,
            UiDispatcher uiDispatcher
    ) {
        this(new RepositoryDirectoryStore(repository), host, backgroundRunner, uiDispatcher);
    }

    SshFileTreeController(
            DirectoryStore directoryStore,
            Host host,
            BackgroundRunner backgroundRunner,
            UiDispatcher uiDispatcher
    ) {
        this.directoryStore = directoryStore;
        this.host = host;
        this.backgroundRunner = backgroundRunner;
        this.uiDispatcher = uiDispatcher;
    }

    public FileTreeNode getFileTree() {
        if (cachedFileTree != null) {
            return cachedFileTree;
        }
        String label = loading ? "正在读取 SSH 目录..." : (error.length() == 0 ? "SSH" : error);
        String placeholderPath = host.projectPath().length() == 0 ? "." : host.projectPath();
        return new FileTreeNode(label, placeholderPath, true, true, Collections.emptyList());
    }

    public void requestFileTreeLoad(boolean force) {
        if (!host.isSshExecutionMode()) {
            return;
        }
        String root = cleanPath(host.projectPath());
        if (root.length() == 0 && rootPath.length() > 0) {
            root = rootPath;
        }
        if (root.length() == 0) {
            root = ".";
        }
        if (force) {
            invalidateFileTree();
        }
        requestDirectoryLoad(root, true, false);
    }

    public void requestDirectoryLoad(String path, boolean root, boolean force) {
        if (!host.isSshExecutionMode()) {
            return;
        }
        String cleanPath = cleanPath(path);
        if (cleanPath.length() == 0) {
            cleanPath = ".";
        }
        if (force) {
            invalidateDirectory(cleanPath);
        }
        if (!force && loadedPaths.contains(cleanPath)) {
            rebuildCachedTree();
            return;
        }
        if (loadingPaths.contains(cleanPath)) {
            return;
        }
        loadingPaths.add(cleanPath);
        loading = true;
        if (root && rootPath.length() == 0) {
            rootPath = cleanPath;
        }
        rebuildCachedTree();
        startDirectoryLoad(cleanPath, root, generation);
    }

    public void refreshDirectoryAfterFileOperation(String expandedPath) {
        String path = cleanPath(expandedPath);
        if (path.length() == 0) {
            requestFileTreeLoad(true);
            return;
        }
        invalidateDirectory(path);
        requestDirectoryLoad(path, path.equals(host.projectPath()) || path.equals(rootPath), false);
    }

    public void rebuildCachedTree() {
        String treeRootPath = rootPath.length() == 0 ? host.projectPath() : rootPath;
        if (treeRootPath == null || treeRootPath.trim().length() == 0) {
            cachedFileTree = null;
            return;
        }
        String rootName = nameByPath.containsKey(treeRootPath)
                ? nameByPath.get(treeRootPath)
                : host.basename(treeRootPath);
        if (rootName == null || rootName.length() == 0) {
            rootName = host.projectLabel();
        }
        cachedFileTree = buildVisibleDirectory(treeRootPath, rootName, true);
    }

    public void invalidateFileTree() {
        generation++;
        cachedFileTree = null;
        rootPath = "";
        error = "";
        loading = false;
        childrenByPath.clear();
        nameByPath.clear();
        loadedPaths.clear();
        loadingPaths.clear();
    }

    public void invalidateDirectory(String path) {
        String cleanPath = cleanPath(path);
        if (cleanPath.length() == 0) {
            return;
        }
        generation++;
        cachedFileTree = null;
        error = "";
        childrenByPath.remove(cleanPath);
        loadedPaths.remove(cleanPath);
        loadingPaths.remove(cleanPath);
        updateLoadingState();
    }

    private void startDirectoryLoad(String path, boolean root, int requestGeneration) {
        backgroundRunner.execute("linecode-ssh-directory-index", () -> {
            try {
                FileTreeNode listing = directoryStore.listDirectory(path);
                uiDispatcher.post(() -> applyDirectoryListing(path, listing, root, requestGeneration));
            } catch (Exception e) {
                uiDispatcher.post(() -> applyDirectoryError(path, e.getMessage(), requestGeneration));
            }
        });
    }

    private void applyDirectoryListing(String requestedPath, FileTreeNode listing, boolean root, int requestGeneration) {
        if (requestGeneration != generation) {
            return;
        }
        loadingPaths.remove(requestedPath);
        updateDirectoryListing(listing);
        error = "";
        updateLoadingState();
        if (root) {
            rootPath = listing.getPath();
        }
        if (host.projectPath().length() == 0) {
            host.setProjectPathFromSshRoot(listing.getPath());
            host.addExpandedPath(listing.getPath());
        }
        rebuildCachedTree();
        host.render();
        if (root) {
            startIndexPrefetch(listing.getChildren(), requestGeneration);
        }
    }

    private void applyDirectoryError(String path, String message, int requestGeneration) {
        if (requestGeneration != generation) {
            return;
        }
        loadingPaths.remove(path);
        error = message == null ? "" : message;
        updateLoadingState();
        rebuildCachedTree();
        host.render();
    }

    private void startIndexPrefetch(List<FileTreeNode> rootChildren, int requestGeneration) {
        if (rootChildren == null || rootChildren.size() > PREFETCH_MAX_ROOT_CHILDREN) {
            return;
        }
        ArrayList<String> paths = new ArrayList<>();
        for (FileTreeNode child : rootChildren) {
            if (child == null || !child.isDirectory()) {
                continue;
            }
            String path = child.getPath();
            if (path.length() == 0 || loadedPaths.contains(path) || loadingPaths.contains(path)) {
                continue;
            }
            paths.add(path);
            loadingPaths.add(path);
            if (paths.size() >= PREFETCH_MAX_DIRECTORIES) {
                break;
            }
        }
        if (paths.isEmpty()) {
            updateLoadingState();
            return;
        }
        updateLoadingState();
        backgroundRunner.execute("linecode-ssh-index-prefetch", () -> {
            for (String path : paths) {
                try {
                    FileTreeNode listing = directoryStore.listDirectory(path);
                    uiDispatcher.post(() -> applyPrefetchListing(path, listing, requestGeneration, false, ""));
                } catch (Exception e) {
                    String message = e.getMessage();
                    uiDispatcher.post(() -> applyPrefetchListing(path, null, requestGeneration, true, message));
                }
            }
        });
    }

    private void applyPrefetchListing(String path, FileTreeNode listing, int requestGeneration, boolean failed, String message) {
        if (requestGeneration != generation) {
            return;
        }
        loadingPaths.remove(path);
        if (!failed && listing != null) {
            updateDirectoryListing(listing);
        } else if (message != null && message.length() > 0 && host.isExpanded(path)) {
            error = message;
        }
        updateLoadingState();
        rebuildCachedTree();
        if (host.isExpanded(path)) {
            host.render();
        }
    }

    private void updateDirectoryListing(FileTreeNode listing) {
        if (listing == null || listing.getPath().length() == 0) {
            return;
        }
        ArrayList<FileTreeNode> children = new ArrayList<>(listing.getChildren());
        childrenByPath.put(listing.getPath(), children);
        loadedPaths.add(listing.getPath());
        nameByPath.put(listing.getPath(), listing.getName());
        for (FileTreeNode child : children) {
            if (child != null && child.isDirectory()) {
                nameByPath.put(child.getPath(), child.getName());
            }
        }
    }

    private FileTreeNode buildVisibleDirectory(String path, String name, boolean forceExpanded) {
        boolean expanded = forceExpanded || host.isExpanded(path);
        ArrayList<FileTreeNode> children = new ArrayList<>();
        if (expanded) {
            List<FileTreeNode> indexedChildren = childrenByPath.get(path);
            if (indexedChildren != null) {
                for (FileTreeNode child : indexedChildren) {
                    if (child == null) {
                        continue;
                    }
                    if (child.isDirectory()) {
                        String childName = child.getName().length() == 0
                                ? host.basename(child.getPath())
                                : child.getName();
                        children.add(buildVisibleDirectory(child.getPath(), childName, false));
                    } else {
                        children.add(child);
                    }
                }
            }
            if (loadingPaths.contains(path) && indexedChildren == null) {
                children.add(new FileTreeNode("正在读取...", path + "#loading", false, false, Collections.emptyList()));
            }
            if (error.length() > 0 && path.equals(rootPath) && indexedChildren == null) {
                children.add(new FileTreeNode(error, path + "#error", false, false, Collections.emptyList()));
            }
        }
        return new FileTreeNode(name, path, true, expanded, children);
    }

    private void updateLoadingState() {
        loading = !loadingPaths.isEmpty();
    }

    private String cleanPath(String path) {
        return path == null ? "" : path.trim();
    }
}
