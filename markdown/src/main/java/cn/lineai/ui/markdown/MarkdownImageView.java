package cn.lineai.ui.markdown;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.markdown.R;
import java.io.File;
import java.util.Locale;

public final class MarkdownImageView extends LinearLayout {
    public MarkdownImageView(Context context, String destination, String altText) {
        super(context);
        setOrientation(VERTICAL);
        String url = destination == null ? "" : destination.trim();
        Bitmap bitmap = decodeBitmap(url);
        if (bitmap == null) {
            String imageLabel = context.getString(R.string.markdown_image_label);
            String fallbackText = altText == null || altText.trim().length() == 0
                    ? imageLabel
                    : imageLabel.substring(0, imageLabel.length() - 1) + ": " + altText.trim() + "]";
            TextView fallback = LineTheme.text(context,
                    fallbackText,
                    LineTheme.FONT_SM,
                    LineTheme.TEXT_TERTIARY,
                    Typeface.NORMAL);
            addView(fallback, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        ImageView image = new ImageView(context);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setMaxHeight(LineTheme.dp(context, 520));
        image.setImageBitmap(bitmap);
        addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        String caption = altText == null ? "" : altText.trim();
        if (caption.length() > 0 && caption.length() <= 120) {
            TextView text = LineTheme.text(context, caption, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.topMargin = LineTheme.dp(context, 4);
            addView(text, params);
        }
    }

    private Bitmap decodeBitmap(String url) {
        try {
            if (url.toLowerCase(Locale.ROOT).startsWith("data:image/")) {
                int comma = url.indexOf(',');
                if (comma < 0) {
                    return null;
                }
                byte[] bytes = decodeBase64(url.substring(comma + 1));
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
            String path = url.startsWith("file://") ? Uri.parse(url).getPath() : url;
            if (path == null || !path.startsWith("/")) {
                return null;
            }
            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                return null;
            }
            return BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] decodeBase64(String value) {
        try {
            return android.util.Base64.decode(value, android.util.Base64.DEFAULT);
        } catch (IllegalArgumentException ignored) {
            return android.util.Base64.decode(value, android.util.Base64.URL_SAFE);
        }
    }
}
