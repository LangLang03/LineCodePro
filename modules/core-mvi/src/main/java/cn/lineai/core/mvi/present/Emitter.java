package cn.lineai.core.mvi.present;

/** Sink through which running effects report follow-up actions. */
@FunctionalInterface
public interface Emitter<A> {
    /**
     * Emits an action back into the owning store.
     * @param action action to dispatch
     */
    void emit(A action);
}
