package cn.lineai.feature.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.lineai.core.model.ThemePalette;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.tokens.Tokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class McpSettingsPageTest {

    private ActivityController<Activity> controller;
    private Activity activity;
    private MapSettings settings;
    private int sshOpens;
    private int termuxOpens;
    private int providerOpens;
    private int testOpens;
    private int modeChanges;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        settings = new MapSettings();
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
    }

    @Test
    public void localModeShowsTheLocalShellCard() {
        McpSettingsPage page = page();
        activity.setContentView(page);

        assertNotNull("local shell should be a tool card, not a bare switch",
                findText(page, text(R.string.tool_group_local_shell_name)));
        assertNotNull(findText(page, text(R.string.tool_group_local_shell_desc)));
    }

    @Test
    public void terminalProviderModeOffersShortcutToTheProviderPage() {
        settings.values.put("mcp.executionMode", McpPreferences.MODE_TERMINAL_PROVIDER);
        McpSettingsPage page = page();
        activity.setContentView(page);

        assertNotNull("the terminal provider card should explain the mode",
                findText(page, text(R.string.screen_mcp_terminal_provider_overview)));
        assertNull("the SSH card must not be shown for the provider mode",
                findText(page, text(R.string.screen_mcp_ssh_settings)));

        clickableWithText(page, text(R.string.screen_mcp_terminal_provider_manage))
                .performClick();

        assertEquals(1, providerOpens);
        assertEquals(0, sshOpens);
        assertEquals(0, termuxOpens);
    }

    @Test
    public void sshModeKeepsItsOwnShortcutsAndHidesTheProviderShortcut() {
        settings.values.put("mcp.executionMode", McpPreferences.MODE_SSH);
        McpSettingsPage page = page();
        activity.setContentView(page);

        assertNotNull(findText(page, text(R.string.screen_mcp_ssh_overview)));
        assertNull("the provider shortcut belongs to the provider mode only",
                findText(page, text(R.string.screen_mcp_terminal_provider_manage)));

        clickableWithText(page, text(R.string.screen_mcp_ssh_settings)).performClick();

        assertEquals(1, sshOpens);
        assertEquals(0, providerOpens);
    }

    @Test
    public void terminalProviderModeExposesTheConnectionTest() {
        settings.values.put("mcp.executionMode", McpPreferences.MODE_TERMINAL_PROVIDER);
        McpSettingsPage page = page();
        activity.setContentView(page);

        clickableWithText(page, text(R.string.screen_mcp_terminal_provider_test)).performClick();

        assertEquals(1, testOpens);
        assertNotNull(findText(page, text(R.string.screen_mcp_terminal_provider_testing_title)));
        page.showTerminalProviderResult("Linux ubuntu 5.10", false);
        assertNotNull(findText(page,
                text(R.string.screen_mcp_terminal_provider_test_success_title)));
    }

    @Test
    public void switchingModeNotifiesTheRuntime() {
        McpSettingsPage page = page();
        activity.setContentView(page);
        assertNull(findText(page, text(R.string.screen_mcp_terminal_provider_manage)));

        findText(page, text(R.string.screen_mcp_execution_terminal_provider)).performClick();

        assertEquals(McpPreferences.MODE_TERMINAL_PROVIDER, new McpPreferences(settings).mode());
        assertEquals(1, modeChanges);
        clickableWithText(page, text(R.string.screen_mcp_terminal_provider_manage))
                .performClick();
        assertEquals(1, providerOpens);
    }

    private McpSettingsPage page() {
        return new McpSettingsPage(activity, Tokens.of(ThemePalette.dark()), () -> { },
                new McpPreferences(settings), new McpSettingsPage.Listener() {
                    public void onOpenSshSettings() {
                        sshOpens++;
                    }

                    public void onOpenTermuxIntegration() {
                        termuxOpens++;
                    }

                    public void onOpenTerminalProviders() {
                        providerOpens++;
                    }

                    public void onExecutionModeChanged() {
                        modeChanges++;
                    }

                    public McpSettingsPage.ProviderStatus terminalProviderStatus() {
                        return new McpSettingsPage.ProviderStatus(
                                McpSettingsPage.ProviderState.NONE, "");
                    }

                    public void onTestTerminalProvider() {
                        testOpens++;
                    }
                });
    }

    private String text(int id) {
        return activity.getString(id);
    }

    private static View clickableWithText(View root, String expected) {
        TextView label = findText(root, expected);
        assertNotNull("missing text: " + expected, label);
        View view = label;
        while (view != null && !view.isClickable()) {
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        assertNotNull("no clickable ancestor for: " + expected, view);
        return view;
    }

    private static TextView findText(View root, String expected) {
        List<TextView> texts = new ArrayList<>();
        collect(root, texts);
        for (TextView view : texts) {
            if (view.getText().toString().equals(expected)) return view;
        }
        return null;
    }

    private static void collect(View root, List<TextView> out) {
        if (root instanceof TextView) {
            out.add((TextView) root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), out);
            }
        }
    }

    private static final class MapSettings implements SettingsStore {
        final Map<String, String> values = new HashMap<>();

        @Override public String get(String key, String fallback) {
            return values.getOrDefault(key, fallback);
        }

        @Override public int getInt(String key, int fallback) {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        }

        @Override public void put(String key, String value) {
            values.put(key, value);
        }

        @Override public void remove(String key) {
            values.remove(key);
        }

        @Override public void invalidate() {
        }

        @Override public Map<String, String> all() {
            return new HashMap<>(values);
        }
    }
}
