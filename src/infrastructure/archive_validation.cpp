#include "infrastructure/archive_validation.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cstdint>
#include <limits>
#include <map>
#include <optional>
#include <set>
#include <string>
#include <string_view>
#include <variant>
#include <utility>
#include <vector>

#include "infrastructure/archive_json.h"
#include "infrastructure/linecode_zip.h"

namespace linecode::infrastructure {
namespace {

namespace json = archive_json;

std::unexpected<ArchiveValidationError> Invalid(std::string message) {
  return std::unexpected(ArchiveValidationError{std::move(message)});
}

const std::int64_t *Integer(const json::Value *value) {
  return value ? std::get_if<std::int64_t>(value) : nullptr;
}

const bool *Boolean(const json::Value *value) {
  return value ? std::get_if<bool>(value) : nullptr;
}

bool IsKnownWorkspaceRoot(std::string_view name) {
  constexpr std::array<std::string_view, 3> names{"home", "project",
                                                  "skills"};
  return std::ranges::find(names, name) != names.end();
}

bool IsValidBase64(std::string_view text) {
  if (text.size() % 4U != 0U) {
    return false;
  }
  const auto digit = [](char value) {
    return (value >= 'A' && value <= 'Z') ||
           (value >= 'a' && value <= 'z') ||
           (value >= '0' && value <= '9') || value == '+' || value == '/';
  };
  for (std::size_t index = 0; index < text.size(); index += 4) {
    const bool final = index + 4U == text.size();
    if (!digit(text[index]) || !digit(text[index + 1])) {
      return false;
    }
    const char third = text[index + 2];
    const char fourth = text[index + 3];
    if ((!digit(third) && third != '=') ||
        (!digit(fourth) && fourth != '=') ||
        ((third == '=' || fourth == '=') && !final) ||
        (third == '=' && fourth != '=')) {
      return false;
    }
  }
  return true;
}

std::expected<void, ArchiveValidationError>
ValidateCell(const json::Value &value) {
  const auto *cell = json::AsObject(&value);
  if (!cell) {
    return Invalid("database row cell is not an object");
  }
  const auto *type = json::AsString(json::Find(*cell, "type"));
  if (!type) {
    return Invalid("database row cell has no type");
  }
  const auto *stored = json::Find(*cell, "value");
  if (*type == "null") {
    if (stored) {
      return Invalid("null database cell unexpectedly has a value");
    }
    return {};
  }
  if (*type == "integer") {
    if (!Integer(stored)) {
      return Invalid("invalid integer database cell");
    }
    return {};
  }
  if (*type == "float") {
    if (!Integer(stored) && !(stored && std::get_if<double>(stored))) {
      return Invalid("invalid float database cell");
    }
    return {};
  }
  const auto *text = json::AsString(stored);
  if (!text) {
    return Invalid("invalid text database cell");
  }
  if (*type == "string") {
    return {};
  }
  if (*type == "blob") {
    return IsValidBase64(*text)
               ? std::expected<void, ArchiveValidationError>{}
               : Invalid("invalid base64 database cell");
  }
  return Invalid("unsupported database cell type");
}

std::string BytesText(std::span<const std::byte> bytes) {
  std::string text;
  text.reserve(bytes.size());
  for (const auto value : bytes)
    text.push_back(static_cast<char>(std::to_integer<unsigned char>(value)));
  return text;
}

const std::string *ObjectString(const json::Object &object,
                                std::string_view key) {
  return json::AsString(json::Find(object, key));
}

std::string StringOr(const json::Object &object, std::string_view key,
                     std::string fallback = {}) {
  const auto *value = ObjectString(object, key);
  return value ? *value : std::move(fallback);
}

std::int64_t IntegerOr(const json::Object &object, std::string_view key,
                       std::int64_t fallback) {
  const auto *value = Integer(json::Find(object, key));
  return value ? *value : fallback;
}

bool BooleanOr(const json::Object &object, std::string_view key,
               bool fallback) {
  const auto *value = Boolean(json::Find(object, key));
  return value ? *value : fallback;
}

std::string LowerTrimmed(std::string_view value) {
  const auto first = std::ranges::find_if(value, [](unsigned char character) {
    return std::isspace(character) == 0;
  });
  const auto last =
      std::find_if(value.rbegin(), value.rend(), [](unsigned char character) {
        return std::isspace(character) == 0;
      }).base();
  if (first >= last)
    return {};
  std::string result(first, last);
  std::ranges::transform(result, result.begin(), [](unsigned char character) {
    return static_cast<char>(std::tolower(character));
  });
  return result;
}

domain::ModelProtocol LegacyProtocol(std::string_view value) {
  const auto normalized = LowerTrimmed(value);
  if (normalized == "codex" || normalized == "codex_responses")
    return domain::ModelProtocol::codex_responses;
  if (normalized == "anthropic" || normalized == "claude" ||
      normalized == "anthropic_messages")
    return domain::ModelProtocol::anthropic_messages;
  if (normalized == "local" || normalized == "gguf" ||
      normalized == "local_gguf")
    return domain::ModelProtocol::local_gguf;
  return domain::ModelProtocol::openai_compatible;
}

std::string_view ProtocolName(domain::ModelProtocol value) {
  switch (value) {
  case domain::ModelProtocol::openai_compatible:
    return "OPENAI_COMPATIBLE";
  case domain::ModelProtocol::codex_responses:
    return "CODEX_RESPONSES";
  case domain::ModelProtocol::anthropic_messages:
    return "ANTHROPIC_MESSAGES";
  case domain::ModelProtocol::local_gguf:
    return "LOCAL_GGUF";
  }
  return "OPENAI_COMPATIBLE";
}

std::string_view ProtocolLabel(domain::ModelProtocol value) {
  switch (value) {
  case domain::ModelProtocol::openai_compatible:
    return "OpenAI";
  case domain::ModelProtocol::codex_responses:
    return "Codex";
  case domain::ModelProtocol::anthropic_messages:
    return "Anthropic";
  case domain::ModelProtocol::local_gguf:
    return "Local";
  }
  return "OpenAI";
}

int BoundedInt(std::int64_t value, int fallback) {
  if (value < std::numeric_limits<int>::min() ||
      value > std::numeric_limits<int>::max())
    return fallback;
  return static_cast<int>(value);
}

domain::ModelConfig DecodeLegacyModel(const json::Object &object) {
  std::string protocol_value = StringOr(object, "protocolType");
  if (protocol_value.empty())
    protocol_value = StringOr(object, "provider");
  const auto protocol = LegacyProtocol(protocol_value);
  std::string provider = StringOr(object, "providerLabel");
  if (provider.empty())
    provider = ProtocolLabel(protocol);
  std::string model_id = StringOr(object, "modelId");
  if (model_id.empty()) {
    const auto *local = json::AsObject(json::Find(object, "localModel"));
    if (local) {
      model_id = StringOr(*local, "fileName");
      if (model_id.empty())
        model_id = StringOr(*local, "localPath");
    }
  }
  auto tool_limit = BoundedInt(
      IntegerOr(object, "toolCallLimit",
                IntegerOr(object, "tool_call_limit", 200)),
      200);
  if (tool_limit < -1)
    tool_limit = 0;
  const bool compression_enabled = BooleanOr(
      object, "compressionModelEnabled",
      BooleanOr(object, "compression_model_enabled", false));
  const bool compression_auto = BooleanOr(
      object, "compressionModelAuto",
      BooleanOr(object, "compression_model_auto", true));
  std::string compression_id = StringOr(object, "compressionModelId");
  if (compression_id.empty())
    compression_id = StringOr(object, "compression_model_id");
  const auto first = compression_id.find_first_not_of(" \t\n\r");
  const auto last = compression_id.find_last_not_of(" \t\n\r");
  compression_id = first == std::string::npos
                       ? std::string{}
                       : compression_id.substr(first, last - first + 1U);
  auto context_size = BoundedInt(
      IntegerOr(object, "contextSize", IntegerOr(object, "context_size", 0)),
      0);
  if (context_size < 0)
    context_size = 0;
  return domain::ModelConfig{
      .id = StringOr(object, "id"),
      .name = StringOr(object, "name"),
      .protocol = protocol,
      .provider_label = std::move(provider),
      .base_url = StringOr(object, "baseUrl"),
      .api_key = StringOr(object, "apiKey"),
      .model_id = std::move(model_id),
      .tool_call_limit = tool_limit,
      .compression_model_enabled =
          compression_enabled &&
          (protocol == domain::ModelProtocol::openai_compatible ||
           protocol == domain::ModelProtocol::codex_responses),
      .compression_model_auto = compression_auto,
      .compression_model_id = std::move(compression_id),
      .context_size = context_size,
  };
}

json::Value LegacyModelJson(const domain::ModelConfig &model) {
  return json::Object{
      {"apiKey", model.api_key},
      {"baseUrl", model.base_url},
      {"compressionModelAuto", model.compression_model_auto},
      {"compressionModelEnabled", model.compression_model_enabled},
      {"compressionModelId", model.compression_model_id},
      {"contextSize", std::int64_t{model.context_size}},
      {"id", model.id},
      {"modelId", model.model_id},
      {"name", model.name},
      {"protocolType", std::string{ProtocolName(model.protocol)}},
      {"providerLabel", model.provider_label},
      {"toolCallLimit", std::int64_t{model.tool_call_limit}},
  };
}

std::string SafeConversationFileName(std::string_view id) {
  std::string safe;
  safe.reserve(id.size());
  for (const unsigned char value : id) {
    safe.push_back(std::isalnum(value) != 0 || value == '_' || value == '-'
                       ? static_cast<char>(value)
                       : '_');
  }
  return (safe.empty() ? std::string{"conversation"} : std::move(safe)) +
         ".json";
}

std::string SafeSelectedFileName(std::string file_name,
                                 std::string_view conversation_id) {
  if (file_name.empty() || file_name.contains('/') || file_name.contains('\\') ||
      file_name.contains(".."))
    return SafeConversationFileName(conversation_id);
  return file_name;
}

bool IsBareLegacyMessageId(std::string_view id) {
  return id.size() >= 2U && id.front() == 'm' &&
         std::ranges::all_of(id.substr(1),
                             [](unsigned char value) {
                               return std::isdigit(value) != 0;
                             });
}

std::string ScopedMessageId(std::string_view conversation_id,
                            std::string id, std::size_t order) {
  if (id.empty())
    return std::string{conversation_id} + ':' + std::to_string(order);
  if (!conversation_id.empty() && IsBareLegacyMessageId(id) &&
      !id.starts_with(conversation_id))
    return std::string{conversation_id} + ':' + id;
  return id;
}

std::string LegacyRole(std::string_view role) {
  if (role == "system" || role == "assistant" || role == "tool")
    return std::string{role};
  return "user";
}

bool IsLegacySettingKey(std::string_view key) {
  if (!key.starts_with("@lineai_") && !key.starts_with("@linecode_"))
    return false;
  constexpr std::array<std::string_view, 4> reserved{
      "@lineai_models", "@lineai_selected_model",
      "@lineai_conversation_list", "@lineai_current_conversation"};
  return std::ranges::find(reserved, key) == reserved.end() &&
         !key.starts_with("@lineai_conv_") &&
         !key.starts_with("@lineai_conv_chunk_");
}

// Mirrors `LineCodeArchiveCodec.messageJson`
// (`LineCodeArchiveCodec.java:225-239`). `raw_json` is folded in first, then
// the structured fields overwrite, which is what the legacy `objectFromRaw`
// plus successive `put` calls amount to.
json::Value LegacyMessageJson(const application::LegacyArchiveMessage &message) {
  json::Object object;
  if (!message.raw_json.empty()) {
    if (auto parsed = json::Parse(message.raw_json);
        parsed && json::AsObject(&*parsed) != nullptr)
      object = *json::AsObject(&*parsed);
  }
  object.insert_or_assign("id", message.id);
  object.insert_or_assign("role", LegacyRole(message.role));
  object.insert_or_assign("content", message.content);
  object.insert_or_assign("reasoningContent", message.reasoning_content);
  object.insert_or_assign("timestamp", message.timestamp);
  object.insert_or_assign("streaming", message.streaming);
  object.insert_or_assign("hidden", message.hidden);
  object.insert_or_assign("excludeFromContext", message.exclude_from_context);
  object.insert_or_assign("toolCallId", message.tool_call_id);
  object.insert_or_assign("toolName", message.tool_name);
  object.insert_or_assign("isError", message.is_error);
  return object;
}

// Mirrors `LineCodeArchiveCodec.conversationJson`
// (`LineCodeArchiveCodec.java:210-223`).
json::Value
LegacyConversationJson(const application::LegacyArchiveConversation &conversation) {
  json::Object object;
  if (!conversation.raw_json.empty()) {
    if (auto parsed = json::Parse(conversation.raw_json);
        parsed && json::AsObject(&*parsed) != nullptr)
      object = *json::AsObject(&*parsed);
  }
  object.insert_or_assign("id", conversation.id);
  object.insert_or_assign("title", conversation.title);
  object.insert_or_assign("createdAt", conversation.created_at);
  object.insert_or_assign("updatedAt", conversation.updated_at);
  json::Array messages;
  messages.reserve(conversation.messages.size());
  for (const auto &message : conversation.messages)
    messages.push_back(LegacyMessageJson(message));
  object.insert_or_assign("messages", std::move(messages));
  return object;
}

} // namespace

std::string EncodeLegacyModelJson(const domain::ModelConfig &model) {
  return json::Serialize(LegacyModelJson(model));
}

LegacyArchiveEncoding
EncodeLegacyArchive(const application::LegacyArchiveData &data) {
  // `LineCodeArchiveCodec.buildAsyncStorageEntries`.
  json::Array entries;
  json::Array models;
  for (const auto &model : data.models)
    models.push_back(LegacyModelJson(model.config));
  entries.push_back(json::Object{{"key", std::string{"@lineai_models"}},
                                 {"value", json::Serialize(models)}});
  if (!data.selected_model_id.empty()) {
    entries.push_back(json::Object{
        {"key", std::string{"@lineai_selected_model"}},
        {"value", data.selected_model_id}});
  }
  if (!data.current_conversation_id.empty()) {
    entries.push_back(json::Object{
        {"key", std::string{"@lineai_current_conversation"}},
        {"value", data.current_conversation_id}});
  }

  LegacyArchiveEncoding encoding;
  json::Array conversation_list;
  for (const auto &conversation : data.conversations) {
    const auto file_name = SafeConversationFileName(conversation.id);
    auto content = json::Serialize(LegacyConversationJson(conversation));
    conversation_list.push_back(json::Object{
        {"id", conversation.id},
        {"title", conversation.title},
        {"createdAt", conversation.created_at},
        {"updatedAt", conversation.updated_at}});
    // The metadata entry carries the byte size of the file it points at, so
    // it has to be built after serialising that file.
    entries.push_back(json::Object{
        {"key", std::string{"@lineai_conv_"} + conversation.id},
        {"value", json::Serialize(json::Object{
                      {"storage", std::string{"file"}},
                      {"schemaVersion", kCurrentArchiveDatabaseSchemaVersion},
                      {"id", conversation.id},
                      {"fileName", file_name},
                      {"size", static_cast<std::int64_t>(content.size())},
                      {"updatedAt", conversation.updated_at},
                      {"messageCount",
                       static_cast<std::int64_t>(conversation.messages.size())},
                  })}});
    encoding.conversation_files.emplace_back(
        "conversations/" + file_name, std::move(content));
  }
  entries.push_back(json::Object{
      {"key", std::string{"@lineai_conversation_list"}},
      {"value", json::Serialize(conversation_list)}});
  for (const auto &[key, value] : data.settings) {
    if (IsLegacySettingKey(key))
      entries.push_back(json::Object{{"key", key}, {"value", value}});
  }
  encoding.async_storage_json = json::Serialize(entries);
  return encoding;
}

std::expected<ValidatedArchiveManifest, ArchiveValidationError>
ValidateArchiveManifest(std::string_view text) {
  auto parsed = json::Parse(text);
  if (!parsed) {
    return Invalid("invalid .linecode manifest JSON: " +
                   parsed.error().message);
  }
  const auto *root = json::AsObject(&*parsed);
  if (!root) {
    return Invalid(".linecode manifest is not an object");
  }
  const auto *format = json::AsString(json::Find(*root, "format"));
  const auto *version = Integer(json::Find(*root, "formatVersion"));
  const auto *container = json::AsString(json::Find(*root, "container"));
  const auto *created_at = Integer(json::Find(*root, "createdAt"));
  const auto *database = Boolean(json::Find(*root, "database"));
  const auto *roots = json::AsArray(json::Find(*root, "workspaceRoots"));
  if (!format || *format != "linecode" || !version || *version != 1 ||
      !container || *container != "zip" || !created_at || *created_at < 0 ||
      !database || !roots) {
    return Invalid("invalid or unsupported .linecode manifest");
  }
  std::set<std::string, std::less<>> names;
  for (const auto &value : *roots) {
    const auto *name = json::AsString(&value);
    if (!name || !IsKnownWorkspaceRoot(*name) ||
        !names.emplace(*name).second) {
      return Invalid("invalid workspace root in .linecode manifest");
    }
  }
  return ValidatedArchiveManifest{.contains_database = *database};
}

std::expected<void, ArchiveValidationError>
ValidateDatabaseSnapshot(std::string_view text,
                         std::int64_t maximum_schema_version) {
  auto parsed = json::Parse(text);
  if (!parsed) {
    return Invalid("invalid database snapshot JSON: " +
                   parsed.error().message);
  }
  const auto *root = json::AsObject(&*parsed);
  const auto *format = root ? json::AsString(json::Find(*root, "format"))
                            : nullptr;
  const auto *version = root ? Integer(json::Find(*root, "schemaVersion"))
                             : nullptr;
  const auto *tables = root ? json::AsObject(json::Find(*root, "tables"))
                            : nullptr;
  if (!format || *format != "linecode-database" || !version || *version < 0 ||
      !tables) {
    return Invalid("invalid .linecode database snapshot");
  }
  if (*version > maximum_schema_version) {
    return Invalid("archive was created by a newer LineCode database schema");
  }

  for (const auto &[table_name, value] : *tables) {
    const auto *table = json::AsObject(&value);
    const auto *columns = table ? json::AsArray(json::Find(*table, "columns"))
                                : nullptr;
    const auto *rows = table ? json::AsArray(json::Find(*table, "rows"))
                             : nullptr;
    if (!columns || !rows) {
      return Invalid("invalid database table payload: " + table_name);
    }
    std::set<std::string, std::less<>> declared;
    for (const auto &column_value : *columns) {
      const auto *column = json::AsString(&column_value);
      if (!column || column->empty() || !declared.emplace(*column).second) {
        return Invalid("invalid database columns in table " + table_name);
      }
    }
    for (const auto &row_value : *rows) {
      const auto *row = json::AsObject(&row_value);
      if (!row || row->size() != declared.size()) {
        return Invalid("database rows use inconsistent columns in table " +
                       table_name);
      }
      for (const auto &[column, cell] : *row) {
        if (!declared.contains(column)) {
          return Invalid("database row contains an undeclared column in table " +
                         table_name);
        }
        auto valid = ValidateCell(cell);
        if (!valid) {
          return valid;
        }
      }
    }
  }
  return {};
}

std::expected<application::LegacyArchiveData, ArchiveValidationError>
DecodeLegacyArchive(std::span<const ZipEntryData> entries,
                    std::int64_t fallback_timestamp) {
  const auto find_entry = [&](std::string_view name) -> const ZipEntryData * {
    const auto found = std::ranges::find(entries, name, &ZipEntryData::name);
    return found == entries.end() ? nullptr : &*found;
  };
  const auto *storage_entry = find_entry("async-storage.json");
  if (!storage_entry)
    return Invalid("legacy .linecode archive has no async-storage.json");
  auto parsed_storage = json::Parse(BytesText(storage_entry->content));
  const auto *storage_array =
      parsed_storage ? json::AsArray(&*parsed_storage) : nullptr;
  if (!storage_array)
    return Invalid("invalid legacy async-storage.json");

  std::map<std::string, std::string, std::less<>> storage;
  std::vector<std::string> key_order;
  for (const auto &value : *storage_array) {
    const auto *entry = json::AsObject(&value);
    if (!entry)
      continue;
    const auto *key = ObjectString(*entry, "key");
    const auto *stored = json::Find(*entry, "value");
    if (!key || key->empty() || !stored || std::holds_alternative<json::Null>(*stored))
      continue;
    const auto *text = json::AsString(stored);
    if (!text)
      return Invalid("legacy async-storage value is not text: " + *key);
    if (!storage.contains(*key))
      key_order.push_back(*key);
    storage[*key] = *text;
  }

  application::LegacyArchiveData output;
  if (const auto models = storage.find("@lineai_models");
      models != storage.end() && !models->second.empty()) {
    auto parsed = json::Parse(models->second);
    const auto *array = parsed ? json::AsArray(&*parsed) : nullptr;
    if (!array)
      return Invalid("invalid legacy model list");
    for (const auto &value : *array) {
      const auto *object = json::AsObject(&value);
      if (!object)
        continue;
      auto model = DecodeLegacyModel(*object);
      if (model.id.empty())
        continue;
      auto raw_json = json::Serialize(LegacyModelJson(model));
      output.models.push_back(application::LegacyArchiveModel{
          .config = std::move(model), .raw_json = std::move(raw_json)});
    }
  }
  if (const auto selected = storage.find("@lineai_selected_model");
      selected != storage.end())
    output.selected_model_id = selected->second;
  if (const auto current = storage.find("@lineai_current_conversation");
      current != storage.end())
    output.current_conversation_id = current->second;
  for (const auto &[key, value] : storage) {
    if (IsLegacySettingKey(key))
      output.settings.emplace(key, value);
  }

  std::vector<std::string> conversation_ids;
  std::set<std::string, std::less<>> seen_ids;
  const auto add_id = [&](std::string id) {
    if (!id.empty() && seen_ids.emplace(id).second)
      conversation_ids.push_back(std::move(id));
  };
  if (const auto list = storage.find("@lineai_conversation_list");
      list != storage.end() && !list->second.empty()) {
    auto parsed = json::Parse(list->second);
    const auto *array = parsed ? json::AsArray(&*parsed) : nullptr;
    if (!array)
      return Invalid("invalid legacy conversation list");
    for (const auto &value : *array) {
      const auto *metadata = json::AsObject(&value);
      if (metadata)
        add_id(StringOr(*metadata, "id"));
    }
  }
  constexpr std::string_view conversation_prefix = "@lineai_conv_";
  constexpr std::string_view chunk_prefix = "@lineai_conv_chunk_";
  for (const auto &key : key_order) {
    if (key.starts_with(conversation_prefix) && !key.starts_with(chunk_prefix))
      add_id(key.substr(conversation_prefix.size()));
  }

  for (const auto &reference_id : conversation_ids) {
    const auto stored = storage.find(std::string{conversation_prefix} + reference_id);
    if (stored == storage.end() || stored->second.empty())
      continue;
    auto parsed_stored = json::Parse(stored->second);
    const auto *stored_object =
        parsed_stored ? json::AsObject(&*parsed_stored) : nullptr;
    if (!stored_object)
      return Invalid("invalid legacy conversation pointer: " + reference_id);

    json::Value conversation_value = *parsed_stored;
    if (BooleanOr(*stored_object, "chunked", false)) {
      const auto count = IntegerOr(*stored_object, "chunks", 0);
      if (count < 0 || count > 4096)
        return Invalid("invalid legacy conversation chunk count: " + reference_id);
      std::string combined;
      for (std::int64_t index = 0; index < count; ++index) {
        const auto part = storage.find(std::string{chunk_prefix} + reference_id +
                                       '_' + std::to_string(index));
        if (part == storage.end())
          return Invalid("legacy conversation chunk is missing: " + reference_id);
        if (combined.size() + part->second.size() > 128U * 1024U * 1024U)
          return Invalid("legacy conversation is too large: " + reference_id);
        combined += part->second;
      }
      auto parsed = json::Parse(combined);
      if (!parsed || !json::AsObject(&*parsed))
        return Invalid("invalid chunked legacy conversation: " + reference_id);
      conversation_value = std::move(*parsed);
    } else if (StringOr(*stored_object, "storage") == "file") {
      std::string file_name = SafeSelectedFileName(
          StringOr(*stored_object, "fileName", SafeConversationFileName(reference_id)),
          reference_id);
      const std::string entry_name = "conversations/" + file_name;
      const auto *conversation_file = find_entry(entry_name);
      if (!conversation_file)
        conversation_file = find_entry(entry_name + ".bak");
      if (!conversation_file)
        return Invalid("legacy conversation file is missing: " + reference_id);
      auto parsed = json::Parse(BytesText(conversation_file->content));
      if (!parsed || !json::AsObject(&*parsed))
        return Invalid("invalid legacy conversation file: " + reference_id);
      conversation_value = std::move(*parsed);
    }

    const auto *conversation = json::AsObject(&conversation_value);
    if (!conversation)
      return Invalid("legacy conversation is not an object: " + reference_id);
    application::LegacyArchiveConversation converted;
    converted.id = StringOr(*conversation, "id", reference_id);
    converted.title = StringOr(*conversation, "title", "New conversation");
    if (converted.title.empty())
      converted.title = "新对话";
    const auto created = IntegerOr(*conversation, "createdAt", fallback_timestamp);
    converted.created_at = created > 0 ? created : fallback_timestamp;
    const auto updated = IntegerOr(*conversation, "updatedAt", converted.created_at);
    converted.updated_at = updated > 0 ? updated : fallback_timestamp;
    converted.raw_json = json::Serialize(conversation_value);
    if (const auto *messages = json::AsArray(json::Find(*conversation, "messages"))) {
      converted.messages.reserve(messages->size());
      for (std::size_t index = 0; index < messages->size(); ++index) {
        const auto *message = json::AsObject(&(*messages)[index]);
        if (!message)
          continue;
        application::LegacyArchiveMessage item;
        std::string id = StringOr(*message, "id");
        if (id.empty())
          id = "imported_" + std::to_string(index);
        item.id = ScopedMessageId(converted.id, std::move(id), index);
        item.role = LegacyRole(StringOr(*message, "role"));
        item.content = StringOr(*message, "content");
        item.reasoning_content = StringOr(*message, "reasoningContent");
        if (item.reasoning_content.empty())
          item.reasoning_content = StringOr(*message, "reasoning");
        const auto timestamp = IntegerOr(*message, "timestamp", fallback_timestamp);
        item.timestamp = timestamp > 0 ? timestamp : fallback_timestamp;
        item.streaming = BooleanOr(*message, "streaming", false);
        item.hidden = BooleanOr(*message, "hidden", false);
        item.exclude_from_context =
            BooleanOr(*message, "excludeFromContext", false);
        item.tool_call_id = StringOr(*message, "toolCallId");
        item.tool_name = StringOr(*message, "toolName");
        item.is_error = BooleanOr(*message, "isError", false);
        item.raw_json = json::Serialize((*messages)[index]);
        converted.messages.push_back(std::move(item));
      }
    }
    output.conversations.push_back(std::move(converted));
  }
  return output;
}

} // namespace linecode::infrastructure
