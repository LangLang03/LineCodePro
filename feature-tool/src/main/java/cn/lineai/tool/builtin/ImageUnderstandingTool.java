package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import android.content.Context;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.data.repository.SshFileTreeStore;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.ipc.IpcProviderManager;
import cn.lineai.ipc.IpcProviderType;
import cn.lineai.ipc.terminal.TerminalIpcProvider;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelStore;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ModelServiceProvider;
import cn.lineai.tool.R;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolDisplayCategory;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import org.json.JSONObject;

public final class ImageUnderstandingTool extends BaseTool {
    public static final String NAME = "image_understanding";
    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;

    private final IpcProviderManager ipcProviderManager;
    private final Context context;

    public ImageUnderstandingTool() {
        this(null);
    }

    public ImageUnderstandingTool(Context context) {
        this(context, null);
    }

    public ImageUnderstandingTool(Context context, IpcProviderManager ipcProviderManager) {
        this.context = context == null ? null : context.getApplicationContext();
        this.ipcProviderManager = ipcProviderManager;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String promptSupplement(String executionMode, boolean isSsh) {
        if (isSsh) {
            return "image_understanding reads images from the SSH workspace via SFTP, then runs via the app-side vision model configuration.";
        }
        return "image_understanding reads images from the terminal provider environment via IPC, then runs via the app-side vision model configuration.";
    }

    @Override
    public String getDescription() {
        return "Read a local or SSH workspace image file and call the vision model selected in tool settings to understand its content. Supports OpenAI compatible, Codex Responses, and Anthropic Messages protocols.";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.READ;
    }

    @Override
    public ToolDisplayCategory getDisplayCategory() {
        return ToolDisplayCategory.READ;
    }

    @Override
    public String getActionName(Context context) {
        return context.getString(R.string.tool_call_image_understanding);
    }

    @Override
    public int getActionIcon() {
        return ICON_PAINTBRUSH;
    }

    @Override
    public JSONObject getParameters() throws org.json.JSONException {
        return new JSONObject()
                .put("type", "object")
                .put("properties", new JSONObject()
                        .put("path", new JSONObject()
                                .put("type", "string")
                                .put("description", "Image path, relative to the current workspace or an authorized directory; in SSH mode relative to the SSH workspace, or a remote absolute path"))
                        .put("prompt", new JSONObject()
                                .put("type", "string")
                                .put("description", "Question or analysis request for the vision model to answer")))
                .put("required", new org.json.JSONArray().put("path").put("prompt"));
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext context) {
        ToolSettingsStore settingsRepository = context != null ? context.getToolSettingsStore() : null;
        ModelStore modelRepository = context != null ? context.getModelRepository() : null;
        ModelServiceProvider modelServiceProvider = context != null ? context.getModelServiceProvider() : null;
        PromptTemplateRepository promptTemplateRepository = context != null ? context.getPromptTemplateRepository() : null;
        SshFileTreeStore sshFileTreeRepository = context != null ? context.getSshFileTreeRepository() : null;
        if (settingsRepository == null || modelRepository == null) {
            return error(context.getString(R.string.tool_img_under_no_context));
        }
        String path = first(input, "path", "image_path", "file_path");
        if (path.length() == 0) {
            return error(context.getString(R.string.tool_img_under_path_empty));
        }
        String prompt = input.optString("prompt").trim();
        if (prompt.length() == 0) {
            prompt = "Please describe the content of this image.";
        }
        ModelConfig model = selectedModel(settingsRepository, modelRepository);
        if (model == null) {
            return error(context.getString(R.string.tool_img_under_no_model));
        }
        if (modelServiceProvider == null) {
            return error(context.getString(R.string.tool_img_under_no_model_service));
        }
        if (!modelServiceProvider.supportsImageUnderstanding(model.getProtocolType())) {
            return error(context.getString(R.string.tool_img_under_unsupported_protocol));
        }
        try {
            ImageBytes image = readImageBytes(path, context, sshFileTreeRepository, settingsRepository);
            String mimeType = mimeType(image.path);
            if (!isSupportedMimeType(mimeType)) {
                return error(context.getString(R.string.tool_img_under_unsupported_format, image.path));
            }
            String rawInput = rawInputJson(prompt, mimeType, android.util.Base64.encodeToString(image.bytes, android.util.Base64.NO_WRAP));
            String text = modelServiceProvider.completeImageUnderstanding(model, systemPrompt(promptTemplateRepository), prompt, rawInput).trim();
            return ok(text.length() == 0 ? context.getString(R.string.tool_img_under_no_content) : text);
        } catch (Exception e) {
            return error(context.getString(R.string.tool_img_under_failed, e.getMessage()));
        }
    }

    private String systemPrompt(PromptTemplateRepository promptTemplateRepository) {
        if (promptTemplateRepository != null) {
            return promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_IMAGE_UNDERSTANDING_TOOL_SYSTEM);
        }
        return "You are LineCode's image understanding tool. Analyze the image based on the user's prompt and return only content relevant to the image and the prompt. Do not mention tool calls, base64, or file paths; when uncertain, state the uncertainty.";
    }

    private ModelConfig selectedModel(ToolSettingsStore settingsRepository, ModelStore modelRepository) {
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

    private ImageBytes readImageBytes(String path, ToolContext context, SshFileTreeStore sshFileTreeRepository, ToolSettingsStore settingsRepository) throws Exception {
        if (isTerminalProviderMode(settingsRepository)) {
            if (ipcProviderManager == null) {
                throw new IllegalStateException("Image understanding via terminal provider is not connected to the app context.");
            }
            TerminalIpcProvider provider = ipcProviderManager.getProviderByType(IpcProviderType.TERMINAL) instanceof TerminalIpcProvider
                    ? (TerminalIpcProvider) ipcProviderManager.getProviderByType(IpcProviderType.TERMINAL)
                    : null;
            if (provider == null || !provider.isBound()) {
                throw new IllegalStateException("No bound terminal provider.");
            }
            String remotePath = resolveSshPath(path, context == null ? "" : context.getHomePath());
            if (context != null) {
                context.reportToolProgress(getName(), context.getString(R.string.tool_img_under_ipc_reading), false);
            }
            return new ImageBytes(remotePath, provider.readFile(remotePath));
        }
        if (isSshMode(settingsRepository)) {
            if (sshFileTreeRepository == null) {
                throw new IllegalStateException("Image understanding via SSH is not connected to the app context.");
            }
            String remotePath = resolveSshPath(path, context == null ? "" : context.getHomePath());
            if (context != null) {
                context.reportToolProgress(getName(), context.getString(R.string.tool_img_under_sftp_reading), false);
            }
            return new ImageBytes(remotePath, sshFileTreeRepository.readFileBytes(remotePath, MAX_IMAGE_BYTES));
        }
        File file = resolveLocalImage(path, context);
        if (!file.exists()) {
            throw new IllegalStateException("Image file not found: " + path);
        }
        if (file.isDirectory()) {
            throw new IllegalStateException("Path is a directory, cannot process as image: " + path);
        }
        if (file.length() > MAX_IMAGE_BYTES) {
            throw new IllegalStateException("Image too large, current limit is 10 MB: " + path);
        }
        return new ImageBytes(file.getAbsolutePath(), readBytes(file));
    }

    private File resolveLocalImage(String path, ToolContext context) throws Exception {
        File rawFile = new File(path == null ? "" : path.trim());
        if (rawFile.isAbsolute() && PhoneScreenshotCache.isInside(this.context, rawFile)) {
            return rawFile.getCanonicalFile();
        }
        return FileToolPathPolicy.resolve(context, path);
    }

    private boolean isSshMode(ToolSettingsStore settingsRepository) {
        return settingsRepository != null
                && ToolSettingsStore.EXECUTION_SSH.equals(settingsRepository.getExecutionMode());
    }

    private boolean isTerminalProviderMode(ToolSettingsStore settingsRepository) {
        return settingsRepository != null
                && ToolSettingsStore.EXECUTION_TERMINAL_PROVIDER.equals(settingsRepository.getExecutionMode());
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

    private static boolean isSupportedMimeType(String mimeType) {
        String value = mimeType == null ? "" : mimeType.toLowerCase(java.util.Locale.ROOT);
        return "image/png".equals(value)
                || "image/jpeg".equals(value)
                || "image/webp".equals(value)
                || "image/gif".equals(value);
    }

    private static String rawInputJson(String prompt, String mimeType, String dataBase64) throws org.json.JSONException {
        return new JSONObject()
                .put("kind", "linecode_image_understanding")
                .put("prompt", prompt == null ? "" : prompt)
                .put("mime_type", normalizeMimeType(mimeType))
                .put("data_base64", dataBase64 == null ? "" : dataBase64)
                .toString();
    }

    private static String normalizeMimeType(String mimeType) {
        String value = (mimeType == null ? "" : mimeType).trim().toLowerCase(java.util.Locale.ROOT);
        if ("image/jpg".equals(value)) {
            return "image/jpeg";
        }
        if (isSupportedMimeType(value)) {
            return value;
        }
        return "image/png";
    }

    @Override
    public Class<? extends cn.lineai.tool.ToolCallCardView> getToolCallViewClass() {
        return cn.lineai.tool.ui.ToolCallReadView.class;
    }
}
