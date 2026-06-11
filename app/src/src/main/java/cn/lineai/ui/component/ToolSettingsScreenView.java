package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.ui.theme.LineTheme;

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
        super(context, "工具设置", listener::onBack, null);
        this.listener = listener;
        this.state = state == null ? new McpSettingsState(ToolSettingsRepository.EXECUTION_LOCAL, null) : state;
        this.imageUnderstandingModelLabel = imageUnderstandingModelLabel == null ? "" : imageUnderstandingModelLabel.trim();
        this.imageGenerationModelLabel = imageGenerationModelLabel == null ? "" : imageGenerationModelLabel.trim();
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);

        addSectionHeader(content, "图片操作");
        addImageUnderstanding(content);
        addImageGeneration(content);

        addSectionHeader(content, "搜索");
        addWebSearch(content);
    }

    private void addImageUnderstanding(LinearLayout content) {
        Context context = content.getContext();
        LinearLayout card = card(context);
        card.addView(title(context, "图片理解模型"));
        TextView desc = desc(context, "image_understanding 工具会读取本地或 SSH 工作区图片，并用这里选择的模型根据提示词返回图片内容。支持 OpenAI 兼容、Codex Responses 和 Anthropic Messages 协议。");
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        card.addView(desc, descParams);

        TextView selected = LineTheme.text(context,
                imageUnderstandingModelLabel.length() == 0 ? "未选择模型" : imageUnderstandingModelLabel,
                LineTheme.FONT_SM,
                imageUnderstandingModelLabel.length() == 0 ? LineTheme.TEXT_TERTIARY : LineTheme.TEXT,
                Typeface.BOLD);
        selected.setSingleLine(true);
        LinearLayout.LayoutParams selectedParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        selectedParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(selected, selectedParams);

        LinearLayout button = actionButton(context, "选择模型", IconButtonView.PAINTBRUSH, true, v -> listener.onOpenImageUnderstandingModelPicker());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 42));
        buttonParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(button, buttonParams);
        addCard(content, card);
    }

    private void addImageGeneration(LinearLayout content) {
        Context context = content.getContext();
        LinearLayout card = card(context);
        card.addView(title(context, "图片生成模型"));
        TextView desc = desc(context, "image_generation 工具会调用这里选择的生图模型生成图片。生成结果以内联 Markdown 图片返回，并直接显示在聊天内容里。");
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        card.addView(desc, descParams);

        TextView selected = LineTheme.text(context,
                imageGenerationModelLabel.length() == 0 ? "未选择模型" : imageGenerationModelLabel,
                LineTheme.FONT_SM,
                imageGenerationModelLabel.length() == 0 ? LineTheme.TEXT_TERTIARY : LineTheme.TEXT,
                Typeface.BOLD);
        selected.setSingleLine(true);
        LinearLayout.LayoutParams selectedParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        selectedParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(selected, selectedParams);

        LinearLayout button = actionButton(context, "选择模型", IconButtonView.PAINTBRUSH, true, v -> listener.onOpenImageGenerationModelPicker());
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
        card.addView(title(context, "网页搜索 API"));
        TextView desc = desc(context, "web_search 和 web_fetch 共用这组搜索 API、模型/搜索源和密钥。本地与 SSH 模式都使用应用侧网络配置。");
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        card.addView(desc, descParams);

        FormTextFieldView baseUrlField = new FormTextFieldView(context, "Search API URL", config.getBaseUrl(), "https://api.example.com/search", null, false, false);
        FormTextFieldView apiKeyField = new FormTextFieldView(context, "API Key", config.getApiKey(), "搜索服务密钥", null, false, true);
        FormTextFieldView modelField = new FormTextFieldView(context, "模型 / 搜索源", config.getModel(), "如 basic、advanced、google，可留空", null, false, false);
        FormTextFieldView queryParamField = new FormTextFieldView(context, "查询参数名", config.getQueryParam(), "q", null, false, false);
        FormTextFieldView apiKeyHeaderField = new FormTextFieldView(context, "密钥 Header", config.getApiKeyHeader(), "Authorization，可留空", null, false, false);
        FormTextFieldView apiKeyParamField = new FormTextFieldView(context, "密钥 Query 参数", config.getApiKeyParam(), "api_key，可留空", null, false, false);

        GridLayout providers = new GridLayout(context);
        providers.setColumnCount(3);
        addProviderButton(providers, "Tavily", WebSearchConfig.PROVIDER_TAVILY, selectedProvider, suppressChange,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        addProviderButton(providers, "Brave Search", WebSearchConfig.PROVIDER_BRAVE, selectedProvider, suppressChange,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        addProviderButton(providers, "SerpAPI", WebSearchConfig.PROVIDER_SERPAPI, selectedProvider, suppressChange,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        addProviderButton(providers, "Bing Search", WebSearchConfig.PROVIDER_BING, selectedProvider, suppressChange,
                baseUrlField, apiKeyField, modelField, queryParamField, apiKeyHeaderField, apiKeyParamField);
        addProviderButton(providers, "自定义", WebSearchConfig.PROVIDER_CUSTOM, selectedProvider, suppressChange,
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
    }

    private void addProviderButton(
            GridLayout providers,
            String label,
            String provider,
            String[] selectedProvider,
            boolean[] suppressChange,
            FormTextFieldView baseUrlField,
            FormTextFieldView apiKeyField,
            FormTextFieldView modelField,
            FormTextFieldView queryParamField,
            FormTextFieldView apiKeyHeaderField,
            FormTextFieldView apiKeyParamField
    ) {
        Context context = providers.getContext();
        boolean active = provider.equals(selectedProvider[0]);
        TextView button = LineTheme.text(context, label, LineTheme.FONT_XS, active ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackground(LineTheme.roundedStroke(context, active ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 8, active ? LineTheme.ACCENT : LineTheme.BORDER_LIGHT));
        button.setClickable(true);
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
        card.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(card, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
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
