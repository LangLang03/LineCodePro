package cn.lineai.ui;
import cn.lineai.ui.theme.LineTheme;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.model.ChatUiState;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.FileTreeNode;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.SheetOption;
import cn.lineai.mvp.MainContract;
import cn.lineai.mvp.MainUiController;
import cn.lineai.mvp.QuoteController;
import cn.lineai.mvp.ShareController;
import cn.lineai.security.UrlPolicy;
import cn.lineai.share.ExportFormatResolver;
import cn.lineai.share.ShareHelper;
import cn.lineai.ui.component.AboutScreenView;
import cn.lineai.ui.component.AttachmentPickerSheetView;
import cn.lineai.ui.component.BackNavigation;
import cn.lineai.ui.component.BottomSheetView;
import cn.lineai.ui.component.ChatMessageListView;
import cn.lineai.ui.component.ComposerView;
import cn.lineai.ui.component.DataSettingsScreenView;
import cn.lineai.ui.component.DialogBuilder;
import cn.lineai.ui.component.DialogManager;
import cn.lineai.ui.component.DirectoryPickerSheetView;
import cn.lineai.ui.component.DrawerView;
import cn.lineai.ui.component.ExtensionDetailScreenView;
import cn.lineai.ui.component.ExtensionsScreenView;
import cn.lineai.ui.component.FileActionRow;
import cn.lineai.ui.component.HeaderView;
import cn.lineai.ui.component.InAppBrowserScreenView;
import cn.lineai.ui.component.InputSettingsScreenView;
import cn.lineai.ui.component.KeepAliveSettingsScreenView;
import cn.lineai.ui.component.AgentExtensionEditScreenView;
import cn.lineai.ui.component.LLMSettingsScreenView;
import cn.lineai.ui.component.LicensesScreenView;
import cn.lineai.ui.component.MCPSettingsScreenView;
import cn.lineai.ui.component.McpExtensionEditScreenView;
import cn.lineai.ui.component.MemorySettingsScreenView;
import cn.lineai.ui.component.MessageActionListener;
import cn.lineai.ui.component.ModelAddOptionsScreenView;
import cn.lineai.ui.component.ModelAddScreenView;
import cn.lineai.ui.component.ModelListScreenView;
import cn.lineai.ui.component.MainChatViewLayoutBuilder;
import cn.lineai.ui.component.OutputSettingsScreenView;
import cn.lineai.ui.component.PromptTemplatesScreenView;
import cn.lineai.ui.component.OverlayManager;
import cn.lineai.ui.component.ScreenFactories;
import cn.lineai.ui.component.ScreenRegistry;
import cn.lineai.ui.component.SettingsScreenView;
import cn.lineai.ui.component.ShellCommandScreenView;
import cn.lineai.ui.component.SimpleScreenContent;
import cn.lineai.ui.component.SimpleSettingsScreenView;
import cn.lineai.ui.component.SshSettingsScreenView;
import cn.lineai.ui.component.StorageManagementScreenView;
import cn.lineai.ui.component.TerminalProviderDetailScreenView;
import cn.lineai.ui.component.TermuxIntegrationScreenView;
import cn.lineai.ui.component.TextSelectionDialog;
import cn.lineai.ui.component.ThemeSettingsScreenView;
import cn.lineai.ui.component.ToolSettingsScreenView;
import cn.lineai.ui.component.TutorialScreenView;
import cn.lineai.ui.util.KeyboardController;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public final class MainChatView extends FrameLayout implements MainContract.View, BackNavigation.BackTarget {
    private static final long SCREEN_ENTER_MS = 280L;
    private static final long SCREEN_EXIT_MS = 220L;

    public interface WorkspaceHost {
        void openExternalProjectPicker();

        void openManageAllFilesPermissionSettings();

        void requestLegacyStoragePermissions();

        void recreateMainView(String screenId);

        void openDocumentPicker(String mimeType, String[] extensions, DocumentPickCallback callback);

        void createDocument(String mimeType, String displayName, DocumentCreateCallback callback);

        /**
         * 启动系统图片选择器。Android 13+ 使用 Photo Picker
         * ({@link android.provider.MediaStore#ACTION_PICK_IMAGES})，低版本回退到 SAF
         * ({@link android.content.Intent#ACTION_OPEN_DOCUMENT})。
         */
        void pickImage(DocumentPickCallback callback);
    }

    public interface DocumentPickCallback {
        void onDocumentPicked(String uri, String displayName);

        void onDocumentPickCancelled();
    }

    public interface DocumentCreateCallback {
        void onDocumentCreated(String uri, String displayName);

        void onDocumentCreateCancelled();
    }

    private final MainUiController presenter;
    private final DialogManager dialogManager = new DialogManager();
    private OverlayManager overlayManager;
    private final HeaderView headerView;
    private final LinearLayout contentView;
    private final ChatMessageListView messageListView;
    private final ComposerView composerView;
    private final DrawerView drawerView;
    private final BottomSheetView bottomSheetView;
    private final DirectoryPickerSheetView directoryPickerSheetView;
    private final AttachmentPickerSheetView attachmentPickerSheetView;
    private final FrameLayout screenHost;
    private ChatUiState lastState;
    private String shellCommandText = "";
    private String currentScreenId = "";
    private final ScreenRegistry screenRegistry = new ScreenRegistry();
    private final LinkedHashMap<String, View> screenCache = new LinkedHashMap<>();
    private int screenAnimationGeneration;
    private boolean screenClosing;
    private String attachmentPickerTitle = "";
    private String attachmentPickerMessage = "";
    private String attachmentPickerSource = InputAttachment.SOURCE_LOCAL;
    private boolean attachmentPickerLoading;
    private FileTreeNode attachmentPickerTree;
    private final ShareController shareController;
    private final QuoteController quoteController;

    public MainChatView(Context context, MainUiController presenter) {
        super(context);
        this.presenter = presenter;
        this.quoteController = new QuoteController();
        this.shareController = new ShareController(new ExportFormatResolver());
        setBackgroundColor(LineTheme.BG);

        MainChatViewLayoutBuilder.Result layout = MainChatViewLayoutBuilder.build(context);
        contentView = layout.contentView;
        screenHost = layout.screenHost;
        addView(contentView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(screenHost, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        headerView = new HeaderView(context);
        headerView.setListener(new HeaderView.Listener() {
            @Override
            public void onMenuClick() {
                MainChatView.this.presenter.onMenuClick();
            }

            @Override
            public void onProjectClick() {
                MainChatView.this.presenter.onProjectClick();
            }

            @Override
            public void onPermissionClick() {
                MainChatView.this.presenter.onPermissionClick();
            }

            @Override
            public void onNewConversationClick() {
                MainChatView.this.presenter.onNewConversation();
            }

            @Override
            public void onMoreClick() {
                MainChatView.this.presenter.onMoreClick();
            }
        });
        contentView.addView(headerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        messageListView = new ChatMessageListView(context);
        messageListView.setToolReviewListener(new cn.lineai.tool.ToolReviewListener() {
            @Override
            public void onToolReview(String toolCallId, String state, String diffId) {
                MainChatView.this.presenter.onToolReview(toolCallId, state, diffId);
            }

            @Override
            public void onViewShellCommand(String command) {
                shellCommandText = command == null ? "" : command;
                MainChatView.this.presenter.onSettingsItemSelected("shellCommand");
            }
        });
        messageListView.setMarkdownLinkHandler(url -> MainChatView.this.presenter.onOpenUrl(url));
        messageListView.setMessageActionListener(new MessageActionListener() {
            @Override
            public void onCopyMessage(ChatMessage message) {
                copyMessage(message);
            }

            @Override
            public void onRecallMessage(ChatMessage message) {
                if (message != null) {
                    MainChatView.this.presenter.onRecallMessage(message.getId());
                }
            }

            @Override
            public void onQuoteMessage(ChatMessage message) {
                quoteController.setQuote(message.getContent());
            }

            @Override
            public void onShareMessage(ChatMessage message) {
                shareController.showFormatPicker(getContext(), Collections.singletonList(message));
            }

            @Override
            public void onSelectText(ChatMessage message) {
                TextSelectionDialog.show(getContext(), message.getContent());
            }

            @Override
            public void onMultiSelectToggle() {
                messageListView.enterMultiSelectMode();
            }
        });
        contentView.addView(messageListView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        messageListView.setMultiSelectListener(new ChatMessageListView.MultiSelectListener() {
            @Override
            public void onExportRequested(List<ChatMessage> selectedMessages) {
                shareController.showFormatPicker(getContext(), selectedMessages);
            }

            @Override
            public void onMultiSelectExit() {
                messageListView.exitMultiSelectMode();
            }
        });
        messageListView.setEmptyStateListener(new ChatMessageListView.EmptyStateListener() {
            @Override
            public void onAddModel() {
                MainChatView.this.presenter.showModelManagement();
            }

            @Override
            public void onOpenWorkspace() {
                MainChatView.this.presenter.onProjectClick();
            }
        });

        composerView = new ComposerView(context);
        composerView.setListener(new ComposerView.Listener() {
            @Override
            public void onSend(String text, List<InputAttachment> attachments) {
                String finalText = quoteController.composeWithQuote(text);
                MainChatView.this.presenter.onSendMessage(finalText, attachments);
            }

            @Override
            public void onSendWithImage(String text, List<InputAttachment> attachments,
                                        String imageBase64, String imageMimeType, String imageName) {
                String finalText = quoteController.composeWithQuote(text);
                MainChatView.this.presenter.onSendMessageWithImage(
                        finalText, attachments, imageBase64, imageMimeType, imageName);
            }

            @Override
            public void onAttachClick() {
                MainChatView.this.presenter.onAttachmentPickerRequested();
            }

            @Override
            public void onImagePickerClick() {
                MainChatView.this.presenter.onImagePickerRequested();
            }

            @Override
            public void onModeChanged(String mode) {
                MainChatView.this.presenter.onChatModeChanged(mode);
            }

            @Override
            public void onStop() {
                MainChatView.this.presenter.onStopGeneration();
            }

            @Override
            public void onModelQuickSwitch(String modelId) {
                MainChatView.this.presenter.onModelQuickSwitch(modelId);
            }

            @Override
            public void onModelManageClick() {
                MainChatView.this.presenter.showModelManagement();
            }

            @Override
            public void onAiReasoningEffortChanged(String effort) {
                MainChatView.this.presenter.onAiReasoningEffortChanged(effort);
            }

            @Override
            public int onQueryModelCount(String baseUrl) throws Exception {
                return MainChatView.this.presenter.queryModelCount(baseUrl);
            }
        });
        quoteController.setPreview(composerView);
        composerView.setQuoteDismissListener(() -> quoteController.clearQuote());
        contentView.addView(composerView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        drawerView = new DrawerView(context);
        drawerView.setListener(new DrawerView.Listener() {
            @Override
            public void onCloseDrawer() {
            }

            @Override
            public void onNewConversation() {
                MainChatView.this.presenter.onNewConversation();
            }

            @Override
            public void onConversationSelected(String id) {
                MainChatView.this.presenter.onConversationSelected(id);
            }

            @Override
            public void onConversationDeleted(String id) {
                MainChatView.this.presenter.onConversationDeleted(id);
            }

            @Override
            public void onCurrentProjectRemoveRequested() {
                MainChatView.this.presenter.onCurrentProjectRemoveRequested();
            }

            @Override
            public void onFileNodeSelected(String path, boolean directory) {
                MainChatView.this.presenter.onFileNodeSelected(path, directory);
            }

            @Override
            public void onFileNodeLongPressed(String path, String name, boolean directory, boolean root) {
                MainChatView.this.presenter.onFileNodeLongPressed(path, name, directory, root);
            }

            @Override
            public void onFileTreeActivated() {
                MainChatView.this.presenter.onFileTreeActivated();
            }

            @Override
            public void onFileTreeRefresh() {
                MainChatView.this.presenter.onFileTreeRefresh();
            }
        });
        addView(drawerView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        bottomSheetView = new BottomSheetView(context);
        bottomSheetView.setListener(new BottomSheetView.Listener() {
            @Override
            public void onSheetDismissed() {
            }

            @Override
            public void onSheetOptionSelected(String id) {
                MainChatView.this.presenter.onSheetOptionSelected(id);
            }
        });
        addView(bottomSheetView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        directoryPickerSheetView = new DirectoryPickerSheetView(context);
        directoryPickerSheetView.setListener(new DirectoryPickerSheetView.Listener() {
            @Override
            public void onDirectoryPickerClosed() {
                MainChatView.this.presenter.onDirectoryPickerCancelled();
            }

            @Override
            public void onDirectoryPicked(String path) {
                MainChatView.this.presenter.onDirectoryPickerNodeSelected(path);
            }

            @Override
            public void onDirectoryPickerConfirmed() {
                MainChatView.this.presenter.onDirectoryPickerConfirmed();
            }
        });
        addView(directoryPickerSheetView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        attachmentPickerSheetView = new AttachmentPickerSheetView(context);
        attachmentPickerSheetView.setListener(new AttachmentPickerSheetView.Listener() {
            @Override
            public void onAttachmentPickerClosed() {
                MainChatView.this.presenter.onAttachmentPickerCancelled();
            }

            @Override
            public void onAttachmentNodeSelected(String path, boolean directory) {
                MainChatView.this.presenter.onAttachmentPickerNodeSelected(path, directory);
            }

            @Override
            public void onAttachmentFileToggled(String path, String name, String source) {
                composerView.toggleAttachment(new InputAttachment(name, path, source));
                renderAttachmentPicker();
            }
        });
        addView(attachmentPickerSheetView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        overlayManager = new OverlayManager(drawerView, bottomSheetView, directoryPickerSheetView, attachmentPickerSheetView);

        MainChatViewLayoutBuilder.installSystemBarInsetsHandling(this, contentView, screenHost);
        registerScreenFactories();
    }

    private void registerScreenFactories() {
        screenRegistry.register(new ScreenFactories.SettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.LlmSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.PromptTemplatesScreenFactory());
        screenRegistry.register(new ScreenFactories.InputSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.ToolSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.McpSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.OutputSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.ToolCallPreviewScreenFactory());
        screenRegistry.register(new ScreenFactories.SecuritySettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.ThemeSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.DataSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.StorageManagementScreenFactory());
        screenRegistry.register(new ScreenFactories.MemorySettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.ErrorLogsScreenFactory());
        screenRegistry.register(new ScreenFactories.KeepAliveSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.AdvancedFeaturesScreenFactory());
        screenRegistry.register(new ScreenFactories.PhoneControlScreenFactory());
        screenRegistry.register(new ScreenFactories.SshSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.TermuxIntegrationScreenFactory());
        screenRegistry.register(new ScreenFactories.AboutScreenFactory());
        screenRegistry.register(new ScreenFactories.LicensesScreenFactory());
        screenRegistry.register(new ScreenFactories.TutorialScreenFactory());
        screenRegistry.register(new ScreenFactories.TutorialFromSettingsScreenFactory());
        screenRegistry.register(new ScreenFactories.ModelListScreenFactory());
        screenRegistry.register(new ScreenFactories.ImageUnderstandingModelScreenFactory());
        screenRegistry.register(new ScreenFactories.ImageGenerationModelScreenFactory());
        screenRegistry.register(new ScreenFactories.ModelAddOptionsScreenFactory());
        screenRegistry.register(new ScreenFactories.ModelAddScreenFactory());
        screenRegistry.register(new ScreenFactories.ModelAddLocalScreenFactory());
        screenRegistry.register(new ScreenFactories.ModelAddPresetScreenFactory());
        screenRegistry.register(new ScreenFactories.ModelEditScreenFactory());
        screenRegistry.register(new ScreenFactories.ExtensionsScreenFactory());
        screenRegistry.register(new ScreenFactories.TerminalProviderScreenFactory());
        screenRegistry.register(new ScreenFactories.AgentEditScreenFactory());
        screenRegistry.register(new ScreenFactories.McpEditScreenFactory());
        screenRegistry.register(new ScreenFactories.ExtensionDetailScreenFactory());
        screenRegistry.register(new ScreenFactories.SkillStoreScreenFactory());
        screenRegistry.register(new ScreenFactories.SkillHubLoginScreenFactory());
        screenRegistry.register(new ScreenFactories.SkillHubCenterScreenFactory());
        screenRegistry.register(new ScreenFactories.SkillHubWebScreenFactory());
        screenRegistry.register(new ScreenFactories.SkillHubPublishScreenFactory());
        screenRegistry.register(new ScreenFactories.SkillStoreDetailScreenFactory());
        screenRegistry.register(new ScreenFactories.BrowserScreenFactory());
        screenRegistry.register(new ScreenFactories.BrowserPrefixScreenFactory());
        screenRegistry.register(new ScreenFactories.ShellCommandScreenFactory());
    }

    @Override
    public void render(ChatUiState state) {
        lastState = state;
        headerView.render(state);
        messageListView.render(state);
        composerView.render(state);
        if (drawerView.getVisibility() == VISIBLE) {
            renderDrawer(state);
        }
    }

    @Override
    public void setComposerDraft(String text) {
        showChatScreen();
        composerView.setDraft(text);
    }

    @Override
    public void setComposerDraft(String text, List<InputAttachment> attachments) {
        showChatScreen();
        composerView.setDraft(text, attachments);
    }

    @Override
    public void showDrawer() {
        KeyboardController.clearFocusAndHide(this);
        overlayManager.closeAllExcept(drawerView);
        renderDrawer(lastState);
        drawerView.open();
    }

    @Override
    public void showSheet(String title, List<SheetOption> options) {
        KeyboardController.clearFocusAndHide(this);
        overlayManager.closeAllExcept(bottomSheetView);
        bottomSheetView.show(title, options);
    }

    @Override
    public void showFileActionDialog(String title, String subtitle, List<SheetOption> options) {
        KeyboardController.clearFocusAndHide(this);
        overlayManager.closeAllExcept(drawerView);

        Dialog dialog = DialogBuilder.create(getContext());
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LineTheme.rounded(getContext(), LineTheme.SURFACE_ELEVATED, 16));
        LineTheme.padding(panel, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);

        TextView titleView = LineTheme.textMedium(getContext(),
                title == null || title.length() == 0 ? getContext().getString(R.string.dialog_file_action_title) : title,
                LineTheme.FONT_LG,
                LineTheme.TEXT);
        panel.addView(titleView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (subtitle != null && subtitle.length() > 0) {
            TextView subtitleView = LineTheme.text(getContext(), subtitle, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            subtitleView.setSingleLine(false);
            LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            subtitleParams.topMargin = LineTheme.dp(getContext(), LineTheme.XS);
            panel.addView(subtitleView, subtitleParams);
        }

        View divider = new View(getContext());
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1);
        dividerParams.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        dividerParams.bottomMargin = LineTheme.dp(getContext(), LineTheme.XS);
        panel.addView(divider, dividerParams);

        if (options != null) {
            for (SheetOption option : options) {
                panel.addView(FileActionRow.create(getContext(), dialog, option, presenter::onSheetOptionSelected), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
        }

        DialogBuilder.showInset(dialog, panel);
    }

    @Override
    public void showInputDialog(String title, String message, String initialValue, String actionId) {
        KeyboardController.clearFocusAndHide(this);
        overlayManager.closeAll();
        final String capturedActionId = actionId;
        dialogManager.showInput(getContext(), title, message, null, initialValue,
                value -> presenter.onDialogInputSubmitted(capturedActionId, value));
    }

    @Override
    public void showConfirmationDialog(String title, String message, String confirmLabel, boolean danger, String actionId) {
        KeyboardController.clearFocusAndHide(this);
        overlayManager.closeAll();
        final String capturedActionId = actionId;
        dialogManager.showConfirm(getContext(), title, message, confirmLabel, danger,
                () -> presenter.onDialogConfirmed(capturedActionId),
                null);
    }

    @Override
    public void showDirectoryPicker(String title, String subtitle, FileTreeNode tree, String selectedPath, boolean loading, String message) {
        KeyboardController.clearFocusAndHide(this);
        overlayManager.closeAllExcept(directoryPickerSheetView);
        directoryPickerSheetView.show(title, subtitle, tree, selectedPath, loading, message);
    }

    @Override
    public void showAttachmentPicker(String title, FileTreeNode tree, boolean loading, String message, String source) {
        KeyboardController.clearFocusAndHide(this);
        overlayManager.closeAllExcept(attachmentPickerSheetView);
        attachmentPickerTitle = title == null ? "" : title;
        attachmentPickerTree = tree;
        attachmentPickerLoading = loading;
        attachmentPickerMessage = message == null ? "" : message;
        attachmentPickerSource = InputAttachment.SOURCE_SSH.equals(source)
                ? InputAttachment.SOURCE_SSH
                : InputAttachment.SOURCE_LOCAL;
        renderAttachmentPicker();
    }

    @Override
    public void hideOverlays() {
        KeyboardController.clearFocusAndHide(this);
        overlayManager.closeAll();
        composerView.dismissSlashPopup();
    }

    @Override
    public void hideDirectoryPicker() {
        directoryPickerSheetView.close();
    }

    @Override
    public void hideAttachmentPicker() {
        attachmentPickerSheetView.close();
    }

    @Override
    public void exportCurrentChat() {
        if (lastState == null || lastState.getMessages().isEmpty()) {
            Toast.makeText(getContext(), R.string.toast_chat_empty_export, Toast.LENGTH_SHORT).show();
            return;
        }
        shareController.showFormatPicker(getContext(), lastState.getMessages());
    }

    @Override
    public void enterMessageSelectMode() {
        exportCurrentChat();
    }

    @Override
    public void showScreen(String screenId) {
        showScreen(screenId, true);
    }

    public void showScreen(String screenId, boolean forward) {
        showScreen(screenId, forward, true);
    }

    public void showScreen(String screenId, boolean forward, boolean animate) {
        int animationGeneration = ++screenAnimationGeneration;
        screenClosing = false;
        String previousScreenId = currentScreenId;
        currentScreenId = screenId == null ? "" : screenId;
        KeyboardController.clearFocusAndHide(screenHost);
        KeyboardController.clearFocusAndHide(this);
        overlayManager.closeAll();
        screenHost.animate().cancel();
        View existing = previousScreenId.length() > 0 ? screenCache.get(previousScreenId) : null;
        if (existing == null || existing.getParent() != screenHost) {
            existing = screenHost.getChildCount() > 0 ? screenHost.getChildAt(screenHost.getChildCount() - 1) : null;
        }
        if (existing != null) {
            existing.animate().cancel();
            existing.setTranslationX(0f);
            existing.setAlpha(1f);
        }
        View cached = currentScreenId.length() > 0 ? screenCache.get(currentScreenId) : null;
        View nextView;
        if (cached != null && cached.getParent() == null) {
            nextView = cached;
        } else {
            nextView = buildScreen(currentScreenId);
            if (currentScreenId.length() > 0 && nextView != null) {
                screenCache.put(currentScreenId, nextView);
            }
        }
        if (nextView != null && nextView.getParent() == null) {
            screenHost.addView(nextView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        }
        screenHost.setVisibility(VISIBLE);
        screenHost.setAlpha(1f);
        screenHost.setTranslationX(0f);
        screenHost.requestFocus();
        screenHost.bringToFront();
        if (nextView == null) {
            resetScreenHostAnimationState();
            return;
        }
        if (!animate || currentScreenId.equals(previousScreenId)) {
            nextView.animate().cancel();
            nextView.setTranslationX(0f);
            nextView.setAlpha(1f);
            for (int i = screenHost.getChildCount() - 1; i >= 0; i--) {
                View child = screenHost.getChildAt(i);
                if (child == nextView) {
                    continue;
                }
                child.animate().cancel();
                child.setTranslationX(0f);
                child.setAlpha(1f);
                screenHost.removeViewAt(i);
            }
            resetScreenHostAnimationState();
            return;
        }
        float distance = screenTransitionDistance();
        float enterFrom = forward ? distance : -distance;
        float exitTo = forward ? -distance : distance;
        if (existing == null || existing == nextView) {
            nextView.setTranslationX(enterFrom);
            nextView.setAlpha(1f);
            nextView.animate()
                    .translationX(0f)
                    .setDuration(SCREEN_ENTER_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        if (animationGeneration == screenAnimationGeneration) {
                            resetScreenHostAnimationState();
                        }
                    })
                    .start();
            return;
        }
        nextView.setTranslationX(enterFrom);
        nextView.setAlpha(1f);
        existing.setTranslationX(0f);
        existing.setAlpha(1f);
        final View exitingView = existing;
        existing.animate()
                .translationX(exitTo)
                .setDuration(SCREEN_ENTER_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        nextView.animate()
                .translationX(0f)
                .setDuration(SCREEN_ENTER_MS)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (animationGeneration == screenAnimationGeneration) {
                        screenHost.removeView(exitingView);
                        exitingView.setTranslationX(0f);
                        resetScreenHostAnimationState();
                    }
                })
                .start();
    }

    @Override
    public void evictScreen(String screenId) {
        String safeId = screenId == null ? "" : screenId;
        if (safeId.length() == 0) {
            return;
        }
        View cached = screenCache.remove(safeId);
        if (cached != null && cached.getParent() instanceof ViewGroup) {
            ((ViewGroup) cached.getParent()).removeView(cached);
        }
    }

    public void invalidateScreen(String screenId) {
        String safeId = screenId == null ? "" : screenId;
        evictScreen(safeId);
        if (safeId.equals(currentScreenId)) {
            showScreen(safeId, true, false);
        }
    }

    @Override
    public void showChatScreen() {
        int animationGeneration = ++screenAnimationGeneration;
        currentScreenId = "";
        KeyboardController.clearFocusAndHide(screenHost);
        KeyboardController.clearFocusAndHide(this);
        screenHost.animate().cancel();
        if (screenHost.getVisibility() != VISIBLE) {
            screenClosing = false;
            screenHost.removeAllViews();
            screenHost.setVisibility(GONE);
            resetScreenHostAnimationState();
            return;
        }
        screenClosing = true;
        View existing = screenHost.getChildCount() > 0 ? screenHost.getChildAt(0) : null;
        if (existing == null) {
            screenHost.setVisibility(GONE);
            screenClosing = false;
            resetScreenHostAnimationState();
            return;
        }
        existing.animate().cancel();
        existing.animate()
                .translationX(screenTransitionDistance())
                .setDuration(SCREEN_EXIT_MS)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (animationGeneration != screenAnimationGeneration) {
                        return;
                    }
                    screenHost.removeAllViews();
                    screenHost.setVisibility(GONE);
                    screenClosing = false;
                    existing.setTranslationX(0f);
                    resetScreenHostAnimationState();
                })
                .start();
    }

    @Override
    protected void onDetachedFromWindow() {
        KeyboardController.clearFocusAndHide(this);
        super.onDetachedFromWindow();
    }

    @Override
    public void openExternalProjectPicker() {
        Context context = getContext();
        if (context instanceof WorkspaceHost) {
            ((WorkspaceHost) context).openExternalProjectPicker();
        }
    }

    @Override
    public void openLineCodeImportPicker() {
        Context context = getContext();
        if (!(context instanceof WorkspaceHost)) {
            return;
        }
        ((WorkspaceHost) context).openDocumentPicker("*/*", new String[] {"LineCode.linecode"}, new DocumentPickCallback() {
            @Override
            public void onDocumentPicked(String uri, String displayName) {
                presenter.onLineCodeImportPicked(uri, displayName);
            }

            @Override
            public void onDocumentPickCancelled() {
                presenter.onLineCodeImportCancelled();
            }
        });
    }

    @Override
    public void openImagePicker() {
        Context context = getContext();
        if (!(context instanceof WorkspaceHost)) {
            return;
        }
        ((WorkspaceHost) context).pickImage(new DocumentPickCallback() {
            @Override
            public void onDocumentPicked(String uri, String displayName) {
                handleImagePicked(uri, displayName);
            }

            @Override
            public void onDocumentPickCancelled() {
                // 用户取消，无需处理
            }
        });
    }

    private void handleImagePicked(final String uriString, final String displayName) {
        if (uriString == null || uriString.length() == 0) {
            return;
        }
        final Uri uri;
        try {
            uri = Uri.parse(uriString);
        } catch (Exception ignored) {
            return;
        }
        if (uri == null) {
            return;
        }
        final Context context = getContext();
        Toast.makeText(context, context.getString(R.string.composer_image_loading), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String mimeType = "";
            String base64 = "";
            try {
                android.content.ContentResolver resolver = context.getContentResolver();
                byte[] bytes = readAndCompressImage(resolver, uri);
                if (bytes != null && bytes.length > 0) {
                    base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                    // 统一使用 image/jpeg，因为压缩后输出的是 JPEG
                    mimeType = "image/jpeg";
                }
            } catch (Exception ignored) {
            }
            final String finalMimeType = mimeType;
            final String finalBase64 = base64;
            post(() -> {
                if (finalBase64.length() == 0) {
                    Toast.makeText(context, context.getString(R.string.composer_image_load_failed), Toast.LENGTH_SHORT).show();
                    return;
                }
                composerView.onImagePicked(uri, finalBase64, finalMimeType, displayName);
            });
        }, "linecode-image-load").start();
    }

    /**
     * 读取图片并按需压缩：长边超过 1568 时缩放至 1568；压缩后总字节超过 3.5MB 时进一步降低质量。
     * 这是 OpenAI / Anthropic 视觉模型对单图大小的常见上限的折中。
     */
    private byte[] readAndCompressImage(android.content.ContentResolver resolver, Uri uri) throws Exception {
        android.graphics.Bitmap bitmap = null;
        java.io.InputStream input = resolver.openInputStream(uri);
        if (input == null) {
            return null;
        }
        try {
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeStream(input, null, opts);
            int width = opts.outWidth;
            int height = opts.outHeight;
            int sampleSize = computeSampleSize(width, height, 1568);
            java.io.InputStream decodeInput = resolver.openInputStream(uri);
            if (decodeInput == null) {
                return null;
            }
            try {
                android.graphics.BitmapFactory.Options decodeOpts = new android.graphics.BitmapFactory.Options();
                decodeOpts.inSampleSize = sampleSize;
                bitmap = android.graphics.BitmapFactory.decodeStream(decodeInput, null, decodeOpts);
            } finally {
                decodeInput.close();
            }
        } finally {
            input.close();
        }
        if (bitmap == null) {
            return null;
        }
        try {
            int maxEdge = 1568;
            int bw = bitmap.getWidth();
            int bh = bitmap.getHeight();
            if (Math.max(bw, bh) > maxEdge) {
                float scale = (float) maxEdge / Math.max(bw, bh);
                int newW = Math.max(1, Math.round(bw * scale));
                int newH = Math.max(1, Math.round(bh * scale));
                android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, newW, newH, true);
                if (scaled != bitmap) {
                    bitmap.recycle();
                    bitmap = scaled;
                }
            }
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            int quality = 85;
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, output);
            byte[] result = output.toByteArray();
            // 超过 3.5 MB 时进一步压缩
            while (result.length > 3_500_000 && quality > 30) {
                quality -= 15;
                output.reset();
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, output);
                result = output.toByteArray();
            }
            return result;
        } finally {
            bitmap.recycle();
        }
    }

    private int computeSampleSize(int width, int height, int targetMaxEdge) {
        if (width <= 0 || height <= 0) {
            return 1;
        }
        int maxEdge = Math.max(width, height);
        if (maxEdge <= targetMaxEdge) {
            return 1;
        }
        int sample = 1;
        while ((maxEdge / sample) > targetMaxEdge * 2) {
            sample *= 2;
        }
        return sample;
    }

    @Override
    public void openLineCodeExportPicker(String fileName) {
        Context context = getContext();
        if (!(context instanceof WorkspaceHost)) {
            return;
        }
        ((WorkspaceHost) context).createDocument("application/zip", fileName, new DocumentCreateCallback() {
            @Override
            public void onDocumentCreated(String uri, String displayName) {
                presenter.onLineCodeExportTargetPicked(uri, displayName);
            }

            @Override
            public void onDocumentCreateCancelled() {
                presenter.onLineCodeExportCancelled();
            }
        });
    }

    @Override
    public void openManageAllFilesPermissionSettings() {
        Context context = getContext();
        if (context instanceof WorkspaceHost) {
            ((WorkspaceHost) context).openManageAllFilesPermissionSettings();
        }
    }

    @Override
    public void requestLegacyStoragePermissions() {
        Context context = getContext();
        if (context instanceof WorkspaceHost) {
            ((WorkspaceHost) context).requestLegacyStoragePermissions();
        }
    }

    @Override
    public void openExternalUrl(String url) {
        String safeUrl = UrlPolicy.normalizeHttpOrHttpsUrl(url);
        if (safeUrl.length() == 0) {
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(getContext(), getContext().getString(R.string.toast_open_link_failed, safeUrl), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyMessage(ChatMessage message) {
        if (message == null) {
            return;
        }
        String text = message.getContent();
        if ((text == null || text.length() == 0) && message.getReasoningContent().length() > 0) {
            text = message.getReasoningContent();
        }
        if (text == null || text.length() == 0) {
            return;
        }
        ShareHelper.copy(getContext(), text);
        Toast.makeText(getContext(), getContext().getString(R.string.toast_copied), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void recreateForTheme(String screenId) {
        Context context = getContext();
        if (context instanceof WorkspaceHost) {
            ((WorkspaceHost) context).recreateMainView(screenId);
        }
    }

    private void renderDrawer(ChatUiState state) {
        String projectLabel = state == null ? "" : state.getProjectLabel();
        String projectPath = state == null ? "" : state.getProjectPath();
        if (state == null) {
            projectLabel = "";
            projectPath = "";
        }
        drawerView.render(
                presenter.getConversationMetas(),
                presenter.getCurrentConversationId(),
                projectLabel.length() == 0 ? getContext().getString(R.string.header_project_default) : projectLabel,
                projectPath.length() == 0 ? "" : projectPath,
                presenter.canRemoveCurrentProject(),
                drawerView.isFilesTabActive() ? presenter.getFileTree() : null
        );
    }

    public boolean handleBackPressed() {
        return BackNavigation.handle(this);
    }

    @Override
    public boolean isScreenVisible() {
        return screenHost.getVisibility() == VISIBLE && !screenClosing;
    }

    @Override
    public boolean isDirectoryPickerVisible() {
        return directoryPickerSheetView.getVisibility() == VISIBLE;
    }

    @Override
    public boolean isAttachmentPickerVisible() {
        return attachmentPickerSheetView.getVisibility() == VISIBLE;
    }

    @Override
    public boolean isBottomSheetVisible() {
        return bottomSheetView.getVisibility() == VISIBLE;
    }

    @Override
    public boolean isDrawerVisible() {
        return drawerView.getVisibility() == VISIBLE;
    }

    @Override
    public void backFromScreen() {
        presenter.onScreenBackFrom(currentScreenId);
    }

    @Override
    public void closeDirectoryPicker() {
        directoryPickerSheetView.close();
    }

    @Override
    public void closeAttachmentPicker() {
        attachmentPickerSheetView.close();
    }

    @Override
    public void closeBottomSheet() {
        bottomSheetView.close();
    }

    @Override
    public void closeDrawer() {
        drawerView.close();
    }

    public void handleScreenBack() {
        presenter.onScreenBackFrom(currentScreenId);
    }

    public String getCurrentScreenId() {
        return currentScreenId;
    }

    public String getShellCommandText() {
        return shellCommandText;
    }

    private void renderAttachmentPicker() {
        attachmentPickerSheetView.show(
                attachmentPickerTitle,
                attachmentPickerTree,
                composerView.selectedAttachmentPaths(attachmentPickerSource),
                attachmentPickerLoading,
                attachmentPickerMessage,
                attachmentPickerSource
        );
    }

    private View buildScreen(String screenId) {
        View view = screenRegistry.createScreen(screenId, this, presenter, getContext());
        if (view != null) {
            return view;
        }
        return simpleScreen(screenId);
    }

    private View simpleScreen(String screenId) {
        Context context = getContext();
        String title = SimpleScreenContent.title(context, screenId);
        String subtitle = SimpleScreenContent.subtitle(context, screenId);
        String[] rows = SimpleScreenContent.rows(context, screenId);
        return new SimpleSettingsScreenView(context, title, subtitle, rows, this::handleScreenBack);
    }

    private float screenTransitionDistance() {
        int width = screenHost.getWidth();
        return width > 0 ? width : getResources().getDisplayMetrics().widthPixels;
    }

    private void resetScreenHostAnimationState() {
        screenHost.setAlpha(1f);
        screenHost.setTranslationX(0f);
    }
}
