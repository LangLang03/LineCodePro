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

    final DisclosureState disclosure;
    final ToolPresentationProvider provider;
    final UiPreferences preferences;
    final MessageActions actions;
    final SelectionHost selection;
    final ToolDiffLookup diffLookup;
    final Consumer<String> links;

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
