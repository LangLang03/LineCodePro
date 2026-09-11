#pragma once

#include <cstdint>
#include <map>
#include <string>
#include <vector>

#include "domain/model_config.h"

namespace linecode::application {

struct LegacyArchiveModel final {
  domain::ModelConfig config;
  std::string raw_json;
};

struct LegacyArchiveMessage final {
  std::string id;
  std::string role;
  std::string content;
  std::string reasoning_content;
  std::int64_t timestamp{};
  bool streaming{};
  bool hidden{};
  bool exclude_from_context{};
  std::string tool_call_id;
  std::string tool_name;
  bool is_error{};
  std::string raw_json;
};

struct LegacyArchiveConversation final {
  std::string id;
  std::string title;
  std::int64_t created_at{};
  std::int64_t updated_at{};
  std::string raw_json;
  std::vector<LegacyArchiveMessage> messages;
};

struct LegacyArchiveData final {
  std::vector<LegacyArchiveModel> models;
  std::string selected_model_id;
  std::vector<LegacyArchiveConversation> conversations;
  std::string current_conversation_id;
  std::map<std::string, std::string, std::less<>> settings;
};

} // namespace linecode::application
