#include "application/tool_permission_service.h"

#include <algorithm>
#include <array>
#include <bit>
#include <cctype>
#include <cstddef>
#include <cstdint>
#include <ranges>
#include <stdexcept>
#include <string_view>
#include <utility>
#include <vector>

#include "infrastructure/archive_json.h"

namespace linecode::application {
namespace {

namespace json = infrastructure::archive_json;

constexpr auto kPermissionModeKey = "@lineai_permission_mode";
constexpr auto kCommandGrantsKey = "@linecode_command_grants_v1";
constexpr std::string_view kShellToolName = "shell_execute";

constexpr std::array<std::uint32_t, 64> kSha256RoundConstants{
    0x428a2f98U, 0x71374491U, 0xb5c0fbcfU, 0xe9b5dba5U,
    0x3956c25bU, 0x59f111f1U, 0x923f82a4U, 0xab1c5ed5U,
    0xd807aa98U, 0x12835b01U, 0x243185beU, 0x550c7dc3U,
    0x72be5d74U, 0x80deb1feU, 0x9bdc06a7U, 0xc19bf174U,
    0xe49b69c1U, 0xefbe4786U, 0x0fc19dc6U, 0x240ca1ccU,
    0x2de92c6fU, 0x4a7484aaU, 0x5cb0a9dcU, 0x76f988daU,
    0x983e5152U, 0xa831c66dU, 0xb00327c8U, 0xbf597fc7U,
    0xc6e00bf3U, 0xd5a79147U, 0x06ca6351U, 0x14292967U,
    0x27b70a85U, 0x2e1b2138U, 0x4d2c6dfcU, 0x53380d13U,
    0x650a7354U, 0x766a0abbU, 0x81c2c92eU, 0x92722c85U,
    0xa2bfe8a1U, 0xa81a664bU, 0xc24b8b70U, 0xc76c51a3U,
    0xd192e819U, 0xd6990624U, 0xf40e3585U, 0x106aa070U,
    0x19a4c116U, 0x1e376c08U, 0x2748774cU, 0x34b0bcb5U,
    0x391c0cb3U, 0x4ed8aa4aU, 0x5b9cca4fU, 0x682e6ff3U,
    0x748f82eeU, 0x78a5636fU, 0x84c87814U, 0x8cc70208U,
    0x90befffaU, 0xa4506cebU, 0xbef9a3f7U, 0xc67178f2U,
};

[[nodiscard]] std::string Sha256(std::string_view input) {
  std::vector<std::uint8_t> bytes(input.begin(), input.end());
  const auto bit_length = static_cast<std::uint64_t>(bytes.size()) * 8U;
  bytes.push_back(0x80U);
  while (bytes.size() % 64U != 56U)
    bytes.push_back(0U);
  for (int shift = 56; shift >= 0; shift -= 8)
    bytes.push_back(static_cast<std::uint8_t>(bit_length >> shift));

  std::array<std::uint32_t, 8> hash{
      0x6a09e667U, 0xbb67ae85U, 0x3c6ef372U, 0xa54ff53aU,
      0x510e527fU, 0x9b05688cU, 0x1f83d9abU, 0x5be0cd19U,
  };
  for (std::size_t block = 0; block < bytes.size(); block += 64U) {
    std::array<std::uint32_t, 64> words{};
    for (std::size_t index = 0; index < 16U; ++index) {
      const auto offset = block + index * 4U;
      words[index] = static_cast<std::uint32_t>(bytes[offset]) << 24U |
                     static_cast<std::uint32_t>(bytes[offset + 1U]) << 16U |
                     static_cast<std::uint32_t>(bytes[offset + 2U]) << 8U |
                     static_cast<std::uint32_t>(bytes[offset + 3U]);
    }
    for (std::size_t index = 16U; index < words.size(); ++index) {
      const auto s0 = std::rotr(words[index - 15U], 7) ^
                      std::rotr(words[index - 15U], 18) ^
                      (words[index - 15U] >> 3U);
      const auto s1 = std::rotr(words[index - 2U], 17) ^
                      std::rotr(words[index - 2U], 19) ^
                      (words[index - 2U] >> 10U);
      words[index] = words[index - 16U] + s0 + words[index - 7U] + s1;
    }

    auto [a, b, c, d, e, f, g, h] = hash;
    for (std::size_t index = 0; index < words.size(); ++index) {
      const auto sum1 = std::rotr(e, 6) ^ std::rotr(e, 11) ^
                        std::rotr(e, 25);
      const auto choice = (e & f) ^ (~e & g);
      const auto temporary1 =
          h + sum1 + choice + kSha256RoundConstants[index] + words[index];
      const auto sum0 = std::rotr(a, 2) ^ std::rotr(a, 13) ^
                        std::rotr(a, 22);
      const auto majority = (a & b) ^ (a & c) ^ (b & c);
      const auto temporary2 = sum0 + majority;
      h = g;
      g = f;
      f = e;
      e = d + temporary1;
      d = c;
      c = b;
      b = a;
      a = temporary1 + temporary2;
    }
    hash[0] += a;
    hash[1] += b;
    hash[2] += c;
    hash[3] += d;
    hash[4] += e;
    hash[5] += f;
    hash[6] += g;
    hash[7] += h;
  }

  constexpr std::string_view digits = "0123456789abcdef";
  std::string output;
  output.reserve(64U);
  for (const auto word : hash) {
    for (int shift = 28; shift >= 0; shift -= 4)
      output.push_back(digits[(word >> shift) & 0x0fU]);
  }
  return output;
}

[[nodiscard]] std::string Trimmed(std::string_view value) {
  const auto visible = [](unsigned char character) {
    return std::isspace(character) == 0;
  };
  const auto begin = std::ranges::find_if(value, visible);
  if (begin == value.end())
    return {};
  const auto end = std::ranges::find_if(value | std::views::reverse, visible);
  return std::string{begin, end.base()};
}

[[nodiscard]] std::vector<std::string> ParseGrants(std::string_view stored) {
  const auto value = json::Parse(stored);
  const auto *array = value ? json::AsArray(&*value) : nullptr;
  if (!array)
    return {};
  std::vector<std::string> grants;
  grants.reserve(array->size());
  for (const auto &item : *array) {
    if (const auto *text = json::AsString(&item); text && !text->empty())
      grants.push_back(*text);
  }
  return grants;
}

[[nodiscard]] std::string SerializeGrants(
    const std::vector<std::string> &grants) {
  json::Array values;
  values.reserve(grants.size());
  for (const auto &grant : grants)
    values.emplace_back(grant);
  return json::Serialize(values);
}

[[nodiscard]] std::string GrantKey(const RegisteredTool &tool,
                                   const CompletionToolCall &call,
                                   std::string_view scope) {
  if (!tool.permanent_grant_supported || scope.empty() ||
      call.name != kShellToolName)
    return {};
  const auto arguments = json::Parse(call.arguments_json);
  const auto *object = arguments ? json::AsObject(&*arguments) : nullptr;
  if (!object)
    return {};
  const auto *command = json::AsString(json::Find(*object, "command"));
  if (!command || Trimmed(*command).empty())
    return {};
  std::string cwd;
  if (const auto *value = json::AsString(json::Find(*object, "cwd")))
    cwd = Trimmed(*value);
  return Sha256(json::Serialize(json::Array{
      std::string{scope}, call.name, *command, std::move(cwd)}));
}

} // namespace

ToolPermissionService::ToolPermissionService(
    std::shared_ptr<AsyncSettingsStore> store)
    : store_(std::move(store)) {
  if (!store_)
    throw std::invalid_argument(
        "ToolPermissionService requires a settings store");
}

huxerui::Task<SettingsResult<ToolPermissionState>>
ToolPermissionService::Load() {
  auto mode = co_await store_->GetString(kPermissionModeKey, "auto");
  if (!mode)
    co_return std::unexpected(std::move(mode.error()));
  auto grants = co_await store_->GetString(kCommandGrantsKey, "[]");
  if (!grants)
    co_return std::unexpected(std::move(grants.error()));
  co_return ToolPermissionState{
      .mode = domain::ParseToolPermissionMode(*mode),
      .has_permanent_grants = !ParseGrants(*grants).empty(),
  };
}

huxerui::Task<SettingsResult<void>>
ToolPermissionService::SetMode(domain::ToolPermissionMode mode) {
  co_return co_await store_->SetString(
      kPermissionModeKey,
      std::string{domain::SerializeToolPermissionMode(mode)});
}

huxerui::Task<SettingsResult<void>>
ToolPermissionService::ClearPermanentGrants() {
  co_return co_await store_->Remove(kCommandGrantsKey);
}

huxerui::Task<SettingsResult<ToolPermissionDecision>>
ToolPermissionService::Evaluate(const RegisteredTool &tool,
                                const CompletionToolCall &call,
                                std::string permission_scope) {
  auto state = co_await Load();
  if (!state)
    co_return std::unexpected(std::move(state.error()));
  if (state->mode == domain::ToolPermissionMode::read_only) {
    co_return tool.allowed_in_read_only ? ToolPermissionDecision::execute
                                       : ToolPermissionDecision::deny;
  }
  if (state->mode == domain::ToolPermissionMode::automatic)
    co_return ToolPermissionDecision::execute;

  const auto key = GrantKey(tool, call, permission_scope);
  if (!key.empty()) {
    auto stored = co_await store_->GetString(kCommandGrantsKey, "[]");
    if (!stored)
      co_return std::unexpected(std::move(stored.error()));
    const auto grants = ParseGrants(*stored);
    if (std::ranges::find(grants, key) != grants.end())
      co_return ToolPermissionDecision::execute;
  }
  co_return ToolPermissionDecision::review;
}

huxerui::Task<SettingsResult<void>>
ToolPermissionService::RememberPermanentGrant(
    const RegisteredTool &tool, const CompletionToolCall &call,
    std::string permission_scope) {
  const auto key = GrantKey(tool, call, permission_scope);
  if (key.empty())
    co_return SettingsResult<void>{};
  auto stored = co_await store_->GetString(kCommandGrantsKey, "[]");
  if (!stored)
    co_return std::unexpected(std::move(stored.error()));
  auto grants = ParseGrants(*stored);
  if (std::ranges::find(grants, key) != grants.end())
    co_return SettingsResult<void>{};
  if (grants.size() > 511U)
    grants.erase(grants.begin(), grants.end() - 511);
  grants.push_back(key);
  co_return co_await store_->SetString(kCommandGrantsKey,
                                       SerializeGrants(grants));
}

} // namespace linecode::application
