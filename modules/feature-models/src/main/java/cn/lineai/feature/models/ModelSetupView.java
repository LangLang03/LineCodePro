package cn.lineai.feature.models;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelPreset;
import cn.lineai.data.api.ModelStore;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Connection settings for the active model.
 *
 * <p>One screen, one job: edit the endpoint/key/model triple and persist it. The chat reads the
 * same keys through {@code ChatController}, so saving here takes effect on the next send.</p>
 */
public final class ModelSetupView extends LinearLayout {

    public interface Listener {
        void onBack();

        void onSaved();
    }

    private final ModelStore models;
    private final ModelConfig existing;
    private final ModelPreset preset;
    private final EditText baseUrl;
    private final EditText apiKey;
    private final EditText modelId;
    private final EditText displayName;
    private final EditText contextSize;
    private final EditText maxTokens;
    private Listener listener;
    private Tokens tokens;

    public ModelSetupView(Context context, ModelStore models, Tokens tokens) {
        this(context, models, null, null, tokens);
    }

    public ModelSetupView(Context context, ModelStore models, ModelConfig existing,
                          ModelPreset preset, Tokens tokens) {
        super(context);
        this.models = models;
        this.existing = existing;
        this.preset = preset;
        this.tokens = tokens;
        setOrientation(VERTICAL);
        setBackgroundColor(tokens.bg);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(Ui.dp(context, 56));
        Ui.padding(header, 4, 2, 8, 2);
        IconButton back = new IconButton(context, Icons.CHEVRON_LEFT);
        back.setIconColor(tokens.textSecondary);
        back.setIconSizeDp(40, 19);
        back.setContentDescription(context.getString(R.string.common_back));
        back.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBack();
            }
        });
        header.addView(back, new LayoutParams(Ui.dp(context, 40), Ui.dp(context, 48)));
        TextView title = Ui.medium(context, context.getString(R.string.screen_model_add_title_edit),
                Tokens.FONT_LG, tokens.text);
        header.addView(title, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout form = new LinearLayout(context);
        form.setOrientation(VERTICAL);
        Ui.padding(form, 16, 8, 16, 24);
        scroll.addView(form, new android.widget.ScrollView.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        String name = existing == null ? "" : existing.name;
        String url = existing == null ? (preset == null ? "" : preset.baseUrl) : existing.baseUrl;
        String key = existing == null ? "" : existing.apiKey;
        String id = existing == null ? "" : existing.modelId;
        baseUrl = field(form, R.string.screen_model_add_field_base_url,
                url, InputType.TYPE_TEXT_VARIATION_URI);
        apiKey = field(form, R.string.screen_model_add_field_api_key,
                key, InputType.TYPE_TEXT_VARIATION_PASSWORD);
        modelId = field(form, R.string.screen_model_add_field_model_id,
                id, InputType.TYPE_CLASS_TEXT);
        displayName = field(form, R.string.screen_model_add_field_name,
                name, InputType.TYPE_CLASS_TEXT);
        contextSize = field(form, R.string.model_field_context_size,
                existing == null ? "128000" : String.valueOf(existing.contextSize),
                InputType.TYPE_CLASS_NUMBER);
        maxTokens = field(form, R.string.screen_model_add_field_tool_call_limit,
                existing == null ? "4096" : String.valueOf(existing.maxTokens),
                InputType.TYPE_CLASS_NUMBER);

        TextView save = Ui.medium(context, context.getString(R.string.common_save),
                Tokens.FONT_MD, tokens.textOnColor);
        save.setGravity(Gravity.CENTER);
        save.setBackground(Ui.rounded(context, tokens.accent, 12));
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, Ui.dp(context, 48));
        saveParams.topMargin = Ui.dp(context, 24);
        form.addView(save, saveParams);
    }

    private EditText field(LinearLayout parent, int labelRes, String value, int inputType) {
        TextView label = Ui.text(getContext(), getContext().getString(labelRes),
                Tokens.FONT_SM, tokens.textSecondary, Typeface.NORMAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = Ui.dp(getContext(), 16);
        labelParams.bottomMargin = Ui.dp(getContext(), 6);
        parent.addView(label, labelParams);

        EditText input = new EditText(getContext());
        input.setText(value == null ? "" : value);
        input.setTextSize(14);
        input.setTextColor(tokens.text);
        input.setHintTextColor(tokens.textTertiary);
        input.setBackground(Ui.rounded(getContext(), tokens.inputBg, 12));
        input.setInputType(inputType);
        input.setSingleLine(true);
        input.setIncludeFontPadding(false);
        Ui.padding(input, 12, 10, 12, 10);
        parent.addView(input, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return input;
    }

    public void setListener(Listener value) {
        listener = value;
    }

    private void save() {
        String url = text(baseUrl);
        String id = text(modelId);
        if (url.isEmpty() || id.isEmpty()) {
            android.widget.Toast.makeText(getContext(), R.string.screen_model_add_test_missing,
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        String name = text(displayName).isEmpty() ? id : text(displayName);
        ModelConfig.Builder builder = ModelConfig.builder()
                .identity(existing == null ? Ids.prefixed("model") : existing.id, name)
                .endpoint(preset == null ? "custom" : preset.id, id, url)
                .secret(text(apiKey))
                .context(number(contextSize, 128000), number(maxTokens, 4096))
                .flags(true, true);
        if (existing != null) {
            builder.protocol(existing.protocol);
            builder.sampling(existing.temperature, existing.reasoningEffort);
            builder.capabilities(existing.supportsVision, existing.supportsTools);
        } else if (preset != null) {
            builder.protocol(preset.protocol);
        }
        models.save(builder.build());
        if (listener != null) listener.onSaved();
    }

    private int number(EditText input, int fallback) {
        try {
            return Integer.parseInt(text(input));
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static String text(EditText input) {
        return input.getText().toString().trim();
    }

    public void applyTokens(Tokens current) {
        tokens = current;
        setBackgroundColor(current.bg);
    }
}
