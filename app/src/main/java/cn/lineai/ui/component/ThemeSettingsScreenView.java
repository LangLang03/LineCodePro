package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.model.ThemePalette;
import cn.lineai.model.ThemeSettingsState;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ThemeSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onThemeModeChanged(String mode);

        void onCustomThemeColorsSaved(Map<String, String> colors);
    }

    private static final ThemeOption[] THEMES = new ThemeOption[] {
            new ThemeOption(ThemePalette.MODE_SYSTEM, R.string.screen_theme_system, R.string.screen_theme_system_desc, IconButtonView.MONITOR),
            new ThemeOption(ThemePalette.MODE_LIGHT, R.string.screen_theme_light, R.string.screen_theme_light_desc, IconButtonView.SUN),
            new ThemeOption(ThemePalette.MODE_DARK, R.string.screen_theme_dark, R.string.screen_theme_dark_desc, IconButtonView.MOON),
            new ThemeOption(ThemePalette.MODE_COFFEE, R.string.screen_theme_coffee, R.string.screen_theme_coffee_desc, IconButtonView.COFFEE),
            new ThemeOption(ThemePalette.MODE_VSCODE, R.string.screen_theme_vscode, R.string.screen_theme_vscode_desc, IconButtonView.CODE),
            new ThemeOption(ThemePalette.MODE_GITHUB_DARK, R.string.screen_theme_github_dark, R.string.screen_theme_github_dark_desc, IconButtonView.GIT_BRANCH),
            new ThemeOption(ThemePalette.MODE_GRUVBOX, R.string.screen_theme_gruvbox, R.string.screen_theme_gruvbox_desc, IconButtonView.CODE),
            new ThemeOption(ThemePalette.MODE_HIGH_CONTRAST, R.string.screen_theme_high_contrast, R.string.screen_theme_high_contrast_desc, IconButtonView.CONTRAST),
            new ThemeOption(ThemePalette.MODE_CUSTOM, R.string.screen_theme_custom, R.string.screen_theme_custom_desc, IconButtonView.PAINTBRUSH)
    };

    private static final ColorField[] COLOR_FIELDS = new ColorField[] {
            new ColorField(ThemePalette.KEY_BG, R.string.screen_theme_color_background, R.string.screen_theme_color_background_desc),
            new ColorField(ThemePalette.KEY_SURFACE, R.string.screen_theme_color_surface, R.string.screen_theme_color_surface_desc),
            new ColorField(ThemePalette.KEY_SURFACE_ELEVATED, R.string.screen_theme_color_panel, R.string.screen_theme_color_panel_desc),
            new ColorField(ThemePalette.KEY_SURFACE_LIGHT, R.string.screen_theme_color_panel_light, R.string.screen_theme_color_panel_light_desc),
            new ColorField(ThemePalette.KEY_INPUT_BG, R.string.screen_theme_color_input, R.string.screen_theme_color_input_desc),
            new ColorField(ThemePalette.KEY_TEXT, R.string.screen_theme_color_text, R.string.screen_theme_color_text_desc),
            new ColorField(ThemePalette.KEY_TEXT_SECONDARY, R.string.screen_theme_color_text_secondary, R.string.screen_theme_color_text_secondary_desc),
            new ColorField(ThemePalette.KEY_TEXT_TERTIARY, R.string.screen_theme_color_text_tertiary, R.string.screen_theme_color_text_tertiary_desc),
            new ColorField(ThemePalette.KEY_TEXT_ON_COLOR, R.string.screen_theme_color_text_on_color, R.string.screen_theme_color_text_on_color_desc),
            new ColorField(ThemePalette.KEY_ACCENT, R.string.screen_theme_color_accent, R.string.screen_theme_color_accent_desc),
            new ColorField(ThemePalette.KEY_USER_BUBBLE, R.string.screen_theme_color_user_bubble, R.string.screen_theme_color_user_bubble_desc),
            new ColorField(ThemePalette.KEY_AI_BUBBLE, R.string.screen_theme_color_ai_bubble, R.string.screen_theme_color_ai_bubble_desc),
            new ColorField(ThemePalette.KEY_BORDER, R.string.screen_theme_color_border, R.string.screen_theme_color_border_desc),
            new ColorField(ThemePalette.KEY_BORDER_LIGHT, R.string.screen_theme_color_border_light, R.string.screen_theme_color_border_light_desc),
            new ColorField(ThemePalette.KEY_CODE_BG, R.string.screen_theme_color_code_background, R.string.screen_theme_color_code_background_desc),
            new ColorField(ThemePalette.KEY_CODE_BORDER, R.string.screen_theme_color_code_border, R.string.screen_theme_color_code_border_desc),
            new ColorField(ThemePalette.KEY_DANGER, R.string.screen_theme_color_danger, R.string.screen_theme_color_danger_desc),
            new ColorField(ThemePalette.KEY_WARNING, R.string.screen_theme_color_warning, R.string.screen_theme_color_warning_desc),
            new ColorField(ThemePalette.KEY_SUCCESS, R.string.screen_theme_color_success, R.string.screen_theme_color_success_desc)
    };

    private static final String[] SWATCHES = new String[] {
            "#F4EFE6", "#FBF7EF", "#EEE5D8", "#E7DCCA",
            "#2B2118", "#6C5A49", "#9B8976", "#D97757",
            "#B86F50", "#EFE4D4", "#DDD0BF", "#6A7F46",
            "#0A0A0A", "#1C1C1E", "#FFFFFF", "#0A84FF",
            "#1E1E1E", "#252526", "#007ACC", "#D4D4D4",
            "#0D1117", "#161B22", "#2F81F7", "#E6EDF3",
            "#282828", "#FABD2F", "#EBDBB2", "#458588",
            "#64D2FF", "#FFD60A", "#30D158", "#FF453A"
    };

    private final Listener listener;
    private final LinkedHashMap<String, String> draft = new LinkedHashMap<>();
    private final LinearLayout previewBox;
    private final LinearLayout previewBubble;
    private final TextView previewTitle;
    private final TextView previewText;
    private final TextView previewPill;
    private final Map<String, String> savedCustomColors;
    private LinearLayout saveAction;
    private IconButtonView saveIcon;
    private TextView saveText;
    private LinearLayout starterPanel;
    private final TextView swatchLabel;
    private final GridLayout swatchGrid;
    private final LinearLayout editorGroup;
    private String activeKey = ThemePalette.KEY_ACCENT;
    private String activeStarter = "default";

    public ThemeSettingsScreenView(Context context, ThemeSettingsState state, Listener listener) {
        super(context, context.getString(R.string.screen_theme_section_themes), listener::onBack, null);
        this.listener = listener;
        ThemeSettingsState safeState = state == null
                ? new ThemeSettingsState(ThemePalette.MODE_SYSTEM, ThemePalette.MODE_DARK, null, ThemePalette.forMode(ThemePalette.MODE_DARK))
                : state;
        draft.putAll(createThemeDraft(ThemePalette.forMode(ThemePalette.MODE_CUSTOM), safeState.getCustomColors()));
        savedCustomColors = new LinkedHashMap<>(safeState.getCustomColors());
        if (!safeState.getCustomColors().isEmpty()) {
            activeStarter = "saved";
        }

        LinearLayout content = getContent();
        addThemeModes(content, safeState.getThemeMode());
        addCustomHeader(content);
        addStarterPanel(content);

        previewBox = panel(context);
        previewBubble = new LinearLayout(context);
        previewBubble.setOrientation(LinearLayout.VERTICAL);
        LineTheme.padding(previewBubble, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        previewTitle = LineTheme.text(context, getResources().getString(R.string.screen_theme_section_preview), LineTheme.FONT_MD, LineTheme.TEXT, Typeface.BOLD);
        previewText = LineTheme.text(context, getResources().getString(R.string.screen_theme_section_preview_desc), LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams previewTextParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        previewTextParams.topMargin = LineTheme.dp(context, 4);
        previewBubble.addView(previewTitle, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        previewBubble.addView(previewText, previewTextParams);
        previewBox.addView(previewBubble, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        previewPill = LineTheme.text(context, getResources().getString(R.string.screen_theme_color_accent), LineTheme.FONT_XS, LineTheme.TEXT_ON_COLOR, Typeface.BOLD);
        previewPill.setGravity(Gravity.CENTER);
        LineTheme.padding(previewPill, LineTheme.MD, LineTheme.XS, LineTheme.MD, LineTheme.XS);
        LinearLayout.LayoutParams pillParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        pillParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        previewBox.addView(previewPill, pillParams);
        addPanel(content, previewBox);

        LinearLayout swatchPanel = panel(context);
        swatchLabel = LineTheme.textMedium(context, "", LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY);
        swatchPanel.addView(swatchLabel, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        swatchGrid = new GridLayout(context);
        swatchGrid.setColumnCount(7);
        LinearLayout.LayoutParams swatchGridParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        swatchGridParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        swatchPanel.addView(swatchGrid, swatchGridParams);
        addPanel(content, swatchPanel);

        editorGroup = new LinearLayout(context);
        editorGroup.setOrientation(LinearLayout.VERTICAL);
        editorGroup.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        editorParams.leftMargin = LineTheme.dp(context, LineTheme.LG);
        editorParams.rightMargin = LineTheme.dp(context, LineTheme.LG);
        editorParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(editorGroup, editorParams);

        refreshCustomViews();
    }

    private void addThemeModes(LinearLayout content, String themeMode) {
        Context context = content.getContext();
        SettingsSectionView themes = new SettingsSectionView(context, getResources().getString(R.string.screen_theme_section_themes));
        String currentMode = ThemePalette.normalizeMode(themeMode);
        for (int i = 0; i < THEMES.length; i++) {
            ThemeOption option = THEMES[i];
            themes.addRow(new OptionRowView(
                    context,
                    option.iconType,
                    getResources().getString(option.labelResId),
                    getResources().getString(option.descResId),
                    option.mode.equals(currentMode),
                    () -> listener.onThemeModeChanged(option.mode)
            ), i < THEMES.length - 1);
        }
        content.addView(themes, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addCustomHeader(LinearLayout content) {
        Context context = content.getContext();
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        SectionHeaderView title = new SectionHeaderView(context, getResources().getString(R.string.screen_theme_custom_colors));
        header.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        IconButtonView reset = new IconButtonView(context, IconButtonView.ROTATE_CCW);
        reset.setIconColor(LineTheme.TEXT_SECONDARY);
        reset.setIconSizeDp(34, 15);
        reset.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_LIGHT, 17));
        reset.setOnClickListener(v -> {
            activeStarter = "default";
            draft.clear();
            draft.putAll(createThemeDraft(ThemePalette.forMode(ThemePalette.MODE_CUSTOM), null));
            refreshCustomViews();
        });
        header.addView(reset, new LinearLayout.LayoutParams(LineTheme.dp(context, 34), LineTheme.dp(context, 34)));

        saveAction = new LinearLayout(context);
        saveAction.setOrientation(HORIZONTAL);
        saveAction.setGravity(Gravity.CENTER);
        saveAction.setClickable(true);
        LineTheme.padding(saveAction, LineTheme.MD, 0, LineTheme.MD, 0);
        saveAction.setOnClickListener(v -> {
            if (hasInvalidColor()) {
                Toast.makeText(getContext(), getResources().getString(R.string.screen_theme_color_invalid), Toast.LENGTH_SHORT).show();
                return;
            }
            listener.onCustomThemeColorsSaved(new LinkedHashMap<>(draft));
        });
        saveIcon = new IconButtonView(context, IconButtonView.SAVE);
        saveIcon.setIconColor(LineTheme.TEXT_ON_COLOR);
        saveIcon.setIconSizeDp(15, 15);
        saveIcon.setClickable(false);
        saveAction.addView(saveIcon, new LinearLayout.LayoutParams(LineTheme.dp(context, 15), LineTheme.dp(context, 15)));
        saveText = LineTheme.text(context, getResources().getString(R.string.screen_theme_color_save), LineTheme.FONT_SM, LineTheme.TEXT_ON_COLOR, Typeface.BOLD);
        LinearLayout.LayoutParams saveTextParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        saveTextParams.leftMargin = LineTheme.dp(context, LineTheme.XS);
        saveAction.addView(saveText, saveTextParams);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LineTheme.dp(context, 34));
        saveParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        header.addView(saveAction, saveParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context, LineTheme.XL);
        params.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(header, params);
    }

    private void addStarterPanel(LinearLayout content) {
        Context context = content.getContext();
        starterPanel = panel(context);
        refreshStarterPanel();
        addPanel(content, starterPanel);
    }

    private void refreshStarterPanel() {
        if (starterPanel == null) {
            return;
        }
        Context context = starterPanel.getContext();
        starterPanel.removeAllViews();
        starterPanel.addView(LineTheme.textMedium(context, getResources().getString(R.string.screen_theme_starter_section), LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(3);
        addStarter(grid, "default", R.string.screen_theme_starter_default, IconButtonView.PAINTBRUSH, ThemePalette.forMode(ThemePalette.MODE_CUSTOM));
        addStarter(grid, ThemePalette.MODE_LIGHT, R.string.screen_theme_starter_light, IconButtonView.SUN, ThemePalette.forMode(ThemePalette.MODE_LIGHT));
        addStarter(grid, ThemePalette.MODE_DARK, R.string.screen_theme_starter_dark, IconButtonView.MOON, ThemePalette.forMode(ThemePalette.MODE_DARK));
        addStarter(grid, ThemePalette.MODE_COFFEE, R.string.screen_theme_starter_coffee, IconButtonView.COFFEE, ThemePalette.forMode(ThemePalette.MODE_COFFEE));
        addStarter(grid, ThemePalette.MODE_VSCODE, R.string.screen_theme_starter_vscode, IconButtonView.CODE, ThemePalette.forMode(ThemePalette.MODE_VSCODE));
        addStarter(grid, ThemePalette.MODE_GITHUB_DARK, R.string.screen_theme_starter_github, IconButtonView.GIT_BRANCH, ThemePalette.forMode(ThemePalette.MODE_GITHUB_DARK));
        addStarter(grid, ThemePalette.MODE_GRUVBOX, R.string.screen_theme_starter_gruvbox, IconButtonView.CODE, ThemePalette.forMode(ThemePalette.MODE_GRUVBOX));
        addStarter(grid, ThemePalette.MODE_HIGH_CONTRAST, R.string.screen_theme_starter_high_contrast, IconButtonView.CONTRAST, ThemePalette.forMode(ThemePalette.MODE_HIGH_CONTRAST));
        if (!savedCustomColors.isEmpty()) {
            addStarter(grid, "saved", R.string.screen_theme_starter_saved, IconButtonView.SAVE, ThemePalette.forMode(ThemePalette.MODE_CUSTOM).withCustomColors(savedCustomColors));
        }
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        starterPanel.addView(grid, gridParams);
    }

    private void addStarter(GridLayout grid, String id, int labelResId, int iconType, ThemePalette palette) {
        Context context = grid.getContext();
        boolean active = id.equals(activeStarter);
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setClickable(true);
        button.setBackground(LineTheme.roundedStroke(context,
                active ? LineTheme.ACCENT_MUTED : LineTheme.SURFACE,
                8,
                active ? LineTheme.ACCENT : LineTheme.BORDER_LIGHT));
        LineTheme.padding(button, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.SM);
        button.setOnClickListener(v -> {
            activeStarter = id;
            draft.clear();
            draft.putAll(starterDraft(id, palette));
            refreshCustomViews();
        });

        LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(HORIZONTAL);
        int[] colors = new int[] {palette.bg, palette.aiBubble, palette.accent};
        for (int i = 0; i < colors.length; i++) {
            View chip = new View(context);
            chip.setBackground(LineTheme.roundedStroke(context, colors[i], 9, Color.argb(32, 0, 0, 0)));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18));
            if (i > 0) chipParams.leftMargin = LineTheme.dp(context, -4);
            chips.addView(chip, chipParams);
        }
        button.addView(chips, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        IconButtonView starterIcon = new IconButtonView(context, iconType);
        starterIcon.setIconColor(active ? LineTheme.ACCENT : LineTheme.TEXT_SECONDARY);
        starterIcon.setIconSizeDp(14, 14);
        starterIcon.setClickable(false);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 14), LineTheme.dp(context, 14));
        iconParams.topMargin = LineTheme.dp(context, 6);
        button.addView(starterIcon, iconParams);
        TextView text = LineTheme.text(context, getResources().getString(labelResId), LineTheme.FONT_XS,
                active ? LineTheme.ACCENT : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        textParams.topMargin = LineTheme.dp(context, 4);
        button.addView(text, textParams);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(0, LineTheme.dp(context, LineTheme.SM), LineTheme.dp(context, LineTheme.SM), 0);
        grid.addView(button, params);
    }

    private void refreshCustomViews() {
        int bg = color(ThemePalette.KEY_BG);
        int border = color(ThemePalette.KEY_BORDER);
        int aiBubble = color(ThemePalette.KEY_AI_BUBBLE);
        int text = color(ThemePalette.KEY_TEXT);
        int textSecondary = color(ThemePalette.KEY_TEXT_SECONDARY);
        int accent = color(ThemePalette.KEY_ACCENT);
        int textOnColor = ThemePalette.forMode(ThemePalette.MODE_CUSTOM).textOnColor;

        previewBox.setBackground(LineTheme.roundedStroke(getContext(), bg, 12, border));
        previewBubble.setBackground(LineTheme.rounded(getContext(), aiBubble, 8));
        previewTitle.setTextColor(text);
        previewText.setTextColor(textSecondary);
        previewPill.setTextColor(textOnColor);
        previewPill.setBackground(LineTheme.rounded(getContext(), accent, 999));
        updateSaveAction();
        refreshStarterPanel();
        refreshSwatches();
        refreshEditor();
    }

    private void refreshSwatches() {
        Context context = getContext();
        swatchLabel.setText(getResources().getString(R.string.theme_current_editing, labelFor(activeKey)));
        swatchGrid.removeAllViews();
        String activeValue = draft.get(activeKey);
        for (String value : SWATCHES) {
            FrameLayout swatch = new FrameLayout(context);
            swatch.setClickable(true);
            boolean active = value.equalsIgnoreCase(activeValue);
            swatch.setBackground(LineTheme.roundedStroke(context, Color.parseColor(value), 17, active ? LineTheme.ACCENT : LineTheme.BORDER_LIGHT));
            swatch.setOnClickListener(v -> {
                draft.put(activeKey, value);
                activeStarter = "custom-editing";
                refreshCustomViews();
            });
            if (active) {
                IconButtonView check = new IconButtonView(context, IconButtonView.CHECK);
                check.setIconColor(LineTheme.TEXT_ON_COLOR);
                check.setIconSizeDp(14, 14);
                check.setClickable(false);
                swatch.addView(check, new FrameLayout.LayoutParams(LineTheme.dp(context, 14), LineTheme.dp(context, 14), Gravity.CENTER));
            }
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = LineTheme.dp(context, 34);
            params.height = LineTheme.dp(context, 34);
            params.setMargins(0, LineTheme.dp(context, LineTheme.SM), LineTheme.dp(context, LineTheme.SM), 0);
            swatchGrid.addView(swatch, params);
        }
    }

    private void refreshEditor() {
        Context context = getContext();
        editorGroup.removeAllViews();
        for (int i = 0; i < COLOR_FIELDS.length; i++) {
            ColorField field = COLOR_FIELDS[i];
            editorGroup.addView(colorRow(
                            context,
                            field.key,
                            getResources().getString(field.labelResId),
                            getResources().getString(field.descResId),
                            i < COLOR_FIELDS.length - 1
                    ),
                    new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private View colorRow(Context context, String key, String label, String desc, boolean divider) {
        LinearLayout rowWrapper = new LinearLayout(context);
        rowWrapper.setOrientation(VERTICAL);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(LineTheme.dp(context, 66));
        row.setBackgroundColor(activeKey.equals(key) ? LineTheme.ACCENT_MUTED : Color.TRANSPARENT);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            activeKey = key;
            refreshCustomViews();
        });
        LineTheme.padding(row, LineTheme.MD, 0, LineTheme.MD, 0);

        String value = draft.get(key);
        boolean valid = ThemePalette.isHexColor(value);
        View preview = new View(context);
        preview.setBackground(LineTheme.roundedStroke(context, valid ? Color.parseColor(value) : LineTheme.SURFACE_LIGHT, 15, LineTheme.BORDER_LIGHT));
        row.addView(preview, new LinearLayout.LayoutParams(LineTheme.dp(context, 30), LineTheme.dp(context, 30)));

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        metaParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        row.addView(meta, metaParams);
        meta.addView(LineTheme.textMedium(context, label, LineTheme.FONT_MD, LineTheme.TEXT));
        TextView descView = LineTheme.text(context, valid ? desc : getResources().getString(R.string.screen_theme_color_hex_hint), LineTheme.FONT_XS,
                valid ? LineTheme.TEXT_TERTIARY : LineTheme.DANGER, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        meta.addView(descView, descParams);

        EditText input = new EditText(context);
        input.setText(value);
        input.setTextColor(valid ? LineTheme.TEXT : LineTheme.DANGER);
        input.setTextSize(LineTheme.FONT_SM);
        input.setSingleLine(true);
        input.setTypeface(Typeface.MONOSPACE);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(9)});
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(getResources().getString(R.string.screen_theme_color_hex_placeholder));
        input.setHintTextColor(LineTheme.TEXT_TERTIARY);
        input.setSelectAllOnFocus(false);
        input.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_LIGHT, 8, valid ? LineTheme.BORDER_LIGHT : LineTheme.DANGER));
        input.setPadding(LineTheme.dp(context, LineTheme.SM), 0, LineTheme.dp(context, LineTheme.SM), 0);
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !activeKey.equals(key)) {
                activeKey = key;
                refreshSwatches();
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String next = s == null ? "" : s.toString().trim();
                if (next.length() > 0 && !next.startsWith("#")) {
                    next = "#" + next;
                }
                draft.put(key, next);
                activeKey = key;
                activeStarter = "custom-editing";
                boolean nextValid = ThemePalette.isHexColor(next);
                input.setTextColor(nextValid ? LineTheme.TEXT : LineTheme.DANGER);
                input.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.SURFACE_LIGHT, 8, nextValid ? LineTheme.BORDER_LIGHT : LineTheme.DANGER));
                preview.setBackground(LineTheme.roundedStroke(getContext(), nextValid ? Color.parseColor(next) : LineTheme.SURFACE_LIGHT, 15, LineTheme.BORDER_LIGHT));
                descView.setText(nextValid ? desc : getResources().getString(R.string.screen_theme_color_hex_hint));
                descView.setTextColor(nextValid ? LineTheme.TEXT_TERTIARY : LineTheme.DANGER);
                updatePreviewOnly();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        row.addView(input, new LinearLayout.LayoutParams(LineTheme.dp(context, 92), LineTheme.dp(context, 38)));
        rowWrapper.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        if (divider) {
            View line = new View(context);
            line.setBackgroundColor(LineTheme.BORDER_LIGHT);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1);
            lineParams.leftMargin = LineTheme.dp(context, 58);
            rowWrapper.addView(line, lineParams);
        }
        return rowWrapper;
    }

    private void updatePreviewOnly() {
        updateSaveAction();
        if (!ThemePalette.isHexColor(draft.get(activeKey))) {
            return;
        }
        previewBox.setBackground(LineTheme.roundedStroke(getContext(), color(ThemePalette.KEY_BG), 12, color(ThemePalette.KEY_BORDER)));
        previewBubble.setBackground(LineTheme.rounded(getContext(), color(ThemePalette.KEY_AI_BUBBLE), 8));
        previewTitle.setTextColor(color(ThemePalette.KEY_TEXT));
        previewText.setTextColor(color(ThemePalette.KEY_TEXT_SECONDARY));
        previewPill.setBackground(LineTheme.rounded(getContext(), color(ThemePalette.KEY_ACCENT), 999));
        refreshSwatches();
    }

    private void updateSaveAction() {
        if (saveAction == null || saveIcon == null || saveText == null) {
            return;
        }
        boolean valid = !hasInvalidColor();
        saveAction.setEnabled(valid);
        saveAction.setBackground(LineTheme.rounded(getContext(), valid ? LineTheme.ACCENT : LineTheme.SURFACE_LIGHT, 17));
        int color = valid ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT_TERTIARY;
        saveIcon.setIconColor(color);
        saveText.setTextColor(color);
    }

    private boolean hasInvalidColor() {
        for (String key : ThemePalette.EDITABLE_KEYS) {
            if (!ThemePalette.isHexColor(draft.get(key))) {
                return true;
            }
        }
        return false;
    }

    private int color(String key) {
        ThemePalette fallback = ThemePalette.forMode(ThemePalette.MODE_CUSTOM);
        return ThemePalette.parseHex(draft.get(key), fallback.colorForKey(key));
    }

    private String labelFor(String key) {
        for (ColorField field : COLOR_FIELDS) {
            if (field.key.equals(key)) {
                return getResources().getString(field.labelResId);
            }
        }
        return key;
    }

    private static final class ThemeOption {
        private final String mode;
        private final int labelResId;
        private final int descResId;
        private final int iconType;

        ThemeOption(String mode, int labelResId, int descResId, int iconType) {
            this.mode = mode;
            this.labelResId = labelResId;
            this.descResId = descResId;
            this.iconType = iconType;
        }
    }

    private static final class ColorField {
        private final String key;
        private final int labelResId;
        private final int descResId;

        ColorField(String key, int labelResId, int descResId) {
            this.key = key;
            this.labelResId = labelResId;
            this.descResId = descResId;
        }
    }

    private Map<String, String> createThemeDraft(ThemePalette base, Map<String, String> stored) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>(base.editableHexMap());
        if (stored != null) {
            for (String key : ThemePalette.EDITABLE_KEYS) {
                String value = stored.get(key);
                if (ThemePalette.isHexColor(value)) {
                    values.put(key, value);
                }
            }
        }
        return values;
    }

    private Map<String, String> starterDraft(String id, ThemePalette palette) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>(palette.editableHexMap());
        if ("saved".equals(id)) {
            values.clear();
            values.putAll(createThemeDraft(ThemePalette.forMode(ThemePalette.MODE_CUSTOM), savedCustomColors));
            return values;
        }
        if (ThemePalette.MODE_LIGHT.equals(id)) {
            values.put(ThemePalette.KEY_CODE_BG, "#F2F2F7");
        } else if (ThemePalette.MODE_DARK.equals(id)) {
            values.put(ThemePalette.KEY_CODE_BG, "#151515");
        } else if (ThemePalette.MODE_COFFEE.equals(id)) {
            values.put(ThemePalette.KEY_CODE_BG, "#EFE4D4");
        }
        return values;
    }

    private LinearLayout panel(Context context) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(VERTICAL);
        panel.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(panel, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        return panel;
    }

    private void addPanel(LinearLayout content, LinearLayout panel) {
        Context context = content.getContext();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.leftMargin = LineTheme.dp(context, LineTheme.LG);
        params.rightMargin = LineTheme.dp(context, LineTheme.LG);
        params.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(panel, params);
    }
}
