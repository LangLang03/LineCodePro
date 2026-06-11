package cn.lineai.data.importer;

import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.MessageRecord;
import cn.lineai.model.ModelConfig;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LineCodeArchiveCodec {
    public static final String ENTRY_MANIFEST = "manifest.json";
    public static final String ENTRY_DATABASE = "database.json";
    public static final String ENTRY_ASYNC_STORAGE = "async-storage.json";

    private static final String ROOT_HOME = "home";
    private static final String ROOT_PROJECT = "project";
    private static final String ROOT_SKILLS = "skills";
    private static final String ROOT_LINECODE = ".linecode";
    private static final String CONVERSATION_FILES_DIR = "conversations";
    private static final int BUFFER_SIZE = 8192;

    private final LineCodeImportMapper importMapper = new LineCodeImportMapper();

    public void writeArchive(
            ImportedLineCodeData data,
            File homeRoot,
            File projectRoot,
            File skillsRoot,
            JSONObject databaseSnapshot,
            OutputStream rawOutput
    ) throws Exception {
        ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(rawOutput));
        try {
            ImportedLineCodeData exportData = ArchiveSecretRedactor.redactData(data);
            JSONObject exportDatabaseSnapshot = ArchiveSecretRedactor.redactDatabaseSnapshot(databaseSnapshot);
            LinkedHashMap<String, String> conversationFiles = new LinkedHashMap<>();
            JSONArray asyncStorage = buildAsyncStorageEntries(exportData, conversationFiles);
            putUtf8(zip, ENTRY_MANIFEST, manifest(exportDatabaseSnapshot != null).toString(2));
            if (exportDatabaseSnapshot != null) {
                putUtf8(zip, ENTRY_DATABASE, exportDatabaseSnapshot.toString(2));
            }
            putUtf8(zip, ENTRY_ASYNC_STORAGE, asyncStorage.toString(2));
            for (Map.Entry<String, String> entry : conversationFiles.entrySet()) {
                putUtf8(zip, entry.getKey(), entry.getValue());
            }
            addDirectoryTree(zip, homeRoot, ROOT_HOME);
            addDirectoryTree(zip, projectRoot, ROOT_PROJECT);
            addDirectoryTree(zip, skillsRoot, ROOT_SKILLS);
            zip.finish();
        } finally {
            zip.close();
        }
    }

    public void extractArchive(InputStream rawInput, File targetDir) throws Exception {
        if (targetDir == null) {
            throw new IllegalArgumentException("缺少 .linecode 解压目录。");
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        File canonicalTarget = targetDir.getCanonicalFile();
        ZipInputStream zip = new ZipInputStream(new BufferedInputStream(rawInput));
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null || name.length() == 0) {
                    zip.closeEntry();
                    continue;
                }
                File out = new File(targetDir, name).getCanonicalFile();
                if (!isInside(canonicalTarget, out)) {
                    throw new IllegalArgumentException(".linecode 包含越界路径: " + name);
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(out, false));
                    try {
                        copy(zip, output);
                    } finally {
                        output.close();
                    }
                }
                zip.closeEntry();
            }
        } finally {
            zip.close();
        }
    }

    public ImportedLineCodeData readLegacyPayload(File payloadDir) throws Exception {
        return importMapper.readPayload(payloadDir);
    }

    public int restoreWorkspaceRoots(
            File payloadDir,
            File homeRoot,
            File projectRoot,
            File skillsRoot,
            boolean replace
    ) throws Exception {
        int count = 0;
        count += restoreRoot(payloadDir, ROOT_HOME, homeRoot, replace);
        count += restoreRoot(payloadDir, ROOT_PROJECT, projectRoot, replace);
        count += restoreRoot(payloadDir, ROOT_SKILLS, skillsRoot, replace);
        return count;
    }

    private JSONArray buildAsyncStorageEntries(
            ImportedLineCodeData data,
            LinkedHashMap<String, String> conversationFiles
    ) throws Exception {
        ImportedLineCodeData safeData = data == null
                ? new ImportedLineCodeData(null, "", null, "", null)
                : data;
        JSONArray entries = new JSONArray();
        entries.put(entry(LineCodeImportMapper.KEY_MODELS, modelsJson(safeData).toString()));
        if (safeData.getSelectedModelId().length() > 0) {
            entries.put(entry(LineCodeImportMapper.KEY_SELECTED_MODEL, safeData.getSelectedModelId()));
        }
        if (safeData.getCurrentConversationId().length() > 0) {
            entries.put(entry(LineCodeImportMapper.KEY_CURRENT_CONVERSATION, safeData.getCurrentConversationId()));
        }

        JSONArray conversationList = new JSONArray();
        for (ConversationRecord conversation : safeData.getConversations()) {
            JSONObject conversationJson = conversationJson(conversation);
            String fileName = safeConversationFileName(conversation.getId());
            String entryName = CONVERSATION_FILES_DIR + "/" + fileName;
            byte[] bytes = conversationJson.toString(2).getBytes(StandardCharsets.UTF_8);
            conversationFiles.put(entryName, conversationJson.toString(2));
            conversationList.put(new JSONObject()
                    .put("id", conversation.getId())
                    .put("title", conversation.getTitle())
                    .put("createdAt", conversation.getCreatedAt())
                    .put("updatedAt", conversation.getUpdatedAt()));
            entries.put(entry(
                    LineCodeImportMapper.KEY_CONVERSATION_PREFIX + conversation.getId(),
                    new JSONObject()
                            .put("storage", "file")
                            .put("schemaVersion", 2)
                            .put("id", conversation.getId())
                            .put("fileName", fileName)
                            .put("size", bytes.length)
                            .put("updatedAt", conversation.getUpdatedAt())
                            .put("messageCount", conversation.getMessages().size())
                            .toString()
            ));
        }
        entries.put(entry(LineCodeImportMapper.KEY_CONVERSATION_LIST, conversationList.toString()));

        for (Map.Entry<String, String> setting : safeData.getSettings().entrySet()) {
            String key = setting.getKey();
            if (key == null || key.length() == 0 || isReservedAsyncStorageKey(key)) {
                continue;
            }
            entries.put(entry(key, setting.getValue() == null ? "" : setting.getValue()));
        }
        return entries;
    }

    private JSONObject manifest(boolean containsDatabase) throws Exception {
        JSONArray roots = new JSONArray();
        roots.put(ROOT_HOME);
        roots.put(ROOT_PROJECT);
        roots.put(ROOT_SKILLS);
        return new JSONObject()
                .put("format", "linecode")
                .put("formatVersion", 1)
                .put("container", "zip")
                .put("createdAt", System.currentTimeMillis())
                .put("database", containsDatabase)
                .put("workspaceRoots", roots);
    }

    private JSONObject entry(String key, String value) throws Exception {
        return new JSONObject()
                .put("key", key == null ? "" : key)
                .put("value", value == null ? "" : value);
    }

    private JSONArray modelsJson(ImportedLineCodeData data) throws Exception {
        JSONArray array = new JSONArray();
        for (ModelConfig model : data.getModels()) {
            array.put(model.toJson());
        }
        return array;
    }

    private JSONObject conversationJson(ConversationRecord conversation) throws Exception {
        JSONObject object = objectFromRaw(conversation.getRawJson());
        object.put("id", conversation.getId());
        object.put("title", conversation.getTitle());
        object.put("projectId", conversation.getProjectId());
        object.put("createdAt", conversation.getCreatedAt());
        object.put("updatedAt", conversation.getUpdatedAt());
        JSONArray messages = new JSONArray();
        for (MessageRecord message : conversation.getMessages()) {
            messages.put(messageJson(message));
        }
        object.put("messages", messages);
        return object;
    }

    private JSONObject messageJson(MessageRecord message) throws Exception {
        JSONObject object = objectFromRaw(message.getRawJson());
        object.put("id", message.getId());
        object.put("role", message.getRole().getProtocolName());
        object.put("content", message.getContent());
        object.put("reasoningContent", message.getReasoningContent());
        object.put("timestamp", message.getTimestamp());
        object.put("streaming", message.isStreaming());
        object.put("hidden", message.isHidden());
        object.put("excludeFromContext", message.isExcludeFromContext());
        object.put("toolCallId", message.getToolCallId());
        object.put("toolName", message.getToolName());
        object.put("isError", message.isError());
        return object;
    }

    private JSONObject objectFromRaw(String rawJson) {
        if (rawJson == null || rawJson.trim().length() == 0) {
            return new JSONObject();
        }
        try {
            return new JSONObject(rawJson);
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }

    private boolean isReservedAsyncStorageKey(String key) {
        return LineCodeImportMapper.KEY_MODELS.equals(key)
                || LineCodeImportMapper.KEY_SELECTED_MODEL.equals(key)
                || LineCodeImportMapper.KEY_CONVERSATION_LIST.equals(key)
                || LineCodeImportMapper.KEY_CURRENT_CONVERSATION.equals(key)
                || key.startsWith(LineCodeImportMapper.KEY_CONVERSATION_PREFIX)
                || key.startsWith(LineCodeImportMapper.KEY_CONVERSATION_CHUNK_PREFIX);
    }

    private String safeConversationFileName(String id) {
        String safeId = (id == null ? "" : id).replaceAll("[^a-zA-Z0-9_-]", "_");
        return (safeId.length() == 0 ? "conversation" : safeId) + ".json";
    }

    private void putUtf8(ZipOutputStream zip, String name, String value) throws Exception {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(System.currentTimeMillis());
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private void addDirectoryTree(ZipOutputStream zip, File root, String archiveRoot) throws Exception {
        if (root == null || !root.exists() || !root.isDirectory()) {
            return;
        }
        File canonicalRoot = root.getCanonicalFile();
        addDirectoryChildren(zip, canonicalRoot, canonicalRoot, archiveRoot);
    }

    private void addDirectoryChildren(ZipOutputStream zip, File canonicalRoot, File dir, String archiveRoot) throws Exception {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            File canonicalChild = child.getCanonicalFile();
            if (!isInside(canonicalRoot, canonicalChild)) {
                continue;
            }
            String relative = relativePath(canonicalRoot, canonicalChild);
            if (relative.length() == 0) {
                continue;
            }
            String entryName = archiveRoot + "/" + relative.replace(File.separatorChar, '/');
            if (canonicalChild.isDirectory()) {
                addDirectoryChildren(zip, canonicalRoot, canonicalChild, archiveRoot);
            } else if (canonicalChild.isFile()) {
                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(canonicalChild.lastModified());
                zip.putNextEntry(entry);
                BufferedInputStream input = new BufferedInputStream(new FileInputStream(canonicalChild));
                try {
                    copy(input, zip);
                } finally {
                    input.close();
                }
                zip.closeEntry();
            }
        }
    }

    private int restoreRoot(File payloadDir, String rootName, File targetRoot, boolean replace) throws Exception {
        File source = sourceRoot(payloadDir, rootName);
        if (source == null || !source.exists() || !source.isDirectory() || targetRoot == null) {
            return 0;
        }
        if (!targetRoot.exists()) {
            targetRoot.mkdirs();
        }
        if (replace) {
            deleteContents(targetRoot);
        }
        return copyDirectoryContents(source.getCanonicalFile(), targetRoot.getCanonicalFile());
    }

    private File sourceRoot(File payloadDir, String rootName) {
        File root = new File(payloadDir, rootName);
        if (root.isDirectory()) {
            return root;
        }
        File nested = new File(new File(payloadDir, ROOT_LINECODE), rootName);
        return nested.isDirectory() ? nested : root;
    }

    private int copyDirectoryContents(File sourceRoot, File targetRoot) throws Exception {
        File[] children = sourceRoot.listFiles();
        if (children == null) {
            return 0;
        }
        int count = 0;
        for (File child : children) {
            File canonicalChild = child.getCanonicalFile();
            if (!isInside(sourceRoot, canonicalChild)) {
                continue;
            }
            String relative = relativePath(sourceRoot, canonicalChild);
            File out = new File(targetRoot, relative).getCanonicalFile();
            if (!isInside(targetRoot, out)) {
                throw new IllegalArgumentException(".linecode 工作区路径越界: " + relative);
            }
            if (canonicalChild.isDirectory()) {
                out.mkdirs();
                count += copyDirectoryContents(canonicalChild, out);
            } else if (canonicalChild.isFile()) {
                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                BufferedInputStream input = new BufferedInputStream(new FileInputStream(canonicalChild));
                try {
                    BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(out, false));
                    try {
                        copy(input, output);
                    } finally {
                        output.close();
                    }
                } finally {
                    input.close();
                }
                count++;
            }
        }
        return count;
    }

    private void deleteContents(File root) throws Exception {
        File canonicalRoot = root.getCanonicalFile();
        File[] children = canonicalRoot.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            deleteRecursive(canonicalRoot, child.getCanonicalFile());
        }
    }

    private void deleteRecursive(File canonicalRoot, File file) throws Exception {
        if (!isInside(canonicalRoot, file)) {
            throw new IllegalArgumentException("删除路径越界: " + file.getPath());
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(canonicalRoot, child.getCanonicalFile());
                }
            }
        }
        file.delete();
    }

    private String relativePath(File root, File child) throws Exception {
        String rootPath = root.getCanonicalPath();
        String childPath = child.getCanonicalPath();
        if (rootPath.equals(childPath)) {
            return "";
        }
        return childPath.substring(rootPath.length() + 1);
    }

    private boolean isInside(File root, File file) throws Exception {
        String rootPath = root.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        return rootPath.equals(filePath) || filePath.startsWith(rootPath + File.separator);
    }

    private void copy(InputStream input, OutputStream output) throws Exception {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    String readUtf8(File file) throws Exception {
        FileInputStream input = new FileInputStream(file);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            copy(input, output);
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            input.close();
        }
    }
}
