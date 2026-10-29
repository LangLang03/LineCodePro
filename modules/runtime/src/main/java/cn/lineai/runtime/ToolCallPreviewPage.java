package cn.lineai.runtime;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.feature.chat.toolcall.ToolCallCardView;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Tool call card preview: renders one real card per category with fake data.
 *
 * <p>Every sample goes through the same {@link ToolCallCardView} the chat timeline uses, so the
 * page is a visual regression harness rather than a second implementation of the cards. The fake
 * diff lookup keeps the write/edit/diff samples meaningful without touching the diff store.</p>
 */
public final class ToolCallPreviewPage extends SettingsPage {

    private final List<ToolCallCardView> cards = new ArrayList<>();
    private final List<TextView> headings = new ArrayList<>();
    private TextView note;

    public ToolCallPreviewPage(Context context, Tokens tokens, Runnable onBack) {
        super(context, R.string.screen_toolcall_preview_title, tokens, onBack);
        Ui.padding(content, 0, 0, 0, 100);

        note = Ui.text(context, context.getString(R.string.toolcall_preview_section_note),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        Ui.padding(note, 16, 12, 16, 0);
        content.addView(note, new LayoutParams(-1, -2));

        final cn.lineai.feature.chat.ToolPresentationProvider provider =
                ToolPreviewSamples::presentation;
        int lastCategory = 0;
        for (ToolPreviewSamples.Sample sample : ToolPreviewSamples.all()) {
            if (sample.category != lastCategory) {
                addHeading(sample.category);
                lastCategory = sample.category;
            }
            addCard(sample, provider);
        }
        theme.bind(this::applyTheme);
    }

    private void addHeading(int categoryRes) {
        TextView heading = Ui.bold(getContext(),
                getContext().getString(categoryRes), Tokens.FONT_XS,
                theme.current().textSecondary);
        heading.setLetterSpacing(0.05f);
        Ui.padding(heading, 16, 12, 16, 4);
        content.addView(heading, new LayoutParams(-1, -2));
        headings.add(heading);
    }

    private void addCard(ToolPreviewSamples.Sample sample,
                         cn.lineai.feature.chat.ToolPresentationProvider provider) {
        ToolCallCardView card = new ToolCallCardView(getContext(), theme.current());
        card.setProvider(provider);
        card.setDiffLookup(ToolPreviewSamples.diffLookup());
        card.bind(sample.entry, theme.current());
        card.setExpanded(sample.expanded);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.leftMargin = Ui.dp(getContext(), 16);
        params.rightMargin = Ui.dp(getContext(), 16);
        params.bottomMargin = Ui.dp(getContext(), 8);
        content.addView(card, params);
        cards.add(card);
    }

    private void applyTheme(Tokens tokens) {
        for (ToolCallCardView card : cards) card.applyTokens(tokens);
        note.setTextColor(tokens.textTertiary);
        for (TextView heading : headings) heading.setTextColor(tokens.textSecondary);
    }
}
