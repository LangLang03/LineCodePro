#!/usr/bin/env python3
"""Deterministic MCP discovery and invocation endpoint for parity tests."""

from __future__ import annotations

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class McpHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    MCP_PROTOCOL_VERSION = "2025-03-26"
    SESSION_ID = "linecode-parity-session"

    def do_POST(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler API
        length = int(self.headers.get("Content-Length", "0"))
        try:
            request = json.loads(self.rfile.read(length))
        except (UnicodeDecodeError, json.JSONDecodeError):
            self._write(400, {"error": "invalid JSON"})
            return
        if self.headers.get("X-Parity") != "token":
            self._write(401, {"error": "missing X-Parity header"})
            return
        method = request.get("method")
        if method == "initialize":
            self._initialize(request)
            return
        if method == "tools/call":
            self._call_tool(request)
            return
        if method != "tools/list":
            self._write(400, {"error": "unsupported method"})
            return
        self._write(
            200,
            {
                "jsonrpc": "2.0",
                "id": request.get("id"),
                "result": {
                    "tools": [
                        {
                            "name": "parity_echo",
                            "description": "Return a fixed parity reply",
                            "inputSchema": {
                                "type": "object",
                                "properties": {
                                    "text": {"type": "string"},
                                },
                            },
                        },
                        {
                            "name": "parity_status",
                            "description": "Return fixed status",
                            "inputSchema": {"type": "object"},
                        },
                    ]
                },
            },
        )

    def _initialize(self, request: dict[str, object]) -> None:
        params = request.get("params")
        protocol_version = (
            params.get("protocolVersion") if isinstance(params, dict) else None
        )
        if protocol_version != self.MCP_PROTOCOL_VERSION:
            self._write(400, {"error": "unexpected protocol version"})
            return
        self._write(
            200,
            {
                "jsonrpc": "2.0",
                "id": request.get("id"),
                "result": {
                    "protocolVersion": self.MCP_PROTOCOL_VERSION,
                    "capabilities": {"tools": {}},
                    "serverInfo": {"name": "linecode-parity", "version": "1"},
                },
            },
            {"Mcp-Session-Id": self.SESSION_ID},
        )

    def _call_tool(self, request: dict[str, object]) -> None:
        if self.headers.get("Mcp-Protocol-Version") != self.MCP_PROTOCOL_VERSION:
            self._write(400, {"error": "missing protocol version header"})
            return
        if self.headers.get("Mcp-Session-Id") != self.SESSION_ID:
            self._write(400, {"error": "missing session header"})
            return
        params = request.get("params")
        if not isinstance(params, dict):
            self._write(400, {"error": "missing call params"})
            return
        tool_name = params.get("name")
        if tool_name == "parity_echo":
            arguments = params.get("arguments")
            text = arguments.get("text") if isinstance(arguments, dict) else None
            reply = f"fixed parity reply: {text}" if text else "fixed parity reply"
        elif tool_name == "parity_status":
            reply = "fixed parity status: ready"
        else:
            self._write(
                200,
                {
                    "jsonrpc": "2.0",
                    "id": request.get("id"),
                    "error": {"code": -32601, "message": "unknown parity tool"},
                },
            )
            return
        self._write(
            200,
            {
                "jsonrpc": "2.0",
                "id": request.get("id"),
                "result": {"text": reply},
            },
        )

    def log_message(self, format: str, *args: object) -> None:
        return

    def _write(
        self,
        status: int,
        payload: object,
        headers: dict[str, str] | None = None,
    ) -> None:
        body = json.dumps(payload, separators=(",", ":")).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        for name, value in (headers or {}).items():
            self.send_header(name, value)
        self.end_headers()
        self.wfile.write(body)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", default=18765, type=int)
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), McpHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
