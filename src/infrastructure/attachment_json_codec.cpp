#include "infrastructure/attachment_json_codec.h"

#include <algorithm>
#include <utility>

#include "infrastructure/archive_json.h"

namespace linecode::infrastructure {
namespace {

namespace json = archive_json;

const std::string *OptionalString(const json::Object &object,
                                  const std::string_view key) noexcept {
  return json::AsString(json::Find(object, key));
}

} // namespace

std::string EncodeAttachmentJson(
    const std::span<const domain::InputAttachment> attachments) {
  if (attachments.empty()) {
    return {};
  }
  json::Array values;
  values.reserve(attachments.size());
  for (const auto &attachment : attachments) {
    values.emplace_back(json::Object{
        {"name", attachment.Name()},
        {"path", attachment.Path()},
        {"source", attachment.Source()},
    });
  }
  return json::Serialize(
      json::Object{{"attachments", std::move(values)}});
}

std::vector<domain::InputAttachment>
DecodeAttachmentJson(const std::string_view attachments_json) {
  std::vector<domain::InputAttachment> attachments;
  if (attachments_json.empty() ||
      attachments_json.size() > max_attachment_json_bytes) {
    return attachments;
  }
  const auto parsed = json::Parse(attachments_json);
  if (!parsed) {
    return attachments;
  }
  const auto *object = json::AsObject(&*parsed);
  const auto *values = object == nullptr
                           ? nullptr
                           : json::AsArray(json::Find(*object, "attachments"));
  if (values == nullptr) {
    return attachments;
  }
  attachments.reserve(std::min(values->size(), max_attachments_per_message));
  for (const auto &value : *values) {
    if (attachments.size() >= max_attachments_per_message) {
      break;
    }
    const auto *item = json::AsObject(&value);
    if (item == nullptr) {
      continue;
    }
    const auto *path = OptionalString(*item, "path");
    if (path == nullptr || path->empty() ||
        path->size() > max_attachment_path_bytes) {
      continue;
    }
    const auto *name = OptionalString(*item, "name");
    if (name != nullptr && name->size() > max_attachment_name_bytes) {
      continue;
    }
    const auto *source = OptionalString(*item, "source");
    attachments.emplace_back(name == nullptr ? std::string{} : *name, *path,
                             source == nullptr ? std::string{} : *source);
  }
  return attachments;
}

} // namespace linecode::infrastructure
