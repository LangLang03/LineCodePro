package cn.lineai.feature.models;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.ScreenBar;
import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.ThemeBinder;
import cn.lineai.ui.tokens.Tokens;

/**
 * Model management list with selection, long-press actions and batch deletion.
 *
 * <p>This page owns the module's loading idiom: the store is read on the io scheduler and the list
 * is re-rendered on the main scheduler (see {@link #onShown}). Re-rendering is wholesale — the list
 * container is emptied and rebuilt on every data change — which is acceptable here because the set
 * of models is small and changes rarely, and every render derives its styling from the current
 * palette so theme changes ride along for free. Selection and multi-select state live in the page,
 * not in the cards, so rebuilds cannot lose them.</p>
 */
public final class ModelListPage extends LinearLayout implements cn.lineai.ui.kit.Refreshable {

    /**
     * Outcome sink for navigation, selection and deletion; the page never navigates or persists on
     * its own.
     */
    public interface Listener {
        /** Reports that the header back button was pressed. */
        void onBack();

        /** Reports that the add-model action was requested. */
        void onAddModel();

        /**
         * Reports a tapped card outside multi-select mode.
         *
         * @param id id of the tapped model
         */
        void onSelectModel(String id);

        /**
         * Reports the modify action for a model.
         *
         * @param id id of the model to edit
         */
        void onEditModel(String id);

        /**
         * Reports the confirmed deletion of one or more models.
         *
         * @param ids ids selected for deletion, never empty
         */
        void onDeleteModels(List<String> ids);
    }

    private final ThemeBinder theme;
    private final ArrayList<ModelConfig> models = new ArrayList<>();
    private final Set<String> multiSelected = new HashSet<>();
    private final Listener listener;
    private final FrameLayout headerHost;
    private final LinearLayout list;
    private final String title;
    private final boolean management;
    private String selectedId;
    private java.util.function.Supplier<List<ModelConfig>> loader;
    private java.util.function.Supplier<String> selection;
    private final cn.lineai.core.kernel.time.Scheduler scheduler;

    /**
     * Creates a management-mode list page with the default title.
     *
     * @param context hosting context
     * @param tokens initial palette snapshot
     * @param models initial model set; null renders as empty
     * @param selectedId id highlighted as the active model; may be null
     * @param listener outcome sink; never null in management use
     * @param scheduler scheduler for the io/main refresh hop; may be null, in which case loading
     *     falls back to synchronous calls
     */
    public ModelListPage(Context context, Tokens tokens, List<ModelConfig> models, String selectedId,
                         Listener listener, cn.lineai.core.kernel.time.Scheduler scheduler) {
        this(context, tokens, models, selectedId, context.getString(R.string.screen_models_title),
                true, listener, scheduler);
    }

    /**
     * Creates a list page with full control over title and mode.
     *
     * @param context hosting context
     * @param tokens initial palette snapshot
     * @param models initial model set; null renders as empty
     * @param selectedId id highlighted as the active model; may be null
     * @param title header title; null or empty falls back to the default resource
     * @param management true enables the add action and long-press model actions
     * @param listener outcome sink; may be null in read-only mode
     * @param scheduler scheduler for the io/main refresh hop; may be null for synchronous loading
     */
    public ModelListPage(Context context, Tokens tokens, List<ModelConfig> models, String selectedId,
                         String title, boolean management, Listener listener,
                         cn.lineai.core.kernel.time.Scheduler scheduler) {
        super(context);
        this.scheduler = scheduler;
        this.theme = new ThemeBinder(tokens);
        this.models.addAll(models == null ? new ArrayList<ModelConfig>() : models);
        this.selectedId = selectedId == null ? "" : selectedId;
        this.title = title == null || title.length() == 0
                ? context.getString(R.string.screen_models_title) : title;
        this.management = management;
        this.listener = listener;
        setOrientation(VERTICAL);
        theme.bind(t -> setBackgroundColor(t.bg));

        headerHost = new FrameLayout(context);
        addView(headerHost, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(context);
        list = new LinearLayout(context);
        list.setOrientation(VERTICAL);
        Ui.padding(list, 16, 16, 16, 100);
        scroll.addView(list, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        renderHeader();
        renderList();
    }

    /**
     * Installs the refresh suppliers used by {@link #onShown}. Both suppliers run on the io
     * scheduler; neither may touch views.
     *
     * @param loader store reader producing the current model list
     * @param selection store reader producing the currently active model id
     */
    public void loader(java.util.function.Supplier<List<ModelConfig>> loader,
                       java.util.function.Supplier<String> selection) {
        this.loader = loader;
        this.selection = selection;
    }

    /**
     * Refreshes the list on every visibility change. With a scheduler installed, the suppliers run
     * on io and {@link #apply} runs on main; without one (legacy harnesses) both run inline on the
     * calling thread.
     */
    @Override public void onShown() {
        if (loader == null) return;
        if (scheduler == null) {
            apply(loader.get(), selection == null ? selectedId : selection.get());
            return;
        }
        // The loader hits the store on io and renders on main; the factory supplies plain
        // store lambdas, so the page owns the async idiom.
        scheduler.io(() -> {
            List<ModelConfig> latest = loader.get();
            String current = selection == null ? selectedId : selection.get();
            scheduler.main(() -> apply(latest, current));
        });
    }

    /**
     * Replaces page state when the loaded data actually differs; identical data skips the rebuild
     * so scroll position survives a no-op refresh. Must run on the main thread.
     */
    private void apply(List<ModelConfig> latest, String current) {
        boolean changed = !latest.equals(models) || !current.equals(selectedId);
        if (!changed) return;
        models.clear();
        models.addAll(latest);
        selectedId = current;
        renderHeader();
        renderList();
    }

    /** Rebuilds the header, switching to the multi-select bar when a selection is active. */
    private void renderHeader() {
        Context context = getContext();
        headerHost.removeAllViews();
        ScreenBar bar;
        if (!multiSelected.isEmpty()) {
            bar = new ScreenBar(context, theme, R.string.screen_models_title,
                    Icons.CLOSE, R.string.common_close, () -> {
                        multiSelected.clear();
                        renderHeader();
                        renderList();
                    },
                    Icons.TRASH_2, R.string.common_delete, this::confirmDelete);
            bar.title(context.getString(R.string.screen_models_selected_count, multiSelected.size()));
            bar.iconSizes(20, 20);
        } else {
            bar = new ScreenBar(context, theme, R.string.screen_models_title,
                    Icons.CHEVRON_LEFT, R.string.common_back, listener::onBack,
                    management ? Icons.PLUS : 0,
                    management ? R.string.screen_model_add_options_title : 0,
                    management ? listener::onAddModel : null);
            bar.title(title);
            if (management) bar.iconSizes(22, 20);
        }
        headerHost.addView(bar, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    /** Rebuilds the card list from the current model set, or shows the empty-state caption. */
    private void renderList() {
        Context context = getContext();
        list.removeAllViews();
        if (models.isEmpty()) {
            TextView empty = Ui.text(context, context.getString(management
                            ? R.string.screen_models_empty_can_add
                            : R.string.screen_models_empty_readonly),
                    Tokens.FONT_SM, theme.current().textTertiary, Typeface.NORMAL);
            empty.setLineSpacing(Ui.dp(context, 3), 1f);
            list.addView(empty, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        for (ModelConfig model : models) {
            addCard(model);
        }
    }

    /** Builds one model card: provider badge, name and id, plus the selection affordance current
     * mode requires. Cards are disposable; page state, not card state, encodes selection. */
    private void addCard(ModelConfig model) {
        Context context = getContext();
        Tokens tokens = theme.current();
        boolean selected = selectedId.equals(model.id);
        boolean checked = multiSelected.contains(model.id);
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setClickable(true);
        card.setOnClickListener(v -> {
            if (!multiSelected.isEmpty()) {
                toggleSelected(model.id);
                return;
            }
            listener.onSelectModel(model.id);
        });
        card.setOnLongClickListener(v -> {
            if (!management) return true;
            if (!multiSelected.isEmpty()) toggleSelected(model.id);
            else showActions(model);
            return true;
        });
        int background = checked ? tokens.accentMuted : tokens.bg;
        int border = selected || checked ? tokens.accent : Color.TRANSPARENT;
        card.setBackground(Ui.roundedStroke(context, background, 12, border));
        Ui.padding(card, 12, 12, 12, 12);
        LinearLayout.LayoutParams cardParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = Ui.dp(context, 8);
        list.addView(card, cardParams);

        TextView badge = Ui.bold(context, provider(model), Tokens.FONT_XS, tokens.textOnColor);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.rounded(context, badgeColor(model.protocol), 8));
        Ui.padding(badge, 8, 4, 8, 4);
        LinearLayout.LayoutParams badgeParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        badgeParams.rightMargin = Ui.dp(context, 12);
        card.addView(badge, badgeParams);

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(VERTICAL);
        card.addView(info, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        TextView name = Ui.medium(context, model.displayName(), Tokens.FONT_MD, tokens.text);
        name.setSingleLine(true);
        info.addView(name, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView id = Ui.text(context, model.modelId, Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        id.setSingleLine(true);
        LinearLayout.LayoutParams idParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        idParams.topMargin = Ui.dp(context, 2);
        info.addView(id, idParams);

        if (!multiSelected.isEmpty()) {
            FrameLayout check = new FrameLayout(context);
            check.setBackground(Ui.roundedStroke(context,
                    checked ? tokens.accent : Color.TRANSPARENT, 11,
                    checked ? tokens.accent : tokens.textTertiary));
            if (checked) {
                IconButton icon = new IconButton(context, Icons.CHECK);
                icon.setIconColor(tokens.textOnColor);
                icon.setIconSizeDp(18, 14);
                icon.setClickable(false);
                check.addView(icon, new FrameLayout.LayoutParams(
                        Ui.dp(context, 18), Ui.dp(context, 18), Gravity.CENTER));
            }
            LinearLayout.LayoutParams checkParams = new LayoutParams(
                    Ui.dp(context, 22), Ui.dp(context, 22));
            checkParams.leftMargin = Ui.dp(context, 12);
            card.addView(check, checkParams);
        } else if (selected) {
            View dot = new View(context);
            dot.setBackground(Ui.rounded(context, tokens.accent, 4));
            LinearLayout.LayoutParams dotParams = new LayoutParams(
                    Ui.dp(context, 8), Ui.dp(context, 8));
            dotParams.leftMargin = Ui.dp(context, 12);
            card.addView(dot, dotParams);
        }
    }

    /** Shows the per-model action sheet (modify, enter multi-select) for a long-pressed card. */
    private void showActions(ModelConfig model) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout panel = sheetPanel(tokens);
        handle(panel, tokens);
        sheetTitle(panel, tokens, model.displayName().isEmpty()
                ? context.getString(R.string.screen_models_title) : model.displayName());
        divider(panel, tokens);
        Dialog[] holder = new Dialog[1];
        sheetAction(panel, tokens, context.getString(R.string.screen_models_action_modify),
                context.getString(R.string.screen_models_action_modify_desc), () -> {
                    if (holder[0] != null) holder[0].dismiss();
                    listener.onEditModel(model.id);
                });
        sheetAction(panel, tokens, context.getString(R.string.screen_models_action_multi_select),
                context.getString(R.string.screen_models_action_multi_select_desc), () -> {
                    if (holder[0] != null) holder[0].dismiss();
                    multiSelected.clear();
                    multiSelected.add(model.id);
                    renderHeader();
                    renderList();
                });
        bottomInset(panel);
        holder[0] = Sheets.showBottom(context, tokens, panel);
    }

    /**
     * Shows the delete confirmation sheet for the current multi-selection. The optimistic local
     * removal after the listener callback keeps the list responsive; the next {@link #onShown}
     * refresh reconciles with the store.
     */
    private void confirmDelete() {
        if (multiSelected.isEmpty()) return;
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout panel = sheetPanel(tokens);
        handle(panel, tokens);
        sheetTitle(panel, tokens, context.getString(R.string.screen_models_delete_title));
        TextView desc = Ui.text(context,
                context.getString(R.string.screen_models_delete_message, multiSelected.size()),
                Tokens.FONT_SM, tokens.textTertiary, Typeface.NORMAL);
        Ui.padding(desc, 16, 0, 16, 12);
        panel.addView(desc, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        divider(panel, tokens);
        final Dialog[] holder = new Dialog[1];
        sheetAction(panel, tokens, context.getString(R.string.common_cancel), "",
                () -> { if (holder[0] != null) holder[0].dismiss(); });
        ArrayList<String> ids = new ArrayList<>(multiSelected);
        sheetAction(panel, tokens, context.getString(R.string.common_delete),
                context.getString(R.string.screen_models_delete_warning), () -> {
                    if (holder[0] != null) holder[0].dismiss();
                    listener.onDeleteModels(ids);
                    for (int i = models.size() - 1; i >= 0; i--) {
                        if (ids.contains(models.get(i).id)) models.remove(i);
                    }
                    multiSelected.clear();
                    renderHeader();
                    renderList();
                }, tokens.danger);
        bottomInset(panel);
        holder[0] = Sheets.showBottom(context, tokens, panel);
    }

    /** Toggles one id in the multi-selection and rebuilds header and list. */
    private void toggleSelected(String id) {
        if (!multiSelected.remove(id)) multiSelected.add(id);
        renderHeader();
        renderList();
    }

    /** Builds the rounded-top panel shared by every sheet this page shows. */
    private LinearLayout sheetPanel(Tokens tokens) {
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(VERTICAL);
        panel.setBackground(Ui.roundedTop(getContext(), tokens.surfaceElevated, 16));
        return panel;
    }

    /** Adds the drag handle pill at the top of a sheet panel. */
    private void handle(LinearLayout panel, Tokens tokens) {
        View handle = new View(getContext());
        handle.setBackground(Ui.rounded(getContext(), tokens.textTertiary, 2));
        LinearLayout.LayoutParams params = new LayoutParams(Ui.dp(getContext(), 36), Ui.dp(getContext(), 4));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = Ui.dp(getContext(), 8);
        params.bottomMargin = Ui.dp(getContext(), 4);
        panel.addView(handle, params);
    }

    /** Adds the bold sheet heading. */
    private void sheetTitle(LinearLayout panel, Tokens tokens, String value) {
        TextView title = Ui.bold(getContext(), value, 17, tokens.text);
        Ui.padding(title, 16, 0, 16, 12);
        panel.addView(title, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    /** Adds the hairline divider under a sheet heading. */
    private void divider(LinearLayout panel, Tokens tokens) {
        View divider = new View(getContext());
        divider.setBackgroundColor(tokens.borderLight);
        panel.addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));
    }

    /** Adds a tappable action row with default text color; see the full overload for details. */
    private void sheetAction(LinearLayout panel, Tokens tokens, String label, String desc, Runnable action) {
        sheetAction(panel, tokens, label, desc, action, tokens.text);
    }

    /**
     * Adds a tappable action row with an optional secondary description line.
     *
     * @param panel sheet panel receiving the row
     * @param tokens palette snapshot
     * @param label primary action text
     * @param desc secondary text; empty hides the line
     * @param action run on tap; the caller dismisses the sheet itself
     * @param labelColor color of the primary text, allowing danger-tinted rows
     */
    private void sheetAction(LinearLayout panel, Tokens tokens, String label, String desc,
                             Runnable action, int labelColor) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> action.run());
        Ui.padding(row, 16, 14, 16, 14);
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        row.addView(labels, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        labels.addView(Ui.text(context, label, Tokens.FONT_MD, labelColor, Typeface.NORMAL),
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        if (desc != null && desc.length() > 0) {
            TextView description = Ui.text(context, desc, Tokens.FONT_XS,
                    tokens.textTertiary, Typeface.NORMAL);
            LinearLayout.LayoutParams descParams = new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            descParams.topMargin = Ui.dp(context, 2);
            labels.addView(description, descParams);
        }
        panel.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    /** Applies the bottom inset that keeps sheet content above the navigation gesture area. */
    private void bottomInset(LinearLayout panel) {
        panel.setPadding(0, 0, 0, Ui.dp(getContext(), 12));
    }

    /**
     * Resolves the badge caption for a model: provider label when present and non-custom, otherwise
     * the protocol name.
     */
    private String provider(ModelConfig model) {
        String custom = getContext().getString(R.string.model_provider_custom);
        String label = model.providerLabel == null || model.providerLabel.isEmpty()
                ? (model.providerId == null ? "" : model.providerId) : model.providerLabel;
        if (label.isEmpty() || custom.equals(label)) return protocolLabel(model.protocol);
        return label;
    }

    /** Maps a protocol to its short badge caption; unknown values fall back to OpenAI. */
    private static String protocolLabel(ModelConfig.Protocol protocol) {
        switch (protocol) {
            case CODEX_RESPONSES: return "Codex";
            case ANTHROPIC_MESSAGES: return "Anthropic";
            case LOCAL_GGUF: return "Local";
            case OPENAI_COMPATIBLE:
            default: return "OpenAI";
        }
    }

    /** Maps a protocol to its badge fill color; unknown values fall back to the OpenAI tone. */
    private static int badgeColor(ModelConfig.Protocol protocol) {
        switch (protocol) {
            case CODEX_RESPONSES: return Color.parseColor("#4B8BFF");
            case ANTHROPIC_MESSAGES: return Color.parseColor("#B86F50");
            case LOCAL_GGUF: return Color.parseColor("#2E7D62");
            case OPENAI_COMPATIBLE:
            default: return Color.parseColor("#10A37F");
        }
    }
}
