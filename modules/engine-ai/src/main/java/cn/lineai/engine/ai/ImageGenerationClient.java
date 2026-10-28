package cn.lineai.engine.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.lineai.core.model.ModelConfig;

/** Calls an OpenAI-compatible images/generations endpoint and returns PNG bytes. */
public final class ImageGenerationClient {

    public byte[] generate(ModelConfig model, String prompt) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("model", model.modelId);
            body.put("prompt", prompt);
            body.put("n", 1);
            body.put("size", "1024x1024");
            body.put("response_format", "b64_json");
        } catch (Exception error) {
            throw new IOException("Cannot build image request", error);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + model.apiKey);
        String url = ModelCatalogClient.endpoint(model.baseUrl, "/images/generations");
        String raw = ModelCatalogClient.execute("POST", url, headers, body.toString());
        byte[] bytes = extractBase64(raw);
        if (bytes == null) {
            String imageUrl = extractUrl(raw);
            if (!imageUrl.isEmpty()) {
                bytes = ModelCatalogClient.download(imageUrl);
            }
        }
        if (bytes == null || bytes.length == 0) {
            throw new IOException("Image response contained no data: " + raw);
        }
        return bytes;
    }

    static byte[] extractBase64(String raw) {
        try {
            JSONArray data = new JSONObject(raw).optJSONArray("data");
            if (data == null || data.length() == 0) return null;
            String encoded = data.getJSONObject(0).optString("b64_json", "");
            return encoded.isEmpty() ? null : Base64.getDecoder().decode(encoded);
        } catch (Exception error) {
            return null;
        }
    }

    static String extractUrl(String raw) {
        try {
            JSONArray data = new JSONObject(raw).optJSONArray("data");
            if (data == null || data.length() == 0) return "";
            return data.getJSONObject(0).optString("url", "");
        } catch (Exception error) {
            return "";
        }
    }
}
