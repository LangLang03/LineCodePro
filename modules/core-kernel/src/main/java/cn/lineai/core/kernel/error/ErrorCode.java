package cn.lineai.core.kernel.error;

/**
 * Stable error codes. UI text is produced by {@code ErrorTextMapper}, never by the thrower.
 *
 * <p>The names are part of the cross-layer contract: values may be added safely, but renaming or
 * removing one breaks persisted state and UI mapping that key on the name.</p>
 */
public enum ErrorCode {
    /** Transport-level failure: unreachable host, refused connection or broken response stream. */
    NETWORK,
    /** An operation exceeded its deadline before completing. */
    TIMEOUT,
    /** Credentials were rejected or missing for an authenticated call. */
    AUTH,
    /** The provider throttled the request due to quota or rate limits. */
    RATE_LIMIT,
    /** Local persistence failed: read, write or corrupt storage. */
    STORAGE,
    /** A referenced entity, file or prompt does not exist. */
    NOT_FOUND,
    /** Caller-supplied data failed validation before any side effect occurred. */
    INVALID_INPUT,
    /** A tool invocation failed while executing; specifics live in the exception detail string. */
    TOOL_FAILED,
    /** A required prompt template is absent from the catalog and overrides. */
    PROMPT_MISSING,
    /** The operation ended because the user or an enclosing scope cancelled it. */
    CANCELLED,
    /** Fallback for failures that match no specific code. */
    UNKNOWN
}
