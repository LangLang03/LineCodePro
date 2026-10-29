package cn.lineai.core.mvi.nav;

import cn.lineai.core.kernel.observe.Observable;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.mvi.present.UiState;

import java.util.List;

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

    void back();

    /** Keeps the nearest matching entry; an absent registered id is a no-op. */
    void popTo(ScreenId<?, ?> id);

    void popToRoot();

    /** Null only when empty. */
    ScreenKey<?, ?> current();

    /** Immutable snapshot, ordered root to top. */
    List<ScreenKey<?, ?>> entries();

    List<Params.Args> save();

    /** Validates and decodes every entry before posting one atomic replacement. */
    void restore(List<Params.Args> saved);

    Cancellable observe(Observable.Observer<List<ScreenKey<?, ?>>> observer);

    @Override
    void close();
}
