#include "gtest_support.h"
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <memory>
#include <string>
#include <utility>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "presentation/components/chat_tail_scroll.h"

namespace {

using namespace huxerui;
using namespace huxerui::testing;
using linecode::presentation::ChatTailScroll;

// Mirrors the conversation surface. History rows and streamed markdown rows are
// taller than the list's extent estimate, and every streaming revision appends
// another markdown row while the newest one keeps growing, exactly as the chat
// screen does while tokens arrive.
//
// The transcript lives in composition state, the way the application owns it,
// and the tappable controls below the list drive the same transitions the send
// and stream paths do. Mutating the scenario from the test body alone would
// not recompose the tree, so the assertions would observe a list that never
// changed.
class TailScenario final {
public:
  ScrollController controller;
  std::size_t revision{};
  std::size_t history_rows{24U};
  std::size_t live_chunks{};
  float message_height{320.0F};
  float chunk_height{260.0F};
  float live_growth{};
  float estimated_extent{180.0F};
  std::uint64_t smooth_request{};
  std::uint64_t turn_id{};
  bool finished{};
  bool failed{};
  bool captured{};

  // Item 0 is the leading spacer and the last item is the trailing spacer, as
  // `Conversation()` builds them.
  [[nodiscard]] std::size_t answer_rows() const {
    if (failed)
      return live_chunks == 0U ? 0U : 1U;
    if (!finished)
      return live_chunks;
    return live_chunks == 0U ? 0U : 1U;
  }
  [[nodiscard]] std::size_t item_count() const {
    return 2U + history_rows + answer_rows();
  }
  [[nodiscard]] std::size_t newest_row_index() const {
    return item_count() - 2U;
  }
};

std::shared_ptr<TailScenario> active;

View TapTarget(std::string key, std::function<void()> action) {
  return Stack{Text(key)}
      .OnClick(std::move(action))
      .With(Frame{.width = 56.0F, .height = 40.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Focusable(), PointerCursor(PointerCursorKind::Hand))
      .Key(std::move(key));
}

View TailProbe() {
  const auto scenario = active;
  const auto controller = UseScrollController();
  if (!scenario->captured) {
    scenario->captured = true;
    scenario->controller = controller;
  }
  auto history = UseState(scenario->history_rows);
  auto live = UseState(scenario->live_chunks);
  auto growth = UseState(scenario->live_growth);
  auto revision = UseState(scenario->revision);
  auto smooth_request = UseState(scenario->smooth_request);
  auto finished = UseState(scenario->finished);
  auto failed = UseState(scenario->failed);
  auto turn_id = UseState(scenario->turn_id);

  const auto history_rows = history.Get();
  const auto live_chunks = live.Get();
  const auto live_growth = growth.Get();
  const auto message_height = scenario->message_height;
  const auto chunk_height = scenario->chunk_height;
  const auto estimate = scenario->estimated_extent;
  const bool answer_failed = failed.Get();
  const bool answer_is_persisted = finished.Get();
  const auto answer_rows = (answer_failed || answer_is_persisted)
                               ? (live_chunks == 0U ? 0U : 1U)
                               : live_chunks;
  const auto count = 2U + history_rows + answer_rows;

  View controls = Row{
      TapTarget("append-row",
                [scenario, history, revision] {
                  history += 1U;
                  revision += 1U;
                  scenario->history_rows += 1U;
                  scenario->revision += 1U;
                }),
      TapTarget("stream-chunk",
                [scenario, live, growth, revision] {
                  live += 1U;
                  growth += 120.0F;
                  revision += 1U;
                  scenario->live_chunks += 1U;
                  scenario->live_growth += 120.0F;
                  scenario->revision += 1U;
                }),
      TapTarget("scroll-to-bottom", [scenario, smooth_request] {
        smooth_request += 1U;
        scenario->smooth_request += 1U;
      }),
      TapTarget("send-message",
                [scenario, history, revision, turn_id] {
                  history += 1U;
                  revision += 1U;
                  turn_id += 1U;
                  scenario->history_rows += 1U;
                  scenario->revision += 1U;
                  scenario->turn_id += 1U;
                }),
      TapTarget("fail-answer", [scenario, failed, revision] {
        failed = true;
        revision += 1U;
        scenario->failed = true;
        scenario->revision += 1U;
      }),
      TapTarget("finish-answer", [scenario, finished, revision] {
        finished = true;
        revision += 1U;
        scenario->finished = true;
        scenario->revision += 1U;
      }),
  }.With(Spacing(8.0F));

  View list = VirtualList(
                  count,
                  [history_rows, live_chunks, answer_rows,
                   answer_is_persisted, answer_failed, message_height,
                   chunk_height, live_growth,
                   count](std::size_t index) {
                    if (index == 0U)
                      return Stack{}.With(Frame{.height = 8.0F}).Key("top");
                    if (index > history_rows + answer_rows)
                      return Stack{}.With(Frame{.height = 8.0F}).Key("tail");
                    if (index > history_rows) {
                      if (answer_failed)
                        return Stack{Text("error")}
                            .With(Frame{.height = 60.0F})
                            .Key("error");
                      if (answer_is_persisted) {
                        // The completed answer is persisted under a new key, so
                        // the list starts from its extent estimate again.
                        return Stack{Text("answer")}
                            .With(Frame{.height = chunk_height + live_growth})
                            .Key("answer");
                      }
                      const bool newest = index == count - 2U;
                      return Stack{Text("chunk")}
                          .With(Frame{.height = chunk_height +
                                                (newest ? live_growth : 0.0F)})
                          .Key("chunk:" + std::to_string(index));
                    }
                    return Stack{Text(std::to_string(index))}
                        .With(Frame{.height = message_height})
                        .Key(index);
                  })
                  .EstimatedItemExtent(estimate)
                  .CacheExtent(480.0F)
                  .Controller(controller)
                  .With(Grow(), ChatTailScroll{
                                    .controller = controller,
                                    .conversation_id = "tail-conversation",
                                    .content_revision = revision.Get(),
                                    .smooth_request = smooth_request.Get(),
                                    .turn_id = turn_id.Get()});

  return Column{std::move(list), std::move(controls)}
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

bool AtTail(const ScrollMetrics &metrics) {
  return metrics.maximum_offset <= 0.0F ||
         metrics.offset >= metrics.maximum_offset - 1.0F;
}

std::string DescribeTail(const ScrollMetrics &metrics) {
  return "offset=" + std::to_string(metrics.offset) +
         " maximum_offset=" + std::to_string(metrics.maximum_offset) +
         " content_extent=" + std::to_string(metrics.content_extent);
}

// Ground truth for "the view is at the very bottom": the trailing item is
// mounted and on screen, so nothing is left below the fold.
bool TrailingItemVisible(UiTest &ui) {
  const auto trailing = ui.Find(UiSelector::Key("tail"));
  return trailing.Exists() && trailing.One().in_viewport;
}

// Selector for the newest content row. It differs per transition: a history row
// keeps its numeric index, a streamed markdown row is keyed by its chunk, and
// the completed answer is persisted under its own key.
UiSelector NewestRowSelector(const TailScenario &scenario) {
  const auto index = scenario.newest_row_index();
  if (index <= scenario.history_rows)
    return UiSelector::Key(index);
  if (scenario.failed)
    return UiSelector::Key("error");
  if (scenario.finished)
    return UiSelector::Key("answer");
  return UiSelector::Key("chunk:" + std::to_string(index));
}

bool RowVisible(UiTest &ui, const UiSelector &selector) {
  const auto row = ui.Find(selector);
  return row.Exists() && row.One().in_viewport;
}

} // namespace

// The conversation surface is pinned to its tail: every committed revision must
// leave the newest output visible at the bottom, not somewhere above it.
TEST(chat_tail_scroll_tests, FollowsTheNewestOutputToTheBottom) {
  active = std::make_shared<TailScenario>();
  const Application application(TailProbe, {.show_debug_overlay = false});
  UiTestOptions options;
  options.viewport = {800.0F, 600.0F};
  UiTest ui(application, options);
  ui.PumpAndSettle();

  auto metrics = active->controller.Metrics();
  EXPECT_TRUE(AtTail(metrics)) << DescribeTail(metrics);
  EXPECT_TRUE(TrailingItemVisible(ui)) << DescribeTail(metrics);

  for (int step = 0; step < 6; ++step) {
    // A streaming revision appends a markdown row and grows the newest one.
    ui.Find(UiSelector::Key("stream-chunk")).Tap();
    ui.PumpAndSettle();

    metrics = active->controller.Metrics();
    EXPECT_TRUE(AtTail(metrics))
        << "step " << step << " " << DescribeTail(metrics);
    EXPECT_TRUE(RowVisible(ui, NewestRowSelector(*active)))
        << "step " << step << " newest row off screen "
        << DescribeTail(metrics);
    EXPECT_TRUE(TrailingItemVisible(ui))
        << "step " << step << " " << DescribeTail(metrics);
  }
  active.reset();
}

// A newly persisted message must not change the observed tail behaviour either:
// the fresh row is the newest output and belongs at the bottom.
TEST(chat_tail_scroll_tests, FollowsANewlyAppendedRow) {
  active = std::make_shared<TailScenario>();
  const Application application(TailProbe, {.show_debug_overlay = false});
  UiTestOptions options;
  options.viewport = {800.0F, 600.0F};
  UiTest ui(application, options);
  ui.PumpAndSettle();

  ui.Find(UiSelector::Key("append-row")).Tap();
  ui.PumpAndSettle();

  const auto metrics = active->controller.Metrics();
  EXPECT_TRUE(AtTail(metrics)) << DescribeTail(metrics);
  EXPECT_TRUE(RowVisible(ui, NewestRowSelector(*active)))
      << DescribeTail(metrics);
  EXPECT_TRUE(TrailingItemVisible(ui)) << DescribeTail(metrics);
  active.reset();
}

// A manual scroll away from the tail must stop the automatic follow, and the
// explicit scroll-to-bottom request must restore it.
TEST(chat_tail_scroll_tests, ManualScrollPausesFollowing) {
  active = std::make_shared<TailScenario>();
  const Application application(TailProbe, {.show_debug_overlay = false});
  UiTestOptions options;
  options.viewport = {800.0F, 600.0F};
  UiTest ui(application, options);
  ui.PumpAndSettle();

  ui.SendScroll({.position = {400.0F, 300.0F}, .delta_y = -600.0F});
  ui.PumpAndSettle();
  const auto scrolled_up = active->controller.Metrics();
  EXPECT_TRUE(scrolled_up.offset < scrolled_up.maximum_offset - 1.0F)
      << DescribeTail(scrolled_up);

  // Streaming must not yank the reader back down on its own.
  ui.Find(UiSelector::Key("stream-chunk")).Tap();
  ui.PumpAndSettle();
  const auto while_reading = active->controller.Metrics();
  EXPECT_TRUE(while_reading.offset < while_reading.maximum_offset - 1.0F)
      << DescribeTail(while_reading);

  // The explicit scroll-to-bottom request re-arms the follow and lands at the
  // newest output.
  ui.Find(UiSelector::Key("scroll-to-bottom")).Tap();
  ui.PumpAndSettle();
  const auto restored = active->controller.Metrics();
  EXPECT_TRUE(AtTail(restored)) << DescribeTail(restored);
  EXPECT_TRUE(RowVisible(ui, NewestRowSelector(*active)))
      << DescribeTail(restored);
  EXPECT_TRUE(TrailingItemVisible(ui)) << DescribeTail(restored);
  active.reset();
}

// When a response finishes, the streamed rows are replaced by the persisted
// message under a new key, so the list re-enters with an extent estimate for
// the answer it just displayed. The follow has to keep the newest output at the
// bottom across that transition instead of leaving the reader mid-answer.
TEST(chat_tail_scroll_tests, FollowsTheCompletedAnswer) {
  active = std::make_shared<TailScenario>();
  const Application application(TailProbe, {.show_debug_overlay = false});
  UiTestOptions options;
  options.viewport = {800.0F, 600.0F};
  UiTest ui(application, options);
  ui.PumpAndSettle();

  for (int step = 0; step < 4; ++step) {
    ui.Find(UiSelector::Key("stream-chunk")).Tap();
    ui.PumpAndSettle();
  }

  ui.Find(UiSelector::Key("finish-answer")).Tap();
  ui.PumpAndSettle();

  const auto metrics = active->controller.Metrics();
  EXPECT_TRUE(AtTail(metrics)) << DescribeTail(metrics);
  EXPECT_TRUE(RowVisible(ui, NewestRowSelector(*active)))
      << "completed answer off screen " << DescribeTail(metrics);
  EXPECT_TRUE(TrailingItemVisible(ui)) << DescribeTail(metrics);
  active.reset();
}

// A reader who scrolled up keeps their place while the turn they are reading
// streams, but sending a message starts a new turn: its answer is the newest
// output and the follow has to resume on its own.
TEST(chat_tail_scroll_tests, NewTurnResumesFollowing) {
  active = std::make_shared<TailScenario>();
  const Application application(TailProbe, {.show_debug_overlay = false});
  UiTestOptions options;
  options.viewport = {800.0F, 600.0F};
  UiTest ui(application, options);
  ui.PumpAndSettle();

  ui.SendScroll({.position = {400.0F, 300.0F}, .delta_y = -600.0F});
  ui.PumpAndSettle();
  const auto scrolled_up = active->controller.Metrics();
  EXPECT_TRUE(scrolled_up.offset < scrolled_up.maximum_offset - 1.0F)
      << DescribeTail(scrolled_up);

  ui.Find(UiSelector::Key("send-message")).Tap();
  ui.PumpAndSettle();

  const auto sent = active->controller.Metrics();
  EXPECT_TRUE(AtTail(sent)) << DescribeTail(sent);
  EXPECT_TRUE(RowVisible(ui, NewestRowSelector(*active))) << DescribeTail(sent);
  EXPECT_TRUE(TrailingItemVisible(ui)) << DescribeTail(sent);

  // The answer that follows in the same turn stays in view too.
  ui.Find(UiSelector::Key("stream-chunk")).Tap();
  ui.PumpAndSettle();
  const auto streamed = active->controller.Metrics();
  EXPECT_TRUE(AtTail(streamed)) << DescribeTail(streamed);
  EXPECT_TRUE(RowVisible(ui, NewestRowSelector(*active)))
      << DescribeTail(streamed);
  active.reset();
}

// A failed request is reported as a message carrying the error text, and that
// message is the newest output: it has to be on screen instead of leaving the
// reader in blank space below the transcript.
TEST(chat_tail_scroll_tests, KeepsTheErrorRowInView) {
  active = std::make_shared<TailScenario>();
  const Application application(TailProbe, {.show_debug_overlay = false});
  UiTestOptions options;
  options.viewport = {800.0F, 600.0F};
  UiTest ui(application, options);
  ui.PumpAndSettle();

  for (int step = 0; step < 3; ++step) {
    ui.Find(UiSelector::Key("stream-chunk")).Tap();
    ui.PumpAndSettle();
  }

  // The failure clears the streamed rows and appends the error message.
  ui.Find(UiSelector::Key("fail-answer")).Tap();
  ui.PumpAndSettle();

  const auto metrics = active->controller.Metrics();
  EXPECT_TRUE(AtTail(metrics)) << DescribeTail(metrics);
  EXPECT_TRUE(RowVisible(ui, NewestRowSelector(*active)))
      << "error row off screen " << DescribeTail(metrics);
  EXPECT_TRUE(TrailingItemVisible(ui)) << DescribeTail(metrics);
  active.reset();
}
