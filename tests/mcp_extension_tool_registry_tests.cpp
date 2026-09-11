#include <cassert>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "application/mcp_extension_tool_registry.h"
#include "infrastructure/json_mcp_tool_schema_policy.h"

namespace {

using namespace linecode;

class StubStore final : public application::McpExtensionStore {
public:
  huxerui::Task<application::ExtensionStoreResult<
      std::vector<domain::McpExtension>>>
  ListMcps() override {
    co_return std::vector<domain::McpExtension>{};
  }
  huxerui::Task<application::ExtensionStoreResult<
      std::optional<domain::McpExtension>>>
  FindMcp(std::string) override {
    co_return std::optional<domain::McpExtension>{};
  }
  huxerui::Task<application::ExtensionStoreResult<domain::McpExtension>>
  SaveMcp(domain::McpExtension value) override {
    co_return value;
  }
  huxerui::Task<application::ExtensionStoreResult<void>>
  SetMcpEnabled(std::string, bool) override {
    co_return application::ExtensionStoreResult<void>{};
  }
  huxerui::Task<application::ExtensionStoreResult<void>>
  DeleteMcps(std::vector<std::string>) override {
    co_return application::ExtensionStoreResult<void>{};
  }
};

class StubInvoker final : public application::McpToolInvoker {
public:
  huxerui::Task<std::expected<application::McpToolInvocationResult,
                              application::McpToolInvocationError>>
  Invoke(domain::McpExtension, domain::McpToolSummary,
         std::string) override {
    co_return application::McpToolInvocationResult{.content = "ok"};
  }
};

application::McpExtensionToolRegistry Registry() {
  return {std::make_shared<StubStore>(), std::make_shared<StubInvoker>(),
          std::make_shared<infrastructure::JsonMcpToolSchemaPolicy>()};
}

domain::McpExtension Extension(std::string id, bool enabled,
                               std::string name,
                               std::vector<domain::McpToolSummary> tools) {
  return {.id = std::move(id),
          .enabled = enabled,
          .name = std::move(name),
          .url = "https://mcp.example/rpc",
          .request_headers = {},
          .tools = std::move(tools),
          .created_at = 0,
          .updated_at = 0};
}

void IndexesOnlyEnabledTools() {
  auto registry = Registry();
  registry.Update({
      Extension("server-1", true, "Source MCP",
                {{.name = "repo search",
                  .enabled = true,
                  .description = "Search repositories",
                  .input_schema_json =
                      R"({"required":["query"],"type":"object"})"},
                 {.name = "disabled",
                  .enabled = false,
                  .description = {},
                  .input_schema_json = {}}}),
      Extension("server-2", false, "Disabled MCP",
                {{.name = "hidden",
                  .enabled = true,
                  .description = {},
                  .input_schema_json = {}}}),
  });
  const auto tools = registry.Tools();
  assert(tools.size() == 1);
  assert(tools[0].name == "mcpx_42svxm_repo_search");
  assert(tools[0].description ==
         "Invoke the tool repo search of the custom HTTP MCP \"Source "
         "MCP\".\nSearch repositories\nMCP address: "
         "https://mcp.example/rpc");
  assert(tools[0].parameters_json ==
         R"({"required":["query"],"type":"object"})");
  assert(registry.Contains(tools[0].name));
  assert(!registry.Contains("mcpx_missing"));
}

void ReplacesInvalidSchemasAndRemovedBindings() {
  auto registry = Registry();
  registry.Update({Extension(
      "server-1", true, "MCP",
      {{.name = "bad",
        .enabled = true,
        .description = {},
        .input_schema_json = "[]"},
       {.name = "wrong-type",
        .enabled = true,
        .description = {},
        .input_schema_json = R"({"type":"string"})"}})});
  assert(registry.Tools().size() == 2);
  for (const auto &tool : registry.Tools()) {
    assert(tool.parameters_json ==
           R"({"additionalProperties":true,"properties":{},"type":"object"})");
  }
  registry.Update({});
  assert(registry.Tools().empty());
  assert(!registry.Contains("mcpx_42svxm_bad"));
}

void LastDuplicateWinsWithoutChangingOrder() {
  auto registry = Registry();
  registry.Update({
      Extension("same-id", true, "First",
                {{.name = "same",
                  .enabled = true,
                  .description = "old",
                  .input_schema_json = {}}}),
      Extension("same-id", true, "Second",
                {{.name = "same",
                  .enabled = true,
                  .description = "new",
                  .input_schema_json = {}}}),
  });
  assert(registry.Tools().size() == 1);
  assert(registry.Tools()[0].description.contains("Second"));
  assert(registry.Tools()[0].description.contains("new"));
  assert(!registry.Tools()[0].description.contains("old"));
}

} // namespace

int main() {
  IndexesOnlyEnabledTools();
  ReplacesInvalidSchemasAndRemovedBindings();
  LastDuplicateWinsWithoutChangingOrder();
}
