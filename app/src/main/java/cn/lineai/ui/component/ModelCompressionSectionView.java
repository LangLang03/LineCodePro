package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.ui.util.KeyboardController;
import java.util.ArrayList;
import java.util.List;

public final class ModelCompressionSectionView extends LinearLayout {

    public interface CompressionListener {
        List<String> onFetchCompressionCatalog(ModelProtocolType type, String baseUrl, String apiKey) throws Exception;
        void onStateChanged();
    }

    public interface ValueSource {
        String value();
    }

    private Switch compressionEnabledSwitch;
    private Switch compressionAutoSwitch;
    private Switch compressionCustomIdSwitch;
    private EditText compressionModelIdInput;
    private LinearLayout compressionModelInputHost;
    private LinearLayout compressionQueryButton;
    private TextView compressionQueryLabel;
    private TextView compressionQueryText;
    private IconButtonView compressionQueryIcon;
    private LinearLayout compressionDetailsSection;
    private final ArrayList<String> fetchedCompressionModelIds = new ArrayList<>();
    private final String[] selectedCompressionModelId = new String[] {""};
    private boolean fetchingCompressionModels;
    private final ModelProtocolType[] protocolType;
    private final CompressionListener listener;
    private final ValueSource baseUrlSource;
    private final ValueSource apiKeySource;

    public ModelCompressionSectionView(Context context, ModelProtocolType[] protocolType,
                                        boolean enabled, boolean auto, String modelId,
                                        CompressionListener listener,
                                        ValueSource baseUrlSource, ValueSource apiKeySource) {
        super(context);
        this.protocolType = protocolType;
        this.listener = listener;
        this.baseUrlSource = baseUrlSource;
        this.apiKeySource = apiKeySource;
        setOrientation(VERTICAL);

        LinearLayout enabledHeader = new LinearLayout(context);
        enabledHeader.setOrientation(HORIZONTAL);
        enabledHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = ModelFormHelper.label(context, context.getString(R.string.screen_model_add_compaction_label));
        enabledHeader.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        compressionEnabledSwitch = new Switch(context);
        ModelFormHelper.tintSwitch(compressionEnabledSwitch);
        compressionEnabledSwitch.setChecked(enabled);
        enabledHeader.addView(compressionEnabledSwitch, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(enabledHeader, ModelFormHelper.labelParams(context, LineTheme.LG, LineTheme.SM));

        TextView hint = LineTheme.text(context, context.getString(R.string.screen_model_add_compaction_hint), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        hint.setLineSpacing(LineTheme.dp(context, 3), 1f);
        compressionDetailsSection = new LinearLayout(context);
        compressionDetailsSection.setOrientation(VERTICAL);
        addView(compressionDetailsSection, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        compressionDetailsSection.addView(hint, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout autoHeader = new LinearLayout(context);
        autoHeader.setOrientation(HORIZONTAL);
        autoHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView autoLabel = ModelFormHelper.label(context, context.getString(R.string.screen_model_add_compaction_auto_label));
        autoHeader.addView(autoLabel, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        compressionAutoSwitch = new Switch(context);
        ModelFormHelper.tintSwitch(compressionAutoSwitch);
        compressionAutoSwitch.setChecked(auto);
        autoHeader.addView(compressionAutoSwitch, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        compressionDetailsSection.addView(autoHeader, ModelFormHelper.labelParams(context, LineTheme.MD, LineTheme.SM));

        LinearLayout modelIdHeader = new LinearLayout(context);
        modelIdHeader.setOrientation(HORIZONTAL);
        modelIdHeader.setGravity(Gravity.CENTER_VERTICAL);
        modelIdHeader.addView(ModelFormHelper.label(context, context.getString(R.string.screen_model_add_compaction_id_label)), new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout switchWrap = new LinearLayout(context);
        switchWrap.setOrientation(HORIZONTAL);
        switchWrap.setGravity(Gravity.CENTER_VERTICAL);
        switchWrap.addView(LineTheme.textMedium(context, context.getString(R.string.screen_model_add_custom_id_label), LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY),
                new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        compressionCustomIdSwitch = new Switch(context);
        ModelFormHelper.tintSwitch(compressionCustomIdSwitch);
        LinearLayout.LayoutParams customSwitchParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        customSwitchParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        switchWrap.addView(compressionCustomIdSwitch, customSwitchParams);
        modelIdHeader.addView(switchWrap, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        compressionDetailsSection.addView(modelIdHeader, ModelFormHelper.labelParams(context, LineTheme.MD, LineTheme.SM));

        compressionModelInputHost = new LinearLayout(context);
        compressionModelInputHost.setOrientation(VERTICAL);
        compressionDetailsSection.addView(compressionModelInputHost, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        selectedCompressionModelId[0] = modelId != null ? modelId : "";
        compressionModelIdInput = ModelFormHelper.input(context, modelId != null ? modelId : "", context.getString(R.string.screen_model_add_compaction_id_hint), false, false);
        compressionQueryLabel = LineTheme.text(context, context.getString(R.string.screen_model_add_pick_first), LineTheme.FONT_MD, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        compressionQueryLabel.setSingleLine(true);
        compressionQueryLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        compressionQueryButton = createQueryButton(context);
        compressionCustomIdSwitch.setChecked(modelId != null && modelId.length() > 0);
        renderModelIdInput(compressionCustomIdSwitch.isChecked());

        compressionEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateVisibility();
            notifyStateChanged();
        });
        compressionAutoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            renderModelIdInput(compressionCustomIdSwitch != null && compressionCustomIdSwitch.isChecked());
            notifyStateChanged();
        });
        compressionCustomIdSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            renderModelIdInput(isChecked);
            notifyStateChanged();
        });
        compressionQueryButton.setOnClickListener(v -> {
            if (!canQuery() || fetchingCompressionModels) {
                return;
            }
            if (!fetchedCompressionModelIds.isEmpty()) {
                showPicker();
                return;
            }
            fetchCatalog();
        });

        updateVisibility();
    }

    public boolean isEnabled() {
        return compressionEnabledSwitch != null
                && compressionEnabledSwitch.isChecked()
                && protocolType[0].supportsDedicatedCompression();
    }

    public boolean isAuto() {
        return compressionAutoSwitch == null || compressionAutoSwitch.isChecked();
    }

    public String getModelId() {
        return compressionCustomIdSwitch != null && compressionCustomIdSwitch.isChecked()
                ? ModelFormHelper.value(compressionModelIdInput)
                : selectedCompressionModelId[0];
    }

    public boolean isReady() {
        if (!isEnabled()) {
            return true;
        }
        if (isAuto()) {
            return true;
        }
        return getModelId().length() > 0;
    }

    public void updateForProtocolChange() {
        updateVisibility();
    }

    public void clearFetched() {
        fetchedCompressionModelIds.clear();
        selectedCompressionModelId[0] = "";
        if (compressionCustomIdSwitch != null && !compressionCustomIdSwitch.isChecked()) {
            renderModelIdInput(false);
        }
    }

    private void notifyStateChanged() {
        if (listener != null) {
            listener.onStateChanged();
        }
    }

    private void updateVisibility() {
        boolean supported = protocolType[0].supportsDedicatedCompression();
        setVisibility(supported ? VISIBLE : GONE);
        if (!supported && compressionEnabledSwitch != null && compressionEnabledSwitch.isChecked()) {
            compressionEnabledSwitch.setChecked(false);
        }
        boolean enabled = supported && compressionEnabledSwitch != null && compressionEnabledSwitch.isChecked();
        if (compressionDetailsSection != null) {
            compressionDetailsSection.setVisibility(enabled ? VISIBLE : GONE);
        }
        if (compressionAutoSwitch != null) {
            compressionAutoSwitch.setEnabled(enabled);
            compressionAutoSwitch.setAlpha(compressionAutoSwitch.isEnabled() ? 1f : 0.45f);
        }
        if (compressionCustomIdSwitch != null) {
            boolean customEnabled = enabled && compressionAutoSwitch != null && !compressionAutoSwitch.isChecked();
            compressionCustomIdSwitch.setEnabled(customEnabled);
            compressionCustomIdSwitch.setAlpha(customEnabled ? 1f : 0.45f);
        }
        renderModelIdInput(compressionCustomIdSwitch != null && compressionCustomIdSwitch.isChecked());
        updateQueryState();
    }

    private boolean canQuery() {
        return isEnabled() && !isAuto() && baseUrlSource.value().length() > 0 && apiKeySource.value().length() > 0;
    }

    private void renderModelIdInput(boolean custom) {
        if (compressionModelInputHost == null) {
            return;
        }
        KeyboardController.clearFocusAndHide(compressionModelInputHost);
        compressionModelInputHost.removeAllViews();
        boolean enabled = isEnabled();
        boolean automatic = isAuto();
        if (!enabled || automatic) {
            compressionModelInputHost.setVisibility(GONE);
            return;
        }
        compressionModelInputHost.setVisibility(VISIBLE);
        if (custom) {
            ModelFormHelper.detachFromParent(compressionModelIdInput);
            compressionModelInputHost.addView(compressionModelIdInput, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        ModelFormHelper.detachFromParent(compressionQueryLabel);
        ModelFormHelper.detachFromParent(compressionQueryButton);
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout selector = new LinearLayout(context);
        selector.setOrientation(HORIZONTAL);
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_LIGHT, 12, LineTheme.BORDER_LIGHT));
        LineTheme.padding(selector, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        boolean hasSelected = selectedCompressionModelId[0].length() > 0;
        compressionQueryLabel.setText(hasSelected ? selectedCompressionModelId[0] : context.getString(R.string.screen_model_add_pick_first));
        compressionQueryLabel.setTextColor(hasSelected ? LineTheme.TEXT : LineTheme.TEXT_TERTIARY);
        selector.addView(compressionQueryLabel, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        IconButtonView down = new IconButtonView(context, IconButtonView.CHEVRON_DOWN);
        down.setIconColor(LineTheme.TEXT_TERTIARY);
        down.setIconSizeDp(16, 14);
        down.setClickable(false);
        selector.addView(down, new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));
        selector.setOnClickListener(v -> compressionQueryButton.performClick());
        row.addView(selector, new LinearLayout.LayoutParams(0, LineTheme.dp(context, 48), 1f));
        LinearLayout.LayoutParams queryParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 48));
        queryParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        row.addView(compressionQueryButton, queryParams);
        compressionModelInputHost.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout createQueryButton(Context context) {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setMinimumWidth(LineTheme.dp(context, 76));
        LineTheme.padding(button, LineTheme.LG, 0, LineTheme.LG, 0);
        compressionQueryIcon = new IconButtonView(context, IconButtonView.SEARCH);
        compressionQueryIcon.setClickable(false);
        compressionQueryIcon.setIconSizeDp(16, 16);
        button.addView(compressionQueryIcon, new LinearLayout.LayoutParams(LineTheme.dp(context, 16), LineTheme.dp(context, 16)));
        compressionQueryText = LineTheme.text(context, context.getString(R.string.screen_model_add_query_button), LineTheme.FONT_MD, LineTheme.TEXT_TERTIARY, Typeface.BOLD);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = LineTheme.dp(context, LineTheme.XS);
        button.addView(compressionQueryText, textParams);
        return button;
    }

    private void updateQueryState() {
        if (compressionQueryButton == null) {
            return;
        }
        Context context = getContext();
        boolean canQuery = canQuery() && !fetchingCompressionModels;
        if (compressionQueryText != null) {
            compressionQueryText.setText(fetchingCompressionModels ? context.getString(R.string.screen_model_add_query_button_loading) : context.getString(R.string.screen_model_add_query_button));
            compressionQueryText.setTextColor((canQuery || fetchingCompressionModels) ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_TERTIARY);
        }
        if (compressionQueryIcon != null) {
            compressionQueryIcon.setIconColor((canQuery || fetchingCompressionModels) ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_TERTIARY);
        }
        compressionQueryButton.setBackground(LineTheme.rounded(context, (canQuery || fetchingCompressionModels) ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 12));
        compressionQueryButton.setEnabled(canQuery);
    }

    private void fetchCatalog() {
        fetchingCompressionModels = true;
        updateQueryState();
        new Thread(() -> {
            try {
                List<String> rawIds = listener.onFetchCompressionCatalog(protocolType[0], baseUrlSource.value(), apiKeySource.value());
                final List<String> ids = rawIds != null ? rawIds : java.util.Collections.<String>emptyList();
                post(() -> {
                    fetchingCompressionModels = false;
                    fetchedCompressionModelIds.clear();
                    fetchedCompressionModelIds.addAll(ids);
                    updateQueryState();
                    if (ids.isEmpty()) {
                        android.widget.Toast.makeText(getContext(), R.string.screen_model_add_fetch_failed, android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    showPicker();
                });
            } catch (Exception e) {
                post(() -> {
                    fetchingCompressionModels = false;
                    updateQueryState();
                    android.widget.Toast.makeText(getContext(), e.getMessage() != null ? e.getMessage() : getContext().getString(R.string.toast_query_failed), android.widget.Toast.LENGTH_LONG).show();
                });
            }
        }, "linecode-compression-catalog").start();
    }

    private void showPicker() {
        ModelPickerDialog.show(getContext(), fetchedCompressionModelIds, selectedCompressionModelId[0], (modelId, custom) -> {
            if (custom) {
                selectedCompressionModelId[0] = "";
                compressionCustomIdSwitch.setChecked(true);
                compressionModelIdInput.setText("");
                renderModelIdInput(true);
            } else {
                selectedCompressionModelId[0] = modelId;
                compressionCustomIdSwitch.setChecked(false);
                renderModelIdInput(false);
            }
            notifyStateChanged();
        });
    }
}
