#include "infrastructure/archive_redaction.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <iterator>
#include <span>
#include <string>
#include <string_view>
#include <utility>
#include <variant>

#include "infrastructure/archive_json.h"

namespace linecode::infrastructure {
namespace {

namespace json = archive_json;

std::string Lower(std::string_view value) {
  std::string lowered;
  lowered.reserve(value.size());
  std::ranges::transform(value, std::back_inserter(lowered),
                         [](unsigned char character) {
                           return static_cast<char>(std::tolower(character));
                         });
  return lowered;
}

void RedactRecursive(json::Value &value) {
  if (auto *object = std::get_if<json::Object>(&value)) {
    for (auto &[key, child] : *object) {
      if (IsSensitiveArchiveName(key)) {
        child = std::string{};
      } else {
        RedactRecursive(child);
      }
    }
    return;
  }
  if (auto *array = std::get_if<json::Array>(&value)) {
    for (auto &child : *array) {
      RedactRecursive(child);
    }
  }
}

std::string RedactObjectFields(std::string_view raw,
                               std::span<const std::string_view> fields) {
  if (raw.empty()) {
    return {};
  }
  auto parsed = json::Parse(raw);
  auto *object = parsed ? std::get_if<json::Object>(&*parsed) : nullptr;
  if (!object) {
    return {};
  }
  for (const auto field : fields) {
    if (auto found = object->find(field); found != object->end()) {
      found->second = std::string{};
    }
  }
  return json::Serialize(*parsed);
}

std::string RedactSshSetting(std::string_view raw) {
  constexpr std::array<std::string_view, 3> fields{"password", "privateKey",
                                                   "passphrase"};
  return RedactObjectFields(raw, fields);
}

std::string RedactWebSearchSetting(std::string_view raw) {
  constexpr std::array<std::string_view, 1> fields{"apiKey"};
  return RedactObjectFields(raw, fields);
}

using StructuredSettingRedactor = std::string (*)(std::string_view);

struct StructuredSettingRule final {
  std::string_view key;
  StructuredSettingRedactor redact;
};

constexpr std::array kStructuredSettingRules{
    StructuredSettingRule{"@lineai_ssh_config", RedactSshSetting},
    StructuredSettingRule{"@lineai_web_search_config",
                          RedactWebSearchSetting},
};

} // namespace

bool IsSensitiveArchiveName(std::string_view value) {
  constexpr std::array keywords{
      "apikey",      "api_key",     "api-key",    "authorization",
      "password",    "passwd",      "passphrase", "privatekey",
      "private_key", "private-key", "secret",     "token",
      "cookie",
  };
  const auto lowered = Lower(value);
  return std::ranges::any_of(keywords, [&](std::string_view keyword) {
    return lowered.contains(keyword);
  });
}

std::string RedactArchiveJsonSecrets(std::string_view raw) {
  if (raw.empty()) {
    return {};
  }
  auto parsed = json::Parse(raw);
  if (!parsed) {
    return {};
  }
  RedactRecursive(*parsed);
  return json::Serialize(*parsed);
}

std::string RedactArchiveHeaders(std::string_view raw) {
  if (raw.empty()) {
    return "[]";
  }
  auto parsed = json::Parse(raw);
  auto *array = parsed ? std::get_if<json::Array>(&*parsed) : nullptr;
  if (!array) {
    return {};
  }
  for (auto &value : *array) {
    auto *object = std::get_if<json::Object>(&value);
    if (!object) {
      continue;
    }
    const auto *name_value = json::Find(*object, "name");
    const auto *name = json::AsString(name_value);
    if (name && IsSensitiveArchiveName(*name)) {
      (*object)["value"] = std::string{};
    }
  }
  return json::Serialize(*parsed);
}

std::string RedactArchiveSettingValue(std::string_view key,
                                      std::string_view raw) {
  const auto structured = std::ranges::find(kStructuredSettingRules, key,
                                             &StructuredSettingRule::key);
  if (structured != kStructuredSettingRules.end()) {
    return structured->redact(raw);
  }
  return IsSensitiveArchiveName(key) ? std::string{} : std::string{raw};
}

} // namespace linecode::infrastructure
