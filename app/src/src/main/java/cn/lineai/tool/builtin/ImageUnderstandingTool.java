package cn.lineai.tool.builtin;

import android.content.Context;
import cn.lineai.ai.ImageInputPayload;
import cn.lineai.ai.ModelClient;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.message.SystemModelMessage;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.data.repository.SshFileTreeRepository;
import cn.lineai.data.repository.ToolSettingsRepository;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.model.ModelRepository;
import cn.lineai.ssh.SshService;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import org.json.JSONObject;

public final class ImageUnderstandingTool extends BaseTool {
    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;

    private final ToolSettingsRepository settingsRepository;
    private final ModelRepository modelRepository;
    private final SshFileTreeRepository sshFileTreeRepository;
    private final ModelClient modelClient;

    public ImageUnderstandingTool() {
        this(null);
    }

    public ImageUnderstandingTool(Context context) {
        Context appContext = context == null ? null : context.getApplicationContext();
        settingsRepository = appContext == null ? null : new ToolSettingsRepository(appContext);
        modelRepository = appContext == null ? null : new ModelRepository(appContext);
        sshFileTreeRepository = appContext == null ? null : new SshFileTreeRepository(new SshService(appContext));
        modelClient = new ModelClient();
    }

    @Override
    public String getName() {
        return "image_understanding";
    }

    @Override
    public String getDescription() {
        return "读取本地或 SSH 工作区图片文件并调用工具设置里选择的视觉模型理解图片内容。支持 OpenAI 兼容、Codex Responses 和 Anthropic Messages 协议。";
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
                        .put("path", new JSONObject()
                                .put("type", "string")
                                .put("description", "图片路径，相对当前工作区或已授权目录；SSH 模式下相对 SSH 工作区，也可以是远端绝对路径"))
                        .put("prompt", new JSONObject()
                                .put("type", "string")
                                .put("description", "希望视觉模型回答的问题或分析要求")))
                .put("required", new org.json.JSONArray().put("path").put("prompt"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        if (settingsRepository == null || modelRepository == null) {
            return error("图片理解工具未接入应用上下文。");
        }
        String path = first(input, "path", "image_path", "file_path");
        if (path.length() == 0) {
            return error("图片路径不能为空。");
        }
        String prompt = input.optString("prompt").trim();
        if (prompt.length() == 0) {
            prompt = "请描述这张图片的内容。";
        }
        ModelConfig model = selectedModel();
        if (model == null) {
            return error("图片理解未选择模型。请在 设置 -> 工具设置 -> 图片操作 中选择视觉模型。");
        }
        if (model.getProtocolType() == ModelProtocolType.LOCAL_GGUF) {
            return error("本地 GGUF 协议暂不支持图片理解工具。请选择 OpenAI、Codex 或 Anthropic 协议模型。");
        }
        try {
            ImageBytes image = readImageBytes(path, context);
            String mimeType = mimeType(image.path);
            if (!ImageInputPayload.isSupportedMimeType(mimeType)) {
                return error("不支持的图片格式: " + image.path + "。支持 PNG、JPEG、WebP 和 GIF。");
            }
            String rawInput = ImageInputPayload.rawInputJson(prompt, mimeType, android.util.Base64.encodeToString(image.bytes, android.util.Base64.NO_WRAP));
            ArrayList<ModelMessage> messages = new ArrayList<>();
            messages.add(new SystemModelMessage("你是 LineCode 的图片理解工具。根据用户提示分析图片，只返回与图片和提示相关的内容。不要提及工具调用、base64 或文件路径；无法确定时说明不确定。"));
            messages.add(new UserModelMessage(prompt, rawInput));
            ModelCompletionResponse response = modelClient.complete(model, messages);
            String text = response.getText().trim();
            return ok(text.length() == 0 ? "视觉模型没有返回内容。" : text);
        } catch (Exception e) {
            return error("图片理解失败: " + e.getMessage());
        }
    }

    private ModelConfig selectedModel() {
        String modelId = settingsRepository.getImageUnderstandingModelId();
        return modelId.length() == 0 ? null : modelRepository.getModel(modelId);
    }

    private byte[] readBytes(File file) throws Exception {
        FileInputStream input = new FileInputStream(file);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private ImageBytes readImageBytes(String path, ToolContext context) throws Exception {
        if (isSshMode()) {
            if (sshFileTreeRepository == null) {
                throw new IllegalStateException("SSH 图片理解未接入应用上下文。");
            }
            String remotePath = resolveSshPath(path, context == null ? "" : context.getHomePath());
            if (context != null) {
                context.reportToolProgress(getName(), "正在通过 SFTP 读取图片...", false);
            }
            return new ImageBytes(remotePath, sshFileTreeRepository.readFileBytes(remotePath, MAX_IMAGE_BYTES));
        }
        File file = FileToolPathPolicy.resolve(context, path);
        if (!file.exists()) {
            throw new IllegalStateException("图片文件不存在: " + path);
        }
        if (file.isDirectory()) {
            throw new IllegalStateException("路径是目录，无法作为图片理解: " + path);
        }
        if (file.length() > MAX_IMAGE_BYTES) {
            throw new IllegalStateException("图片过大，当前上限为 10 MB: " + path);
        }
        return new ImageBytes(file.getAbsolutePath(), readBytes(file));
    }

    private boolean isSshMode() {
        return settingsRepository != null
                && ToolSettingsRepository.EXECUTION_SSH.equals(settingsRepository.getExecutionMode());
    }

    private String resolveSshPath(String path, String homePath) {
        String value = path == null ? "" : path.trim();
        if (value.startsWith("/") || value.startsWith("~/") || "~".equals(value) || ".".equals(value)) {
            return value;
        }
        String root = homePath == null ? "" : homePath.trim();
        if (root.length() == 0 || "~".equals(root)) {
            return value;
        }
        while (root.length() > 1 && root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        return root + "/" + value;
    }

    private String mimeType(String path) {
        String name = path == null ? "" : path.toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        return "";
    }

    private static final class ImageBytes {
        private final String path;
        private final byte[] bytes;

        private ImageBytes(String path, byte[] bytes) {
            this.path = path == null ? "" : path;
            this.bytes = bytes == null ? new byte[0] : bytes;
        }
    }

    private String first(JSONObject input, String... keys) {
        if (input == null) {
            return "";
        }
        for (String key : keys) {
            String value = input.optString(key).trim();
            if (value.length() > 0) {
                return value;
            }
        }
        return "";
    }

    private ToolResult ok(String content) {
        return new ToolResult("", getName(), content, false);
    }

    private ToolResult error(String content) {
        return new ToolResult("", getName(), content, true);
    }
}
