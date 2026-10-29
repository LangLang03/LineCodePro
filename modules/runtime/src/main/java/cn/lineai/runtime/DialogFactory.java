package cn.lineai.runtime;

import android.app.Dialog;

import java.util.List;
import java.util.function.Consumer;

import cn.lineai.feature.chat.DrawerPanel;

/**
 * Chat overlay entry points. Each one opens the C++-parity sheet implemented by
 * {@link ChatSheets} / {@link ChatMenuSheets} / {@link ChatAttachmentSheet}; the method names and
 * signatures are kept stable for callers in the app shell and the settings screens.
 */
public final class DialogFactory {

    private DialogFactory() {
    }

    /** Permission menu: auto / confirm / read-only, manage-all-files and revoke grants. */
    public static void showPermission(PageHost host) {
        ChatSheets.showPermission(host);
    }

    /** Reasoning depth picker (off / auto / low / medium / high / max) as a bottom sheet. */
    public static void showReasoning(PageHost host) {
        ChatSheets.showReasoningPicker(host);
    }

    /** More menu: tutorial, settings, export, select-export, compact and clear. */
    public static void showMore(PageHost host) {
        ChatSheets.showMore(host);
    }

    /** Chat mode picker: chat / plan / agent / control. */
    public static void showChatMode(PageHost host) {
        ChatMenuSheets.showChatMode(host);
    }

    /** Context usage sheet: title plus used / limit / percent rows. */
    public static Dialog showContextUsage(PageHost host) {
        return ChatSheets.showContextUsage(host);
    }

    /** Confirmation menu for manual context compaction. */
    public static void showCompaction(PageHost host, Runnable onConfirm) {
        ChatSheets.showCompaction(host, onConfirm);
    }

    /** Context menu: files, image, model, mode, workspace, permissions, settings, more. */
    public static void showContextMenu(PageHost host) {
        ChatSheets.showContextMenu(host);
    }

    /** Model picker used by the composer's model pill. */
    public static void showModelPicker(PageHost host) {
        ChatSheets.showModelPicker(host);
    }

    /** Workspace attachment tree used by the composer's plus button. */
    public static void showAttachmentPicker(PageHost host, DrawerPanel.FileNode tree,
                                            List<String> selected,
                                            Consumer<String> onToggle) {
        ChatSheets.showAttachmentPicker(host, tree, selected, onToggle);
    }
}
