#pragma once

#include <cstddef>
#include <memory>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include "application/ports/chat_export_delivery.h"
#include "domain/app_state.h"

namespace linecode::application {

struct ChatExportOption final {
  std::string id;
  std::string display_name;

  bool operator==(const ChatExportOption &) const = default;
};

struct ChatExportResult final {
  bool accepted{};
  bool warn_large_clipboard{};
  std::string error;

  [[nodiscard]] bool Succeeded() const noexcept {
    return accepted && error.empty();
  }
};

class ChatExportFormat {
public:
  virtual ~ChatExportFormat() = default;

  [[nodiscard]] virtual ChatExportOption Option() const = 0;
  [[nodiscard]] virtual ChatExportResult
  Export(std::span<const domain::ChatMessage> messages,
         ChatExportDelivery &delivery) const = 0;
};

class ChatExportRegistry final {
public:
  void Register(std::unique_ptr<ChatExportFormat> format);

  [[nodiscard]] std::vector<ChatExportOption> Options() const;
  [[nodiscard]] ChatExportResult
  Export(std::string_view id, std::span<const domain::ChatMessage> messages,
         ChatExportDelivery &delivery) const;

private:
  std::vector<std::unique_ptr<ChatExportFormat>> formats_;
};

class ChatExportService final {
public:
  ChatExportService(std::shared_ptr<ChatExportDelivery> delivery,
                    ChatExportRegistry registry);

  [[nodiscard]] std::vector<ChatExportOption> Options() const;
  [[nodiscard]] ChatExportResult
  Export(std::string_view id,
         std::span<const domain::ChatMessage> messages) const;

private:
  std::shared_ptr<ChatExportDelivery> delivery_;
  ChatExportRegistry registry_;
};

[[nodiscard]] ChatExportRegistry CreateDefaultChatExportRegistry();

} // namespace linecode::application
