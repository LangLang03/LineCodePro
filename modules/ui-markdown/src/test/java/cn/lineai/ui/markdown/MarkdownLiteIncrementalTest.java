package cn.lineai.ui.markdown;

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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import cn.lineai.core.model.ThemePalette;
import cn.lineai.ui.tokens.Tokens;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class MarkdownLiteIncrementalTest {
    private ActivityController<Activity> controller;
    private Activity activity;
    private Tokens dark;
    private Tokens light;
    private MarkdownLite markdown;

    @Before
    public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        activity = controller.get();
        dark = Tokens.of(ThemePalette.dark());
        light = Tokens.of(ThemePalette.light());
        markdown = new MarkdownLite(activity, dark);
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
    }

    @Test
    public void firstChunkIsSynchronousAndRemainderRunsAfterAttachAndIdle() {
        AtomicInteger completed = new AtomicInteger();
        markdown.renderIncremental("# one\n\ntwo\n\nthree\n\nfour\n\nfive", dark, 2,
                completed::incrementAndGet);
        assertEquals(2, markdown.getChildCount());
        assertEquals(0, completed.get());

        activity.setContentView(markdown);
        idleMainQueue();
        assertEquals(5, markdown.getChildCount());
        assertEquals(Arrays.asList("one", "two", "three", "four", "five"), texts(markdown));
        assertEquals(1, completed.get());

        idleMainQueue();
        assertEquals(5, markdown.getChildCount());
        assertEquals(1, completed.get());
    }

    @Test
    public void switchingSourceMidFlightDropsTheOldPass() {
        AtomicInteger oldCompleted = new AtomicInteger();
        markdown.renderIncremental("# old\n\nold body\n\nold tail", dark, 1,
                oldCompleted::incrementAndGet);
        assertEquals(1, markdown.getChildCount());

        AtomicInteger newCompleted = new AtomicInteger();
        markdown.renderIncremental("# new\n\nnew body", dark, 1, newCompleted::incrementAndGet);
        assertEquals(1, markdown.getChildCount());
        assertEquals(Arrays.asList("new"), texts(markdown));

        activity.setContentView(markdown);
        idleMainQueue();
        assertEquals(2, markdown.getChildCount());
        assertEquals(Arrays.asList("new", "new body"), texts(markdown));
        assertEquals(0, oldCompleted.get());
        assertEquals(1, newCompleted.get());
    }

    @Test
    public void fullRenderCancelsAnInFlightPass() {
        AtomicInteger completed = new AtomicInteger();
        markdown.renderIncremental("a\n\nb\n\nc", dark, 1, completed::incrementAndGet);
        markdown.render("# final", dark);
        assertEquals(1, markdown.getChildCount());

        activity.setContentView(markdown);
        idleMainQueue();
        assertEquals(1, markdown.getChildCount());
        assertEquals("final", ((TextView) markdown.getChildAt(0)).getText().toString());
        assertEquals(0, completed.get());
    }

    @Test
    public void applyThemeRestylesRetainedBlocksInPlace() {
        markdown.render("# title\n\nbody with **bold**\n\n- item", dark);
        List<View> original = descendants(markdown, View.class);
        TextView title = text("title");
        assertEquals(dark.text, title.getCurrentTextColor());

        markdown.applyTheme(light);
        assertEquals(original, descendants(markdown, View.class));
        assertSame(title, text("title"));
        assertEquals(light.text, title.getCurrentTextColor());

        markdown.applyTheme(light);
        assertEquals(original, descendants(markdown, View.class));
        assertEquals(light.text, title.getCurrentTextColor());
    }

    @Test
    public void chunksBuiltAfterThemeChangeUseTheNewTokens() {
        markdown.renderIncremental("# one\n\ntwo\n\nthree\n\nfour", dark, 1, () -> { });
        assertEquals(1, markdown.getChildCount());
        markdown.applyTheme(light);

        activity.setContentView(markdown);
        idleMainQueue();
        assertEquals(4, markdown.getChildCount());
        for (TextView view : descendants(markdown, TextView.class)) {
            assertEquals(light.text, view.getCurrentTextColor());
        }
    }

    @Test
    public void completedPassKeepsViewsAndReplaysCompletion() {
        String source = "one\n\ntwo\n\nthree";
        markdown.renderIncremental(source, dark, 2, () -> { });
        activity.setContentView(markdown);
        idleMainQueue();
        assertEquals(3, markdown.getChildCount());
        List<View> original = descendants(markdown, View.class);

        AtomicInteger completed = new AtomicInteger();
        markdown.renderIncremental(new String(source), dark, 2, completed::incrementAndGet);
        assertEquals(1, completed.get());
        assertEquals(original, descendants(markdown, View.class));
    }

    private void idleMainQueue() {
        for (int i = 0; i < 32; i++) {
            shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private TextView text(String expected) {
        for (TextView view : descendants(markdown, TextView.class)) {
            if (expected.equals(view.getText().toString())) {
                return view;
            }
        }
        throw new AssertionError("Missing text: " + expected + "; found " + texts(markdown));
    }

    private static List<String> texts(View root) {
        List<String> texts = new ArrayList<>();
        for (TextView view : descendants(root, TextView.class)) {
            texts.add(view.getText().toString());
        }
        return texts;
    }

    private static <T> List<T> descendants(View root, Class<T> type) {
        List<T> views = new ArrayList<>();
        if (type.isInstance(root)) {
            views.add(type.cast(root));
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                views.addAll(descendants(group.getChildAt(i), type));
            }
        }
        return views;
    }
}
