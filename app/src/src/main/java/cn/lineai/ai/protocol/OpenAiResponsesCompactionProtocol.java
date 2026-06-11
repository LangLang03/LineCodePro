package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import java.util.HashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OpenAiResponsesCompactionProtocol extends AbstractHttpModelProtocol {
    @Override
    public ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException {
        throw new ModelCompletionException("OpenAI Responses compact 协议只能用于上下文压缩");
    }

    public String compact(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelCancellationToken cancellationToken
    ) throws ModelCompletionException {
        try {
            JSONObject body = new JSONObject();
            body.put("model", ModelContextParser.apiModelId(config.getEffectiveCompressionModelId()));
            body.put("input", ResponsesInputBuilder.inputJson(messages));

            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + config.getApiKey());
            String raw = postJson(endpoint(config.getBaseUrl(), "/responses/compact"), body, headers, cancellationToken);
            if (cancellationToken != null && cancellationToken.isCancelled()) {
                return "";
            }
            JSONObject response = new JSONObject(raw);
            String item = findCompactionItem(response);
            if (item.length() == 0) {
                throw new ModelCompletionException("OpenAI 压缩 API 未返回 compaction item");
            }
            return item;
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("OpenAI 压缩 API 解析失败: " + e.getMessage(), e);
        }
    }

    private String findCompactionItem(JSONObject response) {
        String direct = firstCompactionItem(response.optJSONArray("output"));
        if (direct.length() > 0) {
            return direct;
        }
        JSONObject compaction = response.optJSONObject("compaction");
        if (compaction == null) {
            return "";
        }
        String nested = firstCompactionItem(compaction.optJSONArray("output"));
        if (nested.length() > 0) {
            return nested;
        }
        if ("compaction".equals(compaction.optString("type"))) {
            return compaction.toString();
        }
        return "";
    }

    private String firstCompactionItem(JSONArray output) {
        if (output == null) {
            return "";
        }
        for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if ("compaction".equals(item.optString("type"))) {
                return item.toString();
            }
        }
        return "";
    }
}
