package cn.lineai.core.kernel.error;

/** Stable error codes. UI text is produced by {@code ErrorTextMapper}, never by the thrower. */
public enum ErrorCode {
    NETWORK,
    TIMEOUT,
    AUTH,
    RATE_LIMIT,
    STORAGE,
    NOT_FOUND,
    INVALID_INPUT,
    TOOL_FAILED,
    PROMPT_MISSING,
    CANCELLED,
    UNKNOWN
}
