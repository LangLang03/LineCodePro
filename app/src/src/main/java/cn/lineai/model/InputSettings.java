package cn.lineai.model;

public final class InputSettings {
    public static final String ENTER_SEND = "send";
    public static final String ENTER_NEWLINE = "newline";

    private final String enterKeyBehavior;

    public InputSettings(String enterKeyBehavior) {
        this.enterKeyBehavior = normalizeEnterKeyBehavior(enterKeyBehavior);
    }

    public String getEnterKeyBehavior() {
        return enterKeyBehavior;
    }

    public boolean isEnterSendEnabled() {
        return ENTER_SEND.equals(enterKeyBehavior);
    }

    public static String normalizeEnterKeyBehavior(String behavior) {
        return ENTER_NEWLINE.equals(behavior) ? ENTER_NEWLINE : ENTER_SEND;
    }
}
