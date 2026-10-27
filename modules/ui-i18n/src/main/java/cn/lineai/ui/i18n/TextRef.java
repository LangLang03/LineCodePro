package cn.lineai.ui.i18n;

import android.content.Context;

import java.util.Arrays;

/** A deferred string lookup: resource id plus format arguments. */
public final class TextRef {

    public final int resourceId;
    public final Object[] args;

    public TextRef(int resourceId, Object... args) {
        this.resourceId = resourceId;
        this.args = args == null ? new Object[0] : args;
    }

    public static TextRef of(int resourceId) {
        return new TextRef(resourceId);
    }

    public static TextRef of(int resourceId, Object... args) {
        return new TextRef(resourceId, args);
    }

    public String resolve(Context context) {
        if (context == null) {
            return "";
        }
        return args.length == 0
                ? context.getString(resourceId)
                : context.getString(resourceId, args);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TextRef)) {
            return false;
        }
        TextRef that = (TextRef) other;
        return resourceId == that.resourceId && Arrays.equals(args, that.args);
    }

    @Override
    public int hashCode() {
        return 31 * resourceId + Arrays.hashCode(args);
    }
}
