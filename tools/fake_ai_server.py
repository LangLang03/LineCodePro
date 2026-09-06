#!/usr/bin/env python3
"""Deterministic, dependency-free AI protocol fixture for LineCode tests.

This is intentionally not an AI implementation. It only returns a configured,
fixed string so UI and protocol integration tests remain repeatable.
"""

from __future__ import annotations

import argparse
import json
import signal
import socket
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import urlsplit


DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 18080
DEFAULT_REPLY = "这是 LineCode 自动化测试的固定回复。"
MODEL_ID = "linecode-test-model"
MAX_REQUEST_BODY_BYTES = 1024 * 1024
REQUEST_READ_TIMEOUT_SECONDS = 5.0


class RequestBodyError(Exception):
    """A client-facing request body error with an HTTP status."""

    def __init__(self, status: HTTPStatus, message: str) -> None:
        super().__init__(message)
        self.status = status


def compact_json(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


class FixtureServer(ThreadingHTTPServer):
    """HTTP server carrying immutable fixture configuration for each handler."""

    daemon_threads = True
    allow_reuse_address = True

    def __init__(
        self,
        address: tuple[str, int],
        *,
        reply: str = DEFAULT_REPLY,
        log_requests: bool = True,
        read_timeout: float = REQUEST_READ_TIMEOUT_SECONDS,
    ) -> None:
        super().__init__(address, FakeAiHandler)
        self.reply = reply
        self.log_requests = log_requests
        self.read_timeout = read_timeout


class FakeAiHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "LineCodeFixedReplyFixture/1.0"

    @property
    def fixture_server(self) -> FixtureServer:
        return self.server  # type: ignore[return-value]

    @property
    def reply(self) -> str:
        return self.fixture_server.reply

    def setup(self) -> None:
        super().setup()
        self.connection.settimeout(self.fixture_server.read_timeout)

    def log_message(self, message: str, *args: object) -> None:
        if self.fixture_server.log_requests:
            print(f"{self.address_string()} - {message % args}", flush=True)

    def send_json(self, value: Any, status: HTTPStatus = HTTPStatus.OK) -> None:
        body = compact_json(value)
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        if self.close_connection:
            self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)

    def send_sse(self, events: list[tuple[str | None, Any]]) -> None:
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache, no-transform")
        self.send_header("Connection", "close")
        self.end_headers()
        for event_name, payload in events:
            if event_name:
                self.wfile.write(f"event: {event_name}\n".encode("utf-8"))
            data = payload if isinstance(payload, str) else compact_json(payload).decode("utf-8")
            self.wfile.write(f"data: {data}\n\n".encode("utf-8"))
            self.wfile.flush()
        self.close_connection = True

    def read_request_json(self) -> dict[str, Any]:
        content_lengths = self.headers.get_all("Content-Length", failobj=[])
        if not content_lengths:
            raise RequestBodyError(
                HTTPStatus.LENGTH_REQUIRED,
                "Content-Length is required",
            )
        if len(content_lengths) != 1:
            raise RequestBodyError(
                HTTPStatus.BAD_REQUEST,
                "multiple Content-Length headers are not allowed",
            )

        raw_length = content_lengths[0]
        if not raw_length or not raw_length.isascii() or not raw_length.isdecimal():
            raise RequestBodyError(
                HTTPStatus.BAD_REQUEST,
                "invalid Content-Length",
            )
        try:
            length = int(raw_length)
        except ValueError as error:
            raise RequestBodyError(
                HTTPStatus.BAD_REQUEST,
                "invalid Content-Length",
            ) from error
        if length > MAX_REQUEST_BODY_BYTES:
            raise RequestBodyError(
                HTTPStatus.REQUEST_ENTITY_TOO_LARGE,
                f"request body exceeds {MAX_REQUEST_BODY_BYTES} bytes",
            )
        if length == 0:
            return {}

        deadline = time.monotonic() + self.fixture_server.read_timeout
        chunks: list[bytes] = []
        received = 0
        try:
            while received < length:
                remaining_time = deadline - time.monotonic()
                if remaining_time <= 0:
                    raise TimeoutError
                self.connection.settimeout(remaining_time)
                chunk = self.rfile.read1(min(length - received, 64 * 1024))
                if not chunk:
                    break
                chunks.append(chunk)
                received += len(chunk)
        except (TimeoutError, socket.timeout) as error:
            raise RequestBodyError(
                HTTPStatus.REQUEST_TIMEOUT,
                "timed out while reading request body",
            ) from error
        finally:
            self.connection.settimeout(self.fixture_server.read_timeout)

        body = b"".join(chunks)
        if len(body) != length:
            raise RequestBodyError(
                HTTPStatus.BAD_REQUEST,
                "request body ended before Content-Length bytes were received",
            )

        decoded = json.loads(body.decode("utf-8"))
        if not isinstance(decoded, dict):
            raise ValueError("request body must be a JSON object")
        return decoded

    def route_path(self) -> str:
        path = urlsplit(self.path).path.rstrip("/")
        return path or "/"

    def send_not_found(self) -> None:
        self.send_json(
            {"error": {"message": "route not found", "type": "invalid_request_error"}},
            HTTPStatus.NOT_FOUND,
        )

    def do_GET(self) -> None:  # noqa: N802
        path = self.route_path()
        if path in {"/health", "/healthz"}:
            self.send_json(
                {
                    "status": "ok",
                    "fixture": "fixed-reply",
                    "model": MODEL_ID,
                }
            )
            return
        if path in {"/v1/models", "/models"}:
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
        self.send_not_found()

    def do_POST(self) -> None:  # noqa: N802
        try:
            request = self.read_request_json()
        except RequestBodyError as error:
            # Invalid framing, oversized bodies and timeouts cannot safely share
            # this HTTP/1.1 connection with a subsequent request.
            self.close_connection = True
            self.send_json(
                {"error": {"message": str(error), "type": "invalid_request_error"}},
                error.status,
            )
            return
        except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as error:
            self.send_json(
                {"error": {"message": str(error), "type": "invalid_request_error"}},
                HTTPStatus.BAD_REQUEST,
            )
            return

        path = self.route_path()
        if path in {"/v1/chat/completions", "/chat/completions"}:
            self.handle_chat_completions(request.get("stream") is True)
            return
        if path in {"/v1/responses", "/responses"}:
            self.handle_responses(request.get("stream") is True)
            return
        # Kept for the app's Anthropic protocol option. It is also deterministic.
        if path in {"/v1/messages", "/messages", "/anthropic/v1/messages"}:
            self.handle_anthropic_messages(request.get("stream") is True)
            return
        self.send_not_found()

    def handle_chat_completions(self, stream: bool) -> None:
        response_id = "chatcmpl-linecode-test"
        common = {
            "id": response_id,
            "created": 0,
            "model": MODEL_ID,
        }
        if stream:
            self.send_sse(
                [
                    (
                        None,
                        common
                        | {
                            "object": "chat.completion.chunk",
                            "choices": [
                                {
                                    "index": 0,
                                    "delta": {"role": "assistant", "content": ""},
                                    "finish_reason": None,
                                }
                            ],
                        },
                    ),
                    (
                        None,
                        common
                        | {
                            "object": "chat.completion.chunk",
                            "choices": [
                                {
                                    "index": 0,
                                    "delta": {"content": self.reply},
                                    "finish_reason": None,
                                }
                            ],
                        },
                    ),
                    (
                        None,
                        common
                        | {
                            "object": "chat.completion.chunk",
                            "choices": [
                                {"index": 0, "delta": {}, "finish_reason": "stop"}
                            ],
                        },
                    ),
                    (None, "[DONE]"),
                ]
            )
            return
        self.send_json(
            common
            | {
                "object": "chat.completion",
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

    def response_object(self, status: str = "completed") -> dict[str, Any]:
        return {
            "id": "resp_linecode_test",
            "object": "response",
            "created_at": 0,
            "status": status,
            "model": MODEL_ID,
            "output": [
                {
                    "id": "msg_linecode_test",
                    "type": "message",
                    "status": "completed" if status == "completed" else "in_progress",
                    "role": "assistant",
                    "content": [
                        {
                            "type": "output_text",
                            "text": self.reply,
                            "annotations": [],
                        }
                    ],
                }
            ],
            "usage": {"input_tokens": 1, "output_tokens": 1, "total_tokens": 2},
        }

    def handle_responses(self, stream: bool) -> None:
        output_item = {
            "id": "msg_linecode_test",
            "type": "message",
            "status": "in_progress",
            "role": "assistant",
            "content": [],
        }
        content_part = {"type": "output_text", "text": "", "annotations": []}
        if stream:
            self.send_sse(
                [
                    (
                        "response.created",
                        {
                            "type": "response.created",
                            "sequence_number": 0,
                            "response": self.response_object("in_progress") | {"output": []},
                        },
                    ),
                    (
                        "response.output_item.added",
                        {
                            "type": "response.output_item.added",
                            "sequence_number": 1,
                            "output_index": 0,
                            "item": output_item,
                        },
                    ),
                    (
                        "response.content_part.added",
                        {
                            "type": "response.content_part.added",
                            "sequence_number": 2,
                            "item_id": "msg_linecode_test",
                            "output_index": 0,
                            "content_index": 0,
                            "part": content_part,
                        },
                    ),
                    (
                        "response.output_text.delta",
                        {
                            "type": "response.output_text.delta",
                            "sequence_number": 3,
                            "item_id": "msg_linecode_test",
                            "output_index": 0,
                            "content_index": 0,
                            "delta": self.reply,
                        },
                    ),
                    (
                        "response.output_text.done",
                        {
                            "type": "response.output_text.done",
                            "sequence_number": 4,
                            "item_id": "msg_linecode_test",
                            "output_index": 0,
                            "content_index": 0,
                            "text": self.reply,
                        },
                    ),
                    (
                        "response.content_part.done",
                        {
                            "type": "response.content_part.done",
                            "sequence_number": 5,
                            "item_id": "msg_linecode_test",
                            "output_index": 0,
                            "content_index": 0,
                            "part": content_part | {"text": self.reply},
                        },
                    ),
                    (
                        "response.output_item.done",
                        {
                            "type": "response.output_item.done",
                            "sequence_number": 6,
                            "output_index": 0,
                            "item": output_item
                            | {
                                "status": "completed",
                                "content": [content_part | {"text": self.reply}],
                            },
                        },
                    ),
                    (
                        "response.completed",
                        {
                            "type": "response.completed",
                            "sequence_number": 7,
                            "response": self.response_object(),
                        },
                    ),
                ]
            )
            return
        self.send_json(self.response_object())

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
                    (
                        "message_start",
                        {
                            "type": "message_start",
                            "message": message | {"content": []},
                        },
                    ),
                    (
                        "content_block_start",
                        {
                            "type": "content_block_start",
                            "index": 0,
                            "content_block": {"type": "text", "text": ""},
                        },
                    ),
                    (
                        "content_block_delta",
                        {
                            "type": "content_block_delta",
                            "index": 0,
                            "delta": {"type": "text_delta", "text": self.reply},
                        },
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
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--reply", default=DEFAULT_REPLY)
    parser.add_argument("--quiet", action="store_true", help="disable per-request logs")
    return parser.parse_args()


def exposure_warning(host: str) -> str | None:
    if host in {"0.0.0.0", "::", "[::]"}:
        return (
            "WARNING: wildcard binding exposes this unauthenticated test fixture; "
            "use it only on a trusted local network."
        )
    return None


def main() -> None:
    args = parse_args()
    server = FixtureServer(
        (args.host, args.port), reply=args.reply, log_requests=not args.quiet
    )

    def request_shutdown(signum: int, _frame: object) -> None:
        raise KeyboardInterrupt(f"received signal {signum}")

    for signal_name in ("SIGINT", "SIGTERM"):
        shutdown_signal = getattr(signal, signal_name, None)
        if shutdown_signal is not None:
            signal.signal(shutdown_signal, request_shutdown)

    host, port = server.server_address[:2]
    print(
        f"LineCode fixed-reply test fixture listening on http://{host}:{port}",
        flush=True,
    )
    if warning := exposure_warning(args.host):
        print(warning, flush=True)
    print("This is a deterministic test fixture, not a real AI service.", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Stopping LineCode fixed-reply test fixture.", flush=True)
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
