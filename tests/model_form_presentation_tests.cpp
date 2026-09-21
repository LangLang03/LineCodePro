#include "gtest_support.h"
#include <string>

#include "presentation/model_form_presentation.h"

namespace {

using linecode::domain::ModelProtocol;
using linecode::presentation::ModelFormLayoutMetrics;
using linecode::presentation::ModelFormSectionLabelKind;
using linecode::presentation::ModelFormSectionLabelMetricsFor;
using linecode::presentation::ModelSelectionSlots;
using linecode::presentation::ProtocolTabAction;
using linecode::presentation::ResolveProtocolTabDecision;

void LayoutMetricsMatchMeasuredLegacyControls() {
  EXPECT_EXPRESSION(ModelFormLayoutMetrics::header_minimum_height == 61.14F);
  EXPECT_EXPRESSION(ModelFormLayoutMetrics::header_title_offset_y == 1.52F);
  EXPECT_EXPRESSION(ModelFormLayoutMetrics::header_action_offset_y == 1.90F);
  EXPECT_EXPRESSION(ModelFormLayoutMetrics::toggle_height == 46.0F);
  EXPECT_EXPRESSION(ModelFormLayoutMetrics::acceleration_label_bottom_padding == 9.3F);
  EXPECT_EXPRESSION(ModelFormLayoutMetrics::acceleration_text_offset_y == 1.14F);
  EXPECT_EXPRESSION(ModelFormLayoutMetrics::form_border_width == 1.14F);
  EXPECT_EXPRESSION(ModelFormLayoutMetrics::base_url_hint_trailing_padding == 6.0F);
  EXPECT_EXPRESSION(
      ModelFormSectionLabelMetricsFor(ModelFormSectionLabelKind::cjk_or_mixed)
          .line_box_height == 18.67F);
  EXPECT_EXPRESSION(ModelFormSectionLabelMetricsFor(ModelFormSectionLabelKind::latin)
             .line_box_height == 15.24F);
}

void NewRemoteFormSelectsProtocolsAndRedirectsLocalEntry() {
  const auto codex = ResolveProtocolTabDecision(
      ModelProtocol::openai_compatible, ModelProtocol::codex_responses, false);
  EXPECT_EXPRESSION(codex.enabled);
  EXPECT_EXPRESSION(codex.action == ProtocolTabAction::select_protocol);

  const auto local = ResolveProtocolTabDecision(
      ModelProtocol::openai_compatible, ModelProtocol::local_gguf, false);
  EXPECT_EXPRESSION(local.enabled);
  EXPECT_EXPRESSION(local.action == ProtocolTabAction::prompt_local_entry);
}

void NewLocalFormKeepsTabsOpaqueAndRedirectsRemoteEntry() {
  const auto remote = ResolveProtocolTabDecision(
      ModelProtocol::local_gguf, ModelProtocol::openai_compatible, false);
  EXPECT_EXPRESSION(remote.enabled);
  EXPECT_EXPRESSION(remote.action == ProtocolTabAction::prompt_custom_entry);

  const auto local = ResolveProtocolTabDecision(
      ModelProtocol::local_gguf, ModelProtocol::local_gguf, false);
  EXPECT_EXPRESSION(local.enabled);
  EXPECT_EXPRESSION(local.action == ProtocolTabAction::prompt_local_entry);
}

void LockedFormsDisableOtherProtocolsWithoutDimmingTheActiveTab() {
  const auto inactive = ResolveProtocolTabDecision(
      ModelProtocol::anthropic_messages, ModelProtocol::openai_compatible, true);
  EXPECT_EXPRESSION(!inactive.enabled);
  EXPECT_EXPRESSION(inactive.action == ProtocolTabAction::none);

  const auto active = ResolveProtocolTabDecision(
      ModelProtocol::anthropic_messages, ModelProtocol::anthropic_messages,
      true);
  EXPECT_EXPRESSION(active.enabled);
  EXPECT_EXPRESSION(active.action == ProtocolTabAction::none);
}

void CustomTogglePreservesBothLegacySelectionSlots() {
  ModelSelectionSlots<std::string> slots{
      .manual = "manual-model", .catalog = "catalog-model", .custom = true};
  EXPECT_EXPRESSION(slots.Effective() == "manual-model");

  slots.SetCustom(false);
  EXPECT_EXPRESSION(slots.Effective() == "catalog-model");
  EXPECT_EXPRESSION(slots.manual == "manual-model");

  slots.SetCustom(true);
  EXPECT_EXPRESSION(slots.Effective() == "manual-model");
  EXPECT_EXPRESSION(slots.catalog == "catalog-model");
}

void PickerActionsAndCatalogInvalidationMatchLegacyBehavior() {
  ModelSelectionSlots<std::string> slots{
      .manual = "manual-model", .catalog = "old-catalog", .custom = true};
  slots.ClearCatalog();
  EXPECT_EXPRESSION(slots.manual == "manual-model");
  EXPECT_EXPRESSION(slots.catalog.empty());
  EXPECT_EXPRESSION(slots.custom);

  slots.ChooseCatalog("queried-model");
  EXPECT_EXPRESSION(!slots.custom);
  EXPECT_EXPRESSION(slots.Effective() == "queried-model");
  EXPECT_EXPRESSION(slots.manual == "manual-model");

  slots.ChooseCustom();
  EXPECT_EXPRESSION(slots.custom);
  EXPECT_EXPRESSION(slots.manual.empty());
  EXPECT_EXPRESSION(slots.catalog.empty());
}

} // namespace

TEST(model_form_presentation_tests, LegacySuite) {
  LayoutMetricsMatchMeasuredLegacyControls();
  NewRemoteFormSelectsProtocolsAndRedirectsLocalEntry();
  NewLocalFormKeepsTabsOpaqueAndRedirectsRemoteEntry();
  LockedFormsDisableOtherProtocolsWithoutDimmingTheActiveTab();
  CustomTogglePreservesBothLegacySelectionSlots();
  PickerActionsAndCatalogInvalidationMatchLegacyBehavior();
}
