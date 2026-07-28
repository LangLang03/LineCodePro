package cn.lineai.ui.component;

import android.content.Context;
import android.widget.LinearLayout;
import cn.lineai.R;
import cn.lineai.model.AiBehaviorSettings;
import java.util.ArrayList;
import java.util.List;

public final class LLMSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onToneModeChanged(String toneMode);

        void onReasoningEffortChanged(String effort);

        void onThinkingScrollChanged(boolean enabled);

        void onThinkingAutoExpandChanged(boolean enabled);

        void onPreserveReasoningChanged(boolean enabled);

        void onLearningModeChanged(boolean enabled);

        void onOpenPromptTemplates();
    }

    private final List<ReasoningRow> reasoningRows = new ArrayList<>();
    private final List<ToneRow> toneRows = new ArrayList<>();

    public LLMSettingsScreenView(Context context, AiBehaviorSettings settings, Listener listener) {
        super(context, context.getString(R.string.screen_llm_title), listener::onBack, null);
        LinearLayout content = getContent();
        AiBehaviorSettings value = settings == null
                ? new AiBehaviorSettings(null, true, false, null, false, false)
                : settings;

        SettingsSectionView reasoning = new SettingsSectionView(context, context.getString(R.string.screen_llm_section_thinking));
        addReasoningRow(reasoning, listener, AiBehaviorSettings.REASONING_OFF, context.getString(R.string.screen_llm_thinking_off_label), context.getString(R.string.screen_llm_thinking_off_desc), value.getReasoningEffort(), true);
        addReasoningRow(reasoning, listener, AiBehaviorSettings.REASONING_AUTO, context.getString(R.string.screen_llm_thinking_auto_label), context.getString(R.string.screen_llm_thinking_auto), value.getReasoningEffort(), true);
        addReasoningRow(reasoning, listener, AiBehaviorSettings.REASONING_LOW, context.getString(R.string.screen_llm_thinking_low_label), context.getString(R.string.screen_llm_thinking_low), value.getReasoningEffort(), true);
        addReasoningRow(reasoning, listener, AiBehaviorSettings.REASONING_MEDIUM, context.getString(R.string.screen_llm_thinking_medium_label), context.getString(R.string.screen_llm_thinking_medium), value.getReasoningEffort(), true);
        addReasoningRow(reasoning, listener, AiBehaviorSettings.REASONING_HIGH, context.getString(R.string.screen_llm_thinking_high_label), context.getString(R.string.screen_llm_thinking_high), value.getReasoningEffort(), true);
        addReasoningRow(reasoning, listener, AiBehaviorSettings.REASONING_MAX, context.getString(R.string.screen_llm_thinking_max_label), context.getString(R.string.screen_llm_thinking_max), value.getReasoningEffort(), false);
        content.addView(reasoning, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView learning = new SettingsSectionView(context, context.getString(R.string.screen_llm_section_learning));
        learning.addRow(new SwitchRowView(
                context,
                IconButtonView.BRAIN,
                context.getString(R.string.screen_llm_learning_label),
                context.getString(R.string.screen_llm_learning_desc),
                value.isLearningModeEnabled(),
                (buttonView, isChecked) -> listener.onLearningModeChanged(isChecked)
        ), false);
        content.addView(learning, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView tone = new SettingsSectionView(context, context.getString(R.string.screen_llm_section_tone));
        addToneRow(tone, listener, AiBehaviorSettings.TONE_CODING, context.getString(R.string.screen_llm_tone_coding), context.getString(R.string.screen_llm_tone_coding_desc), IconButtonView.ZAP, value.getToneMode(), true);
        addToneRow(tone, listener, AiBehaviorSettings.TONE_CHAT, context.getString(R.string.screen_llm_tone_chat), context.getString(R.string.screen_llm_tone_chat_desc), IconButtonView.SMILE, value.getToneMode(), false);
        content.addView(tone, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView prompts = new SettingsSectionView(context, context.getString(R.string.screen_llm_section_prompts));
        prompts.addRow(new OptionRowView(
                context,
                IconButtonView.FILE_PEN_LINE,
                context.getString(R.string.screen_llm_prompts_label),
                context.getString(R.string.screen_llm_prompts_desc),
                false,
                listener::onOpenPromptTemplates
        ), false);
        content.addView(prompts, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView thinking = new SettingsSectionView(context, context.getString(R.string.screen_llm_section_thinking_display));
        thinking.addRow(new SwitchRowView(
                context,
                IconButtonView.SCROLL_TEXT,
                context.getString(R.string.screen_llm_scroll_label),
                context.getString(R.string.screen_llm_scroll_desc),
                value.isThinkingScrollEnabled(),
                (buttonView, isChecked) -> listener.onThinkingScrollChanged(isChecked)
        ), true);
        thinking.addRow(new SwitchRowView(
                context,
                IconButtonView.EXPAND,
                context.getString(R.string.screen_llm_auto_expand_label),
                context.getString(R.string.screen_llm_auto_expand_desc),
                value.isThinkingAutoExpandEnabled(),
                (buttonView, isChecked) -> listener.onThinkingAutoExpandChanged(isChecked)
        ), true);
        thinking.addRow(new SwitchRowView(
                context,
                IconButtonView.BRAIN,
                context.getString(R.string.screen_llm_keep_reasoning_label),
                context.getString(R.string.screen_llm_keep_reasoning_desc),
                value.isPreserveReasoningEnabled(),
                (buttonView, isChecked) -> listener.onPreserveReasoningChanged(isChecked)
        ), false);
        content.addView(thinking, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addReasoningRow(
            SettingsSectionView section,
            Listener listener,
            String effort,
            String label,
            String desc,
            String selected,
            boolean divider
    ) {
        OptionRowView row = new OptionRowView(getContext(), IconButtonView.SPARKLES, label, desc, effort.equals(selected), () -> {
            listener.onReasoningEffortChanged(effort);
            updateReasoningRows(effort);
        });
        reasoningRows.add(new ReasoningRow(effort, row));
        section.addRow(row, divider);
    }

    private void addToneRow(
            SettingsSectionView section,
            Listener listener,
            String toneMode,
            String label,
            String desc,
            int icon,
            String selected,
            boolean divider
    ) {
        OptionRowView row = new OptionRowView(getContext(), icon, label, desc, toneMode.equals(selected), () -> {
            listener.onToneModeChanged(toneMode);
            updateToneRows(toneMode);
        });
        toneRows.add(new ToneRow(toneMode, row));
        section.addRow(row, divider);
    }

    private void updateReasoningRows(String selected) {
        for (ReasoningRow item : reasoningRows) {
            item.row.setActive(item.effort.equals(selected));
        }
    }

    private void updateToneRows(String selected) {
        for (ToneRow item : toneRows) {
            item.row.setActive(item.toneMode.equals(selected));
        }
    }

    private static final class ReasoningRow {
        final String effort;
        final OptionRowView row;

        ReasoningRow(String effort, OptionRowView row) {
            this.effort = effort;
            this.row = row;
        }
    }

    private static final class ToneRow {
        final String toneMode;
        final OptionRowView row;

        ToneRow(String toneMode, OptionRowView row) {
            this.toneMode = toneMode;
            this.row = row;
        }
    }
}
