package cn.lineai.ui.i18n;

import cn.lineai.core.kernel.error.ErrorCode;

/**
 * Maps a stable error code to one existing string resource.
 *
 * <p>Callers never render {@code Throwable.getMessage()}: the thrower keeps the technical detail
 * for logs, the UI shows one of these strings.</p>
 */
public final class ErrorTextMapper {

    private ErrorTextMapper() {
    }

    public static TextRef text(ErrorCode code) {
        if (code == null) {
            return TextRef.of(R.string.toast_ai_generate_failed);
        }
        switch (code) {
            case CANCELLED:
                return TextRef.of(R.string.chat_stop);
            case PROMPT_MISSING:
                return TextRef.of(R.string.model_retry_failed);
            case STORAGE:
                return TextRef.of(R.string.screen_storage_title);
            case NETWORK:
            case TIMEOUT:
            case AUTH:
            case RATE_LIMIT:
            case NOT_FOUND:
            case INVALID_INPUT:
            case TOOL_FAILED:
            case UNKNOWN:
            default:
                return TextRef.of(R.string.toast_ai_generate_failed);
        }
    }
}
