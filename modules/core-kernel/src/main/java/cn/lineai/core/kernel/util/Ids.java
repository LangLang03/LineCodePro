package cn.lineai.core.kernel.util;

import java.util.UUID;

/** Id factory. Kept injectable so tests can produce deterministic ids. */
public final class Ids {

    private Ids() {
    }

    public static String random() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String prefixed(String prefix) {
        String safe = prefix == null ? "" : prefix;
        return safe.isEmpty() ? random() : safe + "_" + random();
    }
}
