package cn.lineai.feature.extensions;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.engine.skillhub.SkillHubApi;

/**
 * Loads SkillHub artwork off the UI thread and caches it per store page.
 *
 * <p>Every request is tagged on the target view so a late bitmap can never land on a view that
 * has already been rebound to another skill. The page keeps the default package glyph visible
 * until the bitmap arrives, which is also the fallback when the URL is missing or invalid.</p>
 */
final class SkillIconLoader {

    private static final int ICON_SIZE_PX = 96;

    private final Scheduler scheduler;
    private final SkillHubApi api;
    private final LruCache<String, Bitmap> cache = new LruCache<>(24);

    SkillIconLoader(Scheduler scheduler) {
        this.scheduler = scheduler;
        this.api = new SkillHubApi();
    }

    void load(String rawUrl, ImageView target) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        target.setTag(url);
        if (url.isEmpty()) {
            return;
        }
        Bitmap cached = cache.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        scheduler.io(() -> {
            Bitmap bitmap;
            try {
                bitmap = decode(api.icon(url));
            } catch (Exception error) {
                return;
            }
            if (bitmap == null) {
                return;
            }
            cache.put(url, bitmap);
            scheduler.main(() -> {
                if (url.equals(target.getTag())) {
                    target.setImageBitmap(bitmap);
                }
            });
        });
    }

    private static Bitmap decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
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
