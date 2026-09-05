#!/usr/bin/env python3
"""Contract tests for the deterministic LineCode AI fixture."""

from __future__ import annotations

import http.client
import json
import threading
import unittest

import fake_ai_server


class FakeAiServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.server = fake_ai_server.ThreadingHTTPServer(
            ("127.0.0.1", 0), fake_ai_server.FakeAiHandler
        )
        cls.server.reply = fake_ai_server.DEFAULT_REPLY
        cls.port = cls.server.server_address[1]
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls) -> None:
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join(timeout=2)

    def request(self, method: str, path: str, body: dict | None = None) -> tuple[int, str]:
        connection = http.client.HTTPConnection("127.0.0.1", self.port, timeout=2)
        encoded = None if body is None else json.dumps(body).encode()
        headers = {} if encoded is None else {"Content-Type": "application/json"}
        connection.request(method, path, body=encoded, headers=headers)
        response = connection.getresponse()
        payload = response.read().decode("utf-8")
        connection.close()
        return response.status, payload

    def test_health_and_model_catalog(self) -> None:
        status, health = self.request("GET", "/health")
        self.assertEqual(200, status)
        self.assertEqual("ok", json.loads(health)["status"])
        _, models = self.request("GET", "/v1/models")
        self.assertEqual(fake_ai_server.MODEL_ID, json.loads(models)["data"][0]["id"])

    def test_openai_chat_non_streaming_and_streaming(self) -> None:
        _, body = self.request("POST", "/v1/chat/completions", {"stream": False})
        self.assertEqual(
            fake_ai_server.DEFAULT_REPLY,
            json.loads(body)["choices"][0]["message"]["content"],
        )
        _, stream = self.request("POST", "/v1/chat/completions", {"stream": True})
        self.assertIn(fake_ai_server.DEFAULT_REPLY, stream)
        self.assertIn("data: [DONE]", stream)

    def test_responses_and_anthropic_contracts(self) -> None:
        _, response = self.request("POST", "/v1/responses", {})
        self.assertEqual(
            fake_ai_server.DEFAULT_REPLY,
            json.loads(response)["output"][0]["content"][0]["text"],
        )
        _, message = self.request("POST", "/v1/messages", {})
        self.assertEqual(
            fake_ai_server.DEFAULT_REPLY,
            json.loads(message)["content"][0]["text"],
        )


if __name__ == "__main__":
    unittest.main()
