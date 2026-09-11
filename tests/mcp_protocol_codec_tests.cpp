#include <cassert>
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
  assert(object);
  return *object;
}

std::string Text(const json::Object &object, std::string_view name) {
  const auto *text = json::AsString(json::Find(object, name));
  assert(text);
  return *text;
}

void EncodesInitializeRequest() {
  const auto decoded = json::Parse(BuildMcpInitializeRequest("init-1"));
  assert(decoded);
  const auto &root = ObjectValue(&*decoded);
  assert(Text(root, "jsonrpc") == "2.0");
  assert(Text(root, "id") == "init-1");
  assert(Text(root, "method") == "initialize");
  const auto &params = ObjectValue(json::Find(root, "params"));
  assert(Text(params, "protocolVersion") == "2025-03-26");
  const auto &client = ObjectValue(json::Find(params, "clientInfo"));
  assert(Text(client, "name") == "linecode");
  assert(Text(client, "version") == "1.0");
}

void EncodesCallAndRejectsNonObjectArguments() {
  const auto encoded = BuildMcpCallRequest(
      "call-1", "repo_search", R"({"query":"a\"b","limit":3})");
  assert(encoded);
  const auto decoded = json::Parse(*encoded);
  assert(decoded);
  const auto &root = ObjectValue(&*decoded);
  assert(Text(root, "method") == "tools/call");
  const auto &params = ObjectValue(json::Find(root, "params"));
  assert(Text(params, "name") == "repo_search");
  const auto &arguments = ObjectValue(json::Find(params, "arguments"));
  assert(Text(arguments, "query") == "a\"b");

  assert(!BuildMcpCallRequest("call-2", "bad", "[]"));
  assert(!BuildMcpCallRequest("call-3", "bad", "not json"));
}

void DecodesJsonAndSseResults() {
  assert(DecodeMcpCallResponse(
             R"({"jsonrpc":"2.0","result":{"text":"done"}})") ==
         linecode::infrastructure::DecodedMcpCallResult{.content = "done",
                                                        .error = false});
  assert(DecodeMcpCallResponse(
             "event: message\r\ndata: {\"result\":{\"content\":\"from "
             "sse\"}}\r\ndata: [DONE]\r\n") ==
         linecode::infrastructure::DecodedMcpCallResult{
             .content = "from sse", .error = false});
  assert(DecodeMcpCallResponse(
             R"({"jsonrpc":"2.0","error":{"message":"denied"}})") ==
         linecode::infrastructure::DecodedMcpCallResult{.content = "denied",
                                                        .error = true});
  assert(DecodeMcpCallResponse("plain response") ==
         linecode::infrastructure::DecodedMcpCallResult{
             .content = "plain response", .error = false});
}

void PreservesLegacyToolNames() {
  assert(linecode::domain::McpExtensionToolName("server-1", "repo search") ==
         "mcpx_42svxm_repo_search");
  assert(linecode::domain::McpExtensionToolName("", "123") ==
         "mcpx_45h_tool_123");
  assert(linecode::domain::McpExtensionToolName("server-1", "___") ==
         "mcpx_42svxm_tool");
  assert(linecode::domain::McpExtensionToolName("server-1",
                                                std::string(100, 'a'))
             .size() <= 64);
}

} // namespace

int main() {
  EncodesInitializeRequest();
  EncodesCallAndRejectsNonObjectArguments();
  DecodesJsonAndSseResults();
  PreservesLegacyToolNames();
}
