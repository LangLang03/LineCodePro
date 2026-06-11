package cn.lineai.data.importer;

import android.content.Context;
import android.net.Uri;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.data.repository.ConversationRepository;
import cn.lineai.data.repository.SettingsRepository;
import cn.lineai.model.ModelRepository;
import cn.lineai.workspace.WorkspacePaths;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import org.json.JSONObject;

public final class LineCodeArchiveService {
    public static final class ExportResult {
        private final int modelCount;
        private final int conversationCount;
        private final int settingCount;

        ExportResult(int modelCount, int conversationCount, int settingCount) {
            this.modelCount = modelCount;
            this.conversationCount = conversationCount;
            this.settingCount = settingCount;
        }

        public int getModelCount() {
            return modelCount;
        }

        public int getConversationCount() {
            return conversationCount;
        }

        public int getSettingCount() {
            return settingCount;
        }
    }

    public static final class ImportResult {
        private final int modelCount;
        private final int conversationCount;
        private final int settingCount;
        private final int restoredFileCount;
        private final boolean databaseRestored;

        ImportResult(
                int modelCount,
                int conversationCount,
                int settingCount,
                int restoredFileCount,
                boolean databaseRestored
        ) {
            this.modelCount = modelCount;
            this.conversationCount = conversationCount;
            this.settingCount = settingCount;
            this.restoredFileCount = restoredFileCount;
            this.databaseRestored = databaseRestored;
        }

        public int getModelCount() {
            return modelCount;
        }

        public int getConversationCount() {
            return conversationCount;
        }

        public int getSettingCount() {
            return settingCount;
        }

        public int getRestoredFileCount() {
            return restoredFileCount;
        }

        public boolean isDatabaseRestored() {
            return databaseRestored;
        }
    }

    private final Context context;
    private final ModelRepository modelRepository;
    private final ConversationRepository conversationRepository;
    private final SettingsRepository settingsRepository;
    private final WorkspacePaths workspacePaths;
    private final LineCodeDatabase database;
    private final LineCodeImportService importService;
    private final LineCodeArchiveCodec archiveCodec = new LineCodeArchiveCodec();
    private final LineCodeDatabaseArchive databaseArchive = new LineCodeDatabaseArchive();

    public LineCodeArchiveService(Context context) {
        this.context = context.getApplicationContext();
        modelRepository = new ModelRepository(this.context);
        conversationRepository = new ConversationRepository(this.context);
        settingsRepository = new SettingsRepository(this.context);
        workspacePaths = new WorkspacePaths(this.context);
        database = LineCodeDatabase.getInstance(this.context);
        importService = new LineCodeImportService(this.context);
    }

    public ExportResult exportArchive(String uri) throws Exception {
        OutputStream rawOutput = context.getContentResolver().openOutputStream(Uri.parse(safe(uri)), "wt");
        if (rawOutput == null) {
            throw new IllegalArgumentException("无法写入 .linecode 文件。");
        }
        BufferedOutputStream output = new BufferedOutputStream(rawOutput);
        try {
            return exportArchive(output);
        } finally {
            output.close();
        }
    }

    public ExportResult exportArchive(OutputStream output) throws Exception {
        workspacePaths.ensurePrivateRoots();
        ImportedLineCodeData data = new ImportedLineCodeData(
                modelRepository.getModels(),
                modelRepository.getSelectedModelId(),
                conversationRepository.getAllConversations(),
                conversationRepository.getCurrentConversationId(),
                settingsRepository.getLineCodeSettings()
        );
        JSONObject snapshot = databaseArchive.exportSnapshot(database);
        archiveCodec.writeArchive(
                data,
                workspacePaths.getHomeRoot(),
                workspacePaths.getProjectRoot(),
                workspacePaths.getSkillsRoot(),
                snapshot,
                output
        );
        return new ExportResult(
                data.getModels().size(),
                data.getConversations().size(),
                data.getSettings().size()
        );
    }

    public ImportResult importArchive(String uri, LineCodeImportService.Mode mode) throws Exception {
        InputStream rawInput = context.getContentResolver().openInputStream(Uri.parse(safe(uri)));
        if (rawInput == null) {
            throw new IllegalArgumentException("无法读取 .linecode 文件。");
        }
        BufferedInputStream input = new BufferedInputStream(rawInput);
        try {
            return importArchive(input, mode);
        } finally {
            input.close();
        }
    }

    public ImportResult importArchive(InputStream input, LineCodeImportService.Mode mode) throws Exception {
        workspacePaths.ensurePrivateRoots();
        LineCodeImportService.Mode safeMode = mode == null ? LineCodeImportService.Mode.REPLACE : mode;
        File tempDir = new File(context.getCacheDir(), "linecode-import-" + System.currentTimeMillis());
        deleteRecursive(tempDir);
        tempDir.mkdirs();
        try {
            archiveCodec.extractArchive(input, tempDir);
            boolean hasDatabase = databaseArchive.hasSnapshot(tempDir);
            boolean hasAsyncStorage = new File(tempDir, LineCodeArchiveCodec.ENTRY_ASYNC_STORAGE).isFile();
            if (!hasDatabase && !hasAsyncStorage) {
                throw new IllegalArgumentException("请选择有效的 .linecode 备份文件。");
            }

            if (hasDatabase) {
                databaseArchive.importSnapshot(database, databaseArchive.readSnapshot(tempDir));
            } else {
                ImportedLineCodeData data = archiveCodec.readLegacyPayload(tempDir);
                importService.importData(data, safeMode);
            }

            int restoredFiles = archiveCodec.restoreWorkspaceRoots(
                    tempDir,
                    workspacePaths.getHomeRoot(),
                    workspacePaths.getProjectRoot(),
                    workspacePaths.getSkillsRoot(),
                    safeMode == LineCodeImportService.Mode.REPLACE
            );
            return new ImportResult(
                    modelRepository.getModels().size(),
                    conversationRepository.getAllConversations().size(),
                    settingsRepository.getLineCodeSettings().size(),
                    restoredFiles,
                    hasDatabase
            );
        } finally {
            deleteRecursive(tempDir);
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
