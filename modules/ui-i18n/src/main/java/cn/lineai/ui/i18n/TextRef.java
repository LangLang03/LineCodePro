package cn.lineai.ui.i18n;

import android.content.Context;

import java.util.Arrays;

/**
 * A deferred string lookup: resource id plus format arguments.
 *
 * <p>Carrier for strings that cross process or layer boundaries without a {@code Context} at hand;
 * resolution happens later against whatever context renders the text. Instances are value objects
 * so they can be compared and cached.</p>
 */
public final class TextRef {

    public final int resourceId;
    public final Object[] args;

    /**
     * Creates one deferred lookup; the argument array is never null.
     *
     * @param resourceId string resource identifier
     * @param args format arguments consumed by {@code getString(resourceId, args)}
     */
    public TextRef(int resourceId, Object... args) {
        this.resourceId = resourceId;
        this.args = args == null ? new Object[0] : args;
    }

    /**
     * Creates a lookup without format arguments.
     *
     * @param resourceId string resource identifier
     * @return lookup resolving to the plain resource string
     */
    public static TextRef of(int resourceId) {
        return new TextRef(resourceId);
    }

    /**
     * Creates a lookup with format arguments.
     *
     * @param resourceId string resource identifier
     * @param args format arguments consumed at resolution time
     * @return lookup resolving to the formatted resource string
     */
    public static TextRef of(int resourceId, Object... args) {
        return new TextRef(resourceId, args);
    }

    /**
     * Resolves the resource against the given context.
     *
     * @param context context used to read the resources; null resolves to the empty string
     * @return formatted string, or empty when no context is available
     */
    public String resolve(Context context) {
        if (context == null) {
            return "";
        }
        return args.length == 0
                ? context.getString(resourceId)
                : context.getString(resourceId, args);
    }

    /** Two lookups are equal when resource id and arguments match. */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof TextRef)) {
            return false;
        }
        TextRef that = (TextRef) other;
        return resourceId == that.resourceId && Arrays.equals(args, that.args);
    }

    /** Hash code derived from the resource id and the argument array. */
    @Override
    public int hashCode() {
        return 31 * resourceId + Arrays.hashCode(args);
    }
}
