package cn.lineai.core.mvi.present;

public interface Presenter<S extends UiState, A extends Action> {
    /** Called once on the Store constructor's thread. */
    S initial();

    /** Pure state transition, invoked serially on the main thread. */
    Transition<S> reduce(S state, A action);

    /** Invoked through Scheduler.io; report expected failures as actions. */
    void run(Effect effect, Emitter<A> out);
}
