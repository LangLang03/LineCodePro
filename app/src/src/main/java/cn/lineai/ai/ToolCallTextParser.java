package cn.lineai.ai;

import cn.lineai.tool.ToolCall;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

public final class ToolCallTextParser {
    private static final Pattern TOOL_CALLS_TAG = Pattern.compile(
            "<tool_calls\\b[^>]*>(.*?)</tool_calls>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern TOOL_CALL_TAG = Pattern.compile(
            "<tool_call\\b[^>]*>(.*?)</tool_call>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern TOOL_CALL_XML_TAG = Pattern.compile(
            "<tool_call\\b([^>]*)>(.*?)</tool_call>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern TOOL_CALL_OPEN_TAG = Pattern.compile(
            "<tool_call\\b([^>]*)>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern TOOL_CALL_PARTIAL_OPEN_TAG = Pattern.compile(
            "<tool_call\\b([^>]*)$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern ARGUMENT_TAG = Pattern.compile(
            "<argument\\b([^>]*)>(.*?)</argument>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "([A-Za-z_][A-Za-z0-9_-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')"
    );
    private static final Pattern FENCED_JSON = Pattern.compile(
            "^\\s*```(?:json)?\\s*(.*?)\\s*```\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private ToolCallTextParser() {
    }

    public static Result parse(String text) {
        return parseInternal(text, false);
    }

    public static Result parseStreamingPreview(String text) {
        return parseInternal(text, true);
    }

    private static Result parseInternal(String text, boolean includePartialXml) {
        String source = text == null ? "" : text;
        ArrayList<ToolCall> calls = new ArrayList<>();
        boolean hasToolMarkup = hasToolMarkup(source);
        String withoutToolCalls = extractTaggedPayloads(source, TOOL_CALLS_TAG, calls);
        String cleanText = extractSingleToolCalls(withoutToolCalls, calls).trim();
        if (includePartialXml) {
            parsePartialXmlToolCalls(cleanText, calls);
        }
        cleanText = stripOpenToolMarkup(cleanText).trim();
        return new Result(cleanText, calls, hasToolMarkup);
    }

    private static String extractTaggedPayloads(String source, Pattern pattern, ArrayList<ToolCall> calls) {
        Matcher matcher = pattern.matcher(source);
        StringBuffer clean = new StringBuffer();
        while (matcher.find()) {
            parsePayload(matcher.group(1), calls);
            matcher.appendReplacement(clean, "");
        }
        matcher.appendTail(clean);
        return clean.toString();
    }

    private static String extractSingleToolCalls(String source, ArrayList<ToolCall> calls) {
        Matcher matcher = TOOL_CALL_XML_TAG.matcher(source);
        StringBuffer clean = new StringBuffer();
        while (matcher.find()) {
            int beforeCount = calls.size();
            parseXmlToolCalls(matcher.group(0), calls);
            if (calls.size() == beforeCount) {
                parsePayload(matcher.group(2), calls);
            }
            matcher.appendReplacement(clean, "");
        }
        matcher.appendTail(clean);
        return clean.toString();
    }

    private static void parsePayload(String payload, ArrayList<ToolCall> calls) {
        if (payload == null) {
            return;
        }
        String normalized = unwrapFence(payload.trim()).trim();
        if (normalized.length() == 0) {
            return;
        }
        int beforeXmlCount = calls.size();
        parseXmlToolCalls(normalized, calls);
        if (calls.size() > beforeXmlCount) {
            return;
        }
        normalized = normalized.replaceAll("(?is)</?tool_call\\b[^>]*>", "").trim();
        try {
            if (normalized.startsWith("[")) {
                parseArray(new JSONArray(normalized), calls);
                return;
            }
            if (normalized.startsWith("{")) {
                parseObject(new JSONObject(normalized), calls);
            }
        } catch (Exception ignored) {
        }
    }

    private static void parseXmlToolCalls(String payload, ArrayList<ToolCall> calls) {
        Matcher matcher = TOOL_CALL_XML_TAG.matcher(payload);
        while (matcher.find()) {
            String attributes = matcher.group(1);
            String body = matcher.group(2);
            String name = normalizeToolName(attribute(attributes, "name"));
            if (name.length() == 0) {
                name = normalizeToolName(readFirstArgument(body, "name"));
            }
            if (name.length() == 0) {
                continue;
            }
            JSONObject args = new JSONObject();
            Matcher argMatcher = ARGUMENT_TAG.matcher(body);
            while (argMatcher.find()) {
                String argName = attribute(argMatcher.group(1), "name");
                if (argName.length() == 0 || "name".equals(argName)) {
                    continue;
                }
                try {
                    args.put(argName, decodeText(argMatcher.group(2)));
                } catch (Exception ignored) {
                }
            }
            String id = attribute(attributes, "id");
            if (id.length() == 0) {
                id = defaultXmlToolId(calls.size());
            }
            calls.add(new ToolCall(id, name, args.toString()));
        }
    }

    private static void parsePartialXmlToolCalls(String source, ArrayList<ToolCall> calls) {
        int searchStart = 0;
        Matcher matcher = TOOL_CALL_OPEN_TAG.matcher(source);
        while (matcher.find(searchStart)) {
            int bodyStart = matcher.end();
            int close = indexOfIgnoreCase(source, "</tool_call>", bodyStart);
            if (close >= 0) {
                searchStart = close + "</tool_call>".length();
                continue;
            }
            parsePartialXmlToolCall(matcher.group(1), source.substring(bodyStart), calls);
            return;
        }

        Matcher partialOpen = TOOL_CALL_PARTIAL_OPEN_TAG.matcher(source);
        if (partialOpen.find()) {
            parsePartialXmlToolCall(partialOpen.group(1), "", calls);
        }
    }

    private static void parsePartialXmlToolCall(String attributes, String body, ArrayList<ToolCall> calls) {
        String name = normalizeToolName(attribute(attributes, "name"));
        if (name.length() == 0) {
            name = normalizeToolName(readFirstArgument(body, "name"));
        }
        if (name.length() == 0) {
            return;
        }
        JSONObject args = new JSONObject();
        Matcher argMatcher = ARGUMENT_TAG.matcher(body == null ? "" : body);
        while (argMatcher.find()) {
            String argName = attribute(argMatcher.group(1), "name");
            if (argName.length() == 0 || "name".equals(argName)) {
                continue;
            }
            try {
                args.put(argName, decodeText(argMatcher.group(2)));
            } catch (Exception ignored) {
            }
        }
        String id = attribute(attributes, "id");
        if (id.length() == 0) {
            id = defaultXmlToolId(calls.size());
        }
        calls.add(new ToolCall(id, name, args.toString()));
    }

    private static int indexOfIgnoreCase(String source, String target, int fromIndex) {
        return source.toLowerCase(java.util.Locale.ROOT)
                .indexOf(target.toLowerCase(java.util.Locale.ROOT), Math.max(0, fromIndex));
    }

    private static String defaultXmlToolId(int index) {
        return "text_tool_xml_" + index;
    }

    private static String readFirstArgument(String body, String argumentName) {
        Matcher matcher = ARGUMENT_TAG.matcher(body);
        while (matcher.find()) {
            if (argumentName.equals(attribute(matcher.group(1), "name"))) {
                return decodeText(matcher.group(2));
            }
        }
        return "";
    }

    private static String attribute(String attributes, String name) {
        if (attributes == null || name == null) {
            return "";
        }
        Matcher matcher = ATTRIBUTE.matcher(attributes);
        while (matcher.find()) {
            if (name.equals(matcher.group(1))) {
                return decodeText(matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
            }
        }
        return "";
    }

    private static String decodeText(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.startsWith("<![CDATA[") && text.endsWith("]]>")) {
            text = text.substring("<![CDATA[".length(), text.length() - "]]>".length());
        }
        return text
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private static String normalizeToolName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() == 0) {
            return "";
        }
        String compact = name.replace("-", "").replace("_", "").toLowerCase(java.util.Locale.ROOT);
        if ("filewrite".equals(compact) || "writefile".equals(compact)) return "file_write";
        if ("fileread".equals(compact) || "readfile".equals(compact)) return "file_read";
        if ("fileedit".equals(compact) || "editfile".equals(compact)) return "file_edit";
        if ("filedelete".equals(compact) || "deletefile".equals(compact)) return "file_delete";
        if ("listdir".equals(compact) || "listdirectory".equals(compact)) return "list_dir";
        if ("glob".equals(compact) || "filesearch".equals(compact) || "searchfile".equals(compact)) return "glob";
        if ("shellexecute".equals(compact) || "shell".equals(compact)) return "shell_execute";
        return name;
    }

    private static boolean hasToolMarkup(String source) {
        if (source == null) {
            return false;
        }
        String lower = source.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("<tool_calls") || lower.contains("<tool_call");
    }

    private static String stripOpenToolMarkup(String text) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        int callsIndex = lower.indexOf("<tool_calls");
        int callIndex = lower.indexOf("<tool_call");
        int index;
        if (callsIndex < 0) {
            index = callIndex;
        } else if (callIndex < 0) {
            index = callsIndex;
        } else {
            index = Math.min(callsIndex, callIndex);
        }
        return index >= 0 ? text.substring(0, index) : text;
    }

    private static String unwrapFence(String value) {
        Matcher matcher = FENCED_JSON.matcher(value);
        return matcher.matches() ? matcher.group(1).trim() : value;
    }

    private static void parseObject(JSONObject object, ArrayList<ToolCall> calls) {
        JSONArray array = object.optJSONArray("tool_calls");
        if (array != null) {
            parseArray(array, calls);
            return;
        }
        ToolCall call = toToolCall(object, calls.size());
        if (call != null) {
            calls.add(call);
        }
    }

    private static void parseArray(JSONArray array, ArrayList<ToolCall> calls) {
        for (int i = 0; i < array.length(); i++) {
            Object item = array.opt(i);
            if (item instanceof JSONObject) {
                ToolCall call = toToolCall((JSONObject) item, calls.size());
                if (call != null) {
                    calls.add(call);
                }
            }
        }
    }

    private static ToolCall toToolCall(JSONObject object, int index) {
        JSONObject function = object.optJSONObject("function");
        String name = "";
        Object arguments = null;
        if (function != null) {
            name = normalizeToolName(function.optString("name"));
            arguments = function.opt("arguments");
        }
        if (name.length() == 0) {
            name = normalizeToolName(object.optString("name"));
        }
        if (name.length() == 0) {
            name = normalizeToolName(object.optString("tool_name"));
        }
        if (arguments == null) {
            arguments = object.opt("arguments");
        }
        if (arguments == null) {
            arguments = object.opt("input");
        }
        if (arguments == null) {
            arguments = object.opt("parameters");
        }
        if (name.length() == 0 && object.names() != null && object.length() == 1) {
            String key = object.names().optString(0);
            if (key.length() > 0) {
                name = key;
                arguments = object.opt(key);
            }
        }
        if (name.length() == 0) {
            return null;
        }
        String id = object.optString("id");
        if (id.length() == 0) {
            id = "text_tool_json_" + index;
        }
        String args = argumentsToString(arguments);
        return new ToolCall(id, name, args);
    }

    private static String argumentsToString(Object arguments) {
        if (arguments == null || arguments == JSONObject.NULL) {
            return "{}";
        }
        if (arguments instanceof JSONObject || arguments instanceof JSONArray) {
            return arguments.toString();
        }
        String value = String.valueOf(arguments).trim();
        return value.length() == 0 ? "{}" : value;
    }

    public static final class Result {
        private final String text;
        private final List<ToolCall> toolCalls;
        private final boolean toolMarkup;

        Result(String text, List<ToolCall> toolCalls, boolean toolMarkup) {
            this.text = text == null ? "" : text;
            this.toolCalls = toolCalls;
            this.toolMarkup = toolMarkup;
        }

        public String getText() {
            return text;
        }

        public List<ToolCall> getToolCalls() {
            return toolCalls;
        }

        public boolean hasToolCalls() {
            return !toolCalls.isEmpty();
        }

        public boolean hasToolMarkup() {
            return toolMarkup;
        }
    }
}
