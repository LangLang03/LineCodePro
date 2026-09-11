#include <cassert>
#include <string>
#include <vector>

#include "domain/extension_config.h"
#include "infrastructure/extension_config_codec.h"

namespace {

void VerifyLegacyAgentNormalization() {
  using namespace linecode::domain;
  assert(NormalizeAgentSlug("  My  Agent!  ", "") == "my-agent");
  assert(NormalizeAgentSlug("A__B--C", "") == "a__b-c");
  assert(NormalizeAgentSlug("", "Fallback Name") == "fallback-name");
  assert(NormalizeAgentEditorSlug("  Test  Fixer! ") == "test-fixer");
  assert(NormalizeAgentEditorSlug("123 Agent") == "agent-123-agent");
  assert(NormalizeAgentEditorSlug("___") == "");
  assert(NormalizeAgentEditorSlug(std::string(60, 'a')).size() == 48);

  const auto normalized = NormalizeAgentExtension(AgentExtension{
      .id = " agent-id ",
      .name = " Test Agent ",
      .slug = " ",
      .prompt = "  Do the work.  ",
      .trigger = "  When asked. ",
      .tool_names = {" file_read ", "", " glob"},
      .mcp_ids = {" custom:mcp-id ", "  "},
  });
  assert(normalized.id == " agent-id ");
  assert(normalized.name == " Test Agent ");
  assert(normalized.slug == "test-agent");
  assert(normalized.prompt == "  Do the work.  ");
  assert(
      (normalized.tool_names == std::vector<std::string>{"file_read", "glob"}));
  assert((normalized.mcp_ids == std::vector<std::string>{"custom:mcp-id"}));
}

void VerifyLegacyStringArrays() {
  using namespace linecode::infrastructure;
  const auto encoded =
      EncodeExtensionStringList({" file_read ", "", "glob", "glob"});
  assert(encoded == R"(["file_read","glob","glob"])");
  assert((DecodeExtensionStringList(encoded) ==
          std::vector<std::string>{"file_read", "glob", "glob"}));
  assert(DecodeExtensionStringList("not-json").empty());
  assert((DecodeExtensionStringList(R"([" a ",4,null,"b"])") ==
          std::vector<std::string>{"a", "b"}));
}

void VerifyLegacyMcpCodec() {
  using namespace linecode;
  const std::vector<domain::McpRequestHeader> headers{
      {.name = " Authorization ", .value = " Bearer secret "},
      {.name = "", .value = "ignored"},
  };
  const auto header_json = infrastructure::EncodeMcpRequestHeaders(headers);
  assert((infrastructure::DecodeMcpRequestHeaders(header_json) ==
          std::vector<domain::McpRequestHeader>{
              {.name = " Authorization ", .value = " Bearer secret "}}));
  assert(infrastructure::DecodeMcpRequestHeaders(
             R"([{"name":"   ","value":"ignored"}])")
             .empty());

  const std::vector<domain::McpToolSummary> tools{
      {.name = "read_file",
       .enabled = false,
       .description = "Read a file",
       .input_schema_json = R"({"type":"object","required":["path"]})"},
      {.name = "legacy_string",
       .enabled = true,
       .description = {},
       .input_schema_json = {}},
      {.name = "invalid_schema",
       .enabled = true,
       .description = {},
       .input_schema_json = "not-json"},
  };
  const auto tool_json = infrastructure::EncodeMcpTools(tools);
  const auto decoded = infrastructure::DecodeMcpTools(tool_json);
  assert(decoded.size() == 2);
  assert(decoded[0].name == tools[0].name);
  assert(decoded[0].enabled == tools[0].enabled);
  assert(decoded[0].description == tools[0].description);
  assert(decoded[0].input_schema_json ==
         R"({"required":["path"],"type":"object"})");
  assert(decoded[1] == tools[1]);

  const auto legacy = infrastructure::DecodeMcpTools(
      R"(["string-tool",{"name":"snake","enabled":0,"description":"d","input_schema":{"type":"string"}},{"name":"schema","schema":{"type":"number"}}])");
  assert(legacy.size() == 3);
  assert(legacy[0] == domain::McpToolSummary{.name = "string-tool",
                                             .enabled = true,
                                             .description = {},
                                             .input_schema_json = {}});
  assert(!legacy[1].enabled);
  assert(legacy[1].input_schema_json == R"({"type":"string"})");
  assert(legacy[2].enabled);
  assert(legacy[2].input_schema_json == R"({"type":"number"})");
  assert(infrastructure::DecodeMcpTools("{}").empty());
}

void VerifyMcpNormalization() {
  using namespace linecode::domain;
  assert(IsHttpMcpUrl(" HTTP://localhost:3000/mcp "));
  assert(IsHttpMcpUrl("https://example.test/mcp"));
  assert(!IsHttpMcpUrl("file:///tmp/mcp"));

  const auto normalized = NormalizeMcpExtension(McpExtension{
      .id = " mcp-id ",
      .name = " Local MCP ",
      .url = " https://example.test/mcp/// ",
      .request_headers = {{.name = " X-Key ", .value = " value "},
                          {.name = " ", .value = "ignored"}},
      .tools = {{.name = "",
                 .enabled = true,
                 .description = {},
                 .input_schema_json = {}},
                {.name = "read",
                 .enabled = true,
                 .description = {},
                 .input_schema_json = {}}},
  });
  assert(normalized.id == " mcp-id ");
  assert(normalized.name == " Local MCP ");
  assert(normalized.url == "https://example.test/mcp");
  assert((normalized.request_headers ==
          std::vector<McpRequestHeader>{{.name = "X-Key", .value = "value"}}));
  assert(normalized.tools.size() == 2);
  assert(normalized.tools[1] == McpToolSummary{.name = "read",
                                               .enabled = true,
                                               .description = {},
                                               .input_schema_json = {}});
}

void VerifyMcpToolResponses() {
  using linecode::infrastructure::DecodeMcpToolResponse;
  auto json = DecodeMcpToolResponse(
      R"({"jsonrpc":"2.0","result":{"tools":[{"name":"read","description":"Read"}]}})");
  assert(json.has_value());
  assert(json->size() == 1);
  assert(json->front().name == "read");

  auto event_stream = DecodeMcpToolResponse(
      "event: message\n"
      "data: {\"result\":{\"tools\":[\"one\",\"two\"]}}\n\n");
  assert(event_stream.has_value());
  assert(event_stream->size() == 2);
  assert(event_stream->back().name == "two");
  assert(!DecodeMcpToolResponse(R"({"result":{}})").has_value());
}

} // namespace

int main() {
  VerifyLegacyAgentNormalization();
  VerifyLegacyStringArrays();
  VerifyLegacyMcpCodec();
  VerifyMcpNormalization();
  VerifyMcpToolResponses();
}
