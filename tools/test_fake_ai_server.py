#!/usr/bin/env python3
"""Protocol contract tests for the deterministic LineCode AI fixture."""

from __future__ import annotations

import http.client
import json
import ssl
import subprocess
import tempfile
import threading
import time
import unittest
from typing import Any

import fake_ai_server


CUSTOM_REPLY = "deterministic fixture reply"


class FakeAiServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.server = fake_ai_server.FixtureServer(
            ("127.0.0.1", 0),
            reply=CUSTOM_REPLY,
            log_requests=False,
            read_timeout=0.25,
        )
        cls.port = cls.server.server_address[1]
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls) -> None:
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=2)
        if cls.thread.is_alive():
            raise RuntimeError("fixture server did not stop cleanly")

    def request(
        self,
        method: str,
        path: str,
        body: Any = None,
        *,
        port: int | None = None,
        headers: dict[str, str] | None = None,
    ) -> tuple[int, dict[str, str], str]:
        connection = http.client.HTTPConnection(
            "127.0.0.1", self.port if port is None else port, timeout=2
        )
        if body is None:
            encoded = None
        elif isinstance(body, bytes):
            encoded = body
        else:
            encoded = json.dumps(body).encode("utf-8")
        request_headers = dict(headers or {})
        if encoded is not None:
            request_headers.setdefault("Content-Type", "application/json")
        connection.request(method, path, body=encoded, headers=request_headers)
        response = connection.getresponse()
        payload = response.read().decode("utf-8")
        response_headers = {key.lower(): value for key, value in response.getheaders()}
        status = response.status
        connection.close()
        return status, response_headers, payload

    def request_with_content_length(
        self, value: str | None, body: bytes = b""
    ) -> tuple[int, dict[str, str], str]:
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=2)
        connection.putrequest("POST", "/v1/chat/completions")
        connection.putheader("Content-Type", "application/json")
        if value is not None:
            connection.putheader("Content-Length", value)
        connection.endheaders(body)
        response = connection.getresponse()
        payload = response.read().decode("utf-8")
        response_headers = {key.lower(): item for key, item in response.getheaders()}
        status = response.status
        connection.close()
        return status, response_headers, payload

    @staticmethod
    def sse_events(payload: str) -> list[tuple[str | None, str]]:
        events: list[tuple[str | None, str]] = []
        for block in payload.strip().split("\n\n"):
            event_name: str | None = None
            data_lines: list[str] = []
            for line in block.splitlines():
                if line.startswith("event: "):
                    event_name = line.removeprefix("event: ")
                elif line.startswith("data: "):
                    data_lines.append(line.removeprefix("data: "))
            events.append((event_name, "\n".join(data_lines)))
        return events

    def test_health_and_model_catalog(self) -> None:
        status, headers, health = self.request("GET", "/healthz?probe=test")
        self.assertEqual(200, status)
        self.assertIn("application/json", headers["content-type"])
        self.assertEqual("fixed-reply", json.loads(health)["fixture"])

        status, _, models = self.request("GET", "/v1/models/")
        self.assertEqual(200, status)
        self.assertEqual(fake_ai_server.MODEL_ID, json.loads(models)["data"][0]["id"])

    def test_https_health_and_model_catalog_with_trusted_fixture_certificate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            certificate = fake_ai_server.Path(directory) / "fixture.crt"
            private_key = fake_ai_server.Path(directory) / "fixture.key"
            subprocess.run(
                [
                    "openssl",
                    "req",
                    "-x509",
                    "-newkey",
                    "rsa:2048",
                    "-nodes",
                    "-days",
                    "1",
                    "-subj",
                    "/CN=localhost",
                    "-addext",
                    "subjectAltName=DNS:localhost,IP:127.0.0.1",
                    "-keyout",
                    str(private_key),
                    "-out",
                    str(certificate),
                ],
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            server = fake_ai_server.FixtureServer(
                ("127.0.0.1", 0), log_requests=False
            )
            fake_ai_server.enable_tls(server, certificate, private_key)
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                context = ssl.create_default_context(cafile=str(certificate))
                connection = http.client.HTTPSConnection(
                    "localhost", server.server_address[1], context=context, timeout=2
                )
                connection.request("GET", "/v1/models")
                response = connection.getresponse()
                payload = json.loads(response.read().decode("utf-8"))
                self.assertEqual(200, response.status)
                self.assertEqual(fake_ai_server.MODEL_ID, payload["data"][0]["id"])
                self.assertGreaterEqual(
                    connection.sock.version().removeprefix("TLSv"), "1.2"
                )
                connection.close()
            finally:
                server.shutdown()
                server.server_close()
                thread.join(timeout=2)
                self.assertFalse(thread.is_alive())

    def test_openai_chat_non_streaming(self) -> None:
        status, _, body = self.request(
            "POST", "/v1/chat/completions", {"model": "ignored", "stream": False}
        )
        self.assertEqual(200, status)
        decoded = json.loads(body)
        self.assertEqual(CUSTOM_REPLY, decoded["choices"][0]["message"]["content"])
        self.assertEqual("stop", decoded["choices"][0]["finish_reason"])

    def test_openai_chat_streaming(self) -> None:
        status, headers, body = self.request(
            "POST", "/v1/chat/completions", {"stream": True}
        )
        self.assertEqual(200, status)
        self.assertIn("text/event-stream", headers["content-type"])
        events = self.sse_events(body)
        self.assertEqual("[DONE]", events[-1][1])
        chunks = [json.loads(data) for _, data in events[:-1]]
        self.assertEqual("assistant", chunks[0]["choices"][0]["delta"]["role"])
        self.assertEqual(CUSTOM_REPLY, chunks[1]["choices"][0]["delta"]["content"])
        self.assertEqual("stop", chunks[2]["choices"][0]["finish_reason"])

    def test_legacy_application_context_does_not_hide_latest_human_turn(self) -> None:
        request = {
            "messages": [
                {"role": "user", "content": fake_ai_server.TOOL_FLOW_TRIGGER},
                {
                    "role": "user",
                    "content": (
                        "[Application context]\n"
                        "This is background state, not a new user request."
                    ),
                },
            ]
        }
        turn, index, content, rounds = fake_ai_server.request_turn(request, "chat")
        self.assertEqual(1, turn)
        self.assertEqual(0, index)
        self.assertEqual(fake_ai_server.TOOL_FLOW_TRIGGER, content)
        self.assertEqual(0, rounds)

    def test_openai_shell_tool_fixture_is_explicit_and_one_shot(self) -> None:
        tool = {
            "type": "function",
            "function": {"name": "shell_execute", "parameters": {}},
        }
        request = {
            "model": "ignored",
            "stream": True,
            "messages": [
                {"role": "user", "content": fake_ai_server.SHELL_TOOL_TRIGGER}
            ],
            "tools": [tool],
        }
        status, _, body = self.request("POST", "/v1/chat/completions", request)
        self.assertEqual(200, status)
        chunks = [
            json.loads(data)
            for _, data in self.sse_events(body)
            if data != "[DONE]"
        ]
        call = chunks[0]["choices"][0]["delta"]["tool_calls"][0]
        self.assertEqual("shell_execute", call["function"]["name"])
        self.assertEqual(
            fake_ai_server.SHELL_TOOL_COMMAND,
            json.loads(call["function"]["arguments"])["command"],
        )
        self.assertEqual("tool_calls", chunks[1]["choices"][0]["finish_reason"])

        request["messages"].extend(
            [
                {"role": "assistant", "content": None, "tool_calls": [call]},
                {
                    "role": "tool",
                    "tool_call_id": call["id"],
                    "content": "linecode-tool-ok",
                },
            ]
        )
        status, _, body = self.request("POST", "/v1/chat/completions", request)
        self.assertEqual(200, status)
        chunks = [
            json.loads(data)
            for _, data in self.sse_events(body)
            if data != "[DONE]"
        ]
        self.assertEqual(
            CUSTOM_REPLY, chunks[1]["choices"][0]["delta"]["content"]
        )

    def test_delete_tool_fixture_is_safe_and_requires_file_delete(self) -> None:
        request = {
            "model": "ignored",
            "stream": False,
            "messages": [
                {"role": "user", "content": fake_ai_server.DELETE_TOOL_TRIGGER}
            ],
            "tools": [
                {
                    "type": "function",
                    "function": {"name": "file_delete", "parameters": {}},
                }
            ],
        }
        status, _, body = self.request("POST", "/v1/chat/completions", request)
        self.assertEqual(200, status)
        call = json.loads(body)["choices"][0]["message"]["tool_calls"][0]
        self.assertEqual("file_delete", call["function"]["name"])
        arguments = json.loads(call["function"]["arguments"])
        self.assertEqual([fake_ai_server.DELETE_TOOL_PATH], arguments["paths"])
        self.assertTrue(arguments["reason"])

    def test_delayed_ordinary_tool_flow_is_deterministic_and_turn_scoped(self) -> None:
        delay = 0.06
        delayed_server = fake_ai_server.FixtureServer(
            ("127.0.0.1", 0),
            reply=CUSTOM_REPLY,
            log_requests=False,
            read_timeout=0.25,
            response_delay=delay,
        )
        port = delayed_server.server_address[1]
        thread = threading.Thread(target=delayed_server.serve_forever, daemon=True)
        thread.start()
        try:
            tool = {
                "type": "function",
                "function": {"name": "file_write", "parameters": {}},
            }
            request = {
                "model": "ignored",
                "stream": True,
                "messages": [
                    {
                        "role": "user",
                        "content": fake_ai_server.TOOL_FLOW_TRIGGER,
                    }
                ],
                "tools": [tool],
            }

            started = time.monotonic()
            status, _, body = self.request(
                "POST", "/v1/chat/completions", request, port=port
            )
            self.assertGreaterEqual(time.monotonic() - started, delay - 0.01)
            self.assertEqual(200, status)
            chunks = [
                json.loads(data)
                for _, data in self.sse_events(body)
                if data != "[DONE]"
            ]
            call = chunks[0]["choices"][0]["delta"]["tool_calls"][0]
            self.assertEqual("file_write", call["function"]["name"])
            self.assertEqual(
                {
                    "file_path": fake_ai_server.TOOL_FLOW_PATH,
                    "content": fake_ai_server.TOOL_FLOW_CONTENT,
                },
                json.loads(call["function"]["arguments"]),
            )
            self.assertEqual(
                "tool_calls", chunks[1]["choices"][0]["finish_reason"]
            )

            request["messages"].extend(
                [
                    {"role": "assistant", "content": None, "tool_calls": [call]},
                    {
                        "role": "tool",
                        "tool_call_id": call["id"],
                        "content": "fixture file written",
                    },
                ]
            )
            started = time.monotonic()
            status, _, body = self.request(
                "POST", "/v1/chat/completions", request, port=port
            )
            self.assertGreaterEqual(time.monotonic() - started, delay - 0.01)
            self.assertEqual(200, status)
            chunks = [
                json.loads(data)
                for _, data in self.sse_events(body)
                if data != "[DONE]"
            ]
            self.assertEqual(
                CUSTOM_REPLY, chunks[1]["choices"][0]["delta"]["content"]
            )

            # The old trigger must not leak into an ordinary later turn.
            request["messages"].extend(
                [
                    {"role": "assistant", "content": CUSTOM_REPLY},
                    {"role": "user", "content": "ordinary follow-up"},
                ]
            )
            status, _, body = self.request(
                "POST", "/v1/chat/completions", request, port=port
            )
            self.assertEqual(200, status)
            chunks = [
                json.loads(data)
                for _, data in self.sse_events(body)
                if data != "[DONE]"
            ]
            self.assertEqual(
                CUSTOM_REPLY, chunks[1]["choices"][0]["delta"]["content"]
            )

            # A fresh explicit trigger in the same conversation starts a new
            # one-shot tool round despite earlier tool results.
            request["messages"].extend(
                [
                    {"role": "assistant", "content": CUSTOM_REPLY},
                    {
                        "role": "user",
                        "content": fake_ai_server.TOOL_FLOW_TRIGGER,
                    },
                ]
            )
            status, _, body = self.request(
                "POST", "/v1/chat/completions", request, port=port
            )
            self.assertEqual(200, status)
            chunks = [
                json.loads(data)
                for _, data in self.sse_events(body)
                if data != "[DONE]"
            ]
            repeated_call = chunks[0]["choices"][0]["delta"]["tool_calls"][0]
            self.assertEqual("file_write", repeated_call["function"]["name"])
        finally:
            delayed_server.shutdown()
            delayed_server.server_close()
            thread.join(timeout=2)
            self.assertFalse(thread.is_alive(), "delayed fixture did not stop")

    def test_failure_budget_isolated_by_session_and_turn_and_resettable(self) -> None:
        request = {
            "messages": [
                {"role": "user", "content": fake_ai_server.FAIL_TOOL_TRIGGER}
            ]
        }

        def completion(session: str, body: dict[str, Any] = request) -> int:
            status, _, _ = self.request(
                "POST",
                "/v1/chat/completions",
                body,
                headers={fake_ai_server.FIXTURE_SESSION_HEADER: session},
            )
            return status

        self.assertEqual([500, 500, 200], [completion("run-a") for _ in range(3)])
        self.assertEqual(500, completion("run-b"))

        # A historical failure trigger cannot affect a newer ordinary turn.
        ordinary_follow_up = {
            "messages": request["messages"]
            + [
                {"role": "assistant", "content": "retry finished"},
                {"role": "user", "content": "ordinary follow-up"},
            ]
        }
        self.assertEqual(200, completion("run-a", ordinary_follow_up))

        status, _, body = self.request(
            "POST", "/reset", {"session": "run-a"}
        )
        self.assertEqual(200, status)
        self.assertEqual("reset", json.loads(body)["status"])
        self.assertEqual(500, completion("run-a"))
        # Resetting run-a must not alter run-b's independent second attempt.
        self.assertEqual(500, completion("run-b"))

        status, _, body = self.request(
            "POST", "/fixture/state", {"session": "run-a"}
        )
        self.assertEqual(200, status)
        state = json.loads(body)
        self.assertEqual(1, state["failure_attempts"])
        self.assertEqual({"1": 1}, state["failure_attempts_by_turn"])
        self.assertEqual(1, state["protocol_request_count"])
        self.assertEqual({"chat": 1}, state["protocol_request_counts"])

    def test_state_counts_completion_requests_by_protocol_and_reset(self) -> None:
        session = "protocol-state"
        headers = {fake_ai_server.FIXTURE_SESSION_HEADER: session}
        requests = (
            (
                "/v1/chat/completions",
                {"messages": [{"role": "user", "content": "chat"}]},
            ),
            (
                "/v1/responses",
                {
                    "input": [
                        {"type": "message", "role": "user", "content": "responses"}
                    ]
                },
            ),
            (
                "/v1/messages",
                {"messages": [{"role": "user", "content": "anthropic"}]},
            ),
        )
        for path, body in requests:
            status, _, _ = self.request("POST", path, body, headers=headers)
            self.assertEqual(200, status)

        status, _, body = self.request("POST", "/state", {"session": session})
        self.assertEqual(200, status)
        state = json.loads(body)
        self.assertEqual(3, state["protocol_request_count"])
        self.assertEqual(
            {"anthropic": 1, "chat": 1, "responses": 1},
            state["protocol_request_counts"],
        )
        self.assertEqual({}, state["protocol_tool_result_counts"])
        self.assertEqual({}, state["protocol_empty_tool_result_counts"])

        result_requests = (
            (
                "/v1/chat/completions",
                {"messages": [{"role": "tool", "content": "chat result"}]},
            ),
            (
                "/v1/responses",
                {"input": [{"type": "function_call_output", "output": ""}]},
            ),
            (
                "/v1/messages",
                {"messages": [{"role": "user", "content": [{"type": "tool_result", "content": "anthropic result"}]}]},
            ),
        )
        for path, request_body in result_requests:
            status, _, _ = self.request("POST", path, request_body, headers=headers)
            self.assertEqual(200, status)

        status, _, body = self.request("POST", "/state", {"session": session})
        self.assertEqual(200, status)
        state = json.loads(body)
        self.assertEqual(
            {"anthropic": 1, "chat": 1, "responses": 1},
            state["protocol_tool_result_counts"],
        )
        self.assertEqual({"responses": 1}, state["protocol_empty_tool_result_counts"])

        status, _, _ = self.request("POST", "/reset", {"session": session})
        self.assertEqual(200, status)
        status, _, body = self.request("POST", "/state", {"session": session})
        self.assertEqual(200, status)
        self.assertEqual({}, json.loads(body)["protocol_request_counts"])
        self.assertEqual({}, json.loads(body)["protocol_tool_result_counts"])
        self.assertEqual({}, json.loads(body)["protocol_empty_tool_result_counts"])

    def test_always_fail_trigger_never_exhausts_its_failure_budget(self) -> None:
        request = {
            "messages": [
                {
                    "role": "user",
                    "content": fake_ai_server.ALWAYS_FAIL_TRIGGER,
                }
            ]
        }
        statuses = [
            self.request(
                "POST",
                "/v1/chat/completions",
                request,
                headers={fake_ai_server.FIXTURE_SESSION_HEADER: "always-fail"},
            )[0]
            for _ in range(5)
        ]
        self.assertEqual([500] * 5, statuses)
        status, _, body = self.request(
            "POST", "/state", {"session": "always-fail"}
        )
        self.assertEqual(200, status)
        self.assertEqual(5, json.loads(body)["failure_attempts"])

    def test_call_and_response_ids_are_deterministic_per_session_turn_and_round(self) -> None:
        tool = {
            "type": "function",
            "function": {"name": "file_write", "parameters": {}},
        }
        request = {
            "messages": [
                {"role": "user", "content": fake_ai_server.TOOL_FLOW_TRIGGER}
            ],
            "tools": [tool],
        }

        def invoke(session: str, body: dict[str, Any]) -> dict[str, Any]:
            status, _, payload = self.request(
                "POST",
                "/v1/chat/completions",
                body,
                headers={fake_ai_server.FIXTURE_SESSION_HEADER: session},
            )
            self.assertEqual(200, status)
            return json.loads(payload)

        first = invoke("stable-session", request)
        repeated = invoke("stable-session", request)
        self.assertEqual(first["id"], repeated["id"])
        first_call = first["choices"][0]["message"]["tool_calls"][0]
        repeated_call = repeated["choices"][0]["message"]["tool_calls"][0]
        self.assertEqual(first_call["id"], repeated_call["id"])

        other_session = invoke("other-session", request)
        other_call = other_session["choices"][0]["message"]["tool_calls"][0]
        self.assertNotEqual(first["id"], other_session["id"])
        self.assertNotEqual(first_call["id"], other_call["id"])

        second_turn_request = {
            "messages": request["messages"]
            + [
                {"role": "assistant", "content": None, "tool_calls": [first_call]},
                {
                    "role": "tool",
                    "tool_call_id": first_call["id"],
                    "content": "written",
                },
                {"role": "assistant", "content": CUSTOM_REPLY},
                {"role": "user", "content": fake_ai_server.TOOL_FLOW_TRIGGER},
            ],
            "tools": [tool],
        }
        second_turn = invoke("stable-session", second_turn_request)
        second_call = second_turn["choices"][0]["message"]["tool_calls"][0]
        self.assertNotEqual(first["id"], second_turn["id"])
        self.assertNotEqual(first_call["id"], second_call["id"])

    def test_three_and_fourteen_tool_sequences_finish_exactly(self) -> None:
        tools = [
            {
                "type": "function",
                "function": {"name": name, "parameters": {}},
            }
            for name in ("list_dir", "file_read", "todo_update")
        ]

        def run(trigger: str, expected_names: list[str]) -> list[str]:
            request: dict[str, Any] = {
                "messages": [{"role": "user", "content": trigger}],
                "tools": tools,
            }
            names: list[str] = []
            call_ids: list[str] = []
            for _ in range(len(expected_names) + 1):
                status, _, payload = self.request(
                    "POST",
                    "/v1/chat/completions",
                    request,
                    headers={fake_ai_server.FIXTURE_SESSION_HEADER: trigger},
                )
                self.assertEqual(200, status)
                decoded = json.loads(payload)
                message = decoded["choices"][0]["message"]
                calls = message.get("tool_calls", [])
                if not calls:
                    self.assertEqual(CUSTOM_REPLY, message["content"])
                    break
                call = calls[0]
                names.append(call["function"]["name"])
                call_ids.append(call["id"])
                request["messages"].extend(
                    [
                        {"role": "assistant", "content": None, "tool_calls": [call]},
                        {
                            "role": "tool",
                            "tool_call_id": call["id"],
                            "content": "fixture result",
                        },
                    ]
                )
            self.assertEqual(expected_names, names)
            self.assertEqual(len(call_ids), len(set(call_ids)))
            return names

        run(fake_ai_server.THREE_TOOL_TRIGGER, ["list_dir", "file_read", "todo_update"])
        run(
            fake_ai_server.LOOP_TOOL_TRIGGER,
            ["list_dir"] * fake_ai_server.LOOP_TOOL_ROUNDS,
        )

    def test_stage_delay_gate_release_and_reasoning_stream(self) -> None:
        session = "staged-chat"
        status, _, _ = self.request(
            "POST", "/fixture/gate", {"session": session, "stage": "text"}
        )
        self.assertEqual(200, status)
        request = {
            "stream": True,
            "messages": [
                {"role": "user", "content": fake_ai_server.STAGED_FLOW_TRIGGER}
            ],
            "fixture": {"stage_delays": {"final": 0.04}},
        }
        result: list[tuple[int, dict[str, str], str]] = []
        worker = threading.Thread(
            target=lambda: result.append(
                self.request(
                    "POST",
                    "/v1/chat/completions",
                    request,
                    headers={fake_ai_server.FIXTURE_SESSION_HEADER: session},
                )
            )
        )
        started = time.monotonic()
        worker.start()
        time.sleep(0.05)
        self.assertTrue(worker.is_alive(), "text gate did not hold the stream")
        status, _, body = self.request(
            "POST", "/release", {"session": session, "stages": ["text"]}
        )
        self.assertEqual(200, status)
        self.assertEqual("released", json.loads(body)["status"])
        worker.join(timeout=2)
        self.assertFalse(worker.is_alive(), "released stream did not finish")
        self.assertGreaterEqual(time.monotonic() - started, 0.08)
        self.assertEqual(200, result[0][0])
        chunks = [
            json.loads(data)
            for _, data in self.sse_events(result[0][2])
            if data != "[DONE]"
        ]
        self.assertEqual(
            fake_ai_server.FIXTURE_REASONING,
            chunks[0]["choices"][0]["delta"]["reasoning_content"],
        )
        self.assertEqual(CUSTOM_REPLY, chunks[2]["choices"][0]["delta"]["content"])

    def test_long_reasoning_fixture_preserves_all_sixteen_lines(self) -> None:
        status, _, body = self.request(
            "POST",
            "/v1/chat/completions",
            {
                "stream": True,
                "messages": [
                    {
                        "role": "user",
                        "content": fake_ai_server.LONG_REASONING_TRIGGER,
                    }
                ],
            },
        )
        self.assertEqual(200, status)
        chunks = [
            json.loads(data)
            for _, data in self.sse_events(body)
            if data != "[DONE]"
        ]
        reasoning_chunks = [
            chunk["choices"][0]["delta"]["reasoning_content"]
            for chunk in chunks
            if chunk.get("choices")
            and "reasoning_content" in chunk["choices"][0].get("delta", {})
        ]
        self.assertEqual(16, len(reasoning_chunks))
        reasoning = "".join(reasoning_chunks)
        self.assertEqual(fake_ai_server.LONG_FIXTURE_REASONING, reasoning)
        self.assertEqual(16, len(reasoning.splitlines()))
        text = "".join(
            chunk["choices"][0]["delta"].get("content", "")
            for chunk in chunks
            if chunk.get("choices")
        )
        self.assertEqual(CUSTOM_REPLY, text)

    def test_responses_tool_round_trip_is_turn_scoped_and_staged(self) -> None:
        tool = {"type": "function", "name": "list_dir", "parameters": {}}
        request: dict[str, Any] = {
            "stream": True,
            "input": [
                {
                    "type": "message",
                    "role": "user",
                    "content": [
                        {
                            "type": "input_text",
                            "text": fake_ai_server.LOOP_TOOL_TRIGGER
                            + " "
                            + fake_ai_server.STAGED_FLOW_TRIGGER,
                        }
                    ],
                }
            ],
            "tools": [tool],
            "fixture_session": "responses-run",
        }
        status, _, body = self.request("POST", "/v1/responses", request)
        self.assertEqual(200, status)
        events = [(name, json.loads(data)) for name, data in self.sse_events(body)]
        names = [name for name, _ in events]
        self.assertIn("response.reasoning_summary_text.delta", names)
        self.assertIn("response.function_call_arguments.delta", names)
        completed = events[-1][1]["response"]
        call = completed["output"][0]
        self.assertEqual("list_dir", call["name"])

        request["stream"] = False
        request["input"].extend(
            [
                call,
                {
                    "type": "function_call_output",
                    "call_id": call["call_id"],
                    "output": "fixture result",
                },
            ]
        )
        status, _, body = self.request("POST", "/v1/responses", request)
        self.assertEqual(200, status)
        second = json.loads(body)["output"][0]
        self.assertEqual("function_call", second["type"])
        self.assertNotEqual(call["call_id"], second["call_id"])

        # An ordinary newer user input suppresses the historical trigger.
        request["input"].append(
            {
                "type": "message",
                "role": "user",
                "content": [{"type": "input_text", "text": "ordinary"}],
            }
        )
        status, _, body = self.request("POST", "/v1/responses", request)
        self.assertEqual(200, status)
        self.assertEqual("message", json.loads(body)["output"][0]["type"])

    def test_anthropic_tool_round_trip_is_turn_scoped_and_staged(self) -> None:
        request: dict[str, Any] = {
            "stream": True,
            "messages": [
                {
                    "role": "user",
                    "content": fake_ai_server.TOOL_FLOW_TRIGGER
                    + " "
                    + fake_ai_server.STAGED_FLOW_TRIGGER,
                }
            ],
            "tools": [{"name": "file_write", "input_schema": {}}],
            "fixture_session": "anthropic-run",
        }
        status, _, body = self.request("POST", "/v1/messages", request)
        self.assertEqual(200, status)
        events = [json.loads(data) for _, data in self.sse_events(body)]
        thinking = next(
            event
            for event in events
            if event.get("delta", {}).get("type") == "thinking_delta"
        )
        self.assertEqual(fake_ai_server.FIXTURE_REASONING, thinking["delta"]["thinking"])
        tool_start = next(
            event
            for event in events
            if event.get("content_block", {}).get("type") == "tool_use"
        )
        call = tool_start["content_block"]
        self.assertEqual("file_write", call["name"])

        request["stream"] = False
        request["messages"].extend(
            [
                {"role": "assistant", "content": [call]},
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "tool_result",
                            "tool_use_id": call["id"],
                            "content": "written",
                        }
                    ],
                },
            ]
        )
        status, _, body = self.request("POST", "/v1/messages", request)
        self.assertEqual(200, status)
        self.assertEqual(CUSTOM_REPLY, json.loads(body)["content"][0]["text"])

        request["messages"].extend(
            [
                {"role": "assistant", "content": [{"type": "text", "text": CUSTOM_REPLY}]},
                {"role": "user", "content": "ordinary follow-up"},
            ]
        )
        status, _, body = self.request("POST", "/v1/messages", request)
        self.assertEqual(200, status)
        self.assertEqual(CUSTOM_REPLY, json.loads(body)["content"][0]["text"])

    def test_agent_and_agent_pipeline_fixtures_are_explicit_and_one_shot(self) -> None:
        fixtures = (
            (
                fake_ai_server.AGENT_TOOL_TRIGGER,
                "agent",
                {
                    "type": "explore",
                    "description": "Inspect the workspace",
                    "prompt": "List the files and report what you find.",
                    "read_scope": ["."],
                },
            ),
            (
                fake_ai_server.AGENT_PIPELINE_TOOL_TRIGGER,
                "agent_pipeline",
                {"agents": list(fake_ai_server.AGENT_PIPELINE_TASKS)},
            ),
            (
                fake_ai_server.AGENT_FAIL_TOOL_TRIGGER,
                "agent",
                {
                    "type": "explore",
                    "description": "Fail the inspection",
                    "prompt": fake_ai_server.AGENT_FAILURE_PROMPT,
                    "read_scope": ["."],
                },
            ),
            (
                fake_ai_server.AGENT_PIPELINE_FAIL_TOOL_TRIGGER,
                "agent_pipeline",
                {"agents": list(fake_ai_server.AGENT_PIPELINE_FAIL_TASKS)},
            ),
            (
                fake_ai_server.AGENT_NESTED_TOOL_TRIGGER,
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
                fake_ai_server.AGENT_PIPELINE_PARALLEL_TOOL_TRIGGER,
                "agent_pipeline",
                {"agents": list(fake_ai_server.AGENT_PIPELINE_PARALLEL_TASKS)},
            ),
        )
        for trigger, tool_name, expected_arguments in fixtures:
            with self.subTest(tool_name=tool_name):
                request = {
                    "model": "ignored",
                    "stream": True,
                    "messages": [{"role": "user", "content": trigger}],
                    "tools": [
                        {
                            "type": "function",
                            "function": {"name": tool_name, "parameters": {}},
                        }
                    ],
                }
                status, _, body = self.request(
                    "POST", "/v1/chat/completions", request
                )
                self.assertEqual(200, status)
                chunks = [
                    json.loads(data)
                    for _, data in self.sse_events(body)
                    if data != "[DONE]"
                ]
                call = chunks[0]["choices"][0]["delta"]["tool_calls"][0]
                self.assertEqual(tool_name, call["function"]["name"])
                self.assertEqual(
                    expected_arguments, json.loads(call["function"]["arguments"])
                )
                self.assertEqual(
                    "tool_calls", chunks[1]["choices"][0]["finish_reason"]
                )

                request["messages"].extend(
                    [
                        {"role": "assistant", "content": None, "tool_calls": [call]},
                        {
                            "role": "tool",
                            "tool_call_id": call["id"],
                            "content": "fixture tool completed",
                        },
                    ]
                )
                status, _, body = self.request(
                    "POST", "/v1/chat/completions", request
                )
                self.assertEqual(200, status)
                chunks = [
                    json.loads(data)
                    for _, data in self.sse_events(body)
                    if data != "[DONE]"
                ]
                self.assertEqual(
                    CUSTOM_REPLY, chunks[1]["choices"][0]["delta"]["content"]
                )

    def test_openai_image_tool_fixture_is_explicit_and_one_shot(self) -> None:
        tool = {
            "type": "function",
            "function": {"name": "image_generation", "parameters": {}},
        }
        request = {
            "model": "ignored",
            "stream": True,
            "messages": [
                {"role": "user", "content": fake_ai_server.IMAGE_TOOL_TRIGGER}
            ],
            "tools": [tool],
        }
        status, _, body = self.request("POST", "/v1/chat/completions", request)
        self.assertEqual(200, status)
        chunks = [
            json.loads(data)
            for _, data in self.sse_events(body)
            if data != "[DONE]"
        ]
        call = chunks[0]["choices"][0]["delta"]["tool_calls"][0]
        self.assertEqual("image_generation", call["function"]["name"])
        self.assertEqual(
            fake_ai_server.IMAGE_TOOL_PROMPT,
            json.loads(call["function"]["arguments"])["prompt"],
        )
        request["messages"].extend(
            [
                {"role": "assistant", "content": None, "tool_calls": [call]},
                {
                    "role": "tool",
                    "tool_call_id": call["id"],
                    "content": "image generated",
                },
            ]
        )
        status, _, body = self.request("POST", "/v1/chat/completions", request)
        self.assertEqual(200, status)
        chunks = [
            json.loads(data)
            for _, data in self.sse_events(body)
            if data != "[DONE]"
        ]
        self.assertEqual(CUSTOM_REPLY, chunks[1]["choices"][0]["delta"]["content"])

    def test_openai_image_understanding_fixture_is_explicit_and_one_shot(self) -> None:
        tool = {
            "type": "function",
            "function": {"name": "image_understanding", "parameters": {}},
        }
        request = {
            "model": "ignored",
            "stream": True,
            "messages": [
                {
                    "role": "user",
                    "content": fake_ai_server.IMAGE_UNDERSTANDING_TRIGGER,
                }
            ],
            "tools": [tool],
        }
        status, _, body = self.request("POST", "/v1/chat/completions", request)
        self.assertEqual(200, status)
        chunks = [
            json.loads(data)
            for _, data in self.sse_events(body)
            if data != "[DONE]"
        ]
        call = chunks[0]["choices"][0]["delta"]["tool_calls"][0]
        self.assertEqual("image_understanding", call["function"]["name"])
        arguments = json.loads(call["function"]["arguments"])
        self.assertEqual(fake_ai_server.IMAGE_UNDERSTANDING_PATH, arguments["path"])
        self.assertEqual(
            fake_ai_server.IMAGE_UNDERSTANDING_PROMPT, arguments["prompt"]
        )

        request["messages"].extend(
            [
                {"role": "assistant", "content": None, "tool_calls": [call]},
                {
                    "role": "tool",
                    "tool_call_id": call["id"],
                    "content": "fixture image understood",
                },
            ]
        )
        status, _, body = self.request("POST", "/v1/chat/completions", request)
        self.assertEqual(200, status)
        chunks = [
            json.loads(data)
            for _, data in self.sse_events(body)
            if data != "[DONE]"
        ]
        self.assertEqual(CUSTOM_REPLY, chunks[1]["choices"][0]["delta"]["content"])

    def test_responses_non_streaming(self) -> None:
        status, _, body = self.request("POST", "/v1/responses", {})
        self.assertEqual(200, status)
        decoded = json.loads(body)
        self.assertEqual("completed", decoded["status"])
        self.assertEqual(CUSTOM_REPLY, decoded["output"][0]["content"][0]["text"])

    def test_openai_and_codex_image_generation(self) -> None:
        status, _, body = self.request(
            "POST",
            "/v1/images/generations",
            {"model": fake_ai_server.MODEL_ID, "prompt": "LineCode icon"},
        )
        self.assertEqual(200, status)
        decoded = json.loads(body)
        self.assertEqual(
            fake_ai_server.FIXTURE_IMAGE_BASE64,
            decoded["data"][0]["b64_json"],
        )
        self.assertEqual("LineCode icon", decoded["data"][0]["revised_prompt"])

        status, _, body = self.request(
            "POST",
            "/v1/responses",
            {
                "model": fake_ai_server.MODEL_ID,
                "input": "LineCode icon",
                "tools": [{"type": "image_generation", "action": "generate"}],
            },
        )
        self.assertEqual(200, status)
        decoded = json.loads(body)
        self.assertEqual("image_generation_call", decoded["output"][0]["type"])
        self.assertEqual(
            fake_ai_server.FIXTURE_IMAGE_BASE64,
            decoded["output"][0]["result"],
        )

    def test_responses_streaming_event_sequence(self) -> None:
        status, headers, body = self.request("POST", "/v1/responses", {"stream": True})
        self.assertEqual(200, status)
        self.assertIn("text/event-stream", headers["content-type"])
        events = self.sse_events(body)
        names = [event_name for event_name, _ in events]
        self.assertEqual(
            [
                "response.created",
                "response.output_item.added",
                "response.content_part.added",
                "response.output_text.delta",
                "response.output_text.done",
                "response.content_part.done",
                "response.output_item.done",
                "response.completed",
            ],
            names,
        )
        decoded = [json.loads(data) for _, data in events]
        self.assertEqual(CUSTOM_REPLY, decoded[3]["delta"])
        self.assertEqual("completed", decoded[-1]["response"]["status"])

    def test_anthropic_compatibility(self) -> None:
        status, _, body = self.request("POST", "/anthropic/v1/messages", {})
        self.assertEqual(200, status)
        self.assertEqual(CUSTOM_REPLY, json.loads(body)["content"][0]["text"])

    def test_anthropic_streaming_event_sequence(self) -> None:
        status, headers, body = self.request(
            "POST", "/v1/messages", {"stream": True}
        )
        self.assertEqual(200, status)
        self.assertIn("text/event-stream", headers["content-type"])
        events = self.sse_events(body)
        decoded = [json.loads(data) for _, data in events]
        self.assertEqual(
            [
                "message_start",
                "content_block_start",
                "content_block_delta",
                "content_block_stop",
                "message_delta",
                "message_stop",
            ],
            [event["type"] for event in decoded],
        )
        self.assertEqual(CUSTOM_REPLY, decoded[2]["delta"]["text"])

    def test_errors_are_json(self) -> None:
        status, _, body = self.request("POST", "/v1/chat/completions", b"[")
        self.assertEqual(400, status)
        self.assertEqual("invalid_request_error", json.loads(body)["error"]["type"])

        status, _, body = self.request("GET", "/missing")
        self.assertEqual(404, status)
        self.assertEqual("route not found", json.loads(body)["error"]["message"])

    def test_content_length_validation_and_body_limit(self) -> None:
        cases = [
            (None, 411),
            ("not-a-number", 400),
            ("-1", 400),
            (str(fake_ai_server.MAX_REQUEST_BODY_BYTES + 1), 413),
        ]
        for content_length, expected_status in cases:
            with self.subTest(content_length=content_length):
                status, headers, body = self.request_with_content_length(content_length)
                self.assertEqual(expected_status, status)
                self.assertEqual("close", headers["connection"])
                self.assertEqual(
                    "invalid_request_error",
                    json.loads(body)["error"]["type"],
                )

    def test_partial_request_body_times_out_and_closes_connection(self) -> None:
        status, headers, body = self.request_with_content_length("2", b"{")
        self.assertEqual(408, status)
        self.assertEqual("close", headers["connection"])
        self.assertIn("timed out", json.loads(body)["error"]["message"])

    def test_wildcard_warning_without_binding_publicly(self) -> None:
        warning = fake_ai_server.exposure_warning("0.0.0.0")
        self.assertIsNotNone(warning)
        self.assertIn("trusted local network", warning or "")
        self.assertIsNone(fake_ai_server.exposure_warning("127.0.0.1"))


if __name__ == "__main__":
    unittest.main()
