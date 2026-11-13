package cn.lineai.core.model;

/** Chat session mode; controls the prompt and which tool families are exposed. */
public final class ChatMode {

    public static final String CHAT = "chat";
    public static final String PLAN = "plan";
    /** Default mode returned when no or an unknown mode is supplied. */
    public static final String AGENT = "agent";
    public static final String CONTROL = "control";

    /** Non-instantiable holder of mode identifiers. */
    private ChatMode() {
    }

    /**
     * Returns {@code mode} when it names a supported mode, otherwise {@link #AGENT}.
     * @param mode raw mode identifier, may be null
     * @return a supported mode identifier
     */
    public static String normalize(String mode) {
        if (CHAT.equals(mode) || PLAN.equals(mode) || CONTROL.equals(mode)) return mode;
        return AGENT;
    }
}
