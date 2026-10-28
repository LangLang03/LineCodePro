package cn.lineai.runtime;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import cn.lineai.feature.settings.ToolPreferences;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Model picker, probe and result dialogs for tool settings and model tests. */
public final class ModelPickerFactory {

    private ModelPickerFactory() {
    }

    public static void pick(PageHost host, boolean understanding) {
        java.util.List<cn.lineai.core.model.ModelConfig> models =
                host.graph().models().list();
        Sheets.showBottom(host.activity(), host.tokens(), panel(host, models, understanding));
    }

    private static View panel(PageHost host, java.util.List<cn.lineai.core.model.ModelConfig> models,
                              boolean understanding) {
        Tokens tokens = host.tokens();
        LinearLayout panel = new LinearLayout(host.activity());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.roundedTop(host.activity(), tokens.surfaceElevated, 16));
        TextView title = Ui.bold(host.activity(), host.activity().getString(
                understanding ? R.string.screen_tools_image_understanding_label
                        : R.string.screen_tools_image_generation_label), Tokens.FONT_LG, tokens.text);
        Ui.padding(title, 16, 16, 16, 12);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView none = Ui.text(host.activity(),
                host.activity().getString(R.string.screen_tools_no_model_selected),
                Tokens.FONT_MD, tokens.textSecondary, Typeface.NORMAL);
        Ui.padding(none, 16, 14, 16, 14);
        none.setOnClickListener(v -> {
            save(host, understanding, "");
            dismiss(panel);
        });
        panel.addView(none, new LinearLayout.LayoutParams(-1, -2));
        for (cn.lineai.core.model.ModelConfig model : models) {
            TextView row = Ui.text(host.activity(), model.displayName(), Tokens.FONT_MD,
                    tokens.text, Typeface.NORMAL);
            Ui.padding(row, 16, 14, 16, 14);
            row.setOnClickListener(v -> {
                save(host, understanding, model.id);
                dismiss(panel);
            });
            panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        return panel;
    }

    public static void save(PageHost host, boolean understanding, String id) {
        ToolPreferences preferences = new ToolPreferences(host.graph().settings());
        if (understanding) preferences.setImageUnderstandingModelId(id);
        else preferences.setImageGenerationModelId(id);
        host.navigation().back();
        host.navigation().push(cn.lineai.feature.settings.SettingsScreens.TOOL_SETTINGS
                .key(cn.lineai.core.mvi.nav.Params.Empty.INSTANCE));
    }

    public static void probe(PageHost host, cn.lineai.core.model.ModelConfig model) {
        long start = System.currentTimeMillis();
        cn.lineai.core.kernel.time.Scheduler scheduler =
                host.graph().graph().get(cn.lineai.core.kernel.time.Scheduler.class);
        scheduler.io(() -> {
            try {
                String raw = new cn.lineai.engine.ai.ModelProbe().complete(model);
                long duration = System.currentTimeMillis() - start;
                String summary = host.activity().getString(raw.isEmpty()
                        ? R.string.screen_model_add_test_success_no_data
                        : R.string.screen_model_add_test_success, duration);
                String message = host.activity().getString(
                        R.string.screen_model_add_test_result_body, summary,
                        host.activity().getString(R.string.screen_model_add_test_raw_response),
                        raw);
                scheduler.main(() -> showResult(host, message));
            } catch (cn.lineai.core.kernel.error.DomainException error) {
                long duration = System.currentTimeMillis() - start;
                scheduler.main(() -> Toast.makeText(host.activity(),
                        host.activity().getString(R.string.screen_model_add_test_error_duration,
                                host.activity().getString(R.string.screen_model_add_test_error,
                                        error.detail()), duration),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private static void showResult(PageHost host, String message) {
        Tokens tokens = host.tokens();
        Dialog dialog = new Dialog(host.activity());
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(host.activity());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.roundedTop(host.activity(), tokens.bg, 24));
        Ui.padding(panel, 24, 22, 24, 24);
        panel.addView(Ui.medium(host.activity(),
                host.activity().getString(R.string.screen_model_add_test_result_title),
                Tokens.FONT_LG, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        TextView body = Ui.text(host.activity(), message, Tokens.FONT_SM,
                tokens.textSecondary, Typeface.NORMAL);
        body.setTextIsSelectable(true);
        Ui.padding(body, 0, 12, 0, 12);
        panel.addView(body, new LinearLayout.LayoutParams(-1, -2));
        TextView confirm = Ui.medium(host.activity(),
                host.activity().getString(R.string.screen_model_add_test_result_confirm),
                Tokens.FONT_MD, tokens.accent);
        confirm.setGravity(android.view.Gravity.END);
        confirm.setPadding(Ui.dp(host.activity(), 12), Ui.dp(host.activity(), 10),
                Ui.dp(host.activity(), 12), Ui.dp(host.activity(), 10));
        confirm.setOnClickListener(v -> dialog.dismiss());
        panel.addView(confirm, new LinearLayout.LayoutParams(-1, -2));
        dialog.setContentView(panel);
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(Ui.dp(host.activity(), 320), -2);
        }
    }

    static void dismiss(View panel) {
        ViewParent parent = panel.getParent();
        while (parent != null && !(parent instanceof Dialog)) {
            parent = parent.getParent();
        }
        if (parent instanceof Dialog) ((Dialog) parent).dismiss();
    }
}
