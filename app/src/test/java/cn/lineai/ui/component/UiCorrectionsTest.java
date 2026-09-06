package cn.lineai.ui.component;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.*;
import cn.lineai.model.tool.*;
import cn.lineai.tool.*;
import cn.lineai.tool.ui.*;
import cn.lineai.ui.model.ConversationTimeline;
import cn.lineai.ui.theme.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, application=Application.class, qualifiers="zh-rCN-w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiCorrectionsTest {
    private Activity activity;
    private final List<String> events = new ArrayList<>();
    @Before public void setup() {
        LineTheme.apply(ThemePalette.forMode("light"));
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        ToolCallViewFactoryRegistry registry=new ToolCallViewFactoryRegistry();
        registry.register(new AgentToolCallViewFactory());
        registry.register(new AgentPipelineToolCallViewFactory());
        registry.register(new ReadToolCallViewFactory());
        registry.register(new ShellToolCallViewFactory());
        ToolCallViewFactoryRegistry.setDefault(registry);
        ToolInfoResolverProvider.setDefault(new ToolDisplayResolver(new ToolRegistry()));
    }
    @After public void finish() { activity.finish(); }
    @SuppressWarnings("unchecked") private <T> T listener(Class<T> type) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            events.add(method.getName());
            if (List.class.isAssignableFrom(method.getReturnType())) return Collections.emptyList();
            return null;
        });
    }
    private void layout(View view) {
        activity.setContentView(view);
        view.measure(View.MeasureSpec.makeMeasureSpec(390, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(844, View.MeasureSpec.EXACTLY));
        view.layout(0,0,390,844);
    }
    private static <T> List<T> all(View view, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(view)) found.add(type.cast(view));
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++)
            found.addAll(all(((ViewGroup)view).getChildAt(i), type));
        return found;
    }
    private static IconButtonView icon(View view, int type) {
        for (IconButtonView button : all(view,IconButtonView.class)) if (button.getIconType()==type) return button;
        throw new AssertionError("Missing icon: "+type);
    }
    private static TextView text(View view, String content) {
        for (TextView t : all(view,TextView.class)) if (content.equals(t.getText().toString())) return t;
        throw new AssertionError("Missing text: "+content);
    }
    private static int iconSize(IconButtonView button) {
        return Math.min(button.getWidth()-button.getPaddingLeft()-button.getPaddingRight(),
                button.getHeight()-button.getPaddingTop()-button.getPaddingBottom());
    }
    private void screenshot(View view,String name) throws Exception {
        File file = new File("build/reports/ui-corrections/"+name+".png");
        file.getParentFile().mkdirs();
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(LineTheme.BG);view.draw(canvas);
        try(FileOutputStream output=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,output);}
        bitmap.recycle();
    }
    @Test public void headerOrderAndIconSizeSurviveLargerTouchContainers() throws Exception {
        LinearLayout page=new LinearLayout(activity);page.setOrientation(LinearLayout.VERTICAL);
        HeaderView header=new HeaderView(activity);header.setListener(listener(HeaderView.Listener.class));
        page.addView(header,new LinearLayout.LayoutParams(-1,-2));layout(page);
        IconButtonView more=icon(header,IconButtonView.MORE),plus=icon(header,IconButtonView.PLUS);
        assertTrue(icon(header,IconButtonView.SHIELD).getRight()<=plus.getLeft());
        assertTrue(plus.getRight()<=more.getLeft());assertEquals(19,iconSize(plus));
        assertEquals(iconSize(icon(header,IconButtonView.MENU)),iconSize(more));
        assertEquals(icon(header,IconButtonView.MENU).getWidth(),more.getWidth());
        ((View)text(header,activity.getString(R.string.header_project_default)).getParent()).performClick();
        assertTrue(events.contains("onProjectClick"));
        icon(header,IconButtonView.MENU).performClick();
        assertTrue(events.contains("onMenuClick"));
        icon(header,IconButtonView.SHIELD).performClick();more.performClick();plus.performClick();
        assertTrue(events.containsAll(Arrays.asList("onPermissionClick","onMoreClick","onNewConversationClick")));
        screenshot(page,"chat-header");
        ErrorLogsScreenView logs=new ErrorLogsScreenView(activity,listener(ErrorLogsScreenView.Listener.class));layout(logs);
        assertMatchingHeaderActions(logs,IconButtonView.TRASH_2);screenshot(logs,"error-logs");
        ModelListScreenView models=new ModelListScreenView(activity,Collections.emptyList(),"",listener(ModelListScreenView.Listener.class));
        layout(models);assertMatchingHeaderActions(models,IconButtonView.PLUS);screenshot(models,"models");
        ModelAddScreenView add=new ModelAddScreenView(activity,null,false,listener(ModelAddScreenView.Listener.class));layout(add);
        assertEquals((float)LineTheme.FONT_MD,text(add,activity.getString(R.string.common_save)).getTextSize(),.01f);
        screenshot(add,"model-add");
    }
    private void assertMatchingHeaderActions(View page,int rightType) {
        IconButtonView left=icon(page,IconButtonView.CHEVRON_LEFT),right=icon(page,rightType);
        assertEquals(22,iconSize(left));
        assertEquals(left.getWidth(),right.getWidth());assertEquals(left.getHeight(),right.getHeight());
    }
    @Test public void settingsRowsUseRestoredGroupsAndStandaloneCardsKeepGaps() throws Exception {
        LLMSettingsScreenView llm=new LLMSettingsScreenView(activity,null,listener(LLMSettingsScreenView.Listener.class));layout(llm);
        List<SettingsSectionView> sections=all(llm,SettingsSectionView.class);
        assertFalse(sections.isEmpty());
        LinearLayout group=sections.get(0).getGroup();
        assertNotNull(group.getBackground());
        assertTrue(group.getChildCount()>=2);
        assertEquals(0,((LinearLayout.LayoutParams)group.getChildAt(1).getLayoutParams()).topMargin);
        assertEquals(2,((ViewGroup)group.getChildAt(0)).getChildCount());
        screenshot(llm,"reasoning-options");
        ModelAddOptionsScreenView providers=new ModelAddOptionsScreenView(activity,listener(ModelAddOptionsScreenView.Listener.class));layout(providers);
        List<LinearLayout> providerCards=all(providers,LinearLayout.class);
        int visibleCards=0;
        for(LinearLayout card:providerCards) if(card.isClickable()&&card.getBackground()!=null) visibleCards++;
        assertTrue(visibleCards>=4);
        screenshot(providers,"providers");
        McpSettingsState toolState=new McpSettingsState("local",Arrays.asList(
                new McpToolConfig("files","文件操作","读取、编辑和搜索项目文件",true,new String[]{"file_read","file_edit"}),
                new McpToolConfig("shell","终端命令","运行命令并查看结果",true,new String[]{"shell_execute"})));
        MCPSettingsScreenView tools=new MCPSettingsScreenView(activity,toolState,listener(MCPSettingsScreenView.Listener.class));layout(tools);
        assertEquals(16,tools.getContent().getPaddingLeft());assertEquals(358,tools.getContent().getChildAt(0).getWidth());
        screenshot(tools,"tools-execution");
        DrawerView drawer=new DrawerView(activity);drawer.setListener(listener(DrawerView.Listener.class));
        drawer.render(Arrays.asList(new ConversationUiModel("a","修复连接",1),new ConversationUiModel("b","工具调用",2)),"a","LineCode","/workspace",true,null);
        drawer.open();layout(drawer);
        View first=(View)text(drawer,"修复连接").getParent().getParent();
        View second=(View)text(drawer,"工具调用").getParent().getParent();
        assertTrue(second.getTop()-first.getBottom()>=8);assertNotNull(second.getBackground());
        drawer.getChildAt(1).animate().cancel();drawer.getChildAt(1).setTranslationX(0);
        drawer.getChildAt(0).animate().cancel();drawer.getChildAt(0).setAlpha(1);
        screenshot(drawer,"drawer");
    }
    @Test public void builtInCardColorsDifferAndCustomEditorIsAlwaysPresent() throws Exception {
        for(String mode:Arrays.asList("light","dark","coffee","vscode","github_dark","gruvbox","high_contrast")) {
            ThemePalette p=ThemePalette.forMode(mode);
            int delta=Math.abs(android.graphics.Color.red(p.bg)-android.graphics.Color.red(p.surfaceElevated))
                    +Math.abs(android.graphics.Color.green(p.bg)-android.graphics.Color.green(p.surfaceElevated))
                    +Math.abs(android.graphics.Color.blue(p.bg)-android.graphics.Color.blue(p.surfaceElevated));
            assertTrue(mode+": card and app colors too close",delta>=45);
            LineTheme.apply(p);SettingsScreenView settings=new SettingsScreenView(activity,listener(SettingsScreenView.Listener.class));layout(settings);
            TextView modelTitle=text(settings,activity.getString(R.string.settings_row_models_title));
            LinearLayout labels=(LinearLayout)modelTitle.getParent();
            assertTrue(all(labels,TextView.class).size()>=2);
            LinearLayout item=(LinearLayout)labels.getParent();
            assertEquals(36,item.getChildAt(0).getLayoutParams().width);
            assertNotNull(item.getChildAt(0).getBackground());
            ViewGroup wrapper=(ViewGroup)item.getParent();
            assertNotNull(((View)wrapper.getParent()).getBackground());
            screenshot(settings,"settings-"+mode);
        }
        LineTheme.apply(ThemePalette.forMode("light"));
        ThemeSettingsScreenView theme=new ThemeSettingsScreenView(activity,null,listener(ThemeSettingsScreenView.Listener.class));layout(theme);
        View save=(View)text(theme,activity.getString(R.string.screen_theme_color_save)).getParent();
        android.graphics.Rect bounds=new android.graphics.Rect(0,0,save.getWidth(),save.getHeight());
        theme.offsetDescendantRectToMyCoords(save,bounds);
        assertEquals(16,theme.getWidth()-bounds.right);
        assertTrue(all(theme,DisclosureSectionView.class).isEmpty());
        assertTrue(all(theme,android.widget.EditText.class).size()>=19);
        theme.getScrollView().scrollTo(0,theme.getContent().getChildAt(1).getTop());
        screenshot(theme,"custom-theme-restored");
    }
    private ChatMessage work(String id,String prose,String reasoning,String tool) {
        return new ChatMessage(id,ChatMessage.Role.ASSISTANT,prose,reasoning,false)
                .withToolCalls(Collections.singletonList(new ToolCall(id,tool,"{}")),false)
                .withToolResults(Collections.singletonList(ToolResult.of(id,tool,"OK",false)));
    }
    @Test public void reasoningKeepsItsPositionAndContinuousCallsDoNotCreateThinkingRows() throws Exception {
        List<ChatMessage> messages=Arrays.asList(work("a","开始检查。","第一次思考",ToolNames.FILE_READ),
                work("b","","连续调用中的思考",ToolNames.FILE_READ),
                work("c","接下来编辑。","第二次思考",ToolNames.FILE_EDIT),
                new ChatMessage("end",ChatMessage.Role.ASSISTANT,"完成。",false));
        ConversationTimeline.Row row=ConversationTimeline.build(messages).get(0);
        List<String> order=new ArrayList<>();
        for(ConversationTimeline.Block b:row.process) order.add(b.reasoning?"thinking":b.isTools()?"tools":"text");
        assertEquals(Arrays.asList("thinking","text","tools","thinking","text","tools"),order);
        Map<String,Boolean> expanded=new HashMap<>();expanded.put("a:process",true);
        AssistantTurnView view=new AssistantTurnView(activity);view.bind(row,expanded,"",null,null,null,false,false);layout(view);
        List<ThinkingBlockView> thoughts=all(view,ThinkingBlockView.class);
        int visible=0;for(ThinkingBlockView thought:thoughts)if(thought.isShown())visible++;
        assertEquals(2,visible);
        View firstThought=thoughts.get(0);
        view.bind(row,expanded,"",null,null,null,false,false);assertSame(firstThought,all(view,ThinkingBlockView.class).get(0));
        screenshot(view,"thinking-in-order");
    }
    @Test public void agentsUseOriginalExpandedCardsWithoutToolGroupWrapper() throws Exception {
        ToolCall call=new ToolCall("agent",ToolNames.AGENT,"{\"description\":\"检查连接恢复\",\"type\":\"explore\"}");
        ToolCallBlockView agent=new ToolCallBlockView(activity);agent.bind(call,ToolResult.of("agent",ToolNames.AGENT,"连接恢复验证通过。",false));
        layout(agent);ToolCallAgentView card=all(agent,ToolCallAgentView.class).get(0);
        assertTrue(card.getChildCount()>1);assertNotNull(card.getBackground());
        LinearLayout canvas=new LinearLayout(activity);canvas.setOrientation(LinearLayout.VERTICAL);
        agent.removeView(card);canvas.addView(card,new LinearLayout.LayoutParams(-1,-2));
        ToolCallAgentPipelineView pipeline=new ToolCallAgentPipelineView(activity);
        pipeline.bind(new ToolCall("pipe",ToolNames.AGENT_PIPELINE,"{\"agents\":[{\"id\":\"one\",\"description\":\"分析\",\"type\":\"explore\",\"task\":\"检查\"},{\"id\":\"two\",\"description\":\"修复\",\"type\":\"coding\",\"task\":\"修改\"}]}"),null);
        canvas.addView(pipeline,new LinearLayout.LayoutParams(-1,-2));layout(canvas);
        assertTrue(pipeline.getChildCount()>1);assertNotNull(pipeline.getBackground());screenshot(canvas,"agents-restored");
        ConversationTimeline.Row row=ConversationTimeline.build(Arrays.asList(work("a","", "",ToolNames.FILE_READ),
                work("b","","",ToolNames.AGENT),work("c","","",ToolNames.FILE_READ))).get(0);
        assertEquals(3,row.process.size());assertTrue(row.process.get(1).isAgent());
    }
    @Test public void errorsStayInsideTheirToolResultsAndFollowDisclosure() throws Exception {
        ToolCall call=new ToolCall("fail",ToolNames.FILE_READ,"{\"file_path\":\"/workspace/config.json\"}");
        ToolResult failed=ToolResult.of("fail",ToolNames.FILE_READ,"无法读取：连接已断开。\n重试失败。",true);
        ToolCall shell=new ToolCall("shell",ToolNames.SHELL_EXECUTE,"{\"command\":\"./gradlew assembleDebug\"}");
        ToolResult shellFailed=ToolResult.of("shell",ToolNames.SHELL_EXECUTE,"构建失败：无法解析依赖。\nConnection reset",true);
        ChatMessage work=new ChatMessage("a",ChatMessage.Role.ASSISTANT,"",false)
                .withToolCalls(Arrays.asList(call,shell),false).withToolResults(Arrays.asList(failed,shellFailed));
        ConversationTimeline.Row row=ConversationTimeline.build(Collections.singletonList(work)).get(0);
        Map<String,Boolean> disclosure=new HashMap<>();
        AssistantTurnView view=new AssistantTurnView(activity);
        view.bind(row,disclosure,"",null,null,null,false,false);layout(view);
        assertEquals(0,visibleTextCount(view,failed.getContent()));
        assertEquals(0,visibleTextCount(view,shellFailed.getContent()));
        screenshot(view,"collapsed-tool-error");
        ((View)text(view,activity.getString(R.string.chat_process_done)).getParent()).performClick();layout(view);
        assertEquals(0,visibleTextCount(view,failed.getContent()));
        View groupHeader=(View)text(view,activity.getString(R.string.chat_tools_count,2)).getParent();
        groupHeader.performClick();layout(view);
        ToolCallReadView read=all(view,ToolCallReadView.class).get(0);
        assertTrue(text(read,failed.getContent()).isShown());
        assertFalse(read.isClickable());
        assertEquals(1,visibleTextCount(view,failed.getContent()));
        assertEquals(0,visibleTextCount(view,shellFailed.getContent()));
        ToolCallShellView command=all(view,ToolCallShellView.class).get(0);
        command.getChildAt(0).performClick();layout(view);
        assertEquals(1,visibleTextCount(command,shellFailed.getContent()));
        assertEquals(1,visibleTextCount(view,shellFailed.getContent()));
        for(ToolCallBlockView block:all(view,ToolCallBlockView.class))assertEquals(1,block.getChildCount());
        screenshot(view,"inline-tool-error");
        groupHeader.performClick();layout(view);
        assertEquals(0,visibleTextCount(view,failed.getContent()));
        assertEquals(0,visibleTextCount(view,shellFailed.getContent()));
        read.bind(call,ToolResult.of("fail",ToolNames.FILE_READ,"file contents stay hidden",false));
        assertEquals(0,visibleTextCount(read,failed.getContent()));
        assertEquals(0,visibleTextCount(read,"file contents stay hidden"));
    }
    private int visibleTextCount(View root,String content) {
        int count=0;
        for(TextView t:all(root,TextView.class))if(t.isShown()&&t.getText().toString().contains(content))count++;
        return count;
    }
    @Test public void retryDetailsShareOneTimedSectionAndStayCollapsed() throws Exception {
        ChatMessage first=work("first","","",ToolNames.SHELL_EXECUTE).withProcessingTimes(1000,187000);
        ChatMessage retry=ChatMessage.retryNotice("retry","正在第 2/3 次重试，错误：连接已断开。")
                .withProcessingTimes(1000,187000);
        ChatMessage second=work("second","","",ToolNames.SHELL_EXECUTE).withProcessingTimes(1000,187000);
        ChatMessage answer=new ChatMessage("answer",ChatMessage.Role.ASSISTANT,"已恢复连接并完成检查。",false)
                .withProcessingTimes(1000,187000);
        List<ConversationTimeline.Row> rows=ConversationTimeline.build(Arrays.asList(first,retry,second,answer));
        assertEquals(1,rows.size());
        AssistantTurnView view=new AssistantTurnView(activity);
        view.bind(rows.get(0),new HashMap<>(),"",null,null,null,false,true);layout(view);
        String label=activity.getString(R.string.chat_process_done)+" 3m 6s";
        assertEquals(1,visibleTextCount(view,label));
        assertEquals(0,visibleTextCount(view,retry.getContent()));
        assertEquals(1,visibleTextCount(view,answer.getContent()));
        screenshot(view,"retry-collapsed-timed");
        ((View)text(view,label).getParent()).performClick();layout(view);
        assertEquals(1,visibleTextCount(view,retry.getContent()));
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(5));
        assertEquals(1,visibleTextCount(view,label));
        screenshot(view,"retry-expanded-timed");
    }
    @Test public void workingStatusAndReasoningKeepTheCompactMessageActions() throws Exception {
        AssistantMessageView view = new AssistantMessageView(activity);
        view.bind(new ChatMessage("reply", ChatMessage.Role.ASSISTANT, "", true));
        layout(view);
        WorkingStatusView status = all(view, WorkingStatusView.class).get(0);
        MessageActionBarView actions = all(view, MessageActionBarView.class).get(0);
        cn.lineai.ui.markdown.MarkdownView content = all(view, cn.lineai.ui.markdown.MarkdownView.class).get(0);
        assertEquals(View.VISIBLE, status.getVisibility());
        assertEquals(View.GONE, content.getVisibility());
        assertEquals(View.GONE, actions.getVisibility());
        view.bind(new ChatMessage("reply", ChatMessage.Role.ASSISTANT, "", "**检查重连**", true));
        layout(view);
        assertEquals(View.VISIBLE, all(view, ThinkingBlockView.class).get(0).getVisibility());
        assertEquals(View.VISIBLE, status.getVisibility());
        assertEquals(activity.getString(R.string.message_assistant_thinking), status.getContentDescription());
        ChatMessage done = new ChatMessage("reply", ChatMessage.Role.ASSISTANT, "重连已恢复。", false);
        view.bind(done);
        layout(view);
        assertEquals(View.GONE, status.getVisibility());
        assertEquals(View.GONE, actions.getVisibility());
        assertTrue(content.performLongClick());
        assertEquals(View.VISIBLE, actions.getVisibility());
        view.bind(done);
        assertEquals(View.VISIBLE, actions.getVisibility());
        screenshot(view, "working-status-compact-actions");
    }

    @Test public void toolFailuresStillProduceLogFiles() throws Exception {
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        try {
            cn.lineai.log.ErrorLog.init(activity);
            cn.lineai.log.ErrorLogRepository logs=new cn.lineai.log.ErrorLogRepository(activity.getFilesDir().getAbsolutePath());logs.clear();
            ToolExecutor executor=new ToolExecutor(new ToolRegistry(),null,null,null,null,null,null);
            ToolResult result=executor.execute(new ToolCall("missing","missing_tool","{}"),null);
            assertTrue(result.isError());assertEquals(1,logs.list().size());
            String content=new String(java.nio.file.Files.readAllBytes(logs.list().get(0).getFile().toPath()),java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(content.contains(result.getContent()));assertTrue(content.contains("tool_execution"));
            ToolRegistry registry=new ToolRegistry();
            registry.register(new BaseTool() {
                public String getName(){return "failure_test";}
                public String getDescription(){return "Failure regression test";}
                public ToolCategory getCategory(){return ToolCategory.SYSTEM;}
                public org.json.JSONObject getParameters(){return new org.json.JSONObject();}
                public ToolResult execute(org.json.JSONObject input,ToolContext context){
                    if(input.optBoolean("throw"))throw new IllegalStateException("Connection lost");
                    return ToolResult.of(context.getToolCallId(),getName(),"Command exited with code 1",true);
                }
            });
            cn.lineai.data.repository.ToolSettingsStore settings=(cn.lineai.data.repository.ToolSettingsStore)Proxy.newProxyInstance(
                    getClass().getClassLoader(),new Class<?>[]{cn.lineai.data.repository.ToolSettingsStore.class},(proxy,method,args)->{
                        if(method.getName().equals("canExecuteTool"))return PermissionResult.allowed();
                        if(method.getReturnType()==boolean.class)return false;
                        return null;
                    });
            executor=new ToolExecutor(registry,settings,null,null,null,null,null);
            assertTrue(executor.execute(new ToolCall("result_failure","failure_test","{}"),null).isError());
            assertTrue(executor.execute(new ToolCall("exception_failure","failure_test","{\"throw\":true}"),null).isError());
            assertEquals(3,logs.list().size());
        } finally {Thread.setDefaultUncaughtExceptionHandler(previous);}
    }
}
