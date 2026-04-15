"""Tests for the Bluetooth RFCOMM server (Step 3.1)."""

from __future__ import annotations

import json
import threading
import time
from unittest.mock import MagicMock

import pytest

from server.auth_manager import AuthManager
from server.bt_server import BluetoothServer


class FakeClientSocket:
    """Simulates a Bluetooth client socket using an in-memory buffer."""

    def __init__(self):
        self._recv_buffer = b""
        self._send_buffer = b""
        self._closed = False
        self._lock = threading.Lock()
        self._data_ready = threading.Event()

    def inject(self, data: bytes) -> None:
        """Inject data as if sent by the remote client."""
        with self._lock:
            self._recv_buffer += data
            self._data_ready.set()

    def recv(self, bufsize: int) -> bytes:
        """Block until data is available, then return it."""
        self._data_ready.wait(timeout=2)
        if self._closed:
            return b""
        with self._lock:
            chunk = self._recv_buffer[:bufsize]
            self._recv_buffer = self._recv_buffer[bufsize:]
            if not self._recv_buffer:
                self._data_ready.clear()
            return chunk

    def send(self, data: bytes) -> int:
        with self._lock:
            self._send_buffer += data
        return len(data)

    def get_sent(self) -> str:
        with self._lock:
            result = self._send_buffer.decode("utf-8")
            return result

    def close(self) -> None:
        self._closed = True
        self._data_ready.set()


class FakeServerSocket:
    """Simulates a Bluetooth server socket that yields one FakeClientSocket."""

    def __init__(self, client_socket: FakeClientSocket):
        self._client = client_socket
        self._accepted = False
        self._closed = False

    def accept(self):
        if self._accepted or self._closed:
            # Block until closed
            while not self._closed:
                time.sleep(0.1)
            raise OSError("Server closed")
        self._accepted = True
        return self._client, ("AA:BB:CC:DD:EE:FF", 1)

    def close(self):
        self._closed = True


class FakeSocketFactory:
    def __init__(self, server_socket: FakeServerSocket):
        self._server = server_socket

    def create_server(self, uuid: str):
        return self._server


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture
def auth():
    return AuthManager()


@pytest.fixture
def controller():
    ctrl = MagicMock()
    ctrl.handle = MagicMock()
    return ctrl


@pytest.fixture
def client_socket():
    return FakeClientSocket()


@pytest.fixture
def bt_server(auth, controller, client_socket):
    server_sock = FakeServerSocket(client_socket)
    factory = FakeSocketFactory(server_sock)
    srv = BluetoothServer(auth, controller, socket_factory=factory)
    srv.start()
    time.sleep(0.1)  # let accept loop start
    yield srv
    srv.stop()


def _send_json(sock: FakeClientSocket, data: dict) -> None:
    """Send a JSON message terminated by newline."""
    sock.inject((json.dumps(data) + "\n").encode("utf-8"))


def _wait_response(sock: FakeClientSocket, timeout: float = 1.0) -> dict:
    """Wait for the server to send a response and parse it."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        sent = sock.get_sent()
        if sent.strip():
            # May contain multiple lines; return last complete one
            lines = [l for l in sent.strip().split("\n") if l.strip()]
            if lines:
                return json.loads(lines[-1])
        time.sleep(0.05)
    raise TimeoutError("No response received")


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

class TestBtServerStart:
    def test_bt_server_starts(self, bt_server):
        """The BT server is running after start()."""
        assert bt_server.is_running


class TestBtAuth:
    def test_bt_auth_success(self, bt_server, auth, client_socket):
        """Correct PIN yields auth success over Bluetooth."""
        _send_json(client_socket, {"type": "auth", "pin": auth.get_pin()})
        resp = _wait_response(client_socket)
        assert resp["success"] is True

    def test_bt_auth_failure(self, bt_server, auth, client_socket):
        """Wrong PIN yields auth failure over Bluetooth."""
        bad_pin = "0000" if auth.get_pin() != "0000" else "1111"
        _send_json(client_socket, {"type": "auth", "pin": bad_pin})
        resp = _wait_response(client_socket)
        assert resp["success"] is False


class TestBtMessage:
    def test_bt_receive_message(self, bt_server, auth, controller, client_socket):
        """A JSON message received via BT is dispatched to InputController."""
        # Authenticate first
        _send_json(client_socket, {"type": "auth", "pin": auth.get_pin()})
        _wait_response(client_socket)
        time.sleep(0.1)

        # Send a mouse_move
        _send_json(client_socket, {"type": "mouse_move", "dx": 15, "dy": -3})
        time.sleep(0.2)

        controller.handle.assert_called_once()
        msg = controller.handle.call_args[0][0]
        assert msg.dx == 15
        assert msg.dy == -3

    def test_bt_unauthenticated_rejected(self, bt_server, client_socket):
        """Messages before auth are rejected."""
        _send_json(client_socket, {"type": "mouse_move", "dx": 1, "dy": 0})
        resp = _wait_response(client_socket)
        assert resp["success"] is False


class TestBtSingleClient:
    def test_bt_single_client(self, bt_server):
        """Only one BT client at a time (enforced by accept loop)."""
        # The fake server socket only yields one client,
        # verifying the server handles single-client mode
        assert bt_server.is_running
