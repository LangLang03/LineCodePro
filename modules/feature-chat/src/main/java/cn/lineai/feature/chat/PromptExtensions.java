package cn.lineai.feature.chat;

/** Extra system-prompt sections supplied by user extensions. */
public interface PromptExtensions {

    String section();

    /** Learning-mode context (memories, history, skill paths); empty when the mode is off. */
    default String learningSection() {
        return "";
    }
}
