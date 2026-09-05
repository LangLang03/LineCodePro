package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.WebSearchConfig;
import java.util.ArrayList;
import java.util.List;

public final class ToolSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onWebSearchConfigChanged(WebSearchConfig config);

        void onOpenImageUnderstandingModelPicker();

        void onOpenImageGenerationModelPicker();
    }

    private final Listener listener;
    private final McpSettingsState state;
    private final String imageUnderstandingModelLabel;
    private final String imageGenerationModelLabel;

    public ToolSettingsScreenView(
            Context context,
            McpSettingsState state,
            String imageUnderstandingModelLabel,
            String imageGenerationModelLabel,
            Listener listener
    ) {
        super(context, context.getString(R.string.screen_tools_title), listener::onBack, null);
        this.listener = listener;
        this.state = state == null ? new McpSettingsState("local", null) : state;
        this.imageUnderstandingModelLabel = imageUnderstandingModelLabel == null ? "" : imageUnderstandingModelLabel.trim();
        this.imageGenerationModelLabel = imageGenerationModelLabel == null ? "" : imageGenerationModelLabel.trim();
        LinearLayout content = getContent();
        LineTheme.padding(content, 28, 8, 28, 48);

        addSectionHeader(content, context.getString(R.string.screen_tools_section_images));
        addImageUnderstanding(content);
        addImageGeneration(content);

        addSectionHeader(content, context.getString(R.string.screen_tools_section_search));
        addWebSearch(content);
    }

    private void addImageUnderstanding(LinearLayout content) {
        Context context = content.getContext();
        LinearLayout card = card(context);
        card.addView(title(context, context.getString(R.string.screen_tools_image_understanding_label)));
        TextView desc = desc(context, context.getString(R.string.screen_tools_image_understanding_desc));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        card.addView(desc, descParams);

        TextView selected = LineTheme.text(context,
                imageUnderstandingModelLabel.length() == 0 ? context.getString(R.string.screen_tools_no_model_selected) : imageUnderstandingModelLabel,
                LineTheme.FONT_SM,
                imageUnderstandingModelLabel.length() == 0 ? LineTheme.TEXT_TERTIARY : LineTheme.TEXT,
                Typeface.BOLD);
        selected.setSingleLine(true);
        LinearLayout.LayoutParams selectedParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        selectedParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(selected, selectedParams);

        LinearLayout button = actionButton(context, context.getString(R.string.screen_tools_pick_model), IconButtonView.PAINTBRUSH, true, v -> listener.onOpenImageUnderstandingModelPicker());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 42));
        buttonParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(button, buttonParams);
        addCard(content, card);
    }

    private void addImageGeneration(LinearLayout content) {
        Context context = content.getContext();
        LinearLayout card = card(context);
        card.addView(title(context, context.getString(R.string.screen_tools_image_generation_label)));
        TextView desc = desc(context, context.getString(R.string.screen_tools_image_generation_desc));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        card.addView(desc, descParams);

        TextView selected = LineTheme.text(context,
                imageGenerationModelLabel.length() == 0 ? context.getString(R.string.screen_tools_no_model_selected) : imageGenerationModelLabel,
                LineTheme.FONT_SM,
                imageGenerationModelLabel.length() == 0 ? LineTheme.TEXT_TERTIARY : LineTheme.TEXT,
                Typeface.BOLD);
        selected.setSingleLine(true);
        LinearLayout.LayoutParams selectedParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        selectedParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(selected, selectedParams);

        LinearLayout button = actionButton(context, context.getString(R.string.screen_tools_pick_model), IconButtonView.SPARKLES, true, v -> listener.onOpenImageGenerationModelPicker());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 42));
        buttonParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(button, buttonParams);
        addCard(content, card);
    }

    private void addWebSearch(LinearLayout content) {
        Context context = content.getContext();
        WebSearchConfig config = state.getWebSearchConfig();
        String[] selectedProvider = new String[] {config.getProvider()};
        boolean[] suppressChange = new boolean[] {false};

        LinearLayout card = card(context);
        card.addView(title(context, context.getString(R.string.screen_tools_web_search_label)));
        TextView desc = desc(context, context.getString(R.string.screen_tools_web_search_desc));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        card.addView(desc, descParams);

        FormTextFieldView baseUrlField = new FormTextFieldView(context, context.getString(R.string.screen_tools_field_search_url), config.getBaseUrl(), "https://api.example.com/search", null, false, false);
        FormTextFieldView apiKeyField = new FormTextFieldView(context, context.getString(R.string.screen_tools_field_api_key), config.getApiKey(), context.getString(R.string.screen_tools_hint_api_key), null, false, true);
        FormTextFieldView modelField = new FormTextFieldView(context, context.getString(R.string.screen_tools_field_search_model), config.getModel(), context.getString(R.string.screen_tools_hint_model), null, false, false);
        FormTextFieldView queryParamField = new FormTextFieldView(context, context.getString(R.string.screen_tools_field_query_param), config.getQueryParam(), "q", null, false, false);
        FormTextFieldView apiKeyHeaderField = new FormTextFieldView(context, context.getString(R.string.screen_tools_field_key_header), config.getApiKeyHeader(), context.getString(R.string.screen_tools_hint_key_header), null, false, false);
        FormTextFieldView apiKeyParamField = new FormTextFieldView(context, context.getString(R.string.screen_tools_field_key_query), config.getApiKeyParam(), context.getString(R.string.screen_tools_hint_key_query), null, false, false);

        GridLayout providers = new GridLayout(context);
        providers.setColumnCount(3);
        List<TextView> providerButtons = new ArrayList<>();
        List<String> providerKeys = new ArrayList<>();
        addProviderButton(providers, context.getString(R.string.screen_tools_provider_bing_rss_free), WebSearchConfig.PROVIDER_BING_RSS_FREE, selectedProvider, suppressChange,
                providerButtons, providerKeys,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        addProviderButton(providers, context.getString(R.string.screen_tools_provider_tavily), WebSearchConfig.PROVIDER_TAVILY, selectedProvider, suppressChange,
                providerButtons, providerKeys,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        addProviderButton(providers, context.getString(R.string.screen_tools_provider_brave), WebSearchConfig.PROVIDER_BRAVE, selectedProvider, suppressChange,
                providerButtons, providerKeys,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        addProviderButton(providers, context.getString(R.string.screen_tools_provider_serpapi), WebSearchConfig.PROVIDER_SERPAPI, selectedProvider, suppressChange,
                providerButtons, providerKeys,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        addProviderButton(providers, context.getString(R.string.screen_tools_provider_bing), WebSearchConfig.PROVIDER_BING, selectedProvider, suppressChange,
                providerButtons, providerKeys,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        addProviderButton(providers, context.getString(R.string.screen_tools_provider_custom), WebSearchConfig.PROVIDER_CUSTOM, selectedProvider, suppressChange,
                providerButtons, providerKeys,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        LinearLayout.LayoutParams providerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        providerParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(providers, providerParams);

        TextWatcher watcher = configWatcher(selectedProvider, suppressChange,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        baseUrlField.getInput().addTextChangedListener(watcher);
        apiKeyField.getInput().addTextChangedListener(watcher);
        modelField.getInput().addTextChangedListener(watcher);
        queryParamField.getInput().addTextChangedListener(watcher);
        apiKeyHeaderField.getInput().addTextChangedListener(watcher);
        apiKeyParamField.getInput().addTextChangedListener(watcher);

        card.addView(baseUrlField, formParams(context));
        card.addView(apiKeyField, formParams(context));
        card.addView(modelField, formParams(context));
        card.addView(queryParamField, formParams(context));
        card.addView(apiKeyHeaderField, formParams(context));
        card.addView(apiKeyParamField, formParams(context));
        addCard(content, card);

        applyProviderFieldVisibility(selectedProvider[0],
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
    }

    private void addProviderButton(
            GridLayout providers,
            String label,
            String provider,
            String[] selectedProvider,
            boolean[] suppressChange,
            List<TextView> providerButtons,
            List<String> providerKeys,
            FormTextFieldView baseUrlField,
            FormTextFieldView apiKeyField,
            FormTextFieldView modelField,
            FormTextFieldView queryParamField,
            FormTextFieldView apiKeyHeaderField,
            FormTextFieldView apiKeyParamField
    ) {
        Context context = providers.getContext();
        boolean active = provider.equals(selectedProvider[0]);
        TextView button = LineTheme.text(context, label, LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        applyProviderButtonStyle(context, button, active);
        button.setClickable(true);
        providerButtons.add(button);
        providerKeys.add(provider);
        button.setOnClickListener(v -> {
            WebSearchConfig defaults = WebSearchConfig.defaultConfig(provider);
            selectedProvider[0] = defaults.getProvider();
            suppressChange[0] = true;
            baseUrlField.getInput().setText(defaults.getBaseUrl());
            apiKeyField.getInput().setText("");
            modelField.getInput().setText(defaults.getModel());
            queryParamField.getInput().setText(defaults.getQueryParam());
            apiKeyHeaderField.getInput().setText(defaults.getApiKeyHeader());
            apiKeyParamField.getInput().setText(defaults.getApiKeyParam());
            suppressChange[0] = false;
            refreshProviderButtons(context, providerButtons, providerKeys, selectedProvider[0]);
            applyProviderFieldVisibility(selectedProvider[0],
                    baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
            listener.onWebSearchConfigChanged(readWebSearchConfig(selectedProvider[0],
                    baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField));
        });
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = LineTheme.dp(context, 34);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(0, LineTheme.dp(context, LineTheme.SM), LineTheme.dp(context, LineTheme.SM), 0);
        providers.addView(button, params);
    }

    private void applyProviderButtonStyle(Context context, TextView button, boolean active) {
        button.setTextColor(active ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
        button.setBackground(LineTheme.roundedStroke(context, active ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 8, active ? LineTheme.ACCENT : LineTheme.BORDER_LIGHT));
    }

    private void refreshProviderButtons(Context context, List<TextView> buttons, List<String> keys, String currentProvider) {
        for (int i = 0; i < buttons.size(); i++) {
            applyProviderButtonStyle(context, buttons.get(i), keys.get(i).equals(currentProvider));
        }
    }

    /**
     * 切换 provider 时，根据是否需要 API Key 等字段控制其可见性。
     * bing_rss_free 是免密钥的内置 provider，相关字段全部隐藏，简化界面。
     */
    private void applyProviderFieldVisibility(
            String provider,
            FormTextFieldView baseUrlField,
            FormTextFieldView apiKeyField,
            FormTextFieldView modelField,
            FormTextFieldView queryParamField,
            FormTextFieldView apiKeyHeaderField,
            FormTextFieldView apiKeyParamField
    ) {
        boolean freeProvider = WebSearchConfig.PROVIDER_BING_RSS_FREE.equals(provider);
        int fieldVisibility = freeProvider ? View.GONE : View.VISIBLE;
        baseUrlField.setVisibility(fieldVisibility);
        apiKeyField.setVisibility(fieldVisibility);
        modelField.setVisibility(fieldVisibility);
        queryParamField.setVisibility(fieldVisibility);
        apiKeyHeaderField.setVisibility(fieldVisibility);
        apiKeyParamField.setVisibility(fieldVisibility);
        baseUrlField.getInput().setEnabled(!freeProvider);
        apiKeyField.getInput().setEnabled(!freeProvider);
        modelField.getInput().setEnabled(!freeProvider);
        queryParamField.getInput().setEnabled(!freeProvider);
        apiKeyHeaderField.getInput().setEnabled(!freeProvider);
        apiKeyParamField.getInput().setEnabled(!freeProvider);
    }

    private TextWatcher configWatcher(
            String[] selectedProvider,
            boolean[] suppressChange,
            FormTextFieldView baseUrlField,
            FormTextFieldView apiKeyField,
            FormTextFieldView modelField,
            FormTextFieldView queryParamField,
            FormTextFieldView apiKeyHeaderField,
            FormTextFieldView apiKeyParamField
    ) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (suppressChange[0]) {
                    return;
                }
                listener.onWebSearchConfigChanged(readWebSearchConfig(selectedProvider[0],
                        baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField));
            }
        };
    }

    private WebSearchConfig readWebSearchConfig(
            String provider,
            FormTextFieldView baseUrlField,
            FormTextFieldView apiKeyField,
            FormTextFieldView modelField,
            FormTextFieldView queryParamField,
            FormTextFieldView apiKeyHeaderField,
            FormTextFieldView apiKeyParamField
    ) {
        return new WebSearchConfig(
                provider,
                baseUrlField.getInput().getText().toString(),
                apiKeyField.getInput().getText().toString(),
                modelField.getInput().getText().toString(),
                queryParamField.getInput().getText().toString(),
                apiKeyHeaderField.getInput().getText().toString(),
                apiKeyParamField.getInput().getText().toString()
        );
    }

    private LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(null);
        LineTheme.padding(card, 0, 16, 0, 24);
        return card;
    }

    private LinearLayout actionButton(Context context, String label, int iconType, boolean primary, View.OnClickListener listener) {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(LineTheme.roundedStroke(context, primary ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 8, primary ? LineTheme.ACCENT : LineTheme.BORDER_LIGHT));
        button.setOnClickListener(listener);
        LineTheme.padding(button, LineTheme.SM, 0, LineTheme.SM, 0);
        IconButtonView icon = new IconButtonView(context, iconType);
        icon.setIconColor(primary ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY);
        icon.setIconSizeDp(15, 15);
        icon.setClickable(false);
        button.addView(icon, new LinearLayout.LayoutParams(LineTheme.dp(context, 15), LineTheme.dp(context, 15)));
        TextView text = LineTheme.text(context, label, LineTheme.FONT_XS, primary ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        text.setSingleLine(true);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = LineTheme.dp(context, 6);
        button.addView(text, textParams);
        return button;
    }

    private TextView title(Context context, String text) {
        return LineTheme.text(context, text, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.BOLD);
    }

    private void addSectionHeader(LinearLayout content, String text) {
        Context context = content.getContext();
        TextView title = LineTheme.textMedium(context, text, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY);
        title.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context, LineTheme.SM);
        params.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(title, params);
    }

    private TextView desc(Context context, String text) {
        TextView view = LineTheme.text(context, text, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        view.setLineSpacing(LineTheme.dp(context, 3), 1f);
        return view;
    }

    private LinearLayout.LayoutParams formParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context, LineTheme.MD);
        return params;
    }

    private void addCard(LinearLayout content, LinearLayout card) {
        Context context = content.getContext();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(card, params);
    }
}
