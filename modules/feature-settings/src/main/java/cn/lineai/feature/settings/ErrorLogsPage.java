package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import cn.lineai.data.api.ErrorLog;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.ScreenBar;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Failure log list with a clear action and a detail viewer. */
public final class ErrorLogsPage extends SettingsPage {

    public interface Listener {
        List<ErrorLog> load();

        void clear();
    }

    private final Listener listener;

    public ErrorLogsPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, R.string.screen_error_logs_title, tokens, back, Icons.TRASH_2,
                R.string.common_clear, () -> {
                    listener.clear();
                    Toast.makeText(context, R.string.screen_error_logs_cleared,
                            Toast.LENGTH_SHORT).show();
                });
        this.listener = listener;
        render(java.util.Collections.<ErrorLog>emptyList());
    }

    public void render(List<ErrorLog> logs) {
        content.removeAllViews();
        if (logs == null || logs.isEmpty()) {
            TextView empty = Ui.text(getContext(), getContext().getString(R.string.screen_error_logs_empty),
                    Tokens.FONT_MD, theme.current().textTertiary, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            Ui.padding(empty, 16, 24, 16, 24);
            content.addView(empty, new LayoutParams(-1, -2));
            return;
        }
        SettingsSection section = section(R.string.screen_error_logs_section_title);
        for (int i = 0; i < logs.size(); i++) {
            ErrorLog entry = logs.get(i);
            PreferenceRow row = new PreferenceRow(getContext(), theme, PreferenceRow.Style.ACTION,
                    Icons.FILE_TEXT, R.string.screen_error_logs_title, R.string.screen_error_logs_empty);
            row.titleText(entry.title);
            row.descriptionText(entry.subtitle);
            row.setOnClickListener(v -> open(entry));
            section.row(row, i < logs.size() - 1, 68);
        }
    }

    private void open(ErrorLog entry) {
        Dialogs.confirm(getContext(), theme.current(), entry.title,
                entry.detail.isEmpty() ? entry.subtitle : entry.detail,
                getContext().getString(R.string.common_close),
                getContext().getString(R.string.common_close),
                confirmed -> { });
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }
}
