#!/usr/bin/env python3
"""Protocol contract tests for the deterministic LineCode AI fixture."""

from __future__ import annotations

import http.client
import json
import threading
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
        self, method: str, path: str, body: Any = None
    ) -> tuple[int, dict[str, str], str]:
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=2)
        if body is None:
            encoded = None
        elif isinstance(body, bytes):
            encoded = body
        else:
            encoded = json.dumps(body).encode("utf-8")
        headers = {} if encoded is None else {"Content-Type": "application/json"}
        connection.request(method, path, body=encoded, headers=headers)
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

    def test_responses_non_streaming(self) -> None:
        status, _, body = self.request("POST", "/v1/responses", {})
        self.assertEqual(200, status)
        decoded = json.loads(body)
        self.assertEqual("completed", decoded["status"])
        self.assertEqual(CUSTOM_REPLY, decoded["output"][0]["content"][0]["text"])

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
