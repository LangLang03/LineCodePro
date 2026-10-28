package cn.lineai.feature.settings;

import android.content.Context;
import cn.lineai.core.model.UiPreferences.Field;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.tokens.Tokens;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class BehaviorSettingsPage extends SettingsPage {
    private final Consumer<SettingsAction> dispatch;
    private final List<Choice> reasoning = new ArrayList<>();
    private final List<Choice> tones = new ArrayList<>();
    private final PreferenceRow minimal;
    private final PreferenceRow learning;
    private final PreferenceRow softCompaction;
    private final PreferenceRow scroll;
    private final PreferenceRow autoExpand;
    private final PreferenceRow preserve;

    public BehaviorSettingsPage(Context context, Tokens tokens, Runnable back,
                                Consumer<SettingsAction> dispatch, Runnable promptTemplates) {
        super(context, R.string.screen_llm_title, tokens, back);
        this.dispatch = dispatch;
        SettingsSection thinking = section(R.string.screen_llm_section_thinking);
        choice(thinking, reasoning, Field.REASONING, "off", R.string.screen_llm_thinking_off_label,
                R.string.screen_llm_thinking_off_desc, true);
        choice(thinking, reasoning, Field.REASONING, "auto", R.string.screen_llm_thinking_auto_label,
                R.string.screen_llm_thinking_auto, true);
        choice(thinking, reasoning, Field.REASONING, "low", R.string.screen_llm_thinking_low_label,
                R.string.screen_llm_thinking_low, true);
        choice(thinking, reasoning, Field.REASONING, "medium", R.string.screen_llm_thinking_medium_label,
                R.string.screen_llm_thinking_medium, true);
        choice(thinking, reasoning, Field.REASONING, "high", R.string.screen_llm_thinking_high_label,
                R.string.screen_llm_thinking_high, true);
        choice(thinking, reasoning, Field.REASONING, "max", R.string.screen_llm_thinking_max_label,
                R.string.screen_llm_thinking_max, false);

        SettingsSection learningSection = section(R.string.screen_llm_section_learning);
        minimal = toggle(learningSection, Icons.ZAP, R.string.screen_llm_minimal_mode_label,
                R.string.screen_llm_minimal_mode_desc, Field.MINIMAL_MODE, true);
        learning = toggle(learningSection, Icons.BRAIN, R.string.screen_llm_learning_label,
                R.string.screen_llm_learning_desc, Field.LEARNING_MODE, true);
        softCompaction = toggle(learningSection, Icons.ROTATE_CCW, R.string.screen_llm_soft_compact_label,
                R.string.screen_llm_soft_compact_desc, Field.SOFT_COMPACTION, false);

        SettingsSection tone = section(R.string.screen_llm_section_tone);
        choice(tone, tones, Field.TONE, "coding", R.string.screen_llm_tone_coding,
                R.string.screen_llm_tone_coding_desc, true, Icons.ZAP);
        choice(tone, tones, Field.TONE, "chat", R.string.screen_llm_tone_chat,
                R.string.screen_llm_tone_chat_desc, false, Icons.SMILE);

        PreferenceRow prompts = row(PreferenceRow.Style.OPTION, Icons.FILE_PEN_LINE,
                R.string.screen_llm_prompts_label, R.string.screen_llm_prompts_desc);
        prompts.setOnClickListener(v -> promptTemplates.run());
        section(R.string.screen_llm_section_prompts).row(prompts, false, 0);

        SettingsSection display = section(R.string.screen_llm_section_thinking_display);
        scroll = toggle(display, Icons.SCROLL_TEXT, R.string.screen_llm_scroll_label,
                R.string.screen_llm_scroll_desc, Field.THINKING_SCROLL, true);
        autoExpand = toggle(display, Icons.EXPAND, R.string.screen_llm_auto_expand_label,
                R.string.screen_llm_auto_expand_desc, Field.THINKING_AUTO_EXPAND, true);
        preserve = toggle(display, Icons.BRAIN, R.string.screen_llm_keep_reasoning_label,
                R.string.screen_llm_keep_reasoning_desc, Field.PRESERVE_REASONING, false);
    }

    private void choice(SettingsSection section, List<Choice> group, Field field, String value,
                        int label, int description, boolean divider) {
        choice(section, group, field, value, label, description, divider, Icons.SPARKLES);
    }

    private void choice(SettingsSection section, List<Choice> group, Field field, String value,
                        int label, int description, boolean divider, int icon) {
        PreferenceRow row = row(PreferenceRow.Style.OPTION, icon, label, description);
        row.setOnClickListener(v -> {
            dispatch.accept(new SettingsAction.Change(field, value));
            for (Choice item : group) item.row.active(item.value.equals(value));
        });
        group.add(new Choice(value, row));
        section.row(row, divider, 0);
    }

    private PreferenceRow toggle(SettingsSection section, int icon, int label, int description,
                                 Field field, boolean divider) {
        PreferenceRow row = row(PreferenceRow.Style.SWITCH, icon, label, description);
        row.onToggle(value -> dispatch.accept(new SettingsAction.Change(field, value.toString())));
        section.row(row, divider, 0);
        return row;
    }

    private PreferenceRow row(PreferenceRow.Style style, int icon, int label, int description) {
        return new PreferenceRow(getContext(), theme, style, icon, label, description);
    }

    public void render(SettingsState state) {
        for (Choice item : reasoning) {
            item.row.active(item.value.equals(state.values.get(Field.REASONING)));
        }
        for (Choice item : tones) {
            item.row.active(item.value.equals(state.values.get(Field.TONE)));
        }
        minimal.checked(state.values.minimalMode());
        learning.checked(state.values.learningMode());
        softCompaction.checked(state.values.softCompaction());
        scroll.checked(state.values.thinkingScroll());
        autoExpand.checked(state.values.thinkingAutoExpand());
        preserve.checked(state.values.preserveReasoning());
        for (PreferenceRow row : new PreferenceRow[]{minimal, learning, softCompaction, scroll, autoExpand, preserve}) {
            row.setEnabled(state.loaded);
        }
    }

    private static final class Choice {
        final String value;
        final PreferenceRow row;
        Choice(String value, PreferenceRow row) {
            this.value = value;
            this.row = row;
        }
    }
}
