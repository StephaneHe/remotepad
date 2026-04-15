"""End-to-end integration tests for RemotePad (Task 12).

Uses a real WebSocket server with a mocked InputController
and a real AuthManager, exercised through actual websockets clients.
"""

from __future__ import annotations

import asyncio
import json
import time
from unittest.mock import MagicMock

import pytest
import pytest_asyncio
import websockets
from websockets.protocol import State

from server.auth_manager import AuthManager
from server.messages import (
    MouseMove, MouseClick, MouseScroll, KeyPress, KeyCombo, TextInput,
)
from server.server import RemotePadServer


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest_asyncio.fixture
async def components():
    """Create server components with a mocked InputController."""
    auth = AuthManager()
    controller = MagicMock()
    controller.handle = MagicMock()
    return auth, controller


@pytest_asyncio.fixture
async def server(components):
    """Start a RemotePadServer on a random port, yield it, then stop."""
    auth, controller = components
    srv = RemotePadServer("127.0.0.1", 0, auth, controller)
    await srv.start()
    yield srv, auth, controller
    await srv.stop()


def _ws_url(srv: RemotePadServer) -> str:
    return f"ws://127.0.0.1:{srv.port}"


async def _connect(srv: RemotePadServer):
    """Open a websocket connection to the server."""
    return await websockets.connect(_ws_url(srv))


async def _authenticate(ws, pin: str) -> dict:
    """Send auth message and return the parsed response."""
    await ws.send(json.dumps({"type": "auth", "pin": pin}))
    resp = await asyncio.wait_for(ws.recv(), timeout=2)
    return json.loads(resp)


async def _auth_client(srv, pin):
    """Connect and authenticate, returning the websocket."""
    ws = await _connect(srv)
    resp = await _authenticate(ws, pin)
    assert resp["success"] is True
    return ws


def _is_open(ws) -> bool:
    """Check if a websocket is open (compatible with websockets v16+)."""
    return ws.state is State.OPEN


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestE2EConnect:
    @pytest.mark.asyncio
    async def test_e2e_connect(self, server):
        """Client connects to the WebSocket server successfully."""
        srv, auth, ctrl = server
        ws = await _connect(srv)
        assert _is_open(ws)
        await ws.close()


class TestE2EAuth:
    @pytest.mark.asyncio
    async def test_e2e_auth_success(self, server):
        """Correct PIN yields auth_response(success=true)."""
        srv, auth, ctrl = server
        ws = await _connect(srv)
        resp = await _authenticate(ws, auth.get_pin())
        assert resp["success"] is True
        assert resp["type"] == "auth_response"
        await ws.close()

    @pytest.mark.asyncio
    async def test_e2e_auth_failure(self, server):
        """Wrong PIN yields auth_response(success=false)."""
        srv, auth, ctrl = server
        ws = await _connect(srv)
        resp = await _authenticate(ws, "0000" if auth.get_pin() != "0000" else "1111")
        assert resp["success"] is False
        await ws.close()

    @pytest.mark.asyncio
    async def test_e2e_auth_lockout(self, server):
        """After 3 wrong PINs the client is locked out."""
        srv, auth, ctrl = server
        bad_pin = "0000" if auth.get_pin() != "0000" else "1111"
        ws = await _connect(srv)
        for _ in range(3):
            resp = await _authenticate(ws, bad_pin)
        assert resp["success"] is False
        # 4th attempt should also fail even with correct PIN (locked)
        resp = await _authenticate(ws, auth.get_pin())
        assert resp["success"] is False
        await ws.close()

    @pytest.mark.asyncio
    async def test_e2e_unauthenticated_rejected(self, server):
        """A non-auth message before authentication is rejected."""
        srv, auth, ctrl = server
        ws = await _connect(srv)
        await ws.send(json.dumps({"type": "mouse_move", "dx": 10, "dy": 5}))
        resp = await asyncio.wait_for(ws.recv(), timeout=2)
        data = json.loads(resp)
        assert data["type"] == "auth_response"
        assert data["success"] is False
        await ws.close()


class TestE2ECommands:
    @pytest.mark.asyncio
    async def test_e2e_auth_and_move(self, server):
        """After auth, mouse_move is dispatched to InputController."""
        srv, auth, ctrl = server
        ws = await _auth_client(srv, auth.get_pin())
        await ws.send(json.dumps({"type": "mouse_move", "dx": 10, "dy": -5}))
        await asyncio.sleep(0.1)
        ctrl.handle.assert_called()
        msg = ctrl.handle.call_args[0][0]
        assert isinstance(msg, MouseMove)
        assert msg.dx == 10
        assert msg.dy == -5
        await ws.close()

    @pytest.mark.asyncio
    async def test_e2e_mouse_move(self, server):
        """mouse_move is received and dispatched."""
        srv, auth, ctrl = server
        ws = await _auth_client(srv, auth.get_pin())
        await ws.send(json.dumps({"type": "mouse_move", "dx": 20, "dy": 0}))
        await asyncio.sleep(0.1)
        msg = ctrl.handle.call_args[0][0]
        assert isinstance(msg, MouseMove)
        await ws.close()

    @pytest.mark.asyncio
    async def test_e2e_mouse_click(self, server):
        """mouse_click(left) is dispatched."""
        srv, auth, ctrl = server
        ws = await _auth_client(srv, auth.get_pin())
        await ws.send(json.dumps({"type": "mouse_click", "button": "left"}))
        await asyncio.sleep(0.1)
        msg = ctrl.handle.call_args[0][0]
        assert isinstance(msg, MouseClick)
        assert msg.button == "left"
        await ws.close()

    @pytest.mark.asyncio
    async def test_e2e_key_press(self, server):
        """key_press(a) is dispatched."""
        srv, auth, ctrl = server
        ws = await _auth_client(srv, auth.get_pin())
        await ws.send(json.dumps({"type": "key_press", "key": "a", "modifiers": []}))
        await asyncio.sleep(0.1)
        msg = ctrl.handle.call_args[0][0]
        assert isinstance(msg, KeyPress)
        assert msg.key == "a"
        await ws.close()

    @pytest.mark.asyncio
    async def test_e2e_key_combo(self, server):
        """key_combo(ctrl+c) is dispatched."""
        srv, auth, ctrl = server
        ws = await _auth_client(srv, auth.get_pin())
        await ws.send(json.dumps({"type": "key_combo", "keys": ["ctrl", "c"]}))
        await asyncio.sleep(0.1)
        msg = ctrl.handle.call_args[0][0]
        assert isinstance(msg, KeyCombo)
        assert msg.keys == ["ctrl", "c"]
        await ws.close()

    @pytest.mark.asyncio
    async def test_e2e_text_input(self, server):
        """text_input(hello) is dispatched."""
        srv, auth, ctrl = server
        ws = await _auth_client(srv, auth.get_pin())
        await ws.send(json.dumps({"type": "text_input", "text": "hello"}))
        await asyncio.sleep(0.1)
        msg = ctrl.handle.call_args[0][0]
        assert isinstance(msg, TextInput)
        assert msg.text == "hello"
        await ws.close()


class TestE2EFullSession:
    @pytest.mark.asyncio
    async def test_e2e_full_session(self, server):
        """Complete session: connect, auth, moves, clicks, text, disconnect."""
        srv, auth, ctrl = server
        ws = await _auth_client(srv, auth.get_pin())

        await ws.send(json.dumps({"type": "mouse_move", "dx": 5, "dy": 5}))
        await asyncio.sleep(0.05)
        await ws.send(json.dumps({"type": "mouse_click", "button": "right"}))
        await asyncio.sleep(0.05)
        await ws.send(json.dumps({"type": "mouse_scroll", "dx": 0, "dy": 3}))
        await asyncio.sleep(0.05)
        await ws.send(json.dumps({"type": "key_press", "key": "Return", "modifiers": []}))
        await asyncio.sleep(0.05)
        await ws.send(json.dumps({"type": "text_input", "text": "world"}))
        await asyncio.sleep(0.05)

        assert ctrl.handle.call_count == 5
        await ws.close()


class TestE2ERobustness:
    @pytest.mark.asyncio
    async def test_e2e_malformed_message(self, server):
        """Non-JSON message doesn't crash the server."""
        srv, auth, ctrl = server
        ws = await _auth_client(srv, auth.get_pin())
        await ws.send("this is not json {{{")
        await asyncio.sleep(0.1)
        await ws.send(json.dumps({"type": "mouse_move", "dx": 1, "dy": 0}))
        await asyncio.sleep(0.1)
        ctrl.handle.assert_called()
        await ws.close()

    @pytest.mark.asyncio
    async def test_e2e_single_client(self, server):
        """A second authenticated client disconnects the first."""
        srv, auth, ctrl = server
        ws1 = await _auth_client(srv, auth.get_pin())
        ws2 = await _auth_client(srv, auth.get_pin())

        await asyncio.sleep(0.3)
        try:
            await ws1.send(json.dumps({"type": "mouse_move", "dx": 1, "dy": 0}))
            await asyncio.wait_for(ws1.recv(), timeout=1)
            kicked = False
        except (websockets.exceptions.ConnectionClosed,
                websockets.exceptions.ConnectionClosedOK,
                websockets.exceptions.ConnectionClosedError,
                asyncio.TimeoutError):
            kicked = True

        assert kicked, "First client should be disconnected when second authenticates"
        await ws2.close()

    @pytest.mark.asyncio
    async def test_e2e_client_disconnect(self, server):
        """Client disconnection is handled cleanly."""
        srv, auth, ctrl = server
        ws = await _auth_client(srv, auth.get_pin())
        await ws.close()
        await asyncio.sleep(0.1)
        ws2 = await _auth_client(srv, auth.get_pin())
        assert _is_open(ws2)
        await ws2.close()

    @pytest.mark.asyncio
    async def test_e2e_reconnection(self, server):
        """After disconnect, a new client can connect and authenticate."""
        srv, auth, ctrl = server
        ws = await _auth_client(srv, auth.get_pin())
        await ws.close()
        await asyncio.sleep(0.1)

        ws2 = await _auth_client(srv, auth.get_pin())
        await ws2.send(json.dumps({"type": "mouse_move", "dx": 1, "dy": 1}))
        await asyncio.sleep(0.1)
        ctrl.handle.assert_called()
        await ws2.close()


class TestE2EPerformance:
    @pytest.mark.asyncio
    async def test_e2e_latency(self, server):
        """Average send latency on localhost is < 20ms."""
        srv, auth, ctrl = server
        ws = await _auth_client(srv, auth.get_pin())

        latencies = []
        for _ in range(100):
            start = time.monotonic()
            await ws.send(json.dumps({"type": "mouse_move", "dx": 1, "dy": 0}))
            elapsed = (time.monotonic() - start) * 1000
            latencies.append(elapsed)

        avg = sum(latencies) / len(latencies)
        assert avg < 20, f"Average latency {avg:.1f}ms > 20ms"
        await ws.close()

    @pytest.mark.asyncio
    async def test_e2e_rapid_events(self, server):
        """100 rapid mouse events are all handled without loss."""
        srv, auth, ctrl = server
        ws = await _auth_client(srv, auth.get_pin())

        for i in range(100):
            await ws.send(json.dumps({"type": "mouse_move", "dx": i, "dy": 0}))

        await asyncio.sleep(1)
        assert ctrl.handle.call_count == 100, (
            f"Expected 100 calls, got {ctrl.handle.call_count}"
        )
        await ws.close()
