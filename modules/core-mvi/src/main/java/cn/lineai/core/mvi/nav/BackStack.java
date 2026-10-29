package cn.lineai.core.mvi.nav;

import cn.lineai.core.kernel.observe.Observable;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.mvi.internal.MainMailbox;
import cn.lineai.core.mvi.present.UiState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Main-thread mailbox navigation; parent is metadata, never an implicit parameterless push. */
public final class BackStack implements Navigator {
    private final ScreenRegistry registry;
    private final MainMailbox<List<ScreenKey<?, ?>>> mailbox;
    private boolean lastPushAnimated = true;

    public BackStack(ScreenRegistry registry, Scheduler scheduler) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.mailbox = new MainMailbox<>(Collections.emptyList(), scheduler);
    }

    @Override
    public <S extends UiState, P extends Params> void push(ScreenKey<S, P> key, boolean animate) {
        Objects.requireNonNull(key, "key");
        if (mailbox.isClosed()) {
            return;
        }
        registry.requireRegistered(key.id());
        mailbox.post(() -> {
            if (key.id().singleTop() && key.equals(current())) {
                return;
            }
            List<ScreenKey<?, ?>> next = new ArrayList<>(entries());
            next.add(key);
            lastPushAnimated = animate;
            publish(next);
        });
    }

    /**
     * Whether the most recent published push asked for the page transition.
     *
     * <p>The screen host reads this synchronously from the navigation observer so a silent push
     * never flickers a slide animation on its first frame.</p>
     */
    public boolean lastPushAnimated() {
        return lastPushAnimated;
    }

    @Override
    public void pop() {
        mailbox.post(() -> {
            List<ScreenKey<?, ?>> current = entries();
            if (current.size() > 1) {
                publish(current.subList(0, current.size() - 1));
            }
        });
    }

    @Override
    public void back() {
        pop();
    }

    @Override
    public void popTo(ScreenId<?, ?> id) {
        Objects.requireNonNull(id, "id");
        if (mailbox.isClosed()) {
            return;
        }
        registry.requireRegistered(id);
        mailbox.post(() -> {
            List<ScreenKey<?, ?>> current = entries();
            for (int i = current.size() - 1; i >= 0; i--) {
                if (current.get(i).id() == id) {
                    publish(current.subList(0, i + 1));
                    return;
                }
            }
        });
    }

    @Override
    public void popToRoot() {
        mailbox.post(() -> {
            List<ScreenKey<?, ?>> current = entries();
            if (current.size() > 1) {
                publish(current.subList(0, 1));
            }
        });
    }

    @Override
    public ScreenKey<?, ?> current() {
        List<ScreenKey<?, ?>> current = entries();
        return current.isEmpty() ? null : current.get(current.size() - 1);
    }

    @Override
    public List<ScreenKey<?, ?>> entries() {
        return mailbox.value();
    }

    @Override
    public List<Params.Args> save() {
        List<Params.Args> saved = new ArrayList<>();
        for (ScreenKey<?, ?> entry : entries()) {
            saved.add(entry.save());
        }
        return immutableCopy(saved);
    }

    @Override
    public void restore(List<Params.Args> saved) {
        Objects.requireNonNull(saved, "saved");
        if (mailbox.isClosed()) {
            return;
        }
        List<ScreenKey<?, ?>> restored = new ArrayList<>();
        for (Params.Args entry : immutableCopy(saved)) {
            restored.add(registry.restore(entry));
        }
        List<ScreenKey<?, ?>> snapshot = immutableCopy(restored);
        mailbox.post(() -> publish(snapshot));
    }

    private void publish(List<ScreenKey<?, ?>> entries) {
        List<ScreenKey<?, ?>> snapshot = immutableCopy(entries);
        if (!snapshot.equals(mailbox.value())) {
            mailbox.publish(snapshot);
        }
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        List<T> snapshot = new ArrayList<>(source);
        for (T entry : snapshot) {
            Objects.requireNonNull(entry, "entry");
        }
        return Collections.unmodifiableList(snapshot);
    }

    @Override
    public Cancellable observe(Observable.Observer<List<ScreenKey<?, ?>>> observer) {
        return mailbox.observe(observer);
    }

    /** Cancels observers and queued mutations; keeps the last snapshot readable. */
    @Override
    public void close() {
        mailbox.close();
    }
}
