package cn.lineai.feature.settings;

import android.content.Context;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

import cn.lineai.core.model.MemoryItem;
import cn.lineai.ui.i18n.R;

/** Formatting helpers for the memory screen. */
final class MemoryText {

    /** Utility class; not instantiable. */
    private MemoryText() {
    }

    /**
     * Normalizes whitespace and truncates text to the given length for row previews.
     *
     * @param context context used for the empty-value fallback string
     * @param text raw memory text; may be null
     * @param maxChars maximum number of characters to return
     * @return single-line preview, or the localized empty marker
     */
    static String preview(Context context, String text, int maxChars) {
        String value = text == null ? "" : text.replace('\n', ' ').replace('\r', ' ').trim();
        while (value.contains("  ")) value = value.replace("  ", " ");
        if (value.length() <= maxChars) {
            return value.isEmpty() ? context.getString(R.string.screen_memory_value_empty) : value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    /**
     * Formats an epoch timestamp using the system short date-time style.
     *
     * @param context context used for the unknown-time fallback string
     * @param value epoch milliseconds; non-positive renders as unknown
     * @return localized date-time string
     */
    static String time(Context context, long value) {
        if (value <= 0) return context.getString(R.string.screen_memory_value_unknown_time);
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(value));
    }

    /**
     * Builds the row title for a memory item, preferring its title over its content.
     *
     * @param context context used for formatting
     * @param memory item to describe
     * @return truncated row title
     */
    static String label(Context context, MemoryItem memory) {
        String title = memory.title.trim();
        return preview(context, title.isEmpty() ? memory.content : title, 80);
    }

    /**
     * Builds the row description with source, use count, and last update time.
     *
     * @param context context used for localized labels
     * @param memory item to describe
     * @return single-line description string
     */
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

    /**
     * Builds the full multi-line detail body shown in the read-only detail dialog.
     *
     * @param context context used for localized labels
     * @param memory item to describe
     * @return multi-line detail text
     */
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

    /**
     * Formats a section heading including its entry count.
     *
     * @param context context used for formatting
     * @param title string resource of the section name
     * @param count number of entries in the section
     * @return formatted heading string
     */
    static String sectionTitle(Context context, int title, int count) {
        return context.getString(R.string.memory_section_count, context.getString(title), count);
    }

    /**
     * Builds the confirmation body for a delete dialog, embedding a content preview.
     *
     * @param context context used for formatting
     * @param content raw memory content to preview
     * @return formatted confirmation message
     */
    static String deleteBody(Context context, String content) {
        return context.getString(R.string.screen_memory_action_body, preview(context, content, 120));
    }
}
