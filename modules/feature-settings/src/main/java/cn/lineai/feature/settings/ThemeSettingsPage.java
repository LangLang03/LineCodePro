package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import cn.lineai.core.model.ThemePalette;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Theme mode picker plus the custom palette editor. Mirrors the legacy layout one-to-one. */
public final class ThemeSettingsPage extends SettingsPage {

    private static final Mode[] MODES = {
            new Mode(ThemePalette.MODE_SYSTEM, R.string.screen_theme_system, R.string.screen_theme_system_desc, Icons.MONITOR),
            new Mode(ThemePalette.MODE_LIGHT, R.string.screen_theme_light, R.string.screen_theme_light_desc, Icons.SUN),
            new Mode(ThemePalette.MODE_DARK, R.string.screen_theme_dark, R.string.screen_theme_dark_desc, Icons.MOON),
            new Mode(ThemePalette.MODE_COFFEE, R.string.screen_theme_coffee, R.string.screen_theme_coffee_desc, Icons.COFFEE),
            new Mode(ThemePalette.MODE_VSCODE, R.string.screen_theme_vscode, R.string.screen_theme_vscode_desc, Icons.CODE),
            new Mode(ThemePalette.MODE_GITHUB_DARK, R.string.screen_theme_github_dark, R.string.screen_theme_github_dark_desc, Icons.GIT_BRANCH),
            new Mode(ThemePalette.MODE_GRUVBOX, R.string.screen_theme_gruvbox, R.string.screen_theme_gruvbox_desc, Icons.CODE),
            new Mode(ThemePalette.MODE_HIGH_CONTRAST, R.string.screen_theme_high_contrast, R.string.screen_theme_high_contrast_desc, Icons.CONTRAST),
            new Mode(ThemePalette.MODE_CUSTOM, R.string.screen_theme_custom, R.string.screen_theme_custom_desc, Icons.PAINTBRUSH)
    };

    private static final Field[] FIELDS = {
            new Field(ThemePalette.KEY_BG, R.string.screen_theme_color_background, R.string.screen_theme_color_background_desc),
            new Field(ThemePalette.KEY_SURFACE, R.string.screen_theme_color_surface, R.string.screen_theme_color_surface_desc),
            new Field(ThemePalette.KEY_SURFACE_ELEVATED, R.string.screen_theme_color_panel, R.string.screen_theme_color_panel_desc),
            new Field(ThemePalette.KEY_SURFACE_LIGHT, R.string.screen_theme_color_panel_light, R.string.screen_theme_color_panel_light_desc),
            new Field(ThemePalette.KEY_INPUT_BG, R.string.screen_theme_color_input, R.string.screen_theme_color_input_desc),
            new Field(ThemePalette.KEY_TEXT, R.string.screen_theme_color_text, R.string.screen_theme_color_text_desc),
            new Field(ThemePalette.KEY_TEXT_SECONDARY, R.string.screen_theme_color_text_secondary, R.string.screen_theme_color_text_secondary_desc),
            new Field(ThemePalette.KEY_TEXT_TERTIARY, R.string.screen_theme_color_text_tertiary, R.string.screen_theme_color_text_tertiary_desc),
            new Field(ThemePalette.KEY_TEXT_ON_COLOR, R.string.screen_theme_color_text_on_color, R.string.screen_theme_color_text_on_color_desc),
            new Field(ThemePalette.KEY_ACCENT, R.string.screen_theme_color_accent, R.string.screen_theme_color_accent_desc),
            new Field(ThemePalette.KEY_USER_BUBBLE, R.string.screen_theme_color_user_bubble, R.string.screen_theme_color_user_bubble_desc),
            new Field(ThemePalette.KEY_AI_BUBBLE, R.string.screen_theme_color_ai_bubble, R.string.screen_theme_color_ai_bubble_desc),
            new Field(ThemePalette.KEY_BORDER, R.string.screen_theme_color_border, R.string.screen_theme_color_border_desc),
            new Field(ThemePalette.KEY_BORDER_LIGHT, R.string.screen_theme_color_border_light, R.string.screen_theme_color_border_light_desc),
            new Field(ThemePalette.KEY_CODE_BG, R.string.screen_theme_color_code_background, R.string.screen_theme_color_code_background_desc),
            new Field(ThemePalette.KEY_CODE_BORDER, R.string.screen_theme_color_code_border, R.string.screen_theme_color_code_border_desc),
            new Field(ThemePalette.KEY_DANGER, R.string.screen_theme_color_danger, R.string.screen_theme_color_danger_desc),
            new Field(ThemePalette.KEY_WARNING, R.string.screen_theme_color_warning, R.string.screen_theme_color_warning_desc),
            new Field(ThemePalette.KEY_SUCCESS, R.string.screen_theme_color_success, R.string.screen_theme_color_success_desc)
    };

    private static final String[] SWATCHES = {
            "#F4EFE6", "#FBF7EF", "#EEE5D8", "#E7DCCA",
            "#2B2118", "#6C5A49", "#9B8976", "#D97757",
            "#B86F50", "#EFE4D4", "#DDD0BF", "#6A7F46",
            "#0A0A0A", "#1C1C1E", "#FFFFFF", "#0A84FF",
            "#1E1E1E", "#252526", "#007ACC", "#D4D4D4",
            "#0D1117", "#161B22", "#2F81F7", "#E6EDF3",
            "#282828", "#FABD2F", "#EBDBB2", "#458588",
            "#64D2FF", "#FFD60A", "#30D158", "#FF453A"
    };

    private final LinkedHashMap<String, String> draft = new LinkedHashMap<>();
    private final Map<String, String> savedColors;
    private final Consumer<String> onModeChanged;
    private final Consumer<Map<String, String>> onSaveColors;
    private final LinearLayout previewBox;
    private final LinearLayout previewBubble;
    private final TextView previewTitle;
    private final TextView previewText;
    private final TextView previewPill;
    private final LinearLayout saveAction;
    private final List<PreferenceRow> modeRows = new ArrayList<>();
    private IconButton saveIcon;
    private TextView saveText;
    private final LinearLayout starterPanel;
    private final TextView swatchLabel;
    private final GridLayout swatchGrid;
    private final LinearLayout editorGroup;
    private String mode;
    private String activeKey = ThemePalette.KEY_ACCENT;
    private String activeStarter;
    private Tokens rendered;

    public ThemeSettingsPage(Context context, Tokens tokens, Runnable back, String mode,
                             Map<String, String> savedColors, Consumer<String> onModeChanged,
                             Consumer<Map<String, String>> onSaveColors) {
        super(context, R.string.screen_theme_section_themes, tokens, back);
        this.mode = ThemePalette.normalizeMode(mode);
        this.rendered = tokens;
        this.savedColors = new LinkedHashMap<>(savedColors == null ? Collections.<String, String>emptyMap() : savedColors);
        this.onModeChanged = onModeChanged;
        this.onSaveColors = onSaveColors;
        this.activeStarter = this.savedColors.isEmpty() ? "default" : "saved";
        draft.putAll(themeDraft(ThemePalette.customDefault(), this.savedColors));

        saveAction = buildSaveAction();
        addModes();
        addCustomHeader();
        starterPanel = panel();
        addPanel(starterPanel);
        previewBox = panel();
        previewBubble = new LinearLayout(context);
        previewBubble.setOrientation(VERTICAL);
        Ui.padding(previewBubble, 12, 12, 12, 12);
        previewTitle = Ui.bold(context, context.getString(R.string.screen_theme_section_preview),
                Tokens.FONT_MD, rendered.text);
        previewText = Ui.text(context, context.getString(R.string.screen_theme_section_preview_desc),
                Tokens.FONT_SM, rendered.textSecondary, Typeface.NORMAL);
        LayoutParams textParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        textParams.topMargin = Ui.dp(context, 4);
        previewBubble.addView(previewTitle, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        previewBubble.addView(previewText, textParams);
        previewBox.addView(previewBubble, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        previewPill = Ui.bold(context, context.getString(R.string.screen_theme_color_accent),
                Tokens.FONT_XS, rendered.textOnColor);
        previewPill.setGravity(Gravity.CENTER);
        Ui.padding(previewPill, 12, 4, 12, 4);
        LayoutParams pillParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        pillParams.topMargin = Ui.dp(context, 12);
        previewBox.addView(previewPill, pillParams);
        addPanel(previewBox);

        LinearLayout swatchPanel = panel();
        swatchLabel = Ui.medium(context, "", Tokens.FONT_SM, rendered.textSecondary);
        swatchPanel.addView(swatchLabel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        swatchGrid = new GridLayout(context);
        swatchGrid.setColumnCount(7);
        LayoutParams gridParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = Ui.dp(context, 8);
        swatchPanel.addView(swatchGrid, gridParams);
        addPanel(swatchPanel);

        editorGroup = new LinearLayout(context);
        editorGroup.setOrientation(LinearLayout.VERTICAL);
        LayoutParams editorParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        editorParams.leftMargin = editorParams.rightMargin = Ui.dp(context, 16);
        editorParams.topMargin = Ui.dp(context, 12);
        content.addView(editorGroup, editorParams);

        refreshCustomViews();
    }

    private void addModes() {
        SettingsSection section = section(R.string.screen_theme_section_themes);
        for (int i = 0; i < MODES.length; i++) {
            Mode option = MODES[i];
            PreferenceRow row = new PreferenceRow(getContext(), theme, PreferenceRow.Style.OPTION,
                    option.icon, option.label, option.description);
            row.active(option.mode.equals(mode));
            row.setOnClickListener(v -> {
                mode = option.mode;
                onModeChanged.accept(option.mode);
                refreshModeRows();
            });
            modeRows.add(row);
            section.row(row, i < MODES.length - 1, 0);
        }
    }

    private void refreshModeRows() {
        for (int i = 0; i < modeRows.size(); i++) {
            modeRows.get(i).active(MODES[i].mode.equals(mode));
        }
    }

    private void addCustomHeader() {
        Context context = getContext();
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(header, 16, 0, 16, 0);
        TextView title = Ui.medium(context,
                context.getString(R.string.screen_theme_custom_colors).toUpperCase(java.util.Locale.ROOT),
                Tokens.FONT_XS, rendered.textTertiary);
        title.setLetterSpacing(0.05f);
        Ui.padding(title, 0, 0, 8, 0);
        header.addView(title, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        IconButton reset = new IconButton(context, Icons.ROTATE_CCW);
        reset.setIconColor(rendered.textSecondary);
        reset.setIconSizeDp(34, 15);
        reset.setBackground(Ui.rounded(context, rendered.surfaceLight, 17));
        reset.setContentDescription(context.getString(R.string.common_reset));
        reset.setOnClickListener(v -> {
            activeStarter = null;
            draft.clear();
            draft.putAll(themeDraft(ThemePalette.customDefault(), null));
            refreshCustomViews();
        });
        header.addView(reset, new LayoutParams(Ui.dp(context, 34), Ui.dp(context, 34)));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, Ui.dp(context, 34));
        saveParams.leftMargin = Ui.dp(context, 8);
        header.addView(saveAction, saveParams);
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(context, 20);
        params.bottomMargin = Ui.dp(context, 8);
        content.addView(header, params);
    }

    private LinearLayout buildSaveAction() {
        Context context = getContext();
        LinearLayout action = new LinearLayout(context);
        action.setGravity(Gravity.CENTER);
        action.setClickable(true);
        Ui.padding(action, 12, 0, 12, 0);
        action.setOnClickListener(v -> {
            if (invalid()) {
                Toast.makeText(context, R.string.screen_theme_color_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            onSaveColors.accept(new LinkedHashMap<>(draft));
        });
        saveIcon = new IconButton(context, Icons.SAVE);
        saveIcon.setIconColor(rendered.textOnColor);
        saveIcon.setIconSizeDp(15, 15);
        saveIcon.setClickable(false);
        action.addView(saveIcon, new LayoutParams(Ui.dp(context, 15), Ui.dp(context, 15)));
        saveText = Ui.bold(context, context.getString(R.string.screen_theme_color_save),
                Tokens.FONT_SM, rendered.textOnColor);
        LayoutParams textParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = Ui.dp(context, 4);
        action.addView(saveText, textParams);
        return action;
    }

    private void refreshCustomViews() {
        int bg = color(ThemePalette.KEY_BG);
        int border = color(ThemePalette.KEY_BORDER);
        int aiBubble = color(ThemePalette.KEY_AI_BUBBLE);
        previewBox.setBackground(Ui.roundedStroke(getContext(), bg, 12, border));
        previewBubble.setBackground(Ui.rounded(getContext(), aiBubble, 8));
        previewTitle.setTextColor(color(ThemePalette.KEY_TEXT));
        previewText.setTextColor(color(ThemePalette.KEY_TEXT_SECONDARY));
        previewPill.setTextColor(ThemePalette.customDefault().textOnColor);
        previewPill.setBackground(Ui.rounded(getContext(), color(ThemePalette.KEY_ACCENT), 999));
        updateSaveAction();
        refreshStarterPanel();
        refreshSwatches();
        refreshEditor();
    }

    private void refreshStarterPanel() {
        if (starterPanel == null) return;
        Context context = getContext();
        starterPanel.removeAllViews();
        starterPanel.addView(Ui.medium(context, context.getString(R.string.screen_theme_starter_section),
                Tokens.FONT_SM, rendered.textSecondary), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(3);
        addStarter(grid, "default", R.string.screen_theme_starter_default, Icons.PAINTBRUSH, ThemePalette.customDefault());
        addStarter(grid, ThemePalette.MODE_LIGHT, R.string.screen_theme_starter_light, Icons.SUN, ThemePalette.light());
        addStarter(grid, ThemePalette.MODE_DARK, R.string.screen_theme_starter_dark, Icons.MOON, ThemePalette.dark());
        addStarter(grid, ThemePalette.MODE_COFFEE, R.string.screen_theme_starter_coffee, Icons.COFFEE, ThemePalette.coffee());
        addStarter(grid, ThemePalette.MODE_VSCODE, R.string.screen_theme_starter_vscode, Icons.CODE, ThemePalette.vscode());
        addStarter(grid, ThemePalette.MODE_GITHUB_DARK, R.string.screen_theme_starter_github, Icons.GIT_BRANCH, ThemePalette.githubDark());
        addStarter(grid, ThemePalette.MODE_GRUVBOX, R.string.screen_theme_starter_gruvbox, Icons.CODE, ThemePalette.gruvbox());
        addStarter(grid, ThemePalette.MODE_HIGH_CONTRAST, R.string.screen_theme_starter_high_contrast, Icons.CONTRAST, ThemePalette.highContrast());
        if (!savedColors.isEmpty()) {
            addStarter(grid, "saved", R.string.screen_theme_starter_saved, Icons.SAVE,
                    ThemePalette.customDefault().withCustomColors(savedColors));
        }
        LayoutParams gridParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        gridParams.topMargin = Ui.dp(context, 8);
        starterPanel.addView(grid, gridParams);
    }

    private void addStarter(GridLayout grid, String id, int label, int icon, ThemePalette palette) {
        Context context = getContext();
        boolean active = id.equals(activeStarter);
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setClickable(true);
        button.setBackground(Ui.roundedStroke(context,
                active ? rendered.accentMuted : rendered.surface, 8,
                active ? rendered.accent : rendered.borderLight));
        Ui.padding(button, 8, 8, 8, 8);
        button.setOnClickListener(v -> {
            activeStarter = id;
            draft.clear();
            draft.putAll(starterDraft(id, palette));
            refreshCustomViews();
        });
        LinearLayout chips = new LinearLayout(context);
        int[] colors = {palette.bg, palette.aiBubble, palette.accent};
        for (int i = 0; i < colors.length; i++) {
            android.view.View chip = new android.view.View(context);
            chip.setBackground(Ui.roundedStroke(context, colors[i], 9, Color.argb(32, 0, 0, 0)));
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(Ui.dp(context, 18), Ui.dp(context, 18));
            if (i > 0) chipParams.leftMargin = Ui.dp(context, -4);
            chips.addView(chip, chipParams);
        }
        button.addView(chips, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        IconButton starterIcon = new IconButton(context, icon);
        starterIcon.setIconColor(active ? rendered.accent : rendered.textSecondary);
        starterIcon.setIconSizeDp(14, 14);
        starterIcon.setClickable(false);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(Ui.dp(context, 14), Ui.dp(context, 14));
        iconParams.topMargin = Ui.dp(context, 6);
        button.addView(starterIcon, iconParams);
        TextView text = Ui.bold(context, context.getString(label), Tokens.FONT_XS,
                active ? rendered.accent : rendered.textSecondary);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        textParams.topMargin = Ui.dp(context, 4);
        button.addView(text, textParams);
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = GridLayout.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(0, Ui.dp(context, 8), Ui.dp(context, 8), 0);
        grid.addView(button, params);
    }

    private void refreshSwatches() {
        Context context = getContext();
        swatchLabel.setText(context.getString(R.string.theme_current_editing, labelFor(activeKey)));
        swatchGrid.removeAllViews();
        String activeValue = draft.get(activeKey);
        for (String value : SWATCHES) {
            FrameLayout swatch = new FrameLayout(context);
            swatch.setClickable(true);
            boolean active = value.equalsIgnoreCase(activeValue);
            swatch.setBackground(Ui.roundedStroke(context, Color.parseColor(value), 17,
                    active ? rendered.accent : rendered.borderLight));
            swatch.setOnClickListener(v -> {
                draft.put(activeKey, value);
                activeStarter = "custom-editing";
                refreshCustomViews();
            });
            if (active) {
                IconButton check = new IconButton(context, Icons.CHECK);
                check.setIconColor(rendered.textOnColor);
                check.setIconSizeDp(14, 14);
                check.setClickable(false);
                swatch.addView(check, new FrameLayout.LayoutParams(
                        Ui.dp(context, 14), Ui.dp(context, 14), Gravity.CENTER));
            }
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = Ui.dp(context, 34);
            params.height = Ui.dp(context, 34);
            params.setMargins(0, Ui.dp(context, 8), Ui.dp(context, 8), 0);
            swatchGrid.addView(swatch, params);
        }
    }

    private void refreshEditor() {
        Context context = getContext();
        editorGroup.removeAllViews();
        editorGroup.setBackground(Ui.rounded(context, rendered.surfaceElevated, 12));
        for (int i = 0; i < FIELDS.length; i++) {
            Field field = FIELDS[i];
            editorGroup.addView(colorRow(field, i < FIELDS.length - 1),
                    new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private android.view.View colorRow(Field field, boolean divider) {
        Context context = getContext();
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(context, 66));
        row.setBackgroundColor(activeKey.equals(field.key) ? rendered.accentMuted : Color.TRANSPARENT);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            activeKey = field.key;
            refreshCustomViews();
        });
        Ui.padding(row, 12, 0, 12, 0);

        String value = draft.get(field.key);
        boolean valid = ThemePalette.isHexColor(value);
        android.view.View preview = new android.view.View(context);
        preview.setBackground(Ui.roundedStroke(context,
                valid ? Color.parseColor(value) : rendered.surfaceLight, 15, rendered.borderLight));
        row.addView(preview, new LinearLayout.LayoutParams(Ui.dp(context, 30), Ui.dp(context, 30)));

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        metaParams.leftMargin = Ui.dp(context, 12);
        row.addView(meta, metaParams);
        meta.addView(Ui.medium(context, context.getString(field.label), Tokens.FONT_MD, rendered.text));
        TextView desc = Ui.text(context,
                context.getString(valid ? field.description : R.string.screen_theme_color_hex_hint),
                Tokens.FONT_XS, valid ? rendered.textTertiary : rendered.danger, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = Ui.dp(context, 2);
        meta.addView(desc, descParams);

        EditText input = new EditText(context);
        input.setText(value);
        input.setTextColor(valid ? rendered.text : rendered.danger);
        input.setTextSize(Tokens.FONT_SM);
        input.setSingleLine(true);
        input.setTypeface(Typeface.MONOSPACE);
        input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(9)});
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(context.getString(R.string.screen_theme_color_hex_placeholder));
        input.setHintTextColor(rendered.textTertiary);
        input.setSelectAllOnFocus(false);
        input.setBackground(Ui.roundedStroke(context, rendered.surfaceLight, 8,
                valid ? rendered.borderLight : rendered.danger));
        input.setPadding(Ui.dp(context, 8), 0, Ui.dp(context, 8), 0);
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !activeKey.equals(field.key)) {
                activeKey = field.key;
                refreshSwatches();
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String next = s == null ? "" : s.toString().trim();
                if (next.length() > 0 && !next.startsWith("#")) next = "#" + next;
                draft.put(field.key, next);
                activeKey = field.key;
                activeStarter = "custom-editing";
                boolean nextValid = ThemePalette.isHexColor(next);
                input.setTextColor(nextValid ? rendered.text : rendered.danger);
                input.setBackground(Ui.roundedStroke(getContext(), rendered.surfaceLight, 8,
                        nextValid ? rendered.borderLight : rendered.danger));
                preview.setBackground(Ui.roundedStroke(getContext(),
                        nextValid ? Color.parseColor(next) : rendered.surfaceLight, 15, rendered.borderLight));
                desc.setText(nextValid ? field.description : R.string.screen_theme_color_hex_hint);
                desc.setTextColor(nextValid ? rendered.textTertiary : rendered.danger);
                updatePreviewOnly();
            }
        });
        row.addView(input, new LinearLayout.LayoutParams(Ui.dp(context, 92), Ui.dp(context, 38)));
        wrapper.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        if (divider) {
            android.view.View line = new android.view.View(context);
            line.setBackgroundColor(rendered.borderLight);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1);
            lineParams.leftMargin = Ui.dp(context, 58);
            wrapper.addView(line, lineParams);
        }
        return wrapper;
    }

    private void updatePreviewOnly() {
        updateSaveAction();
        if (!ThemePalette.isHexColor(draft.get(activeKey))) return;
        previewBox.setBackground(Ui.roundedStroke(getContext(), color(ThemePalette.KEY_BG), 12,
                color(ThemePalette.KEY_BORDER)));
        previewBubble.setBackground(Ui.rounded(getContext(), color(ThemePalette.KEY_AI_BUBBLE), 8));
        previewTitle.setTextColor(color(ThemePalette.KEY_TEXT));
        previewText.setTextColor(color(ThemePalette.KEY_TEXT_SECONDARY));
        previewPill.setBackground(Ui.rounded(getContext(), color(ThemePalette.KEY_ACCENT), 999));
        refreshSwatches();
    }

    private void updateSaveAction() {
        boolean valid = !invalid();
        saveAction.setEnabled(valid);
        saveAction.setBackground(Ui.rounded(getContext(), valid ? rendered.accent : rendered.surfaceLight, 17));
        int color = valid ? rendered.textOnColor : rendered.textTertiary;
        saveIcon.setIconColor(color);
        saveText.setTextColor(color);
    }

    private boolean invalid() {
        for (String key : ThemePalette.EDITABLE_KEYS) {
            if (!ThemePalette.isHexColor(draft.get(key))) return true;
        }
        return false;
    }

    private int color(String key) {
        return ThemePalette.parseHex(draft.get(key), ThemePalette.customDefault().colorForKey(key));
    }

    private String labelFor(String key) {
        for (Field field : FIELDS) {
            if (field.key.equals(key)) return getContext().getString(field.label);
        }
        return key;
    }

    private LinearLayout panel() {
        Context context = getContext();
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.rounded(context, rendered.surfaceElevated, 12));
        Ui.padding(panel, 12, 12, 12, 12);
        return panel;
    }

    private void addPanel(LinearLayout panel) {
        Context context = getContext();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.leftMargin = params.rightMargin = Ui.dp(context, 16);
        params.bottomMargin = Ui.dp(context, 12);
        content.addView(panel, params);
    }

    private Map<String, String> themeDraft(ThemePalette base, Map<String, String> stored) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>(base.editableHexMap());
        if (stored != null) {
            for (String key : ThemePalette.EDITABLE_KEYS) {
                String value = stored.get(key);
                if (ThemePalette.isHexColor(value)) values.put(key, value);
            }
        }
        return values;
    }

    private Map<String, String> starterDraft(String id, ThemePalette palette) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>(palette.editableHexMap());
        if ("saved".equals(id)) {
            return themeDraft(ThemePalette.customDefault(), savedColors);
        }
        if (ThemePalette.MODE_LIGHT.equals(id)) values.put(ThemePalette.KEY_CODE_BG, "#F2F2F7");
        else if (ThemePalette.MODE_DARK.equals(id)) values.put(ThemePalette.KEY_CODE_BG, "#151515");
        else if (ThemePalette.MODE_COFFEE.equals(id)) values.put(ThemePalette.KEY_CODE_BG, "#EFE4D4");
        return values;
    }

    /** Keeps the custom editor colours in sync when the app theme changes. */
    @Override public void applyTokens(Tokens tokens) {
        rendered = tokens;
        theme.apply(tokens);
        refreshCustomViews();
    }

    private static final class Mode {
        final String mode;
        final int label;
        final int description;
        final int icon;
        Mode(String mode, int label, int description, int icon) {
            this.mode = mode;
            this.label = label;
            this.description = description;
            this.icon = icon;
        }
    }

    private static final class Field {
        final String key;
        final int label;
        final int description;
        Field(String key, int label, int description) {
            this.key = key;
            this.label = label;
            this.description = description;
        }
    }
}
