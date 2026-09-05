package cn.lineai.ui.component;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.ChatUiState;
import cn.lineai.model.ThemePalette;
import cn.lineai.model.ToolApproval;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.mvp.MainUiController;
import cn.lineai.tool.ToolNames;
import cn.lineai.tool.ui.ToolCallReadView;
import cn.lineai.tool.ui.ToolCallShellView;
import cn.lineai.ui.MainChatView;
import cn.lineai.ui.model.ConversationTimeline;
import cn.lineai.ui.theme.LineTheme;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class, qualifiers = "zh-rCN-w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ChatLayoutTest {
    private Activity activity;
    @Before public void setup() {
        LineTheme.apply(ThemePalette.forMode("light"));
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        cn.lineai.tool.ui.ToolCallViewFactoryRegistry registry = new cn.lineai.tool.ui.ToolCallViewFactoryRegistry();
        registry.register(new cn.lineai.tool.ui.ReadToolCallViewFactory());
        registry.register(new cn.lineai.tool.ui.ShellToolCallViewFactory());
        registry.register(new cn.lineai.tool.ui.WriteToolCallViewFactory(id -> new cn.lineai.model.DiffUiModel(id,
                "/workspace/SshConnectionPool.java", "private Session connect() {\n    return cachedSession;\n}\n",
                "private Session connect() {\n    if (!isSessionAlive()) {\n        clearStaleSession();\n        return retryWithBackoff(3);\n    }\n    return cachedSession;\n}\n", false)));
        cn.lineai.tool.ui.ToolCallViewFactoryRegistry.setDefault(registry);
        cn.lineai.tool.ui.ToolInfoResolverProvider.setDefault(new cn.lineai.tool.ui.ToolInfoResolver() {
            public cn.lineai.tool.ToolDisplayCategory getDisplayCategory(String name) {
                if (ToolNames.FILE_READ.equals(name)) return cn.lineai.tool.ToolDisplayCategory.READ;
                if (ToolNames.FILE_EDIT.equals(name) || ToolNames.FILE_WRITE.equals(name)) return cn.lineai.tool.ToolDisplayCategory.WRITE;
                if (ToolNames.SHELL_EXECUTE.equals(name)) return cn.lineai.tool.ToolDisplayCategory.SHELL;
                return cn.lineai.tool.ToolDisplayCategory.GENERIC;
            }
            public String getDisplayLabel(android.content.Context context, String name, org.json.JSONObject input, String workspace) { return input.optString("file_path", name); }
            public String getActionName(android.content.Context context, String name) { return name; }
            public int getActionIcon(String name) { return cn.lineai.ui.theme.IconButtonView.FILE; }
            public cn.lineai.tool.ToolInfo getToolInfo(String name) { return null; }
        });
    }
    private void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }
    private int wrapHeight(View view, int width) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, width, view.getMeasuredHeight()); return view.getMeasuredHeight();
    }
    @Test public void emptyComposerStaysCompactAndGrowsForOnlyThreeLines() {
        ComposerView composer = new ComposerView(activity); activity.setContentView(composer);
        int empty = wrapHeight(composer, 390);
        assertTrue("Empty composer including margins: " + empty, empty <= 94);
        EditText input = find(composer, EditText.class);
        input.setText("one\ntwo\nthree\nfour\nfive\nsix\nseven");
        int grown = wrapHeight(composer, 390);
        assertTrue(grown > empty); assertTrue("Long draft: " + grown, grown <= 160);
        assertEquals(3, input.getMaxLines());
        assertTrue(input.getHeight() <= input.getLineHeight() * 3 + input.getCompoundPaddingTop() + input.getCompoundPaddingBottom());
        input.setText(""); assertEquals(empty, wrapHeight(composer, 390));
    }
    @Test public void userMessageIsARightAlignedBubble() {
        UserMessageView view = new UserMessageView(activity);
        view.bind(new ChatMessage("user", ChatMessage.Role.USER, "SSH 断开后，帮我自动重连。", false));
        wrapHeight(view, 390);
        TextView text = text(view, "SSH 断开后，帮我自动重连。");
        assertNotNull(text.getBackground()); assertEquals(362, text.getRight());
        assertTrue(text.getLeft() > 28);
    }
    @Test public void streamUpdatesRespectManualProcessAndToolDisclosure() {
        Map<String, Boolean> expansion = new HashMap<>();
        ToolCall call = new ToolCall("read", ToolNames.FILE_READ, "{\"file_path\":\"/workspace/Example.java\"}");
        ChatMessage work = new ChatMessage("work", ChatMessage.Role.ASSISTANT, "", false).withToolCalls(Collections.singletonList(call), false);
        ChatMessage answer = new ChatMessage("answer", ChatMessage.Role.ASSISTANT, "重连已经恢复。", false);
        AssistantTurnView view = new AssistantTurnView(activity); activity.setContentView(view);
        bindTurn(view, expansion, work, answer);
        assertNull(text(view, "使用了 1 个工具"));
        ((View) text(view, "正在处理").getParent()).performClick();
        ((View) text(view, "使用了 1 个工具").getParent()).performClick();
        assertTrue(expansion.get("tools:read"));
        bindTurn(view, expansion, work.withToolResults(Collections.singletonList(ToolResult.of("read", ToolNames.FILE_READ, "SECRET CONTENT", false))), answer);
        assertTrue(expansion.get("work:process")); assertTrue(expansion.get("tools:read"));
        assertNotNull(find(view, ToolCallReadView.class)); assertNull(text(view, "SECRET CONTENT"));
        ((View) text(view, "已处理").getParent()).performClick();
        bindTurn(view, expansion, work, answer);
        assertFalse(expansion.get("work:process"));
        assertTrue(text(view, "重连已经恢复。").isShown());
        ChatMessage historical = work.withToolResults(Collections.singletonList(
                ToolResult.withReview("read", ToolNames.FILE_READ, "", false, "", "pending", "")));
        view.bind(ConversationTimeline.build(Arrays.asList(historical, answer)).get(0), expansion,
                "/workspace", null, null, null, false, false);
        assertNotNull(text(view, "已处理")); assertNull(text(view, "等待确认"));
    }
    private void bindTurn(AssistantTurnView view, Map<String, Boolean> expansion, ChatMessage work, ChatMessage answer) {
        view.bind(ConversationTimeline.build(Arrays.asList(work, answer)).get(0), expansion, "/workspace", null, null, null, false, true);
        wrapHeight(view, 390);
    }
    @Test public void readRowsAreNotClickableAndNeverContainTheFileContents() {
        ToolCallReadView view = new ToolCallReadView(activity);
        ToolCall call = new ToolCall("read", ToolNames.FILE_READ, "{\"file_path\":\"/workspace/a.txt\"}");
        view.bind(call, ToolResult.of("read", ToolNames.FILE_READ, "SECRET CONTENT", false));
        assertFalse(view.isClickable()); assertNull(text(view, "SECRET CONTENT"));
        for (int i = 0; i < view.getChildCount(); i++) assertFalse(view.getChildAt(i).isClickable());
    }
    @Test public void shellOutputDoesNotAutoExpandWhenRunning() {
        ToolCallShellView view = new ToolCallShellView(activity); activity.setContentView(view);
        Map<String, Boolean> state = new HashMap<>(); view.setExpansionState(state, "shell");
        ToolCall call = new ToolCall("cmd", ToolNames.SHELL_EXECUTE, "{\"command\":\"pwd\"}");
        view.bind(call, ToolResult.withReview("cmd", ToolNames.SHELL_EXECUTE, "/workspace", false, "", "running", ""));
        assertNull(text(view, "$ pwd\n\n/workspace"));
        ((View) text(view, activity.getString(cn.lineai.tool.ui.R.string.tool_call_status_running) + "  pwd").getParent()).performClick();
        assertTrue(text(view, "$ pwd\n\n/workspace").isShown());
        view.bind(call, ToolResult.of("cmd", ToolNames.SHELL_EXECUTE, "finished", false));
        assertTrue(state.get("shell"));
    }
    @Test public void approvalUsesComposerSlotAndKeepsDraftThenRespondsOnce() throws Exception {
        final int[] reviews = {0}; final String[] decision = {""};
        MainUiController presenter = (MainUiController) Proxy.newProxyInstance(MainUiController.class.getClassLoader(),
                new Class<?>[]{MainUiController.class}, (proxy, method, args) -> {
                    if (method.getName().equals("onToolReview")) { reviews[0]++; decision[0] = (String) args[1]; }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                });
        MainChatView root = new MainChatView(activity, presenter); activity.setContentView(root);
        ChatUiState idle = new ChatUiState("LineCode", "/workspace", "model", "0%", 0, false, true,
                Collections.singletonList(new ChatMessage("u", ChatMessage.Role.USER, "检查一下构建。", false)));
        root.render(idle); root.setComposerDraft("保留草稿");
        ToolCall call = new ToolCall("cmd", ToolNames.SHELL_EXECUTE, "{\"command\":\"./gradlew :app:assembleDebug\"}");
        root.render(idle.withToolApproval(new ToolApproval("cmd", call, true))); layout(root, 320, 720);
        assertEquals(View.GONE, find(root, ComposerView.class).getVisibility());
        assertTrue(find(root, ToolApprovalView.class).isShown());
        TextView always = text(root, "永久允许"); assertTrue(always.getWidth() > 0); assertTrue(always.getHeight() >= 44);
        always.performClick(); always.performClick(); assertEquals(1, reviews[0]); assertEquals("permanent", decision[0]);
        screenshot(root, "native-light-320-permission");
        root.render(idle); layout(root, 320, 720);
        assertEquals(View.VISIBLE, find(root, ComposerView.class).getVisibility());
        assertEquals("保留草稿", find(root, EditText.class).getText().toString());
    }
    @Test public void tabletConversationColumnIsCenteredAndBounded() {
        MainChatViewLayoutBuilder.Result result = MainChatViewLayoutBuilder.build(activity);
        layout(result.contentView, 1100, 800);
        assertEquals(792, result.contentView.getMeasuredWidth());
    }
    @Test public void darkConversationCanRenderWithoutExtraToolCards() throws Exception {
        LineTheme.apply(ThemePalette.forMode("dark"));
        android.widget.LinearLayout root = new android.widget.LinearLayout(activity); root.setOrientation(1); root.setBackgroundColor(LineTheme.BG);
        root.addView(new HeaderView(activity));
        UserMessageView user = new UserMessageView(activity); user.bind(new ChatMessage("user", ChatMessage.Role.USER, "SSH 断开后，帮我自动重连。", false)); root.addView(user);
        AssistantTurnView turn = new AssistantTurnView(activity);
        ChatMessage work = new ChatMessage("work", ChatMessage.Role.ASSISTANT, "", false).withToolCalls(Collections.singletonList(new ToolCall("call", ToolNames.SHELL_EXECUTE, "{}")), false)
                .withToolResults(Collections.singletonList(ToolResult.of("call", ToolNames.SHELL_EXECUTE, "done", false)));
        turn.bind(ConversationTimeline.build(Arrays.asList(work, new ChatMessage("answer", ChatMessage.Role.ASSISTANT,
                "## 重连已经恢复。\n\n连接断开时，先清理失效会话。随后逐步增加重试间隔。\n\n断线恢复与取消场景，都已通过测试。", false))).get(0),
                new HashMap<>(), "/workspace", null, null, null, false, false);
        root.addView(turn); View space = new View(activity); root.addView(space, new android.widget.LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(new ComposerView(activity)); activity.setContentView(root); layout(root, 390, 844); screenshot(root, "native-dark-390-chat");
    }
    @Test public void editsShowInlineDiffAndRetainReviewActions() throws Exception {
        ToolCall call = new ToolCall("edit", ToolNames.FILE_EDIT, "{\"file_path\":\"/workspace/SshConnectionPool.java\"}");
        ToolResult result = ToolResult.withReview("edit", ToolNames.FILE_EDIT, "done", false, "diff", "", "");
        ChatMessage work = new ChatMessage("work", ChatMessage.Role.ASSISTANT, "修改完成，运行测试并检查差异。", false)
                .withToolCalls(Collections.singletonList(call), false).withToolResults(Collections.singletonList(result));
        ChatMessage answer = new ChatMessage("answer", ChatMessage.Role.ASSISTANT, "## 重连已经恢复。\n\n断线恢复与取消场景，都已通过测试。", false);
        AssistantTurnView view = new AssistantTurnView(activity); view.setBackgroundColor(LineTheme.BG);
        Map<String, Boolean> state = new HashMap<>(); state.put("work:process", true); state.put("tools:edit", true); state.put("call:edit", true);
        String[] reviewed = {""};
        view.bind(ConversationTimeline.build(Arrays.asList(work, answer)).get(0), state, "/workspace",
                (id, decision, diffId) -> reviewed[0] = decision + ":" + diffId, null, null, false, false);
        activity.setContentView(view);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (text(view, "    if (!isSessionAlive()) {") == null && System.nanoTime() < deadline) {
            Thread.sleep(10); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        }
        assertNotNull(text(view, "    if (!isSessionAlive()) {"));
        wrapHeight(view, 390);
        screenshot(view, "native-light-390-diff");
        assertNull(text(view, "apply_patch"));
        TextView revert = text(view, activity.getString(cn.lineai.tool.ui.R.string.tool_call_write_revert));
        revert.performClick(); assertEquals("rejected:diff", reviewed[0]);
        text(view, "审阅").performClick(); assertTrue(state.get("work:files")); assertTrue(state.get("review:edit"));
    }

    @Test public void approvalActionsFitAtLargeFontScale() throws Exception {
        android.content.res.Configuration config = new android.content.res.Configuration(activity.getResources().getConfiguration());
        config.fontScale = 1.6f;
        ToolApprovalView view = new ToolApprovalView(activity.createConfigurationContext(config));
        ToolCall call = new ToolCall("cmd", ToolNames.SHELL_EXECUTE, "{\"command\":\"./gradlew :app:assembleDebug\"}");
        view.bind(new ToolApproval("cmd", call, true)); activity.setContentView(view);
        int height = wrapHeight(view, 320);
        assertTrue("Approval height: " + height, height < 400);
        assertEquals(1, text(view, "./gradlew :app:assembleDebug").getLayout().getLineCount());
        for (String label : new String[]{"拒绝", "允许一次", "永久允许"}) {
            TextView button = text(view, label);
            assertTrue(button.getWidth() > 0); assertTrue(button.getHeight() >= 44);
            assertTrue(button.getLayout().getHeight() <= button.getHeight() - button.getPaddingTop() - button.getPaddingBottom());
        }
        screenshot(view, "native-light-320-large-font-permission");
    }

    @Test public void deletionShowsExactPathsAndOnlyOneApprovalSurface() {
        ToolCall call = new ToolCall("delete", ToolNames.FILE_DELETE,
                "{\"reason\":\"清理构建输出\",\"paths\":[\"/workspace/build\",\"/workspace/cache\"]}");
        ToolApprovalView approval = new ToolApprovalView(activity);
        approval.bind(new ToolApproval("delete", call, false)); wrapHeight(approval, 320);
        assertNotNull(text(approval, "/workspace/build\n/workspace/cache"));
        assertEquals(View.GONE, text(approval, "永久允许").getVisibility());
        cn.lineai.tool.ui.ToolCallDeleteView row = new cn.lineai.tool.ui.ToolCallDeleteView(activity);
        Map<String, Boolean> state = new HashMap<>(); state.put("delete", true); row.setExpansionState(state, "delete");
        row.bind(call, ToolResult.withReview("delete", ToolNames.FILE_DELETE, "", false, "", "pending", ""));
        assertNull(text(row, activity.getString(cn.lineai.tool.ui.R.string.tool_call_delete_confirm)));
    }

    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { T found = find(group.getChildAt(i), type); if (found != null) return found; }
        } return null;
    }
    private static TextView text(View view, String value) {
        if (view instanceof TextView && value.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof ViewGroup) { ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) { TextView found = text(group.getChildAt(i), value); if (found != null) return found; }
        } return null;
    }
    private void screenshot(View view, String name) throws Exception {
        File directory = new File("build/reports/ui-previews"); assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        bitmap.recycle();
    }
}
