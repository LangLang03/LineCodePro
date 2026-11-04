package cn.lineai.feature.settings;

import android.app.Activity;
import android.os.Looper;
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
import java.util.List;

import cn.lineai.core.model.ThemePalette;
import cn.lineai.ui.tokens.Tokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TutorialPageTest {

    private static final String LAST_SECTION = "附：隐私与安全提示";
    private static final int RESTORED_OFFSET = 1500;

    private ActivityController<Activity> controller;
    private Activity activity;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
    }

    @Test
    public void rendersFirstChunkSynchronouslyAndCompletesAfterAttach() {
        TutorialPage page = newPage();
        assertFalse("Only the first chunk should exist before layout",
                containsText(page, LAST_SECTION));

        activity.setContentView(page);
        idleMainQueue();

        assertTrue("The whole tutorial should be materialised",
                containsText(page, LAST_SECTION));
    }

    @Test
    public void restoredScrollOffsetIsAppliedAfterTheFullDocumentRenders() {
        TutorialPage page = newPage();
        page.restoreScroll(RESTORED_OFFSET);

        activity.setContentView(page);
        idleMainQueue();
        measureAndLayout(page);
        page.getViewTreeObserver().dispatchOnPreDraw();

        assertEquals(RESTORED_OFFSET, page.scroll.getScrollY());
    }

    private TutorialPage newPage() {
        return new TutorialPage(activity, Tokens.of(ThemePalette.dark()), () -> { });
    }

    private void measureAndLayout(TutorialPage page) {
        int width = 1080;
        int height = 2400;
        page.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, width, height);
    }

    private void idleMainQueue() {
        for (int i = 0; i < 32; i++) {
            shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private static boolean containsText(View root, String expected) {
        List<TextView> texts = new ArrayList<>();
        collect(root, texts);
        for (TextView view : texts) {
            if (view.getText().toString().equals(expected)) {
                return true;
            }
        }
        return false;
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
}
