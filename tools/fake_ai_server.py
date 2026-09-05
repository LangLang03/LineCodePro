#!/usr/bin/env python3
"""Deterministic OpenAI/Responses/Anthropic-compatible test server for LineCode."""

from __future__ import annotations

import argparse
import json
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any


DEFAULT_REPLY = "这是 LineCode 自动化测试的固定回复。"
MODEL_ID = "linecode-test-model"


def compact_json(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


class FakeAiHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "LineCodeFakeAI/1.0"

    @property
    def reply(self) -> str:
        return self.server.reply  # type: ignore[attr-defined]

    def log_message(self, message: str, *args: object) -> None:
        print(f"{self.address_string()} - {message % args}", flush=True)

    def send_json(self, value: Any, status: HTTPStatus = HTTPStatus.OK) -> None:
        body = compact_json(value)
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def send_sse(self, events: list[tuple[str | None, Any]]) -> None:
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        for event_name, payload in events:
            if event_name:
                self.wfile.write(f"event: {event_name}\n".encode())
            if isinstance(payload, str):
                data = payload
            else:
                data = compact_json(payload).decode("utf-8")
            self.wfile.write(f"data: {data}\n\n".encode("utf-8"))
            self.wfile.flush()

    def read_request_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0:
            return {}
        decoded = json.loads(self.rfile.read(length).decode("utf-8"))
        if not isinstance(decoded, dict):
            raise ValueError("request body must be a JSON object")
        return decoded

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/health":
            self.send_json({"status": "ok", "model": MODEL_ID})
            return
        if self.path in {"/v1/models", "/models"}:
            self.send_json(
                {
                    "object": "list",
                    "data": [
                        {
                            "id": MODEL_ID,
                            "object": "model",
                            "created": 0,
                            "owned_by": "linecode-tests",
                        }
                    ],
                }
            )
            return
        self.send_json({"error": {"message": "route not found"}}, HTTPStatus.NOT_FOUND)

    def do_POST(self) -> None:  # noqa: N802
        try:
            request = self.read_request_json()
        except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as error:
            self.send_json({"error": {"message": str(error)}}, HTTPStatus.BAD_REQUEST)
            return

        if self.path in {"/v1/chat/completions", "/chat/completions"}:
            self.handle_chat_completions(bool(request.get("stream")))
            return
        if self.path in {"/v1/responses", "/responses"}:
            self.handle_responses(bool(request.get("stream")))
            return
        if self.path in {"/v1/messages", "/messages"}:
            self.handle_anthropic_messages(bool(request.get("stream")))
            return
        self.send_json({"error": {"message": "route not found"}}, HTTPStatus.NOT_FOUND)

    def handle_chat_completions(self, stream: bool) -> None:
        response_id = "chatcmpl-linecode-test"
        if stream:
            chunk = {
                "id": response_id,
                "object": "chat.completion.chunk",
                "created": 0,
                "model": MODEL_ID,
                "choices": [{"index": 0, "delta": {"content": self.reply}, "finish_reason": None}],
            }
            completed = {
                "id": response_id,
                "object": "chat.completion.chunk",
                "created": 0,
                "model": MODEL_ID,
                "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}],
            }
            self.send_sse([(None, chunk), (None, completed), (None, "[DONE]")])
            return
        self.send_json(
            {
                "id": response_id,
                "object": "chat.completion",
                "created": 0,
                "model": MODEL_ID,
                "choices": [
                    {
                        "index": 0,
                        "message": {"role": "assistant", "content": self.reply},
                        "finish_reason": "stop",
                    }
                ],
                "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2},
            }
        )

    def handle_responses(self, stream: bool) -> None:
        response_id = "resp_linecode_test"
        output = {
            "id": response_id,
            "object": "response",
            "created_at": 0,
            "status": "completed",
            "model": MODEL_ID,
            "output": [
                {
                    "id": "msg_linecode_test",
                    "type": "message",
                    "status": "completed",
                    "role": "assistant",
                    "content": [{"type": "output_text", "text": self.reply, "annotations": []}],
                }
            ],
            "usage": {"input_tokens": 1, "output_tokens": 1, "total_tokens": 2},
        }
        if stream:
            self.send_sse(
                [
                    ("response.created", {"type": "response.created", "response": output | {"status": "in_progress"}}),
                    (
                        "response.output_text.delta",
                        {
                            "type": "response.output_text.delta",
                            "item_id": "msg_linecode_test",
                            "output_index": 0,
                            "content_index": 0,
                            "delta": self.reply,
                        },
                    ),
                    ("response.completed", {"type": "response.completed", "response": output}),
                ]
            )
            return
        self.send_json(output)

    def handle_anthropic_messages(self, stream: bool) -> None:
        message = {
            "id": "msg_linecode_test",
            "type": "message",
            "role": "assistant",
            "model": MODEL_ID,
            "content": [{"type": "text", "text": self.reply}],
            "stop_reason": "end_turn",
            "stop_sequence": None,
            "usage": {"input_tokens": 1, "output_tokens": 1},
        }
        if stream:
            self.send_sse(
                [
                    ("message_start", {"type": "message_start", "message": message | {"content": []}}),
                    (
                        "content_block_start",
                        {"type": "content_block_start", "index": 0, "content_block": {"type": "text", "text": ""}},
                    ),
                    (
                        "content_block_delta",
                        {"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta", "text": self.reply}},
                    ),
                    ("content_block_stop", {"type": "content_block_stop", "index": 0}),
                    (
                        "message_delta",
                        {
                            "type": "message_delta",
                            "delta": {"stop_reason": "end_turn", "stop_sequence": None},
                            "usage": {"output_tokens": 1},
                        },
                    ),
                    ("message_stop", {"type": "message_stop"}),
                ]
            )
            return
        self.send_json(message)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18080)
    parser.add_argument("--reply", default=DEFAULT_REPLY)
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    server = ThreadingHTTPServer((args.host, args.port), FakeAiHandler)
    server.reply = args.reply  # type: ignore[attr-defined]
    print(f"LineCode fake AI listening on http://{args.host}:{args.port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
