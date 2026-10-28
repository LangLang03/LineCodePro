package cn.lineai.core.mvi.present;

@FunctionalInterface
public interface Emitter<A> {
    void emit(A action);
}
