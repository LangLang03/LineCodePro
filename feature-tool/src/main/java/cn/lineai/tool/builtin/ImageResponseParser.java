package cn.lineai.tool.builtin;

import cn.lineai.tool.R;
import cn.lineai.tool.ToolContext;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 图片生成响应解析器。负责解析 Images API 与 Responses API 返回的 JSON，
 * 提取图片数据（URL 或 Base64）并校验。
 */
final class ImageResponseParser {
    private final ImageApiClient apiClient;
    private final Base64ImageValidator validator;

    ImageResponseParser(ImageApiClient apiClient, Base64ImageValidator validator) {
        this.apiClient = apiClient;
        this.validator = validator;
    }

    GeneratedImage parseImagesResponse(String raw, ToolContext context) throws Exception {
        enforceResponseSizeLimit(raw);
        JSONObject response = new JSONObject(raw);
        JSONArray data = response.optJSONArray("data");
        if (data == null || data.length() == 0 || data.optJSONObject(0) == null) {
            throw new Exception("API did not return image data.");
        }
        JSONObject item = data.getJSONObject(0);
        String revisedPrompt = item.optString("revised_prompt").trim();
        boolean invalidImageData = false;
        String b64 = optNonNullString(item, "b64_json");
        String mimeType = optNonNullString(item, "mime_type");
        if (mimeType.length() == 0 || validator.isNullLikeString(mimeType)) {
            mimeType = "image/png";
        }
        if (b64.length() > 0) {
            if (validator.isUsableBase64ImagePayload(b64)) {
                return new GeneratedImage(mimeType, "data:" + mimeType + ";base64," + b64, revisedPrompt);
            }
            invalidImageData = true;
        }
        String dataUrl = optNonNullString(item, "data_url");
        if (validator.isImageDataUrl(dataUrl)) {
            if (validator.isUsableImageDataUrl(dataUrl)) {
                return new GeneratedImage(mimeFromDataUrl(dataUrl), dataUrl, revisedPrompt);
            }
            invalidImageData = true;
        }
        String url = optNonNullString(item, "url");
        if (validator.isNullLikeString(url)) {
            invalidImageData = true;
            url = "";
        }
        if (url.length() == 0) {
            if (invalidImageData) {
                throw new Exception(context != null
                        ? context.getString(R.string.tool_img_parse_invalid_data)
                        : "API returned invalid image data.");
            }
            throw new Exception("API did not return b64_json, data_url or url.");
        }
        if (context != null) {
            context.reportToolProgress(ImageGenerationTool.NAME, context.getString(R.string.tool_img_parse_reading), false);
        }
        ImageApiClient.DownloadedImage downloaded = apiClient.downloadImage(url);
        return new GeneratedImage(downloaded.mimeType,
                "data:" + downloaded.mimeType + ";base64," + android.util.Base64.encodeToString(downloaded.bytes, android.util.Base64.NO_WRAP),
                revisedPrompt);
    }

    GeneratedImage parseResponsesImage(String raw, ToolContext context) throws Exception {
        enforceResponseSizeLimit(raw);
        JSONObject response = new JSONObject(raw);
        JSONObject error = response.optJSONObject("error");
        if (error != null) {
            String message = error.optString("message").trim();
            throw new Exception(message.length() == 0 ? error.toString() : message);
        }
        JSONArray output = response.optJSONArray("output");
        if (output == null || output.length() == 0) {
            throw new Exception("Responses API did not return output.");
        }
        boolean invalidImageData = false;
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null || !"image_generation_call".equals(item.optString("type"))) {
                continue;
            }
            String result = optNonNullString(item, "result");
            if (validator.isImageDataUrl(result)) {
                if (validator.isUsableImageDataUrl(result)) {
                    return new GeneratedImage(mimeFromDataUrl(result), result, item.optString("revised_prompt").trim());
                }
                invalidImageData = true;
                continue;
            }
            if (result.length() > 0) {
                if (validator.isUsableBase64ImagePayload(result)) {
                    return new GeneratedImage("image/png", "data:image/png;base64," + result, item.optString("revised_prompt").trim());
                }
                invalidImageData = true;
                continue;
            }
            String status = item.optString("status").trim();
            if ("failed".equals(status)) {
                throw new Exception("Responses API image generation failed.");
            }
        }
        if (invalidImageData) {
            throw new Exception(context != null
                    ? context.getString(R.string.tool_img_parse_responses_invalid)
                    : "Responses API returned invalid image data.");
        }
        throw new Exception("Responses API did not return image_generation_call.result.");
    }

    private void enforceResponseSizeLimit(String raw) throws Exception {
        if (raw == null) {
            return;
        }
        long sizeBytes = raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (sizeBytes > ImageApiClient.MAX_RESPONSE_BYTES) {
            throw new Exception("Image API response too large, current limit is " + (ImageApiClient.MAX_RESPONSE_BYTES / 1024 / 1024) + " MB.");
        }
    }

    private String mimeFromDataUrl(String dataUrl) {
        int colon = dataUrl.indexOf(':');
        int semicolon = dataUrl.indexOf(';');
        if (colon >= 0 && semicolon > colon) {
            return dataUrl.substring(colon + 1, semicolon);
        }
        return "image/png";
    }

    private String optNonNullString(JSONObject object, String key) {
        if (object == null) {
            return "";
        }
        String value = object.optString(key, "").trim();
        return value;
    }

    static final class GeneratedImage {
        final String mimeType;
        final String dataUrl;
        final String revisedPrompt;

        private GeneratedImage(String mimeType, String dataUrl, String revisedPrompt) {
            this.mimeType = mimeType == null || mimeType.length() == 0 ? "image/png" : mimeType;
            this.dataUrl = dataUrl == null ? "" : dataUrl;
            this.revisedPrompt = revisedPrompt == null ? "" : revisedPrompt;
        }
    }
}
