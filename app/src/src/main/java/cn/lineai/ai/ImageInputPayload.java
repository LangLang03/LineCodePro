package cn.lineai.ai;

import org.json.JSONObject;

public final class ImageInputPayload {
    public static final String KIND = "linecode_image_understanding";

    private ImageInputPayload() {
    }

    public static String rawInputJson(String prompt, String mimeType, String dataBase64) throws org.json.JSONException {
        return new JSONObject()
                .put("kind", KIND)
                .put("prompt", safe(prompt))
                .put("mime_type", normalizeMimeType(mimeType))
                .put("data_base64", safe(dataBase64))
                .toString();
    }

    public static Payload fromRawInputJson(String rawInputJson) {
        if (rawInputJson == null || rawInputJson.trim().length() == 0) {
            return null;
        }
        String raw = rawInputJson.trim();
        if (!raw.startsWith("{")) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(raw);
            if (!KIND.equals(object.optString("kind"))) {
                return null;
            }
            String dataBase64 = object.optString("data_base64").trim();
            if (dataBase64.length() == 0) {
                return null;
            }
            return new Payload(
                    object.optString("prompt"),
                    normalizeMimeType(object.optString("mime_type")),
                    dataBase64
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String normalizeMimeType(String mimeType) {
        String value = safe(mimeType).toLowerCase(java.util.Locale.ROOT);
        if ("image/jpg".equals(value)) {
            return "image/jpeg";
        }
        if (isSupportedMimeType(value)) {
            return value;
        }
        return "image/png";
    }

    public static boolean isSupportedMimeType(String mimeType) {
        String value = safe(mimeType).toLowerCase(java.util.Locale.ROOT);
        return "image/png".equals(value)
                || "image/jpeg".equals(value)
                || "image/webp".equals(value)
                || "image/gif".equals(value);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Payload {
        private final String prompt;
        private final String mimeType;
        private final String dataBase64;

        private Payload(String prompt, String mimeType, String dataBase64) {
            this.prompt = prompt == null ? "" : prompt;
            this.mimeType = normalizeMimeType(mimeType);
            this.dataBase64 = dataBase64 == null ? "" : dataBase64;
        }

        public String getPrompt() {
            return prompt;
        }

        public String getMimeType() {
            return mimeType;
        }

        public String getDataBase64() {
            return dataBase64;
        }

        public String dataUrl() {
            return "data:" + mimeType + ";base64," + dataBase64;
        }
    }
}
