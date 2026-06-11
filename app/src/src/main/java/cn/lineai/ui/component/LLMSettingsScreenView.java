package cn.lineai.ui.component;

import android.content.Context;
import android.widget.LinearLayout;
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
        super(context, "AI 行为", listener::onBack, null);
        LinearLayout content = getContent();
        AiBehaviorSettings value = settings == null
                ? new AiBehaviorSettings(null, true, false, null, false, false)
                : settings;

        SettingsSectionView reasoning = new SettingsSectionView(context, "思考深度");
        addReasoningRow(reasoning, listener, AiBehaviorSettings.REASONING_OFF, "关闭", "不启用思考模式，响应更快", value.getReasoningEffort(), true);
        addReasoningRow(reasoning, listener, AiBehaviorSettings.REASONING_LOW, "低", "简短思考，适合简单任务", value.getReasoningEffort(), true);
        addReasoningRow(reasoning, listener, AiBehaviorSettings.REASONING_MEDIUM, "中", "标准思考深度，平衡速度和质量", value.getReasoningEffort(), true);
        addReasoningRow(reasoning, listener, AiBehaviorSettings.REASONING_HIGH, "高", "深度思考，适合复杂任务", value.getReasoningEffort(), true);
        addReasoningRow(reasoning, listener, AiBehaviorSettings.REASONING_MAX, "最高", "最深度思考，适合极难问题", value.getReasoningEffort(), false);
        content.addView(reasoning, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView learning = new SettingsSectionView(context, "学习与记忆");
        learning.addRow(new SwitchRowView(
                context,
                IconButtonView.BRAIN,
                "学习模式",
                "启用自动 Skills、长期记忆、项目记忆、短期记忆和聊天记录检索",
                value.isLearningModeEnabled(),
                (buttonView, isChecked) -> listener.onLearningModeChanged(isChecked)
        ), false);
        content.addView(learning, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView tone = new SettingsSectionView(context, "交流语气");
        addToneRow(tone, listener, AiBehaviorSettings.TONE_CODING, "编程模式", "严谨专业，代码优先，不使用 emoji", IconButtonView.ZAP, value.getToneMode(), true);
        addToneRow(tone, listener, AiBehaviorSettings.TONE_CHAT, "聊天模式", "亲切温柔，像朋友聊天，可以使用 emoji", IconButtonView.SMILE, value.getToneMode(), false);
        content.addView(tone, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView prompts = new SettingsSectionView(context, "提示词");
        prompts.addRow(new OptionRowView(
                context,
                IconButtonView.FILE_PEN_LINE,
                "自定义提示词",
                "编辑 system、语气模式、会话模式、学习记忆和上下文压缩模板",
                false,
                listener::onOpenPromptTemplates
        ), false);
        content.addView(prompts, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView thinking = new SettingsSectionView(context, "思考过程");
        thinking.addRow(new SwitchRowView(
                context,
                IconButtonView.SCROLL_TEXT,
                "滚动显示",
                "关闭后直接完全展开显示",
                value.isThinkingScrollEnabled(),
                (buttonView, isChecked) -> listener.onThinkingScrollChanged(isChecked)
        ), true);
        thinking.addRow(new SwitchRowView(
                context,
                IconButtonView.EXPAND,
                "自动展开",
                "收到思考内容时自动展开",
                value.isThinkingAutoExpandEnabled(),
                (buttonView, isChecked) -> listener.onThinkingAutoExpandChanged(isChecked)
        ), true);
        thinking.addRow(new SwitchRowView(
                context,
                IconButtonView.BRAIN,
                "保留完整 reasoning",
                "将历史思考发回兼容模型，适合多轮工具调用",
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
