#include "infrastructure/hux_known_hosts_store.h"

#include <atomic>
#include <stdexcept>
#include <utility>

namespace linecode::infrastructure {
namespace {

std::atomic<std::uint64_t> next_stage{1};

application::SshError Error(std::string message) {
  return {.code = application::SshErrorCode::io,
          .message = std::move(message)};
}

} // namespace

HuxKnownHostsStore::HuxKnownHostsStore(huxerui::File file)
    : file_(std::move(file)) {
  if (file_.Path().empty())
    throw std::invalid_argument("Known-hosts file must not be empty");
}

application::SshResult<std::string> HuxKnownHostsStore::Load() {
  const std::scoped_lock lock{mutex_};
  if (!file_.Exists())
    return std::string{};
  auto read = file_.ReadString();
  if (!read.Succeeded())
    return std::unexpected(Error("Unable to read the SSH known-hosts store"));
  return std::move(read.Value());
}

application::SshResult<void>
HuxKnownHostsStore::Replace(std::string value) {
  const std::scoped_lock lock{mutex_};
  const auto parent = file_.Parent();
  if (!parent || !parent->CreateDirectories())
    return std::unexpected(Error("Unable to create the SSH settings directory"));
  const auto stage = parent->Child(
      ".known-hosts-stage-" +
      std::to_string(next_stage.fetch_add(1, std::memory_order_relaxed)));
  if (!stage.WriteString(value)) {
    static_cast<void>(stage.Delete());
    return std::unexpected(Error("Unable to stage the SSH known-hosts store"));
  }
  if (!stage.MoveTo(file_, true)) {
    static_cast<void>(stage.Delete());
    return std::unexpected(Error("Unable to commit the SSH known-hosts store"));
  }
  return {};
}

} // namespace linecode::infrastructure
