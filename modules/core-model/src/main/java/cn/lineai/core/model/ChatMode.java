package cn.lineai.core.model;

/** Chat session mode; controls the prompt and which tool families are exposed. */
public final class ChatMode {

    public static final String CHAT = "chat";
    public static final String PLAN = "plan";
    public static final String AGENT = "agent";
    public static final String CONTROL = "control";

    private ChatMode() {
    }

    public static String normalize(String mode) {
        if (CHAT.equals(mode) || PLAN.equals(mode) || CONTROL.equals(mode)) return mode;
        return AGENT;
    }
}
