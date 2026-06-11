package cn.lineai.data.repository;

import cn.lineai.model.FileTreeNode;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class FileTreeRepository {
    private static final int MAX_CHILDREN_PER_DIR = 300;

    public FileTreeNode buildTree(String rootPath, Set<String> expandedPaths) {
        File root = new File(rootPath == null ? "" : rootPath);
        String displayName = root.getName().length() == 0 ? "home" : root.getName();
        if (!root.exists()) {
            return new FileTreeNode(displayName, root.getAbsolutePath(), true, true, Collections.emptyList());
        }
        return buildNode(root, displayName, true, expandedPaths == null ? Collections.emptySet() : expandedPaths);
    }

    public FileTreeNode buildReadableTree(String rootPath, Set<String> expandedPaths) {
        File root = new File(rootPath == null ? "" : rootPath);
        String displayName = root.getName().length() == 0 ? root.getAbsolutePath() : root.getName();
        if (!root.exists()) {
            throw new IllegalStateException("目录不存在: " + root.getAbsolutePath());
        }
        if (!root.isDirectory()) {
            throw new IllegalStateException("路径不是目录: " + root.getAbsolutePath());
        }
        return buildReadableNode(root, displayName, true, expandedPaths == null ? Collections.emptySet() : expandedPaths);
    }

    public boolean isDirectory(String path) {
        return path != null && new File(path).isDirectory();
    }

    public boolean exists(String path) {
        return path != null && new File(path).exists();
    }

    public void createFile(String parentPath, String name) throws Exception {
        File parent = requireDirectory(parentPath);
        String cleanName = cleanName(name);
        File target = new File(parent, cleanName);
        if (target.exists()) {
            throw new IllegalStateException("文件已存在: " + cleanName);
        }
        File targetParent = target.getParentFile();
        if (targetParent != null && !targetParent.exists() && !targetParent.mkdirs()) {
            throw new IllegalStateException("无法创建父目录: " + targetParent.getAbsolutePath());
        }
        if (!target.createNewFile()) {
            throw new IllegalStateException("无法创建文件: " + cleanName);
        }
    }

    public void createDirectory(String parentPath, String name) throws Exception {
        File parent = requireDirectory(parentPath);
        String cleanName = cleanName(name);
        File target = new File(parent, cleanName);
        if (target.exists()) {
            throw new IllegalStateException("目录已存在: " + cleanName);
        }
        if (!target.mkdirs()) {
            throw new IllegalStateException("无法创建目录: " + cleanName);
        }
    }

    public String rename(String path, String newName) throws Exception {
        File source = requireExisting(path);
        String cleanName = cleanName(newName);
        File parent = source.getParentFile();
        if (parent == null) {
            throw new IllegalStateException("无法重命名根目录");
        }
        File target = new File(parent, cleanName);
        if (target.exists()) {
            throw new IllegalStateException("目标已存在: " + cleanName);
        }
        if (!source.renameTo(target)) {
            throw new IllegalStateException("重命名失败: " + source.getName());
        }
        return target.getAbsolutePath();
    }

    public void delete(String path) throws Exception {
        File target = requireExisting(path);
        deleteRecursively(target);
    }

    public String copyInto(String sourcePath, String targetDirectoryPath) throws Exception {
        File source = requireExisting(sourcePath);
        File targetDirectory = requireDirectory(targetDirectoryPath);
        File target = uniqueCopyTarget(targetDirectory, source.getName());
        copyRecursively(source, target);
        return target.getAbsolutePath();
    }

    private FileTreeNode buildNode(File file, String fallbackName, boolean forceExpanded, Set<String> expandedPaths) {
        boolean directory = file.isDirectory();
        boolean expanded = directory && (forceExpanded || expandedPaths.contains(file.getAbsolutePath()));
        ArrayList<FileTreeNode> children = new ArrayList<>();
        if (expanded) {
            children.addAll(readChildren(file, expandedPaths));
        }
        String name = file.getName();
        if (name.length() == 0) {
            name = fallbackName == null || fallbackName.length() == 0 ? file.getAbsolutePath() : fallbackName;
        }
        return new FileTreeNode(name, file.getAbsolutePath(), directory, expanded, children);
    }

    private FileTreeNode buildReadableNode(File file, String fallbackName, boolean forceExpanded, Set<String> expandedPaths) {
        boolean directory = file.isDirectory();
        boolean expanded = directory && (forceExpanded || expandedPaths.contains(file.getAbsolutePath()));
        ArrayList<FileTreeNode> children = new ArrayList<>();
        if (expanded) {
            children.addAll(readReadableChildren(file, expandedPaths));
        }
        String name = file.getName();
        if (name.length() == 0) {
            name = fallbackName == null || fallbackName.length() == 0 ? file.getAbsolutePath() : fallbackName;
        }
        return new FileTreeNode(name, file.getAbsolutePath(), directory, expanded, children);
    }

    private List<FileTreeNode> readChildren(File dir, Set<String> expandedPaths) {
        File[] rawChildren = dir.listFiles();
        if (rawChildren == null || rawChildren.length == 0) {
            return Collections.emptyList();
        }
        return sortedLimitedChildren(rawChildren, expandedPaths, false);
    }

    private List<FileTreeNode> readReadableChildren(File dir, Set<String> expandedPaths) {
        File[] rawChildren = dir.listFiles();
        if (rawChildren == null) {
            throw new IllegalStateException("目录不可读取: " + dir.getAbsolutePath());
        }
        if (rawChildren.length == 0) {
            return Collections.emptyList();
        }
        return sortedLimitedChildren(rawChildren, expandedPaths, true);
    }

    private List<FileTreeNode> sortedLimitedChildren(File[] rawChildren, Set<String> expandedPaths, boolean strict) {
        Arrays.sort(rawChildren, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                if (left.isDirectory() != right.isDirectory()) {
                    return left.isDirectory() ? -1 : 1;
                }
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        int limit = Math.min(rawChildren.length, MAX_CHILDREN_PER_DIR);
        ArrayList<FileTreeNode> children = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            children.add(strict
                    ? buildReadableNode(rawChildren[i], rawChildren[i].getName(), false, expandedPaths)
                    : buildNode(rawChildren[i], rawChildren[i].getName(), false, expandedPaths));
        }
        return children;
    }

    private File requireExisting(String path) {
        File file = new File(path == null ? "" : path);
        if (!file.exists()) {
            throw new IllegalStateException("路径不存在: " + path);
        }
        return file;
    }

    private File requireDirectory(String path) {
        File dir = requireExisting(path);
        if (!dir.isDirectory()) {
            throw new IllegalStateException("路径不是目录: " + path);
        }
        return dir;
    }

    private String cleanName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.length() == 0) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (value.contains("/") || value.contains("\\") || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException("名称不能包含路径分隔符");
        }
        return value;
    }

    private void deleteRecursively(File target) throws Exception {
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!target.delete()) {
            throw new IllegalStateException("删除失败: " + target.getAbsolutePath());
        }
    }

    private void copyRecursively(File source, File target) throws Exception {
        if (source.isDirectory()) {
            if (!target.mkdirs() && !target.isDirectory()) {
                throw new IllegalStateException("无法创建目录: " + target.getAbsolutePath());
            }
            File[] children = source.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                copyRecursively(child, new File(target, child.getName()));
            }
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建父目录: " + parent.getAbsolutePath());
        }
        FileInputStream input = new FileInputStream(source);
        try {
            FileOutputStream output = new FileOutputStream(target);
            try {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
    }

    private File uniqueCopyTarget(File directory, String name) {
        File target = new File(directory, name);
        if (!target.exists()) {
            return target;
        }
        String base = name;
        String extension = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            extension = name.substring(dot);
        }
        for (int i = 1; i < 1000; i++) {
            File candidate = new File(directory, base + " copy" + (i == 1 ? "" : " " + i) + extension);
            if (!candidate.exists()) {
                return candidate;
            }
        }
        throw new IllegalStateException("无法生成复制目标名称: " + name);
    }
}
