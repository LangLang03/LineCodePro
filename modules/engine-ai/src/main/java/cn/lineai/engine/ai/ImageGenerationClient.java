package cn.lineai.engine.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.lineai.core.model.ModelConfig;

/**
 * Calls an OpenAI-compatible images/generations endpoint and returns PNG bytes.
 *
 * <p>Providers answer either with inline Base64 data or with a URL; both response shapes are
 * handled, and URL results are downloaded with the size cap enforced by the shared downloader.</p>
 */
public final class ImageGenerationClient {

    /**
     * Generates one 1024x1024 image for a prompt.
     *
     * @param model model configuration selecting the endpoint and credentials
     * @param prompt image description
     * @return decoded image bytes
     * @throws IOException when the request fails, the response is malformed or carries no image
     */
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

    /**
     * Decodes the inline Base64 image from a generation response.
     *
     * @param raw response body
     * @return decoded bytes of the first {@code b64_json} entry, or null when absent or invalid
     */
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

    /**
     * Extracts the hosted image URL from a generation response.
     *
     * @param raw response body
     * @return URL of the first data entry, or an empty string when absent or invalid
     */
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
