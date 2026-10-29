package cn.lineai.app;

import android.app.Application;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import cn.lineai.core.model.ThemePalette;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.feature.chat.ChatComposer;
import cn.lineai.feature.chat.ChatState;
import cn.lineai.ui.tokens.Tokens;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class RewriteComposerTest {
    private final List<String> sent = new ArrayList<>();
    private final List<List<String>> sentImages = new ArrayList<>();
    private final List<List<String>> sentFiles = new ArrayList<>();
    private int stopped;
    private final List<String> modes = new ArrayList<>();
    private int modelClicks;
    private ChatComposer composer;
    private final Tokens tokens = Tokens.of(ThemePalette.dark());

    @Before public void setup() {
        android.app.Activity activity = org.robolectric.Robolectric.buildActivity(android.app.Activity.class).setup().get();
        composer = new ChatComposer(activity, tokens);
        activity.setContentView(composer);
        composer.setListener(new ChatComposer.Listener() {
            public void onSend(String text, java.util.List<String> images,
                               java.util.List<String> files) {
                sent.add(text);
                sentImages.add(images);
                sentFiles.add(files);
            }
            public void onStop() { stopped++; }
            public void onAttachFiles() { }
            public void onAttachImage() { }
            public void onVideoPicker() { }
            public void onModel() { modelClicks++; }
            public void onReasoning() { }
            public void onMode() { }
            public void onChatMode(String mode) { modes.add(mode); }
        });
    }

    @Test public void enterSendsButShiftEnterDoesNot() {
        composer.setDraft("message");
        EditText input = input(composer);
        input.dispatchKeyEvent(new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER,
                0, KeyEvent.META_SHIFT_ON));
        assertTrue(sent.isEmpty());
        input.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        assertEquals(1, sent.size());
    }

    @Test public void newlineModeDoesNotSendFromKeyboardAction() {
        composer.preferences(UiPreferences.defaults().with(UiPreferences.Field.ENTER_BEHAVIOR, "newline"));
        composer.setDraft("draft");
        input(composer).onEditorAction(EditorInfo.IME_ACTION_SEND);
        assertTrue(sent.isEmpty());
        assertEquals("draft", composer.draft());
    }

    @Test public void imeSendDispatchesOnceAndClearsDraft() {
        composer.setDraft("draft");
        input(composer).onEditorAction(EditorInfo.IME_ACTION_SEND);
        assertEquals(java.util.Collections.singletonList("draft"), sent);
        assertEquals("", composer.draft());
    }

    @Test public void blankStreamingButtonStopsInsteadOfTryingToSend() {
        composer.render(ChatState.builder().streaming(true).build(), tokens);
        find(composer, RuntimeEnvironment.getApplication().getString(cn.lineai.ui.i18n.R.string.chat_stop)).performClick();
        assertEquals(1, stopped);
        assertTrue(sent.isEmpty());
    }

    @Test public void queuedTextIsNotLostOrSentUntilCompletion() {
        composer.render(ChatState.builder().streaming(true).build(), tokens);
        composer.setDraft("next");
        input(composer).onEditorAction(EditorInfo.IME_ACTION_SEND);
        assertTrue(sent.isEmpty());
        android.os.Bundle saved = composer.save();
        composer.restore(saved);
        composer.render(ChatState.empty(), tokens);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        java.util.ArrayList<android.os.Bundle> queue = saved.getParcelableArrayList("queue");
        assertEquals(1, queue.size());
        assertEquals("next", queue.get(0).getString("text"));
        assertEquals(java.util.Collections.singletonList("next"), sent);
        composer.render(ChatState.empty(), tokens);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        assertEquals(1, sent.size());
    }

    @Test public void slashCommandsSwitchModeOrOpenPickers() {
        composer.setDraft("/plan");
        input(composer).onEditorAction(EditorInfo.IME_ACTION_SEND);
        assertEquals(java.util.Collections.singletonList("plan"), modes);
        assertTrue(sent.isEmpty());
        composer.setDraft("/model");
        input(composer).onEditorAction(EditorInfo.IME_ACTION_SEND);
        assertEquals(1, modelClicks);
        composer.setDraft("/agent fix this");
        input(composer).onEditorAction(EditorInfo.IME_ACTION_SEND);
        assertEquals(java.util.Collections.singletonList("/agent fix this"), sent);
    }

    @Test public void attachmentAloneIsSendable() throws Exception {
        java.io.File file = java.io.File.createTempFile("attach", ".png");
        java.nio.file.Files.write(file.toPath(), new byte[] {1, 2, 3});
        composer.addAttachment(file.getAbsolutePath());
        assertTrue(composer.hasContent());
        composer.setDraft("");
        input(composer).onEditorAction(EditorInfo.IME_ACTION_SEND);
        assertEquals(java.util.Collections.singletonList(""), sent);
        assertEquals(java.util.Collections.singletonList(file.getAbsolutePath()),
                sentImages.get(0));
        file.delete();
    }

    @Test public void fileAttachmentIsCarriedWithTheMessage() throws Exception {
        java.io.File file = java.io.File.createTempFile("notes", ".txt");
        java.nio.file.Files.write(file.toPath(), "content".getBytes("UTF-8"));
        composer.addFileAttachment(file.getAbsolutePath());
        assertTrue(composer.hasContent());
        input(composer).onEditorAction(EditorInfo.IME_ACTION_SEND);
        assertEquals(java.util.Collections.singletonList(""), sent);
        assertEquals(java.util.Collections.singletonList(file.getAbsolutePath()),
                sentFiles.get(0));
        file.delete();
    }

    @Test public void draftAndSelectionSurviveRecreationAndThemeChange() {
        composer.setDraft("keep draft");
        input(composer).setSelection(3);
        android.os.Bundle saved = composer.save();
        ChatComposer next = new ChatComposer(RuntimeEnvironment.getApplication(), tokens);
        next.restore(saved);
        EditText before = input(next);
        next.applyTokens(Tokens.of(ThemePalette.light()));
        assertSame(before, input(next));
        assertEquals("keep draft", next.draft());
        assertEquals(3, before.getSelectionStart());
    }

    private EditText input(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText result = input(group.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    private View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = find(group.getChildAt(i), description);
                if (result != null) return result;
            }
        }
        return null;
    }
}
