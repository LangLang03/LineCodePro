package cn.lineai.core.mvi.nav;

import cn.lineai.core.kernel.observe.Observable;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.mvi.present.UiState;

import java.util.List;

/** Navigation operations over an ordered screen stack owned by the app graph. */
public interface Navigator extends AutoCloseable {
    /** Pushes with the default page transition (slide in from the right). */
    default <S extends UiState, P extends Params> void push(ScreenKey<S, P> key) {
        push(key, true);
    }

    /**
     * Pushes a screen and chooses whether the page transition runs.
     *
     * <p>Utility surfaces such as the workspace picker appear over the chat without a page
     * slide, exactly like their legacy bottom-sheet presentation. Passing {@code false} shows
     * the page immediately.</p>
     */
    <S extends UiState, P extends Params> void push(ScreenKey<S, P> key, boolean animate);

    /** Pop/back preserve the root; empty and root-only stacks are no-ops. */
    void pop();

    /** Synonym for {@link #pop()}, named for intent at call sites. */
    void back();

    /** Keeps the nearest matching entry; an absent registered id is a no-op. */
    void popTo(ScreenId<?, ?> id);

    /** Keeps only the root entry; a no-op when the stack already holds the root alone. */
    void popToRoot();

    /** Null only when empty. */
    ScreenKey<?, ?> current();

    /** Immutable snapshot, ordered root to top. */
    List<ScreenKey<?, ?>> entries();

    /**
     * Serialises every entry for process recreation.
     * @return immutable saved state, ordered root to top
     */
    List<Params.Args> save();

    /** Validates and decodes every entry before posting one atomic replacement. */
    void restore(List<Params.Args> saved);

    /**
     * Observes stack changes; delivery starts with the current snapshot.
     * @param observer notified on the main thread of every published snapshot
     * @return handle cancelling the observation
     */
    Cancellable observe(Observable.Observer<List<ScreenKey<?, ?>>> observer);

    /** Cancels observers and rejects further mutations; the last snapshot stays readable. */
    @Override
    void close();
}
