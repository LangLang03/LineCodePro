package cn.lineai.platform;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** URL gate used by the browser and by links in answers. */
public final class LinkPolicy {

    private LinkPolicy() {
    }

    public static boolean allows(String value, boolean allowAnyHttp) {
        if (value == null) return false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch) || Character.isISOControl(ch) || ch == '\\') return false;
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (uri.getHost() == null || uri.getRawUserInfo() != null) return false;
            if ("https".equals(scheme)) return true;
            if (!"http".equals(scheme)) return false;
            if (allowAnyHttp) return true;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return "localhost".equals(host) || "127.0.0.1".equals(host) || "10.0.2.2".equals(host);
        } catch (URISyntaxException error) {
            return false;
        }
    }
}
