#include <cassert>
#include <string>

#include "presentation/model_form_presentation.h"

namespace {

using linecode::domain::ModelProtocol;
using linecode::presentation::ModelFormLayoutMetrics;
using linecode::presentation::ModelSelectionSlots;
using linecode::presentation::ProtocolTabAction;
using linecode::presentation::ResolveProtocolTabDecision;

void LayoutMetricsMatchMeasuredLegacyControls() {
  assert(ModelFormLayoutMetrics::header_action_minimum_width == 56.0F);
  assert(ModelFormLayoutMetrics::header_action_minimum_height == 39.0F);
  assert(ModelFormLayoutMetrics::header_action_baseline_padding == 3.0F);
  assert(ModelFormLayoutMetrics::toggle_height == 46.0F);
  assert(ModelFormLayoutMetrics::toggle_baseline_padding == 2.0F);
  assert(ModelFormLayoutMetrics::latin_toggle_height == 44.5F);
  assert(ModelFormLayoutMetrics::latin_toggle_baseline_padding == 0.75F);
  assert(ModelFormLayoutMetrics::acceleration_label_bottom_padding == 7.0F);
}

void NewRemoteFormSelectsProtocolsAndRedirectsLocalEntry() {
  const auto codex = ResolveProtocolTabDecision(
      ModelProtocol::openai_compatible, ModelProtocol::codex_responses, false);
  assert(codex.enabled);
  assert(codex.action == ProtocolTabAction::select_protocol);

  const auto local = ResolveProtocolTabDecision(
      ModelProtocol::openai_compatible, ModelProtocol::local_gguf, false);
  assert(local.enabled);
  assert(local.action == ProtocolTabAction::prompt_local_entry);
}

void NewLocalFormKeepsTabsOpaqueAndRedirectsRemoteEntry() {
  const auto remote = ResolveProtocolTabDecision(
      ModelProtocol::local_gguf, ModelProtocol::openai_compatible, false);
  assert(remote.enabled);
  assert(remote.action == ProtocolTabAction::prompt_custom_entry);

  const auto local = ResolveProtocolTabDecision(
      ModelProtocol::local_gguf, ModelProtocol::local_gguf, false);
  assert(local.enabled);
  assert(local.action == ProtocolTabAction::prompt_local_entry);
}

void LockedFormsDisableOtherProtocolsWithoutDimmingTheActiveTab() {
  const auto inactive = ResolveProtocolTabDecision(
      ModelProtocol::anthropic_messages, ModelProtocol::openai_compatible, true);
  assert(!inactive.enabled);
  assert(inactive.action == ProtocolTabAction::none);

  const auto active = ResolveProtocolTabDecision(
      ModelProtocol::anthropic_messages, ModelProtocol::anthropic_messages,
      true);
  assert(active.enabled);
  assert(active.action == ProtocolTabAction::none);
}

void CustomTogglePreservesBothLegacySelectionSlots() {
  ModelSelectionSlots<std::string> slots{
      .manual = "manual-model", .catalog = "catalog-model", .custom = true};
  assert(slots.Effective() == "manual-model");

  slots.SetCustom(false);
  assert(slots.Effective() == "catalog-model");
  assert(slots.manual == "manual-model");

  slots.SetCustom(true);
  assert(slots.Effective() == "manual-model");
  assert(slots.catalog == "catalog-model");
}

void PickerActionsAndCatalogInvalidationMatchLegacyBehavior() {
  ModelSelectionSlots<std::string> slots{
      .manual = "manual-model", .catalog = "old-catalog", .custom = true};
  slots.ClearCatalog();
  assert(slots.manual == "manual-model");
  assert(slots.catalog.empty());
  assert(slots.custom);

  slots.ChooseCatalog("queried-model");
  assert(!slots.custom);
  assert(slots.Effective() == "queried-model");
  assert(slots.manual == "manual-model");

  slots.ChooseCustom();
  assert(slots.custom);
  assert(slots.manual.empty());
  assert(slots.catalog.empty());
}

} // namespace

int main() {
  LayoutMetricsMatchMeasuredLegacyControls();
  NewRemoteFormSelectsProtocolsAndRedirectsLocalEntry();
  NewLocalFormKeepsTabsOpaqueAndRedirectsRemoteEntry();
  LockedFormsDisableOtherProtocolsWithoutDimmingTheActiveTab();
  CustomTogglePreservesBothLegacySelectionSlots();
  PickerActionsAndCatalogInvalidationMatchLegacyBehavior();
}
