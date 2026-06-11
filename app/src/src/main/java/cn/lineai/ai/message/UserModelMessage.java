package cn.lineai.ai.message;

public final class UserModelMessage extends ModelMessage {
    public UserModelMessage(String content) {
        super(content);
    }

    public UserModelMessage(String content, String rawInputJson) {
        super(content, "", rawInputJson);
    }

    @Override
    public String getRole() {
        return "user";
    }
}
