#include "gtest_support.h"
#include <string>

#include "application/pending_message_queue.h"

TEST(pending_message_queue_tests, LegacySuite) {
  using linecode::application::PendingMessageQueue;
  using linecode::application::SendMessageError;

  PendingMessageQueue queue;
  const auto empty = queue.Enqueue(" \n", {{"", "", "local"}});
  EXPECT_EXPRESSION(!empty && empty.error() == SendMessageError::empty);

  EXPECT_EXPRESSION(queue.Enqueue(" first "));
  EXPECT_EXPRESSION(queue.Enqueue("", {{"a.txt", "/a.txt", "local"},
                              {"duplicate", "/a.txt", "local"}}));
  EXPECT_EXPRESSION(queue.Items().size() == 2U);
  EXPECT_EXPRESSION(queue.Items()[0].text == "first");
  EXPECT_EXPRESSION(queue.Items()[1].attachments.size() == 1U);

  linecode::domain::ChatImage image{.name = "photo.png",
                                    .mime_type = "image/png",
                                    .base64 = "iVBORw0KGgo="};
  EXPECT_EXPRESSION(queue.Enqueue("", {}, image));
  EXPECT_EXPRESSION(queue.Items().back().image == image);

  const auto first = queue.TakeNext();
  EXPECT_EXPRESSION(first && first->text == "first");
  EXPECT_EXPRESSION(!queue.Remove(3U));
  EXPECT_EXPRESSION(queue.Remove(0U));
  EXPECT_EXPRESSION(queue.Items().size() == 1U);
  queue.Clear();
  EXPECT_EXPRESSION(queue.Empty());
}
