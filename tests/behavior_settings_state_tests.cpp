// Contract tests for the shared AI-behaviour settings snapshot. The cell is the
// only thing the LLM settings screen and the chat screen read, so these cases
// are exactly the "switch says one thing, the app does another" defect class.

#include "gtest_support.h"

#include "presentation/components/behavior_settings_state.h"

namespace {

using linecode::domain::AiBehaviorSettings;
using linecode::domain::ReasoningEffort;
using linecode::domain::ToneMode;
using linecode::presentation::ApplyBehaviorEdit;
using linecode::presentation::ApplyBehaviorLoad;
using linecode::presentation::ApplyBehaviorLoadFailure;
using linecode::presentation::ApplyBehaviorPersistFailure;
using linecode::presentation::BehaviorSettingsState;

AiBehaviorSettings WithMinimal(bool value) {
  AiBehaviorSettings settings;
  settings.minimal_mode = value;
  return settings;
}

// A screen that has rendered defaults is not allowed to claim it loaded them.
void StartsUnloaded() {
  const BehaviorSettingsState state;
  EXPECT_EXPRESSION(!state.loaded);
  EXPECT_EXPRESSION(!state.value.minimal_mode);
  EXPECT_EXPRESSION(state.error.empty());
  EXPECT_EXPRESSION(state.edit_revision == 0U);
}

// A plain load applies and marks the cell ready.
void AppliesALoadThatStartedBeforeAnyEdit() {
  BehaviorSettingsState state;
  const auto started_at = state.edit_revision;
  EXPECT_EXPRESSION(ApplyBehaviorLoad(state, started_at, WithMinimal(true)));
  EXPECT_EXPRESSION(state.loaded);
  EXPECT_EXPRESSION(state.value.minimal_mode);
}

// The reported symptom: the user turns minimal mode off while the mount-time
// load is still in flight. The stale read reports "true" and used to overwrite
// the screen, leaving the switch off while every request stayed minimal.
void DiscardsALoadThatStartedBeforeTheUsersEdit() {
  BehaviorSettingsState state;
  const auto started_at = state.edit_revision;

  auto edited = state.value;
  edited.minimal_mode = false;
  static_cast<void>(ApplyBehaviorEdit(state, edited));

  EXPECT_EXPRESSION(!ApplyBehaviorLoad(state, started_at, WithMinimal(true)));
  EXPECT_EXPRESSION(!state.value.minimal_mode);
  EXPECT_EXPRESSION(!state.loaded);
}

// A load that starts after the edit is still allowed to settle the cell.
void AppliesALoadThatStartedAfterTheUsersEdit() {
  BehaviorSettingsState state;
  auto edited = state.value;
  edited.reasoning = ReasoningEffort::high;
  static_cast<void>(ApplyBehaviorEdit(state, edited));

  const auto started_at = state.edit_revision;
  auto loaded = edited;
  loaded.thinking_scroll = false;
  EXPECT_EXPRESSION(ApplyBehaviorLoad(state, started_at, loaded));
  EXPECT_EXPRESSION(state.value.reasoning == ReasoningEffort::high);
  EXPECT_EXPRESSION(!state.value.thinking_scroll);
}

// A failed persist must not leave the optimistic value on screen: the store
// still holds the previous one, and every request reads the store.
void RevertsAfterAFailedPersist() {
  BehaviorSettingsState state;
  static_cast<void>(
      ApplyBehaviorLoad(state, state.edit_revision, AiBehaviorSettings{}));

  const auto previous = state.value;
  static_cast<void>(ApplyBehaviorEdit(state, WithMinimal(true)));
  const auto optimistic = state.value;

  EXPECT_EXPRESSION(ApplyBehaviorPersistFailure(state, optimistic, previous,
                                                "database is locked"));
  EXPECT_EXPRESSION(!state.value.minimal_mode);
  EXPECT_EXPRESSION(state.error == "database is locked");
}

// A newer edit wins: the late failure only reports, it does not undo it.
void KeepsANewerEditWhenAnOlderPersistFails() {
  BehaviorSettingsState state;
  const auto previous = state.value;
  static_cast<void>(ApplyBehaviorEdit(state, WithMinimal(true)));
  const auto optimistic = state.value;
  static_cast<void>(ApplyBehaviorEdit(state, WithMinimal(false)));
  const auto later = state.value;

  EXPECT_EXPRESSION(!ApplyBehaviorPersistFailure(state, optimistic, previous,
                                                 "write failed"));
  EXPECT_EXPRESSION(state.value == later);
  EXPECT_EXPRESSION(!state.value.minimal_mode);
  EXPECT_EXPRESSION(state.error == "write failed");
}

// A load failure records the reason and must not pretend defaults are real.
void RecordsALoadFailureWithoutClaimingDefaults() {
  BehaviorSettingsState state;
  ApplyBehaviorLoadFailure(state, "no such table: settings");
  EXPECT_EXPRESSION(!state.loaded);
  EXPECT_EXPRESSION(!state.value.minimal_mode);
  EXPECT_EXPRESSION(state.error == "no such table: settings");
}

// Editing clears a previous error and advances the revision monotonically.
void EditingAdvancesTheRevisionAndClearsErrors() {
  BehaviorSettingsState state;
  ApplyBehaviorLoadFailure(state, "boom");
  const auto first = ApplyBehaviorEdit(state, WithMinimal(true));
  auto tone = state.value;
  tone.tone = ToneMode::chat;
  const auto second = ApplyBehaviorEdit(state, tone);
  EXPECT_EXPRESSION(first == 1U);
  EXPECT_EXPRESSION(second == 2U);
  EXPECT_EXPRESSION(state.error.empty());
  EXPECT_EXPRESSION(state.value.tone == ToneMode::chat);
}

} // namespace

TEST(behavior_settings_state_tests, StartsUnloaded) { StartsUnloaded(); }
TEST(behavior_settings_state_tests, AppliesALoadThatStartedBeforeAnyEdit) {
  AppliesALoadThatStartedBeforeAnyEdit();
}
TEST(behavior_settings_state_tests, DiscardsALoadThatStartedBeforeTheUsersEdit) {
  DiscardsALoadThatStartedBeforeTheUsersEdit();
}
TEST(behavior_settings_state_tests, AppliesALoadThatStartedAfterTheUsersEdit) {
  AppliesALoadThatStartedAfterTheUsersEdit();
}
TEST(behavior_settings_state_tests, RevertsAfterAFailedPersist) {
  RevertsAfterAFailedPersist();
}
TEST(behavior_settings_state_tests, KeepsANewerEditWhenAnOlderPersistFails) {
  KeepsANewerEditWhenAnOlderPersistFails();
}
TEST(behavior_settings_state_tests, RecordsALoadFailureWithoutClaimingDefaults) {
  RecordsALoadFailureWithoutClaimingDefaults();
}
TEST(behavior_settings_state_tests, EditingAdvancesTheRevisionAndClearsErrors) {
  EditingAdvancesTheRevisionAndClearsErrors();
}
