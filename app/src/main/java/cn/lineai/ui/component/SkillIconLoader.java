package cn.lineai.ui.component;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import cn.lineai.data.service.SkillHubClient;

final class SkillIconLoader {
    private static final int ICON_SIZE_PX = 192;
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(32);
    private final SkillHubClient client = new SkillHubClient();
    private final Handler main = new Handler(Looper.getMainLooper());

    void load(String url, ImageView target) {
        String value = url == null ? "" : url.trim();
        target.setTag(value);
        if (value.length() == 0) {
            return;
        }
        Bitmap cached = CACHE.get(value);
        if (cached != null) {
            target.setImageBitmap(cached);
            target.clearColorFilter();
            return;
        }
        new Thread(() -> {
            try {
                Bitmap bitmap = decode(client.icon(value));
                if (bitmap == null) {
                    return;
                }
                CACHE.put(value, bitmap);
                main.post(() -> {
                    if (value.equals(target.getTag())) {
                        target.setImageBitmap(bitmap);
                        target.clearColorFilter();
                    }
                });
            } catch (Exception ignored) {
                // 保留项目图标占位，不让单个远程图标影响商店内容。
            }
        }, "skillhub-icon").start();
    }

    private static Bitmap decode(byte[] bytes) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int sampleSize = 1;
        while (bounds.outWidth / sampleSize > ICON_SIZE_PX * 2
                || bounds.outHeight / sampleSize > ICON_SIZE_PX * 2) {
            sampleSize *= 2;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }
}
