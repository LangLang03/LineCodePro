package cn.lineai.ui.markdown;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.lineai.core.model.ThemePalette;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MarkdownLiteTest {
    private Context context;
    private Tokens dark;
    private Tokens light;
    private MarkdownLite markdown;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        dark = Tokens.of(ThemePalette.dark());
        light = Tokens.of(ThemePalette.light());
        markdown = new MarkdownLite(context, dark);
    }

    @Test
    public void paragraphAndAllHeadingsMatchLegacyMetrics() {
        render("body\nsecond line\n\n# h1\n\n## h2\n\n### h3\n\n#### h4\n\n##### h5\n\n###### h6");
        assertEquals(7, markdown.getChildCount());
        TextView body = (TextView) markdown.getChildAt(0);
        assertEquals("body\nsecond line", body.getText().toString());
        metrics(body, 16, 7, 2, 18);
        int[] sizes = {28, 24, 20, 16, 16, 16};
        for (int i = 1; i <= 6; i++) {
            TextView heading = (TextView) markdown.getChildAt(i);
            metrics(heading, sizes[i - 1], 5, 8, 20);
            assertEquals(Typeface.create("sans-serif-medium", Typeface.NORMAL), heading.getTypeface());
        }
    }

    @Test
    public void mixedNestedListsKeepTreeMarkersAndStartingNumber() {
        render("3) parent\n   - child\n     - grandchild\n4) next");
        LinearLayout list = (LinearLayout) markdown.getChildAt(0);
        assertEquals(2, list.getChildCount());
        assertEquals(Arrays.asList("3.", "parent", "-", "child", "+", "grandchild", "4.", "next"), texts(markdown));
        margins(list, 1, 7);
        TextView parentMarker = text("3.");
        TextView childMarker = text("-");
        TextView grandMarker = text("+");
        assertEquals(dp(22), parentMarker.getLayoutParams().width);
        assertEquals(dp(26), childMarker.getLayoutParams().width);
        assertEquals(dp(30), grandMarker.getLayoutParams().width);
        assertEquals(dp(8), ((LinearLayout.LayoutParams) childMarker.getLayoutParams()).rightMargin);
        assertNotSame(text("parent").getParent(), text("child").getParent());
        margins((View) parentMarker.getParent(), 0, 3);
        metrics(text("parent"), 16, 7, 0, 8);
    }

    @Test
    public void looseListsRetainParagraphsQuotesAndNestedBlocks() {
        render("- first\n\n  second paragraph\n\n  > ## quoted\n  >\n  > - inner\n  >\n  >     ```txt\n  >     code\n  >     ```\n\n- last");
        assertTrue(texts(markdown).containsAll(Arrays.asList("first", "second paragraph", "quoted", "inner", "last")));
        assertEquals(1, descendants(markdown, CodeBlockView.class).size());
        metrics(text("quoted"), 24, 5, 4, 10);
        metrics(text("second paragraph"), 16, 7, 0, 8);
    }

    @Test
    public void quoteRailAndChildrenHaveOriginalSpacing() {
        render("> paragraph\n>\n> > nested");
        LinearLayout quote = (LinearLayout) markdown.getChildAt(0);
        margins(quote, 3, 8);
        View rail = quote.getChildAt(0);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rail.getLayoutParams();
        assertEquals(dp(3), params.width);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.height);
        assertEquals(dp(12), params.rightMargin);
        assertEquals(dp(2), ((GradientDrawable) rail.getBackground()).getCornerRadius(), 0f);
        assertEquals(dark.text, text("paragraph").getCurrentTextColor());
        assertNotSame(text("paragraph").getParent(), text("nested").getParent());
    }

    @Test
    public void tablesKeepAlignmentInlineStylesDimensionsAndZebraColours() {
        render("| left | middle | right |\n| :--- | :---: | ---: |\n| **bold** | `code` | [web](https://example.com) |\n| a | b | c |");
        HorizontalScrollView scroll = (HorizontalScrollView) markdown.getChildAt(0);
        margins(scroll, 4, 8);
        assertTrue(scroll.isFillViewport());
        assertTrue(scroll.getClipToOutline());
        TableLayout table = descendants(markdown, TableLayout.class).get(0);
        assertEquals(3, table.getChildCount());
        assertEquals(3, ((ViewGroup) table.getChildAt(0)).getChildCount());
        assertEquals(Gravity.CENTER_HORIZONTAL, text("middle").getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK);
        assertEquals(Gravity.END, text("right").getGravity() & Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK);
        TextView header = text("left");
        assertTrue(header.getTypeface().isBold());
        assertEquals(13f * context.getResources().getDisplayMetrics().scaledDensity, header.getTextSize(), .01f);
        assertEquals(dp(196), header.getMaxWidth());
        View cell = (View) header.getParent();
        assertEquals(dp(84), cell.getMinimumWidth());
        assertEquals(dp(38), cell.getMinimumHeight());
        assertEquals(dp(12), cell.getPaddingLeft());
        assertEquals(dp(8), cell.getPaddingTop());
        assertEquals(dark.surfaceLight, fill(cell));
        assertEquals(dark.surface, fill((View) text("bold").getParent()));
        assertEquals(dark.codeBg, fill((View) text("a").getParent()));
        assertEquals(1, spans(text("bold"), StyleSpan.class).length);
        assertEquals(1, spans(text("web"), ClickableSpan.class).length);
    }

    @Test
    public void tableCanBeNestedInListAndQuote() {
        render("- before\n\n  > | key | value |\n  > | --- | --- |\n  > | **nested** | yes |\n\n  after");
        assertEquals(1, descendants(markdown, TableLayout.class).size());
        assertTrue(texts(markdown).containsAll(Arrays.asList("before", "key", "value", "nested", "yes", "after")));
        assertEquals(1, spans(text("nested"), StyleSpan.class).length);
    }

    @Test
    public void astRendersNestedInlineStylesEscapesEntitiesAndBreaks() {
        render("**bold *both*** and _italic_ and ``a`b`` \\*literal\\* &amp;\nsoft  \nhard <b>raw</b>");
        TextView text = (TextView) markdown.getChildAt(0);
        assertEquals("bold both and italic and a`b *literal* &\nsoft\nhard <b>raw</b>", text.getText().toString());
        StyleSpan[] styles = spans(text, StyleSpan.class);
        assertEquals(3, styles.length);
        Spanned content = (Spanned) text.getText();
        int both = text.getText().toString().indexOf("both");
        assertEquals(2, content.getSpans(both, both + 4, StyleSpan.class).length);
        assertEquals(1, spans(text, TypefaceSpan.class).length);
        assertNull(text.getMovementMethod());
    }

    @Test
    public void malformedDelimitersDoNotConsumeOrdinaryText() {
        String[] examples = {"prefix **unfinished", "a_b_c", "[label](unfinished", "![alt](unfinished",
                "literal ` unmatched", "text #not-heading", "[not a reference]", "***unfinished", "1.not-a-list"};
        for (String example : examples) {
            render(example);
            assertEquals(example, ((TextView) markdown.getChildAt(0)).getText().toString());
        }
    }

    @Test
    public void commonmarkRecognizesSetextRulesAndAllCodeForms() {
        render("Setext\n======\n\n***\n\n~~~java options\na < b\n~~~\n\n    indented\n\n```txt\nunclosed");
        assertEquals(5, markdown.getChildCount());
        metrics((TextView) markdown.getChildAt(0), 28, 5, 8, 20);
        View rule = markdown.getChildAt(1);
        margins(rule, 8, 8);
        assertEquals(Math.max(1, dp(1)), rule.getLayoutParams().height);
        assertEquals(3, descendants(markdown, CodeBlockView.class).size());
        assertNotNull(text("java"));
        assertNotNull(text("a < b\n"));
        assertNotNull(text("indented\n"));
        assertNotNull(text("unclosed\n"));
    }

    @Test
    public void onlyHttpAndHttpsLinksGetClickableSpans() {
        render("[web](https://example.com/path) [http](HTTP://example.com) "
                + "[intent](intent://host) [file](file:///tmp/a) [js](javascript:alert) "
                + "[mail](mailto:a@example.com) [relative](/path) [bad](https:///missing) "
                + "[encoded](jav&#x61;script:alert) [user](https://name@example.com)");
        TextView text = (TextView) markdown.getChildAt(0);
        assertEquals("web http intent file js mail relative bad encoded user", text.getText().toString());
        ClickableSpan[] links = spans(text, ClickableSpan.class);
        assertEquals(2, links.length);
        List<String> opened = new ArrayList<>();
        markdown.setLinkHandler(opened::add);
        for (ClickableSpan link : links) {
            link.onClick(text);
        }
        assertEquals(Arrays.asList("https://example.com/path", "HTTP://example.com"), opened);
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity());
        assertFalse(InlineContent.isWebLink("https://example.com\\@evil.test"));
        assertFalse(InlineContent.isWebLink("https://example.com\n"));
        assertFalse(InlineContent.isWebLink("//example.com"));
    }

    @Test
    public void changingHandlerUsesExistingSpansAndDefaultBrowserIsSafe() {
        render("<https://example.com>");
        TextView text = (TextView) markdown.getChildAt(0);
        ClickableSpan link = spans(text, ClickableSpan.class)[0];
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        markdown.setLinkHandler(first::add);
        link.onClick(text);
        markdown.setLinkHandler(second::add);
        link.onClick(text);
        assertEquals(first, second);
        assertEquals(1, second.size());
        assertSame(link, spans(text, ClickableSpan.class)[0]);
        markdown.setLinkHandler(null);
        link.onClick(text);
        Intent intent = shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("https://example.com", intent.getDataString());
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE));
    }

    @Test
    public void imageFallbackPreservesAltAndSafeDestination() {
        render("before ![a **diagram**](https://example.com/image.png) "
                + "![blocked](file:///tmp/image.png) ![](https://example.com/empty.png) after");
        TextView text = (TextView) markdown.getChildAt(0);
        assertEquals("before a diagram blocked Image after", text.getText().toString());
        assertEquals(2, spans(text, ClickableSpan.class).length);
        List<String> links = new ArrayList<>();
        markdown.setLinkHandler(links::add);
        spans(text, ClickableSpan.class)[0].onClick(text);
        assertEquals("https://example.com/image.png", links.get(0));
        render("[![alt](https://example.com/image.png)](https://example.com/page)");
        text = (TextView) markdown.getChildAt(0);
        assertEquals(1, spans(text, ClickableSpan.class).length);
        spans(text, ClickableSpan.class)[0].onClick(text);
        assertEquals("https://example.com/page", links.get(1));
    }

    @Test
    public void codeWrapRemeasuresWithoutReplacingViewsOrLosingCopy() {
        String code = "abcdefghijklmnopqrstuvwxyz".repeat(12) + "\nsecond line\n";
        String source = "```java\n" + code + "```";
        render(source);
        measure(markdown, 240);
        CodeBlockView block = descendants(markdown, CodeBlockView.class).get(0);
        TextView body = text(code);
        IconButton copy = descendants(block, IconButton.class).get(0);
        HorizontalScrollView scroll = descendants(block, HorizontalScrollView.class).get(0);
        int lines = body.getLineCount();
        int height = block.getMeasuredHeight();
        assertTrue(body.getMeasuredWidth() > scroll.getMeasuredWidth());
        assertEquals(dp(16), block.getPaddingLeft());
        assertEquals(dp(8), block.getPaddingTop());
        assertEquals(dp(48), copy.getLayoutParams().width);
        assertEquals(dp(6), body.getLineSpacingExtra(), 0f);
        List<View> original = descendants(markdown, View.class);
        markdown.setCodeWrapEnabled(true);
        measure(markdown, 240);
        assertEquals(original, descendants(markdown, View.class));
        assertTrue(body.getLineCount() > lines);
        assertTrue(block.getMeasuredHeight() > height);
        assertTrue(body.getMeasuredWidth() <= scroll.getMeasuredWidth());
        assertTrue(body.isTextSelectable());
        copy.performClick();
        assertEquals(code, clipboardText());
        markdown.render(source, light);
        markdown.setCodeWrapEnabled(false);
        measure(markdown, 240);
        assertEquals(original, descendants(markdown, View.class));
        assertEquals(lines, body.getLineCount());
        assertEquals(height, block.getMeasuredHeight());
        copy.performClick();
        assertEquals(code, clipboardText());
    }

    @Test
    public void wrapSetBeforeRenderAndNestedCodeRespondToWidthChanges() {
        markdown.setCodeWrapEnabled(true);
        String code = "longword".repeat(50) + "\n";
        render("> ```\n> " + code + "> ```");
        measure(markdown, 320);
        TextView body = text(code);
        int widerLines = body.getLineCount();
        measure(markdown, 160);
        assertTrue(body.getLineCount() > widerLines);
        markdown.setCodeWrapEnabled(false);
        measure(markdown, 160);
        assertEquals(2, body.getLineCount());
    }

    @Test
    public void identicalRenderAndThemeRetainEveryViewAndSpannable() {
        String source = "# title\n\ntext `code` [web](https://example.com)\n\n"
                + "- item\n  - child\n\n> quote\n\n```txt\ncode\n```\n\n"
                + "| a | b |\n| --- | --- |\n| c | d |\n| e | f |";
        render(source);
        List<View> views = descendants(markdown, View.class);
        TextView body = text("text code web");
        CharSequence originalText = body.getText();
        markdown.render(new String(source), dark);
        assertEquals(views, descendants(markdown, View.class));
        assertSame(originalText, body.getText());
        GradientDrawable cellBackground = (GradientDrawable) ((View) text("a").getParent()).getBackground();
        markdown.render(source, light);
        assertEquals(views, descendants(markdown, View.class));
        assertSame(originalText, body.getText());
        assertEquals(light.text, body.getCurrentTextColor());
        assertEquals(light.textSecondary, text("c").getCurrentTextColor());
        assertSame(cellBackground, ((View) text("a").getParent()).getBackground());
        assertEquals(light.surfaceLight, fill((View) text("a").getParent()));
        assertEquals(light.surface, fill((View) text("c").getParent()));
        assertEquals(light.codeBg, fill((View) text("e").getParent()));
        assertEquals(light.codeBg, fill(descendants(markdown, CodeBlockView.class).get(0)));
        TextPaint codePaint = drawAt(body, 6);
        assertEquals(light.codeBg, codePaint.bgColor);
        assertEquals(light.text, codePaint.getColor());
        assertEquals(light.accent, drawAt(body, 11).getColor());
    }

    @Test
    public void themePreservesOuterAndHorizontalScrollOffsets() {
        String source = "paragraph\n\n".repeat(15) + "```\n" + "wide".repeat(200) + "\n```\n\n"
                + "| left | middle | right |\n| --- | --- | --- |\n| a | b | c |";
        render(source);
        ScrollView outer = new ScrollView(context);
        outer.addView(markdown);
        outer.measure(exact(220), exact(180));
        outer.layout(0, 0, 220, 180);
        outer.scrollTo(0, 100);
        List<HorizontalScrollView> horizontal = descendants(markdown, HorizontalScrollView.class);
        for (HorizontalScrollView scroll : horizontal) {
            scroll.scrollTo(30, 0);
            assertEquals(30, scroll.getScrollX());
        }
        markdown.render(source, light);
        outer.measure(exact(220), exact(180));
        outer.layout(0, 0, 220, 180);
        assertEquals(100, outer.getScrollY());
        for (HorizontalScrollView scroll : horizontal) {
            assertEquals(30, scroll.getScrollX());
        }
    }

    @Test
    public void streamingKeepsUnchangedPrefixAndReferencesInvalidateDependents() {
        render("stable\n\n[link][ref]\n\n[ref]: https://one.example");
        View prefix = markdown.getChildAt(0);
        TextView oldLink = text("link");
        render("stable\n\n[link][ref]\n\n[ref]: https://two.example\n\nnew");
        assertSame(prefix, markdown.getChildAt(0));
        assertNotSame(oldLink, text("link"));
        List<String> opened = new ArrayList<>();
        markdown.setLinkHandler(opened::add);
        spans(text("link"), ClickableSpan.class)[0].onClick(text("link"));
        assertEquals(Arrays.asList("https://two.example"), opened);
        render("stable\n\n[link][ref]\n\n[ref]: javascript:alert");
        assertEquals(0, spans(text("link"), ClickableSpan.class).length);
        assertEquals(2, markdown.getChildCount());
    }

    @Test
    public void nullAndEmptyInputLeaveNoStaleViews() {
        render("first");
        markdown.render(null, dark);
        assertEquals(0, markdown.getChildCount());
        markdown.render("", light);
        assertEquals(0, markdown.getChildCount());
        render("new");
        assertEquals(1, markdown.getChildCount());
    }

    private void render(String source) {
        markdown.render(source, dark);
    }

    private TextView text(String expected) {
        for (TextView view : descendants(markdown, TextView.class)) {
            if (expected.equals(view.getText().toString())) {
                return view;
            }
        }
        throw new AssertionError("Missing text: " + expected + "; found " + texts(markdown));
    }

    private static List<String> texts(View root) {
        List<String> texts = new ArrayList<>();
        for (TextView view : descendants(root, TextView.class)) {
            texts.add(view.getText().toString());
        }
        return texts;
    }

    private static <T> List<T> descendants(View root, Class<T> type) {
        List<T> views = new ArrayList<>();
        if (type.isInstance(root)) {
            views.add(type.cast(root));
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                views.addAll(descendants(group.getChildAt(i), type));
            }
        }
        return views;
    }

    private static <T> T[] spans(TextView text, Class<T> type) {
        Spanned value = (Spanned) text.getText();
        return value.getSpans(0, value.length(), type);
    }

    private TextPaint drawAt(TextView view, int index) {
        TextPaint paint = new TextPaint();
        Spanned text = (Spanned) view.getText();
        for (CharacterStyle span : text.getSpans(index, index + 1, CharacterStyle.class)) {
            span.updateDrawState(paint);
        }
        return paint;
    }

    private void metrics(TextView view, int sp, int lineDp, int top, int bottom) {
        assertEquals(sp * context.getResources().getDisplayMetrics().scaledDensity, view.getTextSize(), .01f);
        assertEquals(dp(lineDp), view.getLineSpacingExtra(), 0f);
        assertFalse(view.getIncludeFontPadding());
        margins(view, top, bottom);
    }

    private void margins(View view, int top, int bottom) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        assertEquals(dp(top), params.topMargin);
        assertEquals(dp(bottom), params.bottomMargin);
    }

    private int dp(int value) {
        return Ui.dp(context, value);
    }

    private int fill(View view) {
        return ((GradientDrawable) view.getBackground()).getColor().getDefaultColor();
    }

    private void measure(View view, int width) {
        view.measure(exact(width), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, width, view.getMeasuredHeight());
    }

    private static int exact(int value) {
        return View.MeasureSpec.makeMeasureSpec(value, View.MeasureSpec.EXACTLY);
    }

    private String clipboardText() {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        return clipboard.getPrimaryClip().getItemAt(0).getText().toString();
    }
}
