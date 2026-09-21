#include "gtest_support.h"
#include <string>
#include <vector>

#include "domain/extension_config.h"
#include "infrastructure/extension_config_codec.h"

namespace {

void VerifyLegacyAgentNormalization() {
  using namespace linecode::domain;
  EXPECT_EXPRESSION(NormalizeAgentSlug("  My  Agent!  ", "") == "my-agent");
  EXPECT_EXPRESSION(NormalizeAgentSlug("A__B--C", "") == "a__b-c");
  EXPECT_EXPRESSION(NormalizeAgentSlug("", "Fallback Name") == "fallback-name");
  EXPECT_EXPRESSION(NormalizeAgentEditorSlug("  Test  Fixer! ") == "test-fixer");
  EXPECT_EXPRESSION(NormalizeAgentEditorSlug("123 Agent") == "agent-123-agent");
  EXPECT_EXPRESSION(NormalizeAgentEditorSlug("___") == "");
  EXPECT_EXPRESSION(NormalizeAgentEditorSlug(std::string(60, 'a')).size() == 48);

  const auto normalized = NormalizeAgentExtension(AgentExtension{
      .id = " agent-id ",
      .name = " Test Agent ",
      .slug = " ",
      .prompt = "  Do the work.  ",
      .trigger = "  When asked. ",
      .tool_names = {" file_read ", "", " glob"},
      .mcp_ids = {" custom:mcp-id ", "  "},
  });
  EXPECT_EXPRESSION(normalized.id == " agent-id ");
  EXPECT_EXPRESSION(normalized.name == " Test Agent ");
  EXPECT_EXPRESSION(normalized.slug == "test-agent");
  EXPECT_EXPRESSION(normalized.prompt == "  Do the work.  ");
  EXPECT_EXPRESSION(
      (normalized.tool_names == std::vector<std::string>{"file_read", "glob"}));
  EXPECT_EXPRESSION((normalized.mcp_ids == std::vector<std::string>{"custom:mcp-id"}));
}

void VerifyLegacyStringArrays() {
  using namespace linecode::infrastructure;
  const auto encoded =
      EncodeExtensionStringList({" file_read ", "", "glob", "glob"});
  EXPECT_EXPRESSION(encoded == R"(["file_read","glob","glob"])");
  EXPECT_EXPRESSION((DecodeExtensionStringList(encoded) ==
          std::vector<std::string>{"file_read", "glob", "glob"}));
  EXPECT_EXPRESSION(DecodeExtensionStringList("not-json").empty());
  EXPECT_EXPRESSION((DecodeExtensionStringList(R"([" a ",4,null,"b"])") ==
          std::vector<std::string>{"a", "b"}));
}

void VerifyLegacyMcpCodec() {
  using namespace linecode;
  const std::vector<domain::McpRequestHeader> headers{
      {.name = " Authorization ", .value = " Bearer secret "},
      {.name = "", .value = "ignored"},
  };
  const auto header_json = infrastructure::EncodeMcpRequestHeaders(headers);
  EXPECT_EXPRESSION((infrastructure::DecodeMcpRequestHeaders(header_json) ==
          std::vector<domain::McpRequestHeader>{
              {.name = " Authorization ", .value = " Bearer secret "}}));
  EXPECT_EXPRESSION(infrastructure::DecodeMcpRequestHeaders(
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
  EXPECT_EXPRESSION(decoded.size() == 2);
  EXPECT_EXPRESSION(decoded[0].name == tools[0].name);
  EXPECT_EXPRESSION(decoded[0].enabled == tools[0].enabled);
  EXPECT_EXPRESSION(decoded[0].description == tools[0].description);
  EXPECT_EXPRESSION(decoded[0].input_schema_json ==
         R"({"required":["path"],"type":"object"})");
  EXPECT_EXPRESSION(decoded[1] == tools[1]);

  const auto legacy = infrastructure::DecodeMcpTools(
      R"(["string-tool",{"name":"snake","enabled":0,"description":"d","input_schema":{"type":"string"}},{"name":"schema","schema":{"type":"number"}}])");
  EXPECT_EXPRESSION(legacy.size() == 3);
  EXPECT_EXPRESSION(legacy[0] == domain::McpToolSummary{.name = "string-tool",
                                             .enabled = true,
                                             .description = {},
                                             .input_schema_json = {}});
  EXPECT_EXPRESSION(!legacy[1].enabled);
  EXPECT_EXPRESSION(legacy[1].input_schema_json == R"({"type":"string"})");
  EXPECT_EXPRESSION(legacy[2].enabled);
  EXPECT_EXPRESSION(legacy[2].input_schema_json == R"({"type":"number"})");
  EXPECT_EXPRESSION(infrastructure::DecodeMcpTools("{}").empty());
}

void VerifyMcpNormalization() {
  using namespace linecode::domain;
  EXPECT_EXPRESSION(IsHttpMcpUrl(" HTTP://localhost:3000/mcp "));
  EXPECT_EXPRESSION(IsHttpMcpUrl("https://example.test/mcp"));
  EXPECT_EXPRESSION(!IsHttpMcpUrl("file:///tmp/mcp"));

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
  EXPECT_EXPRESSION(normalized.id == " mcp-id ");
  EXPECT_EXPRESSION(normalized.name == " Local MCP ");
  EXPECT_EXPRESSION(normalized.url == "https://example.test/mcp");
  EXPECT_EXPRESSION((normalized.request_headers ==
          std::vector<McpRequestHeader>{{.name = "X-Key", .value = "value"}}));
  EXPECT_EXPRESSION(normalized.tools.size() == 2);
  EXPECT_EXPRESSION(normalized.tools[1] == McpToolSummary{.name = "read",
                                               .enabled = true,
                                               .description = {},
                                               .input_schema_json = {}});
}

void VerifyMcpToolResponses() {
  using linecode::infrastructure::DecodeMcpToolResponse;
  auto json = DecodeMcpToolResponse(
      R"({"jsonrpc":"2.0","result":{"tools":[{"name":"read","description":"Read"}]}})");
  EXPECT_EXPRESSION(json.has_value());
  EXPECT_EXPRESSION(json->size() == 1);
  EXPECT_EXPRESSION(json->front().name == "read");

  auto event_stream = DecodeMcpToolResponse(
      "event: message\n"
      "data: {\"result\":{\"tools\":[\"one\",\"two\"]}}\n\n");
  EXPECT_EXPRESSION(event_stream.has_value());
  EXPECT_EXPRESSION(event_stream->size() == 2);
  EXPECT_EXPRESSION(event_stream->back().name == "two");
  EXPECT_EXPRESSION(!DecodeMcpToolResponse(R"({"result":{}})").has_value());
}

} // namespace

TEST(extension_store_tests, LegacySuite) {
  VerifyLegacyAgentNormalization();
  VerifyLegacyStringArrays();
  VerifyLegacyMcpCodec();
  VerifyMcpNormalization();
  VerifyMcpToolResponses();
}
