package cn.lineai.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ThemePalette;
import cn.lineai.core.mvi.nav.BackStack;
import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenId;
import cn.lineai.core.mvi.nav.ScreenRegistry;
import cn.lineai.core.mvi.present.UiState;
import cn.lineai.ui.i18n.R;
import cn.lineai.runtime.ScreenHost;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.tokens.Tokens;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class RewriteScreenHostTest {
    private final ScreenId<UiState, Params.Empty> root = new ScreenId<>("root", Params.Empty::fromArgs);
    private final ScreenId<UiState, Params.Empty> settings = new ScreenId<>("settings", root, true, Params.Empty::fromArgs);
    private final ScreenId<UiState, Params.Empty> input = new ScreenId<>("input", settings, true, Params.Empty::fromArgs);
    private ScreenRegistry registry;
    private BackStack navigation;
    private FrameLayout frame;
    private Activity activity;
    private ScreenHost host;
    private int disposed;

    @Before public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        registry = new ScreenRegistry().register(root).register(settings).register(input);
        mount();
    }

    private void mount() {
        frame = new FrameLayout(activity);
        activity.setContentView(frame);
        navigation = new BackStack(registry, Scheduler.immediate());
        navigation.push(root.key(Params.Empty.INSTANCE));
        host = new ScreenHost(frame, navigation, key -> new SettingsPage(activity, R.string.screen_settings_title,
                Tokens.of(ThemePalette.dark()), navigation::back) {
            @Override public void dispose() { disposed++; }
        });
    }

    @Test public void poppingChildReusesParentViewAndReleasesChild() {
        navigation.push(settings.key(Params.Empty.INSTANCE));
        View parent = frame.getChildAt(0);
        navigation.push(input.key(Params.Empty.INSTANCE));
        assertEquals(View.GONE, parent.getVisibility());
        navigation.back();
        assertEquals(1, disposed);
        assertSame(parent, frame.getChildAt(0));
        assertEquals(View.VISIBLE, parent.getVisibility());
    }

    @Test public void savedStackRestoresChildAndItsParent() {
        navigation.push(settings.key(Params.Empty.INSTANCE));
        navigation.push(input.key(Params.Empty.INSTANCE));
        Bundle saved = host.save();
        host.close();
        mount();
        host.restore(saved, registry);
        assertEquals(3, navigation.entries().size());
        assertSame(input, navigation.current().id());
        navigation.back();
        assertSame(settings, navigation.current().id());
        assertEquals(View.VISIBLE, frame.getChildAt(0).getVisibility());
    }

    @Test public void changingThemeRetainsAllMountedScreens() {
        navigation.push(settings.key(Params.Empty.INSTANCE));
        View before = frame.getChildAt(0);
        host.applyTokens(Tokens.of(ThemePalette.light()));
        assertSame(before, frame.getChildAt(0));
        assertEquals(0, disposed);
    }

    @Test public void backToRootHidesOverlayAndCloseReleasesSubscriptions() {
        navigation.push(settings.key(Params.Empty.INSTANCE));
        navigation.back();
        assertEquals(View.GONE, frame.getVisibility());
        assertEquals(0, frame.getChildCount());
        host.close();
        navigation.push(input.key(Params.Empty.INSTANCE));
        assertEquals(0, frame.getChildCount());
    }
}
