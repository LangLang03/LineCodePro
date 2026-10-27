package cn.lineai.engine.protocol;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReasoningPolicyTest {

    private JSONObject body(ReasoningSpec spec, double temperature) throws Exception {
        JSONObject body = new JSONObject();
        body.put("temperature", temperature);
        ReasoningPolicy.apply(body, spec);
        return body;
    }

    @Test
    public void deepseekSkipsAutoAndMapsLevels() throws Exception {
        assertFalse(body(spec("https://api.deepseek.com", "deepseek-chat", "auto"), 0.7)
                .has("reasoning_effort"));
        assertEquals("none", body(spec("https://api.deepseek.com", "deepseek-chat", "off"), 0.7)
                .getString("reasoning_effort"));
        assertEquals("high", body(spec("https://api.deepseek.com", "deepseek-chat", "medium"), 0.7)
                .getString("reasoning_effort"));
        assertEquals("max", body(spec("https://api.deepseek.com", "deepseek-chat", "max"), 0.7)
                .getString("reasoning_effort"));
    }

    @Test
    public void glmKeepsOnlyLowHighMax() throws Exception {
        assertEquals("low", body(spec("https://open.bigmodel.cn", "glm-4", "off"), 0.7)
                .getString("reasoning_effort"));
        assertEquals("high", body(spec("https://open.bigmodel.cn", "glm-4", "medium"), 0.7)
                .getString("reasoning_effort"));
        assertEquals("max", body(spec("https://open.bigmodel.cn", "glm-4", "max"), 0.7)
                .getString("reasoning_effort"));
        JSONObject thinking = body(spec("https://open.bigmodel.cn", "glm-4", "high"), 0.7)
                .getJSONObject("thinking");
        assertEquals("enabled", thinking.getString("type"));
    }

    @Test
    public void openAiReasoningModelsSendEffortWithoutAuto() throws Exception {
        assertEquals("medium", body(spec("https://api.openai.com/v1", "gpt-5", "auto"), 0.7)
                .getString("reasoning_effort"));
        assertEquals("xhigh", body(spec("https://api.openai.com/v1", "o3-mini", "max"), 0.7)
                .getString("reasoning_effort"));
        assertFalse(body(spec("https://api.openai.com/v1", "gpt-5", "off"), 0.7)
                .has("reasoning_effort"));
    }

    @Test
    public void genericModelsUseNestedReasoningObject() throws Exception {
        JSONObject reasoning = body(spec("https://example.com/v1", "custom", "high"), 0.7)
                .getJSONObject("reasoning");
        assertEquals("high", reasoning.getString("effort"));
    }

    @Test
    public void dashscopeSendsBudgetAndPreserveFlag() throws Exception {
        ReasoningSpec spec = new ReasoningSpec("https://dashscope.aliyuncs.com", "qwen-max", "qwen",
                "high", true);
        JSONObject body = body(spec, 0.7);
        assertTrue(body.getBoolean("enable_thinking"));
        assertEquals(8192, body.getInt("thinking_budget"));
        assertTrue(body.getBoolean("preserve_thinking"));
    }

    @Test
    public void kimiClampsTemperatureAndKeepsReasoning() throws Exception {
        JSONObject body = body(new ReasoningSpec("https://api.moonshot.cn", "kimi-k2", "moonshot",
                "low", true), 0.2);
        assertEquals(1.0, body.getDouble("temperature"), 0.0001);
        assertEquals("all", body.getJSONObject("thinking").getString("keep"));
    }

    @Test
    public void minimaxUsesAdaptiveThinking() throws Exception {
        JSONObject body = body(spec("https://api.minimax.chat", "abab6", "medium"), 0.7);
        assertEquals("adaptive", body.getJSONObject("thinking").getString("type"));
        assertTrue(body.getBoolean("reasoning_split"));
    }

    @Test
    public void nvidiaGatewayReceivesNoReasoningFields() throws Exception {
        JSONObject body = body(new ReasoningSpec("https://integrate.api.nvidia.com/v1", "gpt-5",
                "nvidia", "high", true), 0.7);
        assertFalse(body.has("reasoning_effort"));
        assertFalse(body.has("reasoning"));
        assertFalse(body.has("thinking"));
    }

    @Test
    public void codecAppliesReasoningToRequestBody() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .model("deepseek-chat")
                .reasoning(new ReasoningSpec("https://api.deepseek.com", "deepseek-chat", "",
                        "low", false))
                .build();
        JSONObject body = new JSONObject(OpenAiChatCodec.requestBody(request));
        assertEquals("low", body.getString("reasoning_effort"));
    }

    private ReasoningSpec spec(String baseUrl, String model, String effort) {
        return new ReasoningSpec(baseUrl, model, "", effort, false);
    }
}
