package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.model.ModelRepository;
import cn.lineai.security.UrlPolicy;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ImageGenerationTool extends BaseTool {
    private static final int MAX_DOWNLOAD_BYTES = 12 * 1024 * 1024;

    private final ToolSettingsRepository settingsRepository;
    private final ModelRepository modelRepository;

    public ImageGenerationTool() {
        this(null);
    }

    public ImageGenerationTool(Context context) {
        Context appContext = context == null ? null : context.getApplicationContext();
        settingsRepository = appContext == null ? null : new ToolSettingsRepository(appContext);
        modelRepository = appContext == null ? null : new ModelRepository(appContext);
    }

    @Override
    public String getName() {
        return "image_generation";
    }

    @Override
    public String getDescription() {
        return "根据提示词调用工具设置里选择的生图模型生成图片，成功后返回可直接嵌入 Markdown 的图片。支持 OpenAI 兼容 Images API。";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.READ;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("prompt", new JSONObject()
                                .put("type", "string")
                                .put("description", "图片生成提示词，包含主体、风格、构图、文字要求和限制"))
                        .put("size", new JSONObject()
                                .put("type", "string")
                                .put("description", "图片尺寸，默认 1024x1024；常见值: 1024x1024、1024x1536、1536x1024、auto"))
                        .put("quality", new JSONObject()
                                .put("type", "string")
                                .put("description", "质量选项，可留空；常见值: auto、low、medium、high、standard、hd"))
                        .put("background", new JSONObject()
                                .put("type", "string")
                                .put("description", "背景选项，可留空；常见值: auto、transparent、opaque")))
                .put("required", new JSONArray().put("prompt"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        if (settingsRepository == null || modelRepository == null) {
            return error("图片生成工具未接入应用上下文。");
        }
        String prompt = input == null ? "" : input.optString("prompt").trim();
        if (prompt.length() == 0) {
            return error("图片生成提示词不能为空。");
        }
        ModelConfig model = selectedModel();
        if (model == null) {
            return error("图片生成未选择模型。请在 设置 -> 工具设置 -> 图片操作 中选择生图模型。");
        }
        if (model.getProtocolType() != ModelProtocolType.OPENAI_COMPATIBLE
                && model.getProtocolType() != ModelProtocolType.CODEX_RESPONSES) {
            return error("图片生成当前仅支持 OpenAI 兼容或 Codex 协议模型。请添加或选择一个 Images API 兼容模型。");
        }
        try {
            if (context != null) {
                context.reportToolProgress(getName(), "正在生成图片...", false);
            }
            JSONObject body = requestBody(model, input, prompt, true);
            String raw;
            try {
                raw = postJson(imagesEndpoint(model.getBaseUrl()), body, authHeaders(model));
            } catch (Exception e) {
                body = requestBody(model, input, prompt, false);
                raw = postJson(imagesEndpoint(model.getBaseUrl()), body, authHeaders(model));
            }
            GeneratedImage image = parseImage(raw, context);
            String displayMarkdown = "- ![" + markdownAlt(prompt) + "](" + image.dataUrl + ")";
            String modelContent = "图片已生成并已在对话中显示。提示词: " + trimForModel(prompt)
                    + (image.revisedPrompt.length() == 0 ? "" : "\n修订提示词: " + trimForModel(image.revisedPrompt));
            JSONObject result = new JSONObject()
                    .put("linecode_image_generation", true)
                    .put("display_markdown", displayMarkdown)
                    .put("model_content", modelContent)
                    .put("mime_type", image.mimeType)
                    .put("prompt", prompt)
                    .put("revised_prompt", image.revisedPrompt);
            return ok(result.toString());
        } catch (Exception e) {
            return error("图片生成失败: " + e.getMessage());
        }
    }

    private ModelConfig selectedModel() {
        String modelId = settingsRepository.getImageGenerationModelId();
        return modelId.length() == 0 ? null : modelRepository.getModel(modelId);
    }

    private JSONObject requestBody(ModelConfig model, JSONObject input, String prompt, boolean requestBase64) throws Exception {
        JSONObject body = new JSONObject()
                .put("model", ModelContextParser.apiModelId(model.getModelId()))
                .put("prompt", prompt)
                .put("n", 1);
        String size = input == null ? "" : input.optString("size").trim();
        body.put("size", size.length() == 0 ? "1024x1024" : size);
        putIfPresent(body, "quality", input == null ? "" : input.optString("quality").trim());
        putIfPresent(body, "background", input == null ? "" : input.optString("background").trim());
        if (requestBase64) {
            body.put("response_format", "b64_json");
        }
        return body;
    }

    private void putIfPresent(JSONObject body, String key, String value) throws Exception {
        if (value != null && value.trim().length() > 0) {
            body.put(key, value.trim());
        }
    }

    private Map<String, String> authHeaders(ModelConfig model) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + model.getApiKey());
        return headers;
    }

    private String postJson(String url, JSONObject body, Map<String, String> headers) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    UrlPolicy.requireHttpOrLocalCleartextUrl(url, "图片生成 API 地址")
            ).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(180000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (header.getValue() != null && header.getValue().length() > 0) {
                    connection.setRequestProperty(header.getKey(), header.getValue());
                }
            }
            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8);
            writer.write(body.toString());
            writer.flush();
            writer.close();
            int code = connection.getResponseCode();
            String response = readAll(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream(), 1024 * 1024);
            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + ": " + response);
            }
            return response;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private GeneratedImage parseImage(String raw, ToolContext context) throws Exception {
        JSONObject response = new JSONObject(raw);
        JSONArray data = response.optJSONArray("data");
        if (data == null || data.length() == 0 || data.optJSONObject(0) == null) {
            throw new Exception("接口没有返回图片数据。");
        }
        JSONObject item = data.getJSONObject(0);
        String revisedPrompt = item.optString("revised_prompt").trim();
        String b64 = item.optString("b64_json").trim();
        String mimeType = item.optString("mime_type").trim();
        if (mimeType.length() == 0) {
            mimeType = "image/png";
        }
        if (b64.length() > 0) {
            return new GeneratedImage(mimeType, "data:" + mimeType + ";base64," + b64, revisedPrompt);
        }
        String dataUrl = item.optString("data_url").trim();
        if (dataUrl.startsWith("data:image/")) {
            return new GeneratedImage(mimeFromDataUrl(dataUrl), dataUrl, revisedPrompt);
        }
        String url = item.optString("url").trim();
        if (url.length() == 0) {
            throw new Exception("接口没有返回 b64_json、data_url 或 url。");
        }
        if (context != null) {
            context.reportToolProgress(getName(), "正在读取生成图片...", false);
        }
        DownloadedImage downloaded = downloadImage(url);
        return new GeneratedImage(downloaded.mimeType,
                "data:" + downloaded.mimeType + ";base64," + android.util.Base64.encodeToString(downloaded.bytes, android.util.Base64.NO_WRAP),
                revisedPrompt);
    }

    private DownloadedImage downloadImage(String url) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    UrlPolicy.requireHttpOrLocalCleartextUrl(url, "生成图片地址")
            ).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(120000);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new Exception("下载生成图片失败: HTTP " + code);
            }
            String mimeType = connection.getContentType();
            if (mimeType == null || !mimeType.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) {
                mimeType = "image/png";
            } else {
                int semicolon = mimeType.indexOf(';');
                if (semicolon > 0) {
                    mimeType = mimeType.substring(0, semicolon).trim();
                }
            }
            return new DownloadedImage(mimeType, readBytes(connection.getInputStream(), MAX_DOWNLOAD_BYTES));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readAll(InputStream input, int maxBytes) throws Exception {
        return new String(readBytes(input, maxBytes), StandardCharsets.UTF_8);
    }

    private byte[] readBytes(InputStream input, int maxBytes) throws Exception {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new Exception("图片数据过大，当前上限为 " + (maxBytes / 1024 / 1024) + " MB。");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private String imagesEndpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base.substring(0, base.length() - "/chat/completions".length()) + "/images/generations";
        }
        if (base.endsWith("/responses")) {
            return base.substring(0, base.length() - "/responses".length()) + "/images/generations";
        }
        if (base.endsWith("/images/generations")) {
            return base;
        }
        return base + "/images/generations";
    }

    private String mimeFromDataUrl(String dataUrl) {
        int colon = dataUrl.indexOf(':');
        int semicolon = dataUrl.indexOf(';');
        if (colon >= 0 && semicolon > colon) {
            return dataUrl.substring(colon + 1, semicolon);
        }
        return "image/png";
    }

    private String markdownAlt(String prompt) {
        String value = prompt == null ? "" : prompt.replace('\n', ' ').replace('\r', ' ').replace('[', ' ').replace(']', ' ').trim();
        if (value.length() > 80) {
            value = value.substring(0, 77) + "...";
        }
        return value.length() == 0 ? "生成图片" : value;
    }

    private String trimForModel(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() > 500) {
            return text.substring(0, 497) + "...";
        }
        return text;
    }

    private ToolResult ok(String content) {
        return new ToolResult("", getName(), content, false);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }

    private static final class GeneratedImage {
        private final String mimeType;
        private final String dataUrl;
        private final String revisedPrompt;

        private GeneratedImage(String mimeType, String dataUrl, String revisedPrompt) {
            this.mimeType = mimeType == null || mimeType.length() == 0 ? "image/png" : mimeType;
            this.dataUrl = dataUrl == null ? "" : dataUrl;
            this.revisedPrompt = revisedPrompt == null ? "" : revisedPrompt;
        }
    }

    private static final class DownloadedImage {
        private final String mimeType;
        private final byte[] bytes;

        private DownloadedImage(String mimeType, byte[] bytes) {
            this.mimeType = mimeType == null || mimeType.length() == 0 ? "image/png" : mimeType;
            this.bytes = bytes == null ? new byte[0] : bytes;
        }
    }
}
