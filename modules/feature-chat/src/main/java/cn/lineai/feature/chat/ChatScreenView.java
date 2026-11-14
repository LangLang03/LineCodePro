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

    private ChatController controller;
    /** Latest sub agent snapshot, shared by the header chip, the sheet and the drawer. */
    private java.util.List<cn.lineai.tool.api.AgentEntry> agents =
            java.util.Collections.emptyList();
    private Scope scope;
    private final LinearLayout content;
    private final ChatHeaderView header;
    private final SelectionBar selectionBar;
    private final TimelineView timeline;
    private final ChatApprovalView approval;
    private final TextView notice;
    private final ChatComposer composer;
    private final DrawerPanel drawer;
    private ChatScreenHost host;
    private Tokens tokens;
    private java.util.List<cn.lineai.core.model.Conversation> conversations =
            java.util.Collections.emptyList();
    private String drawerConversationId = "";
    private String drawerProject = "";

    /** Builds the column and binds every child to the controller and host hooks. */
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

    /** Routes header taps to the drawer or the host. */
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
            public void onAgents() {
                AgentSessionSheet.show(getContext(), tokens, agents, currentAgentId(),
                        agentId -> {
                            if (host != null) host.onSelectAgent(agentId);
                        });
            }

            @Override
            public void onContext() {
                if (host != null) {
                    host.onContextUsage();
                }
            }
        });
    }

    /** Routes composer sends/stops to the controller and menu rows to the host. */
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

    /** Wires tool presentation, diff lookup, message actions and the empty state. */
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

    /** Routes approval decisions to the controller (queued on the event queue). */
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

    /** Routes drawer conversation actions to the controller and file taps to the host. */
    private void bindDrawer() {
        drawer.setListener(new DrawerPanel.Listener() {
            @Override
            public void onNewConversation() {
                if (host != null) host.onSelectAgent("");
                controller.newConversation();
                composer.setDraft("");
            }

            @Override
            public void onSelect(String conversationId) {
                // Picking a user conversation always returns to the root view first.
                if (host != null) host.onSelectAgent("");
                controller.open(conversationId);
            }

            @Override
            public void onDelete(String conversationId) {
                if (host != null) host.onSelectAgent("");
                controller.deleteConversation(conversationId);
            }

            @Override
            public void onSelectAgent(String agentId) {
                if (host != null) host.onSelectAgent(agentId);
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

    /** Installs the host hooks; most commands no-op until this is called. */
    public void setHost(ChatScreenHost value) {
        host = value;
    }

    /** Applies display preferences and the markdown link handler. */
    public void preferences(cn.lineai.core.model.UiPreferences values,
                            java.util.function.Consumer<String> links) {
        composer.preferences(values);
        timeline.preferences(values, links);
    }

    /** Attaches an image to the composer (host-side import). */
    public void addAttachment(String path) {
        composer.addAttachment(path);
    }

    /** Attaches a file to the composer (host-side import). */
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

    /** Persists composer state across configuration changes. */
    public android.os.Bundle saveComposer() { return composer.save(); }
    /** Restores composer state saved by {@link #saveComposer}. */
    public void restoreComposer(android.os.Bundle saved) { composer.restore(saved); }

    /**
     * Re-binds the view to another controller (root conversation vs a sub agent session).
     * The previous observation is dropped; the new controller's current snapshot renders
     * immediately. Sub agent controllers are never told to start() — their session is
     * already bound and started by the registry.
     *
     * @param target the controller to display
     */
    public void bindController(ChatController target) {
        if (target == null || target == controller) {
            return;
        }
        if (scope != null) {
            scope.close();
            scope = null;
        }
        controller = target;
        observeController();
        onChanged(controller.state());
    }

    /** Attaches the state and conversation-list observations to the current controller. */
    private void observeController() {
        scope = new Scope();
        scope.track(controller.observe(this));
        scope.track(controller.observeConversations(list -> {
            conversations = list;
            refreshDrawerConversations();
        }));
    }

    /** Starts observing the controller; call once the view is attached. */
    public void start() {
        if (scope != null) {
            return;
        }
        observeController();
        controller.start();
    }

    /** Detaches observers; the drawer keeps its last rendered conversations. */
    public void stop() {
        if (scope != null) {
            scope.close();
            scope = null;
        }
    }

    /** Re-renders the drawer list with the viewed and running conversation ids. */
    private void refreshDrawerConversations() {
        drawer.setConversations(conversations, controller.viewedConversationId(),
                controller.runningConversationId());
    }

    /** Updates the drawer's live sub agent list; called by the host from the registry. */
    public void setAgents(java.util.List<cn.lineai.tool.api.AgentEntry> agents) {
        this.agents = agents == null
                ? java.util.Collections.<cn.lineai.tool.api.AgentEntry>emptyList()
                : new java.util.ArrayList<>(agents);
        drawer.setAgents(this.agents, currentAgentId());
        header.setAgentCount(this.agents.size());
    }

    /** The sub agent owning the currently displayed conversation; empty for the root view. */
    public String currentAgentId() {
        return controller.state().agentId;
    }

    /** Re-renders every child from one snapshot; also syncs the drawer id/project caches. */
    @Override
    public void onChanged(ChatState state) {
        String currentId = controller.viewedConversationId();
        if (!currentId.equals(drawerConversationId)) {
            drawerConversationId = currentId;
            refreshDrawerConversations();
        }
        if (!state.projectLabel.equals(drawerProject)) {
            drawerProject = state.projectLabel;
            drawer.refreshProject();
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

    /** Leaves multi-select mode and hides the selection bar. */
    private void exitMultiSelect() {
        timeline.setMultiSelectMode(false);
        selectionBar.setVisibility(GONE);
    }

    /** Restyles every child in place. */
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

    /** True while the drawer covers the screen. */
    public boolean isDrawerOpen() {
        return drawer.getVisibility() == VISIBLE;
    }

    /** Opens the drawer. */
    public void openDrawer() {
        drawer.open();
    }

    /** Closes the drawer; false when it was already hidden. */
    public boolean closeDrawer() {
        if (isDrawerOpen()) {
            drawer.close();
            return true;
        }
        return false;
    }

    /** Back handling: the drawer consumes the event when open. */
    public boolean handleBack() {
        return closeDrawer();
    }

    /** Applies system-bar and IME insets to the column and the drawer sidebar. */
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
