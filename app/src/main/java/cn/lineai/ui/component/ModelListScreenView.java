package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.ModelConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ModelListScreenView extends LinearLayout {
    public interface Listener {
        void onBack();

        void onAddModel();

        void onSelectModel(String id);

        void onEditModel(String id);

        void onDeleteModels(List<String> ids);
    }

    private final ArrayList<ModelConfig> models;
    private final String selectedModelId;
    private final Listener listener;
    private final String title;
    private final boolean allowManagement;
    private final FrameLayout headerHost;
    private final LinearLayout list;
    private final Set<String> multiSelectedIds = new HashSet<>();

    public ModelListScreenView(Context context, List<ModelConfig> models, String selectedModelId, Listener listener) {
        this(context, models, selectedModelId, context.getString(R.string.screen_models_title), true, listener);
    }

    public ModelListScreenView(
            Context context,
            List<ModelConfig> models,
            String selectedModelId,
            String title,
            boolean allowManagement,
            Listener listener
    ) {
        super(context);
        this.models = new ArrayList<>(models == null ? new ArrayList<>() : models);
        this.selectedModelId = selectedModelId == null ? "" : selectedModelId;
        this.listener = listener;
        this.title = title == null || title.length() == 0 ? context.getString(R.string.screen_models_title) : title;
        this.allowManagement = allowManagement;
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);

        headerHost = new FrameLayout(context);
        addView(headerHost, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        list = new LinearLayout(context);
        list.setOrientation(VERTICAL);
        LineTheme.padding(list, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);
        scrollView.addView(list, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        renderHeader();
        renderList();
    }

    private void renderHeader() {
        Context context = getContext();
        headerHost.removeAllViews();
        if (!multiSelectedIds.isEmpty()) {
            IconButtonView close = new IconButtonView(context, IconButtonView.CLOSE);
            close.setIconColor(LineTheme.TEXT);
            close.setIconSizeDp(36, 20);
            close.setOnClickListener(v -> {
                multiSelectedIds.clear();
                renderHeader();
                renderList();
            });

            IconButtonView trash = new IconButtonView(context, IconButtonView.TRASH_2);
            trash.setIconColor(LineTheme.DANGER);
            trash.setIconSizeDp(36, 20);
            trash.setOnClickListener(v -> showDeleteConfirm());
            headerHost.addView(
                    new ScreenHeaderView(context, context.getString(R.string.screen_models_selected_count, multiSelectedIds.size()), close, trash),
                    new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            );
            return;
        }

        IconButtonView add = null;
        if (allowManagement) {
            add = new IconButtonView(context, IconButtonView.PLUS);
            add.setIconColor(LineTheme.TEXT);
            add.setIconSizeDp(36, 20);
            add.setOnClickListener(v -> listener.onAddModel());
        }
        headerHost.addView(
                new ScreenHeaderView(context, title, listener::onBack, add),
                new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        );
    }

    private void renderList() {
        Context context = getContext();
        list.removeAllViews();

        if (models.isEmpty()) {
            String emptyText = allowManagement
                    ? context.getString(R.string.screen_models_empty_can_add)
                    : context.getString(R.string.screen_models_empty_readonly);
            TextView empty = LineTheme.text(context, emptyText, LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            empty.setLineSpacing(LineTheme.dp(context, 3), 1f);
            list.addView(empty, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }

        for (ModelConfig model : models) {
            addModel(list, model, selectedModelId.equals(model.getId()), multiSelectedIds.contains(model.getId()));
        }
    }

    private void addModel(LinearLayout list, ModelConfig model, boolean selected, boolean checked) {
        Context context = list.getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setClickable(true);
        card.setOnClickListener(v -> {
            if (!multiSelectedIds.isEmpty()) {
                toggleMultiSelected(model.getId());
                return;
            }
            listener.onSelectModel(model.getId());
        });
        card.setOnLongClickListener(v -> {
            if (!allowManagement) {
                return true;
            }
            if (!multiSelectedIds.isEmpty()) {
                toggleMultiSelected(model.getId());
            } else {
                showModelActions(model);
            }
            return true;
        });
        int background = checked ? LineTheme.ACCENT_MUTED : LineTheme.BG;
        int border = selected || checked ? LineTheme.ACCENT : Color.TRANSPARENT;
        card.setBackground(LineTheme.roundedStroke(context, background, 12, border));
        LineTheme.padding(card, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        list.addView(card, cardParams);

        String provider = displayProvider(model);
        TextView badge = LineTheme.text(context, provider, LineTheme.FONT_XS, LineTheme.TEXT_ON_COLOR, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(LineTheme.rounded(context, badgeColor(model), 8));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        badgeParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        LineTheme.padding(badge, LineTheme.SM, 4, LineTheme.SM, 4);
        card.addView(badge, badgeParams);

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(VERTICAL);
        card.addView(info, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView title = LineTheme.textMedium(context, model.getName(), LineTheme.FONT_MD, LineTheme.TEXT);
        title.setSingleLine(true);
        info.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView sub = LineTheme.text(context, model.getModelId(), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        sub.setSingleLine(true);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subParams.topMargin = LineTheme.dp(context, 2);
        info.addView(sub, subParams);

        if (!multiSelectedIds.isEmpty()) {
            FrameLayout check = new FrameLayout(context);
            check.setBackground(LineTheme.roundedStroke(context, checked ? LineTheme.ACCENT : Color.TRANSPARENT, 11, checked ? LineTheme.ACCENT : LineTheme.TEXT_TERTIARY));
            if (checked) {
                IconButtonView icon = new IconButtonView(context, IconButtonView.CHECK);
                icon.setIconColor(LineTheme.TEXT_ON_COLOR);
                icon.setIconSizeDp(18, 14);
                icon.setClickable(false);
                check.addView(icon, new FrameLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18), Gravity.CENTER));
            }
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 22), LineTheme.dp(context, 22));
            checkParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
            card.addView(check, checkParams);
        } else if (selected) {
            View dot = new View(context);
            dot.setBackground(LineTheme.rounded(context, LineTheme.ACCENT, 4));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 8), LineTheme.dp(context, 8));
            dotParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
            card.addView(dot, dotParams);
        }
    }

    private void showModelActions(ModelConfig model) {
        Context context = getContext();
        Dialog dialog = createBottomDialog(context);
        LinearLayout panel = createBottomPanel(context);
        addHandle(panel);
        addSheetTitle(panel, model.getName().length() == 0 ? context.getString(R.string.screen_models_title) : model.getName());
        addDivider(panel);
        addActionRow(panel, context.getString(R.string.screen_models_action_modify), context.getString(R.string.screen_models_action_modify_desc), () -> {
            dialog.dismiss();
            listener.onEditModel(model.getId());
        });
        addActionRow(panel, context.getString(R.string.screen_models_action_multi_select), context.getString(R.string.screen_models_action_multi_select_desc), () -> {
            dialog.dismiss();
            startMultiSelect(model.getId());
        });
        addBottomInset(panel);
        showBottomDialog(dialog, panel);
    }

    private void showDeleteConfirm() {
        if (multiSelectedIds.isEmpty()) {
            return;
        }
        Context context = getContext();
        Dialog dialog = createBottomDialog(context);
        LinearLayout panel = createBottomPanel(context);
        addHandle(panel);
        addSheetTitle(panel, context.getString(R.string.screen_models_delete_title));
        TextView desc = LineTheme.text(context, context.getString(R.string.screen_models_delete_message, multiSelectedIds.size()), LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LineTheme.padding(desc, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);
        panel.addView(desc, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addDivider(panel);
        addActionRow(panel, context.getString(R.string.common_cancel), "", dialog::dismiss);
        addActionRow(panel, context.getString(R.string.common_delete), context.getString(R.string.screen_models_delete_warning), () -> {
            ArrayList<String> ids = new ArrayList<>(multiSelectedIds);
            dialog.dismiss();
            listener.onDeleteModels(ids);
            for (int i = models.size() - 1; i >= 0; i--) {
                if (ids.contains(models.get(i).getId())) {
                    models.remove(i);
                }
            }
            multiSelectedIds.clear();
            renderHeader();
            renderList();
        }, LineTheme.DANGER);
        addBottomInset(panel);
        showBottomDialog(dialog, panel);
    }

    private void startMultiSelect(String id) {
        multiSelectedIds.clear();
        multiSelectedIds.add(id);
        renderHeader();
        renderList();
    }

    private void toggleMultiSelected(String id) {
        if (multiSelectedIds.contains(id)) {
            multiSelectedIds.remove(id);
        } else {
            multiSelectedIds.add(id);
        }
        renderHeader();
        renderList();
    }

    private Dialog createBottomDialog(Context context) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private LinearLayout createBottomPanel(Context context) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(VERTICAL);
        panel.setBackground(LineTheme.roundedTop(context, LineTheme.SURFACE_ELEVATED, 16));
        return panel;
    }

    private void showBottomDialog(Dialog dialog, LinearLayout panel) {
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.BOTTOM);
    }

    private void addHandle(LinearLayout panel) {
        Context context = panel.getContext();
        View handle = new View(context);
        handle.setBackground(LineTheme.rounded(context, LineTheme.TEXT_TERTIARY, 2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LineTheme.dp(context, 36), LineTheme.dp(context, 4));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = LineTheme.dp(context, LineTheme.SM);
        params.bottomMargin = LineTheme.dp(context, LineTheme.XS);
        panel.addView(handle, params);
    }

    private void addSheetTitle(LinearLayout panel, String title) {
        Context context = panel.getContext();
        TextView titleView = LineTheme.text(context, title, LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        LineTheme.padding(titleView, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);
        panel.addView(titleView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addDivider(LinearLayout panel) {
        View divider = new View(panel.getContext());
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        panel.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));
    }

    private void addActionRow(LinearLayout panel, String label, String desc, Runnable action) {
        addActionRow(panel, label, desc, action, LineTheme.TEXT);
    }

    private void addActionRow(LinearLayout panel, String label, String desc, Runnable action, int labelColor) {
        Context context = panel.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> action.run());
        LineTheme.padding(row, LineTheme.LG, 14, LineTheme.LG, 14);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        labels.addView(LineTheme.text(context, label, LineTheme.FONT_MD, labelColor, Typeface.NORMAL), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        if (desc != null && desc.length() > 0) {
            TextView descView = LineTheme.text(context, desc, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            descParams.topMargin = LineTheme.dp(context, 2);
            labels.addView(descView, descParams);
        }
        panel.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addBottomInset(LinearLayout panel) {
        panel.addView(new View(panel.getContext()), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(panel.getContext(), 34)));
    }

    private String displayProvider(ModelConfig model) {
        String provider = model.getProviderLabel();
        String customLabel = getContext().getString(R.string.model_provider_custom);
        if (provider == null || provider.length() == 0 || customLabel.equals(provider)) {
            return model.getProtocolType().getLabel();
        }
        return provider;
    }

    private int badgeColor(ModelConfig model) {
        switch (model.getProtocolType()) {
            case CODEX_RESPONSES:
                return Color.parseColor("#4B8BFF");
            case ANTHROPIC_MESSAGES:
                return Color.parseColor("#B86F50");
            case LOCAL_GGUF:
                return Color.parseColor("#2E7D62");
            case OPENAI_COMPATIBLE:
            default:
                return Color.parseColor("#10A37F");
        }
    }
}
