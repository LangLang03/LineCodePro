package cn.lineai.mvp;

import cn.lineai.data.importer.LineCodeArchiveService;
import cn.lineai.data.importer.LineCodeImportService;

public final class LineCodeArchiveController {
    interface ArchiveGateway {
        ExportSummary exportArchive(String uri) throws Exception;

        ImportSummary importArchive(String uri) throws Exception;
    }

    interface BackgroundRunner {
        void execute(String name, Runnable runnable);
    }

    interface Clock {
        long nowMillis();
    }

    interface Host {
        void openExportPicker(String fileName);

        void persistBeforeExport();

        void openImportPicker();

        void showImportConfirmation(String sourceName);

        void beforeImport();

        void afterImport();

        void showNotice(String text);
    }

    interface UiDispatcher {
        void post(Runnable runnable);
    }

    static final class ExportSummary {
        private final int conversationCount;
        private final int modelCount;
        private final int settingCount;

        ExportSummary(int conversationCount, int modelCount, int settingCount) {
            this.conversationCount = conversationCount;
            this.modelCount = modelCount;
            this.settingCount = settingCount;
        }
    }

    static final class ImportSummary {
        private final int conversationCount;
        private final int modelCount;
        private final int settingCount;
        private final int restoredFileCount;

        ImportSummary(int conversationCount, int modelCount, int settingCount, int restoredFileCount) {
            this.conversationCount = conversationCount;
            this.modelCount = modelCount;
            this.settingCount = settingCount;
            this.restoredFileCount = restoredFileCount;
        }
    }

    private static final class ServiceArchiveGateway implements ArchiveGateway {
        private final LineCodeArchiveService archiveService;

        ServiceArchiveGateway(LineCodeArchiveService archiveService) {
            this.archiveService = archiveService;
        }

        @Override
        public ExportSummary exportArchive(String uri) throws Exception {
            LineCodeArchiveService.ExportResult result = archiveService.exportArchive(uri);
            return new ExportSummary(
                    result.getConversationCount(),
                    result.getModelCount(),
                    result.getSettingCount()
            );
        }

        @Override
        public ImportSummary importArchive(String uri) throws Exception {
            LineCodeArchiveService.ImportResult result = archiveService.importArchive(
                    uri,
                    LineCodeImportService.Mode.REPLACE
            );
            return new ImportSummary(
                    result.getConversationCount(),
                    result.getModelCount(),
                    result.getSettingCount(),
                    result.getRestoredFileCount()
            );
        }
    }

    private final ArchiveGateway archiveGateway;
    private final Host host;
    private final BackgroundRunner backgroundRunner;
    private final UiDispatcher uiDispatcher;
    private final Clock clock;
    private String pendingImportUri = "";
    private String pendingImportDisplayName = "";

    public LineCodeArchiveController(
            LineCodeArchiveService archiveService,
            Host host,
            BackgroundRunner backgroundRunner,
            UiDispatcher uiDispatcher
    ) {
        this(
                new ServiceArchiveGateway(archiveService),
                host,
                backgroundRunner,
                uiDispatcher,
                System::currentTimeMillis
        );
    }

    LineCodeArchiveController(
            ArchiveGateway archiveGateway,
            Host host,
            BackgroundRunner backgroundRunner,
            UiDispatcher uiDispatcher,
            Clock clock
    ) {
        this.archiveGateway = archiveGateway;
        this.host = host;
        this.backgroundRunner = backgroundRunner;
        this.uiDispatcher = uiDispatcher;
        this.clock = clock;
    }

    public void requestExport() {
        host.openExportPicker(defaultArchiveName());
    }

    public void exportTargetPicked(String uri) {
        String targetUri = safe(uri).trim();
        if (targetUri.length() == 0) {
            return;
        }
        host.persistBeforeExport();
        backgroundRunner.execute("linecode-export", () -> {
            try {
                ExportSummary result = archiveGateway.exportArchive(targetUri);
                uiDispatcher.post(() -> host.showNotice(exportSuccessMessage(result)));
            } catch (Exception e) {
                uiDispatcher.post(() -> host.showNotice("导出 .linecode 失败: " + errorMessage(e)));
            }
        });
    }

    public void exportCancelled() {
    }

    public void requestImport() {
        host.openImportPicker();
    }

    public void importPicked(String uri, String displayName) {
        pendingImportUri = safe(uri).trim();
        pendingImportDisplayName = safe(displayName);
        if (pendingImportUri.length() == 0) {
            return;
        }
        host.showImportConfirmation(importSourceName());
    }

    public void importCancelled() {
        clearPendingImport();
    }

    public void confirmImport() {
        String uri = pendingImportUri;
        clearPendingImport();
        if (uri.length() == 0) {
            return;
        }
        host.beforeImport();
        backgroundRunner.execute("linecode-import", () -> {
            try {
                ImportSummary result = archiveGateway.importArchive(uri);
                uiDispatcher.post(() -> {
                    host.afterImport();
                    host.showNotice(importSuccessMessage(result));
                });
            } catch (Exception e) {
                uiDispatcher.post(() -> host.showNotice("导入 .linecode 失败: " + errorMessage(e)));
            }
        });
    }

    private String defaultArchiveName() {
        return "LineCode-" + clock.nowMillis() + ".linecode";
    }

    private String importSourceName() {
        return pendingImportDisplayName.length() == 0 ? ".linecode 文件" : pendingImportDisplayName;
    }

    private void clearPendingImport() {
        pendingImportUri = "";
        pendingImportDisplayName = "";
    }

    private String exportSuccessMessage(ExportSummary result) {
        return "已导出 .linecode："
                + result.conversationCount + " 个会话，"
                + result.modelCount + " 个模型，"
                + result.settingCount + " 项设置。";
    }

    private String importSuccessMessage(ImportSummary result) {
        return "已导入 .linecode："
                + result.conversationCount + " 个会话，"
                + result.modelCount + " 个模型，"
                + result.settingCount + " 项设置，"
                + result.restoredFileCount + " 个工作区文件。";
    }

    private String errorMessage(Exception error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        return message == null || message.length() == 0 ? error.getClass().getSimpleName() : message;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
