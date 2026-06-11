package cn.lineai.ai.message;

public final class SystemModelMessage extends ModelMessage {
    public SystemModelMessage(String content) {
        super(content);
    }

    @Override
    public String getRole() {
        return "system";
    }
}
