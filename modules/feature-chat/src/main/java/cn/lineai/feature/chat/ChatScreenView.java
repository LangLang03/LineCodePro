package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.observe.Observable;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scope;
import cn.lineai.ui.i18n.ErrorTextMapper;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * The chat screen: header, timeline, composer and the conversation drawer.
 *
 * <p>The content column is capped at 792dp and centred, exactly like the legacy shell, so the
 * layout looks unchanged on tablets and foldables.</p>
 */
public final class ChatScreenView extends FrameLayout implements Observable.Observer<ChatState> {

    public interface Host {
        void onOpenModelSettings();

        void onOpenSettings();

        void onReasoning();

        void onMore();

        void onProject();

        void onPermission();

        void onAttach();

        void onAttachFile();

        /** Opens the model-picker sheet; falls back to the models page when no model is set. */
        void onModelPicker();

        /** Argument-aware tool presentation so cards show the real path/command/query. */
        default cn.lineai.tool.api.ToolPresentation toolPresentation(String name,
                                                                     String argumentsJson) {
            return null;
        }

        void onContextUsage();

        void onChatMode();

        void setChatMode(String mode);

        void openFile(String relativePath);

        String projectLabel();

        String projectPath();

        java.util.List<cn.lineai.core.model.WorkspaceNode> workspaceFiles(String path);

        /** Loads the whole workspace tree for the drawer and the attachment picker. */
        default void loadWorkspaceTree(java.util.function.Consumer<DrawerPanel.FileNode> callback) {
            callback.accept(null);
        }

        /** Opens the workspace attachment tree (composer plus menu row). */
        default void onAttachmentPicker() {
            onAttachFile();
        }

        /** Model capability switches for the composer menu and reasoning lock. */
        default ChatCapabilities capabilities() {
            return ChatCapabilities.of(null);
        }

        /** Opens the system video picker (only when the model supports video input). */
        default void onVideoPicker() {
            onAttach();
        }

        /** Long press on the plus button: opens the system image picker. */
        default void onImagePicker() {
            onAttach();
        }

        default boolean projectRemovable() {
            return false;
        }

        default void onProjectRemoveRequested() {
            onProject();
        }

        /** Optional persisted file-diff lookup for write cards; {@code null} falls back. */
        default cn.lineai.feature.chat.toolcall.ToolDiffLookup.Snapshot toolDiff(String diffId) {
            return null;
        }
    }

    private final ChatController controller;
    private Scope scope;
    private final LinearLayout content;
    private final ChatHeaderView header;
    private final SelectionBar selectionBar;
    private final TimelineView timeline;
    private final ChatApprovalView approval;
    private final TextView notice;
    private final ChatComposer composer;
    private final DrawerPanel drawer;
    private Host host;
    private Tokens tokens;
    private java.util.List<cn.lineai.core.model.Conversation> conversations =
            java.util.Collections.emptyList();
    private String drawerConversationId = "";

    public ChatScreenView(Context context, ChatController controller, Tokens tokens) {
        super(context);
        this.controller = controller;
        this.tokens = tokens;
        setBackgroundColor(tokens.bg);

        content = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int width = Math.min(MeasureSpec.getSize(widthSpec), Ui.dp(getContext(), 792));
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightSpec);
            }
        };
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.TOP);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT,
                Gravity.CENTER_HORIZONTAL));

        header = new ChatHeaderView(context, tokens);
        content.addView(header, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        notice = Ui.text(context, "", Tokens.FONT_XS, tokens.danger, android.graphics.Typeface.NORMAL);
        notice.setGravity(Gravity.CENTER);
        Ui.padding(notice, 16, 6, 16, 6);
        notice.setVisibility(GONE);
        content.addView(notice, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        timeline = new TimelineView(context, tokens);

        selectionBar = new SelectionBar(context, tokens, this::exitMultiSelect);
        selectionBar.setVisibility(GONE);
        content.addView(selectionBar, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, Ui.dp(context, 48)));
        content.addView(timeline, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        approval = new ChatApprovalView(context, tokens);
        content.addView(approval, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        composer = new ChatComposer(context, tokens);
        content.addView(composer, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        drawer = new DrawerPanel(context, tokens);
        addView(drawer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        bindHeader();
        bindComposer();
        bindTimeline();
        bindDrawer();
        bindApproval();
        installInsets();
    }

    private void bindHeader() {
        header.setListener(new ChatHeaderView.Listener() {
            @Override
            public void onMenu() {
                drawer.open();
            }

            @Override
            public void onProject() {
                if (host != null) {
                    host.onProject();
                }
            }

            @Override
            public void onPermission() {
                if (host != null) {
                    host.onPermission();
                }
            }

            @Override
            public void onNewConversation() {
                controller.newConversation();
            }

            @Override
            public void onMore() {
                if (host != null) {
                    host.onMore();
                }
            }

            @Override
            public void onContext() {
                if (host != null) {
                    host.onContextUsage();
                }
            }
        });
    }

    private void bindComposer() {
        composer.setListener(new ChatComposer.Listener() {
            @Override
            public void onSend(String text, java.util.List<String> imagePaths,
                               java.util.List<String> filePaths) {
                controller.send(text, imagePaths, filePaths);
            }

            @Override
            public void onStop() {
                controller.stop();
            }

            @Override
            public void onAttachFiles() {
                if (host != null) {
                    host.onAttachmentPicker();
                }
            }

            @Override
            public void onAttachImage() {
                if (host != null) {
                    host.onImagePicker();
                }
            }

            @Override
            public void onVideoPicker() {
                if (host != null) {
                    host.onVideoPicker();
                }
            }

            @Override
            public void onMode() {
                if (host != null) {
                    host.onChatMode();
                }
            }

            @Override
            public void onModel() {
                if (host != null) {
                    host.onModelPicker();
                }
            }

            @Override
            public void onReasoning() {
                if (host != null) {
                    host.onReasoning();
                }
            }

            @Override
            public void onChatMode(String mode) {
                if (host != null) host.setChatMode(mode);
            }
        });
    }

    private void bindTimeline() {
        timeline.toolPresentations(new ToolPresentationProvider() {
            @Override
            public cn.lineai.tool.api.ToolPresentation presentation(String toolName) {
                return controller.presentationFor(toolName);
            }

            @Override
            public cn.lineai.tool.api.ToolPresentation presentation(String toolName,
                                                                    String argumentsJson) {
                cn.lineai.tool.api.ToolPresentation resolved =
                        host == null ? null : host.toolPresentation(toolName, argumentsJson);
                return resolved == null ? controller.presentationFor(toolName) : resolved;
            }
        });
        timeline.toolDiffLookup(diffId -> host == null ? null : host.toolDiff(diffId));
        timeline.messageActions(new MessageActions() {
            @Override
            public void onQuote(cn.lineai.core.model.Message message) {
                String quoted = "> " + message.plainText().replace("\n", "\n> ") + "\n";
                composer.setDraft(composer.draft() + quoted);
            }

            @Override
            public boolean canRetry() {
                return false;
            }

            @Override
            public void onMultiSelect(cn.lineai.core.model.Message message) {
                timeline.setMultiSelectMode(true);
                timeline.toggleSelection(message.id, message);
                selectionBar.setVisibility(VISIBLE);
            }

            @Override
            public void onSelectionChanged(java.util.List<cn.lineai.core.model.Message> selected) {
                selectionBar.setVisibility(timeline.isMultiSelectMode() ? VISIBLE : GONE);
                selectionBar.setSelection(selected);
            }
        });
        timeline.setEmptyAction(new TimelineView.EmptyAction() {
            @Override
            public void onAddModel() {
                if (host != null) {
                    host.onOpenModelSettings();
                }
            }

            @Override
            public void onOpenWorkspace() {
                if (host != null) {
                    host.onProject();
                }
            }
        });
    }

    private void bindApproval() {
        approval.setListener(new ChatApprovalView.Listener() {
            @Override public void onDeny() {
                controller.denyApproval();
            }

            @Override public void onAllowOnce() {
                controller.approveOnce();
            }

            @Override public void onAllowAlways() {
                controller.approveAlways();
            }
        });
    }

    private void bindDrawer() {
        drawer.setListener(new DrawerPanel.Listener() {
            @Override
            public void onNewConversation() {
                controller.newConversation();
                composer.setDraft("");
            }

            @Override
            public void onSelect(String conversationId) {
                controller.open(conversationId);
            }

            @Override
            public void onDelete(String conversationId) {
                controller.deleteConversation(conversationId);
            }

            @Override
            public void onFileSelected(String path, String name) {
                if (host != null) host.openFile(path);
            }

            @Override
            public void onFileLongPressed(String path, String name,
                                          boolean directory, boolean root) {
                if (host != null && root) host.onAttachmentPicker();
            }

            @Override
            public void onProjectRemoveRequested() {
                if (host != null) host.onProjectRemoveRequested();
            }

            @Override
            public String projectLabel() {
                return host == null ? "" : host.projectLabel();
            }

            @Override
            public String projectPath() {
                return host == null ? "" : host.projectPath();
            }

            @Override
            public boolean projectRemovable() {
                return host != null && host.projectRemovable();
            }

            @Override
            public void loadFileTree(java.util.function.Consumer<DrawerPanel.FileNode> callback) {
                if (host == null) {
                    callback.accept(null);
                } else {
                    host.loadWorkspaceTree(callback);
                }
            }
        });
    }

    public void setHost(Host value) {
        host = value;
    }

    public void preferences(cn.lineai.core.model.UiPreferences values,
                            java.util.function.Consumer<String> links) {
        composer.preferences(values);
        timeline.preferences(values, links);
    }

    public void addAttachment(String path) {
        composer.addAttachment(path);
    }

    public void addFileAttachment(String path) {
        composer.addFileAttachment(path);
    }

    /** Paths currently attached to the composer, used by the attachment tree picker. */
    public java.util.List<String> fileAttachmentPaths() {
        return composer.fileAttachmentPaths();
    }

    /** Adds or removes one attachment path; the picker calls this for every tap. */
    public void toggleFileAttachment(String path) {
        composer.toggleFileAttachment(path);
    }

    public android.os.Bundle saveComposer() { return composer.save(); }
    public void restoreComposer(android.os.Bundle saved) { composer.restore(saved); }

    /** Starts observing the controller; call once the view is attached. */
    public void start() {
        if (scope != null) {
            return;
        }
        scope = new Scope();
        scope.track(controller.observe(this));
        scope.track(controller.observeConversations(list -> {
            conversations = list;
            drawer.setConversations(list, controller.conversationId());
        }));
        controller.start();
    }

    public void stop() {
        if (scope != null) {
            scope.close();
            scope = null;
        }
    }

    @Override
    public void onChanged(ChatState state) {
        String currentId = controller.conversationId();
        if (!currentId.equals(drawerConversationId)) {
            drawerConversationId = currentId;
            drawer.setConversations(conversations, currentId);
        }
        header.render(state, tokens);
        timeline.render(state, tokens);
        approval.render(state.pendingApprovals, tokens);
        composer.capabilities(host == null ? ChatCapabilities.of(null) : host.capabilities());
        composer.render(state, tokens);
        String status = state.statusText;
        if (status.isEmpty()) {
            notice.setVisibility(GONE);
        } else {
            ErrorCode code;
            try {
                code = ErrorCode.valueOf(status);
            } catch (IllegalArgumentException e) {
                code = ErrorCode.UNKNOWN;
            }
            notice.setText(ErrorTextMapper.text(code).resolve(getContext()));
            notice.setVisibility(VISIBLE);
            notice.bringToFront();
        }
    }

    private void exitMultiSelect() {
        timeline.setMultiSelectMode(false);
        selectionBar.setVisibility(GONE);
    }

    public void applyTokens(Tokens current) {
        tokens = current;
        setBackgroundColor(current.bg);
        content.setBackgroundColor(current.bg);
        notice.setTextColor(current.danger);
        selectionBar.applyTokens(current);
        header.applyTokens(current);
        timeline.applyTokens(current);
        approval.applyTokens(current);
        composer.applyTokens(current);
        drawer.applyTokens(current);
    }

    public boolean isDrawerOpen() {
        return drawer.getVisibility() == VISIBLE;
    }

    public void openDrawer() {
        drawer.open();
    }

    public boolean closeDrawer() {
        if (isDrawerOpen()) {
            drawer.close();
            return true;
        }
        return false;
    }

    public boolean handleBack() {
        return closeDrawer();
    }

    private void installInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsets.Type.ime());
            content.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            drawer.setTopInset(bars.top);
            return insets;
        });
        post(this::requestApplyInsets);
    }
}
