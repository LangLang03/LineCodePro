package cn.lineai.feature.models;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Form for a local GGUF model backed by the bundled llama.cpp runtime. */
public final class LocalModelPage extends SettingsPage {

    public interface Listener {
        void onPickFile();

        void onSave(ModelConfig model);

        void onDelete(String id);
    }

    private final Listener listener;
    private final ModelConfig editing;
    private final FormField name;
    private final TextView fileLabel;
    private final LinearLayout accelerationRow;
    private String modelPath;
    private String acceleration;

    public LocalModelPage(Context context, Tokens tokens, Runnable back, ModelConfig editing,
                          boolean gpuAvailable, Listener listener) {
        super(context, R.string.screen_model_add_options_local, tokens, back);
        this.listener = listener;
        this.editing = editing == null ? null : editing;
        this.modelPath = editing == null ? "" : editing.localModelPath;
        this.acceleration = editing == null || editing.localAcceleration.isEmpty()
                ? "automatic" : editing.localAcceleration;
        Ui.padding(content, 16, 16, 16, 100);

        name = new FormField(context, theme, R.string.screen_model_add_field_name,
                editing == null ? "" : editing.name,
                context.getString(R.string.screen_model_add_hint_local_name), false);
        content.addView(name, new LinearLayout.LayoutParams(-1, -2));

        TextView fileTitle = Ui.medium(context,
                context.getString(R.string.screen_model_add_local_field_file),
                Tokens.FONT_SM, tokens.textSecondary);
        LinearLayout.LayoutParams fileTitleParams = new LinearLayout.LayoutParams(-1, -2);
        fileTitleParams.topMargin = Ui.dp(context, 16);
        content.addView(fileTitle, fileTitleParams);
        fileLabel = Ui.text(context, modelPath.isEmpty()
                        ? context.getString(R.string.screen_model_add_local_pick_file_desc)
                        : modelPath,
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        fileLabel.setLineSpacing(Ui.dp(context, 3), 1f);
        fileLabel.setBackground(Ui.roundedStroke(context, tokens.inputBg, 10, tokens.border));
        Ui.padding(fileLabel, 12, 10, 12, 10);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = Ui.dp(context, 6);
        content.addView(fileLabel, labelParams);
        TextView pick = actionButton(R.string.screen_model_add_local_pick_file_label, true,
                () -> listener.onPickFile());
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
        pickParams.topMargin = Ui.dp(context, 10);
        content.addView(pick, pickParams);

        TextView accelerationTitle = Ui.medium(context,
                context.getString(R.string.screen_model_add_acceleration_label),
                Tokens.FONT_SM, tokens.textSecondary);
        LinearLayout.LayoutParams accelerationTitleParams =
                new LinearLayout.LayoutParams(-1, -2);
        accelerationTitleParams.topMargin = Ui.dp(context, 18);
        content.addView(accelerationTitle, accelerationTitleParams);
        accelerationRow = new LinearLayout(context);
        accelerationRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams accelerationParams = new LinearLayout.LayoutParams(-1, -2);
        accelerationParams.topMargin = Ui.dp(context, 8);
        content.addView(accelerationRow, accelerationParams);
        addAcceleration("automatic", R.string.screen_model_add_acceleration_auto);
        addAcceleration("cpu", R.string.screen_model_add_acceleration_cpu);
        if (gpuAvailable) addAcceleration("gpu", R.string.screen_model_add_acceleration_gpu);

        TextView save = actionButton(R.string.common_save, true, this::save);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 46));
        saveParams.topMargin = Ui.dp(context, 22);
        content.addView(save, saveParams);
        if (editing != null && !editing.id.isEmpty()) {
            TextView delete = actionButton(R.string.common_delete, false, () ->
                    Dialogs.confirm(context, theme.current(),
                            context.getString(R.string.common_delete),
                            getContext().getString(R.string.screen_agent_delete_message,
                                    editing.name),
                            context.getString(R.string.common_cancel),
                            context.getString(R.string.common_delete),
                            confirmed -> {
                                if (confirmed) listener.onDelete(editing.id);
                            }));
            delete.setTextColor(theme.current().danger);
            LinearLayout.LayoutParams deleteParams =
                    new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
            deleteParams.topMargin = Ui.dp(context, 8);
            content.addView(delete, deleteParams);
        }
        renderAcceleration();
    }

    public void setModelPath(String path) {
        modelPath = path == null ? "" : path;
        fileLabel.setText(modelPath.isEmpty()
                ? getContext().getString(R.string.screen_model_add_local_pick_file_desc)
                : modelPath);
        if (name.value().isEmpty() && !modelPath.isEmpty()) {
            name.input().setText(new File(modelPath).getName());
        }
    }

    private void addAcceleration(String value, int label) {
        TextView chip = Ui.medium(getContext(), getContext().getString(label),
                Tokens.FONT_SM, theme.current().textSecondary);
        chip.setGravity(Gravity.CENTER);
        chip.setClickable(true);
        chip.setOnClickListener(v -> {
            acceleration = value;
            renderAcceleration();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Ui.dp(getContext(), 38), 1f);
        params.rightMargin = Ui.dp(getContext(), 6);
        accelerationRow.addView(chip, params);
    }

    private void renderAcceleration() {
        Tokens tokens = theme.current();
        for (int i = 0; i < accelerationRow.getChildCount(); i++) {
            TextView chip = (TextView) accelerationRow.getChildAt(i);
            boolean active = isActive(i);
            chip.setTextColor(active ? tokens.textOnColor : tokens.textSecondary);
            chip.setBackground(active
                    ? Ui.rounded(getContext(), tokens.accent, 10)
                    : Ui.roundedStroke(getContext(), tokens.surfaceLight, 10, tokens.border));
        }
    }

    private boolean isActive(int index) {
        String value = index == 0 ? "automatic" : index == 1 ? "cpu" : "gpu";
        return value.equals(acceleration);
    }

    private void save() {
        if (modelPath.isEmpty()) {
            Toast.makeText(getContext(), R.string.screen_model_add_local_pick_file_desc,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String label = name.value().isEmpty()
                ? new File(modelPath).getName() : name.value();
        String id = editing == null || editing.id.isEmpty()
                ? "local-" + System.currentTimeMillis() : editing.id;
        ModelConfig model = ModelConfig.builder()
                .identity(id, label)
                .endpoint("local", new File(modelPath).getName(), "")
                .protocol(ModelConfig.Protocol.LOCAL_GGUF)
                .context(4096, 2048)
                .local(modelPath, acceleration)
                .capabilities(false, false)
                .toolCalls(0)
                .build();
        listener.onSave(model);
    }

    private TextView actionButton(int label, boolean primary, Runnable action) {
        Context context = getContext();
        Tokens tokens = theme.current();
        TextView button = Ui.bold(context, context.getString(label), Tokens.FONT_MD,
                primary ? tokens.textOnColor : tokens.textSecondary);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(primary ? Ui.rounded(context, tokens.accent, 12)
                : Ui.roundedStroke(context, tokens.surfaceLight, 12, tokens.border));
        button.setOnClickListener(v -> action.run());
        return button;
    }
}
