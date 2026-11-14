package cn.lineai.core.kernel.util;

import java.util.UUID;

/** Id factory. Kept injectable so tests can produce deterministic ids. */
public final class Ids {

    private Ids() {
    }

    /** Returns a new 32-character lowercase UUID without hyphens. */
    public static String random() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Returns a namespaced identifier: {@code prefix}, an underscore and a fresh random id.
     *
     * @param prefix optional namespace for the id; may be null or empty
     * @return a collision-resistant identifier, unnamespaced when the prefix is absent
     */
    public static String prefixed(String prefix) {
        String safe = prefix == null ? "" : prefix;
        return safe.isEmpty() ? random() : safe + "_" + random();
    }
}
