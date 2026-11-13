package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.data.api.ErrorLog;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.Refreshable;
import cn.lineai.ui.kit.ScreenBar;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Failure log list: every row shows the failure reason and its date, and tapping the row writes
 * the entry to a text file that is opened in another app instead of showing a detail dialog.
 */
public final class ErrorLogsPage extends SettingsPage implements Refreshable {

    /** Data operations supplied by the host; must be safe to call from the I/O scheduler. */
    public interface Listener {
        /**
         * Loads all recorded failure entries.
         *
         * @return current error log entries; never null
         */
        List<ErrorLog> load();

        /** Deletes every recorded failure entry. */
        void clear();
    }

    private final Listener listener;
    private final Scheduler scheduler;

    /**
     * Creates the page without a scheduler; content stays in the loading state until
     * {@link #render} is called externally.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param listener data operations for loading and clearing logs
     */
    public ErrorLogsPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        this(context, tokens, back, null, listener);
    }

    /**
     * Creates the page; a scheduler lets the page load itself, including failure and retry states.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param scheduler scheduler enforcing the I/O-to-main threading contract; may be null
     * @param listener data operations for loading and clearing logs
     */
    public ErrorLogsPage(Context context, Tokens tokens, Runnable back, Scheduler scheduler,
                         Listener listener) {
        super(context, R.string.screen_error_logs_title, tokens, back, Icons.TRASH_2,
                R.string.common_clear, () -> clear(context, listener));
        this.listener = listener;
        this.scheduler = scheduler;
        if (getChildAt(0) instanceof ScreenBar) {
            ScreenBar bar = (ScreenBar) getChildAt(0);
            bar.rightTint(theme.current().danger).iconSizes(22, 20);
            theme.bind(t -> bar.rightTint(t.danger));
        }
        if (scheduler == null) {
            renderLoading();
        } else {
            load();
        }
    }

    /** Reloads the log list on every show so newly recorded failures appear. */
    @Override public void onShown() {
        if (scheduler != null) load();
    }

    /** Loads asynchronously on I/O and renders loading, failed, or ready states on main. */
    private void load() {
        renderLoading();
        scheduler.io(() -> {
            List<ErrorLog> logs;
            try {
                logs = listener.load();
            } catch (RuntimeException error) {
                scheduler.main(this::renderFailed);
                return;
            }
            scheduler.main(() -> render(logs));
        });
    }

    /**
     * Renders the log list; each row opens its entry as a text file in another app.
     *
     * @param logs entries to display; null or empty shows the empty-state message
     */
    public void render(List<ErrorLog> logs) {
        content.removeAllViews();
        if (logs == null || logs.isEmpty()) {
            content.addView(message(R.string.screen_error_logs_empty), new LayoutParams(-1, -2));
            return;
        }
        SettingsSection section = section(R.string.screen_error_logs_section_title);
        for (int index = 0; index < logs.size(); index++) {
            ErrorLog entry = logs.get(index);
            PreferenceRow row = new PreferenceRow(getContext(), theme, PreferenceRow.Style.ACTION,
                    Icons.FILE_TEXT, R.string.screen_error_logs_title,
                    R.string.screen_error_logs_empty);
            row.titleText(entry.title);
            row.descriptionText(ErrorLogFiles.dateLine(getContext(), entry));
            row.setOnClickListener(v -> ErrorLogFiles.open(getContext(), entry));
            section.row(row, index < logs.size() - 1, 68);
        }
    }

    /** Replaces the content with the loading placeholder. */
    public void renderLoading() {
        content.removeAllViews();
        content.addView(message(R.string.screen_error_logs_loading), new LayoutParams(-1, -2));
    }

    /** Replaces the content with the failure message and, when possible, a retry action. */
    public void renderFailed() {
        content.removeAllViews();
        content.addView(message(R.string.screen_error_logs_load_failed), new LayoutParams(-1, -2));
        if (scheduler == null) return;
        TextView retry = Ui.medium(getContext(), getContext().getString(R.string.common_refresh),
                Tokens.FONT_SM, theme.current().accent);
        retry.setGravity(Gravity.CENTER);
        retry.setFocusable(true);
        retry.setMinimumHeight(Ui.dp(getContext(), 36));
        Ui.padding(retry, 16, 8, 16, 8);
        retry.setOnClickListener(v -> load());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(retry, params);
    }

    /** Builds a centered placeholder message view. */
    private TextView message(int text) {
        TextView view = Ui.text(getContext(), getContext().getString(text),
                Tokens.FONT_MD, theme.current().textTertiary, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        Ui.padding(view, 16, 32, 16, 32);
        return view;
    }

    /** Clears all logs via the listener and reports the outcome with a toast. */
    private static void clear(Context context, Listener listener) {
        try {
            listener.clear();
            Toast.makeText(context, R.string.screen_error_logs_cleared, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException error) {
            Toast.makeText(context, R.string.screen_error_logs_clear_failed,
                    Toast.LENGTH_SHORT).show();
        }
    }
}
