package cn.lineai.feature.settings;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.core.model.UiPreferences.Field;
import cn.lineai.core.mvi.present.Store;
import cn.lineai.data.api.SettingsStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsPresenterTest {
    @Test public void loadsPersistedSettingsAndNormalizesInvalidValues() {
        MemorySettings data = new MemorySettings();
        data.put(Field.CODE_WRAP.key, "true");
        data.put(Field.ENTER_BEHAVIOR.key, "invalid");
        Store<SettingsState, SettingsAction> store = open(data);
        assertTrue(store.state().loaded);
        assertTrue(store.state().values.codeWrap());
        assertTrue(store.state().values.enterSends());
        assertTrue(data.writes.size() == 2);
    }

    @Test public void changesArePersistedAndSurviveReopening() {
        MemorySettings data = new MemorySettings();
        Store<SettingsState, SettingsAction> store = open(data);
        store.dispatch(new SettingsAction.Change(Field.ENTER_BEHAVIOR, "newline"));
        store.dispatch(new SettingsAction.Change(Field.CODE_WRAP, "true"));
        store.dispatch(new SettingsAction.Change(Field.BROWSER_MODE, "external"));
        store.dispatch(new SettingsAction.Change(Field.PROCESS_AUTO_EXPAND, "true"));
        store.close();
        SettingsState reopened = open(data).state();
        assertFalse(reopened.values.enterSends());
        assertTrue(reopened.values.codeWrap());
        assertTrue(reopened.values.externalBrowser());
        assertTrue(reopened.values.processAutoExpand());
        assertEquals(reopened.values, reopened.saved);
    }

    @Test public void unchangedValueDoesNotWrite() {
        MemorySettings data = new MemorySettings();
        Store<SettingsState, SettingsAction> store = open(data);
        store.dispatch(new SettingsAction.Change(Field.CODE_WRAP, "false"));
        assertTrue(data.writes.isEmpty());
    }

    @Test public void failedWriteRevertsToCommittedState() {
        MemorySettings data = new MemorySettings();
        Store<SettingsState, SettingsAction> store = open(data);
        data.fail = true;
        store.dispatch(new SettingsAction.Change(Field.CODE_WRAP, "true"));
        assertEquals(ErrorCode.STORAGE, store.state().error);
        assertFalse(store.state().values.codeWrap());
        data.fail = false;
        store.dispatch(new SettingsAction.Change(Field.CODE_WRAP, "true"));
        assertNull(store.state().error);
        assertTrue(store.state().saved.codeWrap());
    }

    @Test public void loadFailureDoesNotEnableWrites() {
        MemorySettings data = new MemorySettings();
        data.fail = true;
        Store<SettingsState, SettingsAction> store = open(data);
        assertFalse(store.state().loaded);
        assertEquals(ErrorCode.STORAGE, store.state().error);
        store.dispatch(new SettingsAction.Change(Field.CODE_WRAP, "true"));
        assertTrue(data.writes.isEmpty());
    }

    @Test public void securityPreferencesDefaultOffAndRoundTrip() {
        MemorySettings data = new MemorySettings();
        Store<SettingsState, SettingsAction> store = open(data);
        assertFalse(store.state().values.allowAnyHttp());
        assertFalse(store.state().values.browserJavaScript());
        assertFalse(store.state().values.bypassPathProtection());
        store.dispatch(new SettingsAction.Change(Field.ALLOW_ANY_HTTP, "true"));
        store.dispatch(new SettingsAction.Change(Field.BROWSER_JAVASCRIPT, "true"));
        store.dispatch(new SettingsAction.Change(Field.BYPASS_PATH_PROTECTION, "true"));
        assertTrue(store.state().saved.allowAnyHttp());
        assertTrue(store.state().saved.browserJavaScript());
        assertTrue(store.state().saved.bypassPathProtection());
    }

    @Test public void reducerDoesNotReadOrWriteStorage() {
        MemorySettings data = new MemorySettings();
        data.fail = true;
        SettingsPresenter presenter = new SettingsPresenter(data);
        SettingsState loaded = new SettingsState(UiPreferences.defaults(), UiPreferences.defaults(), null, true, null);
        presenter.reduce(loaded, new SettingsAction.Change(Field.CODE_WRAP, "true"));
        assertTrue(data.writes.isEmpty());
    }

    private Store<SettingsState, SettingsAction> open(MemorySettings data) {
        Store<SettingsState, SettingsAction> store = new Store<>(new SettingsPresenter(data), Scheduler.immediate());
        store.dispatch(new SettingsAction.Load());
        return store;
    }

    static final class MemorySettings implements SettingsStore {
        final Map<String, String> values = new HashMap<>();
        final List<String> writes = new ArrayList<>();
        boolean fail;
        public String get(String key, String fallback) { return values.getOrDefault(key, fallback); }
        public int getInt(String key, int fallback) { return Integer.parseInt(get(key, String.valueOf(fallback))); }
        public void remove(String key) { values.remove(key); }
        public void invalidate() { }
        public void put(String key, String value) {
            if (fail) throw new DomainException(ErrorCode.STORAGE, "test storage failure");
            values.put(key, value);
            writes.add(key);
        }
        public Map<String, String> all() {
            if (fail) throw new DomainException(ErrorCode.STORAGE, "test storage failure");
            return new HashMap<>(values);
        }
    }
}
