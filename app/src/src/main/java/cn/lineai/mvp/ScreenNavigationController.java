package cn.lineai.mvp;

import java.util.ArrayList;

public final class ScreenNavigationController {
    public interface Host {
        void hideOverlays();

        void showScreen(String screenId);

        void showChatScreen();
    }

    private final ArrayList<String> screenStack = new ArrayList<>();

    public void showScreen(String screenId, Host host) {
        String safeScreenId = normalizeScreenId(screenId);
        if (safeScreenId.length() == 0) {
            return;
        }
        if (screenStack.isEmpty() || !safeScreenId.equals(screenStack.get(screenStack.size() - 1))) {
            screenStack.add(safeScreenId);
        }
        showVisibleScreen(safeScreenId, host);
    }

    public void refreshVisibleScreen(String screenId, Host host) {
        String safeScreenId = normalizeScreenId(screenId);
        if (safeScreenId.length() == 0) {
            return;
        }
        if (screenStack.isEmpty()) {
            screenStack.add(safeScreenId);
        } else if (!safeScreenId.equals(screenStack.get(screenStack.size() - 1))) {
            int existingIndex = screenStack.lastIndexOf(safeScreenId);
            if (existingIndex >= 0) {
                while (screenStack.size() > existingIndex + 1) {
                    screenStack.remove(screenStack.size() - 1);
                }
            } else {
                screenStack.add(safeScreenId);
            }
        }
        showVisibleScreen(safeScreenId, host);
    }

    public void returnToScreen(String screenId, Host host) {
        String safeScreenId = normalizeScreenId(screenId);
        if (safeScreenId.length() == 0) {
            return;
        }
        int existingIndex = screenStack.lastIndexOf(safeScreenId);
        if (existingIndex >= 0) {
            while (screenStack.size() > existingIndex + 1) {
                screenStack.remove(screenStack.size() - 1);
            }
        } else {
            screenStack.clear();
            screenStack.add(safeScreenId);
        }
        showVisibleScreen(safeScreenId, host);
    }

    public void backFrom(String visibleScreenId, Host host) {
        String currentScreenId = normalizeScreenId(visibleScreenId);
        if (screenStack.isEmpty()) {
            if (currentScreenId.length() == 0) {
                return;
            }
            screenStack.add(currentScreenId);
        } else if (currentScreenId.length() > 0
                && !currentScreenId.equals(screenStack.get(screenStack.size() - 1))) {
            int visibleIndex = screenStack.lastIndexOf(currentScreenId);
            if (visibleIndex >= 0) {
                while (screenStack.size() > visibleIndex + 1) {
                    screenStack.remove(screenStack.size() - 1);
                }
            } else {
                screenStack.add(currentScreenId);
            }
        }
        currentScreenId = screenStack.remove(screenStack.size() - 1);
        String previousScreenId = screenStack.isEmpty()
                ? parentScreenFor(currentScreenId)
                : screenStack.get(screenStack.size() - 1);
        if (previousScreenId.length() == 0) {
            if (host != null) {
                host.showChatScreen();
            }
        } else {
            if (screenStack.isEmpty()) {
                screenStack.add(previousScreenId);
            }
            showVisibleScreen(previousScreenId, host);
        }
    }

    public ArrayList<String> stackSnapshot() {
        return new ArrayList<>(screenStack);
    }

    public static String normalizeScreenId(String screenId) {
        return screenId == null ? "" : screenId.trim();
    }

    public static String parentScreenFor(String screenId) {
        String safeScreenId = normalizeScreenId(screenId);
        if (safeScreenId.length() == 0 || "settings".equals(safeScreenId)) {
            return "";
        }
        if ("llm".equals(safeScreenId)
                || "input".equals(safeScreenId)
                || "models".equals(safeScreenId)
                || "extensions".equals(safeScreenId)
                || "mcp".equals(safeScreenId)
                || "toolSettings".equals(safeScreenId)
                || "output".equals(safeScreenId)
                || "theme".equals(safeScreenId)
                || "experimental".equals(safeScreenId)
                || "data".equals(safeScreenId)
                || "storage".equals(safeScreenId)
                || "memory".equals(safeScreenId)
                || "keepAlive".equals(safeScreenId)
                || "about".equals(safeScreenId)) {
            return "settings";
        }
        if ("sshSettings".equals(safeScreenId)
                || "termuxIntegration".equals(safeScreenId)) {
            return "mcp";
        }
        if ("imageUnderstandingModel".equals(safeScreenId)) {
            return "toolSettings";
        }
        if ("promptTemplates".equals(safeScreenId)) {
            return "llm";
        }
        if ("licenses".equals(safeScreenId)) {
            return "about";
        }
        if ("modelAddOptions".equals(safeScreenId) || safeScreenId.startsWith("modelEdit:")) {
            return "models";
        }
        if ("modelAdd".equals(safeScreenId)
                || "modelAdd:local".equals(safeScreenId)
                || safeScreenId.startsWith("modelAdd:preset:")) {
            return "modelAddOptions";
        }
        if (safeScreenId.startsWith("extension:")
                || "agentEdit".equals(safeScreenId)
                || safeScreenId.startsWith("agentEdit:")
                || "mcpEdit".equals(safeScreenId)
                || safeScreenId.startsWith("mcpEdit:")) {
            return "extensions";
        }
        return "";
    }

    private void showVisibleScreen(String screenId, Host host) {
        if (host == null) {
            return;
        }
        host.hideOverlays();
        host.showScreen(screenId);
    }
}
