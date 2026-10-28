package cn.lineai.feature.settings;

import android.content.Context;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.tokens.Tokens;

/** Export / import of the whole data archive. */
public final class DataSettingsPage extends SettingsPage {

    public interface Listener {
        void onExport();

        void onImport();
    }

    public DataSettingsPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, R.string.screen_data_title, tokens, back);
        PreferenceRow export = new PreferenceRow(context, theme, PreferenceRow.Style.ACTION,
                Icons.DOWNLOAD, R.string.screen_data_export_all,
                R.string.screen_data_export_all_desc);
        export.setOnClickListener(v -> listener.onExport());
        PreferenceRow importRow = new PreferenceRow(context, theme, PreferenceRow.Style.ACTION,
                Icons.UPLOAD, R.string.screen_data_import_linecode,
                R.string.screen_data_import_linecode_desc);
        importRow.setOnClickListener(v -> listener.onImport());
        SettingsSection section = section(R.string.screen_data_section_all);
        section.row(export, true, 68);
        section.row(importRow, false, 68);
    }
}
