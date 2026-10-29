package cn.lineai.feature.chat.toolcall;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import cn.lineai.feature.chat.ToolPresentationProvider;
import cn.lineai.ui.kit.DotMatrixSpinner;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.markdown.MarkdownLite;
import cn.lineai.ui.tokens.Tokens;

/**
 * Frame handed to one tool renderer: a bare legacy-style header row plus an expandable body.
 *
 * <p>Matches {@code BaseToolCallView}/{@code ToolCallReadView}: the card itself is transparent,
 * the header is a 48dp row with a bare 16dp glyph in a 24x32 slot, a 14sp secondary label and an
 * optional 14dp chevron. Details live in 12dp-radius code boxes. Renderers only build views and
 * register token appliers; the card owns the disclosure state.</p>
 */
final class ToolCardFrame {

    /** A colour that depends on the current theme tokens. */
    interface Tint {
        int color(Tokens tokens);
    }

    private final Context context;
    private final ToolCallCardView card;
    private final List<Runnable> appliers;
    final LinearLayout header;
    final LinearLayout body;
    private Tokens tokens;
    private boolean bodyForced;

    ToolCardFrame(Context context, Tokens tokens, ToolCallCardView card, LinearLayout header,
                  LinearLayout body, List<Runnable> appliers) {
        this.context = context;
        this.tokens = tokens;
        this.card = card;
        this.header = header;
        this.body = body;
        this.appliers = appliers;
    }

    Context context() {
        return context;
    }

    Tokens tokens() {
        return tokens;
    }

    ToolPresentationProvider provider() {
        return card.provider();
    }

    ToolDiffLookup diffLookup() {
        return card.diffLookup();
    }

    boolean expanded() {
        return card.isExpanded();
    }

    void requestToggle() {
        card.toggleExpanded();
    }

    void addTokenApplier(Runnable applier) {
        if (applier == null) return;
        applier.run();
        appliers.add(applier);
    }

    void applyTokens(Tokens next) {
        tokens = next;
        card.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        for (Runnable applier : appliers) applier.run();
    }

    void showBodyAlways() {
        bodyForced = true;
    }

    void finish() {
        body.setVisibility(bodyForced || (card.isExpanded() && body.getChildCount() > 0)
                ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- builders

    LinearLayout row() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    LinearLayout column() {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    TextView text(CharSequence value, int sizeSp, final Tint tint, int style) {
        final TextView view = Ui.text(context, value, sizeSp, tint.color(tokens), style);
        addTokenApplier(() -> view.setTextColor(tint.color(tokens)));
        return view;
    }

    TextView mono(CharSequence value, int sizeSp, final Tint tint, boolean singleLine) {
        final TextView view = text(value, sizeSp, tint, Typeface.NORMAL);
        view.setTypeface(Typeface.MONOSPACE);
        view.setSingleLine(singleLine);
        return view;
    }

    /** Legacy icon slot: 24x32 container with a bare 16dp glyph. */
    LinearLayout iconSlot(int iconType, final boolean failed) {
        final IconButton glyph = new IconButton(context, iconType);
        glyph.setIconSizeDp(24, 16);
        glyph.setClickable(false);
        final LinearLayout slot = new LinearLayout(context);
        slot.setGravity(Gravity.CENTER);
        slot.addView(glyph, new LinearLayout.LayoutParams(Ui.dp(context, 24),
                Ui.dp(context, 32)));
        addTokenApplier(() -> glyph.setIconColor(failed ? tokens.danger : tokens.textSecondary));
        return slot;
    }

    /** Legacy header label: 14sp secondary (danger on failure), single line, 6dp leading margin. */
    TextView headerLabel(CharSequence label, final boolean failed, boolean middleEllipsis) {
        final TextView view = Ui.text(context, label, 14,
                failed ? tokens.danger : tokens.textSecondary, Typeface.NORMAL);
        view.setSingleLine(true);
        view.setEllipsize(middleEllipsis ? TextUtils.TruncateAt.MIDDLE
                : TextUtils.TruncateAt.END);
        addTokenApplier(() -> view.setTextColor(failed ? tokens.danger : tokens.textSecondary));
        return view;
    }

    /** 14dp chevron in a 24x32 slot. */
    IconButton chevron(boolean expanded) {
        final IconButton arrow = new IconButton(context,
                expanded ? Icons.CHEVRON_DOWN : Icons.CHEVRON_RIGHT);
        arrow.setIconSizeDp(24, 14);
        arrow.setClickable(false);
        addTokenApplier(() -> arrow.setIconColor(tokens.textTertiary));
        arrow.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(context, 24),
                Ui.dp(context, 32)));
        return arrow;
    }

    /** 28/30dp outlined ring used by the agent and pipeline cards (bot / git-branch). */
    LinearLayout ringWell(int iconType, int glyphDp, int boxDp, final Tint tint,
                          final Tint border) {
        final LinearLayout well = new LinearLayout(context);
        well.setGravity(Gravity.CENTER);
        final IconButton glyph = new IconButton(context, iconType);
        glyph.setIconSizeDp(boxDp, glyphDp);
        glyph.setClickable(false);
        well.addView(glyph, new LinearLayout.LayoutParams(Ui.dp(context, boxDp),
                Ui.dp(context, boxDp)));
        addTokenApplier(() -> {
            well.setBackground(Ui.roundedStroke(context, android.graphics.Color.TRANSPARENT,
                    boxDp / 2f, border.color(tokens)));
            glyph.setIconColor(tint.color(tokens));
        });
        return well;
    }

    View spacer() {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        return view;
    }

    View divider() {
        final View line = new View(context);
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
        addTokenApplier(() -> line.setBackgroundColor(tokens.codeBorder));
        return line;
    }

    /** Bare dot-matrix spinner, the same square walk as the working row, tinted by card status. */
    DotMatrixSpinner spinner(int sizeDp, final Tint tint) {
        final DotMatrixSpinner view = new DotMatrixSpinner(context, sizeDp);
        view.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(context, sizeDp),
                Ui.dp(context, sizeDp)));
        view.start();
        addTokenApplier(() -> view.setColor(tint.color(tokens)));
        return view;
    }

    /**
     * Legacy 48dp header row: bare glyph, 14sp status/target label, optional running spinner and
     * chevron. Clicking the row toggles the body only when {@code expandable} is true.
     */
    LinearLayout bareHeader(int iconType, CharSequence label, boolean failed, boolean expandable,
                            boolean running, boolean middleEllipsis) {
        LinearLayout row = row();
        row.setMinimumHeight(Ui.dp(context, 48));
        row.addView(iconSlot(iconType, failed));
        TextView text = headerLabel(label, failed, middleEllipsis);
        LinearLayout.LayoutParams textParams = grow();
        textParams.leftMargin = Ui.dp(context, 6);
        row.addView(text, textParams);
        if (running) {
            LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
                    Ui.dp(context, 14), Ui.dp(context, 14));
            spinnerParams.rightMargin = Ui.dp(context, 4);
            row.addView(spinner(14, t -> t.accent), spinnerParams);
        }
        if (expandable) {
            row.addView(chevron(expanded()));
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> requestToggle());
        }
        header.addView(row, fill());
        return row;
    }

    /** Code surface: {@code codeBg} fill, 1dp {@code codeBorder}, 12dp radius. */
    LinearLayout codeBox() {
        final LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipToOutline(true);
        addTokenApplier(() -> box.setBackground(
                Ui.roundedStroke(context, tokens.codeBg, 12, tokens.codeBorder)));
        return box;
    }

    void addCodeText(LinearLayout box, CharSequence text, int maxHeightDp, final Tint tint) {
        BoundedScrollView scroll = new BoundedScrollView(context,
                maxHeightDp <= 0 ? 240 : maxHeightDp);
        final TextView view = Ui.text(context, text, Tokens.FONT_SM, tint.color(tokens),
                Typeface.NORMAL);
        view.setTypeface(Typeface.MONOSPACE);
        view.setTextIsSelectable(true);
        view.setLineSpacing(Ui.dp(context, 4), 1f);
        Ui.padding(view, 14, 12, 14, 12);
        addTokenApplier(() -> view.setTextColor(tint.color(tokens)));
        scroll.addView(view, new ScrollView.LayoutParams(-1, -2));
        box.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
    }

    void addScrollable(LinearLayout box, View child, int maxHeightDp) {
        BoundedScrollView scroll = new BoundedScrollView(context,
                maxHeightDp <= 0 ? 240 : maxHeightDp);
        scroll.addView(child, new ScrollView.LayoutParams(-1, -2));
        box.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Wraps a full code card (already {@code codeBox}) in a bounded scroll view. */
    ScrollView bounded(View content, int maxHeightDp) {
        BoundedScrollView scroll = new BoundedScrollView(context,
                maxHeightDp <= 0 ? 240 : maxHeightDp);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    MarkdownLite markdown(CharSequence text) {
        final MarkdownLite view = new MarkdownLite(context, tokens);
        final String value = text == null ? "" : text.toString();
        view.render(value, tokens);
        addTokenApplier(() -> view.render(value, tokens));
        return view;
    }

    /** Small rounded status/type pill; 10sp bold on a bordered surface. */
    TextView pill(CharSequence text, final Tint tint, final Tint background) {
        final TextView view = Ui.text(context, text, 10, tint.color(tokens), Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        Ui.padding(view, 4, 1, 4, 1);
        addTokenApplier(() -> {
            view.setTextColor(tint.color(tokens));
            view.setBackground(Ui.roundedStroke(context, background.color(tokens), 999,
                    tokens.codeBorder));
        });
        return view;
    }

    TextView inputLabel(CharSequence text) {
        return text(text, 11, t -> t.textTertiary, Typeface.BOLD);
    }

    LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(-2, -2);
    }

    LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    LinearLayout.LayoutParams grow() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    LinearLayout.LayoutParams withTop(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.dp(context, topDp);
        return params;
    }

    LinearLayout.LayoutParams withPadding(int leftDp, int topDp, int rightDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.leftMargin = Ui.dp(context, leftDp);
        params.topMargin = Ui.dp(context, topDp);
        params.rightMargin = Ui.dp(context, rightDp);
        params.bottomMargin = Ui.dp(context, bottomDp);
        return params;
    }
}
