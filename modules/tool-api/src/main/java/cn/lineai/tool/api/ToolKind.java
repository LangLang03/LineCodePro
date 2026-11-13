package cn.lineai.tool.api;

/**
 * Non-file semantic families the UI and mode policies can filter on.
 *
 * <p>File tools stay unclassified ({@link #GENERAL}); kinds exist so phone automation can be
 * toggled independently of the file toolset without enumerating tool names.</p>
 */
public enum ToolKind {

    /** Tools that operate on the workspace filesystem or the network. */
    GENERAL,

    /** Tools that drive the on-device accessibility layer (phone automation). */
    PHONE
}
