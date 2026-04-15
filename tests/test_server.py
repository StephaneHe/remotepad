"""Tests for the WebSocket server (Step 1.5 - RED phase).

Uses real WebSocket connections on localhost with mocked InputController.
"""

import asyncio
import json
import logging

import pytest
import pytest_asyncio
import websockets

from unittest.mock import MagicMock

from server.auth_manager import AuthManager
from server.messages import serialize_message, AuthRequest, MouseMove
from server.server import RemotePadServer


@pytest.fixture
def auth_manager():
    return AuthManager()


@pytest.fixture
def input_controller():
    mock = MagicMock()
    mock.handle = MagicMock()
    return mock


@pytest_asyncio.fixture
async def server(auth_manager, input_controller):
    srv = RemotePadServer(
        host="127.0.0.1",
        port=0,
        auth_manager=auth_manager,
        input_controller=input_controller,
    )
    await srv.start()
    yield srv
    await srv.stop()


def _ws_url(srv):
    return f"ws://127.0.0.1:{srv.port}"


class TestServerStartup:
    @pytest.mark.asyncio
    async def test_server_starts(self, server):
        assert server.port > 0
        assert server.is_running


class TestClientConnect:
    @pytest.mark.asyncio
    async def test_client_connect(self, server, caplog):
        with caplog.at_level(logging.INFO):
            async with websockets.connect(_ws_url(server)) as ws:
                assert ws.protocol.state.name == "OPEN"
        assert any("connect" in r.message.lower() for r in caplog.records)


class TestUnauthenticated:
    @pytest.mark.asyncio
    async def test_unauthenticated_rejected(self, server):
        async with websockets.connect(_ws_url(server)) as ws:
            msg = json.dumps({"type": "mouse_move", "dx": 10, "dy": 5})
            await ws.send(msg)
            resp = await asyncio.wait_for(ws.recv(), timeout=2)
            data = json.loads(resp)
            assert data.get("success") is False or "auth" in data.get("message", "").lower()


class TestAuthSuccess:
    @pytest.mark.asyncio
    async def test_auth_success(self, server, auth_manager):
        pin = auth_manager.get_pin()
        async with websockets.connect(_ws_url(server)) as ws:
            await ws.send(json.dumps({"type": "auth", "pin": pin}))
            resp = await asyncio.wait_for(ws.recv(), timeout=2)
            data = json.loads(resp)
            assert data["type"] == "auth_response"
            assert data["success"] is True


class TestAuthFailure:
    @pytest.mark.asyncio
    async def test_auth_failure(self, server):
        async with websockets.connect(_ws_url(server)) as ws:
            await ws.send(json.dumps({"type": "auth", "pin": "0000"}))
            resp = await asyncio.wait_for(ws.recv(), timeout=2)
            data = json.loads(resp)
            assert data["type"] == "auth_response"
            assert data["success"] is False


class TestAuthThenCommand:
    @pytest.mark.asyncio
    async def test_auth_then_command(self, server, auth_manager, input_controller):
        pin = auth_manager.get_pin()
        async with websockets.connect(_ws_url(server)) as ws:
            await ws.send(json.dumps({"type": "auth", "pin": pin}))
            resp = await asyncio.wait_for(ws.recv(), timeout=2)
            assert json.loads(resp)["success"] is True

            await ws.send(json.dumps({"type": "mouse_move", "dx": 10, "dy": -5}))
            await asyncio.sleep(0.1)
            input_controller.handle.assert_called()
            call_arg = input_controller.handle.call_args[0][0]
            assert isinstance(call_arg, MouseMove)
            assert call_arg.dx == 10 and call_arg.dy == -5


class TestSingleClient:
    @pytest.mark.asyncio
    async def test_single_client(self, server, auth_manager):
        pin = auth_manager.get_pin()

        ws1 = await websockets.connect(_ws_url(server))
        await ws1.send(json.dumps({"type": "auth", "pin": pin}))
        resp1 = await asyncio.wait_for(ws1.recv(), timeout=2)
        assert json.loads(resp1)["success"] is True

        ws2 = await websockets.connect(_ws_url(server))
        await ws2.send(json.dumps({"type": "auth", "pin": pin}))
        resp2 = await asyncio.wait_for(ws2.recv(), timeout=2)
        assert json.loads(resp2)["success"] is True

        await asyncio.sleep(0.2)
        try:
            await asyncio.wait_for(ws1.recv(), timeout=1)
        except (
            websockets.exceptions.ConnectionClosed,
            websockets.exceptions.ConnectionClosedOK,
            websockets.exceptions.ConnectionClosedError,
        ):
            pass

        await ws2.close()


class TestClientDisconnect:
    @pytest.mark.asyncio
    async def test_client_disconnect(self, server, caplog):
        with caplog.at_level(logging.INFO):
            async with websockets.connect(_ws_url(server)) as ws:
                pass
            await asyncio.sleep(0.1)
        assert any("disconnect" in r.message.lower() for r in caplog.records)


class TestMalformedMessage:
    @pytest.mark.asyncio
    async def test_malformed_message(self, server, auth_manager):
        pin = auth_manager.get_pin()
        async with websockets.connect(_ws_url(server)) as ws:
            await ws.send(json.dumps({"type": "auth", "pin": pin}))
            await asyncio.wait_for(ws.recv(), timeout=2)

            await ws.send("this is not json {{{")
            await asyncio.sleep(0.1)
            # Server should survive -- send a valid command
            await ws.send(json.dumps({"type": "mouse_move", "dx": 1, "dy": 1}))
            await asyncio.sleep(0.1)


class TestUnknownType:
    @pytest.mark.asyncio
    async def test_unknown_type(self, server, auth_manager):
        pin = auth_manager.get_pin()
        async with websockets.connect(_ws_url(server)) as ws:
            await ws.send(json.dumps({"type": "auth", "pin": pin}))
            await asyncio.wait_for(ws.recv(), timeout=2)

            await ws.send(json.dumps({"type": "unknown_thing", "foo": "bar"}))
            await asyncio.sleep(0.1)
            # Server should survive
            await ws.send(json.dumps({"type": "mouse_move", "dx": 0, "dy": 0}))
            await asyncio.sleep(0.1)
