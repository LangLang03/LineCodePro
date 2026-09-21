#!/usr/bin/env python3
"""Deterministic, dependency-free AI protocol fixture for LineCode tests.

This is intentionally not an AI implementation. It only returns a configured,
fixed string so UI and protocol integration tests remain repeatable.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import signal
import socket
import ssl
import threading
import time
from dataclasses import dataclass
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
# A self-contained ordinary-tool fixture for the chat timeline UI: unlike the
# shell fixture it is available in the default local execution mode, and its
# write card has an expandable detail body.
TOOL_FLOW_TRIGGER = "__LINECODE_TEST_TOOL_FLOW__"
TOOL_FLOW_PATH = "linecode-ui-tool-flow.txt"
TOOL_FLOW_CONTENT = "linecode deterministic tool flow"
# A destructive operation that both implementations must always present for
# approval, independent of the currently selected permission mode.  The path
# is intentionally absent, so accepting the fixture cannot remove user data.
DELETE_TOOL_TRIGGER = "__LINECODE_TEST_DELETE__"
DELETE_TOOL_PATH = "linecode-fixture-does-not-exist.txt"
IMAGE_TOOL_TRIGGER = "__LINECODE_TEST_IMAGE__"
IMAGE_TOOL_PROMPT = "A deterministic LineCode fixture image"
IMAGE_UNDERSTANDING_TRIGGER = "__LINECODE_TEST_VISION__"
IMAGE_UNDERSTANDING_PATH = "assets/linecode-test.png"
IMAGE_UNDERSTANDING_PROMPT = "Describe the deterministic LineCode fixture"
FILE_TOOL_TRIGGER = "__LINECODE_TEST_FILE__"
FILE_TOOL_PATH = "linecode-tool-check.txt"
FILE_TOOL_CONTENT = "linecode file tool ok"
AGENT_TOOL_TRIGGER = "__LINECODE_TEST_AGENT__"
AGENT_PIPELINE_TOOL_TRIGGER = "__LINECODE_TEST_AGENT_PIPELINE__"
AGENT_FAIL_TOOL_TRIGGER = "__LINECODE_TEST_AGENT_FAIL__"
AGENT_PIPELINE_FAIL_TOOL_TRIGGER = "__LINECODE_TEST_AGENT_PIPELINE_FAIL__"
AGENT_NESTED_TOOL_TRIGGER = "__LINECODE_TEST_AGENT_NESTED__"
AGENT_PIPELINE_PARALLEL_TOOL_TRIGGER = "__LINECODE_TEST_AGENT_PIPELINE_PARALLEL__"
AGENT_FAILURE_PROMPT = (
    "This deterministic sub-agent must exercise its failure path. "
    "__LINECODE_TEST_ALWAYS_FAIL__"
)
AGENT_PIPELINE_TASKS = (
    {
        "id": "inspect",
        "type": "explore",
        "description": "Inspect the workspace",
        "prompt": "List the files and summarize the workspace. Do not modify files.",
        "read_scope": ["."],
    },
    {
        "id": "verify",
        "type": "explore",
        "description": "Verify the inspection",
        "prompt": "Check the inspection summary. Do not modify files.",
        "read_scope": ["."],
        "depends_on": ["inspect"],
    },
)
AGENT_PIPELINE_FAIL_TASKS = (
    {
        "id": "inspect",
        "type": "explore",
        "description": "Inspect before failure",
        "prompt": "Inspect the workspace before the failure probe.",
        "read_scope": ["."],
    },
    {
        "id": "fail",
        "type": "explore",
        "description": "Fail the inspection",
        "prompt": AGENT_FAILURE_PROMPT,
        "read_scope": ["."],
        "depends_on": ["inspect"],
    },
)
AGENT_PIPELINE_PARALLEL_TASKS = (
    {
        "id": "left",
        "type": "explore",
        "description": "Inspect the left branch",
        "prompt": "Inspect the left branch without modifying files.",
        "read_scope": ["."],
    },
    {
        "id": "right",
        "type": "explore",
        "description": "Inspect the right branch",
        "prompt": "Inspect the right branch without modifying files.",
        "read_scope": ["."],
    },
)
# Same, but a writable agent whose prompt asks for a file write, so the
# sub-agent has to raise a review before touching the filesystem.
AGENT_WRITE_TOOL_TRIGGER = "__LINECODE_TEST_AGENT_WRITE__"
# Keeps requesting one cheap read-only tool so a single conversation can build
# a long tool loop; used to exercise mid-loop context compaction.
LOOP_TOOL_TRIGGER = "__LINECODE_TEST_LOOP__"
# A shorter sequential tool loop for timeline tests that need several cards
# without paying the cost of the compaction fixture's fourteen rounds.
THREE_TOOL_TRIGGER = "__LINECODE_TEST_MULTI_3__"
# Adds protocol-native reasoning before text/tool output.  It can be combined
# with any tool trigger and with the stage gate endpoints below.
STAGED_FLOW_TRIGGER = "__LINECODE_TEST_STAGES__"
FIXTURE_REASONING = "LineCode deterministic fixture reasoning."
LONG_REASONING_TRIGGER = "__LINECODE_TEST_LONG_REASONING__"
LONG_FIXTURE_REASONING = "\n".join(
    f"Reasoning line {index}: deterministic expanded content."
    for index in range(1, 17)
)
# Fails the first N completions so the retry path can be exercised, then
# answers normally. N counts requests within one fixture session/user turn.
FAIL_TOOL_TRIGGER = "__LINECODE_TEST_FAIL__"
FAIL_TOOL_ATTEMPTS = 2
ALWAYS_FAIL_TRIGGER = "__LINECODE_TEST_ALWAYS_FAIL__"
LOOP_TOOL_ROUNDS = 14
TODO_TOOL_TRIGGER = "__LINECODE_TEST_TODO__"
TODO_TOOL_ITEMS = (
    {"content": "Verify the todo prompt projection", "status": "in_progress"},
    {"content": "Confirm the todo projection round trip", "status": "pending"},
)
MAX_REQUEST_BODY_BYTES = 1024 * 1024
REQUEST_READ_TIMEOUT_SECONDS = 5.0
FIXTURE_SESSION_HEADER = "X-LineCode-Fixture-Session"
LEGACY_APPLICATION_CONTEXT_PREFIX = "[Application context]\n"
FIXTURE_STAGES = frozenset(
    {"reasoning", "text", "tool_requested", "tool_running", "final"}
)


@dataclass(frozen=True)
class FixtureContext:
    """Protocol-neutral identity for one request in a conversation turn."""

    protocol: str
    session: str
    turn: int
    latest_user_content: str
    tool_rounds: int

    def stable_id(self, prefix: str, ordinal: int = 0) -> str:
        source = (
            f"{self.protocol}\0{self.session}\0{self.turn}\0"
            f"{self.latest_user_content}\0{self.tool_rounds}\0{ordinal}"
        )
        digest = hashlib.sha256(source.encode("utf-8")).hexdigest()[:20]
        return f"{prefix}_{digest}"


def fixture_reasoning(context: FixtureContext) -> str:
    """Return short or multi-line reasoning selected by the current user turn."""
    if LONG_REASONING_TRIGGER in context.latest_user_content:
        return LONG_FIXTURE_REASONING
    return FIXTURE_REASONING


def chat_reasoning_chunks(context: FixtureContext) -> tuple[str, ...]:
    """Model realistic token-sized SSE fragmentation for the long fixture."""
    reasoning = fixture_reasoning(context)
    if LONG_REASONING_TRIGGER not in context.latest_user_content:
        return (reasoning,)
    return tuple(reasoning.splitlines(keepends=True))


def requests_fixture_reasoning(context: FixtureContext) -> bool:
    return (
        STAGED_FLOW_TRIGGER in context.latest_user_content
        or LONG_REASONING_TRIGGER in context.latest_user_content
    )


class RequestBodyError(Exception):
    """A client-facing request body error with an HTTP status."""

    def __init__(self, status: HTTPStatus, message: str) -> None:
        super().__init__(message)
        self.status = status


def compact_json(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def text_content(value: Any) -> str:
    """Extract only human-authored text, excluding protocol tool results."""
    if isinstance(value, str):
        return value
    if not isinstance(value, list):
        return ""
    parts: list[str] = []
    for part in value:
        if not isinstance(part, dict):
            continue
        part_type = part.get("type")
        if part_type in {"text", "input_text"}:
            text = part.get("text")
            if isinstance(text, str):
                parts.append(text)
    return "\n".join(parts)


def protocol_items(request: dict[str, Any], protocol: str) -> list[Any]:
    if protocol == "responses":
        value = request.get("input")
        if isinstance(value, str):
            return [{"type": "message", "role": "user", "content": value}]
        return value if isinstance(value, list) else []
    value = request.get("messages")
    return value if isinstance(value, list) else []


def user_text(item: Any, protocol: str) -> str | None:
    if not isinstance(item, dict):
        return None
    if protocol == "responses":
        if item.get("type", "message") != "message" or item.get("role") != "user":
            return None
    elif item.get("role") != "user":
        return None
    content = text_content(item.get("content"))
    # Anthropic represents tool results as role=user messages.  An empty or
    # tool-result-only block is not a new human turn.
    if protocol == "anthropic" and not content:
        return None
    return content


def is_synthetic_application_context(content: str) -> bool:
    """Identify legacy context projection that is not a new human turn.

    The Kotlin application appends this as a role=user message after the real
    user input. Treating it as the current turn hides explicit fixture
    triggers and also inflates the turn ordinal used for deterministic IDs.
    """
    return content.startswith(LEGACY_APPLICATION_CONTEXT_PREFIX)


def is_tool_result(item: Any, protocol: str) -> bool:
    if not isinstance(item, dict):
        return False
    if protocol == "chat":
        return item.get("role") == "tool"
    if protocol == "responses":
        return item.get("type") == "function_call_output"
    if protocol == "anthropic" and item.get("role") == "user":
        content = item.get("content")
        return isinstance(content, list) and any(
            isinstance(part, dict) and part.get("type") == "tool_result"
            for part in content
        )
    return False


def tool_result_contents(request: dict[str, Any], protocol: str) -> list[str]:
    """Return protocol-native tool-result payloads in wire order."""
    contents: list[str] = []
    for item in protocol_items(request, protocol):
        if not isinstance(item, dict):
            continue
        if protocol == "chat" and item.get("role") == "tool":
            value = item.get("content")
            contents.append(value if isinstance(value, str) else "")
            continue
        if protocol == "responses" and item.get("type") == "function_call_output":
            value = item.get("output")
            contents.append(value if isinstance(value, str) else "")
            continue
        if protocol != "anthropic" or item.get("role") != "user":
            continue
        blocks = item.get("content")
        if not isinstance(blocks, list):
            continue
        for block in blocks:
            if not isinstance(block, dict) or block.get("type") != "tool_result":
                continue
            value = block.get("content")
            if isinstance(value, str):
                contents.append(value)
            else:
                contents.append(text_content(value))
    return contents


def request_turn(request: dict[str, Any], protocol: str) -> tuple[int, int, str, int]:
    """Return (turn ordinal, item index, latest text, later tool results)."""
    items = protocol_items(request, protocol)
    users: list[tuple[int, str]] = []
    for index, item in enumerate(items):
        if (
            (content := user_text(item, protocol)) is not None
            and not is_synthetic_application_context(content)
        ):
            users.append((index, content))
    if not users:
        return 0, -1, "", sum(is_tool_result(item, protocol) for item in items)
    latest_index, latest_content = users[-1]
    rounds = sum(is_tool_result(item, protocol) for item in items[latest_index + 1 :])
    return len(users), latest_index, latest_content, rounds


def available_tool_names(request: dict[str, Any]) -> set[str]:
    names: set[str] = set()
    tools = request.get("tools")
    if not isinstance(tools, list):
        return names
    for tool in tools:
        if not isinstance(tool, dict):
            continue
        function = tool.get("function")
        if isinstance(function, dict) and isinstance(function.get("name"), str):
            names.add(function["name"])
        elif isinstance(tool.get("name"), str):
            # Responses and Anthropic both put the name at the top level.
            names.add(tool["name"])
    return names


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
        stage_delays: dict[str, float] | None = None,
        gate_timeout: float = 120.0,
    ) -> None:
        super().__init__(address, FakeAiHandler)
        self.reply = reply
        self.log_requests = log_requests
        self.read_timeout = read_timeout
        self.response_delay = response_delay
        self.request_log = request_log
        self.request_log_lock = threading.Lock()
        self.stage_delays = dict(stage_delays or {})
        self.gate_timeout = gate_timeout
        self.state_condition = threading.Condition()
        # Failure counters are isolated by explicit/derived session and user
        # turn. A retry in one UI run must never consume another run's budget.
        self.failure_counts: dict[tuple[str, int], int] = {}
        self.protocol_request_counts: dict[tuple[str, str], int] = {}
        self.protocol_tool_results: dict[tuple[str, str], list[str]] = {}
        self.gated_stages: set[tuple[str, str]] = set()

    def record_request(self, path: str, request: dict[str, Any]) -> None:
        if self.request_log is None:
            return
        record = compact_json({"path": path, "body": request}) + b"\n"
        with self.request_log_lock:
            self.request_log.parent.mkdir(parents=True, exist_ok=True)
            with self.request_log.open("ab") as output:
                output.write(record)

    def reset(self, session: str | None = None) -> None:
        with self.state_condition:
            if session is None:
                self.failure_counts.clear()
                self.protocol_request_counts.clear()
                self.protocol_tool_results.clear()
                self.gated_stages.clear()
            else:
                self.failure_counts = {
                    key: value
                    for key, value in self.failure_counts.items()
                    if key[0] != session
                }
                self.protocol_request_counts = {
                    key: value
                    for key, value in self.protocol_request_counts.items()
                    if key[0] != session
                }
                self.protocol_tool_results = {
                    key: value
                    for key, value in self.protocol_tool_results.items()
                    if key[0] != session
                }
                self.gated_stages = {
                    key for key in self.gated_stages if key[0] != session
                }
            self.state_condition.notify_all()

    def note_protocol_request(
        self, context: FixtureContext, request: dict[str, Any]
    ) -> None:
        key = (context.session, context.protocol)
        with self.state_condition:
            self.protocol_request_counts[key] = (
                self.protocol_request_counts.get(key, 0) + 1
            )
            self.protocol_tool_results.setdefault(key, []).extend(
                tool_result_contents(request, context.protocol)
            )

    def gate(self, session: str, stages: set[str]) -> None:
        with self.state_condition:
            self.gated_stages.update((session, stage) for stage in stages)

    def release(self, session: str, stages: set[str]) -> None:
        with self.state_condition:
            for stage in stages:
                self.gated_stages.discard((session, stage))
            self.state_condition.notify_all()

    def pause_stage(
        self,
        context: FixtureContext,
        stage: str,
        request: dict[str, Any],
    ) -> None:
        fixture = request.get("fixture")
        request_delays = fixture.get("stage_delays", {}) if isinstance(fixture, dict) else {}
        delay = request_delays.get(stage, self.stage_delays.get(stage, 0.0))
        if isinstance(delay, (int, float)) and delay > 0:
            time.sleep(float(delay))

        inline_gates = fixture.get("gates", []) if isinstance(fixture, dict) else []
        if isinstance(inline_gates, list) and stage in inline_gates:
            self.gate(context.session, {stage})
        deadline = time.monotonic() + self.gate_timeout
        with self.state_condition:
            while (context.session, stage) in self.gated_stages:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    # A forgotten release must not leak a server thread forever.
                    self.gated_stages.discard((context.session, stage))
                    break
                self.state_condition.wait(remaining)

    def next_failure_count(self, context: FixtureContext) -> int:
        key = (context.session, context.turn)
        with self.state_condition:
            count = self.failure_counts.get(key, 0) + 1
            self.failure_counts[key] = count
            return count

    def snapshot(self, session: str) -> dict[str, Any]:
        """Return deterministic, read-only state for UI-runner assertions."""
        with self.state_condition:
            attempts = {
                str(turn): count
                for (candidate, turn), count in sorted(self.failure_counts.items())
                if candidate == session
            }
            gated = sorted(
                stage for candidate, stage in self.gated_stages if candidate == session
            )
            protocol_counts = {
                protocol: count
                for (candidate, protocol), count in sorted(
                    self.protocol_request_counts.items()
                )
                if candidate == session
            }
            tool_result_counts = {
                protocol: len(contents)
                for (candidate, protocol), contents in sorted(
                    self.protocol_tool_results.items()
                )
                if candidate == session and contents
            }
            empty_tool_result_counts = {
                protocol: sum(not content.strip() for content in contents)
                for (candidate, protocol), contents in sorted(
                    self.protocol_tool_results.items()
                )
                if candidate == session
                and any(not content.strip() for content in contents)
            }
        return {
            "status": "ok",
            "session": session,
            "failure_attempts": sum(attempts.values()),
            "failure_attempts_by_turn": attempts,
            "protocol_request_count": sum(protocol_counts.values()),
            "protocol_request_counts": protocol_counts,
            "protocol_tool_result_counts": tool_result_counts,
            "protocol_empty_tool_result_counts": empty_tool_result_counts,
            "gated_stages": gated,
        }


class FakeAiHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    server_version = "LineCodeFixedReplyFixture/1.0"

    @property
    def fixture_server(self) -> FixtureServer:
        return self.server  # type: ignore[return-value]

    @property
    def reply(self) -> str:
        return self.fixture_server.reply

    def fixture_session(self, request: dict[str, Any], protocol: str) -> str:
        candidates: list[Any] = [
            self.headers.get(FIXTURE_SESSION_HEADER),
            request.get("fixture_session"),
            request.get("conversation_id"),
            request.get("user"),
        ]
        metadata = request.get("metadata")
        if isinstance(metadata, dict):
            candidates.extend(
                (metadata.get("fixture_session"), metadata.get("session_id"))
            )
        fixture = request.get("fixture")
        if isinstance(fixture, dict):
            candidates.append(fixture.get("session"))
        for candidate in candidates:
            if isinstance(candidate, str) and candidate.strip():
                return candidate.strip()

        items = protocol_items(request, protocol)
        first_user = next(
            (
                content
                for item in items
                if (content := user_text(item, protocol)) is not None
            ),
            "",
        )
        digest = hashlib.sha256(
            f"{protocol}\0{first_user}".encode("utf-8")
        ).hexdigest()[:16]
        return f"auto-{digest}"

    def fixture_context(
        self, request: dict[str, Any], protocol: str
    ) -> FixtureContext:
        turn, _, latest_content, rounds = request_turn(request, protocol)
        return FixtureContext(
            protocol=protocol,
            session=self.fixture_session(request, protocol),
            turn=turn,
            latest_user_content=latest_content,
            tool_rounds=rounds,
        )

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

    def send_staged_sse(
        self,
        events: list[tuple[str, str | None, Any]],
        context: FixtureContext,
        request: dict[str, Any],
    ) -> None:
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache, no-transform")
        self.send_header("Connection", "close")
        self.end_headers()
        reached_stages: set[str] = set()
        for stage, event_name, payload in events:
            if stage not in reached_stages:
                self.fixture_server.pause_stage(context, stage, request)
                reached_stages.add(stage)
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
        if path in {"/reset", "/fixture/reset"}:
            session = request.get("session")
            self.fixture_server.reset(session if isinstance(session, str) else None)
            self.send_json({"status": "reset", "session": session})
            return
        if path in {"/state", "/fixture/state"}:
            session = request.get("session")
            if not isinstance(session, str) or not session:
                self.send_json(
                    {
                        "error": {
                            "message": "session is required",
                            "type": "invalid_request_error",
                        }
                    },
                    HTTPStatus.BAD_REQUEST,
                )
                return
            self.send_json(self.fixture_server.snapshot(session))
            return
        if path in {"/gate", "/fixture/gate", "/release", "/fixture/release"}:
            session = request.get("session")
            raw_stages = request.get("stages", request.get("stage", []))
            if isinstance(raw_stages, str):
                raw_stages = [raw_stages]
            stages = (
                {stage for stage in raw_stages if stage in FIXTURE_STAGES}
                if isinstance(raw_stages, list)
                else set()
            )
            if not isinstance(session, str) or not session or not stages:
                self.send_json(
                    {
                        "error": {
                            "message": "session and valid stage(s) are required",
                            "type": "invalid_request_error",
                        }
                    },
                    HTTPStatus.BAD_REQUEST,
                )
                return
            if path.endswith("/gate") or path == "/gate":
                self.fixture_server.gate(session, stages)
                status = "gated"
            else:
                self.fixture_server.release(session, stages)
                status = "released"
            self.send_json({"status": status, "session": session, "stages": sorted(stages)})
            return

        delay = self.fixture_server.response_delay
        if delay > 0:
            time.sleep(delay)
        self.fixture_server.record_request(path, request)
        if path in {"/v1/chat/completions", "/chat/completions"}:
            self.fixture_server.note_protocol_request(
                self.fixture_context(request, "chat"), request
            )
            self.handle_chat_completions(request, request.get("stream") is True)
            return
        if path in {"/v1/images/generations", "/images/generations"}:
            self.handle_image_generation(request)
            return
        if path in {"/v1/responses", "/responses"}:
            self.fixture_server.note_protocol_request(
                self.fixture_context(request, "responses"), request
            )
            self.handle_responses(request, request.get("stream") is True)
            return
        # Kept for the app's Anthropic protocol option. It is also deterministic.
        if path in {"/v1/messages", "/messages", "/anthropic/v1/messages"}:
            self.fixture_server.note_protocol_request(
                self.fixture_context(request, "anthropic"), request
            )
            self.handle_anthropic_messages(request, request.get("stream") is True)
            return
        self.send_not_found()

    def requested_function_tool(
        self,
        request: dict[str, Any],
        protocol: str = "chat",
        context: FixtureContext | None = None,
    ) -> dict[str, Any] | None:
        context = context or self.fixture_context(request, protocol)
        latest_user_content = context.latest_user_content
        tool_rounds = context.tool_rounds
        available = available_tool_names(request)
        if not available:
            return None

        sequential: tuple[tuple[str, dict[str, Any]], ...] | None = None
        if THREE_TOOL_TRIGGER in latest_user_content:
            sequential = (
                ("list_dir", {"path": "."}),
                ("file_read", {"file_path": "README.md"}),
                ("todo_update", {"items": list(TODO_TOOL_ITEMS)}),
            )
        elif LOOP_TOOL_TRIGGER in latest_user_content:
            sequential = tuple(
                ("list_dir", {"path": "."}) for _ in range(LOOP_TOOL_ROUNDS)
            )
        if sequential is not None:
            if tool_rounds >= len(sequential):
                return None
            name, arguments = sequential[tool_rounds]
            if name not in available:
                return None
            return {
                "index": 0,
                "id": context.stable_id("call_linecode", tool_rounds),
                "type": "function",
                "function": {
                    "name": name,
                    "arguments": compact_json(arguments).decode("utf-8"),
                },
            }

        # Every non-sequential trigger stops after one call in its current user
        # turn. A later user turn may explicitly request the same tool again.
        if tool_rounds:
            return None
        strategies = (
            (
                AGENT_PIPELINE_PARALLEL_TOOL_TRIGGER,
                "agent_pipeline",
                {"agents": list(AGENT_PIPELINE_PARALLEL_TASKS)},
            ),
            (
                AGENT_PIPELINE_FAIL_TOOL_TRIGGER,
                "agent_pipeline",
                {"agents": list(AGENT_PIPELINE_FAIL_TASKS)},
            ),
            (
                AGENT_PIPELINE_TOOL_TRIGGER,
                "agent_pipeline",
                {"agents": list(AGENT_PIPELINE_TASKS)},
            ),
            (
                AGENT_FAIL_TOOL_TRIGGER,
                "agent",
                {
                    "type": "explore",
                    "description": "Fail the inspection",
                    "prompt": AGENT_FAILURE_PROMPT,
                    "read_scope": ["."],
                },
            ),
            (
                AGENT_NESTED_TOOL_TRIGGER,
                "agent",
                {
                    "type": "explore",
                    "description": "Nested tool inspection",
                    "prompt": (
                        "Run the deterministic nested read-only tool flow. "
                        "__LINECODE_TEST_MULTI_3__"
                    ),
                    "read_scope": ["."],
                },
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
            ),
            (
                TOOL_FLOW_TRIGGER,
                "file_write",
                {
                    "file_path": TOOL_FLOW_PATH,
                    "content": TOOL_FLOW_CONTENT,
                },
            ),
            (
                DELETE_TOOL_TRIGGER,
                "file_delete",
                {
                    "paths": [DELETE_TOOL_PATH],
                    "reason": "LineCode deterministic approval fixture",
                },
            ),
            (
                FILE_TOOL_TRIGGER,
                "file_write",
                {
                    "file_path": FILE_TOOL_PATH,
                    "content": FILE_TOOL_CONTENT,
                },
            ),
            (
                TODO_TOOL_TRIGGER,
                "todo_update",
                {"items": list(TODO_TOOL_ITEMS)},
            ),
            (
                SHELL_TOOL_TRIGGER,
                "shell_execute",
                {"command": SHELL_TOOL_COMMAND},
            ),
            (
                IMAGE_TOOL_TRIGGER,
                "image_generation",
                {"prompt": IMAGE_TOOL_PROMPT, "size": "1024x1024"},
            ),
            (
                IMAGE_UNDERSTANDING_TRIGGER,
                "image_understanding",
                {
                    "path": IMAGE_UNDERSTANDING_PATH,
                    "prompt": IMAGE_UNDERSTANDING_PROMPT,
                },
            ),
        )
        for ordinal, (trigger, name, arguments) in enumerate(strategies):
            trigger_present = trigger in latest_user_content
            if trigger_present and name in available:
                return {
                    "index": 0,
                    "id": context.stable_id("call_linecode", ordinal),
                    "type": "function",
                    "function": {
                        "name": name,
                        "arguments": compact_json(arguments).decode("utf-8"),
                    },
                }
        return None

    @classmethod
    def requests_shell_tool(cls, request: dict[str, Any]) -> bool:
        # Kept for imports that only need trigger detection. Tool dispatch uses
        # the instance method above so IDs can include the fixture session.
        _, _, latest, _ = request_turn(request, "chat")
        call = SHELL_TOOL_TRIGGER in latest
        available = "shell_execute" in available_tool_names(request)
        return call and available

    def maybe_fail(self, request: dict[str, Any], context: FixtureContext) -> bool:
        """Fails deterministically while a FAIL trigger asks for it."""
        always_fail = ALWAYS_FAIL_TRIGGER in context.latest_user_content
        if not always_fail and FAIL_TOOL_TRIGGER not in context.latest_user_content:
            return False
        failure_count = self.fixture_server.next_failure_count(context)
        if not always_fail and failure_count > FAIL_TOOL_ATTEMPTS:
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
        context = self.fixture_context(request, "chat")
        if self.maybe_fail(request, context):
            return
        usage = {
            "prompt_tokens": self.prompt_tokens(request),
            "completion_tokens": 1,
            "total_tokens": self.prompt_tokens(request) + 1,
        }
        response_id = context.stable_id("chatcmpl-linecode")
        common = {
            "id": response_id,
            "created": 0,
            "model": MODEL_ID,
        }
        call = self.requested_function_tool(request, "chat", context)
        staged = requests_fixture_reasoning(context)
        reasoning = fixture_reasoning(context)
        if call is not None:
            if stream:
                events: list[tuple[str, str | None, Any]] = []
                if staged:
                    events.extend(
                        (
                            "reasoning",
                            None,
                            common
                            | {
                                "object": "chat.completion.chunk",
                                "choices": [
                                    {
                                        "index": 0,
                                        "delta": {
                                            "role": "assistant",
                                            "reasoning_content": chunk,
                                        },
                                        "finish_reason": None,
                                    }
                                ],
                            },
                        )
                        for chunk in chat_reasoning_chunks(context)
                    )
                events.extend(
                    [
                        (
                            "tool_requested",
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
                            "tool_running",
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
                            "final",
                            None,
                            common
                            | {
                                "object": "chat.completion.chunk",
                                "choices": [],
                                    "usage": usage,
                            },
                        ),
                        ("final", None, "[DONE]"),
                    ]
                )
                self.send_staged_sse(events, context, request)
                return
            self.fixture_server.pause_stage(context, "tool_requested", request)
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
            events = []
            if staged:
                events.extend(
                    (
                        "reasoning",
                        None,
                        common
                        | {
                            "object": "chat.completion.chunk",
                            "choices": [
                                {
                                    "index": 0,
                                    "delta": {
                                        "role": "assistant",
                                        "reasoning_content": chunk,
                                    },
                                    "finish_reason": None,
                                }
                            ],
                        },
                    )
                    for chunk in chat_reasoning_chunks(context)
                )
            events.extend(
                [
                    (
                        "text",
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
                        "text",
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
                        "final",
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
                        "final",
                        None,
                        common
                        | {
                            "object": "chat.completion.chunk",
                            "choices": [],
                            "usage": usage,
                        },
                    ),
                    ("final", None, "[DONE]"),
                ]
            )
            self.send_staged_sse(events, context, request)
            return
        self.fixture_server.pause_stage(context, "final", request)
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

    def response_object(
        self,
        context: FixtureContext,
        status: str = "completed",
        output: list[dict[str, Any]] | None = None,
    ) -> dict[str, Any]:
        message_id = context.stable_id("msg_linecode")
        return {
            "id": context.stable_id("resp_linecode"),
            "object": "response",
            "created_at": 0,
            "status": status,
            "model": MODEL_ID,
            "output": output if output is not None else [
                {
                    "id": message_id,
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
        context = self.fixture_context(request, "responses")
        if self.maybe_fail(request, context):
            return
        if self.requests_responses_image(request) and not stream:
            self.send_json(
                {
                    "id": context.stable_id("resp_linecode_image"),
                    "object": "response",
                    "status": "completed",
                    "output": [
                        {
                            "id": context.stable_id("image_linecode"),
                            "type": "image_generation_call",
                            "status": "completed",
                            "result": FIXTURE_IMAGE_BASE64,
                        }
                    ],
                }
            )
            return
        response_id = context.stable_id("resp_linecode")
        message_id = context.stable_id("msg_linecode")
        call = self.requested_function_tool(request, "responses", context)
        staged = requests_fixture_reasoning(context)
        reasoning = fixture_reasoning(context)
        if call is not None:
            tool_item = {
                "id": context.stable_id("fc_linecode"),
                "type": "function_call",
                "status": "completed",
                "call_id": call["id"],
                "name": call["function"]["name"],
                "arguments": call["function"]["arguments"],
            }
            completed = self.response_object(context, output=[tool_item])
            if not stream:
                self.fixture_server.pause_stage(context, "tool_requested", request)
                self.send_json(completed)
                return
            sequence = 0
            events: list[tuple[str, str | None, Any]] = [
                (
                    "reasoning" if staged else "tool_requested",
                    "response.created",
                    {
                        "type": "response.created",
                        "sequence_number": sequence,
                        "response": self.response_object(
                            context, "in_progress", output=[]
                        ),
                    },
                )
            ]
            sequence += 1
            if staged:
                events.extend(
                    [
                        (
                            "reasoning",
                            "response.reasoning_summary_part.added",
                            {
                                "type": "response.reasoning_summary_part.added",
                                "sequence_number": sequence,
                                "output_index": 0,
                                "summary_index": 0,
                                "part": {"type": "summary_text", "text": ""},
                            },
                        ),
                        (
                            "reasoning",
                            "response.reasoning_summary_text.delta",
                            {
                                "type": "response.reasoning_summary_text.delta",
                                "sequence_number": sequence + 1,
                                "output_index": 0,
                                "summary_index": 0,
                                "delta": reasoning,
                            },
                        ),
                    ]
                )
                sequence += 2
            events.extend(
                [
                    (
                        "tool_requested",
                        "response.output_item.added",
                        {
                            "type": "response.output_item.added",
                            "sequence_number": sequence,
                            "output_index": 0,
                            "item": tool_item | {"arguments": ""},
                        },
                    ),
                    (
                        "tool_running",
                        "response.function_call_arguments.delta",
                        {
                            "type": "response.function_call_arguments.delta",
                            "sequence_number": sequence + 1,
                            "output_index": 0,
                            "item_id": tool_item["id"],
                            "delta": tool_item["arguments"],
                        },
                    ),
                    (
                        "tool_running",
                        "response.output_item.done",
                        {
                            "type": "response.output_item.done",
                            "sequence_number": sequence + 2,
                            "output_index": 0,
                            "item": tool_item,
                        },
                    ),
                    (
                        "final",
                        "response.completed",
                        {
                            "type": "response.completed",
                            "sequence_number": sequence + 3,
                            "response": completed,
                        },
                    ),
                ]
            )
            self.send_staged_sse(events, context, request)
            return
        output_item = {
            "id": message_id,
            "type": "message",
            "status": "in_progress",
            "role": "assistant",
            "content": [],
        }
        content_part = {"type": "output_text", "text": "", "annotations": []}
        if stream:
            events: list[tuple[str, str | None, Any]] = [
                    (
                        "reasoning" if staged else "text",
                        "response.created",
                        {
                            "type": "response.created",
                            "sequence_number": 0,
                            "response": self.response_object(
                                context, "in_progress", output=[]
                            ),
                        },
                    )
            ]
            sequence_offset = 0
            if staged:
                events.extend(
                    [
                        (
                            "reasoning",
                            "response.reasoning_summary_part.added",
                            {
                                "type": "response.reasoning_summary_part.added",
                                "sequence_number": 1,
                                "output_index": 0,
                                "summary_index": 0,
                                "part": {"type": "summary_text", "text": ""},
                            },
                        ),
                        (
                            "reasoning",
                            "response.reasoning_summary_text.delta",
                            {
                                "type": "response.reasoning_summary_text.delta",
                                "sequence_number": 2,
                                "output_index": 0,
                                "summary_index": 0,
                                "delta": reasoning,
                            },
                        ),
                    ]
                )
                sequence_offset = 2
            events.extend(
                [
                    (
                        "text",
                        "response.output_item.added",
                        {
                            "type": "response.output_item.added",
                            "sequence_number": 1 + sequence_offset,
                            "output_index": 0,
                            "item": output_item,
                        },
                    ),
                    (
                        "text",
                        "response.content_part.added",
                        {
                            "type": "response.content_part.added",
                            "sequence_number": 2 + sequence_offset,
                            "item_id": message_id,
                            "output_index": 0,
                            "content_index": 0,
                            "part": content_part,
                        },
                    ),
                    (
                        "text",
                        "response.output_text.delta",
                        {
                            "type": "response.output_text.delta",
                            "sequence_number": 3 + sequence_offset,
                            "item_id": message_id,
                            "output_index": 0,
                            "content_index": 0,
                            "delta": self.reply,
                        },
                    ),
                    (
                        "final",
                        "response.output_text.done",
                        {
                            "type": "response.output_text.done",
                            "sequence_number": 4 + sequence_offset,
                            "item_id": message_id,
                            "output_index": 0,
                            "content_index": 0,
                            "text": self.reply,
                        },
                    ),
                    (
                        "final",
                        "response.content_part.done",
                        {
                            "type": "response.content_part.done",
                            "sequence_number": 5 + sequence_offset,
                            "item_id": message_id,
                            "output_index": 0,
                            "content_index": 0,
                            "part": content_part | {"text": self.reply},
                        },
                    ),
                    (
                        "final",
                        "response.output_item.done",
                        {
                            "type": "response.output_item.done",
                            "sequence_number": 6 + sequence_offset,
                            "output_index": 0,
                            "item": output_item
                            | {
                                "status": "completed",
                                "content": [content_part | {"text": self.reply}],
                            },
                        },
                    ),
                    (
                        "final",
                        "response.completed",
                        {
                            "type": "response.completed",
                            "sequence_number": 7 + sequence_offset,
                            "response": self.response_object(context),
                        },
                    ),
                ]
            )
            self.send_staged_sse(events, context, request)
            return
        self.fixture_server.pause_stage(context, "final", request)
        self.send_json(self.response_object(context))

    def handle_anthropic_messages(
        self, request: dict[str, Any], stream: bool
    ) -> None:
        context = self.fixture_context(request, "anthropic")
        if self.maybe_fail(request, context):
            return
        message_id = context.stable_id("msg_linecode")
        call = self.requested_function_tool(request, "anthropic", context)
        staged = requests_fixture_reasoning(context)
        reasoning = fixture_reasoning(context)
        if call is not None:
            tool_block = {
                "type": "tool_use",
                "id": call["id"],
                "name": call["function"]["name"],
                "input": json.loads(call["function"]["arguments"]),
            }
            message = {
                "id": message_id,
                "type": "message",
                "role": "assistant",
                "model": MODEL_ID,
                "content": [tool_block],
                "stop_reason": "tool_use",
                "stop_sequence": None,
                "usage": {"input_tokens": 1, "output_tokens": 1},
            }
            if not stream:
                self.fixture_server.pause_stage(context, "tool_requested", request)
                self.send_json(message)
                return
            events: list[tuple[str, str | None, Any]] = [
                (
                    "reasoning" if staged else "tool_requested",
                    "message_start",
                    {
                        "type": "message_start",
                        "message": message | {"content": [], "stop_reason": None},
                    },
                )
            ]
            block_index = 0
            if staged:
                events.extend(
                    [
                        (
                            "reasoning",
                            "content_block_start",
                            {
                                "type": "content_block_start",
                                "index": 0,
                                "content_block": {"type": "thinking", "thinking": ""},
                            },
                        ),
                        (
                            "reasoning",
                            "content_block_delta",
                            {
                                "type": "content_block_delta",
                                "index": 0,
                                "delta": {
                                    "type": "thinking_delta",
                                    "thinking": reasoning,
                                },
                            },
                        ),
                        (
                            "reasoning",
                            "content_block_stop",
                            {"type": "content_block_stop", "index": 0},
                        ),
                    ]
                )
                block_index = 1
            events.extend(
                [
                    (
                        "tool_requested",
                        "content_block_start",
                        {
                            "type": "content_block_start",
                            "index": block_index,
                            "content_block": tool_block | {"input": {}},
                        },
                    ),
                    (
                        "tool_running",
                        "content_block_delta",
                        {
                            "type": "content_block_delta",
                            "index": block_index,
                            "delta": {
                                "type": "input_json_delta",
                                "partial_json": call["function"]["arguments"],
                            },
                        },
                    ),
                    (
                        "tool_running",
                        "content_block_stop",
                        {"type": "content_block_stop", "index": block_index},
                    ),
                    (
                        "final",
                        "message_delta",
                        {
                            "type": "message_delta",
                            "delta": {
                                "stop_reason": "tool_use",
                                "stop_sequence": None,
                            },
                            "usage": {"output_tokens": 1},
                        },
                    ),
                    ("final", "message_stop", {"type": "message_stop"}),
                ]
            )
            self.send_staged_sse(events, context, request)
            return
        message = {
            "id": message_id,
            "type": "message",
            "role": "assistant",
            "model": MODEL_ID,
            "content": [{"type": "text", "text": self.reply}],
            "stop_reason": "end_turn",
            "stop_sequence": None,
            "usage": {"input_tokens": 1, "output_tokens": 1},
        }
        if stream:
            events: list[tuple[str, str | None, Any]] = [
                    (
                        "reasoning" if staged else "text",
                        "message_start",
                        {
                            "type": "message_start",
                            "message": message | {"content": []},
                        },
                    )
            ]
            text_index = 0
            if staged:
                events.extend(
                    [
                        (
                            "reasoning",
                            "content_block_start",
                            {
                                "type": "content_block_start",
                                "index": 0,
                                "content_block": {"type": "thinking", "thinking": ""},
                            },
                        ),
                        (
                            "reasoning",
                            "content_block_delta",
                            {
                                "type": "content_block_delta",
                                "index": 0,
                                "delta": {
                                    "type": "thinking_delta",
                                    "thinking": reasoning,
                                },
                            },
                        ),
                        (
                            "reasoning",
                            "content_block_stop",
                            {"type": "content_block_stop", "index": 0},
                        ),
                    ]
                )
                text_index = 1
            events.extend(
                [
                    (
                        "text",
                        "content_block_start",
                        {
                            "type": "content_block_start",
                            "index": text_index,
                            "content_block": {"type": "text", "text": ""},
                        },
                    ),
                    (
                        "text",
                        "content_block_delta",
                        {
                            "type": "content_block_delta",
                            "index": text_index,
                            "delta": {"type": "text_delta", "text": self.reply},
                        },
                    ),
                    (
                        "final",
                        "content_block_stop",
                        {"type": "content_block_stop", "index": text_index},
                    ),
                    (
                        "final",
                        "message_delta",
                        {
                            "type": "message_delta",
                            "delta": {"stop_reason": "end_turn", "stop_sequence": None},
                            "usage": {"output_tokens": 1},
                        },
                    ),
                    ("final", "message_stop", {"type": "message_stop"}),
                ]
            )
            self.send_staged_sse(events, context, request)
            return
        self.fixture_server.pause_stage(context, "final", request)
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
    parser.add_argument(
        "--stage-delay",
        action="append",
        default=[],
        type=parse_stage_delay,
        metavar="STAGE=SECONDS",
        help=(
            "delay one streaming stage; repeat for reasoning, text, "
            "tool_requested, tool_running, or final"
        ),
    )
    parser.add_argument(
        "--gate-timeout",
        type=float,
        default=120.0,
        help="maximum seconds a forgotten fixture stage gate may block",
    )
    parser.add_argument(
        "--tls-cert",
        type=Path,
        help="PEM certificate used to serve the fixture over HTTPS",
    )
    parser.add_argument(
        "--tls-key",
        type=Path,
        help="PEM private key used to serve the fixture over HTTPS",
    )
    return parser.parse_args()


def parse_stage_delay(value: str) -> tuple[str, float]:
    stage, separator, raw_delay = value.partition("=")
    if not separator or stage not in FIXTURE_STAGES:
        raise argparse.ArgumentTypeError(
            "stage delay must be STAGE=SECONDS for a supported fixture stage"
        )
    try:
        delay = float(raw_delay)
    except ValueError as error:
        raise argparse.ArgumentTypeError("stage delay must be a number") from error
    if delay < 0:
        raise argparse.ArgumentTypeError("stage delay cannot be negative")
    return stage, delay


def exposure_warning(host: str) -> str | None:
    if host in {"0.0.0.0", "::", "[::]"}:
        return (
            "WARNING: wildcard binding exposes this unauthenticated test fixture; "
            "use it only on a trusted local network."
        )
    return None


def enable_tls(server: FixtureServer, certificate: Path, private_key: Path) -> None:
    """Wrap a fixture listener with TLS without changing its HTTP behavior."""
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(certfile=certificate, keyfile=private_key)
    server.socket = context.wrap_socket(server.socket, server_side=True)


def main() -> None:
    args = parse_args()
    if (args.tls_cert is None) != (args.tls_key is None):
        raise SystemExit("--tls-cert and --tls-key must be provided together")
    server = FixtureServer(
        (args.host, args.port), reply=args.reply, log_requests=not args.quiet,
        response_delay=args.response_delay,
        request_log=args.request_log,
        stage_delays=dict(args.stage_delay),
        gate_timeout=args.gate_timeout,
    )
    if args.tls_cert is not None and args.tls_key is not None:
        enable_tls(server, args.tls_cert, args.tls_key)

    def request_shutdown(signum: int, _frame: object) -> None:
        raise KeyboardInterrupt(f"received signal {signum}")

    for signal_name in ("SIGINT", "SIGTERM"):
        shutdown_signal = getattr(signal, signal_name, None)
        if shutdown_signal is not None:
            signal.signal(shutdown_signal, request_shutdown)

    host, port = server.server_address[:2]
    scheme = "https" if args.tls_cert is not None else "http"
    print(
        f"LineCode fixed-reply test fixture listening on {scheme}://{host}:{port}",
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
