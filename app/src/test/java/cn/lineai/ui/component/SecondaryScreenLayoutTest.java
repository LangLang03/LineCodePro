package cn.lineai.ui.component;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import cn.lineai.model.*;
import cn.lineai.ui.theme.LineTheme;
import java.io.*;
import java.lang.reflect.*;
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
public class SecondaryScreenLayoutTest {
    private Activity activity;
    private final List<String> events = new ArrayList<>();
    @Before public void setup() {
        LineTheme.apply(ThemePalette.forMode("light"));
        activity = Robolectric.buildActivity(Activity.class).setup().get();
    }
    @After public void cleanup() { activity.finish(); }
    private Object value(Class<?> type) throws Exception {
        if(type==void.class) return null;
        if(type==boolean.class) return false;
        if(type==int.class) return 0;
        if(type==long.class) return 0L;
        if(type==float.class) return 0f;
        if(type==String.class) return "";
        if(type==Context.class) return activity;
        if(type==List.class) return Collections.emptyList();
        if(type==Map.class) return Collections.emptyMap();
        if(type.isArray()) return Array.newInstance(type.getComponentType(),0);
        if(type==ExtensionKindUiModel.class) return new ExtensionKindUiModel("agent","智能体",0,"添加智能体","创建智能体","定义任务与工具",1,true,"还没有智能体",Collections.emptyList());
        if(type.isEnum()) return type.getEnumConstants()[0];
        if(type.isInterface()) return Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->{
            events.add(method.getName()); return value(method.getReturnType());
        });
        if(type.getName().startsWith("cn.lineai.model.")) {
            for(Method method:type.getMethods()) if(Modifier.isStatic(method.getModifiers()) && method.getParameterCount()==0 && method.getReturnType()==type) return method.invoke(null);
            Constructor<?> constructor=Arrays.stream(type.getConstructors()).min(Comparator.comparingInt(Constructor::getParameterCount)).orElse(null);
            if(constructor!=null) return create(constructor);
        }
        return null;
    }
    private Object create(Constructor<?> constructor) throws Exception {
        Object[] args=new Object[constructor.getParameterCount()];Class<?>[] types=constructor.getParameterTypes();
        for(int i=0;i<args.length;i++) args[i]=value(types[i]);
        return constructor.newInstance(args);
    }
    @SuppressWarnings("unchecked") private <T> T listener(Class<T> type) throws Exception {return (T)value(type);}
    private View screen(String name) throws Exception {
        if (name.equals("SkillHubWeb")) return new SkillHubWebScreenView(activity, "stars", () -> {});
        Class<?> type=Class.forName("cn.lineai.ui.component."+name+"ScreenView");
        return (View)create(Arrays.stream(type.getConstructors()).min(Comparator.comparingInt(Constructor::getParameterCount)).get());
    }
    private void layout(View view,int width,int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));
        view.layout(0,0,width,height);
    }
    @Test public void everyNativeDestinationBuildsAtPhoneWidth() throws Exception {
        String[] names={"About","AdvancedFeatures","AgentExtensionEdit","DataSettings","ErrorLogs","ExtensionDetail","Extensions","InAppBrowser","InputSettings","KeepAliveSettings","LLMSettings","Licenses","MCPSettings","McpExtensionEdit","MemorySettings","ModelAddOptions","ModelAdd","ModelList","OutputSettings","PhoneControl","PromptTemplates","SecuritySettings","Settings","ShellCommand","SimpleSettings","SkillHubCenter","SkillHubLogin","SkillHubPublish","SkillHubWeb","SkillStoreDetail","SkillStore","SshSettings","StorageManagement","TerminalProviderDetail","TermuxIntegration","ThemeSettings","ToolCallPreview","ToolSettings","Tutorial"};
        List<String> failures=new ArrayList<>();
        for(String name:names) {
            try {
                View view=screen(name);layout(view,320,720);
                assertTrue(view.getMeasuredHeight()>0);
                if(Arrays.asList("Settings","Extensions","ModelAdd","SshSettings","MCPSettings","OutputSettings","ThemeSettings","SkillStore","TerminalProviderDetail","MemorySettings","Tutorial").contains(name)) {
                    layout(view,390,844);screenshot(view,"light-390-"+name);
                }
            } catch(Throwable error) {
                Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause(); failures.add(name+": "+cause);
            }
        }
        assertTrue(failures.toString(),failures.isEmpty());
    }
    @Test public void settingsNavigationAndBackStillDispatch() throws Exception {
        SettingsScreenView view=new SettingsScreenView(activity,listener(SettingsScreenView.Listener.class));
        layout(view,390,844);
        TextView models=text(view,activity.getString(cn.lineai.R.string.settings_row_models_title));
        clickable(models).performClick();assertTrue(events.contains("onItem"));
        ScreenHeaderView header=find(view,ScreenHeaderView.class);
        header.getChildAt(0).performClick();assertTrue(events.contains("onBack"));
    }
    @Test public void classicSshFormRetainsDraftAndSaveCallback() throws Exception {
        SshSettingsScreenView view=new SshSettingsScreenView(activity,listener(SshSettingsScreenView.Listener.class));
        assertNull(find(view,DisclosureSectionView.class));
        FormTextFieldView keyField=(FormTextFieldView)text(view,activity.getString(cn.lineai.R.string.screen_ssh_field_private_key)).getParent();
        EditText key=keyField.getInput(); key.setText("draft-private-key");
        assertEquals("draft-private-key",key.getText().toString());
        clickable(text(view,activity.getString(cn.lineai.R.string.screen_ssh_save))).performClick();
        assertTrue(events.contains("onSaveConfig"));
    }
    @Test public void darkPagesAndTabletReadingWidth() throws Exception {
        LineTheme.apply(ThemePalette.forMode("dark"));
        View view=screen("Extensions");layout(view,1100,800);assertEquals(0,view.getPaddingLeft());
        layout(view,390,844);screenshot(view,"dark-390-Extensions");
        view=screen("Settings");layout(view,390,844);screenshot(view,"dark-390-Settings");
    }
    @Test public void largeFontFormsAndSheetsRemainReachable() throws Exception {
        android.content.res.Configuration config=new android.content.res.Configuration(activity.getResources().getConfiguration());
        config.fontScale=1.6f;activity.getResources().updateConfiguration(config,activity.getResources().getDisplayMetrics());
        View view=screen("ModelAdd");layout(view,320,720);screenshot(view,"large-font-320-ModelAdd");
        BottomSheetView sheet=new BottomSheetView(activity);
        List<SheetOption> options=new ArrayList<>();
        for(int i=0;i<25;i++)options.add(new SheetOption("option"+i,"选项 "+i,"说明",false));
        sheet.show("选择工作区",options);layout(sheet,320,720);
        InsetSheetLayout panel=find(sheet,InsetSheetLayout.class);assertTrue(panel.getHeight()<=656);
        assertTrue(find(panel,android.widget.ScrollView.class).getHeight()>0);
        panel.animate().cancel();panel.setTranslationY(0);sheet.getChildAt(0).animate().cancel();sheet.getChildAt(0).setAlpha(1);
        screenshot(sheet,"large-font-320-Sheet");
    }
    @Test public void classicProtocolTabsSwitchWithoutLosingNameDraft() throws Exception {
        ModelAddScreenView view=new ModelAddScreenView(activity,null,false,listener(ModelAddScreenView.Listener.class));
        activity.setContentView(view);layout(view,390,844);
        EditText name=find(view,EditText.class);name.setText("My model");
        clickable(text(view,"Anthropic")).performClick();
        assertEquals("My model",name.getText().toString());
    }
    @Test public void modelRowsDispatchSelectionAndKeepManagement() throws Exception {
        ModelConfig model=ModelConfig.builder("one","工作模型",ModelProtocolType.OPENAI_COMPATIBLE,"Custom","https://example.invalid/v1","","example-model").build();
        ModelListScreenView view=new ModelListScreenView(activity,Collections.singletonList(model),"one",listener(ModelListScreenView.Listener.class));
        activity.setContentView(view);layout(view,390,844);
        View row=clickable(text(view,"工作模型"));row.performClick();assertTrue(events.contains("onSelectModel"));
        row.performLongClick();android.app.Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing());
        clickable(text(dialog.getWindow().getDecorView(),activity.getString(cn.lineai.R.string.screen_models_action_modify))).performClick();
        assertTrue(events.contains("onEditModel"));
    }
    @Test public void drawerAndDirectoryConfirmKeepCallbacksAndStayInPanel() throws Exception {
        DrawerView drawer=new DrawerView(activity);drawer.setListener(listener(DrawerView.Listener.class));
        drawer.render(Collections.singletonList(new ConversationUiModel("one","重连修复",1)),"one","LineCode","/workspace",true,null);
        drawer.open();layout(drawer,390,844);clickable(text(drawer,"重连修复")).performClick();assertTrue(events.contains("onConversationSelected"));
        DirectoryPickerSheetView picker=new DirectoryPickerSheetView(activity);picker.setListener(listener(DirectoryPickerSheetView.Listener.class));
        picker.show("工作目录","/workspace",new FileTreeNode("workspace","/workspace",true,true,Collections.emptyList()),"/workspace",false,"");
        layout(picker,1100,800);InsetSheetLayout panel=find(picker,InsetSheetLayout.class);assertEquals(560,panel.getWidth());
        java.lang.reflect.Field field=DirectoryPickerSheetView.class.getDeclaredField("confirmButton");field.setAccessible(true);
        View confirm=(View)field.get(picker);assertTrue(confirm.getParent() instanceof View);assertNotSame(picker,confirm.getParent());
        confirm.performClick();assertTrue(events.contains("onDirectoryPickerConfirmed"));
        layout(picker,320,480);assertTrue(panel.getHeight()<=416);
    }
    @Test public void genericKeepsDisclosureAndAgentRestoresExpandedCard() throws Exception {
        Map<String,Boolean> state=new HashMap<>();
        cn.lineai.model.tool.ToolCall call=new cn.lineai.model.tool.ToolCall("generic","mcp_example","{\"query\":\"layout\"}");
        cn.lineai.model.tool.ToolResult result=cn.lineai.model.tool.ToolResult.of("generic","mcp_example","RESULT CONTENT",false);
        cn.lineai.tool.ui.ToolCallGenericView generic=new cn.lineai.tool.ui.ToolCallGenericView(activity,"MCP");
        generic.setExpansionState(state,"generic");generic.bind(call,result);assertNull(text(generic,"RESULT CONTENT"));
        View genericHeader=generic.getChildAt(0);assertNull(genericHeader.getBackground());
        assertEquals(48,genericHeader.getMinimumHeight());
        genericHeader.performClick();assertNotNull(text(generic,"RESULT CONTENT"));
        cn.lineai.ui.theme.BoundedScrollView genericDetail=find(generic,cn.lineai.ui.theme.BoundedScrollView.class);
        assertNotNull(genericDetail);assertNotNull(genericDetail.getBackground());
        activity.setContentView(generic);layout(generic,390,240);screenshot(generic,"native-light-390-mcp-card");
        cn.lineai.tool.ui.ToolCallGenericView localGeneric=new cn.lineai.tool.ui.ToolCallGenericView(activity,"MCP");
        localGeneric.bind(call,result);localGeneric.getChildAt(0).performClick();assertNotNull(text(localGeneric,"RESULT CONTENT"));
        cn.lineai.tool.ui.ToolCallGenericView rebound=new cn.lineai.tool.ui.ToolCallGenericView(activity,"MCP");
        rebound.setExpansionState(state,"generic");rebound.bind(call,result);assertNotNull(text(rebound,"RESULT CONTENT"));
        cn.lineai.tool.ui.ToolCallAgentView agent=new cn.lineai.tool.ui.ToolCallAgentView(activity);agent.bind(call,result);
        assertTrue(agent.getChildCount()>1);assertNotNull(agent.getBackground());
        agent.getChildAt(0).performClick();assertEquals(1,agent.getChildCount());
        agent.bind(call,result);assertEquals(1,agent.getChildCount());
        agent.getChildAt(0).performClick();assertTrue(agent.getChildCount()>1);
    }

    @Test public void workspaceSelectionFillsTheSheetWidth() throws Exception {
        BottomSheetView sheet = new BottomSheetView(activity);
        sheet.show("选择工作区", Arrays.asList(new SheetOption("one", "LineCode", "/workspace", true),
                new SheetOption("two", "其他工作区", "/other", false)));
        layout(sheet, 390, 844);
        InsetSheetLayout panel = find(sheet, InsetSheetLayout.class);
        View selected = clickable(text(sheet, "LineCode"));
        assertEquals(panel.getWidth(), selected.getWidth());
        assertEquals(0, selected.getLeft());
        panel.animate().cancel(); panel.setTranslationY(0);
        sheet.getChildAt(0).animate().cancel(); sheet.getChildAt(0).setAlpha(1);
        screenshot(sheet, "workspace-full-selection");
    }

    @Test public void modelSheetsWrapShortListsAndActions() throws Exception {
        ModelPickerDialog.show(activity, Collections.singletonList("model-one"), "model-one", (id, custom) -> {});
        android.app.Dialog dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        android.widget.ScrollView picker = find(dialog.getWindow().getDecorView(), android.widget.ScrollView.class);
        picker.measure(View.MeasureSpec.makeMeasureSpec(358, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.AT_MOST));
        assertTrue(picker.getMeasuredHeight() < 180);
        dialog.dismiss();
        ModelConfig model = ModelConfig.builder("one", "工作模型", ModelProtocolType.OPENAI_COMPATIBLE,
                "Custom", "https://example.invalid/v1", "", "example-model").build();
        ModelListScreenView list = new ModelListScreenView(activity, Collections.singletonList(model), "one", listener(ModelListScreenView.Listener.class));
        activity.setContentView(list); layout(list, 390, 844);
        clickable(text(list, "工作模型")).performLongClick();
        dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog();
        android.widget.ScrollView scroll = find(dialog.getWindow().getDecorView(), android.widget.ScrollView.class);
        scroll.measure(View.MeasureSpec.makeMeasureSpec(358, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(700, View.MeasureSpec.AT_MOST));
        scroll.layout(0, 0, 358, scroll.getMeasuredHeight());
        ViewGroup panel = (ViewGroup) scroll.getChildAt(0);
        View last = panel.getChildAt(panel.getChildCount() - 1);
        assertEquals(12, panel.getHeight() - last.getBottom());
        assertEquals(panel.getHeight(), scroll.getHeight());
        assertTrue(scroll.getHeight() < 300);
        screenshot(scroll, "model-long-press-sheet");
        dialog.dismiss();
    }

    @Test public void storageHeaderDoesNotConsumeTheContentArea() throws Exception {
        StorageManagementScreenView view = (StorageManagementScreenView) screen("StorageManagement");
        layout(view, 390, 844);
        ScreenHeaderView header = find(view, ScreenHeaderView.class);
        View refresh = find(view, RefreshCwButtonView.class);
        View back = header.getChildAt(0);
        assertEquals(back.getWidth(), refresh.getWidth());
        assertEquals(back.getHeight(), refresh.getHeight());
        assertEquals(ScreenHeaderView.ACTION_SIZE_DP, refresh.getWidth());
        assertTrue(header.getHeight() < 130);
        assertTrue(view.getScrollView().getHeight() > 600);
        assertTrue(text(view, activity.getString(cn.lineai.R.string.screen_storage_row_home)).getHeight() > 0);
        screenshot(view, "storage-restored");
    }

    @Test public void extensionsUseRestoredCardSpacingAndExecutionCardsHideInternalIds() throws Exception {
        View view = screen("Extensions"); layout(view, 390, 844);
        ViewGroup list = (ViewGroup) find(view, android.widget.ScrollView.class).getChildAt(0);
        for (int i = 1; i < list.getChildCount(); i++)
            assertTrue(list.getChildAt(i).getTop() - list.getChildAt(i - 1).getBottom() >= 8);
        screenshot(view, "extensions-spaced");
        McpToolConfig config = new McpToolConfig("files", "文件操作", "读取和编辑工作区", true,
                new String[]{"internal_tool_id"});
        MCPSettingsScreenView tools = new MCPSettingsScreenView(activity,
                new McpSettingsState("local", Collections.singletonList(config)), listener(MCPSettingsScreenView.Listener.class));
        layout(tools, 390, 844);
        assertNull(find(tools, DisclosureSectionView.class));
        assertNull(text(tools, "internal_tool_id"));
        assertNotNull(find(tools, android.widget.Switch.class));
        screenshot(tools, "execution-without-ids");
    }

    @Test public void skillStoreUsesClassicCenteredHeaderAndGutters() throws Exception {
        SkillStoreScreenView view = (SkillStoreScreenView) screen("SkillStore");
        layout(view, 390, 844);
        ScreenHeaderView header = find(view, ScreenHeaderView.class);
        View back = header.getChildAt(0);
        TextView title = text(header, activity.getString(cn.lineai.R.string.skillhub_title_store));
        assertSame(header, back.getParent());
        assertSame(header, title.getParent());
        assertEquals(back.getRight(), title.getLeft());
        assertEquals(16, view.getContent().getPaddingLeft());
        assertEquals(16, view.getContent().getPaddingRight());
        screenshot(view, "skills-store-compact-header");
    }

    private static View clickable(View view){while(!view.isClickable()&&view.getParent() instanceof View)view=(View)view.getParent();return view;}
    private static TextView text(View root,String value) {if(root instanceof TextView && value.contentEquals(((TextView)root).getText()))return (TextView)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView v=text(((ViewGroup)root).getChildAt(i),value);if(v!=null)return v;}return null;}
    private static <T> T find(View root,Class<T> type) {if(type.isInstance(root))return type.cast(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){T v=find(((ViewGroup)root).getChildAt(i),type);if(v!=null)return v;}return null;}
    private void screenshot(View view,String name) throws Exception {
        File file=new File("build/reports/ui-previews/"+name+".png");file.getParentFile().mkdirs();
        Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
        try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
}
