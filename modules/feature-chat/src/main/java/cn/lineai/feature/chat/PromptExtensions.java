package cn.lineai.feature.chat;

/** Extra system-prompt sections supplied by user extensions. */
public interface PromptExtensions {

    /**
     * Extra system-prompt sections rendered from user extensions.
     *
     * @return extension text appended to the system prompt; empty when none are enabled
     */
    String section();

    /** Learning-mode context (memories, history, skill paths); empty when the mode is off. */
    default String learningSection() {
        return "";
    }
}
