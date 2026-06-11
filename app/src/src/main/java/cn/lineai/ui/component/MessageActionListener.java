package cn.lineai.ui.component;

import cn.lineai.model.ChatMessage;

public interface MessageActionListener {
    void onCopyMessage(ChatMessage message);

    void onRecallMessage(ChatMessage message);
}
