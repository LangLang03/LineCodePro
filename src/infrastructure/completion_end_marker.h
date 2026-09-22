#pragma once

#include <string>
#include <string_view>

namespace linecode::infrastructure {

// Models (and the proxies in front of them) sometimes leak their end-of-turn
// control token into the visible text: `<end>`, `<end_of_turn>`,
// `<｜end▁of▁sentence｜>`, `<|im_end|>`, `<end_of_text>`, ... The legacy app never
// showed them, and a leaked marker also made a finished turn look like it was
// still writing: the marker was appended to the answer and everything the model
// said afterwards went with it.
//
// A name is recognized only when it is the bare word `end`, or `end` followed by
// a separator (`end_of_turn`, `end▁of▁sentence`, `end of sentence`) or the
// `endof…` spelling. That deliberately keeps ordinary markup such as
// `<endpoint>` intact.
[[nodiscard]] bool IsCompletionEndMarkerName(std::string_view name) noexcept;

// One-shot variant used for non-streaming responses.
[[nodiscard]] std::string StripCompletionEndMarkers(std::string_view text);

// Streaming variant: tolerates a marker that is split across deltas by holding
// back a tail that could still become one.
class CompletionEndMarkerFilter final {
public:
  // Returns the part of `delta` that is safe to show. Empty once a marker has
  // been seen (everything after it is dropped).
  [[nodiscard]] std::string Push(std::string_view delta);
  // Returns the text held back because it could still have become a marker.
  [[nodiscard]] std::string Flush();
  [[nodiscard]] bool Terminated() const noexcept { return terminated_; }

private:
  std::string held_;
  bool terminated_{false};
};

} // namespace linecode::infrastructure
