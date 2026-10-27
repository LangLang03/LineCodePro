package cn.lineai.tool.api;

/** Accessibility-backed phone actions used by the phone tools. */
public interface PhoneControl {

    boolean click(int x, int y);

    boolean longPress(int x, int y, int durationMs);

    boolean swipe(int x1, int y1, int x2, int y2, int durationMs);

    String viewHierarchy();

    boolean action(String action);

    byte[] screenshot();
}
