package cn.lineai.feature.settings;

import android.content.Context;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import cn.lineai.core.model.MemoryItem;
import cn.lineai.ui.i18n.R;

/** Formatting helpers for the memory screen. */
final class MemoryText {

    private MemoryText() {
    }

    static String preview(Context context, String text, int maxChars) {
        String value = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
        while (value.contains("  ")) value = value.replace("  ", " ");
        if (value.length() <= maxChars) {
            return value.isEmpty() ? context.getString(R.string.screen_memory_value_empty) : value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    static String time(Context context, long value) {
        if (value <= 0) return context.getString(R.string.screen_memory_value_unknown_time);
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(value));
    }

    static String label(Context context, MemoryItem memory) {
        String title = memory.title.trim();
        return preview(context, title.isEmpty() ? memory.content : title, 80);
    }

    static String desc(Context context, MemoryItem memory) {
        StringBuilder builder = new StringBuilder();
        builder.append(context.getString(R.string.screen_memory_field_source))
                .append(memory.source)
                .append(" · ")
                .append(context.getString(R.string.screen_memory_field_use_count_prefix))
                .append(memory.useCount)
                .append(' ')
                .append(context.getString(R.string.screen_memory_field_use_count_suffix))
                .append(" · ")
                .append(time(context, memory.updatedAt));
        return builder.toString();
    }

    static String detail(Context context, MemoryItem memory) {
        StringBuilder builder = new StringBuilder();
        if (!memory.title.trim().isEmpty()) {
            builder.append(context.getString(R.string.screen_memory_field_title))
                    .append(memory.title).append("\n\n");
        }
        builder.append(memory.content).append("\n\n")
                .append(context.getString(R.string.screen_memory_field_scope)).append(memory.scope).append('\n')
                .append(context.getString(R.string.screen_memory_field_source)).append(memory.source).append('\n')
                .append(context.getString(R.string.screen_memory_field_project))
                .append(memory.projectId.isEmpty()
                        ? context.getString(R.string.screen_memory_value_global) : memory.projectId)
                .append('\n')
                .append(context.getString(R.string.screen_memory_field_confidence))
                .append(String.format(Locale.ROOT, "%.2f", memory.confidence)).append('\n')
                .append(context.getString(R.string.screen_memory_field_use_count)).append(memory.useCount).append('\n')
                .append(context.getString(R.string.screen_memory_field_created)).append(time(context, memory.createdAt)).append('\n')
                .append(context.getString(R.string.screen_memory_field_updated)).append(time(context, memory.updatedAt)).append('\n')
                .append(context.getString(R.string.screen_memory_field_last_used))
                .append(memory.lastUsedAt > 0 ? time(context, memory.lastUsedAt)
                        : context.getString(R.string.screen_memory_value_not_used_yet));
        return builder.toString();
    }

    static String sectionTitle(Context context, int title, int count) {
        return context.getString(R.string.memory_section_count, context.getString(title), count);
    }

    static String deleteBody(Context context, String content) {
        return context.getString(R.string.screen_memory_action_body, preview(context, content, 120));
    }
}
