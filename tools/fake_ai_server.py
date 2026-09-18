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
import threading
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from pathlib import Path
from urllib.parse import urlsplit


DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 18080
DEFAULT_REPLY = "这是 LineCode 自动化测试的固定回复。"
MODEL_ID = "linecode-test-model"
# Valid deterministic 1x1 PNG used by image tool integration tests.
FIXTURE_IMAGE_BASE64 = (
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8"
    "/x8AAusB9Y9ZHz8AAAAASUVORK5CYII="
)
SHELL_TOOL_TRIGGER = "__LINECODE_TEST_SHELL__"
SHELL_TOOL_COMMAND = "printf linecode-tool-ok"
IMAGE_TOOL_TRIGGER = "__LINECODE_TEST_IMAGE__"
IMAGE_TOOL_PROMPT = "A deterministic LineCode fixture image"
IMAGE_UNDERSTANDING_TRIGGER = "__LINECODE_TEST_VISION__"
IMAGE_UNDERSTANDING_PATH = "assets/linecode-test.png"
IMAGE_UNDERSTANDING_PROMPT = "Describe the deterministic LineCode fixture"
FILE_TOOL_TRIGGER = "__LINECODE_TEST_FILE__"
FILE_TOOL_PATH = "linecode-tool-check.txt"
FILE_TOOL_CONTENT = "linecode file tool ok"
AGENT_TOOL_TRIGGER = "__LINECODE_TEST_AGENT__"
# Same, but a writable agent whose prompt asks for a file write, so the
# sub-agent has to raise a review before touching the filesystem.
AGENT_WRITE_TOOL_TRIGGER = "__LINECODE_TEST_AGENT_WRITE__"
# Keeps requesting one cheap read-only tool so a single conversation can build
# a long tool loop; used to exercise mid-loop context compaction.
LOOP_TOOL_TRIGGER = "__LINECODE_TEST_LOOP__"
# Fails the first N completions so the retry path can be exercised, then
# answers normally. N counts requests, not triggers.
FAIL_TOOL_TRIGGER = "__LINECODE_TEST_FAIL__"
FAIL_TOOL_ATTEMPTS = 2
LOOP_TOOL_ROUNDS = 14
TODO_TOOL_TRIGGER = "__LINECODE_TEST_TODO__"
TODO_TOOL_ITEMS = (
    {"content": "Verify the todo prompt projection", "status": "in_progress"},
    {"content": "Confirm the todo projection round trip", "status": "pending"},
)
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
        response_delay: float = 0.0,
        request_log: Path | None = None,
    ) -> None:
        super().__init__(address, FakeAiHandler)
        self.reply = reply
        self.log_requests = log_requests
        self.read_timeout = read_timeout
        self.response_delay = response_delay
        self.request_log = request_log
        self.request_log_lock = threading.Lock()
        # Counts completions failed by FAIL_TOOL_TRIGGER so the retry path is
        # deterministic and bounded.
        self.fail_count = 0

    def record_request(self, path: str, request: dict[str, Any]) -> None:
        if self.request_log is None:
            return
        record = compact_json({"path": path, "body": request}) + b"\n"
        with self.request_log_lock:
            self.request_log.parent.mkdir(parents=True, exist_ok=True)
            with self.request_log.open("ab") as output:
                output.write(record)


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

        try:
            decoded = json.loads(body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            # A malformed tool schema is otherwise only visible as a byte
            # offset, which is unusable in a 27KB body; log the region.
            position = getattr(error, "pos", 0)
            if self.fixture_server.request_log is not None:
                # Write the whole body next to the log: a 150-byte window is
                # not enough to locate a defect in a 27KB request, and the body
                # can be re-parsed offline for the exact error.
                copy_path = (
                    self.fixture_server.request_log.parent / "malformed-body.json"
                )
                copy_path.write_bytes(body)
                with self.fixture_server.request_log.open("ab") as dump:
                    dump.write(
                        b"\n--- malformed body at byte "
                        + str(position).encode()
                        + b" (full copy: malformed-body.json) ---\n"
                    )
            raise
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
        delay = self.fixture_server.response_delay
        if delay > 0:
            time.sleep(delay)
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
        self.fixture_server.record_request(path, request)
        if path in {"/v1/chat/completions", "/chat/completions"}:
            self.handle_chat_completions(request, request.get("stream") is True)
            return
        if path in {"/v1/images/generations", "/images/generations"}:
            self.handle_image_generation(request)
            return
        if path in {"/v1/responses", "/responses"}:
            self.handle_responses(request, request.get("stream") is True)
            return
        # Kept for the app's Anthropic protocol option. It is also deterministic.
        if path in {"/v1/messages", "/messages", "/anthropic/v1/messages"}:
            self.handle_anthropic_messages(request.get("stream") is True)
            return
        self.send_not_found()

    @staticmethod
    def requested_function_tool(request: dict[str, Any]) -> dict[str, Any] | None:
        messages = request.get("messages")
        tools = request.get("tools")
        if not isinstance(messages, list) or not isinstance(tools, list):
            return None
        loop_requested = any(
            isinstance(message, dict)
            and message.get("role") == "user"
            and LOOP_TOOL_TRIGGER in str(message.get("content", ""))
            for message in messages
        )
        tool_rounds = sum(
            1
            for message in messages
            if isinstance(message, dict) and message.get("role") == "tool"
        )
        if tool_rounds:
            # Every other trigger stops after one call to keep runs short. The
            # loop trigger keeps going so the tool loop itself is exercised.
            if not loop_requested or tool_rounds >= LOOP_TOOL_ROUNDS:
                return None
            available = any(
                isinstance(tool, dict)
                and isinstance(tool.get("function"), dict)
                and tool["function"].get("name") == "list_dir"
                for tool in tools
            )
            if available:
                return {
                    "index": 0,
                    "id": f"call_linecode_loop_{tool_rounds}",
                    "type": "function",
                    "function": {
                        "name": "list_dir",
                        "arguments": compact_json({"path": "."}).decode("utf-8"),
                    },
                }
            return None
        strategies = (
            (
                # Starts the loop: without an entry here the first request of a
                # loop run falls through to the default reply and the loop the
                # trigger is named for never begins.
                LOOP_TOOL_TRIGGER,
                "list_dir",
                {"path": "."},
                "call_linecode_loop_first",
            ),
            (
                AGENT_WRITE_TOOL_TRIGGER,
                "agent",
                {
                    "type": "sub-coding",
                    "description": "Write the check file",
                    "prompt": "Write the check file. " + FILE_TOOL_TRIGGER,
                    "write_scope": ["."],
                },
                "call_linecode_agent_write_test",
            ),
            (
                AGENT_TOOL_TRIGGER,
                "agent",
                {
                    "type": "explore",
                    "description": "Inspect the workspace",
                    "prompt": "List the files and report what you find.",
                    "read_scope": ["."],
                },
                "call_linecode_agent_test",
            ),
            (
                FILE_TOOL_TRIGGER,
                "file_write",
                {
                    "file_path": FILE_TOOL_PATH,
                    "content": FILE_TOOL_CONTENT,
                },
                "call_linecode_file_test",
            ),
            (
                TODO_TOOL_TRIGGER,
                "todo_update",
                {"items": list(TODO_TOOL_ITEMS)},
                "call_linecode_todo_test",
            ),
            (
                SHELL_TOOL_TRIGGER,
                "shell_execute",
                {"command": SHELL_TOOL_COMMAND},
                "call_linecode_shell_test",
            ),
            (
                IMAGE_TOOL_TRIGGER,
                "image_generation",
                {"prompt": IMAGE_TOOL_PROMPT, "size": "1024x1024"},
                "call_linecode_image_test",
            ),
            (
                IMAGE_UNDERSTANDING_TRIGGER,
                "image_understanding",
                {
                    "path": IMAGE_UNDERSTANDING_PATH,
                    "prompt": IMAGE_UNDERSTANDING_PROMPT,
                },
                "call_linecode_vision_test",
            ),
        )
        for trigger, name, arguments, call_id in strategies:
            trigger_present = any(
                isinstance(message, dict)
                and message.get("role") == "user"
                and trigger in str(message.get("content", ""))
                for message in messages
            )
            available = any(
                isinstance(tool, dict)
                and isinstance(tool.get("function"), dict)
                and tool["function"].get("name") == name
                for tool in tools
            )
            if trigger_present and available:
                return {
                    "index": 0,
                    "id": call_id,
                    "type": "function",
                    "function": {
                        "name": name,
                        "arguments": compact_json(arguments).decode("utf-8"),
                    },
                }
        return None

    @classmethod
    def requests_shell_tool(cls, request: dict[str, Any]) -> bool:
        call = cls.requested_function_tool(request)
        return call is not None and call["function"]["name"] == "shell_execute"

    def maybe_fail(self, request: dict[str, Any]) -> bool:
        """Fails deterministically while a FAIL trigger asks for it."""
        trigger = any(
            isinstance(message, dict)
            and message.get("role") == "user"
            and FAIL_TOOL_TRIGGER in str(message.get("content", ""))
            for message in (request.get("messages") or [])
        )
        if not trigger:
            return False
        self.fail_count += 1
        if self.fail_count > FAIL_TOOL_ATTEMPTS:
            return False
        body = b'{"error":{"message":"deterministic test failure","type":"server_error"}}'
        self.send_response(500)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        return True

    @staticmethod
    def prompt_tokens(request: dict[str, Any]) -> int:
        """A plausible prompt size for the reported usage.

        The app prefers the server's count over its own estimate once one is
        reported, so a constant `1` would silently disable context
        compaction. Roughly the same characters-over-four rule the local
        estimate uses keeps the two in the same ballpark.
        """
        size = len(compact_json(request.get("messages") or []))
        size += len(compact_json(request.get("tools") or []))
        return max(1, size // 4)

    def handle_chat_completions(
        self, request: dict[str, Any], stream: bool
    ) -> None:
        if self.maybe_fail(request):
            return
        usage = {
            "prompt_tokens": self.prompt_tokens(request),
            "completion_tokens": 1,
            "total_tokens": self.prompt_tokens(request) + 1,
        }
        response_id = "chatcmpl-linecode-test"
        common = {
            "id": response_id,
            "created": 0,
            "model": MODEL_ID,
        }
        call = self.requested_function_tool(request)
        if call is not None:
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
                                        "delta": {
                                            "role": "assistant",
                                            "tool_calls": [call],
                                        },
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
                                        "delta": {},
                                        "finish_reason": "tool_calls",
                                    }
                                ],
                            },
                        ),
                        (
                            None,
                            common
                            | {
                                "object": "chat.completion.chunk",
                                "choices": [],
                                    "usage": usage,
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
                            "message": {
                                "role": "assistant",
                                "content": None,
                                "tool_calls": [call],
                            },
                            "finish_reason": "tool_calls",
                        }
                    ],
                    "usage": usage,
                }
            )
            return
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
                    (
                        None,
                        common
                        | {
                            "object": "chat.completion.chunk",
                            "choices": [],
                            "usage": usage,
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
                "usage": usage,
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

    def handle_image_generation(self, request: dict[str, Any]) -> None:
        prompt = str(request.get("prompt", "")).strip()
        if not prompt:
            self.send_json(
                {"error": {"message": "prompt is required", "type": "invalid_request_error"}},
                HTTPStatus.BAD_REQUEST,
            )
            return
        self.send_json(
            {
                "created": 0,
                "data": [
                    {
                        "b64_json": FIXTURE_IMAGE_BASE64,
                        "mime_type": "image/png",
                        "revised_prompt": prompt,
                    }
                ],
            }
        )

    @staticmethod
    def requests_responses_image(request: dict[str, Any]) -> bool:
        tools = request.get("tools")
        return isinstance(tools, list) and any(
            isinstance(tool, dict) and tool.get("type") == "image_generation"
            for tool in tools
        )

    def handle_responses(self, request: dict[str, Any], stream: bool) -> None:
        if self.requests_responses_image(request) and not stream:
            self.send_json(
                {
                    "id": "resp_linecode_image_test",
                    "object": "response",
                    "status": "completed",
                    "output": [
                        {
                            "id": "image_linecode_test",
                            "type": "image_generation_call",
                            "status": "completed",
                            "result": FIXTURE_IMAGE_BASE64,
                        }
                    ],
                }
            )
            return
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
    parser.add_argument(
        "--response-delay",
        type=float,
        default=0.0,
        help=(
            "seconds to wait before answering. Transient UI states -- the "
            "working indicator, a running tool card -- cannot be screenshotted "
            "against an instant reply, so holding the response is the only way "
            "to compare them."
        ),
    )
    parser.add_argument(
        "--request-log",
        type=Path,
        help="append each parsed POST body as one JSON line for integration assertions",
    )
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
        (args.host, args.port), reply=args.reply, log_requests=not args.quiet,
        response_delay=args.response_delay,
        request_log=args.request_log,
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
