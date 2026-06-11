package cn.lineai.ui.component;

import android.content.Context;
import android.widget.LinearLayout;

public final class DataSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onExport();

        void onImport();
    }

    public DataSettingsScreenView(Context context, Listener listener) {
        super(context, "数据管理", listener::onBack, null);
        LinearLayout content = getContent();

        SettingsSectionView archive = new SettingsSectionView(context, "全量数据");
        archive.addRow(new ActionRowView(context, IconButtonView.DOWNLOAD, "导出所有数据", "导出数据库、聊天、设置和 .linecode 工作区为 ZIP", false, true, listener::onExport), true);
        archive.addRow(new ActionRowView(context, IconButtonView.UPLOAD, "导入 .linecode", "从 ZIP 覆盖恢复数据库、聊天、配置和工作区文件", false, true, listener::onImport), false);
        content.addView(archive, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
}
