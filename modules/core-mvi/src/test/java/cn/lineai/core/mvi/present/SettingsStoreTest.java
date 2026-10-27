package cn.lineai.core.mvi.present;

import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.mvi.nav.BackStack;
import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenId;
import cn.lineai.core.mvi.nav.ScreenRegistry;
import cn.lineai.core.mvi.testing.TestScheduler;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Executable integration example: immutable settings state, IO persistence and screen teardown. */
public class SettingsStoreTest {
    @Test
    public void settingsTogglePersistsOnIoAndRendersOnMain() {
        TestScheduler scheduler = new TestScheduler();
        MemorySettings repository = new MemorySettings();
        Store<SettingsState, SettingsAction> store = new Store<>(new SettingsPresenter(repository), scheduler);
        ScreenId<SettingsState, Params.Empty> id = new ScreenId<>(
                "settings.theme", null, true, Params.Empty::fromArgs);
        BackStack nav = new BackStack(new ScreenRegistry().register(id), scheduler);
        nav.push(id.key(Params.Empty.INSTANCE));
        List<SettingsState> rendered = new ArrayList<>();
        Cancellable subscription = store.observe(state -> {
            assertTrue(scheduler.isMain());
            rendered.add(state);
        });

        store.dispatch(SettingsAction.ENABLE_DARK);
        assertTrue(store.state().saving);
        assertFalse(repository.dark);
        scheduler.drainIo();
        assertTrue(repository.dark);
        assertTrue(store.state().saving);
        scheduler.drainMain();
        assertTrue(store.state().dark);
        assertFalse(store.state().saving);
        assertEquals(3, rendered.size());

        List<Params.Args> savedStack = nav.save();
        store.close();
        assertTrue(subscription.isCancelled());
        nav.close();
        BackStack recreated = new BackStack(new ScreenRegistry().register(id), scheduler);
        recreated.restore(savedStack);
        assertEquals(id.key(Params.Empty.INSTANCE), recreated.current());
        recreated.close();
    }

    private static final class SettingsState implements UiState {
        final boolean dark;
        final boolean saving;

        SettingsState(boolean dark, boolean saving) {
            this.dark = dark;
            this.saving = saving;
        }
    }

    private enum SettingsAction implements Action { ENABLE_DARK, SAVED }
    private enum SettingsEffect implements Effect { SAVE_DARK }

    private static final class MemorySettings {
        private boolean dark;
    }

    private static final class SettingsPresenter implements Presenter<SettingsState, SettingsAction> {
        private final MemorySettings repository;

        SettingsPresenter(MemorySettings repository) {
            this.repository = repository;
        }

        @Override
        public SettingsState initial() {
            return new SettingsState(false, false);
        }

        @Override
        public Transition<SettingsState> reduce(SettingsState state, SettingsAction action) {
            if (action == SettingsAction.ENABLE_DARK) {
                return Transition.with(new SettingsState(true, true), SettingsEffect.SAVE_DARK);
            }
            return Transition.state(new SettingsState(state.dark, false));
        }

        @Override
        public void run(Effect effect, Emitter<SettingsAction> out) {
            if (effect != SettingsEffect.SAVE_DARK) {
                throw new IllegalArgumentException("Unknown settings effect");
            }
            repository.dark = true;
            out.emit(SettingsAction.SAVED);
        }
    }
}
