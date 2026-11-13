package cn.lineai.feature.settings;

import android.content.Context;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.tokens.Tokens;

/** Export / import of the whole data archive. */
/** Export / import of the whole data archive. */
public final class DataSettingsPage extends SettingsPage {

    /** Host callbacks for the archive operations. */
    public interface Listener {
        /** Starts the full data export flow. */
        void onExport();

        /** Starts the archive import flow. */
        void onImport();
    }

    /**
     * Builds the data settings screen.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param listener host callbacks for export and import
     */
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
