package cn.lineai.ai;

public final class ModelCompletionException extends Exception {
    public ModelCompletionException(String message) {
        super(message);
    }

    public ModelCompletionException(String message, Throwable cause) {
        super(message, cause);
    }
}
