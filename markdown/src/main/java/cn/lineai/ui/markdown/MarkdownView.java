package cn.lineai.ui.markdown;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

public final class MarkdownView extends LinearLayout {
    public interface TextScaleListener {
        void onTextScaleChanged(float scale);
    }

    private static final float MIN_TEXT_SCALE = 0.5f;
    private static final float MAX_TEXT_SCALE = 1.6f;

    private final Parser parser;
    private final MarkdownRenderer renderer;
    private final ScaleGestureDetector scaleDetector;
    private final Map<TextView, Float> baseTextSizes = new IdentityHashMap<>();
    private String lastMarkdown;
    private boolean plainMode;
    private boolean codeWrapEnabled;
    private boolean pinchZoomEnabled;
    private float textScale = 1f;
    private MarkdownLinkHandler linkHandler;
    private TextScaleListener textScaleListener;

    public MarkdownView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipToPadding(false);
        Iterable<Extension> extensions = Collections.singletonList(TablesExtension.create());
        parser = Parser.builder().extensions(extensions).build();
        renderer = new MarkdownRenderer(context);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float next = Math.max(MIN_TEXT_SCALE,
                        Math.min(MAX_TEXT_SCALE, textScale * detector.getScaleFactor()));
                if (Math.abs(next - textScale) < 0.005f) {
                    return true;
                }
                textScale = next;
                applyTextScale(MarkdownView.this);
                if (textScaleListener != null) {
                    textScaleListener.onTextScaleChanged(textScale);
                }
                return true;
            }
        });
    }

    public void setPinchZoomEnabled(boolean enabled) {
        pinchZoomEnabled = enabled;
    }

    public void setTextScale(float scale) {
        float next = Math.max(MIN_TEXT_SCALE, Math.min(MAX_TEXT_SCALE, scale));
        if (Math.abs(next - textScale) < 0.005f) {
            return;
        }
        textScale = next;
        applyTextScale(this);
    }

    public void setTextScaleListener(TextScaleListener listener) {
        textScaleListener = listener;
    }

    public float getTextScale() {
        return textScale;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (pinchZoomEnabled && (event.getPointerCount() > 1 || scaleDetector.isInProgress())) {
            getParent().requestDisallowInterceptTouchEvent(true);
            scaleDetector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    public void setCodeWrapEnabled(boolean enabled) {
        if (codeWrapEnabled == enabled) {
            return;
        }
        codeWrapEnabled = enabled;
        renderer.setCodeWrapEnabled(enabled);
        if (!plainMode) {
            rerender();
        }
    }

    public void setLinkHandler(MarkdownLinkHandler linkHandler) {
        if (this.linkHandler == linkHandler) {
            return;
        }
        this.linkHandler = linkHandler;
        renderer.setLinkHandler(linkHandler);
        if (!plainMode) {
            rerender();
        }
    }

    public void setMarkdown(String markdown) {
        String value = markdown == null ? "" : markdown;
        if (!plainMode && value.equals(lastMarkdown)) {
            return;
        }
        plainMode = false;
        lastMarkdown = value;
        renderValue(value);
    }

    /**
     * 以纯文本（等宽字体）渲染，跳过 Markdown 解析。
     * 用于错误消息、流中断、异常堆栈等不应被按 markup 解析的内容。
     */
    public void setPlainText(String plainText) {
        String value = plainText == null ? "" : plainText;
        plainMode = true;
        lastMarkdown = null;
        removeAllViews();
        if (value.length() == 0) {
            return;
        }
        TextView text = new TextView(getContext());
        text.setTypeface(Typeface.MONOSPACE);
        text.setText(value);
        text.setTextIsSelectable(true);
        addView(text, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void rerender() {
        if (lastMarkdown == null) {
            return;
        }
        renderValue(lastMarkdown);
    }

    private void renderValue(String value) {
        removeAllViews();
        baseTextSizes.clear();
        if (value.trim().length() == 0) {
            return;
        }
        Node document = parser.parse(value);
        renderer.renderInto(this, document);
        if (textScale != 1f) {
            applyTextScale(this);
        }
    }

    private void applyTextScale(View view) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            Float baseSp = baseTextSizes.get(text);
            if (baseSp == null) {
                baseSp = text.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
                baseTextSizes.put(text, baseSp);
            }
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSp * textScale);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTextScale(group.getChildAt(i));
            }
        }
    }
}
