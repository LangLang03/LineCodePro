package cn.lineai.mvp;

import org.junit.Assert;
import org.junit.Test;

public final class LineCodeArchiveControllerTest {
    @Test
    public void requestExportUsesTimestampedArchiveName() {
        Fixture fixture = new Fixture();

        fixture.controller.requestExport();

        Assert.assertEquals("LineCode-123456.linecode", fixture.host.exportFileName);
    }

    @Test
    public void exportTargetPickedPersistsAndReportsSummary() {
        Fixture fixture = new Fixture();
        fixture.gateway.exportSummary = new LineCodeArchiveController.ExportSummary(3, 2, 5);

        fixture.controller.exportTargetPicked("content://backup");

        Assert.assertTrue(fixture.host.persistedBeforeExport);
        Assert.assertEquals("content://backup", fixture.gateway.exportUri);
        Assert.assertEquals("linecode-export", fixture.runner.lastTaskName);
        Assert.assertEquals("已导出 .linecode：3 个会话，2 个模型，5 项设置。", fixture.host.notice);
    }

    @Test
    public void exportTargetPickedIgnoresBlankUri() {
        Fixture fixture = new Fixture();

        fixture.controller.exportTargetPicked(" ");

        Assert.assertFalse(fixture.host.persistedBeforeExport);
        Assert.assertEquals("", fixture.gateway.exportUri);
        Assert.assertEquals("", fixture.host.notice);
    }

    @Test
    public void importPickedConfirmsAndConfirmImportRunsLifecycle() {
        Fixture fixture = new Fixture();
        fixture.gateway.importSummary = new LineCodeArchiveController.ImportSummary(7, 4, 9, 11);

        fixture.controller.importPicked("content://restore", "backup.linecode");
        fixture.controller.confirmImport();

        Assert.assertEquals("backup.linecode", fixture.host.importConfirmationSourceName);
        Assert.assertTrue(fixture.host.beforeImport);
        Assert.assertTrue(fixture.host.afterImport);
        Assert.assertEquals("content://restore", fixture.gateway.importUri);
        Assert.assertEquals("linecode-import", fixture.runner.lastTaskName);
        Assert.assertEquals("已导入 .linecode：7 个会话，4 个模型，9 项设置，11 个工作区文件。", fixture.host.notice);
    }

    @Test
    public void importCancelledClearsPendingImport() {
        Fixture fixture = new Fixture();

        fixture.controller.importPicked("content://restore", "");
        fixture.controller.importCancelled();
        fixture.controller.confirmImport();

        Assert.assertEquals(".linecode 文件", fixture.host.importConfirmationSourceName);
        Assert.assertFalse(fixture.host.beforeImport);
        Assert.assertEquals("", fixture.gateway.importUri);
    }

    @Test
    public void importFailureReportsErrorWithoutReloading() {
        Fixture fixture = new Fixture();
        fixture.gateway.importError = new IllegalStateException("boom");

        fixture.controller.importPicked("content://restore", "");
        fixture.controller.confirmImport();

        Assert.assertTrue(fixture.host.beforeImport);
        Assert.assertFalse(fixture.host.afterImport);
        Assert.assertEquals("导入 .linecode 失败: boom", fixture.host.notice);
    }

    private static final class Fixture {
        private final FakeArchiveGateway gateway = new FakeArchiveGateway();
        private final FakeHost host = new FakeHost();
        private final RecordingRunner runner = new RecordingRunner();
        private final LineCodeArchiveController controller = new LineCodeArchiveController(
                gateway,
                host,
                runner,
                Runnable::run,
                () -> 123456L
        );
    }

    private static final class FakeArchiveGateway implements LineCodeArchiveController.ArchiveGateway {
        private LineCodeArchiveController.ExportSummary exportSummary =
                new LineCodeArchiveController.ExportSummary(0, 0, 0);
        private LineCodeArchiveController.ImportSummary importSummary =
                new LineCodeArchiveController.ImportSummary(0, 0, 0, 0);
        private Exception exportError;
        private Exception importError;
        private String exportUri = "";
        private String importUri = "";

        @Override
        public LineCodeArchiveController.ExportSummary exportArchive(String uri) throws Exception {
            exportUri = uri;
            if (exportError != null) {
                throw exportError;
            }
            return exportSummary;
        }

        @Override
        public LineCodeArchiveController.ImportSummary importArchive(String uri) throws Exception {
            importUri = uri;
            if (importError != null) {
                throw importError;
            }
            return importSummary;
        }
    }

    private static final class FakeHost implements LineCodeArchiveController.Host {
        private String exportFileName = "";
        private boolean persistedBeforeExport;
        private String importConfirmationSourceName = "";
        private boolean beforeImport;
        private boolean afterImport;
        private String notice = "";

        @Override
        public void openExportPicker(String fileName) {
            exportFileName = fileName;
        }

        @Override
        public void persistBeforeExport() {
            persistedBeforeExport = true;
        }

        @Override
        public void openImportPicker() {
        }

        @Override
        public void showImportConfirmation(String sourceName) {
            importConfirmationSourceName = sourceName;
        }

        @Override
        public void beforeImport() {
            beforeImport = true;
        }

        @Override
        public void afterImport() {
            afterImport = true;
        }

        @Override
        public void showNotice(String text) {
            notice = text;
        }
    }

    private static final class RecordingRunner implements LineCodeArchiveController.BackgroundRunner {
        private String lastTaskName = "";

        @Override
        public void execute(String name, Runnable runnable) {
            lastTaskName = name;
            runnable.run();
        }
    }
}
