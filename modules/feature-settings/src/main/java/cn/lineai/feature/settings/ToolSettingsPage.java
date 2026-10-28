package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.WebSearchConfig;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Image-tool model slots and the web-search provider form. */
public final class ToolSettingsPage extends SettingsPage {

    public interface Listener {
        void onPickImageUnderstanding();

        void onPickImageGeneration();

        void onWebSearchChanged(WebSearchConfig config);
    }

    private final Listener listener;
    private final FormField baseUrl;
    private final FormField apiKey;
    private final FormField model;
    private final FormField queryParam;
    private final FormField apiKeyHeader;
    private final FormField apiKeyParam;
    private final List<TextView> providerButtons = new ArrayList<>();
    private final List<String> providerKeys = new ArrayList<>();
    private final String[] selectedProvider;
    private boolean suppress;
    private int section;

    public ToolSettingsPage(Context context, Tokens tokens, Runnable back, String understandingLabel,
                            String generationLabel, WebSearchConfig search, Listener listener) {
        super(context, R.string.screen_tools_title, tokens, back);
        this.listener = listener;
        this.selectedProvider = new String[] {search.provider};
        Ui.padding(content, 16, 16, 16, 100);

        sectionHeader(R.string.screen_tools_section_images);
        imageCard(R.string.screen_tools_image_understanding_label,
                R.string.screen_tools_image_understanding_desc, understandingLabel,
                Icons.PAINTBRUSH, listener::onPickImageUnderstanding);
        imageCard(R.string.screen_tools_image_generation_label,
                R.string.screen_tools_image_generation_desc, generationLabel,
                Icons.SPARKLES, listener::onPickImageGeneration);

        sectionHeader(R.string.screen_tools_section_search);
        LinearLayout card = card();
        card.addView(Ui.bold(context, context.getString(R.string.screen_tools_web_search_label),
                Tokens.FONT_MD, tokens.text), new LayoutParams(-1, -2));
        TextView desc = desc(context.getString(R.string.screen_tools_web_search_desc));
        LinearLayout.LayoutParams descParams = new LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 2);
        card.addView(desc, descParams);

        baseUrl = new FormField(context, theme, R.string.screen_tools_field_search_url,
                search.baseUrl, "https://api.example.com/search", false);
        apiKey = new FormField(context, theme, R.string.screen_tools_field_api_key,
                search.apiKey, context.getString(R.string.screen_tools_hint_api_key), true);
        model = new FormField(context, theme, R.string.screen_tools_field_search_model,
                search.model, context.getString(R.string.screen_tools_hint_model), false);
        queryParam = new FormField(context, theme, R.string.screen_tools_field_query_param,
                search.queryParam, "q", false);
        apiKeyHeader = new FormField(context, theme, R.string.screen_tools_field_key_header,
                search.apiKeyHeader, context.getString(R.string.screen_tools_hint_key_header), false);
        apiKeyParam = new FormField(context, theme, R.string.screen_tools_field_key_query,
                search.apiKeyParam, context.getString(R.string.screen_tools_hint_key_query), false);

        GridLayout providers = new GridLayout(context);
        providers.setColumnCount(3);
        addProvider(providers, R.string.screen_tools_provider_bing_rss_free,
                WebSearchConfig.PROVIDER_BING_RSS_FREE);
        addProvider(providers, R.string.screen_tools_provider_tavily, WebSearchConfig.PROVIDER_TAVILY);
        addProvider(providers, R.string.screen_tools_provider_brave, WebSearchConfig.PROVIDER_BRAVE);
        addProvider(providers, R.string.screen_tools_provider_serpapi, WebSearchConfig.PROVIDER_SERPAPI);
        addProvider(providers, R.string.screen_tools_provider_bing, WebSearchConfig.PROVIDER_BING);
        addProvider(providers, R.string.screen_tools_provider_custom, WebSearchConfig.PROVIDER_CUSTOM);
        LinearLayout.LayoutParams providerParams = new LayoutParams(-1, -2);
        providerParams.topMargin = Ui.dp(context, 12);
        card.addView(providers, providerParams);

        TextWatcher watcher = watcher();
        for (FormField field : new FormField[] {baseUrl, apiKey, model, queryParam, apiKeyHeader, apiKeyParam}) {
            field.input().addTextChangedListener(watcher);
            card.addView(field, formParams());
        }
        addCard(card);
        applyVisibility();
    }

    private void sectionHeader(int title) {
        TextView view = Ui.medium(getContext(), getContext().getString(title).toUpperCase(java.util.Locale.ROOT),
                Tokens.FONT_XS, theme.current().textTertiary);
        view.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams params = new LayoutParams(-1, -2);
        params.topMargin = Ui.dp(getContext(), section == 0 ? 8 : 8);
        params.bottomMargin = Ui.dp(getContext(), 8);
        content.addView(view, params);
        section++;
        theme.bind(t -> view.setTextColor(t.textTertiary));
    }

    private void imageCard(int title, int description, String selectedLabel, int icon, Runnable pick) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = card();
        card.addView(Ui.bold(context, context.getString(title), Tokens.FONT_MD, tokens.text),
                new LayoutParams(-1, -2));
        TextView desc = desc(context.getString(description));
        LinearLayout.LayoutParams descParams = new LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 2);
        card.addView(desc, descParams);
        boolean hasLabel = selectedLabel != null && !selectedLabel.trim().isEmpty();
        TextView selected = Ui.bold(context, hasLabel ? selectedLabel
                        : context.getString(R.string.screen_tools_no_model_selected),
                Tokens.FONT_SM, hasLabel ? tokens.text : tokens.textTertiary);
        selected.setSingleLine(true);
        LinearLayout.LayoutParams selectedParams = new LayoutParams(-1, -2);
        selectedParams.topMargin = Ui.dp(context, 12);
        card.addView(selected, selectedParams);
        LinearLayout button = actionButton(context, tokens, R.string.screen_tools_pick_model, icon, pick);
        LinearLayout.LayoutParams buttonParams = new LayoutParams(-1, Ui.dp(context, 42));
        buttonParams.topMargin = Ui.dp(context, 12);
        card.addView(button, buttonParams);
        addCard(card);
    }

    private LinearLayout actionButton(Context context, Tokens tokens, int label, int icon, Runnable action) {
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.roundedStroke(context, tokens.accent, 8, tokens.accent));
        button.setOnClickListener(v -> action.run());
        Ui.padding(button, 8, 0, 8, 0);
        IconButton glyph = new IconButton(context, icon);
        glyph.setIconColor(tokens.textOnColor);
        glyph.setIconSizeDp(15, 15);
        glyph.setClickable(false);
        button.addView(glyph, new LayoutParams(Ui.dp(context, 15), Ui.dp(context, 15)));
        TextView text = Ui.bold(context, context.getString(label), Tokens.FONT_XS, tokens.textOnColor);
        text.setSingleLine(true);
        LinearLayout.LayoutParams textParams = new LayoutParams(-2, -2);
        textParams.leftMargin = Ui.dp(context, 6);
        button.addView(text, textParams);
        return button;
    }

    private void addProvider(GridLayout grid, int label, String provider) {
        Context context = getContext();
        Tokens tokens = theme.current();
        boolean active = provider.equals(selectedProvider[0]);
        TextView button = Ui.bold(context, context.getString(label), Tokens.FONT_XS,
                active ? tokens.textOnColor : tokens.textSecondary);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Ui.roundedStroke(context, active ? tokens.accent : tokens.surfaceLight, 8,
                active ? tokens.accent : tokens.borderLight));
        button.setClickable(true);
        providerButtons.add(button);
        providerKeys.add(provider);
        button.setOnClickListener(v -> selectProvider(provider));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = Ui.dp(context, 34);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(0, Ui.dp(context, 8), Ui.dp(context, 8), 0);
        grid.addView(button, params);
    }

    private void selectProvider(String provider) {
        WebSearchConfig defaults = WebSearchConfig.defaultConfig(provider);
        selectedProvider[0] = defaults.provider;
        suppress = true;
        baseUrl.input().setText(defaults.baseUrl);
        apiKey.input().setText("");
        model.input().setText(defaults.model);
        queryParam.input().setText(defaults.queryParam);
        apiKeyHeader.input().setText(defaults.apiKeyHeader);
        apiKeyParam.input().setText(defaults.apiKeyParam);
        suppress = false;
        Tokens tokens = theme.current();
        for (int i = 0; i < providerButtons.size(); i++) {
            boolean active = providerKeys.get(i).equals(selectedProvider[0]);
            TextView button = providerButtons.get(i);
            button.setTextColor(active ? tokens.textOnColor : tokens.textSecondary);
            button.setBackground(Ui.roundedStroke(getContext(), active ? tokens.accent : tokens.surfaceLight,
                    8, active ? tokens.accent : tokens.borderLight));
        }
        applyVisibility();
        listener.onWebSearchChanged(readConfig());
    }

    private void applyVisibility() {
        boolean free = WebSearchConfig.PROVIDER_BING_RSS_FREE.equals(selectedProvider[0]);
        int visibility = free ? View.GONE : View.VISIBLE;
        for (FormField field : new FormField[] {baseUrl, apiKey, model, queryParam, apiKeyHeader, apiKeyParam}) {
            field.setVisibility(visibility);
            field.input().setEnabled(!free);
        }
    }

    private TextWatcher watcher() {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!suppress) listener.onWebSearchChanged(readConfig());
            }
        };
    }

    private WebSearchConfig readConfig() {
        return new WebSearchConfig(selectedProvider[0], baseUrl.value(), apiKey.value(),
                model.value(), queryParam.value(), apiKeyHeader.value(), apiKeyParam.value());
    }

    private LinearLayout card() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);
        theme.bind(t -> card.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }

    private void addCard(View card) {
        LinearLayout.LayoutParams params = new LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(getContext(), 12);
        content.addView(card, params);
    }

    private TextView desc(String text) {
        TextView view = Ui.text(getContext(), text, Tokens.FONT_XS, theme.current().textTertiary,
                Typeface.NORMAL);
        view.setLineSpacing(Ui.dp(getContext(), 3), 1f);
        return view;
    }

    private LinearLayout.LayoutParams formParams() {
        LinearLayout.LayoutParams params = new LayoutParams(-1, -2);
        params.topMargin = Ui.dp(getContext(), 12);
        return params;
    }
}
