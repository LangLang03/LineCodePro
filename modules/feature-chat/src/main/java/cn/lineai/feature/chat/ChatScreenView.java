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

        void onProject();

        void onPermission();

        void onAttach();

        void onAttachFile();

        void onContextUsage();

        void onChatMode();

        void setChatMode(String mode);

        java.util.List<cn.lineai.core.model.WorkspaceNode> workspaceFiles(String path);
    }

    private final ChatController controller;
    private Scope scope;
    private final LinearLayout content;
    private final ChatHeaderView header;
    private final TimelineView timeline;
    private final ChatApprovalView approval;
    private final TextView notice;
    private final ChatComposer composer;
    private final DrawerPanel drawer;
    private Host host;
    private Tokens tokens;

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
                    host.onOpenSettings();
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
            public void onAttach() {
                if (host != null) {
                    host.onAttach();
                }
            }

            @Override
            public void onAttachFile() {
                if (host != null) host.onAttachFile();
            }

            @Override
            public void onModel() {
                if (host != null) {
                    host.onOpenModelSettings();
                }
            }

            @Override
            public void onReasoning() {
                if (host != null) {
                    host.onOpenSettings();
                }
            }

            @Override
            public void onMode() {
                if (host != null) host.onChatMode();
            }

            @Override
            public void onChatMode(String mode) {
                if (host != null) host.setChatMode(mode);
            }
        });
    }

    private void bindTimeline() {
        timeline.toolPresentations(controller::presentationFor);
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
                drawer.close();
                composer.setDraft("");
            }

            @Override
            public void onSelect(String conversationId) {
                controller.open(conversationId);
                drawer.close();
            }

            @Override
            public void onDelete(String conversationId) {
                controller.deleteConversation(conversationId);
            }

            @Override
            public java.util.List<cn.lineai.core.model.WorkspaceNode> files(String path) {
                return host == null ? java.util.Collections
                        .<cn.lineai.core.model.WorkspaceNode>emptyList()
                        : host.workspaceFiles(path);
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

    public android.os.Bundle saveComposer() { return composer.save(); }
    public void restoreComposer(android.os.Bundle saved) { composer.restore(saved); }

    /** Starts observing the controller; call once the view is attached. */
    public void start() {
        if (scope != null) {
            return;
        }
        scope = new Scope();
        scope.track(controller.observe(this));
        scope.track(controller.observeConversations(drawer::setConversations));
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
        header.render(state, tokens);
        timeline.render(state, tokens);
        approval.render(state.pendingApprovals, tokens);
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

    public void applyTokens(Tokens current) {
        tokens = current;
        setBackgroundColor(current.bg);
        content.setBackgroundColor(current.bg);
        notice.setTextColor(current.danger);
        header.applyTokens(current);
        timeline.applyTokens(current);
        approval.applyTokens(current);
        composer.applyTokens(current);
        drawer.applyTokens(current);
    }

    public boolean isDrawerOpen() {
        return drawer.getVisibility() == VISIBLE;
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
