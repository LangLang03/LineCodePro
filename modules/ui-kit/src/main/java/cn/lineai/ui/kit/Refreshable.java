package cn.lineai.ui.kit;

/** Screens that reload their data whenever they become the visible top of the stack. */
public interface Refreshable {
    void onShown();
}
