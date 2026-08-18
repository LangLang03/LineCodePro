package cn.lineai.data.service;

import android.content.Context;
import cn.lineai.resource.ResourceProvider;
import java.io.InputStream;

/**
 * ResourceProvider backed by Android Context.
 *
 * Lets data-layer classes (services/repositories) read string resources without
 * holding a Context themselves: the UI layer constructs this provider and
 * injects it, mirroring {@code MainDependencies}' wiring pattern.
 */
public final class ContextResourceProvider implements ResourceProvider {
    private final Context context;

    public ContextResourceProvider(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public InputStream openAsset(String path) {
        try {
            return context.getAssets().open(path);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String getString(int resId) {
        return context.getString(resId);
    }

    @Override
    public String getString(int resId, Object... formatArgs) {
        return context.getString(resId, formatArgs);
    }
}