package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;

import android.content.Context;
import android.widget.LinearLayout;
import cn.lineai.R;

public final class DataSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onExport();

        void onImport();
    }

    public DataSettingsScreenView(Context context, Listener listener) {
        super(context, context.getString(R.string.screen_data_title), listener::onBack, null);
        LinearLayout content = getContent();

        SettingsSectionView archive = new SettingsSectionView(context, context.getString(R.string.screen_data_section_all));
        archive.addRow(new ActionRowView(context, IconButtonView.DOWNLOAD, context.getString(R.string.screen_data_export_all), context.getString(R.string.screen_data_export_all_desc), false, true, listener::onExport), true);
        archive.addRow(new ActionRowView(context, IconButtonView.UPLOAD, context.getString(R.string.screen_data_import_linecode), context.getString(R.string.screen_data_import_linecode_desc), false, true, listener::onImport), false);
        content.addView(archive, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
}
