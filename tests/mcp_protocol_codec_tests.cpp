#include "gtest_support.h"
#include <string>

#include "domain/extension_config.h"
#include "infrastructure/archive_json.h"
#include "infrastructure/mcp_protocol_codec.h"

namespace {

using linecode::infrastructure::BuildMcpCallRequest;
using linecode::infrastructure::BuildMcpInitializeRequest;
using linecode::infrastructure::DecodeMcpCallResponse;
namespace json = linecode::infrastructure::archive_json;

const json::Object &ObjectValue(const json::Value *value) {
  const auto *object = json::AsObject(value);
  EXPECT_EXPRESSION(object);
  return *object;
}

std::string Text(const json::Object &object, std::string_view name) {
  const auto *text = json::AsString(json::Find(object, name));
  EXPECT_EXPRESSION(text);
  return *text;
}

void EncodesInitializeRequest() {
  const auto decoded = json::Parse(BuildMcpInitializeRequest("init-1"));
  EXPECT_EXPRESSION(decoded);
  const auto &root = ObjectValue(&*decoded);
  EXPECT_EXPRESSION(Text(root, "jsonrpc") == "2.0");
  EXPECT_EXPRESSION(Text(root, "id") == "init-1");
  EXPECT_EXPRESSION(Text(root, "method") == "initialize");
  const auto &params = ObjectValue(json::Find(root, "params"));
  EXPECT_EXPRESSION(Text(params, "protocolVersion") == "2025-03-26");
  const auto &client = ObjectValue(json::Find(params, "clientInfo"));
  EXPECT_EXPRESSION(Text(client, "name") == "linecode");
  EXPECT_EXPRESSION(Text(client, "version") == "1.0");
}

void EncodesCallAndRejectsNonObjectArguments() {
  const auto encoded = BuildMcpCallRequest(
      "call-1", "repo_search", R"({"query":"a\"b","limit":3})");
  EXPECT_EXPRESSION(encoded);
  const auto decoded = json::Parse(*encoded);
  EXPECT_EXPRESSION(decoded);
  const auto &root = ObjectValue(&*decoded);
  EXPECT_EXPRESSION(Text(root, "method") == "tools/call");
  const auto &params = ObjectValue(json::Find(root, "params"));
  EXPECT_EXPRESSION(Text(params, "name") == "repo_search");
  const auto &arguments = ObjectValue(json::Find(params, "arguments"));
  EXPECT_EXPRESSION(Text(arguments, "query") == "a\"b");

  EXPECT_EXPRESSION(!BuildMcpCallRequest("call-2", "bad", "[]"));
  EXPECT_EXPRESSION(!BuildMcpCallRequest("call-3", "bad", "not json"));
}

void DecodesJsonAndSseResults() {
  EXPECT_EXPRESSION(DecodeMcpCallResponse(
             R"({"jsonrpc":"2.0","result":{"text":"done"}})") ==
         linecode::infrastructure::DecodedMcpCallResult{.content = "done",
                                                        .error = false});
  EXPECT_EXPRESSION(DecodeMcpCallResponse(
             "event: message\r\ndata: {\"result\":{\"content\":\"from "
             "sse\"}}\r\ndata: [DONE]\r\n") ==
         linecode::infrastructure::DecodedMcpCallResult{
             .content = "from sse", .error = false});
  EXPECT_EXPRESSION(DecodeMcpCallResponse(
             R"({"jsonrpc":"2.0","error":{"message":"denied"}})") ==
         linecode::infrastructure::DecodedMcpCallResult{.content = "denied",
                                                        .error = true});
  EXPECT_EXPRESSION(DecodeMcpCallResponse("plain response") ==
         linecode::infrastructure::DecodedMcpCallResult{
             .content = "plain response", .error = false});
}

void PreservesLegacyToolNames() {
  EXPECT_EXPRESSION(linecode::domain::McpExtensionToolName("server-1", "repo search") ==
         "mcpx_42svxm_repo_search");
  EXPECT_EXPRESSION(linecode::domain::McpExtensionToolName("", "123") ==
         "mcpx_45h_tool_123");
  EXPECT_EXPRESSION(linecode::domain::McpExtensionToolName("server-1", "___") ==
         "mcpx_42svxm_tool");
  EXPECT_EXPRESSION(linecode::domain::McpExtensionToolName("server-1",
                                                std::string(100, 'a'))
             .size() <= 64);
}

} // namespace

TEST(mcp_protocol_codec_tests, LegacySuite) {
  EncodesInitializeRequest();
  EncodesCallAndRejectsNonObjectArguments();
  DecodesJsonAndSseResults();
  PreservesLegacyToolNames();
}
