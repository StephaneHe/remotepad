"""Bluetooth RFCOMM server for RemotePad (Step 3.1).

Provides a Bluetooth transport that accepts RFCOMM connections,
authenticates clients, and dispatches input messages.

The socket layer is injected for testability. In production, a real
Bluetooth RFCOMM socket is used; in tests, a mock socket is provided.
"""

from __future__ import annotations

import json
import logging
import threading
from typing import Any, Optional, Protocol

from server.auth_manager import AuthManager
from server.input_controller import InputController
from server.messages import AuthResponse, parse_message, serialize_message
from server.transport import Transport

logger = logging.getLogger("remotepad")

BT_UUID = "00001101-0000-1000-8000-00805F9B34FB"


class ServerSocketFactory(Protocol):
    """Protocol for creating a Bluetooth server socket."""

    def create_server(self, uuid: str) -> Any:
        """Return a server socket that has accept() and close()."""
        ...


class BluetoothServer(Transport):
    """Bluetooth RFCOMM server transport.

    Args:
        auth_manager: Shared authentication manager.
        input_controller: Shared input controller for dispatching messages.
        socket_factory: Injectable factory for creating the server socket.
            If None, attempts to use PyBluez (must be installed separately).
    """

    def __init__(
        self,
        auth_manager: AuthManager,
        input_controller: InputController,
        socket_factory: Optional[ServerSocketFactory] = None,
    ) -> None:
        self._auth = auth_manager
        self._controller = input_controller
        self._socket_factory = socket_factory
        self._server_socket: Any = None
        self._client_socket: Any = None
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._authenticated = False

    # -- Transport interface -------------------------------------------------

    @property
    def is_running(self) -> bool:
        return self._running

    def start(self) -> None:
        """Start the Bluetooth RFCOMM server in a background thread."""
        if self._running:
            return
        if self._socket_factory is None:
            raise RuntimeError(
                "No Bluetooth socket factory provided and PyBluez is not available"
            )
        self._running = True
        self._server_socket = self._socket_factory.create_server(BT_UUID)
        self._thread = threading.Thread(target=self._accept_loop, daemon=True)
        self._thread.start()
        logger.info("Bluetooth server started (UUID %s)", BT_UUID)

    def stop(self) -> None:
        """Stop the server and close all sockets."""
        self._running = False
        if self._client_socket is not None:
            try:
                self._client_socket.close()
            except Exception:
                pass
            self._client_socket = None
        if self._server_socket is not None:
            try:
                self._server_socket.close()
            except Exception:
                pass
            self._server_socket = None
        self._authenticated = False
        logger.info("Bluetooth server stopped")

    def send(self, client_id: str, message: str) -> None:
        """Send a message to the connected client."""
        if self._client_socket is not None:
            try:
                self._client_socket.send((message + "\n").encode("utf-8"))
            except Exception as exc:
                logger.error("BT send error: %s", exc)

    # -- Internal ------------------------------------------------------------

    def _accept_loop(self) -> None:
        """Accept incoming Bluetooth connections (one at a time)."""
        while self._running:
            try:
                client_sock, client_info = self._server_socket.accept()
                logger.info("BT client connected: %s", client_info)
                if self._client_socket is not None:
                    try:
                        self._client_socket.close()
                    except Exception:
                        pass
                self._client_socket = client_sock
                self._authenticated = False
                self._handle_client(client_sock, str(client_info))
            except OSError:
                break
            except Exception as exc:
                logger.error("BT accept error: %s", exc)

    def _handle_client(self, sock: Any, client_id: str) -> None:
        """Read newline-delimited JSON messages from the client."""
        buffer = ""
        while self._running:
            try:
                data = sock.recv(4096)
                if not data:
                    break
                buffer += data.decode("utf-8", errors="replace")
                while "\n" in buffer:
                    line, buffer = buffer.split("\n", 1)
                    line = line.strip()
                    if line:
                        self._process_message(line, client_id)
            except OSError:
                break
            except Exception as exc:
                logger.error("BT recv error: %s", exc)
                break
        logger.info("BT client disconnected: %s", client_id)
        self._authenticated = False

    def _process_message(self, raw: str, client_id: str) -> None:
        """Parse and handle a single JSON message."""
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            logger.warning("BT malformed message from %s", client_id)
            return

        msg_type = data.get("type", "")

        if not self._authenticated:
            if msg_type == "auth":
                pin = data.get("pin", "")
                resp = self._auth.verify(pin, client_id)
                self.send(client_id, serialize_message(resp))
                if resp.success:
                    self._authenticated = True
                    logger.info("BT client authenticated: %s", client_id)
            else:
                resp = AuthResponse(success=False, message="Not authenticated")
                self.send(client_id, serialize_message(resp))
            return

        # Authenticated — dispatch
        try:
            msg = parse_message(raw)
            self._controller.handle(msg)
        except Exception as exc:
            logger.warning("BT dispatch error: %s", exc)
