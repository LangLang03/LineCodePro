#include <cassert>
#include <string>

#include "application/pending_message_queue.h"

int main() {
  using linecode::application::PendingMessageQueue;
  using linecode::application::SendMessageError;

  PendingMessageQueue queue;
  const auto empty = queue.Enqueue(" \n", {{"", "", "local"}});
  assert(!empty && empty.error() == SendMessageError::empty);

  assert(queue.Enqueue(" first "));
  assert(queue.Enqueue("", {{"a.txt", "/a.txt", "local"},
                              {"duplicate", "/a.txt", "local"}}));
  assert(queue.Items().size() == 2U);
  assert(queue.Items()[0].text == "first");
  assert(queue.Items()[1].attachments.size() == 1U);

  const auto first = queue.TakeNext();
  assert(first && first->text == "first");
  assert(!queue.Remove(2U));
  assert(queue.Remove(0U));
  assert(queue.Empty());
  queue.Clear();
}
