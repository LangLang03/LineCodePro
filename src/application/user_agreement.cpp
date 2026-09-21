#include "application/user_agreement.h"

#include <stdexcept>
#include <utility>

namespace linecode::application {
namespace {

constexpr auto kAcceptedKey = "@linecode_user_agreement_accepted";
constexpr auto kVersionKey = "@linecode_user_agreement_version";

} // namespace

UserAgreement::UserAgreement(std::shared_ptr<AsyncSettingsStore> settings)
    : settings_(std::move(settings)) {
  if (!settings_)
    throw std::invalid_argument("User agreement settings store is required");
}

huxerui::Task<bool> UserAgreement::ShouldShow() const {
  auto accepted = co_await settings_->GetBoolean(kAcceptedKey, false);
  if (!accepted || !*accepted)
    co_return true;
  auto version = co_await settings_->GetInteger(kVersionKey, 0);
  co_return !version || *version < current_version;
}

huxerui::Task<bool> UserAgreement::Accept() const {
  auto accepted = co_await settings_->SetBoolean(kAcceptedKey, true);
  if (!accepted)
    co_return false;
  auto version =
      co_await settings_->SetInteger(kVersionKey, current_version);
  co_return version.has_value();
}

} // namespace linecode::application
