package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.log.ErrorLog;
import cn.lineai.log.ErrorLogRedactor;
import cn.lineai.model.ContextSizeParser;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextInfo;
import cn.lineai.model.ModelContextParser;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.model.ModelProviderPreset;
import cn.lineai.ui.util.KeyboardController;
import cn.lineai.ui.util.ModelProviderPresetStrings;
import java.util.ArrayList;
import java.util.List;

public final class ModelAddScreenView extends LinearLayout {
    public interface Listener {
        void onBack();
        void onSave(ModelConfig model);
        void onTest(ModelConfig model);
        List<String> onFetchModelCatalog(ModelProtocolType type, String baseUrl, String apiKey) throws Exception;
    }

    private final String[] providerLabels = new String[4];

    private TextView saveAction;
    private TextView testAction;
    private TextView providerLabelView;
    private EditText nameInput;
    private final ModelProviderPreset preset;
    private final ModelConfig editingModel;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> fetchedModelIds = new ArrayList<>();
    private LinearLayout queryButton;
    private TextView queryLabel;
    private TextView queryText;
    private IconButtonView queryIcon;
    private TextView baseUrlHintView;
    private LinearLayout modelInputHost;
    private Switch customIdSwitch;
    private EditText baseUrlInput;
    private EditText apiKeyInput;
    private EditText modelIdInput;
    private EditText toolCallLimitInput;
    private EditText contextSizeInput;
    private final String[] selectedModelId = new String[] {""};
    private final boolean local;
    private final boolean lockedPreset;
    private final String providerLabel;
    private final ModelProtocolType[] protocolType = new ModelProtocolType[1];
    private boolean saveEnabled;
    private boolean fetchingModels;
    private ModelCompressionSectionView compressionSection;

    public ModelAddScreenView(Context context, ModelProviderPreset preset, boolean local, Listener listener) {
        this(context, preset, local, null, listener);
    }

    public ModelAddScreenView(Context context, ModelProviderPreset preset, boolean local, ModelConfig editingModel, Listener listener) {
        super(context);
        this.listener = listener;
        this.editingModel = editingModel;
        boolean editing = editingModel != null;
        this.local = local || (editing && editingModel.getProtocolType() == ModelProtocolType.LOCAL_GGUF);
        this.preset = preset;
        this.lockedPreset = preset != null || editing;
        this.protocolType[0] = editing ? editingModel.getProtocolType() : this.local ? ModelProtocolType.LOCAL_GGUF : preset == null ? ModelProtocolType.OPENAI_COMPATIBLE : preset.getProtocolType();
        this.providerLabel = this.local ? context.getString(R.string.model_provider_local) : editing ? cleanProviderLabel(editingModel.getProviderLabel()) : preset == null ? null : ModelProviderPresetStrings.getLabel(context, preset.getId());
        this.providerLabels[0] = "OpenAI";
        this.providerLabels[1] = "Codex";
        this.providerLabels[2] = "Anthropic";
        this.providerLabels[3] = context.getString(R.string.model_provider_local);
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);

        saveAction = LineTheme.textMedium(context, context.getString(R.string.common_save), LineTheme.FONT_MD, LineTheme.TEXT_TERTIARY);
        saveAction.setGravity(Gravity.CENTER);
        LineTheme.padding(saveAction, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        testAction = LineTheme.textMedium(context, context.getString(R.string.screen_model_add_test_button), LineTheme.FONT_MD, LineTheme.ACCENT);
        testAction.setGravity(Gravity.CENTER);
        testAction.setVisibility(local ? GONE : VISIBLE);
        LineTheme.padding(testAction, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        LinearLayout headerActions = new LinearLayout(context);
        headerActions.setOrientation(HORIZONTAL);
        headerActions.setGravity(Gravity.CENTER_VERTICAL);
        headerActions.addView(testAction, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        saveParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        headerActions.addView(saveAction, saveParams);

        addView(new ScreenHeaderView(context, context.getString(editing ? R.string.screen_model_add_title_edit : R.string.screen_model_add_title_add), listener::onBack, headerActions), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        scrollView.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        providerLabelView = ModelFormHelper.label(context, providerTitle());
        content.addView(providerLabelView, ModelFormHelper.labelParams(context, LineTheme.LG, LineTheme.SM));
        LinearLayout providerRow = new LinearLayout(context);
        providerRow.setOrientation(HORIZONTAL);
        for (int i = 0; i < providerLabels.length; i++) {
            final int index = i;
            boolean enabled = !lockedPreset || isActiveProviderIndex(index);
            ModelFormHelper.addToggle(providerRow, providerLabels[i], isActiveProviderIndex(index), enabled, () -> {
                if (index == 3) {
                    Toast.makeText(context, R.string.screen_model_add_open_local_form, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (this.local) {
                    Toast.makeText(context, R.string.screen_model_add_open_custom_form, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!lockedPreset) {
                    protocolType[0] = protocolForIndex(index);
                    fetchedModelIds.clear();
                    selectedModelId[0] = "";
                    if (modelIdInput != null) {
                        modelIdInput.setText("");
                    }
                    if (compressionSection != null) {
                        compressionSection.clearFetched();
                    }
                    updateProviderToggles(providerRow);
                    updateBaseUrlHint();
                    renderModelIdInput(customIdSwitch != null && customIdSwitch.isChecked());
                    if (compressionSection != null) {
                        compressionSection.updateForProtocolChange();
                    }
                    updateQueryState();
                    updateSaveState();
                }
            });
        }
        content.addView(providerRow, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        content.addView(ModelFormHelper.label(context, context.getString(R.string.screen_model_add_field_name)), ModelFormHelper.labelParams(context, LineTheme.LG, LineTheme.SM));
        nameInput = ModelFormHelper.input(context, editing ? editingModel.getName() : "", this.local ? context.getString(R.string.screen_model_add_hint_local_name) : preset == null ? context.getString(R.string.screen_model_add_hint_remote_name) : context.getString(R.string.screen_model_add_hint_name_optional), false, false);
        content.addView(nameInput, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (this.local) {
            addLocalUi(context, content);
            baseUrlInput = null;
            apiKeyInput = null;
            customIdSwitch = null;
            queryButton = null;
            queryLabel = null;
            queryText = null;
            queryIcon = null;
            baseUrlHintView = null;
            modelInputHost = null;
            modelIdInput = null;
        } else {
            content.addView(ModelFormHelper.label(context, context.getString(R.string.screen_model_add_field_base_url)), ModelFormHelper.labelParams(context, LineTheme.LG, LineTheme.SM));
            baseUrlInput = ModelFormHelper.input(context, editing ? editingModel.getBaseUrl() : preset == null ? "" : preset.getBaseUrl(), preset == null ? context.getString(R.string.screen_model_add_hint_base_url) : preset.getPlaceholder(), false, false);
            content.addView(baseUrlInput, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            baseUrlHintView = LineTheme.text(context, hintFor(preset), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            baseUrlHintView.setLineSpacing(LineTheme.dp(context, 3), 1f);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            hintParams.topMargin = LineTheme.dp(context, LineTheme.SM);
            content.addView(baseUrlHintView, hintParams);

            content.addView(ModelFormHelper.label(context, context.getString(R.string.screen_model_add_field_api_key)), ModelFormHelper.labelParams(context, LineTheme.LG, LineTheme.SM));
            apiKeyInput = ModelFormHelper.input(context, editing ? editingModel.getApiKey() : "", context.getString(R.string.screen_model_add_hint_api_key), false, true);
            content.addView(apiKeyInput, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            LinearLayout modelIdHeader = new LinearLayout(context);
            modelIdHeader.setOrientation(HORIZONTAL);
            modelIdHeader.setGravity(Gravity.CENTER_VERTICAL);
            TextView modelIdLabel = ModelFormHelper.label(context, context.getString(R.string.screen_model_add_field_model_id));
            modelIdHeader.addView(modelIdLabel, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout switchWrap = new LinearLayout(context);
            switchWrap.setOrientation(HORIZONTAL);
            switchWrap.setGravity(Gravity.CENTER_VERTICAL);
            switchWrap.addView(LineTheme.textMedium(context, context.getString(R.string.screen_model_add_custom_id_label), LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY), new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            customIdSwitch = new Switch(context);
            ModelFormHelper.tintSwitch(customIdSwitch);
            LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            switchParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
            switchWrap.addView(customIdSwitch, switchParams);
            modelIdHeader.addView(switchWrap, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            content.addView(modelIdHeader, ModelFormHelper.labelParams(context, LineTheme.LG, LineTheme.SM));

            modelInputHost = new LinearLayout(context);
            modelInputHost.setOrientation(VERTICAL);
            content.addView(modelInputHost, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            modelIdInput = ModelFormHelper.input(context, editing ? editingModel.getModelId() : "", context.getString(R.string.screen_model_add_hint_model_id), false, false);
            queryLabel = LineTheme.text(context, context.getString(R.string.screen_model_add_pick_first), LineTheme.FONT_MD, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            queryLabel.setSingleLine(true);
            queryLabel.setEllipsize(TextUtils.TruncateAt.END);
            queryButton = createQueryButton(context);
            selectedModelId[0] = editing ? editingModel.getModelId() : "";
            customIdSwitch.setChecked(editing);
            renderModelIdInput(editing);

            customIdSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                renderModelIdInput(isChecked);
                updateSaveState();
            });
            queryButton.setOnClickListener(v -> {
                if (!canQuery() || fetchingModels) {
                    return;
                }
                if (!fetchedModelIds.isEmpty()) {
                    showModelPicker();
                    return;
                }
                fetchModelCatalog();
            });

            content.addView(ModelFormHelper.label(context, context.getString(R.string.screen_model_add_field_tool_call_limit)), ModelFormHelper.labelParams(context, LineTheme.LG, LineTheme.SM));
            toolCallLimitInput = ModelFormHelper.input(context, String.valueOf(editing ? editingModel.getToolCallLimit() : ModelConfig.DEFAULT_TOOL_CALL_LIMIT), context.getString(R.string.screen_model_add_hint_tool_call_limit), false, false);
            toolCallLimitInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            content.addView(toolCallLimitInput, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            TextView toolLimitHint = LineTheme.text(context, context.getString(R.string.screen_model_add_max_tool_calls_hint), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            toolLimitHint.setLineSpacing(LineTheme.dp(context, 3), 1f);
            LinearLayout.LayoutParams toolLimitHintParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            toolLimitHintParams.topMargin = LineTheme.dp(context, LineTheme.SM);
            content.addView(toolLimitHint, toolLimitHintParams);

            content.addView(ModelFormHelper.label(context, context.getString(R.string.model_field_context_size)), ModelFormHelper.labelParams(context, LineTheme.LG, LineTheme.SM));
            contextSizeInput = ModelFormHelper.input(context, editing ? initialContextSizeText(editingModel) : "", context.getString(R.string.model_field_context_size_hint), false, false);
            content.addView(contextSizeInput, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            TextView contextSizeHint = LineTheme.text(context, context.getString(R.string.model_field_context_size_hint_desc), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            contextSizeHint.setLineSpacing(LineTheme.dp(context, 3), 1f);
            LinearLayout.LayoutParams contextSizeHintParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            contextSizeHintParams.topMargin = LineTheme.dp(context, LineTheme.SM);
            content.addView(contextSizeHint, contextSizeHintParams);

            compressionSection = new ModelCompressionSectionView(context, protocolType,
                    editing && editingModel.isCompressionModelEnabled(),
                    !editing || editingModel.isCompressionModelAuto(),
                    editing ? editingModel.getCompressionModelId() : "",
                    new ModelCompressionSectionView.CompressionListener() {
                        @Override
                        public List<String> onFetchCompressionCatalog(ModelProtocolType type, String baseUrl, String apiKey) throws Exception {
                            return listener.onFetchModelCatalog(type, baseUrl, apiKey);
                        }

                        @Override
                        public void onStateChanged() {
                            updateSaveState();
                        }
                    },
                    this::effectiveBaseUrl,
                    () -> ModelFormHelper.value(apiKeyInput)
            );
            content.addView(compressionSection, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSaveState();
                updateQueryState();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        nameInput.addTextChangedListener(watcher);
        if (!this.local) {
            baseUrlInput.addTextChangedListener(watcher);
            apiKeyInput.addTextChangedListener(watcher);
            modelIdInput.addTextChangedListener(watcher);
            toolCallLimitInput.addTextChangedListener(watcher);
            contextSizeInput.addTextChangedListener(watcher);
            TextWatcher catalogWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    fetchedModelIds.clear();
                    if (customIdSwitch != null && !customIdSwitch.isChecked()) {
                        selectedModelId[0] = "";
                        renderModelIdInput(false);
                    }
                    if (compressionSection != null) {
                        compressionSection.clearFetched();
                    }
                    updateSaveState();
                }
                @Override public void afterTextChanged(Editable s) {}
            };
            baseUrlInput.addTextChangedListener(catalogWatcher);
            apiKeyInput.addTextChangedListener(catalogWatcher);
        }

        saveAction.setOnClickListener(v -> {
            if (!saveEnabled) {
                return;
            }
            ModelConfig model = buildModelConfig(context);
            if (model != null) {
                listener.onSave(model);
            }
        });
        testAction.setOnClickListener(v -> {
            ModelConfig model = buildTestModelConfig();
            if (model != null) {
                listener.onTest(model);
            }
        });
        updateBaseUrlHint();
        updateQueryState();
        updateSaveState();
    }

    private void addLocalUi(Context context, LinearLayout content) {
        content.addView(ModelFormHelper.label(context, context.getString(R.string.screen_model_add_local_field_file)), ModelFormHelper.labelParams(context, LineTheme.LG, LineTheme.SM));
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setMinimumHeight(LineTheme.dp(context, 74));
        card.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_LIGHT, 12, LineTheme.BORDER_LIGHT));
        LineTheme.padding(card, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        FrameLayout iconWrap = new FrameLayout(context);
        iconWrap.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 8));
        IconButtonView fileIcon = new IconButtonView(context, IconButtonView.FILE_UP);
        fileIcon.setIconColor(LineTheme.ACCENT);
        fileIcon.setIconSizeDp(38, 20);
        fileIcon.setClickable(false);
        iconWrap.addView(fileIcon, new FrameLayout.LayoutParams(LineTheme.dp(context, 38), LineTheme.dp(context, 38), Gravity.CENTER));
        card.addView(iconWrap, new LinearLayout.LayoutParams(LineTheme.dp(context, 38), LineTheme.dp(context, 38)));

        LinearLayout fileText = new LinearLayout(context);
        fileText.setOrientation(VERTICAL);
        LinearLayout.LayoutParams fileTextParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        fileTextParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        fileTextParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(fileText, fileTextParams);
        fileText.addView(LineTheme.text(context, context.getString(R.string.screen_model_add_local_pick_file_label), LineTheme.FONT_MD, LineTheme.TEXT_TERTIARY, Typeface.BOLD));
        TextView desc = LineTheme.text(context, context.getString(R.string.screen_model_add_local_pick_file_desc), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 3);
        fileText.addView(desc, descParams);
        IconButtonView down = new IconButtonView(context, IconButtonView.CHEVRON_DOWN);
        down.setIconColor(LineTheme.TEXT_TERTIARY);
        down.setIconSizeDp(16, 14);
        down.setClickable(false);
        card.addView(down, new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));
        card.setOnClickListener(v -> Toast.makeText(context, R.string.screen_model_add_local_picker_pending, Toast.LENGTH_SHORT).show());
        content.addView(card, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        content.addView(ModelFormHelper.label(context, context.getString(R.string.screen_model_add_context_length_label)), ModelFormHelper.labelParams(context, LineTheme.LG, LineTheme.SM));
        EditText ctx = ModelFormHelper.input(context, "4096", "4096", false, false);
        ctx.setInputType(InputType.TYPE_CLASS_NUMBER);
        content.addView(ctx, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView hint = LineTheme.text(context, context.getString(R.string.screen_model_add_context_length_hint), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(hint, hintParams);

        content.addView(ModelFormHelper.label(context, context.getString(R.string.screen_model_add_acceleration_label)), ModelFormHelper.labelParams(context, LineTheme.LG, LineTheme.SM));
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        ModelFormHelper.addToggle(row, context.getString(R.string.screen_model_add_acceleration_auto), true, true, null);
        ModelFormHelper.addToggle(row, context.getString(R.string.screen_model_add_acceleration_cpu), false, true, null);
        ModelFormHelper.addToggle(row, context.getString(R.string.screen_model_add_acceleration_npu), false, true, null);
        content.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView accelHint = LineTheme.text(context, context.getString(R.string.screen_model_add_acceleration_auto_desc), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        accelHint.setLineSpacing(LineTheme.dp(context, 3), 1f);
        LinearLayout.LayoutParams accelHintParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        accelHintParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(accelHint, accelHintParams);
    }

    private void renderModelIdInput(boolean custom) {
        KeyboardController.clearFocusAndHide(modelInputHost);
        modelInputHost.removeAllViews();
        if (custom) {
            ModelFormHelper.detachFromParent(modelIdInput);
            modelInputHost.addView(modelIdInput, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        ModelFormHelper.detachFromParent(queryLabel);
        ModelFormHelper.detachFromParent(queryButton);
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout selector = new LinearLayout(context);
        selector.setOrientation(HORIZONTAL);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_LIGHT, 12, LineTheme.BORDER_LIGHT));
        LineTheme.padding(selector, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        boolean hasSelected = selectedModelId[0].length() > 0;
        queryLabel.setText(hasSelected ? selectedModelId[0] : context.getString(R.string.screen_model_add_pick_first));
        queryLabel.setTextColor(hasSelected ? LineTheme.TEXT : LineTheme.TEXT_TERTIARY);
        selector.addView(queryLabel, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        IconButtonView down = new IconButtonView(context, IconButtonView.CHEVRON_DOWN);
        down.setIconColor(LineTheme.TEXT_TERTIARY);
        down.setIconSizeDp(16, 14);
        down.setClickable(false);
        selector.addView(down, new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));
        selector.setOnClickListener(v -> queryButton.performClick());
        row.addView(selector, new LinearLayout.LayoutParams(0, LineTheme.dp(context, 48), 1f));
        LinearLayout.LayoutParams queryParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 48));
        queryParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        row.addView(queryButton, queryParams);
        modelInputHost.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout createQueryButton(Context context) {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setMinimumWidth(LineTheme.dp(context, 76));
        LineTheme.padding(button, LineTheme.LG, 0, LineTheme.LG, 0);
        queryIcon = new IconButtonView(context, IconButtonView.SEARCH);
        queryIcon.setClickable(false);
        queryIcon.setIconSizeDp(16, 16);
        button.addView(queryIcon, new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));
        queryText = LineTheme.text(context, context.getString(R.string.screen_model_add_query_button), LineTheme.FONT_MD, LineTheme.TEXT_TERTIARY, Typeface.BOLD);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = LineTheme.dp(context, LineTheme.XS);
        button.addView(queryText, textParams);
        return button;
    }

    private void fetchModelCatalog() {
        fetchingModels = true;
        updateQueryState();
        String baseUrl = effectiveBaseUrl();
        String apiKey = ModelFormHelper.value(apiKeyInput);
        ModelProtocolType type = protocolType[0];
        new Thread(() -> {
            try {
                List<String> rawIds = listener.onFetchModelCatalog(type, baseUrl, apiKey);
                final List<String> ids = rawIds != null ? rawIds : java.util.Collections.<String>emptyList();
                mainHandler.post(() -> {
                    fetchingModels = false;
                    fetchedModelIds.clear();
                    fetchedModelIds.addAll(ids);
                    updateQueryState();
                    if (ids.isEmpty()) {
                        Toast.makeText(getContext(), R.string.screen_model_add_fetch_failed, Toast.LENGTH_LONG).show();
                        return;
                    }
                    showModelPicker();
                });
            } catch (Exception e) {
                ErrorLog.record("model_catalog", "model catalog query failed", e,
                        "protocol=" + type + ", baseUrl=" + baseUrl
                                + ", apiKey=" + ErrorLogRedactor.redact("Authorization=Bearer " + apiKey));
                mainHandler.post(() -> {
                    fetchingModels = false;
                    updateQueryState();
                    Toast.makeText(getContext(), e.getMessage() != null ? e.getMessage() : getContext().getString(R.string.toast_query_failed), Toast.LENGTH_LONG).show();
                });
            }
        }, "linecode-model-catalog").start();
    }

    private void showModelPicker() {
        ModelPickerDialog.show(getContext(), fetchedModelIds, selectedModelId[0], (modelId, custom) -> {
            if (custom) {
                selectedModelId[0] = "";
                customIdSwitch.setChecked(true);
                modelIdInput.setText("");
                renderModelIdInput(true);
            } else {
                selectedModelId[0] = modelId;
                if (nameInput.getText().toString().trim().length() == 0 && preset != null) {
                    nameInput.setText(modelId);
                }
                customIdSwitch.setChecked(false);
                renderModelIdInput(false);
            }
            updateSaveState();
        });
    }

    private ModelConfig buildModelConfig(Context context) {
        if (local) {
            Toast.makeText(context, R.string.screen_model_add_gguf_required, Toast.LENGTH_SHORT).show();
            return null;
        }
        String baseUrl = effectiveBaseUrl();
        String apiKey = ModelFormHelper.value(apiKeyInput);
        String modelId = customIdSwitch.isChecked() ? ModelFormHelper.value(modelIdInput) : selectedModelId[0];
        Integer toolCallLimit = parseToolCallLimit();
        int contextSize = parseContextSize();
        boolean compressionEnabled = compressionSection != null && compressionSection.isEnabled();
        boolean compressionAuto = compressionSection == null || compressionSection.isAuto();
        String compressionModelId = compressionSection != null ? compressionSection.getModelId() : "";
        String name = ModelFormHelper.value(nameInput);
        if (name.length() == 0) {
            name = modelId;
        }
        if (modelId.length() == 0 || name.length() == 0) {
            Toast.makeText(context, R.string.screen_model_add_require_name_id, Toast.LENGTH_SHORT).show();
            return null;
        }
        if (apiKey.length() == 0) {
            Toast.makeText(context, R.string.screen_model_add_require_api_key, Toast.LENGTH_SHORT).show();
            return null;
        }
        if (toolCallLimit == null) {
            Toast.makeText(context, R.string.screen_model_add_tool_call_range_invalid, Toast.LENGTH_SHORT).show();
            return null;
        }
        if (compressionSection != null && !compressionSection.isReady()) {
            Toast.makeText(context, R.string.screen_model_add_require_compaction_id, Toast.LENGTH_SHORT).show();
            return null;
        }
        String label = providerLabel != null ? providerLabel : protocolType[0].getLabel();
        return new ModelConfig(
                editingModel == null ? "" : editingModel.getId(),
                name, protocolType[0], label, baseUrl, apiKey, modelId,
                toolCallLimit, compressionEnabled, compressionAuto, compressionModelId, contextSize
        );
    }

    private ModelConfig buildTestModelConfig() {
        if (local) {
            return null;
        }
        Context context = getContext();
        String baseUrl = effectiveBaseUrl();
        String apiKey = ModelFormHelper.value(apiKeyInput);
        String modelId = customIdSwitch.isChecked() ? ModelFormHelper.value(modelIdInput) : selectedModelId[0];
        Integer toolCallLimit = parseToolCallLimit();
        if (toolCallLimit == null) {
            toolCallLimit = ModelConfig.DEFAULT_TOOL_CALL_LIMIT;
        }
        int contextSize = parseContextSize();
        boolean compressionEnabled = compressionSection != null && compressionSection.isEnabled();
        boolean compressionAuto = compressionSection == null || compressionSection.isAuto();
        String compressionModelId = compressionSection != null ? compressionSection.getModelId() : "";
        String name = ModelFormHelper.value(nameInput);
        if (name.length() == 0) {
            name = modelId;
        }
        if (baseUrl.length() == 0 || apiKey.length() == 0 || modelId.length() == 0) {
            Toast.makeText(context, R.string.screen_model_add_test_missing, Toast.LENGTH_SHORT).show();
            return null;
        }
        String label = providerLabel != null ? providerLabel : protocolType[0].getLabel();
        return new ModelConfig(
                editingModel == null ? "" : editingModel.getId(),
                name, protocolType[0], label, baseUrl, apiKey, modelId,
                toolCallLimit, compressionEnabled, compressionAuto, compressionModelId, contextSize
        );
    }

    private String initialContextSizeText(ModelConfig model) {
        if (model == null) {
            return "";
        }
        if (model.getContextSize() > 0) {
            return ContextSizeParser.format(model.getContextSize());
        }
        String trimmed = model.getModelId() == null ? "" : model.getModelId().trim();
        if (trimmed.length() == 0 || !trimmed.endsWith("]")) {
            return "";
        }
        ModelContextInfo legacy = ModelContextParser.parse(trimmed);
        int legacyTokens = legacy.getContextTokens();
        if (legacyTokens <= 0) {
            return "";
        }
        return ContextSizeParser.format(legacyTokens);
    }

    private int parseContextSize() {
        if (local || contextSizeInput == null) {
            return ModelConfig.CONTEXT_SIZE_UNSET;
        }
        return ContextSizeParser.parse(ModelFormHelper.value(contextSizeInput));
    }

    private void updateProviderToggles(LinearLayout providerRow) {
        for (int i = 0; i < providerRow.getChildCount(); i++) {
            TextView button = (TextView) providerRow.getChildAt(i);
            boolean active = isActiveProviderIndex(i);
            button.setTextColor(active ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
            button.setBackground(LineTheme.rounded(getContext(), active ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 12));
            button.setAlpha(lockedPreset && !active ? 0.45f : 1f);
        }
    }

    private void updateBaseUrlHint() {
        if (local || baseUrlHintView == null) {
            return;
        }
        baseUrlHintView.setText(hintFor(lockedPreset ? preset : null));
        if (!lockedPreset && baseUrlInput != null) {
            baseUrlInput.setHint(placeholderFor(protocolType[0]));
        }
        if (providerLabelView != null) {
            providerLabelView.setText(providerTitle());
        }
        if (compressionSection != null) {
            compressionSection.updateForProtocolChange();
        }
    }

    private void updateQueryState() {
        if (local || queryButton == null) {
            return;
        }
        Context context = getContext();
        boolean canQuery = canQuery() && !fetchingModels;
        if (queryText != null) {
            queryText.setText(fetchingModels ? context.getString(R.string.screen_model_add_query_button_loading) : context.getString(R.string.screen_model_add_query_button));
            queryText.setTextColor((canQuery || fetchingModels) ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_TERTIARY);
        }
        if (queryIcon != null) {
            queryIcon.setIconColor((canQuery || fetchingModels) ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_TERTIARY);
        }
        queryButton.setBackground(LineTheme.rounded(getContext(), (canQuery || fetchingModels) ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 12));
        queryButton.setEnabled(canQuery);
    }

    private void updateSaveState() {
        boolean canSave;
        if (local) {
            canSave = false;
        } else {
            String id = customIdSwitch.isChecked() ? ModelFormHelper.value(modelIdInput) : selectedModelId[0];
            String name = ModelFormHelper.value(nameInput);
            boolean compressionReady = compressionSection == null || compressionSection.isReady();
            canSave = (name.length() > 0 || id.length() > 0)
                    && id.length() > 0
                    && ModelFormHelper.value(apiKeyInput).length() > 0
                    && parseToolCallLimit() != null
                    && compressionReady;
        }
        saveEnabled = canSave;
        saveAction.setTextColor(canSave ? LineTheme.ACCENT : LineTheme.TEXT_TERTIARY);
        saveAction.setAlpha(canSave ? 1f : 0.45f);
    }

    private boolean isActiveProviderIndex(int index) {
        if (index == 3) {
            return local;
        }
        return !local && protocolForIndex(index) == protocolType[0];
    }

    private ModelProtocolType protocolForIndex(int index) {
        if (index == 1) {
            return ModelProtocolType.CODEX_RESPONSES;
        }
        if (index == 2) {
            return ModelProtocolType.ANTHROPIC_MESSAGES;
        }
        return ModelProtocolType.OPENAI_COMPATIBLE;
    }

    private boolean canQuery() {
        return !local && effectiveBaseUrl().length() > 0 && ModelFormHelper.value(apiKeyInput).length() > 0;
    }

    private String effectiveBaseUrl() {
        String baseUrl = ModelFormHelper.value(baseUrlInput);
        if (baseUrl.length() > 0) {
            return baseUrl;
        }
        if (preset != null && preset.getBaseUrl() != null && preset.getBaseUrl().length() > 0) {
            return preset.getBaseUrl();
        }
        return defaultBaseUrlFor(protocolType[0]);
    }

    private String defaultBaseUrlFor(ModelProtocolType type) {
        if (type == ModelProtocolType.ANTHROPIC_MESSAGES) {
            return "https://api.anthropic.com";
        }
        return "https://api.openai.com/v1";
    }

    private String placeholderFor(ModelProtocolType type) {
        if (type == ModelProtocolType.ANTHROPIC_MESSAGES) {
            return "https://api.example.com/anthropic";
        }
        return "https://api.example.com/v1";
    }

    private String hintFor(ModelProviderPreset preset) {
        Context context = getContext();
        if (preset != null) {
            return ModelProviderPresetStrings.getHint(context, preset.getId());
        }
        if (protocolType[0] == ModelProtocolType.CODEX_RESPONSES) {
            return context.getString(R.string.screen_model_add_url_codex);
        }
        if (protocolType[0] == ModelProtocolType.ANTHROPIC_MESSAGES) {
            return context.getString(R.string.screen_model_add_url_anthropic);
        }
        return context.getString(R.string.screen_model_add_url_openai);
    }

    private String providerTitle() {
        Context context = getContext();
        if (providerLabel != null && providerLabel.length() > 0) {
            return context.getString(R.string.screen_model_add_provider_label) + providerLabel;
        }
        if (lockedPreset) {
            return context.getString(R.string.screen_model_add_provider_label) + protocolType[0].getLabel();
        }
        return context.getString(R.string.screen_model_add_provider_title);
    }

    private String cleanProviderLabel(String label) {
        if (label == null || label.length() == 0 || getContext().getString(R.string.model_provider_preset_custom_label).equals(label)) {
            return null;
        }
        return label;
    }

    private Integer parseToolCallLimit() {
        if (local || toolCallLimitInput == null) {
            return ModelConfig.DEFAULT_TOOL_CALL_LIMIT;
        }
        String value = ModelFormHelper.value(toolCallLimitInput);
        if (value.length() == 0) {
            return null;
        }
        try {
            int limit = Integer.parseInt(value);
            if (limit < ModelConfig.UNLIMITED_TOOL_CALLS) {
                return null;
            }
            return ModelConfig.normalizeToolCallLimit(limit);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        KeyboardController.clearFocusAndHide(this);
        super.onDetachedFromWindow();
    }
}
