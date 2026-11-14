package cn.lineai.feature.chat;

import java.util.function.Consumer;

import cn.lineai.core.model.UiPreferences;
import cn.lineai.feature.chat.toolcall.ToolDiffLookup;

/**
 * Everything a message row needs from the screen besides its own data.
 *
 * <p>A single immutable holder keeps row constructors and bind methods short: disclosure memory,
 * tool presentation, display preferences, host actions, selection hooks and the markdown link
 * handler.</p>
 */
final class RowEnv {

    /** Disclosure memory shared across all rows of the screen. */
    final DisclosureState disclosure;
    /** Tool label/icon resolution; UI never branches on tool names. */
    final ToolPresentationProvider provider;
    /** Display preferences (timestamps, bubble width, typography). */
    final UiPreferences preferences;
    /** Long-press actions; copy/share work without a host. */
    final MessageActions actions;
    /** Multi-select hooks owned by the timeline. */
    final SelectionHost selection;
    /** Resolves reviewable diffs for tool result cards. */
    final ToolDiffLookup diffLookup;
    /** Markdown link handler; null disables clickable links. */
    final Consumer<String> links;

    /**
     * @param disclosure disclosure memory; null gets a fresh instance
     * @param provider   tool presentation resolver
     * @param preferences display preferences; null gets defaults
     * @param actions    message action hooks
     * @param selection  selection hooks
     * @param diffLookup diff resolver for tool cards
     * @param links      markdown link handler
     */
    RowEnv(DisclosureState disclosure, ToolPresentationProvider provider,
           UiPreferences preferences, MessageActions actions, SelectionHost selection,
           ToolDiffLookup diffLookup, Consumer<String> links) {
        this.disclosure = disclosure == null ? new DisclosureState() : disclosure;
        this.provider = provider;
        this.preferences = preferences == null ? UiPreferences.defaults() : preferences;
        this.actions = actions;
        this.selection = selection;
        this.diffLookup = diffLookup;
        this.links = links;
    }
}
