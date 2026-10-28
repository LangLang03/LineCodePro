package cn.lineai.feature.models;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ContextSizes;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelPreset;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.ScreenBar;
import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.ThemeBinder;
import cn.lineai.ui.tokens.Tokens;

/** Add/edit model form, ported from the legacy {@code ModelAddScreenView}. */
public final class ModelEditPage extends LinearLayout {

    public interface Listener {
        void onBack();

        void onSave(ModelConfig model);

        void onTest(ModelConfig model);

        default boolean localModelsAvailable() {
            return false;
        }
    }

    public interface Catalog {
        List<String> fetch(ModelConfig.Protocol protocol, String baseUrl, String apiKey) throws Exception;
    }

    private static final String[] PROVIDER_LABELS = {"OpenAI", "Codex", "Anthropic", "Local"};

    private final ThemeBinder theme;
    private final Scheduler scheduler;
    private final Listener listener;
    private final Catalog catalog;
    private final ModelForm form;
    private final ModelPreset preset;
    private final boolean lockedPreset;
    private final String providerLabel;
    private final TextView saveAction;
    private final TextView testAction;
    private final TextView providerLabelView;
    private final LinearLayout providerRow;
    private final EditText nameInput;
    private final EditText baseUrlInput;
    private final EditText apiKeyInput;
    private final EditText modelIdInput;
    private final EditText toolLimitInput;
    private final EditText contextInput;
    private final LinearLayout modelInputHost;
    private final TextView pickLabel;
    private final LinearLayout queryButton;
    private final TextView queryText;
    private final IconButton queryIcon;
    private Switch customIdSwitch;
    private final TextView baseUrlHint;
    private final CompressionSection compression;
    private final ArrayList<String> fetched = new ArrayList<>();
    private boolean fetching;
    private String selectedId;

    public ModelEditPage(Context context, Tokens tokens, Scheduler scheduler, Runnable back,
                         ModelConfig editing, ModelPreset preset, Catalog catalog, Listener listener) {
        super(context);
        this.theme = new ThemeBinder(tokens);
        this.scheduler = scheduler;
        this.listener = listener;
        this.catalog = catalog;
        this.preset = preset;
        this.form = editing != null ? ModelForm.from(editing) : ModelForm.forPreset(preset);
        this.lockedPreset = preset != null || editing != null;
        this.providerLabel = !form.providerLabel.isEmpty() ? form.providerLabel
                : preset == null ? null : PresetText.label(context, preset.id);
        setOrientation(VERTICAL);
        theme.bind(t -> setBackgroundColor(t.bg));
        Tokens t0 = tokens;

        saveAction = Ui.medium(context, context.getString(R.string.common_save), Tokens.FONT_MD, t0.textTertiary);
        saveAction.setGravity(Gravity.CENTER);
        Ui.padding(saveAction, 12, 8, 12, 8);
        testAction = Ui.medium(context, context.getString(R.string.screen_model_add_test_button), Tokens.FONT_MD, t0.accent);
        testAction.setGravity(Gravity.CENTER);
        Ui.padding(testAction, 12, 8, 12, 8);
        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(testAction, new LayoutParams(-2, -2));
        LayoutParams saveParams = new LayoutParams(-2, -2);
        saveParams.leftMargin = Ui.dp(context, 8);
        actions.addView(saveAction, saveParams);

        ScreenBar bar = new ScreenBar(context, theme,
                editing != null ? R.string.screen_model_add_title_edit : R.string.screen_model_add_title_add,
                Icons.CHEVRON_LEFT, R.string.common_back, back, 0, 0, null);
        bar.addView(actions, new LayoutParams(-2, -2));
        addView(bar, new LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        Ui.padding(content, 16, 16, 16, 16);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        addView(scroll, new LayoutParams(-1, 0, 1f));

        providerLabelView = FormFields.label(context, t0, providerTitle());
        content.addView(providerLabelView, FormFields.labelParams(context, 16, 8));
        providerRow = new LinearLayout(context);
        for (int i = 0; i < PROVIDER_LABELS.length; i++) {
            if (i == 3 && !listener.localModelsAvailable()) continue;
            final int index = i;
            FormFields.addToggle(providerRow, t0, PROVIDER_LABELS[i], activeProvider(index),
                    !lockedPreset || activeProvider(index), () -> chooseProvider(index));
        }
        content.addView(providerRow, new LayoutParams(-1, -2));

        content.addView(FormFields.label(context, t0, context.getString(R.string.screen_model_add_field_name)),
                FormFields.labelParams(context, 16, 8));
        nameInput = FormFields.input(context, t0, form.name,
                context.getString(preset == null
                        ? R.string.screen_model_add_hint_remote_name
                        : R.string.screen_model_add_hint_name_optional), false, false);
        content.addView(nameInput, new LayoutParams(-1, -2));

        content.addView(FormFields.label(context, t0, context.getString(R.string.screen_model_add_field_base_url)),
                FormFields.labelParams(context, 16, 8));
        baseUrlInput = FormFields.input(context, t0, form.baseUrl,
                preset == null ? context.getString(R.string.screen_model_add_hint_base_url) : preset.baseUrl,
                false, false);
        content.addView(baseUrlInput, new LayoutParams(-1, -2));
        baseUrlHint = Ui.text(context, hint(), Tokens.FONT_XS, t0.textTertiary, Typeface.NORMAL);
        baseUrlHint.setLineSpacing(Ui.dp(context, 3), 1f);
        LayoutParams hintParams = new LayoutParams(-1, -2);
        hintParams.topMargin = Ui.dp(context, 8);
        content.addView(baseUrlHint, hintParams);

        content.addView(FormFields.label(context, t0, context.getString(R.string.screen_model_add_field_api_key)),
                FormFields.labelParams(context, 16, 8));
        apiKeyInput = FormFields.input(context, t0, form.apiKey,
                context.getString(R.string.screen_model_add_hint_api_key), false, true);
        content.addView(apiKeyInput, new LayoutParams(-1, -2));

        content.addView(modelIdHeader(context, t0), FormFields.labelParams(context, 16, 8));
        modelInputHost = new LinearLayout(context);
        modelInputHost.setOrientation(VERTICAL);
        content.addView(modelInputHost, new LayoutParams(-1, -2));
        modelIdInput = FormFields.input(context, t0, form.modelId,
                context.getString(R.string.screen_model_add_hint_model_id), false, false);
        pickLabel = Ui.text(context, context.getString(R.string.screen_model_add_pick_first),
                Tokens.FONT_MD, t0.textTertiary, Typeface.NORMAL);
        pickLabel.setSingleLine(true);
        pickLabel.setEllipsize(TextUtils.TruncateAt.END);
        queryButton = queryButton(context, t0);
        queryText = Ui.bold(context, context.getString(R.string.screen_model_add_query_button),
                Tokens.FONT_MD, t0.textTertiary);
        LayoutParams queryTextParams = new LayoutParams(-2, -2);
        queryTextParams.leftMargin = Ui.dp(context, 4);
        queryButton.addView(queryText, queryTextParams);
        queryIcon = (IconButton) queryButton.getChildAt(0);
        selectedId = editing == null ? "" : editing.modelId;
        renderModelInput(customIdSwitch.isChecked());

        content.addView(FormFields.label(context, t0, context.getString(R.string.screen_model_add_field_tool_call_limit)),
                FormFields.labelParams(context, 16, 8));
        toolLimitInput = FormFields.input(context, t0, form.toolCallLimit,
                context.getString(R.string.screen_model_add_hint_tool_call_limit), false, false);
        toolLimitInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        content.addView(toolLimitInput, new LayoutParams(-1, -2));
        content.addView(hintText(context, t0, R.string.screen_model_add_max_tool_calls_hint),
                FormFields.labelParams(context, 8, 0));

        content.addView(FormFields.label(context, t0, context.getString(R.string.model_field_context_size)),
                FormFields.labelParams(context, 16, 8));
        contextInput = FormFields.input(context, t0, form.contextSize,
                context.getString(R.string.model_field_context_size_hint), false, false);
        content.addView(contextInput, new LayoutParams(-1, -2));
        content.addView(hintText(context, t0, R.string.model_field_context_size_hint_desc),
                FormFields.labelParams(context, 8, 0));

        ModelConfig.Protocol[] protocol = {form.protocol};
        compression = new CompressionSection(context, t0, protocol, form.compressionEnabled,
                form.compressionAuto, form.compressionModelId,
                (type, url, key) -> catalog == null
                        ? Collections.<String>emptyList() : catalog.fetch(type, url, key),
                (ids, selected, picker) -> ModelPickerSheet.show(context, theme.current(), ids, selected, picker),
                scheduler, this::effectiveBaseUrl, () -> FormFields.value(apiKeyInput), this::updateSaveState);
        content.addView(compression, new LayoutParams(-1, -2));

        TextWatcher watcher = watcher();
        nameInput.addTextChangedListener(watcher);
        toolLimitInput.addTextChangedListener(watcher);
        contextInput.addTextChangedListener(watcher);
        modelIdInput.addTextChangedListener(watcher);
        baseUrlInput.addTextChangedListener(catalogWatcher());
        apiKeyInput.addTextChangedListener(catalogWatcher());
        customIdSwitch.setOnCheckedChangeListener((view, checked) -> {
            renderModelInput(checked);
            updateSaveState();
        });
        queryButton.setOnClickListener(v -> query());

        saveAction.setOnClickListener(v -> save());
        testAction.setOnClickListener(v -> test());
        updateSaveState();
    }

    private LinearLayout modelIdHeader(Context context, Tokens tokens) {
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(FormFields.label(context, tokens,
                context.getString(R.string.screen_model_add_field_model_id)), new LayoutParams(0, -2, 1f));
        LinearLayout wrap = new LinearLayout(context);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.addView(Ui.medium(context, context.getString(R.string.screen_model_add_custom_id_label),
                Tokens.FONT_SM, tokens.textSecondary), new LayoutParams(-2, -2));
        customIdSwitch = new Switch(context);
        FormFields.tintSwitch(customIdSwitch, tokens);
        customIdSwitch.setChecked(!form.id.isEmpty());
        LayoutParams params = new LayoutParams(-2, -2);
        params.leftMargin = Ui.dp(context, 8);
        wrap.addView(customIdSwitch, params);
        header.addView(wrap, new LayoutParams(-2, -2));
        return header;
    }

    private TextView hintText(Context context, Tokens tokens, int text) {
        TextView hint = Ui.text(context, context.getString(text), Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL);
        hint.setLineSpacing(Ui.dp(context, 3), 1f);
        return hint;
    }

    private LinearLayout queryButton(Context context, Tokens tokens) {
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setMinimumWidth(Ui.dp(context, 76));
        Ui.padding(button, 16, 0, 16, 0);
        IconButton icon = new IconButton(context, Icons.SEARCH);
        icon.setClickable(false);
        icon.setIconSizeDp(16, 16);
        button.addView(icon, new LayoutParams(Ui.dp(context, 16), Ui.dp(context, 16)));
        return button;
    }

    private TextWatcher watcher() {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSaveState();
            }
        };
    }

    private TextWatcher catalogWatcher() {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                fetched.clear();
                if (!customIdSwitch.isChecked()) {
                    selectedId = "";
                    renderModelInput(false);
                }
                updateSaveState();
                updateQueryState();
            }
        };
    }

    private boolean activeProvider(int index) {
        if (index == 3) return false;
        return form.protocol == protocolFor(index);
    }

    private ModelConfig.Protocol protocolFor(int index) {
        if (index == 1) return ModelConfig.Protocol.CODEX_RESPONSES;
        if (index == 2) return ModelConfig.Protocol.ANTHROPIC_MESSAGES;
        return ModelConfig.Protocol.OPENAI_COMPATIBLE;
    }

    private void chooseProvider(int index) {
        if (index == 3) {
            Toast.makeText(getContext(), R.string.screen_model_add_open_local_form, Toast.LENGTH_SHORT).show();
            return;
        }
        if (lockedPreset) return;
        form.protocol = protocolFor(index);
        fetched.clear();
        selectedId = "";
        modelIdInput.setText("");
        compression.clearFetched();
        refreshProviderRow();
        baseUrlHint.setText(hint());
        renderModelInput(customIdSwitch.isChecked());
        compression.updateForProtocolChange();
        updateQueryState();
        updateSaveState();
    }

    private void refreshProviderRow() {
        for (int i = 0; i < providerRow.getChildCount(); i++) {
            TextView button = (TextView) providerRow.getChildAt(i);
            boolean active = activeProvider(i);
            Tokens tokens = theme.current();
            button.setTextColor(active ? tokens.textOnColor : tokens.textSecondary);
            button.setBackground(Ui.rounded(getContext(), active ? tokens.accent : tokens.surfaceLight, 12));
            button.setAlpha(lockedPreset && !active ? 0.45f : 1f);
        }
        providerLabelView.setText(providerTitle());
    }

    private String providerTitle() {
        if (providerLabel != null) {
            return getContext().getString(R.string.model_form_provider_value, providerLabel);
        }
        if (lockedPreset) {
            return getContext().getString(R.string.model_form_provider_value,
                    PresetText.protocol(getContext(), form.protocol));
        }
        return getContext().getString(R.string.screen_model_add_provider_title);
    }

    private String hint() {
        if (preset != null) return PresetText.hint(getContext(), preset.id);
        if (form.protocol == ModelConfig.Protocol.CODEX_RESPONSES) {
            return getContext().getString(R.string.screen_model_add_url_codex);
        }
        if (form.protocol == ModelConfig.Protocol.ANTHROPIC_MESSAGES) {
            return getContext().getString(R.string.screen_model_add_url_anthropic);
        }
        return getContext().getString(R.string.screen_model_add_url_openai);
    }

    private String effectiveBaseUrl() {
        String value = FormFields.value(baseUrlInput);
        if (!value.isEmpty()) return value;
        if (preset != null && !preset.baseUrl.isEmpty()) return preset.baseUrl;
        return form.protocol == ModelConfig.Protocol.ANTHROPIC_MESSAGES
                ? "https://api.anthropic.com" : "https://api.openai.com/v1";
    }

    private void renderModelInput(boolean custom) {
        FormFields.detach(modelIdInput);
        FormFields.detach(pickLabel);
        FormFields.detach(queryButton);
        modelInputHost.removeAllViews();
        modelInputHost.setVisibility(VISIBLE);
        if (custom) {
            modelInputHost.addView(modelIdInput, new LayoutParams(-1, -2));
            updateQueryState();
            return;
        }
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout selector = new LinearLayout(context);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setBackground(Ui.roundedStroke(context, android.graphics.Color.TRANSPARENT, 12,
                theme.current().border));
        Ui.padding(selector, 16, 12, 16, 12);
        boolean has = !selectedId.isEmpty();
        pickLabel.setText(has ? selectedId : context.getString(R.string.screen_model_add_pick_first));
        pickLabel.setTextColor(has ? theme.current().text : theme.current().textTertiary);
        selector.addView(pickLabel, new LayoutParams(0, -2, 1f));
        IconButton down = new IconButton(context, Icons.CHEVRON_DOWN);
        down.setIconColor(theme.current().textTertiary);
        down.setIconSizeDp(16, 14);
        down.setClickable(false);
        selector.addView(down, new LayoutParams(Ui.dp(context, 16), Ui.dp(context, 16)));
        selector.setOnClickListener(v -> queryButton.performClick());
        row.addView(selector, new LayoutParams(0, Ui.dp(context, 48), 1f));
        LayoutParams queryParams = new LayoutParams(-2, Ui.dp(context, 48));
        queryParams.leftMargin = Ui.dp(context, 8);
        row.addView(queryButton, queryParams);
        modelInputHost.addView(row, new LayoutParams(-1, -2));
        updateQueryState();
    }

    private void query() {
        if (!canQuery() || fetching) return;
        if (!fetched.isEmpty()) {
            ModelPickerSheet.show(getContext(), theme.current(), fetched, selectedId, this::picked);
            return;
        }
        fetching = true;
        updateQueryState();
        final ModelConfig.Protocol type = form.protocol;
        final String url = effectiveBaseUrl();
        final String key = FormFields.value(apiKeyInput);
        scheduler.io(() -> {
            try {
                List<String> ids = catalog == null ? Collections.<String>emptyList()
                        : catalog.fetch(type, url, key);
                scheduler.main(() -> {
                    fetching = false;
                    fetched.clear();
                    if (ids != null) fetched.addAll(ids);
                    updateQueryState();
                    if (fetched.isEmpty()) {
                        Toast.makeText(getContext(), R.string.screen_model_add_fetch_failed,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    ModelPickerSheet.show(getContext(), theme.current(), fetched, selectedId, this::picked);
                });
            } catch (Exception error) {
                scheduler.main(() -> {
                    fetching = false;
                    updateQueryState();
                    Toast.makeText(getContext(), error.getMessage() == null
                                    ? getContext().getString(R.string.toast_query_failed)
                                    : error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private boolean canQuery() {
        return effectiveBaseUrl().length() > 0 && FormFields.value(apiKeyInput).length() > 0;
    }

    private void updateQueryState() {
        boolean can = canQuery() && !fetching && !customIdSwitch.isChecked();
        queryText.setText(fetching ? R.string.screen_model_add_query_button_loading
                : R.string.screen_model_add_query_button);
        queryText.setTextColor(can || fetching ? theme.current().textOnColor : theme.current().textTertiary);
        queryIcon.setIconColor(can || fetching ? theme.current().textOnColor : theme.current().textTertiary);
        queryButton.setBackground(Ui.rounded(getContext(), can || fetching
                ? theme.current().accent : theme.current().surfaceLight, 12));
        queryButton.setEnabled(can);
    }

    private void picked(String id, boolean custom) {
        if (custom) {
            selectedId = "";
            customIdSwitch.setChecked(true);
            modelIdInput.setText("");
            renderModelInput(true);
        } else {
            selectedId = id;
            if (nameInput.getText().toString().trim().isEmpty()) nameInput.setText(id);
            customIdSwitch.setChecked(false);
            renderModelInput(false);
        }
        updateSaveState();
    }

    private void syncForm() {
        form.name = FormFields.value(nameInput);
        form.baseUrl = effectiveBaseUrl();
        form.apiKey = FormFields.value(apiKeyInput);
        form.modelId = customIdSwitch.isChecked() ? FormFields.value(modelIdInput) : selectedId;
        form.toolCallLimit = FormFields.value(toolLimitInput);
        form.contextSize = FormFields.value(contextInput);
        form.compressionEnabled = compression.compressionEnabled();
        form.compressionAuto = compression.isAuto();
        form.compressionModelId = compression.modelId();
        form.providerId = preset == null ? "custom" : preset.id;
        form.providerLabel = providerLabel == null ? "" : providerLabel;
    }

    private void updateSaveState() {
        syncForm();
        boolean can = form.canSave();
        saveAction.setTextColor(can ? theme.current().accent : theme.current().textTertiary);
        saveAction.setAlpha(can ? 1f : 0.45f);
    }

    private void save() {
        syncForm();
        ModelForm.Error error = form.validateForSave();
        if (error != ModelForm.Error.NONE) {
            Toast.makeText(getContext(), message(error), Toast.LENGTH_SHORT).show();
            return;
        }
        listener.onSave(form.build());
    }

    private void test() {
        syncForm();
        if (form.validateForTest() != ModelForm.Error.NONE) {
            Toast.makeText(getContext(), R.string.screen_model_add_test_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        listener.onTest(form.build());
    }

    private int message(ModelForm.Error error) {
        switch (error) {
            case MISSING_KEY: return R.string.screen_model_add_require_api_key;
            case TOOL_RANGE: return R.string.screen_model_add_tool_call_range_invalid;
            case COMPRESSION_ID: return R.string.screen_model_add_require_compaction_id;
            case MISSING_ID:
            default: return R.string.screen_model_add_require_name_id;
        }
    }

    @Override public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        clearFocus();
    }
}
